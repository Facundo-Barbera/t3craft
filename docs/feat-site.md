# feat/site: sitio físico en terreno aleatorio

Rama `feat/site` (sale de `main` @ `4687f93`). Paquetes: `dev.t3craft.site` (lógica pura, sin
mundo), `dev.t3craft.site.mc` (adaptador al `ServerLevel`) y `dev.t3craft.client.site` (comandos,
varita y preview). **Alcance:** elegir y evaluar el lugar, preview, nivelado/cimentación, snapshot
exacto, undo y move. Los constructores animados (`feat/builders`) y el diseño de la base
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
| `/t3craft site here` | Previsualiza el sitio justo delante del jugador, con la entrada mirando hacia él. |
| `/t3craft site pick` | Busca en un radio de 32 bloques el lugar válido más plano y cercano, y lo previsualiza. |
| `/t3craft site wand` | Arma la "varita": el próximo bloque al que se hace clic derecho (con cualquier ítem) pasa a ser el centro. El clic se consume en el cliente, así que no se coloca ni se usa nada. |
| `/t3craft site confirm <token>` | Construye el sitio previsualizado. |
| `/t3craft site cancel` | Descarta el preview o la confirmación pendiente. |
| `/t3craft site undo` → `undo confirm <token> [keep\|overwrite]` | Restaura el área exactamente como estaba. |
| `/t3craft site move` → `move confirm <token> [keep\|overwrite]` | Restaura el lugar viejo y construye en el último lugar previsualizado. |
| `/t3craft site resume` | Termina un build, undo o move interrumpido. Es idempotente. |
| `/t3craft site status` | Muestra el sitio activo, su fase y el último build. |

Las respuestas traen botones clicables (`[confirm]`, `[keep]`, `[overwrite]`, `[cancel]`). La
preview usa partículas:
- contorno verde: se puede construir;
- contorno rojo: rechazado;
- contorno azul: sitio existente;
- borde blanco: el pad;
- naranja: celdas que cambió el jugador.

**Multijugador:** el mod es solo de cliente. Sin servidor integrado, los comandos se niegan y no
tocan nada. Ningún servidor dedicado se modifica.
**Supervivencia:** por ahora no cuesta materiales, y lo que se corta no suelta drops (así nadie
gana recursos gratis; el undo los devuelve). Opción futura documentada abajo.

## Geometría y evaluación (`SiteLimits`, `SiteEvaluator`, `Columns`)

- **Huella fija:** 15×15, con un anillo de mezcla (*apron*) de 2 bloques. La caja sube 12 bloques
  de *headroom* sobre el pad. Caja típica: 19×~14-20×19, unas 7-9k celdas. Topes duros:
  `MAX_VOLUME` 49 152 celdas y 9 chunks.
- **Pad:** a la altura de la mediana de los topes de suelo de la huella; es la altura que minimiza
  los bloques cortados más los rellenados.
- **Se rechaza el sitio si:**
  - algún chunk no está cargado (nunca se cargan ni se generan chunks para evaluar);
  - hay agua o lava en la caja, en el anillo justo afuera (para que no entre fluido) o hasta 8
    bloques bajo el suelo;
  - hay cualquier bloque *artificial* en la caja: todo lo que no se reconozca como suelo o
    vegetación natural (clasificación conservadora por tags vanilla en `LevelTerrain`). Esto
    excluye casas, aldeas y ruinas;
  - hay entidades que no son mobs salvajes: marcos, armor stands, vagonetas, botes, y mobs con
    nombre, domesticados o persistentes;
  - el relieve de la huella supera 6 bloques;
  - la caja queda fuera de la altura de construcción o supera los límites.
- **Árboles y plantas** se permiten, pero suman costo. `pick` puntúa
  `corte + relleno + 2·árboles + 4·relieve + 0.25·distancia` y saltea candidatos que tocan el
  sitio actual.

## Nivelado y cimentación (`Leveling`)

- **Huella:**
  - pad sólido (borde de stone bricks, interior de polished andesite);
  - debajo, donde el terreno es más bajo, cimiento: muro de contención de stone bricks en el borde
    y cobblestone adentro;
  - encima, todo despejado hasta el tope de la caja.
- **Apron:** cada columna se acerca al pad en ⅔ (anillo 1) o ⅓ (anillo 2) de la diferencia. Lleva
  grass arriba, dirt de relleno o corte a aire. Si la columna tiene troncos, se despeja hasta el
  tope de la caja.
- Las celdas que no hace falta cambiar quedan intactas (`null` en el plan).
- Después se superpone la estructura (`StructureSource`) en la zona
  `huella × [pad+1, pad+12]`.

## Snapshot (`Snapshot`, `SiteStore`)

- **Qué guarda:** toda la caja, bloque por bloque (paleta de estados canónicos + índice por
  celda) y el NBT completo de cada block entity (`saveWithFullMetadata`). Se restaura con
  `loadWithComponents`, así que vuelven cofres con su contenido, carteles, etc.
- **Archivos** en `<mundo>/t3craft/site/`:
  - `snapshots/<id>.nbt`: NBT gzip, legible con herramientas vanilla;
  - `plans/<id>.nbt`: el plan aplicado;
  - `state.nbt`: el journal;
  - `archive/`: snapshots ya restaurados. Nunca se borran.
- **Durable:** se escribe un temporal, se hace `fsync`, `rename` atómico y `fsync` del directorio
  (donde el SO lo permite). Después se **relee y se compara**: checksum SHA-256 del contenido
  canónico, con las claves NBT ordenadas. Si el snapshot no queda verificado, no se modifica
  ningún bloque. Un snapshot existente nunca se sobrescribe.
- **Validación al leer:** formato, versión, caja dentro de límites, índices de paleta y de block
  entity, y checksum. Un archivo dañado se rechaza; nunca se "adivina".

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
- **Move:** primero guarda y verifica el snapshot del lugar nuevo. Si falla, no cambió nada.
  Después restaura el viejo, lo archiva y construye el nuevo. El lugar nuevo no puede tocar al
  viejo.
- **Journal (`state.nbt`):** fases `NONE → BUILDING → BUILT → RESTORING`. Se escribe antes de cada
  fase.
  - Al entrar al mundo se limpian los `.tmp` y se avisa si quedó algo interrumpido.
  - `resume` termina el trabajo. Build y restore son idempotentes: lo ya hecho se saltea.
  - Después de un restore se hace `saveAllChunks(flush)` antes de que el journal olvide el sitio,
    para que un crash no deje la base en disco sin registro.
- **Escrituras:** sin actualizaciones de vecinos ni de forma, sin drops ni efectos de block
  entity, sin `onPlace`. Así no hay física, fluidos ni contenedores que se vacían, y nada se
  escribe fuera de la caja.

## Interfaces para Taller (integración)

- **Constructor:** `SiteBuilder.submit(CellPlan plan, CellPlan.Origin origin, CellGuard guard, Consumer<Report> done)`.
  - Contrato (javadoc en `SiteBuilder`):
    - escribir solo las celdas no nulas del plan;
    - llamar a `guard.mayWrite(x,y,z)` justo antes de cada bloque y saltear la celda si da
      `false`;
    - ser idempotente;
    - llamar a `done` una sola vez, en el hilo del servidor.
  - El orden del plan ya es por capas, de abajo hacia arriba.
  - Respaldo: `InstantBuilder`.
  - Para enchufar `feat/builders`: `SiteRuntime.setBuilderFactory(level -> adaptador(BuildJob))`.
    El adaptador traduce `CellPlan` → `BuildPlan` y llama al guard por bloque.
- **Estructura:** `StructureSource.design(Box area, Origin origin)` → `CellPlan` dentro de
  `area`, sin block entities. `origin` es el centro del pad, un bloque arriba, con `facing` hacia
  el jugador. Respaldo: `StructureSource.PLACEHOLDER` (postes en las esquinas y faroles). Para
  enchufar `feat/hq-design`: `SiteRuntime.setStructure(adaptador(hq BuildPlan + anchors))`. Los
  anchors de la HQ deben ser relativos a `origin`.
- **Hilo:** toda la API de `SiteService` corre en el hilo del servidor integrado.
  `SiteClient.run()` ya hace el `server.execute` → `mc.execute`.

## Conflictos potenciales con otras ramas

- `fabric.mod.json`: se agrega una segunda entrada `client`
  (`dev.t3craft.client.site.SiteClient`). Si otra rama toca esa línea, hay que unir ambas
  entradas.
- `/t3craft site` se registra aparte con `literal("t3craft").then(literal("site"))`. Brigadier
  fusiona los nodos, así que no se tocó `T3CraftCommands`. Si otra rama registra también un `site`
  bajo `t3craft`, va a chocar.
- `THIRD_PARTY_NOTICES.md`: se reemplazó "Files adapted from AgentCraft: *(none yet)*". Si otras
  ramas también agregan archivos, hay que unir las listas.
- Si `feat/builders` mueve entidades NPC dentro de la caja, a `LevelTerrain.foreignEntities` no le
  importan los NPC solo-cliente (no existen en el servidor). Si esa rama crea entidades de
  servidor con nombre, una nueva evaluación las va a ver como "entities in the way" (correcto: el
  sitio activo ya se excluye).

## Limitaciones conocidas

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
- **`SnapshotTest`:**
  - captura y restauración exactas, incluido el contenido de un cofre, sin tocar nada fuera de la
    caja;
  - ida y vuelta NBT, con detección de manipulación, celdas truncadas y formato incorrecto;
  - escritura durable sin `.tmp` sobrantes, sin sobrescribir y rechazando archivos dañados;
  - conflictos, `keep`/`overwrite` e idempotencia;
  - el builder no pisa cambios del jugador y reenviar el build no hace nada;
  - ida y vuelta del plan y del journal.
- **`SelectionTest`:**
  - terreno plano y pendiente suave (mediana);
  - colina rechazada;
  - agua, agua en el anillo, lava bajo el suelo, construcción, antorcha bajo copa y marco:
    rechazados;
  - árboles con costo;
  - chunks no cargados;
  - `pick` encuentra la meseta y evita el sitio actual;
  - nivelado: pad, muro, núcleo, corte y apron.
- **`SiteServiceTest`:**
  - el preview no escribe y el confirm construye después del snapshot;
  - el token sirve una sola vez;
  - el undo deja el área idéntica y el snapshot queda archivado;
  - el confirm se rechaza si el área cambió o si el preview venció;
  - undo con cambios: exige elegir y rechaza un reporte viejo; `keep` y `overwrite`;
  - move restaura exacto y construye el nuevo; rechaza solapes;
  - build y restore interrumpidos se reportan y se reanudan;
  - mensajes de rechazo;
  - `pick`;
  - un segundo sitio exige move o undo.

Sin probar en juego (este entorno no corre el cliente; no se levantaron dev servers): el
adaptador `LevelBlockWorld`/`LevelTerrain` y la UI de cliente solo compilan contra 26.3.
Verificación manual sugerida en un mundo de prueba, nunca el real:
1. `pick` → `confirm`;
2. romper o poner bloques;
3. `undo` (ver los conflictos en naranja) → `keep`;
4. otro `pick` → `move`;
5. cerrar el juego a mitad de un build → entrar → `resume`.

## Build

`cd mod && ./gradlew build --no-daemon` (JDK 25). Verde en la rama: los tests existentes más los
28 nuevos.
