package dev.t3craft.client.blocks;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Monitor options, from {@code config/t3craft.json} → {@code "blocks": {"monitor": {...}}}. Every
 * key is additive and optional: a missing or mistyped key keeps its default, values are clamped.
 *
 * <pre>
 * "blocks": { "monitor": {
 *   "liveLog": true,            // subscribe to visible monitors' threads (false = status only)
 *   "subscribeRadius": 16,      // blocks: a monitor closer than this (and visible) subscribes
 *   "releaseHysteresis": 4,     // extra blocks before a subscribed monitor lets go
 *   "releaseAfterSeconds": 10,  // a subscribed monitor out of view lets go after this long
 *   "visibility": "frustum",    // frustum | line_of_sight | distance
 *   "viewDistance": 64,         // blocks: the screen is not drawn beyond this
 *   "nearDistance": 8           // blocks (per block of screen): closer = full log, else big status
 * } }
 * </pre>
 */
public record MonitorSettings(
		boolean liveLog,
		double subscribeRadius,
		double releaseHysteresis,
		long releaseAfterMs,
		Visibility visibility,
		int viewDistance,
		double nearDistance) {

	/** How "the player can see this monitor" is decided (on top of the distance). */
	public enum Visibility {
		/** Inside the camera's view cone. */
		FRUSTUM,
		/** In the view cone and no opaque block between the eye and the screen. */
		LINE_OF_SIGHT,
		/** Distance only. */
		DISTANCE;

		static @Nullable Visibility parse(String s) {
			return switch (s.toLowerCase(Locale.ROOT)) {
				case "frustum" -> FRUSTUM;
				case "line_of_sight", "los" -> LINE_OF_SIGHT;
				case "distance" -> DISTANCE;
				default -> null;
			};
		}
	}

	public static final MonitorSettings DEFAULT = new MonitorSettings(true, 16, 4, 10_000, Visibility.FRUSTUM, 64, 8);

	public MonitorSettings {
		subscribeRadius = clamp(subscribeRadius, 1, 128);
		releaseHysteresis = clamp(releaseHysteresis, 0, 64);
		releaseAfterMs = (long) clamp(releaseAfterMs, 0, 600_000);
		visibility = visibility == null ? Visibility.FRUSTUM : visibility;
		viewDistance = (int) clamp(viewDistance, 8, 256);
		nearDistance = clamp(nearDistance, 1, 64);
	}

	/** From the whole config object (may be null). */
	public static MonitorSettings from(@Nullable JsonObject root) {
		if (root == null || !(root.get("blocks") instanceof JsonObject blocks) || !(blocks.get("monitor") instanceof JsonObject m)) {
			return DEFAULT;
		}
		MonitorSettings d = DEFAULT;
		Visibility v = d.visibility();
		if (m.get("visibility") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			Visibility p = Visibility.parse(e.getAsString());
			v = p == null ? v : p;
		}
		return new MonitorSettings(
				bool(m, "liveLog", d.liveLog()),
				num(m, "subscribeRadius", d.subscribeRadius()),
				num(m, "releaseHysteresis", d.releaseHysteresis()),
				(long) (num(m, "releaseAfterSeconds", d.releaseAfterMs() / 1000.0) * 1000),
				v,
				(int) num(m, "viewDistance", d.viewDistance()),
				num(m, "nearDistance", d.nearDistance()));
	}

	/** Reads {@code file} (missing or broken → defaults). Never throws. */
	public static MonitorSettings load(Path file) {
		try {
			if (Files.exists(file)) {
				return from(new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class));
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			// broken file: defaults (the config screen reports it)
		}
		return DEFAULT;
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
	}

	private static double num(JsonObject o, String k, double def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
