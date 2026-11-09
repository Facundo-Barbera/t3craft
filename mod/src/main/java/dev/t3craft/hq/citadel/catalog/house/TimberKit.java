package dev.t3craft.hq.citadel.catalog.house;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
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
 * Small helpers shared by the {@code house_timber_*} variants: state builders with the sides set by
 * hand, a recessed timber-frame wall, window dressing (sills, shutters, planters) and a gable roof.
 * Everything writes through {@link Plot}, so it rotates and clips like the rest of the catalog.
 */
final class TimberKit {
	private TimberKit() {
	}

	/** A block state for cell {@code (i, y)} along a wall; {@code null} means "leave it". */
	interface Cell {
		BlockState at(int i, int y);
	}

	// ------------------------------------------------------------------ states

	static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	static BlockState stair(Block b, Direction facing, boolean top) {
		BlockState s = CitadelCanvas.stairs(b, facing);
		return top ? s.setValue(BlockStateProperties.HALF, Half.TOP) : s;
	}

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState pane(boolean alongU, DyeColor tint) {
		BlockState g = tint == null ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.STAINED_GLASS_PANE.pick(tint).defaultBlockState();
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	/** A trapdoor. Open: the panel hugs the side opposite to {@code facing}. */
	static BlockState trap(Block b, Direction facing, boolean open, boolean top) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState door(Block b, Direction facing, boolean upper) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	static BlockState fence(Block b, boolean n, boolean e, boolean s, boolean w) {
		return b.defaultBlockState().setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.EAST, e)
				.setValue(BlockStateProperties.SOUTH, s).setValue(BlockStateProperties.WEST, w);
	}

	/** A low wall block with the connected sides set by hand. */
	static BlockState lowWall(Block b, boolean n, boolean e, boolean s, boolean w) {
		return b.defaultBlockState().setValue(BlockStateProperties.UP, true)
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, w ? WallSide.LOW : WallSide.NONE);
	}

	/** Deterministic pick from a weighted list (repeat an entry to weight it). */
	static BlockState mix(Plot p, int u, int y, int v, BlockState... m) {
		return m[p.hash(u, y, v, m.length)];
	}

	// ------------------------------------------------------------------ wall coordinates

	/** Writes to cell {@code i} along a wall whose fixed coordinate is {@code line}. */
	static void put(CitadelCanvas c, Plot p, boolean alongU, int line, int i, int y, BlockState s) {
		if (alongU) {
			p.set(c, i, y, line, s);
		} else {
			p.set(c, line, y, i, s);
		}
	}

	/** Direction pointing out of a wall (local). {@code outward} is -1 (towards low u/v) or +1. */
	static Direction out(boolean alongU, int outward) {
		return alongU ? (outward < 0 ? Direction.NORTH : Direction.SOUTH) : (outward < 0 ? Direction.WEST : Direction.EAST);
	}

	/** A window cell: a one- or two-wide gap between posts is all window, wider gaps keep their ends solid. */
	static boolean gapWin(IntPredicate post, int i, int a, int b) {
		if (post.test(i)) {
			return false;
		}
		int l = i;
		while (l > a && !post.test(l)) {
			l--;
		}
		int r = i;
		while (r < b && !post.test(r)) {
			r++;
		}
		int g = r - l - 1;
		int pos = i - l - 1;
		return g <= 2 || (pos >= 1 && pos <= g - 2);
	}

	/**
	 * [depth] A timber-frame wall: posts flush on the frame line {@code fixed}, the infill and the glass
	 * set one block back, so every bay is a recess. Non-post cells may carry a {@code rail} on the frame
	 * line (a stone footing) and window cells get panes in rows {@code w1..w2}.
	 */
	static void wall(CitadelCanvas c, Plot p, boolean alongU, int fixed, int outward, int a, int b, int y1, int y2,
			IntPredicate post, IntPredicate win, int w1, int w2, IntFunction<DyeColor> tint,
			Cell postCell, Cell rail, Cell infill) {
		int in = fixed - outward;
		for (int i = a; i <= b; i++) {
			boolean isPost = post.test(i);
			boolean isWin = !isPost && win != null && win.test(i);
			for (int y = y1; y <= y2; y++) {
				if (isPost) {
					put(c, p, alongU, fixed, i, y, postCell.at(i, y));
					continue;
				}
				if (rail != null) {
					BlockState rs = rail.at(i, y);
					if (rs != null) {
						put(c, p, alongU, fixed, i, y, rs);
					}
				}
				if (isWin && y >= w1 && y <= w2) {
					put(c, p, alongU, in, i, y, pane(alongU, tint == null ? null : tint.apply(i)));
				} else if (infill != null) {
					BlockState s = infill.at(i, y);
					if (s != null) {
						put(c, p, alongU, in, i, y, s);
					}
				}
			}
		}
	}

	/**
	 * [depth] Window dressing: a sill (upside-down stair) sticking out under every window, open
	 * trapdoor shutters at the two ends of a run of two or more windows and, if asked, a planter
	 * (flowering bush) on the sill.
	 */
	static void dress(CitadelCanvas c, Plot p, boolean alongU, int fixed, int outward, int a, int b,
			IntPredicate post, IntPredicate win, int w1, int w2, Block sill, Block shutter, boolean planters) {
		Direction outD = out(alongU, outward);
		Direction lowFace = alongU ? Direction.EAST : Direction.SOUTH;
		Direction highFace = alongU ? Direction.WEST : Direction.NORTH;
		for (int i = a; i <= b; i++) {
			if (post.test(i) || !win.test(i)) {
				continue;
			}
			boolean left = i > a && !post.test(i - 1) && win.test(i - 1);
			boolean right = i < b && !post.test(i + 1) && win.test(i + 1);
			int line = fixed + outward;
			if (sill != null) {
				put(c, p, alongU, line, i, w1 - 1, stair(sill, outD.getOpposite(), true));
			}
			if (planters) {
				int u = alongU ? i : line;
				int v = alongU ? line : i;
				put(c, p, alongU, line, i, w1, planter(p, u, w1, v));
			}
			if (shutter != null && left != right) {
				for (int y = w1; y <= w2; y++) {
					put(c, p, alongU, fixed, i, y, trap(shutter, left ? highFace : lowFace, true, false));
				}
			}
		}
	}

	/** A flowering window box: azalea bushes and pots, by hash. */
	static BlockState planter(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 4)) {
			case 0 -> CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
			case 1 -> Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState();
			case 2 -> CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
			default -> Blocks.POTTED_CHERRY_SAPLING.defaultBlockState();
		};
	}

	/** A garden plant for a ground cell, or {@code null} for bare ground. */
	static BlockState groundPlant(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 9)) {
			case 0 -> Blocks.FLOWERING_AZALEA.defaultBlockState();
			case 1 -> Blocks.AZALEA.defaultBlockState();
			case 2 -> Blocks.FERN.defaultBlockState();
			case 3 -> Blocks.PINK_PETALS.defaultBlockState();
			case 4 -> Blocks.CHERRY_SAPLING.defaultBlockState();
			default -> null;
		};
	}

	// ------------------------------------------------------------------ roof

	/** Roof surface height at cross row {@code t} of a gable whose eave rows are {@code t0} and {@code t1}. */
	static int gableY(int t, int t0, int t1, int y) {
		return y + Math.min(t - t0, t1 - t);
	}

	/**
	 * [finish] A gable roof. The ridge runs along u ({@code ridgeU}) or along v; {@code s0..s1} is the
	 * ridge-axis range (the overhang at the ends is part of it) and {@code t0..t1} the cross range,
	 * whose first and last rows are the eaves, drawn in {@code eave}. The middle row(s) are a full ridge
	 * block with a slab cap. {@code alt} (nullable) is a second roof material scattered over the slope.
	 * {@code rakeStart}/{@code rakeEnd} close the underside of an overhanging end.
	 */
	static void gable(CitadelCanvas c, Plot p, boolean ridgeU, int s0, int s1, int t0, int t1, int y,
			Block roof, Block eave, Block ridge, Block ridgeSlab, Block alt, boolean rakeStart, boolean rakeEnd) {
		int n = t1 - t0 + 1;
		int mid0 = t0 + (n - 1) / 2;
		int mid1 = t0 + n / 2;
		Direction up = ridgeU ? Direction.SOUTH : Direction.EAST;
		for (int t = t0; t <= t1; t++) {
			int h = gableY(t, t0, t1, y);
			boolean isRidge = t >= mid0 && t <= mid1;
			boolean edge = t == t0 || t == t1;
			Direction face = t < mid0 ? up : up.getOpposite();
			for (int s = s0; s <= s1; s++) {
				// [palette] a scatter of {@code alt} tiles breaks the flat plane of the slope
				Block m = edge ? eave : (alt != null && p.hash(s, h, t, 5) == 0 ? alt : roof);
				BlockState st = isRidge ? ridge.defaultBlockState() : stair(m, face, false);
				putS(c, p, ridgeU, s, t, h, st);
				if (isRidge) {
					putS(c, p, ridgeU, s, t, h + 1, slab(ridgeSlab, false));
				} else if (h - 1 >= y && ((s == s0 && rakeStart) || (s == s1 && rakeEnd))) {
					putS(c, p, ridgeU, s, t, h - 1, stair(roof, face.getOpposite(), true));
				}
			}
		}
	}

	/** {@link #gable} with a single roof material (no {@code alt} scatter). */
	static void gable(CitadelCanvas c, Plot p, boolean ridgeU, int s0, int s1, int t0, int t1, int y,
			Block roof, Block eave, Block ridge, Block ridgeSlab, boolean rakeStart, boolean rakeEnd) {
		gable(c, p, ridgeU, s0, s1, t0, t1, y, roof, eave, ridge, ridgeSlab, null, rakeStart, rakeEnd);
	}

	static void putS(CitadelCanvas c, Plot p, boolean ridgeU, int s, int t, int y, BlockState st) {
		if (ridgeU) {
			p.set(c, s, y, t, st);
		} else {
			p.set(c, t, y, s, st);
		}
	}
}
