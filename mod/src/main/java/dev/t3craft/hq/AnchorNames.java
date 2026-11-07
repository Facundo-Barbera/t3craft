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
	 * Prefix of the standing spots in front of the board ({@code board_slot_0..}), nearest to the
	 * board centre first, facing the board. NPCs that need attention gather here.
	 */
	public static final String BOARD_SLOT = "board_slot_";
	/** Standing spot right in front of the board, foot level, facing the board. */
	public static final String BOARD_FRONT = "board_front";
	/** Prefix of the hub benches' seats ({@code bench_0..}), facing into the hub. Resting spots. */
	public static final String BENCH = "bench_";
	/** Prefix of the hub's stroll points ({@code walk_hub_0..}), open floor away from furniture. */
	public static final String WALK_HUB = "walk_hub_";

	/** A standing spot in front of the hub's "Archived" shelf (south wall, east of the entrance), facing it. */
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

	private AnchorNames() {
	}

	/** The seat of a thread's desk: the NPC sits here looking at its monitor. */
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
