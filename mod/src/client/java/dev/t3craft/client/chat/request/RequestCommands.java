package dev.t3craft.client.chat.request;

import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The writes that answer a pending request (feat/t3-user-input), exactly as T3 Code's
 * {@code packages/client-runtime/src/operations/commands.ts} sends them, each as the payload of
 * {@code orchestration.dispatchCommand} ({@code OrchestrationV2Command}, authorized with
 * {@code orchestration:operate}):
 * <ul>
 *   <li>{@code respondToThreadUserInput}: {@code runtime-request.respond {commandId, threadId,
 *   requestId, answers}} with {@code answers: {questionId: string | string[]}}
 *   ({@link RequestDraft#answersJson()}). Kind {@link OperateKind#REQUEST_APPROVE}.</li>
 *   <li>{@code respondToThreadApproval}: {@code runtime-request.respond {commandId, threadId,
 *   requestId, decision, feedback?}}; accept / acceptForSession / acceptAlways are
 *   {@link OperateKind#REQUEST_APPROVE}, decline / cancel {@link OperateKind#REQUEST_DENY}
 *   (destructive: the in-game confirmation applies, as for every deny).</li>
 *   <li>{@code dismissThreadUserInput}: {@code thread.user-input.dismiss {commandId, threadId,
 *   requestId}} for a message-mode question ({@link PendingRequest#dismissible()}).
 *   {@link OperateKind#REQUEST_DENY}.</li>
 * </ul>
 * Pure (the command ids come from a supplier).
 */
public final class RequestCommands {
	private static final Set<String> APPROVE = Set.of("accept", "acceptForSession", "acceptAlways");

	private final Supplier<String> commandIds;

	public RequestCommands(Supplier<String> commandIds) {
		this.commandIds = commandIds;
	}

	public static RequestCommands system() {
		return new RequestCommands(() -> UUID.randomUUID().toString());
	}

	/** {@code runtime-request.respond} with the answers of a question request. */
	public OperateAction answer(String envId, PendingRequest r, @Nullable String title, JsonObject answers) {
		JsonObject c = base("runtime-request.respond", r);
		c.add("answers", answers.deepCopy());
		return OperateAction.dispatch(OperateKind.REQUEST_APPROVE, envId, r.threadId(), title, c);
	}

	/** {@code runtime-request.respond} with an approval decision (and optional feedback on a decline). */
	public OperateAction decide(String envId, PendingRequest r, @Nullable String title, String decision, @Nullable String feedback) {
		JsonObject c = base("runtime-request.respond", r);
		c.addProperty("decision", decision);
		if (feedback != null && !feedback.isBlank()) {
			c.addProperty("feedback", feedback.strip());
		}
		return OperateAction.dispatch(APPROVE.contains(decision) ? OperateKind.REQUEST_APPROVE : OperateKind.REQUEST_DENY, envId, r.threadId(), title, c);
	}

	/** {@code thread.user-input.dismiss}: leaves a message-mode question unanswered. */
	public OperateAction dismiss(String envId, PendingRequest r, @Nullable String title) {
		return OperateAction.dispatch(OperateKind.REQUEST_DENY, envId, r.threadId(), title, base("thread.user-input.dismiss", r));
	}

	public static boolean approves(String decision) {
		return APPROVE.contains(decision);
	}

	private JsonObject base(String type, PendingRequest r) {
		if (r.threadId() == null || r.threadId().isBlank() || r.requestId().isBlank()) {
			throw new IllegalArgumentException("threadId and requestId are required");
		}
		JsonObject c = new JsonObject();
		c.addProperty("type", type);
		c.addProperty("commandId", commandIds.get());
		c.addProperty("threadId", r.threadId());
		c.addProperty("requestId", r.requestId());
		return c;
	}

	/** A failed answer as the player should read it (the choices stay on the card). */
	public static String failure(OperateResult r) {
		return switch (r.outcome()) {
			case READ_ONLY -> "Read-only: enable control (operate) to answer from Minecraft.";
			case AUTH_FAILED -> "T3 refused the control session: enable control again (" + r.message() + ").";
			case OFFLINE -> "Could not reach T3: nothing was sent. Try again.";
			case CANCELLED, NEEDS_CONFIRMATION -> "Not sent.";
			case FAILED -> "No answer from T3 (it may or may not have applied): " + r.message();
			default -> "T3 rejected the answer: " + r.message();
		};
	}
}
