package dev.t3craft.t3;

import java.util.Set;

/** Thread state shown by an NPC. Same rules as bridge/src/state/mapper.ts. */
public enum ThreadState {
	NEEDS_ATTENTION("needs_attention", 0xFFAA00, "!", 0, "needs you"),
	ERROR("error", 0xFF5555, "✖", 1, "error"),
	WORKING("working", 0x55FFFF, "⚙", 2, "working"),
	DONE("done", 0x55FF55, "✔", 3, "done"),
	IDLE("idle", 0xAAAAAA, "·", 4, "idle");

	public final String wire;
	public final int color;
	public final String glyph;
	public final int priority;
	public final String word;

	ThreadState(String wire, int color, String glyph, int priority, String word) {
		this.wire = wire;
		this.color = color;
		this.glyph = glyph;
		this.priority = priority;
		this.word = word;
	}

	/** T3's LIVE_SHELL_STATUSES: {@code waiting} is a live run (tool, thread_wait, subagents, background work). */
	private static final Set<String> WORKING_STATUSES = Set.of("queued", "preparing", "starting", "running", "waiting");

	/**
	 * First match wins: needs_attention (blocking pending request of any kind — approval, user_input,
	 * dynamic_tool_call, auth_refresh — actionable plan, guest messages) → working (activity run,
	 * queued/preparing/starting/running/waiting, background tasks) → error (failed) → done (completed)
	 * → idle. {@code waiting} alone never needs the user: only the blocking request does, as in T3 Code.
	 */
	public static ThreadState of(ThreadShell t) {
		ThreadShell.PendingRequest p = t.pendingRuntimeRequest();
		if (p != null && !Boolean.FALSE.equals(p.blocking())) {
			return NEEDS_ATTENTION;
		}
		if (t.hasActionableProposedPlan() || t.pendingGuestMessageCount() > 0) {
			return NEEDS_ATTENTION;
		}
		if (t.activityRunStatus() != null || WORKING_STATUSES.contains(t.status()) || t.pendingBackgroundTasks() > 0) {
			return WORKING;
		}
		if ("failed".equals(t.status())) {
			return ERROR;
		}
		if ("completed".equals(t.status())) {
			return DONE;
		}
		return IDLE;
	}
}
