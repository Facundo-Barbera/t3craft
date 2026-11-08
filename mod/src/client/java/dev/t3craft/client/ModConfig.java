package dev.t3craft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.T3Craft;
import dev.t3craft.t3.Visibility;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/t3craft.json} (no secrets here; T3 credentials live in
 * {@code config/t3craft/credentials/}).
 * <ul>
 *   <li>{@code mode}: {@code "direct"} (pair the mod with each T3 environment, the default for new
 *   installs) or {@code "bridge"} (legacy: talk to the t3craft bridge). An existing file with
 *   {@code bridgeUrl} and no {@code mode} stays in bridge mode.</li>
 *   <li>{@code bridgeUrl}, {@code bridgeKey}: bridge mode only.</li>
 *   <li>{@code visibility.recentWindowHours} (24), {@code visibility.maxPerEnvironment} (40),
 *   {@code includeSubagents} (false): direct mode filter.</li>
 * </ul>
 * Overrides: {@code T3CRAFT_MODE} / {@code -Dt3craft.mode}, {@code T3CRAFT_BRIDGE_URL} /
 * {@code -Dt3craft.bridgeUrl}.
 */
public record ModConfig(Mode mode, URI bridgeUrl, @Nullable String bridgeKey, Visibility.Options visibility) {
	public enum Mode {
		DIRECT,
		BRIDGE
	}

	public static final String DEFAULT_URL = "ws://127.0.0.1:7878/";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	}

	/** tools/ui-render: a data dir to use instead of the game's (headless renders); null = the game's. */
	private static volatile @Nullable Path dataDirOverride;

	/** {@code config/t3craft/}: environments.json + credentials/. */
	public static Path dataDir() {
		Path o = dataDirOverride;
		return o != null ? o : FabricLoader.getInstance().getConfigDir().resolve("t3craft");
	}

	/** Only for the headless UI renderer (docs/tools/ui-render.md): point {@link #dataDir()} at a scratch folder, null to undo. */
	public static void useDataDir(@Nullable Path dir) {
		dataDirOverride = dir;
	}

	public static ModConfig load() {
		Path file = file();
		String url = DEFAULT_URL;
		String key = null;
		Mode mode = Mode.DIRECT;
		long windowMs = Visibility.DEFAULT_RECENT_WINDOW_MS;
		int max = Visibility.DEFAULT_MAX_PER_ENVIRONMENT;
		boolean subagents = false;
		try {
			if (Files.exists(file)) {
				JsonObject o = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
				if (o == null) {
					o = new JsonObject();
				}
				if (prim(o, "bridgeUrl")) {
					url = o.get("bridgeUrl").getAsString();
				}
				if (prim(o, "bridgeKey")) {
					key = o.get("bridgeKey").getAsString();
				}
				if (prim(o, "mode")) {
					mode = "bridge".equalsIgnoreCase(o.get("mode").getAsString()) ? Mode.BRIDGE : Mode.DIRECT;
				} else if (o.has("bridgeUrl")) {
					mode = Mode.BRIDGE; // pre-v2.1 install: keep working as before
				}
				JsonElement vis = o.get("visibility");
				if (vis != null && vis.isJsonObject()) {
					JsonObject v = vis.getAsJsonObject();
					if (prim(v, "recentWindowHours")) {
						double h = v.get("recentWindowHours").getAsDouble();
						if (h >= 0 && Double.isFinite(h)) {
							windowMs = (long) (h * 3_600_000L);
						}
					}
					if (prim(v, "maxPerEnvironment")) {
						int m = v.get("maxPerEnvironment").getAsInt();
						if (m >= 1 && m <= 1000) {
							max = m;
						}
					}
				}
				subagents = prim(o, "includeSubagents") && o.get("includeSubagents").getAsBoolean();
			} else {
				JsonObject o = new JsonObject();
				o.addProperty("mode", "direct");
				JsonObject v = new JsonObject();
				v.addProperty("recentWindowHours", 24);
				v.addProperty("maxPerEnvironment", 40);
				o.add("visibility", v);
				o.addProperty("includeSubagents", false);
				o.addProperty("bridgeUrl", DEFAULT_URL);
				o.add("bridgeKey", com.google.gson.JsonNull.INSTANCE);
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("could not read {}: {}; using defaults", file, e.getMessage());
		}
		String modeOverride = System.getProperty("t3craft.mode", System.getenv("T3CRAFT_MODE"));
		if (modeOverride != null && !modeOverride.isBlank()) {
			mode = "bridge".equalsIgnoreCase(modeOverride.trim()) ? Mode.BRIDGE : Mode.DIRECT;
		}
		String override = System.getProperty("t3craft.bridgeUrl", System.getenv("T3CRAFT_BRIDGE_URL"));
		if (override != null && !override.isBlank()) {
			url = override.trim();
		}
		return new ModConfig(mode, parse(url), key == null || key.isBlank() ? null : key.trim(),
				new Visibility.Options(windowMs, max, subagents));
	}

	private static boolean prim(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive();
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
