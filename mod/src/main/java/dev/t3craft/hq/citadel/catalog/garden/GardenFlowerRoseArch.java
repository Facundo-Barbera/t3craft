package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A cottage flower garden: a flagstone path that swings from the entry to a bend and back, a
 * climbing-rose arbour at the apex of the bend, and flower beds that rise in two terraces away from
 * the path (low flowers by the stones, taller ones behind, roses and lilacs against the hedge), with
 * a bench, a birdbath and lanterns.
 *
 * <p>Size: 9–12 × 9–12 × 5+. [depth] terraces of soil, a 3-high arbour with brackets and a leafy
 * top, bench and birdbath in the beds. [palette] stone/andesite flagstones, moss and grass, flower
 * drifts by colour (a cluster hash, never confetti), pale birch arbour, pink blossom. [finish] hanging
 * lantern, hedge on a stone kerb, lamp posts at the entry.
 */
public final class GardenFlowerRoseArch implements Variant {
	private static final Block[] LOW = {
			Blocks.PINK_TULIP, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
			Blocks.WHITE_TULIP, Blocks.POPPY, Blocks.ORANGE_TULIP, Blocks.ALLIUM, Blocks.DANDELION, Blocks.LILY_OF_THE_VALLEY
	};
	private static final Block[] TALL = {Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.LILAC, Blocks.ROSE_BUSH};

	@Override
	public String id() {
		return "garden_flower_rose_arch";
	}

	@Override
	public Family family() {
		return Family.GARDEN;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 12;
	}

	/** Centre line of the path: a cosine whose peak is the arbour's bend. */
	private static double centre(double v, double mid, double amp, double va, double period) {
		return mid + amp * Math.cos(2 * Math.PI * (v - va) / period);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int sign = p.hash(0, 0, 0, 2) == 0 ? 1 : -1; // which side the bend swings to
		double mid = (w - 1) / 2.0;
		double amp = Math.min((w - 6) * 0.5, 1.8) * sign;
		int va = Math.round(d * 0.58f);
		double period = d * 1.2;
		double[] cen = new double[d + 3];
		for (int v = -1; v <= d + 1; v++) {
			cen[v + 1] = centre(v, mid, amp, va, period);
		}
		int arbourU = (int) Math.max(2, Math.min(w - 5, Math.round(cen[va + 1] - 1.5)));
		// the path hugs the arbour's axis around its two rows
		double axis = arbourU + 1.5;

		boolean[][] path = new boolean[w][d];
		double[][] dist = new double[w][d];
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double best = 99;
				boolean on = false;
				for (double vv = v - 1.5; vv <= v + 1.5; vv += 0.5) {
					double cc = vv >= va - 0.5 && vv <= va + 1.5 ? axis : lerpAxis(vv, cen, axis, va);
					best = Math.min(best, Math.hypot(u - cc, v - vv));
					if (Math.abs(vv - v) <= 0.5 && Math.abs(u - cc) <= 1.15) {
						on = true;
					}
				}
				path[u][v] = on && u > 0 && u < w - 1 && v < d - 1;
				dist[u][v] = best;
			}
		}

		boolean[][] taken = new boolean[w][d];
		kerbAndHedge(c, p, path, taken);
		flagstones(c, p, path, taken);
		beds(c, p, path, taken, dist);
		arbour(c, p, arbourU, va);
		furniture(c, p, taken, dist, path);
	}

	/** Centre of the path at row {@code v}: the cosine, pulled onto the arbour axis near its rows. */
	private static double lerpAxis(double v, double[] cen, double axis, int va) {
		double base = cen[(int) Math.max(0, Math.min(cen.length - 1, Math.round(v) + 1))];
		double k = Math.max(0, 1 - Math.abs(v - (va + 0.5)) / 3.0); // 1 at the arbour, 0 three rows away
		return base + (axis - base) * k;
	}

	// ------------------------------------------------------------------ kerb, hedge, flagstones

	private static void kerbAndHedge(CitadelCanvas c, Plot p, boolean[][] path, boolean[][] taken) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
				if (!edge) {
					continue;
				}
				taken[u][v] = true;
				if (path[u][v] || (v == 0 && path[u][1])) {
					continue; // handled by the path (entry)
				}
				p.set(c, u, 0, v, switch (p.hash(u, 0, v, 4)) {
					case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
					case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
					default -> b(Blocks.STONE_BRICKS);
				});
				if (v == 0) {
					if (p.hash(u, 1, v, 3) == 0) {
						p.set(c, u, 1, v, b(Blocks.MOSS_CARPET));
					} else if (p.hash(u, 1, v, 3) == 1) {
						p.set(c, u, 1, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
					}
					continue;
				}
				int k = p.hash(u, 2, v, 6);
				p.set(c, u, 1, v, k == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : k == 1 ? leaves(Blocks.CHERRY_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				if (v == d - 1 && p.hash(u, 3, v, 3) != 0) {
					p.set(c, u, 2, v, k == 1 ? leaves(Blocks.CHERRY_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
	}

	private static void flagstones(CitadelCanvas c, Plot p, boolean[][] path, boolean[][] taken) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (!path[u][v]) {
					continue;
				}
				taken[u][v] = true;
				boolean rim = !at(path, u - 1, v) || !at(path, u + 1, v) || !at(path, u, v - 1) || !at(path, u, v + 1);
				Block base;
				if (rim) {
					base = switch (p.hash(u, 4, v, 4)) {
						case 0 -> Blocks.MOSSY_COBBLESTONE;
						case 1 -> Blocks.ANDESITE;
						default -> Blocks.POLISHED_ANDESITE;
					};
				} else {
					base = switch (p.hash(u, 4, v, 5)) {
						case 0 -> Blocks.MOSSY_STONE_BRICKS;
						case 1 -> Blocks.CRACKED_STONE_BRICKS;
						case 2 -> Blocks.COBBLESTONE;
						default -> Blocks.STONE_BRICKS;
					};
				}
				if (v == 0) {
					p.set(c, u, 0, v, slab(Blocks.STONE_BRICK_SLAB)); // half step up from the ground
				} else {
					p.set(c, u, 0, v, b(base));
				}
			}
		}
	}

	private static boolean at(boolean[][] g, int u, int v) {
		return u >= 0 && v >= 0 && u < g.length && v < g[0].length && g[u][v];
	}

	// ------------------------------------------------------------------ terraced beds

	private static BlockState top(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y + 20, v, 8)) {
			case 0 -> b(Blocks.MOSS_BLOCK);
			case 1 -> b(Blocks.PODZOL);
			default -> b(Blocks.GRASS_BLOCK);
		};
	}

	private static void beds(CitadelCanvas c, Plot p, boolean[][] path, boolean[][] taken, double[][] dist) {
		int w = p.w();
		int d = p.d();
		for (int u = 1; u < w - 1; u++) {
			for (int v = 1; v < d - 1; v++) {
				if (path[u][v]) {
					continue;
				}
				double dp = dist[u][v];
				int level = dp <= 1.9 ? 0 : dp <= 3.2 ? 1 : 2;
				if (v >= d - 3 && level < 1) {
					level = 1; // the back always rises against the hedge
				}
				for (int y = 0; y < level; y++) {
					p.set(c, u, y, v, p.hash(u, y, v, 4) == 0 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK));
				}
				p.set(c, u, level, v, top(p, u, level, v));
				taken[u][v] = true;
				flower(c, p, u, v, level);
			}
		}
	}

	/** Flower drifts: neighbouring 2×2 cells share a colour, so beds read as swathes. */
	private static void flower(CitadelCanvas c, Plot p, int u, int v, int level) {
		int y = level + 1;
		int drift = p.hash(u / 2, 5, v / 2, 997);
		int roll = p.hash(u, 6, v, 10);
		if (roll == 0 && p.hash(u, 12, v, 2) == 0) {
			return;
		}
		if (roll == 1) {
			p.set(c, u, y, v, b(Blocks.SHORT_GRASS));
			return;
		}
		boolean tall = level == 2 && p.hash(u, 7, v, 4) != 0 || level == 1 && drift % 5 == 0;
		if (tall && y + 1 < p.h()) {
			Block plant = TALL[drift % TALL.length];
			p.set(c, u, y, v, b(plant).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			p.set(c, u, y + 1, v, b(plant).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			return;
		}
		if (roll == 2 && level == 0) {
			p.set(c, u, y, v, b(Blocks.PINK_PETALS));
			return;
		}
		p.set(c, u, y, v, b(LOW[(drift + (roll == 3 ? 1 : 0)) % LOW.length]));
	}

	// ------------------------------------------------------------------ arbour

	/** Birch arbour two rows deep over the path: posts, brackets, a leafy roof of roses and a lantern. */
	private static void arbour(CitadelCanvas c, Plot p, int a, int va) {
		int bu = a + 5;
		BlockState post = b(Blocks.STRIPPED_BIRCH_LOG);
		for (int v = va; v <= va + 1; v++) {
			for (int y = 1; y <= 3; y++) {
				p.set(c, a, y, v, post);
				p.set(c, bu, y, v, post);
			}
			p.set(c, a + 1, 3, v, stairs(Blocks.BIRCH_STAIRS, Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
			p.set(c, bu - 1, 3, v, stairs(Blocks.BIRCH_STAIRS, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
			for (int u = a; u <= bu; u++) {
				boolean mid = u > a + 1 && u < bu - 1;
				p.set(c, u, 4, v, mid ? slab(Blocks.BIRCH_SLAB) : b(Blocks.BIRCH_PLANKS));
			}
		}
		// roses climbing the posts and crowning the top
		for (int v = va - 1; v <= va + 2; v++) {
			boolean fringe = v == va - 1 || v == va + 2;
			for (int u : new int[] {a - 1, bu + 1}) {
				for (int y = 1; y <= 3; y++) {
					if (!fringe || y <= 2 && p.hash(u, y, v, 2) == 0) {
						p.set(c, u, y, v, p.hash(u, y, v, 3) == 0 ? leaves(Blocks.CHERRY_LEAVES) : leaves(Blocks.FLOWERING_AZALEA_LEAVES));
					}
				}
			}
		}
		for (int v = va; v <= va + 1; v++) {
			for (int u = a - 1; u <= bu + 1; u++) {
				if (p.hash(u, 9, v, 3) != 0 || u == a - 1 || u == bu + 1) {
					p.set(c, u, 4, v, p.hash(u, 8, v, 3) == 0 ? leaves(Blocks.CHERRY_LEAVES) : leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
		}
		p.put(c, a + 2, 3, va, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		p.put(c, a + 3, 3, va + 1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ bench, birdbath, lamps

	private static void furniture(CitadelCanvas c, Plot p, boolean[][] taken, double[][] dist, boolean[][] path) {
		int w = p.w();
		int d = p.d();
		// birdbath: the free cell in the lower half of the plot farthest from the path
		int bu = -1;
		int bv = -1;
		double best = 0;
		for (int u = 2; u < w - 2; u++) {
			for (int v = 2; v <= d / 2 - 1; v++) {
				if (!path[u][v] && dist[u][v] > best) {
					best = dist[u][v];
					bu = u;
					bv = v;
				}
			}
		}
		if (bu >= 0) {
			int level = dist[bu][bv] <= 1.9 ? 0 : dist[bu][bv] <= 3.2 ? 1 : 2;
			p.set(c, bu, level + 1, bv, b(Blocks.STONE_BRICK_WALL));
			p.set(c, bu, level + 2, bv, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			for (Direction dir : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
				p.set(c, bu + dir.getStepX(), level + 1, bv + dir.getStepZ(), b(Blocks.LILY_OF_THE_VALLEY));
			}
		}
		// bench: two stairs facing away from the path, on the far side of the bend
		int bu2 = -1;
		int bv2 = -1;
		for (int v = d / 2 + 1; v < d - 3 && bu2 < 0; v++) {
			for (int u = 2; u < w - 2; u++) {
				if (!path[u][v] && !path[u][v + 1] && dist[u][v] >= 2 && dist[u][v] <= 2.8 && dist[u][v + 1] >= 2) {
					bu2 = u;
					bv2 = v;
					break;
				}
			}
		}
		if (bu2 >= 0) {
			boolean pathEast = path[Math.min(w - 1, bu2 + 2)][bv2] || path[Math.min(w - 1, bu2 + 3)][bv2];
			Direction back = pathEast ? Direction.WEST : Direction.EAST;
			int level = dist[bu2][bv2] <= 1.9 ? 0 : 1;
			for (int dv = 0; dv <= 1; dv++) {
				p.set(c, bu2, level + 1, bv2 + dv, stairs(Blocks.STONE_BRICK_STAIRS, back));
			}
			p.set(c, bu2 + back.getStepX(), level + 1, bv2, b(Blocks.POTTED_POPPY));
			p.set(c, bu2 + back.getStepX(), level + 1, bv2 + 1, b(Blocks.POTTED_BLUE_ORCHID));
		}
		// lamp posts flanking the entry
		for (int u = 1; u < w - 1; u++) {
			if (path[u][1]) {
				for (int lu : new int[] {u - 2, u + 2}) {
					if (lu > 0 && lu < w - 1 && !path[lu][0]) {
						Support.fillIfFree(c, p, lu, 0, 0, b(Blocks.STONE_BRICKS)); // fix/settle-sources: a footing under the post (the ground row is open at the entry)
						p.set(c, lu, 1, 0, b(Blocks.CHERRY_FENCE));
						p.set(c, lu, 2, 0, b(Blocks.CHERRY_FENCE));
						p.set(c, lu, 3, 0, b(Blocks.LANTERN));
					}
				}
				break;
			}
		}
	}
}
