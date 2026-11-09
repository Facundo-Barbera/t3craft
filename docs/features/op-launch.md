# feat/op-launch: crear o lanzar un hilo (ítem 23, ⚠️ escritura)

Usa la infraestructura de `docs/features/operate.md`. Una sola llamada, `orchestration.launchThread`
(`OperateKind.THREAD_LAUNCH`, **no destructiva**): crea el hilo y manda el primer mensaje a la vez.
`thread.create` (vía `dispatchCommand`) no se usa: obligaría a un segundo envío para el prompt.

## Para el jugador

- **Pantalla `NewThreadScreen`**: botón "New thread…" del terminal portátil. Se elige el entorno
  (solo los que tienen control/operate), el proyecto (lista del shell, en solo lectura) y el modelo
  (opcional; si no, el del proyecto), y se escribe el prompt multilínea. "Review…" muestra un
  resumen (entorno, proyecto, modelo, título, tamaño del prompt y sus primeras líneas) y desde ahí
  "Launch". Los dos botones pasan por `OperateUi.guard`. No hay pantalla de confirmación porque no
  es destructiva.
- **Comando**: `/t3craft operate new <env> <proyecto> <prompt…>` (`Origin.COMMAND`). El proyecto es
  su id o su nombre exacto, entre comillas si tiene espacios. Usa el modelo por defecto.
- **Resultado**: el `OperateResult` sale en el chat (y en la pantalla). Si T3 devuelve el
  `threadId`, se añade un enlace `[open chat]` que lo abre en el chat (el mismo helper que usa el
  buscador, `/t3craft search-open`). El NPC aparece por el flujo normal del shell: no se spawnea
  a mano.
- **API para bloques** (feat/blocks-core): `NewThreadScreen.open(env, projectId)` abre la pantalla
  con el proyecto preseleccionado. Cualquiera de los dos puede ser null.

## Payload (contrato `t3code-ref` @ 15fe2832, `OrchestrationV2ThreadLaunchInput`)

```json
{ "commandId": "cmd-<uuid>", "threadId": "<uuid>", "projectId": "…",
  "title": "<primera línea, ≤60>", "generateTitle": true,
  "modelSelection": { "instanceId": "…", "model": "…", "options": {…}? },
  "runtimeMode": "approval-required", "interactionMode": "default",
  "workspaceStrategy": { "type": "root" },
  "initialMessage": { "messageId": "msg-<uuid>", "text": "<prompt>", "attachments": [] } }
```

- `runtimeMode` es `approval-required`, el más seguro (el default de T3 es `full-access`): el agente
  pide aprobación y su NPC lo avisa.
- `workspaceStrategy: root` trabaja en el checkout del proyecto, sin crear worktree.
- No se manda `creationSource`: es opcional y ninguno de sus valores describe a un cliente Minecraft.
- **Modelos**: salen de lo que el shell de lectura ya trae. Primero el `defaultModelSelection` del
  proyecto (con sus `options`), luego los modelos distintos de los hilos del proyecto y después los
  del resto del entorno. Si no hay ninguno, no se lanza y se pide configurar un modelo en T3.
- El resultado (`{threadId, projection, resumed, sequence?}`) decide el `threadId` del enlace.

## Seguridad y audit

- Sin token operate: `READ_ONLY`, no sale nada y queda en el audit log.
- El prompt solo viaja en el payload, que nunca se loguea. El audit lleva `action: thread_launch`,
  `destructive: false`, el resultado y el origen, sin texto ni título. En la UI y el comando se
  resume como "N characters, M lines" (`ThreadLaunch.promptSummary`).
- Límite de prompt: 16 000 caracteres (`ThreadLaunch.MAX_PROMPT`).

## Código

| Pieza | Dónde |
|---|---|
| `ThreadLaunch` (payload, validación, proyectos, modelos, entornos con operate) | `dev.t3craft.t3` (puro, testeado en `ThreadLaunchTest`) |
| `ShellState.View.projectDefaultModels` (cuarto componente; el constructor de 3 argumentos sigue) | `dev.t3craft.t3` |
| `LaunchClient` (glue cliente), `LaunchCommand` (`operate new`) | `dev.t3craft.client.operate` |
| `NewThreadScreen` | `dev.t3craft.client.screen` |
