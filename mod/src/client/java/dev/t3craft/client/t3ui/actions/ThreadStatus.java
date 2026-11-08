package dev.t3craft.client.t3ui.actions;

import dev.t3craft.t3.ThreadShell;
import java.util.Set;

/**
 * The status pill of a thread, as T3 Code's sidebar shows it ({@code resolveThreadStatusPill} in
 * {@code apps/web/src/components/Sidebar.logic.ts}), mapped onto the shell fields T3Craft reads
 * ({@link ThreadShell}). Same labels, priorities and colours (the dark-theme Tailwind tones); same
 * order of checks. T3 only shows Completed / Failed while unseen; the shell does not carry the read
 * watermark here, so they show until the thread runs again. {@link #IDLE} is T3's "no pill". Pure.
 */
public enum ThreadStatus {
	PENDING_APPROVAL("Pending Approval", 0xFFFCD34D, false, 5),
	AWAITING_INPUT("Awaiting Input", 0xFFA5B4FC, false, 4),
	GUEST_REQUEST("Guest Request", 0xFFF0ABFC, false, 3.75),
	FAILED("Failed", 0xFFFCA5A5, false, 3.5),
	WORKING("Working", 0xFF7DD3FC, true, 3),
	CONNECTING("Connecting", 0xFF7DD3FC, true, 3),
	WAITING("Waiting", 0xFF9CA3AF, false, 2.5),
	PLAN_READY("Plan Ready", 0xFFC4B5FD, false, 2),
	COMPLETED("Completed", 0xFF6EE7B7, false, 1),
	IDLE("Idle", 0xFF6B7280, false, 0);

	public final String label;
	/** ARGB text / dot colour. */
	public final int color;
	/** T3 pulses the dot while the thread works. */
	public final boolean pulse;
	public final double priority;

	ThreadStatus(String label, int color, boolean pulse, double priority) {
		this.label = label;
		this.color = color;
		this.pulse = pulse;
		this.priority = priority;
	}

	private static final Set<String> CONNECTING_STATUSES = Set.of("preparing", "starting", "queued");

	public static ThreadStatus of(ThreadShell t) {
		ThreadShell.PendingRequest p = t.pendingRuntimeRequest();
		if (p != null && !Boolean.FALSE.equals(p.blocking())) {
			return "user_input".equals(p.kind()) ? AWAITING_INPUT : PENDING_APPROVAL;
		}
		if (t.pendingGuestMessageCount() > 0) {
			return GUEST_REQUEST;
		}
		String s = t.status();
		if ("running".equals(s) || "waiting".equals(s) || "running".equals(t.activityRunStatus())) {
			return WORKING;
		}
		String activity = t.activityRunStatus();
		if (CONNECTING_STATUSES.contains(s) || (activity != null && CONNECTING_STATUSES.contains(activity))) {
			return CONNECTING;
		}
		if (t.pendingBackgroundTasks() > 0) {
			return WAITING;
		}
		if ("failed".equals(s)) {
			return FAILED;
		}
		if (t.hasActionableProposedPlan()) {
			return PLAN_READY;
		}
		if ("completed".equals(s)) {
			return COMPLETED;
		}
		return IDLE;
	}

	/** Whether "Interrupt" makes sense (there is a run in progress). */
	public boolean running() {
		return this == WORKING || this == CONNECTING || this == WAITING || this == PENDING_APPROVAL || this == AWAITING_INPUT;
	}
}
