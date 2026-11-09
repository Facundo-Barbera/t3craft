package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Block-state recipes and small props shared by the Japanese house variants ({@code HouseMachiya},
 * {@code HouseJapaneseEngawa}, {@code HouseJapaneseTeahouse}). All directions are local (NORTH = front).
 */
final class JapaneseKit {
	private JapaneseKit() {
	}

	// ------------------------------------------------------------------ states

	static BlockState stair(Block b, Direction facing, Half half, StairsShape shape) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, half)
				.setValue(BlockStateProperties.STAIRS_SHAPE, shape);
	}

	static BlockState stair(Block b, Direction facing) {
		return stair(b, facing, Half.BOTTOM, StairsShape.STRAIGHT);
	}

	static BlockState stairTop(Block b, Direction facing) {
		return stair(b, facing, Half.TOP, StairsShape.STRAIGHT);
	}

	static BlockState slabTop(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** An open trapdoor lies flat against the cell side opposite to {@code facing}; a closed one is a floor or ceiling plate. */
	static BlockState trapdoor(Block b, Direction facing, boolean open, Half half) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.OPEN, open)
				.setValue(BlockStateProperties.HALF, half);
	}

	static BlockState fence(Block b, boolean north, boolean east, boolean south, boolean west) {
		return b.defaultBlockState().setValue(BlockStateProperties.NORTH, north).setValue(BlockStateProperties.EAST, east)
				.setValue(BlockStateProperties.SOUTH, south).setValue(BlockStateProperties.WEST, west);
	}

	static BlockState pane(BlockState base, boolean north, boolean east, boolean south, boolean west) {
		return base.setValue(BlockStateProperties.NORTH, north).setValue(BlockStateProperties.EAST, east)
				.setValue(BlockStateProperties.SOUTH, south).setValue(BlockStateProperties.WEST, west);
	}

	static BlockState paneU(BlockState base) {
		return pane(base, false, true, false, true);
	}

	static BlockState paneV(BlockState base) {
		return pane(base, true, false, true, false);
	}

	static BlockState wall(Block b, boolean up, boolean north, boolean east, boolean south, boolean west) {
		return b.defaultBlockState().setValue(BlockStateProperties.UP, up)
				.setValue(BlockStateProperties.NORTH_WALL, north ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, east ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, south ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, west ? WallSide.LOW : WallSide.NONE);
	}

	static BlockState lantern(boolean hanging) {
		return b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, hanging);
	}

	static BlockState glassPane(net.minecraft.world.item.DyeColor color) {
		return Blocks.STAINED_GLASS_PANE.pick(color).defaultBlockState();
	}

	// ------------------------------------------------------------------ props

	/** An ishidoro: a short stone column, a firebox with a lantern in it and a slab for a roof. Needs 4 layers. */
	static void stoneLantern(CitadelCanvas c, Plot p, int u, int y, int v) {
		p.set(c, u, y, v, b(Blocks.POLISHED_ANDESITE));
		p.set(c, u, y + 1, v, wall(Blocks.STONE_BRICK_WALL, true, false, false, false, false));
		p.set(c, u, y + 2, v, lantern(false));
		p.set(c, u, y + 3, v, slab(Blocks.STONE_BRICK_SLAB));
	}

	/**
	 * A small weeping sakura: a bent cherry trunk, a flat layered crown of cherry leaves with deterministic
	 * gaps, hanging blossom below and petals on the ground. {@code size} 0 = 3x3 crown, 1 = 5x5.
	 */
	static void sakura(CitadelCanvas c, Plot p, int u, int y, int v, int size) {
		int trunk = 3 + size;
		for (int i = 0; i < trunk; i++) {
			p.set(c, u, y + i, v, b(Blocks.CHERRY_LOG));
		}
		int lean = p.hash(u, y, v, 2) == 0 ? 1 : -1;
		p.set(c, u + lean, y + trunk - 1, v, log(Blocks.CHERRY_LOG, Direction.Axis.X));
		int cr = 1 + size;
		int top = y + trunk;
		java.util.List<int[]> crown = new java.util.ArrayList<>();
		for (int dy = 0; dy <= 2; dy++) {
			int rad = dy == 2 ? cr - 1 : cr;
			for (int du = -rad - 1; du <= rad + 1; du++) {
				for (int dv = -rad - 1; dv <= rad + 1; dv++) {
					int dist = Math.abs(du) + Math.abs(dv);
					if (dist > rad + 1 + (dy == 0 ? 1 : 0) || (dy == 2 && dist > rad)) {
						continue;
					}
					int cu = u + du;
					int cv = v + dv;
					if (du == 0 && dv == 0 && dy < 1) {
						continue;
					}
					if (p.hash(cu, top + dy, cv, 5) == 0) {
						continue;
					}
					p.set(c, cu, top - 1 + dy, cv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
					crown.add(new int[] {cu, top - 1 + dy, cv});
				}
			}
		}
		// fix/settle-sources: the gaps leave some crown leaves touching the rest only on a corner; those go (a leaf alone in the air is
		// no blossom), twice so a leaf left alone by the first pass goes too
		for (int pass = 0; pass < 2; pass++) {
			for (int[] q : crown) {
				BlockState here = p.at(c, q[0], q[1], q[2]);
				if (here == null || here.isAir()) {
					continue;
				}
				boolean held = false;
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					if (Support.present(c, p, q[0] + d[0], q[1] + d[1], q[2] + d[2])) {
						held = true;
						break;
					}
				}
				if (!held) {
					p.set(c, q[0], q[1], q[2], CitadelCanvas.AIR);
				}
			}
		}
		// hanging blossom under the rim of the crown
		for (int du = -cr; du <= cr; du += 2) {
			for (int dv = -cr; dv <= cr; dv += 2) {
				// fix/settle-sources: the blossom hangs from a leaf of the crown, never from a gap in it
				if ((Math.abs(du) == cr || Math.abs(dv) == cr) && p.hash(u + du, top, v + dv, 3) != 0 && Support.present(c, p, u + du, top - 1, v + dv)) {
					p.set(c, u + du, top - 2, v + dv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		// petals on the ground around the foot
		for (int du = -cr; du <= cr; du++) {
			for (int dv = -cr; dv <= cr; dv++) {
				if ((du != 0 || dv != 0) && p.hash(u + du, y, v + dv, 3) == 0) {
					p.set(c, u + du, y, v + dv, b(Blocks.PINK_PETALS));
				}
			}
		}
	}
}
