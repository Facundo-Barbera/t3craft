# Rediseño del HQ: estilo C "Campus de cerezo" (renders)

Renders headless del plan del HQ (`BuildPlan`) de la rama `feat/hq-style-cherry`, con el **estilo C**
(default, `hq.style = cherry`) sobre la capa `HqStyle`, con los bloques del mod (kit, monitores,
pantallas grandes, pared kanban, terminal de archivo y lámparas) **activados**, salvo los `vanilla-fallback_*`.
Los PNG son renders generados; **no** se commitea ninguna textura de Minecraft.

| Archivo | Qué muestra |
|---|---|
| `cherry_module.png` | Un módulo (ala de 6 escritorios) desde fuera: tejado cóncavo de tejas de deepslate, cumbrera de blackstone, paneles shoji iluminados, jardín con cerezo, bambú y faroles de piedra. |
| `cherry_module_cutaway.png` | El mismo módulo cortado en y=4: escritorios de pie (`standing_desk`) con monitor y lámpara, agentes **de pie** (sin sillas), pasillo con la alfombra del color del entorno, pantalla grande (3×2) al fondo del pasillo, puerta abierta de 3 sin escalón. |
| `cherry_section_wing.png` | Corte transversal del ala: perfil del tejado (dentro de y ≤ 10), escritorio de pie contra el muro y monitor a la altura de los ojos. |
| `cherry_hub.png` | Hub y entrada: tejado de dos niveles con clerestorio de shoji, cúpula con el faro (P5), torii, deck (engawa), sendero de grava y dos cerezos. |
| `cherry_hub_cutaway.png` | Hub cortado en y=5: pared kanban de hilos al oeste (P3, `thread_board`), pantalla grande del hub al este (P1), bancos mirando a ambas, tablero "Needs attention" con su tejadillo, columna libre en el centro para el holograma (P4) y nicho de la terminal de archivo junto a la entrada. |
| `cherry_plan.png` | Planta cortada en y=2 (1 proyecto). |
| `cherry_1p_full.png`, `cherry_1p_cutaway.png` | HQ completo con 1 proyecto (4 hilos). |
| `cherry_2p_full.png`, `cherry_2p_cutaway.png` | 2 proyectos, uno con anexo (8 + 3 hilos, 1 archivado cada uno). |
| `cherry_4p_full.png`, `cherry_4p_cutaway.png` | 4 proyectos con anexos (9, 5, 7 y 2 hilos). |
| `vanilla-fallback_hub.png`, `vanilla-fallback_module_cutaway.png` | El mismo estilo con el módulo de bloques **apagado** (fallback vanilla: postes de cerezo oscuro, calcita, papel blanco, losas de cerezo). |
| `classic_2p_full.png` | El estilo anterior ("classic", `hq.style = classic`) sobre el mismo layout nuevo, para comparar. |

Las figuras de colores son NPCs de referencia que dibuja el renderer en los anclajes (escritorios, tablero,
bancos, terminal y pared kanban); no son bloques del plan.

## Layout nuevo (igual en todos los estilos)

- Misma rejilla de módulos (`HqGeometry`: hub 17×17, filas de 14, segmentos de 4, `TOP = 10`), así que el
  site, la envolvente y los builders no cambian.
- **De pie**: cada escritorio ocupado deja libre la celda de delante (pies y cabeza) sobre suelo firme; el
  ancla `desk_<hilo>` apunta al monitor. Los escritorios vacíos no se construyen.
- **Archivo**: una sola terminal (`archiveTerminal`, `t3craft:archive_terminal`) en un nicho del muro sur
  del hub, al este de la entrada, con su sitio de pie (`archive`). No hay estantes por hilo.
- **Pantallas**: `bigscreen_hub@5x3=env:<entorno>` en el muro este del hub y `bigscreen_module@<i>@3x2=project:<proyecto>`
  al fondo del pasillo de cada módulo; `thread_wall` y `thread_wall_end` (9×4) en el muro oeste del hub;
  `hologram` y la columna libre sobre el centro del hub; `beacon` en la cúpula; monitor y lámpara por escritorio.
- Puertas abiertas de 3 de ancho y 3 de alto sin escalones; pasillos de al menos 2 de ancho.

## Herramienta: `hq-render` (propia, MIT)

`mod/src/hqrender/` es un source set propio (nunca entra en el jar del mod). Arranca los registros vanilla
como los tests JUnit (sin lanzar Minecraft), registra los bloques del mod, genera el plan y lo dibuja con un
*ray caster* ortográfico en Java puro: lee blockstates, modelos y texturas **en memoria** desde el jar de
Minecraft que Loom ya tiene en el classpath y desde `assets/t3craft` del mod, con sombras y bloques emisivos.

```
cd mod && JAVA_HOME=$HOME/.local/jdks/jdk-25.0.5+8 ./gradlew hqRender                      # todas las vistas → build/hq-render
./gradlew hqRender -PhqArgs="--style classic --only 2p_full"                                # otro estilo, filtrar vistas
./gradlew hqRender -PhqArgs="--vanilla --out build/hq-render-vanilla"                       # sin el módulo de bloques
```

Opciones: `--style ID`, `--only a,b`, `--scale F`, `--ss N` (supersampling), `--vanilla`, `--no-npcs`, `--out DIR`.

### Por qué esta herramienta (opciones evaluadas, 2026-11)

| Opción | Licencia | ¿Sirve en la VPS? |
|---|---|---|
| Servidores MCP de Minecraft (yuniko-software/minecraft-mcp-server, Apache-2.0; CDX-1/minecraft-client-mcp, MIT; Kagurazaka135/minecraft-mcp, MIT; gerred/mcpmc, MIT) | MIT / Apache-2.0 | **No**: todos controlan un bot de Mineflayer conectado a un servidor de Minecraft en marcha, y aquí no se puede lanzar ninguno. |
| minecraft-library/asset-renderer | Apache-2.0 | Parcial: render headless de 26.1+, pero de **bloques sueltos**, no de estructuras. |
| mcrender (Mineways + Blender) | GPL-3.0 | **No**: Mineways es solo de Windows (necesita Wine) y Blender; es pesado y GPL. |
| Chunky | GPL-3.0 | **No**: renderiza mundos guardados (región/chunks), no planes, y no es headless trivial. |
| mcschematic (Python), Amulet | MIT / licencia propia | Generan o editan schematics, pero **no renderizan**. |
| isocraft | sin licencia clara | Lee schematics antiguos; sin licencia utilizable. |
| **Ray caster propio sobre el `BuildPlan`** | MIT (código del mod) | **Sí**: sin servidores ni dependencias nuevas, usa los modelos reales (variants, multipart, rotaciones y kit del mod) y tarda menos de 1 s por vista. |
