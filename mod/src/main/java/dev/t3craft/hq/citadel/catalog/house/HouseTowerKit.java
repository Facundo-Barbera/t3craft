package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Small helpers shared by the tower-house variants: writes that never clip (they check the box
 * first), and block-state factories with the connections set by hand. All directions are local
 * (NORTH = towards the front).
 */
final class HouseTowerKit {
	private HouseTowerKit() {
	}

	/** Solid write that silently skips cells outside the box (so a tall finial never counts as clipped). */
	static void set(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}

	static void put(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s, int layer) {
		if (p.has(u, y, v)) {
			p.put(c, u, y, v, s, layer);
		}
	}

	static void fill(CitadelCanvas c, Plot p, int u1, int y1, int v1, int u2, int y2, int v2, BlockState s) {
		for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
				for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
					set(c, p, u, y, v, s);
				}
			}
		}
	}

	static BlockState stair(Block block, Direction facing, boolean upsideDown) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block block, boolean top) {
		return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState log(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	static BlockState fence(Block block, boolean n, boolean e, boolean s, boolean w) {
		return block.defaultBlockState().setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.EAST, e)
				.setValue(BlockStateProperties.SOUTH, s).setValue(BlockStateProperties.WEST, w);
	}

	static BlockState wall(Block block, boolean n, boolean e, boolean s, boolean w, boolean up) {
		return block.defaultBlockState().setValue(BlockStateProperties.UP, up)
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, w ? WallSide.LOW : WallSide.NONE);
	}

	static BlockState pane(Block block, boolean alongU) {
		BlockState g = block.defaultBlockState();
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	static BlockState trapdoor(Block block, Direction facing, boolean open, boolean top) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState hangingLantern() {
		return b(net.minecraft.world.level.block.Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState lantern() {
		return b(net.minecraft.world.level.block.Blocks.LANTERN);
	}
}
