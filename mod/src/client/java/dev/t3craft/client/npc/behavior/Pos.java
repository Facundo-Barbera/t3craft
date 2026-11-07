package dev.t3craft.client.npc.behavior;

/**
 * Feet position in world coordinates. A plain value type (no Minecraft classes) so the behaviour
 * logic can be unit-tested without a game; {@link BehaviorDirector} converts to/from {@code Vec3}.
 */
public record Pos(double x, double y, double z) {
	public Pos add(double dx, double dy, double dz) {
		return new Pos(x + dx, y + dy, z + dz);
	}

	public double distSqr(Pos o) {
		double dx = o.x - x;
		double dy = o.y - y;
		double dz = o.z - z;
		return dx * dx + dy * dy + dz * dz;
	}

	public double horizDist(Pos o) {
		double dx = o.x - x;
		double dz = o.z - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public int blockX() {
		return (int) Math.floor(x);
	}

	public int blockY() {
		return (int) Math.floor(y + 0.01);
	}

	public int blockZ() {
		return (int) Math.floor(z);
	}

	/** Minecraft body yaw (degrees) for looking from {@code this} towards {@code o}. */
	public float yawTo(Pos o) {
		return (float) Math.toDegrees(Math.atan2(-(o.x - x), o.z - z));
	}
}
