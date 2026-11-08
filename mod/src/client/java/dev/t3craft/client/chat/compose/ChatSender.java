package dev.t3craft.client.chat.compose;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadControl;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Sends the composer's turns through {@link OperateGate} (operate pairing). Two writes, both
 * {@code orchestration.dispatchCommand} with an {@code OrchestrationV2Command}
 * ({@code t3code-ref packages/contracts/src/orchestrationV2.ts}):
 * <ul>
 *   <li><b>send</b>: {@code message.dispatch} exactly as T3 Code's web composer sends a plain
 *   message to an existing thread ({@code packages/client-runtime/src/operations/commands.ts}
 *   {@code startThreadTurn}, mode {@code "auto"}):
 *   {@code {type, commandId, createdBy:"user", creationSource:"web", threadId, messageId, text,
 *   attachments:[], deliveryIntent:"auto", dispatchMode:{type:"start_immediately"}}}. With
 *   {@code deliveryIntent:"auto"} the server steers the running turn if there is one, or starts a
 *   new turn ({@code Orchestrator.resolveMessageDispatchIntent}). {@code createdBy} and
 *   {@code creationSource} are required by the contract; {@code "web"} is what T3's own UI sends
 *   and none of its values names a Minecraft client. Kind {@link OperateKind#MESSAGE_SEND}, not
 *   destructive.</li>
 *   <li><b>stop</b>: {@code run.interrupt} with the active run, reusing op-stop's
 *   {@link ThreadControl#interrupt}. Destructive: the in-game confirmation still applies.</li>
 * </ul>
 * The {@code messageId} is chosen here so the optimistic message can be matched with the
 * {@code user_message} turn item the server echoes ({@link ComposerState}).
 */
public final class ChatSender {
	public static final String CREATED_BY = "user";
	public static final String CREATION_SOURCE = "web";
	public static final String DELIVERY_INTENT = "auto";

	/** Runs an action and calls {@code done} once with its result (client thread in production). */
	public interface Dispatch {
		void run(OperateAction action, Consumer<OperateResult> done);
	}

	/** Fresh ids for one message. */
	public record Ids(String commandId, String messageId) {
	}

	private final OperateGate gate;
	private final Dispatch dispatch;
	private final Supplier<Ids> ids;

	public ChatSender(OperateGate gate, Dispatch dispatch, Supplier<Ids> ids) {
		this.gate = gate;
		this.dispatch = dispatch;
		this.ids = ids;
	}

	public ChatSender(OperateGate gate, Dispatch dispatch) {
		this(gate, dispatch, () -> new Ids("cmd-" + UUID.randomUUID(), "msg-" + UUID.randomUUID()));
	}

	public boolean canOperate(String envId) {
		return gate.canOperate(envId);
	}

	/** The {@code message.dispatch} command (the payload of {@code orchestration.dispatchCommand}). */
	public static JsonObject messageDispatch(String threadId, String text, Ids ids) {
		if (threadId == null || threadId.isBlank()) {
			throw new IllegalArgumentException("threadId is required");
		}
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", "message.dispatch");
		cmd.addProperty("commandId", ids.commandId());
		cmd.addProperty("createdBy", CREATED_BY);
		cmd.addProperty("creationSource", CREATION_SOURCE);
		cmd.addProperty("threadId", threadId);
		cmd.addProperty("messageId", ids.messageId());
		cmd.addProperty("text", text);
		cmd.add("attachments", new JsonArray());
		cmd.addProperty("deliveryIntent", DELIVERY_INTENT);
		JsonObject mode = new JsonObject();
		mode.addProperty("type", "start_immediately");
		cmd.add("dispatchMode", mode);
		return cmd;
	}

	/** Fresh ids for the next message (taken before sending, so the optimistic row exists first). */
	public Ids newIds() {
		return ids.get();
	}

	/** Sends {@code text} to the thread with {@code ids} ({@link #newIds()}). */
	public void send(String envId, String threadId, @Nullable String title, String text, Ids ids, Consumer<OperateResult> done) {
		OperateAction a = gate.operate(envId).dispatch(OperateKind.MESSAGE_SEND, threadId, title, messageDispatch(threadId, text, ids));
		dispatch.run(a, done);
	}

	/** Interrupts {@code runId} ({@code run.interrupt}, destructive: confirmed in game). */
	public void interrupt(String envId, String threadId, @Nullable String title, String runId, Consumer<OperateResult> done) {
		dispatch.run(ThreadControl.interrupt(gate.operate(envId), threadId, title, runId), done);
	}
}
