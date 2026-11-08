package dev.t3craft.t3;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * One write to send to a T3 environment with the operate session: which RPC ({@code rpcTag},
 * usually {@link #DISPATCH_COMMAND}) with which payload, plus what the confirmation screen and the
 * audit log need. The payload may carry user content (a chat message): it is never logged and
 * never shown in {@link #toString()}.
 *
 * @param kind     what it does; decides {@link #destructive()}
 * @param envId    the environment ({@link Environments.Environment#id()})
 * @param threadId the thread it acts on, or null (e.g. creating a thread)
 * @param threadTitle shown in the confirmation (not logged)
 * @param rpcTag   Effect RPC tag, e.g. {@code orchestration.dispatchCommand}
 * @param payload  RPC payload (for dispatchCommand: an {@code OrchestrationV2Command})
 */
public record OperateAction(OperateKind kind, String envId, @Nullable String threadId, @Nullable String threadTitle,
		String rpcTag, JsonObject payload) {
	public static final String DISPATCH_COMMAND = "orchestration.dispatchCommand";
	public static final String LAUNCH_THREAD = "orchestration.launchThread";
	/**
	 * feat/t3-actions: {@code projects.mutate} ({@code ProjectMutation}: project.create / update /
	 * delete). Not an {@code orchestration.*} tag, but T3 authorizes it with
	 * {@code orchestration:operate} too ({@code apps/server/src/auth/RpcAuthorization.ts}), as long as
	 * the mutation carries no {@code scripts}.
	 */
	public static final String PROJECTS_MUTATE = "projects.mutate";
	/**
	 * feat/t3-ui-clean: {@code assets.persistChatAttachments} ({@code PersistChatAttachmentsInput}:
	 * {@code threadId, messageId, attachments: UploadChatImageAttachment[]}), the upload step before a
	 * {@code message.dispatch} with images. T3 authorizes it with {@code orchestration:operate}
	 * ({@code apps/server/src/auth/RpcAuthorization.ts}), as the web composer uses it
	 * ({@code packages/client-runtime/src/operations/commands.ts persistAttachments}).
	 */
	public static final String PERSIST_ATTACHMENTS = "assets.persistChatAttachments";

	public OperateAction {
		if (kind == null || envId == null || rpcTag == null || payload == null) {
			throw new IllegalArgumentException("kind, envId, rpcTag and payload are required");
		}
		if (!rpcTag.startsWith("orchestration.") && !PROJECTS_MUTATE.equals(rpcTag) && !PERSIST_ATTACHMENTS.equals(rpcTag)) {
			// orchestration:operate only covers orchestration RPCs; anything else needs other scopes.
			throw new IllegalArgumentException("not an orchestration RPC: " + rpcTag);
		}
		payload = payload.deepCopy();
	}

	/** {@code orchestration.dispatchCommand} with {@code command} as payload. */
	public static OperateAction dispatch(OperateKind kind, String envId, @Nullable String threadId, @Nullable String threadTitle, JsonObject command) {
		return new OperateAction(kind, envId, threadId, threadTitle, DISPATCH_COMMAND, command);
	}

	public boolean destructive() {
		return kind.destructive;
	}

	@Override
	public JsonObject payload() {
		return payload.deepCopy();
	}

	@Override
	public String toString() {
		return "OperateAction[" + kind.wire + " env=" + envId + " thread=" + threadId + " rpc=" + rpcTag + ", payload=<redacted>]";
	}
}
