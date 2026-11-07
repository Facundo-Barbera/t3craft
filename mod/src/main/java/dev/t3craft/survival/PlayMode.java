package dev.t3craft.survival;

import java.util.Locale;

/** Minecraft game modes without Minecraft classes, so the policy stays plain Java (and testable). */
public enum PlayMode {
	SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR;

	/** Survival and adventure: blocks cost something and the world is not free to edit. */
	public boolean pays() {
		return this == SURVIVAL || this == ADVENTURE;
	}

	/** By name ({@code "survival"}, {@code GameType#getName()}); unknown or null → SURVIVAL (the strict one). */
	public static PlayMode of(String name) {
		if (name != null) {
			try {
				return valueOf(name.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				// fall through
			}
		}
		return SURVIVAL;
	}
}
