package dev.t3craft.site;

/**
 * Plan-relative ↔ world coordinates around a site {@link CellPlan.Origin}. Plan convention (same as
 * {@code dev.t3craft.build.BuildPlan}): the front faces north ({@code -z}), {@code x = 0} is the
 * centre line, {@code y = 0} is the first layer above the pad. The plan is turned clockwise (seen
 * from above) so its front faces {@link CellPlan.Origin#facing()}.
 *
 * <p>{@link #quarterTurns} is also the ordinal of the matching
 * {@code net.minecraft.world.level.block.Rotation} (NONE, CLOCKWISE_90, CLOCKWISE_180,
 * COUNTERCLOCKWISE_90), which rotates block states (doors, stairs) the same way.
 */
public final class SiteFrame {
	private SiteFrame() {
	}

	/** Clockwise quarter turns from north to {@code facing}. */
	public static int quarterTurns(CellPlan.Facing facing) {
		return switch (facing) {
			case NORTH -> 0;
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
		};
	}

	/** Rotates (dx, dz) clockwise by {@code turns} quarter turns: one turn maps (x, z) to (-z, x). */
	public static int[] rotate(int dx, int dz, int turns) {
		int x = dx;
		int z = dz;
		for (int i = 0; i < (turns & 3); i++) {
			int t = x;
			x = -z;
			z = t;
		}
		return new int[] {x, z};
	}

	public static int[] toWorld(CellPlan.Origin o, int dx, int dy, int dz) {
		int[] r = rotate(dx, dz, quarterTurns(o.facing()));
		return new int[] {o.x() + r[0], o.y() + dy, o.z() + r[1]};
	}

	/** Inverse of {@link #toWorld}. */
	public static int[] toPlan(CellPlan.Origin o, int x, int y, int z) {
		int[] r = rotate(x - o.x(), z - o.z(), 4 - quarterTurns(o.facing()));
		return new int[] {r[0], y - o.y(), r[1]};
	}
}
