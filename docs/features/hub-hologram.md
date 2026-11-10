# feat/hub-hologram: holograma del hub

P4 de `docs/research/agentcraft-world-elements.md`. Paquete: `mod/src/client/java/dev/t3craft/client/hologram/`
(cliente puro). Tests: `mod/src/test/java/dev/t3craft/client/hologram/HologramModelTest.java` (19 tests JUnit).

## Qué hace

Sobre el centro del hub del HQ flota una tarjeta con el resumen de **todos los entornos**:

```
        ⚙ 3   ! 1   ✖ 0   ✔ 4                    ← cabecera grande (se lee de lejos; los ceros, atenuados)
  3 working · 1 needs you · 0 error · 4 done today
  ◕ t3craft 4/5   ◑ demo 1/2   +1                ← un anillo por entorno (o por proyecto)
  ! Fix login redirect · needs you · t3craft     ← el hilo más urgente, o "All quiet"
  Offline · last known state                     ← solo si ningún entorno está sincronizado
               ┃                                 ← haz de luz desde el suelo, del color del estado más urgente
```

- **Conteos**: hilos visibles y no archivados del roster de NPCs (los mismos que la barra del HUD).
  *Done today*: en modo directo, hilos de las vistas de shell (sin filtrar, así cuentan también los ya
  archivados) con estado `done`, no borrados, sin padre (los subagentes no cuentan) y cuyo
  `latestRunCompletedAt` (o, si falta, `settledAt`) cae desde la medianoche local. En modo bridge, sin
  marcas de tiempo, cuenta los NPC en `done`.
- **Anillos**: uno por entorno si hay varios; si hay uno solo, uno por proyecto. El relleno (○ ◔ ◑ ◕ ●,
  por cuartos) es `done/total`, como el anillo de AgentCraft que sigue el conteo de hechos, y el texto
  dice `done/total`. Color: el estado más urgente del grupo. Orden: más urgente, más grande, nombre.
  Máximo 4, luego `+N`.
- **Más urgente**: needs you › error › working; empate por prioridad del NPC y luego por título.
- **Haz**: bloque display de vidrio teñido, fino y full-bright: naranja (needs you), rojo (error),
  celeste (working), lima (done) o blanco (idle).

## Dónde

1. Ancla `hub` del HQ en pie (`HqPresence.current()`: sitio o demo), si su módulo ya está construido.
2. Si no, el ancla `board` del HQ.
3. Sin HQ: encima del tablero de la cuadrícula temporal de NPCs (`NpcRegistry.fallbackLayout().board()`).
4. Sin nada de eso: no se muestra.

## Legibilidad

Alturas en `HologramLayout`, sobre el nivel del suelo del ancla: la tarjeta empieza a 2,95 bloques,
por encima de la franja de nametags (nombre ≈ 2,2, panel de detalle hasta ≈ 2,8), así que desde la
altura de los ojos (1,62) nunca tapa un nametag; la parte de arriba queda bajo el farol colgante del
hub (5 bloques). Cabecera a escala 1,4 y detalle a 0,6, ambos billboard `CENTER`, full-bright y con
rango de vista ×4. Un test comprueba estos límites.

## Cómo funciona

- `HologramModel` (puro, sin clases de Minecraft): `build(Inputs)` → conteos, anillos, más urgente,
  estado de cabecera y líneas de texto. `doneToday(shells, dayStart)`, `startOfDay(now, zone)`,
  `doneFromStates(entries)`.
- `HologramState`: guarda los últimos `Inputs` y **solo reconstruye el modelo si cambian**
  (`builds()` los cuenta). El *done today* se recuenta solo si cambió alguna vista de shell (por
  identidad) o el día.
- `HologramRenderer`: tres entidades display de cliente (cabecera, detalle, haz). Se crean una vez por
  ancla, se mueven solo si el ancla cambia y el texto se empuja solo con un modelo nuevo. Si alguna
  desaparece (barrido de huérfanos de `NpcRegistry`), se recrean.
- `HologramClient`: entrypoint de cliente (`fabric.mod.json`), tick con `GuardedEvents` (módulo
  `hologram`) y `DevReload` (`t3craft.hologram`).

**Ids de entidad**: rango propio desde **−8.000.000** hacia abajo (`HologramRenderer.ID_BASE`, banda de
100.000). Rangos ocupados: NpcRegistry −1M, builders −2M, BehaviorDirector −3M, helpers −4M,
workstations −5M, estante de archivados −6M, tablero de tareas programadas −7M.

## Config

`hud.hologram` (booleano, por defecto `true`), aditivo: un archivo sin la clave lo deja encendido.
Toggle "Hub hologram" en la pantalla de ajustes (si cabe la fila). Apagado → nada en el mundo.

```json
"hud": { "statusBar": true, "position": "top", "align": "center", "hideWhenIdle": true, "hologram": true }
```

## Fuera de alcance / pendiente

- Sin bloques ni registros; no toca el módulo blocks. Funciona en modo GUI-only.
- "Próxima tarea programada": feat/schedule-board aún no está en main; se puede añadir como línea
  extra de `detail()` cuando entre su fuente.
- Sin declutter con nametags (`PlateLayout` de AgentCraft): se resuelve por altura.

Adaptado de AgentCraft (MIT), `StatusLampRenderer` (holograma del Goal Atrium); ver `THIRD_PARTY_NOTICES.md`.

**Update (world text, #34):** in the default `labels.style: "hologram"` the hologram is one card with a
code-drawn progress ring, its `done/total` caption and a soft beam, plus bubbles over the decision
podiums while a decision waits; `labels.hologram.hubHologram: false` switches both off. The lines
above are the `"classic"` style. See `docs/features/world-text.md`.
