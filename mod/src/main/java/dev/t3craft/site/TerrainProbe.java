package dev.t3craft.site;

import java.util.List;

/**
 * Read-only view of the terrain for site selection. The Minecraft implementation classifies block
 * states with vanilla tags and never loads or generates chunks ({@link #loaded} is false for any
 * column whose chunk is not already fully loaded).
 */
public interface TerrainProbe {
	enum Kind {
		AIR,
		/** Grass, flowers, snow layers, saplings… (cleared freely). */
		PLANT,
		/** Natural logs and leaves (cleared, but count against a site). */
		TREE,
		/** Natural ground: dirt, stone, sand, gravel, ores, terracotta, ice… */
		GROUND,
		WATER,
		LAVA,
		/** Anything else: assumed placed by a player or a structure (village, ruin). Never touched. */
		ARTIFICIAL
	}

	boolean loaded(int x, int z);

	/** Highest non-air y of the column (vanilla WORLD_SURFACE heightmap minus one). */
	int surfaceY(int x, int z);

	Kind kind(int x, int y, int z);

	/** Short block name for messages, e.g. {@code oak_planks}. */
	String describe(int x, int y, int z);

	int minBuildY();

	int maxBuildY();

	/** Entities inside the box that are not plain mobs, items or players (item frames, armor stands, minecarts, named or tamed mobs…). */
	List<String> foreignEntities(Box box);
}
