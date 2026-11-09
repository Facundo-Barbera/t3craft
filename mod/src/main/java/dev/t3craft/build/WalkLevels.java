package dev.t3craft.build;

import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * fix/build-walkable-level: the player's height decides what is built next. Shared rules of
 * {@link RadialCursor} and {@link GroundPrimer}:
 * <ul>
 *   <li>a <b>walkable cell</b> is a plan block a player can stand on (it has a collision shape: full cubes,
 *   slabs, stairs, planks, carpets) with two passable cells over it in the final plan (absent, air, or no
 *   collision: torches, flowers, signs);</li>
 *   <li>a <b>floor</b> is a walkable cell inside a 2 × 2 square of walkable cells at the same height, so the
 *   top of a one-thick wall or of a fence line is never taken for a floor;</li>
 *   <li>the <b>level</b> is the player's feet cell (plan-relative y). A column's surface is its floor whose
 *   feet height ({@code y + 1}) is nearest the level, within {@link #PICK_REACH}; on a tie the higher one.
 *   The column's own terrain top (when walkable) is always a candidate, floor or not.</li>
 *   <li>the <b>band</b> is what is "at the player's height": heads from {@link #BAND_DOWN} under the floor the
 *   player stands on to {@link #BAND_UP} over its feet (railings, walls at head height, a 3-high ceiling,
 *   stairs going up or down a flight). Builders work the band first; work far under or over it later.</li>
 * </ul>
 * Pure. The shape checks run off-thread only (plan analysis, primer), cached per state.
 */
final class WalkLevels {
	/** No level known (nobody in the build yet): every column keeps its terrain surface, every head is in band. */
	static final int NO_LEVEL = Integer.MIN_VALUE;
	/** A floor counts as a column's surface when the feet on it are at most this far from the player's feet. */
	static final int PICK_REACH = 3;
	/** Band: heads down to this many blocks under the floor the player stands on. */
	static final int BAND_DOWN = 3;
	/** Band: heads up to this many blocks over the player's feet (head height is +1, a 3-high ceiling +3). */
	static final int BAND_UP = 3;
	/** The level follows the feet once they moved this far (blocks): the vertical twin of {@link RadialCursor#MOVE}. */
	static final int LEVEL_MOVE = 2;

	private WalkLevels() {
	}

	/** Shape answers per block state (bit flags: {@code 1} standable, {@code 2} passable, {@code 4} full cube); one per analysis thread. */
	static final class Shapes {
		private final IdentityHashMap<BlockState, Integer> cache = new IdentityHashMap<>();

		private int flags(BlockState s) {
			Integer f = cache.get(s);
			if (f == null) {
				boolean empty = s.isAir() || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
				int v = empty ? 2 : 1;
				if (!empty && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					v |= 4;
				}
				f = v;
				cache.put(s, f);
			}
			return f;
		}

		/** Its collision shape is the full cube (planks, stone; not slabs, carpets, stairs). */
		boolean full(BlockState s) {
			return (flags(s) & 4) != 0;
		}

		/** A player can stand on it (it has a collision shape). */
		boolean standable(BlockState s) {
			return (flags(s) & 1) != 0;
		}

		/** A player's body goes through it (air, or no collision shape). */
		boolean passable(BlockState s) {
			return (flags(s) & 2) != 0;
		}
	}

	/** Feet cell of a player at plan-relative height {@code y} (the cell over the block it stands on, slabs included). */
	static int feet(double y) {
		return (int) Math.floor(y - 0.01) + 1;
	}

	/** Whether a surface at {@code sy} is nearer the {@code level} than {@code bestY} (tie: higher); {@code bestY} NO_LEVEL = none yet. */
	static boolean nearer(int sy, int bestY, int level) {
		int d = Math.abs(sy + 1 - level);
		if (d > PICK_REACH) {
			return false;
		}
		if (bestY == NO_LEVEL) {
			return true;
		}
		int bd = Math.abs(bestY + 1 - level);
		return d < bd || d == bd && sy > bestY;
	}

	/** Whether a head at {@code hy} is at the player's height ({@code level}); always true with no level. */
	static boolean inBand(int hy, int level) {
		return level == NO_LEVEL || hy >= level - 1 - BAND_DOWN && hy <= level + BAND_UP;
	}
}
