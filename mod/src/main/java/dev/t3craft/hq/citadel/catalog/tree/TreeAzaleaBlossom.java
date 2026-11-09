package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Flowering azalea tree in a round planter: a cherry-wood trunk that forks into two boughs, a rounded,
 * lumpy crown of flowering azalea leaves with cherry-blossom pink on top, drips of blossom hanging
 * from the rim and fallen petals on a moss bed. The planter is a dark deepslate curb with a pale cap.
 *
 * <ul>
 * <li><b>[depth]</b> lumpy crown (noise in 2×2×2 clumps), a forked trunk with a root flare, a curb
 *     that steps from deepslate to a light cap, petals and a flowering bush at different heights.</li>
 * <li><b>[palette]</b> deepslate bricks / polished / cracked (dark) → moss → cherry wood → azalea,
 *     flowering azalea and cherry leaves (the pink rises with the height).</li>
 * <li><b>[finish]</b> a lantern hanging from a fence arm under the fork, a stripped-wood knot at the
 *     fork, drips of leaves under the crown and fallen petals even outside the bed.</li>
 * </ul>
 * Size: 5–9 × 5–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeAzaleaBlossom implements Variant {
	private static final BlockState WOOD = b(Blocks.CHERRY_WOOD);
	private static final BlockState STRIPPED = b(Blocks.STRIPPED_CHERRY_WOOD);

	@Override
	public String id() {
		return "tree_azalea_blossom";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 5;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int top = Math.min(p.h(), 13) - 1;
		int cu = (w - 1) / 2;
		int cv = (d - 1) / 2;
		double ccu = (w - 1) / 2.0;
		double ccv = (d - 1) / 2.0;
		int tr = Math.max(3, (top + 1) * 3 / 8); // fork height
		int cb = tr + 2; // crown bottom
		int crownH = top - cb + 1;

		bed(c, p, w, d, ccu, ccv, cu, cv);

		// --- crown: rounded, lumpy, full of blossom
		double ru = ccu + 0.45;
		double rv = ccv + 0.45;
		int[][] lowest = new int[w][d];
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				lowest[u][v] = -1;
			}
		}
		for (int y = cb; y <= top; y++) {
			double t = (double) (y - cb) / Math.max(1, crownH - 1);
			double k = (t - 0.45) / 0.62;
			double f = Math.sqrt(Math.max(0, 1 - k * k));
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					double du = (u - ccu) / ru;
					double dv = (v - ccv) / rv;
					double n = (p.hash(u >> 1, y >> 1, v >> 1, 100) - 50) / 50.0 * 0.28 + (p.hash(u, y, v, 100) - 50) / 50.0 * 0.1;
					if (du * du + dv * dv + n <= f * f) {
						if (lowest[u][v] < 0) {
							lowest[u][v] = y;
						}
						int s = p.hash(u, y, v, 20);
						BlockState lf;
						if (t > 0.55) {
							lf = s < 6 ? leaves(Blocks.CHERRY_LEAVES) : s < 15 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES);
						} else {
							lf = s < 2 ? leaves(Blocks.CHERRY_LEAVES) : s < 12 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES);
						}
						set(c, p, u, y, v, lf);
					}
				}
			}
		}
		// drips of blossom under the rim
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				int lo = lowest[u][v];
				if (lo < 2) {
					continue;
				}
				boolean edge = false;
				for (int[] n : n4) {
					int nu = u + n[0];
					int nv = v + n[1];
					if (nu < 0 || nv < 0 || nu >= w || nv >= d || lowest[nu][nv] < 0 || lowest[nu][nv] > lo) {
						edge = true;
					}
				}
				if (edge && lo > cb - 1) {
					int g = p.hash(u, 7, v, 5);
					if (g < 2) {
						set(c, p, u, lo - 1, v, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
					}
					if (g == 0 && lo - 2 >= 2) {
						set(c, p, u, lo - 2, v, leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
		}

		// --- trunk: root flare, trunk, fork into two boughs and a spine
		set(c, p, cu, 0, cv, b(Blocks.ROOTED_DIRT));
		int[][] flare = {{-1, 0, 1}, {1, 0, 1}, {0, 1, 1}, {0, -1, 1}};
		for (int[] f : flare) {
			Direction toTrunk = f[0] == -1 ? Direction.EAST : f[0] == 1 ? Direction.WEST : f[1] == 1 ? Direction.NORTH : Direction.SOUTH;
			if (p.hash(cu + f[0], 3, cv + f[1], 3) != 0) {
				set(c, p, cu + f[0], 1, cv + f[1], stairs(Blocks.CHERRY_STAIRS, toTrunk));
			}
		}
		for (int y = 1; y < tr; y++) {
			set(c, p, cu, y, cv, WOOD);
		}
		set(c, p, cu, tr, cv, STRIPPED); // the knot of the fork
		int reach = w >= 7 ? 2 : 1;
		line(c, p, cu, tr, cv, cu - reach, cb + 1, cv, WOOD);
		line(c, p, cu, tr, cv, cu + reach, cb + 2, cv, WOOD);
		for (int y = tr + 1; y <= Math.min(top - 1, cb + crownH / 2 + 1); y++) {
			set(c, p, cu, y, cv, y == cb ? STRIPPED : WOOD);
		}
		// lantern arm towards the front
		if (cv - 1 >= 0) {
			set(c, p, cu, tr + 1, cv - 1, b(Blocks.CHERRY_FENCE).setValue(BlockStateProperties.SOUTH, true));
			p.put(c, cu, tr, cv - 1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}

	/** The round planter: dark curb with a light cap, moss, petals and a small flowering bush. */
	private static void bed(CitadelCanvas c, Plot p, int w, int d, double ccu, double ccv, int cu, int cv) {
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (!inBed(u, v, ccu, ccv)) {
					// fallen petals and a little moss outside the bed
					int g = p.hash(u, 0, v, 4);
					if (g == 0) {
						set(c, p, u, 0, v, petals(p, u, v, 1 + p.hash(u, 2, v, 2)));
					} else if (g == 1) {
						set(c, p, u, 0, v, b(Blocks.MOSS_CARPET));
					}
					continue;
				}
				boolean rim = !inBed(u + 1, v, ccu, ccv) || !inBed(u - 1, v, ccu, ccv) || !inBed(u, v + 1, ccu, ccv) || !inBed(u, v - 1, ccu, ccv);
				if (rim) {
					int r = p.hash(u, 0, v, 6);
					set(c, p, u, 0, v, r < 3 ? b(Blocks.DEEPSLATE_BRICKS) : r < 5 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.CRACKED_DEEPSLATE_BRICKS));
					if (p.hash(u, 1, v, 7) != 0) { // the cap is missing now and then: blossom spills over
						set(c, p, u, 1, v, p.hash(u, 2, v, 2) == 0 ? slab(Blocks.POLISHED_DIORITE_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
					}
				} else {
					int dist = Math.abs(u - cu) + Math.abs(v - cv);
					set(c, p, u, 0, v, dist <= 1 || p.hash(u, 0, v, 6) == 0 ? b(Blocks.ROOTED_DIRT) : b(Blocks.MOSS_BLOCK));
					if (dist >= 1) {
						switch (p.hash(u, 1, v, 9)) {
							case 0, 1, 2 -> set(c, p, u, 1, v, b(Blocks.MOSS_CARPET));
							case 3, 4 -> set(c, p, u, 1, v, petals(p, u, v, 1 + p.hash(u, 3, v, 4)));
							case 5 -> set(c, p, u, 1, v, b(Blocks.FLOWERING_AZALEA));
							case 6 -> set(c, p, u, 1, v, b(Blocks.SHORT_GRASS));
							default -> {
							}
						}
					}
				}
			}
		}
	}

	private static boolean inBed(int u, int v, double ccu, double ccv) {
		double du = (u - ccu) / (ccu + 0.5);
		double dv = (v - ccv) / (ccv + 0.5);
		return du * du + dv * dv <= 1.0;
	}

	private static BlockState petals(Plot p, int u, int v, int amount) {
		Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		return b(Blocks.PINK_PETALS)
				.setValue(BlockStateProperties.FLOWER_AMOUNT, amount)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, dirs[p.hash(u, 9, v, 4)]);
	}

	/** A thick line of wood: vertical with horizontal links where it steps sideways. */
	private static void line(CitadelCanvas c, Plot p, int ua, int ya, int va, int ub, int yb, int vb, BlockState wood) {
		int steps = Math.max(1, yb - ya);
		int pu = ua;
		int pv = va;
		for (int i = 0; i <= steps; i++) {
			int y = ya + i;
			int tu = ua + rnd((double) (ub - ua) * i / steps);
			int tv = va + rnd((double) (vb - va) * i / steps);
			int u0 = Math.min(pu, tu);
			int u1 = Math.max(pu, tu);
			int v0 = Math.min(pv, tv);
			int v1 = Math.max(pv, tv);
			Direction.Axis ax = u1 > u0 || v1 > v0 ? (u1 - u0 >= v1 - v0 ? Direction.Axis.X : Direction.Axis.Z) : Direction.Axis.Y;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					set(c, p, u, y, v, wood.setValue(BlockStateProperties.AXIS, ax));
				}
			}
			pu = tu;
			pv = tv;
		}
	}

	private static int rnd(double x) {
		return (int) (Math.signum(x) * Math.floor(Math.abs(x) + 0.5));
	}

	private static void set(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
