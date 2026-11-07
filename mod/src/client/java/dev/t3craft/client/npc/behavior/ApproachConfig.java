package dev.t3craft.client.npc.behavior;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * "Comes to the player" in ATTENTION: its own additive block in {@code config/t3craft.json}
 * (missing block or key → default; wrong type or out of range → default):
 *
 * <pre>{@code
 * "npcApproach": {
 *   "enabled": true,
 *   "maxDistance": 16,      // blocks; a player farther than this is not approached
 *   "maxConcurrent": 2,     // NPCs approaching at once
 *   "cooldownSeconds": 60,  // per NPC, after an approach ends
 *   "maxSeconds": 30        // give up and go back to its spot after this long
 * }
 * }</pre>
 *
 * Read-only from the mod (no screen yet); {@code ConfigCodec} keeps unknown keys on save.
 */
public record ApproachConfig(boolean enabled, double maxDistance, int maxConcurrent, int cooldownSeconds, int maxSeconds) {
	public static final String KEY = "npcApproach";
	public static final ApproachConfig DEFAULTS = new ApproachConfig(true, 16, 2, 60, 30);
	public static final ApproachConfig OFF = new ApproachConfig(false, 16, 2, 60, 30);
	private static final Gson GSON = new Gson();

	public int cooldownTicks() {
		return cooldownSeconds * 20;
	}

	public int maxTicks() {
		return maxSeconds * 20;
	}

	public static ApproachConfig parse(@Nullable JsonObject root) {
		ApproachConfig d = DEFAULTS;
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return d;
		}
		JsonElement e = o.get("enabled");
		boolean enabled = e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : d.enabled;
		return new ApproachConfig(enabled,
				num(o, "maxDistance", 1, 64, d.maxDistance),
				(int) num(o, "maxConcurrent", 0, 32, d.maxConcurrent),
				(int) num(o, "cooldownSeconds", 0, 3600, d.cooldownSeconds),
				(int) num(o, "maxSeconds", 1, 600, d.maxSeconds));
	}

	private static double num(JsonObject o, String k, double min, double max, double def) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return def;
		}
		double v = e.getAsDouble();
		return Double.isFinite(v) && v >= min && v <= max ? v : def;
	}

	/** Re-reads the file when its modification time changes (checked at most every {@code every} calls). */
	public static final class Watcher {
		private final Path file;
		private final int every;
		private int calls;
		private java.nio.file.attribute.@Nullable FileTime stamp;
		private ApproachConfig current = DEFAULTS;
		private boolean loaded;

		public Watcher(Path file, int every) {
			this.file = file;
			this.every = Math.max(1, every);
		}

		public ApproachConfig get() {
			if (!loaded || ++calls >= every) {
				calls = 0;
				java.nio.file.attribute.FileTime now;
				try {
					now = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
				} catch (IOException e) {
					now = null;
				}
				if (!loaded || !java.util.Objects.equals(now, stamp)) {
					stamp = now;
					current = load(file);
					loaded = true;
				}
			}
			return current;
		}
	}

	/** Missing or unreadable file → defaults (never throws). */
	public static ApproachConfig load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			return parse(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return DEFAULTS;
		}
	}
}
