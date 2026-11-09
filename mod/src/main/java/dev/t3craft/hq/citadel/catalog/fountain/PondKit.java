package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Shared pieces of the natural-water fountains: rim stone with a gradient, pond beds, shore plants,
 * boulders, a stone lantern. Package-private on purpose: each variant stays one readable file.
 *
 * <p>Water note: the plot's ground (y = -1) is not ours, so ponds are raised basins: a bed at y = 0, the
 * water surface block at y = 1 and a complete ring of solid rim at y = 1 so no source ever flows out.
 */
final class PondKit {
	/** Cell classes of a pond mask. */
	static final int OUT = 0;
	static final int WATER = 1;
	static final int RIM = 2;

	/** The four local horizontal directions (NORTH = towards the front, v - 1). */
	static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	private PondKit() {
	}

	/** Writes only when the cell is inside the plot (decoration that may overhang the box). */
	static void put(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}

	static void putLayer(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s, int layer) {
		if (p.has(u, y, v)) {
			p.put(c, u, y, v, s, layer);
		}
	}

	static BlockState water() {
		return b(Blocks.WATER);
	}

	/** Water that falls: a column below a source or another falling block. */
	static BlockState fallingWater() {
		return b(Blocks.WATER).setValue(LiquidBlock.LEVEL, 8);
	}

	/** Dark, heavy base stone of a rim (deepslate and tuff). */
	static BlockState rimBase(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 7)) {
			case 0, 1 -> b(Blocks.COBBLED_DEEPSLATE);
			case 2 -> b(Blocks.TUFF);
			case 3 -> b(Blocks.DEEPSLATE_BRICKS);
			case 4 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 5 -> b(Blocks.TUFF_BRICKS);
			default -> b(Blocks.COBBLED_DEEPSLATE);
		};
	}

	/** Lighter, mossier top stone of a rim. */
	static BlockState rimTop(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 10)) {
			case 0, 1, 2 -> b(Blocks.MOSSY_COBBLESTONE);
			case 3, 4 -> b(Blocks.STONE);
			case 5, 6 -> b(Blocks.ANDESITE);
			case 7, 8 -> b(Blocks.MOSSY_STONE_BRICKS);
			default -> b(Blocks.COBBLESTONE);
		};
	}

	/** Pier / wall stone for small masonry. */
	static BlockState brick(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 4)) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.STONE_BRICKS);
			case 2 -> b(Blocks.MOSSY_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** Pond bed seen through the water: mud at the edges, clay and gravel towards the middle. */
	static BlockState bed(Plot p, int u, int v, boolean edge) {
		int h = p.hash(u, 9, v, 10);
		if (edge) {
			return h < 6 ? b(Blocks.MUD) : b(Blocks.PACKED_MUD);
		}
		return h < 5 ? b(Blocks.CLAY) : h < 8 ? b(Blocks.GRAVEL) : b(Blocks.MUD);
	}

	static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}

	/** A fence with its sides set by hand (plan states are kept exactly). */
	static BlockState fence(boolean north, boolean south, boolean west, boolean east) {
		return b(Blocks.CHERRY_FENCE).setValue(BlockStateProperties.NORTH, north).setValue(BlockStateProperties.SOUTH, south)
				.setValue(BlockStateProperties.WEST, west).setValue(BlockStateProperties.EAST, east);
	}

	/** Mossy low step from the grass up to a rim that lies in direction {@code toRim}. */
	static BlockState shoreStep(Plot p, int u, int v, Direction toRim) {
		return stairs(p.hash(u, 4, v, 2) == 0 ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.MOSSY_STONE_BRICK_STAIRS, toRim);
	}

	/** What grows on bare shore cells (y = 0, on the grass of the pad); null = leave the grass. */
	static BlockState shorePlant(Plot p, int u, int v) {
		return switch (p.hash(u, 3, v, 12)) {
			case 0, 1, 2, 3 -> b(Blocks.SHORT_GRASS);
			case 4, 5 -> b(Blocks.FERN);
			case 6 -> b(Blocks.PINK_PETALS);
			case 8 -> b(Blocks.FLOWERING_AZALEA);
			case 9 -> b(Blocks.AZALEA);
			case 10 -> b(Blocks.CORNFLOWER);
			default -> null;
		};
	}

	/** Whether any 4-neighbour of {@code (u, v)} in the mask has class {@code what}. */
	static boolean touches(int[][] k, int u, int v, int what) {
		for (Direction dir : DIRS) {
			int nu = u + dir.getStepX();
			int nv = v + dir.getStepZ();
			if (nu >= 0 && nu < k.length && nv >= 0 && nv < k[0].length && k[nu][nv] == what) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The shore pass: every free OUT cell next to a rim becomes a mossy step up to it (a slab where it
	 * touches two rims) and the rest gets plants. Cells in {@code reserved} are left alone.
	 */
	static void shore(CitadelCanvas c, Plot p, int[][] k, boolean[][] reserved) {
		for (int u = 0; u < k.length; u++) {
			for (int v = 0; v < k[0].length; v++) {
				if (k[u][v] != OUT || reserved[u][v]) {
					continue;
				}
				int rims = 0;
				Direction toRim = null;
				for (Direction dir : DIRS) {
					int nu = u + dir.getStepX();
					int nv = v + dir.getStepZ();
					if (p.has(nu, 0, nv) && k[nu][nv] == RIM) {
						rims++;
						toRim = dir;
					}
				}
				if (rims == 1) {
					p.set(c, u, 0, v, shoreStep(p, u, v, toRim));
				} else if (rims > 1) {
					p.set(c, u, 0, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
				} else {
					BlockState plant = shorePlant(p, u, v);
					if (plant != null) {
						p.set(c, u, 0, v, plant);
					}
				}
			}
		}
	}

	/** A stacked boulder of mixed rock, capped with a slab or moss. */
	static void boulder(CitadelCanvas c, Plot p, int u, int v, int height) {
		for (int y = 0; y < height; y++) {
			BlockState s = y == 0 ? rimBase(p, u, v) : rimTop(p, u, y, v);
			put(c, p, u, y, v, s);
		}
		BlockState cap = p.hash(u, 11, v, 3) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.MOSSY_COBBLESTONE_SLAB);
		put(c, p, u, height, v, cap);
	}

	/**
	 * Japanese stone lantern. Height 4: plinth, post, lantern, mossy roof slab. From height 5 it gets
	 * three bodies: plinth, post, a chiseled lamp house that carries the lantern, a roof and a finial.
	 */
	static void toro(CitadelCanvas c, Plot p, int u, int v) {
		put(c, p, u, 0, v, b(Blocks.POLISHED_ANDESITE));
		put(c, p, u, 1, v, b(Blocks.STONE_BRICK_WALL));
		if (p.h() >= 5) {
			put(c, p, u, 2, v, b(Blocks.CHISELED_STONE_BRICKS));
			put(c, p, u, 3, v, b(Blocks.LANTERN));
			put(c, p, u, 4, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
			put(c, p, u, 5, v, b(Blocks.STONE_BRICK_WALL));
		} else {
			put(c, p, u, 2, v, b(Blocks.LANTERN));
			put(c, p, u, 3, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
		}
	}

	/** A bamboo stalk with leaves on top; cluster them with different heights. */
	static void bamboo(CitadelCanvas c, Plot p, int u, int v, int height) {
		for (int y = 0; y < height; y++) {
			BlockState s = b(Blocks.BAMBOO);
			if (y == height - 1) {
				s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.LARGE);
			} else if (y == height - 2) {
				s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.SMALL);
			}
			put(c, p, u, y, v, s);
		}
	}

	/** A bamboo grove on the cells {@code (u + du*i, v)}: heights 3..6 (capped by the plot), skips cells that are not free. */
	static void grove(CitadelCanvas c, Plot p, int[][] k, boolean[][] reserved, int u0, int du, int v0, int dv, int n) {
		for (int i = 0; i < n; i++) {
			int u = u0 + du * (i / 2);
			int v = v0 + dv * (i % 2);
			if (p.has(u, 0, v) && k[u][v] == OUT && !reserved[u][v]) {
				bamboo(c, p, u, v, Math.min(p.h() - 1, 3 + p.hash(u, 1, v, 4)));
				reserved[u][v] = true;
			}
		}
	}

	/**
	 * A young cherry tree: a trunk that bends towards {@code lean} (+1 / -1 in u) and an irregular
	 * four-layer canopy with holes, some leaves hanging lower on the lean side, pink petals around the
	 * foot. Everything is clipped by the plot, never written outside.
	 */
	static void cherry(CitadelCanvas c, Plot p, int u, int v, int lean) {
		int trunk = Math.max(3, Math.min(5, p.h() - 2));
		for (int y = 0; y < trunk; y++) {
			put(c, p, u + (y >= 2 ? lean : 0), y, v, b(Blocks.CHERRY_LOG));
		}
		put(c, p, u + 2 * lean, trunk - 2, v, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // a branch over the water
		int topU = u + lean;
		int[] radius = {1, 2, 2, 1};
		for (int layer = 0; layer < 4; layer++) {
			int y = trunk - 2 + layer;
			int r = radius[layer];
			for (int du = -r; du <= r; du++) {
				for (int dv = -r; dv <= r; dv++) {
					boolean corner = Math.abs(du) == r && Math.abs(dv) == r && r > 1;
					int lu = topU + du + (layer == 0 ? lean : 0);
					int lv = v + dv;
					if (!p.has(lu, y, lv) || corner || (du == 0 && dv == 0 && layer < 2) || p.hash(lu, y, lv, 4) == 0
							|| p.at(c, lu, y, lv) != null) {
						continue;
					}
					put(c, p, lu, y, lv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		for (Direction dir : DIRS) {
			int pu = u + dir.getStepX();
			int pv = v + dir.getStepZ();
			if (p.has(pu, 0, pv) && p.at(c, pu, 0, pv) == null && p.hash(pu, 6, pv, 2) == 0) { // only on bare ground
				put(c, p, pu, 0, pv, b(Blocks.PINK_PETALS));
			}
		}
	}
}
