package dev.t3craft;

import dev.t3craft.portable.PortableItems;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The single "T3Craft" creative tab ({@code t3craft:t3craft}, title {@code itemGroup.t3craft}, icon
 * the T3 Terminal). Every module adds its own items from its own registration code through
 * {@link #contribute}; a module that is off (e.g. blocks with {@code blocks.enabled=false}) never
 * calls it, so its items do not appear. Entries are read when the tab is built, so contributing
 * after {@link #register} is fine. An empty tab is hidden by vanilla.
 */
public final class T3CraftTab {
	public static final ResourceKey<CreativeModeTab> KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(T3Craft.MOD_ID, T3Craft.MOD_ID));
	public static final String TITLE_KEY = "itemGroup." + T3Craft.MOD_ID;

	private static final TabContents<Item> CONTENTS = new TabContents<>();
	private static volatile boolean registered;

	private T3CraftTab() {
	}

	/** Adds a module's items (in order) to the tab. */
	public static void contribute(List<? extends Item> items) {
		CONTENTS.add(items);
	}

	/** Lazy variant: {@code items} is asked each time the tab is (re)built. */
	public static void contribute(Supplier<? extends List<? extends Item>> items) {
		CONTENTS.add(items);
	}

	public static List<Item> entries() {
		return CONTENTS.entries();
	}

	/** Registers the tab once (common entrypoint). */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable(TITLE_KEY))
				.icon(T3CraftTab::icon)
				.displayItems((params, output) -> entries().forEach(output::accept))
				.build());
	}

	private static ItemStack icon() {
		Item terminal = PortableItems.terminal();
		if (terminal != null) {
			return new ItemStack(terminal);
		}
		List<Item> all = entries();
		return new ItemStack(all.isEmpty() ? Items.COMMAND_BLOCK : all.getFirst());
	}
}
