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
 * Cloud pine (niwaki) in a low stone pot, for patios. The pot is only two blocks high (a deepslate band
 * and a stone-brick stair lip with calcite corners) with podzol and moss inside, so the tree reads above
 * it. A twisting spruce trunk (2×2 when the soil allows, else 1×1 with a root flare) zig-zags upwards,
 * bending towards each pad; from every bend a visible limb runs out to a flat cloud pad (a two-layer
 * cushion: wide rounded bottom, smaller rounded top, spruce needles with azalea and a little moss), the pads
 * at different heights around the trunk with the limbs showing in the gaps, and a domed crown pad on top.
 * On plots of 6×6 and more a raked gravel bed rings the pot, with a stone lantern on the corner away from
 * the lowest pad and a mossy rock.
 *
 * <p>Size: 3–7 × 3–7 × 8+ (the tree stops at 12 high). Below 6×6 the pot fills the plot.
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
		int H = Math.min(p.h(), 12);
		boolean ring = w >= 6 && d >= 6;
		int pu0 = ring ? 1 : 0;
		int pu1 = ring ? w - 2 : w - 1;
		int pv0 = ring ? 1 : 0;
		int pv1 = ring ? d - 2 : d - 1;
		int iw = pu1 - pu0 - 1; // the soil inside the lip
		int id = pv1 - pv0 - 1;
		int cu = (pu0 + pu1) / 2;
		int cv = (pv0 + pv1) / 2;
		int small = Math.min(w, d);

		// --- the plan of the pads: heights, directions, sizes
		// pads two layers thick, at least two layers apart (consecutive pads sit on opposite sides), the lowest
		// clear of the lip, the crown pad two layers under the top
		int crownY = H - 2;
		int y0 = crownY >= 9 ? 4 : 3;
		int nMax = small <= 4 ? 2 : 3;
		int n = TreeKit.clamp((crownY - y0) / 2, 1, nMax);
		double gap = (crownY - y0) / (double) n;
		double rBase = small >= 6 ? 2.1 : small >= 5 ? 1.8 : 1.5;
		double a0 = r.nextDouble() * Math.PI * 2;
		int[] py = new int[n];
		double[] ang = new double[n];
		for (int k = 0; k < n; k++) {
			py[k] = y0 + (int) Math.round(k * gap);
			ang[k] = a0 + k * (Math.PI * 0.85 + (r.nextDouble() - 0.5) * 0.4);
		}

		// --- fixed pieces: gravel, lantern, rock, pot
		if (ring) {
			gravel(t, w, d, cu, cv, ang[0]);
		}
		pot(t, pu0, pu1, pv0, pv1);

		// --- the base on the soil (y = 1)
		int lu;
		int lv;
		int lo = pu0 + 1;
		int uHi = pu1 - 1;
		int vLo = pv0 + 1;
		int vHi = pv1 - 1;
		if (iw >= 3 && id >= 3) {
			int bu = pu0 + 1 + (iw - 2) / 2;
			int bv = pv0 + 1 + (id - 2) / 2;
			int ou = r.nextInt(2);
			int ov = r.nextInt(2);
			for (int a = 0; a < 2; a++) {
				for (int e = 0; e < 2; e++) {
					t.wood(bu + a, 1, bv + e, SPRUCE.bark());
					if (a == ou || e == ov) {
						t.wood(bu + a, 2, bv + e, SPRUCE.log(Direction.Axis.Y));
					}
				}
			}
			lu = bu + ou;
			lv = bv + ov;
			// surface roots over the soil from the faces of the 2×2
			for (int u = bu - 1; u <= bu + 2; u++) {
				for (int v = bv - 1; v <= bv + 2; v++) {
					boolean outU = u < bu || u > bu + 1;
					boolean outV = v < bv || v > bv + 1;
					if (outU == outV || u < lo || u > uHi || v < vLo || v > vHi) {
						continue; // inside the 2×2, a corner, or off the soil
					}
					if (t.hash(u, 1, v, 2) == 0) {
						t.woodSoft(u, 1, v, SPRUCE.log(outU ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
		} else {
			lu = cu;
			lv = cv;
			t.wood(cu, 1, cv, SPRUCE.bark());
			t.wood(cu, 2, cv, SPRUCE.log(Direction.Axis.Y));
			// a flare: a root or two on the soil around the foot
			int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			int made = 0;
			for (int[] f : n4) {
				int u = cu + f[0];
				int v = cv + f[1];
				if (made < 2 && u >= lo && u <= uHi && v >= vLo && v <= vHi && t.hash(u, 1, v, 3) != 0) {
					t.woodSoft(u, 1, v, SPRUCE.log(f[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
					made++;
				}
			}
		}

		// --- the trunk zig-zags through a bend under every pad, a limb runs from each bend to its pad
		int uMin = w >= 3 ? 1 : 0;
		int uMax = w >= 3 ? w - 2 : w - 1;
		int vMin = d >= 3 ? 1 : 0;
		int vMax = d >= 3 ? d - 2 : d - 1;
		int[] prev = {lu, 2, lv};
		for (int k = 0; k < n; k++) {
			double ex = Math.cos(ang[k]);
			double ez = Math.sin(ang[k]);
			int[] bend = {TreeKit.clamp(cu + (int) Math.round(ex), uMin, uMax), py[k] - 1,
					TreeKit.clamp(cv + (int) Math.round(ez), vMin, vMax)};
			TreeKit.branch(t, SPRUCE, prev, bend, false);
			double rk = rBase - 0.2 * k;
			double reach = TreeKit.clamp(TreeKit.room(t, bend[0], bend[2], ex, ez, 0) - rk * 0.5, 0.8, 2.4);
			int tu = TreeKit.clamp((int) Math.round(bend[0] + ex * reach), 0, w - 1);
			int tv = TreeKit.clamp((int) Math.round(bend[2] + ez * reach), 0, d - 1);
			TreeKit.branch(t, SPRUCE, bend, new int[] {tu, py[k], tv}, false);
			double rz = rk * (0.85 + r.nextDouble() * 0.15);
			TreeKit.lobe(t, tu, py[k], tv, rk, rz, 1.2, 0.6, k % 2 == 0 ? CUSHION : CUSHION.clumps(2), 0.3, 10 + k);
			prev = bend;
		}
		// the crown pad, domed a little more, on the end of the leader
		int[] crown = {cu, crownY, cv};
		TreeKit.branch(t, SPRUCE, prev, crown, false);
		double rc = rBase - 0.3;
		TreeKit.lobe(t, cu, crownY, cv, rc, rc * 0.9, 1.5, 0.6, CUSHION, 0.3, 40);

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
