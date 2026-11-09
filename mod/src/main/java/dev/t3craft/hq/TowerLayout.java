package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Geometry of the <b>vertical</b> HQ (layout/vertical, style {@value VerticalStyle#ID}): towers with an
 * atrium, joined by skybridges and a common podium with a plaza. Everything here is fixed: a module's
 * place depends only on its {@link HqLayout.Module#index() index} (its <b>parcel</b>), so the same
 * layout always gives the same buildings and adding a project never moves what already stands.
 *
 * <h2>Parcels</h2>
 * A parcel is <b>half a floor</b> of a tower (the east or the west wing around the atrium, 6 desks).
 * Parcel {@code i}: tower {@link #parcel(int)}, floor {@code 1 + (i - first) / 2}, east when
 * {@code (i - first)} is even. {@link HqLayout#allocate} already gives a new module the lowest free
 * index and frees the index of a module that empties, so the floors of tower 1 fill first (bottom up,
 * east then west), then tower 2 is born, then tower 3; a freed half floor is the first one reused.
 *
 * <h2>Towers</h2>
 * <ul>
 *   <li><b>T1 "Atrio"</b> (always built): 43 x 31, 5 project floors of 8 blocks; a 13 x 13 atrium of the
 *   whole height holds the hub (board, big screen, benches, the hologram's free column and the
 *   beacon in the glass cupola on the roof); kanban wall on the first-floor balcony; a glass review
 *   bridge crosses the atrium on floor 3; the roof is the lookout (scheduled tasks).</li>
 *   <li><b>T2 "Jardín"</b> (parcels 10..23): east, set back north, 35 x 31, 7 floors of 7 blocks (the
 *   tallest), small light well, garden roof with a timber crown.</li>
 *   <li><b>T3 "Piedra"</b> (parcels 24..31): west, set further back, 39 x 31, 4 floors of 9 blocks (the
 *   lowest and widest floors), crenellated roof with a water tank.</li>
 * </ul>
 * Every tower climbs by a monumental switchback stair on the south side of its atrium (two lanes:
 * even floors climb east, odd floors west), so NPCs walk every floor. T2 joins T1 by the ground
 * podium and two skybridges (T1 floor 2 → T2 floor 2, T1 floor 5 → T2 floor 6); T3 by the ground
 * podium and one skybridge (T1 floor 3 → T3 floor 3). Bridges between different floor heights
 * climb with steps.
 */
public final class TowerLayout {
	private TowerLayout() {
	}

	/** The kind of floor plan of a half floor. */
	public enum Plan {
		/** One open hall: three desk islands, back to back. */
		OPEN,
		/** A corridor and three small glass rooms, one desk island each. */
		CELLS,
		/** A raised dais (one step) with two rows of desks facing the facade. */
		MEZZANINE
	}

	/**
	 * One tower. Coordinates: centre {@code (cx, cz)}; plate {@code cx±px, cz±pz}; atrium (void)
	 * {@code ±atrium}; gallery ring out to {@code ±gallery}; the halves (parcels) from {@code gallery+1}
	 * (partition) to {@code px} (facade). Floor {@code f} has its slab at {@code y = f * floorH}; floor
	 * 0 is the lobby; floors {@code 1..floors} are parcels; the roof slab is at {@code (floors+1) * floorH}.
	 */
	public record Tower(int id, String name, int cx, int cz, int px, int pz, int atrium, int gallery, int floorH, int floors,
			int firstParcel, List<Plan> plans) {
		public int parcels() {
			return 2 * floors;
		}

		public int floorY(int f) {
			return f * floorH;
		}

		public int roofY() {
			return (floors + 1) * floorH;
		}

		/** Stair blocks per flight (the landing is the last rise). */
		public int run() {
			return floorH - 1;
		}

		/** Local x of a flight's first stair block (flights span {@code x0 .. x0+run-1}). */
		public int x0() {
			return -run() / 2;
		}

		/** Interior width of a half (cells between partition and facade). */
		public int width() {
			return px - gallery - 2;
		}

		public boolean hasParcel(int index) {
			return index >= firstParcel && index < firstParcel + parcels();
		}

		public HqFootprints.Area plate() {
			return new HqFootprints.Area(cx - px, cz - pz, cx + px, cz + pz);
		}

		public Plan plan(int floor) {
			return plans.get(floor - 1);
		}
	}

	/** A half floor: tower, floor, side ({@code +1} east, {@code -1} west). */
	public record Parcel(int index, Tower tower, int floor, int side) {
	}

	/** Lane of the southern stair band a flight from floor {@code f} uses: lane A (0) on even floors. */
	static int lane(int f) {
		return f & 1;
	}

	/** First local z row of lane 0 / 1 (two rows each); the balustrade is at {@link #BALUSTRADE_Z}. */
	static int laneZ(int lane) {
		return lane == 0 ? 11 : 13;
	}

	static final int BALUSTRADE_Z = 10;

	public static final Tower T1 = new Tower(1, "Atrio", 0, 0, 21, 15, 6, 9, 8, 5, 0,
			List.of(Plan.OPEN, Plan.CELLS, Plan.MEZZANINE, Plan.OPEN, Plan.CELLS));
	public static final Tower T2 = new Tower(2, "Jardin", 54, -14, 17, 15, 3, 6, 7, 7, 10,
			List.of(Plan.MEZZANINE, Plan.OPEN, Plan.CELLS, Plan.MEZZANINE, Plan.OPEN, Plan.CELLS, Plan.OPEN));
	public static final Tower T3 = new Tower(3, "Piedra", -54, -22, 19, 15, 4, 8, 9, 4, 24,
			List.of(Plan.CELLS, Plan.OPEN, Plan.MEZZANINE, Plan.OPEN));
	public static final List<Tower> TOWERS = List.of(T1, T2, T3);

	/** Modules the three towers hold; modules with a higher index are not built. */
	public static final int CAPACITY = T3.firstParcel() + T3.parcels();

	/** Highest y any block of the vertical HQ uses (top of the antenna on the T1 lookout). */
	public static final int TOP = 62;

	// ------------------------------------------------------------------ fixed areas

	/** Podium front (entrance hall, archive, café; its roof is the lounge terrace), in front of T1. */
	public static final Box PODIUM_FRONT = Box.of(-21, 0, 16, 21, 8, 23);
	/** The lounge terrace on the podium roof (level with T1 floor 1). */
	public static final Box LOUNGE = Box.of(-21, 9, 16, 21, 13, 23);
	/** The plaza in front of the podium. */
	public static final Box PLAZA = Box.of(-44, 0, 24, 44, 7, 46);
	/** The glass review bridge across T1's atrium on floor 3. */
	public static final Box REVIEW = Box.of(1, 3 * 8, -6, 5, 3 * 8 + 4, 6);
	/** Ground podium halls joining T1 to T2 (east) and T3 (west). */
	public static final Box PODIUM_EAST = Box.of(22, 0, -9, 36, 6, 1);
	public static final Box PODIUM_WEST = Box.of(-34, 0, -15, -22, 6, -7);

	/**
	 * A skybridge from T1's facade ({@code xa}, slab {@code ya}) to the other tower's facade
	 * ({@code xb}, slab {@code yb}), deck rows {@code z1..z2} (walls one row outside).
	 */
	public record Bridge(String id, Tower to, int xa, int ya, int xb, int yb, int z1, int z2) {
		public Box box() {
			return Box.of(xa, Math.min(ya, yb) - 1, z1 - 1, xb, Math.max(ya, yb) + 4, z2 + 1);
		}

		/** +1 when the bridge runs towards +x. */
		public int dir() {
			return xb > xa ? 1 : -1;
		}
	}

	public static final List<Bridge> BRIDGES = List.of(
			new Bridge("bridge_e2", T2, 22, T1.floorY(2), 36, T2.floorY(2), -4, -2),
			new Bridge("bridge_e5", T2, 22, T1.floorY(5), 36, T2.floorY(6), -12, -10),
			new Bridge("bridge_w3", T3, -22, T1.floorY(3), -34, T3.floorY(3), -12, -10));

	/** Ground doors of the podium halls in the facades (rows z1..z2 of the given world facade x). */
	static final int PODIUM_EAST_Z1 = -5;
	static final int PODIUM_EAST_Z2 = -3;
	static final int PODIUM_WEST_Z1 = -12;
	static final int PODIUM_WEST_Z2 = -10;

	// ------------------------------------------------------------------ parcels

	/** The parcel of module {@code index}, or null beyond {@link #CAPACITY}. */
	public static @Nullable Parcel parcel(int index) {
		for (Tower t : TOWERS) {
			if (t.hasParcel(index)) {
				int k = index - t.firstParcel();
				return new Parcel(index, t, 1 + k / 2, (k & 1) == 0 ? 1 : -1);
			}
		}
		return null;
	}

	/** Towers a layout needs: T1 always, T2/T3 while one of their parcels holds a module. */
	public static Set<Integer> towers(HqLayout layout) {
		Set<Integer> out = new TreeSet<>();
		out.add(1);
		for (HqLayout.Module m : layout.modules()) {
			Parcel p = parcel(m.index());
			if (p != null) {
				out.add(p.tower().id());
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ footprints

	/** Plaza, podium front and T1 (always built). */
	public static HqFootprints.Area base() {
		return area(PLAZA).union(area(PODIUM_FRONT)).union(T1.plate());
	}

	/** What tower {@code t} (2 or 3) adds: its plate, its podium hall and its skybridges. */
	public static HqFootprints.Area towerArea(Tower t) {
		HqFootprints.Area a = t.plate().union(area(t == T2 ? PODIUM_EAST : PODIUM_WEST));
		for (Bridge b : BRIDGES) {
			if (b.to() == t) {
				a = a.union(area(b.box()));
			}
		}
		return a;
	}

	/** Every cell the vertical HQ may use with {@code towers} towers (1..3). */
	public static HqFootprints.Area envelope(int towers) {
		HqFootprints.Area a = base();
		for (int i = 1; i < Math.min(towers, TOWERS.size()); i++) {
			a = a.union(towerArea(TOWERS.get(i)));
		}
		return a;
	}

	/** Every footprint area by name (base, tower_2, tower_3), for docs and tests. */
	public static List<HqFootprints.Area> areas() {
		List<HqFootprints.Area> out = new ArrayList<>();
		out.add(base());
		out.add(towerArea(T2));
		out.add(towerArea(T3));
		return out;
	}

	/**
	 * The sample roster of the docs and renders: {@code projects} projects over four environments with
	 * 1 to 9 active threads each (more than 6 → an annex half floor) and a settled thread every third
	 * project. Deterministic.
	 */
	public static HqInput sampleRoster(int projects) {
		String[] names = {"t3craft", "bridge", "website", "infra", "docs", "mobile", "billing", "search", "agentcraft", "notes",
				"vision", "ledger", "atlas", "pulse", "relay", "orbit", "quartz", "harbor", "spark", "mosaic"};
		String[] envs = {"vps", "laptop", "desktop", "cloud"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			String name = p < names.length ? names[p] : "project" + p;
			String env = envs[p % envs.length];
			String key = env + ":" + name;
			int threads = 1 + (p * 7 + 3) % 9;
			for (int t = 0; t < threads; t++) {
				b.thread(key, name, env, env.toUpperCase(java.util.Locale.ROOT), key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, name, env, env.toUpperCase(java.util.Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	static HqFootprints.Area area(Box b) {
		return new HqFootprints.Area(b.minX(), b.minZ(), b.maxX(), b.maxZ());
	}
}
