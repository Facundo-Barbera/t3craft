package dev.t3craft.hq.citadel.nature;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Shared bits of the living pieces of the citadel ({@link Trees}, {@link Plots}, {@link Gardens}):
 * the deterministic hash every variation is seeded with (by lot, by node or by cell, never by run), the
 * flower palettes of a "macizo" (a patch of one palette), the double plants and the petals.
 *
 * <p>Everything here is a pure function of its arguments. A plant is only ever placed on a soil
 * (see {@link #soil}) and never over a lamp of the street-light lattice.
 */
public final class Flora {
	private Flora() {
	}

	/** A well mixed non-negative hash of three ints (the seed of every deterministic variation). */
	public static int hash(int a, int b, int salt) {
		int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ salt * 0x9E3779B1;
		h ^= h >>> 15;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h & 0x7fffffff;
	}

	// ------------------------------------------------------------------ soil and free cells

	/** A block a plant may stand on (vanilla's dirt tag, plus farmland). */
	public static boolean soil(BlockState s) {
		return s != null && (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.COARSE_DIRT) || s.is(Blocks.DIRT) || s.is(Blocks.MUD));
	}

	/** True when the cell holds nothing (or air). */
	public static boolean free(CitadelCanvas c, int x, int y, int z) {
		BlockState s = c.at(x, y, z);
		return s == null || s == AIR || s.isAir();
	}

	/** Soil at {@code y = 0} of the column that is not a street light. */
	public static boolean planting(CitadelCanvas c, int x, int z) {
		return soil(c.at(x, 0, z)) && c.at(x, 0, z) != LIGHT;
	}

	/** A plant (one block high) on the soil of the column at {@code y = 0}, if the cell above it is free. */
	public static boolean plant(CitadelCanvas c, int x, int z, BlockState s) {
		return plantOn(c, x, 0, z, s);
	}

	/** A plant (one block high) standing on the soil block at {@code (x, y, z)}, if the cell above it is free and it is no lamp. */
	public static boolean plantOn(CitadelCanvas c, int x, int y, int z, BlockState s) {
		BlockState below = c.at(x, y, z);
		if (soil(below) && free(c, x, y + 1, z)) {
			c.set(x, y + 1, z, s);
			return true;
		}
		return false;
	}

	/** A double plant (tall grass, large fern, peony, lilac, rose bush) with both halves, on the soil at {@code y = 0}. */
	public static boolean tall(CitadelCanvas c, int x, int z, Block block) {
		return tallOn(c, x, 0, z, block);
	}

	/** A double plant standing on the soil block at {@code (x, y, z)}. */
	public static boolean tallOn(CitadelCanvas c, int x, int y, int z, Block block) {
		if (soil(c.at(x, y, z)) && free(c, x, y + 1, z) && free(c, x, y + 2, z)) {
			c.set(x, y + 1, z, block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			c.set(x, y + 2, z, block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ blocks

	/** Pink petals: {@code amount} 1..4 in direction {@code h}. */
	public static BlockState petals(int h) {
		return Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + h % 4)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((h >> 3) & 3));
	}

	/** Wildflowers (the 1.21.5 patch of petals of every colour). */
	public static BlockState wild(int h) {
		return Blocks.WILDFLOWERS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + h % 4)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((h >> 3) & 3));
	}

	/** The palettes of a macizo: three flowers that look good together. */
	private static final Block[][] PALETTES = {
			{Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.AZURE_BLUET},
			{Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY},
			{Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY},
			{Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.DANDELION},
			{Blocks.BLUE_ORCHID, Blocks.CORNFLOWER, Blocks.AZURE_BLUET},
			{Blocks.ALLIUM, Blocks.PINK_TULIP, Blocks.LILY_OF_THE_VALLEY}};

	public static int palettes() {
		return PALETTES.length;
	}

	/** A flower of palette {@code palette} picked by {@code h}. */
	public static BlockState bloom(int palette, int h) {
		Block[] p = PALETTES[Math.floorMod(palette, PALETTES.length)];
		return b(p[h % p.length]);
	}

	/** The tall flower (a double plant) that goes with palette {@code palette}. */
	public static Block bigBloom(int palette) {
		return switch (Math.floorMod(palette, 4)) {
			case 0 -> Blocks.PEONY;
			case 1 -> Blocks.LILAC;
			case 2 -> Blocks.ROSE_BUSH;
			default -> Blocks.PEONY;
		};
	}

	/** A leaf bush: azalea leaves, flowering one time in three. */
	public static BlockState bush(int h) {
		return leaves(h % 3 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
	}

	/** The soil under a bloom: grass, with a patch of moss now and then. */
	public static BlockState turf(int x, int z) {
		return (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS;
	}

	/** A pond cell is water at {@code y = 0} over a deepslate bed. */
	public static boolean isWater(BlockState s) {
		return s == WATER || (s != null && s.is(Blocks.WATER));
	}

	/**
	 * What grows in a cell of a "macizo" meadow: the cell's patch (coarse 4x4 blocks: the same for a
	 * whole patch) decides between a flower palette, tall grass, ferns, a bush or plain short grass, and
	 * the hash of the cell decides whether anything stands in it. Places the plant on the soil.
	 */
	public static void meadow(CitadelCanvas c, int x, int z, int a, int bb, int salt) {
		int patch = hash(Math.floorDiv(a, 4), Math.floorDiv(bb, 4), salt) % 12;
		int h = hash(x, z, salt + 17);
		switch (patch) {
			case 0, 1 -> {
				if (h % 100 < 52) {
					plant(c, x, z, bloom(patch + salt, h >> 8));
				}
			}
			case 2 -> {
				if (h % 100 < 55) {
					plant(c, x, z, wild(h >> 8));
				}
			}
			case 3 -> {
				if (h % 100 < 38) {
					tall(c, x, z, h % 7 == 0 ? Blocks.LARGE_FERN : Blocks.TALL_GRASS);
				} else if (h % 100 < 70) {
					plant(c, x, z, b(Blocks.SHORT_GRASS));
				}
			}
			case 4 -> {
				if (h % 100 < 30) {
					plant(c, x, z, b(Blocks.FERN));
				}
			}
			case 5 -> {
				if (h % 100 < 12) {
					plant(c, x, z, b(Blocks.BUSH));
				} else if (h % 100 < 40) {
					plant(c, x, z, b(Blocks.SHORT_GRASS));
				}
			}
			default -> {
				if (h % 100 < 9) {
					plant(c, x, z, b(Blocks.SHORT_GRASS));
				}
			}
		}
	}
}
