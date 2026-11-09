# feat/site: sitio físico en terreno aleatorio

Rama `feat/site` (sale de `main` @ `4687f93`, con `origin/main` mergeado). Paquetes: `dev.t3craft.site` (lógica pura, sin
mundo), `dev.t3craft.site.mc` (adaptador al `ServerLevel`) y `dev.t3craft.client.site` (comandos,
varita y preview). **Alcance:** elegir y evaluar el lugar (con reserva para crecer), preview,
nivelado/cimentación por módulo, snapshot exacto por ampliación, undo por ampliación y move con
módulos. El modo demo (superflat con origen fijo) no pasa por acá. Los constructores animados (`feat/builders`) y el diseño de la base
(`feat/hq-design`) quedan fuera; acá hay una interfaz mínima para enchufarlos.

## Diferencia con AgentCraft

AgentCraft esquiva el problema: usa un mundo superflat propio y construye en un origen fijo.
t3craft trabaja en el mundo normal de Facundo, así que cada sitio:
1. se elige (comando o varita) y se evalúa;
2. se previsualiza sin tocar nada;
3. se confirma con un token;
4. se guarda un snapshot durable y verificado;
5. recién entonces se construye.

Para no reescribir los cambios del jugador al reconstruir, se tomó de AgentCraft (`hq/Plan.java`,
`PlanStore.java`) la idea de aplicar el plan como un diff y guardar el plan aplicado. Solo se copió
literal el conjunto de flags de escritura; está atribuido en `THIRD_PARTY_NOTICES.md` y en la
cabecera de `LevelBlockWorld.java`.

## Uso (solo singleplayer, creativo o supervivencia)

| Comando | Qué hace |
|---|---|
| `/t3craft site here` | Previsualiza el sitio justo delante del jugador (fuera de su reserva), con la entrada mirando hacia él. |
| `/t3craft site pick` | Busca en un radio de 32 bloques el lugar válido más plano y cercano (base + reserva), y lo previsualiza. |
| `/t3craft site wand` | Arma la "varita": el próximo bloque al que se hace clic derecho (con cualquier ítem) pasa a ser el centro. El clic se consume en el cliente, así que no se coloca ni se usa nada. |
| `/t3craft site confirm <token>` | Construye el sitio previsualizado. |
| `/t3craft site cancel` | Descarta el preview o la confirmación pendiente. |
| `/t3craft site undo` → `undo confirm <token> [keep\|overwrite]` | Deshace la **última ampliación** (módulo) y restaura su caja exactamente como estaba antes de ella. Repetirlo deshace la anterior, hasta la base y el terreno original. |
| `/t3craft site move` → `move confirm <token> [keep\|overwrite]` | Restaura el lugar viejo (todas las ampliaciones, de la más nueva a la más vieja), construye la base en el último lugar previsualizado y deja los demás módulos pendientes para que los builders los reconstruyan. |
| `/t3craft site resume` | Termina un build, undo o move interrumpido. Es idempotente. |
| `/t3craft site status` | Muestra el sitio activo, su fase, la reserva, los módulos y los pendientes. |

Las respuestas traen botones clicables (`[confirm]`, `[keep]`, `[overwrite]`, `[cancel]`). La
preview usa partículas:
- contorno verde: se puede construir (en la preview, el contorno es toda la reserva);
- contorno rojo: rechazado;
- contorno azul: sitio existente;
- borde blanco: el pad;
- naranja: celdas que cambió el jugador.

**Multijugador:** el mod es solo de cliente. Sin servidor integrado, los comandos se niegan y no
tocan nada. Ningún servidor dedicado se modifica.
**Supervivencia:** por ahora no cuesta materiales, y lo que se corta no suelta drops (así nadie
gana recursos gratis; el undo los devuelve). Opción futura documentada abajo.

## HQ modular: base, envolvente y módulos (`SiteRequest`, `Rect`, `SiteModule`, `SiteFrame`)

El HQ crece por **módulos** (un piso o ala por proyecto). La selección pide un `SiteRequest`:
- `base`: el primer `SiteModule` (id, `spec` opaco de hq-design y `Rect` de su huella);
- `envelope`: la **envolvente de crecimiento**, la huella máxima que el HQ puede alcanzar.

Todo en coordenadas **relativas al plan** (`SiteFrame`): origen = centro del pad de la base (un
bloque arriba), frente hacia `-z`, eje `x = 0`; es la misma convención que `BuildPlan`. El plan
gira en sentido horario hasta que su frente mire a la entrada (`facing`). `Rotation.values()
[SiteFrame.quarterTurns(facing)]` gira los estados igual.

- `SiteRequest.SQUARE`: la base 15×15 sola, sin reserva (lo que había antes; lo usan los tests
  viejos).
- `SiteRequest.GROWABLE` (default de `SiteRuntime` hasta que hq-design defina la suya): base 15×15
  al frente y envolvente 31×31 que comparte el borde frontal (crece a los lados y hacia atrás).
- `SiteRuntime.setRequest(...)` la cambia para la **próxima** selección; un sitio existente guarda
  la suya en el journal.

## Geometría y evaluación (`SiteLimits`, `SiteEvaluator`, `Columns`)

- **Reserva** = envolvente + anillo de mezcla (*apron*) de 2 bloques, a toda la altura. Se evalúa
  **entera** al elegir, aunque solo se nivele la base.
- **Pad:** una sola altura para todos los módulos: la mediana de los topes de suelo de toda la
  envolvente (minimiza corte + relleno del HQ completo).
- **Caja de la base:** huella de la base + apron, desde justo debajo del suelo más bajo hasta 12
  bloques de *headroom* sobre el pad. Topes duros por caja (por módulo): `MAX_VOLUME` 49 152
  celdas y 9 chunks. La reserva puede tocar hasta 36 chunks (`MAX_RESERVE_CHUNKS`).
- **Se rechaza el sitio si, en la reserva:**
  - algún chunk no está cargado (nunca se cargan ni se generan chunks para evaluar);
  - hay agua o lava, en el anillo justo afuera (para que no entre fluido) o hasta 8 bloques bajo
    el suelo;
  - hay cualquier bloque *artificial*: todo lo que no se reconozca como suelo o vegetación
    natural (clasificación conservadora por tags vanilla en `LevelTerrain`). Esto excluye casas,
    aldeas y ruinas;
  - hay entidades que no son mobs salvajes: marcos, armor stands, vagonetas, botes, y mobs con
    nombre, domesticados o persistentes;
  - el relieve de la huella de la base supera 6 bloques, o el de la envolvente supera 10
    (`MAX_ENVELOPE_RELIEF`, "too uneven to grow");
  - alguna caja queda fuera de la altura de construcción o supera los límites.
- **Árboles y plantas** se permiten, pero suman costo. `pick` puntúa
  `corte + relleno (base) + 0.25·(corte + relleno de la envolvente) + 2·árboles + 4·relieve +
  relieve de la envolvente + 0.25·distancia`. Cada candidato mira hacia el jugador, y se saltean
  los que tocan la reserva del sitio actual.

## Nivelado y cimentación (`Leveling`)

Se nivela **un módulo a la vez**: la base al confirmar y cada módulo nuevo en su ampliación, todos
al mismo pad. Las columnas de pads de módulos ya construidos no se tocan (`Leveling.Keep`).

- **Huella del módulo:**
  - pad sólido (borde de stone bricks, interior de polished andesite);
  - debajo, donde el terreno es más bajo, cimiento: muro de contención de stone bricks en el borde
    y cobblestone adentro;
  - encima, todo despejado hasta el tope de la caja.
- **Apron:** cada columna se acerca al pad en ⅔ (anillo 1) o ⅓ (anillo 2) de la diferencia. Lleva
  grass arriba, dirt de relleno o corte a aire. Si la columna tiene troncos, se despeja hasta el
  tope de la caja.
- Las celdas que no hace falta cambiar quedan intactas (`null` en el plan).
- Después se superpone el diseño del módulo (`ModuleDesigner`) en la zona
  `huella × [pad+1, pad+12]`.

## Snapshot por ampliación (`Snapshot`, `SiteStore`, `Expansion`)

Cada ampliación (la base y cada módulo) guarda **su propio snapshot** de su caja justo antes de
escribir en ella. El journal guarda la pila (`stack`, de la más vieja a la más nueva). Las cajas
de módulos vecinos se solapan (apron, bordes), y no pasa nada: se restaura en orden LIFO y cada
snapshot ya contiene lo que dejaron las ampliaciones anteriores.



- **Qué guarda:** toda la caja, bloque por bloque (paleta de estados canónicos + índice por
  celda) y el NBT completo de cada block entity (`saveWithFullMetadata`). Se restaura con
  `loadWithComponents`, así que vuelven cofres con su contenido, carteles, etc.
- **Archivos** en `<mundo>/t3craft/site/`:
  - `snapshots/<key>.nbt`: NBT gzip, legible con herramientas vanilla (uno por ampliación);
  - `plans/<key>.nbt`: el plan aplicado a esa ampliación;
  - `state.nbt`: el journal / registro del sitio (v2, ver abajo);
  - `archive/`: snapshots ya restaurados. Nunca se borran.
- **Durable:** se escribe un temporal, se hace `fsync`, `rename` atómico y `fsync` del directorio
  (donde el SO lo permite). Después se **relee y se compara**: checksum SHA-256 del contenido
  canónico, con las claves NBT ordenadas. Si el snapshot no queda verificado, no se modifica
  ningún bloque. Un snapshot existente nunca se sobrescribe.
- **Validación al leer:** formato, versión, caja dentro de límites, índices de paleta y de block
  entity, y checksum. Un archivo dañado se rechaza; nunca se "adivina".

## Registro del sitio (`state.nbt`, `SiteJournal` v2)

Guarda la fase, la dimensión, el origen y la entrada, el `SiteRequest`, la caja de reserva y la
pila de `Expansion(key, SiteModule, box)`. Durante un undo o un move, guarda además el modo
(`keep`/`overwrite`), hasta qué altura de la pila restaurar (`downTo`) y el sitio siguiente
(`next`). También la lista de módulos pendientes de reconstruir después de un move (`pending`).
Los módulos se guardan como **id + spec + rect**, nunca como bloques crudos. Un `state.nbt` v1
(solo de builds de prueba previas al merge) se rechaza con un mensaje claro; no se adivina.

## Seguridad del flujo (`SiteService`)

- **Preview sin escrituras:** evalúa, arma el plan y captura un snapshot en memoria. El token
  vence a los 5 minutos y sirve una sola vez.
- **Confirm:** reevalúa y recaptura. Si algo cambió desde el preview (aunque sea una planta), se
  cancela sin tocar nada.
- **Cambios del jugador** (`Restorer`): una celda es *nuestra* si tiene el contenido original o lo
  que puso el plan. Cualquier otra cosa es un **conflicto**.
  - El builder nunca pisa un conflicto (lo cuenta como `skipped`).
  - Undo y move listan los conflictos y los marcan en naranja. Con conflictos, hay que elegir
    explícitamente `keep` (restaurar todo lo demás y dejar los cambios del jugador) u `overwrite`.
  - Si los conflictos cambian entre el reporte y la confirmación, la confirmación se rechaza.
- **Undo:** reporta los conflictos solo de la ampliación más nueva y restaura solo su caja.
- **Move:** primero guarda y verifica el snapshot de la base nueva. Si falla, no cambió nada.
  Después restaura todas las ampliaciones viejas (de la más nueva a la más vieja). Para cada
  ampliación, los conflictos se miden solo en celdas que ninguna ampliación más nueva cubre.
  Luego archiva cada una y construye la base nueva con el mismo `spec` de base. Los demás módulos
  quedan en `pending`. La reserva nueva no puede tocar la vieja.
- **Ampliación (`expand`):** el módulo tiene que caber en la envolvente, no solaparse con otro
  módulo y tener id nuevo. Su caja no puede tener fluidos, entidades ajenas ni bloques
  artificiales que no sean del sitio (una celda es del sitio si coincide con el plan de alguna
  ampliación anterior). Si algo falla, no se escribe nada.
- **Journal:** fases `NONE → BUILDING → BUILT → RESTORING`. Se escribe antes de cada fase y
  después de restaurar cada ampliación (primero se saca de la pila, después se archiva).
  - Al entrar al mundo se limpian los `.tmp` y se avisa si quedó algo interrumpido.
  - `resume` termina el trabajo. Build y restore son idempotentes: lo ya hecho se saltea.
  - Después de un restore se hace `saveAllChunks(flush)` antes de que el journal olvide el sitio,
    para que un crash no deje la base en disco sin registro.
- **Escrituras:** sin actualizaciones de vecinos ni de forma, sin drops ni efectos de block
  entity, sin `onPlace`. Así no hay física, fluidos ni contenedores que se vacían, y nada se
  escribe fuera de la caja.

## Interfaz para builders y hq-design (integración)

Todo corre en el **hilo del servidor integrado** (`server.execute(...)`). Desde el cliente:
`mc.getSingleplayerServer().execute(() -> ...)`.

### Qué consulta el hilo de builders

```java
SiteHandle h = SiteRuntime.handle(server);      // null si no hay sitio
h.origin();          // CellPlan.Origin: centro del pad de la base +1, facing = lado de la entrada
h.padY();            // altura común del pad
h.request();         // base + envelope (relativos al plan)
h.reserve();         // caja mundo de la reserva (envolvente + apron)
h.modules();         // módulos construidos, del más viejo al más nuevo (el primero es la base)
h.pending();         // módulos que un move dejó para reconstruir
h.fits(rect);        // cabe en la envolvente y no se solapa con otro módulo
h.footprint(rect);   // pad del módulo en coords mundo (una capa)
h.moduleArea(rect);  // huella × headroom: donde va la estructura del módulo
h.toWorld(dx,dy,dz); // relativo al plan → mundo (girado según la entrada)
```

### Construir un módulo nuevo (ampliación)

```java
SiteService.Reply r = SiteRuntime.expand(server, new SiteModule("project-t3craft", spec, rect));
```
`expand` valida el módulo, **toma y verifica el snapshot de su caja** (este es el hook
"snapshot antes de construir") y guarda el plan (nivelación + `ModuleDesigner.design(module)`).
Después escribe el journal y le pasa el plan al `SiteBuilder`. Si `r.ok()` es false, no se tocó
nada y `r.lines()` explica por qué. `site undo` lo deshace.

### Hooks (`SiteListener`, vía `SiteRuntime.addListener`)

| Hook | Cuándo |
|---|---|
| `beforeExpansion(site, expansion, snapshot, plan)` | El snapshot de `expansion.box()` ya es durable y está verificado, el plan está guardado y todavía no se escribió ningún bloque. Se llama para la base, para cada `expand` y al reanudar. |
| `built(site, expansion, report)` | El builder terminó (no se llama si se canceló). |
| `restored(site, expansion, result)` | Undo de una ampliación, o cada ampliación del sitio viejo en un move. |
| `moved(newSite, toRebuild)` | Move terminado: la base nueva está en obra y `toRebuild` lista los demás módulos (del más viejo al más nuevo), para llamar a `expand` con cada uno. |

`SiteRuntime.modulesToRebuild(server)` devuelve los mismos pendientes en cualquier momento (sobrevive
reinicios). Cada `expand` exitoso saca su id de la lista. Los `rect` son relativos al plan, así que
el mismo módulo cae en el mismo lugar relativo aunque el sitio nuevo mire hacia otro lado.

### Quién escribe los bloques (`SiteBuilder`)

`SiteBuilder.submit(CellPlan plan, Origin origin, CellGuard guard, Consumer<Report> done)`.
Contrato (javadoc en `SiteBuilder`):
- escribir solo las celdas no nulas;
- preguntar `guard.mayWrite(x,y,z)` justo antes de cada celda y saltearla si devuelve `false`
  (el jugador la cambió);
- ser idempotente;
- llamar a `done` una sola vez, en el hilo del servidor.

- Default: `PacedBuilder`, 8 bloques por tick, con el ritmo de `BuildCursor` de feat/builders.
  Escribe con los flags del sitio y suma el sonido y las partículas de colocación
  (`EffectsBlockWorld`, lo mismo que `ServerLevelSink`). `SiteRuntime` lo tickea en
  `END_SERVER_TICK`. Si el servidor se cierra, la obra queda en BUILDING y `resume` la termina.
- Respaldo: `SiteRuntime::instant` (`InstantBuilder`).
- Cambiarlo: `SiteRuntime.setBuilderFactory(level -> ...)`.
- Puente: `BuildPlanBridge.toBuildPlan(cellPlan, levelBlockWorld)` da el `BuildPlan` relativo a
  `box.min` (`Rotation.NONE`) para animarlo.
- **Propuesta para feat/builders:** para usar la cuadrilla animada (`BuildService.build`) sin
  perder el guard, hace falta que `BuildService` acepte un envoltorio del `BlockSink` (por
  ejemplo `build(..., UnaryOperator<BlockSink> wrap)`). Con eso, el adaptador `SiteBuilder` →
  cuadrilla es: sink que devuelve `SKIPPED` si `!guard.mayWrite`, más un `BuildListener` que llama
  a `done` al terminar. Sin ese cambio, la cuadrilla podría pisar bloques que el jugador pone
  durante la obra, así que no se enchufó.

### Diseño de los módulos (`ModuleDesigner`, hq-design)

- `SiteRuntime.setDesigner((module, area, origin) -> CellPlan)`: el plan cubre `area`
  (huella × headroom) en coordenadas mundo. `origin` es el del **sitio**. Tiene que ser
  determinista por `module.spec()`, porque un move reconstruye desde el spec. Sin block entities.
- Con `BuildPlan`: `SiteRuntime.setDesigner(BuildPlanBridge.designer(module -> buildPlan))`, con el
  `BuildPlan` en coordenadas relativas al plan del sitio (y = 0 es la primera capa sobre el pad,
  frente `-z`). El puente gira posiciones y estados, recorta al área y descarta block entities
  (con un warning).
- `SiteRuntime.setStructure(StructureSource)` sigue andando: usa la misma estructura para todos
  los módulos. Default: `ModuleDesigner.PLACEHOLDER` (postes en las esquinas y faroles).
- `SiteRuntime.setRequest(new SiteRequest(base, envelope))`: base y envolvente del HQ.

## Conflictos potenciales con otras ramas

- `fabric.mod.json`: se agrega la entrada `client` `dev.t3craft.client.site.SiteClient` a la
  lista (ya unida con las de builders, config y devx al mergear main).
- `/t3craft site` se registra aparte con `literal("t3craft").then(literal("site"))`. Brigadier
  fusiona los nodos, así que no se tocó `T3CraftCommands`. Si otra rama registra también un `site`
  bajo `t3craft`, va a chocar.
- `THIRD_PARTY_NOTICES.md`: la entrada de `LevelBlockWorld` está en la misma lista que los
  archivos de npc-behavior (ya unidos al mergear main).
- Si `feat/builders` mueve entidades NPC dentro de la caja, a `LevelTerrain.foreignEntities` no le
  importan los NPC solo-cliente (no existen en el servidor). Si esa rama crea entidades de
  servidor con nombre, una nueva evaluación las va a ver como "entities in the way" (correcto: el
  sitio activo ya se excluye).

## Limitaciones conocidas

- La reserva se valida al elegir. Si después el jugador construye adentro, `expand` lo detecta y
  rechaza ese módulo, pero no hay otra protección.
- Un sitio existente conserva su `SiteRequest`. Si hq-design cambia la envolvente, solo vale para
  sitios nuevos (o después de un move).
- No hay comando para ampliar a mano: las ampliaciones son API para los builders.

- Las entidades (mobs, marcos, armor stands) no entran en el snapshot. Por eso la selección
  rechaza cajas con entidades que no son mobs salvajes, y los mobs salvajes que estén dentro no se
  restauran.
- Las hojas que quedan por encima del tope de la caja pueden quedar flotando si se cortó su
  tronco dentro de la caja; el undo restaura el tronco.
- El `InstantBuilder` puede encerrar al jugador si está parado donde va un bloque sólido (el
  placeholder solo pone esquinas). Los builders animados deberían posponer las celdas ocupadas.
- Cambios en block entities "vivos" (un horno que cocina) cuentan como conflicto. La selección
  rechaza cajas con block entities, así que en la práctica no aparecen salvo que el jugador los
  ponga después.
- Si la partida se publica en LAN, se sigue permitiendo (solo el dueño puede ejecutar los
  comandos), pero otros jugadores podrían estar dentro de la caja.
- La varita es un modo armado por comando, no un ítem registrado: registrar un ítem exige un mod
  en el lado común/servidor.

## Opción futura: costo de materiales en supervivencia

`CellPlan.count()` por estado da la lista de materiales. Habría que agregar una opción
`survivalCost: none|inventory|chest`. En modo `inventory`, el confirm verifica y descuenta del
inventario del jugador. En modo `chest`, descuenta de un cofre "almacén" junto al sitio y el
builder pausa si falta algo. El undo devolvería los materiales de los bloques propios restaurados
(los bloques cortados del terreno seguirían sin drops). No implementado.

## Pruebas

`mod/src/test/java/dev/t3craft/site/`, con un `FakeWorld` en memoria. Los tests usan las clases
NBT reales de Minecraft, sin bootstrap.
- **`SnapshotTest`:** captura y restauración exactas (incluido un cofre); ida y vuelta NBT con
  detección de manipulación; escritura durable; conflictos, `keep`/`overwrite` e idempotencia;
  ida y vuelta del plan y del journal v2 (pila, `next`, `pending`; v1 rechazado).
- **`SelectionTest`:** pendientes, colinas, agua, lava, construcciones, marcos, árboles, chunks no
  cargados, `pick` y nivelado (huella fija).
- **`SiteServiceTest`:** flujo preview/confirm/undo/move/resume con la huella fija.
- **`ModularSiteTest`:**
  - la reserva rechaza agua y construcciones dentro de la envolvente pero fuera de la base, y
    pendientes fuertes al fondo (con `SQUARE` el mismo lugar es válido);
  - la reserva gira con la entrada;
  - solo se nivela la base;
  - `SiteHandle` (reserva, huellas, `fits`);
  - cada ampliación tiene su snapshot y undo vuelve paso a paso al terreno original, comparando
    el área entera en cada paso;
  - undo de un módulo solo pregunta por cambios en su caja;
  - las ampliaciones inválidas (duplicadas, solapadas, fuera de la envolvente, con bloques del
    jugador o agua) no escriben nada;
  - una ampliación interrumpida se reanuda y se deshace;
  - move restaura todo, devuelve `[project-a, project-b]`, los reconstruye girados en el sitio
    nuevo y se deshace módulo a módulo;
  - move dentro de la reserva vieja se rechaza;
  - un move interrumpido se termina con `resume`;
  - orden de los hooks.
- **`PacedBuilderTest`:** ritmo por tick de abajo hacia arriba, guard justo antes de cada celda,
  reconstrucción sin reescribir, cancel con un único reporte, giros de `SiteFrame` y validación
  de `SiteRequest` y `SiteModule`.

Sin probar en juego (este entorno no corre el cliente; no se levantaron dev servers): el
adaptador `LevelBlockWorld`/`LevelTerrain` y la UI de cliente solo compilan contra 26.3.
Verificación manual sugerida en un mundo de prueba, nunca el real:
1. `pick` → `confirm`;
2. romper o poner bloques;
3. `undo` (ver los conflictos en naranja) → `keep`;
4. otro `pick` → `move`;
5. cerrar el juego a mitad de un build → entrar → `resume`.

## Build

`cd mod && JAVA_HOME=$HOME/.local/jdks/jdk-25.0.5+8 ./gradlew build` (JDK 25). Verde en la rama
con main mergeado (incluye demo-world y portable): 274 tests, 0 fallos.
