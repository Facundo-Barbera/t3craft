# Pendiente de ejecución (moratoria del 2026-11-09)

Facundo prohibió ejecutar cualquier cosa (gradle, compilar, renders, java, tests) hasta nuevo aviso. Todo esto está en código, commiteado y subido, pero **sin compilar ni verificar**. Cuando se levante la moratoria, el coordinador lo ejecuta en este orden, de a un proceso pesado por vez con `tools/heavy.sh`.

## 0. Atajo: `citadel/next` (todo el catálogo junto, listo para una sola compilación)
La rama `citadel/next` ya junta, con los conflictos resueltos (unión de `CatalogEntries`, ordenada por id, y del changelog):
- las 16 ramas `cat/*` revisadas (49 variantes);
- `citadel/place-catalog`;
- `citadel/lead-office-hook`.

Al levantarse la moratoria:
1. Compilar `citadel/next` una vez, con el lock y `tools/heavy.sh`.
2. Corregir lo que diga el compilador. La revisión a mano dejó como riesgos los tipos de argumentos y las mezclas `int`/`long`/`double`.
3. Renderizar en el PC una galería por familia y la ciudad antes/después.
4. Recién entonces integrar a main.

Las ramas de la ola B (`cat/street-furniture` y `cat/well-annex`) se suman a `citadel/next` cuando terminen.

## 1. Compilar e integrar en main (cada uno: merge → compilación → main)
| rama | qué | estado del trabajo |
|---|---|---|
| `fix/plan-connect` | Conexiones de bardas, muros, paneles y escaleras para todos los layouts (`PlanConnect`) | Terminado por Opus, sin compilar por el coordinador |
| `fix/new-thread-hitch` | Detector de trabones (`/t3dev perf hitches`) y arreglos del trabón al llegar un hilo nuevo | Terminado, sin compilar |
| `citadel/lead-office-hook` | La ciudadela construye la oficina del coordinador en su hall (`CitadelBarrios.hall` → `CitadelLeadOffice.place`) | Hecho por el coordinador, sin compilar; puede chocar con `citadel/2-houses` en `CitadelBarrios` |
| `citadel/place-catalog` | Coloca las variantes del catálogo en lotes, lotes vacíos, calles, plazas y explanada (`CitadelPlacement`, param `catalog.enabled`) | Terminado (`2c442a9`), sin compilar ni renderizar; ya va dentro de `citadel/next` |
| `citadel/scale` | Ciudadela a 1.5×, configurable (`RINGCAMPUS_SCALE`) | WIP rescatado; faltaba la revisión independiente |

## 2. Ramas de diseño del swarm 1 (citadel/*)
`citadel/1-connect` (reemplazada por `fix/plan-connect`, descartar), `citadel/1-light`, `citadel/1-paths`, `citadel/2-hub`, `citadel/2-nodes`, `citadel/2-houses`, `citadel/2-roofs-yards`, `citadel/2-nature`, `citadel/2-office`. Algunas tienen commits WIP rescatados. Todas necesitan compilar y renderizar para revisar antes de integrar.

## 3. Variantes del catálogo, ola A (cat/*): WIP rescatado, sin compilar ni renderizar
house-timber, house-stone, house-japanese, house-terrace, house-workshop, house-tower, stall-market, fountain-tiered, fountain-pond, tree-cherry, tree-mixed, tree-garden, garden-zen, lamp-street, gate-arch, plaza-center.

**Para cada una:** compilar, galería `--catalog`, revisión del director de arte e integración. Requiere una sesión de render aprobada por Facundo (`~/.cache/t3craft-coord/renders-approved`).

## Arreglo del crash al abrir el HQ: integrado
`fix/void-null-layer`, commit del fix `c9b98ce`, merge en main `b3c2df1`. Compilado una vez con `tools/heavy.sh`. Para actualizar el juego en Windows, hacer pull de main (o de ese commit) y recompilar.

## Arreglo de Ctrl+V: integrado
`fix/paste-crash`: el commit del arreglo es `faf953e` y el merge en main es `6562e0e`. Compilado.
- En Windows ya no se usa el portapapeles AWT (se puede volver a activar con `-Dt3craft.awtClipboard=true`).
- La lectura del portapapeles y el pegado de imágenes quedan envueltos: un fallo muestra un error en el composer en vez de crashear.
- Si vuelve a crashear, hace falta el crash report (`crash-reports/` o `hs_err_pid*.log` en el directorio `run`).

## `citadel/next`: compila (2026-11-09)
Incluye el catálogo (60 variantes, con la ola B: bancas, jardineras, postes, pozos, abrevadero, invernadero, cobertizo y pérgola), la colocación, el gancho de la oficina y las 10 ramas del swarm 1 (sin `scale` ni `1-connect`).
- Compilado con `tools/heavy.sh`: hubo que renombrar un `record Tail` duplicado que venía de `fix/new-thread-hitch`.
- Falta: renders en el PC (por el relay, bloqueado por la clave SSH) y la integración a main.

## En curso (solo código)
- `feat/build-near-player` (**prioridad máxima**):
  - los builders construyen primero cerca del jugador;
  - la construcción es más rápida, con la base (isla, suelo, podio) colocada por capas sin animación bloque a bloque;
  - hay más bloques por tick para lo visible.

## Renders en el PC de Facundo (my-pc): probar 1 job
**BLOQUEADO 2026-11-09:** la VPS no tiene acceso SSH al PC (`Permission denied (publickey…)`). Hay que agregar en `~/.ssh/authorized_keys` del user `wsluser` en WSL esta clave pública de la VPS:
`ssh-ed25519 AAAA...REDACTED example-key`
Además, en WSL, `sshd` tiene que escuchar en la IP de Tailscale del PC (100.64.0.20), o bien hay que darle a `T3CRAFT_RENDER_HOST` el host y el puerto correctos. Con eso, el coordinador corre el job de prueba (paso 4).
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
- `review/wip-catalog`: terminada. Las 16 ramas `cat/*` parecen listas para compilar; informe en `docs/reviews/wip-catalog.md`, ya en main.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/reviews/wip-citadel.md`.

## 3b. Ola B (solo código, en curso)
- `cat/street-furniture`: bancas, jardineras y postes indicadores.
- `cat/well-annex`: pozos, abrevadero, invernadero, cobertizo y pérgola.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.
