package dev.t3craft.client.npc.behavior;

/** What an NPC is doing, derived from its thread state ({@code NpcInfo.state()} wire value). */
public enum Mode {
	/** Walk to the desk and type. */
	WORKING,
	/** Go to the central board, floating icon and speech bubble (needs_attention and error). */
	ATTENTION,
	/** Wander around the neighbourhood. */
	IDLE,
	/** Celebrate once, then rest. */
	COMPLETED;

	public static Mode of(String stateWire) {
		return switch (stateWire) {
			case "working" -> WORKING;
			case "needs_attention", "error" -> ATTENTION;
			case "done" -> COMPLETED;
			default -> IDLE;
		};
	}
}
