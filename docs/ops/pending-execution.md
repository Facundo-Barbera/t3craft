# Pendiente de ejecución

## Estado al 2026-11-09, ~17:50 CET: el coordinador arranca en la Mac mini
Ya no hay moratoria. Dónde corre cada cosa está en `AGENTS.md`, "Where things run": el coordinador en la Mac mini, los hilos hijos en la VPS, y Gradle y los renders solo en el PC.

### Reset de T3 en la VPS
- **Causa:** `statev2.sqlite` llegó a ~6,2 GB porque los agentes leían PNG de renders.
  - Se movió a `~/.t3/userdata/backups/statev2-20261109-1037-CST`. **No borrarla.**
  - T3 arrancó con una base nueva, así que los hilos viejos no aparecen.
  - Regla nueva: `AGENTS.md`, regla 9.
- **Agentes:** todos se detuvieron hacia las 17:30 CET. En la VPS no queda ningún proceso java, gradle ni bucle `submit.sh`. La carga es ~0,5.
- **Aprobación de renders:** la de la sesión anterior quedó cerrada.
  - Se renombró `~/.cache/t3craft-coord/renders-approved` a `renders-approved.expired-20261109-old-session`.
  - Para renderizar hace falta una aprobación nueva de Facundo.
- **Handoff:** `docs/handoff-2026-11-09.md` no existe, ni en el árbol ni en ninguna rama.
  - La cola del coordinador anterior está en la VPS, en `~/.cache/t3craft-coord/night-queue.md` (brief común: `swarm4-common.md`).
  - El resumen está en `docs/ops/night-2026-11-09.md`.

### Rescate (todo subido a origin; no se borró nada)
| rama | qué había | qué se hizo |
|---|---|---|
| `feat/kit-blocks-art` | 21 texturas de dispositivos retocadas y `mod/tools/device_textures.py` (al integrarla, moverlo a `tools/art/` y ajustar su ruta de salida), todo sin commitear | Commit WIP `7e49837c` y push. Falta el railing (esquinas, T, extremos). Sin compilar. |
| `t3/paper-lantern-kit-1` | un merge local y 2 generadores de faroles sin seguimiento: borradores, porque main ya tiene las versiones de `kit/paper-lantern2` | Commit WIP `cb152d4e` y push. Probablemente se descarte. |
| `rescue/barrio-hall3-before-settle-dbg` | un print de depuración `SETTLE-DBG` en `CitadelSettle`, dentro del worktree base `/tmp/barrio-hall3-before` | Commit `066e04a2` y push. Es solo depuración: **no integrar**. |
| `struct/signposts` | 5 commits que solo estaban en local | Push. Seguía en curso al cortar. |
| `cat/statue-obelisk-braziers` | 1 commit que solo estaba en local | Push. Seguía en curso al cortar (tanda 1). |
| `cat/bridge-covered-timber` | 2 commits que solo estaban en local | Push. El coordinador anterior la canceló, porque los puentes no se colocan. |

Quedan como estaban:
- el `stash@{0}` del checkout de la VPS (su `RingCampusGenerator.java` ya está en el historial, en `6dcec3f3`);
- los worktrees de `/tmp` (`barrio-hall3-before` y `claude-light-base`), que ya no tienen nada sin subir;
- la caché `tools/studio/__pycache__`.

### Ramas en origin sin integrar en main
- **En curso al cortar** (tanda 1 de `night-queue.md`; se retoman desde `origin/<rama>`): `cat/gate-torii-cherry2`, `cat/gate-arch-battlement2`, `cat/lamp-brazier-tripod2`, `cat/tree-azalea-blossom2`, `struct/signposts` y `cat/statue-obelisk-braziers`.
  - No hay nota final del director registrada para ninguna, así que hay que leer su último commit y su changelog.
  - Sus rondas 1 (`cat/gate-torii-cherry`, `cat/gate-arch-battlement`, `cat/lamp-brazier-tripod`, `cat/tree-azalea-blossom`) quedan solo como base.
- **Reemplazadas por una ronda posterior que ya está en main:**
  - `struct/hub-statues` y `struct/hub-statues2`, porque main tiene `hub-statues3`;
  - `struct/leader-office5`, porque lo útil está en `leader-office6`;
  - `citadel/1-connect`, reemplazada por `fix/plan-connect`;
  - `citadel/scale`, reemplazada por `citadel/scale-port` y `struct/hq-scale`.
- **Descartadas a propósito** (ver "Sin integrar" en `docs/ops/night-2026-11-09.md`): `cat/bridge-stone-arch`, `cat/bridge-covered-timber`, `struct/avenue-lamps` 1–3 y `cat/planter-tall-pot`, que espera la decisión sobre `PLANTER_H`.
- **Exploraciones de estilo del 2026-11-08:** `design/{steampunk,gothic,skyislands,mesoamerican,dwarven,undersea,cyberpunk,nordic,arctic,treehouse,solarpunk,pirate,mediterranean,brutalist,orbital}` y `feat/hq-spacious-rooms`.

### Preparación del 2026-11-09 por la tarde (tareas 1 y 2)
- **GitHub Actions apagado del todo:**
  - permisos en `enabled:false`;
  - `.github/` borrado de main (`chore/remove-ci`);
  - 0 workflows, 0 corridas pendientes;
  - artifacts y caches en 0 (se borraron 264 artifacts y 111 caches, unos 8 GB de caché);
  - sin Pages, runners propios, Dependabot ni code scanning.

  Regla en `AGENTS.md`.
- **Layout del repo** (`chore/repo-layout`):
  - docs en `features/`, `plans/`, `ops/` e índice `docs/README.md`;
  - `mod/tools` → `tools/art`;
  - 257 PNG fuera del árbol (tag `archive/research-renders-2026-11-09`).
- **Ramas:**
  - se borraron 308 ramas remotas ya integradas; en origin quedan 40 sin integrar más `main`;
  - en la VPS se quitaron 39 worktrees limpios e integrados y las ramas locales bajaron de 564 a 41;
  - en la Mac quedó solo `main`.
- **PC:**
  - main compila (`2bca8f4b`, `build hqrenderClasses uirenderClasses`, 18 s).
  - **El daemon de render quedó parado:** tenía el lock pesado global tomado y no hay sesión de renders. Se levanta con `tools/render/start-daemon.sh` y en el arranque recompila el baseline.

### Aprobado por Facundo (2026-11-09, noche)
- **Plan de limpieza** (`docs/plans/code-cleanup.md`) con las decisiones D1–D5. Hay un issue por fase: #2 (fase 0), #3 (1), #4 (2), #5 (3), #6 (4), #7 (5) y #8 (6). La investigación de T3 Connect es #1.
- **D4, alcance de `hqRender` en el PC, mientras dure la limpieza:**
  - lo corre **solo el coordinador** y **solo para reportes del plan**;
  - la salida es texto, con la firma del plan antes y después;
  - uno a la vez, con `tools/heavy.sh`;
  - **no se generan ni se traen PNG**.

  Todo otro render sigue necesitando una sesión aprobada (regla 5 de `AGENTS.md`).
- **Limpieza de ramas:** se conserva solo lo activo en origin, la VPS, el PC y la Mac.
  - Lo integrado se borra.
  - Lo que no está integrado y no está activo se archiva con el tag `archive/<rama>` y se borra.
  - Lo activo:
    - las 6 rondas en pausa;
    - `feat/kit-blocks-art`;
    - `cat/planter-tall-pot`, `cat/bridge-stone-arch` y `cat/bridge-covered-timber`, que esperan decisiones de diseño.
- **Otras aprobaciones:**
  - borrar los worktrees de jobs del PC;
  - la desinstalación de herramientas pesadas en la VPS;
  - que el relay traiga solo reportes, sin PNG.

### Migración a repo público (2026-11-09, noche): hecha
- **Repo nuevo:** `Facundo-Barbera/t3craft`, público.
  - El historial está reescrito y saneado: autor único con noreply y sin datos personales ni de terceros. Se conserva `gradle-wrapper.jar`.
  - Secret scanning y push protection activos. Actions apagado.
- **Repo viejo:** `t3craft-private-archive`, privado y archivado. Conserva los tags `archive/*` y el historial de Actions.
- **Issues:** los 20 están en el repo nuevo con la misma numeración. #2 y #9 se recrearon limpios para no publicar el historial de ediciones; los originales quedan en el archivo.
- **Clones nuevos:** VPS, PC y Mac, con hook `pre-push` de guardia. El `origin` del PC es el clon de la VPS.
- **Variables de entorno:** los valores reales de host viven en variables (`T3CRAFT_VPS_HOSTNAME`, `T3CRAFT_RENDER_HOST`; perfil de la VPS y `~/.zshenv` de la Mac), porque los valores por defecto del repo ahora son placeholders.

### Ejecución (2026-11-09, noche)
- **Fase 0 (#2, cerrado):**
  - el proyecto t3craft está registrado en el T3 de la VPS;
  - convenciones en `docs/dev.md`, regla de issues en `AGENTS.md`;
  - `tools/refactor/{moves.tsv,apply-moves.sh}`;
  - `.gitattributes` con `merge=union` para el changelog.
- **Fase 1 (#3, cerrado):** 1a, 1b, 1c y 1d integrados, cada uno compilado en el PC en su SHA exacto. Build limpio final de main (`bfb0f6e6`): `BUILD SUCCESSFUL`.
- **#1:** informe en `docs/plans/t3-connect.md` y resumen comentado en el issue. Sigue abierto hasta que decida Facundo.
- **VPS:** desinstalados el JDK, `~/.gradle`, los build dirs y los PNG de los cachés de render/coord. Hay stubs `java`/`javac`/`gradle` en `~/.local/bin`. El relay ya no trae PNG.
- **PC:** se borraron los 173 worktrees de jobs. **El daemon de render quedó parado:** se levanta con `tools/render/start-daemon.sh`.
- **Ramas:** en origin quedan main, las 10 activas y `research/game-ui-integrations` (hilo de Facundo). Hay 32 tags `archive/*`.
- **Límite de T3:** `delegate_task` contra un entorno vinculado no admite `isolation: worktree`, así que el hijo trabaja en el checkout compartido del proyecto.
  - Los hijos de implementación van **de a uno**: cada uno hace checkout de su rama, push, y vuelve a main.
  - Los hijos de solo lectura pueden ir en paralelo si leen con `git show origin/main:` y escriben en `/tmp`.

### Propuestas pendientes de OK (versión anterior; ver arriba lo aprobado)
- **Las 40 ramas sin integrar.** Archivar quiere decir: tag `archive/<rama>` subido, después se borran la rama y su worktree en la VPS.
  - **Se quedan, en pausa:** `cat/gate-torii-cherry2`, `cat/gate-arch-battlement2`, `cat/lamp-brazier-tripod2`, `cat/tree-azalea-blossom2`, `struct/signposts`, `cat/statue-obelisk-braziers` y `feat/kit-blocks-art`. Sus rondas 1 (`cat/gate-torii-cherry`, `cat/gate-arch-battlement`, `cat/lamp-brazier-tripod`, `cat/tree-azalea-blossom`) se quedan hasta cerrar la ronda 2.
  - **Se quedan hasta que Facundo decida:** `cat/planter-tall-pot` (`PLANTER_H`) y `cat/bridge-stone-arch` / `cat/bridge-covered-timber` (puentes).
  - **Se archivan:**
    - reemplazadas: `struct/hub-statues`, `struct/hub-statues2`, `struct/leader-office5`, `citadel/1-connect`, `citadel/scale`;
    - descartadas: `struct/avenue-lamps`, `struct/avenue-lamps2`, `struct/avenue-lamps3`;
    - las 15 exploraciones `design/*` del 2026-11-08 y `feat/hq-spacious-rooms`;
    - rescates sin valor: `t3/paper-lantern-kit-1`, `rescue/barrio-hall3-before-settle-dbg`.
- **Worktrees de jobs en el PC** (`~/t3craft-jobs`): son 173 worktrees y ocupan 30 GB. Están todos sucios, porque el relay desempaca encima el `mod/src` de cada agente, y su contenido son copias de ramas que ya están subidas. Propuesta: borrarlos; el relay los vuelve a crear cuando hacen falta.
- **Relay y regla 9:** que el relay deje de copiar PNG a la VPS y traiga solo reportes, o miniaturas generadas en el PC. Está en pausa junto con los renders.

### `feat/ingame-preview-drop`: escrita sin compilar ni jugar (pendiente de ejecución)
- Carpeta `config/t3craft/preview-drop/`, recinto + portal en el mundo HQ; doc en `docs/features/ingame-preview-drop.md`.
- Falta: compilar en el PC (`tools/heavy.sh`), y probar en el cliente de Facundo: `/t3craft drop portal`, soltar un `.nbt` y un `.schem`, cruzar el arco, editar y borrar un archivo dentro del recinto, y un archivo roto (camino de error). Línea RESTART en `docs/CHANGELOG-dev.md`.
- No toca `feat/hq-living-fixes` (solo archivos nuevos más una línea en `T3CraftClient`).

### `fix/hq-load-build-portal`: escrita sin compilar ni jugar (pendiente de ejecución)
- Tres arreglos (ver `docs/features/fix-hq-load-build-portal.md`): plan del HQ vacío calculado una vez fuera del hilo del servidor (el cuelgue al 16 %), el build radial espera chunks no cargados en vez de descartar bloques, y el portal del preview-drop fijo en el HQ (sin `/t3craft drop portal`).
- Falta: compilar en el PC (`tools/heavy.sh`) y probar en el cliente de Facundo: entrar al HQ (¿ya no se queda al 16 %?), construir caminando hasta el borde y volver (¿se completa?), y ver el arco al norte de la calle interior y cruzarlo en ambos sentidos. Línea RESTART en `docs/CHANGELOG-dev.md`.

### `feat/piece-catalog-preview`: escrita sin compilar ni jugar (pendiente de ejecución)
- Catálogo de piezas en el juego (`/t3craft piece ...`), cada pieza a escala real en su propio escenario del mundo HQ; doc en `docs/features/piece-catalog-preview.md`. Ramas base: `fix/hq-load-build-portal` (`822cc454`).
- Falta: compilar en el PC (`tools/heavy.sh`) y probar en el cliente de Facundo: `/t3craft piece show hq_hub` (¿cabe el edificio central, cuánto tarda, el vestíbulo sube antes del teletransporte?), `next`/`prev` por `house`, `front e`, `size 12x12x14`, `reload` tras un hot swap, `clear` y el arco de vuelta. Línea RESTART en `docs/CHANGELOG-dev.md`.
- No toca el preview-drop (carpeta, recinto y portal siguen como están): solo archivos nuevos más una línea en `T3CraftClient`.

### `feat/algo-map-generators`: escrita sin compilar ni jugar (pendiente de ejecución)
- Interfaz `dev.t3craft.mapgen` (generador, huecos, piezas), el HQ descrito en ese modelo (`hq.CitadelMapGenerator`, sin tocar el pintor) y el mapa nuevo de islas flotantes con puentes en tiempo real; doc en `docs/features/algo-map-generators.md`.
- Falta: compilar en el PC (`tools/heavy.sh`: `compileJava compileClientJava`) y probar `/t3craft islands`: isla de aparición lista antes de entrar, puente que crece al acercarse a un borde, islas lejanas que se construyen al andar, caída al vacío con rescate. Línea RESTART en `docs/CHANGELOG-dev.md` (nuevo entrypoint cliente).
- Toca `VanillaDemoWorld` (ahora público), `HqStyle`/`RingCampusStyle` (un método nuevo) y `fabric.mod.json`; no cambia el mundo HQ.

### Siguiente, en orden
1. **Aprobación de Facundo:**
   - plan de limpieza de código (`docs/plans/code-cleanup.md`, decisiones D1–D5);
   - lista de desinstalación de la VPS;
   - las propuestas de arriba.
2. **Ejecución de la limpieza:** hijos en la VPS con `delegate_task` contra `vps-env`, en Sonnet, como máximo 8, por fases.
3. **En pausa hasta nuevo aviso:** las rondas `cat/*` y `struct/*` cortadas, la sesión de renders y las miniaturas del relay.
4. **Decisiones de diseño abiertas:** las de `docs/ops/night-2026-11-09.md`, en "Decisiones que te tocan" (`PLANTER_H`, puentes, iluminación de avenidas, musgo).

## Historial (antes del 2026-11-09 17:30; puede estar desactualizado)
Esto se escribió durante la moratoria y a lo largo del día, y buena parte ya está integrada: ver `docs/ops/night-2026-11-09.md`.

## 0. Atajo: `citadel/next` (todo el catálogo junto, listo para una sola compilación)
La rama `citadel/next` ya junta, con los conflictos resueltos (unión de `CatalogEntries`, ordenada por id, y del changelog):
- las 16 ramas `cat/*` revisadas (49 variantes);
- `citadel/place-catalog`;
- `citadel/lead-office-hook`.

Al levantarse la moratoria:
1. Compilar `citadel/next` una vez, con el lock y `tools/heavy.sh`.
2. Corregir lo que diga el compilador. La revisión a mano dejó como riesgos los tipos de argumentos y las mezclas `int`/`long`/`double`.
3. Renderizar en el PC una galería por familia y la ciudad antes/después.
4. Recién entonces integrar a main.

Las ramas de la ola B (`cat/street-furniture` y `cat/well-annex`) se suman a `citadel/next` cuando terminen.

## 1. Compilar e integrar en main (cada uno: merge → compilación → main)
| rama | qué | estado del trabajo |
|---|---|---|
| `fix/plan-connect` | Conexiones de bardas, muros, paneles y escaleras para todos los layouts (`PlanConnect`) | Terminado por Opus, sin compilar por el coordinador |
| `fix/new-thread-hitch` | Detector de trabones (`/t3dev perf hitches`) y arreglos del trabón al llegar un hilo nuevo | Terminado, sin compilar |
| `citadel/lead-office-hook` | La ciudadela construye la oficina del coordinador en su hall (`CitadelBarrios.hall` → `CitadelLeadOffice.place`) | Hecho por el coordinador, sin compilar; puede chocar con `citadel/2-houses` en `CitadelBarrios` |
| `citadel/place-catalog` | Coloca las variantes del catálogo en lotes, lotes vacíos, calles, plazas y explanada (`CitadelPlacement`, param `catalog.enabled`) | Terminado (`2c442a9`), sin compilar ni renderizar; ya va dentro de `citadel/next` |
| `citadel/scale` | Ciudadela a 1.5×, configurable (`RINGCAMPUS_SCALE`) | WIP rescatado; faltaba la revisión independiente |

## 2. Ramas de diseño del swarm 1 (citadel/*)
`citadel/1-connect` (reemplazada por `fix/plan-connect`, descartar), `citadel/1-light`, `citadel/1-paths`, `citadel/2-hub`, `citadel/2-nodes`, `citadel/2-houses`, `citadel/2-roofs-yards`, `citadel/2-nature`, `citadel/2-office`. Algunas tienen commits WIP rescatados. Todas necesitan compilar y renderizar para revisar antes de integrar.

## 3. Variantes del catálogo, ola A (cat/*): WIP rescatado, sin compilar ni renderizar
house-timber, house-stone, house-japanese, house-terrace, house-workshop, house-tower, stall-market, fountain-tiered, fountain-pond, tree-cherry, tree-mixed, tree-garden, garden-zen, lamp-street, gate-arch, plaza-center.

**Para cada una:** compilar, galería `--catalog`, revisión del director de arte e integración. Requiere una sesión de render aprobada por Facundo (`~/.cache/t3craft-coord/renders-approved`).

## Arreglo del crash al abrir el HQ: integrado
`fix/void-null-layer`, commit del fix `c9b98ce`, merge en main `b3c2df1`. Compilado una vez con `tools/heavy.sh`. Para actualizar el juego en Windows, hacer pull de main (o de ese commit) y recompilar.

## Arreglo de Ctrl+V: integrado
`fix/paste-crash`: el commit del arreglo es `faf953e` y el merge en main es `6562e0e`. Compilado.
- En Windows ya no se usa el portapapeles AWT (se puede volver a activar con `-Dt3craft.awtClipboard=true`).
- La lectura del portapapeles y el pegado de imágenes quedan envueltos: un fallo muestra un error en el composer en vez de crashear.
- Si vuelve a crashear, hace falta el crash report (`crash-reports/` o `hs_err_pid*.log` en el directorio `run`).

## `citadel/next`: compila (2026-11-09)
Incluye el catálogo (60 variantes, con la ola B: bancas, jardineras, postes, pozos, abrevadero, invernadero, cobertizo y pérgola), la colocación, el gancho de la oficina y las 10 ramas del swarm 1 (sin `scale` ni `1-connect`).
- Compilado con `tools/heavy.sh`: hubo que renombrar un `record Tail` duplicado que venía de `fix/new-thread-hitch`.
- Falta: renders en el PC (por el relay, bloqueado por la clave SSH) y la integración a main.

## En curso (solo código)
- `feat/build-near-player`: **integrado en main** (rama `82597e6`), compilado y sin probar en juego. Base por capas (estimación: de ~7–9 min a ~10–20 s), arranque inmediato junto al jugador, prioridad por cercanía, 96 bloques por tick y hasta 8 builders. Pendiente: en supervivencia, las alas se siguen construyendo en orden de índice.

## Swarm de diseño con renders en el PC (ola 1, 2026-11-09)
Son 4 agentes Sonnet high, cada uno con un director de arte Opus. Trabajan sobre `citadel/next`, con renders solo en el PC (por el relay), y como mucho un render a la vez por agente. Reglas en `~/.cache/t3craft-coord/swarm3-common.md`.
- `design/houses-a`: casas de entramado, de piedra y japonesas.
- `design/houses-b`: casas en terraza, talleres y casas-torre.
- `design/nature-water`: árboles, jardines y fuentes.
- `design/street`: faroles, bancas, jardineras, postes, arcos, centros de plaza y mástiles.

Estado de la ola 1:
- houses-a, houses-b y street están integradas en `citadel/next`. Las tres agotaron su cupo de renders con varias variantes por debajo de 7, así que tienen segunda ronda (`design/houses-a2`, `design/houses-b2`, `design/street2`) con más renders: hasta 10 por familia y 12 para casas.
- nature-water también está integrada. Su segunda ronda va en dos partes: `design/trees2` (Opus, las 9 especies con copas en lóbulos) está en curso, y la de fuentes y jardines queda para cuando baje el número de agentes.
- **Integrado en main (2026-11-09):**
  - `fix/build-walkable-level` (`1f7bb12`): el piso se elige según la altura del jugador;
  - `feat/hq-spawn-start` (`61fe666`): spawn en el centro del HQ sobre un bloque, y la obra espera a que el jugador camine o pulse [Comenzar construcción];
  - renders en truecolor (`db92976`): la paleta de 256 colores es opcional y el relay ignora `--preview`.
- **En curso:**
  - `tools/render-quality`: AO, sombras suaves, texturas reales y filtro AA en el raycaster, con un render antes/después;
  - `design/wave2-a`: puestos, pozos y anexos;
  - `design/wave2-b`: estatuas, gazebos, puentes y remates;
  - `design/trees2`, `design/street3` y `design/citadel-terraces`.
  
  Casas A ronda 2: las 7 casas con nota ≥7, ya en `citadel/next`.
- **Más integrado en main:**
  - la ciudadela early (`d35e01d`);
  - los árboles de la ronda 2 (`cb12f2a`);
  - Ctrl+V en Windows sin imágenes de portapapeles por SDL (`31265a3`), porque crasheaba de forma nativa sin dejar reporte.
- **En curso:**
  - `fix/demo-reconcile-offthread`: el log del juego mostró 4,3 s congelado en `demo.reconcile` al arrancar la obra;
  - `design/walkability-audit`: escaleras que hay que saltar, decoraciones absurdas como la fogata rodeada de agua, y escala 150 vs 100;
  - `feat/kit-blocks-art`: railing con esquinas, T y extremos, retoque de las 42 texturas y variantes. Es kernel del daemon del PC, así que hay que reiniciarlo al integrarlo.
- **En cola:** tercera ronda de árboles, cuando haya cupo de agentes.
- **Pendiente del OK de Facundo:** instalar Chunky en el PC para los renders "bonitos" (requiere su aprobación).
- **Calidad de render (2026-11-09):** los jobs al PC salen por defecto en final con `--ss 3`; preview solo si se pide. El daemon del PC corre con 11 hilos y `-Xmx10g`. La GPU está pendiente de que Facundo decida (plan en la respuesta del coordinador): AO y sombras en CPU, o Chunky con GPU desde un export `.schem`.
- **Citadel Studio en el PC** se actualizó a `79be0f6`: ahora lista todos los agentes con jobs, del más reciente al más viejo.
- **Construcción:** `fix/build-walkable` **integrado en main**, compilado y pendiente de que Facundo haga la prueba de cruzar el mapa. Hace que el HQ real use el mismo plan radial único que el demo (hoy `HqConstruction` lanza un trabajo por parte, de abajo hacia arriba) y agrega un corredor de suelo por delante del jugador en su dirección de avance. Criterio de aceptación: cruzar el mapa en línea recta sin caerse.
- **Hueco del renderer:** hqRender dibuja los banners (`*_banner` y `*_wall_banner`) como cajas de madera. Por eso no se pueden juzgar en imagen. Pendiente: un modelo de banner en `BlockModels`.

Ola 2, pendiente:
- puestos, pozos, anexos, estatuas, gazebos, puentes y remates de torre;
- la ciudad completa antes y después, a escala 1.0 y 1.5.

## En curso (solo código, 2026-11-09)
- `feat/build-radial`: **integrado en main** (`f436217`) y compilado. Construye en anillos de 5 bloques alrededor del jugador; la base tarda ~1,7 min estimados. Quedan pendientes tres cosas:
  - en supervivencia no hay orden radial;
  - `HqConstruction` sigue mandando un trabajo por parte;
  - con 8 builders algunos adelantan el anillo siguiente.
- `fix/build-radial-tune` (solo código, sin compilar): el suelo crece desde los pies del jugador.
  - Primero la superficie (y una capa de soporte), como mucho 1 anillo por delante; el relleno oculto va 2 anillos por detrás.
  - Builders por tramos de muro: suben por hiladas y trepan sin caminar, a 10 bloques/tick, y ayudan en su anillo en vez de saltar al siguiente.
  - `fastFoundation` ahora está apagado por defecto.
  - Falta: compilar y probarlo en juego, sobre todo el anillo 0 cerrado, los muros y que el suelo siga al jugador en el void.
- `citadel/scale-port`: **dentro de `citadel/next`** (`3d03dd0`), que compila. La escala por defecto es 1.0. Con 1.5 los nodos y algunos detalles mantienen tamaño fijo, y hay que verlo en render.
- `fix/chat-send-agents-menu`: código listo, **sin compilar ni probar**.
  - el segundo mensaje ya no se pierde: se encola detrás del que está en vuelo (`ComposerState` outbox, "⏱ Queued");
  - menú de agentes con filtro Active/All, activos arriba y terminados abajo (plegados con Active).
  - Probar en juego: dos Enter seguidos en un hilo idle y en uno corriendo; dos mensajes en un hilo nuevo (draft).
- `cat/wave-c`: estatuas, gazebos, mástiles, puentes y remates de torre.
- `citadel/light-aesthetic`: luz visible solo como diseño (faroles con ritmo, linternas en aleros, apliques junto a puertas, candelabros centrados) y relleno invisible. Nada de bloques luminosos visibles "donde caigan".
- `feat/ui-hq-menu-lineage`:
  - en el menú de HQ, la ciudadela va arriba y el resto en una sección "Legacy";
  - el linaje sale del sidebar y queda solo en el menú de agentes del header, mostrando solo hijos idle o activos.

## Arreglo del lag al iniciar un hilo: integrado
`fix/new-thread-hitch` ya está en main (`a031a79`), compilado. Trae el detector de trabones: `/t3dev perf hitches`, con WARN en latest.log cuando un frame o tick pasa de ~50 ms. Si el lag sigue, ese log dice qué fue.

## Renders en el PC de Facundo (my-pc): FUNCIONA (2026-11-09)
- **Relay probado de punta a punta.** El job `prueba-relay` (`--catalog LAMP`) compiló en el PC, renderizó la galería con etiquetas y trajo PNGs y reporte a la VPS, con código de salida 0.
- **Transporte:** scripts por stdin (`ssh -T user@100.64.0.20 < script`) y archivos en tar+base64. No se usa rsync ni scp.
- **Aprobación de la sesión:** `~/.cache/t3craft-coord/renders-approved` en la VPS. Alcance: el swarm de diseño de la ciudadela.
## Revisiones a mano en curso (solo código)
- `review/wip-catalog`: terminada. Las 16 ramas `cat/*` parecen listas para compilar; informe en `docs/ops/reviews/wip-catalog.md`, ya en main.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/ops/reviews/wip-citadel.md`.

## 3b. Ola B (solo código, en curso)
- `cat/street-furniture`: bancas, jardineras y postes indicadores.
- `cat/well-annex`: pozos, abrevadero, invernadero, cobertizo y pérgola.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.

### `fix/builder-round-radius`: escrita sin compilar ni jugar (pendiente de ejecución)
- Radio redondo alrededor del jugador (mínimo 5 bloques, crece 4 al completar el disco, vuelve al mínimo al moverse), columnas en chunks sin cargar aparcadas en vez de bloquear todo, y constructores activos que crecen con el trabajo cercano (ver `docs/features/fix-builder-round-radius.md`).
- Falta: compilar en el PC (`tools/heavy.sh`) y probar en el cliente de Facundo: ¿los builders terminan el HQ?, ¿vuelven con el jugador al moverse?, ¿suben a 12 en el centro del HQ? (`/t3craft demo status` muestra `radius` y `builders k/n`). Línea RESTART en `docs/CHANGELOG-dev.md`.
