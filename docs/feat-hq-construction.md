# feat/hq-construction: los builders levantan el HQ en el sitio

Ítem 8 de `docs/research/inventario.md` ("hilo E"). Une `feat/hq-design` (el HQ como datos),
`feat/site` (sitio, snapshot, undo, move) y `feat/builders` (cuadrilla animada).

Paquetes:
- `dev.t3craft.hq.construct`: lógica pura y testeable (`HqSiteFrame`, `HqTarget`, `HqConstruction`, `HqCost`, `HqStore`).
- `dev.t3craft.hq.construct.mc`: pegamento con el servidor integrado (`HqRuntime`, `HqWriter`, `PacedHqWriter`).
- `dev.t3craft.client.hq`: entrypoint, cuadrilla y conexión con NPCs y workstations (`HqConstructionClient`, `CrewBridge`, `HqPresence`, `HqWorldAnchors`, `HqStations`).

## Flujo

1. `/t3craft site pick | here` → `confirm`. La petición del sitio ya es la del HQ
   (`HqSiteFrame.request()`): la base es hub + pórtico + sótano del archivo, la envolvente tiene lugar
   para 4 alas y la base pide 5 capas bajo el pad para el sótano. El diseñador del sitio corta cada
   módulo del plan actual del HQ, así que **confirmar ya construye el hub** (automático).
2. `HqConstruction` (servidor, 1 vez por segundo) toma el roster vivo: los NPC que se muestran, o
   sea, sin subagentes (ya filtrados por `Visibility`). Solo usa un roster cuando todos los entornos
   están en `ok`, para no demoler alas por un roster a medio cargar. Tiene un debounce de 3 s.
3. Calcula el objetivo (`HqTarget`: `HqLayout.allocate` con el layout aplicado, de modo que los
   escritorios no se mueven). Luego da un paso por vez:
   - **Ampliaciones**: primero los módulos `pending` de un move, después cada slot de la grilla que
     falte hasta el índice de módulo más alto. Cada ala es **una ampliación de site**
     (`SiteRuntime.expand`): site guarda y verifica el snapshot de su caja *antes* de escribir, así
     que `site undo` la deshace y `site move` la devuelve como pendiente.
   - **Cambios in-place** en módulos ya construidos (escritorio liberado u ocupado, computadora al
     archivo, ala vaciada, texto de carteles): `SiteService.claim` filtra las celdas que siguen
     siendo del sitio, registra el nuevo estado en el plan guardado de cada ampliación que las cubre
     (para que undo no las vea como cambios del jugador) y la cuadrilla las escribe.
   - Al terminar, el layout pasa a ser el aplicado y se guarda en `<mundo>/t3craft/hq/construction.json`.
4. **Idempotente**: la misma lista de hilos (en cualquier orden) da el mismo objetivo. Si ya está
   aplicado, no se construye nada.

### Geometría (`HqFootprints`, `HqSiteFrame`)

- Coordenadas: `sitio = (-x, y, -z)` del HQ (media vuelta: el frente del sitio, `-z`, es la entrada
  del HQ). El origen del HQ (centro del piso del hub) es el origen del sitio. El piso apoya sobre el
  pad y el sótano entra en él.
- Módulos de site: `base` y `wing-0..3`, rectángulos que no se solapan. El slot par de cada fila
  también es dueño de su tramo de la espina, y cada fila incluye la línea donde va la tapa de la
  espina. Un test verifica que toda colocación de cualquier plan de ≤ 4 módulos cae en un slot construido.
- Capacidad: `SiteRequest.MAX_ENVELOPE` (64) permite 2 filas = **4 alas = 24 escritorios**. Los hilos
  que no entran quedan en `unseated` y esperan en el hub hasta que se libere un escritorio
  (`/t3craft hq status` lo muestra).

### NPCs y workstations (`HqPresence`)

En cuanto hay un HQ en pie (site o demo) se registran:
- `behavior().setAnchorSource(new HqWorldAnchors(...))`: **ningún NPC cae al anillo**. Un hilo con
  escritorio en un ala construida va a su escritorio. Un hilo con settle va a su computadora del
  archivo. Los demás (ala todavía en obra, HQ lleno, id desconocido) esperan en el hub, en los
  puntos de paseo y los bancos. `home`, `rest`, `board` y `boardSlots` siempre devuelven algo.
- `WorkstationClient.setPlacementSource(new HqStations(...))`: la pantalla de cada hilo va sobre el
  `computerPos` de su escritorio, o sobre su computadora del archivo. Sin computadora en el edificio
  no hay estación (nunca vuelve al pasto).

Sin HQ, las dos fuentes se ponen en `null` (vuelve el fallback de otras ramas). Si hay sitio, gana
sobre el demo.

### Eventos de site
- `built` → el siguiente paso. Las anclas se actualizan por módulo terminado.
- `restored` (undo) → la construcción **queda en espera**, así el undo no se reconstruye solo.
  `/t3craft hq resume` la reanuda.
- `moved` → se levanta la espera y se reconstruyen los pendientes en el sitio nuevo.
- `site resume`: mientras el sitio no esté `BUILT` e inactivo, el HQ espera (`waiting site`).

### Supervivencia
Antes de cada ampliación y de cada actualización in-place se consulta `Survival.rules()`
(`buildInWorld` y `materials`) con el dueño del mundo. `HqCost.bill` arma la `MaterialBill`: no
cuenta el aire y cuenta puertas y camas una sola vez. Con `PLAYER_INVENTORY`, si falta algo, el paso
queda bloqueado y se muestra qué falta. Si el inventario alcanza, se avisa y se construye. Esta rama
**no descuenta** nada: lo hace `feat/builders-survival`. El nivelado del sitio (pad, cimiento) no se
cobra.

### Cuadrilla (`CrewBridge`)
- Las ampliaciones usan `SiteRuntime.setBuilderFactory(CrewBridge.siteBuilder())`.
- Los cambios in-place usan `HqRuntime.setWriter(CrewBridge.writer(...))`.

Las dos usan `Builders.service().build(..., wrap)`: el guard de site (no pisar cambios del jugador)
va dentro del sink. Si no hay cuadrilla posible (otra dimensión, sin servicio), lo hacen el
`PacedBuilder` de site o el `PacedHqWriter`, a 8 bloques por tick.

## Demo
El HQ del demo es el de `feat/demo-hq` (`DemoHq`/`DemoHqSource`, en main). Esta rama solo cambió
cómo se engancha: `DemoClient.installAnchors` ahora pasa por `HqPresence.demo(...)`
(`DemoHqSource.standing(origin)`). Así el demo también usa `HqWorldAnchors`, sin caer al anillo,
y las workstations quedan sobre las computadoras del plan.

## Paleta (blocks-core)
El objetivo se genera con `HqPlans.generate`, que aplica `HqFixtures` cuando el módulo de bloques
propios está activo. Si no, el HQ es vanilla. Los datos de block entity (bindings, carteles) se
escriben en el paso in-place.

## Comandos
| Comando | Qué hace |
|---|---|
| `/t3craft hq` / `hq status` | Fase, alas (proyecto, entorno, escritorios), archivo, hilos sin escritorio, bloqueos. |
| `/t3craft hq build` | Saltea el debounce y reintenta un paso bloqueado. |
| `/t3craft hq pause` / `hq resume` | Pausa o reanuda la construcción y las obras en curso. `resume` también levanta la espera tras un undo. |

## Cambios fuera del paquete (estructurales)
- `fabric.mod.json`: nueva entrada `client` `dev.t3craft.client.hq.HqConstructionClient` (después de `SiteClient`). → **RESTART**.
- `site`:
  - `SiteRequest.baseDepth` (aditivo, persistido como `baseDepth`, ausente = 0, con constructor de 2 argumentos intacto);
  - el evaluador y el diseño de la base bajan esa profundidad;
  - `SiteService.claim(...)` y `busy()` nuevos.
- `build`: `BuildService.build(..., UnaryOperator<BlockSink> wrap)` (método default) con `Billing` y `BuildManager.submit(..., cost, wrap)`. `BuildersClient` lo implementa junto con la facturación de builders-survival (la cuadrilla del HQ usa `Billing.POLICY`, así que en supervivencia los bloques se cobran al colocarlos). `BuildJob` no se tocó.
- `DemoClient` / `DemoHqSource` (de feat/demo-hq): anclas del demo vía `HqPresence` (más workstations).
- `hq`: `HqFootprints`, una vista pública de la geometría.

## Tests (`mod/src/test/java/dev/t3craft/hq/construct/`)
- `HqConstructionTest` (con fakes):
  - orden (base → wing-0 → wing-1 → in-place);
  - idempotencia (roster reordenado, instancia nueva desde lo guardado);
  - anexo con snapshot antes de escribir;
  - veredicto de supervivencia que bloquea y `hq build`;
  - settle → archivo (desk liberado, computadora del archivo escrita);
  - move → pendientes reconstruidos en el sitio nuevo;
  - sitio interrumpido → espera → `resume`;
  - undo → espera → `hq resume`;
  - sin sitio, sin anclas.
- `HqSiteFrameTest`: petición válida y round-trip NBT, slots disjuntos dentro de la envolvente y del presupuesto de chunks, toda colocación en un slot construido (historia aleatoria), `HqSite` igual a `SiteFrame` en los 4 giros, capacidad.
- `HqWorldAnchorsTest`: todo NPC tiene lugar dentro del HQ (escritorio, archivo u hub); las estaciones van sobre los `computerPos`.

## Pendientes
- Sin probar en juego (no se lanzó Minecraft).
- Las celdas de carteles con texto cuentan como "cambio del jugador" en un undo (site no guarda block entities en sus planes): undo pide keep/overwrite.
- Un ala vaciada se "demuele" in-place (aire, pasto a nivel del piso) y su ampliación de site sigue en la pila. Se vuelve a usar si un proyecto ocupa ese índice.
- Más de 4 alas exige subir `SiteRequest.MAX_ENVELOPE` (decisión de site).
- Si un crash ocurre entre `claim` y la escritura, la celda queda registrada con el estado nuevo: un undo la reporta como conflicto.
