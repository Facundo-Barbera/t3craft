# Interfaces del juego e integraciones externas: mapa inicial

Rama `research/game-ui-integrations`, fecha 2026-11-09. Solo investigación: no se tocó código, no se compiló nada y no se
abrió ninguna imagen. Las rutas de código pueden cambiar por la limpieza en curso (issues #3 a #8).

**Fuentes**
- Repo: `README.md`, `docs/features/*.md` (portable, chat, chat-compose, t3-ui, t3-actions, t3-composer, op-launch,
  op-queue, schedule-board, archive-view, hub-hologram, panel-chips, blocks, workstation, config, lead-office),
  `docs/research/inventario.md`, `docs/research/agentcraft-world-elements.md`, `docs/research/compat.md` y el código en
  `mod/src/client/java/dev/t3craft/client/` y `mod/src/main/java/dev/t3craft/t3/`.
- Web (texto): docs de Fabric (HUD), GLFW, ntfy, Discord, VS Code y el repo `pingdotgg/t3code`. Las URLs están al final.
- Marca **(verificar)**: no pude confirmar el nombre exacto de la API en Minecraft 26.3 sin compilar. Antes de implementar
  hay que mirar las firmas reales (el repo ya usa `javap` sobre el jar para eso, ver `docs/research/ui-audit.md`).

**Fuera de alcance:** el issue #1 (T3 Connect o una CLI de T3 Code como backend del mod) lo investiga otro hilo. Aquí solo se
cita donde cambia la respuesta (ver "Dependencia con el issue #1").

---

## 1. Estado actual

### 1.1 Interfaces dentro del juego

| Categoría | Qué hay hoy | Dónde |
|---|---|---|
| Pantalla principal | Pantalla única "T3" (tecla `Y`): sidebar de hilos por proyecto, búsqueda (Ctrl+F), nuevo hilo (Ctrl+N), conversación con markdown, tool calls plegables, composer con imágenes, undo/redo, steer/cola, selector de modelo | `docs/features/t3-ui.md`, `t3-composer.md`, `chat.md`, `chat-compose.md`; `client/t3ui/`, `client/chat/` |
| Terminal portátil | Ítem `t3craft:t3_terminal` (receta barata), pantalla con entornos, hilos y notificaciones; pantalla "en la mano" dibujada en una textura propia | `docs/features/portable.md`; `client/portable/` (`held/`) |
| HUD | `StatusBar` (conteos working / needs you / error + badge "n waiting · tecla"), `PeekHud` (banner de entornos) vía `HudElementRegistry` | `client/hud/` |
| Avisos | `Notifier`: línea de chat con botón `[Answer]`, `SystemToast` (un id por tipo), 3 sonidos vanilla (campana, bajo, level-up), registro de notificaciones. Se pueden apagar por tipo | `client/hud/Notifier.java`, `Toasts.java`; `docs/features/config.md` |
| Acciones de T3 (operate) | Interrumpir, stop, settle, snooze, pin, archivar, responder aprobaciones y preguntas, nuevo hilo (`launchThread`), cola de runs (ver, reordenar, cancelar), cambiar modelo, acceso y plan mode. Confirmaciones en el juego para lo destructivo y `audit.log` | `docs/features/operate.md`, `t3-actions.md`, `op-launch.md`, `op-queue.md`; `client/operate/`, `client/queue/` |
| Config | `SettingsScreen`, `EnvironmentsScreen`, Mod Menu (`ModMenuIntegration`), `t3craft.json` con migraciones | `docs/features/config.md`; `client/config/` |
| Teclas | Categoría "T3Craft": `J` ajustes (por defecto), `Y` terminal, `G` iniciar demo/HQ; sin asignar: entornos, pair, reconnect, reload, toggle notificaciones | `client/config/T3CraftKeys.java`, `portable/PortableClient.java`, `demo/DemoClient.java` |
| Comandos | `/t3craft status \| reconnect \| reload \| pair \| unpair \| envs \| answer \| search \| snooze \| queue \| schedule \| archive \| operate …` y comandos de HQ, site, builders, survival | `client/T3CraftCommands.java` y vecinos |

### 1.2 Elementos en el mundo

| Elemento | Qué hay hoy | Dónde |
|---|---|---|
| NPC por hilo | `RemotePlayer` de cliente con skin por entorno/estado, nameplate con modelo, panel (entorno · proyecto, rama), partículas vanilla, bocadillo con el último resumen, comportamiento (escritorio, tablero, deambular, festejo), helpers para subagentes | `docs/features/npc-behavior.md`, `panel-chips.md`, `npc-helpers.md`; `client/npc/` |
| Text/Item/Block displays | Etiquetas, tablero de tareas programadas (`TextDisplay`, rota de página), holograma del hub (cabecera, detalle y haz con `BlockDisplay`), props de la estación de trabajo | `hub-hologram.md`, `schedule-board.md`, `workstation.md` |
| Bloques propios | `monitor` (multibloque, log en vivo), `status_lamp`/`board_lamp`, `decision_podium`, `thread_board` (pared kanban), `archive_terminal`, `merge_station` (PRs), pantalla de estado, kit decorativo y faroles de papel | `docs/features/blocks.md`, `thread-wall.md`, `archive-terminal.md` |
| HQ | Ciudadela generada, oficina del líder, constructores animados, mundo demo | `docs/features/hq-*.md`, `lead-office.md`, `builders.md` |
| Enlaces | `Blaze3D.openUri` para URLs web y PR (con confirmación vanilla). Los archivos se **copian** al portapapeles, nunca se abren | `client/chat/Links.java`, `ConversationPanel.java`, `MergeStationScreen.java` |

### 1.3 Integraciones externas

**Hoy no hay ninguna.** Lo único que sale del juego es el WebSocket a cada entorno de T3 (`t3/T3Connection`, `PairingClient`,
`OperateConnection`) o al bridge legacy. No hay webhooks, notificaciones del sistema, Discord, GitHub directo, voz ni MCP.
Tampoco hay sonidos propios (no existe `assets/t3craft/sounds*`), ni resource packs integrados, ni mapas, libros, boss bars,
scoreboard, action bar o título (`grep` sin resultados).

### 1.4 Huecos que se ven en el código (T3 ya los expone)

Del inventario (`docs/research/inventario.md`) y de un `grep` en `mod/src`:
- **Diff / revisión de cambios:** no hay pantalla. `getTurnDiff` y `getFullThreadDiff` no aparecen en el código.
- **Costo y tokens:** `usageLimitResetAt` y `serverGetUsageSummary` no se usan.
- **Salida de comandos en vivo:** `subscribeCommandOutput` no se usa.
- **Teclas para la cola de atención:** hay aviso con `[Answer]`, pero ninguna tecla de "saltar al siguiente hilo que me necesita".
- **P11 (partículas teñidas) y P15 (toasts con retrato)** de `agentcraft-world-elements.md` siguen sin hacerse.

---

## 2. Catálogo: interfaces del juego

Esfuerzo: S ≈ ½ a 1 día, M ≈ 2 a 4 días, L ≈ una semana o más. Todo es **client-side** salvo que se diga otra cosa, así que
funciona en servidores remotos (modo GUI-only, `compat.md`).

### G1. Sonido propio y espacial
- **Qué es:** `sounds.json` + OGG en el jar. Un cue distinto por estado, un timbre/tono por entorno, y sonidos posicionales:
  tecleo suave cerca de un escritorio con hilo `working`, un "ding" en el podio cuando algo necesita al jugador.
- **Aporta:** hoy los 3 avisos usan notas vanilla y suenan en la UI. Un sonido por estado se oye aunque no mires el HUD, y
  el tecleo ambiente hace que el HQ se sienta vivo sin leer nada.
- **Viabilidad:** `SoundEvent` con id propio y `SimpleSoundInstance` posicional (`forUI` se usa hoy). Un `SoundEvent` creado por
  id no necesita registro de servidor para reproducirse en el cliente. **(verificar)** el constructor en 26.3.
- **Esfuerzo:** S (cues) / M (ambiente posicional con límite de voces).
- **Riesgos:** los OGG hay que generarlos (el pipeline `tools/art` es Python sin ffmpeg garantizado) o usar sonidos con licencia
  libre; ruido en el HQ si hay muchos hilos (necesita tope y atenuación).

### G2. Avisos discretos: action bar y título
- **Qué es:** mensaje en la barra de acción (`setOverlayMessage`) o título/subtítulo para "tu hilo X necesita respuesta", sin
  toast ni chat.
- **Aporta:** una capa intermedia entre el chat (se pierde) y el toast (tapa). Útil en combate o con el chat oculto.
- **Viabilidad:** API de cliente de `Gui`, sin servidor. **(verificar)** el nombre en 26.3 (el mod ya usa `mc.gui.hud.getChat()`,
  así que hubo reorganización).
- **Esfuerzo:** S.
- **Riesgos:** otros mods lo usan. Hay que respetar `notifications.*` y no apilar.

### G3. Indicador direccional al hilo que te necesita
- **Qué es:** flecha en el borde del HUD (o marcador flotante) hacia el NPC / escritorio / podio del hilo en `needs_attention`.
- **Aporta:** la ciudadela es grande y los NPC están en sus escritorios; hoy hay que abrir la pantalla `Y` para saber cuál es.
- **Viabilidad:** proyección de coordenadas del NPC a pantalla en un `HudElement`. Cliente puro.
- **Esfuerzo:** S a M.
- **Riesgos:** declutter con la `StatusBar` y con nameplates (ver P13).

### G4. Teclas para la cola de atención
- **Qué es:** teclas sin asignar por defecto: "siguiente hilo que me necesita" (cicla y abre la tarjeta de respuesta), "último
  chat abierto", "aprobar / negar" con confirmación.
- **Aporta:** el flujo principal del mod es atender lo que bloquea a los agentes. Hoy es `Y` + buscar, o el botón del chat.
- **Viabilidad:** `KeyMappingHelper` ya se usa y `Notifier.takeRecentAsk` ya sabe "el hilo que preguntó hace poco".
- **Esfuerzo:** S.
- **Riesgos:** choques de teclas (por eso quedan sin asignar). Aprobar con una tecla es riesgoso: debe pasar por la confirmación
  que ya existe.

### G5. Menú radial de acciones sobre el NPC
- **Qué es:** clic derecho largo sobre un NPC, monitor o podio abre un menú radial: Chat, Interrumpir, Settle, Snooze, Abrir PR.
- **Aporta:** las acciones de operate están en el header del chat; en el mundo solo se abre el chat. Esto cierra el círculo
  diegético sin pasar por la pantalla.
- **Viabilidad:** `Screen` propia con el hook de clic que ya existe (`UseEntityCallback`). Las acciones ya pasan por `OperateGate`.
- **Esfuerzo:** M.
- **Riesgos:** duplica UI (header + radial); mantener un solo catálogo de acciones. Existe un radial de construcción
  (`docs/features/build-radial.md`) cuya implementación conviene mirar antes de duplicar.

### G6. Toasts propios (retrato, barra, estado)
- **Qué es:** una clase `Toast` propia (retrato de la skin del NPC, franja del color del estado, contador "×3") en lugar de
  `SystemToast`. Es la P15 pendiente de AgentCraft.
- **Aporta:** se reconoce de un vistazo qué hilo y qué estado sin leer. Los toasts del mismo hilo se fusionan.
- **Viabilidad:** la interfaz `Toast` de vanilla es pública; se añade con `toastManager().addToast`. **(verificar)** firma en 26.3.
- **Esfuerzo:** S a M.
- **Riesgos:** los toasts de vanilla no son clicables; el "Y to answer" se mantiene como hoy.

### G7. El ítem Terminal con estado
- **Qué es:** el tooltip del `t3_terminal` muestra los conteos (3 working · 1 needs you) y el ítem cambia de textura o muestra una
  insignia cuando hay algo pendiente.
- **Aporta:** el inventario es el lugar donde el jugador ya mira; avisa sin HUD.
- **Viabilidad:** `ItemTooltipCallback` (cliente) para el tooltip. Para el cambio de modelo, las definiciones de ítem de 26.x
  admiten propiedades condicionales, pero una propiedad propia necesita registro **(verificar)** y toca el módulo de ítems (main).
- **Esfuerzo:** S (tooltip) / M (modelo dinámico).
- **Riesgos:** el modelo dinámico entra en la zona "RESTART" y en el módulo que se puede apagar.

### G8. Modo observador / cámara del agente
- **Qué es:** "ver lo que hace" un hilo: la cámara se pone en su escritorio (o en la del NPC) y el monitor se ve grande, con el
  log en vivo.
- **Aporta:** convierte el HQ en una sala de control; combina con el monitor de log en vivo (P2) que ya existe.
- **Viabilidad:** `Minecraft.setCameraEntity` con una entidad de cliente, o simplemente un teletransporte suave del jugador al
  ancla del monitor. **(verificar)** que una `RemotePlayer` sin servidor sea aceptada como cámara.
- **Esfuerzo:** M.
- **Riesgos:** mareo, interacción con el control del jugador, y en servidores remotos puede chocar con la posición real.

### G9. Pantalla de diff y revisión
- **Qué es:** `DiffScreen`: lista de archivos A/M/D, unified diff con colores, enlace al PR. Es lo que AgentCraft llama
  `DiffScreen` y lo que la `merge_station` hoy solo enlaza.
- **Aporta:** revisar un cambio sin salir del juego. Es el hueco más grande del inventario (T3 lo expone con `orchestration:read`).
- **Viabilidad:** `getTurnDiff` / `getFullThreadDiff` por RPC de solo lectura. El renderizado de texto con colores ya existe
  (`Markdown`, `TextWrap`, `ChatDraw`).
- **Esfuerzo:** M a L (diffs grandes: paginación y límites).
- **Riesgos:** diffs enormes (memoria, scroll); leer código en una GUI de Minecraft es incómodo, así que debe ser un resumen +
  "abrir en el editor" (ver I6).

### G10. Costo, tokens y límites
- **Qué es:** línea de uso en el panel y en el holograma ("$1.20 hoy", "límite en 2 h").
- **Aporta:** visibilidad de gasto, que AgentCraft ya muestra en `/status`.
- **Viabilidad:** `usageLimitResetAt` en el shell (read). `serverGetUsageSummary` pide `diagnostics:read`, que el pairing actual
  rechaza (solo `read` + `operate`), así que el costo exacto no sale sin ampliar scopes.
- **Esfuerzo:** S (solo lo que ya viene en el shell) / M (con otro scope).
- **Riesgos:** el scope extra choca con la política "el mod rechaza todo scope de más".

### G11. Salida de comandos en vivo en el monitor
- **Qué es:** el monitor muestra el tail del comando que corre el agente (`subscribeCommandOutput`).
- **Aporta:** se ve "qué corre" además de "qué dice".
- **Viabilidad:** read; un stream acotado por comando.
- **Esfuerzo:** M.
- **Riesgos:** volumen de streams (solo los monitores cercanos), secretos impresos por comandos en pantalla.

### G12. Resource packs integrados (temas y accesibilidad)
- **Qué es:** packs opcionales dentro del jar: tema alto contraste, tema oscuro del papel, sonidos alternativos.
- **Aporta:** personalización sin tocar código; accesibilidad.
- **Viabilidad:** Fabric permite packs integrados activables por el jugador (`ResourceManagerHelper.registerBuiltinResourcePack`
  y su reemplazo de la 1.21.9, con carpeta `resourcepacks/<id>/` en el jar). **(verificar)** la API de 26.x.
- **Esfuerzo:** S.
- **Riesgos:** hay que mantener los assets sincronizados con los cambios de GUI; casi nada de la UI depende de texturas (se
  dibuja con `fill`).

### G13. Boss bar / scoreboard / tab list
- **Qué es:** barra de jefe cliente con el progreso del plan de un hilo; lista de la tecla Tab con los hilos como "jugadores".
- **Aporta:** poco: la `StatusBar` custom ya cubre el resumen y lo hace mejor.
- **Viabilidad:** boss bar: `BossHealthOverlay` guarda un mapa de `LerpingBossEvent`; sin servidor hay que usar un mixin o access
  widener. Scoreboard: el cliente lo reemplaza al unirse, no vale la pena. Tab: posible con entradas de `PlayerInfo` falsas.
- **Esfuerzo:** S a M.
- **Riesgos:** mixins, choques con otros mods. **No recomendado.**

### G14. Libros y mapas
- **Qué es:** un libro (`BookViewScreen`) para leer el plan de un hilo; un mapa (ítem) como minimapa del HQ con puntos por estado.
- **Aporta:** estética diegética; el libro como "plan para revisar" en la oficina del líder. El minimapa sirve en ciudadelas grandes.
- **Viabilidad:** `BookViewScreen` abre libros arbitrarios en el cliente. Un mapa propio requiere mixin o textura propia.
  Ya existe una pantalla en mano con textura dinámica (`held/HeldScreenTexture`), así que el mapa no hace falta.
- **Esfuerzo:** S (libro) / M (minimapa).
- **Riesgos:** valor bajo frente al chat con markdown que ya existe. **Solo si Facundo quiere el efecto "diegético".**

### G15. Narrador (accesibilidad)
- **Qué es:** leer en voz alta los avisos ("el hilo X necesita tu respuesta") con el narrador del juego.
- **Aporta:** accesibilidad, y avisos en manos libres.
- **Viabilidad:** `GameNarrator` es público. En Windows y macOS funciona; en Linux depende de la librería `flite` instalada, y si no
  está falla con errores de librería.
- **Esfuerzo:** S.
- **Riesgos:** el narrador de Minecraft se activa por opción global del jugador. Depende del SO del jugador.

### G16. Shaders y post-efectos
- **Qué es:** viñeta roja en error, brillo en `needs you`.
- **Aporta:** casi nada que ya no den los colores y partículas. **No recomendado.** Los shaders además chocan con Iris/Sodium.
- **Esfuerzo:** L.

### G17. Lo que falta de la lista de Facundo
- **Carteles (sign):** son bloques con entidad de servidor; el cliente no puede crear uno que se vea. El HQ ya los usa en la
  construcción (`CitadelHub`, `BarrioGateBig`). Los `TextDisplay` de cliente los reemplazan. Nada que proponer.
- **Mod Menu:** ya integrado (`ModMenuIntegration`). Nada nuevo.
- **Partículas y fuegos artificiales:** ya hay festejo con `FIREWORK`. Pendiente solo P11 (partículas teñidas), S.
- **Interaction entities / botones clicables en hologramas:** hoy los displays no tienen hitbox. Un `raycast` propio sobre
  anclas daría botones Allow/Deny en el podio (P7). M. Va junto con G5.

---

## 3. Catálogo: integraciones externas

**Premisa a confirmar con Facundo:** el mod solo corre mientras Minecraft está abierto. Todo lo que sale del mod (webhooks,
notificaciones) deja de funcionar con el juego cerrado. Ver pregunta 1.

### I1. Señal del sistema sin procesos: título de ventana y parpadeo de la barra de tareas
- **Qué es:** cuando el juego no tiene el foco y un hilo pasa a `needs_attention`: cambiar el título de la ventana
  ("T3Craft · 2 te necesitan") y pedir atención al SO (`glfwRequestWindowAttention`).
- **Aporta:** la forma más barata de avisar con el juego en segundo plano. Sin procesos ni red ni permisos.
- **Viabilidad:** GLFW ≥ 3.3 expone `glfwRequestWindowAttention`; en Windows hace parpadear la barra, en macOS rebota el Dock
  (atención a la aplicación) y el SO la cancela al enfocar. LWJGL lo trae. Hay que llamarla desde el hilo principal (el tick
  del cliente lo es). Título: `glfwSetWindowTitle`. Cliente puro.
- **Esfuerzo:** S.
- **Riesgos:** comportamiento por SO (en algunos Linux/Wayland no hace nada visible). Respetar `notifications.*`.

### I2. Notificaciones nativas del SO
- **Qué es:** toast del escritorio con título, texto y el nombre del hilo.
- **Aporta:** avisa con el juego minimizado, sin depender de la barra de tareas.
- **Viabilidad:** `java.awt.SystemTray` **no sirve** dentro de Minecraft en macOS: AWT y GLFW compiten por el run loop de Cocoa.
  Alternativa segura: lanzar un proceso del SO: `notify-send` (Linux), `osascript -e 'display notification …'` (macOS), PowerShell
  con el toast de Windows. Cliente puro.
- **Esfuerzo:** S a M (tres plataformas, escapado de argumentos).
- **Riesgos:** inyección por título/texto del hilo en los argumentos (escapar siempre y no pasar por shell); lanzar procesos desde
  un mod puede alarmar a antivirus; hay que mostrarlo solo sin foco.

### I3. Push al teléfono: ntfy (o Pushover, Telegram)
- **Qué es:** un `POST` a un tema de ntfy con `Title`, `Priority`, `Tags` y `Click` (por ejemplo `t3code://threads/<env>/<hilo>`
  para abrir la app). ntfy admite hasta 3 botones de acción en la notificación.
- **Aporta:** aviso en el móvil cuando el jugador está lejos, con un toque abre T3 en el hilo. Es la integración más barata
  de "te necesito" fuera del juego.
- **Viabilidad:** HTTP simple. `java.net.http.HttpClient` ya se usa en `t3/`. Cliente puro.
- **Esfuerzo:** S.
- **Riesgos:** el tema de ntfy.sh es la única "contraseña" (usar tema largo o token); no poner títulos de hilos sensibles en un
  servidor público (se puede autohospedar); sale del juego, así que debe ser opt-in con aviso claro.

### I4. Webhooks genéricos de eventos (Discord, Slack, Home Assistant, luces)
- **Qué es:** un solo módulo: `config.integrations.webhooks[]` con una URL y un filtro de eventos (needs you, error, done,
  hilo nuevo, run terminado). Cubre Discord, Slack, Home Assistant (luz que cambia de color), IFTTT y n8n.
- **Aporta:** una pieza, muchos destinos. Discord permite `content` hasta 2000 caracteres, hasta 10 `embeds`, `username` y
  `avatar_url`; los webhooks que no son de una aplicación no admiten botones.
- **Viabilidad:** HTTP `POST`. Se reutiliza el punto de decisión de `Notifier.transition` para disparar eventos. Cliente puro.
- **Esfuerzo:** S (Discord) / M (plantillas, reintentos, límites).
- **Riesgos:** la URL del webhook es un secreto (guardar como credencial, no en `t3craft.json`; nunca en logs); los límites de
  Discord no están en esa página (el servidor responde 429, hay que respetar `Retry-After`); un hilo que oscila de estado puede
  inundar el canal (debounce y agrupar).

### I5. Abrir T3 (app o web) en el hilo
- **Qué es:** botón "Open in T3" que abre el hilo en la app de escritorio, la app móvil o la web.
- **Aporta:** pasar del juego a la herramienta real sin buscar el hilo.
- **Viabilidad:** hoy el mod conoce `t3-thread://v2/<env>/<hilo>`, que según T3 es solo un formato de enlace de markdown, no un
  esquema registrado del SO. La app de iOS reconoce `t3code://threads/<env>/<hilo>` y hay un pedido para el escritorio
  (issue `pingdotgg/t3code#26915`). La web usa una URL de relay (`https://<relay>.t3coderelay.com/<env>/<hilo>`). `Blaze3D.openUri`
  ya abre URLs. **(verificar)** qué esquema acepta hoy el escritorio.
- **Esfuerzo:** S.
- **Riesgos:** depende de que T3 registre el esquema; el entorno en el esquema es el id de T3, no el que el mod muestra.

### I6. Abrir archivos en el editor
- **Qué es:** en el chat, un archivo mencionado (`/ruta/archivo.ts:42`) se abre en VS Code, Cursor o Zed.
- **Aporta:** hoy la ruta solo se copia (`Links.Kind.FILE`) porque el juego corre en otra máquina que los archivos.
- **Viabilidad:** `vscode://file/{ruta}:línea:columna` y, para archivos remotos, `vscode://vscode-remote/ssh-remote+host/ruta`
  (documentado). El host sale de la URL del entorno de T3. Cursor y Zed tienen esquemas parecidos **(verificar)**.
- **Esfuerzo:** S a M (mapa entorno → host SSH configurable).
- **Riesgos:** el esquema debe estar registrado en la máquina del jugador; la ruta del agente puede no existir donde corre el
  editor; confirmar siempre antes de abrir.

### I7. GitHub: issues y PRs
- **Qué es:** (a) ya mostrado: PR vinculado, estado, +N/−N, `merge_station`; (b) añadir enlaces a checks y a la lista de PRs;
  (c) crear un issue o PR desde el juego.
- **Aporta:** (a) existe. (b) y (c) cerrarían el ciclo, pero T3 tiene su propio flujo de PR y expone
  `source-control:write` (fuera de alcance, inventario ítem 25).
- **Viabilidad:** lo correcto es usar a T3 como intermediario (sus RPC de PR) antes que una API de GitHub con un token en el mod.
  Un token de GitHub en la config sería un secreto más y duplica lo que T3 ya hace.
- **Esfuerzo:** S (enlaces) / L (escritura vía `source-control:write`, necesita otro scope).
- **Riesgos:** ampliar scopes del pairing; no se recomienda guardar un PAT en el mod.

### I8. Voz: dictado y lectura
- **Qué es:** (a) mantener una tecla para dictar al composer; (b) leer la respuesta en voz alta.
- **Aporta:** hablar con el agente sin teclear, útil en VR o con las manos ocupadas.
- **Viabilidad:** el micrófono se captura con OpenAL (`ALC11.alcCaptureOpenDevice`, ya incluido en LWJGL) o `javax.sound`. La
  transcripción exige un motor: local (whisper.cpp en la máquina del jugador, por proceso externo) o una API. Lectura:
  `GameNarrator` (ver G15) o TTS externo. Cliente puro, pero el mic pide permiso del SO en macOS.
- **Esfuerzo:** M (dictado con un motor local) / L (embebido).
- **Riesgos:** privacidad (audio fuera de la máquina), latencia, dependencias nativas, coste de la API. **Alto esfuerzo, valor
  incierto.**

### I9. MCP: lo que ya existe y lo que se podría ofrecer
- **Qué es:** T3 ya corre su propio servidor MCP (`https://<entorno>/mcp`, OAuth, HTTP) para que agentes externos lean
  proyectos y hilos, lancen y manden mensajes a hilos y consulten proveedores y modelos. Dos direcciones posibles para el mod:
  1. **El mod como cliente MCP de T3** (otro transporte a T3, además del WebSocket). Eso es del issue #1, no se desarrolla aquí.
  2. **El mod como servidor MCP local**: expone al agente herramientas del jugador (`notify_player`, `show_on_hologram`,
     `ask_player_in_game`, `player_location`), para que un agente "llame" al jugador dentro del juego.
- **Aporta:** (2) es la única idea que da algo **nuevo** a los agentes en vez de solo mostrar lo que ya hacen.
- **Viabilidad:** (2) un servidor HTTP/stdio en el cliente. Los agentes corren en otras máquinas (VPS), así que hace falta red
  (Tailscale) y autenticación. Los clientes MCP de Claude Code y Codex admiten HTTP remoto con OAuth. Es la idea más especulativa.
- **Esfuerzo:** L.
- **Riesgos:** superficie de ataque (un agente controla el juego del jugador), autenticación, y el modelo de seguridad actual
  (el mod no escucha puertos; `compat.md`) cambia. **Explorar solo si Facundo ve valor.**

### I10. API local de estado (Stream Deck, barra de menú, OBS)
- **Qué es:** el mod sirve `http://127.0.0.1:<puerto>/status.json` y un stream con los conteos y los hilos visibles.
- **Aporta:** integración con Stream Deck, Raycast/Alfred, waybar, un overlay de OBS. AgentCraft tiene algo parecido
  (DevBridge en `:7879`).
- **Viabilidad:** `com.sun.net.httpserver` en loopback. Cliente puro.
- **Esfuerzo:** S a M.
- **Riesgos:** abre un puerto (solo loopback, con token); casi todo lo que muestra ya lo ofrece T3 directamente a esas
  herramientas. El valor propio del mod sería "la vista filtrada por mi configuración", que es poco.

### I11. Discord Rich Presence
- **Qué es:** el estado de Discord del jugador dice "3 agentes trabajando en t3craft".
- **Aporta:** vistoso, nada funcional.
- **Viabilidad:** IPC local con el cliente de Discord (socket unix o pipe). Existen mods y bibliotecas. **(verificar)** si hay
  un cliente Java mantenido.
- **Esfuerzo:** M.
- **Riesgos:** expone títulos de hilos a terceros. **No recomendado.**

### I12. Calendario y tareas programadas
- **Qué es:** exportar el tablero de tareas programadas (`scheduledTasks.subscribe`) como feed `.ics`, o crear tareas desde el juego.
- **Aporta:** bajo. T3 ya tiene su UI de tareas programadas. Escribir es el ítem 26 del inventario (no aprobado).
- **Esfuerzo:** S (ics) / M (escritura).
- **Riesgos:** escritura sin aprobación. **No recomendado ahora.**

---

## 4. Dependencia con el issue #1

Si el issue #1 termina en "el mod habla con T3 Connect o con una CLI/daemon de T3", cambian tres cosas aquí:
- **Notificaciones con el juego cerrado (I2, I3, I4):** las dos opciones del mod siguen exigiendo el juego abierto. Si Facundo
  quiere avisos sin el juego, necesita un proceso aparte que escuche a T3 (el bridge actual, congelado como legacy en el issue #7,
  o el daemon/CLI que salga del #1) y dispare los webhooks. El módulo de eventos (I4) conviene escribirlo como una pieza pura,
  independiente del cliente, para poder moverlo.
- **Autenticación (I9):** el servidor MCP de T3 usa OAuth con sesiones de 30 días, igual que el pairing. Lo que decida el #1
  sobre cuentas de T3 Connect afecta cómo un servidor MCP del mod se autenticaría hacia el exterior.
- **Scopes (G10, I7):** costo exacto y escritura de PR piden scopes que el mod hoy rechaza.

---

## 5. Top recomendado

Criterio: valor para el flujo "agentes trabajando, el humano atiende lo que bloquea", esfuerzo, riesgo y que no choque con la
limpieza en curso. Todo es cliente puro.

| # | Idea | Esfuerzo | Por qué primero |
|---|---|---|---|
| 1 | **I1 + G2: señales discretas** (título de ventana, parpadeo de barra, action bar) | S | Resuelve "juego en segundo plano" y "no me di cuenta" casi gratis, sin procesos ni red. Se apoya en `Notifier`. |
| 2 | **G4 + G3: cola de atención** (teclas "siguiente que me necesita" y flecha hacia su NPC) | S a M | El flujo principal del mod. Hoy hay aviso, pero llegar al hilo cuesta varios pasos. |
| 3 | **I4 con I3: eventos salientes** (webhook genérico con ntfy y Discord como primeras plantillas) | S a M | Una pieza que cubre móvil, Discord y domótica. Define el contrato de eventos que también vale para un daemon futuro. |
| 4 | **G1: sonido propio** (cue por estado + tecleo ambiente) | S a M | Los 3 avisos actuales son notas vanilla. El audio da presencia al HQ y avisa sin mirar. |
| 5 | **I5 + I6: saltar a T3 y al editor** (`t3code://`, `vscode://`) | S a M | Cierra el círculo juego a herramienta real. Depende de qué esquemas registre T3 en el escritorio. |
| 6 | **G9: pantalla de diff y revisión** | M a L | El mayor hueco funcional del inventario y es solo lectura. Se arma sobre `merge_station` y el chat. |
| 7 | **G6: toasts propios con retrato** | S a M | La P15 que quedó pendiente, y refuerza 1 y 2. |
| 8 | **I9 (2): servidor MCP local** | L | Lo más nuevo (el agente llama al jugador), pero es lo más especulativo y cambia el modelo de seguridad. Explorar en frío, sin implementar. |

No recomendados por ahora: G13 (boss bar, scoreboard, tab), G16 (shaders), I11 (Rich Presence), I12 (calendario), G14 (libros y
mapas, salvo que se quiera el efecto diegético) y I10 (API local).

---

## 6. Preguntas para Facundo

1. **¿Dónde corre el juego y cuánto está abierto?** Si Minecraft está abierto solo a ratos, las integraciones salientes del mod
   (I2, I3, I4) tienen poco alcance y conviene un daemon aparte (ligado al #1). ¿Qué máquina y qué SO (Windows, macOS, Linux)?
   Cambia G15, I1, I2 y I8.
2. **¿Qué tanto "diegético" quieres?** ¿Prefieres que todo ocurra en el mundo (libros, mapas, podio, cámara), o priorizas rapidez
   de atención (teclas, flecha, pantalla)? Decide si G14, G8 y G5 suben o bajan.
3. **¿Aceptas que el mod envíe datos fuera de la máquina?** (títulos de hilos a ntfy.sh o Discord). ¿Autohospedado, o público
   con tema aleatorio? ¿Solo "hay algo pendiente" sin títulos?
4. **¿Qué hace falta para revisar cambios en el juego?** (G9) ¿Diff completo, solo lista de archivos y +N/−N, o solo el enlace
   al PR y al editor? Esto define si el diff es M o L.
5. **¿Scopes extra?** Costo y tokens (`diagnostics:read`) y escritura de PR (`source-control:write`) piden ampliar el pairing.
   ¿Se mantiene la regla "solo read + operate"?
6. **¿Servidor MCP del mod (I9)?** ¿Te interesa que un agente pueda llamar al jugador dentro del juego, o es demasiado?
7. **Audio:** ¿hay preferencia por sonidos sintetizados (generados en `tools/art`) o por un pack con licencia libre? (G1)
8. **Editor y esquemas:** ¿qué editor usas (VS Code, Cursor, Zed) y qué esquema abre la app de escritorio de T3 hoy? (I5, I6)
9. **¿Qué ideas convierto en issue?** El backlog vive en GitHub issues y `gh` está autenticado en esta máquina. No creo
   ninguno hasta que apruebes.

---

## 7. Anexo: cuánta libertad da la UI de Minecraft (unificar Settings, Pairing, etc. con el chat)

Pregunta de Facundo (2026-11-09): si el mod llevara su propia instancia de T3 Code, ¿cuánto se puede unificar y personalizar
la UI? ¿Iconos propios, texto con tamaño independiente del GUI scale?

**Respuesta corta: casi total.** Una `Screen` es Java arbitrario que dibuja en un lienzo de píxeles de GUI y recibe teclado y
ratón. Lo que se puede hacer ahí no depende de vanilla, solo de cuánto quiera escribir el mod.

### Por qué hoy las pantallas se ven distintas (en el código)
- **Chat y T3Screen** (`client/chat/`, `client/t3ui/`) dibujan con `ChatTheme` + `ChatDraw` + el kit de sprites
  `assets/t3craft/textures/gui/sprites/chat/*` (paneles nine-slice con `.mcmeta`, `pill`, `keycap`, `text_field`,
  `scroll_*`, `dot_*`, `icon_*`). Todo propio y tematizable (`ChatTheme.warmStudio()` / `terminal()`).
- **Settings, Environments, Pair, Confirm, NewThread, OperatePair** (`client/config/`, `client/screen/`) heredan de `Screen`
  con `BaseConfigScreen` y usan los **widgets vanilla** (`Button`, `EditBox`, el toggle "ON/OFF" en verde y rojo). De ahí el salto visual.
- Ya se hace en el mod lo difícil: composer propio con edición completa (`ComposerText`), listas con scroll, popovers, tooltips,
  nine-slice, texturas dinámicas (miniaturas de imágenes, pantalla en mano).

### Iconos propios: sí, de tres maneras
1. **Sprites** (ya existe: `chat/icon_*.png`). PNG en `textures/gui/sprites`, se dibujan con `blitSprite`; con `.mcmeta`
   `nine_slice` se estiran sin deformar. Un usuario puede reemplazarlos con un resource pack (skin gratis).
2. **Glifos en una fuente propia** (recomendado para iconos inline). Un `font/*.json` con un proveedor `bitmap` (PNG de
   iconos mapeados a caracteres Unicode del área privada) o `ttf` (una fuente de iconos vectorial). El icono se escribe
   dentro de cualquier `Component`: línea de chat, toast, tooltip, botón, label, y se tiñe con el color del texto.
   El mod ya usa fuentes por `Style.withFont` (`ConversationPanel`, `RequestCard` usan la `uniform` de vanilla).
3. **Dibujo por código** (`fill`, líneas, formas) y **texturas dinámicas** (`DynamicTexture` desde un `NativeImage`),
   por ejemplo un **QR** para el pairing o un avatar generado.
- Vigilar licencias: si se toman iconos de T3 Code o de una librería (Lucide, Phosphor), revisar la licencia; los logos de T3
  son marca.

### Tamaño de texto independiente del GUI scale: sí, con cuidado
- El GUI scale de vanilla es **entero** (1, 2, 3, 4, "auto") y global. Todo lo demás se multiplica por él. En 4K con 2 todo
  queda pequeño y con 3 enorme, y no existe 2,5.
- **`GuiGraphicsExtractor.pose()`** es una pila `Matrix3x2fStack` (traslación, rotación, escala). Se puede escalar el texto y todo
  el contenido por un factor fraccionario. El mod ya lo hace en el mundo (`MonitorRenderer`, `StatusScreen`).
- **Para que se vea nítido** hay que dejar de depender de la fuente bitmap de 8 px (se ve irregular a escalas no enteras) y
  usar un proveedor **`ttf`** con `size` y `oversample` (Inter, Geist, JetBrains Mono para código; revisar licencia OFL). Eso da
  texto vectorial, bordes finos y una tipografía más parecida a T3 Code.
- **Patrón recomendado: "UI scale" propio** (ajuste de 75 % a 200 %, o "igual que T3 web"). Cada pantalla de T3Craft calcula
  `k = deseado / guiScale`, aplica `pose().scale(k)` al empezar a dibujar, trabaja en un espacio de layout virtual
  (`width/k`, `height/k`) y **divide las coordenadas del ratón por `k`**. Es el mismo mecanismo para todas las pantallas, y
  no afecta a los demás mods ni al resto del juego.
- Alternativa descartada: cambiar `Window` guiScale mientras la pantalla está abierta. Es global, hay que restaurarlo y
  rompe el HUD, los toasts y otros mods.
- **Costes del patrón:** hay que transformar a mano el hit-testing y el cursor de texto; verificar que `enableScissor` y los
  tooltips respeten la escala **(verificar en 26.3)**; los tooltips de vanilla se dibujan sin escala, así que conviene
  tener los propios (el mod ya los tiene en el chat).

### Qué sí limita
- **Un solo lienzo:** todo cae dentro de la ventana del juego. Un segundo OS-window (AWT/Swing) choca con GLFW en macOS.
- **Accesibilidad hay que construirla:** los widgets propios no se narran ni se navegan con teclado, a diferencia de `Button`.
  Hay que implementar `NarratableEntry` y el foco con Tab, o aceptar la pérdida.
- **IME y texto:** el composer ya resolvió la entrada por SDL3 (`TextInputManager`); cada campo propio nuevo debe reutilizarlo.
- **Rendimiento:** pantallas grandes con markdown deben cachear glifos/líneas (el chat ya lo hace con `DrawnRows`).
- **Resource packs de usuario** pueden sobrescribir nuestros sprites. Es una ventaja si es intencional.

### Cómo se unificaría (propuesta de arquitectura, no implementada)
1. Extraer del chat un **mini UI kit** compartido: `T3Button`, `T3Toggle`, `T3Field`, `T3Select`, `T3List`, `T3Dialog`,
   `T3Tabs`, sobre `ChatTheme`/`ChatDraw` (que ya existen). Las pantallas actuales pasarían a usarlos.
2. Mover **Settings, Environments, Pairing/Operate y Nuevo hilo** a **paneles dentro de `T3Screen`** (como la página de
   Settings de T3 Code), no pantallas separadas; los diálogos de confirmación pasan a ser modales del kit.
3. Un solo **UI scale** y una sola **fuente TTF** para todo el kit, con un ajuste en Settings.
4. Un solo **catálogo de iconos** (sprites o glifos) con nombres estables.
- Esfuerzo orientativo: kit M, migrar las 6 a 8 pantallas M a L, UI scale + TTF M. Es independiente de la capa de red.

### Relación con "el mod corre su propio T3 Code (CLI)" (issue #1, otro hilo)
Si el mod lleva su propia instancia de T3 y usa T3 Connect, el **emparejamiento por entorno** (`/t3craft pair`, copiar el enlace
`https://host/pair#token=…`, `PairScreen`, `OperatePairScreen`, el toggle Read/Control) se reduce a un **flujo de inicio de sesión**
(código de dispositivo o abrir el navegador, con un QR) y a una lista de entornos. Eso **encoge la superficie de UI a unificar**.
Conviene decidir primero el #1 y luego rediseñar esas pantallas una sola vez. Lo que sí es independiente del #1: el kit, el UI
scale y la fuente.

## 8. Anexo: navegador dentro del juego

Pregunta de Facundo (2026-11-09): ¿se puede ver un "browser" dentro del juego para explorar cosas? Hay **cuatro vías reales**. La
más interesante no necesita meter Chromium en el mod.

### 8.1 Vía A (recomendada para empezar): el navegador del servidor de T3, transmitido al juego
T3 Code ya sabe correr una pestaña de navegador **en el servidor** (Chromium headless) y mostrarla a clientes que no tienen
runtime local: la app web, la móvil y el escritorio la ven así. Evidencia (contratos vendorizados en
`bridge/vendor/t3-contracts/src/preview.ts`, `auth.ts`, `rpc.ts`, y el código público de `pingdotgg/t3code`:
`packages/client-runtime/src/preview/serverBrowserStream.ts`, `apps/server/src/preview/ServerBrowserStream.ts`):

- **Protocolo:** WebSocket en `/api/preview-stream/ws?threadId&tabId&maxWidth&maxHeight`. El servidor manda **frames JPEG
  binarios**, el cliente responde `{"type":"ack"}` por cada uno (control de flujo) y **nunca recibe frames más grandes que
  `maxWidth` × `maxHeight`**. Mensajes JSON de estado: viewport, control, portapapeles, descargas, popups, diálogos, selector de archivos.
- **Entrada (JSON):** `mouse` (move/down/up, coordenadas en px CSS de la página, botones, modificadores, clickCount), `wheel`,
  `key` (con `key`, `code`, `keyCode` de Windows y `text`), `text`, `resize`, `viewport`, `navigate`, `history` (atrás/adelante),
  `reload`, `takeControl` / `releaseControl`, `dialog`.
- **Permisos:** **ver** exige `orchestration:read` (`authenticateMediaRequest(AuthOrchestrationReadScope)`), que el mod ya tiene.
  **Interactuar** exige el scope `preview:operate` (el servidor lo comprueba con `session.scopes.includes(AuthPreviewOperateScope)`
  y sin él entra en modo solo lectura). Hoy el mod **rechaza** cualquier scope fuera de `read` + `operate`, así que ese scope
  sería una decisión de política (pregunta 3 abajo). Un cliente que va con un servidor viejo trata `preview:operate` como
  hijo de `orchestration:operate` (`legacyParents` en `auth.ts`), pero **los servidores no amplían permisos**.
- **Qué se puede mostrar:** las pestañas de preview de un hilo (`preview.list`, eventos `subscribePreviewEvents`) y los
  **servidores locales que levantó el agente** (`subscribeDiscoveredLocalServers`, con `agent.threadId` cuando lo inició el
  hilo). Los agentes abren previews con `preview_open` (`runtime: "server"`) y T3 permite un `previewUrl` por script de proyecto.
- **Quién maneja la pestaña:** `control.controller` es `agent`, `you`, `another-viewer` o `unclaimed`. Un visor pasivo no
  roba el control (parámetro `interactive=false`).
- **Qué necesita el mod:** un cliente WebSocket (ya tiene uno para RPC), decodificar JPEG (`javax.imageio` sin ventana; el mod ya
  usa `java.awt.image` en `ImageSources`), subir a una `DynamicTexture` y dibujarla en una pantalla o en un monitor del
  mundo. Sin dependencias nuevas, sin descargas de 100 MB, sin procesos hijos.
- **Esfuerzo:** visor solo lectura M (descubrir pestañas, conectar, texturizar, `ack`, límites de tamaño, reconexión);
  entrada completa +M (teclas de Minecraft → `key`/`code`/`keyCode`, rueda, texto por SDL3, foco, UI scale); en el mundo (monitor)
  +M con raycast.
- **Riesgos y puntos a verificar:**
  - El host del servidor puede pedir preparación (cierre `4503`, `need: sandbox | libraries`, con el comando que lo arregla).
  - Solo hay frames si la pestaña es del servidor (`headless`/`runtime: "server"`), no si la abrió el escritorio de alguien.
  - No he leído cómo se arma el token del `DeviceHubAccess` (`withDeviceHubQuery`) para clientes de terceros; hay que ver
    si acepta el Bearer del pairing en el upgrade. **(verificar en el código del servidor)**
  - El formato y los permisos pueden cambiar (T3 Code es nightly; el VPS corre `0.1.8-nightly`). Marcar como dependencia
    frágil y tolerante: si falla, el visor muestra "no disponible", no "vacío".
  - Ancho de banda y CPU: JPEG a 30 fps o más y varias pantallas a la vez. Solo suscribirse a lo visible/cercano (patrón de
    los monitores de log en vivo, con refcount) y pedir `maxWidth` pequeño en el mundo.
  - Privacidad: el visor ve lo que ve el agente (cookies de sesión del perfil de preview, datos de la app). Es la misma
    visibilidad que ya da T3.
  - El **issue #1** (T3 Connect o CLI) cambia cómo se obtiene el acceso al servidor; el protocolo de frames es el mismo.

### 8.2 Vía B: Chromium dentro del juego (CEF), si se quiere un navegador general
- **Qué es:** una librería-mod que embebe Chromium (JCEF) y lo renderiza a una textura.
- **Estado para 26.3 (verificado en las fichas, no ejecutado):** el MCEF original de CinemaMod llega hasta **1.21.4** (Chromium 116);
  hay **dos forks que ya declaran 26.3**: **MCEF Modern** (DimasKama, Fabric, "26.3, 26.2, 26.1.x, 1.21.10–1.21.11", LGPL-2.1,
  inicialización asíncrona, API documentada) y **Rinku** (Keksuccino, Fabric/NeoForge/Forge, Chromium mucho más nuevo, 60 fps por
  defecto y 120+ configurable, LGPL-2.1, Maven en `keksuccino.github.io/maven`). Ambos son librerías: no hacen nada solas.
- **Cómo se usaría:** dependencia **opcional** (`suggests` en `fabric.mod.json`, igual que Mod Menu y cargada por separado), una
  `WebScreen` que crea el navegador, dibuja su textura y reenvía ratón, rueda y teclado. Ya existen mods de pantallas web en el
  mundo basados en MCEF (WebDisplays y sus ports a Fabric).
- **Costes:**
  - Descarga de binarios de Chromium en el primer arranque, del orden de **~136 MB** (cifra de `mcef-codec`; la de cada fork
    puede variar), guardados en `config/`. Hay antivirus que la bloquean; hace falta internet.
  - Varios procesos Chromium hijos (renderer, GPU, red) y varios cientos de MB de RAM. En el PC de Facundo es aceptable; en un
    portátil flojo no.
  - **Sin H.264/AAC** en los builds estándar: no reproduce el vídeo de la mayoría de sitios. Existe `mcef-codec` para 26.2.
  - Atado a la versión de Minecraft: el original quedó 6 versiones atrás; hoy las ports siguen a una sola persona cada una.
    Riesgo de mantenimiento y de que la primera 26.4 rompa.
  - Audio por el sistema, no por el mixer de Minecraft **(verificar)**. Cursor, IME, portapapeles y foco son trabajo propio.
  - Es un navegador general que ejecuta JS de cualquier sitio: superficie de ataque. Hay que limitar a una lista de
    dominios o a localhost/tailnet, y no guardar credenciales de T3 en su perfil.
  - **No usar para iniciar sesión de T3 Connect/OAuth:** muchos proveedores bloquean los webviews embebidos y la práctica
    recomendada es el navegador del sistema (RFC 8252). El inicio de sesión sigue con código de dispositivo o `openUri`.
- **Licencia:** LGPL-2.1 de las librerías. Depender de ellas por Maven y no copiar ni modificar su código es compatible con
  el MIT del mod; no incluirlas dentro del jar.
- **Esfuerzo:** spike S a M (abrir una URL en una pantalla); producto completo M a L (pestañas, barra de direcciones,
  descargas, permisos de dominios).

### 8.3 Vía C: renderizador propio de HTML/Markdown (sin JS)
Para docs, PRs y páginas simples con `Jsoup` + el layout de `Markdown`/`TextWrap` que el chat ya tiene. Cero dependencias nativas,
rápido y coherente con el tema, pero **no es un navegador**: nada de JS, formularios ni CSS reales. Esfuerzo M. Solo vale si el caso
de uso es leer documentación.

### 8.4 Vía D: el navegador del sistema (ya existe)
`Blaze3D.openUri` abre URLs con la confirmación vanilla (`ConversationPanel`, `MergeStationScreen`). Es lo que cubre hoy
"abrir el PR" o "abrir la doc". Mejoras baratas: abrir el preview de un hilo en el navegador, y los esquemas `t3code://` y
`vscode://` (ver I5 e I6).

### 8.5 Descartadas
- **JavaFX WebView / Swing:** chocan con GLFW en macOS (dos event loops en el hilo principal).
- **Headless Chromium propio en el PC/VPS** que transmita capturas: duplica lo que T3 ya hace en la vía A y añade un proceso pesado
  (las reglas de `AGENTS.md` restringen procesos pesados al PC).

### 8.6 Qué se vería en el juego (casos de uso, por valor)
1. **Pantalla de preview en el HQ** (la mejor idea de este anexo): el monitor del hilo o una "pantalla de revisión" muestra la
   app que el agente está construyendo (`localhost:5173` visto desde el servidor de T3). Se ve desde el escritorio del NPC,
   con el log en vivo al lado, y se puede entrar y tocar. Es algo que **solo el juego** puede hacer de forma diegética.
   Vía A.
2. **Revisión de cambios** (G9): la lista de archivos y +N/−N en la UI propia y, para el diff completo o la página del PR, una
   pestaña web. Vía A si el PR se abre en una pestaña del servidor; vía B si se quiere abrir `github.com`.
3. **Documentación y búsqueda mientras se juega:** sirve, pero es el uso que menos aporta a t3craft y el que más cuesta
   (navegador general, seguridad). Vía D o B. WebDisplays ya lo cubre para quien lo quiera.
4. **La app web de T3 dentro del juego** (estrategia alternativa a unificar la UI nativa, §7): en vez de reescribir diff, terminal,
   ajustes o plan, un panel carga el T3 web de ese entorno. Ganancia: fidelidad total y cero mantenimiento de pantallas. Pérdida:
   estética propia, accesibilidad y diegético; además exige que el T3 web acepte la sesión del pairing (cookie) **(verificar)** y
   es solo vía B. Puede convivir: UI nativa para lo cotidiano y el web para lo pesado.
5. **Dashboards** (Grafana, estado de servicios) en paredes del HQ: vía B, nicho.

### 8.7 Recomendación
Empezar por la **vía A en solo lectura** (un spike M): una `PreviewScreen` y, si funciona, el monitor en el mundo. Si el protocolo de
T3 no acepta el pairing de terceros o queda inestable, caer a **D** con el spike de **B** (MCEF Modern o Rinku) solo para localhost.
Decidir antes si se acepta el scope `preview:operate` (pregunta 3) y cómo le queda a esto el issue #1.

### 8.8 Preguntas para Facundo
1. ¿Qué quieres explorar con el navegador: **ver lo que construyen los agentes** (preview), **revisar PRs/diffs**, o **navegar por la
   web en general**? Cambia la vía.
2. ¿Aceptas una descarga opcional de ~100 a 150 MB (Chromium) para el navegador general, o prefieres cero dependencias?
3. ¿Se permite pedir el scope `preview:operate` además de `read` y `operate`, o el visor es **solo lectura**?
4. ¿La pantalla de preview debe estar **en el mundo** (monitor del hilo), en una **pantalla** o en ambas?
5. ¿Qué entornos corren los previews hoy (servidor remoto, `localhost` del VPS por Tailscale)? Condiciona si el visor del
   servidor alcanza las apps.

## Fuentes web

- GLFW, `glfwRequestWindowAttention`: https://www.glfw.org/docs/latest/group__window.html
- Fabric, HUD en 26.x: https://docs.fabricmc.net/develop/rendering/hud
- ntfy, publicar: https://docs.ntfy.sh/publish/
- Discord, ejecutar webhook: https://docs.discord.com/developers/resources/webhook
- VS Code, esquemas de URL: https://code.visualstudio.com/docs/editor/command-line
- T3 Code, agentes externos y MCP: https://github.com/pingdotgg/t3code/blob/main/docs/user/outside-agents.md
- T3 Code, enlaces profundos: https://github.com/pingdotgg/t3code/issues/26915
- Narrador de Minecraft en Linux (flite): https://github.com/flathub/com.mojang.Minecraft/issues/14
- AWT y GLFW en macOS: https://github.com/libgdx/libgdx/pull/6673
- MCEF original (hasta 1.21.4): https://github.com/CinemaMod/mcef
- MCEF Modern (26.3): https://modrinth.com/mod/mcef-modern y https://github.com/DimasKama/mcef-modern
- Rinku (26.3): https://www.curseforge.com/minecraft/mc-mods/mcef-keksuccino y https://github.com/Keksuccino/Rinku
- mcef-codec (H.264/AAC): https://github.com/menntdev/mcef-codec
- WebDisplays (Fabric): https://www.curseforge.com/minecraft/mc-mods/webdisplays-unofficial-fabric
- T3 Code, preview en servidor y streaming: `packages/client-runtime/src/preview/serverBrowserStream.ts` y
  `apps/server/src/preview/ServerBrowserStream.ts` en https://github.com/pingdotgg/t3code
