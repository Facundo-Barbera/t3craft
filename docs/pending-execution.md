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
- `feat/build-near-player`: **integrado en main** (rama `82597e6`), compilado y sin probar en juego. Base por capas (estimación: de ~7–9 min a ~10–20 s), arranque inmediato junto al jugador, prioridad por cercanía, 96 bloques por tick y hasta 8 builders. Pendiente: en supervivencia, las alas se siguen construyendo en orden de índice.

## En curso (solo código, 2026-11-09)
- `feat/build-radial`: **integrado en main** (`f436217`) y compilado. Construye en anillos de 5 bloques alrededor del jugador; la base tarda ~1,7 min estimados. Quedan pendientes tres cosas:
  - en supervivencia no hay orden radial;
  - `HqConstruction` sigue mandando un trabajo por parte;
  - con 8 builders algunos adelantan el anillo siguiente.
- `citadel/scale-port`: **dentro de `citadel/next`** (`3d03dd0`), que compila. La escala por defecto es 1.0. Con 1.5 los nodos y algunos detalles mantienen tamaño fijo, y hay que verlo en render.
- `fix/chat-send-agents-menu`: código listo, **sin compilar ni probar**.
  - el segundo mensaje ya no se pierde: se encola detrás del que está en vuelo (`ComposerState` outbox, "⏱ Queued");
  - menú de agentes con filtro Active/All, activos arriba y terminados abajo (plegados con Active).
  - Probar en juego: dos Enter seguidos en un hilo idle y en uno corriendo; dos mensajes en un hilo nuevo (draft).
- `cat/wave-c`: estatuas, gazebos, mástiles, puentes y remates de torre.
- `citadel/light-aesthetic`: luz visible solo como diseño (faroles con ritmo, linternas en aleros, apliques junto a puertas, candelabros centrados) y relleno invisible. Nada de bloques luminosos visibles "donde caigan".
- `feat/ui-hq-menu-lineage`:
  - en el menú de HQ, la ciudadela va arriba y el resto en una sección "Legacy";
  - el linaje sale del sidebar y queda solo en el menú de agentes del header, mostrando solo hijos idle o activos.

## Arreglo del lag al iniciar un hilo: integrado
`fix/new-thread-hitch` ya está en main (`a031a79`), compilado. Trae el detector de trabones: `/t3dev perf hitches`, con WARN en latest.log cuando un frame o tick pasa de ~50 ms. Si el lag sigue, ese log dice qué fue.

## Renders en el PC de Facundo (my-pc): FUNCIONA (2026-11-09)
- **Relay probado de punta a punta.** El job `prueba-relay` (`--catalog LAMP`) compiló en el PC, renderizó la galería con etiquetas y trajo PNGs y reporte a la VPS, con código de salida 0.
- **Transporte:** scripts por stdin (`ssh -T user@100.64.0.20 < script`) y archivos en tar+base64. No se usa rsync ni scp.
- **Aprobación de la sesión:** `~/.cache/t3craft-coord/renders-approved` en la VPS. Alcance: el swarm de diseño de la ciudadela.
## Revisiones a mano en curso (solo código)
- `review/wip-catalog`: terminada. Las 16 ramas `cat/*` parecen listas para compilar; informe en `docs/reviews/wip-catalog.md`, ya en main.
- `review/wip-citadel`: revisa `citadel/*`, `citadel/scale`, `fix/plan-connect` y `fix/new-thread-hitch`, y propone el orden de integración. Informe en `docs/reviews/wip-citadel.md`.

## 3b. Ola B (solo código, en curso)
- `cat/street-furniture`: bancas, jardineras y postes indicadores.
- `cat/well-annex`: pozos, abrevadero, invernadero, cobertizo y pérgola.

## 4. Sin lanzar
- Ola B del swarm (ver `~/.cache/t3craft-coord/swarm2-plan.md`).
- Líderes de familia que coloquen el catálogo en la ciudad.
