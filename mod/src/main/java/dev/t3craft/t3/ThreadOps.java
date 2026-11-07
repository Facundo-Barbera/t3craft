package dev.t3craft.t3;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Item 22: settle, snooze, pin / unpin and mark-read of one thread, as {@link OperateAction}s for
 * {@link OperateGate} ({@code orchestration.dispatchCommand} with an {@code OrchestrationV2Command},
 * contract in t3code {@code packages/contracts/src/orchestrationV2.ts}). Pure: no Minecraft, no
 * network; the clock, the time zone and the command ids are injectable for tests.
 *
 * <ul>
 *   <li>settle → {@code thread.settle} (destructive: the gate wants a confirmation)</li>
 *   <li>snooze → {@code thread.snooze {snoozedUntil}} with a {@link Snooze} preset</li>
 *   <li>pin / unpin → {@code thread.pin} / {@code thread.unpin}</li>
 *   <li>mark-read → {@code thread.visit {visitedAt: now}} (moves the read watermark, like opening it)</li>
 * </ul>
 */
public final class ThreadOps {
	/** Snooze presets offered in the UI and the command. */
	public enum Snooze {
		HOUR_1("1h"),
		HOURS_4("4h"),
		/** Tomorrow at {@link #MORNING} local time. */
		TOMORROW("tomorrow");

		public static final LocalTime MORNING = LocalTime.of(9, 0);

		/** Command argument and lang suffix ({@code t3craft.operate.snooze.<wire>}). */
		public final String wire;

		Snooze(String wire) {
			this.wire = wire;
		}

		public String langKey() {
			return "t3craft.operate.snooze." + wire;
		}

		/** When the thread wakes up again. */
		public Instant until(Instant now, ZoneId zone) {
			return switch (this) {
				case HOUR_1 -> now.plus(1, ChronoUnit.HOURS);
				case HOURS_4 -> now.plus(4, ChronoUnit.HOURS);
				case TOMORROW -> ZonedDateTime.ofInstant(now, zone).toLocalDate().plusDays(1).atTime(MORNING).atZone(zone).toInstant();
			};
		}

		public static @Nullable Snooze parse(@Nullable String s) {
			if (s == null) {
				return null;
			}
			String k = s.trim().toLowerCase(Locale.ROOT);
			for (Snooze d : values()) {
				if (d.wire.equals(k)) {
					return d;
				}
			}
			return switch (k) {
				case "1", "1hour", "hour" -> HOUR_1;
				case "4", "4hours" -> HOURS_4;
				case "manana", "mañana" -> TOMORROW;
				default -> null;
			};
		}
	}

	/** One thread to act on: environment id + T3 thread id (+ title for the confirmation). */
	public record Target(String envId, String threadId, @Nullable String title) {
		/** {@code <env>:<threadId>} (the NPC id shape), or null when there is no separator. */
		public static @Nullable Target parse(String npcId, @Nullable String title) {
			int i = npcId.indexOf(':');
			if (i <= 0 || i == npcId.length() - 1) {
				return null;
			}
			return new Target(npcId.substring(0, i), npcId.substring(i + 1), title);
		}

		public String npcId() {
			return envId + ":" + threadId;
		}
	}

	private final Supplier<Instant> clock;
	private final ZoneId zone;
	private final Supplier<String> commandIds;

	public ThreadOps(Supplier<Instant> clock, ZoneId zone, Supplier<String> commandIds) {
		this.clock = clock;
		this.zone = zone;
		this.commandIds = commandIds;
	}

	/** Real clock, system zone, random UUID command ids. */
	public static ThreadOps system() {
		return new ThreadOps(Instant::now, ZoneId.systemDefault(), () -> UUID.randomUUID().toString());
	}

	public OperateAction settle(Target t) {
		return OperateAction.dispatch(OperateKind.THREAD_SETTLE, t.envId(), t.threadId(), t.title(), command("thread.settle", t));
	}

	public OperateAction snooze(Target t, Snooze d) {
		JsonObject c = command("thread.snooze", t);
		c.addProperty("snoozedUntil", iso(d.until(clock.get(), zone)));
		return OperateAction.dispatch(OperateKind.THREAD_SNOOZE, t.envId(), t.threadId(), t.title(), c);
	}

	public OperateAction pin(Target t) {
		return OperateAction.dispatch(OperateKind.THREAD_PIN, t.envId(), t.threadId(), t.title(), command("thread.pin", t));
	}

	public OperateAction unpin(Target t) {
		return OperateAction.dispatch(OperateKind.THREAD_UNPIN, t.envId(), t.threadId(), t.title(), command("thread.unpin", t));
	}

	public OperateAction markRead(Target t) {
		JsonObject c = command("thread.visit", t);
		c.addProperty("visitedAt", iso(clock.get()));
		return OperateAction.dispatch(OperateKind.THREAD_MARK_READ, t.envId(), t.threadId(), t.title(), c);
	}

	private JsonObject command(String type, Target t) {
		JsonObject c = new JsonObject();
		c.addProperty("type", type);
		c.addProperty("commandId", commandIds.get());
		c.addProperty("threadId", t.threadId());
		return c;
	}

	/** ISO-8601 UTC with milliseconds, like JS {@code Date.toISOString()}. */
	static String iso(Instant i) {
		return i.truncatedTo(ChronoUnit.MILLIS).toString().replaceFirst("(T\\d\\d:\\d\\d:\\d\\d)Z$", "$1.000Z");
	}
}
