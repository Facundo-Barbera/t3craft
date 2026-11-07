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
 * Which thread chips are shown, from its own additive block in {@code config/t3craft.json}:
 *
 * <pre>{@code "chips": {
 *   "modelNameplate": true, "modelPanel": true,
 *   "branchNameplate": false, "branchPanel": true, "branchWorkstation": true
 * }}</pre>
 *
 * Missing block, key or wrong type → default. Like {@code WorkstationConfig}, kept out of
 * {@code T3CraftSettings} so no shared record changes; the settings screen's merge keeps this key.
 * Edit the file by hand; {@link #current()} picks changes up within a couple of seconds.
 */
public record ChipsConfig(
		boolean modelNameplate,
		boolean modelPanel,
		boolean branchNameplate,
		boolean branchPanel,
		boolean branchWorkstation) {
	public static final String KEY = "chips";
	public static final ChipsConfig DEFAULTS = new ChipsConfig(true, true, false, true, true);
	private static final Gson GSON = new Gson();
	private static final long POLL_MS = 2000;

	private static volatile ChipsConfig current = DEFAULTS;
	private static @Nullable FileTime stamp;
	private static long lastPoll = Long.MIN_VALUE;

	public static ChipsConfig parse(@Nullable JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		return new ChipsConfig(
				bool(o, "modelNameplate", DEFAULTS.modelNameplate),
				bool(o, "modelPanel", DEFAULTS.modelPanel),
				bool(o, "branchNameplate", DEFAULTS.branchNameplate),
				bool(o, "branchPanel", DEFAULTS.branchPanel),
				bool(o, "branchWorkstation", DEFAULTS.branchWorkstation));
	}

	/** Missing or unreadable file → defaults (never throws). */
	public static ChipsConfig load(Path file) {
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
	public static ChipsConfig current() {
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

	private static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
	}
}
