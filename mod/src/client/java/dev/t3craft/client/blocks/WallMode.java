package dev.t3craft.client.blocks;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * How the thread wall splits its columns: by state (the kanban: Working / Needs you / Error /
 * Done), by project or by environment. Config {@code blocks.threadWall.columns}, or the anchor's
 * own {@code columns} data. Pure.
 */
public enum WallMode {
	STATUS, PROJECT, ENVIRONMENT;

	/** {@code status | project | environment} (also {@code env}, {@code state}); anything else → {@code fallback}. */
	public static WallMode parse(@Nullable String s, WallMode fallback) {
		if (s == null || s.isBlank()) {
			return fallback;
		}
		return switch (s.trim().toLowerCase(Locale.ROOT)) {
			case "status", "state", "kanban" -> STATUS;
			case "project", "projects" -> PROJECT;
			case "environment", "environments", "env", "envs" -> ENVIRONMENT;
			default -> fallback;
		};
	}

	public String wire() {
		return name().toLowerCase(Locale.ROOT);
	}
}
