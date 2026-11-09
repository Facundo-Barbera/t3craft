package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pruned garden pine (niwaki): three to five flat "clouds" of needles stepped up a leaning, sinuous trunk. Each
 * cloud is a wide ragged pad with a smaller mound on top, carried on its own visible bough that leaves the trunk
 * just below it; between the clouds there is air, so the trunk and the boughs read as a silhouette. The clouds
 * alternate to either side of the trunk, shrink towards the top, and the topmost sits on the leader.
 *
 * <p>Walk: the trunk is 1×1 above the root flare, the lowest bough is at y = 3 (three clear blocks under it and
 * nothing hangs below y = 4), so you can pass under the first cloud. Spruce needles with clumps of dark-oak green
 * on the sunny tops, ferns, moss and a mossy boulder at the foot.
 *
 * <p>Seed variation: number of tiers (3..5 as the height allows), lean direction, the side the first cloud takes,
 * the trunk's waves, the length of each bough, the shape of each cloud's rim.
 *
 * <p>Size: 5–9 × 5–9 × 13+ (the tree stops at 18 high).
 */
public final class TreePineTiered implements Variant {
	private static final Wood WOOD = new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, b(Blocks.MANGROVE_ROOTS));
	private static final BlockState NEEDLE = leaves(Blocks.SPRUCE_LEAVES);
	private static final BlockState TIP = leaves(Blocks.DARK_OAK_LEAVES);
	private static final BlockState LIGHT = leaves(Blocks.AZALEA_LEAVES);

	/** The lowest cloud's bottom layer: the bough under it is at y = 3. */
	private static final int FIRST = 4;

	@Override
	public String id() {
		return "tree_pine_tiered";
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
		return 13;
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
		Random r = p.rng(0x9177);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 18);

		// ---- base: a 2×2 foot with roots, thinning to one trunk at y = 3
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(w, ou);
		int bv = TreeKit.base(d, ov);
		int[][] lead = TreeKit.trunk(t, WOOD, bu, bv, 2, 2, ou, ov, 0, 0);
		int cu0 = lead[0][2];
		int cv0 = lead[1][2];
		boolean big = Math.min(w, d) >= 7;
		TreeKit.roots(t, WOOD, bu, bv, big ? 4 : 3, 1, -1, 7);

		// ---- tiers: 3..5 clouds, at least 3 layers apart, the last one on the leader
		int bLast = H - 1;
		int nMax = Math.min(5, (int) ((bLast - FIRST) / 3.0) + 1);
		int n = nMax <= 3 ? 3 : 3 + r.nextInt(nMax - 2);
		int[] yb = new int[n];
		int[] th = new int[n];
		double gap = (bLast - FIRST) / (double) (n - 1);
		for (int k = 0; k < n; k++) {
			yb[k] = FIRST + (int) Math.round(k * gap);
			// a pad with a mound over it only where the gap to the next cloud stays open; else a single layer
			th[k] = gap >= 4.9 && yb[k] + 1 < H ? 2 : 1;
		}
		int yTop = yb[n - 1] - 1; // the trunk ends inside the top pad: no bare log head over the crown

		// ---- lean, sway and the side of the first cloud
		double ang = r.nextInt(8) * Math.PI / 4;
		double lu = Math.cos(ang);
		double lv = Math.sin(ang);
		double pu = -lv; // across the lean: where the clouds go
		double pv = lu;
		int leanMax = big ? 2 : 1;
		double phase = r.nextDouble() * Math.PI * 2;
		double sgn0 = r.nextBoolean() ? 1 : -1;

		// ---- the trunk, one cell per layer, a corner of bark where it steps
		int[] su = new int[H + 1];
		int[] sv = new int[H + 1];
		int cu = cu0;
		int cv = cv0;
		for (int y = 0; y <= 2; y++) {
			su[y] = cu0;
			sv[y] = cv0;
		}
		for (int y = 3; y <= yTop; y++) {
			double f = (y - 3) / (double) Math.max(1, yTop - 3);
			double e = f * f * (3 - 2 * f);
			double sway = Math.sin(phase + (y - 3) * 0.4) * 0.3 * Math.min(1.0, (y - 2) / 3.0);
			int tu = TreeKit.clamp((int) Math.round(cu0 + lu * leanMax * e + pu * sway), 1, w - 2);
			int tv = TreeKit.clamp((int) Math.round(cv0 + lv * leanMax * e + pv * sway), 1, d - 2);
			int du = Integer.signum(tu - cu);
			int dv = Integer.signum(tv - cv);
			if (du != 0 && dv != 0) {
				if ((y & 1) == 0) {
					dv = 0;
				} else {
					du = 0;
				}
			}
			if (du != 0 || dv != 0) {
				t.wood(cu, y - 1, cv, WOOD.bark());
				t.wood(cu + du, y - 1, cv + dv, WOOD.bark());
			}
			cu += du;
			cv += dv;
			su[y] = cu;
			sv[y] = cv;
			t.wood(cu, y, cv, WOOD.log(net.minecraft.core.Direction.Axis.Y));
		}
		for (int y = yTop + 1; y <= H; y++) {
			su[y] = cu;
			sv[y] = cv;
		}

		// ---- clouds and their boughs
		double half = (Math.min(w, d) - 1) / 2.0;
		for (int k = 0; k < n; k++) {
			int b = yb[k];
			double fk = n == 1 ? 0 : k / (double) (n - 1);
			double rad = half * (0.86 - 0.28 * fk) + 0.35;
			double sgn = sgn0 * ((k & 1) == 0 ? 1 : -1);
			boolean top = k == n - 1;
			double off = top ? 0 : Math.max(1.0, half * (0.42 + 0.26 * r.nextDouble()));
			double bu2 = pu * sgn;
			double bv2 = pv * sgn;
			boolean longU = Math.abs(Math.abs(bu2) - Math.abs(bv2)) < 1e-6 ? r.nextBoolean() : Math.abs(bu2) > Math.abs(bv2);
			double rx = Math.min(rad * (longU ? 1.2 : 0.9), (w - 1) / 2.0 + 0.4);
			double rz = Math.min(rad * (longU ? 0.9 : 1.2), (d - 1) / 2.0 + 0.4);
			double ccu = TreeKit.clamp(su[b] + bu2 * off, rx - 0.4, w - 1 - (rx - 0.4));
			double ccv = TreeKit.clamp(sv[b] + bv2 * off, rz - 0.4, d - 1 - (rz - 0.4));
			int ci = (int) Math.round(ccu);
			int cj = (int) Math.round(ccv);
			if (!top) {
				// the bough: leaves the trunk a layer or two under the pad, ends under its middle and climbs into it
				int ya = Math.max(3, b - 2);
				TreeKit.branch(t, WOOD, new int[] {su[ya], ya, sv[ya]}, new int[] {ci, b - 1, cj}, false);
			}
			if (!top && b - 2 >= 3) {
				// a short dead stub on the other side: a limb that was pruned
				int ou2 = (int) Math.signum(su[b - 2] - ci);
				int ov2 = (int) Math.signum(sv[b - 2] - cj);
				if (Math.abs(su[b - 2] - ci) >= Math.abs(sv[b - 2] - cj)) {
					ov2 = 0;
				} else {
					ou2 = 0;
				}
				if (ou2 != 0 || ov2 != 0) {
					t.woodSoft(su[b - 2] + ou2, b - 2, sv[b - 2] + ov2, WOOD.log(ou2 != 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z));
					t.woodSoft(su[b - 2] + 2 * ou2, b - 1, sv[b - 2] + 2 * ov2, WOOD.bark());
					t.woodSoft(su[b - 2] + 2 * ou2, b - 2, sv[b - 2] + 2 * ov2, WOOD.bark());
				}
			}
			cloud(t, ccu, ccv, b, th[k], rx, rz, k + 1, phase + k * 1.7, H, gap >= 4.9, k > 0 && gap >= 3.4);
		}

		// ---- the foot: ferns, moss, mushrooms, and a mossy boulder away from the lean
		TreeKit.ground(t, cu0 + 0.5, cv0 + 0.5, Math.min(w, d) / 2.0 + 0.5, (u, v, k) -> switch (t.hash(u, 0, v, 9)) {
			case 0, 1 -> b(Blocks.FERN);
			case 2, 3 -> b(Blocks.MOSS_CARPET);
			case 4 -> k > 0.4 ? b(Blocks.BROWN_MUSHROOM) : null;
			case 5 -> b(Blocks.LEAF_LITTER);
			default -> null;
		});
		int ru = TreeKit.clamp((int) Math.round(cu0 - lu * 2.4), 0, w - 1);
		int rv = TreeKit.clamp((int) Math.round(cv0 - lv * 2.4), 0, d - 1);
		if (t.empty(ru, 0, rv)) {
			t.fixed(ru, 0, rv, b(Blocks.MOSSY_COBBLESTONE));
			int su2 = ru + (ru + 1 < w && t.hash(ru, 3, rv, 2) == 0 ? 1 : -1);
			if (t.empty(su2, 0, rv)) {
				t.fixed(su2, 0, rv, b(Blocks.MOSSY_COBBLESTONE_SLAB));
			}
		}
		t.prune();
		t.flush(c);
	}

	/**
	 * One cloud: a wide ragged pad on {@code yb} and a smaller mound over it (a single wider layer when
	 * {@code thick == 1}). The rim is scalloped by two sine waves and a per-cell jitter, some rim cells hang one or
	 * two blocks (never below y = 4, never touching another cloud), and the top gets a few tufts.
	 */
	private static void cloud(Vox t, double cu, double cv, int yb, int thick, double rx, double rz, int id, double ph, int H, boolean hangOk, boolean belly) {
		for (int ly = 0; ly < thick; ly++) {
			int y = yb + ly;
			if (y >= H) {
				break;
			}
			double scale = thick == 1 ? 1.0 : ly == 0 ? 1.0 : 0.66;
			int u0 = (int) Math.floor(cu - rx * 1.5);
			int u1 = (int) Math.ceil(cu + rx * 1.5);
			int v0 = (int) Math.floor(cv - rz * 1.5);
			int v1 = (int) Math.ceil(cv + rz * 1.5);
			for (int v = v0; v <= v1; v++) {
				for (int u = u0; u <= u1; u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double q = Math.hypot(du, dv);
					double a = Math.atan2(dv, du);
					double m = 1 + 0.15 * Math.sin(5 * a + ph) + 0.09 * Math.sin(8 * a + 2 * ph);
					double lim = scale * m + (t.hash(u, y, v, 9) - 4) / 4.0 * 0.11;
					if (q > lim) {
						continue;
					}
					boolean topLayer = ly == thick - 1;
					if (ly > 0 && q > lim - 0.25 && t.hash(u, y + 3, v, 3) == 0) {
						continue; // the mound's rim comes and goes
					}
					int pct = topLayer ? 42 : 24;
					boolean tip = t.hash(u >> 1, y, v >> 1, 100) < pct || (q > lim - 0.3 && t.hash(u, y + 7, v, 4) == 0);
					boolean sunny = topLayer && q < lim - 0.35 && t.hash(u, y + 21, v, 6) == 0;
					t.leafOf(u, y, v, sunny ? LIGHT : tip ? TIP : NEEDLE, id);
					if (belly && ly == 0 && q < 0.55 * lim) {
						t.leafOf(u, y - 1, v, NEEDLE, id); // a sagging belly under the pad's middle
					}
					if (hangOk && ly == 0 && q > 0.55 * lim && y - 1 >= 4 && t.hash(u, y + 5, v, 4) == 0) {
						t.leafOf(u, y - 1, v, NEEDLE, id); // needles hanging from the pad
						if (y - 2 >= 4 && t.hash(u, y + 11, v, 4) == 0) {
							t.leafOf(u, y - 2, v, TIP, id);
						}
					}
					if (hangOk && topLayer && q < 0.6 * lim && y + 1 < H && t.hash(u, y + 13, v, 4) == 0) {
						t.leafOf(u, y + 1, v, NEEDLE, id); // a tuft on top: lumpy, not a plate
					}
				}
			}
		}
	}
}
