package dev.t3craft.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.T3CraftClient;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * T3Craft key mappings, rebindable in Options → Controls → Key Binds (category "T3Craft"; the
 * vanilla screen saves them in {@code options.txt}). Only "open settings" has a default key (J);
 * the rest start unbound so they never clash with other mods.
 *
 * <p>Other features can add their own mappings to {@link #CATEGORY} instead of registering a second
 * T3Craft category.
 */
public final class T3CraftKeys {
	public static final KeyMapping.Category CATEGORY = category(Identifier.fromNamespaceAndPath("t3craft", "general"));

	private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

	private final KeyMapping openSettings = key("open_settings", InputConstants.KEY_J);
	private final KeyMapping openEnvironments = key("open_environments", UNBOUND);
	private final KeyMapping pair = key("pair", UNBOUND);
	/** Piece browser (docs/features/piece-browser.md). */
	private final KeyMapping openPieces = key("open_pieces", InputConstants.KEY_K);
	private final KeyMapping reconnect = key("reconnect", UNBOUND);
	private final KeyMapping reload = key("reload", UNBOUND);
	private final KeyMapping toggleNotifications = key("toggle_notifications", UNBOUND);
	/** Attention queue (docs/features/attention-queue.md): both unbound by default. */
	private final KeyMapping attentionNext = key("attention_next", UNBOUND);
	private final KeyMapping attentionLast = key("attention_last", UNBOUND);

	/**
	 * Vanilla throws when a category id is registered twice (a hot-swapped class re-running its
	 * static init, or another branch registering the same id). Category is a record keyed by id, so
	 * an unregistered instance with the same id is equal to the registered one.
	 */
	private static KeyMapping.Category category(Identifier id) {
		try {
			return KeyMapping.Category.register(id);
		} catch (IllegalArgumentException alreadyRegistered) {
			return new KeyMapping.Category(id);
		}
	}

	private static KeyMapping key(String name, int code) {
		return new KeyMapping("key.t3craft." + name, code, CATEGORY);
	}

	public List<KeyMapping> all() {
		return List.of(openSettings, openEnvironments, openPieces, pair, reconnect, reload, toggleNotifications, attentionNext, attentionLast);
	}

	/** Registers with Fabric. Call once from the client initializer. */
	public void register() {
		all().forEach(KeyMappingHelper::registerKeyMapping);
	}

	/** End-of-tick handler: consumes presses and runs their action. */
	public void tick(Minecraft mc) {
		while (openSettings.consumeClick()) {
			mc.gui.setScreen(new SettingsScreen(null, EnvironmentActions.DEFAULT));
		}
		while (openEnvironments.consumeClick()) {
			mc.gui.setScreen(new EnvironmentsScreen(null, EnvironmentActions.DEFAULT));
		}
		while (openPieces.consumeClick()) {
			if (mc.gui.screen() == null) { // a text box on screen would type the K
				mc.gui.setScreen(new dev.t3craft.client.pieces.PieceBrowserScreen(null));
			}
		}
		while (pair.consumeClick()) {
			mc.gui.setScreen(EnvironmentActions.DEFAULT.pairScreen(null));
		}
		while (reconnect.consumeClick()) {
			T3CraftClient c = T3CraftClient.get();
			if (c != null) {
				c.reconnect();
				overlay(mc, "T3Craft: reconnecting");
			}
		}
		while (reload.consumeClick()) {
			ConfigManager.get().load();
			EnvironmentActions.DEFAULT.reload();
			overlay(mc, "T3Craft: config reloaded");
		}
		while (toggleNotifications.consumeClick()) {
			ConfigManager.SaveResult r = ConfigManager.get().update(s -> s.withNotifications(s.notifications().withEnabled(!s.notifications().enabled())));
			boolean on = ConfigManager.get().settings().notifications().enabled();
			overlay(mc, "T3Craft notifications " + (on ? "on" : "muted") + (r.ok() ? "" : " (" + r.message() + ")"));
		}
		tickAttention(mc);
	}

	/** Split from {@link #tick} so the attention keys stay one delimited block. */
	private void tickAttention(Minecraft mc) {
		while (attentionNext.consumeClick()) {
			if (mc.gui.screen() == null) {
				dev.t3craft.client.attention.AttentionClient.openNext(mc);
			}
		}
		while (attentionLast.consumeClick()) {
			if (mc.gui.screen() == null) {
				dev.t3craft.client.attention.AttentionClient.openLast(mc);
			}
		}
	}

	private static void overlay(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(text));
		}
	}
}
