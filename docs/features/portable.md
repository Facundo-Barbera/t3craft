# feat/portable: terminal portátil T3

Rama `feat/portable`, basada en `main@fc38fd2`. No se mergea a main: la integra Taller.
Build: `cd mod && ./gradlew build --no-daemon` → OK, 185 tests JUnit (tras merge de main; incluye los del terminal y la pantalla en mano).

## Qué hace

- **Ítem "T3 Terminal"** (`t3craft:t3_terminal`). No apila (1 por stack), rareza uncommon.
  - Receta sin forma y barata: panel de vidrio + redstone + lingote de cobre. Se desbloquea en el libro de recetas al tener redstone.
  - Aparece en la pestaña creativa *Herramientas y utilidades*.
  - Al usarlo (clic derecho) se abre la pantalla. No necesita bloques ni base.
- **Tecla configurable** (Opciones › Controles › T3Craft › "Open T3 Terminal", `Y` por defecto; categoría compartida `T3CraftKeys.CATEGORY` = `t3craft:general`). Abre la misma pantalla en cualquier mundo o servidor. Si la pantalla ya está abierta, la misma tecla la cierra.
- **Pantalla `PortableScreen`**, solo lectura:
  - **Hilos**:
    - Columna de entornos con un filtro "All" y uno por entorno. Cada entorno muestra un punto con su estado de conexión, cuántos hilos tiene y cuántos necesitan atención (`!n`). Si se filtra un entorno caído, se ve el motivo.
    - Lista de hilos con glifo de estado, título y entorno · proyecto. Los que necesitan atención van primero.
    - Resumen del hilo seleccionado: estado, entorno/proyecto y actividad.
    - Botones **Open chat** y **Environments…**: delegan en otras ramas (ver más abajo).
  - **Notifications**: los avisos de esta sesión, el más nuevo primero, con hora, título y texto. Incluye contador de no leídos. Enter o doble clic seleccionan ese hilo en la lista.
  - Teclas: ↑↓ / RePág / AvPág / rueda para moverse, ←→ para cambiar de entorno, Enter para abrir, Tab para cambiar de página, Esc o Retroceso para volver o cerrar.
  - **Estados honestos**:
    - *No configurado*: "No T3 environment paired yet…" o "The bridge is not configured.".
    - *Desconectado*: muestra el estado de cada entorno y no enseña filas viejas como si estuvieran vivas.
    - *Conectado pero vacío*: "No threads to show…".
    - *Sin notificaciones*: lo dice, y aclara que el estado inicial no se anuncia.
    - No se inventa contenido.

## Qué NO hace (y por qué)

- **No renderiza historial ni chat.** Eso es de **feat/chat**. "Open chat" llama a `ChatScreens.open(envId, threadId)`.
- **No gestiona entornos.** Eso es de **feat/config**. "Environments…" abre su pantalla.
- **No abre conexiones ni manda RPC.** Todo sale del modelo que el cliente ya tiene, así que no hace falta `getThreadProjection` ni una segunda conexión.
- **No escribe en T3.** Ningún archivo del paquete referencia `dispatchCommand`, `launchThread`, `operate`, `chat.send` ni `sendText`. Lo comprueba un test.
  - Excepción acotada (feat/op-launch): el botón "New thread…" solo abre `NewThreadScreen`, que vive fuera del paquete y es la dueña de la escritura protegida (ver `docs/features/op-launch.md`).

## Arquitectura e interfaces

```
T3CraftClient (existente)
 ├─ npcs().all()      ─┐
 ├─ environments()    ─┼─► PortableAdapter.snapshot() ─► PortableState ─► PortableNav ─► PortableScreen
 ├─ link()/direct()   ─┘                                                     │
 └─ Notifier.transition(...) ─► NotificationLog.INSTANCE ───────────────────┘
PortableScreen ─► PortableLinks.openChat(env, thread)    → feat/chat  (ChatScreens.open)
               └► PortableLinks.openEnvironments(parent) → feat/config
PortableItems (common) ─ TerminalItem.use() (cliente) ─► PortableClient.open()
```

| Clase | Source set | Rol |
|---|---|---|
| `dev.t3craft.portable.PortableItems` | main | Registra el ítem y lo añade a la pestaña creativa. Hook `setClientOpen(Runnable)`. Con `-Dt3craft.portable.item=false` no se registra el ítem. |
| `dev.t3craft.client.portable.PortableClient` | client | Registra la tecla (en `T3CraftKeys.CATEGORY`), el hook del ítem, `HeldScreen` y los *fallbacks*. |
| `…PortableAdapter` | client | Adaptador mínimo de solo lectura: `NpcInfo` + estados de entorno → `PortableState`. Funciona en modo direct y en modo bridge. |
| `…PortableState / PortableEnv / PortableThread` | client (Java puro) | Snapshot inmutable: orden, conteos y estado de conexión (`NOT_CONFIGURED`, `DISCONNECTED`, `CONNECTED`). |
| `…PortableNav` | client (Java puro) | Navegación testeable: filtro, selección estable cuando la lista se reordena, páginas y foco desde una notificación. |
| `…NotificationLog` | client (Java puro) | Anillo de 50 entradas con contador de no leídos. **No decide qué es notable**: registra lo que ya anuncia `Notifier` (`t3.Transitions` en direct, `npc.transition` en bridge, con la siembra silenciosa incluida). |
| `…PortableLinks` | client | Punto de enlace hacia feat/chat y feat/config. |
| `…PortableScreen` | client | La UI. |

### Integración necesaria (Taller)

1. **feat/chat.** `PortableLinks` referencia `dev.t3craft.client.chat.ChatScreens.open(String, String)` directamente (referencia de método en tiempo de compilación, sin reflexión); `PortableClient` la reemplaza con `PortableLinks.setChat(...)`.
2. **feat/config.** `PortableLinks.setEnvironments(parent -> mc.gui.setScreen(new <PantallaEntornos>(parent)));`. Mientras tanto se usa el `PairScreen` de main.
3. Nada más. El registro en `T3CraftClient` y el hook de `Notifier` ya están en esta rama.

### Cambios en archivos existentes (posibles conflictos)

Todos son pequeños y aditivos:

- `T3CraftClient.java`:
  - Un import y `PortableClient.register(this);` después de `PeekHud.register()`.
  - Las dos llamadas a `Notifier.transition` pasan ahora el `npcId`: `t.npcId()` en direct y `msg.npcId` en bridge.
- `hud/Notifier.java`: nueva sobrecarga `transition(mc, @Nullable npcId, …)` que primero registra en `NotificationLog`. La firma vieja se mantiene y delega en la nueva.
- `T3Craft.java`: `PortableItems.register()`. Es el primer registro del lado común; antes no había ninguno.
- `build.gradle`: el source set `test` ve `sourceSets.client.output`, para testear las clases puras del cliente.
- `assets/t3craft/lang/en_us.json` y `es_es.json` son **nuevos**. Si feat/chat o feat/config también crean archivos de idioma, hay que fusionar las claves:
  - `item.t3craft.t3_terminal`
  - `key.t3craft.portable`
- Categoría de teclas: la tecla va en `T3CraftKeys.CATEGORY` (`t3craft:general`, de feat/config). La categoría propia `t3craft:t3craft` y su clave de lang `key.category.t3craft.t3craft` se eliminaron.
- Tecla `Y`: hay que comprobar que ninguna otra rama la use por defecto. Se puede cambiar en el juego.

### Riesgos conocidos

- **Registro de ítems vs. multijugador.** El mod sigue siendo `environment: client`, así que los servidores dedicados no lo cargan y no se modifica ninguno. El ítem vive en el registro del cliente y del servidor integrado:
  - En un servidor vanilla, el ítem extra va al final del registro local y no se usa.
  - En un servidor Fabric sin T3Craft, el registry sync de Fabric debería aceptar entradas extra del cliente. **No lo he verificado en vivo.** Si diera problemas: `-Dt3craft.portable.item=false`. La tecla sigue funcionando en ambos casos.
- La pantalla y el ítem **no se probaron dentro del juego**: no se lanzó cliente ni servidor en el VPS. Las firmas de API de 26.3 se comprobaron con `javap` contra los jars de Loom y el código compila. Pendiente para Taller o QA en la PC: abrir con `Y` y con el ítem, craftear en supervivencia y revisar el layout a escala GUI 2-4.

## Pantalla en vivo en el ítem en la mano (ronda 1b)

Cuando el jugador sostiene el T3 Terminal (mano **izquierda/offhand** o principal), la pantalla del
sprite muestra un resumen en vivo, como el mapa vanilla o el pocket computer de ComputerCraft.
Click derecho sigue abriendo `PortableScreen`. Se apaga con `portable.heldScreen` (ver abajo).

(Preview image removed from the tree on 2026-11-09: `git show archive/research-renders-2026-11-09:docs/feat/held-screen-preview.png`.)

*(Vista previa generada por el rasterizador real con el `ascii.png` de vanilla, 4×; datos sintéticos.)*

**Qué muestra**: la lista unificada en miniatura, con los mismos datos que la lista de feat/chat:
hilos de **todos** los entornos en una sola lista, ordenados por atención (`priority`, mismos tiers
que el mundo) y luego vivos antes que asentados (el empate respeta el orden de origen, que ya
ordena por actividad reciente). Cada fila: chip de entorno (una letra sobre color; color de
`environments.<id>.color` o de una paleta estable), glifo de estado y título. Arriba: `T3 !n need
you` o `n threads`. El hilo más urgente va resaltado con su **último mensaje** (`activity`: lo que
T3 ya manda en el shell, o el motivo de atención/error), en 2 líneas. Estados honestos: sin
emparejar, desconectado (estado de cada entorno, sin filas viejas), sin hilos.

**Cómo** (sin mixin):

```
END_CLIENT_TICK (solo si está en una mano y heldScreen=on)
  PortableAdapter.snapshot ─► HeldScreenModel.build ─► rows (records, equals)
  HeldScreenCache.offer(rows) ── true ─► HeldScreenRaster.render (CPU, 96×144 ARGB) ─► HeldScreenTexture.upload
                              └ false ─► nada (no se redibuja)
cada frame: items/t3_terminal.json ─ select display_context=firstperson_* ─► composite[
              sprite (models/item/t3_terminal_held.json),
              minecraft:special { type: t3craft:held_terminal_screen } ─► TerminalScreenRenderer: un quad con la textura ]
DISCONNECT / CLIENT_STOPPING / heldScreen=off ─► HeldScreen.release() (TextureManager.release + invalida caché)
```

| Clase (`dev.t3craft.client.portable.held`) | Rol |
|---|---|
| `HeldScreenModel` (puro) | Estado → filas de spans coloreados. `order()`, `Badge.of()`. Sin reloj: un terminal inactivo no cambia solo. |
| `HeldScreenCache` (puro) | Redibuja solo si las filas cambian (igualdad de records); `version()` cuenta redibujos; `invalidate()` al liberar. |
| `HeldScreenRaster` (puro) | Filas → píxeles 96×144 (proporción 2:3 = pantalla 6×9 px del sprite × 16). Marco, banda de cabecera, fila resaltada, chips. |
| `BitmapFont` (puro) | Fuente de píxeles a partir del `ascii.png` del propio juego (no se distribuye ningún asset de Mojang). |
| `HeldFont` | Carga la hoja y los `chars` del proveedor (`font/include/default.json`); fallback a rejilla ASCII clásica o cajas. |
| `HeldScreenTexture` | Una `DynamicTexture` en `t3craft:dynamic/held_terminal`, mismo patrón que las texturas de mapa vanilla. |
| `TerminalScreenRenderer` | `SpecialModelRenderer` vanilla registrado en `SpecialModelRenderers.ID_MAPPER` (público). Quad en x 5..11/16, y 4..13/16, delante de ambas caras del sprite (la trasera con U invertida), full-bright. |
| `HeldScreen` | Controlador: tick, caché, textura, liberación, chips (etiqueta del entorno + color de config). |

**Hook de render (documentado)**: Fabric API 0.165.2 no tiene evento de render del ítem en mano
(ni registro de special renderers), así que se usa el mecanismo **data-driven de vanilla**: el
tipo `t3craft:held_terminal_screen` se añade a `SpecialModelRenderers.ID_MAPPER` en el init del
cliente (antes de la primera carga de recursos) y el JSON del ítem lo usa solo en
`firstperson_lefthand/righthand`. En GUI, suelo, marcos y tercera persona se ve el sprite normal
(no enseñamos tus hilos en el terminal de otros jugadores). **No hay mixins.**

**Ajuste visual sin reiniciar**: tamaño/posición en la mano salen del `display` de
`models/item/t3_terminal_held.json` (rotación `[-15,0,0]`, translación `[-2.5,2.5,-2]`, escala
1.25). Se puede retocar con un resource pack y F3+T. **No se probó dentro del juego** (regla: no
lanzar Minecraft): pendiente para QA en la PC revisar que la cara correcta mira a la cámara, la
escala en offhand y la legibilidad.

**Config** (`config/t3craft.json`, aditivo, por defecto encendido):

```json
"portable": { "heldScreen": true }
```

`T3CraftSettings.Portable` + `ConfigCodec` (valor inválido → default con aviso; claves desconocidas
dentro de `portable` se conservan). En la pantalla de ajustes: botón **"Held terminal screen:
ON/OFF"** (primera columna, fila 5; la línea de modo pasó a su derecha). `T3CraftSettings` conserva
el constructor de 9 argumentos para no romper código de feat/config.

## Ronda 2 (pendiente, NO en esta rama)

feat/chat va a replicar el chat de AgentCraft y expondrá la lista de hilos + conversación como un
widget (bounds, tema inyectable, modo compacto). El portable lo incrustará dentro de su marco
(carcasa, bordes y pantalla actuales se conservan). Por eso:

- El WIP de conversación y badges (`dev.t3craft.client.portable.pending`) **se eliminó** en
  feat/chat: la conversación es `chat.ChatTranscript`/`ThreadFeed` (sobre la conexión única,
  `T3Connection.subscribeThread`) y el badge es `chat.EnvBadge` (ver docs/features/chat.md).
- `PortableSettings` (con `heldShowsChat`) se eliminó: `heldScreen` vive ahora en `T3CraftSettings`;
  si la ronda 2 quiere que la pantalla en mano muestre la conversación, se añade otra clave en la
  sección `portable`.
- El held screen ya usa `chat.EnvBadge` (mismo icono que la lista unificada) y el orden compartido
  `chat.ThreadOrder` (también `PortableState.ORDER`).
- `PortableScreen` sigue con su lista propia hasta que el widget de feat/chat exista.

## Tests (`mod/src/test/java/dev/t3craft/client/portable/`)

- `PortableNavTest`:
  - Navegación: arranca en "All", selección acotada en los bordes, ←→ en ambos sentidos, selección estable cuando la lista se reordena, filtro que desaparece o es desconocido.
  - Estados vacío y desconectado sin selección.
  - Página de notificaciones con Back y Toggle, y foco en un hilo que existe o que ya no está.
- `PortableStateTest`:
  - Orden y conteos por entorno.
  - Estados de conexión: `NOT_CONFIGURED`, `DISCONNECTED`, basta un entorno ok, y en bridge manda el enlace (las filas viejas con el enlace caído cuentan como desconectado).
  - `splitId`.
- `NotificationLogTest`: vacío por defecto, el más nuevo primero, no leídos, anillo acotado, entrada sin hilo.
- `PortableLinksTest` (histórico; ya no hay descubrimiento por reflexión): enlazaba un `open(String, String)` estático por nombre.
- `held/HeldScreenModelTest`: estados honestos, lista unificada con chips y orden por atención, último mensaje del hilo top, recorte a ancho y filas, `+n more`, igualdad de entradas → igualdad de filas, `Badge`.
- `held/HeldScreenCacheTest`: no redibuja con el mismo estado reconstruido cada tick; sí con cambio de estado o de último mensaje; `invalidate()`; copia defensiva.
- `held/BitmapFontTest`: anchos con regla vanilla (hoja sintética), plegado de acentos, dibujo y recorte, `clip`, rejilla ASCII clásica, fallback de cajas.
- `held/HeldScreenRasterTest`: proporción del sprite, marco/cabecera/panel/resaltado/chip, opaco, determinista.
- `config/PortableConfigKeyTest`: `portable.heldScreen` por defecto true, ida y vuelta, claves desconocidas conservadas, valor inválido → default con aviso, los `with*` mantienen la sección.
- `PortableWiringTest`:
  - Apertura con ítem y tecla, sin arrancar Minecraft: la receta produce el ítem y cuesta ≤ 3 ítems; la advancement lo desbloquea; el modelo y la textura existen; el JSON del ítem usa la pantalla en vivo solo en primera persona; las claves de idioma del ítem, la tecla y la categoría compartida están en en_us y es_es (y la vieja no).
  - El adaptador mapea `NpcInfo` (sin `threadId` no se adivina nada).
  - **Solo lectura**: un escaneo del código del paquete busca APIs de escritura u operate.

## Reglas respetadas

- Solo lectura: no hay llamadas nuevas a T3, se reusa el estado de la conexión `orchestration:read` existente y no hay una segunda conexión.
- No se copió código de AgentCraft. Se tomó como referencia el patrón de categoría de teclas y DEV.md; está anotado en `THIRD_PARTY_NOTICES.md`.
- No se hizo deploy, no se instalaron paquetes y no se lanzaron dev servers. Los builds se corrieron con `--no-daemon`.
