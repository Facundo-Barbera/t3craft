package dev.t3craft.build;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;

/**
 * fix/build-walkable: the floor in the first second. A large radial job analyses its whole plan
 * off-thread ({@link RadialCursor#plan}: a second or more for the whole HQ with its void island); until
 * that is done nothing was placed. The primer is planned in a few milliseconds on another worker from
 * the columns within {@link #RADIUS} blocks of where the player stands when the job starts, and lays their
 * <b>ground</b> (the walkable surface and the one layer under it, as the radial cursor does), the column
 * under the player first, then outwards. When the radial cursor arrives it continues from there: the
 * entries the primer placed are already right in the world, so its streams skip them.
 *
 * <p>Ground here is a column-local approximation of {@link Foundation}: from the bottom of the column, the
 * contiguous stack of plain opaque full cubes that are terrain, or the column's lowest entry, or hidden
 * (all six neighbours plain cubes of the plan), or a surface (nothing solid of the plan right above).
 * A wall stops the stack (its blocks have a block above and are no terrain), so a primer never puts the
 * top of a wall in the air. Where the two definitions differ the radial cursor sorts it out (it skips
 * what is already there). Pure; any thread.
 */
final class GroundPrimer {
	/** Columns within this many blocks (horizontally) of the starting point. */
	static final int RADIUS = 24;

	/** Plan indices to place, in order: nearest column first, its support layer then its surface. */
	private final int[] entries;
	private int next;

	private GroundPrimer(int[] entries) {
		this.entries = entries;
	}

	/** Where a radial job centres with nobody in it: the origin when inside the footprint (the demo's hub), else the middle. */
	static double[] fallbackFocus(BuildPlan plan) {
		if (plan.isEmpty()) {
			return new double[] {0.5, 0.5};
		}
		int minX = plan.min().getX();
		int maxX = plan.max().getX();
		int minZ = plan.min().getZ();
		int maxZ = plan.max().getZ();
		if (minX <= 0 && 0 <= maxX && minZ <= 0 && 0 <= maxZ) {
			return new double[] {0.5, 0.5};
		}
		return new double[] {(minX + maxX) / 2 + 0.5, (minZ + maxZ) / 2 + 0.5};
	}

	/**
	 * The primer for {@code plan} around plan-relative point {@code (fx, fz)} (cell convention: cell
	 * {@code c} spans {@code [c, c + 1)}). One pass over the plan, then only the nearby entries.
	 */
	static GroundPrimer plan(BuildPlan plan, double fx, double fz) {
		int n = plan.size();
		double r2 = (RADIUS + 0.5) * (RADIUS + 0.5);
		Long2ObjectOpenHashMap<IntArrayList> byCol = new Long2ObjectOpenHashMap<>();
		LongOpenHashSet plainCells = new LongOpenHashSet();
		for (int i = 0; i < n; i++) {
			Placement p = plan.get(i);
			int x = p.pos().getX();
			int z = p.pos().getZ();
			double dx = x + 0.5 - fx;
			double dz = z + 0.5 - fz;
			// one block more than the radius for the neighbour test ("hidden")
			if (dx * dx + dz * dz > (RADIUS + 1.5) * (RADIUS + 1.5)) {
				continue;
			}
			long cell = CrewPlanner.key(x, p.pos().getY(), z);
			if (Foundation.plain(p)) {
				plainCells.add(cell);
			} else {
				plainCells.remove(cell); // a later entry at the same cell wins
			}
			if (dx * dx + dz * dz <= r2) {
				long ck = CrewPlanner.key(x, 0, z);
				IntArrayList l = byCol.get(ck);
				if (l == null) {
					l = new IntArrayList(16);
					byCol.put(ck, l);
				}
				l.add(i);
			}
		}
		// per column: its ground (support + surface), keyed by distance for the order
		long[] keys = new long[byCol.size()];
		int[][] picks = new int[byCol.size()][];
		int nk = 0;
		long[] tmp = new long[16];
		for (IntArrayList l : byCol.values()) {
			int m = l.size();
			if (tmp.length < m) {
				tmp = new long[Math.max(m, tmp.length * 2)];
			}
			for (int k = 0; k < m; k++) {
				int i = l.getInt(k);
				tmp[k] = ((long) (plan.get(i).pos().getY() + (1 << 20)) << 32) | i;
			}
			Arrays.sort(tmp, 0, m);
			// one entry per height: the last of the plan wins (highest index sorts last within a height)
			int[] col = new int[m];
			int c = 0;
			for (int k = 0; k < m; k++) {
				int i = (int) tmp[k];
				int y = (int) (tmp[k] >>> 32) - (1 << 20);
				if (c > 0 && plan.get(col[c - 1]).pos().getY() == y) {
					col[c - 1] = i;
				} else {
					col[c++] = i;
				}
			}
			int top = stackTop(plan, col, c, plainCells);
			if (top < 0) {
				continue;
			}
			int[] g = top >= 1 ? new int[] {col[top - 1], col[top]} : new int[] {col[top]};
			Placement s = plan.get(col[top]);
			double dx = s.pos().getX() + 0.5 - fx;
			double dz = s.pos().getZ() + 0.5 - fz;
			long d = (long) Math.min(1_000_000, Math.floor(Math.sqrt(dx * dx + dz * dz) * 16));
			keys[nk] = (d << 32) | nk;
			picks[nk] = g;
			nk++;
		}
		Arrays.sort(keys, 0, nk);
		IntArrayList out = new IntArrayList(nk * 2);
		for (int k = 0; k < nk; k++) {
			for (int i : picks[(int) keys[k]]) {
				out.add(i);
			}
		}
		return new GroundPrimer(out.toIntArray());
	}

	/** Index (into {@code col}) of the top of the column's bottom stack of ground blocks, or -1 when it has none. */
	private static int stackTop(BuildPlan plan, int[] col, int c, LongOpenHashSet plainCells) {
		int top = -1;
		for (int k = 0; k < c; k++) {
			Placement p = plan.get(col[k]);
			if (!Foundation.plain(p)) {
				break;
			}
			int y = p.pos().getY();
			if (k > 0 && plan.get(col[k - 1]).pos().getY() != y - 1) {
				break; // a gap: what follows stands on something else
			}
			boolean solidAbove = false;
			if (k + 1 < c) {
				Placement a = plan.get(col[k + 1]);
				solidAbove = a.pos().getY() == y + 1 && !a.state().isAir() && a.state().canOcclude();
			}
			int x = p.pos().getX();
			int z = p.pos().getZ();
			boolean hidden = plainCells.contains(CrewPlanner.key(x + 1, y, z)) && plainCells.contains(CrewPlanner.key(x - 1, y, z))
					&& plainCells.contains(CrewPlanner.key(x, y, z + 1)) && plainCells.contains(CrewPlanner.key(x, y, z - 1))
					&& plainCells.contains(CrewPlanner.key(x, y + 1, z)) && plainCells.contains(CrewPlanner.key(x, y - 1, z));
			if (!(Foundation.terrain(p.state()) || k == 0 || hidden || !solidAbove)) {
				break; // a wall course: the builders raise it
			}
			top = k;
			if (!solidAbove) {
				break; // the surface: nothing solid of the plan right above it
			}
		}
		return top;
	}

	boolean done() {
		return next >= entries.length;
	}

	/**
	 * Places up to {@code max} entries (and at most {@link BuildCursor#DEFAULT_MAX_SKIPS_PER_TICK} already
	 * right) within {@code nanos}; returns {@code {placed, skipped}}. Server thread.
	 */
	int[] tick(FrontCursor.Step step, int max, long nanos) {
		long t0 = System.nanoTime();
		int placed = 0;
		int skipped = 0;
		while (next < entries.length && placed < max && skipped < BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
			BuildCursor.Outcome o = step.apply(-1, entries[next]);
			if (o == BuildCursor.Outcome.BLOCKED) {
				break;
			}
			next++;
			if (o == BuildCursor.Outcome.SKIPPED) {
				skipped++;
			} else {
				placed++;
			}
			if (((placed + skipped) & 15) == 0 && System.nanoTime() - t0 > nanos) {
				break;
			}
		}
		return new int[] {placed, skipped};
	}
}
