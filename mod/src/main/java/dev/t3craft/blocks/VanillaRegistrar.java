package dev.t3craft.blocks;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * {@link BlockRegistrar} backed by the built-in registries. Items go to the shared vanilla
 * "Functional blocks" tab (no own tab: one registry entry less to sync).
 */
final class VanillaRegistrar implements BlockRegistrar {
	static final ResourceKey<CreativeModeTab> TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "functional_blocks"));

	@Override
	public Block block(Identifier id, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, id, block);
	}

	@Override
	public Item item(Identifier id, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, id, item);
	}

	@Override
	public <T extends BlockEntity> BlockEntityType<T> blockEntity(Identifier id, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
	}

	@Override
	public void creativeTab(List<Item> items) {
		List<Item> copy = List.copyOf(items);
		CreativeModeTabEvents.modifyOutputEvent(TAB).register(output -> copy.forEach(output::accept));
	}
}
