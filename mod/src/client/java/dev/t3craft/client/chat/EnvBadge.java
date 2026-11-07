package dev.t3craft.client.chat;

import java.util.Locale;

/**
 * The small environment marker shown on each thread row (T3 Code shows the environment only as
 * an icon per sidebar item). Short text (1-3 characters from the label) on a colour picked
 * deterministically from the environment id, so the same environment always looks the same.
 * Pure Java.
 *
 * @param text       1-3 upper-case characters, e.g. "VPS" for "my-vps", "MF" for "Mini Acmesite"
 * @param background ARGB fill of the badge
 * @param foreground ARGB text colour readable on {@code background}
 */
public record EnvBadge(String text, int background, int foreground) {
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
		String text = initials(label == null || label.isBlank() ? envId : label);
		int bg = PALETTE[Math.floorMod(stableHash(envId == null ? "" : envId), PALETTE.length)];
		return new EnvBadge(text, bg, 0xFFF2F2F2);
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
