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

	/**
	 * Prefix of the large screens the plan reserves ({@link #bigScreen}). With the blocks module on,
	 * each cell of the rectangle becomes a {@code t3craft:monitor} and the cells merge into one screen;
	 * without it they stay whatever the generator put there.
	 */
	public static final String BIG_SCREEN = "bigscreen_";

	private AnchorNames() {
	}

	/**
	 * A large screen reserved by the plan, {@code width × height} blocks, showing {@code binding} (a
	 * blocks-module binding string, e.g. {@code "project:" + projectKey}, {@code "env:" + envId} or
	 * {@code "thread:" + threadId}; empty = unbound). {@code key} (no {@code =}) names the spot and must be unique in
	 * the plan ({@code "hub"}, {@code "module@3"}, ...). Publish it as
	 * {@code Anchor.face(name, topLeft, facing)}: the anchor's block is the <b>top-left</b> cell as seen
	 * from the front, its facing the screen's normal. The generator must place a block (any solid
	 * one; a black wall reads well in the vanilla HQ) in every cell: the blocks module only swaps
	 * existing cells. Name format: {@code bigscreen_<key>@<w>x<h>=<binding>}.
	 */
	public static String bigScreen(String key, int width, int height, String binding) {
		if (key.isEmpty() || key.indexOf('=') >= 0 || width < 1 || height < 1) {
			throw new IllegalArgumentException("bad big screen: " + key + " " + width + "x" + height);
		}
		return BIG_SCREEN + key + "@" + width + "x" + height + "=" + (binding == null ? "" : binding);
	}

	/** A parsed {@link #bigScreen} anchor name. */
	public record BigScreen(String key, int width, int height, String binding) {
	}

	/** Parses a {@link #bigScreen} name; null when {@code name} is not one (or is malformed). */
	public static @org.jspecify.annotations.Nullable BigScreen parseBigScreen(String name) {
		if (name == null || !name.startsWith(BIG_SCREEN)) {
			return null;
		}
		// the key may contain '@' (module@3) but not '='; the size is between the last '@' and the first '='
		int eq = name.indexOf('=', BIG_SCREEN.length());
		int at = eq < 0 ? -1 : name.lastIndexOf('@', eq);
		int x = at < 0 ? -1 : name.indexOf('x', at);
		if (at <= BIG_SCREEN.length() || x < 0 || x > eq) {
			return null;
		}
		try {
			int w = Integer.parseInt(name.substring(at + 1, x));
			int h = Integer.parseInt(name.substring(x + 1, eq));
			return w < 1 || h < 1 ? null : new BigScreen(name.substring(BIG_SCREEN.length(), at), w, h, name.substring(eq + 1));
		} catch (NumberFormatException e) {
			return null;
		}
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
