package dev.t3craft.client.workstation;

/**
 * Compact age labels for the screen ("now", "45s", "12m", "3h", "2d"). Coarse on purpose: the label
 * changes at most once a second during the first minute and once a minute after that, which is what
 * bounds how often a screen is re-rendered just because time passed.
 */
public final class AgeFormat {
	private AgeFormat() {
	}

	public static String format(long ageMs) {
		long s = Math.max(0, ageMs) / 1000;
		if (s < 5) {
			return "now";
		}
		if (s < 60) {
			return s + "s";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m";
		}
		long h = m / 60;
		if (h < 48) {
			return h + "h";
		}
		return (h / 24) + "d";
	}
}
