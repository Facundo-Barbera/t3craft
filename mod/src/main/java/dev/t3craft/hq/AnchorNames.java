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
}
