package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.CherryKit.Vox;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The weeping cherry: a 2×2 trunk that thins into a leader, four or five limbs that arch up and then
 * fall like a fountain, a leafy ribbon along the outer half of every arch and curtains of leaves (an
 * azalea tip on each strand) hanging 2–5 blocks from it, so the silhouette is a sheaf of falling
 * pink with the branches seen through the gaps. A thick drift of petals covers the ground.
 *
 * <p>Size: 6–9 × 6–9 × 9+.
 */
public final class TreeCherryWeeping implements Variant {
	@Override
	public String id() {
		return "tree_cherry_weeping";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 6;
	}

	@Override
	public int minD() {
		return 6;
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
		Random r = p.rng(0x3E2D);
		double cu = (p.w() - 1) / 2.0;
		double cv = (p.d() - 1) / 2.0;
		int bu = (int) Math.floor(cu);
		int bv = (int) Math.floor(cv);
		int top = p.h() - 1;
		int cy = top - 1;
		int yFork = Math.max(3, Math.min(5, Math.round(p.h() * 0.36f)));
		int minY = 3; // strands stop here, so the petals show underneath

		double lean = r.nextDouble() * Math.PI * 2;
		int[][] path = CherryKit.path(p.h(), bu, bv, Math.cos(lean) * 1.2, Math.sin(lean) * 1.2, 1, cy - 1);
		int[][] lead = CherryKit.trunk(t, path, yFork, cy - 1, r.nextInt(2), r.nextInt(2));
		CherryKit.roots(t, bu, bv, 6, 3, -1);

		// --- arching limbs: up, over, down
		int n = p.w() >= 8 && p.d() >= 8 ? 5 : 4;
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double ang = a0 + i * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.5;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int y0 = Math.min(yFork + 1 + (i % 3), cy - 2);
			int su = lead[0][y0];
			int sv = lead[1][y0];
			double room = CherryKit.room(t, su, sv, ex, ez);
			double s = Math.max(2.6, Math.min(4.8, room - 0.3));
			int rise = Math.max(1, Math.min(2 + (i % 2), top - 1 - y0));
			int drop = rise + 2 + (i % 2); // the tip ends well below the start: the arch falls

			// sample the arch and join the samples with connected lines
			List<int[]> arch = new ArrayList<>();
			int[] prev = {su, y0, sv};
			for (int k = 1; k <= 5; k++) {
				double f = k / 5.0;
				int u = (int) Math.round(su + ex * s * f);
				int v = (int) Math.round(sv + ez * s * f);
				int y = Math.max(minY + 2, y0 + (int) Math.round(rise * 4 * f * (1 - f)) - (int) Math.round(drop * f * f));
				List<int[]> seg = CherryKit.line(prev[0], prev[1], prev[2], u, y, v);
				CherryKit.branch(t, seg, k == 1 ? 2 : 0, ex, ez);
				arch.addAll(seg);
				prev = new int[] {u, y, v};
			}

			// leaves wrap the outer half of the arch; single-block strands of different lengths hang from it
			int from = arch.size() * 2 / 5;
			double px = -ez;
			double pz = ex;
			for (int k = from; k < arch.size(); k++) {
				int[] a = arch.get(k);
				double f = (k - from) / (double) Math.max(1, arch.size() - from);
				if (k % 2 == 0) {
					t.add(a[0], a[1] + 1, a[2], CherryKit.LEAF);
				}
				for (int side = -1; side <= 1; side++) {
					int u = a[0] + (side == 0 ? 0 : (Math.abs(px) >= Math.abs(pz) ? side : 0));
					int v = a[2] + (side == 0 ? 0 : (Math.abs(px) >= Math.abs(pz) ? 0 : side));
					if (side != 0 && k % 4 == 1) {
						t.add(u, a[1], v, t.hash(u, a[1], v, 3) == 0 ? CherryKit.LEAF2 : CherryKit.LEAF);
						t.add(u, a[1] + 1, v, CherryKit.LEAF);
					}
					int chance = side == 0 ? 22 : 8;
					if (t.hash(u, a[1] + 7, v, 100) < chance + (int) (f * 15)) {
						int len = 2 + t.hash(u + 5, a[1], v, 4) + (int) Math.round(f * 2);
						CherryKit.strand(t, u, a[1] - (side == 0 ? 1 : 0), v, len, minY);
					}
				}
			}
		}

		// --- crown: a low mushroom of leaves on the leader with a skirt of short strands
		int lu = lead[0][cy - 1];
		int lv = lead[1][cy - 1];
		double margin = Math.min(Math.min(lu, p.w() - 1 - lu), Math.min(lv, p.d() - 1 - lv)) + 0.5;
		double cra = Math.min(2.8, margin);
		CherryKit.cloud(t, lu, cy, lv, cra, cra * 0.9, 1, 0, 0, 1, 5, minY);
		for (int a = 0; a < 14; a++) { // a short fringe round the dome
			double an = a * Math.PI * 2 / 14;
			int u = (int) Math.round(lu + Math.cos(an) * (cra - 0.3));
			int v = (int) Math.round(lv + Math.sin(an) * (cra * 0.9 - 0.3));
			if (t.get(u, cy, v) != null && t.hash(u, cy, v, 3) != 0) {
				CherryKit.strand(t, u, cy - 1, v, 2 + t.hash(u, 3, v, 3), minY);
			}
		}
		CherryKit.ground(t, cu, cv, Math.min(p.w(), p.d()) / 2.0 + 1.0);
		t.flush(c);
	}
}
