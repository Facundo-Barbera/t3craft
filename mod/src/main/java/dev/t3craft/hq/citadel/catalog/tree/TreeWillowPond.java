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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Weeping willow on the bank of a small pond. The pond is an irregular oval of still water (1 deep, sealed by
 * a ring of mossy stone, cobble, gravel and moss blocks, reeds on the moss, lily pads) kept two cells inside
 * the box on every side, so it can be walked all round; a stone lantern post stands on its free front corner.
 * On the other side of the box a thick spruce 2×2 trunk, with its roots, leans out over the water; its leader
 * forks into one long limb that arches out over the pond and two or three shorter ones, each rising to an apex
 * and falling to a flat, notched pad of leaves (no ball, no cube). From the outer rim of every pad hang
 * separate one-block curtains, leaves first and vines at the tail (they hang down to one block above the
 * ground), with a free cell round each strand so there are gaps; hanging roots dangle from the limbs. The
 * seed picks the side of the tree, how far it leans, how the limbs turn and how dense the curtains are.
 *
 * <p>Size: 8–15 × 8–15 × 9+ (the tree stops at 13 high). Everything is drawn through {@link TreeKit}.
 */
public final class TreeWillowPond implements Variant {
	private static final Wood WOOD = new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, b(Blocks.MANGROVE_ROOTS));
	private static final Leaves GREEN = Leaves.of(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES).clumps(3);
	private static final Leaves DEEP = Leaves.of(Blocks.AZALEA_LEAVES, Blocks.OAK_LEAVES).clumps(3);
	private static final Leaves CROWN = GREEN.withAccent(leaves(Blocks.BIRCH_LEAVES), 4);
	private static final BlockState STRAND_A = leaves(Blocks.BIRCH_LEAVES);
	private static final BlockState STRAND_B = leaves(Blocks.OAK_LEAVES);
	private static final BlockState STRAND_C = leaves(Blocks.AZALEA_LEAVES);

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
		return 8;
	}

	@Override
	public int minD() {
		return 8;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x5A11);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 14);
		double sc = TreeKit.clamp(Math.min(w, d) / 7.0, 1.0, 2.2);
		int s = r.nextBoolean() ? -1 : 1; // the side of the box the tree stands on (the pond is on the other)
		double lean = 0.5 + r.nextDouble() * 0.4;
		double spin = r.nextDouble() * 0.7 - 0.35;
		int dens = 13 + r.nextInt(4); // strands per 20 candidate cells
		double pondK = 0.92 + r.nextDouble() * 0.2;
		double[] kidney = {(r.nextDouble() - 0.5) * 1.1, 0.15 + r.nextDouble() * 0.5, 0.5 + r.nextDouble() * 0.2};
		int extraLimbs = r.nextInt(2);
		boolean longDome = r.nextBoolean();
		int hVar = r.nextInt(2);

		// the trunk's 2×2 foot: on the tree's side, towards the back
		int u0 = TreeKit.clamp((int) Math.round((w - 1) / 2.0 + s * w * 0.24) - 1, 1, w - 3);
		int v0 = TreeKit.clamp((int) Math.round(d * 0.58), 3, d - 3);

		// fixed pieces first: the tree never overwrites them
		garden(t, w, d);
		boolean[][] wet = new boolean[w][d];
		double[] centroid = pond(t, w, d, s, u0, v0, pondK, kidney, wet);
		double pu = centroid[0];
		double pv = centroid[1];

		// trunk: thick 2×2 foot, then a 1×1 leader that leans out over the water
		int yTop = TreeKit.clamp(H - 4 - hVar, 5, 9);
		int y2 = TreeKit.clamp(yTop - 3, 3, 6); // a thick 2×2 trunk, thinning only near the crown
		int ou = s > 0 ? 0 : 1; // the leader starts on the 2×2 cell nearer the pond
		int ov = 0;
		double maxLean = sc * 1.3 + 0.7;
		double leanU = TreeKit.clamp((pu - (u0 + ou)) * lean, -maxLean, maxLean);
		double leanV = TreeKit.clamp((pv - (v0 + ov)) * lean, -maxLean, maxLean);
		int[][] lead = TreeKit.trunk(t, WOOD, u0, v0, y2, yTop, ou, ov, leanU, leanV);
		int rootBias = Math.floorMod((int) Math.round(Math.atan2(pu - (u0 + 0.5), -(pv - (v0 + 0.5))) / (Math.PI / 4)), 8);
		TreeKit.roots(t, WOOD, u0, v0, sc >= 1.4 ? 7 : 5, sc >= 1.4 ? 3 : 2, rootBias, 21);
		int lu = lead[0][yTop];
		int lv = lead[1][yTop];

		// 1. the domed crown pad on the leader
		double rd = TreeKit.clamp(1.7 * sc, 1.9, 3.4);
		TreeKit.lobe(t, lu, yTop, lv, longDome ? rd * 1.2 : rd * 0.8, longDome ? rd * 0.8 : rd * 1.2, 2.2, 1.0, CROWN, 0.5, 0);

		// 2. the arching limbs: the first one reaches out over the water, the others spread round the trunk
		double ra = TreeKit.clamp(1.35 * sc, 1.5, 2.7);
		double rmax = 3.0 * sc + 0.6;
		int n = (Math.min(w, d) >= 10 ? 4 : 3) + extraLimbs;
		double ang0 = Math.atan2(pv - lv, pu - lu) + spin;
		for (int i = 0; i < n; i++) {
			double a = ang0 + i * (2 * Math.PI / n) + (i == 0 ? 0 : (t.hash(i, 3, 9, 7) - 3) * 0.12);
			limb(t, lead, yTop, i, Math.cos(a), Math.sin(a), ra, i == 0 ? rmax * 1.15 : rmax, i % 2 == 0 ? GREEN : DEEP);
		}

		// 3. the curtains: separate strands from the outer rim of the pads, a free cell all round each one
		boolean[] col = new boolean[t.w * t.d];
		boolean[] bare = new boolean[t.w * t.d]; // the trunk and its leaning leader stay visible: no strands within 2
		for (int y = 0; y <= yTop; y++) {
			int cu0 = y <= y2 ? u0 : lead[0][y];
			int cv0 = y <= y2 ? v0 : lead[1][y];
			int ext = y <= y2 ? 1 : 0;
			for (int dv = -2; dv <= 2 + ext; dv++) {
				for (int du = -2; du <= 2 + ext; du++) {
					int a = cu0 + du;
					int bb = cv0 + dv;
					if (a >= 0 && a < t.w && bb >= 0 && bb < t.d) {
						bare[bb * t.w + a] = true;
					}
				}
			}
		}
		for (int y = 3; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) != TreeKit.LEAF || !t.empty(u, y - 1, v)) {
						continue;
					}
					if (Math.hypot(u - lu, v - lv) < 1.8) {
						continue;
					}
					int ou2 = Integer.signum((int) Math.round(u - lu));
					int ov2 = Integer.signum((int) Math.round(v - lv));
					if (t.kind(u + ou2, y, v + ov2) == TreeKit.LEAF) {
						continue; // not the outer rim
					}
					if (bare[v * t.w + u] || busy(col, t.w, t.d, u, v) || t.hash(u, y, v, 20) >= dens) {
						continue;
					}
					int top = y - 1;
					int roll = t.hash(u, 4, v, 10);
					int bottom = roll < 6 ? 1 : TreeKit.clamp(top - (2 + t.hash(u, 6, v, 3)), 1, top);
					BlockState under = t.get(u, 0, v);
					int leafMin = t.kind(u, 0, v) != TreeKit.EMPTY && under != null && under.getBlock() != Blocks.WATER ? 4 : 3;
					int tail = top - bottom + 1 >= 4 ? 3 : 2;
					int pick = t.hash(u, 7, v, 4);
					boolean ew = Math.abs(u - lu) > Math.abs(v - lv);
					strand(t, u, top, v, bottom, leafMin, tail, pick <= 1 ? STRAND_A : pick == 2 ? STRAND_B : STRAND_C,
							pick <= 1 ? STRAND_B : STRAND_A, ew);
					col[v * t.w + u] = true;
				}
			}
		}

		// 4. the water's dressing, after the curtains so nothing blocks a strand: lily pads, reeds, bank plants
		dress(t, w, d, wet);

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
			if (hsh < 41 && v > pv + 1) {
				return b(Blocks.FLOWERING_AZALEA);
			}
			return null;
		});
		t.prune();

		// hanging roots dangle from the limbs (they need a log above, so they go in after the leaves are pruned)
		for (int y = 4; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) == TreeKit.WOOD && t.empty(u, y - 1, v) && t.hash(u, y, v, 4) == 0) {
						t.fixed(u, y - 1, v, b(Blocks.HANGING_ROOTS));
					}
				}
			}
		}
		t.flush(c);
	}

	/**
	 * A limb from the leader: it rises from {@code sy} to an apex at {@code yTop} half way out, then falls to a
	 * flat pad of leaves at the end (the limb ends inside the pad, so the pad hangs from it).
	 */
	private static void limb(Vox t, int[][] lead, int yTop, int i, double ex, double ez, double ra, double rmax,
			Leaves lv) {
		int sy = Math.max(3, yTop - 4);
		int ay = Math.max(sy, yTop - 2); // the apex stays under the crown pad: no bare log horns above the leaves
		int ty = Math.max(4, yTop - 4 - (i % 3)); // the limb's end: over a bank (feet at y1) it keeps 2.85 of headroom
		int su = lead[0][sy];
		int sv = lead[1][sy];
		double reach = TreeKit.clamp(TreeKit.room(t, su, sv, ex, ez, ra * 0.5), 2.0, rmax);
		int tu = TreeKit.clamp((int) Math.round(su + ex * reach), 1, t.w - 2);
		int tv = TreeKit.clamp((int) Math.round(sv + ez * reach), 1, t.d - 2);
		int au = (int) Math.round(su + (tu - su) * 0.5);
		int av = (int) Math.round(sv + (tv - sv) * 0.5);
		TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {au, ay, av}, false);
		TreeKit.branch(t, WOOD, new int[] {au, ay, av}, new int[] {tu, ty, tv}, false);
		// a flat, wide, ragged pad (full layer on the limb's end, a smaller one above): not a cube
		TreeKit.lobe(t, tu, ty + 1, tv, ra * 1.15, ra * 1.05, 1.0, 0.0, lv, 0.5, 3 + i);
	}

	/** True when a strand already hangs in this column or any of its eight neighbours. */
	private static boolean busy(boolean[] col, int w, int d, int u, int v) {
		for (int dv = -1; dv <= 1; dv++) {
			for (int du = -1; du <= 1; du++) {
				int a = u + du;
				int bb = v + dv;
				if (a >= 0 && a < w && bb >= 0 && bb < d && col[bb * w + a]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A hanging strand from {@code (u, top, v)} down to {@code bottom} (stopping at anything already there):
	 * leaves down to {@code leafMin} (so there is room to walk under it), vines for the last {@code tail}
	 * cells and everything below {@code leafMin}. The first vine clings to the solid beside it when there is
	 * one (and to the leaf above); the vines under it carry the same face, as vines do.
	 */
	private static void strand(Vox t, int u, int top, int v, int bottom, int leafMin, int tail, BlockState body,
			BlockState tip, boolean ew) {
		BooleanProperty face = null;
		boolean prevVine = false;
		for (int y = top; y >= bottom; y--) {
			if (y < 1 || !t.empty(u, y, v)) {
				break;
			}
			boolean vine = y < leafMin || y - bottom < tail;
			if (vine) {
				if (!prevVine) {
					face = solidFace(t, u, y, v);
				}
				t.leaf(u, y, v, vineState(face, ew, !prevVine));
			} else {
				t.leaf(u, y, v, y == top ? body : (t.hash(u, y, v, 3) == 0 ? tip : body));
			}
			prevVine = vine;
		}
	}

	/** The side face of the first solid (leaf, log or stone) next to the cell, or null. */
	private static BooleanProperty solidFace(Vox t, int u, int y, int v) {
		int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		BooleanProperty[] pr = {BlockStateProperties.EAST, BlockStateProperties.WEST, BlockStateProperties.SOUTH,
				BlockStateProperties.NORTH};
		for (int i = 0; i < 4; i++) {
			byte k = t.kind(u + n[i][0], y, v + n[i][1]);
			if (k == TreeKit.LEAF || k == TreeKit.WOOD || k == TreeKit.FIXED) {
				return pr[i];
			}
		}
		return null;
	}

	/** A vine: one face on a solid neighbour, else two opposite faces; the top one also clings upwards. */
	private static BlockState vineState(BooleanProperty face, boolean ew, boolean top) {
		BlockState st = b(Blocks.VINE);
		if (face != null) {
			st = st.setValue(face, true);
		} else {
			st = st.setValue(ew ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
					.setValue(ew ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true);
		}
		return top ? st.setValue(BlockStateProperties.UP, true) : st;
	}

	/**
	 * The pond: an irregular oval of water (y = 0) at least two cells inside the box on every side, so there is a
	 * ring of ground to walk round it, and never on the trunk's foot (the trunk itself holds the water back where
	 * they touch). Every other cell beside the water is a rim block (4-adjacent, so the water is sealed). A stone
	 * lantern post stands on the free front corner and a mossy pebble on the other.
	 *
	 * @param wet filled with the water cells
	 * @return the centre of the water {@code {u, v}}
	 */
	private static double[] pond(Vox t, int w, int d, int s, int u0, int v0, double k, double[] kid, boolean[][] wet) {
		double cu = (w - 1) / 2.0 - s * w * 0.07;
		double cv = Math.max(3.0, Math.min(d - 4.0, d * 0.36 + 0.9));
		double au = TreeKit.clamp(w * 0.4 * k, 2.1, 6.5);
		double av = TreeKit.clamp(d * 0.28 * k, 1.7, 4.5);
		// a second, smaller oval offset from the first makes the water kidney-shaped
		double cu2 = cu + kid[0] * au;
		double cv2 = cv + kid[1] * av;
		double au2 = au * kid[2];
		double av2 = av * kid[2];
		int e = w >= 11 && d >= 11 ? 1 : 0; // a strip of bank between the trunk and the water on big plots
		double su = 0;
		double sv = 0;
		int cnt = 0;
		for (int v = 2; v <= d - 3; v++) {
			for (int u = 2; u <= w - 3; u++) {
				if (u >= u0 - e && u <= u0 + 1 + e && v >= v0 - e && v <= v0 + 1 + e) {
					continue;
				}
				double du = (u - cu) / au;
				double dv = (v - cv) / av;
				double jit = (t.hash(u, 3, v, 9) - 4) * 0.05;
				double du2 = (u - cu2) / au2;
				double dv2 = (v - cv2) / av2;
				if (du * du + dv * dv <= 1.0 + jit || du2 * du2 + dv2 * dv2 <= 1.0) {
					wet[u][v] = true;
					su += u;
					sv += v;
					cnt++;
				}
			}
		}
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (wet[u][v]) {
					t.fixed(u, 0, v, b(Blocks.WATER));
					continue;
				}
				if (u >= u0 && u <= u0 + 1 && v >= v0 && v <= v0 + 1) {
					continue; // the trunk's foot
				}
				boolean rim = false;
				for (int[] n : n4) {
					int nu = u + n[0];
					int nv = v + n[1];
					if (nu >= 0 && nu < w && nv >= 0 && nv < d && wet[nu][nv]) {
						rim = true;
					}
				}
				if (rim) {
					rim(t, u, v);
				}
			}
		}
		// the stone lantern post on the front corner of the pond side (the front stays clear of water)
		int lx = s > 0 ? 0 : w - 1;
		t.fixed(lx, 0, 0, b(Blocks.STONE_BRICKS));
		t.fixed(lx, 1, 0, b(Blocks.STONE_BRICK_WALL));
		t.fixed(lx, 2, 0, b(Blocks.LANTERN));
		t.fixed(lx, 3, 0, slab(Blocks.STONE_BRICK_SLAB));
		// a pebble on the other front corner
		t.fixed(s > 0 ? w - 1 : 0, 0, 0, b(Blocks.MOSS_CARPET));
		if (cnt == 0) {
			return new double[] {cu, cv};
		}
		return new double[] {su / cnt, sv / cnt};
	}

	private static void rim(Vox t, int u, int v) {
		int k = t.hash(u, 0, v, 10);
		BlockState s = switch (k) {
			case 0, 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 2, 3 -> b(Blocks.MOSSY_COBBLESTONE);
			case 4, 5 -> b(Blocks.GRAVEL);
			case 6, 7 -> b(Blocks.MOSS_BLOCK);
			case 8 -> b(Blocks.COBBLESTONE);
			default -> b(Blocks.STONE_BRICKS);
		};
		t.fixed(u, 0, v, s);
	}

	/**
	 * Lily pads on the water, reeds (sugar cane, which needs the moss and the water beside it) on the moss rim
	 * blocks, and ferns and moss carpet on the other rim blocks.
	 */
	private static void dress(Vox t, int w, int d, boolean[][] wet) {
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (wet[u][v]) {
					boolean open = true; // lily pads only in the open: under the canopy they read as unreachable floor
					for (int y = 1; y < t.h; y++) {
						open &= t.empty(u, y, v);
					}
					if (open && t.hash(u, 5, v, 3) == 0) {
						t.cover(u, 1, v, b(Blocks.LILY_PAD));
					} else if (open && t.hash(u, 9, v, 7) == 0) {
						// a stepping stone just under the surface
						t.fixed(u, 0, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB).setValue(BlockStateProperties.WATERLOGGED, true));
					}
					continue;
				}
				BlockState st = t.get(u, 0, v);
				if (st == null || t.kind(u, 0, v) != TreeKit.FIXED) {
					continue;
				}
				if (st.getBlock() == Blocks.MOSS_BLOCK) {
					int h = 1 + t.hash(u, 8, v, 3);
					for (int y = 1; y <= h; y++) {
						if (t.empty(u, y, v)) {
							t.fixed(u, y, v, b(Blocks.SUGAR_CANE));
						}
					}
				} else if (st.getBlock() == Blocks.MOSSY_STONE_BRICKS || st.getBlock() == Blocks.MOSSY_COBBLESTONE) {
					int g = t.hash(u, 1, v, 4);
					if (g == 0) {
						t.cover(u, 1, v, b(Blocks.FERN));
					} else if (g == 1) {
						t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
					}
				}
			}
		}
	}

	/** Behind the pond: a flowering azalea bush in one back corner and a mossy boulder in the other. */
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
		t.fixed(rockU - in, 0, d - 1, slab(Blocks.COBBLESTONE_SLAB));
		t.fixed(rockU, 0, d - 2, slab(Blocks.COBBLESTONE_SLAB));
	}
}
