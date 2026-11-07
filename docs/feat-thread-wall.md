# feat/thread-wall: pared kanban de hilos (P3)

Bloque **`t3craft:thread_board`** del módulo blocks (block entity + BER): una pared con los hilos en
columnas por estado, **Working / Needs you / Error / Done**, con tarjetas del kit del chat. Clic en
una tarjeta → abre el chat del hilo. Es P3 de `docs/research/agentcraft-world-elements.md`,
adaptado del task wall de AgentCraft (MIT, commit `8fe3ad2`; ver `THIRD_PARTY_NOTICES.md`).

No necesita el monitor multibloque: **un bloque ancla** dibuja la pared entera.

## Cómo funciona

- El ancla es la **celda de abajo a la izquierda, vista de frente** (`facing` = hacia donde está el
  espectador). La pared ocupa `width` bloques hacia la derecha del espectador
  (`facing.getCounterClockWise()`) y `height` hacia arriba, sobre el plano de la cara del ancla. Las
  demás celdas pueden ser cualquier bloque sólido (madera, ladrillo…); el BER pinta por encima,
  0,004 bloques por delante de la cara.
- Tamaño y modo de columnas: los datos del block entity (`{"width": 5, "height": 3, "columns": "status"}`)
  ganan; si faltan, la config. Máximo 16×8.
- Puesto a mano: mirando a la pared, el bloque queda con la cara hacia ti; ponlo en la esquina
  inferior izquierda de una pared plana de 5×3 (o del tamaño configurado).

## Qué dibuja

- Tablero de nogal con borde de latón; por columna, cabecera de papel con su nombre, el número de
  hilos y una línea del color del estado (colores de `ChatTheme.warmStudio()`).
- Una tarjeta por hilo no archivado: franja del color de su atención, título recortado, `EnvBadge`
  del entorno (color configurado en `t3craft.json` si lo hay) y una segunda línea: el proyecto (modo
  estado), la palabra del estado (modo proyecto) o estado · proyecto (modo entorno), y `⏳n` si
  el hilo tiene `n` ejecuciones en cola.
- Orden dentro de cada columna: el del chat (`ThreadOrder`: fijados, atención, actividad más
  reciente, título).
- **"+N more"**: si una columna no cabe, la última fila es un chip con los ocultos; clic → el primer
  oculto.
- **Hover**: borde de latón en la tarjeta bajo la mira. Las tarjetas *needs you* llevan un anillo
  que late.
- **Animación**: al cambiar de columna, la tarjeta vuela (más cerca, con sombra) desde donde estaba
  hasta su nuevo sitio y brilla 1,2 s al llegar. Las demás se deslizan a sus huecos.
- Sin hilos: "No threads yet".

## Variantes (`columns`)

| Valor | Columnas |
|---|---|
| `status` (defecto) | Working (working) · Needs you (needs you + approval pending) · Error · Done (done + idle) |
| `project` | Una por proyecto **y entorno** (dos "api" de entornos distintos no se mezclan); color = el hilo más urgente |
| `environment` | Una por entorno; color = el del `EnvBadge` |

En `project`/`environment` las columnas van en orden alfabético (no saltan) y, si no caben
(~64 px por columna), las sobrantes se juntan en "Other".

## Config (aditiva)

```json
"blocks": {
  "threadWall": { "width": 5, "height": 3, "columns": "status", "detailDistance": 24, "viewDistance": 64 }
}
```

Todas las claves son opcionales; tipo incorrecto → defecto. `detailDistance`: hasta dónde se
dibuja texto y hover; `viewDistance`: hasta dónde se dibujan las tarjetas de colores (más allá,
nada). Se relee con el hot-reload (`DevReload`) o al reiniciar.

## Datos y rendimiento

- **Solo lectura**: las filas del chat (`ChatScreens.defaultDirectory()`, que salen de
  `subscribeShell` y ya mantiene el cliente) y el contador de cola que el módulo queue ya observa
  (`QueueClient.queued(npcKey)`, nuevo accessor; solo se conoce para los hilos que el queue vigila:
  los working, hasta 4). Sin streams propios.
- Política de visibilidad: se leen cada 10 ticks **solo** si alguna pared se dibujó en los últimos
  100 ticks; las paredes sin dibujar en 600 ticks se olvidan.
- **Caché por versión**: `WallBoard.sync` compara la lista de tarjetas por valor; solo si cambió
  (o el modo o el tamaño) reagrupa y recalcula el layout y sube `version()`. El renderer reconstruye
  los `FormattedCharSequence` solo cuando cambia esa versión. Una pared quieta cuesta una comparación
  de lista por frame más el dibujo.
- **LOD** por distancia al centro de la pared: `FULL` (texto, badges, hover) ≤ `detailDistance`;
  `SIMPLE` (carriles y tarjetas de color, sin texto) ≤ `viewDistance`; más allá no se dibuja.
- `shouldRenderOffScreen() = true`: la pared es más grande que su bloque ancla.

## Clic

`UseBlockCallback` de cliente (en `BlocksClient`): si el bloque golpeado es una celda de alguna
pared conocida, la cara es la del frente y el punto está en el plano, se pasa a coordenadas de
tablero (`WallGeometry.toBoard`) y se hace hit-test (`WallLayout.Layout.hit`). Tarjeta → abre ese
hilo; chip → el primer oculto. Se abre con el `ThreadOpener` de las workstations (o
`ChatScreenOpener`), como los monitores. Devuelve `FAIL` en el cliente: consumido, no llega
paquete al servidor. Con sneak no hace nada (para poner bloques). Usar el ancla fuera de una
tarjeta se consume sin efecto.

## En el plan del HQ (para feat/hq-style-cherry)

El plan reserva la pared con dos anclas (`AnchorNames`):

- `thread_wall` (`AnchorNames.THREAD_WALL`): `block` = celda inferior izquierda vista de frente,
  `facing` = normal hacia fuera de la cara. El plan **debe poner un bloque sólido** en esa celda
  (y en el resto de la pared): el módulo blocks cambia solo esa celda por `t3craft:thread_board`
  mirando a `facing`.
- `thread_wall_end` (opcional, `THREAD_WALL_END`): celda superior derecha, en el mismo plano. Con
  las dos, `HqBindings.threadWall(plan)` calcula ancho y alto y `HqBlockFixtures` los escribe como
  datos del block entity. Sin ella, manda la config.

```java
b.anchor(Anchor.face(AnchorNames.THREAD_WALL, bottomLeft, Direction.SOUTH));
b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, topRight, Direction.SOUTH));
```

**Sin el módulo blocks** (`HqFixtures.VANILLA`) no se cambia nada: la pared queda con los bloques
del plan y nada se rompe. El HQ actual no publica esas anclas todavía, así que hoy no cambia
(`HqBlockFixtures.ID` sigue en `blocks-2`).

## Piezas

| Clase | Qué es |
|---|---|
| `blocks.ThreadBoardBlock` | Bloque con `facing`; uso consumido (el clic lo resuelve el cliente) |
| `blocks.ThreadBoardBlockEntity` | `width`, `height`, `columns` opcionales; guardados y sincronizados |
| `blocks.HqBindings.threadWall` / `wallSize` | Pared del plan (pura) |
| `blocks.HqBlockFixtures` | Cambia la celda ancla (`States.threadBoard`, default null = no cambiar) |
| `client.blocks.WallCard` | Tarjeta desde `ThreadRow` (+ badge, cola) |
| `client.blocks.WallColumns` | Agrupación por estado / proyecto / entorno |
| `client.blocks.WallLayout` | Layout en px de tablero, "+N more", hit-test, recorte |
| `client.blocks.WallBoard` | Caché por versión y animación (vuelo, brillo) |
| `client.blocks.WallGeometry` | Mundo ⇄ tablero, celdas de la pared, LOD |
| `client.blocks.ThreadWallConfig` | `blocks.threadWall` |
| `client.blocks.ThreadWalls` | Paredes vistas, lectura de datos, hit-test del clic |
| `client.blocks.ThreadBoardRenderer` | El BER |

Tests: `client/blocks/ThreadWallTest` (agrupación, orden, "+N more", hit-test mundo→tarjeta en las
4 orientaciones, caché por versión, animación, variantes, config, LOD) y
`blocks/ThreadWallPlanTest` (anclas del plan, tamaño, cambio de la celda, sin módulo).

Receta: 8 tablones alrededor de un monitor. Textura propia (16×16, generada).
