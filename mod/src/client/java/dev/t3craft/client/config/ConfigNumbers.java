package dev.t3craft.client.config;

import org.jspecify.annotations.Nullable;

/** Number text of the config pages: optional fields (blank = the global default) and their display. */
final class ConfigNumbers {
	private ConfigNumbers() {
	}

	/** Parses an optional number field: blank -> null, invalid -> NaN. */
	static @Nullable Double optional(String raw) {
		String s = raw.trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			return Double.valueOf(s.replace(',', '.'));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	static String format(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}
}
