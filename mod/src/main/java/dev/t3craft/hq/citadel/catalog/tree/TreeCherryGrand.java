package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.CherryKit.Vox;
import java.util.List;
import java.util.Random;

/**
 * The great cherry: a 2×2 trunk that thins at the fork, roots spilling over the grass, a leader and four
 * diagonal limbs at staggered heights, each ending in a turned elliptical cloud of leaves, a crown on
 * top and a carpet of fallen petals. The canopy is tiered like a pagoda (branches show between the
 * clouds), never a ball.
 *
 * <p>Size: 7–9 × 7–9 × 11+ (the crown reaches the top of the box).
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

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x5A4C);
		double cu = (p.w() - 1) / 2.0;
		double cv = (p.d() - 1) / 2.0;
		int bu = (int) Math.floor(cu);
		int bv = (int) Math.floor(cv);
		int top = p.h() - 1;
		int cy = top - 1; // crown middle layer
		int yFork = Math.max(4, Math.min(5, Math.round(p.h() * 0.36f)));

		// --- trunk: 2×2, thinning at the fork, leaning a little so it is not a pole
		double lean = r.nextDouble() * Math.PI * 2;
		int[][] path = CherryKit.path(p.h(), bu, bv, Math.cos(lean) * 1.5, Math.sin(lean) * 1.5, 1, cy - 2);
		int[][] lead = CherryKit.trunk(t, path, yFork, cy - 1, r.nextInt(2), r.nextInt(2));
		CherryKit.roots(t, bu, bv, 6, 3, -1);

		// --- four limbs in two tiers (low pair, high pair), turning around the leader
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 4; i++) {
			boolean high = i % 2 == 1;
			double ang = a0 + i * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.7;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int y0 = Math.min(high ? yFork + 2 : yFork - 1, cy - 4);
			int su = lead[0][y0];
			int sv = lead[1][y0];
			double room = CherryKit.room(t, su, sv, ex, ez);
			double ra = Math.min(high ? 2.9 : 3.2, Math.max(1.8, room - 0.5));
			double s = Math.max(1.8, Math.min(3.8, room - ra + 0.4));
			int ty = Math.min(y0 + (high ? 2 : 1 + (i & 2) / 2), cy - 2);
			int tu = (int) Math.round(su + ex * s);
			int tv = (int) Math.round(sv + ez * s);
			List<int[]> cells = CherryKit.line(su, y0, sv, tu, ty, tv);
			CherryKit.branch(t, cells, high ? 1 : 2, ex, ez);

			// a twig from the middle of the limb, to the side, with its own small cloud
			int[] mid = cells.get(cells.size() / 2);
			double fa = ang + (i % 3 == 0 ? 0.95 : -0.95);
			double fx = Math.cos(fa);
			double fz = Math.sin(fa);
			double froom = CherryKit.room(t, mid[0], mid[2], fx, fz);
			double fl = Math.min(2.0, froom - 1.3);
			if (fl >= 1.0) {
				int fu = (int) Math.round(mid[0] + fx * fl);
				int fv = (int) Math.round(mid[2] + fz * fl);
				int fy = mid[1] + 1;
				CherryKit.branch(t, CherryKit.line(mid[0], mid[1], mid[2], fu, fy, fv), 0, fx, fz);
				CherryKit.cloud(t, fu + fx * 0.3, fy + 1, fv + fz * 0.3, 2.2, 1.7, fx, fz, 0, 1, 20 + i, 2);
			}

			CherryKit.cloud(t, tu + ex * 0.4, ty + 1, tv + ez * 0.4, ra, ra * 0.72, ex, ez, 1, high ? 1 : 0, i, 2);
		}

		// --- crown on top of the leader, turned against the lowest limb
		int lu = lead[0][cy - 1];
		int lv = lead[1][cy - 1];
		double margin = Math.min(Math.min(lu, p.w() - 1 - lu), Math.min(lv, p.d() - 1 - lv)) + 0.5;
		double cra = Math.min(2.8, margin);
		double ca = a0 + 0.8;
		CherryKit.cloud(t, lu, cy, lv, cra, cra * 0.9, Math.cos(ca), Math.sin(ca), 0, 1, 9, 2);
		CherryKit.ground(t, cu, cv, Math.min(p.w(), p.d()) / 2.0 + 0.9);
		t.flush(c);
	}
}
