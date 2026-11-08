package dev.t3craft.client.chat;

import org.jspecify.annotations.Nullable;

/**
 * One row of the chat screen, decoded from a T3 turn item (vendored contract
 * {@code OrchestrationV2TurnItem}). {@code body} is Markdown for messages/plans and plain text
 * otherwise; {@code detail} is the folded part of tool calls (command output, diff, results).
 *
 * @param key        {@code sourceThreadId:itemId}, unique in a transcript
 * @param messageId  set for user/assistant messages ({@code message.updated} targets it)
 * @param ordinal    T3 turn item ordinal (order inside the source thread)
 * @param inherited  the row comes from a parent thread (fork/side chat history)
 */
public record ChatEntry(
		String key,
		String itemId,
		@Nullable String messageId,
		Kind kind,
		String title,
		String body,
		@Nullable String detail,
		boolean streaming,
		String status,
		@Nullable Long at,
		long ordinal,
		boolean inherited) {

	/**
	 * {@link #title} of a user message that steered the running turn (T3's {@code inputIntent}
	 * {@code steer} / {@code promoted_queued_to_steer}): the feed draws "↪ Steer" over it.
	 */
	public static final String STEER = "Steer";
	/** {@link #title} of a user message queued after the running turn ({@code queued_turn}). */
	public static final String QUEUED = "Queued";

	public enum Kind {
		USER("You", 0xFF7FD4FF, false),
		ASSISTANT("Agent", 0xFFB6F09C, false),
		REASONING("Thinking", 0xFF9A9A9A, true),
		PLAN("Plan", 0xFFE0C3FF, false),
		TODO("To-do", 0xFFE0C3FF, false),
		TOOL("Tool", 0xFFFFD27F, true),
		SUBAGENT("Subagent", 0xFFFFB37F, true),
		REQUEST("Needs you", 0xFFFFAA00, false),
		SYSTEM("System", 0xFF8A8A8A, false),
		ERROR("Error", 0xFFFF5555, false);

		/** Role label shown before the row. */
		public final String role;
		public final int color;
		/** Rows of this kind start folded and toggle on click. */
		public final boolean foldable;

		Kind(String role, int color, boolean foldable) {
			this.role = role;
			this.color = color;
			this.foldable = foldable;
		}
	}

	/** Markdown-rendered (messages, plans, requests) vs. plain text. */
	public boolean markdown() {
		return kind == Kind.USER || kind == Kind.ASSISTANT || kind == Kind.PLAN || kind == Kind.TODO || kind == Kind.REQUEST
				|| kind == Kind.REASONING;
	}

	ChatEntry withText(String text, boolean streaming) {
		return new ChatEntry(key, itemId, messageId, kind, title, text, detail, streaming, status, at, ordinal, inherited);
	}
}
