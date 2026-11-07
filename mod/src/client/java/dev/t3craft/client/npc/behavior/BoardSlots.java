package dev.t3craft.client.npc.behavior;

import java.util.List;

/** Standing spots in front of the central board, so NPCs that need attention don't overlap. */
public final class BoardSlots {
	/** Distance from the board to the first row. */
	static final double FRONT = 2.5;
	/** Side spacing between NPCs in a row. */
	static final double SPACING = 1.1;
	static final int PER_ROW = 5;

	private BoardSlots() {
	}

	/**
	 * Slot {@code index} (0 = centre of the first row). Uses {@code explicit} slots first, then rows
	 * of {@link #PER_ROW} behind the board anchor, all facing the anchor's yaw.
	 */
	public static Anchor slot(Anchor board, List<Anchor> explicit, int index) {
		if (index < explicit.size()) {
			return explicit.get(index);
		}
		int i = index - explicit.size();
		int row = i / PER_ROW;
		int col = i % PER_ROW;
		// centre, right, left, right2, left2, ...
		int side = col == 0 ? 0 : (col % 2 == 1 ? (col + 1) / 2 : -(col / 2));
		double yaw = Math.toRadians(board.yaw());
		// Facing direction (Minecraft yaw: 0 = +z, 90 = -x).
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// The NPCs face the board, so they stand opposite to their facing direction.
		double back = FRONT + row * 1.2;
		double sx = -fz;
		double sz = fx;
		Pos p = board.pos().add(-fx * back + sx * side * SPACING, 0, -fz * back + sz * side * SPACING);
		return new Anchor(p, board.yaw());
	}
}
