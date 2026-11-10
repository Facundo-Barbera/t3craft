# feat/config: configuración y gestión

Paquete: `dev.t3craft.client.config` (`mod/src/client/java/dev/t3craft/client/config/`).
Tests: `mod/src/test/java/dev/t3craft/client/config/` (25 tests JUnit, todos en verde).

## Qué hice

### Config persistente (`config/t3craft.json`)
- `T3CraftSettings`: snapshot inmutable y validado (schema **v1**). Mantiene en el mismo sitio y con el
  mismo significado las claves que ya lee `ModConfig` (`mode`, `bridgeUrl`, `bridgeKey`,
  `visibility.recentWindowHours`, `visibility.maxPerEnvironment`, `includeSubagents`), así que
  `ModConfig.load()` y `/t3craft reload` siguen funcionando sin cambios.
- Secciones nuevas:
  ```json
  "notifications": { "enabled": true, "chat": true, "toast": true, "sound": true,
                     "sounds": { "attention": true, "done": true, "error": true },
                     "connectionToasts": true,
                     "attention": { "windowTitle": true, "taskbarFlash": true, "actionBar": "off" } },
  "environments": { "vps-env": { "label": null, "color": "#55FFFF", "visible": true,
                     "filter": { "needsYou": true, "working": true, "recent": true,
                                 "recentWindowHours": null, "maxPerEnvironment": null } } }
  ```
  `null` en `recentWindowHours`/`maxPerEnvironment` = usar el valor global. Un entorno con todo por
  defecto no se guarda.
- `ConfigMigrations`: pasos v0→v1 (archivos de `ModConfig` o escritos a mano). Hace explícito
  `mode: "bridge"` en instalaciones antiguas con `bridgeUrl` sin `mode`, mueve
  `visibility.includeSubagents` al nivel superior, añade `notifications`/`environments`. Nunca borra
  claves desconocidas.
- `ConfigCodec`: JSON ⇄ settings. Valores inválidos → default + aviso **sólo con el nombre de la
  clave, nunca el valor** (si alguien pega un secreto donde no toca, no acaba en el log). Al guardar
  fusiona sobre el JSON leído: las claves de otras features sobreviven.
- `ConfigStore`: escritura atómica. Archivo ausente → defaults. Esquema viejo → migra y guarda copia
  `t3craft.json.v<N>.bak`. Esquema más nuevo → sólo lectura, nunca se sobrescribe. JSON roto →
  defaults en memoria, el archivo se respeta y sólo se aparta a `t3craft.json.broken.bak` al guardar.
- `ConfigManager`: instancia compartida (`ConfigManager.get()`), `update(...)` guarda y notifica a
  listeners, `load()` relee. **Hot-swap**: único estático, siempre del mismo tipo; `reset()` lo
  descarta junto con los listeners (se llama al iniciar el cliente y en `CLIENT_STOPPING`).
- `bridgeKey`: nunca se muestra (sólo "set / not set"); `toString()` la redacta. Los tokens de T3
  siguen en `config/t3craft/credentials/` (main) y esta rama no los lee salvo para re-guardarlos
  intactos al renombrar.

### World text (`labels`, #34)

Additive block read by `client/npc/LabelConfig` (re-read within ~2 s); see `docs/features/world-text.md`.
A missing key or a bad value keeps its default.

| key | default | meaning |
|---|---|---|
| `labels.mode` | `"compact"` | `"detailed"` = the old layout (chips on the tag, panel always on, no caps, classic look) |
| `labels.detailDistance` | `4.0` | blocks: a detail panel wants to open this close (0 to 64) |
| `labels.titleMax` | `24` | code points of the title on the tag / in the open pill (4 to 128) |
| `labels.style` | `"hologram"` | `"hologram"`: pill plates, event bubbles, podium bubble, hub card; `"classic"`: the look before |
| `labels.nearDistance` | `12` | blocks: NPC tags / pills further away hide unless aimed at (0 = any distance) |
| `labels.maxVisible` | `6` | NPC plates shown at once (0 = no cap) |
| `labels.maxPanels` | `1` | detail panels open at once (0 = no cap) |
| `labels.declutter` | `true` | hide the lower-ranked of two overlapping NPC plates |
| `labels.hologram.npcTags` | `true` | NPC tags, pills and detail panels at all |
| `labels.hologram.workstationScreens` | `true` | the desks' floating screens at all |
| `labels.hologram.hubHologram` | `true` | the hub card (also needs `hud.hologram`) and the decision podium bubbles |
| `labels.hologram.scheduleBoard` | `true` | the schedule board's text at all |

`"hologram": false` switches every source off at once. The desk screens also hide beyond
`blocks.monitor.nearDistance` (default 8, +2 of hysteresis). Previous behaviour:
`"style": "classic", "nearDistance": 0, "maxVisible": 0, "maxPanels": 0, "declutter": false`.

### Pantallas
- `SettingsScreen`: avisos (todos / chat / toasts / toasts de conexión), sonidos (todos / needs you /
  done / error), filtro por defecto (ventana reciente en horas, máx. por entorno, subagentes), botones
  a Entornos, Key binds (pantalla vanilla) y "Reload from disk". Edita un borrador; "Save" valida,
  guarda y recarga las fuentes (`T3CraftClient.reload()`).
- `EnvironmentsScreen`: lista paginada con color, nombre, estado de conexión, id y URL;
  Hide/Show, Edit, Unpair (con `ConfirmScreen` y recordatorio de revocar en el host),
  "Habilitar control" / "Revocar" (control `orchestration:operate`, ver `docs/features/operate.md`) y
  "Pair new…", que abre la `PairScreen` de main (pegar link o código + URL; lectura + control por
  defecto, toggle "solo lectura" para pedir solo `orchestration:read`).
- `EnvironmentEditPanel` (página del kit, antes `EnvironmentEditScreen`; "Edit" la apila sobre la lista con
  `host().show`, "Back" vuelve): renombrar, color (paleta de chat de MC + color custom del archivo, como
  muestras), visible, interruptores needs-you / working / recent, ventana y máximo por entorno (vacío = global),
  reset.
- Renombrar usa la API pública de main: `Environments.save(env con nuevo label, credential(id))`
  (mismo id, URL y credencial) y recarga. Si la credencial falta o caducó, guarda el nombre como
  override en `environments.<id>.label`.
- Acceso: Mod Menu (botón Configure), `/t3craft config`, `/t3craft config environments`,
  `/t3craft config reload`, tecla **J**.

### Mod Menu (opcional)
- `ModMenuIntegration` en el entrypoint `modmenu`; `"suggests": {"modmenu": "*"}`. API como
  `clientCompileOnly` (`com.terraformersmc:modmenu:22.0.0-alpha.3`, `transitive = false`, repo
  restringido al grupo `com.terraformersmc`). No se empaqueta ni es requerido en runtime.

### Keybinds (`T3CraftKeys`)
Categoría "T3Craft" en Controles → Key Binds (se guardan en `options.txt`): abrir ajustes (J), abrir
entornos, emparejar, reconectar, recargar config, silenciar/activar avisos (resto sin asignar para no
chocar). Registro de categoría tolerante a duplicados (hot-swap u otra rama con el mismo id).
Lang: `assets/t3craft/lang/en_us.json` y `es_es.json`.

## Archivos compartidos tocados (mínimo)
- `mod/src/main/resources/fabric.mod.json`: segundo entrypoint `client`
  (`dev.t3craft.client.config.ConfigClientInit`), entrypoint `modmenu`, `suggests`.
- `mod/build.gradle`: repo de Mod Menu (exclusivo), `clientCompileOnly` modmenu, y el classpath de
  `test` incluye `sourceSets.client` (para testear lógica pura del cliente).
- `mod/gradle.properties`: `modmenu_version`.
- `mod/src/main/resources/assets/t3craft/lang/*.json` (nuevos; si otra rama añade lang, fusionar claves).

No toqué `T3CraftClient`, `Notifier`, `DirectController`, `PeekHud` ni nada fuera del paquete.

## Puntos de integración (para Taller)
Hasta que se apliquen, los ajustes de avisos y del filtro por entorno se guardan y se ven en pantalla
pero **no cambian el comportamiento**; la ventana/máximo/subagentes globales sí funcionan ya (los lee
`ModConfig` al recargar).

1. **Filtro por entorno (modo directo)**: en `DirectController.npcs`, sustituir
   `Visibility.select(v.threads(), nowMs, options)` por
   `VisibilityPolicy.select(ConfigManager.get().settings(), id, v.threads(), nowMs)`.
   Alternativa sin tocar `DirectController`: en `T3CraftClient.refreshDirect`,
   `direct.setOptions(VisibilityPolicy.loosest(settings))` y
   `views = VisibilityPolicy.filterViews(settings, views)` (no aplica una ventana por entorno más
   estrecha que la global; necesita timestamps). En modo bridge, `filterViews` sobre el snapshot.
2. **Avisos** (`Notifier.transition` / `Toasts`): con `var n = ConfigManager.get().settings().notifications();`
   chat sólo si `n.allowChat()`, toast si `n.allowToast()`, sonido si `n.allowSound(sound)`
   (`attention|done|error`), toasts de conexión (`Toasts.link`) si `n.allowConnectionToast()`.
3. **Color / nombre por entorno**: `settings.env(envId).color()` (0xRRGGBB o null) para teñir cartel
   de barrio / sublabel; `settings.env(envId).labelOr(envLabel)` para el override de nombre.
4. **`/t3craft reload`**: añadir `ConfigManager.get().load()` en `T3CraftClient.reload()` (hoy lo hace
   `/t3craft config reload` y la tecla de recarga).
5. **Reaccionar a cambios**: `ConfigManager.get().addListener(s -> ...)` (corre en el hilo que guarda,
   normalmente el de render).
6. Otras features pueden abrir los ajustes con `ConfigClientInit.settingsScreen(parent)` y añadir sus
   teclas a `T3CraftKeys.CATEGORY`.
7. Si cambia el flujo de pairing/listado de entornos, implementar `EnvironmentActions` (interfaz
   mínima) y pasarla a las pantallas.

## Pendientes
- Aplicar los puntos de integración 1–4.
- No probado dentro del juego (regla: no lanzar Minecraft); verificado con `./gradlew build` y tests.
  Revisar maquetación a escalas de GUI altas.
- La versión de Mod Menu para 26.3 aún es alpha; la API usada (`ModMenuApi`, `ConfigScreenFactory`)
  es estable desde hace años.
- Recargar al vuelo los cambios del archivo editado a mano requiere `/t3craft config reload` (no hay
  watcher de archivo, a propósito).
