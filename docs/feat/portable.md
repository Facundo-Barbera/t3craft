# feat/portable: terminal portátil T3

Rama `feat/portable`, basada en `main@fc38fd2`. No se mergea a main: la integra Taller.
Build: `cd mod && ./gradlew build --no-daemon` → OK, 76 tests JUnit (26 nuevos del terminal + 50 existentes).

## Qué hace

- **Ítem "T3 Terminal"** (`t3craft:t3_terminal`). No apila (1 por stack), rareza uncommon.
  - Receta sin forma y barata: panel de vidrio + redstone + lingote de cobre. Se desbloquea en el libro de recetas al tener redstone.
  - Aparece en la pestaña creativa *Herramientas y utilidades*.
  - Al usarlo (clic derecho) se abre la pantalla. No necesita bloques ni base.
- **Tecla configurable** (Opciones › Controles › T3Craft › "Open T3 Terminal", `Y` por defecto). Abre la misma pantalla en cualquier mundo o servidor. Si la pantalla ya está abierta, la misma tecla la cierra.
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
| `dev.t3craft.client.portable.PortableClient` | client | Registra la tecla y la categoría `key.category.t3craft.t3craft`, el hook del ítem y los *fallbacks*. |
| `…PortableAdapter` | client | Adaptador mínimo de solo lectura: `NpcInfo` + estados de entorno → `PortableState`. Funciona en modo direct y en modo bridge. |
| `…PortableState / PortableEnv / PortableThread` | client (Java puro) | Snapshot inmutable: orden, conteos y estado de conexión (`NOT_CONFIGURED`, `DISCONNECTED`, `CONNECTED`). |
| `…PortableNav` | client (Java puro) | Navegación testeable: filtro, selección estable cuando la lista se reordena, páginas y foco desde una notificación. |
| `…NotificationLog` | client (Java puro) | Anillo de 50 entradas con contador de no leídos. **No decide qué es notable**: registra lo que ya anuncia `Notifier` (`t3.Transitions` en direct, `npc.transition` en bridge, con la siembra silenciosa incluida). |
| `…PortableLinks` | client | Punto de enlace hacia feat/chat y feat/config. |
| `…PortableScreen` | client | La UI. |

### Integración necesaria (Taller)

1. **feat/chat.** Si exporta un `public static void open(String envId, String threadId)` en `ChatScreens`, en uno de estos paquetes: `dev.t3craft.client.chat`, `dev.t3craft.client.screen` o `dev.t3craft.client`, se descubre solo por reflexión (`PortableLinks.CHAT_CANDIDATES`). Si no, o para que quede explícito, al iniciar el cliente:
   `PortableLinks.setChat(ChatScreens::open);`
   Mientras tanto se usa el `ThreadChatScreen` provisional que ya está en main.
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
  - `key.category.t3craft.t3craft`
  - `key.t3craft.portable`
- Categoría de teclas `t3craft:t3craft`: si otra rama la registra también, `PortableClient.category()` la reutiliza y no falla.
- Tecla `Y`: hay que comprobar que ninguna otra rama la use por defecto. Se puede cambiar en el juego.

### Riesgos conocidos

- **Registro de ítems vs. multijugador.** El mod sigue siendo `environment: client`, así que los servidores dedicados no lo cargan y no se modifica ninguno. El ítem vive en el registro del cliente y del servidor integrado:
  - En un servidor vanilla, el ítem extra va al final del registro local y no se usa.
  - En un servidor Fabric sin T3Craft, el registry sync de Fabric debería aceptar entradas extra del cliente. **No lo he verificado en vivo.** Si diera problemas: `-Dt3craft.portable.item=false`. La tecla sigue funcionando en ambos casos.
- La pantalla y el ítem **no se probaron dentro del juego**: no se lanzó cliente ni servidor en el VPS. Las firmas de API de 26.3 se comprobaron con `javap` contra los jars de Loom y el código compila. Pendiente para Taller o QA en la PC: abrir con `Y` y con el ítem, craftear en supervivencia y revisar el layout a escala GUI 2-4.

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
- `PortableLinksTest`: descubre un `open(String, String)` estático por nombre, ignora métodos de instancia y cae al fallback.
- `PortableWiringTest`:
  - Apertura con ítem y tecla, sin arrancar Minecraft: la receta produce el ítem y cuesta ≤ 3 ítems; la advancement lo desbloquea; el modelo y la textura existen; las claves de idioma del ítem, la tecla y la categoría están en en_us y es_es.
  - El adaptador mapea `NpcInfo` (sin `threadId` no se adivina nada).
  - **Solo lectura**: un escaneo del código del paquete busca APIs de escritura u operate.

## Reglas respetadas

- Solo lectura: no hay llamadas nuevas a T3, se reusa el estado de la conexión `orchestration:read` existente y no hay una segunda conexión.
- No se copió código de AgentCraft. Se tomó como referencia el patrón de categoría de teclas y DEV.md; está anotado en `THIRD_PARTY_NOTICES.md`.
- No se hizo deploy, no se instalaron paquetes y no se lanzaron dev servers. Los builds se corrieron con `--no-daemon`.
