package dev.t3craft.client.npc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * How the text over an NPC is laid out, from its own additive block in {@code config/t3craft.json}:
 *
 * <pre>{@code "labels": { "mode": "compact", "detailDistance": 4.0, "titleMax": 24 }}</pre>
 *
 * {@code compact} (default): one short name tag (state glyph + truncated title, state colour); the
 * detail panel only appears when the player is within {@code detailDistance} blocks, aims at the
 * NPC, or left-clicks it. {@code detailed}: the previous layout (chips on the name tag, panel
 * always on). Missing block, key or bad value → default. Same polling as {@link ChipsConfig}.
 */
public record LabelConfig(boolean compact, double detailDistance, int titleMax) {
	public static final String KEY = "labels";
	public static final LabelConfig DEFAULTS = new LabelConfig(true, 4.0, 24);
	private static final Gson GSON = new Gson();
	private static final long POLL_MS = 2000;

	private static volatile LabelConfig current = DEFAULTS;
	private static @Nullable FileTime stamp;
	private static long lastPoll = Long.MIN_VALUE;

	public static LabelConfig parse(@Nullable JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		boolean compact = DEFAULTS.compact;
		if (o.get("mode") instanceof JsonElement m && m.isJsonPrimitive() && m.getAsJsonPrimitive().isString()) {
			String s = m.getAsString().trim();
			if (s.equalsIgnoreCase("detailed")) {
				compact = false;
			} else if (s.equalsIgnoreCase("compact")) {
				compact = true;
			}
		}
		double dist = num(o, "detailDistance", DEFAULTS.detailDistance);
		if (!(dist >= 0 && dist <= 64)) {
			dist = DEFAULTS.detailDistance;
		}
		int max = (int) num(o, "titleMax", DEFAULTS.titleMax);
		if (max < 4 || max > 128) {
			max = DEFAULTS.titleMax;
		}
		return new LabelConfig(compact, dist, max);
	}

	/** Missing or unreadable file → defaults (never throws). */
	public static LabelConfig load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			return parse(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return DEFAULTS;
		}
	}

	/** The live config, re-read from {@code config/t3craft.json} when its mtime changes (polled ≤ every 2 s). */
	public static LabelConfig current() {
		long now = System.currentTimeMillis();
		if (now - lastPoll >= POLL_MS || lastPoll == Long.MIN_VALUE) {
			lastPoll = now;
			try {
				Path f = FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
				FileTime t = Files.exists(f) ? Files.getLastModifiedTime(f) : null;
				if (!Objects.equals(t, stamp)) {
					stamp = t;
					current = load(f);
				}
			} catch (IOException | RuntimeException e) {
				// keep the last known config
			}
		}
		return current;
	}

	private static double num(JsonObject o, String key, double def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
	}
}
