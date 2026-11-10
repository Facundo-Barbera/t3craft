package dev.t3craft.client.chat.compose;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadControl;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
 *
 * <p><b>Images</b> (feat/t3-ui-clean) take two calls, as T3's web composer
 * ({@code packages/client-runtime/src/operations/commands.ts persistAttachments}): first
 * {@code assets.persistChatAttachments {threadId, messageId, attachments: UploadChatImageAttachment[]}}
 * (operate scope; the bytes as data URLs), whose answer is the stored {@code ChatAttachment}s; then
 * the {@code message.dispatch} above with those in {@code attachments}. Both go over the direct
 * operate connection; the legacy bridge is read-only and cannot carry them (see
 * {@code protocol/protocol.md}, {@code chat.send}), so without a direct connection the composer
 * says so instead of sending the text alone.
 *
 * <p><b>Steer / queue</b> (feat/t3-composer), as T3's web composer while a turn runs
 * ({@code packages/client-runtime/src/state/composerDispatch.ts resolveComposerDispatchMode} and
 * {@code operations/commands.ts startThreadTurn}): the default is <i>steer</i>
 * ({@code deliveryIntent:"steer"}, {@code dispatchMode:start_immediately}); Ctrl+Enter / Ctrl+click
 * <i>queues</i> it instead ({@code dispatchMode:queue_after_active}, no {@code deliveryIntent}).
 * Idle threads get {@code deliveryIntent:"auto"}. Whether the provider can take the attachments
 * of a steer ({@code OrchestrationV2TurnCapabilities.activeSteeringAcceptsAttachments}) is the
 * server's call: a steer with images on a text-only provider restarts the turn there, so the
 * client sends the same command either way. The server echoes {@code inputIntent:"steer"} on the
 * {@code user_message}, which the feed marks "↪ Steer".
 */
public final class ChatSender {
	public static final String CREATED_BY = "user";
	public static final String CREATION_SOURCE = "web";
	public static final String DELIVERY_INTENT = "auto";

	/** Runs an action and calls {@code done} once with its result (client thread in production). */
	public interface Dispatch {
		void run(OperateAction action, Consumer<OperateResult> done);
	}

	/** How a message reaches the thread ({@code ComposerDispatchMode} minus {@code restart}). */
	public enum Delivery {
		/** Idle thread: start a turn (the server steers when a turn turned out to be running). */
		AUTO,
		/** A turn runs: steer it with this message. */
		STEER,
		/** A turn runs: queue this message to start after it. */
		QUEUE
	}

	/** Fresh ids for one message. */
	public record Ids(String commandId, String messageId) {
	}

	/** Null only for {@link #preview()}: nothing is ever sent. */
	private final @Nullable OperateGate gate;
	private final Predicate<String> allowed;
	private final Dispatch dispatch;
	private final Supplier<Ids> ids;

	public ChatSender(OperateGate gate, Dispatch dispatch, Supplier<Ids> ids) {
		this.gate = gate;
		this.allowed = gate::canOperate;
		this.dispatch = dispatch;
		this.ids = ids;
	}

	private ChatSender() {
		this.gate = null;
		this.allowed = env -> true;
		this.dispatch = (action, done) -> done.accept(previewRefusal());
		this.ids = () -> new Ids("cmd-" + UUID.randomUUID(), "msg-" + UUID.randomUUID());
	}

	/**
	 * A sender for the UI gallery: every environment may operate (the composer is editable, images attach) and
	 * every send, upload or interrupt is refused with one line, without touching the network.
	 */
	public static ChatSender preview() {
		return new ChatSender();
	}

	private static OperateResult previewRefusal() {
		return OperateResult.of(OperateResult.Outcome.REJECTED, "Gallery: nothing is sent.");
	}

	public ChatSender(OperateGate gate, Dispatch dispatch) {
		this(gate, dispatch, () -> new Ids("cmd-" + UUID.randomUUID(), "msg-" + UUID.randomUUID()));
	}

	public boolean canOperate(String envId) {
		return allowed.test(envId);
	}

	/** The {@code message.dispatch} command (the payload of {@code orchestration.dispatchCommand}). */
	public static JsonObject messageDispatch(String threadId, String text, Ids ids) {
		return messageDispatch(threadId, text, ids, new JsonArray());
	}

	/** {@code message.dispatch} with stored {@code ChatAttachment}s (from {@code assets.persistChatAttachments}). */
	public static JsonObject messageDispatch(String threadId, String text, Ids ids, JsonArray attachments) {
		return messageDispatch(threadId, text, ids, attachments, Delivery.AUTO);
	}

	/** {@code message.dispatch} delivered as {@code delivery} (steer / queue while a turn runs). */
	public static JsonObject messageDispatch(String threadId, String text, Ids ids, JsonArray attachments, Delivery delivery) {
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
		cmd.add("attachments", attachments.deepCopy());
		JsonObject mode = new JsonObject();
		switch (delivery) {
			case QUEUE -> mode.addProperty("type", "queue_after_active");
			case STEER -> {
				cmd.addProperty("deliveryIntent", "steer");
				mode.addProperty("type", "start_immediately");
			}
			default -> {
				cmd.addProperty("deliveryIntent", DELIVERY_INTENT);
				mode.addProperty("type", "start_immediately");
			}
		}
		cmd.add("dispatchMode", mode);
		return cmd;
	}

	/** Fresh ids for the next message (taken before sending, so the optimistic row exists first). */
	public Ids newIds() {
		return ids.get();
	}

	/** Sends {@code text} to the thread with {@code ids} ({@link #newIds()}). */
	public void send(String envId, String threadId, @Nullable String title, String text, Ids ids, Consumer<OperateResult> done) {
		send(envId, threadId, title, text, List.of(), ids, Delivery.AUTO, done);
	}

	/**
	 * Sends {@code text} with {@code images}: uploads them ({@code assets.persistChatAttachments}),
	 * then dispatches the message with the stored attachments. {@code done} runs once: with the
	 * upload's failure (nothing was dispatched) or with the dispatch's result.
	 */
	public void send(String envId, String threadId, @Nullable String title, String text, List<ImageAttachment> images, Ids ids,
			Consumer<OperateResult> done) {
		send(envId, threadId, title, text, images, ids, Delivery.AUTO, done);
	}

	/** {@link #send(String, String, String, String, List, Ids, Consumer)} delivered as {@code delivery}. */
	public void send(String envId, String threadId, @Nullable String title, String text, List<ImageAttachment> images, Ids ids,
			Delivery delivery, Consumer<OperateResult> done) {
		OperateGate gate = this.gate;
		if (gate == null) {
			done.accept(previewRefusal());
			return;
		}
		if (images.isEmpty()) {
			OperateAction a = gate.operate(envId).dispatch(OperateKind.MESSAGE_SEND, threadId, title,
					messageDispatch(threadId, text, ids, new JsonArray(), delivery));
			dispatch.run(a, done);
			return;
		}
		OperateAction upload = new OperateAction(OperateKind.MESSAGE_SEND, envId, threadId, title, OperateAction.PERSIST_ATTACHMENTS,
				ImageAttachment.persistInput(threadId, ids.messageId(), images));
		dispatch.run(upload, r -> {
			if (!r.ok()) {
				done.accept(uploadFailure(r));
				return;
			}
			JsonArray stored = storedAttachments(r.value());
			if (stored == null || stored.size() != images.size()) {
				done.accept(OperateResult.of(OperateResult.Outcome.REJECTED, "Image upload failed: T3 did not return the stored images."));
				return;
			}
			OperateAction a = gate.operate(envId).dispatch(OperateKind.MESSAGE_SEND, threadId, title,
					messageDispatch(threadId, text, ids, stored, delivery));
			dispatch.run(a, done);
		});
	}

	/** {@code PersistChatAttachmentsResult.attachments}, or null when the answer is not that shape. */
	static @Nullable JsonArray storedAttachments(@Nullable JsonElement value) {
		if (value == null || !value.isJsonObject()) {
			return null;
		}
		JsonElement a = value.getAsJsonObject().get("attachments");
		return a != null && a.isJsonArray() ? a.getAsJsonArray() : null;
	}

	/** The upload's failure as the player should read it. */
	static OperateResult uploadFailure(OperateResult r) {
		String m = switch (r.outcome()) {
			case OFFLINE -> "Images go straight to T3 and this environment has no direct connection (the bridge cannot attach images): "
					+ "pair it directly from ⋯ › Environments.";
			case READ_ONLY -> r.message();
			case AUTH_FAILED -> "Image upload refused: enable control again (" + r.message() + ").";
			default -> "Image upload failed: " + r.message();
		};
		return OperateResult.of(r.outcome(), m);
	}

	/**
	 * Uploads {@code images} for a message of a thread that does not exist yet (a draft's first
	 * message: T3's {@code startThreadTurn} persists them under the new thread id before
	 * {@code launchThread}). {@code done} gets the stored {@code ChatAttachment}s, or the failure.
	 */
	public void upload(String envId, String threadId, String messageId, List<ImageAttachment> images,
			java.util.function.BiConsumer<@Nullable JsonArray, @Nullable OperateResult> done) {
		if (images.isEmpty()) {
			done.accept(new JsonArray(), null);
			return;
		}
		OperateAction upload = new OperateAction(OperateKind.MESSAGE_SEND, envId, threadId, null, OperateAction.PERSIST_ATTACHMENTS,
				ImageAttachment.persistInput(threadId, messageId, images));
		dispatch.run(upload, r -> {
			if (!r.ok()) {
				done.accept(null, uploadFailure(r));
				return;
			}
			JsonArray stored = storedAttachments(r.value());
			if (stored == null || stored.size() != images.size()) {
				done.accept(null, OperateResult.of(OperateResult.Outcome.REJECTED, "Image upload failed: T3 did not return the stored images."));
				return;
			}
			done.accept(stored, null);
		});
	}

	/** Runs any operate action through the same path (the draft's {@code launchThread}). */
	public void run(OperateAction action, Consumer<OperateResult> done) {
		dispatch.run(action, done);
	}

	/** Interrupts {@code runId} ({@code run.interrupt}, destructive: confirmed in game). */
	public void interrupt(String envId, String threadId, @Nullable String title, String runId, Consumer<OperateResult> done) {
		OperateGate gate = this.gate;
		if (gate == null) {
			done.accept(previewRefusal());
			return;
		}
		dispatch.run(ThreadControl.interrupt(gate.operate(envId), threadId, title, runId), done);
	}
}
