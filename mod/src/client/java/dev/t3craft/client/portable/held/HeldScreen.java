package dev.t3craft.client.portable.held;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.T3CraftSettings;
import dev.t3craft.client.portable.PortableAdapter;
import dev.t3craft.client.portable.PortableEnv;
import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.portable.held.HeldScreenModel.Row;
import dev.t3craft.portable.PortableItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Live screen on the held T3 Terminal (either hand; map-style, like ComputerCraft's pocket
 * computer). Once per client tick, and only while the local player holds the item and
 * {@code portable.heldScreen} is on, it rebuilds the {@link HeldScreenModel} rows from the state the
 * client already has (no connection, no request); {@link HeldScreenCache} lets the texture be
 * rasterised and uploaded only when those rows change. {@link TerminalScreenRenderer} then draws the
 * cached texture each frame at no CPU cost. The texture is released when the world closes, the
 * client stops or the setting is turned off. Right click still opens {@code PortableScreen}.
 */
public final class HeldScreen {
	private static final HeldScreenCache CACHE = new HeldScreenCache();
	private static final HeldScreenTexture TEXTURE = new HeldScreenTexture();
	private static @Nullable BitmapFont font;
	private static volatile boolean visible;

	private HeldScreen() {
	}

	/** From {@code PortableClient.register}. */
	public static void register(T3CraftClient client) {
		TerminalScreenRenderer.registerType();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(HeldScreen::release));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> release());
	}

	/** True while the renderer should draw the texture (held, enabled, texture uploaded). */
	static boolean visible() {
		return visible;
	}

	static boolean enabled() {
		return ConfigManager.get().settings().portable().heldScreen();
	}

	private static void tick(Minecraft mc, T3CraftClient client) {
		boolean on = enabled();
		LocalPlayer p = mc.player;
		if (!on || p == null || !holds(p)) {
			visible = false;
			if (!on) {
				release();
			}
			return; // not held: keep the texture, switching back costs nothing if nothing changed
		}
		if (font == null) {
			font = HeldFont.load(mc.getResourceManager());
		}
		BitmapFont f = font;
		PortableState state = PortableAdapter.snapshot(client);
		List<Row> rows = HeldScreenModel.build(new HeldScreenModel.Input(state, badges(state), HeldScreenRaster.textWidth(),
				HeldScreenRaster.maxRows(), f::width));
		if (CACHE.offer(rows) || !TEXTURE.ready()) {
			TEXTURE.upload(HeldScreenRaster.render(rows, f), HeldScreenRaster.WIDTH, HeldScreenRaster.HEIGHT);
		}
		visible = true;
	}

	private static boolean holds(LocalPlayer p) {
		Item terminal = PortableItems.terminal();
		return terminal != null && (p.getMainHandItem().is(terminal) || p.getOffhandItem().is(terminal));
	}

	/** Environment chips: label from the environment list (or a thread row), colour from the config. */
	private static java.util.function.Function<String, EnvBadge> badges(PortableState state) {
		Map<String, String> labels = new HashMap<>();
		for (PortableThread t : state.threads()) {
			labels.putIfAbsent(t.env(), t.envLabel());
		}
		for (PortableEnv e : state.environments()) {
			labels.put(e.id(), e.label());
		}
		T3CraftSettings s = ConfigManager.get().settings();
		Map<String, EnvBadge> memo = new HashMap<>();
		return env -> memo.computeIfAbsent(env, id -> EnvBadge.of(id, labels.get(id) == null ? id : labels.get(id), s.env(id).color()));
	}

	/** Frees the GPU texture and forgets the cached rows (world closed / setting off / stopping). */
	public static void release() {
		visible = false;
		TEXTURE.release();
		CACHE.invalidate();
		font = null; // re-read on next use: a resource pack may have changed the sheet
	}
}
