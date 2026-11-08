package dev.t3craft.client.chat.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the player chose so far for one pending question request, and the {@code answers} it makes.
 * A port of T3 Code's {@code packages/client-runtime/src/state/pendingUserInput.ts}
 * ({@code togglePendingUserInputOptionSelection}, {@code setPendingUserInputCustomAnswer},
 * {@code resolvePendingUserInputAnswer}, {@code buildPendingUserInputAnswers}) and of
 * {@code seedUserInputDraftAnswers}: the answer to a question is its custom ("Other") text when
 * there is one, else the chosen option value (a list for multi-select); {@code answers} maps every
 * question id to it, and is null while any question is unanswered. Kept by the card per request id,
 * so a failed send loses nothing. Pure, not thread-safe (client thread).
 */
public final class RequestDraft {
	/** One question's draft: selected option values (selection order) and the custom text. */
	private static final class Answer {
		final List<String> selected = new ArrayList<>();
		String custom = "";
	}

	private final PendingRequest request;
	private final Map<String, Answer> answers = new LinkedHashMap<>();
	private int index;

	public RequestDraft(PendingRequest request) {
		this.request = request;
		for (PendingRequest.Question q : request.questions()) {
			Answer a = new Answer();
			if (q.initialAnswer() != null && q.allowCustomAnswer()) {
				a.custom = q.initialAnswer();
			}
			answers.put(q.id(), a);
		}
	}

	public PendingRequest request() {
		return request;
	}

	/** The question shown (questions go one step at a time, as T3's banner). */
	public int index() {
		return index;
	}

	public void setIndex(int i) {
		index = Math.clamp(i, 0, Math.max(0, request.questions().size() - 1));
	}

	public @Nullable PendingRequest.Question active() {
		List<PendingRequest.Question> qs = request.questions();
		return qs.isEmpty() ? null : qs.get(Math.clamp(index, 0, qs.size() - 1));
	}

	public boolean last() {
		return index >= request.questions().size() - 1;
	}

	private Answer answer(PendingRequest.Question q) {
		return answers.computeIfAbsent(q.id(), k -> new Answer());
	}

	public String custom(PendingRequest.Question q) {
		return answer(q).custom;
	}

	/** T3's {@code isPendingUserInputOptionSelected}: a non-blank custom text hides the selection. */
	public boolean selected(PendingRequest.Question q, PendingRequest.Option o) {
		Answer a = answer(q);
		if (q.allowCustomAnswer() && !a.custom.isBlank()) {
			return false;
		}
		return a.selected.contains(o.wire());
	}

	/** T3's {@code togglePendingUserInputOptionSelection}: clears the custom text. */
	public void toggle(PendingRequest.Question q, PendingRequest.Option o) {
		Answer a = answer(q);
		String v = o.wire();
		if (q.multiSelect()) {
			if (!a.selected.remove(v)) {
				a.selected.add(v);
			}
		} else {
			a.selected.clear();
			a.selected.add(v);
		}
		a.custom = "";
	}

	/** T3's {@code setPendingUserInputCustomAnswer}: typing a custom answer drops the selection. */
	public void setCustom(PendingRequest.Question q, String text) {
		if (!q.allowCustomAnswer()) {
			return;
		}
		Answer a = answer(q);
		a.custom = text == null ? "" : text;
		if (q.initialAnswer() != null || !a.custom.isBlank()) {
			a.selected.clear();
		}
	}

	/**
	 * T3's {@code resolvePendingUserInputAnswer}: a String, a List of Strings (multi-select), or null
	 * while unanswered.
	 */
	public @Nullable Object resolved(PendingRequest.Question q) {
		Answer a = answer(q);
		String custom = !q.allowCustomAnswer() ? null : q.initialAnswer() != null ? a.custom : blankToNull(a.custom);
		if (custom != null && !custom.isEmpty()) {
			return custom;
		}
		List<String> sel = selectedOffered(q, a);
		if (q.multiSelect()) {
			return !sel.isEmpty() ? List.copyOf(sel) : custom;
		}
		return !sel.isEmpty() ? sel.getFirst() : custom;
	}

	public boolean answered(PendingRequest.Question q) {
		return resolved(q) != null;
	}

	/** Every question answered: the card can send. */
	public boolean complete() {
		for (PendingRequest.Question q : request.questions()) {
			if (!answered(q)) {
				return false;
			}
		}
		return !request.questions().isEmpty();
	}

	/** First unanswered question index, or -1. */
	public int firstUnanswered() {
		List<PendingRequest.Question> qs = request.questions();
		for (int i = 0; i < qs.size(); i++) {
			if (!answered(qs.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * T3's {@code buildPendingUserInputAnswers}: {@code {questionId: string | string[]}}, or null while
	 * any question is unanswered.
	 */
	public @Nullable JsonObject answersJson() {
		JsonObject out = new JsonObject();
		for (PendingRequest.Question q : request.questions()) {
			Object r = resolved(q);
			if (r == null) {
				return null;
			}
			if (r instanceof List<?> list) {
				JsonArray arr = new JsonArray();
				list.forEach(v -> arr.add(String.valueOf(v)));
				out.add(q.id(), arr);
			} else {
				out.addProperty(q.id(), String.valueOf(r));
			}
		}
		return out;
	}

	/** The offered options among the selection, deduplicated, in selection order ({@code normalizeSelectedOptionValues}). */
	private static List<String> selectedOffered(PendingRequest.Question q, Answer a) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (String v : a.selected) {
			for (PendingRequest.Option o : q.options()) {
				if (o.wire().equals(v)) {
					out.add(v);
					break;
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static @Nullable String blankToNull(@Nullable String s) {
		if (s == null) {
			return null;
		}
		String t = s.strip();
		return t.isEmpty() ? null : t;
	}
}
