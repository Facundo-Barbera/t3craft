package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The fixed plan of the "Acrópolis en terrazas" HQ (layout {@code hybrid-terraces}): a square grid
 * of {@value #CELL}-block cells around the acropolis, independent of any roster. It says what every
 * cell is (acropolis piece, special building, sky island, bridge garden or a project solar), at
 * which terrace level it sits, which cells form one terrace, how the cells link (stairs, open edges,
 * bridges) and in which order projects take solares. The roster only decides which solares are
 * taken ({@link TerraceStyle}), and so which cells are built ({@link #builtCells}).
 *
 * <h2>Shape</h2>
 * <ul>
 *   <li><b>Acropolis</b> (level {@value #TOP_LEVEL}, the highest): the great plaza in the middle with
 *   the hologram tholos, the temple north of it (attention board, benches), the kanban stoa (west),
 *   the big-screen stoa (east) and the propylaea (south, spawn).</li>
 *   <li><b>Special terraces</b> (level 5, one per corner, each its own terrace): library-hermitage
 *   (archive terminal), guard post, tavern and market hall; the forecourt with the fountain below the
 *   propylaea; the mill on its own sky island behind the temple.</li>
 *   <li><b>Sky islands</b> (level {@value #TOP_LEVEL}, floating): the mill (north) and two project
 *   islands (east and west), each reached by a bridge from the acropolis over a garden terrace.</li>
 *   <li><b>Rings of solares</b> from ring 2 out to ring {@value #RADIUS}, one level lower per ring
 *   (and alternate terraces of a ring one more level down). Consecutive solares of a ring are grouped
 *   into terraces of 2 or 3 (one barrio each), with the same floor and open edges.</li>
 * </ul>
 *
 * <h2>Network</h2>
 * Two built cells whose levels differ by at most one are linked: an open edge when level, a
 * monumental stair carved in the higher cell otherwise; islands only by their bridge. Because every
 * neighbour pair is linked, the network closes into loops by itself (each corner terrace has four
 * stairs, each ring a promenade): there is no single long street.
 *
 * <h2>Order</h2>
 * Solares are ranked by ring (inner first), then by link distance from the plaza, then by
 * farthest-angle sampling (each next solar the one farthest in compass angle from all ranked
 * before), so a ring opens in several directions at once. A new project takes the lowest free rank,
 * so a freed interior solar is refilled before the HQ grows outwards. A taken solar builds the cells
 * on its fixed path to the plaza (as gardens while free), never anything else.
 */
final class TerraceTown {
	/** Cell size (odd: a cell has a centre block). */
	static final int CELL = 29;
	static final int HALF = CELL / 2;
	/** Half size of a cell's inner square (buildings); the band outside it is the promenade. */
	static final int INNER = 10;
	/** Height of one terrace level. */
	static final int STEP = 4;
	/** Level of the acropolis and the sky islands. */
	static final int TOP_LEVEL = 6;
	/** Grid radius in cells. */
	static final int RADIUS = 5;
	/** Highest y any block of the layout may use. */
	static final int TOP_Y = 64;

	enum Kind {
		PLAZA, TEMPLE, STOA_KANBAN, STOA_SCREEN, PROPYLAEA, FORECOURT, LIBRARY, GUARD, TAVERN, LONJA, BRIDGE, MILL_ISLAND, ISLAND_PLOT, PLOT;

		boolean island() {
			return this == MILL_ISLAND || this == ISLAND_PLOT;
		}

		boolean plot() {
			return this == PLOT || this == ISLAND_PLOT;
		}
	}

	/** One cell of the plan. */
	static final class Cell {
		final int i;
		final int j;
		final int ring;
		final Kind kind;
		/** Built whatever the roster. */
		final boolean always;
		int level;
		int terrace;
		/** Link distance from the plaza (-1: unreachable, never a solar). */
		int depth = -1;
		/** Previous cell on the fixed path to the plaza (null for the plaza). */
		@Nullable Cell parent;
		/** The garden a bridge to this cell crosses (islands), else null. */
		@Nullable Cell via;
		/** Rank among the solares (-1 for the others). */
		int rank = -1;

		Cell(int i, int j, Kind kind, int level, boolean always) {
			this.i = i;
			this.j = j;
			this.ring = Math.max(Math.abs(i), Math.abs(j));
			this.kind = kind;
			this.level = level;
			this.always = always;
		}

		int floor() {
			return floorY(level);
		}

		int cx() {
			return centreX(i);
		}

		int cz() {
			return centreZ(j);
		}

		/** Direction of its front: towards the cell it is reached from (its parent; islands: their bridge). */
		Direction front() {
			if (parent == null) {
				return Direction.SOUTH;
			}
			return direction(i, j, parent.i, parent.j);
		}

		long key() {
			return TerraceTown.key(i, j);
		}

		@Override
		public String toString() {
			return kind + "(" + i + "," + j + " L" + level + " t" + terrace + " r" + rank + ")";
		}
	}

	/** A bridge from an acropolis cell over a garden cell to a sky island. */
	record Bridge(Cell from, Cell over, Cell to, Direction dir) {
	}

	private static @Nullable TerraceTown instance;

	final Map<Long, Cell> cells = new HashMap<>();
	/** Every cell, fixed ones first, then ring by ring in compass order. */
	final List<Cell> order = new ArrayList<>();
	final List<Bridge> bridges = new ArrayList<>();
	/** Solares by rank. */
	final List<Cell> plots;

	static synchronized TerraceTown get() {
		if (instance == null) {
			instance = new TerraceTown();
		}
		return instance;
	}

	static long key(int i, int j) {
		return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
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

	static Direction direction(int i, int j, int ni, int nj) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (i + d.getStepX() == ni && j + d.getStepZ() == nj) {
				return d;
			}
		}
		// not a neighbour (islands: their parent is across the bridge garden)
		int di = Integer.signum(ni - i);
		int dj = Integer.signum(nj - j);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (d.getStepX() == di && d.getStepZ() == dj) {
				return d;
			}
		}
		return Direction.SOUTH;
	}

	/** Compass angle of a cell, 0 = north, clockwise, in [0, 1). */
	static double angle(int i, int j) {
		double a = Math.atan2(i, -j) / (2 * Math.PI);
		return a < 0 ? a + 1 : a;
	}

	@Nullable
	Cell cell(int i, int j) {
		return cells.get(key(i, j));
	}

	private TerraceTown() {
		// --- the fixed cells
		fixed(0, 0, Kind.PLAZA, TOP_LEVEL, true);
		fixed(0, -1, Kind.TEMPLE, TOP_LEVEL, true);
		fixed(-1, 0, Kind.STOA_KANBAN, TOP_LEVEL, true);
		fixed(1, 0, Kind.STOA_SCREEN, TOP_LEVEL, true);
		fixed(0, 1, Kind.PROPYLAEA, TOP_LEVEL, true);
		fixed(-1, -1, Kind.LIBRARY, 5, true);
		fixed(1, -1, Kind.GUARD, 5, true);
		fixed(-1, 1, Kind.TAVERN, 5, true);
		fixed(1, 1, Kind.LONJA, 5, true);
		fixed(0, 2, Kind.FORECOURT, 5, true);
		fixed(0, -2, Kind.BRIDGE, 4, true);
		fixed(2, 0, Kind.BRIDGE, 4, false);
		fixed(-2, 0, Kind.BRIDGE, 4, false);
		fixed(0, -3, Kind.MILL_ISLAND, TOP_LEVEL, true);
		fixed(3, 0, Kind.ISLAND_PLOT, TOP_LEVEL, false);
		fixed(-3, 0, Kind.ISLAND_PLOT, TOP_LEVEL, false);
		int terrace = 0;
		for (Cell c : order) {
			c.terrace = terrace++;
		}
		bridges.add(new Bridge(cell(0, -1), cell(0, -2), cell(0, -3), Direction.NORTH));
		bridges.add(new Bridge(cell(1, 0), cell(2, 0), cell(3, 0), Direction.EAST));
		bridges.add(new Bridge(cell(-1, 0), cell(-2, 0), cell(-3, 0), Direction.WEST));
		// --- the rings of solares, grouped into terraces of 2 or 3
		for (int r = 2; r <= RADIUS; r++) {
			List<int[]> ring = new ArrayList<>();
			for (int j = -r; j <= r; j++) {
				for (int i = -r; i <= r; i++) {
					if (Math.max(Math.abs(i), Math.abs(j)) == r) {
						ring.add(new int[] {i, j});
					}
				}
			}
			ring.sort(Comparator.comparingDouble(a -> angle(a[0], a[1])));
			int start = 0;
			for (int k = 0; k < ring.size(); k++) {
				if (cell(ring.get(k)[0], ring.get(k)[1]) != null) {
					start = k;
					break;
				}
			}
			List<List<int[]>> runs = new ArrayList<>();
			List<int[]> run = new ArrayList<>();
			for (int k = 0; k < ring.size(); k++) {
				int[] a = ring.get((start + k) % ring.size());
				if (cell(a[0], a[1]) != null) {
					if (!run.isEmpty()) {
						runs.add(run);
					}
					run = new ArrayList<>();
				} else {
					run.add(a);
				}
			}
			if (!run.isEmpty()) {
				runs.add(run);
			}
			int chunk = 0;
			for (List<int[]> rn : runs) {
				int k = 0;
				for (int size : chunks(rn.size())) {
					int level = r == 2 ? TOP_LEVEL - 2 : Math.max(0, TOP_LEVEL - r - (chunk % 2));
					for (int s = 0; s < size; s++) {
						int[] a = rn.get(k++);
						Cell c = new Cell(a[0], a[1], Kind.PLOT, level, false);
						c.terrace = terrace;
						put(c);
					}
					terrace++;
					chunk++;
				}
			}
		}
		// --- links: every terrace must reach the plaza; lift a stray terrace to its ring's base level
		for (int attempt = 0; attempt < 8; attempt++) {
			link();
			boolean fixedOne = false;
			for (Cell c : order) {
				if (c.depth < 0 && c.kind == Kind.PLOT) {
					int base = Math.max(0, TOP_LEVEL - c.ring);
					if (c.ring == 2) {
						base = TOP_LEVEL - 2;
					}
					for (Cell o : order) {
						if (o.terrace == c.terrace && o.level != base) {
							o.level = base;
							fixedOne = true;
						}
					}
				}
			}
			if (!fixedOne) {
				break;
			}
		}
		link();
		plots = rank();
	}

	/** Terrace sizes for a run of {@code n} consecutive solares: threes, then twos. */
	static List<Integer> chunks(int n) {
		List<Integer> out = new ArrayList<>();
		while (n > 0) {
			if (n == 4) {
				out.add(2);
				out.add(2);
				n = 0;
			} else if (n <= 3) {
				out.add(n);
				n = 0;
			} else {
				out.add(3);
				n -= 3;
			}
		}
		return out;
	}

	private void fixed(int i, int j, Kind kind, int level, boolean always) {
		put(new Cell(i, j, kind, level, always));
	}

	private void put(Cell c) {
		cells.put(c.key(), c);
		order.add(c);
	}

	/** Two cells side by side that a person can walk between (islands only by their bridge). */
	static boolean walkable(Cell a, Cell b) {
		return !a.kind.island() && !b.kind.island() && Math.abs(a.level - b.level) <= 1;
	}

	/** Breadth-first from the plaza over walkable edges and bridges: depth, parent and via of every cell. */
	private void link() {
		for (Cell c : order) {
			c.depth = -1;
			c.parent = null;
			c.via = null;
		}
		Cell plaza = cell(0, 0);
		plaza.depth = 0;
		ArrayDeque<Cell> todo = new ArrayDeque<>();
		todo.add(plaza);
		while (!todo.isEmpty()) {
			Cell c = todo.poll();
			for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
				Cell n = cell(c.i + d.getStepX(), c.j + d.getStepZ());
				if (n != null && n.depth < 0 && walkable(c, n)) {
					n.depth = c.depth + 1;
					n.parent = c;
					todo.add(n);
				}
			}
			for (Bridge b : bridges) {
				if (b.from() == c && b.to().depth < 0) {
					b.to().depth = c.depth + 1;
					b.to().parent = c;
					b.to().via = b.over();
					todo.add(b.to());
				}
			}
		}
	}

	/** Solares by ring, then depth, then farthest-angle sampling. */
	private List<Cell> rank() {
		TreeMap<Long, List<Cell>> groups = new TreeMap<>();
		for (Cell c : order) {
			if (c.kind.plot() && c.depth >= 0) {
				groups.computeIfAbsent((long) c.ring * 1000 + c.depth, k -> new ArrayList<>()).add(c);
			}
		}
		List<Cell> out = new ArrayList<>();
		for (List<Cell> g : groups.values()) {
			g.sort(Comparator.comparingDouble((Cell c) -> angle(c.i, c.j)).thenComparingInt(c -> c.i).thenComparingInt(c -> c.j));
			List<Cell> left = new ArrayList<>(g);
			while (!left.isEmpty()) {
				Cell best = left.get(0);
				double bestGap = -1;
				for (Cell c : left) {
					double gap = 1;
					for (Cell o : out) {
						double d = Math.abs(angle(c.i, c.j) - angle(o.i, o.j));
						gap = Math.min(gap, Math.min(d, 1 - d));
					}
					if (gap > bestGap + 1e-9) {
						bestGap = gap;
						best = c;
					}
				}
				left.remove(best);
				best.rank = out.size();
				out.add(best);
			}
		}
		return List.copyOf(out);
	}

	// ------------------------------------------------------------------ what is built

	/**
	 * The cells built for a set of taken solar ranks: every always-built cell, each taken solar and
	 * every cell on its fixed path to the plaza (plus the gardens its bridges cross), in {@link #order}.
	 */
	List<Cell> builtCells(Collection<Integer> taken) {
		Set<Cell> in = new LinkedHashSet<>();
		for (Cell c : order) {
			if (c.always) {
				in.add(c);
			}
		}
		for (int rank : new TreeSet<>(taken)) {
			if (rank < 0 || rank >= plots.size()) {
				continue;
			}
			Set<Cell> walked = new java.util.HashSet<>();
			for (Cell c = plots.get(rank); c != null && walked.add(c); c = c.parent) {
				in.add(c);
				if (c.via != null) {
					in.add(c.via);
				}
			}
		}
		List<Cell> out = new ArrayList<>();
		for (Cell c : order) {
			if (in.contains(c)) {
				out.add(c);
			}
		}
		return out;
	}

	/** The xz square of a cell. */
	static HqFootprints.Area area(Cell c) {
		return new HqFootprints.Area(c.cx() - HALF, c.cz() - HALF, c.cx() + HALF, c.cz() + HALF);
	}

	/** Union of the squares of the cells built for {@code taken}. */
	HqFootprints.Area envelope(Collection<Integer> taken) {
		HqFootprints.Area a = null;
		for (Cell c : builtCells(taken)) {
			a = a == null ? area(c) : a.union(area(c));
		}
		return a;
	}

	/** The built cells as a map by key (for edge decisions). */
	static Map<Long, Cell> byKey(List<Cell> built) {
		Map<Long, Cell> m = new HashMap<>();
		for (Cell c : built) {
			m.put(c.key(), c);
		}
		return m;
	}
}
