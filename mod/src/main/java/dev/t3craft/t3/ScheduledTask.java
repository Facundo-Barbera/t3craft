package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The fields of T3's {@code ScheduledTask} ({@code scheduledTask.ts}) that the schedule board reads,
 * decoded tolerantly: unknown fields are ignored, missing ones get defaults, a task without an id is
 * dropped. Times are epoch ms or null. Read-only: nothing here can run, enable or edit a task.
 *
 * @param scheduleType {@code interval | fixed_time | once | webhook}, or whatever a newer server sends
 * @param schedule     a short human summary of the schedule ("every 30m", "09:30 Mon-Fri", "webhook")
 */
public record ScheduledTask(
		String id,
		String title,
		String prompt,
		boolean enabled,
		String scheduleType,
		String schedule,
		String projectId,
		@Nullable String threadId,
		@Nullable Long nextRunAt,
		@Nullable Long lastRunAt,
		RunStatus lastRunStatus,
		@Nullable String lastRunError,
		long runCount,
		@Nullable String blockedByPolicy) {

	public enum RunStatus {
		NEVER("never"),
		RUNNING("running"),
		SUCCEEDED("succeeded"),
		FAILED("failed"),
		/** A status this client does not know yet. */
		UNKNOWN("?");

		public final String wire;

		RunStatus(String wire) {
			this.wire = wire;
		}

		public static RunStatus of(@Nullable String wire) {
			if (wire == null) {
				return NEVER;
			}
			for (RunStatus s : values()) {
				if (s != UNKNOWN && s.wire.equals(wire)) {
					return s;
				}
			}
			return UNKNOWN;
		}
	}

	private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

	/** Title, or the prompt's first line when the title is blank, or the id. */
	public String name() {
		if (!title.isBlank()) {
			return title;
		}
		String p = prompt.strip();
		int nl = p.indexOf('\n');
		String first = (nl >= 0 ? p.substring(0, nl) : p).strip();
		return first.isEmpty() ? id : first;
	}

	/** Enabled and not refused by a managed policy. */
	public boolean active() {
		return enabled && blockedByPolicy == null;
	}

	/** One task, or null when there is no id. Never throws. */
	public static @Nullable ScheduledTask fromJson(@Nullable JsonObject o) {
		if (o == null) {
			return null;
		}
		String id = Json.str(o, "id");
		if (id == null || id.isBlank()) {
			return null;
		}
		JsonObject sched = Json.obj(o, "schedule");
		String type = Json.str(sched, "type");
		String blocked = Json.str(o, "blockedByPolicy");
		return new ScheduledTask(
				id,
				nz(Json.str(o, "title")).strip(),
				nz(Json.str(o, "prompt")),
				Json.bool(o, "enabled"),
				type == null ? "" : type,
				describe(sched),
				nz(Json.str(o, "projectId")),
				Json.str(o, "threadId"),
				Json.time(o, "nextRunAt"),
				Json.time(o, "lastRunAt"),
				RunStatus.of(Json.str(o, "lastRunStatus")),
				Json.str(o, "lastRunError"),
				Math.max(0, Json.num(o, "runCount", 0)),
				blocked == null || blocked.isBlank() ? null : blocked);
	}

	/**
	 * A {@code scheduledTasks.subscribe} chunk value ({@code {tasks: [...]}}): every decodable task,
	 * in server order. Null when the value has no {@code tasks} array (not a list result).
	 */
	public static @Nullable List<ScheduledTask> listFrom(@Nullable JsonObject value) {
		JsonArray arr = Json.arr(value, "tasks");
		if (arr == null) {
			return null;
		}
		List<ScheduledTask> out = new ArrayList<>(arr.size());
		for (JsonElement e : arr) {
			ScheduledTask t = e.isJsonObject() ? fromJson(e.getAsJsonObject()) : null;
			if (t != null) {
				out.add(t);
			}
		}
		return out;
	}

	/** Short schedule summary; tolerant of missing or unknown shapes. */
	static String describe(@Nullable JsonObject s) {
		String type = Json.str(s, "type");
		if (type == null) {
			return "";
		}
		return switch (type) {
			case "interval" -> {
				long every = Json.num(s, "everyMs", 0);
				yield every > 0 ? "every " + duration(every) : "interval";
			}
			case "fixed_time" -> {
				String at = nz(Json.str(s, "timeOfDay"));
				String days = weekdays(Json.arr(s, "weekdays"));
				yield (at.isEmpty() ? "daily" : at) + (days.isEmpty() ? "" : " " + days);
			}
			case "once" -> "once";
			case "webhook" -> "webhook";
			default -> type;
		};
	}

	/** "1h 30m", "45m", "2d", "30s": whole units only. */
	static String duration(long ms) {
		long s = ms / 1000;
		if (s < 60) {
			return Math.max(1, s) + "s";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m";
		}
		long h = m / 60;
		long rm = m % 60;
		if (h < 24) {
			return rm == 0 ? h + "h" : h + "h " + rm + "m";
		}
		long d = h / 24;
		long rh = h % 24;
		return rh == 0 ? d + "d" : d + "d " + rh + "h";
	}

	private static String weekdays(@Nullable JsonArray arr) {
		if (arr == null || arr.isEmpty()) {
			return "";
		}
		boolean[] on = new boolean[7];
		for (JsonElement e : arr) {
			try {
				int d = e.getAsInt();
				if (d >= 0 && d <= 6) {
					on[d] = true;
				}
			} catch (RuntimeException ignored) {
				// skip unreadable entries
			}
		}
		int count = 0;
		for (boolean b : on) {
			count += b ? 1 : 0;
		}
		if (count == 0 || count == 7) {
			return "";
		}
		if (count == 5 && !on[0] && !on[6]) {
			return "Mon-Fri";
		}
		if (count == 2 && on[0] && on[6]) {
			return "Sat-Sun";
		}
		StringBuilder b = new StringBuilder();
		// Monday first, Sunday last
		for (int i = 1; i <= 7; i++) {
			int d = i % 7;
			if (on[d]) {
				if (!b.isEmpty()) {
					b.append(',');
				}
				b.append(DAYS[d]);
			}
		}
		return b.toString();
	}

	private static String nz(@Nullable String s) {
		return s == null ? "" : s;
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "ScheduledTask[%s %s next=%s]", id, schedule, nextRunAt);
	}
}
