# Builders: de "disco de columnas" a "obras con cuadrilla" (#25)

Estado: **propuesta aprobada, en implementación** (rama `feat/builder-sites`).

- **Decisiones:** tomadas el 2026-11-10. Facundo pidió decidir por él con lo óptimo, así que valen las
  propuestas por defecto de la sección 5; están marcadas ahí una por una.
- **Código:** `build.order: "sites"` (`SiteCursor`, `SiteProgram`, `SiteDispatch`). `"radial"` sigue siendo el
  predeterminado. Qué fase está hecha y cómo probarla: `docs/features/builder-sites.md`.
- **Sin verificar:** la propuesta se escribió leyendo `origin/fix/25-builder-unfinished-columns` (`86ade3a7`), sin
  compilar ni jugar. El código tampoco se compiló ni se jugó en la máquina donde se escribió.

## Contexto

Tres rondas seguidas arreglaron el mismo sistema (BLOCKED + retry, radio redondo, `WorkRank` + piso por piso) y
ninguna dio el feeling. Lo que Facundo quiere sentir: centrado en el jugador pero con orden visible; la
edificación en la que entra es la prioridad y se termina; cuadrillas que aparecen por proximidad para que no
queden obras a medias cerca; un frente coherente (el edificio, planta a planta), no un disco de columnas.

La conclusión de esta lectura: **el feeling que describe está hecho de objetos (obra, cuadrilla, frente, planta)
y el modelo actual solo tiene columnas y una clave de orden.** Cada parche añadió un modificador a esa clave.
`86ade3a7` va en la dirección correcta (introduce la obra) pero la obra sigue siendo un prefijo de la clave, sin
ciclo de vida, sin cuadrilla propia y sin frente propio.

## 1. Qué modelo de scheduling es hoy

**Nombre:** lista global de prioridad sobre columnas, con ventana de prefijo creciente y re-ordenación al moverse
el jugador. En términos de scheduling es un *list scheduling* voraz con regla "clase primero, luego el más
cercano al jugador"; en términos de motor se parece más a un **streamer de chunks alrededor de la cámara** que a
un jefe de obra.

Cómo funciona (`mod/src/main/java/dev/t3craft/build/RadialCursor.java`):

| pieza | qué hace | dónde |
|---|---|---|
| unidad de trabajo | la **columna** `(x, z)`, de abajo arriba, con un puntero de cabeza | `head` :949 |
| orden | un único array de columnas ordenado por `rango de obra × 2^18 + distancia al jugador` | `order` :724 |
| obra | índice de la parte del plan del HQ (`BuildPlan.workOf`); una columna es de la obra de su entrada de builder más baja; el rango sale de `WorkRank.rank` (dentro de su caja → empezada a < 32 → sin empezar → nada que hacer) | `WorkRank.java:50` |
| ventana | `reachQ`: la primera columna trabajable del orden + 4 bloques; solo crece cuando esa rebanada está **completa hasta el techo** | `updateReach` :1236 |
| re-orden | cada 4 bloques que se mueve el jugador (o 1 bloque tras 3 s) se reordena todo y la ventana vuelve al mínimo | `focus` :794, `installOrder` :909 |
| soporte | una cabeza sin bloque debajo o al lado se *atasca* y despierta cuando se coloca un vecino; las atascadas no frenan la ventana | `supported` :1088 |
| planta | ninguna cabeza por encima de (cabeza más baja **dentro de la ventana**) + 6 | `updateGate` :1271 |
| builders | cursores que toman un *span* (hasta 8 columnas altas vecinas) en el anillo más cercano con trabajo; `active = 3 + restantes de la obra / 1200` | `pick` :1742, `updateCrew` :1306 |
| suelo | corredor bajo/delante del jugador, stream de suelo 1 anillo por delante de la ventana, relleno oculto 2 anillos detrás | :1340, :1416, :1493 |

**Invariantes reales** (lo que el código garantiza, no lo que dice el doc):

1. Todo (suelo, relleno, builders, cuadrilla) consume un **prefijo** del mismo orden total. No hay otro concepto.
2. Dentro de una obra el orden es **distancia al jugador en el último re-orden**. El frente dentro de la obra
   sigue al jugador, no a la obra.
3. Hay **un solo frente global**: una obra a la vez (documentado como abierto en `fix-builder-works.md`).
4. Una obra no tiene estado: no se "abre", no se "cierra", nadie queda comprometido con ella.
5. La cuadrilla es un **número** (`active`), no un grupo ligado a un sitio.

**Fallos de feeling previsibles con `86ade3a7`** (a confirmar jugando; ver paso 0):

- **Rebanadas, no plantas.** El límite de planta solo mira la ventana (≈ 4 bloques de fondo) y la ventana no
  crece hasta completar esa rebanada hasta el techo. Lo que se verá: una rebanada del edificio sube planta a
  planta hasta la cubierta, luego la siguiente. El edificio no sube "entero".
- **El frente persigue al jugador dentro de la obra.** Caminar 4 bloques dentro del hub re-ancla la rebanada;
  los builders sueltan los spans que quedan fuera (`next` devuelve -1 → `release`) y vuelven junto al jugador.
  Queda un rastro de tramos a media altura: skyline dentado.
- **"Se termina" solo si no te mueves.** Entrar en otra caja la pone en tier 0 al siguiente re-orden y, como hay
  un solo frente, la obra anterior se queda sin nadie. Pasear por tres edificios = tres edificios empezados.
- **"Estoy dentro" es una caja.** Las obras son los grupos del plan (`hub`, `archive`, `cloister`, `node_n`,
  `ring_*`, `lot_n`, `module_n`, …; `CitadelFinish.groupRank`). La caja de un `ring_*` o de un `lot_n` contiene
  medio campus: el jugador está "dentro" casi siempre. "La caja más pequeña primero" lo amortigua, no lo evita.
- **Una obra no es siempre un edificio.** Grupos dispersos (jardinería, faroles, `grounds`) son "obras" cuya caja
  cubre todo; trabajar una obra así vuelve a ser bloques sueltos por el mapa.
- **Soporte entre obras.** Una cabeza que se apoya en una pared de *otra* obra queda atascada hasta que esa otra
  obra llegue ahí; mientras, la obra "terminada" tiene huecos. `forceOne` solo actúa cuando todo está parado.
- **Suelo sin obra al final.** El suelo que no pertenece a ninguna obra es rango `FAR` salvo el disco de 9
  bloques alrededor del jugador y el corredor: edificios terminados flotando sobre el vacío hasta el final.
- **Builders ociosos o teletransportados.** Con una obra pequeña la mayoría de los 12 no tiene span; al cambiar
  de obra cruzan el mapa en ≤ 20 ticks. No se lee como "llegó una cuadrilla".
- **Coste de razonar.** 2 094 líneas, cuatro punteros con marcas de despertar, columnas levantadas, aparcadas,
  rango empaquetado en `distAt`. Cada regla nueva interactúa con todas; en la VPS se escribe sin compilar.

## 2. Qué hacen otros (y qué lección deja)

Nivel de verificación: **[L]** leí la fuente en esta sesión; **[R]** solo el resumen del buscador; lo que no
pude abrir se marca así. Lo anterior del proyecto está en `docs/research/builders-ai.md` (Baritone, Structurize,
Litematica, Create, GDMC, k-means, subastas) y no se repite.

| fuente | qué hace | lección para T3craft |
|---|---|---|
| **MineColonies**, wiki del Builder [L] <https://minecolonies.com/wiki/buildings/builder/>; work orders [R] <https://deepwiki.com/ldtteam/minecolonies/2.5-work-orders-and-structure-building> | La unidad es la *work order* (un edificio). "The Builder will not start another build assignment until they have finished the current one." Etapas clear → build solid → decorate, con progreso guardado. | **Compromiso + etapas.** La obra es un objeto con dueño y estado; por eso se lee ordenado aunque el recorrido interno sea tosco. |
| **Location-based management / line of balance / takt** (Kenley & Seppänen) [R] <https://www.researchgate.net/publication/236612612_Location-Based_Management_for_Construction_Planning_scheduling_and_control>, <https://www.tandfonline.com/doi/full/10.1080/01446193.2017.1410561> | La obra real se parte en *localizaciones* (planta × zona); las cuadrillas (oficios) fluyen por ellas en secuencia fija, un oficio por localización, sin huecos ni cruces. | Es literalmente "planta a planta con cuadrillas". El orden visible sale de **localizaciones + secuencia de oficios**, no de la distancia. |
| **Wavefront Raster**, Lindsey, Mellinger, Kumar, RSS 2011 [R, PDF no legible aquí] <https://www.roboticsproceedings.org/rss07/p25.pdf> | Cuadricópteros montan estructuras capa a capa; dentro de la capa se expande un frente de onda y se construye en orden de barrido. | Dentro de una planta, **un frente de onda desde una semilla fija** (no desde un jugador que se mueve) evita huecos inalcanzables y da un borde de avance legible. |
| **TERMES**, Werfel, Petersen, Nagpal, *Science* 343 (2014) [L nota de Harvard] <https://seas.harvard.edu/news/2014/02/robotic-construction-crew-needs-no-foreman>, <https://pubmed.ncbi.nlm.nih.gov/24531967/> | El usuario da la estructura; el sistema **compila** reglas locales y de tráfico que garantizan el resultado con 5 o 500 robots. | **Compilar el orden antes** (precedencias garantizadas) y dejar al runtime solo "toma lo siguiente disponible". Lo contrario del atascar/despertar en vivo. |
| **Stonehearth**, notas de Alpha 15 [R] <https://stonehearth.fandom.com/wiki/Alpha_15_release-537> | Al colocar un edificio se construye un *grafo de dependencias* de piezas (suelo → paredes/columnas → techo); andamios como parte de la obra. | La unidad visible es la **pieza estructural** (un muro, una losa, una cubierta), no el bloque ni la columna. El andamio es una señal de "aquí se trabaja". |
| **Chunker**, McPherson & Zhou, *Rapid Prototyping Journal* (2018) [R] <https://par.nsf.gov/servlets/purl/10209027> | Varias impresoras móviles: la pieza se parte en *chunks* con dependencias para que trabajen a la vez sin estorbarse. | **Tramos (bays)** por builder dentro de una planta; ya existe el germen (`span`, zonas k-means de `CrewPlanner`). |
| **Bucket brigades**, Bartholdi & Eisenstein (1996) [R] <https://www2.isye.gatech.edu/~jjb/bucket-brigades.html>, <https://cdn.aaai.org/SIGMAN/1996/SIGMAN96-002.pdf> | Trabajadores en fila sobre una línea; cada uno avanza su trabajo hasta encontrarse con el siguiente. Se equilibra sola. | Para obras **lineales** (puente, muro, espina): builders en fila a lo largo del frente, sin cruzarse. Regla trivial, lectura muy clara. |
| **Taxonomía MRTA**, Gerkey & Matarić, *IJRR* 23(9), 2004 [R] <https://dblp.org/rec/journals/ijrr/GerkeyM04.html> | Clasifica la asignación de tareas; "tareas multi-robot" = formación de coaliciones. | Nuestro problema es **asignar cuadrillas a obras** (decenas de obras, ≤ 12 builders), no builders a bloques. A ese nivel un voraz con histéresis basta. |
| **HTN**, Humphreys, *Game AI Pro* cap. 12 [R] <https://www.gameaipro.com/GameAIPro/GameAIPro_Chapter12_Exploring_HTN_Planners_through_Example.pdf> | Tareas compuestas que se descomponen con *métodos* según el estado del mundo. | "O lo que tenga sentido con lo que se construye": **un método por tipo de obra** (edificio, torre, puente, isla, plaza). No hace falta un planificador: un árbol estático + un selector. |
| **Colonos tipo RimWorld**, rubenwardy [L] <https://blog.rubenwardy.com/2022/07/17/game-ai-for-colonists/> | Work manager: reparte por distancia + peso heurístico, con reservas. | Voraz por distancia a grano fino = lo que ya tenemos. Sirve para repartir *dentro* de una cuadrilla, no para dar orden. |
| **Factorio**, mod Closest First [L] <https://github.com/audun/factorio-closestfirst> | El juego base construye fantasmas "algo al azar" en el rango; el mod encoge el rango del roboport hasta el fantasma más cercano. | Es exactamente nuestro radio creciente. Resuelve "cerca de mí primero"; no da estructura. Ya llegamos al techo de esa idea. |
| **Estigmergia**, Theraulaz & Bonabeau, *Science* 269 (1995) [R] <https://www.science.org/doi/10.1126/science.269.5224.686> | Reglas locales construyen nidos coherentes solo si las reglas definen etapas que no se pisan. | Contraejemplo útil: lo local sin etapas globales produce ruido. |
| **Baritone**, ajustes `buildInLayers` / `layerHeight` [R] <https://baritone.leijurv.com/baritone/api/Settings.html> | La barrera por capas es opcional y de altura configurable. | La tongada de altura N como barrera es práctica conocida; nos falta aplicarla a la obra entera. |
| **Dragon Quest Builders 2** [R] <https://www.siliconera.com/dragon-quest-builders-2s-automatic-building-system-uses-residents-and-blueprints/> | Se extiende un plano; los residentes lo construyen. | El plano visible antes de la obra es una señal de orden barata (opcional). |

Patrón común: **nadie que se lea ordenado usa la distancia como orden interno.** La distancia (o el jugador)
elige *qué sitio*; dentro del sitio manda una secuencia fija: etapas, plantas, frente de onda.

## 3. Enfoques viables

### A. Afinar lo que hay (lista de columnas + `WorkRank`)

- **Idea.** Jugar `86ade3a7` y corregir sobre el mismo cursor: no reiniciar la ventana si la obra frente no
  cambia; anclar la distancia dentro de la obra a un punto fijo; calcular la planta sobre toda la obra; "estoy
  dentro" por huella y no por caja.
- **En el juego.** Parecido a hoy con menos re-anclajes; el edificio sube más parejo. Sigue habiendo una sola
  obra activa y la cuadrilla sigue siendo un número.
- **Se reutiliza.** Todo.
- **Riesgos.** Cuarto parche sobre la misma lista. Cada cambio toca punteros y despertares que nadie puede
  compilar en la VPS. Multi-obra, compromiso y cuadrillas visibles no caben en "un prefijo de un orden".
- **Encaje.** Parcial: mejora "orden dentro del edificio"; no da "cuadrillas por proximidad" ni "no dejar obras a
  medias". Es lo que Facundo pidió no repetir.

### B. Obras con cuadrilla y frente propio (*site-first*)

- **Idea.** Dos niveles separados.
  - *Despacho* (qué obras están abiertas y con cuántos builders): el jugador elige. Dentro → prioridad;
    empezadas cerca → se cierran; sin empezar → la más cercana. Límite de obras abiertas (WIP) y compromiso: una
    obra abierta conserva cuadrilla hasta cerrarse.
  - *Programa de obra* (el orden dentro): se **compila una vez**, fuera del hilo, por obra: plantas (tongadas) →
    etapas (losa, estructura, cierre, acabados) → tramos. Dentro de la planta el orden es un frente de onda desde
    el **acceso** (el punto de la obra más cercano al jugador *cuando se abre*, congelado). El jugador ya no
    reordena el interior.
- **En el juego.** Entras al hub: una cuadrilla llega junta al lado por donde entraste; el suelo se extiende
  como una ola desde ahí; el perímetro entero sube una planta; se cierra el forjado; siguiente planta; cubierta;
  pasada de faroles y detalles. Caminas dentro y nada se reordena. Sales hacia un módulo: se abre esa obra con
  su cuadrilla; en el hub queda un retén que cierra la planta en curso (o la obra). Nunca hay builders colocando
  fuera de una obra abierta.
- **Se reutiliza.** `BuildPlan.workOf` y `DemoBuildRun.merge` (ids de obra); los tiers, buckets y `KEEP` de
  `WorkRank` como puntuación del despacho; `PartSchedule` (dependencias entre obras que se tocan, ya calculadas
  en `DemoBuildRun`); `Foundation.of` (suelo/relleno); los candidatos de `WalkLevels` para detectar plantas
  reales; de `RadialCursor`: índice de columnas y celdas, `supported` (ahora como validador en la compilación),
  la construcción de spans (→ tramos), el corredor tal cual, aparcar/desaparcar chunks, `standFor`/`climbTo` y
  el `Walker`; zonas k-means de `CrewPlanner` para equilibrar tramos; `BuildBudget`; los avatares
  (`BuildersClient`, `BuildCrew`) sin cambios de API.
- **Riesgos.** Es un cursor nuevo (no un parche), aunque hecho de piezas existentes. Hay que definir "obra" para
  grupos que no son edificios. Puede sentirse *demasiado guionizado* o lento cerca del jugador si el WIP es bajo.
  Las plantas reales no siempre existen (torres, cubiertas inclinadas): hace falta el respaldo de tongada fija.
- **Encaje.** Cubre los cuatro puntos de Facundo de forma directa: son literalmente los objetos del modelo.

### C. Tren de oficios (takt / línea de balance)

- **Idea.** B más cuadrillas especializadas que recorren las mismas localizaciones (obra × planta) en fila:
  solado → albañiles → techadores → decoradores, un oficio por localización, cada uno una localización detrás
  del anterior. El tren sale de donde está el jugador.
- **En el juego.** Un desfile: donde estás levantan muros; detrás, cierran cubiertas; detrás, cuelgan faroles.
  Muy legible y con personalidad (skins por oficio).
- **Se reutiliza.** Todo lo de B; la clasificación `decor`/sólido/suelo ya existe; "cubierta" necesita una
  heurística nueva.
- **Riesgos.** Más guion y más tiempos muertos (un oficio espera al anterior). Clasificar mal un bloque rompe la
  lectura. Más trabajo de cliente (skins, roles).
- **Encaje.** Excelente en "orden visible"; algo peor en "se termina lo mío ya". Es una evolución natural de B,
  no una alternativa: B con una cuadrilla mixta ya aplica el orden de etapas.

### D. Crecimiento adherido desde el jugador (sin obras)

- **Idea.** Mantener un único frente centrado en el jugador pero cambiar la regla: solo se coloca lo que **toca
  algo ya construido**, por tongadas de altura fija, ordenado por distancia *geodésica* sobre lo construido (no
  euclídea). Como un printer de Litematica con `layerHeight`.
- **En el juego.** La estructura cristaliza desde tus pies como una cáscara continua. Nunca hay bloques sueltos.
- **Se reutiliza.** Casi todo `RadialCursor`; cambia la clave de orden y la regla de elegibilidad.
- **Riesgos.** Sigue siendo una mancha que te persigue, ahora en 3D. No sabe qué es un edificio.
- **Encaje.** Resuelve "ruido de bloques sueltos". No resuelve "esta obra se termina" ni "cuadrillas".

## 4. Recomendación (abierta a discusión)

**B como núcleo, con el orden de etapas de C dentro de cada planta (una sola cuadrilla mixta por obra), la regla
de adherencia de D como desempate dentro del tramo, y el corredor actual intacto.** El jugador decide *qué obra*
y *por qué lado se empieza*; la obra decide *el orden*.

Cómo, sin otro rewrite encima:

- **Cursor nuevo al lado, no en sitio.** `SiteCursor implements JobCursor`, elegido con `build.order: "sites"`
  (clave aditiva). `"radial"` sigue siendo el predeterminado y no se toca hasta que el nuevo se haya jugado.
  Comparación A/B con un cambio de config.
- **Invariantes simples a propósito** (escribibles sin compilar): colas explícitas por obra, sin prefijo
  global, sin punteros con despertar, sin re-orden al moverse. El soporte se resuelve al compilar el programa;
  lo que no encaja va a una lista de *remates* al final de la obra.
- **Validación en texto antes de jugar.** El programa de obra es puro: se puede volcar como informe (por obra:
  plantas, tramos, entradas, cuántas quedarían sin soporte en su turno) junto a `PlanReport`
  (`mod/src/hqrender/.../PlanReport.java`), que ya cuenta por grupo. Lo ejecuta el coordinador en el PC, sin
  imágenes.

Fases, cada una jugable y con vuelta atrás por config:

| fase | contenido | qué se aprende |
|---|---|---|
| 0 | **Sin código.** Facundo juega `86ade3a7` con la lista de observación de abajo. El coordinador saca del informe de plan existente cuántas obras hay, tamaños y cuáles son dispersas. | Cuáles de los fallos previstos son reales; si "obra = grupo" sirve. |
| 1 | Despacho + una obra abierta + frente anclado al acceso, planta sobre la obra entera. Suelo y corredor como hoy. | Si "el frente no me persigue" es el feeling. Es la hipótesis central. |
| 2 | Programa compilado: plantas reales, etapas, tramos, remates. Suelo de la obra como ola propia. | Si se lee "planta a planta". |
| 3 | Varias obras abiertas, retén, cuadrillas por proximidad, reparto de ritmo. | "No quedan obras a medias cerca". |
| 4 | Métodos por tipo: puente (brigada en fila), isla (terreno como obra de suelo), jardinería. Opcional: oficios (C), señales de obra. | Islas flotantes y mapas nuevos. |

Lista de observación para la fase 0 (5 preguntas, se responden mirando y con `/t3craft demo status`):

1. Quieto dentro del hub: ¿sube entero o por rebanadas desde donde estoy?
2. Caminando 10–15 bloques dentro del hub: ¿los builders sueltan lo que hacían y me siguen?
3. Cruzando hub → módulo → otro módulo: ¿cuántos quedan a medias y cuánto tardan en volver?
4. ¿El número de obra del status cambia estando quieto, o apunta a algo que no es un edificio (anillo, lote)?
5. ¿Hay edificios terminados sin suelo alrededor? ¿Vuelve a salir `standing on air` en el log?

Criterios de aceptación propuestos para "se siente bien" (medibles con contadores de texto en el status):

- Estando dentro de una obra, la gran mayoría de las colocaciones de builders ocurren en ella.
- Una obra abierta nunca tiene más de una planta de diferencia entre su tramo más alto y el más bajo.
- Cero colocaciones de builders fuera de obras abiertas.
- Caminar dentro de la obra no cambia la asignación de tramos.
- Al salir, la obra queda con la planta cerrada o con retén; nunca dentada.
- Cero `standing on air`.

## 5. Decisiones (tomadas el 2026-11-10)

Todas quedaron en la propuesta por defecto. La última columna dice qué se decidió y en qué fase entra.

| # | decisión | opciones | propuesta | tomada |
|---|---|---|---|---|
| 1 | **Qué cuenta como obra** | (a) el grupo del plan tal cual; (b) grupo con reglas: `lot_n` + sus `module_n` = una obra "lote"; `ring_*` partido en tramos entre nodos; lo disperso (jardinería, faroles, `grounds`) no es obra | (b) | **(b)**. Lo disperso va como pasada de detalles al final. Fase 1. |
| 2 | **"Estoy dentro"** | caja (hoy) / huella real de la obra con margen | huella | **huella con margen** (2 bloques). Fase 1. |
| 3 | **Tamaño de cuadrilla** | fija (3–4) / proporcional al trabajo | proporcional: mín. 2, máx. 6 por obra, 12 en total, nunca más builders que tramos libres | **proporcional**: mín. 2, máx. 6 por obra, 12 en total, nunca más que tramos libres. Fase 1. |
| 4 | **Obras abiertas a la vez** | 1 / 2 / 3 | 2 (foco + cierre de la anterior); 3 si sobran builders | **2**; 3 si sobran builders. Fase 3 (las fases 1 y 2 abren una). |
| 5 | **Si camina a otro edificio** | (a) todos se mudan; (b) se muda la cuadrilla principal y queda un retén de 2; (c) cierran la planta en curso y luego (b) | (c) | **(c)**. Fase 1 cierra la planta y se muda; el retén de 2 es fase 3. |
| 6 | **Obra que queda lejos** (> ~32 bloques) | se congela / sigue con retén / sigue siempre | el retén cierra la planta y se congela; al volver tiene prioridad | **el retén cierra la planta y se congela**; al volver tiene prioridad. Fase 3. |
| 7 | **Qué manda dentro de la obra** | planta completa (todo el perímetro sube parejo) / el lado del jugador primero | planta completa, ola desde el acceso por el que llegó | **planta completa**, ola desde el acceso congelado al abrir. Caminar dentro no reordena. Fase 1. |
| 8 | **Qué es una planta** | suelos reales del edificio / tongada fija de N bloques | reales cuando existan; si no, 4–5 bloques | **reales cuando existan; si no, 5 bloques**. Tongada fija en fase 1, suelos reales en fase 2. |
| 9 | **Suelo de la obra** | lo pone la cuadrilla (visible, lento) / stream rápido sin builders pero como ola de la obra / toda la isla primero | stream como ola de la obra + corredor | **stream como ola de la obra + corredor** intacto. Fase 2. |
| 10 | **Suelo entre obras** | al final (hoy) / alfombra alrededor del jugador + sendero hacia la obra abierta más cercana / todo al principio | alfombra + sendero | **alfombra + sendero**. Alfombra en fase 1, sendero en fase 2. |
| 11 | **Oficios** | cuadrilla mixta / tren de oficios con skins | mixta ahora; oficios como fase 4 | **mixta**, con el orden de etapas dentro de cada planta (losa, estructura, cierre, acabados). Fase 2. Skins de oficios: después. |
| 12 | **Islas** | la isla (terreno) es una obra de suelo que se abre por proximidad / se genera de golpe | obra de suelo; piezas pequeñas (árbol, farol) como lote de jardinería al final de cada isla | **obra de suelo por proximidad**. Fase 4, no ahora. |
| 13 | **Puentes** | builders en fila desde la isla del jugador / stream | brigada de 2 en fila | **brigada de 2 en fila**. Fase 4, no ahora. |
| 14 | **Ritmo** | la obra foco se lleva casi toda la cuota / reparto parejo | 70 % foco, 30 % cierres | **70 % foco, 30 % cierres**. Fase 3. |
| 15 | **Señales de obra** | ninguna / contorno, andamio o estandarte en obra abierta; la cuadrilla llega junta | probar "llega junta" primero; el resto después | **la cuadrilla llega junta**; sin andamios ni estandartes por ahora. Fase 1. |
| 16 | **Alcance** | solo demo y trabajos libres / también HQ real y supervivencia | solo demo y libres | **solo demo y trabajos libres**. No se toca el HQ real ni supervivencia. |
| 17 | **Prototipo** | cursor nuevo por fases (recomendado) / experimento de 3 cambios dentro de `RadialCursor` tras una clave de config | cursor nuevo; el experimento es más barato pero es otro parche a ciegas | **cursor nuevo**: `SiteCursor` + `SiteProgram` + `SiteDispatch`, con `build.order: "sites"` (clave aditiva). `RadialCursor`, `CrewCursor`, `FoundationCursor` y el cliente de avatares no se tocan. |

## 6. Archivos que toca la implementación

- Nuevo: `mod/src/main/java/dev/t3craft/build/SiteCursor.java` (cursor), `SiteProgram.java` (compilación pura:
  plantas, etapas, tramos, remates), `SiteDispatch.java` (despacho puro: qué obras, cuántos builders).
- Aditivo: `BuildTuning` (`build.order: "sites"`, WIP, tamaños de cuadrilla, altura de tongada); `BuildJob`
  (elegir el cursor, `radialStatus` equivalente); `BuildPlan` (nombre/tipo de obra junto a `workOf`);
  `DemoBuildRun.merge` (pasar el nombre del grupo); `WorkRank` (prueba por huella).
- Sin tocar: `RadialCursor`, `CrewCursor`, `FoundationCursor`, cliente de avatares.
- Docs: esta propuesta (`docs/plans/builder-scheduling.md`); `docs/features/builder-sites.md`; línea RESTART en
  `docs/CHANGELOG-dev.md`; issue nuevo por fase (`Refs #N`).

Lo que cambió respecto de esta lista al implementar: `WorkRank` no se tocó (la prueba por huella vive en
`SiteProgram.siteAt`, porque `WorkRank` solo sirve a `RadialCursor`), y el nombre del grupo viaja con
`BuildPlan.named` / `BuildPlan.workName` desde `DemoHq.full`.

## 7. Verificación

- Fase 0: partida de Facundo con la lista de observación; nada que compilar.
- Cada fase con código: el coordinador compila en el PC (`tools/heavy.sh`), vuelca el informe de texto del
  programa de obra, y Facundo juega con `build.order: "sites"` y `/t3craft demo rebuild`, comparando contra
  `"radial"`. Sin tests unitarios, sin renders por lotes, sin PNG en hilos.

## Límites de esta propuesta

- No se jugó ni compiló nada: los fallos de la sección 1 salen de leer el código y son predicciones.
- Varias fuentes están citadas a nivel de resumen del buscador ([R]); el PDF de Wavefront Raster no se pudo
  extraer a texto en esta máquina y la página de work orders de MineColonies devolvió 429.
- No medí tamaños reales de las obras de la ciudadela (hace falta un informe de plan del PC).
