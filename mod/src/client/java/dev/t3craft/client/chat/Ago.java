package dev.t3craft.client.chat;

import org.jspecify.annotations.Nullable;

/** Compact relative age for list rows ("now", "5m", "3h", "2d", "6w", "1y"). Pure Java. */
public final class Ago {
	private Ago() {
	}

	/** Empty when {@code atMs} is unknown. Future times (clock skew) count as "now". */
	public static String format(long nowMs, @Nullable Long atMs) {
		if (atMs == null || atMs <= 0) {
			return "";
		}
		long s = Math.max(0, (nowMs - atMs) / 1000);
		if (s < 60) {
			return "now";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m";
		}
		long h = m / 60;
		if (h < 24) {
			return h + "h";
		}
		long d = h / 24;
		if (d < 14) {
			return d + "d";
		}
		if (d < 365) {
			return (d / 7) + "w";
		}
		return (d / 365) + "y";
	}

	/** Longer form for tooltips and headers ("5 min ago"). */
	public static String phrase(long nowMs, @Nullable Long atMs) {
		String f = format(nowMs, atMs);
		if (f.isEmpty() || f.equals("now")) {
			return f.isEmpty() ? "" : "just now";
		}
		String n = f.substring(0, f.length() - 1);
		return switch (f.charAt(f.length() - 1)) {
			case 'm' -> n + " min ago";
			case 'h' -> n + " h ago";
			case 'd' -> n + (n.equals("1") ? " day ago" : " days ago");
			case 'w' -> n + (n.equals("1") ? " week ago" : " weeks ago");
			default -> n + (n.equals("1") ? " year ago" : " years ago");
		};
	}
}
