# feat/archive-view: estante "Archivados" con datos reales

> **Reemplazado en parte por feat/archive-terminal** (`docs/features/archive-terminal.md`): el archivo ya
> no se muestra en slots ni estantes del mundo. `ArchiveRenderer`, `ArchiveShelf`, `ArchiveSlots` y
> `CatalogScreen` se borraron; la lista vive en `ArchiveScreen` (terminal `t3craft:archive_terminal`,
> `/t3craft archive` o el portátil). Siguen vigentes los datos: `ArchiveStore`, `ArchiveSource`,
> `ArchiveEntry`, `ArchiveWire` y las lecturas de `T3Connection`.

Ítem 13 de `docs/research/inventario.md`. Paquete `dev.t3craft.client.archive` (source set
`client`) y decodificadores en `dev.t3craft.t3.ArchiveWire` (`main`). Tests en
`mod/src/test/java/dev/t3craft/client/archive/` y `dev/t3craft/t3/ArchiveWireTest.java`.

> Cambio de diseño (Facundo): el sótano de archivo sale del layout por defecto. Lo reemplaza un
> **estante de "Archivados" dentro del edificio** (fix/hq-archive), con anchors con nombre
> `archiveShelfSlot(i)` en el plan. Este módulo muestra ahí los hilos cerrados. Las posiciones
> pasan por la interfaz `ArchiveShelf` y, mientras fix/hq-archive no esté en main, se usa un
> estante de fallback junto al tablero.

## Qué hace

- **Slots del estante**: cada hilo cerrado (settled o archivado) que cabe en el estante muestra en
  la cara de su slot un letrero de cliente:

  ```
  Fix login redirect l…     título (blanco, 22 caracteres como máximo)
  ● vps                     entorno, en su color del HQ
  ✔ 2026-11-03              ✔ settled / ▣ archivado + fecha de cierre (zona local)
  t3craft                   proyecto
  ```

  Un clic en un slot ocupado abre el hilo en solo lectura. Un clic en un slot vacío o en el cartel
  **"Archivados · N hilos cerrados"**, centrado sobre el estante, abre el catálogo.
- **Catálogo** (`CatalogScreen`, también con `/t3craft archive`): todos los hilos cerrados, primero
  los cerrados más recientemente, paginados (`<` `>` y la rueda del ratón). Incluye los que no caben
  en el estante, marcados con `■` los que tienen slot. **Más viejos** lee la página siguiente de T3
  y **Refrescar** relee todo. Un clic en una fila abre el hilo en solo lectura.
- Comandos: `/t3craft archive` (abre el catálogo), `refresh` y `status`.

## Posiciones: `ArchiveShelf`

```java
public interface ArchiveShelf {
    List<Anchor> slots();          // relativos al origen del HQ: celda del slot + cara visible (normal hacia fuera)
    static ArchiveShelf fromPlan(BuildPlan plan, IntFunction<String> name);   // anchors name(0), name(1)… hasta el primero que falte
    ArchiveShelf FALLBACK;          // estante junto al tablero (abajo)
}
```

- **Plan del HQ** (fix/hq-archive): si el plan del HQ en pie (`HqPresence.current()`) tiene anchors
  `shelf@0`, `shelf@1`… (los nombres de `AnchorNames.archiveShelfSlot(i)` en esa rama), se usan
  automáticamente con `ArchiveShelf.fromPlan`. El nombre está escrito en
  `ArchiveClient.PLAN_SHELF_SLOT` para no depender de esa rama. Cuando entre a main, se puede
  cambiar por `AnchorNames::archiveShelfSlot`. `setShelf(...)` lo sobrescribe. El orden de los
  anchors es el orden de los slots: el 0 es el primero que se ocupa.
- **Fallback** (activo hoy): dos alas a los lados del tablero de "needs attention" del hub
  (`x = -7..-5` y `5..7`, `z = -4`, `y = 1..4`, mirando al sur como el tablero), sin las dos
  esquinas bajas que tapan las macetas. Son **22 slots**. El slot 0 está a la altura de los ojos y
  junto al tablero. Un test comprueba contra el generador que esas celdas y las de delante son aire
  en todos los planes, que están en el grupo `hub` y en línea con el tablero. Allí no se construye
  nada: mientras la celda es aire se dibuja un prop de cliente (una librería cincelada vanilla,
  llena si el slot está ocupado y vacía si no).
- El cartel se calcula a partir de los slots: centrado sobre ellos, justo encima de la fila más
  alta y en el plano de las caras.

## Slots: `ArchiveSlots`

Son las mismas reglas que el archivo de `HqLayout` (regla 5), con la capacidad del estante:
1. los `capacity` hilos cerrados más recientemente tienen slot; el resto aparece solo en el
   catálogo, así que un cierre nuevo desplaza al más viejo y ningún otro se mueve;
2. un hilo que ya tenía slot lo conserva;
3. los nuevos, en orden `(project key, key)`, toman el slot libre más bajo.

Las claves son `<env>:<thread>`, igual que en `NpcView.id()`. Un test comprueba que, con la
capacidad de `HqLayout`, la asignación coincide con `HqLayout.allocate` sobre el roster completo
(activos incluidos) en una historia aleatoria de 30 pasos.

## Datos (solo lectura)

| Fuente | Qué aporta |
|---|---|
| `subscribeShell` (vista existente, `DirectController.shellViews()`) | settled en vivo, sin costo extra. Es la fuente prioritaria: un hilo activo en el shell sale del archivo. |
| `orchestration.getArchivedShellSnapshot` `{}` | hilos archivados y los nombres de sus proyectos |
| `orchestration.getOlderSettledThreads` `{before:{settledAt, threadId?}, limit≤50}` | settled más viejos, por páginas (`settledAt` desc, id asc); el cursor sale del último hilo de la página |

`ArchiveEntry.isCandidate`: no está borrado, es un hilo de nivel superior (sin subagentes ni side
chats) y está archivado o settled (la misma regla que `Visibility.isSettled`).

**Caché y refresco** (`ArchiveStore`, puro y testeado): por entorno conectado se leen el snapshot
archivado y la primera página al conectar, y después cada **10 min**. Las páginas siguientes solo
se leen a pedido (botón **Más viejos**). Ante un fallo hay backoff de 30 s que se duplica en cada
intento consecutivo de ese tipo de lectura, hasta 10 min. El refresco manual se ignora si la
última lectura tiene menos de 30 s. Nunca hay dos lecturas del mismo tipo en curso por entorno. Los
resultados se aplican en el tick del cliente.

### Vía de peticiones

No hay una vía propia: las dos lecturas son métodos finos de `T3Connection` sobre la vía
compartida de feat/chat (`streams.call(tag, payload)`), con un solo espacio de ids, envíos
serializados y sin socket propio:

```java
public CompletableFuture<ArchiveWire.OlderSettledPage> getOlderSettledThreads(ArchiveWire.Cursor before, int limit)
public CompletableFuture<ArchiveWire.ArchivedSnapshot> getArchivedShellSnapshot()
```

El paquete solo ve la interfaz `ArchiveSource`. El default es `ArchiveSource.direct(...)`, que usa
`DirectController.connection(envId)`. También están `ArchiveSource.of(Caller)`, para un llamador
crudo, y `ArchiveSource.UNAVAILABLE`, que deja solo los settled del shell. En modo bridge no hay
entornos directos y el catálogo lo avisa.

## Render

Solo entidades de cliente: un `TextDisplay` FIXED en la cara de cada slot ocupado (escala 0,18, a
pleno brillo, fondo oscuro); un `BlockDisplay` como prop y un `Interaction` mientras la celda del
slot es aire; y el cartel (`TextDisplay` con su `Interaction`). No se escriben bloques en el mundo,
no hay registros nuevos y nada lo ve el servidor.

**Ids de entidad: desde −6.000.000 hacia abajo** (`ArchiveRenderer.ID_BASE`). Rangos ocupados:
NpcRegistry −1.000.000, builders −2.000.000, BehaviorDirector −3.000.000, ayudantes −4.000.000 y
workstation −5.000.000. Si el barrido de huérfanos de `NpcRegistry` borra alguna entidad, se
reconstruye en el tick siguiente (`isRemoved()`).

Clics: con la mano principal y sin agacharse, sobre la celda de un slot (cuando hay bloque real),
se devuelve `FAIL`: no se envía nada y no se ejecuta la acción propia del bloque. Sobre nuestras
entidades, `SUCCESS`. Los ataques a nuestras entidades se cancelan con `FAIL`.

## Abrir en solo lectura

`ArchiveOpener.DEFAULT`: si existe `ChatScreens` de feat/chat (`PortableLinks.hasChat()`), se usa
ese. Si no, se abre la `ThreadChatScreen` de main con un `NpcInfo` sintético settled. El registro
de NPCs no tiene hilos cerrados.

## Puntos de integración (`ArchiveClient.get()`)

- Sitio: por defecto, el HQ que está en pie en el mundo (`HqPresence.current()` de
  feat/hq-construction, sitio o demo; solo lectura), una vez construido su grupo `hub`.
  `setSite(HqSite)` lo sobrescribe. Sin HQ no se dibuja nada en el mundo, pero el catálogo funciona
  igual.
- `setShelf(ArchiveShelf)`: fuerza unas posiciones (`null` = las del plan en pie o el fallback).
- `slots()`: la asignación actual (clave → slot), para que el dueño del HQ ponga el bloque
  correspondiente en cada slot.
- `setCaller(Caller)` / `setSource(ArchiveSource)`: cambiar de dónde se lee.
- `setOpener(ArchiveOpener)`: otro visor.
- **Hook para feat/blocks-core**: `setSlotProps((occupied, facing) -> BlockState)`, el bloque que
  se muestra en un slot de aire (por ejemplo, el `t3craft:monitor`). No depende de blocks.

## Cambios estructurales → **RESTART**

- `fabric.mod.json`: nuevo entrypoint `client` `dev.t3craft.client.archive.ArchiveClient`.
- Callbacks de cliente: `UseBlockCallback`, `UseEntityCallback`, `AttackEntityCallback`, tick
  (`GuardedEvents`, módulo `archive`), `CLIENT_STOPPING` y `DevReload` (`t3craft.archive`).
- Comando `/t3craft archive [refresh|status]`.
- Fuera del paquete: `T3Connection.getOlderSettledThreads` / `getArchivedShellSnapshot` (sobre
  `streams.call`), `DirectController.labels()` (aditivos), `dev.t3craft.t3.ArchiveWire` (nuevo) y
  claves de lang `t3craft.archive.*`.
- Sin mixins, keybinds, bloques, ítems, registros ni config. No toca `hq`.

## Pendientes

- **fix/hq-archive** (en origin, todavía no en main): su estante (pared sur del hub, `|x| = 3..6`,
  `z = 7`, 3 niveles) se detecta solo por los anchors `shelf@i`. Al entrar, cambiar
  `PLAN_SHELF_SLOT` por `AnchorNames::archiveShelfSlot` y decidir si el fallback junto al tablero
  sigue haciendo falta (no choca con ese estante). Si fix/hq-archive cambia la regla de asignación del
  archivo de `HqLayout`, revisar `ArchiveSlotsTest.matchesHqLayoutRules`.
- **No se probó dentro del juego** (por regla, no se lanza Minecraft). Falta revisar a ojo la escala
  y la altura del letrero, que el texto no quede espejado y que los `Interaction` reciban el clic.
- La caché vive en memoria: al reiniciar se relee (son 2 lecturas por entorno).
- No se copió código de AgentCraft.
