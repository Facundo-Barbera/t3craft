# T3 Connect: seguimiento (issue #1)

Estado: **solo investigación, no se implementa nada**. Hecho el 2026-11-09 (noche), después de `docs/plans/t3-connect.md`.

Este documento reabre la investigación con dos pistas nuevas (integraciones de WoW y el T3 local como puerta de entrada), **verifica o corrige** la conclusión anterior y confirma C+ con una prueba real.

**Fuentes**
- `pingdotgg/t3code` en `6e4711d3` (2026-11-09, servidor `0.1.8-nightly`). El informe anterior usó `33db45ef`; entre ambos hay 80 archivos cambiados en las carpetas que leí. No los revisé todos: se recomprobaron en el commit nuevo los puntos que sustentan las conclusiones (`client_id` cerrado, scopes de `mint-credential`, rutas del servidor y los métodos de `peerLinks`).
- Repos públicos de WoW leídos a nivel de código (ver §1): `ryanrhughes/t3code-wow` y `DSilence/t3wow`.
- Una prueba mínima contra un T3 Connect real (§4).

Lo que no se pudo confirmar se marca **«No confirmado»**.

## Resumen

1. Las integraciones de WoW **no usan T3 Connect con cuenta, ni suplantan un `client_id`, ni usan Clerk o DPoP**. Una usa exactamente el mismo modelo que t3craft (token por entorno con scopes `orchestration:read/operate`, ticket de WebSocket y `subscribeShell`). La otra usa el MCP de salida de T3 con OAuth propio y registro dinámico de cliente. Las dos necesitan un proceso companion en el PC porque los addons de WoW no tienen red; **un mod de Fabric no lo necesita**.
2. **El T3 local no agrega los otros entornos.** El cliente de escritorio se conecta a cada máquina por su cuenta. El servidor local solo puede actuar sobre otro entorno mediante un «enlace de entornos» (beta), y solo por MCP con polling, no por el WebSocket que usa el mod. La afirmación «mi T3 local ya tiene acceso a los demás por Connect» es cierta para **la app**, no para **el servidor** local.
3. **C+ funciona.** `/oauth/token`, el ticket y el upgrade de `/ws` pasan por el hostname del túnel de Connect, con solo `orchestration:read`. Ya no depende de que la URL sea alcanzable por LAN o Tailscale.
4. La conclusión anterior se mantiene, con una corrección: C no es solo «el camino conservador»; con C+ cubre el caso principal de A (alcanzar un entorno desde cualquier red) **sin cuenta y sin ampliar el radio de una fuga**.

## 1. Qué hacen en realidad las integraciones de WoW

Fuentes reales (no el titular): el artículo de agregación que se cita en el issue cita como fuente `MiaAI-Lab/t3code`, pero ese repo es un **fork completo de t3code sin nada de WoW**. Lo útil está en los repos de abajo. El comentario de Theo no se pudo localizar: **No confirmado**. Lo que sí está verificado es que el README del addon nativo da crédito a un creador de contenido por la idea de hacerlo addon en lugar de overlay de escritorio.

### Por qué hace falta un companion en WoW

Un addon de WoW **no puede abrir sockets ni leer archivos**; solo dibuja y lee SavedVariables. Por eso las dos integraciones llevan un programa «bridge» en el PC que habla con T3 y se comunica con el addon por trucos:
- Bridge → juego: escribe archivos Lua en addons de carga bajo demanda y avisa con sonidos silenciosos.
- Juego → bridge: el addon dibuja una tira de cuadros de color y el bridge la lee con una captura de pantalla (cifrada con ChaCha20 y HMAC en `ryanrhughes/t3code-wow`).

Nada de esto es copiable ni necesario en Fabric: **un mod tiene red y sistema de archivos propios.**

### `ryanrhughes/t3code-wow` (addon nativo + bridge en Node)

Es **el mismo modelo que t3craft en modo `direct`**. En `bridge/src/auth.js`, `envs.js`, `t3.js` y `docs/ARCHITECTURE.md`:

| Aspecto | Qué hace |
|---|---|
| Credencial | `t3 auth session issue --scope orchestration:read --scope orchestration:operate --ttl 30d`, guardada con permisos 600 y renovada una semana antes de caducar |
| Transporte | `POST /api/auth/websocket-ticket/rpc` y luego `/ws?orchestrationProtocol=2` con frames RPC JSON |
| Datos | `orchestration.subscribeShell` y `subscribeServerConfig`; detalle por `GET /api/orchestration/threads/:id/bounded` |
| Escrituras | `orchestration.dispatchCommand` (`message.dispatch`, `run.interrupt`, `thread.settle`…) y `orchestration.launchThread` |
| Otras máquinas | **Una conexión por máquina, cada una con su propio token** creado en esa máquina por SSH con su CLI `t3`. Las encuentra por Tailscale (MagicDNS) |
| Identificación | `GET /.well-known/t3/environment` (sin auth) para comprobar el id del entorno y la versión de protocolo antes de enviar el token |
| Cuenta / Clerk / DPoP / `client_id` | **No usa nada de eso** |

Dos frases del código resuelven la pregunta 2 de Facundo. Primero, el comentario de `envs.js`: *«the local server doesn't aggregate them (the desktop app connects to each machine itself), so neither do we»*. Segundo, el README promete «threads from your other computers via T3 Connect», pero el código encuentra cada máquina por Tailscale y le crea la sesión por SSH; **no** inicia sesión en la cuenta de Connect. Si una máquina solo se alcanza por Connect, este bridge no la ve sin una URL y un token propios.

### `DSilence/t3wow` (addon + bridge en Rust)

Usa la **vía oficial para agentes externos**: el endpoint `/mcp` de cada entorno con OAuth (`bridge/src/oauth.rs`, `mcp.rs`).
- Descubrimiento RFC 9728 y RFC 8414, **registro dinámico de cliente** con su propio `client_name` y `software_id` (no suplanta a nadie), código de autorización con PKCE y redirect a `127.0.0.1`.
- La aprobación la hace el humano en una página del entorno: introduce un **código de emparejamiento** del host y elige el nivel (Solo lectura, Supervisado, Auto, Acceso total).
- Token de 30 días, **sin refresh**; se vuelve a iniciar sesión al caducar. Guardado en un archivo 600.
- Documenta que se puede usar la **dirección de T3 Connect** del entorno como URL del MCP para llegar desde cualquier red.
- Limitación declarada: las aprobaciones de herramientas no se pueden responder por MCP.

### ¿Cómo se autentican, en resumen?

| Pregunta | Respuesta |
|---|---|
| ¿Emparejado directo? | Sí, en `ryanrhughes/t3code-wow` (sesión por entorno, scopes mínimos) |
| ¿T3 Connect con cuenta? | **No.** Ninguno inicia sesión en Clerk ni habla con el relay |
| ¿Sidecar/companion en el PC? | Sí, los dos, por la limitación del sandbox de WoW |
| ¿MCP? | Sí, en `DSilence/t3wow` (OAuth de salida de T3) |
| ¿Suplantan `t3-web`/`t3-mobile`? | **No** |
| ¿Usan DPoP? | No. Es solo del flujo de cuenta |

### Qué se puede copiar al mod de Fabric y qué no

**Copiable (y ya cubierto en gran parte):**
- El modelo token-por-entorno con scopes mínimos y el protocolo de WebSocket (t3craft ya lo hace; `ryanrhughes/t3code-wow` es una validación independiente de que es el uso previsto de la API).
- **Comprobar `/.well-known/t3/environment` antes de enviar el credencial** (id de entorno y `orchestrationProtocolVersion`). Upstream lo hace en su cliente («cada ruta directa se comprueba primero con el descriptor público»). t3craft no lo hace: mejora barata de seguridad y de mensajes de error.
- **Varias rutas por entorno** (LAN, Tailscale, túnel) y usar la primera que responda. Es el patrón de upstream y de `ryanrhughes/t3code-wow`.
- Marcar `t3 auth session issue` como alternativa a `pairing create` para entornos que ya se administran por SSH (misma sesión, sin enlace de 5 min).

**No copiable o no necesario:** la tira de píxeles, las ranuras, los sonidos y el cifrado de la tira (workarounds de WoW); el companion; el SSH para crear tokens (el mod no debe asumir SSH).

**Copiable solo con cuidado (OAuth de `/mcp`):** es una vía **oficial, acotada y sin suplantación**, pero entrega un token de MCP, no el flujo de WebSocket del mod. **No confirmado** si el token de MCP sirve para `/ws`. No aporta nada sobre el emparejamiento directo para el caso de un solo entorno; solo importa para §2.

## 2. ¿El T3 local puede ser puerta de entrada a los entornos de Connect?

**Respuesta corta: no por el camino que usa el mod; sí, parcialmente, por MCP y en beta.**

### Lo que hace el desktop y lo que no hace el servidor

- `docs/internals/remote.md`: *«Each connection joins a client to one environment over HTTP and WebSocket»*; las conexiones guardadas son locales al perfil del cliente; *«a project and its threads belong to one environment; an agent reaches another environment's threads only through a peer link»*.
- Las credenciales de T3 Connect (token de cuenta y clave DPoP) viven **en el cliente** (`packages/client-runtime`, paquete privado), no en el servidor local. El servidor local no sabe de las máquinas de la cuenta salvo las que se hayan enlazado explícitamente.
- `apps/server/src/ws.ts` solo expone `peerLinks.list/link/unlink/answerRequest` para esto. **No hay ningún método de RPC que reenvíe `subscribeShell`, hilos o terminales de un entorno remoto.** Los `Link` y `Unlink` exigen `access:write`.
- Si en el desktop se apaga «Local environment» («solo remoto»), **no hay servidor local**; entonces no existe ninguna puerta.

### Enlaces de entornos (`t3 environment link`)

Es la única forma en que un servidor llega a otro:
- Beta: hay que activar **Labs → Linked environments en las dos máquinas**.
- Se crea con un código de emparejamiento y una dirección (la lista incluye las máquinas de la cuenta de Connect). El acceso lo elige quien enlaza; **dura 30 días** y se renueva con un código nuevo.
- En la máquina remota aparece como una sesión de cliente MCP (comprobado en una VPS real: una sesión `mcp-client` con scopes `orchestration:read`, `orchestration:operate`, `settings:write`, `source-control:write`, es decir, **más que lo mínimo** si el enlace se hizo con acceso alto).
- Solo **reenvían** las herramientas MCP que aceptan `environmentId`: `t3_thread_list`, `t3_thread_read`, `t3_thread_send`, `t3_thread_wait`, `t3_thread_interrupt`, capacidades y tareas programadas. Se verificó en `packages/contracts/src/orchestratorMcp.ts` y en `mcp/McpToolAccess.ts`. Ahí se aplica el acceso del enlace y el modo del agente que llama.
- Todo es petición/respuesta: **no hay suscripción en vivo** (hay `t3_thread_wait`, que se parte en trozos de 50 s por el límite del borde de Connect).

### Qué implica para el mod

| Vía | ¿Posible hoy? | Comentario |
|---|---|---|
| Emparejar con el T3 local por `localhost` y suscribirse al shell | Sí, pero **solo ve los hilos del entorno local** | Es el modo `direct` normal |
| Que ese mismo WebSocket traiga hilos de otras máquinas | **No** | No existe agregación |
| Hablar con el `/mcp` del T3 local (OAuth con PKCE + registro dinámico, como `DSilence/t3wow`) y pedir `t3_thread_list` con `environmentId` | **Sí, en beta y con polling** | Sin Clerk ni relay. El mod solo guarda un token del T3 local, y el enlace (la credencial amplia) lo guarda ese T3, no el mod |
| Terminal / shell remoto de otra máquina por el T3 local | **No** | Ninguna herramienta MCP reenvía terminales |

Ventajas de la vía MCP: el mod no maneja credenciales de cuenta ni de las otras máquinas; con el modo «Solo lectura» del lado del mod, las llamadas reenviadas quedan limitadas a lectura (el acceso efectivo es la intersección del enlace y del modo del agente).

Costes y riesgos: polling en vez de streaming; depende de una beta (Labs) y de que el T3 local esté **encendido** mientras se juega; hay que mantener un segundo protocolo (MCP/OAuth) además del WebSocket actual; los enlaces caducan a los 30 días; y el radio real depende de cómo el jugador creó el enlace (puede ser amplio). **No confirmado** que el payload de `t3_thread_list` alcance para pintar el mismo «shell» que `subscribeShell` (campos y paginación).

**Veredicto:** no sustituye a C para el caso principal. Es un posible modo opcional «vía mi T3 local» si upstream estabiliza los enlaces o añade un reenvío de `subscribeShell`; hoy conviene pedirlo como función (§6).

## 3. Verificación de la conclusión anterior

| Afirmación de `t3-connect.md` | Estado en `6e4711d3` |
|---|---|
| `client_id` limitado a `t3-web`/`t3-mobile` | **Confirmado** (`infra/relay/src/auth/RelayTokens.ts`, unión cerrada) |
| `mint-credential` emite siempre todos los scopes estándar | **Confirmado** (`CloudLink.ts`: `scopes: AuthStandardClientScopes`, TTL 2 min, atado a la clave DPoP) |
| Credencial de cuenta = acceso amplio a todas las máquinas | **Confirmado**. Además, en una VPS real las sesiones de los clientes oficiales de Connect (`dpop-access-token`) tienen al menos 11 scopes (los vi en el listado, que se cortó), incluidos `terminal:operate`, `providers:manage` y `settings:write` |
| No existe modo cliente headless en el CLI (camino B) | **Se mantiene.** No encontré uno. `t3 auth session issue` es «para clientes headless o remotos», pero **crea tokens en el propio host** (no es un cliente de Connect) |
| «No confirmado que un servidor local agregue los shells remotos» | **Corregido: confirmado que no los agrega** (§2) |
| C+ «no está confirmado» | **Corregido: confirmado** (§4) |

## 4. C+: emparejamiento directo contra el túnel de Connect

**Resultado: funciona.** Prueba mínima el 2026-11-09, desde la propia VPS contra su hostname público de Connect (sale a Internet, pasa por Cloudflare y vuelve por el túnel, no por loopback):

| Paso | Resultado |
|---|---|
| `GET /.well-known/t3/environment` por el túnel, sin auth | 200, `orchestrationProtocolVersion: 2` |
| `t3 auth pairing create --scope orchestration:read --ttl 2m --base-url https://<hostname-del-túnel>` | credencial emitida localmente |
| `POST /oauth/token` (token-exchange `environment-bootstrap`) por el túnel con `scope=orchestration:read` | 200, scope concedido `orchestration:read`, Bearer de 30 días |
| `POST /api/auth/websocket-ticket/rpc` por el túnel | 200 |
| `GET /ws?orchestrationProtocol=2&wsTicket=…` por el túnel (HTTP/1.1) | `101 Switching Protocols` |

Notas:
- Scope mínimo (solo `orchestration:read`), sin ampliar nada. Las sesiones de prueba se **revocaron** al terminar y se comprobó que quedaban 0.
- Con HTTP/2 el upgrade devuelve 400, como es normal en cualquier cliente que no use CONNECT extendido. El `HttpClient` de Java abre el WebSocket con HTTP/1.1, así que no debería afectar al mod. **No probado desde el mod mismo.**
- Por qué es lógico: el túnel reenvía **todo el hostname** a `http://127.0.0.1:<puerto>` del servidor (se comprobó en la configuración de ingress del cloudflared) y el servidor no distingue la ruta de acceso. `--base-url` de `t3 auth pairing create` solo da formato al enlace impreso.
- Lo que **no** se probó: un entorno de otra cuenta, la reconexión tras caducar el túnel por inactividad, ni el comportamiento con el equipo apagado (el túnel cae; ver `t3-connect-troubleshooting.md` upstream).

**Faltante práctico:** no hay un comando del CLI que **imprima** el hostname del túnel (`t3 connect status` no lo muestra, y `t3 pair` no tiene opción de Connect). En el desktop, «Copy MCP URL» da `https://<hostname>/mcp` cuando Connect sirve el entorno. Hay que pedirlo a upstream o documentar la vía del desktop.

## 5. Qué es viable hoy para t3craft

| Camino | Viabilidad hoy | Radio de una fuga |
|---|---|---|
| **C+ (directo contra el hostname del túnel)** | **Sí, probado** | Un entorno, solo `read`/`operate` |
| C (LAN / Tailscale) | Sí, ya implementado | Un entorno, solo `read`/`operate` |
| A (cliente de cuenta nativo) | No sin suplantar `t3-web`/`t3-mobile` (se mantiene la decisión) | Terminal en todas las máquinas |
| B (CLI sidecar) | No existe el modo cliente | n/a |
| Vía T3 local por MCP | Solo en beta, con polling, y sin terminales | El token del mod es del T3 local; el enlace lo guarda ese T3 |

No se encontró ninguna vía oficial y acotada que haga viable A con radio pequeño.

## 6. Siguiente paso recomendado y espera de upstream

**Recomendado (pequeño, sin cuenta, sin ampliar scopes):**
1. **Aceptar el hostname del túnel como URL válida de un entorno** y documentarlo (`docs/features/operate.md`, `config.md` y `PairScreen`): cómo copiarlo del desktop, y que se empareja igual con `t3 auth pairing create --scope … --base-url https://<hostname>`. Hay que probarlo desde el mod real (lo hará el coordinador al compilar en el PC) y cuidar que el cliente HTTP use HTTP/1.1 para `/ws`.
2. **Comprobar `/.well-known/t3/environment`** antes de canjear (id y protocolo) y mostrar errores claros (hostname de túnel caído, versión de protocolo distinta).
3. **Varias rutas por entorno** (LAN/Tailscale/túnel) con la primera que responda; después, aviso de caducidad a 30 días y «volver a emparejar» en un clic (ya estaba en las mejoras de C).

**Queda a la espera de upstream** (el borrador de consulta ya está en `t3-connect-upstream-query.md`):
- Un `client_id` propio y scopes por entorno para terceros en Connect (A).
- Un modo cliente headless del CLI con interfaz local de solo lectura (B).
- Un reenvío de `subscribeShell` (o de un resumen de hilos) por el servidor local a los entornos enlazados, que haría viable la vía «mi T3 local».
- Un comando que imprima el hostname del túnel y la dirección de Connect.

**Decisión de Facundo pendiente:** publicar la consulta a upstream (acción pública). Con C+ confirmado, la pregunta (c) del borrador pasa de «¿funciona?» a «¿está soportado y es estable?».

## 7. Cambios a `bridge/` (sin cambios respecto a `t3-connect.md`)

Nada de lo anterior usa `bridge/`. La recomendación de retirarlo al cerrar #1 con «C ahora» se mantiene.

## Comentario listo para el issue #1

> Seguimiento de la investigación (detalle en `docs/plans/t3-connect-followup.md`):
>
> - **C+ confirmado con prueba real.** Con `orchestration:read` y una credencial de 2 min emitida localmente, `/oauth/token`, `/api/auth/websocket-ticket/rpc` y `/ws` (101) funcionan a través del hostname del túnel de T3 Connect. No hace falta cuenta ni relay para alcanzar un entorno desde otra red. Las sesiones de prueba se revocaron. Falta probarlo desde el mod mismo.
> - **Integraciones de WoW:** no usan Connect con cuenta, ni Clerk, ni DPoP, ni un `client_id` oficial. Una usa el mismo modelo que t3craft (token por entorno con scopes `orchestration:read/operate`, ticket de WebSocket, `subscribeShell`); la otra usa el OAuth del `/mcp` con registro dinámico de cliente. Necesitan un companion solo porque los addons de WoW no tienen red; en Fabric no hace falta.
> - **T3 local como puerta:** no. El desktop se conecta a cada entorno por separado y el servidor local no agrega shells remotos; solo existen los enlaces de entornos (beta, MCP, polling, sin terminales).
> - **Se mantiene** la decisión de no usar `t3-web`/`t3-mobile` y de no implementar A ni un sidecar. Se confirmó en `6e4711d3` que `mint-credential` emite todos los scopes y que el `client_id` sigue cerrado.
> - **Siguiente paso:** aceptar el hostname del túnel como URL de entorno (docs + `PairScreen`), comprobar `/.well-known/t3/environment` y soportar varias rutas por entorno. A la espera de upstream: `client_id` propio, modo cliente headless, reenvío de `subscribeShell` por el servidor local y un comando que imprima el hostname del túnel.
