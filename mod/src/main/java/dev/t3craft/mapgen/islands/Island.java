package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.Noise;
import net.minecraft.core.BlockPos;

/**
 * One floating island: where it is and the shape of its ground. Pure data and functions of the column; the
 * blocks are {@link IslandBlocks}'. The outline is a circle whose radius wobbles smoothly with the angle, the
 * underside tapers to a cone with a little noise.
 *
 * @param id    {@code i_<gx>_<gz>}, stable
 * @param top   y of the grass layer (players stand at {@code top + 1})
 * @param seed  the field's seed (the shape noise)
 */
public record Island(String id, int gx, int gz, IslandSize size, int cx, int top, int cz, long seed, double phaseA, double phaseB) {

	/** Highest relative deviation of the outline radius from the nominal one. */
	static final double WOBBLE = 0.07 + 0.05;

	public int radius() {
		return size.radius();
	}

	public BlockPos surface() {
		return new BlockPos(cx, top + 1, cz);
	}

	private static double dist(int dx, int dz) {
		return StrictMath.sqrt((double) dx * dx + (double) dz * dz);
	}

	/** Outline radius towards {@code (dx, dz)} from the centre. */
	double outline(int dx, int dz) {
		double a = StrictMath.atan2(dz, dx);
		return size.radius() * (1 + 0.07 * StrictMath.sin(3 * a + phaseA) + 0.05 * StrictMath.sin(5 * a + phaseB));
	}

	/** Whether the column has ground; {@code slack} widens the outline (for "is the player near the island"). */
	public boolean contains(int x, int z, double slack) {
		int dx = x - cx;
		int dz = z - cz;
		return dist(dx, dz) <= outline(dx, dz) + slack + 1e-9;
	}

	/** Blocks of ground in the column, grass included: {@code size.depth()} in the middle, 2 at the edge. */
	public int depth(int x, int z) {
		int dx = x - cx;
		int dz = z - cz;
		double t = Math.min(1.0, dist(dx, dz) / outline(dx, dz));
		int d = 2 + (int) Math.round((size.depth() - 2) * StrictMath.pow(1.0 - t, 1.2)) + Noise.hash(seed, x, z, 11, 3) - 1;
		return Math.max(2, Math.min(size.depth(), d));
	}

	/** Every column the island can cover. */
	public Area area() {
		int r = (int) Math.ceil(size.radius() * (1 + WOBBLE)) + 1;
		return new Area(cx - r, cz - r, cx + r, cz + r);
	}

	/** Horizontal distance from a column to the island's centre. */
	public double distanceTo(int x, int z) {
		return dist(x - cx, z - cz);
	}
}
