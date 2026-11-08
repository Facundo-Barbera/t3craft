package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Ground plan of the "ensanche" HQ (layout/blocks): a piece of city. Public so builders, the site and
 * docs can reason about it; all coordinates are relative to the HQ origin (centre of the plaza mayor,
 * {@code y = 0} the street level, north = {@code -z}).
 *
 * <h2>Cells (manzanas)</h2>
 * The ground is a grid of square <b>cells</b> of {@link #PITCH} blocks: a {@link #BLOCK}-wide manzana
 * in the middle and half a street ({@link #HALF_STREET} blocks) on each side, so two neighbouring cells
 * make one {@link #STREET}-wide street. Cell {@code k} is the {@code k}-th cell of a square spiral
 * around the plaza ({@link #cell}): {@code k = 0} the plaza mayor, ring 1 = {@code k 1..8} starting
 * north and turning clockwise, ring 2 = {@code k 9..24}, and so on. The order never changes, so a
 * cell's place is a pure function of {@code k}.
 * <ul>
 *   <li>{@code k = 0}: the plaza mayor with the civic hall (the hub), the clock tower (scheduled
 *   tasks) and the review hall on its chamfered corners;</li>
 *   <li>cell {@code (1, 0)} ({@code k = 3}): the covered market (the lounge);</li>
 *   <li>cells {@code (n, -n)}, {@code n >= 1} ({@code k = 2, 11, 28, ...}): small squares crossed by
 *   the diagonal promenade that starts at the plaza mayor's north-east corner;</li>
 *   <li>every other cell: a work manzana with four buildings in a pinwheel around a garden patio.</li>
 * </ul>
 *
 * <h2>Parcels</h2>
 * A work manzana has four <b>parcels</b> ({@link #SIDES}), one building each, numbered
 * {@code p = 4k + side} (side 0 south, 1 west, 2 north, 3 east). Sides alternate depth (11 and 13
 * blocks) so their rooms have different sizes; every building has a chamfered outer corner and a
 * covered passage from the street to the patio (with the stairs) at its inner end. A building has up
 * to {@link #MAX_FLOORS} working floors of {@link #STOREY} blocks; HQ module index
 * {@code i = 3p + floor} is the room on that floor (6 desks, {@link HqLayout#DESKS}).
 */
public final class EnsancheGrid {
	/** Manzana width (blocks). */
	public static final int BLOCK = 45;
	/** Half the manzana minus one: a manzana spans {@code -BLOCK_HALF..BLOCK_HALF} around its cell centre. */
	public static final int BLOCK_HALF = 22;
	/** Street width between two manzanas. */
	public static final int STREET = 10;
	public static final int HALF_STREET = STREET / 2;
	/** Cell pitch: manzana + street. */
	public static final int PITCH = BLOCK + STREET;
	/** A cell spans {@code -CELL_HALF..CELL_HALF} around its centre. */
	public static final int CELL_HALF = BLOCK_HALF + HALF_STREET;
	/** Storey height: floor slab + 5 air. */
	public static final int STOREY = 6;
	public static final int MAX_FLOORS = 3;
	public static final int SIDES = 4;
	/** Cut of the chamfered corners ({@code a + d < CHAMFER} is street). */
	public static final int CHAMFER = 4;
	/** Highest y the town uses (clock tower spire). */
	public static final int TOP = 34;
	/** Cells always built: the plaza, its first neighbour, the first small square and the market. */
	public static final int MIN_CELLS = 4;

	/** What a cell holds. */
	public enum Kind {
		PLAZA, WORK, MARKET, PLAZUELA
	}

	private EnsancheGrid() {
	}

	// ------------------------------------------------------------------ spiral

	/** Grid coordinates {@code (bx, bz)} of spiral cell {@code k}. */
	public static int[] cell(int k) {
		if (k < 0) {
			throw new IllegalArgumentException("cell " + k);
		}
		if (k == 0) {
			return new int[] {0, 0};
		}
		int r = 1;
		while ((2 * r + 1) * (2 * r + 1) <= k) {
			r++;
		}
		int t = k - (2 * r - 1) * (2 * r - 1);
		if (t <= r) {
			return new int[] {t, -r};
		}
		if (t <= 3 * r) {
			return new int[] {r, -r + (t - r)};
		}
		if (t <= 5 * r) {
			return new int[] {r - (t - 3 * r), r};
		}
		if (t <= 7 * r) {
			return new int[] {-r, r - (t - 5 * r)};
		}
		return new int[] {-r + (t - 7 * r), -r};
	}

	/** Spiral index of grid cell {@code (bx, bz)}. */
	public static int index(int bx, int bz) {
		int r = Math.max(Math.abs(bx), Math.abs(bz));
		if (r == 0) {
			return 0;
		}
		int first = (2 * r - 1) * (2 * r - 1);
		for (int k = first; k < (2 * r + 1) * (2 * r + 1); k++) {
			int[] c = cell(k);
			if (c[0] == bx && c[1] == bz) {
				return k;
			}
		}
		throw new IllegalStateException("cell " + bx + "," + bz);
	}

	public static Kind kind(int k) {
		int[] c = cell(k);
		if (k == 0) {
			return Kind.PLAZA;
		}
		if (c[0] == 1 && c[1] == 0) {
			return Kind.MARKET;
		}
		if (c[0] >= 1 && c[1] == -c[0]) {
			return Kind.PLAZUELA;
		}
		return Kind.WORK;
	}

	/** Centre x of cell {@code k}. */
	public static int cx(int k) {
		return cell(k)[0] * PITCH;
	}

	/** Centre z of cell {@code k}. */
	public static int cz(int k) {
		return cell(k)[1] * PITCH;
	}

	/** The cells sharing a street side with cell {@code k}, by spiral index. */
	public static List<Integer> neighbours(int k) {
		int[] c = cell(k);
		TreeSet<Integer> out = new TreeSet<>();
		out.add(index(c[0] + 1, c[1]));
		out.add(index(c[0] - 1, c[1]));
		out.add(index(c[0], c[1] + 1));
		out.add(index(c[0], c[1] - 1));
		return List.copyOf(out);
	}

	// ------------------------------------------------------------------ parcels and modules

	public static int parcel(int k, int side) {
		return k * SIDES + side;
	}

	public static int parcelOfModule(int moduleIndex) {
		return moduleIndex / MAX_FLOORS;
	}

	public static int floorOfModule(int moduleIndex) {
		return moduleIndex % MAX_FLOORS;
	}

	public static int cellOfParcel(int p) {
		return p / SIDES;
	}

	public static int sideOfParcel(int p) {
		return p % SIDES;
	}

	public static int module(int parcel, int floor) {
		return parcel * MAX_FLOORS + floor;
	}

	/** Whether module index {@code i} is a room of a work manzana (anything else is reallocated). */
	public static boolean validModule(int i) {
		return i >= 0 && kind(cellOfParcel(parcelOfModule(i))) == Kind.WORK;
	}

	/** Building depth of a side (street wall to patio wall): 11 or 13. */
	public static int depth(int side) {
		return side % 2 == 0 ? 11 : 13;
	}

	/** Building length of a side (chamfered corner to the end wall): 32 or 34. */
	public static int length(int side) {
		return BLOCK - depth((side + 1) % SIDES);
	}

	/**
	 * A building's local frame: {@code a} runs along the street from the chamfered corner
	 * ({@code a = 0}), {@code d} from the street wall ({@code d = 0}) to the patio wall.
	 */
	public record Frame(int ox, int oz, Direction along, Direction in) {
		public BlockPos pos(int a, int y, int d) {
			return new BlockPos(ox + along.getStepX() * a + in.getStepX() * d, y, oz + along.getStepZ() * a + in.getStepZ() * d);
		}

		public Box box(int a1, int y1, int d1, int a2, int y2, int d2) {
			BlockPos p = pos(a1, y1, d1);
			BlockPos q = pos(a2, y2, d2);
			return Box.of(p.getX(), p.getY(), p.getZ(), q.getX(), q.getY(), q.getZ());
		}

		/** Towards the street. */
		public Direction street() {
			return in.getOpposite();
		}
	}

	public static Frame frame(int k, int side) {
		int x0 = cx(k) - BLOCK_HALF;
		int z0 = cz(k) - BLOCK_HALF;
		int e = BLOCK - 1;
		return switch (side) {
			case 0 -> new Frame(x0, z0 + e, Direction.EAST, Direction.NORTH);
			case 1 -> new Frame(x0, z0, Direction.SOUTH, Direction.EAST);
			case 2 -> new Frame(x0 + e, z0, Direction.WEST, Direction.SOUTH);
			case 3 -> new Frame(x0 + e, z0 + e, Direction.NORTH, Direction.WEST);
			default -> throw new IllegalArgumentException("side " + side);
		};
	}

	// ------------------------------------------------------------------ footprints

	/** Whole cell (manzana and its half streets). */
	public static HqFootprints.Area cellArea(int k) {
		return new HqFootprints.Area(cx(k) - CELL_HALF, cz(k) - CELL_HALF, cx(k) + CELL_HALF, cz(k) + CELL_HALF);
	}

	/** The manzana of cell {@code k}, without its streets. */
	public static HqFootprints.Area blockArea(int k) {
		return new HqFootprints.Area(cx(k) - BLOCK_HALF, cz(k) - BLOCK_HALF, cx(k) + BLOCK_HALF, cz(k) + BLOCK_HALF);
	}

	/** The garden patio of a work manzana. */
	public static HqFootprints.Area patioArea(int k) {
		int x0 = cx(k) - BLOCK_HALF;
		int z0 = cz(k) - BLOCK_HALF;
		return new HqFootprints.Area(x0 + depth(1), z0 + depth(2), x0 + BLOCK - 1 - depth(3), z0 + BLOCK - 1 - depth(0));
	}

	/** Parcel {@code p}'s building lot. */
	public static HqFootprints.Area parcelArea(int p) {
		int side = sideOfParcel(p);
		Box b = frame(cellOfParcel(p), side).box(0, 0, 0, length(side) - 1, 0, depth(side) - 1);
		return new HqFootprints.Area(b.minX(), b.minZ(), b.maxX(), b.maxZ());
	}

	/** Every cell a plan whose highest cell is {@code maxCell} may use: cells {@code 0..max(maxCell, MIN_CELLS - 1)}. */
	public static HqFootprints.Area envelope(int maxCell) {
		HqFootprints.Area a = cellArea(0);
		for (int k = 1; k <= Math.max(maxCell, MIN_CELLS - 1); k++) {
			a = a.union(cellArea(k));
		}
		return a;
	}

	/** Highest cell a layout uses (at least {@code MIN_CELLS - 1}): the HQ builds cells {@code 0..builtCells(l)}. */
	public static int maxCell(HqLayout layout) {
		int m = MIN_CELLS - 1;
		for (HqLayout.Module mod : layout.modules()) {
			m = Math.max(m, cellOfParcel(parcelOfModule(mod.index())));
		}
		return m;
	}

	/** Cells in spiral order up to {@code maxCell} (inclusive). */
	public static List<Integer> cells(int maxCell) {
		List<Integer> out = new ArrayList<>();
		for (int k = 0; k <= maxCell; k++) {
			out.add(k);
		}
		return out;
	}
}
