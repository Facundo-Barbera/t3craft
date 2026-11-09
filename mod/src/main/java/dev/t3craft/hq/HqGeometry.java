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
 *                     +--shelf  shelf-+   "Archived" shelf: south wall, both sides of the entrance
 *                         portico        entrance z = 8, spawn z = 11 (looking north)
 * </pre>
 *
 * Each wing grows away from the spine: inner wall ({@code u = 0}, doorway), then {@code segments}
 * 4-cell segments (a desk on its north wall and one on its south wall, a pillar column between),
 * then the end cap. {@code v} runs north to south across the wing: walls at {@code v = 0, 12},
 * a garden strip at {@code v = 13} that separates it from the next row. All modules span
 * {@code y = 0} (floor) to {@link #TOP}.
 */
public final class HqGeometry {
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
	/** Cells per desk segment of a wing; public for the client's NPC anchor adapter. */
	public static final int SEGMENT = 4;
	static final int DOOR_V1 = 5;
	static final int DOOR_V2 = 7;
	/** Desk modules span y = 0..DESK_TOP (below the ceiling). */
	static final int DESK_TOP = 4;
	/** The board's plane (its blocks sit at this z, its face looks south). */
	static final int BOARD_Z = -4;
	static final int BOARD_HALF = 3;

	/**
	 * The "Archived" shelf: the hub's south wall, both sides of the entrance ({@code z = SHELF_Z},
	 * {@code SHELF_X1 <= |x| <= SHELF_X2}), three tiers ({@code y = 1..SHELF_TIERS}) facing north into
	 * the hub, header signs on {@code y = SHELF_TIERS + 1}.
	 */
	static final int SHELF_Z = HUB - 1;
	static final int SHELF_X1 = 3;
	static final int SHELF_X2 = 6;
	static final int SHELF_TIERS = 3;
	/** Tiers in slot order: eye level first, then top, then bottom. */
	private static final int[] SHELF_TIER_ORDER = {2, 3, 1};

	/**
	 * The archive terminal's nook, set into the hub's south wall east of the entrance: a 3-wide alcove
	 * ({@code x = 4..6, z = ARCHIVE_Z}, {@code y = 1..4}) holding the archive terminal on the floor
	 * ({@link #ARCHIVE_TERMINAL}, {@link AnchorNames#ARCHIVE_TERMINAL}, screen facing north into the
	 * hub) with a standing spot in front ({@code z = ARCHIVE_Z - 1}). One terminal lists every
	 * archived thread (feat/archive-terminal); there are no per-thread shelves.
	 */
	static final int ARCHIVE_Z = HUB - 1;
	static final BlockPos ARCHIVE_TERMINAL = new BlockPos(5, 1, ARCHIVE_Z);
	static final BlockPos ARCHIVE_STAND = new BlockPos(5, 1, ARCHIVE_Z - 1);

	static Box archiveNook() {
		return new Box(4, 1, ARCHIVE_Z, 6, 4, ARCHIVE_Z);
	}

	/**
	 * Reserved screen surfaces, for the big multiblock screens (P1) and the thread kanban wall (P3).
	 * Each is a vertical rectangle of cells, written by the generator with the style's screen stand-in
	 * and published as an anchor (big screens: {@link AnchorNames#bigScreen} at the top-left cell; the
	 * kanban wall: {@link AnchorNames#THREAD_WALL} at the bottom-left cell, {@link AnchorNames#THREAD_WALL_END}
	 * at the top-right one).
	 *
	 * @param origin bottom cell at the left end as seen from the front
	 * @param along  direction the rectangle extends to the right as seen from the front
	 * @param facing outward normal of the visible face
	 */
	public record Surface(BlockPos origin, Direction along, Direction facing, int width, int height) {
		public List<BlockPos> cells() {
			List<BlockPos> out = new ArrayList<>();
			for (int y = 0; y < height; y++) {
				for (int i = 0; i < width; i++) {
					out.add(origin.relative(along, i).above(y));
				}
			}
			return out;
		}

		/** Bottom-middle cell (the anchor's block). */
		public BlockPos anchorCell() {
			return origin.relative(along, width / 2);
		}
	}

	/** Hub screen: east interior wall face, 5 wide x 3 tall, looking west into the hub. */
	static Surface hubScreen() {
		return new Surface(new BlockPos(HUB - 1, 2, -2), Direction.SOUTH, Direction.WEST, 5, 3);
	}

	/** Thread kanban wall: west interior wall face, 9 wide x 4 tall, looking east into the hub. */
	static Surface threadWall() {
		return new Surface(new BlockPos(-HUB + 1, 1, 4), Direction.NORTH, Direction.EAST, 9, 4);
	}

	/**
	 * A module's big screen: standing just inside its end cap (the last segment's pillar column, at the
	 * end of the corridor), facing the corridor towards the spine, 3 wide x 2 tall.
	 */
	static Surface moduleScreen(int wingIndex, int segments) {
		int u = segmentU(segments) - 1;
		Direction in = outward(wingIndex).getOpposite();
		// left as seen from the corridor looking at the cap: east wing looks east, left is north
		Direction along = side(wingIndex) > 0 ? Direction.SOUTH : Direction.NORTH;
		int v0 = side(wingIndex) > 0 ? DOOR_V1 : DOOR_V2;
		return new Surface(new BlockPos(wx(wingIndex, u), 2, wz(wingIndex, v0)), along, in, 3, 2);
	}

	/** The hologram's airspace over the hub centre (kept free from y = 1 to {@link #HOLOGRAM_TOP}). */
	public static final int HOLOGRAM_TOP = 8;

	/** One shelf place: the computer cell and the direction its screen faces (into the hub). */
	record ArchiveSlot(BlockPos cell, Direction facing) {
	}

	private HqGeometry() {
	}

	/** The two shelf boxes (east, west), header row included; holes of the hub filled by the archive group. */
	static List<Box> shelves() {
		return List.of(new Box(SHELF_X1, 1, SHELF_Z, SHELF_X2, SHELF_TIERS + 1, SHELF_Z),
				new Box(-SHELF_X2, 1, SHELF_Z, -SHELF_X1, SHELF_TIERS + 1, SHELF_Z));
	}

	/**
	 * Shelf slots in a fixed order: tier by tier (eye level, top, bottom), and in each tier the
	 * columns nearest the entrance first, east before west ({@code x = 3, -3, 4, -4, ..}). Slot
	 * {@code s} is always the same cell, so a shelved computer never moves while it stays shelved.
	 */
	static List<ArchiveSlot> archiveSlots() {
		List<ArchiveSlot> out = new ArrayList<>();
		for (int y : SHELF_TIER_ORDER) {
			for (int ax = SHELF_X1; ax <= SHELF_X2; ax++) {
				out.add(new ArchiveSlot(new BlockPos(ax, y, SHELF_Z), Direction.NORTH));
				out.add(new ArchiveSlot(new BlockPos(-ax, y, SHELF_Z), Direction.NORTH));
			}
		}
		return List.copyOf(out);
	}

	/**
	 * Growth envelope: the box every plan with at most {@code maxModules} project modules fits in.
	 * Hub and portico always (the "Archived" shelf is inside the hub); with modules, the spine runs north one {@link #ROW} per
	 * pair of modules and wings reach {@code |x| = SPINE + 1 + segmentU(SEGMENTS)}.
	 */
	static Box envelope(int maxModules) {
		Box base = hub().union(portico());
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

	/**
	 * Standing row of a slot: the free cell right in front of the computer, where the NPC works
	 * standing (no chair; air at feet and head, desk floor below).
	 */
	static int chairV(int slot) {
		return northDesk(slot) ? 2 : WING_WALL - 2;
	}

	static Box desk(int wingIndex, int slot) {
		int u = segmentU(slot / 2);
		return wingBox(wingIndex, u, u + 2, deskV(slot), chairV(slot), 0, DESK_TOP);
	}

	/** Direction from a slot's standing cell to its desk (and the NPC's view). */
	static Direction deskFacing(int slot) {
		return northDesk(slot) ? Direction.NORTH : Direction.SOUTH;
	}
}
