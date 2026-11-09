package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cloud pine (niwaki) in a low stone pot, for patios. On plots of 5×5 and more the pot is two blocks high
 * (a deepslate band and a stone-brick stair lip with calcite corners), on 3–4 plots a single rim at soil
 * level, so it never hides the tree. A spruce trunk, 2×2 only on the soil, rises as a 1×1 that zig-zags one
 * cell every two layers (bark at the joints). Flat cloud pads (two-layer cushions: a cut-corner disc with a
 * smaller ragged layer on top, spruce needles with azalea and a little moss) sit at up to three heights,
 * alternating left and right with clear air between them, each reached by a short visible diagonal limb
 * that ends under its centre; the top pad crowns the leader. On 3–4 plots the tree is a short bent trunk
 * under one big flat pad as wide as the plot, with at most one small low pad. On plots of 6×6 and more a
 * raked gravel bed rings the pot, with a stone lantern on the corner away from the lowest pad and a mossy
 * rock.
 *
 * <p>Size: 3–7 × 3–7 × 8+ (the tree stops at 13 high, 9 on 3–4 plots). Below 6×6 the pot fills the plot.
 */
public final class TreeCloudPinePot implements Variant {
	private static final Wood SPRUCE = new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, b(Blocks.SPRUCE_WOOD));
	private static final Leaves NEEDLES = Leaves.of(Blocks.SPRUCE_LEAVES, Blocks.AZALEA_LEAVES).clumps(3);
	private static final Leaves CUSHION = NEEDLES.withAccent(b(Blocks.MOSS_BLOCK), 8);

	@Override
	public String id() {
		return "tree_cloud_pine_pot";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 7;
	}

	@Override
	public int maxD() {
		return 7;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xC10D);
		int w = p.w();
		int d = p.d();
		int small = Math.min(w, d);
		boolean tiny = small <= 4;
		int H = Math.min(p.h(), tiny ? 9 : 13);
		boolean ring = w >= 6 && d >= 6;
		int pu0 = ring ? 1 : 0;
		int pu1 = ring ? w - 2 : w - 1;
		int pv0 = ring ? 1 : 0;
		int pv1 = ring ? d - 2 : d - 1;
		int cu = (pu0 + pu1) / 2;
		int cv = (pv0 + pv1) / 2;
		int lo = pu0 + 1; // the soil inside the lip
		int uHi = pu1 - 1;
		int vLo = pv0 + 1;
		int vHi = pv1 - 1;

		// --- the zig-zag: the 1×1 trunk alternates between A and B = A + e every two layers
		boolean alongU = w > d || (w == d && r.nextBoolean());
		int e = r.nextBoolean() ? 1 : -1;
		int eu = alongU ? e : 0;
		int ev = alongU ? 0 : e;
		int la = alongU ? w : d; // the length of the zig-zag axis
		int lp = alongU ? d : w; // across it
		double ma = (la - 1) / 2.0;
		int aA = e > 0 ? (int) Math.floor(ma) : (int) Math.ceil(ma);
		int aP = (int) Math.floor((lp - 1) / 2.0);
		double mp = (lp - 1) / 2.0;
		int au0 = alongU ? aA : aP;
		int av0 = alongU ? aP : aA;
		int[] tu = new int[H + 1];
		int[] tv = new int[H + 1];
		for (int y = 0; y <= H; y++) {
			boolean atB = y >= 2 && ((y - 2) / 2) % 2 == 1;
			tu[y] = au0 + (atB ? eu : 0);
			tv[y] = av0 + (atB ? ev : 0);
		}

		// --- the pads: heights and sizes (two layers each, at least one air layer between them)
		int top = H - 2; // the top pad: layers top and top + 1
		int n;
		int[] py;
		double[] pr;
		if (tiny) {
			n = 2;
			py = new int[] {3, top};
			pr = new double[] {small >= 4 ? 1.3 : 1.0, 0};
		} else {
			n = Math.min(3, (top - 3) / 3 + 1);
			double[] rr = small >= 7 ? new double[] {2.6, 2.3, 2.0}
					: small >= 6 ? new double[] {2.3, 2.0, 1.8} : new double[] {1.9, 1.7, 1.6};
			py = new int[n];
			pr = new double[n];
			for (int k = 0; k < n; k++) {
				py[k] = 3 + (int) Math.round(k * (top - 3) / (double) (n - 1));
				pr[k] = k == n - 1 ? rr[2] : rr[k];
			}
		}

		// --- fixed pieces: gravel, lantern, rock, pot
		if (ring) {
			gravel(t, w, d, cu, cv, Math.atan2(-ev, -eu));
		}
		if (tiny) {
			potLow(t, w, d);
		} else {
			pot(t, pu0, pu1, pv0, pv1);
		}

		// --- the base on the soil (y = 1): a 2×2 covering A and B where the soil allows, else 1×1
		int bu0 = alongU ? Math.min(aA, aA + e) : aP;
		int bv0 = alongU ? aP : Math.min(aA, aA + e);
		if (bu0 >= lo && bu0 + 1 <= uHi && bv0 >= vLo && bv0 + 1 <= vHi) {
			for (int a = 0; a < 2; a++) {
				for (int a2 = 0; a2 < 2; a2++) {
					t.wood(bu0 + a, 1, bv0 + a2, SPRUCE.bark());
				}
			}
			// surface roots over the soil from the faces of the 2×2
			for (int u = bu0 - 1; u <= bu0 + 2; u++) {
				for (int v = bv0 - 1; v <= bv0 + 2; v++) {
					boolean outU = u < bu0 || u > bu0 + 1;
					boolean outV = v < bv0 || v > bv0 + 1;
					if (outU == outV || u < lo || u > uHi || v < vLo || v > vHi) {
						continue; // inside the 2×2, a corner, or off the soil
					}
					if (t.hash(u, 1, v, 2) == 0) {
						t.woodSoft(u, 1, v, SPRUCE.log(outU ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
		} else {
			t.wood(au0, 1, av0, SPRUCE.bark());
		}

		// --- the bending 1×1 trunk up to the underside of the top pad, bark at every joint
		int trunkTop = top - 1;
		for (int y = 2; y <= trunkTop; y++) {
			if (tu[y] != tu[y - 1] || tv[y] != tv[y - 1]) {
				t.wood(tu[y - 1], y - 1, tv[y - 1], SPRUCE.bark());
				t.wood(tu[y], y - 1, tv[y], SPRUCE.bark());
			}
			t.wood(tu[y], y, tv[y], y == trunkTop ? SPRUCE.bark() : SPRUCE.log(Direction.Axis.Y));
		}

		// --- the side pads, alternating sides, each on a short diagonal limb ending under its centre
		for (int k = 0; k < n - 1; k++) {
			int side = k % 2 == 0 ? -1 : 1;
			int ys = Math.max(2, py[k] - 2);
			double rk = pr[k];
			double rAlong = tiny ? rk : rk * 0.85;
			int margin = Math.min((la - 1) / 2, Math.max(tiny ? 0 : 1, (int) Math.floor(rAlong * 0.8)));
			int want = side > 0 ? aA + 2 * e : aA - e; // beyond B, or behind A
			int ca = TreeKit.clamp(want, margin, la - 1 - margin);
			int pcu = alongU ? ca : aP;
			int pcv = alongU ? aP : ca;
			double pcuD = alongU ? ca : mp;
			double pcvD = alongU ? mp : ca;
			TreeKit.branch(t, SPRUCE, new int[] {tu[ys], ys, tv[ys]}, new int[] {pcu, py[k] - 1, pcv}, false);
			double rx = alongU ? rAlong : rk;
			double rz = alongU ? rk : rAlong;
			TreeKit.lobe(t, pcuD, py[k], pcvD, rx, rz, 1.0, 0.0, k % 2 == 0 ? CUSHION : CUSHION.clumps(2), 0.25,
					10 + k);
		}

		// --- the top pad on the leader
		if (tiny) {
			// one big flat pad: the whole plot minus the corners, a plus (or the core) on top
			double hw = w / 2.0;
			double hd = d / 2.0;
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					double q = Math.pow((u - (w - 1) / 2.0) / hw, 2) + Math.pow((v - (d - 1) / 2.0) / hd, 2);
					boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
					if (!corner) {
						t.leaf(u, top, v, TreeKit.tone(t, CUSHION, u, top, v, 0.0, q, 40));
					}
					boolean inner = u >= 1 && u <= w - 2 && v >= 1 && v <= d - 2;
					boolean mid = (w % 2 == 1 && u == w / 2) || (d % 2 == 1 && v == d / 2);
					if (inner || mid) {
						t.leaf(u, top + 1, v, TreeKit.tone(t, CUSHION, u, top + 1, v, 1.0, q, 40));
					}
				}
			}
		} else {
			double rc = pr[n - 1];
			TreeKit.lobe(t, tu[trunkTop], top, tv[trunkTop], rc, rc * 0.9, 1.0, 0.0, CUSHION, 0.25, 40);
		}

		// moss and ferns on the soil
		for (int u = lo; u <= uHi; u++) {
			for (int v = vLo; v <= vHi; v++) {
				int hsh = t.hash(u, 2, v, 6);
				if (hsh <= 2) {
					t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
				} else if (hsh == 3) {
					t.cover(u, 1, v, b(Blocks.FERN));
				}
			}
		}
		t.prune();
		t.flush(c);
	}

	/** The pot of the 3–4 plots: a single rim at soil level (y = 0), soil inside. */
	private static void potLow(Vox t, int w, int d) {
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				if (eu && ev) {
					t.fixed(u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
				} else if (eu || ev) {
					t.fixed(u, 0, v, t.hash(u, 0, v, 4) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
				} else {
					int k = t.hash(u, 0, v, 6);
					t.fixed(u, 0, v, k == 0 ? b(Blocks.MOSS_BLOCK) : k == 1 ? b(Blocks.COARSE_DIRT) : b(Blocks.PODZOL));
				}
			}
		}
	}

	/** The low pot: a deepslate band, a stone-brick stair lip stepping outwards with calcite corners, soil inside. */
	private static void pot(Vox t, int u0, int u1, int v0, int v1) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean eu = u == u0 || u == u1;
				boolean ev = v == v0 || v == v1;
				if (eu || ev) {
					boolean corner = eu && ev;
					int k = t.hash(u, 0, v, 5);
					t.fixed(u, 0, v, corner ? b(Blocks.POLISHED_DEEPSLATE) : k == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_TILES));
					if (corner) {
						t.fixed(u, 1, v, b(Blocks.CALCITE));
					} else {
						Direction out = ev ? (v == v0 ? Direction.NORTH : Direction.SOUTH) : (u == u0 ? Direction.WEST : Direction.EAST);
						BlockState lip = t.hash(u, 1, v, 4) == 0 ? stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, out)
								: stairs(Blocks.STONE_BRICK_STAIRS, out);
						t.fixed(u, 1, v, lip);
					}
				} else {
					int k = t.hash(u, 0, v, 6);
					t.fixed(u, 0, v, k == 0 ? b(Blocks.MOSS_BLOCK) : k == 1 ? b(Blocks.COARSE_DIRT) : b(Blocks.PODZOL));
				}
			}
		}
	}

	/**
	 * Raked gravel around the pot, a toro lantern (post, lantern, slab roof) on the corner farthest from the
	 * lowest pad (direction {@code a}) and a mossy rock on the corner next to it.
	 */
	private static void gravel(Vox t, int w, int d, int cu, int cv, double a) {
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (u >= 1 && u <= w - 2 && v >= 1 && v <= d - 2) {
					continue;
				}
				int k = t.hash(u, 0, v, 8);
				t.fixed(u, 0, v, k < 5 ? b(Blocks.GRAVEL) : k < 7 ? b(Blocks.ANDESITE) : b(Blocks.COBBLESTONE));
			}
		}
		int[][] corners = {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}};
		int best = 0;
		double score = 1e9;
		for (int i = 0; i < 4; i++) {
			double s = Math.cos(a) * (corners[i][0] - cu) + Math.sin(a) * (corners[i][1] - cv);
			if (s < score) {
				score = s;
				best = i;
			}
		}
		int lu = corners[best][0];
		int lv = corners[best][1];
		t.fixed(lu, 0, lv, b(Blocks.STONE_BRICKS));
		t.fixed(lu, 1, lv, b(Blocks.STONE_BRICK_WALL));
		t.fixed(lu, 2, lv, b(Blocks.LANTERN));
		t.fixed(lu, 3, lv, slab(Blocks.STONE_BRICK_SLAB));
		// the rock on the corner across the width from the lantern
		int ru = lu == 0 ? w - 1 : 0;
		int step = ru == 0 ? 1 : -1;
		t.fixed(ru, 0, lv, b(Blocks.MOSSY_COBBLESTONE));
		t.fixed(ru, 1, lv, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		t.fixed(ru + step, 0, lv, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
	}
}
