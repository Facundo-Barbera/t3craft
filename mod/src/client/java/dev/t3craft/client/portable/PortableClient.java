package dev.t3craft.client.portable;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.chat.ChatScreens;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentsScreen;
import dev.t3craft.client.config.T3CraftKeys;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.portable.held.HeldScreen;
import dev.t3craft.portable.PortableItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client wiring of the portable T3 terminal: the configurable key (Options › Controls › T3Craft,
 * default {@code Y}, in the shared {@link T3CraftKeys#CATEGORY}), the T3 Terminal item's client
 * hook, the live held-item screen ({@link HeldScreen}) and the fallbacks for the screens owned by
 * feat/chat and feat/config. One call from {@code T3CraftClient.onInitializeClient}.
 */
public final class PortableClient {
	public static final String KEY_NAME = "key.t3craft.portable";
	private static KeyMapping key;

	private PortableClient() {
	}

	public static void register(T3CraftClient client) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, T3CraftKeys.CATEGORY));

		// Item use (singleplayer / integrated server) and the key both open the same screen.
		PortableItems.setClientOpen(() -> open(client));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.gui.screen() == null) {
					open(client);
				}
			}
		});

		// Live screen on the held item (map-style); off with portable.heldScreen=false.
		HeldScreen.register(client);

		// Until feat/chat and feat/config are merged: the screens main already has.
		PortableLinks.setFallbacks(
				(env, thread) -> {
					// the chat browser, like setChat below (was the ThreadChatScreen placeholder)
					NpcInfo info = client.npcs().info(env + ":" + thread);
					if (info != null) {
						ChatScreens.openFor(info);
					}
				},
				// The environment list (pair new, show/hide, edit, unpair), back to the terminal on
				// Done. It used to be the bare pairing screen, so "Environments…" never listed them.
				parent -> Minecraft.getInstance().gui.setScreen(new EnvironmentsScreen(parent, EnvironmentActions.DEFAULT)));
		// Chat from the terminal: the browser, with Esc back to the terminal (it used to close to the game).
		PortableLinks.setChat((env, thread) -> {
			Minecraft mc = Minecraft.getInstance();
			Screen back = mc.gui.screen();
			mc.gui.setScreen(ChatScreens.create(env.isEmpty() ? thread : env + ":" + thread).withParent(back instanceof dev.t3craft.client.t3ui.T3Screen ? null : back));
		});
	}

	public static KeyMapping key() {
		return key;
	}

	/** The T3 key and the item: the unified T3 screen (feat/t3-ui-unified), most recent thread open. */
	public static void open(T3CraftClient client) {
		Minecraft.getInstance().gui.setScreen(ChatScreens.create(null));
	}
}
