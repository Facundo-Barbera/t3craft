package dev.t3craft.build;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/** What one placed block state costs in survival, as an item id. */
public final class BlockCosts {
	private BlockCosts() {
	}

	/**
	 * The item consumed to place {@code state}, or null when free: air (clearing), the upper half of
	 * two-block parts (doors, tall plants) and the head of a bed (the other half pays), and blocks
	 * without an item (fire, water...). {@code wall_torch} maps to {@code torch} through
	 * {@code asItem()}.
	 */
	public static @Nullable String itemId(BlockState state) {
		if (state.isAir() || state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
			return null; // the invisible light block has no survival item: free
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return null;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return null;
		}
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			return null;
		}
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** {@link #itemId} of every entry of {@code plan}, by index. */
	public static @Nullable String[] of(BuildPlan plan, java.util.function.Function<BlockState, @Nullable String> itemOf) {
		String[] out = new String[plan.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = itemOf.apply(plan.get(i).state());
		}
		return out;
	}
}
