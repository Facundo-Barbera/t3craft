package dev.t3craft.client.chat;

import dev.t3craft.t3.ThreadState;

/**
 * How urgently a thread wants the player, in list order (first = top). Pure Java.
 *
 * <ol>
 *   <li>{@link #NEEDS_YOU}: blocked on you (blocking runtime request, waiting, actionable plan,
 *   guest messages): {@link ThreadState#NEEDS_ATTENTION}.</li>
 *   <li>{@link #ERROR}: the last run failed.</li>
 *   <li>{@link #PENDING}: an approval/request is pending but does not block the run.</li>
 *   <li>{@link #WORKING}, {@link #DONE}, {@link #IDLE}.</li>
 * </ol>
 */
public enum Attention {
	NEEDS_YOU("!", "needs you"),
	ERROR("✖", "error"),
	PENDING("?", "approval pending"),
	WORKING("⚙", "working"),
	DONE("✔", "done"),
	IDLE("·", "idle");

	public final String glyph;
	public final String word;

	Attention(String glyph, String word) {
		this.glyph = glyph;
		this.word = word;
	}

	/**
	 * @param state          thread state (same rules as the NPCs)
	 * @param pendingRequest a runtime request is pending (blocking ones are already NEEDS_ATTENTION)
	 */
	public static Attention of(ThreadState state, boolean pendingRequest) {
		return switch (state) {
			case NEEDS_ATTENTION -> NEEDS_YOU;
			case ERROR -> ERROR;
			default -> pendingRequest ? PENDING : switch (state) {
				case WORKING -> WORKING;
				case DONE -> DONE;
				default -> IDLE;
			};
		};
	}

	/** Shown with a highlight in the list (counted in the header). */
	public boolean urgent() {
		return this == NEEDS_YOU || this == ERROR || this == PENDING;
	}
}
