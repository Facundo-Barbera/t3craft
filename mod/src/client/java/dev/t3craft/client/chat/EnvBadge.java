package dev.t3craft.client.chat;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The small environment icon shown on each thread row, like the environment icon of T3 Code's
 * sidebar: one initial on a colour picked deterministically from the environment id (or the
 * colour configured for it in {@code t3craft.json}), so the same environment always looks the
 * same. Filter chips use the longer {@link #text()}. Pure Java.
 *
 * @param initial    one upper-case character, e.g. "V" for "my-vps", "M" for "Mini Acmesite"
 * @param text       1-3 upper-case characters, e.g. "VPS", "MF"
 * @param background ARGB fill of the badge
 * @param foreground ARGB text colour readable on {@code background}
 */
public record EnvBadge(String initial, String text, int background, int foreground) {
	/** Muted, distinguishable tones that sit well on the dark terminal palette. */
	static final int[] PALETTE = {
			0xFF2F7F8A, // teal
			0xFF8A5A2F, // copper
			0xFF5A6FA8, // slate blue
			0xFF7A8A2F, // olive
			0xFF8A2F5E, // plum
			0xFF2F8A55, // green
			0xFF8A7A2F, // brass
			0xFF6A4A8A, // violet
	};

	public static EnvBadge of(String envId, String label) {
		return of(envId, label, null);
	}

	/** {@code rgb}: configured 0xRRGGBB colour of the environment, or null for the stable default. */
	public static EnvBadge of(String envId, String label, @Nullable Integer rgb) {
		String text = initials(label == null || label.isBlank() ? envId : label);
		int bg = rgb != null ? 0xFF000000 | rgb : PALETTE[Math.floorMod(stableHash(envId == null ? "" : envId), PALETTE.length)];
		return new EnvBadge(text.substring(0, text.offsetByCodePoints(0, 1)), text, bg, readableOn(bg));
	}

	/** Ink on light colours, near-white on dark ones (relative luminance threshold). */
	static int readableOn(int argb) {
		double r = ((argb >> 16) & 0xFF) / 255.0;
		double g = ((argb >> 8) & 0xFF) / 255.0;
		double b = (argb & 0xFF) / 255.0;
		double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		return lum > 0.6 ? 0xFF1F1E1D : 0xFFF2F2F2;
	}

	/**
	 * "my-vps" → "VPS" (a short last word wins: it is usually the host kind), "Mini Acmesite" →
	 * "MF", "homelab" → "HOM", "pc" → "PC".
	 */
	static String initials(String label) {
		String[] words = label.trim().split("[^\\p{L}\\p{N}]+");
		java.util.List<String> parts = new java.util.ArrayList<>();
		for (String w : words) {
			if (!w.isEmpty()) {
				parts.add(w);
			}
		}
		if (parts.isEmpty()) {
			return "?";
		}
		String last = parts.getLast();
		if (parts.size() > 1 && last.length() <= 3 && !last.chars().allMatch(Character::isDigit)) {
			return last.toUpperCase(Locale.ROOT);
		}
		if (parts.size() == 1) {
			String w = parts.getFirst();
			return w.substring(0, Math.min(3, w.length())).toUpperCase(Locale.ROOT);
		}
		StringBuilder b = new StringBuilder();
		for (String p : parts) {
			if (b.length() < 3) {
				b.appendCodePoint(p.codePointAt(0));
			}
		}
		return b.toString().toUpperCase(Locale.ROOT);
	}

	/** FNV-1a: stable across runs and JVMs (unlike identity hashes), spreads short ids well. */
	static int stableHash(String s) {
		int h = 0x811C9DC5;
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= 0x01000193;
		}
		return h;
	}
}
