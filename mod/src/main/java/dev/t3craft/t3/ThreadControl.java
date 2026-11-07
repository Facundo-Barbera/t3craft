package dev.t3craft.t3;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Item 21: interrupt the current run or stop a thread from its NPC, through {@link OperateGate}.
 * Builds the {@code orchestration.dispatchCommand} payloads ({@code OrchestrationV2Command} in
 * {@code t3code-ref packages/contracts/src/orchestrationV2.ts}):
 * <ul>
 *   <li>{@code run.interrupt}: {@code {type, commandId, threadId, runId, reason?, holdQueue?}}, with
 *   {@code runId} = the thread's {@code activeRunId}.</li>
 *   <li>{@code thread.stop}: {@code {type, commandId, threadId, reason?, stopDelegatedTasks?}}
 *   (by default T3 also stops the thread's delegated tasks).</li>
 * </ul>
 * Both kinds are destructive, so they need the in-game confirmation. {@code commandId} is a fresh
 * UUID per action; a {@link OperateResult.Outcome#FAILED} result is never retried automatically.
 */
public final class ThreadControl {
	private ThreadControl() {
	}

	/** {@code env:threadId} as in NPC ids ({@link Presentation}). */
	public record Target(String envId, String threadId) {
		public String npcId() {
			return envId + ":" + threadId;
		}
	}

	/** Parses {@code env:thread} (the NPC id), or null when it has no environment part. */
	public static @Nullable Target parse(@Nullable String npcId) {
		if (npcId == null) {
			return null;
		}
		int i = npcId.indexOf(':');
		if (i <= 0 || i == npcId.length() - 1) {
			return null;
		}
		return new Target(npcId.substring(0, i), npcId.substring(i + 1));
	}

	public static OperateAction interrupt(OperateGate.Env env, String threadId, @Nullable String title, String runId) {
		return interrupt(env, threadId, title, runId, () -> UUID.randomUUID().toString());
	}

	static OperateAction interrupt(OperateGate.Env env, String threadId, @Nullable String title, String runId, Supplier<String> ids) {
		if (runId == null || runId.isBlank()) {
			throw new IllegalArgumentException("run.interrupt needs the thread's activeRunId");
		}
		JsonObject cmd = base("run.interrupt", threadId, ids);
		cmd.addProperty("runId", runId);
		return env.dispatch(OperateKind.RUN_INTERRUPT, threadId, title, cmd);
	}

	public static OperateAction stop(OperateGate.Env env, String threadId, @Nullable String title) {
		return stop(env, threadId, title, () -> UUID.randomUUID().toString());
	}

	static OperateAction stop(OperateGate.Env env, String threadId, @Nullable String title, Supplier<String> ids) {
		return env.dispatch(OperateKind.THREAD_STOP, threadId, title, base("thread.stop", threadId, ids));
	}

	private static JsonObject base(String type, String threadId, Supplier<String> ids) {
		if (threadId == null || threadId.isBlank()) {
			throw new IllegalArgumentException("threadId is required");
		}
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", type);
		cmd.addProperty("commandId", ids.get());
		cmd.addProperty("threadId", threadId);
		return cmd;
	}

	/** Player-facing line for a result. {@code ok} picks the colour; nothing here triggers a retry. */
	public record Feedback(boolean ok, String text) {
	}

	public static Feedback feedback(OperateResult r) {
		String text = switch (r.outcome()) {
			case OK -> "sent";
			case READ_ONLY -> r.message();
			case NEEDS_CONFIRMATION -> "needs confirmation: " + r.message();
			case CANCELLED -> "cancelled, nothing sent";
			case AUTH_FAILED -> "T3 refused the operate session: enable control again (" + r.message() + ")";
			case OFFLINE -> "offline, nothing sent: " + r.message();
			case REJECTED -> "T3 rejected it: " + r.message();
			case FAILED -> "no answer from T3 (" + r.message() + "): it may or may not have applied; check before trying again";
		};
		return new Feedback(r.ok(), text);
	}
}
