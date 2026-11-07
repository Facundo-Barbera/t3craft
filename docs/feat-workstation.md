# feat/workstation: una computadora por hilo

Paquete: `mod/src/client/java/dev/t3craft/client/workstation/` (todo del lado del cliente; no hizo
falta nada en `dev.t3craft.workstation` común).
Tests: `mod/src/test/java/dev/t3craft/client/workstation/` (38 tests JUnit en 6 clases).

## Qué hace

Cada hilo visible tiene una **estación de trabajo** delante del escritorio de su NPC: una
computadora y, encima, una **pantalla** que se lee sin abrir menús:

```
  Fix login redirect loop          ← título (blanco, una línea, "…" si no entra)
  ⚙ WORKING · 3m                   ← glifo + estado + antigüedad, en el color del estado
  Running tests in mod/: 12 of     ← última acción o mensaje (gris claro, hasta 3 líneas)
  40 passed…
```

| Estado del hilo (`NpcInfo.state`) | Pantalla | Color |
|---|---|---|
| `working` | `⚙ WORKING` | `#55FFFF` |
| `needs_attention` | `! NEEDS YOU` | `#FFAA00` |
| `error` | `✖ ERROR` | `#FF5555` |
| `done` | `✔ DONE` | `#55FF55` |
| `idle` (y cualquier estado desconocido) | `· IDLE` | `#AAAAAA` |
| settled (`NpcInfo.settled`) | `▣ ARCHIVED` (título atenuado, sin actividad) | `#777777` |

Los colores y glifos son los de `ThreadState` (lo comprueba un test), así que pantalla, nametag y
HUD coinciden. El fondo de la pantalla es el color del estado oscurecido a 1/5, casi opaco, y la
pantalla es full-bright, como un monitor encendido.

**Click (usar) en la computadora abre el chat de ese hilo** mediante `ThreadOpener`.

## Piezas

| Clase | Rol |
|---|---|
| `StationStatus` | estado del hilo → estado de la estación, con color, glifo, palabra y fondo. Puro. |
| `TextFit` | normalizado (espacios y controles), recorte a una línea con "…" y ajuste de línea por palabras (parte palabras largas, "…" en la última línea si no entra). Puro. |
| `AgeFormat` | `now`, `45s`, `12m`, `3h`, `2d`. Granular a propósito: la etiqueta cambia como mucho una vez por segundo durante el primer minuto y una vez por minuto después. |
| `WorkstationThread` | lo que la estación sabe de un hilo (clave `<env>:<thread>`, env, thread, título, grupo, prioridad, estado, actividad, `updatedAtMs` opcional). `fromNpc(NpcInfo)`. |
| `WorkstationModel` | hilo + edad → `StationScreen` (líneas con color, estado, fondo). Puro y determinista. |
| `WorkstationCache` | **invalidación**: por hilo guarda el hash del contenido (título, estado, actividad) y la etiqueta de edad; solo devuelve una pantalla nueva cuando cambió alguno de los dos. Con un hilo sin cambios cuesta una comparación por tick. |
| `StationPlacement` | `computerPos` (bloque) + `screenPos` + yaw de la pantalla, en números simples. `inFrontOf(...)` y `on(...)`. |
| `WorkstationPlacementSource` | interfaz hilo → `StationPlacement` (null = sin estación). |
| `DeskPlacement` | fuente **provisional** (ver abajo). |
| `WorkstationRenderer` | entidades de display de cliente; crea una vez, actualiza el texto solo si cambió. |
| `ThreadOpener` / `ChatScreenOpener` | qué abre el click; por defecto la `ThreadChatScreen` de main. |
| `WorkstationConfig` | bloque `workstations` de `config/t3craft.json`. |
| `WorkstationClient` | entrypoint `client`: tick, callbacks de uso, comando y puntos de integración. |

### Antigüedad

Si la fuente da `updatedAtMs`, la edad sale de ahí. Hoy `NpcInfo` no trae marcas de tiempo, así que
la edad se mide desde **el último cambio que vio la caché** (estado, actividad o título). Un hilo
que se ve por primera vez (al entrar al mundo o reconectar) muestra el estado **sin edad**, en vez
de un "now" engañoso. Es el mismo criterio de seeding silencioso que los avisos. Para mostrar la
edad real desde el primer momento haría falta llevar `latestMessageAt` o `latestRunCompletedAt`
de `ThreadShell` hasta `NpcInfo` (pendiente, toca tipos compartidos).

## Render en el mundo

Sin bloques ni entidades registradas: no hace falta RESTART por registros y no complica los
snapshots de site.

- **Pantalla**: `Display.TextDisplay` con billboard `FIXED`, el yaw de `StationPlacement`
  (mirando al escritorio), escala 0,45 (28 caracteres ≈ 1,9 bloques de ancho), full-bright y fondo
  teñido.
- **Computadora**: si en `computerPos` ya hay un bloque (lo puso hq-design o builders), se usa ese
  bloque, cualquiera que sea. Mientras ese lugar es **aire** (fuente provisional, cuadrícula, sin
  HQ), se dibuja un `Display.BlockDisplay` de relleno y una entidad `Interaction` de cliente de
  1×1,75 para que haya algo que clicar. En cuanto aparece un bloque real (se revisa cada 20 ticks),
  los dos se retiran y el click pasa por `UseBlockCallback`. El prop lo elige `StationProp.choose`
  (lógica pura, con tests):

  | Bloque real en `computerPos` | Módulo de bloques | Prop |
  |---|---|---|
  | sí | cualquiera | **ninguno** (el bloque real, p. ej. el monitor del HQ, ya es la computadora) |
  | no | activo | `t3craft:monitor` mirando al escritorio |
  | no | apagado (solo GUI) | base baja de **concreto negro** (0,8×0,4×0,8, centrada) bajo la pantalla flotante |

  Nunca se usa un bloque técnico (antes era un **observador**, que parecía un artefacto de
  redstone suelto junto a cada agente). Si el tipo de prop cambia, se rehace. Al primer tick tras
  cargar (también tras un hot-swap), el renderer descarta cualquier `BlockDisplay` de observador
  con id ≤ −5.000.000 que haya quedado huérfano de builds anteriores.
- **Ids de entidad**: rango propio desde **−5.000.000** hacia abajo (`WorkstationRenderer.ID_BASE`).
  NpcRegistry usa −1.000.000…, builders −2.000.000… y BehaviorDirector −3.000.000…. El contador es
  de instancia, no estático.
- Ojo: el barrido de huérfanas de `NpcRegistry` (solo tras una recuperación por error o hot-swap)
  considera suyo todo id ≤ −1.000.000 y puede borrar nuestras entidades. El renderer revisa
  `isRemoved()` cada tick y reconstruye la estación, así que se cura solo. Las entidades se guardan
  por referencia y nunca se buscan por id con cast.
- Las entidades se crean una vez por estación y solo se recrean si la posición cambia o si
  desaparecieron. Al cambiar de nivel se olvidan (murieron con el nivel) y se reconstruyen.

## Posiciones

`WorkstationPlacementSource.placement(thread)` se llama en el hilo del cliente cada tick para cada
hilo visible, precedido por `prepare(threads)` con todos los hilos (archivados incluidos), para que
la fuente rehaga su layout solo cuando el conjunto cambia. Hay que devolver valores cacheados.

**Provisional (`DeskPlacement`, activa hoy)**: la computadora va **un bloque delante del lugar de
escritorio del NPC**, con la pantalla encima y girada hacia él. Ese lugar es el mismo al que el
`BehaviorDirector` lleva al NPC cuando no hay base física: el `RingLayout` construido con el
origen del layout del registro y los mismos miembros (id, grupo, prioridad, ordenados por id). Así
el NPC "teclea" mirando su monitor. Para eso se añadió `NpcRegistry.layoutOrigin()`, un getter de
lectura (es el único cambio fuera del paquete, además de `fabric.mod.json` y el lang). Los hilos
settled no tienen estación aquí: su escritorio queda libre.

**Punto de integración con hq-design** (cuando entre a main): un adaptador que traduzca, para cada
escritorio, `computerPos` y `screenPos` + orientación a `StationPlacement` (constructor directo, o
`StationPlacement.on(cx, cy, cz, yaw)` si solo da el bloque y la orientación) y lo registre así:

```java
WorkstationClient.get().setPlacementSource(thread -> hq.deskFor(thread.key()) /* → StationPlacement o null */);
// null vuelve a DeskPlacement
```

Mientras hq-design solo registre su `AnchorSource` en el director y no esta fuente, los NPCs
caminarán a los escritorios del HQ y las computadoras seguirán en el anillo. Hay que registrar las
dos a la vez.

## Click

- `UseBlockCallback` (solo en cliente): si el bloque clicado es exactamente el `computerPos` de una
  estación visible, con la mano principal y **sin agacharse**, abre el hilo y devuelve `FAIL`. En el
  cliente de Fabric, `FAIL` cancela sin mandar `ServerboundUseItemOnPacket`, así que **no se
  ejecuta la acción propia del bloque** (por ejemplo, si el HQ usa un dispensador o una mesa de
  crafteo como computadora, no se abre su GUI). Cualquier otro bloque, la mano secundaria o el uso
  agachado (para poner bloques contra la computadora) → `PASS`: vanilla intacto.
- `UseEntityCallback` sobre nuestra `Interaction` (o nuestras pantallas): abre el hilo y devuelve
  `SUCCESS`, igual que el handler de los NPCs.
- `AttackEntityCallback` sobre nuestras entidades → `FAIL` (no se manda un paquete de ataque al
  servidor por una entidad que no conoce).
- `ThreadOpener.open(envId, threadId)`: por defecto `ChatScreenOpener` busca el `NpcInfo` en el
  registro y abre la `ThreadChatScreen` que hay hoy en main.

**Punto de integración con feat/chat**: cuando entre la UI unificada de lista y conversación,
`WorkstationClient.get().setThreadOpener((env, thread) -> …abrir su componente…)`. `null` vuelve al
default.

## Hilos settled (archivo)

Un hilo con `settled=true` pasa a `StationStatus.ARCHIVED`. Con `DeskPlacement` deja de mostrarse
en su escritorio. Con una fuente del HQ que devuelva una posición en el archivo, se muestra ahí con
la pantalla `▣ ARCHIVED`. `WorkstationClient.get().archived()` expone las claves de las estaciones
que hoy muestran un hilo archivado. El traslado físico lo hacen hq-design y builders.

## Config (aditiva)

```json
"workstations": { "enabled": true }
```

En `config/t3craft.json`. Si falta, o el tipo es incorrecto, vale `true`. No se tocó
`T3CraftSettings` (así no cambia el constructor del record que comparten otras ramas):
`ConfigCodec` fusiona sobre el JSON leído, así que la clave sobrevive a los guardados de la
pantalla de ajustes. El cliente relee el bloque cuando cambia la fecha de modificación del archivo
(se revisa cada 5 s), así que alcanza con editarlo a mano o con `/t3craft config reload`.

Comando: `/t3craft workstation` (estado), `/t3craft workstation on|off` (guarda el archivo de forma
atómica, fusionando, y recarga `ConfigManager` para que su base de fusión incluya la clave). Nunca
sobrescribe un archivo con JSON roto. Textos en `assets/t3craft/lang/{en_us,es_es}.json`
(`t3craft.workstation.*`).

## Cambios estructurales → **RESTART**

- `fabric.mod.json`: nueva entrada `client` `dev.t3craft.client.workstation.WorkstationClient`.
- Callbacks nuevos: `UseBlockCallback`, `UseEntityCallback`, `AttackEntityCallback`, tick de cliente
  (con `GuardedEvents`, módulo `workstations`), `CLIENT_STOPPING`, `DevReload` (`t3craft.workstations`).
- Comando: `/t3craft workstation [on|off]` (se fusiona con Brigadier bajo `/t3craft`).
- Sin mixins, keybinds, bloques, ítems ni registros.

## Hot-swap

Sin estado estático salvo `instance` (se limpia en `CLIENT_STOPPING`). El reset de `DevReload`
descarta las entidades y la caché; el siguiente tick reconstruye todo.

## Pendientes

- **No se probó dentro del juego** (por regla, no se lanza Minecraft). Falta verificar a ojo la
  escala, la altura (`StationPlacement.SCREEN_HEIGHT`) y la orientación de la pantalla (que el texto
  quede legible desde el escritorio y no espejado), y que la `Interaction` de cliente reciba el click.
- Edad real desde el primer momento: llevar un timestamp de `ThreadShell` a `NpcInfo`.
- Opción en la `SettingsScreen` para el interruptor (hoy: comando o archivo); necesitaría tocar el
  paquete config.
- Los NPCs no esquivan la computadora provisional (es una entidad de display, sin colisión).
- Ideas de AgentCraft (monitores por escritorio); **no se copió código**, no hace falta atribución
  en `THIRD_PARTY_NOTICES.md`.
