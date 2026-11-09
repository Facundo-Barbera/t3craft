package dev.t3craft.hq;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@link HqKit#VANILLA}: the cherry campus (style C) in vanilla blocks, used when the blocks module
 * is off. States are built per call (no static caches: safe across hot reloads). Connections never
 * depend on block tags (railing = waxed copper bars, as in {@link Palette}).
 */
final class VanillaHqKit implements HqKit {
	@Override
	public BlockState state(Role role, Direction facing) {
		Direction h = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
		return switch (role) {
			case DESK -> Blocks.CHERRY_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			case WALL_PANEL -> Blocks.CALCITE.defaultBlockState();
			case WALL_PANEL_LIT -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
			case FLOOR -> Blocks.CHERRY_PLANKS.defaultBlockState();
			case FLOOR_ACCENT -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			case BEAM -> Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, facing.getAxis());
			case BEAM_CAP -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
			case LANTERN -> Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.HANGING, false);
			case PLAQUE -> Blocks.CHERRY_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, h);
			case RAILING -> Blocks.COPPER_BARS.waxed().unaffected().defaultBlockState();
			case GARDEN_PLANTER -> Blocks.POTTED_CHERRY_SAPLING.defaultBlockState();
			case GARDEN_LANTERN -> Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
			case CONSOLE -> Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, h);
			case MERGE_STATION -> Blocks.SMITHING_TABLE.defaultBlockState();
		};
	}

	@Override
	public CompoundTag plaqueData(DyeColor accent, String... lines) {
		return SignTags.sign(accent, true, lines);
	}

	@Override
	public boolean custom() {
		return false;
	}

	@Override
	public String id() {
		return "vanilla";
	}
}
