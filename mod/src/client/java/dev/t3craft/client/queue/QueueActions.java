package dev.t3craft.client.queue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Builds the queue writes as {@code orchestration.dispatchCommand} actions (contract:
 * {@code OrchestrationV2Command} in t3code-ref {@code packages/contracts/src/orchestrationV2.ts}):
 * <ul>
 *   <li>{@code queued-run.cancel {commandId, threadId, runId}} → {@link OperateKind#QUEUE_CANCEL}
 *   (destructive: confirmation)</li>
 *   <li>{@code queued-run.reorder {commandId, threadId, runId, beforeRunId|null}} →
 *   {@link OperateKind#QUEUE_REORDER} (not destructive)</li>
 * </ul>
 * Pure: they only go out through {@code OperateGate}.
 */
public final class QueueActions {
	public static final String CANCEL = "queued-run.cancel";
	public static final String REORDER = "queued-run.reorder";

	private final Supplier<String> commandIds;

	public QueueActions(Supplier<String> commandIds) {
		this.commandIds = commandIds;
	}

	/** Command ids {@code t3craft-<uuid>}. */
	public static QueueActions create() {
		return new QueueActions(() -> "t3craft-" + UUID.randomUUID());
	}

	public OperateAction cancel(QueuedRun run, @Nullable String threadTitle) {
		JsonObject cmd = base(CANCEL, run.threadId(), run.id());
		return OperateAction.dispatch(OperateKind.QUEUE_CANCEL, run.envId(), run.threadId(), threadTitle, cmd);
	}

	public OperateAction reorder(String envId, String threadId, @Nullable String threadTitle, QueueOrder.Move move) {
		JsonObject cmd = base(REORDER, threadId, move.runId());
		if (move.beforeRunId() == null) {
			cmd.add("beforeRunId", JsonNull.INSTANCE);
		} else {
			cmd.addProperty("beforeRunId", move.beforeRunId());
		}
		return OperateAction.dispatch(OperateKind.QUEUE_REORDER, envId, threadId, threadTitle, cmd);
	}

	private JsonObject base(String type, String threadId, String runId) {
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", type);
		cmd.addProperty("commandId", commandIds.get());
		cmd.addProperty("threadId", threadId);
		cmd.addProperty("runId", runId);
		return cmd;
	}
}
