# feat/19-attention-queue: cola de atención

Issue #19. Plan: `docs/plans/ui-browser-and-integrations.md`, frente D. Investigación:
`docs/research/game-ui-and-integrations.md`, G3 y G4. Paquete: `mod/src/client/java/dev/t3craft/client/attention/`
(cliente puro).

## Qué hace

Cierra el circuito "me avisan → llego al hilo → respondo" sin pasar por la lista:

1. **Tecla "siguiente hilo que me necesita"** (`key.t3craft.attention_next`): abre el siguiente hilo urgente.
2. **Tecla "volver al último hilo abierto"** (`key.t3craft.attention_last`).
3. **Flecha en el borde del HUD** hacia el NPC del hilo más urgente, del color de su estado.

Las dos teclas están **sin asignar** por defecto, en la categoría que ya existe (Opciones › Controles › T3Craft).

## Teclas

### Siguiente hilo que me necesita
- **Cola**: las filas de `ChatScreens.defaultDirectory().rows()` con `Attention.urgent()` (necesita atención,
  error o aprobación pendiente) y con id de hilo. Orden de urgencia de la lista, **sin** el "fijados primero": un
  hilo fijado no pasa por delante de uno bloqueado esperándote. Dentro de cada clase, el más reciente primero.
- **Cicla**: cada pulsación abre el siguiente al último al que saltó (con vuelta al principio). Si ese hilo ya no
  está en la cola (respondido), o pasaron más de 30 s desde la última pulsación, empieza otra vez por el más urgente.
- **Reusa el aviso de chat**: en una pulsación que *no* continúa un ciclo, si un aviso de "necesita entrada" llegó en los
  últimos 2 minutos y ese hilo sigue urgente, se abre ese primero (`Notifier.takeRecentAsk`, el mismo que usa la tecla `Y`).
- **Dónde queda el foco**: se abre `ChatScreens.create(key)`, la misma pantalla que el botón `[Answer]`. Si el hilo tiene
  una pregunta o aprobación pendiente, su tarjeta es lo que recibe el foco; si no, la pantalla del hilo la maneja como siempre.
  No se tocó `T3Screen`.
- Nadie espera: mensaje en la barra de acción (`t3craft.attention.nobody`).

### Volver al último hilo abierto
- Cada tick, si la pantalla abierta es `T3Screen`, su hilo seleccionado (`selectedKey()`) pasa a ser el "último abierto",
  sea cual sea la forma de abrirlo (tecla, NPC, escritorio, terminal). Solo cuenta si el hilo está en la lista (los
  borradores no).
- La tecla vuelve a abrirlo con `ChatScreens.create(key)`. Sin hilo previo: `t3craft.attention.no_last`.

### Aprobar y denegar
**No hay tecla que apruebe o niegue.** Las teclas solo abren el hilo; lo destructivo sigue pasando por la
confirmación de la tarjeta.

## Flecha en el borde del HUD

- Elemento HUD `t3craft:attention_arrow`, registrado con `HudElementRegistry.addLast` **después** de la barra de estado.
- **Objetivo**: el hilo más urgente de la cola **cuyo NPC está en el mundo** (`NpcRegistry.position`). Si el primero no
  tiene NPC (sin puesto o aún sin cargar), apunta al siguiente que sí.
- **Dibujo**: un triángulo (filas con `fill`), con sombra, del color de estado del hilo (`ThreadState.color`; una
  aprobación pendiente sobre un hilo trabajando se pinta como "necesita atención"), y debajo o encima su título
  recortado y la distancia (`Fix login · 23m`).
- **Geometría** (`AttentionIndicator`, Java puro): solo el rumbo horizontal entre la mirada del jugador y el NPC; el
  punto se halla trazando un rayo desde el centro de la pantalla hasta el borde del área libre.
- **No se pelea con lo demás**:
  - *Nombres flotantes*: la flecha **se oculta** si el NPC está dentro de ±28° de donde miras (ahí ya se lee su nombre) o a
    menos de 3 bloques.
  - *Barra de estado y línea del `PeekHud`*: el borde superior (o inferior, según `hud.position`) se acorta lo que ocupan
    la línea `PeekHud` y la barra con su insignia; el inferior reserva además la hotbar y la vida.
  - Se oculta con **F1** y con **cualquier pantalla abierta** (igual que la barra).

## Configuración

Aditiva (sin tocar nada existente):

| Clave en `config/t3craft.json` | Defecto | Efecto |
|---|---|---|
| `hud.attentionArrow` | `true` | Muestra u oculta la flecha. Con `false` tampoco se calcula la cola. También en Ajustes (J), junto a "Hub hologram". |

Las teclas se reasignan en Opciones › Controles › T3Craft, o se dejan sin asignar.

## Código

| Archivo | Papel |
|---|---|
| `attention/AttentionQueue` | Orden de urgencia, cursor del ciclo, último abierto. Puro. |
| `attention/AttentionIndicator` | Rumbo, punto en el borde y filas del triángulo. Puro. |
| `attention/AttentionClient` | Registro del HUD y del tick, acciones de las dos teclas. |
| `config/T3CraftKeys` | Dos `KeyMapping` nuevos y un bloque `tickAttention`. |
| `config/T3CraftSettings.Hud` | Campo `attentionArrow` (con constructores de las formas anteriores). |
| `config/ConfigCodec`, `config/SettingsScreen` | Lectura y escritura del campo, y su interruptor. |
| `T3CraftClient` | Una línea: `AttentionClient.register(this)`. |
| `lang/*.json` | Teclas y dos mensajes. |

`Notifier.java` **no se modificó**: lo único que se usa de él es `takeRecentAsk`.

## Límites

- La flecha necesita el NPC cargado; no apunta a un escritorio o podio vacío (un hilo sin NPC no se señala).
- Solo rumbo horizontal: no indica si el NPC está arriba o abajo.
