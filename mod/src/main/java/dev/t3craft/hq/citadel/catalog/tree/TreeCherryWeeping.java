package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The weeping cherry: a 2×2 trunk with diagonal roots that thins to a leader, a small domed crown on top
 * and four or five limbs that climb out diagonally from it and end in small rounded lobes. From the rim of
 * every lobe and from the outer half of every limb hang strands of blossom of very different lengths, some
 * stepping outwards on the way down: a ragged sheaf of falling pink, never a straight curtain, with the
 * limbs seen through the gaps. A thick drift of petals covers the ground.
 *
 * <p>Size: 6–9 × 6–9 × 9+ (the tree stops at 13 high).
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
		Random r = p.rng(0x3E3E);
		int H = Math.min(p.h(), 13);
		double sc = Math.min(p.w(), p.d()) / 7.0;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		int yTop = H - 3;
		boolean small = Math.min(p.w(), p.d()) < 8;
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, 2, yTop, ou, ov,
				small ? 0 : r.nextInt(3) - 1, small ? 0 : r.nextInt(3) - 1);
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 5, 2, -1, 13);

		// the crown dome on the leader
		int lu = lead[0][yTop];
		int lv = lead[1][yTop];
		TreeKit.lobe(t, lu, yTop + 1, lv, 1.7 * sc, 1.6 * sc, 1.6, 1.0, TreeKit.SAKURA, 0.3, 0);

		// limbs: out and up, each to a small lobe
		int n = sc > 1.1 ? 5 : 4;
		double a0 = r.nextDouble() * Math.PI * 2;
		List<int[]> limbCells = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double ang = a0 + i * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.6;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int sy = TreeKit.clamp(yTop - 1 - (i % 3), 4, yTop);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double reach = TreeKit.clamp(TreeKit.room(t, su, sv, ex, ez, 0) - 0.6, 1.5, 3.4 * sc);
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			// an arch: up and out to an apex, then over and down to the tip, where a small lobe hangs
			int ay = Math.min(sy + 2 + (i & 1), H - 3);
			int au = (int) Math.round(su + ex * reach * 0.55);
			int av = (int) Math.round(sv + ez * reach * 0.55);
			int ty = Math.max(4, ay - 1 - (i % 3 == 0 ? 1 : 0));
			List<int[]> rise = TreeKit.branch(t, TreeKit.CHERRY, new int[] {su, sy, sv}, new int[] {au, ay, av}, false);
			List<int[]> fall = TreeKit.branch(t, TreeKit.CHERRY, new int[] {au, ay, av}, new int[] {tu, ty, tv}, false);
			limbCells.addAll(rise.subList(rise.size() / 2, rise.size()));
			limbCells.addAll(fall);
			TreeKit.lobe(t, tu, ty, tv, 1.35 * sc, 1.25 * sc, 1.3, 0.7, i % 2 == 0 ? TreeKit.SAKURA : TreeKit.SAKURA.swap(), 0.3, i + 1);
		}

		// strands: from every leaf with air below that lies away from the trunk, and from the outer limbs
		double ccu = bu + 0.5;
		double ccv = bv + 0.5;
		List<int[]> seeds = new ArrayList<>();
		for (int y = 3; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					byte k = t.kind(u, y, v);
					// only the outer rim hangs: the cell beyond (away from the trunk) is not a leaf
					int ou2 = (int) Math.signum(Math.round(u - ccu));
					int ov2 = (int) Math.signum(Math.round(v - ccv));
					boolean rim = t.kind(u + ou2, y, v + ov2) != TreeKit.LEAF;
					if ((k == TreeKit.LEAF && rim || k == TreeKit.WOOD && contains(limbCells, u, y, v))
							&& t.empty(u, y - 1, v) && Math.hypot(u - ccu, v - ccv) > 1.6) {
						seeds.add(new int[] {u, y, v});
					}
				}
			}
		}
		boolean[] col = new boolean[t.w * t.d]; // one strand per column, never two side by side (no walls)
		for (int[] s : seeds) {
			int u = s[0];
			int y = s[1];
			int v = s[2];
			if (busy(col, t.w, t.d, u, v)) {
				continue;
			}
			int pick = t.hash(u, y, v, 20);
			if (pick >= 14) {
				continue; // gaps: a bit less than half the rim hangs
			}
			// very uneven lengths: stubs, mid strands and a few long ones that nearly reach the grass
			int room = y - 2; // strands stop at least two blocks above the grass
			int len = pick < 4 ? 1 + t.hash(u, 1, v, 2) : 2 + t.hash(u, 2, v, 4); // 1–2 stubs, 2–5 strands
			len = Math.max(1, Math.min(len, room));
			int ku = (int) Math.signum(Math.round(u - ccu));
			int kv = (int) Math.signum(Math.round(v - ccv));
			boolean kink = len >= 3 && t.hash(u, 4, v, 3) == 0;
			if (kink && ku != 0 && kv != 0) {
				if (t.hash(u, 5, v, 2) == 0) {
					ku = 0;
				} else {
					kv = 0;
				}
			}
			TreeKit.hang(t, u, y - 1, v, len, 2, TreeKit.SAKURA.a(), TreeKit.SAKURA.b(), ku, kv, kink);
			col[v * t.w + u] = true;
		}

		// a thick drift of petals
		TreeKit.petalCarpet(t, ccu, ccv, Math.min(p.w(), p.d()) / 2.0 + 1.0);
		t.prune();
		t.flush(c);
	}

	private static boolean busy(boolean[] col, int w, int d, int u, int v) {
		for (int dv = -1; dv <= 1; dv++) {
			for (int du = -1; du <= 1; du++) {
				int a = u + du;
				int b = v + dv;
				if ((du == 0 || dv == 0) && a >= 0 && a < w && b >= 0 && b < d && col[b * w + a]) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean contains(List<int[]> cells, int u, int y, int v) {
		for (int[] c : cells) {
			if (c[0] == u && c[1] == y && c[2] == v) {
				return true;
			}
		}
		return false;
	}
}
