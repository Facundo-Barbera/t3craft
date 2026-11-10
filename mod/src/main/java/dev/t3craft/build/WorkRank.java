package dev.t3craft.build;

import java.util.Arrays;

/**
 * fix/25-builder-unfinished-columns: which work (obra: a building, tower or wing of the plan) the radial build
 * takes next. Plain ints, no world access; {@link RadialCursor} runs it off-thread together with the column
 * order, so the order is "work by work, nearest column first inside each".
 *
 * <p>Works are ranked in four tiers, then inside the tier:
 * <ol>
 *   <li><b>The player is in it</b> (inside its box, one block of margin, a few blocks of height): that building
 *   is the priority, finished before the build disperses. If several boxes hold the player, the smallest goes
 *   first (a room before the hall around it).</li>
 *   <li><b>Started and unfinished, within {@link #KEEP} blocks</b>: a building the builders already opened is
 *   not left half-built while a fresh one nearer the player starts. Nearest first.</li>
 *   <li><b>Not started</b>: nearest to the player first, in buckets of {@link #BUCKET} blocks (the build order
 *   breaks ties and keeps the choice from flipping while the player walks about).</li>
 *   <li><b>Nothing for a builder to do</b> (finished, or only ground and fill): last; their columns are done or
 *   are laid by the streams.</li>
 * </ol>
 * The result is a rank per work, 1-based and never above {@link #MAX_RANK}; rank 0 is for the ground right around
 * the player that belongs to no work, {@link #FAR} for the ground that does not ({@link RadialCursor#order}).
 */
final class WorkRank {
	/** A started work keeps its place ahead of unstarted ones while it is within this many blocks of the player. */
	static final double KEEP = 32.0;
	/** Distances are bucketed by this many blocks, so equal buckets keep the build order. */
	static final double BUCKET = 8.0;
	/** The player counts as in a work when it is at most this many blocks outside its box (horizontally). */
	static final int MARGIN = 1;
	/** ...and between this far under its lowest and this far over its highest block. */
	static final int Y_BELOW = 3;
	static final int Y_ABOVE = 4;
	/** Highest rank of a work (ranks past it share it: the distance order then decides). */
	static final int MAX_RANK = 1900;
	/** Rank of the ground that belongs to no work and is not right around the player. */
	static final int FAR = MAX_RANK + 1;

	private WorkRank() {
	}

	/**
	 * @param box   per work {@code {minX, minZ, maxX, maxZ, minY, maxY}} of its columns (min > max = no column)
	 * @param total builder entries (not ground, fill or air) each work has
	 * @param left  builder entries each work still has to place
	 * @param py    the player's plan-relative height, NaN = unknown (no vertical test)
	 * @return the rank of every work, 1-based
	 */
	static int[] rank(int[][] box, int[] total, int[] left, double px, double pz, double py) {
		int n = box.length;
		long[] keys = new long[n];
		for (int w = 0; w < n; w++) {
			int[] b = box[w];
			long tier;
			long sub = 0;
			if (b[0] > b[2] || b[1] > b[3] || total[w] <= 0 || left[w] <= 0) {
				tier = 3;
			} else {
				double dx = Math.max(0, Math.max(b[0] - px, px - (b[2] + 1)));
				double dz = Math.max(0, Math.max(b[1] - pz, pz - (b[3] + 1)));
				double d = Math.hypot(dx, dz);
				boolean yOk = Double.isNaN(py) || py >= b[4] - Y_BELOW && py <= b[5] + Y_ABOVE;
				if (dx <= MARGIN && dz <= MARGIN && yOk) {
					tier = 0;
					sub = Math.min(1L << 30, (long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1));
				} else {
					tier = left[w] < total[w] && d <= KEEP ? 1 : 2;
					sub = (long) Math.min(1_000_000.0, Math.floor(d / BUCKET));
				}
			}
			keys[w] = tier << 56 | sub << 24 | w;
		}
		Arrays.sort(keys);
		int[] rank = new int[n];
		for (int r = 0; r < n; r++) {
			rank[(int) (keys[r] & 0xFFFFFF)] = Math.min(MAX_RANK, r + 1);
		}
		return rank;
	}
}
