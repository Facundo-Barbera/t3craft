package dev.t3craft.client.npc.behavior;

/**
 * The ground as seen by the {@link GridPathfinder}: for a block cell, the height an NPC's feet
 * would be at when standing in it, or {@code NaN} when it cannot stand there (solid feet block, no
 * headroom, nothing below). Implementations: {@link Collision} (real block collision shapes, over
 * the client level or a fake cell world) and {@link #flat} (tests, or no level).
 */
@FunctionalInterface
public interface Walkable {
	double floor(int x, int y, int z);

	/**
	 * Height of the solid shape top in the cell itself (for headroom checks when stepping up), or
	 * {@code NaN} when the cell is passable. Default: passable.
	 */
	default double solidTop(int x, int y, int z) {
		return Double.NaN;
	}

	/**
	 * Can the body go straight from walkable cell A (feet at {@code fa}) to the adjacent walkable
	 * cell B (feet at {@code fb})? Default: at most a 1-block step, with headroom above A when
	 * stepping up a whole block.
	 */
	default boolean passage(int ax, int ay, int az, double fa, int bx, int by, int bz, double fb) {
		if (Math.abs(fb - fa) > 1.0 + 1e-6) {
			return false;
		}
		return by <= ay || Double.isNaN(solidTop(ax, ay + 2, az));
	}

	/**
	 * Extra A* cost of the move A→B on top of its length (a {@link #passage} that is allowed but
	 * unpleasant, e.g. a hop up a whole block). Default: none.
	 */
	default double extraCost(int ax, int ay, int az, double fa, int bx, int by, int bz, double fb) {
		return 0;
	}

	/**
	 * Can the body stand with feet at (x,y,z), not necessarily centred in a cell? Default: the
	 * cells under the four corners of the 0.6-wide footprint are walkable at that height.
	 */
	default boolean fits(double x, double y, double z) {
		int by = (int) Math.floor(y + 0.01);
		for (int i = 0; i < 4; i++) {
			double px = x + ((i & 1) == 0 ? -0.3 : 0.3);
			double pz = z + ((i & 2) == 0 ? -0.3 : 0.3);
			double f = floor((int) Math.floor(px), by, (int) Math.floor(pz));
			if (Double.isNaN(f) || Math.abs(f - y) > 0.07) {
				return false;
			}
		}
		return true;
	}

	/** An infinite flat floor whose walkable cells are at block {@code y}. */
	static Walkable flat(int y) {
		return (x, cy, z) -> cy == y ? y : Double.NaN;
	}
}
