package dev.t3craft.hq.citadel.catalog;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A walkable stair between two floors of a catalog house, found and cut after the house is built (walkability
 * audit: most multi-storey houses had an upper floor with no way up). It looks for a straight flight inside the
 * walls, one step per cell, that
 * <ul>
 * <li>starts head-on from a free cell of the lower floor (the first step is met from the front, never from a
 *     corner against a wall) and ends on a free cell of the upper floor that the top step walks off onto;</li>
 * <li>has nothing on the cells it uses, so no furniture is ever cut: the air between the steps and the floor
 *     above is free, and the upper floor has 3 free blocks over every cell of the run;</li>
 * <li>cuts the floor above over the run (the top step takes the floor's own layer), and puts a fence round the
 *     hole on the upper floor, except where the flight is walked off.</li>
 * </ul>
 * Among the valid sites the one with a free cell beside every step on one side only wins (the flight hugs a wall and
 * leaves room to pass, rather than standing in the middle of the room or in a 1-wide slot); ties go to the first
 * found, so the result is deterministic. Nothing is built
 * when no site is valid. All coordinates are local ({@link Plot}); {@code lower} and {@code upper} are the local
 * layers a person stands in on each floor (the floor itself is the layer under).
 */
public final class InteriorStair {
	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private InteriorStair() {
	}

	private static boolean air(CitadelCanvas c, Plot p, int u, int y, int v) {
		if (!p.has(u, y, v)) {
			return false;
		}
		BlockState s = p.at(c, u, y, v);
		return s == null || s.isAir();
	}

	private static boolean solid(CitadelCanvas c, Plot p, int u, int y, int v) {
		if (!p.has(u, y, v)) {
			return false;
		}
		BlockState s = p.at(c, u, y, v);
		return s != null && !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** A cell a person can stand in with 3 free blocks over it, on a solid floor. */
	private static boolean room(CitadelCanvas c, Plot p, int u, int y, int v) {
		return solid(c, p, u, y - 1, v) && air(c, p, u, y, v) && air(c, p, u, y + 1, v) && air(c, p, u, y + 2, v);
	}

	/** Score of a flight starting at {@code (u, v)} in direction {@code d}, or -1 when it is not valid. */
	private static int score(CitadelCanvas c, Plot p, int lower, int upper, int u, int v, int d) {
		int rise = upper - lower;
		int du = DIRS[d][0];
		int dv = DIRS[d][1];
		int side = 0;
		for (int j = 0; j < rise; j++) {
			int cu = u + j * du;
			int cv = v + j * dv;
			if (!solid(c, p, cu, upper - 1, cv) || !solid(c, p, cu, lower - 1, cv)) {
				return -1;
			}
			for (int y = lower; y <= upper - 2; y++) {
				if (!air(c, p, cu, y, cv)) {
					return -1;
				}
			}
			for (int y = upper; y <= upper + 2; y++) {
				if (!air(c, p, cu, y, cv)) {
					return -1;
				}
			}
			// free cells beside the flight at the lower floor: room to pass
			for (int s = -1; s <= 1; s += 2) {
				int lu = cu - dv * s;
				int lv = cv + du * s;
				if (room(c, p, lu, lower, lv)) {
					side++;
				}
			}
		}
		// head-on approach on the lower floor, and the cell the top step walks off onto
		if (!room(c, p, u - du, lower, v - dv) || !room(c, p, u + rise * du, upper, v + rise * dv)) {
			return -1;
		}
		// best: one side free all along (a wall on the other), so the flight hugs a wall and leaves room to pass
		return 1 + 2 * rise - Math.abs(side - rise);
	}

	/**
	 * Cuts a flight from {@code lower} to {@code upper} with {@code step} stairs on {@code core} blocks and a
	 * {@code fence} round the hole.
	 *
	 * @return whether a site was found and built
	 */
	public static boolean add(CitadelCanvas c, Plot p, int lower, int upper, Block step, BlockState core, Block fence) {
		int rise = upper - lower;
		if (rise < 3) {
			return false;
		}
		int best = -1;
		int bu = 0;
		int bv = 0;
		int bd = 0;
		for (int d = 0; d < 4; d++) {
			for (int v = 0; v < p.d(); v++) {
				for (int u = 0; u < p.w(); u++) {
					int sc = score(c, p, lower, upper, u, v, d);
					if (sc > best) {
						best = sc;
						bu = u;
						bv = v;
						bd = d;
					}
				}
			}
		}
		if (best < 0) {
			return false;
		}
		int du = DIRS[bd][0];
		int dv = DIRS[bd][1];
		for (int j = 0; j < rise; j++) {
			int cu = bu + j * du;
			int cv = bv + j * dv;
			for (int y = lower; y < lower + j; y++) {
				p.set(c, cu, y, cv, core);
			}
			p.set(c, cu, lower + j, cv, stairs(step, FACING[bd]));
			if (j < rise - 1) {
				p.set(c, cu, upper - 1, cv, AIR); // the hole over the flight (the top step is the floor's own layer)
			}
		}
		// the fence round the hole on the upper floor: both sides of the flight and the end behind its first step
		Set<Integer> rail = new HashSet<>();
		for (int j = 0; j < rise - 1; j++) {
			int cu = bu + j * du;
			int cv = bv + j * dv;
			for (int s = -1; s <= 1; s += 2) {
				addRail(c, p, rail, upper, cu - dv * s, cv + du * s);
			}
		}
		addRail(c, p, rail, upper, bu - du, bv - dv);
		for (int key : rail) {
			int ru = key >> 8;
			int rv = key & 255;
			p.set(c, ru, upper, rv, fence.defaultBlockState()
					.setValue(BlockStateProperties.NORTH, rail.contains(ru << 8 | (rv - 1)))
					.setValue(BlockStateProperties.EAST, rail.contains((ru + 1) << 8 | rv))
					.setValue(BlockStateProperties.SOUTH, rail.contains(ru << 8 | (rv + 1)))
					.setValue(BlockStateProperties.WEST, rail.contains((ru - 1) << 8 | rv)));
		}
		return true;
	}

	/** A timber flight (spruce) for the timber-framed houses. */
	public static boolean timber(CitadelCanvas c, Plot p, int lower, int upper) {
		return add(c, p, lower, upper, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE);
	}

	/** A stone-brick flight with a dark oak rail for the masonry houses. */
	public static boolean stone(CitadelCanvas c, Plot p, int lower, int upper) {
		return add(c, p, lower, upper, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DARK_OAK_FENCE);
	}

	private static void addRail(CitadelCanvas c, Plot p, Set<Integer> rail, int upper, int u, int v) {
		if (u >= 0 && v >= 0 && u < 256 && v < 256 && solid(c, p, u, upper - 1, v) && air(c, p, u, upper, v)) {
			rail.add(u << 8 | v);
		}
	}
}
