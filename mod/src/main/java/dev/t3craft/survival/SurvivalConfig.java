package dev.t3craft.survival;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.survival.SurvivalSettings.DebugGate;
import dev.t3craft.survival.SurvivalSettings.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Reads the {@code "survival"} section of {@code config/t3craft.json}. Read-only on purpose: the file is
 * written by feat/config's {@code ConfigStore}, which keeps unknown sections on save, so this section
 * survives without this class ever writing. Absent file/section/key → default; invalid value → default
 * plus a warning naming only the key.
 * <pre>{@code
 * "survival": {
 *   "terminalForKey": "never",        // never | survival | always
 *   "builderMaterials": "survival",   // never | survival | always
 *   "debugBuilds": "cheats",          // cheats | any
 *   "firstContactAdvancement": true
 * }
 * }</pre>
 */
public final class SurvivalConfig {
	public static final String SECTION = "survival";

	private SurvivalConfig() {
	}

	public record Result(SurvivalSettings settings, List<String> warnings) {
	}

	public static Result load(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return new Result(SurvivalSettings.DEFAULTS, List.of());
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			return parse(root.isJsonObject() ? root.getAsJsonObject() : null);
		} catch (IOException | RuntimeException e) {
			return new Result(SurvivalSettings.DEFAULTS, List.of(SECTION + " (unreadable config file)"));
		}
	}

	/** @param root the whole t3craft.json object (or null) */
	public static Result parse(JsonObject root) {
		List<String> warnings = new ArrayList<>();
		JsonObject s = null;
		if (root != null && root.has(SECTION)) {
			if (root.get(SECTION).isJsonObject()) {
				s = root.getAsJsonObject(SECTION);
			} else {
				warnings.add(SECTION);
			}
		}
		SurvivalSettings d = SurvivalSettings.DEFAULTS;
		if (s == null) {
			return new Result(d, List.copyOf(warnings));
		}
		Requirement terminal = enumValue(s, "terminalForKey", Requirement::valueOf, d.terminalForKey(), warnings);
		Requirement materials = enumValue(s, "builderMaterials", Requirement::valueOf, d.builderMaterials(), warnings);
		DebugGate debug = enumValue(s, "debugBuilds", DebugGate::valueOf, d.debugBuilds(), warnings);
		boolean first = d.firstContactAdvancement();
		if (s.has("firstContactAdvancement")) {
			JsonElement e = s.get("firstContactAdvancement");
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				first = e.getAsBoolean();
			} else {
				warnings.add(SECTION + ".firstContactAdvancement");
			}
		}
		return new Result(new SurvivalSettings(terminal, materials, debug, first), List.copyOf(warnings));
	}

	private static <E> E enumValue(JsonObject s, String key, Function<String, E> valueOf, E fallback, List<String> warnings) {
		if (!s.has(key) || s.get(key).isJsonNull()) {
			return fallback;
		}
		JsonElement e = s.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			try {
				return valueOf.apply(e.getAsString().trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				// warn below
			}
		}
		warnings.add(SECTION + "." + key);
		return fallback;
	}
}
