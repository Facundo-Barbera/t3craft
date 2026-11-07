# feat/demo-world: modo demo "T3Craft HQ" (`dev.t3craft.client.demo`)

Un botón y un comando crean (la primera vez) o abren (las siguientes) un mundo superplano dedicado en
creativo y, al entrar, los constructores animados levantan el HQ en un origen fijo, sin selección de
terreno. Es la versión T3Craft del "AgentCraft HQ" (solo la idea, no se usó código de AgentCraft).

## Uso
- **Botón "T3Craft HQ"**: en la pantalla de título (a la derecha de "Un jugador"; si no cabe, arriba a
  la izquierda) y en la de selección de mundo (arriba a la derecha). Se añade con
  `ScreenEvents.AFTER_INIT` de Fabric (**sin mixins**).
- **`/t3craft demo`**: dentro del demo avisa que ya estás ahí. En otro mundo (o en un servidor) pide
  confirmación con un `ConfirmScreen` vanilla ("se guardará y cerrará este mundo"); al aceptar, guarda,
  sale y abre o crea el demo. Si se usa desde el título, lo abre directamente.
- **`/t3craft demo confirm`**: igual, pero sin preguntar.
- **`/t3craft demo rebuild`**: dentro del demo, vuelve a lanzar la construcción aunque el marcador diga
  que ya está hecha (los bloques que ya coinciden se saltan).
- **`/t3craft demo status`**: carpeta, si existe, si estás dentro, la fuente elegida, qué está construido
  y el progreso.

## Mundo
| | |
|---|---|
| Carpeta / nombre | `saves/t3craft-hq` / "T3Craft HQ" (configurable) |
| Generación | preset `minecraft:flat` por defecto (hierba en y = -61), sin estructuras, semilla fija `0x543343524146544C` |
| Modo | creativo, trucos activados, pacífico |
| Gamerules (una vez) | `advance_time`, `advance_weather`, `spawn_mobs`, `spawn_monsters`, `spawn_patrols`, `spawn_phantoms`, `spawn_wandering_traders` = false |
| Una vez, por comando vanilla | `time set 6000` (mediodía), `weather clear`, `setworldspawn` en el punto de vista (10 bloques delante del origen) |

"Crear o abrir" depende solo de si existe la carpeta (`LevelStorageSource.levelExists`): un mundo existente
nunca se recrea ni se sobrescribe. Toda la API vanilla de creación/carga está en **`VanillaDemoWorld`**
(`WorldOpenFlows.createFreshLevel` / `openWorld`, `LevelSettings`, `WorldOptions`, `WorldPresets.FLAT`,
`GameRules`, `disconnectFromWorld`). Es la única clase que debería cambiar al portar a otra versión.

## Construcción e idempotencia
- `DemoStructureSource` → `List<BuildPlan>` relativos al origen del demo (convención de `BuildPlan`: frente
  al norte, crece hacia `+z`). Se construyen en orden, uno tras otro, cada uno con su cuadrilla
  (`Builders.service().build(...)`, rotación `NONE`).
- Hoy: `ShedStructureSource` (`DemoPlans.shed()`, id `shed`, v1, prioridad 0).
- Marcador **en el propio mundo**: `saves/<carpeta>/t3craft-demo.json`
  `{"setup":1,"structure":"shed","version":1}`. Se escribe `structure/version` solo cuando **todas** las
  partes terminaron (`JobState.COMPLETED`); si sales a mitad o se cancela, la próxima vez se vuelve a
  construir (lo ya colocado se salta). Se reconstruye si cambia la fuente (shed → hq) o sube la versión.
- En la primera construcción el jugador se teletransporta al punto de vista (mirando al sitio) y la
  cuadrilla sale 1 s después.
- La comprobación se hace 2 s después de entrar al mundo, en el hilo cliente; si el jugador está en otra
  dimensión, espera.

## Enchufar el HQ modular (feat/hq-design)
`HqStructureSource` es el adaptador, listo pero **sin registrar** (el generador no está en main). Al entrar
hq-design basta una línea, por ejemplo en `DemoClient.onInitializeClient` o en el init del HQ:
```java
DemoClient.structures().register(new HqStructureSource(HQ_PLAN_VERSION,
        () -> HqPlans.forRoster(roster)));   // List<dev.t3craft.build.BuildPlan>
```
- Prioridad 100 > shed: con `"structure": "auto"` (por defecto) gana en cuanto está registrado; los mundos
  demo que ya tenían el shed reciben el HQ en la siguiente entrada (fuente distinta en el marcador).
- hq-design hoy tiene su propio `dev.t3craft.hq.BuildPlan`/`Placement`: la conversión a
  `dev.t3craft.build.BuildPlan.of(...)` va dentro del supplier.
- Si el supplier devuelve vacío o lanza, la fuente queda "no disponible" y se usa el shed.
- Subir `HQ_PLAN_VERSION` cada vez que cambie el resultado del generador.
- Si el HQ necesita ocupar más espacio hacia `-z`, mover el origen (`demo.origin`) o `DemoPlacement.VIEW_DISTANCE`.

## Config (aditiva, opcional) en `config/t3craft.json`
```json
"demo": {
  "worldFolder": "t3craft-hq", "worldName": "T3Craft HQ",
  "origin": [0, -60, 8], "structure": "auto",
  "blocksPerTick": 2.0, "crew": 4, "titleButton": true
}
```
Lo lee `DemoConfig` directamente del archivo (no se tocó `dev.t3craft.client.config`; su codec conserva
secciones desconocidas al guardar). Valores inválidos → default y aviso en el log con solo el nombre de la
clave. Sin pantalla de ajustes para esto todavía.

## Cambios estructurales
- `fabric.mod.json`: nuevo entrypoint `client` `dev.t3craft.client.demo.DemoClient` → **RESTART**.
- Botones en `TitleScreen` y `SelectWorldScreen` (Fabric Screen API, sin mixins).
- Comandos `/t3craft demo [confirm|rebuild|status]` (cliente, fusionados con Brigadier).
- Lang (`assets/t3craft/lang/{en_us,es_es}.json`): `t3craft.demo.button`, `.button.tooltip`,
  `.confirm.title`, `.confirm.body`.
- Sin registros, ítems, mixins ni dependencias nuevas.

## Tests
`DemoLogicTest` (45 casos): decisión crear/abrir/confirmar, idempotencia por fuente y versión del
plan (y JSON del marcador tolerante), elección de la fuente (auto/explícita/fallback/empates/fallos),
parámetros del preset y del punto de vista, y parseo de la sección `demo`.

## Pendientes
- **Sin probar en juego** (regla: no lanzar Minecraft). Compilado contra 26.3. Lo más frágil:
  `createFreshLevel` con el preset FLAT, la salida/entrada encadenada desde `/t3craft demo`, y que
  `time set 6000` / `setworldspawn` acepten esa sintaxis en 26.3.
- Registrar `HqStructureSource` cuando hq-design esté en main.
- El teletransporte inicial se repite si la primera construcción no llegó a terminar.
- `DemoClient.structures()` es un estático de tipo propio (como `Builders`); si se usa hot-swap con
  cambios de forma en `DemoStructures`, reiniciar.
