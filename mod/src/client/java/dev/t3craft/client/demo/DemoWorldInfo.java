package dev.t3craft.client.demo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.config.ConfigManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * feat/ingame-preview-drop: read-only view of the official HQ world for other client modules (the preview
 * drop showroom), without touching {@link DemoClient}. Answers one question, "is this integrated server
 * running the T3Craft HQ save, and where is its origin?", with the same rules the demo uses (the configured
 * folder, a void or flat world as the save is, the {@code demo.origin} config).
 *
 * @param root the save folder
 */
public record DemoWorldInfo(Path root, int originX, int originY, int originZ, boolean voidWorld) {
	/** The HQ world {@code server} runs, or null when it runs anything else (or is not a singleplayer server). Any thread. */
	public static @Nullable DemoWorldInfo of(MinecraftServer server) {
		DemoConfig loaded = loadConfig();
		if (!VanillaDemoWorld.isDemoServer(server, loaded.spec().folderId())) {
			return null;
		}
		boolean voidWorld = VanillaDemoWorld.isVoid(server);
		DemoWorldSpec spec = loaded.forWorld(voidWorld).spec();
		return new DemoWorldInfo(VanillaDemoWorld.worldRoot(server), spec.originX(), spec.originY(), spec.originZ(), voidWorld);
	}

	private static DemoConfig loadConfig() {
		try {
			Path f = ConfigManager.get().file();
			if (Files.exists(f)) {
				JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				return DemoConfig.fromRoot(e.isJsonObject() ? e.getAsJsonObject() : null);
			}
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[drop] could not read config, using defaults ({})", e.getClass().getSimpleName());
		}
		return DemoConfig.defaults();
	}
}
