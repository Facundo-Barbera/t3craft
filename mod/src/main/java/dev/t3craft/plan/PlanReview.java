package dev.t3craft.plan;

import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * When a linked task counts as "implemented", from the shell row of its thread alone (data the client already has;
 * nothing here subscribes or asks T3 for anything). Pure.
 *
 * <p>A thread has finished the task's turn when it was settled after the task's {@link PlanTask.Link#baseline}, or
 * when it is neither working, waiting for the player nor failed and its last run completed after the baseline. The
 * timestamps are T3's (server clock), so the baseline is taken from the same thread row, never from the local clock,
 * except when the row is not visible ({@link #baselineFor}).
 */
public final class PlanReview {
	private PlanReview() {
	}

	/** The latest of the thread's last run completion and its settle time (server ms), or null when neither is known. */
	public static @Nullable Long completionMark(ThreadShell t) {
		Long a = t.latestRunCompletedAt();
		Long b = t.settledAt();
		if (a == null) {
			return b;
		}
		return b == null ? a : Math.max(a, b);
	}

	/** Whether {@code t} finished a turn after {@code baseline} (null = any finished turn counts). */
	public static boolean finished(ThreadShell t, @Nullable Long baseline) {
		Long settled = t.settledAt();
		if (settled != null && (baseline == null || settled > baseline)) {
			return true;
		}
		ThreadState s = ThreadState.of(t);
		if (s != ThreadState.DONE && s != ThreadState.IDLE) {
			return false;
		}
		Long done = t.latestRunCompletedAt();
		return done != null && (baseline == null || done > baseline);
	}

	/**
	 * The baseline to store when a task is sent back to {@code t}: its current completion mark, so only the next turn
	 * counts. When the row is not visible, the local clock (a rough fallback: clocks may differ by a little).
	 */
	public static long baselineFor(@Nullable ThreadShell t, long now) {
		Long mark = t == null ? null : completionMark(t);
		return mark != null ? mark : now;
	}
}
