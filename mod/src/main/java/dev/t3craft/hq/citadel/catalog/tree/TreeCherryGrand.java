package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The great cherry, as a dome: a 2×2 root flare that thins at once to a single leader, and one broad, flattened
 * ellipsoid crown with 3–5 lobes on its rim (a lobed rim, not stacked lumps; nothing sits on top). The shell is
 * eroded and a few sky windows go right through it. The crown never drops below y = 5, so you walk under the whole
 * thing (the trunk's shoulder keeps 3 blocks of headroom). Two or three limbs leave the trunk under the dome and come
 * out of its side by at least two cells, ending in a small blossom tuft. Roots are few and short (one or two longer).
 * At most 8 % of the leaves are flowering azalea (fixed in code); the dominant tone is cherry pink.
 *
 * <p>Size: 7+ × 7+ × 11+. The tree itself spans at most 15 cells and stays centred on bigger plots.
 */
public final class TreeCherryGrand implements Variant {
	/** The most azalea leaves, as a share of all leaves (fixed here, not left to hash luck). */
	private static final double AZALEA_MAX = 0.08;

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

	/** Dark cherry wood on all sides (no pale log ends) for the limbs too: they read against the pink blossom and the sky in the iso view. */
	private static final TreeKit.Wood LIMB = new TreeKit.Wood(Blocks.CHERRY_WOOD, Blocks.CHERRY_WOOD, TreeKit.CHERRY.rootTip());

	/**
	 * The lowest y a leaf or a limb may occupy. The trunk's shoulder is only the root level (three cells at y = 0, feet at
	 * y = 1), so with 4 there are 3 blocks of headroom over every walkable cell near the trunk.
	 */
	private static final int CLEAR = 4;

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x6A11);
		int mn = Math.min(Math.min(p.w(), p.d()), 15); // the tree's own span; bigger plots stay centred
		int wu0 = (p.w() - mn) / 2;
		int wu1 = wu0 + mn - 1;
		int wv0 = (p.d() - mn) / 2;
		int wv1 = wv0 + mn - 1;
		boolean big = mn >= 10;

		int cy = CLEAR + (p.h() >= 13 ? 2 : 1); // the dome's centre layer: taller plots get a thicker crown on the same clear trunk
		int nUp = Math.min(p.h() - 1 - cy, mn >= 12 ? 3 : 2); // a wide, flat dome: a few layers on a big spread
		double ryUp = nUp + 0.6 + r.nextDouble() * 0.3;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		double lean = r.nextDouble() * Math.PI * 2;
		double amp = 0.4 * r.nextDouble(); // under half a cell: the leader stays straight, no lean step to hang over a root
		double lu = Math.cos(lean) * amp;
		double lv = Math.sin(lean) * amp;
		// the base shifts against the lean, so the crown's centre stays in the middle of the plot
		int bu = TreeKit.clamp(TreeKit.base(p.w(), ou) - (int) Math.round(lu), 0, p.w() - 2);
		int bv = TreeKit.clamp(TreeKit.base(p.d(), ov) - (int) Math.round(lv), 0, p.d() - 2);
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, 0, Math.min(cy + 1, p.h() - 1), ou, ov, lu, lv);

		// ---- roots: 4–5, short; one or two reach further
		roots(t, bu, bv, 4 + r.nextInt(2), 3);

		// ---- the dome
		double cu = lead[0][cy];
		double cv = lead[1][cy];
		int lobes = 3 + r.nextInt(3); // 3, 4 or 5 lobes on the rim
		double phi = r.nextDouble() * Math.PI * 2;
		double lobeAmp = mn < 9 ? 0.10 + r.nextDouble() * 0.04 : 0.17 + r.nextDouble() * 0.08;
		double rRoom = Math.min(Math.min(cu - wu0, wu1 - cu), Math.min(cv - wv0, wv1 - cv)) + 0.9;
		double rWant = Math.min(mn * 0.5 - 0.6, 7.2);
		double rBase = Math.min(rWant, rRoom / (1 + lobeAmp + 0.04));
		rBase = Math.max(rBase, Math.min(rWant, mn * 0.5 - 0.5));
		double ryDown = (cy - CLEAR) + 1.9; // the underside always bottoms out at y4
		// sky windows: a few vertical slits right through the shell
		int nHoles = rBase < 3.4 ? 0 : big ? 3 : 1 + r.nextInt(2); // small crowns keep their shell whole
		double[][] holes = new double[nHoles][2];
		for (int i = 0; i < nHoles; i++) {
			double a = phi + (i + 0.3 + r.nextDouble() * 0.4) / nHoles * Math.PI * 2 + 0.5;
			double d = rBase * (0.42 + r.nextDouble() * 0.3);
			holes[i][0] = cu + Math.cos(a) * d;
			holes[i][1] = cv + Math.sin(a) * d;
		}
		for (int y = CLEAR; y <= cy + nUp; y++) {
			int ly = y - cy;
			double dyn = ly / (ly >= 0 ? ryUp : ryDown);
			double rem = 1.0 - dyn * dyn;
			if (rem <= 0.04) {
				continue;
			}
			for (int v = (int) Math.floor(cv - rBase * 1.3 - 1); v <= (int) Math.ceil(cv + rBase * 1.3 + 1); v++) {
				for (int u = (int) Math.floor(cu - rBase * 1.3 - 1); u <= (int) Math.ceil(cu + rBase * 1.3 + 1); u++) {
					if (u < wu0 || u > wu1 || v < wv0 || v > wv1) {
						continue;
					}
					double du = u - cu;
					double dv = v - cv;
					double th = Math.atan2(dv, du);
					double rad = rBase * (1 + lobeAmp * Math.cos(lobes * (th - phi)));
					double q = (du * du + dv * dv) / (rad * rad);
					double jit = (t.hash(u, y, v, 9) - 4.5) / 4.5 * 0.16;
					if (q > rem * (1.0 + jit)) {
						continue;
					}
					if (q > 0.55 * rem && t.hash(u + 5, y + 11, v, 12) < 2) {
						continue; // an eroded shell, not a smooth skin
					}
					if (y == CLEAR && t.hash(u, y + 3, v + 7, 10) < 3) {
						continue; // a ragged underside
					}
					if (y <= cy && Math.abs(u - lead[0][y]) <= 1 && Math.abs(v - lead[1][y]) <= 1) {
						continue; // trunk and limbs show from underneath
					}
					boolean hole = false;
					for (double[] h : holes) {
						if (y >= cy && u == Math.round(h[0]) && v == Math.round(h[1])) { // 1×1 windows, never in the underside
							hole = true;
							break;
						}
					}
					if (hole) {
						continue;
					}
					t.leaf(u, y, v, TreeKit.SAKURA.a());
				}
			}
		}

		// ---- ribs: bark limbs under the dome that carry its lobes (they also keep every leaf attached)
		int ribs = 3 + (mn >= 9 ? 1 : 0);
		double rib0 = phi + Math.PI / lobes;
		for (int i = 0; i < ribs; i++) {
			double ang = rib0 + i * Math.PI * 2 / ribs + (r.nextDouble() - 0.5) * 0.5;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			double reach = Math.min(rBase * 0.75, room(lead[0][CLEAR], lead[1][CLEAR], ex, ez, wu0, wu1, wv0, wv1));
			int tu = (int) Math.round(lead[0][CLEAR] + ex * reach);
			int tv = (int) Math.round(lead[1][CLEAR] + ez * reach);
			List<int[]> rib = TreeKit.branch(t, LIMB, new int[] {lead[0][CLEAR], CLEAR, lead[1][CLEAR]}, new int[] {tu, cy, tv}, false);
			darkBase(t, rib);
		}

		// ---- side limbs: 2–3 come out of the flank of the dome by at least two cells, ending in a tuft of blossom
		int side = 2 + (r.nextInt(3) == 0 ? 1 : 0);
		int d0 = r.nextInt(4);
		for (int i = 0; i < side; i++) { // along the diagonals: that is where the plot has the most room beyond the crown
			double ang = Math.PI / 4 + ((d0 + i * (side == 2 ? 2 : 1)) % 4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.3;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int sy = CLEAR;
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double edge = 0;
			for (double s = 1; s < 12; s += 0.5) {
				int mu = (int) Math.round(su + ex * s);
				int mv = (int) Math.round(sv + ez * s);
				if (t.kind(mu, cy, mv) == TreeKit.LEAF || t.kind(mu, cy - 1, mv) == TreeKit.LEAF) {
					edge = s;
				}
			}
			double reach = Math.min(room(su, sv, ex, ez, wu0, wu1, wv0, wv1) - 0.8, edge + 2.4);
			if (reach < edge + 1.0 || reach < 3.0) {
				continue;
			}
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			int ty = cy; // the limb climbs out of the dome (and stays under its top)
			List<int[]> limb = TreeKit.branch(t, LIMB, new int[] {su, sy, sv}, new int[] {tu, ty, tv}, false);
			darkBase(t, limb);
			// a blossom tuft rests on the tip and hides the end of the wood from above
			for (int dy = 0; dy <= 1; dy++) {
				double tr = dy == 1 ? 1.1 : 1.5;
				for (int dv = -2; dv <= 2; dv++) {
					for (int du = -2; du <= 2; du++) {
						int u = tu + du;
						int v = tv + dv;
						if (u < wu0 || u > wu1 || v < wv0 || v > wv1 || du * du + dv * dv > tr * tr + 0.3) {
							continue;
						}
						if (dy == 0 && du == 0 && dv == 0) {
							continue; // the tip itself
						}
						t.leaf(u, ty + dy, v, TreeKit.SAKURA.a());
					}
				}
			}
		}

		// ---- fallen petals: thick under the whole crown, thinning out towards the corners, a little moss between
		TreeKit.ground(t, bu + 0.5, bv + 0.5, mn * 0.75, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (hsh < (int) (88 - 40 * k)) {
				return TreeKit.petals(1 + t.hash(u, 1, v, k < 0.5 ? 4 : 3), t.hash(u, 2, v, 4));
			}
			return hsh < (int) (94 - 40 * k) ? Blocks.MOSS_CARPET.defaultBlockState() : null;
		});
		t.prune();
		azalea(t);
		t.flush(c);
	}

	/**
	 * Swaps a few cherry leaves on the underside of the dome for flowering azalea, never more than {@link #AZALEA_MAX} of
	 * all leaves (counted here, so the cap holds for every seed and size).
	 */
	private static void azalea(Vox t) {
		int total = 0;
		for (int y = CLEAR; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF) {
						total++;
					}
				}
			}
		}
		int cap = (int) Math.floor(total * AZALEA_MAX);
		int made = 0;
		for (int y = CLEAR; y < t.h && made < cap; y++) {
			for (int v = 0; v < t.d && made < cap; v++) {
				for (int u = 0; u < t.w && made < cap; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF && t.kind(u, y - 1, v) != TreeKit.LEAF && t.hash(u, y, v, 100) < 14) {
						t.clear(u, y, v);
						t.leaf(u, y, v, TreeKit.SAKURA.b());
						made++;
					}
				}
			}
		}
	}

	/**
	 * Roots over the grass, tapering: a bark cell against the trunk, then a log along the root, a moss carpet on the
	 * tip of the longer ones. Face roots run straight out, corner roots diagonally with a cell tying each step. One block
	 * high. The first two are the long ones ({@code 3..maxLen}); the rest are one or two cells.
	 */
	private static void roots(Vox t, int bu, int bv, int count, int maxLen) {
		boolean[] used = new boolean[8];
		int made = 0;
		for (int pass = 0; pass < 32 && made < count; pass++) {
			int k = t.hash(pass, 7, 3, 8);
			if (used[k]) {
				continue;
			}
			used[k] = true;
			boolean longOne = made < 1 + t.hash(7, 7, 7, 2);
			made++;
			int du = TreeKit.OCT[k][0];
			int dv = TreeKit.OCT[k][1];
			boolean diag = du != 0 && dv != 0;
			int u = du < 0 ? bu - 1 : du > 0 ? bu + 2 : bu + t.hash(k, 1, 3, 2);
			int v = dv < 0 ? bv - 1 : dv > 0 ? bv + 2 : bv + t.hash(k, 2, 3, 2);
			int len = longOne ? 3 : 1; // short roots are a single cell: a flare, not a starfish
			if (diag) {
				t.woodSoft(u - du, 0, v, TreeKit.CHERRY.bark()); // the corner touches the trunk only by an edge
			}
			for (int i = 0; i < len; i++) {
				boolean tip = i == len - 1 && len > 1;
				t.wood(u, 0, v, TreeKit.CHERRY.bark()); // bark on all sides: a log end facing the camera shows pale rings
				if (tip) {
					t.cover(u, 1, v, Blocks.MOSS_CARPET.defaultBlockState()); // a moss cap on the root's end
				}
				u += du;
				v += dv;
			}
		}
	}

	/** The dark first two cells of a limb are the trunk's own bark, so the pale wood starts clear of the leader. */
	private static void darkBase(Vox t, List<int[]> limb) {
		for (int k = 0; k < Math.min(2, limb.size()); k++) {
			int[] cell = limb.get(k);
			t.wood(cell[0], cell[1], cell[2], TreeKit.CHERRY.bark());
		}
	}

	/** How far you can go from {@code (u, v)} along {@code (ex, ez)} before leaving the window. */
	private static double room(double u, double v, double ex, double ez, int u0, int u1, int v0, int v1) {
		double s = 1e9;
		if (ex > 1e-6) {
			s = Math.min(s, (u1 - u) / ex);
		} else if (ex < -1e-6) {
			s = Math.min(s, (u - u0) / -ex);
		}
		if (ez > 1e-6) {
			s = Math.min(s, (v1 - v) / ez);
		} else if (ez < -1e-6) {
			s = Math.min(s, (v - v0) / -ez);
		}
		return Math.max(0, s);
	}
}
