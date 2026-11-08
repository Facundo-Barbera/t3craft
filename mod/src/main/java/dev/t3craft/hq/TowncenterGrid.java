package dev.t3craft.hq;

import net.minecraft.core.Direction;

/**
 * Ground plan of the "hybrid-towncenter" HQ ("Centro urbano con torres"): the ensanche's manzanas,
 * streets, chamfers, patios and covered passages ({@link EnsancheGrid}: same spiral of cells, same
 * four parcels per work manzana, same frames), with selective height. All coordinates are relative
 * to the HQ origin (centre of the plaza mayor, {@code y = 0} the street level, north = {@code -z}).
 *
 * <h2>Rooms</h2>
 * A parcel's building is a one-storey <b>podium</b> (floor 0, the ground-floor room along the street,
 * with its doors to the patio) and, once its project needs a second room, a <b>tower</b> rising on
 * the parcel's chamfered corner of the manzana: {@link #TOWER_LEN} blocks along the street, the
 * building's depth across, with a stair atrium in the middle (a switchback stair in a two-lane void
 * through every floor, under a skylight). HQ module index {@code i = FLOORS * p + floor}: floor 0 is
 * the podium room, floors {@code 1..TOWER_MAX} the tower's rooms (6 desks each).
 *
 * <h2>Skybridges</h2>
 * Every parcel's corner faces a corner of a neighbouring manzana across each of its two streets. A
 * tower's street face ({@code d = 0}) and the facing tower's end face ({@code a = 0}) have their door
 * cells at exactly the same place (the pinwheel is symmetric), so two facing towers are joined by a
 * glazed skybridge across the street on floor {@link #BRIDGE_FLOOR} ({@link #streetPartner}).
 */
public final class TowncenterGrid {
	public static final String ID = "hybrid-towncenter";
	/** Module indices per parcel: floor 0 (podium) plus the tower floors. */
	public static final int FLOORS = 6;
	/** Highest tower floor with rooms. */
	public static final int TOWER_MAX = FLOORS - 1;
	/** A tower is never shorter than this many floors above the podium (rooms or not; see {@link #minTower}). */
	public static final int TOWER_MIN = 3;
	/** Tower length along the street, from the chamfered corner. */
	public static final int TOWER_LEN = 16;
	/** Floor of the skybridges (y = {@code STOREY * BRIDGE_FLOOR}). */
	public static final int BRIDGE_FLOOR = 2;
	public static final int STOREY = EnsancheGrid.STOREY;
	/** Landing column of the stair atrium; steps at {@code a = LANDING + 1 .. LANDING + 6}, upper landing at {@code LANDING + 7}. */
	public static final int LANDING = 4;
	/** First door column of the skybridge doors ({@code a} on the street face, {@code d} on the end face): two cells wide. */
	public static final int BRIDGE_DOOR = 5;
	/** Highest y the town uses (clock tower's lightning rod). */
	public static final int TOP = 48;
	/** Cells always built: the plaza, its first neighbour, the first small square and the market. */
	public static final int MIN_CELLS = EnsancheGrid.MIN_CELLS;

	private TowncenterGrid() {
	}

	// ------------------------------------------------------------------ modules

	public static int module(int parcel, int floor) {
		return parcel * FLOORS + floor;
	}

	public static int parcelOfModule(int moduleIndex) {
		return moduleIndex / FLOORS;
	}

	public static int floorOfModule(int moduleIndex) {
		return moduleIndex % FLOORS;
	}

	/** Whether module index {@code i} is a room of a work manzana (anything else is reallocated). */
	public static boolean validModule(int i) {
		return i >= 0 && EnsancheGrid.kind(EnsancheGrid.cellOfParcel(parcelOfModule(i))) == EnsancheGrid.Kind.WORK;
	}

	/**
	 * Tower floors built on parcel {@code p} when its highest room is on {@code maxFloor}: none for a
	 * podium-only building; otherwise at least the parcel's own minimum (3 to 5 floors, a pure function
	 * of {@code p}, so the skyline is not flat and a tower only grows when its rooms need it).
	 */
	public static int towerFloors(int p, int maxFloor) {
		return maxFloor <= 0 ? 0 : Math.max(minTower(p), maxFloor);
	}

	/** The parcel's minimum tower height: 3, 4 or 5 floors. */
	public static int minTower(int p) {
		int h = p * 0x9E3779B1 ^ 29 * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h = (h ^ (h >>> 13)) & 0x7fffffff;
		return TOWER_MIN + h % (TOWER_MAX - TOWER_MIN + 1);
	}

	/** Lane of the even flights (floor 0 → 1, 2 → 3, ...); the odd flights use the next one. */
	public static int lane(int depth) {
		return depth / 2 - 1;
	}

	// ------------------------------------------------------------------ footprints

	/** Highest cell a layout uses (at least {@code MIN_CELLS - 1}). */
	public static int maxCell(HqLayout layout) {
		int m = MIN_CELLS - 1;
		for (HqLayout.Module mod : layout.modules()) {
			m = Math.max(m, EnsancheGrid.cellOfParcel(parcelOfModule(mod.index())));
		}
		return m;
	}

	public static HqFootprints.Area envelope(int maxCell) {
		return EnsancheGrid.envelope(maxCell);
	}

	// ------------------------------------------------------------------ skybridges

	/**
	 * The parcel whose tower end face ({@code a = 0}) faces parcel {@code p}'s street face across the
	 * street, or -1 when the cell over there is not a work manzana.
	 */
	public static int streetPartner(int p) {
		int k = EnsancheGrid.cellOfParcel(p);
		int side = EnsancheGrid.sideOfParcel(p);
		return across(k, EnsancheGrid.frame(k, side).street(), (side + 1) % EnsancheGrid.SIDES);
	}

	/** The parcel whose street face faces parcel {@code p}'s end face ({@code a = 0}), or -1. */
	public static int endPartner(int p) {
		int k = EnsancheGrid.cellOfParcel(p);
		int side = EnsancheGrid.sideOfParcel(p);
		return across(k, EnsancheGrid.frame(k, side).along().getOpposite(), (side + 3) % EnsancheGrid.SIDES);
	}

	private static int across(int k, Direction dir, int side) {
		int[] c = EnsancheGrid.cell(k);
		int k2 = EnsancheGrid.index(c[0] + dir.getStepX(), c[1] + dir.getStepZ());
		return EnsancheGrid.kind(k2) == EnsancheGrid.Kind.WORK ? EnsancheGrid.parcel(k2, side) : -1;
	}

	/** The skybridge door cells in parcel {@code p}'s street face (a hole of its building). */
	public static Box streetDoor(int p) {
		int y = STOREY * BRIDGE_FLOOR;
		return frameOf(p).box(BRIDGE_DOOR, y + 1, 0, BRIDGE_DOOR + 1, y + 2, 0);
	}

	/** The skybridge door cells in parcel {@code p}'s end face. */
	public static Box endDoor(int p) {
		int y = STOREY * BRIDGE_FLOOR;
		return frameOf(p).box(0, y + 1, BRIDGE_DOOR, 0, y + 2, BRIDGE_DOOR + 1);
	}

	/** The span of the skybridge leaving parcel {@code p}'s street face: walls, floor and roof over the street. */
	public static Box bridgeSpan(int p) {
		int y = STOREY * BRIDGE_FLOOR;
		return frameOf(p).box(BRIDGE_DOOR - 1, y, -1, BRIDGE_DOOR + 2, y + 3, -EnsancheGrid.STREET);
	}

	static EnsancheGrid.Frame frameOf(int p) {
		return EnsancheGrid.frame(EnsancheGrid.cellOfParcel(p), EnsancheGrid.sideOfParcel(p));
	}
}
