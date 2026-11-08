package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The fixed town plan of the village HQ (layout/village): a square grid of {@value #CELL}-block
 * cells around the hub, grown <b>once</b> from a fixed seed, independent of any roster. It says
 * which cells are streets (and how they link), at which terrace level every cell sits, and the order
 * in which projects take building plots ({@code solares}). The roster only decides which plots are
 * taken ({@link VillageLayout}) and therefore which streets are built.
 *
 * <h2>Growth (deterministic, seed {@link #SEED})</h2>
 * <ul>
 *   <li><b>Hub</b>: the 3×3 cells around the origin, one flat terrace at level {@link #HUB_LEVEL}
 *   (the highest): the town hall in the middle, the plaza on the four edge cells and the four
 *   singular buildings on the corner cells.</li>
 *   <li><b>Seeds</b>: one street leaves the plaza on each side (cells {@code (0,±2), (±2,0)}).</li>
 *   <li><b>Rounds</b>: every live street tip advances one cell per round (straight 60 %, turn 40 %),
 *   so the front grows in every direction at once; a new tip may branch sideways (30 %). Going out,
 *   a street steps one terrace down with probability 50 % (never below level 0). The seeds are one
 *   terrace below the plaza, so the hub always stands on its own raised acropolis.</li>
 *   <li><b>Loops</b>: a tip that runs into an existing street, or enters a cell next to a street of
 *   another branch, links to it (when the terrace levels differ by at most one) and stops there: the
 *   network closes into rings with small squares at the junctions, never a single long street.</li>
 *   <li><b>Plots</b>: every free cell next to a street is a plot facing that street (the oldest one
 *   when there are several), on the street's terrace.</li>
 * </ul>
 *
 * <h2>Plot order</h2>
 * Plots are ranked by the round of their street (inner rings first) and, inside a round, by
 * farthest-angle sampling: each next plot is the one farthest (in compass angle) from every plot
 * ranked before it, so consecutive plots fall in different directions. {@link VillageLayout} gives a new project the lowest free rank, so freed
 * interior plots are refilled before the town expands.
 */
final class VillageTown {
	/** Cell size (odd: a cell has a centre block). */
	static final int CELL = 21;
	static final int HALF = CELL / 2;
	/** Height of a terrace step. */
	static final int STEP = 3;
	/** Terrace level of the hub (the hill top). */
	static final int HUB_LEVEL = 4;
	/** Grid radius in cells (the town never reaches further). */
	static final int RADIUS = 7;
	static final long SEED = 0x7A11A6E5L;

	/** A street cell. */
	static final class Street {
		final int i;
		final int j;
		final int level;
		final int round;
		final int order;
		/** Where it grew from: null for a seed (it grew from the plaza). */
		final @Nullable Street parent;
		/** Direction from this cell to the plaza edge, for seeds. */
		final @Nullable Direction toPlaza;
		/** Every linked neighbour street (parent, children and loop links). */
		final List<Street> links = new ArrayList<>();

		Street(int i, int j, int level, int round, int order, @Nullable Street parent, @Nullable Direction toPlaza) {
			this.i = i;
			this.j = j;
			this.level = level;
			this.round = round;
			this.order = order;
			this.parent = parent;
			this.toPlaza = toPlaza;
		}

		@Override
		public String toString() {
			return "Street(" + i + "," + j + " L" + level + " r" + round + ")";
		}
	}

	/** A plot: a cell where a project's hamlet can stand, facing its street. */
	record Plot(int rank, int i, int j, int level, Direction front, Street street) {
	}

	private static @Nullable VillageTown instance;

	final Map<Long, Street> streets = new HashMap<>();
	final List<Street> streetList = new ArrayList<>();
	final List<Plot> plots;
	private final Map<Long, Plot> plotAt = new HashMap<>();

	static synchronized VillageTown get() {
		if (instance == null) {
			instance = new VillageTown();
		}
		return instance;
	}

	static long key(int i, int j) {
		return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
	}

	static boolean hub(int i, int j) {
		return Math.abs(i) <= 1 && Math.abs(j) <= 1;
	}

	static boolean inside(int i, int j) {
		return Math.max(Math.abs(i), Math.abs(j)) <= RADIUS;
	}

	static int centreX(int i) {
		return CELL * i;
	}

	static int centreZ(int j) {
		return CELL * j;
	}

	static int floorY(int level) {
		return STEP * level;
	}

	/** Cell coordinate of a block coordinate. */
	static int cellOf(int c) {
		return Math.floorDiv(c + HALF, CELL);
	}

	@Nullable
	Street street(int i, int j) {
		return streets.get(key(i, j));
	}

	@Nullable
	Plot plot(int i, int j) {
		return plotAt.get(key(i, j));
	}

	/** Terrace level of a cell of the plan (hub, street or plot); -1 for cells that are none. */
	int level(int i, int j) {
		if (hub(i, j)) {
			return HUB_LEVEL;
		}
		Street s = street(i, j);
		if (s != null) {
			return s.level;
		}
		Plot p = plot(i, j);
		return p == null ? -1 : p.level();
	}

	private static final class Rng {
		private long s;

		Rng(long seed) {
			s = seed;
		}

		long next() {
			long z = (s += 0x9E3779B97F4A7C15L);
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			return z ^ (z >>> 31);
		}

		double unit() {
			return (next() >>> 11) * 0x1.0p-53;
		}
	}

	private record Tip(Street at, Direction heading) {
	}

	private VillageTown() {
		Rng rng = new Rng(SEED);
		List<Tip> tips = new ArrayList<>();
		int order = 0;
		// the four seeds, one per side of the plaza
		for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			int level = HUB_LEVEL - 1;
			Street s = add(2 * d.getStepX(), 2 * d.getStepZ(), level, 1, order++, null, d.getOpposite());
			tips.add(new Tip(s, d));
		}
		for (int round = 2; !tips.isEmpty() && round < 40; round++) {
			List<Tip> next = new ArrayList<>();
			for (Tip t : tips) {
				double roll = rng.unit();
				Direction want = roll < 0.6 ? t.heading() : roll < 0.8 ? t.heading().getCounterClockWise() : t.heading().getClockWise();
				Direction[] tries = {want, t.heading(), t.heading().getCounterClockWise(), t.heading().getClockWise()};
				boolean down = rng.unit() < 0.5;
				boolean branch = rng.unit() < 0.3;
				boolean branchLeft = rng.unit() < 0.5;
				boolean stopAtLoop = rng.unit() < 0.7;
				for (Direction d : tries) {
					int ni = t.at().i + d.getStepX();
					int nj = t.at().j + d.getStepZ();
					if (!inside(ni, nj) || hub(ni, nj) || adjacentToHubOnlyDiagonally(ni, nj)) {
						continue;
					}
					Street there = street(ni, nj);
					if (there != null) {
						if (there != t.at() && !t.at().links.contains(there) && Math.abs(there.level - t.at().level) <= 1) {
							link(t.at(), there);
							break; // loop closed, the tip ends
						}
						continue;
					}
					int level = Math.max(0, t.at().level - (down ? 1 : 0));
					Street s = add(ni, nj, level, round, order++, t.at(), null);
					link(t.at(), s);
					boolean looped = false;
					for (Direction e : Direction.Plane.HORIZONTAL) {
						Street o = street(ni + e.getStepX(), nj + e.getStepZ());
						if (o != null && o != t.at() && Math.abs(o.level - level) <= 1 && !s.links.contains(o)) {
							link(s, o);
							looped = true;
						}
					}
					if (!looped || !stopAtLoop) {
						next.add(new Tip(s, d));
						if (branch) {
							next.add(new Tip(s, branchLeft ? d.getCounterClockWise() : d.getClockWise()));
						}
					}
					break;
				}
			}
			tips = next;
		}
		// plots: free cells next to a street, facing the oldest one
		List<Plot> raw = new ArrayList<>();
		for (int j = -RADIUS; j <= RADIUS; j++) {
			for (int i = -RADIUS; i <= RADIUS; i++) {
				if (hub(i, j) || street(i, j) != null) {
					continue;
				}
				Street best = null;
				Direction front = null;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					Street s = street(i + d.getStepX(), j + d.getStepZ());
					if (s != null && (best == null || s.round < best.round || s.round == best.round && s.order < best.order)) {
						best = s;
						front = d;
					}
				}
				if (best != null) {
					raw.add(new Plot(-1, i, j, best.level, front, best));
				}
			}
		}
		plots = rank(raw);
		for (Plot p : plots) {
			plotAt.put(key(p.i(), p.j()), p);
		}
	}

	/** Cells (±2,±2) touch the hub only at a corner; streets never start there (they stay plots or gardens). */
	private static boolean adjacentToHubOnlyDiagonally(int i, int j) {
		return Math.abs(i) == 2 && Math.abs(j) == 2;
	}

	private Street add(int i, int j, int level, int round, int order, @Nullable Street parent, @Nullable Direction toPlaza) {
		Street s = new Street(i, j, level, round, order, parent, toPlaza);
		streets.put(key(i, j), s);
		streetList.add(s);
		return s;
	}

	private static void link(Street a, Street b) {
		if (!a.links.contains(b)) {
			a.links.add(b);
			b.links.add(a);
		}
	}

	/** Round by round; in each round, a van der Corput walk around the compass. */
	private static List<Plot> rank(List<Plot> raw) {
		TreeSet<Integer> rounds = new TreeSet<>();
		raw.forEach(p -> rounds.add(p.street().round));
		List<Plot> out = new ArrayList<>();
		for (int r : rounds) {
			List<Plot> ring = new ArrayList<>();
			for (Plot p : raw) {
				if (p.street().round == r) {
					ring.add(p);
				}
			}
			ring.sort(Comparator.comparingDouble(VillageTown::angle).thenComparingInt(Plot::i).thenComparingInt(Plot::j));
			// farthest-angle sampling: each next plot is the one whose compass angle is farthest from every
			// plot ranked so far (ties: the lowest angle), so consecutive plots fall in different directions
			List<Plot> left = new ArrayList<>(ring);
			while (!left.isEmpty()) {
				Plot best = left.get(0);
				double bestGap = -1;
				for (Plot c : left) {
					double gap = 1;
					for (Plot o : out) {
						double d = Math.abs(angle(c) - angle(o));
						gap = Math.min(gap, Math.min(d, 1 - d));
					}
					if (gap > bestGap + 1e-9) {
						bestGap = gap;
						best = c;
					}
				}
				left.remove(best);
				out.add(new Plot(out.size(), best.i(), best.j(), best.level(), best.front(), best.street()));
			}
		}
		return List.copyOf(out);
	}

	/** Compass angle of a cell, 0 = north, clockwise, in [0, 1). */
	static double angle(Plot p) {
		double a = Math.atan2(p.i(), -p.j()) / (2 * Math.PI);
		return a < 0 ? a + 1 : a;
	}

	// ------------------------------------------------------------------ what is built

	/**
	 * The streets built for a set of taken plots: every street on the way from each taken plot's
	 * street back to the plaza (following {@link Street#parent}). Loop links are built when both
	 * ends are.
	 */
	Set<Street> builtStreets(java.util.Collection<Integer> takenPlots) {
		Set<Street> out = new LinkedHashSet<>();
		for (int rank : new TreeSet<>(takenPlots)) {
			for (Street s = plots.get(rank).street(); s != null && out.add(s); s = s.parent) {
				// walk to the plaza
			}
		}
		return out;
	}

	/** The town district of a cell (0 north, 1 east, 2 south, 3 west), by compass sector. */
	static int district(int i, int j) {
		double a = Math.atan2(i, -j) / (2 * Math.PI);
		a = a < 0 ? a + 1 : a;
		return (int) Math.floor(a * 4 + 0.5) % 4;
	}

	/** A stable per-cell hash (for variants and jitter). */
	static int hash(int i, int j, int salt) {
		long h = key(i, j) * 0x9E3779B97F4A7C15L + salt * 0xD1B54A32D192ED03L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) (h & 0x7FFFFFFF);
	}
}
