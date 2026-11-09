# feat/18-attention-signals: señales discretas de atención

Issue #18. Plan: `docs/plans/ui-browser-and-integrations.md`, frente D. Investigación: G2 e I1 en
`docs/research/game-ui-and-integrations.md`.

Cuando un hilo pasa a necesitar al jugador, el mod ya avisa con una línea de chat, un toast y un sonido. Nada de eso
sirve con el juego en segundo plano. Esta rama suma tres señales silenciosas, sin procesos, sin red y sin permisos.

## Qué hace

| Señal | Qué ocurre | Cuándo |
|---|---|---|
| **Título de la ventana** | El título pasa a `T3Craft · 2 need you · 1 error` y vuelve al normal (`Minecraft.updateTitle()`) cuando no queda nada pendiente. | Mientras haya hilos en *needs you* o *error*, con o sin foco. Se cuenta una vez por tick. |
| **Atención del sistema** | `SDL_FlashWindow(ventana, SDL_FLASH_UNTIL_FOCUSED)`: la barra de tareas parpadea (Windows) o el icono del Dock rebota (macOS) hasta que la ventana recibe el foco. | Solo si la ventana **no tiene el foco** y el aviso es *needs you* o *error* (nunca *done*). **Una vez por hilo** por cada rato en segundo plano: al volver al juego se olvida la lista, y el sistema cancela solo la petición al enfocar. |
| **Barra de acción** | El mismo texto que el toast (`título — texto`) en la action bar. Una sola ranura: un aviso nuevo reemplaza al anterior, no se apilan. | Modo `also` (junto al toast) o `instead` (en lugar del toast). Por defecto `off`. |

No hay ningún sonido nuevo: eso es otro issue.

## Ajustes

Claves nuevas, aditivas, dentro del grupo `notifications` de `config/t3craft.json` (sin subir `schemaVersion`; un
archivo sin ellas se lee con los valores por defecto):

```json
"notifications": {
  "enabled": true,
  "attention": { "windowTitle": true, "taskbarFlash": true, "actionBar": "off" }
}
```

- `attention.windowTitle`: título con el resumen. Por defecto `true`.
- `attention.taskbarFlash`: pedir atención al sistema sin foco. Por defecto `true`.
- `attention.actionBar`: `off` | `also` | `instead`. Por defecto `off`. Un valor inválido vuelve a `off` y deja un aviso en el
  log (solo el nombre de la clave).
- Cada señal se apaga por separado. El interruptor general `notifications.enabled` (y la tecla de silenciar) apaga las tres.
- En la pantalla de ajustes (`Settings`) hay tres controles en la columna del medio, bajo los sonidos: *Window title*,
  *Taskbar flash* y *Action bar: off/also/instead*. Aparecen cuando la ventana es lo bastante alta; si no, se editan en el
  archivo.

## Dónde está el código

- `client/hud/AttentionSignals.java`: toda la lógica. `onTransition(...)` lo llama `Notifier.transition` (un solo gancho,
  dos líneas) y devuelve `true` si la barra de acción sustituye al toast. `register(client)` añade el tick que mantiene el
  título y ejecuta la petición al sistema en el hilo principal.
- `client/config/T3CraftSettings.java`: `Notifications.attention()` y el record `Attention`. `Notifications` conserva su
  constructor de ocho campos para no romper a quien ya lo usa.
- `client/config/ConfigCodec.java`: lectura y escritura de `notifications.attention`.
- `client/config/SettingsScreen.java`: los tres controles. `setN` conserva `attention` porque los demás interruptores
  reconstruyen `Notifications` desde sus ocho campos clásicos.

## Límites conocidos

- **El título pierde el nombre de Minecraft.** `createTitle()` es privado, así que el título de resumen es
  `T3Craft · …` en lugar de `Minecraft 26.3 - mundo`. Al quedar todo atendido vuelve el título normal de Minecraft.
- **Vanilla puede reescribir el título** (al entrar a un mundo, por ejemplo). Por eso se reaplica una vez por segundo.
- **Minecraft 26.3 usa SDL3, no GLFW**: la petición va por `org.lwjgl.sdl.SDLVideo.SDL_FlashWindow`, no por
  `glfwRequestWindowAttention`. Si esa llamada falla por falta del símbolo nativo, se desactiva sola y se registra una línea
  de depuración.
- **Según el sistema operativo:** Windows y macOS son los casos pensados. En algunos Linux y bajo Wayland SDL puede no hacer
  nada visible; es aceptable. Sin verificar todavía en ningún sistema (nadie ha jugado la rama).
- **Modo bridge:** el toast de los avisos llega en un mensaje `notify` aparte que no pasa por `Notifier`. Con `instead` la
  barra de acción se muestra, pero ese toast sigue apareciendo. En modo directo (el normal) sí lo sustituye.
- La barra de acción de vanilla dura unos segundos y la usan otros mods y comandos; un aviso nuestro puede taparlos.
