package dev.t3craft.t3;

import java.util.Locale;

/**
 * Every kind of write T3Craft may send with {@code orchestration:operate}, and whether it is
 * <b>destructive</b> (asks for an in-game confirmation before going out). Destructive: stop or
 * interrupt, deny, settle, cancel a queued run, archive. Unknown kinds are treated as
 * destructive ({@link #isDestructive(String)}).
 *
 * <p>Adding an action: add its kind here (choose {@code destructive} carefully), then see
 * {@code docs/feat-operate.md}.
 */
public enum OperateKind {
	/** Reply in the chat ({@code message.dispatch}, queued if the thread is busy). Item 19. */
	MESSAGE_SEND(false),
	/** Approve a pending request ({@code runtime-request.respond}). Item 20. */
	REQUEST_APPROVE(false),
	/** Deny a pending request ({@code runtime-request.respond}). Item 20. */
	REQUEST_DENY(true),
	/** Interrupt the current run ({@code run.interrupt}). Item 21. */
	RUN_INTERRUPT(true),
	/** Stop the thread ({@code thread.stop} / {@code forceStopThread}). Item 21. */
	THREAD_STOP(true),
	/** Settle the thread. Item 22. */
	THREAD_SETTLE(true),
	THREAD_SNOOZE(false),
	THREAD_PIN(false),
	/** Unpin the thread ({@code thread.unpin}). Item 22. */
	THREAD_UNPIN(false),
	THREAD_MARK_READ(false),
	/** Create a thread ({@code thread.create}). Item 23. */
	THREAD_CREATE(false),
	/** Launch a thread ({@code launchThread}). Item 23. */
	THREAD_LAUNCH(false),
	/** Reorder queued runs ({@code queued-run.*}). Item 24. */
	QUEUE_REORDER(false),
	/** Cancel a queued run ({@code queued-run.*}). Item 24. */
	QUEUE_CANCEL(true),
	THREAD_ARCHIVE(true),
	/**
	 * feat/t3-actions: change a thread's model / effort, runtime mode or plan mode
	 * ({@code thread.model-selection.set}, {@code thread.runtime-mode.set},
	 * {@code thread.interaction-mode.set}).
	 */
	THREAD_CONFIGURE(false),
	/** feat/t3-actions: register a project ({@code projects.mutate} with {@code project.create}). */
	PROJECT_CREATE(false);

	public final boolean destructive;
	/** Stable lower-case id for the audit log and lang keys, e.g. {@code thread_stop}. */
	public final String wire;

	OperateKind(boolean destructive) {
		this.destructive = destructive;
		this.wire = name().toLowerCase(Locale.ROOT);
	}

	/** Lang key of the player-facing name: {@code t3craft.operate.kind.<wire>}. */
	public String langKey() {
		return "t3craft.operate.kind." + wire;
	}

	/** Fail-safe classification by wire name: anything unknown counts as destructive. */
	public static boolean isDestructive(String wire) {
		for (OperateKind k : values()) {
			if (k.wire.equals(wire)) {
				return k.destructive;
			}
		}
		return true;
	}
}
