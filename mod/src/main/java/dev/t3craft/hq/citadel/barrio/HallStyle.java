package dev.t3craft.hq.citadel.barrio;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The four looks of a barrio hall. They read as one neighbourhood (same door, same lanterns, same
 * window rhythm, the lot's own wall and roof materials) but differ in their dressing:
 *
 * <ul>
 * <li>{@link #TIMBER}: timber frame (posts on the piers, a beam over the windows), flower boxes, a porch
 *     with a stair awning and hanging lanterns, a runner rug.</li>
 * <li>{@link #STONE}: stone-brick piers and plinth, tall windows, lamp posts beside the door, ivy,
 *     tub plants and a bordered rug.</li>
 * <li>{@link #SHUTTERED}: twin windows flanked by open trapdoor shutters, flower boxes, lamp posts and a
 *     small hood over the door, a checkered rug.</li>
 * <li>{@link #BALCONY}: stained-glass windows in the project's colour, a balcony over the door (a porch
 *     on a one-floor hall), a rug of concentric rings.</li>
 * </ul>
 *
 * The style of a hall is a pure function of its lot and hall slot ({@link #of}): the three halls of a lot
 * always get three different styles, and neighbouring lots start on different ones. Nothing here is
 * random per run.
 */
public enum HallStyle {
	TIMBER, STONE, SHUTTERED, BALCONY;

	/** The style of hall {@code hall} of lot {@code lot}. */
	public static HallStyle of(int lot, int hall) {
		return values()[Math.floorMod(lot * 3 + hall * 7 + 1, 4)];
	}

	/** A per-hall seed for the small variations (shutter wood, which windows get a flower box...). */
	public static int seed(int lot, int hall) {
		return lot * 131 + hall * 17 + 7;
	}

	/** A deterministic hash of some ints, non negative. */
	public static int hash(int... v) {
		int h = 0x2545F491;
		for (int x : v) {
			h = (h ^ x) * 0x45D9F3B;
			h ^= h >>> 15;
		}
		return h & 0x7fffffff;
	}

	/** Stair block of the hall's own staircase and of its porch awning. */
	public Block stairBlock() {
		return switch (this) {
			case TIMBER -> Blocks.CHERRY_STAIRS;
			case STONE -> Blocks.STONE_BRICK_STAIRS;
			case SHUTTERED -> Blocks.DARK_OAK_STAIRS;
			case BALCONY -> Blocks.BAMBOO_MOSAIC_STAIRS;
		};
	}

	/** The solid core of the staircase. */
	public BlockState stairCore() {
		return switch (this) {
			case TIMBER -> Blocks.CHERRY_PLANKS.defaultBlockState();
			case STONE -> Blocks.STONE_BRICKS.defaultBlockState();
			case SHUTTERED -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
			case BALCONY -> Blocks.BAMBOO_MOSAIC.defaultBlockState();
		};
	}

	/** Fence (or wall) of the railings and lamp posts. */
	public Block fence() {
		return switch (this) {
			case TIMBER, BALCONY -> Blocks.CHERRY_FENCE;
			case STONE -> Blocks.STONE_BRICK_WALL;
			case SHUTTERED -> Blocks.DARK_OAK_FENCE;
		};
	}

	/** Slab of the window ledges. */
	public Block slab() {
		return switch (this) {
			case TIMBER -> Blocks.CHERRY_SLAB;
			case STONE -> Blocks.STONE_BRICK_SLAB;
			case SHUTTERED -> Blocks.DARK_OAK_SLAB;
			case BALCONY -> Blocks.BAMBOO_MOSAIC_SLAB;
		};
	}

	/** The shutters' wood, picked by the hall's seed. */
	public static Block shutter(int seed) {
		return switch (Math.floorMod(seed, 3)) {
			case 0 -> Blocks.CHERRY_TRAPDOOR;
			case 1 -> Blocks.SPRUCE_TRAPDOOR;
			default -> Blocks.DARK_OAK_TRAPDOOR;
		};
	}

	/** A potted plant by hash. */
	public static BlockState pot(int hash) {
		Block b = switch (hash % 6) {
			case 0 -> Blocks.POTTED_FLOWERING_AZALEA;
			case 1 -> Blocks.POTTED_AZALEA;
			case 2 -> Blocks.POTTED_CHERRY_SAPLING;
			case 3 -> Blocks.POTTED_PINK_TULIP;
			case 4 -> Blocks.POTTED_FERN;
			default -> Blocks.POTTED_LILY_OF_THE_VALLEY;
		};
		return b.defaultBlockState();
	}

	/** The second colour of the rugs, never equal to {@code env}. */
	public DyeColor alt(DyeColor env) {
		DyeColor a = switch (this) {
			case TIMBER -> DyeColor.BROWN;
			case STONE -> DyeColor.LIGHT_GRAY;
			default -> DyeColor.WHITE;
		};
		return a == env ? DyeColor.GRAY : a;
	}
}
