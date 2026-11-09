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
 * Weeping willow beside the water: a mossy stone pool at the front with lily pads and stepping stones,
 * a dark-oak trunk that flares into roots and leans out over the pond, and a wide flat dome of leaves
 * whose rim lets fall a curtain of hanging leaves and vines down to the water.
 *
 * <pre>
 *  local layout (v = 0 at the front)            section
 *  v0     ▒ rim ▒ ▒ ▒ ▒ ▒ lantern                  crown   ▄▄▄▄▄▄▄▄
 *  v1-2   ▒ ≈ water, lilies, stones ≈ ▒            curtain ║║ ║║║ ║ ║   (leaves, then vines)
 *  v3     ▒ ▒ roots ▒ ▒ ▒                          trunk     ╲│
 *  v4..   ferns, moss, azalea clumps, boulder      pool   ▒≈≈≈≈▒  roots
 * </pre>
 *
 * <ul>
 * <li><b>[depth]</b> the crown has a lumpy dome profile and the curtain hangs at several lengths, so the
 *     silhouette is a ragged fringe, never a flat wall; the pool is a ring of stone with rounded corners.</li>
 * <li><b>[palette]</b> trunk dark oak (heavy) → oak / azalea / a little birch leaves (light highlights);
 *     rim mossy stone bricks, cobble and cracked bricks mixed by hash.</li>
 * <li><b>[finish]</b> root flare of stairs and mangrove roots, a hanging lantern from the front bough,
 *     a stone lantern, lilies, ferns, azalea shrubs and a mossy boulder.</li>
 * </ul>
 * Size: 7–9 × 7–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeWillowPond implements Variant {
	private static final BlockState LOG = b(Blocks.DARK_OAK_LOG);
	private static final BlockState WOOD = b(Blocks.DARK_OAK_WOOD);

	@Override
	public String id() {
		return "tree_willow_pond";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
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
		int pd = d >= 9 ? 4 : 3; // the pool covers v 0..pd
		int crownH = Math.max(4, Math.min(6, top - 3));
		int cb = top - crownH + 1; // lowest crown layer
		double ccu = (w - 1) / 2.0;
		double ccv = (d - 1) / 2.0;
		double ru = ccu + 0.4;
		double rv = ccv + 0.4;
		int tu = (w - 1) / 2;
		int tv = pd + 1;

		pond(c, p, w, pd, tu);
		ground(c, p, w, d, pd, tu, tv);

		// --- crown: a dome, wide at the bottom, narrowing to a ragged top
		boolean[][][] cr = new boolean[w][top + 1][d];
		int[][] lowest = new int[w][d];
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				lowest[u][v] = -1;
			}
		}
		for (int y = cb; y <= top; y++) {
			double t = (double) (y - cb) / (crownH - 1);
			double f = t < 0.55 ? 0.82 + 0.18 * (t / 0.55) : 1.0 - (t - 0.55) / 0.45 * 0.7;
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					double du = (u - ccu) / ru;
					double dv = (v - ccv) / rv;
					double n = (p.hash(u, y, v, 100) - 50) / 50.0 * 0.2;
					if (du * du + dv * dv + n <= f * f) {
						cr[u][y][v] = true;
						if (lowest[u][v] < 0) {
							lowest[u][v] = y;
						}
						int s = p.hash(u, y, v, 10);
						BlockState lf = s < 6 ? leaves(Blocks.OAK_LEAVES) : s < 8 ? leaves(Blocks.AZALEA_LEAVES)
								: s == 8 && y > cb ? leaves(Blocks.BIRCH_LEAVES) : leaves(Blocks.OAK_LEAVES);
						set(c, p, u, y, v, lf);
					}
				}
			}
		}

		// --- the curtain: every edge column drops leaves, then vines, at its own length
		int[][] du4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				int lo = lowest[u][v];
				if (lo < 2) {
					continue;
				}
				boolean edge = false;
				for (int[] n : du4) {
					int nu = u + n[0];
					int nv = v + n[1];
					if (nu < 0 || nv < 0 || nu >= w || nv >= d || lowest[nu][nv] < 0 || lowest[nu][nv] > lo) {
						edge = true;
					}
				}
				boolean inner = !edge && lo == cb && p.hash(u, 3, v, 7) == 0;
				if (!edge && !inner) {
					continue;
				}
				// open windows in the front curtain so the pool and the trunk show through
				if (v <= 2 && p.hash(u, 8, v, 3) == 0) {
					continue;
				}
				boolean front = v <= 2 && edge;
				int ylo;
				if (inner) {
					ylo = Math.max(1, lo - 2 - p.hash(u, 4, v, 2));
				} else if (front) {
					ylo = 2 + p.hash(u, 1, v, 3) * 2; // shorter at the front: the pool stays visible
				} else if (v <= pd) {
					ylo = 1 + p.hash(u, 1, v, 3); // over the water: reaches it
				} else {
					ylo = 2 + p.hash(u, 1, v, 4);
				}
				ylo = Math.min(ylo, lo - 1);
				boolean ew = Math.abs(u - ccu) / ru > Math.abs(v - ccv) / rv;
				curtain(c, p, u, v, lo, ylo, ew);
			}
		}

		// --- trunk: roots, flare, a lean over the pond, boughs
		int[][] flare = {{-1, 0}, {1, 0}, {0, 1}, {0, -1}};
		for (int[] f : flare) {
			int fu = tu + f[0];
			int fv = tv + f[1];
			boolean side = f[1] == 0;
			if (f[1] < 0) {
				set(c, p, fu, 0, fv, b(Blocks.MANGROVE_ROOTS));
				set(c, p, fu - 1, 0, fv, b(Blocks.MANGROVE_ROOTS));
			} else {
				set(c, p, fu, 0, fv, LOG.setValue(BlockStateProperties.AXIS, side ? Direction.Axis.X : Direction.Axis.Z));
			}
			Direction toTrunk = f[0] == -1 ? Direction.EAST : f[0] == 1 ? Direction.WEST : f[1] == 1 ? Direction.NORTH : Direction.SOUTH;
			set(c, p, fu, 1, fv, stairs(Blocks.DARK_OAK_STAIRS, toTrunk));
		}
		set(c, p, tu, 0, tv, WOOD);
		set(c, p, tu, 1, tv, WOOD);
		set(c, p, tu, 2, tv, LOG);
		set(c, p, tu, 3, tv, LOG);
		set(c, p, tu, 3, tv - 1, LOG.setValue(BlockStateProperties.AXIS, Direction.Axis.Z)); // the bend: leans towards the water
		for (int y = 4; y <= cb + 1; y++) {
			set(c, p, tu, y, tv - 1, y == cb - 1 ? WOOD : LOG);
		}
		int by = cb - 1;
		BlockState ax = LOG.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState az = LOG.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		for (int k = 1; k <= 2; k++) {
			set(c, p, tu - k, by, tv - 1, k == 2 ? WOOD.setValue(BlockStateProperties.AXIS, Direction.Axis.X) : ax);
			set(c, p, tu + k, by, tv - 1, k == 2 ? WOOD.setValue(BlockStateProperties.AXIS, Direction.Axis.X) : ax);
		}
		for (int k = 1; k <= 2 && tv - 1 - k >= 0; k++) {
			set(c, p, tu, by, tv - 1 - k, k == 2 ? WOOD.setValue(BlockStateProperties.AXIS, Direction.Axis.Z) : az);
		}
		if (tv - 3 >= 0 && by - 1 >= 1) {
			p.put(c, tu, by - 1, tv - 3, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}

	/** A hanging curtain from the crown's lowest block {@code lo} down to {@code ylo}. */
	private static void curtain(CitadelCanvas c, Plot p, int u, int v, int lo, int ylo, boolean ew) {
		int len = lo - ylo;
		int style = p.hash(u, 2, v, 4);
		int leafLen = switch (style) {
			case 0 -> len;
			case 1 -> Math.max(1, len * 2 / 3);
			case 2 -> Math.max(1, len / 3);
			default -> 0;
		};
		boolean tail = leafLen > 0 && ylo - 1 >= 1;
		for (int i = 0; i < len + (tail ? 1 : 0); i++) {
			int y = lo - 1 - i;
			if (i < leafLen) {
				set(c, p, u, y, v, leaves(p.hash(u, y, v, 3) == 0 ? Blocks.AZALEA_LEAVES : Blocks.OAK_LEAVES));
			} else {
				BlockState vine = b(Blocks.VINE)
						.setValue(ew ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
						.setValue(ew ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true);
				if (i == leafLen) {
					vine = vine.setValue(BlockStateProperties.UP, true);
				}
				set(c, p, u, y, v, vine);
			}
		}
	}

	/** The pool: a ring of mossy stone with rounded corners, water inside, lilies and stepping stones, a stone lantern. */
	private static void pond(CitadelCanvas c, Plot p, int w, int pd, int tu) {
		for (int u = 0; u < w; u++) {
			for (int v = 0; v <= pd; v++) {
				boolean edge = u == 0 || u == w - 1 || v == 0 || v == pd;
				boolean corner = (u == 1 || u == w - 2) && (v == 1 || v == pd - 1);
				if (edge || corner) {
					rim(c, p, u, v);
				} else {
					set(c, p, u, 0, v, b(Blocks.WATER));
					if (p.hash(u, 5, v, 3) == 0) {
						set(c, p, u, 1, v, b(Blocks.LILY_PAD));
					}
				}
			}
		}
		// stepping stones from the front to the trunk
		set(c, p, tu - 1, 0, 1, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		set(c, p, tu - 1, 1, 1, b(Blocks.AIR));
		set(c, p, tu, 0, 2, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		set(c, p, tu, 1, 2, b(Blocks.AIR));
		if (pd == 4) {
			set(c, p, tu, 0, 3, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
			set(c, p, tu, 1, 3, b(Blocks.AIR));
		}
		set(c, p, tu - 1, 0, 0, b(Blocks.MOSSY_STONE_BRICKS)); // landing
		// stone lantern on the front right corner
		set(c, p, w - 1, 0, 0, b(Blocks.STONE_BRICKS));
		set(c, p, w - 1, 1, 0, b(Blocks.STONE_BRICK_WALL));
		set(c, p, w - 1, 2, 0, b(Blocks.LANTERN));
		set(c, p, w - 1, 3, 0, slab(Blocks.STONE_BRICK_SLAB));
	}

	private static void rim(CitadelCanvas c, Plot p, int u, int v) {
		int r = p.hash(u, 0, v, 7);
		BlockState s = switch (r) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 2 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
			case 3 -> b(Blocks.MOSSY_COBBLESTONE);
			case 4 -> b(Blocks.DEEPSLATE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
		set(c, p, u, 0, v, s);
		if (r == 0 || r == 3) {
			int g = p.hash(u, 1, v, 4);
			if (g == 0) {
				set(c, p, u, 1, v, b(Blocks.FERN));
			} else if (g == 1) {
				set(c, p, u, 1, v, b(Blocks.SHORT_GRASS));
			}
		}
	}

	/** Behind the pool: moss, ferns, azalea clumps in the back corners and a mossy boulder. */
	private static void ground(CitadelCanvas c, Plot p, int w, int d, int pd, int tu, int tv) {
		for (int u = 0; u < w; u++) {
			for (int v = pd + 1; v < d; v++) {
				if (Math.abs(u - tu) <= 1 && Math.abs(v - tv) <= 1) {
					continue; // root flare
				}
				switch (p.hash(u, 0, v, 9)) {
					case 0 -> {
						set(c, p, u, 0, v, b(Blocks.MOSS_BLOCK));
						if (p.hash(u, 1, v, 2) == 0) {
							set(c, p, u, 1, v, b(Blocks.FERN));
						}
					}
					case 1, 2 -> set(c, p, u, 0, v, b(Blocks.FERN));
					case 3, 4 -> set(c, p, u, 0, v, b(Blocks.SHORT_GRASS));
					case 5 -> set(c, p, u, 0, v, b(Blocks.MOSS_CARPET));
					default -> {
					}
				}
			}
		}
		for (int u : new int[] {0, w - 1}) {
			set(c, p, u, 0, d - 1, leaves(Blocks.AZALEA_LEAVES));
			set(c, p, u, 1, d - 1, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			set(c, p, u, 0, d - 2, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		}
		set(c, p, w - 2, 0, d - 2, b(Blocks.MOSSY_COBBLESTONE));
		set(c, p, w - 3, 0, d - 2, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		set(c, p, w - 2, 1, d - 2, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
	}

	private static void set(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
