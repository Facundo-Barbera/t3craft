package dev.t3craft.client.schedule;

import dev.t3craft.t3.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Text of the schedule board and of {@code /t3craft schedule}: plain lines with an RGB colour, no
 * Minecraft classes (the renderer turns them into components). Pure; unit-tested.
 *
 * <p>Board layout, two lines per task under a header:
 * <pre>
 * SCHEDULED TASKS                       1/2
 * ● in 12m  Nightly dependency audit
 *     every 1d · t3craft · vps · last ✔ 3h ago
 * </pre>
 * The dot and the countdown take the task's colour ({@link #color}); the detail line is grey.
 */
public final class ScheduleText {
	public record Line(String text, int color) {
	}

	/** Board contents: the lines and the panel's background (ARGB). */
	public record Board(List<Line> lines, int background) {
	}

	public static final int HEADER = 0xFFAA00;
	public static final int DETAIL = 0xAAAAAA;
	public static final int MUTED = 0x777777;
	public static final int OK = 0x55FF55;
	public static final int FAILED = 0xFF5555;
	public static final int RUNNING = 0x55FFFF;
	public static final int SCHEDULED = 0xFFFFFF;
	/** Dark, mostly opaque slate behind the text. */
	public static final int BACKGROUND = 0xD0101418;
	/** Characters of the task name kept on a board line. */
	public static final int NAME_MAX = 40;
	/** Characters of the detail line. */
	public static final int DETAIL_MAX = 56;

	private ScheduleText() {
	}

	/** Colour of a task's dot and countdown: running, failed last time, paused/blocked, or scheduled. */
	public static int color(ScheduledTask t) {
		if (t.lastRunStatus() == ScheduledTask.RunStatus.RUNNING) {
			return RUNNING;
		}
		if (!t.active()) {
			return MUTED;
		}
		if (t.lastRunStatus() == ScheduledTask.RunStatus.FAILED) {
			return FAILED;
		}
		return t.lastRunStatus() == ScheduledTask.RunStatus.SUCCEEDED ? OK : SCHEDULED;
	}

	/**
	 * The board for one page. {@code unavailable}: environment label → reason, shown when no task is
	 * known at all (so an empty board explains itself). {@code loaded}: some environment already sent
	 * its list (otherwise an empty board says it is still waiting).
	 */
	public static Board board(SchedulePager.Page<ScheduleEntry> page, int total, long nowMs, Map<String, String> unavailable, boolean loaded) {
		List<Line> lines = new ArrayList<>();
		String label = page.label();
		String head = "SCHEDULED TASKS";
		lines.add(new Line(label.isEmpty() ? head : head + "   " + label, HEADER));
		if (total == 0) {
			if (loaded) {
				lines.add(new Line("No scheduled tasks", MUTED));
			}
			if (unavailable.isEmpty() && !loaded) {
				lines.add(new Line("Waiting for T3…", MUTED));
			} else {
				unavailable.forEach((env, why) -> lines.add(new Line(clip(env + ": unavailable (" + why + ")", DETAIL_MAX), MUTED)));
			}
			return new Board(List.copyOf(lines), BACKGROUND);
		}
		for (ScheduleEntry e : page.items()) {
			ScheduledTask t = e.task();
			lines.add(new Line("● " + Countdown.next(t, nowMs) + "  " + clip(t.name(), NAME_MAX), color(t)));
			lines.add(new Line("    " + clip(detail(e, nowMs), DETAIL_MAX), DETAIL));
		}
		return new Board(List.copyOf(lines), BACKGROUND);
	}

	/** "every 1d · t3craft · vps · last ✔ 3h ago" (schedule omitted when unknown). */
	public static String detail(ScheduleEntry e, long nowMs) {
		StringBuilder b = new StringBuilder();
		String sched = e.task().schedule();
		if (!sched.isEmpty()) {
			b.append(sched).append(" · ");
		}
		if (!e.project().isEmpty()) {
			b.append(e.project()).append(" · ");
		}
		b.append(e.envLabel()).append(" · ").append(Countdown.last(e.task(), nowMs));
		return b.toString();
	}

	/** One chat line per task: "in 12m · Nightly audit · every 1d · t3craft · vps · last ✔ 3h ago". */
	public static Line chat(ScheduleEntry e, long nowMs) {
		ScheduledTask t = e.task();
		String text = Countdown.next(t, nowMs) + " · " + clip(t.name(), 60) + " · " + detail(e, nowMs);
		@Nullable String err = t.lastRunStatus() == ScheduledTask.RunStatus.FAILED ? t.lastRunError() : null;
		if (err != null && !err.isBlank()) {
			text += " (" + clip(err.strip().replace('\n', ' '), 60) + ")";
		}
		return new Line(text, color(t));
	}

	/** Cuts to {@code max} characters with an ellipsis; collapses newlines. */
	public static String clip(String s, int max) {
		String one = s.replace('\n', ' ').replace('\r', ' ').strip();
		if (one.length() <= max) {
			return one;
		}
		return one.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
	}
}
