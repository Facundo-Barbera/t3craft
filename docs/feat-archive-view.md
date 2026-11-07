# feat/archive-view: el archivo del sótano, con datos reales

Ítem 13 de `docs/research/inventario.md`. Paquete `dev.t3craft.client.archive` (source set
`client`) y decodificadores en `dev.t3craft.t3.ArchiveWire` (`main`). Tests en
`mod/src/test/java/dev/t3craft/client/archive/` y `dev/t3craft/t3/ArchiveWireTest.java`.

## Qué hace

- **Slots**: cada hilo cerrado (settled o archivado) que cabe en el archivo del HQ
  (`HqLayout.ARCHIVE_CAPACITY` = 148) tiene en la cara de su computadora un letrero de cliente:

  ```
  Fix login redirect l…     título (blanco, 22 caracteres como máximo)
  ● vps                     entorno, en su color del HQ
  ✔ 2026-11-03              ✔ settled / ▣ archivado + fecha de cierre (zona local)
  t3craft                   proyecto
  ```

  Al hacer clic en el slot (en el bloque, o en un hitbox mientras la celda es aire), el hilo se
  abre en solo lectura.
- **Catálogo**: un atril al pie de la escalera del sótano, en `(6, -4, 1)` relativo al HQ y mirando
  al oeste, hacia quien baja. Al usarlo, o con `/t3craft archive`, se abre `CatalogScreen`: todos
  los hilos cerrados, primero los cerrados más recientemente, paginados (`<` `>` y la rueda del
  ratón). Incluye los que no caben en los slots, marcados con `■` los que tienen slot. El botón
  **Más viejos** lee la página siguiente de T3 y **Refrescar** relee todo. Al hacer clic en una
  fila, el hilo se abre en solo lectura.
- Comandos: `/t3craft archive` (abre el catálogo), `refresh` y `status`.

## Datos (solo lectura)

| Fuente | Qué aporta |
|---|---|
| `subscribeShell` (vista ya existente, `DirectController.shellViews()`) | settled en vivo, sin costo extra. Es la fuente prioritaria: un hilo activo en el shell sale del archivo. |
| `orchestration.getArchivedShellSnapshot` `{}` | hilos archivados y los nombres de sus proyectos |
| `orchestration.getOlderSettledThreads` `{before:{settledAt, threadId?}, limit≤50}` | settled más viejos, por páginas (`settledAt` desc, id asc); el cursor sale del último hilo de la página |

`ArchiveEntry.isCandidate`: no está borrado, es un hilo de nivel superior (sin subagentes ni side
chats) y está archivado o settled (la misma regla que `Visibility.isSettled`).

**Caché y refresco** (`ArchiveStore`, puro y testeado): por entorno conectado se leen el snapshot
archivado y la primera página al conectar, y después cada **10 min**. Las páginas siguientes solo
se leen a pedido (botón **Más viejos**). Ante un fallo hay backoff de 30 s que se duplica en cada
intento, hasta 10 min. El refresco manual se ignora si la última lectura tiene menos de 30 s. Nunca
hay dos lecturas del mismo tipo en curso por entorno. Los resultados se aplican en el tick del
cliente. Las páginas viejas que no traen nombre de proyecto lo toman del shell o del snapshot.

### Vía de peticiones

No hay una vía propia: las dos lecturas son métodos finos de `T3Connection` sobre la vía
compartida de feat/chat (`streams.call(tag, payload)`), con un solo espacio de ids, envíos
serializados y sin socket propio:

```java
public CompletableFuture<ArchiveWire.OlderSettledPage> getOlderSettledThreads(ArchiveWire.Cursor before, int limit)
public CompletableFuture<ArchiveWire.ArchivedSnapshot> getArchivedShellSnapshot()
```

El paquete solo ve la interfaz `ArchiveSource`. `ArchiveSource.direct(...)` es el adaptador que
usa la conexión de cada entorno (`DirectController.connection(envId)`) y es el default. También
están `ArchiveSource.of(Caller)`, para cualquier llamador crudo, y `ArchiveSource.UNAVAILABLE`, que
deja solo los settled del shell. En modo bridge no hay lecturas y el catálogo lo avisa.

## Slots coherentes con el HQ

`ArchiveSlots.assign(entries, previous)` llama a **`HqLayout.allocate`** con un roster que solo
tiene líneas settled. Los slots del archivo no dependen de los hilos activos, así que salen
idénticos a los que el generador del HQ da a esos hilos (lo comprueba un test sobre una historia
aleatoria de 30 pasos con el roster completo). Un hilo conserva su slot mientras siga en la lista,
y los nuevos toman el slot libre más bajo en orden `(project key, thread id)`. Los ids son
`<env>:<thread>`, igual que en `NpcView.id()`. Si hay más de 148 hilos cerrados, los **148 cerrados
más recientemente** tienen slot y el resto aparece solo en el catálogo. Así, un cierre nuevo
desplaza al más viejo y ningún otro hilo se mueve.

Las posiciones salen del propio generador (`ArchiveGeometry`): se genera una vez un plan con los
148 slots ocupados y se lee el anchor `archive_<thread>` de cada uno. No se duplica `HqGeometry`.
Las coordenadas de mundo se calculan con `HqSite`.

## Render

Solo entidades de cliente: `TextDisplay` FIXED en la cara de la pantalla (escala 0,18, a pleno
brillo, fondo oscuro), un `Interaction` mientras la celda es aire y, en el catálogo, un título
flotante más un `BlockDisplay` de atril vanilla mientras la celda es aire. No se escriben bloques en
el mundo, no hay registros nuevos y nada lo ve el servidor.

**Ids de entidad: desde −6.000.000 hacia abajo** (`ArchiveRenderer.ID_BASE`). Rangos ocupados:
NpcRegistry −1.000.000, builders −2.000.000, BehaviorDirector −3.000.000, ayudantes −4.000.000 y
workstation −5.000.000. Si el barrido de huérfanos de `NpcRegistry` borra alguna entidad, se
reconstruye en el tick siguiente (`isRemoved()`).

Clics: con la mano principal y sin agacharse, sobre la celda de un slot o del catálogo, se devuelve
`FAIL`: no se envía nada y no se abre la GUI del atril ni del bloque. Sobre nuestras entidades,
`SUCCESS`. Los ataques a nuestras entidades se cancelan con `FAIL`.

## Abrir en solo lectura

`ArchiveOpener.DEFAULT`: si existe `ChatScreens` de feat/chat (`PortableLinks.hasChat()`), se usa
ese. Si no, se abre la `ThreadChatScreen` de main con un `NpcInfo` sintético settled (título,
entorno · proyecto, fecha de cierre). El registro de NPCs no tiene hilos cerrados, así que los
abridores basados en él no los encontrarían.

## Puntos de integración (`ArchiveClient.get()`)

- `setSite(HqSite)`: quien coloca el HQ en el mundo (builders, site o demo). Sin sitio no se dibuja
  nada en el mundo, pero el catálogo funciona igual.
- `layout()`: el `HqLayout` (solo el archivo), para que el dueño del HQ ponga las computadoras
  archivadas donde están los letreros (`HqLayout.allocate` con el mismo roster settled da lo mismo).
- `setCaller(Caller)` / `setSource(ArchiveSource)`: cambiar de dónde se lee (por defecto, las
  conexiones directas).
- `setOpener(ArchiveOpener)`: otro visor.
- **Hook para feat/blocks-core**: `setCatalogProp(Direction → BlockState)`, el bloque que se muestra
  como catálogo mientras la celda es aire. Cualquier bloque real que haya en la celda del catálogo
  abre el catálogo. No depende de blocks.

## Cambios estructurales → **RESTART**

- `fabric.mod.json`: nuevo entrypoint `client` `dev.t3craft.client.archive.ArchiveClient`.
- Callbacks de cliente: `UseBlockCallback`, `UseEntityCallback`, `AttackEntityCallback`, tick
  (`GuardedEvents`, módulo `archive`), `CLIENT_STOPPING` y `DevReload` (`t3craft.archive`).
- Comando `/t3craft archive [refresh|status]`.
- Fuera del paquete: `T3Connection.getOlderSettledThreads` / `getArchivedShellSnapshot` (sobre
  `streams.call`), `DirectController.labels()` (aditivos), `dev.t3craft.t3.ArchiveWire` (nuevo) y
  claves de lang `t3craft.archive.*`.
- Sin mixins, keybinds, bloques, ítems, registros ni config.

## Pendientes

- feat/blocks-core entró a main pero no tiene un bloque de catálogo: el hook `setCatalogProp` queda
  listo para cuando exista.
- Nadie llama todavía a `setSite`: en main no hay un HQ colocado en el mundo real (solo
  `hq-preview` y la demo, que no lo exponen). Hay que llamarlo desde quien construya el HQ.
- **No se probó dentro del juego** (por regla, no se lanza Minecraft). Falta revisar a ojo la escala
  y la altura del letrero, que el texto no quede espejado y que el `Interaction` reciba el clic.
- La caché vive en memoria: al reiniciar se relee (son 2 lecturas por entorno).
- No se copió código de AgentCraft.
