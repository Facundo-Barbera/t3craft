package dev.t3craft.blocks;

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
 * Whether the custom blocks module registers anything. Decided once at startup (registries freeze
 * right after the entrypoints run, so it cannot change per world or per server).
 *
 * <p>Sources, strongest first:
 * <ol>
 *   <li>system property {@code -Dt3craft.blocks=false|true} (modpacks, the future "GUI only" client mode);</li>
 *   <li>{@code "blocks": {"enabled": false}} in {@code config/t3craft.json} (additive key, default true);</li>
 *   <li>default: enabled.</li>
 * </ol>
 * Pure apart from {@link #load}; tolerant of missing files and wrong types (→ default).
 */
public record BlocksSwitch(boolean enabled, String reason) {
	public static final String PROPERTY = "t3craft.blocks";
	public static final String CONFIG_KEY = "blocks";
	public static final BlocksSwitch DEFAULT = new BlocksSwitch(true, "default");
	public static final BlocksSwitch OFF = new BlocksSwitch(false, "disabled");

	/** Resolves from the system property value (may be null) and the whole config object (may be null). */
	public static BlocksSwitch resolve(@Nullable String property, @Nullable JsonObject config) {
		if (property != null && !property.isBlank()) {
			String p = property.trim().toLowerCase(Locale.ROOT);
			if (p.equals("false") || p.equals("off") || p.equals("0")) {
				return new BlocksSwitch(false, "-D" + PROPERTY + "=" + property.trim());
			}
			if (p.equals("true") || p.equals("on") || p.equals("1")) {
				return new BlocksSwitch(true, "-D" + PROPERTY + "=" + property.trim());
			}
		}
		if (config != null && config.get(CONFIG_KEY) instanceof JsonObject o) {
			JsonElement e = o.get("enabled");
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				return new BlocksSwitch(e.getAsBoolean(), "config " + CONFIG_KEY + ".enabled=" + e.getAsBoolean());
			}
		}
		return DEFAULT;
	}

	/** Reads {@code configFile} (missing or broken → no config) and the system property. Never throws. */
	public static BlocksSwitch load(Path configFile) {
		JsonObject root = null;
		try {
			if (Files.exists(configFile)) {
				root = new Gson().fromJson(Files.readString(configFile, StandardCharsets.UTF_8), JsonObject.class);
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			root = null;
		}
		return resolve(System.getProperty(PROPERTY), root);
	}
}
