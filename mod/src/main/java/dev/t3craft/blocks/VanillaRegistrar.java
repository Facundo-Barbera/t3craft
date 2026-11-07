package dev.t3craft.blocks;

import dev.t3craft.T3CraftTab;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * {@link BlockRegistrar} backed by the built-in registries. Items go to the mod's single
 * {@link T3CraftTab} (contributed only when the module is on).
 */
final class VanillaRegistrar implements BlockRegistrar {
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
		T3CraftTab.contribute(items);
	}
}
