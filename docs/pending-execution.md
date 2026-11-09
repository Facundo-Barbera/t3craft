# Pendiente de ejecución (moratoria del 2026-11-09)

Facundo prohibió ejecutar cualquier cosa (gradle, compilar, renders, java, tests) hasta nuevo aviso. Todo esto está en código, commiteado y subido, pero **sin compilar ni verificar**. Cuando se levante la moratoria, el coordinador lo ejecuta en este orden, de a un proceso pesado por vez con `tools/heavy.sh`.

## 1. Compilar e integrar en main (cada uno: merge → compilación → main)
| rama | qué | estado del trabajo |
|---|---|---|
| `fix/plan-connect` | Conexiones de bardas, muros, paneles y escaleras para todos los layouts (`PlanConnect`) | Terminado por Opus, sin compilar por el coordinador |
| `fix/new-thread-hitch` | Detector de trabones (`/t3dev perf hitches`) y arreglos del trabón al llegar un hilo nuevo | Terminado, sin compilar |
| `citadel/lead-office-hook` | La ciudadela construye la oficina del coordinador en su hall (`CitadelBarrios.hall` → `CitadelLeadOffice.place`) | Hecho por el coordinador, sin compilar; puede chocar con `citadel/2-houses` en `CitadelBarrios` |
| `citadel/place-catalog` | Coloca las variantes del catálogo en lotes, lotes vacíos, calles, plazas y explanada (`CitadelPlacement`, param `catalog.enabled`) | Terminado (`2c442a9`), sin compilar ni renderizar; falta el render antes/después de la ciudad |
| `citadel/scale` | Ciudadela a 1.5×, configurable (`RINGCAMPUS_SCALE`) | WIP rescatado; faltaba la revisión independiente |

## 2. Ramas de diseño del swarm 1 (citadel/*)
`citadel/1-connect` (reemplazada por `fix/plan-connect`, descartar), `citadel/1-light`, `citadel/1-paths`, `citadel/2-hub`, `citadel/2-nodes`, `citadel/2-houses`, `citadel/2-roofs-yards`, `citadel/2-nature`, `citadel/2-office`. Algunas tienen commits WIP rescatados. Todas necesitan compilar y renderizar para revisar antes de integrar.

## 3. Variantes del catálogo, ola A (cat/*): WIP rescatado, sin compilar ni renderizar
house-timber, house-stone, house-japanese, house-terrace, house-workshop, house-tower, stall-market, fountain-tiered, fountain-pond, tree-cherry, tree-mixed, tree-garden, garden-zen, lamp-street, gate-arch, plaza-center.

**Para cada una:** compilar, galería `--catalog`, revisión del director de arte e integración. Requiere una sesión de render aprobada por Facundo (`~/.cache/t3craft-coord/renders-approved`).

## Renders en el PC de Facundo (my-pc): probar 1 job cuando se levante la moratoria
Código listo (`tools/render/remote-submit.sh`, `submit.sh` lo usa por defecto; `start-daemon.sh` se niega en la VPS). Sin probar. Pasos:
1. **PC** (lo prepara Taller):
   - WSL Ubuntu 24.04, user `wsluser`, JDK 25;
   - `~/projects/t3craft` en main;
   - `git`, `rsync`, `python3` y `sshd` accesibles por Tailscale (100.64.0.20);
   - un primer build de Gradle hecho (bootstrap y caché de Loom).
2. **VPS → PC, SSH sin contraseña:** la clave del user `wsluser` de la VPS en `~/.ssh/authorized_keys` del PC. Probar `ssh -o BatchMode=yes user@100.64.0.20 true`, que no es pesado.
3. **En el PC:** `cd ~/projects/t3craft && tools/render/start-daemon.sh` (1 worker por defecto). Esperar a que `--status` diga `running`.
4. **En la VPS,** con la sesión aprobada (crear `~/.cache/t3craft-coord/renders-approved` con el alcance), lanzar un job chico: `tools/render/submit.sh --agent prueba-relay -- --catalog LAMP`.
   - Se espera ver `~/t3craft-jobs/prueba-relay` creado en el PC, la compilación en el PC y los PNG y `report.txt` en `~/.cache/t3craft-render/outbox/prueba-relay-*` de la VPS.
   - Debe salir con código 0.
5. **Si falla,** revisar:
   - el `daemon.log` del PC;
   - que el `HOME` de ambos sea `/home/user` (el relay reescribe las rutas si difieren);
   - que el PC tenga `rsync`.
6. **Citadel Studio** (si se vuelve a levantar en la VPS) lee el outbox local y el `daemon.json` que el relay copia mientras espera.

## Revisiones a mano en curso (solo código)
- `review/wip-catalog`: revisa y corrige las 16 ramas `cat/*`. Informe en `docs/reviews/wip-catalog.md`.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/reviews/wip-citadel.md`.

## 3b. Ola B (solo código, en curso)
- `cat/street-furniture`: bancas, jardineras y postes indicadores.
- `cat/well-annex`: pozos, abrevadero, invernadero, cobertizo y pérgola.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.
