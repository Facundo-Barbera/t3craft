package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.Random;

/**
 * The great cherry: a 2×2 trunk with diagonal roots that thins to a single leader, and five limbs that
 * leave it at staggered heights and climb diagonally out to five rounded lobes of blossom of different
 * sizes, spiralling up around the trunk with sky between them, plus a crown lobe on the leader. Each lobe
 * mixes cherry leaves with clumps of flowering azalea (deeper underneath); short drips soften the
 * undersides; fallen petals thin out from the trunk.
 *
 * <p>Size: 7–9 × 7–9 × 11+ (the tree stops at 14 high).
 */
public final class TreeCherryGrand implements Variant {
	@Override
	public String id() {
		return "tree_cherry_grand";
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
		return 11;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	/** Lobes: {angle offset (turns), height fraction, size factor}, spiralling up; the last is the crown. */
	private static final double[][] LOBES = {
			{0.00, 0.44, 1.05},
			{0.42, 0.58, 1.10},
			{0.71, 0.70, 0.85},
			{0.18, 0.80, 0.90},
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x6A11);
		int H = Math.min(p.h(), 14);
		double ys = (H - 1) / 13.0;
		double sc = Math.min(p.w(), p.d()) / 7.0;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		int y2 = Math.max(2, (int) Math.round(3 * ys));
		int crownY = H - 3;
		double lean = r.nextDouble() * Math.PI * 2;
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, y2, crownY, ou, ov,
				Math.cos(lean) * 0.9, Math.sin(lean) * 0.9);
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 6, 2 + (sc > 1.1 ? 1 : 0), -1, 3);

		double a0 = r.nextDouble();
		for (int i = 0; i < LOBES.length; i++) {
			double ang = (a0 + LOBES[i][0] + (r.nextDouble() - 0.5) * 0.08) * Math.PI * 2;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int ly = (int) Math.round(LOBES[i][1] * (H - 1));
			double rx = (2.0 + r.nextDouble() * 0.4) * LOBES[i][2] * sc;
			double rz = rx * (0.8 + r.nextDouble() * 0.25);
			int sy = TreeKit.clamp(ly - 3, y2, crownY - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double room = TreeKit.room(t, su, sv, ex, ez, 0);
			double reach = TreeKit.clamp(room - Math.max(rx, rz) + 1.0, 1.0, 3.2 * sc);
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			TreeKit.branch(t, TreeKit.CHERRY, new int[] {su, sy, sv}, new int[] {tu, ly, tv}, i < 2);
			// a twig off the limb towards the side, with a small satellite lobe (two tones swapped)
			if (i < 3) {
				double fa = ang + (i % 2 == 0 ? 1.1 : -1.1);
				double fx = Math.cos(fa);
				double fz = Math.sin(fa);
				int mu = (su + tu) / 2;
				int mv = (sv + tv) / 2;
				int my = (sy + ly) / 2;
				double fr = TreeKit.clamp(TreeKit.room(t, mu, mv, fx, fz, 0) - 1.2, 0, 2.0);
				if (fr >= 1.0) {
					int fu = (int) Math.round(mu + fx * fr);
					int fv = (int) Math.round(mv + fz * fr);
					TreeKit.branch(t, TreeKit.CHERRY, new int[] {mu, my, mv}, new int[] {fu, my + 1, fv}, false);
					TreeKit.lobe(t, fu, my + 1, fv, 1.6 * sc, 1.5 * sc, 1.7, 1.0, TreeKit.SAKURA.swap(), 0.35, 40 + i);
				}
			}
			TreeKit.lobe(t, tu, ly, tv, rx, rz, 2.1, 1.2, TreeKit.SAKURA, 0.4, i);
			TreeKit.drips(t, tu, ly, tv, rx, rz, i);
		}
		// the crown on the leader
		int cu = lead[0][crownY];
		int cv = lead[1][crownY];
		double cr = TreeKit.clamp(Math.min(Math.min(cu, p.w() - 1 - cu), Math.min(cv, p.d() - 1 - cv)) + 1.0, 2.0, 2.6 * sc);
		TreeKit.lobe(t, cu, crownY + 1, cv, cr, cr * 0.9, 2.2, 1.3, TreeKit.SAKURA, 0.4, 9);

		TreeKit.petalCarpet(t, bu + 0.5, bv + 0.5, Math.min(p.w(), p.d()) / 2.0 + 0.8);
		t.prune();
		t.flush(c);
	}
}
