# feat/operate-core: infraestructura de escritura (`orchestration:operate`)

Decisión de Facundo (2026-11-07): **sí a la escritura** mediante un **segundo emparejamiento
opcional por entorno**. Esta rama solo trae la infraestructura (`docs/plans/plan-v2.md` §1, puntos b–d):
las acciones concretas (inventario §2, ítems 19–24) las implementan otros hilos sobre esta API.
Los ítems 25 (PR, `source-control:write`) y 26 (tareas programadas) quedan fuera.

**Actualización (2026-11-07, `fix/pair-default-operate`)**: decisión de Facundo, **el
emparejamiento por defecto es de escritura**. `/t3craft pair` y la pantalla de emparejar piden
`orchestration:read` + `orchestration:operate` juntos, en **un solo** emparejamiento. Solo lectura
pasa a ser la opción explícita (`/t3craft pair --read-only` o el toggle "Acceso" de la pantalla).
El segundo emparejamiento sigue existiendo como **"Habilitar control"** para mejorar un entorno
que ya está en solo lectura.

## Modelo

| Pieza | Dónde | Qué hace |
|---|---|---|
| `Pairing.OPERATE_SCOPES` / `DEFAULT_SCOPES` | `dev.t3craft.t3` | Exactamente `orchestration:read` + `orchestration:operate`; `DEFAULT_SCOPES` es lo que pide el emparejamiento por defecto y `pairingScopes(readOnly)` elige. `tokenRequestBody(…, scopes)` y `hostCommand(scopes, …)` **solo aceptan** `READ_SCOPES` u `OPERATE_SCOPES`: nunca sale una petición ni un comando sin `--scope` (sin `--scope`, T3 concede todos los scopes: filesystem:write, terminal:operate…). `parseTokenResponse(…, requested)` acepta `read` o `read + operate` dentro de lo pedido y **rechaza cualquier otro scope** con un mensaje que nombra el scope de más y da el comando correcto. |
| `OperateCredentials` | `dev.t3craft.t3` | Token operate en `config/t3craft/credentials/operate/<env>.json` (600). **Es el interruptor**: el control está activo mientras ese archivo tenga un token válido. Nunca en `t3craft.json` ni en `environments.json`. `revoke(env)` borra solo el archivo local. `Environments.remove` (unpair) también lo borra. Ver "Almacenamiento". |
| `OperateKind` | `dev.t3craft.t3` | Tipos de acción y si son **destructivos**: `RUN_INTERRUPT`, `THREAD_STOP`, `REQUEST_DENY`, `THREAD_SETTLE`, `QUEUE_CANCEL` y `THREAD_ARCHIVE`. Un tipo desconocido cuenta como destructivo. |
| `OperateAction` | `dev.t3craft.t3` | Kind + env + hilo + RPC (`orchestration.*`) + payload. El payload nunca se loguea. |
| `OperateGate` | `dev.t3craft.t3` | La única puerta: sin token, `READ_ONLY`; si es destructiva y no está confirmada, `NEEDS_CONFIRMATION`; si pasa, la manda el transporte del entorno. Todo intento queda en el audit log. Nunca lanza excepciones. |
| `OperateConnection` | `dev.t3craft.t3` | Socket operate por entorno, gestionado por `DirectController` junto a la `T3Connection` de lectura (`operateTransport(env)` y `resetOperate(env)`). Se abre bajo demanda y se cierra tras 60 s inactivo. No reintenta escrituras. |
| `AuditLog` | `dev.t3craft.t3` | `config/t3craft/audit.log`, JSON lines: `time, env, thread, action, destructive, result, origin, detail`. Sin payload ni títulos; `detail` pasa por `T3Connection.sanitize`. Rota a `audit.log.1` al superar 1 MiB. |
| `OperateUi` | `dev.t3craft.client.operate` | Para las pantallas: `run(action, origin, returnTo, done)` abre la confirmación si hace falta; `guard(widget, env)` deshabilita el widget con el tooltip "Requiere control (operate)"; `canOperate(env)` es la consulta para textos y botones (p. ej. la caja de respuesta del chat: si es `true`, no digas "solo lectura"); `enableControlScreen(parent, env)` abre "Habilitar control" o, si el entorno no está emparejado, la pantalla de emparejar. |
| `ConfirmActionScreen` | `dev.t3craft.client.screen` | Confirmación reutilizable: acción, hilo y entorno. |
| `PairScreen` | `dev.t3craft.client.screen` | Emparejar: lectura + control por defecto, toggle "Acceso: solo lectura". Muestra el comando exacto para el host según el toggle, y un aviso de riesgo cuando hay control. |
| `OperatePairScreen` | `dev.t3craft.client.screen` | "Habilitar control" (mejora de un entorno en solo lectura), con la advertencia de riesgo y el comando exacto para el host. |
| `OperateClient` | `dev.t3craft.client.operate` | Dueño del gate en el cliente; `enable(env, link, origin, done)` y `revoke(env, origin)`. |

## Para el jugador

- **Emparejar (por defecto, lectura + control)**: `/t3craft pair` o "Emparejar nuevo…" en
  Entornos. En el host:
  `t3 auth pairing create --scope orchestration:read --scope orchestration:operate --ttl 5m --base-url https://host`.
  Si la credencial se emitió solo con `--scope orchestration:read` (T3 concede lo pedido ∩ lo de
  la credencial), el entorno queda emparejado en solo lectura y el mensaje lo dice.
- **Emparejar en solo lectura**: `/t3craft pair --read-only`, o el toggle "Acceso: solo lectura"
  de la pantalla. En el host: `t3 auth pairing create --scope orchestration:read --ttl 5m …`.
  Volver a emparejar un entorno en solo lectura borra su token operate anterior.
- **Habilitar control** (entorno ya emparejado en solo lectura): en Entornos, botón
  `Habilitar control` de la fila, o `/t3craft operate enable <env>`. En el host:
  `t3 auth pairing create --scope orchestration:read --scope orchestration:operate --ttl 5m --label "T3Craft operate" --base-url https://host`.
  Se pega el link (debe ser del mismo host que el entorno). El emparejamiento de lectura se
  mantiene; si la credencial no trae operate, se rechaza y no cambia nada.
- **Revocar**: botón `Revocar` en la fila, o `/t3craft operate revoke <env>`. Borra el token
  operate local y **el entorno queda emparejado en solo lectura** (no se desempareja; para eso
  está `/t3craft unpair <env>`). **En el servidor T3 la revocación es manual**:
  `t3 auth session list` y luego `t3 auth session revoke <id>` (o Sharing & access). T3Craft no
  hace escrituras para revocar. Con un emparejamiento combinado, la zona de lectura sigue usando
  la misma sesión, que en el host conserva operate: el mensaje lo avisa y sugiere revocarla allí y
  volver a emparejar con `--read-only`.
- **Estado**: `/t3craft operate`. La fila del entorno muestra `[control]` cuando el control está
  activo.
- Sin token operate, todo sigue en solo lectura.

## Almacenamiento (decisión de `fix/pair-default-operate`)

- **Emparejamiento combinado** (por defecto): una sola sesión `read + operate`.
  `Environments.pair` la guarda **en los dos archivos**: `credentials/<env>.json` (zona de lectura,
  la usa `T3Connection`) y `credentials/operate/<env>.json` (zona de escritura, la usa
  `OperateGate`). `OperateCredentials.sharesReadSession(env)` dice si son la misma sesión.
- **Solo lectura**: solo `credentials/<env>.json` con `orchestration:read`, y se borra el operate
  anterior si lo había.
- **"Habilitar control"**: una sesión operate aparte en `credentials/operate/<env>.json`; la de
  lectura no se toca.
- **Migración aditiva**: no hay nada que migrar. Lo guardado antes (lectura con
  `orchestration:read` y, opcionalmente, un operate aparte) se lee igual. `Environments.credential`
  ahora acepta `read` o `read + operate` (antes solo `read`) y sigue ignorando cualquier otro
  conjunto. `Environments.save` (lo usa renombrar) nunca toca el token operate.
- **El interruptor del control es el archivo operate**: `OperateGate` solo mira
  `OperateCredentials`. Revocar borra ese archivo y deja el entorno en solo lectura, aunque la
  sesión de lectura sea la combinada.

## Cómo añadir una acción nueva (hilos 19–24)

1. **Elige o añade el `OperateKind`** en `dev.t3craft.t3.OperateKind`, pensando bien si es
   `destructive`, y añade `t3craft.operate.kind.<wire>` en `lang/en_us.json` y `lang/es_es.json`
   (`OperateLangTest` lo comprueba).
2. **Construye la acción** con el payload del RPC (para `dispatchCommand`, un
   `OrchestrationV2Command`; revisa el contrato en `t3code-ref`):
   `OperateAction a = OperateUi.gate().operate(env).dispatch(OperateKind.THREAD_STOP, threadId, title, command);`
   Para otro RPC: `new OperateAction(kind, env, threadId, title, "orchestration.launchThread", payload)`.
3. **Ejecútala desde la UI**: `OperateUi.run(a, OperateGate.Origin.UI, this, r -> …)`. Si es
   destructiva, sale la confirmación; `done` siempre se llama una vez en el hilo cliente con un
   `OperateResult` (`OK`, `READ_ONLY`, `CANCELLED`, `REJECTED`, `AUTH_FAILED`, `OFFLINE`, `FAILED`).
   Desde un comando, usa `Origin.COMMAND`.
4. **Protege el botón**: `OperateUi.guard(button, env)`. Queda deshabilitado con el tooltip
   "Requiere control (operate)" cuando el entorno es solo lectura.
5. **Testea con un fake**: `new OperateGate(creds, env -> fakeTransport, AuditLog.in(tmp), () -> now)`
   (ver `OperateGateTest.exampleSettle`). Nunca contra un T3 real durante el desarrollo.

`FAILED` significa "se envió y no llegó la respuesta": la acción puede haberse aplicado, así que
no se reintenta sola. Muestra el mensaje y deja que el jugador lo compruebe.

## Seguridad

- Las dos zonas de §1 siguen separadas en el código: el socket de lectura solo hace llamadas de
  lectura y las escrituras solo salen por `OperateGate` y el socket operate, que solo existe
  mientras hay escrituras. Con el emparejamiento combinado (por defecto) el token de lectura es la
  misma sesión `read + operate`; con `--read-only` o "Habilitar control" siguen siendo tokens
  distintos.
- Nunca se aceptan scopes fuera de `orchestration:read` / `orchestration:operate`: ni al canjear
  (`parseTokenResponse`), ni al leer del disco (`Environments.credential`,
  `OperateCredentials.stored`).
- Las confirmaciones de acciones destructivas (stop, settle, deny, cancel, interrupt, archive) y
  el audit log no cambian. El emparejamiento también queda en el audit (`pair`, `read+operate` o
  `read-only`).
- `OperateAction` solo acepta RPC `orchestration.*`; cualquier otra cosa necesitaría otros scopes.
- Tokens: solo en `credentials/`, nunca en logs, chat, `toString()` ni en el audit.
- `T3Connection.sanitize` ahora también redacta `token=`, `access_token=`, `subject_token=` y
  campos JSON `accessToken`/`credential`.

## Acción: settle, snooze, pin y mark-read (ítem 22, feat/op-settle)

- **Pura**: `dev.t3craft.t3.ThreadOps` construye las `OperateAction` (`dispatchCommand`):
  `thread.settle` (destructiva, pide confirmación), `thread.snooze {snoozedUntil}`,
  `thread.pin`, `thread.unpin` (kind nuevo `THREAD_UNPIN`) y `thread.visit {visitedAt: ahora}`
  para mark-read. Snooze tiene tres presets (`ThreadOps.Snooze`): 1 h, 4 h y mañana a las 9:00
  (hora local). Reloj, zona y `commandId` son inyectables (tests: `ThreadOpsTest`).
- **UI**: `ThreadOpsBar.add(screen, () -> target, x, y, w, h, this::addRenderableWidget)` es una
  fila de botones (Settle, Snooze…, Pin, Unpin, Read) protegidos con `OperateUi.guard`. Está en el
  menú del NPC (`ThreadChatScreen.addThreadOps`) y en la barra inferior del portable
  (`PortableScreen.addThreadOps`/`updateThreadOps`, fáciles de mover al rediseño). Snooze… abre
  `SnoozeScreen`. El resultado sale en un toast (`ThreadOpsUi.message`).
- **Comando**: `/t3craft operate settle|snooze|pin|unpin|read <npc o env:thread> [1h|4h|tomorrow]`.
  El objetivo es el id del NPC, su título (si es único) o un `env:thread`. Settle abre la
  confirmación en el tick siguiente.
- **El NPC se va a casa**: cuando el settle da `OK`, `BehaviorDirector.depart(npcId)` hace que el
  NPC salude, camine hacia la salida (`AnchorSource.exit()`: la entrada del HQ; si no, el centro de
  su barrio) como mucho 5 s y se desvanezca con partículas (`Departure`, sin teletransportes).
  Queda oculto hasta que T3 refleje el settle (`NpcInfo.settled()`) y el HQ le libere el
  escritorio; si a los 30 s no llegó, vuelve.
