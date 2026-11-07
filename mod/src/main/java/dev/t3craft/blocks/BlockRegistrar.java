package dev.t3craft.blocks;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Where {@link T3Blocks} puts what it creates. The real one ({@link #VANILLA}) writes to
 * {@code BuiltInRegistries} and the creative tab; tests pass a recording one to check that a disabled
 * module touches nothing. Every registration of the module goes through here: nothing else in the
 * mod registers blocks, items or block entity types.
 */
public interface BlockRegistrar {
	Block block(Identifier id, Block block);

	Item item(Identifier id, Item item);

	<T extends BlockEntity> BlockEntityType<T> blockEntity(Identifier id, BlockEntityType<T> type);

	/** Adds the module's items (in order) to the creative inventory. */
	void creativeTab(List<Item> items);

	BlockRegistrar VANILLA = new VanillaRegistrar();
}
