package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.Random;

/**
 * The leaning cherry: a bonsai-garden tree whose 2×2 trunk stands at one side of the plot, thins and bends
 * across it (the seed picks the side), with the roots heaped on the opposite side as if the tree pulled on
 * the ground. The leader ends in a broad, flat lobe that overhangs the plot; a limb thrown back from the
 * elbow carries a smaller counter-lobe high over the roots, and a short twig on the outside of the bend a
 * third, small lobe, so the crown is three rounded masses of different size at three heights. Petals drift
 * in the lee of the overhang.
 *
 * <p>Size: 5–7 × 5–7 × 8+ (the tree stops at 12 high).
 */
public final class TreeCherryLeaning implements Variant {
	@Override
	public String id() {
		return "tree_cherry_leaning";
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
		Random r = p.rng(0x1EA2);
		int H = Math.min(p.h(), 12);
		double sc = Math.min(p.w(), p.d()) / 6.0;
		// the lean: along a diagonal picked by the seed (never towards the front, so the bend shows in the iso view)
		int q = r.nextInt(4);
		int su = q == 0 || q == 3 ? 1 : -1;
		int sv = q < 2 ? 1 : -1;
		int bu = su > 0 ? 0 : p.w() - 2;
		int bv = sv > 0 ? Math.min(1, p.d() - 6 + 1) : p.d() - 2 - Math.min(1, p.d() - 6 + 1);
		bu = TreeKit.clamp(bu + (p.w() >= 6 ? su : 0), 0, p.w() - 2);
		bv = TreeKit.clamp(bv, 0, p.d() - 2);
		int top = H - 3;
		double leanU = su * Math.max(1, (p.w() - 3) * 0.6);
		double leanV = sv * Math.max(1, (p.d() - 3) * 0.5);
		int ou = su > 0 ? 1 : 0;
		int ov = sv > 0 ? 1 : 0;
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, 2, top, ou, ov, leanU, leanV);
		// roots on the side away from the lean: octant pointing to (-su, -sv)
		int bias = octant(-su, -sv);
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 5, 2, bias, 11);

		// the overhang: a broad flat lobe on the leader's tip
		int tu = lead[0][top];
		int tv = lead[1][top];
		double ra = 2.1 * sc;
		TreeKit.lobe(t, tu + su * 0.4, top, tv + sv * 0.4, ra, ra * 0.85, 1.8, 1.1, TreeKit.SAKURA, 0.4, 1);
		TreeKit.drips(t, tu, top, tv, ra, ra * 0.85, 1);

		// the counter-limb, thrown back from the elbow over the roots, with a smaller, higher lobe
		int ey = TreeKit.clamp(top - 3, 3, top - 1);
		int eu = lead[0][ey];
		int ev = lead[1][ey];
		int cu = TreeKit.clamp(eu - su * 2, 1, p.w() - 2);
		int cv = TreeKit.clamp(ev - sv * 1, 1, p.d() - 2);
		int cy = Math.min(top + 1, H - 2);
		TreeKit.branch(t, TreeKit.CHERRY, new int[] {eu, ey, ev}, new int[] {cu, cy, cv}, false);
		TreeKit.lobe(t, cu, cy, cv, 1.5 * sc, 1.4 * sc, 1.7, 1.0, TreeKit.SAKURA.swap(), 0.35, 2);

		// a short twig on the outside of the bend, low, with a small lobe
		int my = TreeKit.clamp(top - 4, 3, top - 2);
		int mu = lead[0][my];
		int mv = lead[1][my];
		int wu = TreeKit.clamp(mu + sv, 1, p.w() - 2); // across the lean
		int wv = TreeKit.clamp(mv - su, 1, p.d() - 2);
		TreeKit.branch(t, TreeKit.CHERRY, new int[] {mu, my, mv}, new int[] {wu, my + 1, wv}, false);
		TreeKit.lobe(t, wu, my + 1, wv, 1.2 * sc, 1.2 * sc, 1.5, 0.8, TreeKit.SAKURA, 0.3, 3);

		// petals in the lee of the overhang
		TreeKit.petalCarpet(t, tu, tv, Math.min(p.w(), p.d()) / 2.0 + 0.6);
		t.prune();
		t.flush(c);
	}

	private static int octant(int du, int dv) {
		for (int k = 0; k < 8; k++) {
			if (TreeKit.OCT[k][0] == Integer.signum(du) && TreeKit.OCT[k][1] == Integer.signum(dv)) {
				return k;
			}
		}
		return 0;
	}
}
