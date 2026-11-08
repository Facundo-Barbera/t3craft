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
 * feat/npc-idle-wander: idle NPCs strolling around the HQ ({@link IdleWander}). Its own additive
 * block in {@code config/t3craft.json} (missing block or key → default; wrong type or out of range →
 * default):
 *
 * <pre>{@code
 * "npcWander": {
 *   "enabled": true,
 *   "idleSeconds": 120,       // calm (idle/done, nothing asked) this long before strolling
 *   "includeUnpinned": false, // also threads that are not pinned in T3
 *   "maxWalkers": 6,          // strollers at once, whole HQ
 *   "routesPerTick": 1,       // new stroll routes asked per client tick, whole HQ
 *   "hopDistance": 18         // blocks; farthest next spot from where the NPC stands
 * }
 * }</pre>
 */
public record WanderConfig(boolean enabled, int idleSeconds, boolean includeUnpinned, int maxWalkers, int routesPerTick, double hopDistance) {
	public static final String KEY = "npcWander";
	public static final WanderConfig DEFAULTS = new WanderConfig(true, 120, false, 6, 1, 18);
	public static final WanderConfig OFF = new WanderConfig(false, 120, false, 6, 1, 18);
	private static final Gson GSON = new Gson();

	public int idleTicks() {
		return idleSeconds * 20;
	}

	public static WanderConfig parse(@Nullable JsonObject root) {
		WanderConfig d = DEFAULTS;
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return d;
		}
		return new WanderConfig(bool(o, "enabled", d.enabled),
				(int) num(o, "idleSeconds", 0, 3600, d.idleSeconds),
				bool(o, "includeUnpinned", d.includeUnpinned),
				(int) num(o, "maxWalkers", 0, 32, d.maxWalkers),
				(int) num(o, "routesPerTick", 1, 4, d.routesPerTick),
				num(o, "hopDistance", 4, 32, d.hopDistance));
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
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
		private WanderConfig current = DEFAULTS;
		private boolean loaded;

		public Watcher(Path file, int every) {
			this.file = file;
			this.every = Math.max(1, every);
		}

		public WanderConfig get() {
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
	public static WanderConfig load(Path file) {
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
