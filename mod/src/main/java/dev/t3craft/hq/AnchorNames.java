package dev.t3craft.hq;

/**
 * Anchor names published by the HQ plan ({@link BuildPlan#anchors()}). Other modules (NPC behaviour,
 * screens, the attention board renderer, the world builder) look anchors up by these names only.
 *
 * <p>{@code <threadId>} is the HQ thread id given in {@link HqInput} (direct mode: {@code NpcView.id()},
 * i.e. {@code env:threadId}, unique across environments). {@code <projectKey>} is the project key
 * ({@code NpcView.groupId()}, {@code env:projectId}).
 */
public final class AnchorNames {
	/** Where a player arrives: under the entrance portico, looking into the hub. */
	public static final String SPAWN = "spawn";
	/** The main doorway of the hub (threshold cell), facing out of the building. */
	public static final String ENTRANCE = "entrance";
	/** Centre of the "needs attention" board's front face; facing = the face's normal. */
	public static final String BOARD = "board";
	/** Prefix of the board's header lamps ({@code board_lamp_0..}) left to right as seen from the front. */
	public static final String BOARD_LAMP = "board_lamp_";
	/** Centre of the hub floor. */
	public static final String HUB = "hub";
	/**
	 * The centre block of the hub lantern's cap (on top of the glazed light well, under the lightning
	 * rod): the HQ's aggregate beacon, seen from outside. Plain copper in the vanilla HQ; the blocks
	 * module puts a status lamp bound {@code beacon} there.
	 */
	public static final String BEACON = "beacon";
	/**
	 * Prefix of the standing spots in front of the board ({@code board_slot_0..}), nearest to the
	 * board centre first, facing the board. NPCs that need attention gather here.
	 */
	public static final String BOARD_SLOT = "board_slot_";
	/** Standing spot right in front of the board, foot level, facing the board. */
	public static final String BOARD_FRONT = "board_front";
	/**
	 * Prefix of the hub's resting spots ({@code bench_0..}): standing in front of each bench seat
	 * (the bench stairs have collision), facing into the hub.
	 */
	public static final String BENCH = "bench_";
	/** Prefix of the hub's stroll points ({@code walk_hub_0..}), open floor away from furniture. */
	public static final String WALK_HUB = "walk_hub_";

	/** The standing spot in front of the archive terminal ({@link #ARCHIVE_TERMINAL}), facing it. */
	public static final String ARCHIVE = "archive";
	/** Prefix of the "Archived" shelf's slots ({@code shelf@0..}); see {@link #archiveShelfSlot}. */
	public static final String SHELF_SLOT = "shelf@";
	/**
	 * Face of the "older threads" sign next to the shelf (west of the entrance, facing into the
	 * hub): the click target that lists the settled threads that are no longer on the shelf.
	 */
	public static final String SHELF_MORE = "shelf_more";
	/** Face of the shelf's "Archived" header sign (east of the entrance, facing into the hub). */
	public static final String SHELF_HEADER = "shelf_header";
	/**
	 * Front face of the archive terminal (feat/archive-terminal): the one place in the HQ where closed
	 * threads are browsed. In the nook in the hub's south wall, east of the entrance, screen facing
	 * north into the hub ({@code facing} = the face's normal; {@link #ARCHIVE} is the standing spot in
	 * front of it). Vanilla
	 * HQ: a lectern stands there; with the blocks module, {@code t3craft:archive_terminal}. A redesign
	 * of the hub only has to publish this anchor (any free floor cell, facing where the player stands)
	 * and keep that cell for the terminal; the fixtures follow the anchor.
	 */
	public static final String ARCHIVE_TERMINAL = "archiveTerminal";

	/**
	 * The hub's big screen (P1 multiblock): face centre of the bottom-middle cell of a 5 x 3 cell
	 * surface on the east interior wall, looking west. Cells: {@code block} ± 2 along z, up 2.
	 */
	public static final String HUB_SCREEN = "hub_screen";
	/**
	 * The thread kanban wall (P3): face centre of the bottom-middle cell of a 9 x 4 cell surface on the
	 * hub's west interior wall, looking east. Cells: {@code block} ± 4 along z, up 3.
	 */
	public static final String THREAD_WALL = "thread_wall";
	/** Where viewers of the kanban wall stand (open floor three cells in front of its middle). */
	public static final String THREAD_WALL_FRONT = "thread_wall_front";
	/**
	 * Point in the air over the hub centre where the hologram (P4) floats; the column from the hub floor
	 * up to it is kept free of blocks in every style.
	 */
	public static final String HOLOGRAM = "hologram";

	/**
	 * A module's big screen (P1): face centre of the bottom-middle cell of a 3 x 2 cell surface on the
	 * module's end cap (on a base, at the end of the corridor), looking towards the spine. Cells: {@code block} ± 1 along
	 * z, up 1.
	 */
	public static String moduleScreen(int index) {
		return "module_screen@" + index;
	}

	private AnchorNames() {
	}

	/**
	 * True for anchors an NPC stands in (feet cell free and walkable, see {@link Anchor#block()}),
	 * false for surface anchors (board, lamps, monitors, screens, archived computers, the shelf's
	 * slots and signs, the beacon) that only mark a block face.
	 */
	public static boolean standing(String name) {
		if (name.equals(BOARD) || name.startsWith(BOARD_LAMP) || name.startsWith("monitor_") || name.startsWith("lamp_")
				|| name.startsWith("screen@") || name.startsWith("archive_") || name.startsWith(SHELF_SLOT) || name.equals(SHELF_MORE)
				|| name.equals(SHELF_HEADER) || name.equals(BEACON) || name.equals(HUB_SCREEN) || name.equals(THREAD_WALL)
				|| name.equals(HOLOGRAM) || name.startsWith("module_screen@")) {
			return false;
		}
		return name.equals(SPAWN) || name.equals(ENTRANCE) || name.equals(HUB) || name.equals(BOARD_FRONT) || name.equals(ARCHIVE)
				|| name.equals(THREAD_WALL_FRONT) || name.startsWith(BOARD_SLOT) || name.startsWith(BENCH) || name.startsWith("walk") || name.startsWith("desk")
				|| name.startsWith("wing_") || name.startsWith("module@");
	}

	/**
	 * Where a thread's NPC works: the free cell in front of its computer (air at feet and head, desk
	 * floor below), standing and looking at the monitor. There are no chairs.
	 */
	public static String desk(String threadId) {
		return "desk_" + threadId;
	}

	/** Centre of the front face of a thread's monitor block. */
	public static String monitor(String threadId) {
		return "monitor_" + threadId;
	}

	/** A thread's status lamp (a waxed copper bulb; toggle {@code lit} to show state). */
	public static String lamp(String threadId) {
		return "lamp_" + threadId;
	}

	/** The doorway of a project's wing, standing inside the wing looking in. */
	public static String wing(String projectKey) {
		return "wing_" + projectKey;
	}

	public static String boardLamp(int i) {
		return BOARD_LAMP + i;
	}

	public static String boardSlot(int i) {
		return BOARD_SLOT + i;
	}

	public static String bench(int i) {
		return BENCH + i;
	}

	public static String walkHub(int i) {
		return WALK_HUB + i;
	}

	/**
	 * A project's stroll point: the middle of its wing's corridor (segment 1, which every wing has),
	 * where idle NPCs of that project wander. It never moves when the wing grows.
	 */
	public static String walk(String projectKey) {
		return "walk_" + projectKey;
	}

	/** The doorway of module {@code index} (inside, looking in). {@link #wing} is an alias for a project's first module. */
	public static String module(int index) {
		return "module@" + index;
	}

	/** Stroll point of module {@code index}; {@link #walk} is an alias for a project's first module. */
	public static String walkModule(int index) {
		return "walk@" + index;
	}

	/** Name of a desk anchor by place (also for vacant desks; see {@link Workstation}). */
	public static String deskSlot(int module, int slot) {
		return "desk@" + module + "." + slot;
	}

	public static String screenSlot(int module, int slot) {
		return "screen@" + module + "." + slot;
	}

	/**
	 * Shelf slot {@code i} of the "Archived" shelf ({@code 0 <= i < HqLayout.ARCHIVE_CAPACITY}),
	 * published for every slot, taken or not: the face of the slot's block, facing into the hub.
	 * Order: eye-level tier first, then top, then bottom; in each tier the columns nearest the
	 * entrance first, east before west. Stable: slot {@code i} is always the same cell.
	 */
	public static String archiveShelfSlot(int i) {
		return SHELF_SLOT + i;
	}

	/** Screen face of a settled thread's computer on the "Archived" shelf (same cell as its {@link #archiveShelfSlot}). */
	public static String archive(String threadId) {
		return "archive_" + threadId;
	}
}
