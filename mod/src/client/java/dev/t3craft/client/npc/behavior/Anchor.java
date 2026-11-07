package dev.t3craft.client.npc.behavior;

/** A spot an NPC walks to and the yaw it faces once there (desk chair, board slot, plaza). */
public record Anchor(Pos pos, float yaw) {
	public static Anchor at(double x, double y, double z, float yaw) {
		return new Anchor(new Pos(x, y, z), yaw);
	}
}
