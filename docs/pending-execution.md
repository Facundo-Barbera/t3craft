# Pendiente de ejecución (moratoria del 2026-11-09)

Facundo prohibió ejecutar cualquier cosa (gradle, compilar, renders, java, tests) hasta nuevo aviso. Todo esto está en código, commiteado y subido, pero **sin compilar ni verificar**. Cuando se levante la moratoria, el coordinador lo ejecuta en este orden, de a un proceso pesado por vez con `tools/heavy.sh`.

## 1. Compilar e integrar en main (cada uno: merge → compilación → main)
| rama | qué | estado del trabajo |
|---|---|---|
| `fix/plan-connect` | Conexiones de bardas, muros, paneles y escaleras para todos los layouts (`PlanConnect`) | Terminado por Opus, sin compilar por el coordinador |
| `fix/new-thread-hitch` | Detector de trabones (`/t3dev perf hitches`) y arreglos del trabón al llegar un hilo nuevo | Terminado, sin compilar |
| `citadel/lead-office-hook` | La ciudadela construye la oficina del coordinador en su hall (`CitadelBarrios.hall` → `CitadelLeadOffice.place`) | Hecho por el coordinador, sin compilar; puede chocar con `citadel/2-houses` en `CitadelBarrios` |
| `citadel/place-catalog` | Coloca las variantes del catálogo en lotes, lotes vacíos, calles, plazas y explanada (`CitadelPlacement`, interruptor `RINGCAMPUS_CATALOG`) | En curso (tarea hija, solo código) |
| `citadel/scale` | Ciudadela a 1.5×, configurable (`RINGCAMPUS_SCALE`) | WIP rescatado; faltaba la revisión independiente |

## 2. Ramas de diseño del swarm 1 (citadel/*)
`citadel/1-connect` (reemplazada por `fix/plan-connect`, descartar), `citadel/1-light`, `citadel/1-paths`, `citadel/2-hub`, `citadel/2-nodes`, `citadel/2-houses`, `citadel/2-roofs-yards`, `citadel/2-nature`, `citadel/2-office`. Algunas tienen commits WIP rescatados. Todas necesitan compilar y renderizar para revisar antes de integrar.

## 3. Variantes del catálogo, ola A (cat/*): WIP rescatado, sin compilar ni renderizar
house-timber, house-stone, house-japanese, house-terrace, house-workshop, house-tower, stall-market, fountain-tiered, fountain-pond, tree-cherry, tree-mixed, tree-garden, garden-zen, lamp-street, gate-arch, plaza-center.

**Para cada una:** compilar, galería `--catalog`, revisión del director de arte e integración. Requiere una sesión de render aprobada por Facundo (`~/.cache/t3craft-coord/renders-approved`).

## Revisiones a mano en curso (solo código)
- `review/wip-catalog`: revisa y corrige las 16 ramas `cat/*`. Informe en `docs/reviews/wip-catalog.md`.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/reviews/wip-citadel.md`.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.
