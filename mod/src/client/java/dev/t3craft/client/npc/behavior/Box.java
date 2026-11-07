package dev.t3craft.client.npc.behavior;

/**
 * An axis-aligned collision box in block-local coordinates (0..1 across, Y may reach 1.5 for
 * fences and walls). Minecraft-free twin of {@code AABB} so collision logic is unit-testable.
 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	public static final Box FULL = new Box(0, 0, 0, 1, 1, 1);

	/** Does this box, placed in block (bx,by,bz), overlap the world-space box (strictly)? */
	boolean overlaps(int bx, int by, int bz, double x0, double y0, double z0, double x1, double y1, double z1) {
		return bx + minX < x1 - Collision.EPS && bx + maxX > x0 + Collision.EPS
				&& by + minY < y1 - Collision.EPS && by + maxY > y0 + Collision.EPS
				&& bz + minZ < z1 - Collision.EPS && bz + maxZ > z0 + Collision.EPS;
	}
}
