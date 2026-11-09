package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Block-state factories shared by the workshop houses ({@code HouseScriptorium}, {@code HouseForgeAtelier},
 * {@code HouseLibraryCopper}). All directions are local (NORTH = towards the front): the plot rotates them.
 */
final class HouseWorkshopKit {
	private HouseWorkshopKit() {
	}

	/** A stair; {@code top} flips it upside down (corbels, cornices, brackets). */
	static BlockState stair(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** A glass pane running along u (east-west) or along v (north-south). */
	static BlockState pane(Block pane, boolean alongU) {
		BlockState s = pane.defaultBlockState();
		return alongU ? s.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: s.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	static BlockState clearPane(boolean alongU) {
		return pane(Blocks.GLASS_PANE, alongU);
	}

	static BlockState tintPane(DyeColor c, boolean alongU) {
		return pane(Blocks.STAINED_GLASS_PANE.pick(c), alongU);
	}

	static BlockState tintBlock(DyeColor c) {
		return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
	}

	static BlockState lantern(boolean hanging) {
		return b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, hanging);
	}

	/** A wall block with its four sides set (a wall with none draws as a lone post). */
	static BlockState wall(Block b, boolean n, boolean e, boolean s, boolean w, boolean up) {
		return b.defaultBlockState()
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, w ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.UP, up);
	}

	/** A fence with its four sides set. */
	static BlockState fence(Block b, boolean n, boolean e, boolean s, boolean w) {
		return b.defaultBlockState().setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.EAST, e)
				.setValue(BlockStateProperties.SOUTH, s).setValue(BlockStateProperties.WEST, w);
	}

	static BlockState trap(Block b, Direction facing, boolean open, boolean top) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState door(Block b, Direction facing, boolean upper) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	static BlockState facing(Block b, Direction d) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}
}
