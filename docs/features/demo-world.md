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

## Reiniciar el mundo HQ (feat/reset-hq, etapa de pruebas)
- **Botón "Reiniciar mundo HQ"**: arriba a la derecha de la pantalla de ajustes de T3Craft (J, Mod Menu o
  `/t3craft config`). Pide confirmación con un `ConfirmScreen` ("¿Seguro? Se pierde todo lo construido en el HQ").
- **`/t3craft debug reset-hq`**: muestra el aviso con un `[Confirmar reinicio]` clicable;
  **`/t3craft debug reset-hq confirm`** lo hace sin preguntar.
- Solo dentro del mundo HQ (o desde el menú, sin mundo abierto). En otro mundo o en un servidor, botón y
  comando se niegan y no tocan nada. Si el HQ no existe todavía, avisa (`/t3craft demo` lo crea).
- Pasos (`DemoClient.doReset`): cancela la construcción, guarda y sale, **valida**
  (`HqReset.validate`), hace el **zip**, borra la carpeta y la vuelve a crear con el flujo del demo
  (`VanillaDemoWorld.create`); al entrar se aplica el setup (spawn en el punto de vista) y se construye el HQ.
- **Validación**: el id de carpeta debe pasar `DemoWorldSpec.validFolderId` (sin `.`, `/`, `..`, comodines);
  el destino es `realpath(saves)/<carpeta>` y debe ser un directorio real (no symlink) cuyo realpath sea
  hijo directo de `saves` con el nombre **exacto** (sensible a mayúsculas). Si existe `t3craft-demo.json`,
  tiene que ser un marcador del demo. El borrado revalida y no sigue symlinks internos. Nada de patrones.
- **Respaldo** (por defecto): `backups/<carpeta>_<yyyy-MM-dd_HH-mm-ss>_reset.zip` en la carpeta de backups
  de Minecraft (`-2`, `-3`… si ya existe), sin `session.lock`. Si el zip falla, no se borra nada.
  Desactivar con `"demo": {"resetBackup": false}` (aditivo, default `true`).
- No toca los sitios de supervivencia (`/t3craft site`): viven en otros mundos; se deshacen con `site undo`.

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
- Fuentes registradas: **`DemoHqSource`** (id `hq`, prioridad 100, el HQ real de feat/hq-design) y
  `ShedStructureSource` (id `shed`, prioridad 0) como **fallback**: si el plan del HQ lanza, `available()`
  pasa a false y `"auto"` elige el shed.
- Antes de elegir, `DemoClient.start` llama a `prepare(marker, spec, worldMinY)` en cada fuente (hook
  opcional del interfaz, por defecto vacío). Otros hooks opcionales: `revision()` (hash de contenido),
  `state()` (datos opacos para el marcador) y `plansSince(marker)` (lo que falta; por defecto todo).
- Marcador **en el propio mundo**: `saves/<carpeta>/t3craft-demo.json`
  `{"setup":1,"structure":"hq","version":1,"hash":"…","state":{"lift":2,"layout":{…}}}`. `hash`/`state` son
  opcionales: los marcadores antiguos (`{"setup":1,"structure":"shed","version":1}`) se siguen leyendo.
  Se escribe solo cuando **todas** las partes terminaron (`JobState.COMPLETED`); si sales a mitad o se
  cancela, la próxima vez se vuelve a construir (lo ya colocado se salta).
- `needsBuild`: fuente distinta, versión distinta, o (si la fuente tiene `revision()`) hash distinto.
- En la primera construcción el jugador se teletransporta al punto de vista (mirando al sitio) y la
  cuadrilla sale 1 s después.
- La comprobación se hace 2 s después de entrar al mundo, en el hilo cliente; si el jugador está en otra
  dimensión, espera.

## El HQ real (`DemoHq` + `DemoHqSource`)
- **Roster**: todos los NPC mostrados (`T3CraftClient.get().npcs().all()`, todos los entornos, agrupados por
  `groupId` = entorno:proyecto; los `settled` van al archivo). Si no hay ninguno, `DemoHq.sample()`: 3
  proyectos fijos en 2 entornos (`vps:t3craft`, `acmesite:forja`, `vps:telar`), 4 hilos cada uno, uno
  archivado. Se fija en cada comprobación (al entrar y con `rebuild`); `status` dice cuál se usó.
- **Layout estable**: `HqGenerator.plan(input, layoutAnterior)` con el layout guardado en `state.layout`,
  así los escritorios no se barajan entre visitas.
- **Hash** = SHA-256 (12 bytes, hex) de `DemoHq.PLAN_VERSION | lift | layout.toJson()`. Mismo hash que el
  marcador → no se construye nada (solo se conectan los anclajes).
- **Diff**: si el mundo tiene un HQ de la misma versión y lift, se construye solo
  `HqPlans.expansion(planAnterior, planNuevo, vacated)` (por grupo; las celdas que sobran pasan a aire, a
  toba en el zócalo y a hierba/tierra bajo el suelo). Si no (primera vez, venía del shed, otra versión u
  otro lift): todo (`HqPlans.byGroup`). `/t3craft demo rebuild` siempre coloca todo (lo igual se salta).
- **Coordenadas**: los planos del HQ se hornean con `HqPlans.demoSite()` (entrada al norte, pórtico en
  `z = 0`, crece hacia `+z`) **elevados `lift` bloques** para que el sótano (`y = -5`) quede sobre el
  bedrock: `lift = max(0, minY + 1 - (origen.y + suelo_sótano))`, 2 con el superplano por defecto (origen
  -60, minY -64). Bajo la huella se pone un **zócalo de toba** entre la hierba y el suelo del hub (primera
  parte del build; se repone si la huella cambia).
- **Anclajes NPC**: al terminar (o si ya estaba construido) se registra
  `HqAnchorSource(plan, DemoHq.worldSite(origen, lift))` con `npcs().behavior().setAnchorSource(...)`:
  los NPC van a `desk_<threadId>`, pizarra y bancos. Se quita (solo si lo puso el demo) al cambiar de
  mundo o al empezar otro build.
- Subir `DemoHq.PLAN_VERSION` si cambia el horneado/zócalo/lift; los cambios del generador de hq-design
  que no cambian el layout **no** cambian el hash: subir también `PLAN_VERSION` cuando hq-design cambie
  su salida (o hacer `rebuild`).
- `HqStructureSource` queda como adaptador genérico (lista fija de planos con id `hq`); registrarlo
  reemplaza a `DemoHqSource`.

## Config (aditiva, opcional) en `config/t3craft.json`
```json
"demo": {
  "worldFolder": "t3craft-hq", "worldName": "T3Craft HQ",
  "origin": [0, -60, 8], "structure": "auto",
  "blocksPerTick": 2.0, "crew": 4, "titleButton": true,
  "resetBackup": true
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
- feat/reset-hq: comando `/t3craft debug reset-hq [confirm]`, botón en `SettingsScreen`, clase `HqReset`,
  clave de config `demo.resetBackup`, lang `t3craft.demo.reset.*`. Sin entrypoints nuevos.
- feat/demo-hq: clases nuevas `DemoHq`, `DemoHqSource`; métodos `default` nuevos en `DemoStructureSource`;
  `DemoMarker` con dos componentes más (`hash`, `state`; constructor de 3 argumentos conservado);
  `DemoBuildRun` con callback de fin. Sin entrypoints, lang ni config nuevos.

## Tests
`DemoLogicTest` (45 casos): decisión crear/abrir/confirmar, idempotencia por fuente y versión del
plan (y JSON del marcador tolerante), elección de la fuente (auto/explícita/fallback/empates/fallos),
parámetros del preset y del punto de vista, y parseo de la sección `demo`.
`DemoHqTest` (arranca el `Bootstrap` vanilla): roster vivo/de ejemplo/archivados, lift, hash
(independiente del orden, sensible al contenido y al lift), idempotencia (mismo layout → nada; marcador
con hash/state ida y vuelta; roster que crece → diff), diff aplicado sobre el HQ anterior = HQ nuevo,
módulo eliminado → aire, convención del demo (pórtico en `z = 0`, sótano elevado, zócalo primero),
anclajes de escritorio y fallback al shed.

## Pendientes
- **Sin probar en juego** (regla: no lanzar Minecraft). Compilado contra 26.3. Lo más frágil:
  `createFreshLevel` con el preset FLAT, la salida/entrada encadenada desde `/t3craft demo`, y que
  `time set 6000` / `setworldspawn` acepten esa sintaxis en 26.3.
- El roster se fija al entrar: si los hilos aparecen después (puente conectando), se ve el de ejemplo
  hasta la próxima entrada o `/t3craft demo rebuild`.
- El generador de hq-design no tiene versión propia: si cambia su salida sin cambiar el layout, hay que
  subir `DemoHq.PLAN_VERSION`.
- El pórtico y los módulos quedan sobre el zócalo de toba 2 bloques por encima de la hierba (sin escalera).
- El teletransporte inicial se repite si la primera construcción no llegó a terminar.
- `DemoClient.structures()` es un estático de tipo propio (como `Builders`); si se usa hot-swap con
  cambios de forma en `DemoStructures`, reiniciar.
