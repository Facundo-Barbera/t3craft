package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Weeping willow beside the water: an oval pool at the front (a mossy stone rim with rounded corners,
 * water, lily pads, submerged stepping stones and a stone lantern on the free corner). Right behind the
 * rim stands a dark-oak 2×2 trunk on diagonal roots, bare for its first blocks; its leader carries a domed
 * crown lobe, two limbs run out to lower side lobes, and one limb arches up and over the pond (rise, apex,
 * fall) to a lobe hanging above the water. Each lobe keeps a notch of air from the next, in two greens.
 * Only the outer rim of the crown hangs: separate 1-wide strands, 2–5 long, never side by side nor
 * diagonal neighbours, stopping two blocks above the water, leaves with a vine at the tip. Ferns, moss, a
 * flowering azalea bush and a mossy boulder behind.
 *
 * <p>Size: 7–9 × 7–9 × 9+ (the tree stops at 12 high).
 */
public final class TreeWillowPond implements Variant {
	private static final Wood WOOD = new Wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, b(Blocks.MANGROVE_ROOTS));
	private static final Leaves GREEN = Leaves.of(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES).clumps(3);
	private static final Leaves DEEP = Leaves.of(Blocks.AZALEA_LEAVES, Blocks.DARK_OAK_LEAVES).clumps(3);
	private static final Leaves CROWN = GREEN.withAccent(leaves(Blocks.BIRCH_LEAVES), 7);

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
		Vox t = new Vox(p);
		Random r = p.rng(0x5A11);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 12);
		double sc = Math.min(w, d) / 7.0;
		int pd = d >= 9 ? 4 : 3; // the pool covers v 0..pd
		int bu = (w - 2) / 2;
		int bv = pd + 1; // the 2×2 stands right behind the back rim
		int s = r.nextBoolean() ? -1 : 1; // the side the arching limb reaches out to

		// the fixed pieces first: the tree never overwrites them
		pond(t, w, pd, bu);
		garden(t, w, d);

		// trunk: an upright 2×2 on roots, its leader bare up to the limbs (the trunk shows under the crown)
		int yTop = H - 3;
		int ou = s < 0 ? 0 : 1;
		int[][] lead = TreeKit.trunk(t, WOOD, bu, bv, 2, yTop, ou, 0, 0, 0);
		TreeKit.roots(t, WOOD, bu, bv, 5, 2, 4, 21);
		int lu = lead[0][yTop];
		int lv = lead[1][yTop];

		// 1. the domed crown lobe on the leader (two cap layers), a little back from the pond
		double rd = TreeKit.clamp(1.9 * sc, 1.9, 2.4);
		TreeKit.lobe(t, lu, yTop, lv + 0.3, rd, rd * 0.9, 2.2, 1.0, CROWN, 0.35, 0);

		// 2. the arching limb: up from the bare trunk to an apex, then out and down over the water to a lobe
		int ay = yTop - 1;
		int ty = ay - (H >= 11 ? 2 : 1);
		int sy = Math.max(3, ay - 3);
		int asu = lead[0][sy];
		int asv = lead[1][sy];
		int tv = pd / 2;
		int tu = TreeKit.clamp(asu + s * (w >= 8 ? 3 : 2), 1, w - 2);
		int au = (int) Math.round(asu + (tu - asu) * 0.45);
		int av = (int) Math.round(asv + (tv - asv) * 0.45);
		TreeKit.branch(t, WOOD, new int[] {asu, sy, asv}, new int[] {au, ay, av}, false);
		TreeKit.branch(t, WOOD, new int[] {au, ay, av}, new int[] {tu, ty, tv}, false);
		double ra = TreeKit.clamp(1.6 * sc, 1.6, 2.0);
		TreeKit.lobe(t, tu, ty + 1, tv, ra, ra * 0.9, 1.0, 0.0, DEEP, 0.35, 3);

		// 3. two lower side lobes on short limbs (crown underside four or more blocks above the ground)
		int cyS = yTop - 1;
		int cy2 = yTop >= 7 ? cyS - 1 : cyS;
		limbLobe(t, lead, Math.max(3, cyS - 2), -s, 0.35, TreeKit.clamp(1.9 * sc, 1.8, 2.3), cyS, GREEN, 1);
		limbLobe(t, lead, Math.max(3, cy2 - 1), s * 0.6, 0.8, TreeKit.clamp(1.7 * sc, 1.6, 2.1), cy2, DEEP.swap(), 2);

		// the curtain: only the outer rim hangs, separate strands with a free cell all round each
		double ccu = bu + 0.5;
		double ccv = bv + 0.5;
		boolean[] col = new boolean[t.w * t.d];
		for (int y = 3; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) != TreeKit.LEAF || !t.empty(u, y - 1, v)) {
						continue;
					}
					if (Math.hypot(u - ccu, v - ccv) < 1.8) {
						continue;
					}
					int ou2 = Integer.signum((int) Math.round(u - ccu));
					int ov2 = Integer.signum((int) Math.round(v - ccv));
					if (t.kind(u + ou2, y, v + ov2) == TreeKit.LEAF) {
						continue; // not the outer rim
					}
					if (busy(col, t.w, t.d, u, v)) {
						continue;
					}
					if (t.hash(u, y, v, 20) >= 12) {
						continue; // gaps
					}
					int room = y - 2; // cells y - 1 down to 2: the lowest stays two blocks over the water
					if (room < 2) {
						continue;
					}
					int len = Math.min(2 + t.hash(u, 2, v, 4), room);
					int vines = len >= 3 ? 1 : 0;
					boolean ew = Math.abs(u - ccu) > Math.abs(v - ccv);
					boolean deep = t.hash(u, 7, v, 3) == 0;
					strand(t, u, y - 1, v, len, vines, deep ? DEEP.a() : GREEN.a(), deep ? GREEN.a() : DEEP.a(), ew);
					col[v * t.w + u] = true;
				}
			}
		}

		// ferns, grass, moss and a few flowering azaleas on the free ground
		TreeKit.ground(t, (w - 1) / 2.0, (d - 1) / 2.0, Math.max(w, d), (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (hsh < 16) {
				return b(Blocks.FERN);
			}
			if (hsh < 28) {
				return b(Blocks.SHORT_GRASS);
			}
			if (hsh < 38) {
				return b(Blocks.MOSS_CARPET);
			}
			if (hsh < 41 && v > pd) {
				return b(Blocks.FLOWERING_AZALEA);
			}
			return null;
		});
		t.prune();
		t.flush(c);
	}

	/**
	 * A limb from the leader at {@code sy} out along {@code (ex, ez)} to a lobe of radius {@code rad} centred
	 * at height {@code cy}; the limb ends on the lobe's underside (cy - 1).
	 */
	private static void limbLobe(Vox t, int[][] lead, int sy, double ex, double ez, double rad, int cy, Leaves lv,
			int salt) {
		double n = Math.hypot(ex, ez);
		double nx = ex / n;
		double nz = ez / n;
		int su = lead[0][sy];
		int sv = lead[1][sy];
		double reach = TreeKit.clamp(TreeKit.room(t, su, sv, nx, nz, 0) - rad * 0.4, 1.5, 3.2);
		int tu = (int) Math.round(su + nx * reach);
		int tv = (int) Math.round(sv + nz * reach);
		TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {tu, cy - 1, tv}, false);
		TreeKit.lobe(t, tu, cy, tv, rad, rad * 0.9, 1.0, 1.0, lv, 0.4, salt);
	}

	/** True when a strand already hangs in this column or any of its eight neighbours. */
	private static boolean busy(boolean[] col, int w, int d, int u, int v) {
		for (int dv = -1; dv <= 1; dv++) {
			for (int du = -1; du <= 1; du++) {
				int a = u + du;
				int b = v + dv;
				if (a >= 0 && a < w && b >= 0 && b < d && col[b * w + a]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A straight hanging strand from {@code (u, yTop, v)} down {@code len} cells (never below y = 2, and it
	 * stops at anything already there): leaves, the last {@code vines} cells vines (else the {@code tip} leaf).
	 */
	private static void strand(Vox t, int u, int yTop, int v, int len, int vines, BlockState body, BlockState tip,
			boolean ew) {
		for (int k = 0; k < len; k++) {
			int y = yTop - k;
			if (y < 2 || !t.empty(u, y, v)) {
				break;
			}
			boolean vine = k >= len - vines;
			BlockState st = vine ? vine(ew, k == len - vines) : k == len - 1 ? tip : body;
			t.leaf(u, y, v, st);
		}
	}

	/** A vine hanging in the middle of its cell: two opposite faces, and the top one also clings upwards. */
	private static BlockState vine(boolean ew, boolean top) {
		BlockState s = b(Blocks.VINE)
				.setValue(ew ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
				.setValue(ew ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true);
		return top ? s.setValue(BlockStateProperties.UP, true) : s;
	}

	/**
	 * The pool: an oval of water inside the box v 0..pd, ringed (4-adjacent, so the water is sealed) by a
	 * mixed mossy rim; the box corners outside the ring stay ground, which rounds the outline. Submerged
	 * stepping stones zig-zag from the front to the trunk, lily pads float, and a stone lantern stands on the
	 * front right corner.
	 */
	private static void pond(Vox t, int w, int pd, int bu) {
		double cu = (w - 1) / 2.0;
		double au = cu - 0.5;
		double cv = pd / 2.0;
		double av = cv - 0.3;
		boolean[][] wet = new boolean[w][pd + 1];
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 1; v <= pd - 1; v++) {
				double du = (u - cu) / au;
				double dv = (v - cv) / av;
				wet[u][v] = du * du + dv * dv <= 1.0;
			}
		}
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int u = 0; u < w; u++) {
			for (int v = 0; v <= pd; v++) {
				if (wet[u][v]) {
					boolean stone = u == bu - 1 + (v & 1) && v >= 1;
					if (stone) {
						t.fixed(u, 0, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB).setValue(BlockStateProperties.WATERLOGGED, true));
					} else {
						t.fixed(u, 0, v, b(Blocks.WATER));
						if (t.hash(u, 5, v, 3) == 0) {
							t.fixed(u, 1, v, b(Blocks.LILY_PAD));
						}
					}
					continue;
				}
				boolean rim = false;
				for (int[] n : n4) {
					int nu = u + n[0];
					int nv = v + n[1];
					if (nu >= 0 && nu < w && nv >= 0 && nv <= pd && wet[nu][nv]) {
						rim = true;
					}
				}
				if (rim) {
					rim(t, u, v);
				}
			}
		}
		// the stone lantern on the front right corner (outside the ring)
		t.fixed(w - 1, 0, 0, b(Blocks.STONE_BRICKS));
		t.fixed(w - 1, 1, 0, b(Blocks.STONE_BRICK_WALL));
		t.fixed(w - 1, 2, 0, b(Blocks.LANTERN));
		t.fixed(w - 1, 3, 0, slab(Blocks.STONE_BRICK_SLAB));
		// a pebble on the front left corner
		t.fixed(0, 0, 0, b(Blocks.MOSS_CARPET)); // flat: a slab here was a lone bump at the plot corner
	}

	private static void rim(Vox t, int u, int v) {
		int k = t.hash(u, 0, v, 8);
		BlockState s = switch (k) {
			case 0, 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 2 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 3 -> b(Blocks.MOSSY_COBBLESTONE);
			case 4 -> b(Blocks.COBBLESTONE);
			default -> b(Blocks.STONE_BRICKS);
		};
		t.fixed(u, 0, v, s);
		if (k <= 1 || k == 3) {
			int g = t.hash(u, 1, v, 4);
			if (g == 0) {
				t.cover(u, 1, v, b(Blocks.FERN));
			} else if (g == 1) {
				t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
			}
		}
	}

	/** Behind the pool: a flowering azalea bush in one back corner and a mossy boulder in the other. */
	private static void garden(Vox t, int w, int d) {
		boolean flip = t.hash(1, 2, 3, 2) == 0;
		int bushU = flip ? w - 1 : 0;
		int rockU = flip ? 0 : w - 1;
		int in = flip ? -1 : 1; // from the bush corner towards the middle
		t.fixed(bushU, 0, d - 1, leaves(Blocks.AZALEA_LEAVES));
		t.fixed(bushU, 1, d - 1, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		t.fixed(bushU, 0, d - 2, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		t.fixed(bushU + in, 0, d - 1, leaves(Blocks.AZALEA_LEAVES));
		t.fixed(rockU, 0, d - 1, b(Blocks.MOSSY_COBBLESTONE));
		t.fixed(rockU, 1, d - 1, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		t.fixed(rockU - in, 0, d - 1, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		t.fixed(rockU, 0, d - 2, slab(Blocks.COBBLESTONE_SLAB));
	}
}
