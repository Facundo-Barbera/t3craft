// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Time separators "between minutes" as in AgentCraft's console (ConsoleScreen.rows()).
package dev.t3craft.client.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Pure rules of the conversation feed (unit-tested). Like AgentCraft's console, a separator
 * ("14:05") goes before a turn whose clock minute differs from the previous timed turn's. A thread
 * spans days, so a separator on a day other than today names the day ("Tue 3 Nov · 14:05").
 */
public final class FeedRules {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH);

	private FeedRules() {
	}

	/** Separator label before a turn at {@code at} after one at {@code prev} (null: first), or null for none. */
	public static @Nullable String separator(@Nullable Long prev, @Nullable Long at, ZoneId zone, long nowMs) {
		if (at == null) {
			return null;
		}
		ZonedDateTime t = Instant.ofEpochMilli(at).atZone(zone);
		if (prev != null) {
			ZonedDateTime p = Instant.ofEpochMilli(prev).atZone(zone);
			if (p.toLocalDate().equals(t.toLocalDate()) && TIME.format(p).equals(TIME.format(t))) {
				return null;
			}
		}
		LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
		return t.toLocalDate().equals(today) ? TIME.format(t) : DAY.format(t);
	}
}
