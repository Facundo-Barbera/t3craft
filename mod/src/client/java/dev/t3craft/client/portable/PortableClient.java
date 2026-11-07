package dev.t3craft.client.portable;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.screen.PairScreen;
import dev.t3craft.client.screen.ThreadChatScreen;
import dev.t3craft.portable.PortableItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client wiring of the portable T3 terminal: the configurable key (Options › Controls › T3Craft,
 * default {@code Y}), the T3 Terminal item's client hook, and the fallbacks for the screens owned
 * by feat/chat and feat/config. One call from {@code T3CraftClient.onInitializeClient}.
 */
public final class PortableClient {
	public static final String KEY_NAME = "key.t3craft.portable";
	private static KeyMapping key;

	private PortableClient() {
	}

	public static void register(T3CraftClient client) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, category()));

		// Item use (singleplayer / integrated server) and the key both open the same screen.
		PortableItems.setClientOpen(() -> open(client));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.gui.screen() == null) {
					open(client);
				}
			}
		});

		// Until feat/chat and feat/config are merged: the screens main already has.
		PortableLinks.setFallbacks(
				(env, thread) -> {
					NpcInfo info = client.npcs().info(env + ":" + thread);
					if (info != null) {
						Minecraft.getInstance().gui.setScreen(new ThreadChatScreen(info));
					}
				},
				parent -> Minecraft.getInstance().gui.setScreen(new PairScreen(parent)));
	}

	public static KeyMapping key() {
		return key;
	}

	public static void open(T3CraftClient client) {
		Minecraft.getInstance().gui.setScreen(new PortableScreen(client));
	}

	/**
	 * The T3Craft key category. Shared with other branches that may register keys too: the
	 * category is a record (equal by id), so whoever registers second reuses it. Same approach as
	 * AgentCraft's hud/Keys (MIT, see THIRD_PARTY_NOTICES.md).
	 */
	private static KeyMapping.Category category() {
		Identifier id = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "t3craft");
		try {
			return KeyMapping.Category.register(id);
		} catch (IllegalArgumentException alreadyRegistered) {
			return new KeyMapping.Category(id);
		}
	}
}
