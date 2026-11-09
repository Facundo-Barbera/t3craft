# Incidente 2026-11-09: VPS saturada (load ~39) y T3 sin responder

Horas en hora local (CET). Fuentes: `~/.cache/t3craft-render/daemon.log`, `sar -q`, `journalctl --user -u t3code.service`, `~/.gradle/daemon/*/`.

## Qué pasó
| hora | evento |
|---|---|
| 00:27 | El coordinador reinicia el daemon de render con **3 workers** (antes 1) y límite de carga 11 (antes 8). |
| 00:30–00:45 | El coordinador lanza la **ola A del swarm: 16 agentes Sonnet**. A eso se suman 4 tareas Opus (escala, conexiones, catálogo ×2 por un duplicado, render-workers) y unos 9 hilos del swarm 1 que seguían activos. En total, **~30 agentes con permiso de pedir renders**. |
| 00:50 | Load 23.6 (sar). |
| 01:16 | La cola del daemon estaba frenada por la carga (había bajado a 1 trabajo a la vez). Para destrabarla, el coordinador **sube el límite de carga a 20**, lo que vuelve a dejar 3 trabajos en paralelo. |
| 01:30–01:50 | Load 29 → 39, ~1.250 procesos, runq de hasta 74. T3 deja de responder. |
| 01:54 | Facundo reinicia `t3code.service`. systemd mata **52 procesos: 26 `sh` + 26 `sleep`**, los bucles de espera de `tools/render/submit.sh`. |

## Procesos pesados
- **Daemon de render** (JVM, `-Xmx6656m`, 3 workers × 2 hilos). **Lo lanzó el coordinador.**
- **Compilaciones por trabajo:** el daemon lanzaba `./gradlew … compileJava …` en el worktree de cada agente; hubo **236 trabajos desde las 00:00**. Unos 25 worktrees eran nuevos, y la primera compilación de cada uno hace el setup de Loom (remapeo del jar de Minecraft), que es lo más pesado de todo. **Los disparaban los agentes con `submit.sh`.**
- **Builds de integración del coordinador** (`./gradlew build hqrenderClasses uirenderClasses`), corriendo a la vez que las compilaciones del daemon. Entre 00:41 y 00:58 Gradle levantó **más de 14 GradleDaemons** ("other compatible daemons were started"). Cada uno queda vivo 3 h si no se para.
- **Renders en lote de agentes:** por ejemplo `citadel-2-nodes` con scripts propios (`/tmp/nodes-batch*.out`) que encadenaban varios renders.

**En paralelo hubo**, como máximo: 1 JVM del daemon con 3 renders, 1 compilación del daemon, 1 build del coordinador, varios GradleDaemons ociosos o activos y ~30 procesos de agente (Claude).

## Por qué quedaron huérfanos
- **Esperas de `submit.sh`:** los agentes lo corren en segundo plano, en un bucle de `sleep 5` de hasta 1 h. Cuando una tarea se cancelaba (el catálogo duplicado), se interrumpía (la escala) o le borraban el worktree, ese bucle seguía vivo sin dueño. Así se llegó a los 26 pares `sh` + `sleep`.
- **GradleDaemons:** por diseño sobreviven al build (3 h de inactividad) y nadie hacía `gradle --stop`.
- **Cancelaciones del coordinador:** al cancelar o interrumpir tareas, el coordinador no mató lo que esas tareas habían lanzado.

## Causa raíz
**El coordinador.** Pasó por encima de los límites de seguridad que él mismo había puesto: carga 8 → 11 → 20 y workers 1 → 3, para que el swarm fuera más rápido. Al mismo tiempo lanzó ~30 agentes que podían pedir renders y siguió compilando en paralelo. No existía un lock global de procesos pesados, ni límite de memoria o timeout por proceso, y nada limpiaba los procesos de las tareas canceladas.

## Arreglos
- Reglas en `AGENTS.md`, sección "VPS: heavy processes".
- `tools/heavy.sh`: lock global, nice/ionice, MemoryMax, timeout y limpieza.
- El daemon toma el lock global, arranca por defecto con 1 worker y carga 6, y requiere aprobación de Facundo.
- `submit.sh` exige aprobación y se cierra si muere quien lo lanzó.
- Los GradleDaemons ociosos mueren a los 10 min.
