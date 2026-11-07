package dev.t3craft.client.npc.behavior;

/**
 * The ground as seen by the {@link GridPathfinder}: for a block cell, the height an NPC's feet
 * would be at when standing in it, or {@code NaN} when it cannot stand there (solid feet block, no
 * headroom, nothing below). Implementations: {@link LevelWalkable} (the client level) and
 * {@link #flat} (tests, or no level).
 */
@FunctionalInterface
public interface Walkable {
	double floor(int x, int y, int z);

	/**
	 * Height of the solid shape top in the cell itself (for headroom checks when stepping up), or
	 * {@code NaN} when the cell is passable. Default: passable.
	 */
	default double solidTop(int x, int y, int z) {
		return Double.NaN;
	}

	/** An infinite flat floor whose walkable cells are at block {@code y}. */
	static Walkable flat(int y) {
		return (x, cy, z) -> cy == y ? y : Double.NaN;
	}
}
