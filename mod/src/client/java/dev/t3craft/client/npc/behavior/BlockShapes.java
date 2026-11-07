package dev.t3craft.client.npc.behavior;

import java.util.List;

/**
 * The blocks as an NPC's body meets them: the collision boxes of each block cell and whether it
 * holds a liquid. Doors and fence gates an NPC could open by hand report no boxes (they are
 * passable). Implementations: {@link LevelBlocks} (the client level) and fake cell worlds in tests.
 */
public interface BlockShapes {
	/** Collision boxes of the block at (x,y,z), block-local; empty when nothing collides. */
	List<Box> boxes(int x, int y, int z);

	/** A liquid block (water, lava): never walked into. Waterlogged blocks are not liquids here. */
	boolean liquid(int x, int y, int z);
}
