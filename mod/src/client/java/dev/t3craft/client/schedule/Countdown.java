package dev.t3craft.client.schedule;

import dev.t3craft.t3.ScheduledTask;
import org.jspecify.annotations.Nullable;

/**
 * Relative time labels for the board and the chat listing. Seconds are only shown in the last
 * minute, so a board re-renders at most once a second near a run and once a minute otherwise.
 */
public final class Countdown {
	private Countdown() {
	}

	/** "45s", "12m", "1h 05m", "3d 4h": compact, two units at most. */
	public static String span(long ms) {
		long s = Math.max(0, ms) / 1000;
		if (s < 60) {
			return s + "s";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m";
		}
		long h = m / 60;
		if (h < 24) {
			return String.format(java.util.Locale.ROOT, "%dh %02dm", h, m % 60);
		}
		long d = h / 24;
		return h % 24 == 0 ? d + "d" : d + "d " + (h % 24) + "h";
	}

	/** Until {@code atMs}: "in 12m", or "due" once it is (or is about to be) past. */
	public static String until(long atMs, long nowMs) {
		long delta = atMs - nowMs;
		return delta < 1000 ? "due" : "in " + span(delta);
	}

	/** Since {@code atMs}: "3h ago", "just now" in the first five seconds. */
	public static String ago(long atMs, long nowMs) {
		long delta = nowMs - atMs;
		return delta < 5000 ? "just now" : span(delta) + " ago";
	}

	/**
	 * The board's "when" column for a task: running, paused, blocked, the countdown, "on webhook",
	 * or "-" when T3 gives no next run.
	 */
	public static String next(ScheduledTask t, long nowMs) {
		if (t.lastRunStatus() == ScheduledTask.RunStatus.RUNNING) {
			return "running";
		}
		if (t.blockedByPolicy() != null) {
			return "blocked";
		}
		if (!t.enabled()) {
			return "paused";
		}
		Long at = t.nextRunAt();
		if (at != null) {
			return until(at, nowMs);
		}
		return "webhook".equals(t.scheduleType()) ? "on webhook" : "-";
	}

	/** "last ✔ 3h ago", "last ✘ 5m ago", "never run". */
	public static String last(ScheduledTask t, long nowMs) {
		@Nullable Long at = t.lastRunAt();
		String mark = switch (t.lastRunStatus()) {
			case SUCCEEDED -> "✔";
			case FAILED -> "✘";
			case RUNNING -> "…";
			case UNKNOWN -> "?";
			case NEVER -> "";
		};
		if (at == null) {
			return t.lastRunStatus() == ScheduledTask.RunStatus.NEVER ? "never run" : "last " + mark;
		}
		return "last " + (mark.isEmpty() ? "" : mark + " ") + ago(at, nowMs);
	}
}
