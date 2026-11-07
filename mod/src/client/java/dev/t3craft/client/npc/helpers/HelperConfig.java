package dev.t3craft.client.npc.helpers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The helpers' own block in {@code config/t3craft.json} (additive; nothing else changes):
 *
 * <pre>{@code "helpers": { "enabled": true, "maxPerParent": 3 }}</pre>
 *
 * Missing block or key, or a wrong type → default. {@code maxPerParent} is clamped to
 * {@code [0, }{@link #MAX}{@code ]}; 0 shows no helper bodies, only the "+n" on the parent's panel.
 * Kept out of {@code T3CraftSettings} (same approach as {@code WorkstationConfig}) so no record
 * constructor changes; {@code ConfigCodec} merges over the JSON it read, so the block survives saves
 * from the settings screen. Re-read on {@code /t3craft reload}.
 */
public record HelperConfig(boolean enabled, int maxPerParent) {
	public static final String KEY = "helpers";
	public static final int MAX = 8;
	public static final HelperConfig DEFAULTS = new HelperConfig(true, 3);

	public static HelperConfig parse(JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		JsonElement e = o.get("enabled");
		boolean enabled = e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : DEFAULTS.enabled;
		JsonElement m = o.get("maxPerParent");
		int max = DEFAULTS.maxPerParent;
		if (m != null && m.isJsonPrimitive() && m.getAsJsonPrimitive().isNumber()) {
			double d = m.getAsDouble();
			max = Double.isFinite(d) ? (int) Math.max(0, Math.min(MAX, Math.round(d))) : DEFAULTS.maxPerParent;
		}
		return new HelperConfig(enabled, max);
	}

	/** Missing or unreadable file → defaults (never throws). */
	public static HelperConfig load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return root instanceof JsonObject o ? parse(o) : DEFAULTS;
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return DEFAULTS;
		}
	}
}
