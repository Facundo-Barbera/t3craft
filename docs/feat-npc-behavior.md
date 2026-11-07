# feat/npc-behavior: comportamiento de los NPCs de hilo

Paquete: `mod/src/client/java/dev/t3craft/client/npc/behavior/` (todo del lado del cliente).

## Qué hace

Cada NPC (un hilo) tiene una **máquina de estados** (`NpcBrain`) que se alimenta en cada tick
con el modo del hilo, sus puntos de destino y la posición del jugador:

| Estado del hilo (`NpcInfo.state`) | `Mode` | Comportamiento |
|---|---|---|
| `working` | `WORKING` | camina a su escritorio (anchor `desk`) y "teclea": balancea los brazos alternados con la cabeza baja y alguna pausa para pensar; emote ⌨ |
| `needs_attention`, `error` | `ATTENTION` | camina al **tablero central** (un lugar por NPC, sin superponerse), espera mirando el tablero; si el jugador se acerca, se gira hacia él y lo saluda. **Ícono flotante** `!` (naranja) o `✖` (rojo, si es error), que sube y baja, y **bocadillo** fijo |
| `idle` | `IDLE` | deambula por su barrio: elige puntos alcanzables al azar (determinista por id) dentro del radio, se detiene, mira alrededor, a veces tararea (♪ con partículas de notas) |
| `done` | `COMPLETED` | festeja unos 3,5 s (saltitos, brazos, partículas felices y fuegos artificiales) y después camina a su lugar de descanso y descansa agachado (emote z) |

- **Navegación** (`Navigation`, `GridPathfinder` adaptado de AgentCraft, `Collision`): A* 3D sobre
  las **cajas de colisión reales** (`getCollisionShape`) de los bloques del nivel del cliente
  (`LevelBlocks`), no "sólido o no":
  - una celda es caminable si hay apoyo bajo el centro (bloque abajo con tope entre 0,5 y 1, o algo
    bajo en la celda: alfombra, losa inferior, trampilla cerrada abajo) y el cuerpo (0,6 × 1,8)
    entra sin tocar ninguna caja; agua, lava, fuego y nieve polvo no se pisan;
  - cada paso entre celdas barre la caja del cuerpo de centro a centro: un escritorio de losas
    superiores, trampillas, vallas o el panel de una puerta de hierro bloquean; las puertas que se
    abren a mano y las puertas de valla cuentan como abiertas (pasables, sin abrirlas visualmente);
  - escalones de hasta 0,6 (losas, alfombras) y subidas de 1 bloque solo si hay un escalón intermedio
    a la entrada (escaleras): los NPC no trepan muebles; bajar 1 bloque sí;
  - si el destino (o el NPC) está dentro de un bloque, se usa la celda libre más cercana del mismo
    nivel (a 2 bloques como mucho), así nunca se apunta al interior de un escritorio o una silla;
  - 8 direcciones sin cortar esquinas, suavizado por línea de visión con la caja del cuerpo;
  - **presupuesto**: 2500 nodos por tick para todos los NPC; una búsqueda que no termina responde
    `PENDING` y sigue en el tick siguiente (el NPC sigue con lo que hacía). Máximo 16.000 nodos por
    búsqueda, en una caja alrededor de origen y destino;
  - **caché**: las rutas encontradas se guardan 5 s como celdas y se revalidan contra los bloques
    actuales en cada uso; los fallos, 2 s. Las lecturas de bloques se cachean solo un tick.
  - **sin ruta = no camina**: ya no hay línea recta de respaldo a través de paredes. El NPC se queda
    (haciendo lo de su lugar) y reintenta a los 2 s.
- **Movimiento** (`Motion`, adaptado de `AgentMotion` de AgentCraft): a paso tranquilo, acelera y
  frena suave, y gira suave. Cada submovimiento pasa por `Collision.move` (como `Entity.move`: Y,
  después el eje horizontal mayor, con auto-escalón de 0,6), así que la caja del NPC **nunca queda
  dentro de un bloque**. El avatar se mueve con `moveOrInterpolateTo`, igual que un jugador remoto,
  así que la animación de caminar de vanilla funciona sola. El panel, el bocadillo y el ícono siguen
  la posición renderizada del avatar.
- **Atorado**: si un NPC camina 25 ticks sin acercarse a su próximo punto, marca 10 s como bloqueada
  la celda contra la que empuja y replanifica (2 veces); después se desliza al centro de la celda
  libre más cercana (con colisión, nunca atraviesa paredes) y replanifica (2 veces); al final se
  queda quieto 8 s y vuelve a intentar.
- **Dentro de un bloque** (al aparecer, al cargar, si le ponen un bloque encima): se lo pasa a la
  celda libre más cercana (se revisa al colocarlo y cada segundo mientras está quieto).
- Ya no se teletransporta, salvo que esté a más de 48 bloques, recién se una al mundo o haya quedado
  dentro de un bloque.
- **Evitar al jugador**:
  1. las rutas no pasan por las celdas que rodean al jugador;
  2. si el jugador está justo adelante, el NPC se frena, lo mira y a los 20 ticks recalcula la
     ruta para rodearlo (si sigue bloqueado mucho rato, toma la ruta que no lo esquiva);
  3. si el NPC está quieto y el jugador se le mete encima, se corre un paso y vuelve a su lugar
     cuando el lugar queda libre.
- **Bocadillos** (`BubbleText` y `SummarySource`): muestran el último resumen del hilo
  (por defecto `NpcInfo.activity()`: último mensaje, pedido pendiente o último error), con ajuste
  de línea a 34 caracteres y máximo 3 líneas con "…", y el glifo del emote delante. Aparecen siempre
  en `ATTENTION`, durante 6 s al cambiar de modo o de resumen, y cuando el jugador está a menos de
  4,5 bloques.
- **Al unirse a un mundo** los NPCs aparecen directamente en su lugar, sin desfile de caminantes y
  sin festejo de los que ya estaban `done`. Es el mismo criterio de "seeding silencioso" de los
  avisos.

## Con o sin base física

`AnchorSource` es la interfaz opcional por la que una base física le dice a cada NPC adónde ir:
`desk(npcId, groupId)`, `board()`, `boardSlots()`, `home(groupId)`, `wanderRadius(groupId)` y
`rest(npcId, groupId)`. Todos sus métodos pueden devolver `null`. Se combina por capas
(`LayeredAnchors`) sobre `RingLayout`, que es **el mismo layout de siempre**: parcelas de barrio en
grilla de 3 junto a donde estaba el jugador y los NPCs en anillo. En ese layout el escritorio es el
lugar del anillo (mirando al centro), el hogar es el centro de la parcela y el tablero es una plaza
delante de la primera fila de parcelas. Sin base, todo funciona como antes, pero con
comportamiento. Con una base parcial (por ejemplo, solo escritorios), lo que falte sale del anillo
para cada NPC.

## Puntos de integración

1. **Base física / HQ (otra rama)**: implementar `AnchorSource` y registrarla con
   `T3CraftClient.get().npcs().behavior().setAnchorSource(src)`; pasar `null` cuando la base desaparece.
   Se llama en el hilo del cliente, varias veces por tick: hay que devolver valores cacheados y no
   escanear el mundo ahí.
   Para el tablero se puede dar solo `board()` (el anchor mira hacia donde mirarán los NPCs; ellos
   se paran detrás en filas de 5) o también `boardSlots()` explícitos.
2. **Resumen del hilo (chat / bridge)**: `T3CraftClient.get().npcs().behavior().setSummarySource((id, info) -> …)`
   para dar un resumen mejor que `activity`.
3. **`NpcRegistry`** (único archivo fuera del paquete, commit aparte `NpcRegistry: hand avatar
   motion…`, unas 20 líneas):
   - campo `behavior` y getter `behavior()`;
   - en `tick()`: construye los `BehaviorDirector.Body` y llama a `behavior.tick(lvl, player,
     anchor, bodies)`. Se quitó el `lookAt` viejo, porque ahora la cabeza la maneja el cerebro;
   - en `reconcile()`: los avatares ya creados **no** se reubican en el anillo (los mueve el
     director). La posición del anillo solo se usa al crearlos;
   - `behavior.forget(id, lvl)` al quitar un NPC, `behavior.reset(lvl)` en `clear()` y
     `behavior.reset(null)` al cambiar de nivel.

   Si otra rama reescribe `NpcRegistry`, alcanza con volver a aplicar esos cuatro puntos.
4. **`mod/build.gradle`**: el source set `test` ahora también ve `sourceSets.client.output`, para
   poder testear la lógica pura del paquete. Son 6 líneas marcadas con un comentario.

## Hot-swap / recarga

- No hay estado estático mutable. El contador de ids de las entidades extra (bocadillo e ícono,
  que arranca en −3.000.000) es un campo de instancia, lejos del rango del registro.
- `BehaviorDirector.reset()` vacía los cerebros y descarta las entidades propias. Las entidades se
  guardan por referencia y nunca se buscan por id con cast, así que no puede repetirse el
  `ClassCastException` de `NpcRegistry`.
- La lógica (`NpcBrain`, `Motion`, `GridPathfinder`, `RingLayout`, `BoardSlots`, `BubbleText`) no
  usa clases de Minecraft: es determinista y tiene tests.

## Tests

`mod/src/test/java/dev/t3craft/client/npc/behavior/`. `NavigationTest` usa un mundo falso de
celdas con cajas (`CellWorld`): puertas abribles y de hierro, escaleras y losas, alfombras, rodeo de
escritorios de losa, trampilla y valla, destino dentro de un escritorio, agua, presupuesto de nodos
repartido en ticks y caché, replanificación al cerrarse un paso (también a mitad de camino), choque
contra una pared y aviso de atorado, auto-escalón, desatasco sin atravesar paredes, empujar fuera de
un bloque y "sin ruta no camina"; siempre comprueba que la caja nunca queda dentro de un bloque. El
resto cubre el pathfinder (línea recta, rodear una pared, encerrado, esquivar al jugador, escalón), el movimiento
(llega y frena, se detiene ante el jugador, ángulos), el cerebro (colocación al unirse, tecleo, ir
al tablero con ícono y bocadillo, saludo, festejo y descanso, seeding sin festejo, deambular dentro
del radio, correrse cuando el jugador lo pisa y volver, determinismo, destello del bocadillo), el
layout (anillo igual al del registro, anchors físicos con fallback, lugares del tablero sin
superponerse) y los bocadillos (ajuste de línea y fallback). `./gradlew build` pasa en verde,
incluidos los tests que ya había.

## Viene hacia el jugador (ATTENTION, ítem 16)

Cuando un hilo pasa a `ATTENTION` y el jugador está cerca, el NPC deja el tablero (o su escritorio),
**camina** con el pathfinder hasta unos 2,5 bloques del jugador, del lado por el que viene, lo mira
y lo saluda (brazo y emote `o/`). Si van varios, se abren en abanico para no pisarse. Si el jugador
se mueve más de 3 bloques, recalcula el lugar. Si no hay ruta, se queda donde está y saluda desde
ahí (nunca se teletransporta).

Vuelve a su lugar cuando el hilo sale de `ATTENTION`, cuando el jugador se aleja (más de
`maxDistance` + 8 bloques) o cuando pasa `maxSeconds`. Si se agota el tiempo, no vuelve a venir
hasta que el hilo salga de `ATTENTION`. Después de cada acercamiento hay un cooldown por NPC.

`ApproachPlanner` (lógica pura, con tests en `ApproachTest`) decide quién viene y adónde.
`NpcBrain.approach(spot)` reemplaza el lugar del tablero mientras dura. `BehaviorDirector` solo
llama al planner y le pasa el resultado a cada cerebro. Config aditiva en `config/t3craft.json`,
que se vuelve a leer cuando cambia el archivo:

```json
"npcApproach": { "enabled": true, "maxDistance": 16, "maxConcurrent": 2, "cooldownSeconds": 60, "maxSeconds": 30 }
```

Por código: `behavior().setApproachConfig(cfg)` (con `null` vuelve a leer el archivo).

## Pendientes

- **No se probó dentro del juego**: no se lanzó Minecraft, por regla. Falta verificar a ojo la
  altura del bocadillo y del ícono respecto del panel (las constantes `PANEL_Y`, `BUBBLE_Y` y
  `LINE_H` están en `BehaviorDirector`), cómo se ve el balanceo de brazos al "teclear", y que la
  animación de caminar con `moveOrInterpolateTo` salga fluida.
- Sentarse de verdad en una silla (montar una entidad asiento) cuando la base dé sillas: por ahora
  "descansar" es agacharse.
- Los letreros de barrio siguen en las parcelas del anillo aunque haya base. Lo decide quien
  integre la base (podría exponer `home()` y que el registro ponga el letrero ahí).
- Que los NPCs se esquiven entre ellos solo se resuelve en el tablero (cada uno tiene su lugar).
  Dos NPCs caminando pueden cruzarse.
- Límite de búsquedas de ruta por tick: hoy cada recálculo es inmediato. Con muchos NPCs
  (más de 50) y cambios masivos de estado, convendría repartirlos entre ticks.
