# feat/npc-helpers: subagentes como "ayudantes" del NPC padre

Ítem 12 de `docs/research/inventario.md`. Decisión de Facundo: los subagentes se ven como
**ayudantes** junto al NPC padre, no como hilos sueltos con NPC y escritorio propios.

Paquete: `mod/src/client/java/dev/t3craft/client/npc/helpers/`.
Tests: `mod/src/test/java/dev/t3craft/client/npc/helpers/` (JUnit, lógica pura).

## Qué se ve

- Cada subagente **en marcha** aparece como un **aprendiz** solo de cliente (jugador pequeño, escala 0,6), detrás de su padre,
  en abanico (hasta 240°, radio 1,4 bloques). Cuando el padre está en su escritorio, los ayudantes
  rodean la silla; cuando camina, lo siguen (paso máximo 0,22 bloques/tick, zona muerta 0,2, salto
  directo si quedan a más de 8 bloques). Parados, giran despacio para mirar al padre.
- **Variante visual**: la variante «aprendiz» de la skin del padre (`AgentSkins.forHelper`: misma
  cara y camisa, más delantal y gorra); ver «Skins de agentes» en `docs/api.md`.
- **Color de estado propio**: nametag `glifo título` en el color del `ThreadState` del subagente
  (⚙ cian trabajando, ! naranja si pide algo) y partículas de polvo de ese color sobre la cabeza.
- **Límite por padre** (`maxPerParent`, 3 por defecto): primero los que esperan al usuario, luego los
  más antiguos; los sobrantes se cuentan en el panel del padre: `⚒ 3 helpers +2`. Con límite 0 solo
  queda `⚒ +n`.
- Clic derecho en un ayudante abre el chat del **padre**.

## Ciclo de vida

| Evento | Qué pasa |
|---|---|
| El subagente arranca (working / needs attention / run activo) | Aparece con una nube de partículas `POOF` en su sitio. |
| Termina (done, failed, idle, settled, archivado o borrado) | Desaparece con `POOF` + `HAPPY_VILLAGER`. |
| El padre deja de tener NPC | Sus ayudantes se liberan con `POOF` + `SMOKE`. |
| Queda por encima del límite | Desaparece sin festejo y pasa al "+n". |
| Cambio de mundo, `/t3craft reload`, error | Se borran sin partículas y se reconstruyen. |

Subagentes anidados (subagente de un subagente) se agrupan bajo el ancestro más cercano que tenga
NPC. Si ningún ancestro tiene NPC, no se muestran.

## Datos (solo lectura)

Del shell que ya llega por `subscribeShell` (sin conexión ni llamada nueva):
`lineage.relationshipToParent == "subagent"` y `lineage.parentThreadId`.
`ThreadShell` ganó el componente `parentThreadId` (último, para no chocar con otras ramas), con
fallback a un `parentThreadId` de nivel superior. `DirectController.shellViews()` expone la vista
sin filtrar de cada entorno. En modo bridge no hay ayudantes (el puente no manda el linaje).

## Escritorios y roster

Los subagentes **no ocupan escritorio ni crean módulos**. El filtro está en un solo lugar:
`T3CraftClient.refreshDirect` pasa la lista de `NpcView` por `HelperRoster.deskRoster` antes de
alimentar `NpcRegistry`. Como `BehaviorDirector`, workstations, `HqPreviewCommand` (HqLayout) y
el portable leen el roster de `NpcRegistry.all()`, todos ven solo a los padres. Con ayudantes
activos, esto vale incluso con `includeSubagents: true`; con ayudantes apagados, `includeSubagents`
funciona como antes. Las notificaciones de transición tampoco se disparan para subagentes.

## Config (aditiva)

```json
"helpers": { "enabled": true, "maxPerParent": 3 }
```

Bloque propio en `config/t3craft.json`, fuera de `T3CraftSettings` (como `workstations`), así que
ningún constructor de record cambia y `ConfigCodec` lo conserva al guardar. `maxPerParent` se acota a
`[0, 8]`; tipos incorrectos → valor por defecto. Se relee con `/t3craft reload`.

## Ids de entidad

`HelperDirector.ID_BASE = -4_000_000`, contando hacia abajo:

| Rango | Dueño |
|---|---|
| −1,000,000… | `NpcRegistry` (avatares, paneles, letreros) |
| −2,000,000… | builders |
| −3,000,000… | `BehaviorDirector` (bocadillos, iconos) |
| **−4,000,000…** | **`HelperDirector` (ayudantes)** |
| −5,000,000… | workstations |

El barrido de huérfanos de `NpcRegistry` trata todo id ≤ −1,000,000 como suyo; el director comprueba
`isRemoved()` cada tick y vuelve a crear los ayudantes borrados.

## Cambios fuera del paquete (mínimos)

- `ThreadShell`: componente `parentThreadId`.
- `DirectController.shellViews()`.
- `NpcRegistry`: `avatar(npcId)` y `setPanelExtra(Function<String, Component>)` (línea extra en el
  panel). Nada en `npc.behavior`.
- `T3CraftClient`: crea el director, lo alimenta en `refreshDirect`, lo hace tick después del
  registro, y el clic derecho en un ayudante abre el chat del padre.

## Clases

| Clase | Qué hace |
|---|---|
| `HelperView` | Un subagente en marcha: id, id del padre, título, estado, inicio; `variant(n)`. |
| `HelperRoster` | Puro: `collect` (del shell), `deskRoster` (filtro), `group` (padre → hijos, límite, +n, anidados), `panelLine`. |
| `HelperFormation` | Puro: abanico detrás del padre, paso de seguimiento, yaw. |
| `HelperLifecycle` | Puro: diff spawn / update / despawn con motivo (`FINISHED`, `PARENT_GONE`, `HIDDEN`). |
| `HelperConfig` | Bloque `helpers` de la config. |
| `HelperAvatar` | `RemotePlayer` pequeño solo de cliente (skin aprendiz del padre y nametag). |
| `HelperDirector` | Entidades, seguimiento, partículas, línea del panel, clic derecho. |
