package dev.t3craft.outbound;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Parses the {@code Retry-After} header: a number of seconds or an HTTP date. */
final class RetryAfter {
	private RetryAfter() {
	}

	/** Milliseconds to wait, or -1 when the value is missing or unreadable. */
	static long parseMs(String raw, long nowMs) {
		if (raw == null) {
			return -1;
		}
		String v = raw.strip();
		if (v.isEmpty()) {
			return -1;
		}
		try {
			double seconds = Double.parseDouble(v);
			if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
				return -1;
			}
			return (long) Math.ceil(Math.min(seconds, 86_400d) * 1000d);
		} catch (NumberFormatException notANumber) {
			// fall through to the HTTP-date form
		}
		try {
			ZonedDateTime at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
			long ms = Duration.between(java.time.Instant.ofEpochMilli(nowMs), at.toInstant()).toMillis();
			return Math.max(0, ms);
		} catch (DateTimeParseException e) {
			return -1;
		}
	}
}
