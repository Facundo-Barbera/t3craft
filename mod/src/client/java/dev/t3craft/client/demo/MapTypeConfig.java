package dev.t3craft.client.demo;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.mapgen.MapTypes;

/**
 * The {@code mapType} key of {@code config/t3craft.json} (top-level, default {@code "hybrid-ringcampus"}, the citadel;
 * additive: the config codec keeps it like any unknown key, as it does {@code hqLayout}). It remembers the map type
 * last chosen in the HQ menu; the menu opens the world of that type. It does not change any world that exists.
 */
public final class MapTypeConfig {
	/** Top-level config key. */
	public static final String KEY = "mapType";

	private MapTypeConfig() {
	}

	/** The configured type; the citadel when the key is missing or names a type that is not registered. */
	public static MapTypes selected() {
		JsonElement e = ConfigManager.get().extra(KEY);
		MapTypes t = e != null && e.isJsonPrimitive() ? MapTypes.byId(e.getAsString()) : null;
		return t != null ? t : MapTypes.CITADEL;
	}

	/** {@link #selected()}'s id; the citadel when the config cannot be read (never throws). */
	public static String selectedId() {
		try {
			return selected().id();
		} catch (RuntimeException | LinkageError e) {
			return MapTypes.CITADEL.id();
		}
	}

	/** Saves {@code mapType}; unknown ids are saved as the citadel. */
	public static ConfigManager.SaveResult select(String typeId) {
		MapTypes t = MapTypes.byId(typeId);
		return ConfigManager.get().putExtra(KEY, new JsonPrimitive(t != null ? t.id() : MapTypes.CITADEL.id()));
	}
}
