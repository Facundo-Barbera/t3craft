# feat/op-queue: cola de runs por hilo (inventario, ítem 24)

## Qué hace

- **Ver la cola (solo lectura, sin operate)**: `QueueScreen` lista los runs en cola por hilo
  (todos los entornos; orden de entrega de T3) y el panel del NPC muestra `⏳ n en cola`.
  `/t3craft queue` abre la pantalla; `/t3craft queue <env:hilo>` lista esa cola en el chat
  (el id corto es clicable y sugiere el comando de cancelar).
- **Gestionarla (escritura, `orchestration:operate`)**, siempre por `OperateGate`:
  - ▲ / ▼ y `/t3craft operate queue up|down <env:hilo> <run>` → `queued-run.reorder`
    (`OperateKind.QUEUE_REORDER`, no destructiva).
  - ✕ y `/t3craft operate queue cancel <env:hilo> <run>` → `queued-run.cancel`
    (`OperateKind.QUEUE_CANCEL`, **destructiva**: abre `ConfirmActionScreen`; con `confirm` al
    final del comando se da por confirmada).
  - Botones con `OperateUi.guard`: sin token operate quedan deshabilitados ("Requiere control").
  - `<run>` admite un prefijo único del id.

## Contrato (t3code-ref@15fe2832, `packages/contracts/src/orchestrationV2.ts`)

- `OrchestrationV2Run`: `id`, `threadId`, `ordinal`, `status`, `queuePosition?` (1-based),
  `queueHeld?`, `userMessageId`, `requestedAt`… Se decodifica tolerante (`QueuedRun.fromJson`):
  sin `queuePosition` se ordena por `ordinal`; sin `threadId` se asume el hilo del stream.
- Orden (`QueueOrder.DELIVERY`) = `QueuedRunOrder.queuedRunsInDeliveryOrder` del servidor: primero
  las entregas automáticas (mensaje con `delegatedCompletion`), luego `queuePosition ?? ordinal`,
  luego `ordinal`.
- `queued-run.reorder {commandId, threadId, runId, beforeRunId: RunId|null}`: `null` = al final.
  Igual que `dispatchQueuedRunReorder`, solo se mueven runs "movibles" (ni `delegatedCompletion`
  ni `notification`) y nunca por delante de una entrega automática. Subir = antes del anterior;
  bajar = antes del que va dos puestos después (o `null`).
- `queued-run.cancel {commandId, threadId, runId}`. `commandId` = `t3craft-<uuid>`.

## De dónde sale la cola

El shell (`subscribeShell`) **no trae runs**; están en la proyección del hilo
(`orchestration.subscribeThread`: snapshot `projection.runs/messages` + eventos `run.created`,
`run.updated`, `message.*`). Para no abrir otra vía de peticiones en `T3Connection`, el paquete
lee de una interfaz pequeña, `QueueSource`, con la misma forma que `StreamRegistry.Listener` de
feat/chat. Hasta que se enchufe (`QueueClient.install(...)`), la fuente es
`QueueSource.UNAVAILABLE` y la pantalla/comandos dicen "Cola no disponible" (nunca "vacía").

Adaptador previsto, una vez feat/chat esté en main:

```java
QueueClient.install((env, thread, l) -> {
	T3Connection c = direct.connection(env);
	if (c == null) return null;
	StreamRegistry.Subscription s = c.subscribeThread(thread, new StreamRegistry.Listener() {
		public void onSubscribed() { l.onSubscribed(); }
		public void onItems(List<JsonObject> items) { l.onItems(items); }
		public void onEnded(String failure) { l.onEnded(failure); }
	});
	return s::close;
});
```

`QueueStore` comparte un stream por hilo (refcount). Mientras la pantalla está abierta vigila el
hilo enfocado y los hilos trabajando (máx. 16); en segundo plano, para el badge, los 4 hilos
trabajando más recientes (una cola solo crece detrás de un run activo).

## Tests (sin T3 real)

`ThreadQueueTest` (fixture `thread-queue-snapshot.json`: campos faltantes, runs inválidos, eventos),
`QueueOrderTest`, `QueueStoreTest` (fake `QueueSource`), `QueueOpsTest` (fake transport de
`OperateGate`: confirmación de cancelar, reorder, READ_ONLY sin token, audit sin contenido),
`QueueTargetsTest` (+ claves de idioma).
