package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The weeping cherry (<i>shidarezakura</i>), a fountain: a tall straight trunk (5–7 blocks bare, so you walk up to it),
 * a rounded cap of blossom where it forks, and 4–6 dark limbs that leave the cap, <b>rise</b>, arch over and <b>fall</b>
 * almost to the grass. Each limb carries a thin fan of leaves that follows its arch (wide at the rim, narrow at the
 * crown), and from the outer half of the fan hang the curtains: single strands of leaves (never face to face), few and
 * short under the crown and long at the rim, some touching the grass and some stopping high, so the hem is ragged and
 * the gaps between fans stay open. The centre under the crown has no strand at all: the trunk is seen from every side
 * and you can walk in to it. The mass is densest on top and thins out down the arch. Fallen petals lie under it.
 * Everything hangs from wood or from a leaf joined to wood ({@code prune}). Seeds change the height, the lean, the
 * limb count, angle and reach, and every strand.
 *
 * <p>Size: 7–11 × 7–11 × 11+ (the tree stops at 13–14 high). Not as wide as {@code tree_cherry_grand}'s lens: taller,
 * narrower on top and open underneath.
 */
public final class TreeCherryWeeping implements Variant {
	/** Dark cherry wood on all sides (no pale log ends): the limbs read against the pink blossom and the sky. */
	private static final TreeKit.Wood LIMB = new TreeKit.Wood(Blocks.CHERRY_WOOD, Blocks.CHERRY_WOOD, TreeKit.CHERRY.rootTip());

	/** The lowest y a limb may take: 3 blocks of headroom under the rim wherever you can stand. */
	private static final int CLEAR = 4;

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
		return 11;
	}

	@Override
	public int maxD() {
		return 11;
	}

	/** Height of a limb along its run, in units of its rise: 0 at the fork, up to 1 near x = 1.8, back below 0 at the tip (x = 5). */
	private static double prof(double x) {
		double k = x <= 1.8 ? (x - 1.8) / 1.8 : (x - 1.8) / 2.5;
		return 1 - k * k;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x5D1B);
		int w = p.w();
		int d = p.d();
		int mn = Math.min(w, d);
		int hMax = p.h();

		// ---- proportions: trunk bare to the fork (yF), the arch rises rs more, the cap is centred near the apex
		int yF = mn >= 9 ? 6 : 5;
		double rs = 1.7 + (mn - 7) * 0.15 + r.nextDouble() * 0.5;
		while (yF + (int) Math.round(rs * 0.8) + 4 > hMax && (rs > 2.0 || yF > 5)) { // never over the plot
			if (rs > 2.0) {
				rs -= 0.4;
			} else {
				yF--;
			}
		}
		int yc = yF + (int) Math.round(rs * 0.8) + 1; // centre height of the cap: above the fork, so the limbs show under it

		// ---- trunk: a straight 1×1 leader (a lean makes ledges to stand on under the crown) with one small root each side
		int lu = (w - 1) / 2;
		int lv = (d - 1) / 2;
		t.wood(lu, 0, lv, TreeKit.CHERRY.bark());
		for (int y = 1; y <= yc; y++) {
			t.wood(lu, y, lv, TreeKit.CHERRY.log(Direction.Axis.Y));
		}
		int[][] foot = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int first = r.nextInt(4);
		for (int i = 0; i < 2; i++) {
			int[] q = foot[(first + i * (1 + r.nextInt(3))) % 4];
			if (t.empty(lu + q[0], 0, lv + q[1])) {
				t.wood(lu + q[0], 0, lv + q[1], TreeKit.CHERRY.bark());
			}
		}

		// ---- limbs: rise out of the cap, arch over and fall; reach and rise vary a little
		int n = mn >= 10 ? 6 : mn >= 9 ? 5 + r.nextInt(2) : mn >= 8 ? 5 : 4 + r.nextInt(2);
		if (mn == 7) {
			n = 4;
		}
		double a0 = r.nextDouble() * Math.PI * 2;
		double[] ang = new double[n];
		double[] rb = new double[n];
		double[] rsi = new double[n];
		int[] y0 = new int[n];
		double rAvg = 0;
		List<int[]> limbCells = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ang[i] = a0 + (i + (r.nextDouble() - 0.5) * 0.4) * Math.PI * 2 / n;
			double ex = Math.cos(ang[i]);
			double ez = Math.sin(ang[i]);
			double room = TreeKit.room(t, lu, lv, ex, ez, 0.1);
			rb[i] = TreeKit.clamp(Math.min(room, 5.2) * (i == 0 ? 1.0 : 0.84 + 0.16 * r.nextDouble()), 2.0, 5.2);
			rsi[i] = rs * (0.85 + 0.3 * r.nextDouble());
			y0[i] = yF + (i % 2); // the limbs leave the leader at two heights
			rAvg += rb[i] / n;
			List<int[]> pts = new ArrayList<>();
			for (int k = 0; k <= 4; k++) { // the wood stops at 0.8 of the reach: the fan and the strands carry the fall
				double x = rb[i] * k / 5.0;
				int pu = (int) Math.round(lu + ex * x);
				int pv = (int) Math.round(lv + ez * x);
				int py = Math.max(CLEAR, (int) Math.round(y0[i] + rsi[i] * prof(k)));
				pts.add(new int[] {pu, py, pv});
			}
			for (int k = 0; k < 4; k++) {
				limbCells.addAll(TreeKit.branch(t, LIMB, pts.get(k), pts.get(k + 1), false));
			}
		}

		// ---- the cap: a rounded mass of blossom round the fork, densest on top
		double rc = 2.1 + 0.2 * rAvg;
		int capLow = Integer.MAX_VALUE;
		for (int y = yc - 2; y <= yc + 3; y++) {
			double dyy = y - yc;
			double ry = dyy > 0 ? 2.1 : 1.4;
			for (int v = lv - 4; v <= lv + 4; v++) {
				for (int u = lu - 4; u <= lu + 4; u++) {
					double q = (Math.pow(u - lu, 2) + Math.pow(v - lv, 2)) / (rc * rc) + dyy * dyy / (ry * ry);
					double jit = (t.hash(u, y, v, 9) - 4) * 0.045;
					if (q <= 1.0 + jit) {
						t.leaf(u, y, v, tone(t, u, y, v));
						capLow = Math.min(capLow, y);
					}
				}
			}
		}

		// ---- fans: a thin sheet of leaves that follows each limb's arch, wide at the rim; densest in the crown
		int[][] sheet = new int[w][d];
		int[][] who = new int[w][d];
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				sheet[u][v] = -1;
				double ex = u - lu;
				double ez = v - lv;
				double rho = Math.hypot(ex, ez);
				if (rho < 1.0) {
					continue;
				}
				double th = Math.atan2(ez, ex);
				int best = -1;
				double bd = 1e9;
				for (int i = 0; i < n; i++) {
					double dt = Math.abs(Math.atan2(Math.sin(th - ang[i]), Math.cos(th - ang[i])));
					if (dt < bd) {
						bd = dt;
						best = i;
					}
				}
				double arc = rho * bd;
				double x = rho / rb[best];
				double half = 0.75 + 0.95 * Math.min(1.0, x);
				if (x > 1.18 || arc > half) {
					continue;
				}
				int keep = 96 - (int) (30 * Math.max(0, x - 0.6) / 0.4) - (arc > half - 0.35 ? 22 : 0);
				if (t.hash(u, 40, v, 100) >= keep) {
					continue;
				}
				int ysh = Math.max(CLEAR + 2, (int) Math.round(y0[best] + rsi[best] * prof(5 * Math.min(x, 1.0))) + 1);
				sheet[u][v] = ysh;
				who[u][v] = best;
			}
		}
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				int ysh = sheet[u][v];
				if (ysh < 0) {
					continue;
				}
				// join to the neighbour nearer the crown, so the sheet is face-connected on a slope
				int bu2 = -1;
				int bv2 = -1;
				double bestRho = Math.hypot(u - lu, v - lv);
				int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] o : nb) {
					int a = u + o[0];
					int b = v + o[1];
					if (a >= 0 && a < w && b >= 0 && b < d && sheet[a][b] >= 0 && Math.hypot(a - lu, b - lv) < bestRho) {
						bestRho = Math.hypot(a - lu, b - lv);
						bu2 = a;
						bv2 = b;
					}
				}
				int lo = ysh;
				int hi = ysh;
				if (bu2 >= 0) {
					lo = Math.min(ysh, sheet[bu2][bv2]);
					hi = Math.max(ysh, sheet[bu2][bv2]);
				}
				for (int y = lo; y <= hi; y++) {
					t.leaf(u, y, v, tone(t, u, y, v));
				}
			}
		}

		// ---- bury the limbs beyond the cap: leaves over and beside the wood (the dark arcs show under the cap, not as beams)
		int[][] side = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] q : limbCells) {
			if (Math.hypot(q[0] - lu, q[2] - lv) < rc * 0.85) {
				continue;
			}
			for (int[] o : side) {
				t.leaf(q[0] + o[0], q[1] + o[1], q[2] + o[2], tone(t, q[0] + o[0], q[1] + o[1], q[2] + o[2]));
			}
		}

		// ---- curtains: single strands (a checker, never face to face) from the sheet, none under the crown
		int par = r.nextInt(3);
		boolean[] strand = new boolean[w * d];
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (sheet[u][v] < 0) {
					continue;
				}
				double rho = Math.hypot(u - lu, v - lv);
				double f = TreeKit.clamp((rho - 0.42 * rAvg) / (0.58 * rAvg), -1.0, 1.0);
				if (f < 0) {
					continue; // the centre stays open
				}
				int y0c = -1;
				for (int y = CLEAR - 1; y < p.h(); y++) {
					byte k = t.kind(u, y, v);
					if (k == TreeKit.WOOD || k == TreeKit.LEAF) {
						y0c = y;
						break;
					}
				}
				if (Math.floorMod(u + 2 * v, 3) != par) {
					// between the strands the hem only has short drips, so its edge is ragged (3+ blocks stay free under it)
					if (f > 0.45 && y0c > CLEAR + 1 && t.empty(u, y0c - 1, v) && t.hash(u, 9, v, 100) < 38) {
						t.leaf(u, y0c - 1, v, TreeKit.SAKURA.a());
						if (y0c - 2 >= CLEAR && t.hash(u, 10, v, 100) < 30) {
							t.leaf(u, y0c - 2, v, TreeKit.SAKURA.b());
						}
					}
					continue; // a lattice of one cell in three carries the long strands: fine hair, never two side by side
				}
				if (y0c < 0 || t.kind(u, y0c - 1, v) != TreeKit.EMPTY) {
					continue;
				}
				int cls = t.hash(u, 5, v, 100);
				int longOnes = 4 + (int) (30 * f * f); // few reach the grass: most of the hem is a ragged line of different lengths
				int bottom;
				if (cls < 12) {
					continue; // no strand here: a window
				} else if (cls < 12 + longOnes) {
					bottom = 0; // a long one: to the grass (never a tip at y = 1: that is a step to stand on)
				} else {
					if (y0c - 1 < CLEAR) {
						continue;
					}
					bottom = Math.max(CLEAR, y0c - 2 - t.hash(u, 6, v, 5)); // a stub: 3+ blocks of headroom under it
				}
				bottom = Math.min(bottom, y0c - 1);
				// the strand drifts one cell outwards after 3 or 4 blocks, so it falls in an arc and not as a post
				int ku = Math.abs(u - lu) >= Math.abs(v - lv) ? Integer.signum(u - lu) : 0;
				int kv = ku == 0 ? Integer.signum(v - lv) : 0;
				int kAt = 1 + t.hash(u, 8, v, 2); // high up: the cell left behind must stay at y >= 4 (3+ blocks under it)
				int su = u;
				int sv = v;
				for (int y = y0c - 1, k = 0; y >= bottom; y--, k++) {
					if (!t.empty(su, y, sv)) {
						break;
					}
					t.leaf(su, y, sv, y == bottom && t.hash(su, y, sv, 100) < 40 ? TreeKit.SAKURA.b() : TreeKit.SAKURA.a());
					if (k == kAt && y >= CLEAR && y > bottom + 1 && t.empty(su + ku, y, sv + kv) && t.empty(su + ku, y - 1, sv + kv)
							&& (ku != 0 || kv != 0)) {
						su += ku; // the step is a leaf beside the last one, face-connected
						sv += kv;
						t.leaf(su, y, sv, TreeKit.SAKURA.a());
						t.leaf(su, y + 1, sv, TreeKit.SAKURA.a()); // and two above it, so the step is no ledge to stand on
						t.leaf(su, y + 2, sv, TreeKit.SAKURA.a());
					}
				}
				strand[v * w + u] = true;
			}
		}
		// a few short tufts under the cap's rim, high up
		for (int v = lv - 4; v <= lv + 4; v++) {
			for (int u = lu - 4; u <= lu + 4; u++) {
				for (int y = capLow; y < capLow + 3; y++) {
					if (t.kind(u, y, v) == TreeKit.LEAF && t.empty(u, y - 1, v) && y - 1 >= CLEAR + 1
							&& t.hash(u, y, v, 100) < 20) {
						t.leaf(u, y - 1, v, TreeKit.SAKURA.a());
						break;
					}
				}
			}
		}

		// ---- fallen petals: thick under the crown and the curtains, thinning out, moss between
		TreeKit.ground(t, (w - 1) / 2.0, (d - 1) / 2.0, rAvg + 2.2, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			int pet = (int) (86 - 40 * k) + (strand[v * w + u] ? 10 : 0);
			if (hsh < pet) {
				return TreeKit.petals(1 + t.hash(u, 1, v, k < 0.55 ? 4 : 2), t.hash(u, 2, v, 4));
			}
			return hsh < pet + 8 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
		});
		t.prune();
		t.flush(c);
	}

	/** Cherry pink with a few azalea leaves (about one in thirteen). */
	private static BlockState tone(Vox t, int u, int y, int v) {
		return t.hash(u, y + 17, v, 100) < 7 ? TreeKit.SAKURA.b() : TreeKit.SAKURA.a();
	}
}
