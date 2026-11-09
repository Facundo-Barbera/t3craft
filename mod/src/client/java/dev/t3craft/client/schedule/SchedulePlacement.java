package dev.t3craft.client.schedule;

import dev.t3craft.client.npc.behavior.Motion;

/**
 * Where the schedule board's text sits: the TextDisplay's position (bottom centre of the text) and
 * the yaw its text faces. Plain numbers, no Minecraft classes, unit-testable.
 *
 * <p>Yaw follows Minecraft: 0 = facing south (+z), 90 = west (−x); a FIXED TextDisplay is readable
 * from the side it faces.
 */
public record SchedulePlacement(double x, double y, double z, float yaw) {
	/** Text bottom below the centre of the HQ board's face (the face spans ±1.5 blocks vertically). */
	public static final double BOARD_DROP = 1.35;
	/** How far in front of a face the text floats (avoids z-fighting with the slate). */
	public static final double FACE_GAP = 0.05;

	/**
	 * On a board face: {@code (cx, cy, cz)} is the centre of the face, {@code faceYaw} its outward
	 * normal as a yaw (what {@code dev.t3craft.hq.plan.Anchor} gives for {@code AnchorNames.BOARD}).
	 */
	public static SchedulePlacement onFace(double cx, double cy, double cz, float faceYaw) {
		double rad = Math.toRadians(faceYaw);
		return new SchedulePlacement(cx - Math.sin(rad) * FACE_GAP, cy - BOARD_DROP, cz + Math.cos(rad) * FACE_GAP, Motion.wrapDegrees(faceYaw));
	}

	/** The HQ's "board" anchor (centre of the board's front face, facing out). */
	public static SchedulePlacement onBoard(dev.t3craft.hq.plan.Anchor board) {
		return onFace(board.x(), board.y(), board.z(), board.yaw());
	}

	/**
	 * Floating {@code distance} blocks in front of someone standing at {@code (x, y, z)} looking along
	 * {@code yaw}, bottom {@code height} above their feet, turned back towards them.
	 */
	public static SchedulePlacement inFrontOf(double x, double y, double z, float yaw, double distance, double height) {
		double rad = Math.toRadians(yaw);
		return new SchedulePlacement(x - Math.sin(rad) * distance, y + height, z + Math.cos(rad) * distance, Motion.wrapDegrees(yaw + 180f));
	}
}
