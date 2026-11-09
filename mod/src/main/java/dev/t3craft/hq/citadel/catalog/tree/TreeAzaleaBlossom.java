package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flowering azalea, a round but ragged crown on a short, twisted oak trunk with roots.
 *
 * <ul>
 * <li><b>Trunk:</b> oak, 1×1 (a 2×2 foot of bark on big plots) that steps sideways one block at a time (the first step at
 *     y 3, the others inside the crown), so it zig-zags. Rooted dirt round the foot, two or three oak roots that end in
 *     rooted dirt, and hanging roots under the limbs.</li>
 * <li><b>Crown:</b> from y 4 up (4 free blocks under it, never a low leaf). One big ellipsoid plus 2–5 satellite lobes
 *     overlapping it, a jittered edge, an eroded rim, and open bites (sky and the limbs show through). Azalea and
 *     flowering azalea leaves mixed evenly, with more flowers on the sun side. Every satellite sits on its own oak limb.</li>
 * <li><b>Foot:</b> azalea bushes on mounds of moss block or rooted dirt (never on stone, never on leaves), potted
 *     azaleas, moss carpet and pink petals.</li>
 * </ul>
 *
 * <p>Seed varies: crown proportions, height, number and position of lobes, the sun side, the share of flowers, the
 * trunk's steps and the roots. Size 5–17 × 5–17 × 9+.
 */
public final class TreeAzaleaBlossom implements Variant {
	private static final Wood WOOD = new Wood(Blocks.OAK_LOG, Blocks.OAK_WOOD, b(Blocks.OAK_WOOD));
	private static final BlockState GREEN = leaves(Blocks.AZALEA_LEAVES);
	private static final BlockState BLOOM = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
	/** The lowest y of any leaf or limb (the trunk and its foot aside): 4 free blocks under the crown. */
	private static final int CLEAR = 4;

	@Override
	public String id() {
		return "tree_azalea_blossom";
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
		return 9;
	}

	@Override
	public int maxW() {
		return 17;
	}

	@Override
	public int maxD() {
		return 17;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xA2A1EA5L);
		int w = p.w();
		int d = p.d();
		double cu0 = (w - 1) / 2.0;
		double cv0 = (d - 1) / 2.0;
		double ru = TreeKit.clamp(w / 2.0 - 0.7, 2.0, 8.5);
		double rv = TreeKit.clamp(d / 2.0 - 0.7, 2.0, 8.5);
		double rc = Math.min(ru, rv);
		boolean big = rc >= 4.5;
		int avail = p.h() - CLEAR;

		// ---- the balls of the crown: {cu, cy, cv, rx, ry, rz}
		double ryM = Math.max(1.3, Math.min(rc * (0.62 + 0.18 * r.nextDouble()), (avail - 0.4) / 1.92));
		double cyM = CLEAR + ryM * 0.92;
		double rxM = ru * (0.74 + 0.08 * r.nextDouble());
		double rzM = rv * (0.74 + 0.08 * r.nextDouble());
		double slack = Math.max(0, Math.min(w / 2.0 - 0.5 - rxM, d / 2.0 - 0.5 - rzM));
		double la = r.nextDouble() * Math.PI * 2;
		double lamp = Math.min(slack, 0.3 + 0.7 * r.nextDouble());
		double cuM = cu0 + Math.cos(la) * lamp;
		double cvM = cv0 + Math.sin(la) * lamp;
		List<double[]> balls = new ArrayList<>();
		balls.add(new double[] {cuM, cyM, cvM, rxM, ryM, rzM});
		int nS = 2 + (rc >= 4.5 ? 1 : 0) + (rc >= 6.5 ? 1 : 0) + (rc >= 3.5 ? r.nextInt(2) : 0);
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < nS; i++) {
			double ang = a0 + i * Math.PI * 2 / nS + (r.nextDouble() - 0.5) * 0.7;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			double room = TreeKit.room(t, cuM, cvM, ex, ez, 0);
			double rs = Math.min(rc * (0.40 + 0.15 * r.nextDouble()), Math.max(1.4, (room + 0.5) * 0.5));
			rs = Math.max(rs, 1.4);
			double dd = TreeKit.clamp(room + 0.5 - rs, 0, rc * (0.5 + 0.25 * r.nextDouble()));
			double rys = rs * (0.7 + 0.2 * r.nextDouble());
			double lo = CLEAR + 0.9 * rys;
			double hi = p.h() - 0.6 - rys;
			double cy = cyM + (r.nextDouble() - 0.4) * ryM * 0.9;
			cy = Math.max(lo, Math.min(Math.max(lo, hi), cy));
			balls.add(new double[] {cuM + ex * dd, cy, cvM + ez * dd, rs, rys, rs * (0.9 + 0.2 * r.nextDouble())});
		}

		// ---- trunk: 1×1 oak with sideways steps (a 2×2 bark foot on big plots)
		int yTop = Math.min((int) Math.floor(cyM), p.h() - 1);
		int bu = (int) Math.floor(cu0) - ((w & 1) == 1 ? r.nextInt(2) : 0);
		int bv = (int) Math.floor(cv0) - ((d & 1) == 1 ? r.nextInt(2) : 0);
		int u = big ? bu + r.nextInt(2) : (int) Math.floor(cu0);
		int v = big ? bv + r.nextInt(2) : (int) Math.floor(cv0);
		int fx0 = big ? bu : u;
		int fx1 = big ? bu + 1 : u;
		int fz0 = big ? bv : v;
		int fz1 = big ? bv + 1 : v;
		int maxOff = big ? 2 : 1;
		int[] lead0 = {u, v};
		int[] stepAt = {CLEAR, 6 + r.nextInt(2), 8 + r.nextInt(2)};
		int nSteps = big ? 3 : 2;
		int stepsDone = 0;
		boolean alongU = r.nextBoolean();
		int sign = r.nextBoolean() ? 1 : -1;
		int[] lu = new int[yTop + 2];
		int[] lv = new int[yTop + 2];
		for (int y = 0; y <= yTop; y++) {
			boolean step = stepsDone < nSteps && y == stepAt[stepsDone] && y < yTop;
			if (big && y <= 2) {
				for (int a = 0; a < 2; a++) {
					for (int k = 0; k < 2; k++) {
						t.wood(bu + a, y, bv + k, WOOD.bark());
					}
				}
			}
			if (step) {
				int nu = u + (alongU ? sign : 0);
				int nv = v + (alongU ? 0 : sign);
				int offU = nu - lead0[0];
				int offV = nv - lead0[1];
				if (Math.abs(offU) > maxOff || Math.abs(offV) > maxOff || nu < 1 || nu > w - 2 || nv < 1 || nv > d - 2) {
					sign = -sign;
					nu = u + (alongU ? sign : 0);
					nv = v + (alongU ? 0 : sign);
				}
				t.wood(u, y, v, WOOD.bark());
				t.wood(nu, y, nv, WOOD.bark());
				u = nu;
				v = nv;
				alongU = !alongU;
				stepsDone++;
			} else {
				t.wood(u, y, v, WOOD.log(Direction.Axis.Y));
			}
			lu[y] = u;
			lv[y] = v;
		}

		// ---- roots: rooted dirt round the foot, oak roots out of three of the four faces, ending in rooted dirt
		for (int vv = fz0 - 1; vv <= fz1 + 1; vv++) {
			for (int uu = fx0 - 1; uu <= fx1 + 1; uu++) {
				if (uu >= fx0 && uu <= fx1 && vv >= fz0 && vv <= fz1) {
					continue;
				}
				if (t.empty(uu, 0, vv) && t.hash(uu, 0, vv, 100) < 38) {
					t.fixed(uu, 0, vv, b(Blocks.ROOTED_DIRT));
				}
			}
		}
		int[][] faces = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		int skip = r.nextInt(4);
		for (int k = 0; k < 4; k++) {
			if (k == skip && !big) {
				continue;
			}
			int du = faces[k][0];
			int dv = faces[k][1];
			int su = du < 0 ? fx0 - 1 : du > 0 ? fx1 + 1 : fx0 + r.nextInt(fx1 - fx0 + 1);
			int sv = dv < 0 ? fz0 - 1 : dv > 0 ? fz1 + 1 : fz0 + r.nextInt(fz1 - fz0 + 1);
			int len = 1 + r.nextInt(big ? 4 : 3);
			Direction.Axis ax = du != 0 ? Direction.Axis.X : Direction.Axis.Z;
			for (int i = 0; i < len; i++) {
				int cu = su + du * i;
				int cv = sv + dv * i;
				if (i == 0) {
					t.woodSoft(cu, 0, cv, WOOD.bark());
				} else if (i == len - 1) {
					if (t.empty(cu, 0, cv)) {
						t.fixed(cu, 0, cv, b(Blocks.ROOTED_DIRT));
					}
				} else {
					t.woodSoft(cu, 0, cv, WOOD.log(ax));
				}
			}
		}

		// ---- limbs: each satellite hangs from its own oak limb that leaves the trunk at y 4 or higher
		for (int i = 1; i < balls.size(); i++) {
			double[] bl = balls.get(i);
			int sy = TreeKit.clamp(CLEAR + (i & 1), CLEAR, yTop);
			int eu = TreeKit.clamp((int) Math.round(bl[0]), 0, w - 1);
			int ev = TreeKit.clamp((int) Math.round(bl[2]), 0, d - 1);
			int ey = Math.max(CLEAR + 1, (int) Math.round(bl[1]));
			TreeKit.branch(t, WOOD, new int[] {lu[sy], sy, lv[sy]}, new int[] {eu, ey, ev}, false);
		}

		// ---- the crown
		double sa = r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6;
		double sx = Math.cos(sa);
		double sz = Math.sin(sa);
		double fb = 0.48 + 0.2 * r.nextDouble();
		int nHoles = rc < 3.5 ? 1 : 2 + Math.min(4, (int) (rc / 2.0));
		double[][] holes = new double[nHoles][4];
		for (int i = 0; i < nHoles; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double el = (rc < 3.5 ? 0.4 : -0.1) + r.nextDouble() * 0.55;
			double horiz = Math.sqrt(1 - el * el);
			holes[i][0] = cuM + Math.cos(ang) * horiz * rxM * 0.9;
			holes[i][1] = cyM + el * ryM * 0.9;
			holes[i][2] = cvM + Math.sin(ang) * horiz * rzM * 0.9;
			holes[i][3] = rc < 3.5 ? 0.8 : 1.0 + r.nextDouble() * Math.min(1.8, rc * 0.22);
		}
		for (int y = CLEAR; y < p.h(); y++) {
			for (int vv = 0; vv < d; vv++) {
				for (int uu = 0; uu < w; uu++) {
					double dist = 99;
					for (double[] bl : balls) {
						double du = (uu - bl[0]) / bl[3];
						double dy = (y - bl[1]) / bl[4];
						double dv = (vv - bl[2]) / bl[5];
						dist = Math.min(dist, Math.sqrt(du * du + dy * dy + dv * dv));
					}
					double jit = (t.hash(uu, y, vv, 9) - 4.0) / 4.5 * 0.14
							+ (t.hash(Math.floorDiv(uu, 2) + 11, Math.floorDiv(y, 2), Math.floorDiv(vv, 2) - 5, 7) - 3.0) / 3.5 * 0.1;
					if (dist > 1.0 + jit) {
						continue;
					}
					if (dist > 0.78 && t.hash(uu + 3, y + 5, vv + 7, 100) < 22) {
						continue; // an eroded rim
					}
					if (y <= CLEAR + 1 && dist > 0.55 && t.hash(uu + 9, y, vv + 1, 100) < 25) {
						continue; // a ragged underside
					}
					boolean bite = false;
					for (double[] h : holes) {
						double hx = uu - h[0];
						double hy = y - h[1];
						double hz = vv - h[2];
						if (hx * hx + hy * hy + hz * hz <= h[3] * h[3]) {
							bite = true;
							break;
						}
					}
					if (bite) {
						continue;
					}
					double sun = ((uu - cuM) * sx + (vv - cvM) * sz) / rc;
					double pf = TreeKit.clamp(fb + 0.3 * sun + 0.04 * (y - cyM), 0.12, 0.85);
					double fine = t.hash(uu, y + 17, vv, 100) / 100.0;
					double coarse = t.hash(Math.floorDiv(uu, 2) + 7, Math.floorDiv(y, 2), Math.floorDiv(vv, 2) - 3, 100) / 100.0;
					boolean flower = (t.hash(uu + 1, y + 2, vv + 3, 100) < 28 ? fine : coarse) < pf;
					t.leaf(uu, y, vv, flower ? BLOOM : GREEN);
				}
			}
		}

		// ---- the foot: bushes on moss or rooted dirt, pots, then the carpet
		int nB = 1 + (rc >= 3.5 ? 1 : 0) + (rc >= 5.5 ? 1 : 0) + (rc >= 7.5 ? 1 : 0) + r.nextInt(2);
		List<int[]> taken = new ArrayList<>();
		for (int i = 0, made = 0; i < 40 && made < nB; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double dist = rc * (0.5 + 0.45 * r.nextDouble());
			int mu = (int) Math.round(lead0[0] + Math.cos(ang) * dist);
			int mv = (int) Math.round(lead0[1] + Math.sin(ang) * dist);
			if (!freeSpot(t, taken, mu, mv, fx0, fx1, fz0, fz1)) {
				continue;
			}
			made++;
			taken.add(new int[] {mu, mv});
			mound(t, r, mu, mv);
			if (r.nextInt(5) < 2) {
				int[] f = faces[r.nextInt(4)];
				if (freeSpot(t, List.of(), mu + f[0], mv + f[1], fx0, fx1, fz0, fz1)) {
					mound(t, r, mu + f[0], mv + f[1]);
				}
			}
		}
		int nP = 1 + (rc >= 4.5 ? 1 : 0) + (rc >= 7 ? 1 : 0);
		for (int i = 0, made = 0; i < 40 && made < nP; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double dist = rc * (0.35 + 0.35 * r.nextDouble());
			int mu = (int) Math.round(lead0[0] + Math.cos(ang) * dist);
			int mv = (int) Math.round(lead0[1] + Math.sin(ang) * dist);
			if (!freeSpot(t, taken, mu, mv, fx0, fx1, fz0, fz1)) {
				continue;
			}
			made++;
			taken.add(new int[] {mu, mv});
			t.fixed(mu, 0, mv, b(r.nextInt(3) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_AZALEA));
		}
		double gu = (fx0 + fx1) / 2.0;
		double gv = (fz0 + fz1) / 2.0;
		TreeKit.ground(t, gu, gv, rc * 1.05 + 1.0, (uu, vv, k) -> {
			int hsh = t.hash(uu, 0, vv, 100);
			int pet = k < 0.5 ? 50 : k < 0.8 ? 34 : 18;
			int moss = k < 0.5 ? 24 : k < 0.8 ? 24 : 20;
			if (hsh < pet) {
				return TreeKit.petals(1 + t.hash(uu, 1, vv, k < 0.5 ? 4 : 2), t.hash(uu, 2, vv, 4));
			}
			if (hsh < pet + moss) {
				return b(Blocks.MOSS_CARPET);
			}
			return k >= 0.8 && hsh < pet + moss + 4 ? b(Blocks.SHORT_GRASS) : null;
		});

		t.prune();
		t.flush(c);

		// ---- hanging roots under the limbs (only from oak, with air below: nothing hangs from leaves)
		int roots = 0;
		int maxRoots = 3 + (int) rc;
		for (int y = CLEAR; y < p.h() && roots < maxRoots; y++) {
			for (int vv = 0; vv < d && roots < maxRoots; vv++) {
				for (int uu = 0; uu < w && roots < maxRoots; uu++) {
					if (t.kind(uu, y, vv) != TreeKit.WOOD || !t.empty(uu, y - 1, vv) || t.hash(uu, y + 41, vv, 100) >= 35) {
						continue;
					}
					p.put(c, uu, y - 1, vv, b(Blocks.HANGING_ROOTS), CitadelCanvas.HANGING);
					roots++;
				}
			}
		}
	}

	/** A cell that is free (y 0 and 1), inside the plot with a margin, clear of the foot and not next to a taken one. */
	private static boolean freeSpot(Vox t, List<int[]> taken, int u, int v, int fx0, int fx1, int fz0, int fz1) {
		if (u < 1 || u > t.w - 2 || v < 1 || v > t.d - 2 || !t.empty(u, 0, v) || !t.empty(u, 1, v)) {
			return false;
		}
		if (u >= fx0 - 2 && u <= fx1 + 2 && v >= fz0 - 2 && v <= fz1 + 2) {
			return false;
		}
		for (int[] o : taken) {
			if (Math.abs(o[0] - u) <= 1 && Math.abs(o[1] - v) <= 1) {
				return false;
			}
		}
		return true;
	}

	/** A one-block mound of moss block or rooted dirt with an azalea bush on top (flowering most of the time). */
	private static void mound(Vox t, Random r, int u, int v) {
		// the bush stands two blocks up: nothing hangs on y 4 over it, so a walker on top keeps 3 free blocks
		if (t.kind(u, CLEAR, v) == TreeKit.LEAF) {
			t.clear(u, CLEAR, v);
		}
		t.fixed(u, 0, v, b(r.nextBoolean() ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT));
		t.cover(u, 1, v, b(r.nextInt(5) < 3 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA));
	}
}
