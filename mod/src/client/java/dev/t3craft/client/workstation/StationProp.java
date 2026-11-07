package dev.t3craft.client.workstation;

/**
 * Which stand-in "computer" a workstation draws at its {@code computerPos}. Pure: the renderer maps
 * the kind to a block state. Never a technical vanilla block (observer, redstone, piston…): those
 * looked like stray contraptions next to every agent.
 */
enum StationProp {
	/** A real block is there (HQ desk, builder): it is the computer, draw nothing over it. */
	NONE,
	/** Blocks module active: a {@code t3craft:monitor} BlockDisplay facing the desk. */
	MONITOR,
	/** GUI-only mode (blocks module off): a small, flat black-concrete base under the floating screen. */
	PANEL;

	/** True when the entry carries a BlockDisplay (and the click hitbox that goes with it). */
	boolean drawn() {
		return this != NONE;
	}

	static StationProp choose(boolean blocksModuleActive, boolean realBlockPresent) {
		if (realBlockPresent) {
			return NONE;
		}
		return blocksModuleActive ? MONITOR : PANEL;
	}
}
