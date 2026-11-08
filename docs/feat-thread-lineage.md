# feat/thread-lineage: agentes delegados (delegate_task) visibles

Los hilos que un coordinador abre con `delegate_task` llegan de T3 con
`lineage.relationshipToParent = "subagent"` y `lineage.parentThreadId`. T3 distingue dos casos
(`isProviderNativeSubagentThread` en `bridge/vendor/t3-contracts/src/orchestrationV2.ts`):

| Caso | `creationSource` | Dónde se ve |
|---|---|---|
| Subagente del proveedor (Agent tool de Claude, …) | `provider` | Dentro del hilo padre; en la ciudad, ayudante bebé (`docs/feat-npc-helpers.md`). Nunca en el sidebar. |
| Tarea delegada (`delegate_task`) | cualquier otro (`mcp`, `server`, …) | Sidebar anidada bajo el padre, NPC trabajador completo con escritorio. |

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

## Sidebar (Y)

- Hijo delegado anidado justo debajo de su padre, en el lugar del padre (carpeta, Pinned,
  Working…): sangría de 8 px por nivel (máx. 4 dibujados), línea guía con codo, marca
  `⤷ delegated` en la línea 1.
- Padre: contador `▾ N agents` a la derecha del título (cian si alguno trabaja); clic pliega o
  despliega. Los contadores de sección cuentan también a los hijos anidados.
- Hijo a nivel raíz con `↳ parent: <título>` cuando el padre no está listado allí (settled, otro
  proyecto con filtro, otro entorno, no coincide con la búsqueda) o el hijo está fijado o settled.
- `focus(key)` de un hijo abre la sección del ancestro raíz y despliega los ancestros.

## Header del hilo

- Hijo: breadcrumb `↳ padre › hijo`, el padre clicable.
- Padre: chip `N agents ▾`; tooltip con cada hijo y su estado; clic abre un menú con los hijos
  (`glifo título · estado`) que abre el elegido.

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
