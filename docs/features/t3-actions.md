# feat/t3-actions: acciones y configuración como en T3 Code (⚠️ escritura)

> feat/t3-composer-fidelity: `NewThreadDialog` was removed; "New thread" is now a draft thread in the T3 screen (docs/features/t3-composer.md). The model picker is the composer's model chip (`ModelPicker.composerChip()`).

Paquete `dev.t3craft.client.t3ui.actions` (source set `client`). Contiene servicios puros y widgets
autocontenidos que montan **feat/t3-ui-unified** (sidebar y pantalla única) y **feat/chat-compose**
(barra de controles del composer). Toda escritura pasa por `OperateGate` (con `OperateUi.run`, que
pide la confirmación en el juego si la acción es destructiva). Sin operate, cada widget se dibuja
deshabilitado con "Habilitar control" (`t3craft.actions.enable_control`) y al hacer clic abre la
pantalla para habilitar o emparejar (`OperateUi.enableControlScreen`).

Contrato: `~/projects/t3code-ref` @ 15fe2832.

## Métodos de T3 usados

| Acción | RPC / comando | Scope | Ruta en `t3code-ref` |
|---|---|---|---|
| Listar providers y modelos | `server.getConfig` → `ServerConfig.providers` (`ServerProvider`, `ServerProviderModel`, `ModelCapabilities.optionDescriptors`) | `orchestration:read` | `packages/contracts/src/server.ts`, `model.ts`, `rpc.ts` (`WS_METHODS.serverGetConfig`) |
| Cambiar modelo / effort | `orchestration.dispatchCommand` `thread.model-selection.set {commandId, threadId, modelSelection}` | operate | `packages/contracts/src/orchestrationV2.ts`; cliente: `packages/client-runtime/src/operations/commands.ts` (`updateThreadMetadata`) |
| Cambiar acceso (runtime) | `thread.runtime-mode.set {commandId, threadId, runtimeMode}` | operate | ídem (`setThreadRuntimeMode`); `providerPolicy.ts` (`RuntimeMode`) |
| Plan mode | `thread.interaction-mode.set {commandId, threadId, interactionMode}` | operate | ídem (`setThreadInteractionMode`) |
| Nuevo hilo | `orchestration.launchThread` (`OrchestrationV2ThreadLaunchInput`, `workspaceStrategy` root/worktree) | operate | `orchestrationV2.ts` (`OrchestrationV2ThreadLaunchWorkspaceStrategy`) |
| Registrar proyecto | `projects.mutate` `project.create {commandId, projectId, title, workspaceRoot, createWorkspaceRootIfMissing, defaultModelSelection: null}` → `Project` | operate (sin `scripts`) | `packages/contracts/src/project.ts` (`ProjectMutation`), `apps/server/src/auth/RpcAuthorization.ts`, `packages/client-runtime/src/operations/projects.ts` (`buildProjectCreateCommand`) |
| Archivar | `thread.archive {commandId, threadId}` (destructiva) | operate | `orchestrationV2.ts` |
| Interrumpir / detener / settle | existentes: `ThreadControl` (`run.interrupt`, `thread.stop`) y `ThreadOps.settle` | operate | `docs/features/operate.md` |

Componentes web que se copian (estructura y comportamiento, no código): `apps/web/src/components/chat/`
`ComposerModelEffort`, `ProviderModelPicker`, `ModelPickerContent`, `TraitsPicker` (el effort es el
primer descriptor `select`), `ComposerControlsBar`, `runtimeModeConfig.ts` (etiquetas Supervised /
Auto-accept edits / Auto / Full access); `BranchToolbarEnvModeSelector` y `WorktreeBaseBranchPicker`
(Local / New worktree); `Sidebar.logic.ts` `resolveThreadStatusPill` (pill de estado);
`AddProjectToMachineDialog` y el "Add project" de la paleta de comandos.

Cambios fuera del paquete (mínimos y aditivos):
- `OperateAction.PROJECTS_MUTATE`: `projects.mutate` es la única RPC no `orchestration.*` admitida
  (T3 la autoriza con `orchestration:operate`).
- `OperateKind.THREAD_CONFIGURE` y `PROJECT_CREATE`, ninguna destructiva; claves de idioma en
  en_us, es_es y es_mx.
- `T3Connection.getServerConfig()` (lectura, por el socket read-only y `StreamRegistry.call`).
- `NewThreadScreen.open(env, project)` ahora abre `NewThreadDialog`. El botón "New thread…" del
  portable y los bloques pasan a usar el diálogo nuevo sin tocar `PortableScreen`.

## API pública

### `ModelPicker` (widget)
```java
ModelPicker p = ModelPicker.forThread(font, env, threadId, title, current, pick -> …); // hilo existente
ModelPicker p = ModelPicker.forNewThread(font, env, pick -> …);                       // valores por defecto de hilos nuevos
p.setBounds(x, y, w, h);
p.render(g, mouseX, mouseY);          // al final: el popup se dibuja encima
p.mouseClicked(x, y, button);         // primero: con el popup abierto consume los clics
p.keyPressed(keyEvent);               // Esc cierra
p.mouseScrolled(x, y, dy);            // scroll de la lista de modelos
p.setEnv(env);                        // solo en forNewThread
p.pick(); p.isOpen(); p.close(); p.enabled(); p.logic();
```
- Cerrado se muestra como un chip `Provider · Modelo · Effort ▾` (más `· Plan` si aplica). Abierto,
  el popup va hacia arriba si el chip está en la mitad inferior de la pantalla: providers a la
  izquierda, modelos a la derecha y filas de Effort, Access (runtime) y Mode (Chat/Plan, solo si el
  provider expone el toggle).
- `forThread`: los cambios quedan en espera hasta "Apply", que manda solo los comandos que cambiaron
  (`ModelPickerLogic.changed()`). El listener se llama cuando T3 los acepta. `current` sale de
  `ModelPick.fromThreadJson(threadObj)` si el host tiene el objeto del hilo (con
  `modelSelection`/`runtimeMode`/`interactionMode`), o si no de `ModelPick.fromMeta(shell.meta())`.
  En ese segundo caso los modos se muestran con el valor por defecto, pero solo se envían si el
  jugador los toca.
- `forNewThread`: cada cambio se guarda como valor por defecto del entorno
  (`ProviderService.setDefaultPick`, en memoria durante la sesión) y llega al listener de inmediato.
- El catálogo se pide al abrir (`server.getConfig`) y se cachea 60 s. Mientras carga, muestra
  "Loading models…"; si T3 no expone modelos, muestra "No models".

### `ThreadStatusChip` (widget) y `ThreadStatus` (lógica)
```java
ThreadStatus s = ThreadStatus.of(threadShell);   // PENDING_APPROVAL, AWAITING_INPUT, GUEST_REQUEST, FAILED,
                                                 // WORKING, CONNECTING, WAITING, PLAN_READY, COMPLETED, IDLE
ThreadStatusChip.draw(g, font, s, x, y, maxW, nowMs);   // en filas de lista; devuelve el ancho
ThreadStatusChip.width(font, s); ThreadStatusChip.height(font);
ThreadStatusChip c = new ThreadStatusChip(font, showIdle); c.setBounds(…); c.setThread(shell); c.render(g);
```
Tiene las mismas etiquetas, prioridades y colores que el pill de T3. El punto late en Working y
Connecting. Completed y Failed se muestran hasta que el hilo vuelve a correr, porque el shell no
trae el watermark de lectura.

### `NewThreadDialog` (Screen)
```java
NewThreadDialog.open(env, projectId);                                  // el hilo creado queda seleccionado en el chat
new NewThreadDialog(parent, env, projectId, (env, threadId) -> …);     // callback propio
```
Tiene estos campos: entorno, proyecto, `ModelPicker` (modo de hilo nuevo), Workspace
(Local / New worktree), "From origin", rama base (solo en worktree), rama (opcional) y el primer
mensaje. La validación se ve en vivo bajo el formulario (`NewThreadForm.errors`). "Create" manda
un `launchThread`. Al terminar vuelve al padre y llama al callback con el `threadId` que devuelve
T3 (por defecto `ActionsClient.selectThread`, es decir, `SearchClient.open`).

### `RegisterProjectDialog` (Screen)
```java
RegisterProjectDialog.open(env, (env, projectId) -> …);
```
Tiene estos campos: entorno, ruta absoluta del workspace, nombre (si se deja vacío, toma el nombre
de la carpeta, como `inferProjectTitleFromPath`) y "Create the folder if missing". Este último está
**apagado por defecto**: T3 web manda `true`, pero así un error al escribir la ruta no crea una
carpeta en el host. El callback recibe el `Project.id` que devuelve T3.

### `ProjectsService` (servicio, para el filtro por proyecto del sidebar)
```java
ProjectsService ps = ActionsClient.projects();
ps.all();                 // todos los proyectos de todos los entornos (env label, label)
ps.byEnv(env);            // los de un entorno
ps.find(key);             // Project(envId, envLabel, projectId, label, threads, active, defaultModel)
ProjectsService.matches(filterKeyOrNull, env, threadShell);   // predicado del filtro
ProjectsService.Key.parse("env:projectId"); key.wire();
```
Lee en solo lectura las vistas del shell (`DirectController.shellViews()`). `threads` y `active`
cuentan solo los hilos de primer nivel.

### Lógica pura reutilizable
- `ProviderCatalog.fromServerConfig(json)`: providers e instancias (`selectable()` = enabled,
  installed, no unavailable y con modelos), modelos (`defaultModel()` = `isDefault` o el primero) y
  effort (`Effort.defaultValue()` = `currentValue` o la opción por defecto).
- `ModelPickerLogic`: al cambiar de provider se queda con su modelo por defecto y el effort por
  defecto; al cambiar de modelo mantiene el effort si existe en el nuevo; el runtime se ajusta a
  `supportedRuntimeModes`; plan solo con `showInteractionModeToggle`; `changed()`, `commit()` y
  `reset()`.
- `ModelPick.modelSelection()`: `{instanceId, model, options?: [{id, value}]}`.
- `ThreadCommands`: `configure(...)` y `archive(...)`.
- `NewThreadForm` / `RegisterProjectForm`: validación y payload.
- `ProviderService`: caché por entorno (TTL 60 s, reintento a los 5 s si falla) y valor por defecto
  para hilos nuevos.
- `ActionGate`: estado ENABLED / READ_ONLY / EXPIRED / NO_ENV, y la clave del texto que se muestra
  cuando está deshabilitado.
- `ActionsClient`: instancias compartidas y `runAll` (encadena acciones y para en el primer fallo).

## Tests
`mod/src/test/java/dev/t3craft/client/t3ui/actions/` usa un `OperateTransport` falso y nunca habla
con T3:
- `ActionsPayloadTest`: comprueba el payload exacto de configure (los 3 comandos y solo los que
  cambian), launch (worktree y root), `project.create` por `projects.mutate` y archive (pide
  confirmación). Sin operate no se envía nada y el estado es "Enable control"; con la sesión
  expirada también. Además verifica que solo `projects.mutate` se admite fuera de
  `orchestration.*`.
- `ProviderPickerTest`: parseo de providers y modelos, lógica del picker, `fromThreadJson` con las
  dos formas de `options` y la caché de `ProviderService`.
- `DialogsStatusProjectsTest`: validación de los dos diálogos y de las refs de git, el mapeo del
  pill de estado y `ProjectsService`.
