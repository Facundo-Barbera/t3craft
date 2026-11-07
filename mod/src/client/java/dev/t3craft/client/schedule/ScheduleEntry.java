package dev.t3craft.client.schedule;

import dev.t3craft.t3.ScheduledTask;
import java.util.Comparator;
import java.util.List;

/**
 * One scheduled task as the board shows it: the task plus where it lives (environment label and
 * project title resolved at snapshot time). {@link #key()} = {@code <env>:<taskId>}.
 */
public record ScheduleEntry(String envId, String envLabel, String project, ScheduledTask task) {
	/**
	 * Board order: soonest next run first; tasks without a next run (paused, blocked, webhook, a
	 * finished one-off) after them; ties by name, environment and id so the order is stable.
	 */
	public static final Comparator<ScheduleEntry> ORDER = Comparator
			.comparing((ScheduleEntry e) -> e.task().nextRunAt(), Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(e -> e.task().name(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(ScheduleEntry::envLabel)
			.thenComparing(e -> e.task().id());

	public String key() {
		return envId + ":" + task.id();
	}

	/** A sorted copy. */
	public static List<ScheduleEntry> sorted(List<ScheduleEntry> entries) {
		return entries.stream().sorted(ORDER).toList();
	}
}
