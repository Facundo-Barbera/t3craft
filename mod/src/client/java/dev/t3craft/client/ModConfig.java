package dev.t3craft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/t3craft.json}. Holds only how to reach the bridge (it normally runs on the
 * homelab, reached over Tailscale). No T3 credentials are ever stored by the mod; {@code bridgeKey}
 * is an optional bridge-access key, not a T3 token.
 *
 * <p>Overrides: env {@code T3CRAFT_BRIDGE_URL} / system property {@code t3craft.bridgeUrl}.
 */
public record ModConfig(URI bridgeUrl, @Nullable String bridgeKey) {
	public static final String DEFAULT_URL = "ws://127.0.0.1:7878/";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	}

	public static ModConfig load() {
		Path file = file();
		String url = DEFAULT_URL;
		String key = null;
		try {
			if (Files.exists(file)) {
				JsonObject o = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
				if (o != null && o.has("bridgeUrl") && o.get("bridgeUrl").isJsonPrimitive()) {
					url = o.get("bridgeUrl").getAsString();
				}
				if (o != null && o.has("bridgeKey") && o.get("bridgeKey").isJsonPrimitive()) {
					key = o.get("bridgeKey").getAsString();
				}
			} else {
				JsonObject o = new JsonObject();
				o.addProperty("bridgeUrl", DEFAULT_URL);
				o.add("bridgeKey", com.google.gson.JsonNull.INSTANCE);
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("could not read {}: {}; using defaults", file, e.getMessage());
		}
		String override = System.getProperty("t3craft.bridgeUrl", System.getenv("T3CRAFT_BRIDGE_URL"));
		if (override != null && !override.isBlank()) {
			url = override.trim();
		}
		return new ModConfig(parse(url), key == null || key.isBlank() ? null : key.trim());
	}

	private static URI parse(String raw) {
		try {
			URI u = URI.create(raw);
			if (("ws".equals(u.getScheme()) || "wss".equals(u.getScheme())) && u.getHost() != null && u.getUserInfo() == null) {
				return u;
			}
			T3Craft.LOGGER.warn("bridgeUrl must be ws:// or wss:// without credentials: {}; using default", raw);
		} catch (IllegalArgumentException e) {
			T3Craft.LOGGER.warn("invalid bridgeUrl {}; using default", raw);
		}
		return URI.create(DEFAULT_URL);
	}
}
