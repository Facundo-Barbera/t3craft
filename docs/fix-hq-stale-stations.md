# fix/hq-stale-stations: los hilos terminados sueltan su puesto

## El bug

En el HQ del demo quedaban puestos (escritorio con computadora, pantalla y nametag) de hilos que ya
habían terminado (✓ DONE) o que ya no existían. Las causas eran tres:

1. **Nadie soltaba el puesto.** La visibilidad del modo directo muestra los hilos con actividad de
   las últimas 24 h, así que un hilo DONE conservaba NPC, pantalla y escritorio durante un día entero.
2. **El demo construía el HQ una sola vez**, unos 40 ticks después de entrar al mundo, muchas veces
   antes de que llegara el primer shell, y no volvía a mirar el roster. Los escritorios de esa sesión
   quedaban para siempre (hasta `demo rebuild`).
3. **Un escritorio libre seguía en el mundo**: el generador dibujaba los escritorios vacantes
   (computadora gris, silla, lámpara), así que un hilo que se iba dejaba un escritorio igual.

## Política (`dev.t3craft.hq.StationPolicy`)

La regla está en un solo lugar. Un hilo **suelta su puesto** cuando:

| Veredicto | Condición |
|---|---|
| `SETTLED` | settled |
| `ARCHIVED` | archivado |
| `DONE_IDLE` | `done` y sin actividad (último mensaje o run) desde hace **más** de X minutos |

Todo lo demás conserva el puesto: `working`, `needs_attention`, `error`, `idle` y un `done`
reciente. Si la fuente no trae marcas de tiempo (modo bridge), la inactividad se cuenta desde la
primera vez que se vio el hilo en `done` (`StationPolicy.DoneClock`).

Config (aditiva, todas las claves son opcionales):

```json
"stations": { "release": true, "releaseDoneAfterMinutes": 5 }
```

`release: false` vuelve al comportamiento anterior. Se lee al arrancar la fuente y en `/t3craft reload`.

### Dónde se aplica

`NpcRegistry` guarda lo que mandan las fuentes en un `StationRoster`, que aplica la política al
recibir datos y cada 10 s (así un `done` suelta el puesto cuando le toca, aunque no cambie nada más).

- `npcs.stationed()` devuelve **solo los hilos con puesto**. De ahí salen el NPC y su nametag (el
  registro solo crea esos), las workstations (`WorkstationClient`) y los escritorios. Un hilo que
  suelta el puesto pierde NPC, nametag y pantallas en el siguiente tick.
- `npcs.released()` devuelve los hilos que soltaron el puesto, marcados `settled = true`, y
  `npcs.hqRoster()` = `stationed()` + `released()` es el roster del HQ: el del demo, el de site
  (`HqConstructionClient.liveRoster`) y el de `hq-preview`. Los liberados van al **archivo**.
- `npcs.all()` no cambia: es todo lo que manda la fuente. Lo siguen usando el chat, la búsqueda,
  operate, la barra de estado, el portable y los bindings de bloques, porque no son puestos.
- En el modo directo, `T3CraftClient.refreshDirect` pasa la última actividad real de cada hilo
  (`Visibility.lastActivity` de su `ThreadShell`).

**Archivo:** el destino es `HqLayout.archive` (los hilos `settled` del `HqInput`). Hoy es el sótano.
Cuando entre fix/hq-archive (estante "Archivados", `archiveShelfSlot(i)`), el cambio queda dentro
del generador y este hilo no tiene que tocar nada: los hilos liberados ya llegan como `settled`.

## El diff también quita (`dev.t3craft.hq.HqDiff`)

`HqDiff.between(previous, next, vacated)` es el paso común de reconciliación para el demo y para
feat/hq-construction:

- `removals()`: celdas que `previous` construyó y `next` ya no usa (un módulo liberado, la fila de
  espina que necesitaba), restauradas con `vacated`, más las celdas que `next` pasa de bloque a aire
  (un escritorio liberado). Van agrupadas por el grupo dueño en `previous`, en orden inverso del
  plan: decoraciones y objetos colgantes antes que lo que los sostiene.
- `builds()`: el resto de los cambios, por grupo de `next`.
- `parts()`: partes `remove:<grupo>` primero (orden propio), después las de construcción (por capas).
- Mismo roster ⇒ mismo edificio y diff vacío (test de idempotencia).

**Escritorios vacantes:** ya no se construyen. Solo queda el piso. Tampoco hay `Workstation` ni
anchors `desk_slot_*` para ellos, así que `HqBindings` no pone un monitor en el aire. Cuando un hilo
nuevo toma el lugar, el escritorio vuelve a aparecer en el mismo sitio.

**Supervivencia / site:** `HqDiff.restoring(original, fallback)` arma el `Vacated` que restaura el
bloque original. Para el site, `original` lee la celda del `Snapshot` de la ampliación
(`site.cell(rel)` → índice de la caja → `StateKey` → `BlockState`), de modo que `site undo` siga
reconociendo esas celdas como "nuestras". El cableado le toca a feat/hq-construction, porque no está
en main.

`HqPlans.expansion` sigue igual por compatibilidad (también vaciaba celdas, pero no separaba las
remociones ni quitaba escritorios).

## Demo

- `DemoHq.step(...)` usa `HqDiff`: primero las remociones, después el plinto (que también se encoge
  si se encoge la huella, con `plinthRemoval`) y por último las construcciones.
- `PLAN_VERSION = 2`: los mundos existentes se reconstruyen una vez. Esa reconstrucción completa
  también limpia las celdas del layout anterior que el nuevo ya no usa (`clearPrevious`), así que no
  quedan módulos ni escritorios de sesiones viejas.
- **Al cargar el mundo** (`DemoClient.tick`), el HQ espera con `RosterGate` a que **cada entorno
  emparejado haya mandado su primer shell** (en modo bridge, el primer snapshot). Sin entornos
  emparejados, reconcilia enseguida (roster de ejemplo). Si el mundo todavía no tiene HQ, después de
  30 s construye con lo que haya. Si ya tiene uno, **espera sin límite**: nunca derriba los puestos
  de un entorno que sigue conectando.
- Después, cada 5 s compara el roster con el último preparado (`DemoHqSource.rosterChanged()`). Si
  cambió (un hilo tomó o soltó un puesto), aplica el diff sin avisar en el chat; solo lo registra en
  el log.

## Entidades huérfanas (`dev.t3craft.client.ClientEntityIds`)

| Rango | Dueño |
|---|---|
| −1.000.000 … −1.999.999 | `NpcRegistry` (avatares, nametags, letreros) |
| −2.000.000 … −2.999.999 | builders |
| −3.000.000 … −3.999.999 | `BehaviorDirector` |
| −4.000.000 … −4.999.999 | `HelperDirector` |
| −5.000.000 … −5.999.999 | workstations |

- Cada dueño barre **solo su rango**. Antes, el barrido de `NpcRegistry` trataba como suyo todo id
  ≤ −1.000.000 y podía borrar workstations, ayudantes y cuadrillas de builders.
- `NpcRegistry` barre su rango cada 10 s y, en una recuperación, limpia su rango y el de behaviour.
- `WorkstationRenderer.sweep` descarta, en el primer tick y después cada 10 s, toda entidad del rango
  de workstations que no pertenezca a una estación viva (pantalla, prop o hitbox de un hilo que soltó
  el puesto, o restos de un renderer anterior).

## Tests

- `StationPolicyTest`: settled, archivado, DONE inactivo pasado el umbral, DONE reciente (y justo en
  el umbral) que se queda, otros estados, umbral configurable, política apagada, `DoneClock` y
  parseo de config.
- `RosterGateTest`: espera el primer sync de cada entorno, sin entornos, timeout solo en la primera
  construcción, bridge.
- `HqDiffTest`: remoción de un módulo y de un escritorio (computadora, lámpara y silla, en orden
  inverso), idempotencia con el roster desordenado, primera construcción sin remociones y `restoring`.
- `StationRosterTest`: los hilos liberados salen del mundo y van al archivo; un `done` suelta el
  puesto a su hora; bridge sin timestamps.
- `ClientEntityIdsTest`: rangos disjuntos; los huérfanos solo salen del rango propio.
- `DemoHqTest`: un escritorio liberado desaparece (el mundo queda igual que una construcción nueva),
  la reconstrucción por versión limpia lo sobrante, el mismo roster en cualquier orden no construye
  nada y la fuente detecta el cambio de roster.
- `HqGeneratorTest` se actualizó: los escritorios vacantes ya no existen.

## Pendiente / notas

- feat/hq-construction (HQ de site) entró a main mientras se hacía este fix. Su diff ya restaura el
  snapshot del sitio (`CellChange.restore`), y su `liveRoster` ahora usa `npcs.hqRoster()`, así que
  la política le llega sin tocar su orquestación. Su propia espera de sync (todos los entornos en
  "ok") sigue igual. `HqDiff`/`RosterGate` quedan disponibles si quiere unificar.
- Si un entorno no conecta nunca, el HQ de un mundo ya construido no se reconcilia (a propósito) y
  los NPC no reciben los anchors del HQ hasta que conecte.
