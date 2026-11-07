package dev.t3craft.portable;

import dev.t3craft.T3Craft;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The "T3 Terminal" portable item ({@code t3craft:t3_terminal}). Using it opens the portable
 * terminal screen on the client; it never touches the world and needs no block or base.
 * <p>Registered from the common entrypoint so the integrated (singleplayer) server knows it and its
 * cheap recipe ({@code data/t3craft/recipe/t3_terminal.json}) works in survival. T3Craft is
 * {@code environment: client}, so dedicated servers never load it: on multiplayer the
 * configurable key opens the same screen. {@code -Dt3craft.portable.item=false} skips the item
 * entirely (the key still works).
 */
public final class PortableItems {
	public static final Identifier TERMINAL_ID = Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, "t3_terminal");
	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "tools_and_utilities"));

	/** Set by the client entrypoint; runs on the client thread. Null on a server-only classpath. */
	private static volatile @Nullable Runnable clientOpen;
	private static @Nullable Item terminal;

	private PortableItems() {
	}

	public static void register() {
		if ("false".equalsIgnoreCase(System.getProperty("t3craft.portable.item"))) {
			T3Craft.LOGGER.info("T3 Terminal item disabled (-Dt3craft.portable.item=false)");
			return;
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TERMINAL_ID);
		terminal = Registry.register(BuiltInRegistries.ITEM, key,
				new TerminalItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		Item item = terminal;
		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> output.accept(item));
	}

	public static @Nullable Item terminal() {
		return terminal;
	}

	public static void setClientOpen(@Nullable Runnable open) {
		clientOpen = open;
	}

	/** Opens the terminal client-side only; the server side just passes (nothing to sync). */
	static final class TerminalItem extends Item {
		TerminalItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			Runnable open = clientOpen;
			if (open == null) {
				return InteractionResult.PASS;
			}
			open.run();
			return InteractionResult.SUCCESS;
		}
	}
}
