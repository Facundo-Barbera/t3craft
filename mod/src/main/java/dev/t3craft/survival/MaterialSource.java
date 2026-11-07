package dev.t3craft.survival;

/** Where builders take blocks from. */
public enum MaterialSource {
	/** Blocks appear from nothing (creative, debug, or the player chose so in config). */
	FREE,
	/** Each placed block consumes one matching item from the requesting player's inventory (see {@link MaterialBill}). */
	PLAYER_INVENTORY
}
