package dev.t3craft.client.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Pure rules of the conversation feed (unit-tested): when to put a time separator between two rows
 * (AgentCraft's console separates minutes; a thread spans days, so here: at the start, on a new
 * day, and after a pause of {@link #GAP_MS}).
 */
public final class FeedRules {
	public static final long GAP_MS = 10 * 60_000L;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH);

	private FeedRules() {
	}

	/**
	 * Separator label before a row at {@code at}, after a row at {@code prev} (null for the first
	 * timed row), or null for none. Today's dates show only the time.
	 */
	public static @Nullable String separator(@Nullable Long prev, @Nullable Long at, ZoneId zone, long nowMs) {
		if (at == null) {
			return null;
		}
		LocalDate day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate();
		LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
		boolean newDay = prev == null || !Instant.ofEpochMilli(prev).atZone(zone).toLocalDate().equals(day);
		if (!newDay && at - prev < GAP_MS) {
			return null;
		}
		var t = Instant.ofEpochMilli(at).atZone(zone);
		if (newDay && !day.equals(today)) {
			return DAY.format(t);
		}
		return (newDay && prev != null ? "Today · " : "") + TIME.format(t);
	}
}
