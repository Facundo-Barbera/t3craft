package dev.t3craft.client.chat.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One request a thread waits on (feat/t3-user-input), as T3 Code's
 * {@code packages/client-runtime/src/state/threadRequests.ts derivePendingThreadRequests} builds
 * it from the thread projection: the runtime request ({@code OrchestrationV2RuntimeRequest}:
 * id, kind, {@code responseCapability}) plus its turn item, which carries the content:
 * <ul>
 *   <li>{@code user_input_request}: {@code questions} ({@code OrchestrationV2UserInputQuestion}:
 *   id, header, question, options {label, description, value?, preview?}, multiSelect?,
 *   allowCustomAnswer?, initialAnswer?, required?) and {@code responseMode};</li>
 *   <li>{@code approval_request}: requestKind, prompt, reason, appName, options
 *   ({@code ProviderApprovalOption}: decision, label, warning?).</li>
 * </ul>
 * Pure.
 *
 * @param threadId         the thread that answers it
 * @param requestId        {@code RuntimeRequestId}
 * @param kind             {@link Kind#QUESTION} for {@code user_input}, else {@link Kind#APPROVAL}
 * @param requestKind      the wire kind ({@code user_input}, {@code command}, {@code file-change}…)
 * @param createdAt        epoch ms, or null
 * @param capability       {@code live}, {@code message} or {@code not_resumable}
 * @param supportsFeedback a live approval takes a decline with feedback
 * @param dismissible      a message-mode question can be dismissed without answering
 * @param questions        the questions (empty for approvals)
 * @param approval         the approval's detail (null for questions)
 */
public record PendingRequest(
		String threadId,
		String requestId,
		Kind kind,
		String requestKind,
		@Nullable Long createdAt,
		String capability,
		boolean supportsFeedback,
		boolean dismissible,
		List<Question> questions,
		@Nullable Approval approval) {

	public enum Kind {
		QUESTION,
		APPROVAL
	}

	/** {@code OrchestrationV2UserInputQuestion}. {@code multiSelect} defaults to false, as T3. */
	public record Question(String id, String header, String question, List<Option> options, boolean multiSelect, boolean allowCustomAnswer,
			@Nullable String initialAnswer) {
	}

	/** An option: {@link #wire()} is what an answer carries (its {@code value}, else its label). */
	public record Option(String label, String description, @Nullable String value, @Nullable String preview) {
		public String wire() {
			return value != null ? value : label;
		}
	}

	/** {@code approval_request} fields T3's approval banner shows. */
	public record Approval(@Nullable String detail, @Nullable String reason, @Nullable String appName, List<ApprovalOption> options) {
	}

	/** {@code ProviderApprovalOption}. */
	public record ApprovalOption(String decision, String label, @Nullable String warning) {
	}

	/** T3's {@code DEFAULT_APPROVAL_OPTIONS} ({@code ComposerPendingApprovalActions.tsx}) when the item has none. */
	public static final List<ApprovalOption> DEFAULT_APPROVAL_OPTIONS = List.of(
			new ApprovalOption("cancel", "Cancel", null),
			new ApprovalOption("decline", "Decline", null),
			new ApprovalOption("acceptForSession", "Always allow this session", null),
			new ApprovalOption("accept", "Approve", null));

	public PendingRequest {
		questions = questions == null ? List.of() : List.copyOf(questions);
	}

	/** T3's {@code canRespond}: a request the provider can no longer take is shown but not answerable. */
	public boolean canRespond() {
		return !"not_resumable".equals(capability);
	}

	public boolean isQuestion() {
		return kind == Kind.QUESTION;
	}

	/** Short header of the card: the first question's header, or "Approval · command". */
	public String title() {
		if (isQuestion()) {
			return questions.isEmpty() ? "Question" : questions.getFirst().header();
		}
		String k = switch (requestKind) {
			case "command" -> "Run command";
			case "file-read" -> "Read file";
			case "file-change" -> "Edit files";
			case "mcp-elicitation" -> approval != null && approval.appName() != null ? approval.appName() : "MCP request";
			case "permission" -> "Permission";
			default -> requestKind.replace('_', ' ').replace('-', ' ');
		};
		return "Approval · " + k;
	}

	/** The approval's choices (the item's, else T3's defaults). */
	public List<ApprovalOption> approvalOptions() {
		return approval == null || approval.options().isEmpty() ? DEFAULT_APPROVAL_OPTIONS : approval.options();
	}

	/**
	 * From a pending runtime request and its turn item (null when the stream has not shown it).
	 * Returns null for what the card does not answer: {@code auth_refresh}, {@code dynamic_tool_call},
	 * a question without its item or without questions.
	 */
	public static @Nullable PendingRequest of(String threadId, String requestId, String kind, @Nullable Long createdAt, String capability,
			boolean supportsFeedback, @Nullable JsonObject item) {
		if ("auth_refresh".equals(kind) || "dynamic_tool_call".equals(kind)) {
			return null;
		}
		if ("user_input".equals(kind)) {
			if (item == null || !"user_input_request".equals(Json.str(item, "type"))) {
				return null;
			}
			List<Question> qs = new ArrayList<>();
			for (JsonObject q : objects(Json.arr(item, "questions"))) {
				Question parsed = question(q);
				if (parsed != null) {
					qs.add(parsed);
				}
			}
			if (qs.isEmpty()) {
				return null;
			}
			boolean message = "message".equals(Json.str(item, "responseMode")) || "message".equals(capability);
			return new PendingRequest(threadId, requestId, Kind.QUESTION, kind, createdAt, capability, false, message, qs, null);
		}
		Approval a = null;
		if (item != null && "approval_request".equals(Json.str(item, "type"))) {
			List<ApprovalOption> opts = new ArrayList<>();
			for (JsonObject o : objects(Json.arr(item, "options"))) {
				String d = Json.str(o, "decision");
				String l = Json.str(o, "label");
				if (d != null && l != null && !l.isBlank()) {
					opts.add(new ApprovalOption(d, l.strip(), blankToNull(Json.str(o, "warning"))));
				}
			}
			a = new Approval(blankToNull(Json.str(item, "prompt")), blankToNull(Json.str(item, "reason")), blankToNull(Json.str(item, "appName")),
					List.copyOf(opts));
		}
		return new PendingRequest(threadId, requestId, Kind.APPROVAL, kind, createdAt, "live".equals(capability) ? "live" : "not_resumable",
				supportsFeedback && "live".equals(capability), false, List.of(), a);
	}

	static @Nullable Question question(JsonObject q) {
		String id = Json.str(q, "id");
		if (id == null || id.isBlank()) {
			return null;
		}
		List<Option> opts = new ArrayList<>();
		for (JsonObject o : objects(Json.arr(q, "options"))) {
			String label = Json.str(o, "label");
			if (label == null || label.isBlank()) {
				continue;
			}
			opts.add(new Option(label.strip(), orEmpty(Json.str(o, "description")).strip(), Json.str(o, "value"), Json.str(o, "preview")));
		}
		Boolean custom = Json.optBool(q, "allowCustomAnswer");
		return new Question(id, orEmpty(Json.str(q, "header")).strip(), orEmpty(Json.str(q, "question")).strip(), List.copyOf(opts),
				Json.bool(q, "multiSelect"), !Boolean.FALSE.equals(custom), Json.str(q, "initialAnswer"));
	}

	private static List<JsonObject> objects(@Nullable JsonArray a) {
		List<JsonObject> out = new ArrayList<>();
		if (a != null) {
			for (JsonElement e : a) {
				if (e != null && e.isJsonObject()) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	private static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}

	private static @Nullable String blankToNull(@Nullable String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
