package dev.t3craft.client.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.ModConfig;
import dev.t3craft.debug.HitchWatch;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * fix/new-thread-hitch: wires {@link HitchWatch} to the game.
 * <ul>
 *   <li>Frame = from one end of the level render ({@code LevelRenderEvents.END_MAIN}) to the next,
 *   so the client ticks run inside it are counted too; no level → no frame watched.</li>
 *   <li>Client tick and integrated server tick: an "early" phase before every other listener and a
 *   "late" one after them, so our own tick work is inside the measured span.</li>
 * </ul>
 * Config (additive, read once at start; nothing is written): {@code config/t3craft.json}
 * {@code "debug": {"hitches": true|false, "hitchMs": 50}}. Default on in a development environment,
 * off otherwise; {@code -Dt3craft.hitches=true|false} overrides both.
 */
public final class HitchWatchClient {
	private static final Identifier EARLY = Identifier.fromNamespaceAndPath("t3craft", "hitch_early");
	private static final Identifier LATE = Identifier.fromNamespaceAndPath("t3craft", "hitch_late");

	private HitchWatchClient() {
	}

	public static void init() {
		boolean on = FabricLoader.getInstance().isDevelopmentEnvironment();
		double ms = HitchWatch.DEFAULT_REPORT_MS;
		Path file = ModConfig.file();
		try {
			if (Files.exists(file)) {
				JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (root != null && root.isJsonObject() && root.getAsJsonObject().get("debug") instanceof JsonObject d) {
					if (d.get("hitches") != null && d.get("hitches").isJsonPrimitive()) {
						on = d.get("hitches").getAsBoolean();
					}
					if (d.get("hitchMs") != null && d.get("hitchMs").isJsonPrimitive()) {
						double v = d.get("hitchMs").getAsDouble();
						if (Double.isFinite(v) && v > 0) {
							ms = v;
						}
					}
				}
			}
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[hitch] could not read debug.hitches from {}: {}", file, e.toString());
		}
		String prop = System.getProperty("t3craft.hitches");
		if (prop != null && !prop.isBlank()) {
			on = Boolean.parseBoolean(prop.trim());
		}
		HitchWatch w = HitchWatch.get();
		w.configure(on, ms);
		w.context(HitchWatchClient::context);
		T3Craft.LOGGER.info("[hitch] frame/tick watchdog {} (report over {} ms; config/t3craft.json debug.hitches)", on ? "on" : "off", w.reportMs());

		try {
			LevelRenderEvents.END_MAIN.register(ctx -> w.frame());
		} catch (LinkageError e) {
			T3Craft.LOGGER.warn("[hitch] no level render event ({}): frames not watched, ticks still are", e.toString());
		}

		ClientTickEvents.START_CLIENT_TICK.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ClientTickEvents.START_CLIENT_TICK.register(EARLY, mc -> w.clientTickStart());
		ClientTickEvents.END_CLIENT_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ClientTickEvents.END_CLIENT_TICK.register(LATE, mc -> {
			w.clientTickEnd();
			if (mc.level == null) {
				w.frameIdle();
			}
		});

		ServerTickEvents.START_SERVER_TICK.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerTickEvents.START_SERVER_TICK.register(EARLY, s -> w.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerTickEvents.END_SERVER_TICK.register(LATE, s -> w.serverTickEnd());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> w.serverStopped());
	}

	/** Render thread, only for slow frames. */
	private static String context() {
		Minecraft mc = Minecraft.getInstance();
		Object screen = mc.gui.screen();
		return "fps " + mc.getFps() + (screen != null ? ", screen " + screen.getClass().getSimpleName() : "");
	}
}
