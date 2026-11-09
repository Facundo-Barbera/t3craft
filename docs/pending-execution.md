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
  - El resumen está en `docs/night-2026-11-09.md`.

### Rescate (todo subido a origin; no se borró nada)
| rama | qué había | qué se hizo |
|---|---|---|
| `feat/kit-blocks-art` | 21 texturas de dispositivos retocadas y `mod/tools/device_textures.py`, todo sin commitear | Commit WIP `7e49837c` y push. Falta el railing (esquinas, T, extremos). Sin compilar. |
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
- **Descartadas a propósito** (ver "Sin integrar" en `docs/night-2026-11-09.md`): `cat/bridge-stone-arch`, `cat/bridge-covered-timber`, `struct/avenue-lamps` 1–3 y `cat/planter-tall-pot`, que espera la decisión sobre `PLANTER_H`.
- **Exploraciones de estilo del 2026-11-08:** `design/{steampunk,gothic,skyislands,mesoamerican,dwarven,undersea,cyberpunk,nordic,arctic,treehouse,solarpunk,pirate,mediterranean,brutalist,orbital}` y `feat/hq-spacious-rooms`.

### Siguiente, en orden
1. **Probar la cadena desde la Mac mini:** compilar main en el PC con `tools/heavy.sh`, con un script por stdin.
   - El checkout del PC está en `df0d483b` y le falta un fetch.
   - El PC tiene 15 GB y el daemon de render corre con `-Xmx10g`. Con ~9 GB libres, apenas pasa el umbral de 8 GB de `heavy.sh`.
   - Hay que mirar si el daemon tiene tomado el lock global mientras está inactivo.
2. **Previews chicas en el relay:** que el PC genere copias de ≤ 768 px de cada PNG y que el relay traiga solo esas a la VPS (regla 9). Hasta entonces, los hilos no abren ningún PNG.
3. **Sesión de renders nueva**, solo si Facundo la aprueba.
4. **Cerrar las rondas en curso** con un hilo hijo por rama y seguir con `night-queue.md`.
5. **Decisiones de Facundo:** siguen abiertas las de `docs/night-2026-11-09.md` ("Decisiones que te tocan": `PLANTER_H`, puentes, iluminación de avenidas, musgo).

## Historial (antes del 2026-11-09 17:30; puede estar desactualizado)
Esto se escribió durante la moratoria y a lo largo del día, y buena parte ya está integrada: ver `docs/night-2026-11-09.md`.

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
- `review/wip-catalog`: terminada. Las 16 ramas `cat/*` parecen listas para compilar; informe en `docs/reviews/wip-catalog.md`, ya en main.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/reviews/wip-citadel.md`.

## 3b. Ola B (solo código, en curso)
- `cat/street-furniture`: bancas, jardineras y postes indicadores.
- `cat/well-annex`: pozos, abrevadero, invernadero, cobertizo y pérgola.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.
