package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The village's ground plan for whoever places it on real terrain (layout/village): one
 * {@value VillageTown#CELL}×{@value VillageTown#CELL} square per built cell, in HQ coordinates
 * (origin = the middle of the town hall, {@code y = 0} the natural ground; the hill top, where the
 * town hall stands, is at {@code y = }{@link #hubFloor()}).
 *
 * <p>Survival reservation (to be wired in {@code site/builders}): a village grows on a fixed grid,
 * so a site reserves {@link #envelope(int)} for the largest number of plots it expects; growth beyond
 * it needs a bigger reservation, never a move. Each build group is exactly one cell ({@link #areas}),
 * so builders can claim, clear and build cells independently and in parallel.
 */
public final class VillageFootprints {
	private VillageFootprints() {
	}

	/** Lowest y of a village block (the natural ground). */
	public static int bottom() {
		return 0;
	}

	/** Highest y of a village block (the town hall's lightning rod is below it). */
	public static int top() {
		return hubFloor() + 24;
	}

	public static int hubFloor() {
		return VillageTown.floorY(VillageTown.HUB_LEVEL);
	}

	/** The square of cell (i, j). */
	public static HqFootprints.Area cell(int i, int j) {
		int cx = VillageTown.centreX(i);
		int cz = VillageTown.centreZ(j);
		return new HqFootprints.Area(cx - VillageTown.HALF, cz - VillageTown.HALF, cx + VillageTown.HALF, cz + VillageTown.HALF);
	}

	/** The hill top: the town hall, the plaza and the four singular buildings (3×3 cells). */
	public static HqFootprints.Area base() {
		return cell(-1, -1).union(cell(1, 1));
	}

	/** Build group → its cell, for every cell a layout builds. */
	public static Map<String, HqFootprints.Area> areas(VillageLayout layout) {
		VillageTown town = VillageTown.get();
		Map<String, HqFootprints.Area> out = new LinkedHashMap<>();
		out.put("hub", cell(0, 0));
		out.put("plaza_s", cell(0, 1));
		out.put("plaza_e", cell(1, 0));
		out.put("plaza_w", cell(-1, 0));
		out.put("review", cell(0, -1));
		out.put(HqGenerator.GROUP_ARCHIVE, cell(-1, 1));
		out.put("lounge", cell(-1, -1));
		out.put("schedule", cell(1, -1));
		out.put("attention", cell(1, 1));
		Set<Integer> taken = new HashSet<>(layout.plots());
		Set<VillageTown.Street> built = town.builtStreets(taken);
		for (VillageTown.Street s : built) {
			out.put(VillageGenerator.streetGroup(s.i, s.j), cell(s.i, s.j));
		}
		for (VillageTown.Plot p : town.plots) {
			if (taken.contains(p.rank()) || built.contains(p.street())) {
				out.put(VillageGenerator.plotGroup(p.rank()), cell(p.i(), p.j()));
			}
		}
		return out;
	}

	/**
	 * Everything a village whose plots all have a rank below {@code plots} may use: the hill top, those
	 * plots, the streets that lead to them and the orchards along those streets.
	 */
	public static HqFootprints.Area envelope(int plots) {
		VillageTown town = VillageTown.get();
		Collection<Integer> ranks = new ArrayList<>();
		for (int r = 0; r < Math.min(plots, town.plots.size()); r++) {
			ranks.add(r);
		}
		Set<VillageTown.Street> built = town.builtStreets(ranks);
		HqFootprints.Area a = base();
		for (VillageTown.Street s : built) {
			a = a.union(cell(s.i, s.j));
		}
		for (VillageTown.Plot p : town.plots) {
			if (ranks.contains(p.rank()) || built.contains(p.street())) {
				a = a.union(cell(p.i(), p.j()));
			}
		}
		return a;
	}

	/** Floor y of the terrace under block column (x, z): its cell's level in the town plan, 0 outside it. */
	public static int floorAt(int x, int z) {
		int l = VillageTown.get().level(VillageTown.cellOf(x), VillageTown.cellOf(z));
		return VillageTown.floorY(Math.max(0, l));
	}

	/** Cell coordinates of the plots in rank order (for docs and tools). */
	public static List<int[]> plotCells() {
		List<int[]> out = new ArrayList<>();
		for (VillageTown.Plot p : VillageTown.get().plots) {
			out.add(new int[] {p.i(), p.j(), p.level()});
		}
		return out;
	}
}
