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

		int H = Math.min(p.h(), CLEAR + 1 + (int) Math.round(mn * 0.75) + r.nextInt(2)); // a wide dome, another height per seed
		int y2 = H >= 16 ? 4 : H >= 13 ? 3 : 2; // 2×2 up to y2 - 1, three cells at y2, then the leader
		int crownY = H - 3;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		double lean = r.nextDouble() * Math.PI * 2;
		double amp = 0.8 + r.nextDouble() * 1.4;
		int[][] lead = TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, y2, crownY, ou, ov,
				Math.cos(lean) * amp, Math.sin(lean) * amp);

		// ---- roots spread out, a flare one block high against the trunk, a collar on the big ones
		roots(t, bu, bv, 4 + (big ? 1 : 0), 2 + (big ? 1 : 0)); // few and short: a flare, not a starfish
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
		double spread = r.nextBoolean() ? 0.5 : 0.9; // umbrella of low lobes, or a layered crown
		int sy0 = Math.max(CLEAR, y2 + 1);
		for (int i = 0; i < n; i++) {
			if (i > 0 && r.nextInt(6) == 0) {
				continue; // a gap in the ring: the silhouette is not even
			}
			double ang = (a0 + (i + (r.nextDouble() - 0.5) * 0.4) / n) * Math.PI * 2;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			double frac = (g0 + i * 0.618) % 1.0; // spreads the heights so neighbours differ
			int ly = TreeKit.clamp(5 + (int) Math.round(Math.pow(frac, 1.5) * (crownY - 5) * spread), 5, crownY);
			int sy = TreeKit.clamp(ly - 3 - r.nextInt(2), sy0, crownY - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double rx = (1.95 + r.nextDouble() * 0.6) * ls;
			double rz = rx * (0.8 + r.nextDouble() * 0.3);
			double room = room(su, sv, ex, ez, wu0, wu1, wv0, wv1);
			double reach = TreeKit.clamp(room - rx * 0.95 + 0.3 - r.nextDouble() * 0.7, 1.5, 5.0 * ls); // out towards the rim: bare limb in between
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
			// a twig off the limb with a small satellite lobe
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
						lobe(t, fu, sat, fv, sx, sz, sat + 1 <= H - 1 ? 1 : 0, 1, TreeKit.SAKURA, 0.45, 40 + i, 1 + (i * 2) / n, null); // the parent's group: it always grows
					}
				}
			}
			int avail = H - 1 - ly;
			int nUp = avail >= 2 && r.nextInt(3) == 0 ? 2 : avail >= 1 ? 1 : 0;
			int nDown = ly >= CLEAR + 2 && Math.min(rx, rz) >= 2.0 ? 2 : 1;
			lobe(t, tu, ly, tv, rx, rz, nUp, nDown, TreeKit.SAKURA, 0.5, i, 1 + (i * 2) / n, lead); // two halves merge: two sky seams
			drips(t, tu, ly - nDown, tv, rx, rz, i);
		}

		// ---- one seed in three: a low limb that sweeps far out to a lobe of its own
		if (r.nextInt(3) == 0) {
			double sa = r.nextDouble() * Math.PI * 2;
			double sx = Math.cos(sa);
			double sz = Math.sin(sa);
			int su = lead[0][sy0];
			int sv = lead[1][sy0];
			double sroom = room(su, sv, sx, sz, wu0, wu1, wv0, wv1);
			if (sroom >= 3.5) {
				double sr = Math.min(1.8 * ls, sroom - 1.5);
				double reach = sroom - sr * 0.9;
				int tu = (int) Math.round(su + sx * reach);
				int tv = (int) Math.round(sv + sz * reach);
				List<int[]> sweep = TreeKit.branch(t, LIMB, new int[] {su, sy0, sv}, new int[] {tu, sy0, tv}, false);
				darkBase(t, sweep);
				lobe(t, tu, sy0 + 1, tv, Math.max(1.3, fit(tu, wu0, wu1, sr)), Math.max(1.3, fit(tv, wv0, wv1, sr)), 1, 1,
						TreeKit.SAKURA, 0.5, 60, 3, null);
			}
		}

		// ---- the crown on the leader
		int cu = lead[0][crownY];
		int cv = lead[1][crownY];
		double cr = TreeKit.clamp(Math.min(Math.min(cu - wu0, wu1 - cu), Math.min(cv - wv0, wv1 - cv)) + 1.0, 2.0, 2.4 * ls);
		lobe(t, cu, crownY + 1, cv, cr, cr * (0.85 + r.nextDouble() * 0.2), 1, 1, TreeKit.SAKURA, 0.5, 9, 1, null);

		// ---- fallen petals: thick under the whole crown, thinning out towards the corners, a little moss between
		TreeKit.ground(t, bu + 0.5, bv + 0.5, mn * 0.75, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (hsh < (int) (88 - 40 * k)) {
				return TreeKit.petals(1 + t.hash(u, 1, v, k < 0.5 ? 4 : 3), t.hash(u, 2, v, 4));
			}
			return hsh < (int) (94 - 40 * k) ? Blocks.MOSS_CARPET.defaultBlockState() : null;
		});
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
		double ryUp = nUp + 0.75;
		double ryDown = nDown + 0.75;
		for (int ly = -nDown; ly <= nUp; ly++) {
			int y = iy + ly;
			if (y < CLEAR) {
				continue;
			}
			double dyn = ly / (ly >= 0 ? ryUp : ryDown);
			double rem = 1.0 - dyn * dyn; // an ellipsoid: each layer is a circle smaller than the middle one
			if (rem <= 0.05) {
				continue;
			}
			double dyTone = ly >= 0 ? ly / (double) Math.max(1, nUp) : ly / (double) Math.max(1, nDown);
			for (int v = (int) Math.floor(cv - rz - 1); v <= (int) Math.ceil(cv + rz + 1); v++) {
				for (int u = (int) Math.floor(cu - rx - 1); u <= (int) Math.ceil(cu + rx + 1); u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double q = du * du + dv * dv;
					double jit = (t.hash(u + salt * 13, y + salt * 7, v + salt * 3, 9) - 4.5) / 4.5 * rough * 0.5;
					if (q > rem * (1.0 + jit)) {
						continue;
					}
					if (lead != null && y <= iy && Math.abs(u - lead[0][y]) <= 1 && Math.abs(v - lead[1][y]) <= 1) {
						continue;
					}
					if (q > 0.55 * rem && t.hash(u + salt * 5, y + 11, v, 9) < 2) {
						continue; // the shell is eroded by noise (about a fifth of it), not by stamped steps
					}
					t.leafOf(u, y, v, tone(t, lv, u, y, v, dyTone, salt), 1000 + id);
				}
			}
		}
	}

	/**
	 * Roots over the grass, tapering: a bark cell against the trunk, then a log along the root, a moss carpet on the
	 * tip of the longer ones (never the muddy brown boxes). Face roots run straight out, corner roots diagonally with a cell
	 * tying each step. One block high.
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
			made++;
			int du = TreeKit.OCT[k][0];
			int dv = TreeKit.OCT[k][1];
			boolean diag = du != 0 && dv != 0;
			int u = du < 0 ? bu - 1 : du > 0 ? bu + 2 : bu + t.hash(k, 1, 3, 2);
			int v = dv < 0 ? bv - 1 : dv > 0 ? bv + 2 : bv + t.hash(k, 2, 3, 2);
			int len = 1 + t.hash(k, 3, 3, maxLen); // 1..maxLen long
			if (diag) {
				t.woodSoft(u - du, 0, v, TreeKit.CHERRY.bark()); // the corner touches the trunk only by an edge
			}
			for (int i = 0; i < len; i++) {
				boolean tip = i == len - 1 && len > 1;
				BlockState s = TreeKit.CHERRY.bark(); // bark on all sides: a log end facing the camera shows pale rings
				t.wood(u, 0, v, s);
				if (tip) {
					t.cover(u, 1, v, Blocks.MOSS_CARPET.defaultBlockState()); // a moss cap on the root's end
				}
				if (diag && i < len - 1) {
					t.woodSoft(u + du, 0, v, TreeKit.CHERRY.bark());
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

	/** Cherry leaves, with the flowering azalea in pairs: a little on top, some on the flanks, a third below. */
	private static BlockState tone(Vox t, TreeKit.Leaves lv, int u, int y, int v, double dy, int salt) {
		int pct = dy < -0.2 ? 20 : dy < 0.35 ? 6 : 0;
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
