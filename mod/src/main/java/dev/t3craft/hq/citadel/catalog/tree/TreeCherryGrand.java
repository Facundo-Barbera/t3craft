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
 * The great cherry: a 2×2 trunk with roots that spread over the grass (and a heavier collar on big plots)
 * that thins to a single leader. Five to eight limbs leave the leader at staggered heights (never below
 * y = 4, so you walk under the whole crown) and climb out to rounded lobes of blossom of different sizes,
 * with a sky seam between neighbouring lobes. Short twigs carry smaller satellite lobes, a crown lobe
 * sits on the leader, drips soften the undersides (none below y = 4) and fallen petals thin out from the
 * trunk. Every lobe is fitted inside the plot (no flat cut sides) and hangs from its own limb (nothing
 * floats). The seed varies height, lean, lobe sizes, heights and angles.
 *
 * <p>Size: 7+ × 7+ × 11+. The tree itself spans at most 15 cells and stays centred on bigger plots.
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

	/** Stripped cherry wood (a pillar with no log ends) for the limbs: pale pink that reads against the dark trunk and the blossom. */
	private static final TreeKit.Wood LIMB = new TreeKit.Wood(Blocks.STRIPPED_CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD,
			TreeKit.CHERRY.rootTip());

	/** The lowest y a leaf or a limb may occupy: 4 blocks of clear walk under the crown. */
	private static final int CLEAR = 4;

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x6A11);
		int mn = Math.min(Math.min(p.w(), p.d()), 15); // the tree's own span; bigger plots stay centred
		double sc = mn / 7.0;
		double ls = Math.min(sc, 1.7); // lobe radius factor
		int wu0 = (p.w() - mn) / 2;
		int wu1 = wu0 + mn - 1;
		int wv0 = (p.d() - mn) / 2;
		int wv1 = wv0 + mn - 1;
		boolean big = mn >= 10;

		int H = Math.min(p.h(), 11 + mn / 2);
		H = Math.max(Math.min(p.h(), 11), H - r.nextInt(3)); // another height per seed
		int y2 = H >= 15 ? 4 : H >= 13 ? 3 : 2; // 2×2 up to y2 - 1, three cells at y2, then the leader
		int crownY = H - 3;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		double lean = r.nextDouble() * Math.PI * 2;
		double amp = 0.7 + r.nextDouble() * 0.9;
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, y2, crownY, ou, ov,
				Math.cos(lean) * amp, Math.sin(lean) * amp);

		// ---- roots spread out, a flare one block high against the trunk, a collar on the big ones
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 6 + (big ? 2 : 0), 2 + (big ? 1 : 0) + (mn >= 13 ? 1 : 0), -1, 3);
		if (big) {
			int lu = lead[0][y2 + 1];
			int lv = lead[1][y2 + 1];
			int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] o : n4) {
				if (t.hash(lu + o[0], y2 + 1, lv + o[1], 3) != 0) {
					t.woodSoft(lu + o[0], y2 + 1, lv + o[1], TreeKit.CHERRY.bark());
				}
			}
		}

		// ---- limbs and lobes
		int n = 5 + (mn >= 9 ? 1 : 0) + (mn >= 11 ? 1 : 0) + (mn >= 14 ? 1 : 0);
		double a0 = r.nextDouble();
		double g0 = r.nextDouble();
		int sy0 = Math.max(CLEAR, y2 + 1);
		for (int i = 0; i < n; i++) {
			double ang = (a0 + (i + (r.nextDouble() - 0.5) * 0.4) / n) * Math.PI * 2;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			double frac = (g0 + i * 0.618) % 1.0; // spreads the heights so neighbours differ
			int ly = TreeKit.clamp(5 + (int) Math.round(Math.pow(frac, 1.5) * (crownY - 5) * 0.9), 5, crownY);
			int sy = TreeKit.clamp(ly - 3 - r.nextInt(2), sy0, crownY - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double rx = (2.1 + r.nextDouble() * 0.8) * ls;
			double rz = rx * (0.8 + r.nextDouble() * 0.3);
			double room = room(su, sv, ex, ez, wu0, wu1, wv0, wv1);
			double reach = TreeKit.clamp(Math.min((1.0 + r.nextDouble() * 1.0) * sc, room - 1.2), 1.5, 4.5 * ls);
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			rx = Math.max(1.3, fit(tu, wu0, wu1, rx));
			rz = Math.max(1.3, fit(tv, wv0, wv1, rz));
			List<int[]> limb = TreeKit.branch(t, LIMB, new int[] {su, sy, sv}, new int[] {tu, ly - 1, tv}, false);
			darkBase(t, limb);
			if (sc >= 1.25) { // the base of a big limb is two logs thick (never below the walk height)
				for (int k = 0; k < Math.min(2, limb.size()); k++) {
					int[] cell = limb.get(k);
					if (cell[1] - 1 >= CLEAR) {
						t.woodSoft(cell[0], cell[1] - 1, cell[2], k == 0 ? TreeKit.CHERRY.bark() : LIMB.bark());
					}
				}
			}
			// a twig off the limb with a small satellite lobe (the tones swapped)
			if (i % 2 == 0 || r.nextInt(3) == 0) {
				int[] mid = limb.get(limb.size() / 2);
				double fa = ang + (i % 4 < 2 ? 1.0 : -1.0) * (1.0 + r.nextDouble() * 0.4);
				double fx = Math.cos(fa);
				double fz = Math.sin(fa);
				double fr = TreeKit.clamp(room(mid[0], mid[2], fx, fz, wu0, wu1, wv0, wv1) - 1.7, 0, 2.2 * ls);
				if (fr >= 1.0) {
					int fu = (int) Math.round(mid[0] + fx * fr);
					int fv = (int) Math.round(mid[2] + fz * fr);
					int sat = Math.max(CLEAR + 1, mid[1] + 1 + r.nextInt(2));
					if (sat <= crownY) {
						double sr = (1.5 + r.nextDouble() * 0.4) * ls;
						double sx = Math.max(1.2, fit(fu, wu0, wu1, sr));
						double sz = Math.max(1.2, fit(fv, wv0, wv1, sr));
						TreeKit.branch(t, LIMB, new int[] {mid[0], mid[1], mid[2]}, new int[] {fu, sat - 1, fv}, false);
						lobe(t, fu, sat, fv, sx, sz, sat + 1 <= H - 1 ? 1 : 0, 1, TreeKit.SAKURA.swap(), 0.45, 40 + i, 1 + (i * 2) / n, null); // the parent's group: it always grows
					}
				}
			}
			int avail = H - 1 - ly;
			int nUp = avail >= 2 ? 2 : avail >= 1 ? 1 : 0;
			int nDown = ly >= CLEAR + 2 && Math.min(rx, rz) >= 2.2 ? 2 : 1;
			lobe(t, tu, ly, tv, rx, rz, nUp, nDown, TreeKit.SAKURA, 0.5, i, 1 + (i * 2) / n, lead); // two halves merge: two sky seams
			drips(t, tu, ly - nDown, tv, rx, rz, i);
		}

		// ---- the crown on the leader
		int cu = lead[0][crownY];
		int cv = lead[1][crownY];
		double cr = TreeKit.clamp(Math.min(Math.min(cu - wu0, wu1 - cu), Math.min(cv - wv0, wv1 - cv)) + 1.0, 2.0, 2.7 * ls);
		lobe(t, cu, crownY + 1, cv, cr, cr * (0.85 + r.nextDouble() * 0.2), 1, 1, TreeKit.SAKURA, 0.5, 9, 1, null);

		// ---- flares one block high against the trunk, only where there is room to stand above them
		for (int v = bv - 1; v <= bv + 2; v++) {
			for (int u = bu - 1; u <= bu + 2; u++) {
				boolean outU = u < bu || u > bu + 1;
				boolean outV = v < bv || v > bv + 1;
				if (outU != outV && t.kind(u, 0, v) == TreeKit.WOOD && clear(t, u, v) && t.hash(u, 1, v, 3) < (big ? 2 : 1)) {
					t.woodSoft(u, 1, v, TreeKit.CHERRY.bark());
				}
			}
		}

		TreeKit.petalCarpet(t, bu + 0.5, bv + 0.5, mn / 2.0 + 1.6);
		trimBareTips(t);
		t.prune();
		t.flush(c);
	}


	/**
	 * A rounded lobe of leaves: {@code nUp} layers above the centre layer and {@code nDown} below, each
	 * narrower than the middle one (a dome, never a plate), edge jittered, outer rings of the end layers
	 * ragged. Lobes with the same {@code id} merge; different ids keep a one-cell seam of sky between them.
	 * Nothing below the walk height. With {@code lead} (the leader's column per y) the lower half of the lobe leaves
	 * a one-cell gap around the trunk, so the trunk and the limb that carries the lobe show beneath it.
	 */
	private static void lobe(Vox t, double cu, double cy, double cv, double rx, double rz, int nUp, int nDown,
			TreeKit.Leaves lv, double rough, int salt, int id, int[][] lead) {
		int iy = (int) Math.round(cy);
		for (int ly = -nDown; ly <= nUp; ly++) {
			int y = iy + ly;
			if (y < CLEAR) {
				continue;
			}
			double f = ly == 0 ? 1.0 : ly == 1 ? (rx >= 2.6 ? 0.82 : 0.72) : ly == 2 ? 0.45 : ly == -1 ? 0.74 : 0.4;
			double ru = Math.max(0.5, rx * f);
			double rv = Math.max(0.5, rz * f);
			double dy = ly >= 0 ? ly / (double) Math.max(1, nUp) : ly / (double) Math.max(1, nDown);
			for (int v = (int) Math.floor(cv - rv - 1); v <= (int) Math.ceil(cv + rv + 1); v++) {
				for (int u = (int) Math.floor(cu - ru - 1); u <= (int) Math.ceil(cu + ru + 1); u++) {
					double du = (u - cu) / ru;
					double dv = (v - cv) / rv;
					double q = du * du + dv * dv;
					double jit = (t.hash(u + salt * 13, y + salt * 7, v + salt * 3, 9) - 4.5) / 4.0 * rough * 0.6;
					if (q > 1.0 + jit) {
						continue;
					}
					if (lead != null && y <= iy && Math.abs(u - lead[0][y]) <= 1 && Math.abs(v - lead[1][y]) <= 1) {
						continue;
					}
					if (ly != 0 && q > 0.5 && t.hash(u + salt, y * 3, v, 6) < 2) {
						continue; // the rim of the top and bottom layers comes and goes
					}
					t.leafOf(u, y, v, tone(t, lv, u, y, v, dy, salt), 1000 + id);
				}
			}
		}
	}

	/** Nothing solid within the 3 cells above a flare (y = 2..4): you could stand on it and hit your head. */
	private static boolean clear(Vox t, int u, int v) {
		return t.kind(u, 2, v) == TreeKit.EMPTY && t.kind(u, 3, v) == TreeKit.EMPTY && t.kind(u, 4, v) == TreeKit.EMPTY;
	}

	/** The dark first two cells of a limb are the trunk's own bark, so the pale wood starts clear of the leader. */
	private static void darkBase(Vox t, List<int[]> limb) {
		for (int k = 0; k < Math.min(2, limb.size()); k++) {
			int[] cell = limb.get(k);
			t.wood(cell[0], cell[1], cell[2], TreeKit.CHERRY.bark());
		}
	}

	/** Cherry leaves, with the flowering azalea in pairs: a little on top, some on the flanks, a third below. */
	private static BlockState tone(Vox t, TreeKit.Leaves lv, int u, int y, int v, double dy, int salt) {
		int pct = dy < -0.2 ? 38 : dy < 0.35 ? 18 : 6;
		return t.hash(u + salt, y, Math.floorDiv(v, 2), 100) < pct ? lv.b() : lv.a();
	}

	/** Removes pale limb cells that end in the open: a tip with at most one wood neighbour and no leaf beside it. */
	private static void trimBareTips(Vox t) {
		int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int pass = 0; pass < 4; pass++) {
			boolean any = false;
			for (int y = CLEAR; y < t.h; y++) {
				for (int v = 0; v < t.d; v++) {
					for (int u = 0; u < t.w; u++) {
						if (t.kind(u, y, v) != TreeKit.WOOD || t.get(u, y, v).getBlock() != Blocks.STRIPPED_CHERRY_WOOD) {
							continue;
						}
						int wood = 0;
						int leaf = 0;
						for (int[] o : n6) {
							byte k = t.kind(u + o[0], y + o[1], v + o[2]);
							wood += k == TreeKit.WOOD ? 1 : 0;
							leaf += k == TreeKit.LEAF ? 1 : 0;
						}
						if (wood <= 1 && leaf == 0) {
							t.clear(u, y, v);
							any = true;
						}
					}
				}
			}
			if (!any) {
				break;
			}
		}
	}

	/** Drips under the rim of the lowest layer {@code yl} of a lobe (never below the walk height). */
	private static void drips(Vox t, int cu, int yl, int cv, double rx, double rz, int salt) {
		for (int v = (int) (cv - rz); v <= cv + rz; v++) {
			for (int u = (int) (cu - rx); u <= cu + rx; u++) {
				double q = Math.pow((u - cu) / rx, 2) + Math.pow((v - cv) / rz, 2);
				if (q < 0.3 || q > 1.0 || t.hash(u, yl + salt, v, 3) != 0 || t.kind(u, yl, v) != TreeKit.LEAF) {
					continue;
				}
				TreeKit.hang(t, u, yl - 1, v, 1 + t.hash(u, 3, v + salt, 3), CLEAR, TreeKit.SAKURA.a(), TreeKit.SAKURA.b(), 0, 0, false);
			}
		}
	}

	/** The largest radius a lobe centred on {@code c} may have to stay inside {@code [lo, hi]} (jitter included). */
	private static double fit(double c, int lo, int hi, double r) {
		return Math.min(r, (Math.min(c - lo, hi - c) + 1.0) / 1.05);
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
