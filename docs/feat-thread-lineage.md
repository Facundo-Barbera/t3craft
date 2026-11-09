# feat/thread-lineage: agentes delegados (delegate_task) visibles

Los hilos que un coordinador abre con `delegate_task` llegan de T3 con
`lineage.relationshipToParent = "subagent"` y `lineage.parentThreadId`. T3 distingue dos casos
(`isProviderNativeSubagentThread` en `bridge/vendor/t3-contracts/src/orchestrationV2.ts`):

| Caso | `creationSource` | Dónde se ve |
|---|---|---|
| Subagente del proveedor (Agent tool de Claude, …) | `provider` | Dentro del hilo padre; en la ciudad, ayudante bebé (`docs/feat-npc-helpers.md`). Nunca en el sidebar. |
| Tarea delegada (`delegate_task`) | cualquier otro (`mcp`, `server`, …) | Menú de agentes del header del padre (no en el sidebar), NPC trabajador completo con escritorio. |

## Modelo

- `ThreadShell`: componentes nuevos `creationSource` y `delegatedFrom` (`DelegatedFrom(environmentId,
  threadId, title)`: el padre está en otro entorno enlazado). Constructores viejos intactos.
  `isSubagent()`, `isDelegated()` (subagente y `creationSource != "provider"`, también con
  `creationSource` null, como T3), `isProviderSubagent()`.
- `dev.t3craft.t3.Lineage`: índice puro por entorno. `Lineage.of(envId, view)` memoiza por
  identidad de la vista (una vista nueva por cambio del shell). API: `parent`, `parentId`,
  `children`, `delegatedChildren`, `ancestors`, `depth`, `rootId`, `counts` /
  `liveDescendants` / `workingDescendants` (working = corre, `waiting` incluido, o pide al
  usuario). Pensado también para las oficinas de hilos líderes.
- `chat.RowLineage` en `ThreadRow.lineage()` (`parentKey`, `parentTitle`, `delegated`); se guarda
  en el caché de disco (campos aditivos).

## Sidebar

feat/ui-hq-menu-lineage (Facundo, 2026-11-09): el sidebar ya no muestra linaje. Sin anidado, sin
contador `▾ N agents` y sin marca `⤷ delegated`; las tareas delegadas (con padre) no son filas del
sidebar (`SidebarModel.hiddenDelegated`). Se abren desde el menú de agentes del padre, un bloque o
un enlace (`SidebarModel.find` las sigue encontrando). Los subagentes del proveedor tampoco son filas.

## Header del hilo

- Hijo: breadcrumb `↳ padre › hijo`, el padre clicable.
- Padre: chip `N agents ▾` (N = hijos activos: idle, working, waiting, needs input,
  `SidebarModel.liveChildrenOf`); sin activos pero con terminados, `0/M agents ▾`; sin hijos no hay
  chip. Tooltip con cada hijo activo y su estado.
- fix/chat-send-agents-menu: el clic abre un menú con una fila de filtro (`Show: ● Active ○ All`,
  por defecto Active), los activos arriba (`glifo título · estado`, abre el elegido) y debajo los
  terminados (done, settled o error, `SidebarModel.finishedChildrenOf`): con Active quedan plegados
  tras `▸ Finished (M)` y un clic los despliega atenuados; con All van bajo un encabezado
  `Finished (M)`. El filtro y el plegado se recuerdan mientras la pantalla esté abierta.

## Ciudad

- `Visibility.tier` ya no oculta tareas delegadas (solo subagentes del proveedor y side chats,
  salvo `includeSubagents`); `HelperRoster.isSubagent` solo cuenta subagentes del proveedor, así
  que las delegadas quedan en el roster de escritorios, workstations, preview y portable.
- Nameplate: segunda línea `↳ <padre>` (`Presentation.present(..., parent)`).
- Escritorio: el hijo comparte el proyecto del padre, así que cae en el ala (módulo) de su
  proyecto, junto al padre si hay sitio; los allocators no cambian.
- Terminadas o settled: se van como cualquier hilo (StationPolicy, ventana reciente) y van al
  archivo (`ArchiveEntry.isCandidate`).
- Sin cambios en el detector de presencia del HQ, `NpcGate` ni el presupuesto de spawns.

## Bridge

`isVisibleThread` deja pasar tareas delegadas; `presentThread` recibe el título del padre
(`↳ <padre>` como sublabel) y el `NpcView` lleva `parentThreadId` y `delegated` opcionales.
