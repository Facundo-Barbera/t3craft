package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Fixed footprint of every HQ module (relative coordinates; origin = hub floor centre, north = -z).
 *
 * <pre>
 *                         spine cap
 *     west wings  ...  [ spine row 1 ]  ...  east wings          wing index i: row i/2,
 *   (odd index)   ...  [ spine row 0 ]  ...  (even index)        east when even, west when odd
 *                     +---------------+
 *                     |      hub      |   board (needs attention) at z = -4, facing the entrance
 *                     +------ ------- +
 *                         portico        entrance z = 8, spawn z = 11 (looking north)
 * </pre>
 *
 * Each wing grows away from the spine: inner wall ({@code u = 0}, doorway), then {@code segments}
 * 4-cell segments (a desk on its north wall and one on its south wall, a pillar column between),
 * then the end cap. {@code v} runs north to south across the wing: walls at {@code v = 0, 12},
 * a garden strip at {@code v = 13} that separates it from the next row. All modules span
 * {@code y = 0} (floor) to {@link #TOP}.
 */
final class HqGeometry {
	static final int TOP = 10;
	/** Hub half size: walls at |x| = 8, |z| = 8. */
	static final int HUB = 8;
	static final int PORTICO_X = 4;
	static final int PORTICO_Z1 = HUB + 1;
	static final int PORTICO_Z2 = HUB + 5;
	/** Spine walls at |x| = 3. */
	static final int SPINE = 3;
	/** Row pitch along the spine (13 cells of wing + 1 garden strip). */
	static final int ROW = 14;
	/** Wing walls at v = 0 and v = WING_WALL, garden at v = ROW - 1. */
	static final int WING_WALL = 12;
	static final int SEGMENT = 4;
	static final int DOOR_V1 = 5;
	static final int DOOR_V2 = 7;
	/** Desk modules span y = 0..DESK_TOP (below the ceiling). */
	static final int DESK_TOP = 4;
	/** The board's plane (its blocks sit at this z, its face looks south). */
	static final int BOARD_Z = -4;
	static final int BOARD_HALF = 3;

	/** Basement (the archive): floor at {@code y = BASEMENT_FLOOR}, ceiling at {@code y = -1}, under the hub. */
	static final int BASEMENT_FLOOR = -5;
	/** Stairwell from the hub down to the basement: columns {@code x = STAIR_X1..STAIR_X2}. */
	static final int STAIR_X1 = 4;
	static final int STAIR_X2 = 5;
	/** Top step (in the hub floor layer) at this z; each step below is one block further north. */
	static final int STAIR_TOP_Z = 6;
	/** Open hub floor (and basement ceiling) over the stairs: {@code z = STAIR_OPEN_Z1..STAIR_OPEN_Z2}. */
	static final int STAIR_OPEN_Z1 = 3;
	static final int STAIR_OPEN_Z2 = 5;

	/** One archive place: the computer cell and the direction its screen faces (the aisle). */
	record ArchiveSlot(BlockPos cell, Direction facing) {
	}

	private HqGeometry() {
	}

	static Box basement() {
		return new Box(-HUB, BASEMENT_FLOOR, -HUB, HUB, -1, HUB);
	}

	/**
	 * Archive slots in a fixed order: shelf rows (north wall, two back-to-back double rows, south
	 * wall), west to east, each column bottom tier then top tier. Slot {@code s} is always the same
	 * cell, so archived computers never move.
	 */
	static List<ArchiveSlot> archiveSlots() {
		List<ArchiveSlot> out = new ArrayList<>();
		addRow(out, -7, -7, 7, Direction.SOUTH);
		addRow(out, -4, -6, 6, Direction.NORTH);
		addRow(out, -3, -6, 6, Direction.SOUTH);
		addRow(out, 0, -6, 2, Direction.NORTH);
		addRow(out, 1, -6, 2, Direction.SOUTH);
		addRow(out, 7, -7, 7, Direction.NORTH);
		return List.copyOf(out);
	}

	private static void addRow(List<ArchiveSlot> out, int z, int x1, int x2, Direction facing) {
		for (int x = x1; x <= x2; x++) {
			out.add(new ArchiveSlot(new BlockPos(x, BASEMENT_FLOOR + 1, z), facing));
			out.add(new ArchiveSlot(new BlockPos(x, BASEMENT_FLOOR + 2, z), facing));
		}
	}

	/**
	 * Growth envelope: the box every plan with at most {@code maxModules} project modules fits in.
	 * Hub, portico and basement always; with modules, the spine runs north one {@link #ROW} per
	 * pair of modules and wings reach {@code |x| = SPINE + 1 + segmentU(SEGMENTS)}.
	 */
	static Box envelope(int maxModules) {
		Box base = hub().union(portico()).union(basement());
		if (maxModules <= 0) {
			return base;
		}
		int rows = (maxModules + 1) / 2;
		int reach = SPINE + 1 + segmentU(HqLayout.SEGMENTS);
		int minX = maxModules >= 2 ? -reach : -HUB;
		return base.union(new Box(minX, 0, spineCap(rows).minZ(), reach, TOP, -HUB - 1));
	}

	static Box hub() {
		return new Box(-HUB, 0, -HUB, HUB, TOP, HUB);
	}

	static Box portico() {
		return new Box(-PORTICO_X, 0, PORTICO_Z1, PORTICO_X, TOP, PORTICO_Z2);
	}

	/** Southmost z of spine row {@code r}. */
	static int rowSouth(int r) {
		return -HUB - 1 - ROW * r;
	}

	/** Northmost z of spine row {@code r} (wing {@code v = 0}). */
	static int rowNorth(int r) {
		return rowSouth(r) - (ROW - 1);
	}

	static Box spineRow(int r) {
		return new Box(-SPINE, 0, rowNorth(r), SPINE, TOP, rowSouth(r));
	}

	static Box spineCap(int rows) {
		int z = rowNorth(rows - 1) - 1;
		return new Box(-SPINE, 0, z, SPINE, TOP, z);
	}

	static int row(int wingIndex) {
		return wingIndex / 2;
	}

	/** +1 east (even index), -1 west (odd index). */
	static int side(int wingIndex) {
		return wingIndex % 2 == 0 ? 1 : -1;
	}

	static Direction outward(int wingIndex) {
		return side(wingIndex) > 0 ? Direction.EAST : Direction.WEST;
	}

	/** World x of wing column {@code u}. */
	static int wx(int wingIndex, int u) {
		return side(wingIndex) * (SPINE + 1 + u);
	}

	/** World z of wing line {@code v}. */
	static int wz(int wingIndex, int v) {
		return rowNorth(row(wingIndex)) + v;
	}

	/** Wing cells from column u1 to u2, lines v1 to v2, heights y1 to y2. */
	static Box wingBox(int wingIndex, int u1, int u2, int v1, int v2, int y1, int y2) {
		return Box.of(wx(wingIndex, u1), y1, wz(wingIndex, v1), wx(wingIndex, u2), y2, wz(wingIndex, v2));
	}

	static Box wingInner(int wingIndex) {
		return wingBox(wingIndex, 0, 0, 0, ROW - 1, 0, TOP);
	}

	static int segmentU(int k) {
		return 1 + SEGMENT * k;
	}

	static Box segment(int wingIndex, int k) {
		return wingBox(wingIndex, segmentU(k), segmentU(k) + SEGMENT - 1, 0, ROW - 1, 0, TOP);
	}

	static Box wingCap(int wingIndex, int segments) {
		int u = segmentU(segments);
		return wingBox(wingIndex, u, u, 0, ROW - 1, 0, TOP);
	}

	/** Slot {@code 2k} is segment k's north desk, {@code 2k+1} its south desk. */
	static boolean northDesk(int slot) {
		return slot % 2 == 0;
	}

	/** Desk row (against the wall) of a slot. */
	static int deskV(int slot) {
		return northDesk(slot) ? 1 : WING_WALL - 1;
	}

	/** Chair row of a slot. */
	static int chairV(int slot) {
		return northDesk(slot) ? 2 : WING_WALL - 2;
	}

	static Box desk(int wingIndex, int slot) {
		int u = segmentU(slot / 2);
		return wingBox(wingIndex, u, u + 2, deskV(slot), chairV(slot), 0, DESK_TOP);
	}

	/** Direction from a slot's chair to its desk (and the occupant's view). */
	static Direction deskFacing(int slot) {
		return northDesk(slot) ? Direction.NORTH : Direction.SOUTH;
	}
}
