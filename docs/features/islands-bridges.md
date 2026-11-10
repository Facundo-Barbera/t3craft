# Islas flotantes: puentes, segundo corte (#32)

Segundo corte de los puentes en tiempo real del mapa `floating-islands` (primer corte y generador en
[`algo-map-generators.md`](algo-map-generators.md); el generador de islas lo cambia #31 en paralelo).

> **Sin compilar ni jugar.** Escrito en la VPS sin Gradle, Java ni Minecraft. Falta compilar en el PC
> (`compileJava compileClientJava`, coordinador) y la prueba en juego de abajo.

## Qué cambia

| | primer corte | ahora |
|---|---|---|
| Qué puente sale | cualquiera a ≤ 9 bloques de su punto de salida | el que está **delante** del jugador (hacia donde camina; si está quieto, hacia donde mira) y más cerca |
| Aviso | ninguno | chispas `end_rod` en el punto de salida desde 16 bloques antes |
| Tablero | recto, a veces un salto de un bloque | sin saltos: losas (medio bloque por paso) o escaleras si es más empinado |
| Barandilla | poste cada 3 pasos | curb + valla/muro continuos a ambos lados |
| Estilos | uno | tablón, arco de piedra, colgante con cadenas |
| Vecinas | 4 de celda | 4 de celda + diagonales (hasta 8), sin cruces |
| Crecimiento | sin efectos | campanilla al empezar y al acabar, sonido del bloque y chispas en la punta |

## Detección (`IslandsRuntime.nearEdge`, cada 4 ticks)

- Solo si el jugador está de pie sobre una isla ya construida (como antes).
- **Rumbo**: lo que se movió desde la última comprobación (si ≥ 0,2 bloques); si está quieto, la dirección
  de la mirada (horizontal).
- Solo cuando la isla ya tiene todos sus planes (`warmed`; ver "Vecinas", planificación repartida).
- Candidatas: los enlaces de la isla (`BridgeNetwork.links`) aún no construidos, con su punto de salida
  (`Bridge.startOn`, la primera celda de tablero sobre el vacío) a ≤ `HINT` = 16 bloques y a menos de ~70°
  del rumbo (`cos ≥ 0,35`); a ≤ `CLOSE` = 3 bloques cuenta en cualquier dirección.
- Gana la de menor `distancia × (2 − cos ángulo)`: la más cerca y más de frente.
- Si está a ≤ `TRIGGER` = 7 bloques y de cara (`cos ≥ 0,65`, ~50°), o a ≤ 3 bloques, **arranca**. Si no,
  **pista**: una columnita de chispas `end_rod` sobre el punto de salida (se ve unos bloques antes).

## Tablero sin saltos (`bridge/DeckProfile`)

El paso 0 toca el borde de la isla `a` a la altura de su césped y el último el de `b`; ambos extremos a ras.

- **Modo medio bloque** (si `|2·desnivel| ≤ pasos − 1`): la superficie sube o baja como mucho medio bloque
  por paso: bloque entero en las alturas enteras, losa inferior en las medias.
- **Modo escalera** (más empinado): bloques enteros, como mucho uno por paso; el paso donde cambia la altura
  es una escalera mirando cuesta arriba (al bajar, la escalera es el último paso alto).
- El colgante además **se comba** en el centro (catenaria leve, hasta 2 bloques: `len/10` medios bloques,
  máx. 4); si la comba obligara a un escalón mayor, se quita.

## Sección transversal (`BridgePlanner`)

- **Línea**: la recta entre centros, un bloque por paso sobre el eje largo (como antes, así los puentes ya
  guardados coinciden). Los **bordes reales** se buscan columna a columna con el `TerrainShape` de
  `IslandField.terrain(ventana)` (antes `generate(ventana).shape()`; no se asume un disco): el tablero va de la última columna con suelo de `a`
  a la primera de `b`. La identidad de la isla de cada columna sale de `Island.contains`.
- **Sin puente** si las islas se tocan, si la recta vuelve a entrar en una isla a medio camino o si alguna
  columna del tablero, la barandilla o su refuerzo (±3 del eje) tiene suelo de una **tercera isla**.
- Cada paso, en este orden (la rebanada crece de abajo arriba): soportes, tablero de **3 de ancho**, curb y
  **barandilla continua** a ±2 (más un bloque a ±3 donde la línea se desplaza de lado, para que la valla no
  deje un hueco en diagonal por el que colarse), luces y cables.
- Se extiende 2 pasos sobre cada borde, **solo en columnas vacías**: rellena alrededor de un borde irregular
  sin tocar el suelo de la isla.
- Al colocar, cada bloque toma sus conexiones de lo que ya hay (`Block.updateFromNeighbourShapes`), así las
  vallas y muros se unen según crece.

## Estilos (`bridge/BridgeStyle`, bloques vanilla)

| estilo | cuándo | tablero / barandilla | soporte y luces |
|---|---|---|---|
| **tablón** (`plank`) | siempre si el tablero tiene ≤ 16 pasos; mezcla en el resto | abeto (tablones, losas, escaleras), curb de roble oscuro, valla de abeto | cada 4 pasos una viga de tronco bajo el tablero y patas de tronco de 3 + valla colgando bajo el curb; farol sobre la valla encima de cada pareja de patas |
| **arco de piedra** (`stone arch`) | 60 % de los empinados; 35 % de los largos y llanos; 45 % del resto | ladrillo de piedra (con musgo y grietas por hash), muro de ladrillo | el hueco bajo el tablero se rellena hasta una curva de intradós: profundo en las islas (`len/4`, 2-7), un bloque en la clave; faroles en el muro cada 5 pasos y uno colgando bajo la clave |
| **colgante** (`suspension`) | 65 % de los largos (≥ 36 pasos) y casi llanos (desnivel ≤ 4); 20 % del resto si el desnivel ≤ 4 | roble oscuro, curb de abeto, valla de roble oscuro, comba leve | torres de tronco descortezado de 5 con farol en ambos extremos, cable de cadenas a cada lado en parábola de la torre a la barandilla con péndolas verticales cada 2 pasos; cadenas con farol colgando bajo el tablero cada 6 pasos |

El estilo sale de un hash estable de la pareja (semilla del campo + celdas), la longitud del tablero y el
desnivel: la misma pareja siempre da el mismo puente. Los empinados (modo escalera) nunca son colgantes.

## Vecinas (`bridge/BridgeNetwork`)

- **Ejes**: las 4 vecinas de celda, como en el primer corte (mismos ids `b:<idA>|<idB>`). Cada tablero queda
  dentro de las dos celdas, así que dos puentes de eje no se cruzan.
- **Diagonales**: en cada bloque 2×2 de celdas con `bx + bz` par (tablero de ajedrez: dos bloques que
  comparten lado nunca tienen las dos) y con tirada de hash < 60 %, una de sus dos diagonales (por hash) es
  candidata. Se queda si:
  1. tiene plan (ninguna tercera isla en medio) y su tablero tiene ≤ 80 pasos;
  2. deja al menos 1 columna libre con cada puente de eje que pueda llegar a su bloque (celdas `bx−1..bx+2`);
  3. deja esa misma holgura con las candidatas de los 4 bloques de esquina; si dos chocan, caen las dos (así
     el resultado no depende de quién pregunte primero).
- **Longitud máxima de los ejes** (#40): un puente de eje de más de `MAX_AXIS_DECK` = 64 pasos de tablero no se
  construye. Con XG en la mezcla (celda de 82) dos islas pequeñas vecinas pueden quedar a ~90 bloques. Para que
  ninguna isla quede aislada, una isla **sin ningún otro enlace** (ni eje corto ni diagonal) conserva su puente de
  eje más corto, sea cual sea su longitud (a igualdad, el de menor id). Es simétrico: el enlace existe si
  cualquiera de las dos islas lo necesita (`axisLinked`, `lifeline`). Las diagonales siguen en ≤ 80. Los ejes
  largos que no se construyen siguen contando como obstáculo al validar las diagonales (no cambia lo ya decidido).
- Todo es determinista y simétrico; los planes se cachean en el `BridgeNetwork` del runtime.
- **Planificación repartida** (#40): `BridgeNetwork.links(isla, maxPlanes)` planifica como mucho `maxPlanes` puentes
  nuevos por llamada y devuelve `null` hasta tenerlos todos (guarda lo ya planificado). `IslandsRuntime` la llama
  con 2 planes por comprobación: `nearEdge` (cada 4 ticks, ticks ≡ 0) y `warm` (ticks ≡ 2), que además de la
  isla bajo el jugador prepara una a una las islas a las que se enlaza. Una isla entera (sus 4 ejes, sus 4
  bloques de diagonales y los ejes que pueden chocar con ellas) se prepara en unos segundos sin tirón, y
  `nearEdge` no da pistas ni arranca puentes hasta que la isla está lista. El planificador ahora pide solo el
  suelo (`IslandField.terrain`) y descarta por caja las islas lejanas, que es lo que más costaba de cada plan.
- `IslandField.generate` (de #31) sigue publicando solo los 4 enlaces de eje como `ON_DEMAND` (sin el límite de
  longitud); las diagonales y el límite solo los conoce `BridgeNetwork`.

## Persistencia y mundos guardados

- Igual que antes: al terminar un puente, `IslandsState.addBridge(id)` con el mismo formato de id. Un puente
  del primer corte ya guardado no se vuelve a construir (se queda con su aspecto antiguo); los que faltaban
  se construyen con el estilo nuevo; las diagonales son ids nuevos.
- El primer corte marcaba como "construido" un par sin vacío entre medias; eso sigue siendo correcto.
- Ningún archivo nuevo, ninguna clave de config.

## Decisiones tomadas (defaults sin preguntar)

- 8 direcciones (ejes + diagonales en tablero de ajedrez) en vez de "las k más cercanas": es simétrico y local
  (se decide con el bloque 2×2 y sus vecinos), sin estado global.
- Puentes rectos: la curva en planta queda fuera; evitar islas se hace descartando el enlace.
- La holgura entre puentes es 1 columna; con islas pequeñas muchas diagonales caen por quedar pegadas a un
  puente de eje en la salida.
- El texto de la barra de acción usa el token `brass` de `ChatTheme.warmStudio()`.
- 2 secciones por tick (como antes): un puente de 40 pasos tarda ~1 s más los soportes.

## Riesgos de compilación a vigilar

- `SoundEvents.AMETHYST_BLOCK_CHIME` se usa como `SoundEvent` en `ServerLevel.playSound(null, pos, …)`;
  si en esta versión fuera un `Holder`, cambiar a `.value()`.
- `Block.updateFromNeighbourShapes(state, level, pos)` (ya usado en `hq.plan.PlanApplier`).
- `Component#withColor(int)` (ya usado en `Notifier`).
- `Bridge` cambió de forma (record con `style`, `startA`, `startB`, `deck`); `BridgePlanner.plan` ahora pide
  el `IslandField`. Solo los usa `IslandsRuntime`.
- (Resuelto en #40) La primera vez que un jugador pisaba una isla se planificaban de golpe unas decenas de puentes:
  ahora se reparten en 2 planes por comprobación (ver "Vecinas").
- Revisión cruzada de #40: sin errores de compilación a la vista; lo no confirmado está en la lista de
  [`islands-worldgen.md`](islands-worldgen.md#riesgos-de-compilación-revisado-a-mano-sin-compilar).
- Cruce #31/#32 corregido: la regeneración (`/t3craft islands regen`) solo limpiaba los pasillos de eje y dejaba en
  pie los puentes diagonales; ahora limpia también la caja de cada diagonal.

## Prueba en juego

1. `/t3craft islands` (desde otro mundo: `/t3craft islands confirm`).
2. En la isla de aparición, caminar hacia un borde: unos 16 bloques antes del borde, en la dirección en que
   caminas, aparecen chispas `end_rod` donde saldrá el puente; al girarte hacia otro lado, la pista cambia al
   borde que tengas delante.
3. Seguir hacia la pista: a ~7 bloques suena una campanilla, la barra de acción dice
   "A *estilo* bridge unfolds towards the *tamaño* island" y el puente crece desde ese borde con sonido y
   chispas en la punta; al llegar a la otra isla, otra campanilla.
4. Cruzarlo andando sin saltar (subidas en losas o escaleras), comprobar que la barandilla no tiene huecos y
   que no se cae uno por los lados.
5. Probar una diagonal: desde la isla de aparición, andar en diagonal (NE/NO/SE/SO); si ese bloque 2×2 tiene
   diagonal, la pista aparece en esa esquina.
6. `/t3craft islands status`: el contador de puentes construidos sube; salir y volver a entrar: los puentes
   no se reconstruyen.
