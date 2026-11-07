package dev.t3craft.client.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The optional {@code "demo"} section of {@code config/t3craft.json}. Additive: absent keys (or the
 * whole section) mean defaults, and the config feature's codec keeps unknown sections when it
 * saves, so nothing else has to know about it.
 * <pre>{@code
 * "demo": {
 *   "worldFolder": "t3craft-hq",     // save folder (letters, digits, _ and -)
 *   "worldName": "T3Craft HQ",       // name in the world list
 *   "origin": [0, -60, 8],           // plan origin; -60 stands on default superflat grass
 *   "structure": "auto",             // "auto" | "hq" | "shed"
 *   "blocksPerTick": 2.0,
 *   "crew": 4,
 *   "titleButton": true,             // button on the title and world-select screens
 *   "resetBackup": true              // zip the old HQ world before "reset HQ world" deletes it
 * }
 * }</pre>
 */
public record DemoConfig(DemoWorldSpec spec, String structure, double blocksPerTick, int crew, boolean titleButton,
		List<String> warnings, boolean resetBackup) {

	public static final double DEFAULT_BLOCKS_PER_TICK = 2.0;
	public static final int DEFAULT_CREW = 4;

	public DemoConfig {
		warnings = List.copyOf(warnings);
	}

	/** Pre-reset-hq constructor (backup on). */
	public DemoConfig(DemoWorldSpec spec, String structure, double blocksPerTick, int crew, boolean titleButton, List<String> warnings) {
		this(spec, structure, blocksPerTick, crew, titleButton, warnings, true);
	}

	public DemoConfig withResetBackup(boolean on) {
		return new DemoConfig(spec, structure, blocksPerTick, crew, titleButton, warnings, on);
	}

	public static DemoConfig defaults() {
		return new DemoConfig(DemoWorldSpec.defaults(), DemoStructures.AUTO, DEFAULT_BLOCKS_PER_TICK, DEFAULT_CREW, true, List.of());
	}

	/** Reads the root config object; invalid values fall back to defaults with a warning naming only the key. */
	public static DemoConfig fromRoot(@Nullable JsonObject root) {
		if (root == null || !root.has("demo") || !root.get("demo").isJsonObject()) {
			return defaults();
		}
		JsonObject d = root.getAsJsonObject("demo");
		List<String> warn = new ArrayList<>();
		DemoWorldSpec spec = DemoWorldSpec.defaults();

		String folder = string(d, "worldFolder", DemoWorldSpec.DEFAULT_FOLDER, warn);
		if (!DemoWorldSpec.validFolderId(folder)) {
			warn.add("demo.worldFolder");
			folder = DemoWorldSpec.DEFAULT_FOLDER;
		}
		String name = string(d, "worldName", DemoWorldSpec.DEFAULT_NAME, warn);
		if (name.isBlank()) {
			name = DemoWorldSpec.DEFAULT_NAME;
		}
		spec = spec.withFolder(folder, name);

		if (d.has("origin")) {
			int[] o = origin(d.get("origin"));
			if (o == null) {
				warn.add("demo.origin");
			} else {
				spec = spec.withOrigin(o[0], o[1], o[2]);
			}
		}

		String structure = string(d, "structure", DemoStructures.AUTO, warn);
		double bpt = number(d, "blocksPerTick", DEFAULT_BLOCKS_PER_TICK, 0.05, 64, warn);
		int crew = (int) number(d, "crew", DEFAULT_CREW, 1, 8, warn);
		boolean button = bool(d, "titleButton", true, warn);
		boolean backup = bool(d, "resetBackup", true, warn);
		return new DemoConfig(spec, structure.isBlank() ? DemoStructures.AUTO : structure, bpt, crew, button, warn, backup);
	}

	private static int @Nullable [] origin(JsonElement e) {
		try {
			if (!e.isJsonArray()) {
				return null;
			}
			JsonArray a = e.getAsJsonArray();
			if (a.size() != 3) {
				return null;
			}
			int[] o = new int[3];
			for (int i = 0; i < 3; i++) {
				double v = a.get(i).getAsDouble();
				if (v != Math.rint(v) || Math.abs(v) > 30_000_000) {
					return null;
				}
				o[i] = (int) v;
			}
			return o[1] >= -64 && o[1] <= 310 ? o : null;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static String string(JsonObject d, String key, String def, List<String> warn) {
		if (!d.has(key)) {
			return def;
		}
		JsonElement e = d.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			return e.getAsString().trim();
		}
		warn.add("demo." + key);
		return def;
	}

	private static double number(JsonObject d, String key, double def, double min, double max, List<String> warn) {
		if (!d.has(key)) {
			return def;
		}
		JsonElement e = d.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			double v = e.getAsDouble();
			if (v >= min && v <= max) {
				return v;
			}
		}
		warn.add("demo." + key);
		return def;
	}

	private static boolean bool(JsonObject d, String key, boolean def, List<String> warn) {
		if (!d.has(key)) {
			return def;
		}
		JsonElement e = d.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		warn.add("demo." + key);
		return def;
	}
}
