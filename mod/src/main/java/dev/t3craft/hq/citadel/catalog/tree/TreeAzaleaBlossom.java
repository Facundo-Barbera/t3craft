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
 * Flowering azalea. Round 2: the vanilla {@code flowering_azalea_leaves} texture has little pink, so the bloom is carried
 * by blocks that are really pink: mossy caps on the crown top with a flowering azalea bush or pink petals on each, and
 * big patches of flowering against plain azalea leaves.
 *
 * <ul>
 * <li><b>Crown:</b> from y 4 up (4 free blocks under it, never a low leaf), a ragged mass of several overlapping lobes
 *     with bites, sun-side flower patches, caps of moss block carrying {@code flowering_azalea} or {@code pink_petals}
 *     on the top surface, and knots of rooted dirt on the underside with hanging roots or a spore blossom.</li>
 * <li><b>Shape:</b> a wide, low dome of many overlapping masses (a big shrub, not a trunk with a crown) held up by
 *     three to five oak stems that stand on prop roots of mangrove roots and lean in under the masses; the middle of
 *     the plot is an open clearing.</li>
 * <li><b>Foot:</b> rooted dirt, mangrove roots, azalea bushes on mounds of moss block or rooted dirt, potted azaleas,
 *     moss carpet and pink petals.</li>
 * </ul>
 *
 * <p>Size 5–17 × 5–17 × 9+. The city asks 7–10 × 7–10 × 20 at scale 200.
 */
public final class TreeAzaleaBlossom implements Variant {
	private static final Wood WOOD = new Wood(Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD, b(Blocks.MANGROVE_WOOD));
	private static final BlockState GREEN = leaves(Blocks.AZALEA_LEAVES);
	private static final BlockState BLOOM = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
	/** A few small islands of real pink on the sunny top (a tenth of the shell at most). */
	private static final BlockState PINK = leaves(Blocks.CHERRY_LEAVES);
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

	/** Everything the steps of a build share. */
	private static final class X {
		Vox t;
		Plot p;
		Random r;
		int w;
		int d;
		double cu0;
		double cv0;
		double ru;
		double rv;
		double rc;
		boolean big;
		boolean small;
		int avail;
		List<double[]> balls = new ArrayList<>();
		double cuM;
		double cyM;
		double cvM;
		double rxM;
		double ryM;
		double rzM;
		int[] lead0 = new int[2];
		int fx0;
		int fx1;
		int fz0;
		int fz1;
		int yTop;
		int[] lu;
		int[] lv;
		List<int[]> tips = new ArrayList<>();
		double sx;
		double sz;
		double fb;
		/** Underside knots of rooted dirt: where a hanging root or spore blossom goes under them. */
		List<int[]> knots = new ArrayList<>();
		static final int[][] FACES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		X x = new X();
		x.t = new Vox(p);
		x.p = p;
		x.r = p.rng(0xA2A1EA5L);
		x.w = p.w();
		x.d = p.d();
		x.cu0 = (x.w - 1) / 2.0;
		x.cv0 = (x.d - 1) / 2.0;
		x.ru = TreeKit.clamp(x.w / 2.0 - 0.55, 2.0, 8.5);
		x.rv = TreeKit.clamp(x.d / 2.0 - 0.55, 2.0, 8.5);
		x.rc = Math.min(x.ru, x.rv);
		x.big = x.rc >= 4.5;
		x.small = x.rc < 3.5;
		x.avail = p.h() - CLEAR;
		x.sx = 1;

		shapeDome(x);
		stemsDome(x);
		crown(x);
		shell(x);
		// leaves over every tip (after the bites, so a tip is never left bare)
		for (int[] tp : x.tips) {
			int[][] around = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}};
			for (int[] o : around) {
				if (tp[1] + o[1] >= CLEAR) {
					x.t.leaf(tp[0] + o[0], tp[1] + o[1], tp[2] + o[2],
							x.t.hash(tp[0], tp[1] + o[1], tp[2] + o[2], 2) == 0 ? GREEN : BLOOM);
				}
			}
		}
		knots(x);
		foot(x);
		x.t.prune();
		x.t.flush(c);
		hanging(c, x);
	}

	// ------------------------------------------------------------------ shapes

	/** The dome: a taller middle mass and 4–7 lower ones round it, overlapping, wide and low like a big shrub. */
	private static void shapeDome(X x) {
		Random r = x.r;
		double rc = x.rc;
		x.ryM = Math.max(1.3, Math.min(Math.min(4.0, rc * 0.62 + 0.8), (x.avail - 0.4) / 1.92));
		x.cyM = CLEAR + x.ryM * 0.92;
		x.rxM = x.ru * (0.7 + 0.1 * r.nextDouble());
		x.rzM = x.rv * (0.7 + 0.1 * r.nextDouble());
		double slack = Math.max(0, Math.min(x.w / 2.0 - 0.5 - x.rxM, x.d / 2.0 - 0.5 - x.rzM));
		double la = r.nextDouble() * Math.PI * 2;
		double lamp = Math.min(slack, 0.2 + 0.5 * r.nextDouble());
		x.cuM = x.cu0 + Math.cos(la) * lamp;
		x.cvM = x.cv0 + Math.sin(la) * lamp;
		x.balls.add(new double[] {x.cuM, x.cyM, x.cvM, x.rxM, x.ryM, x.rzM});
		int nS = 4 + (rc >= 4.5 ? 1 : 0) + (rc >= 6.5 ? 2 : 0);
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < nS; i++) {
			double ang = a0 + i * Math.PI * 2 / nS + (r.nextDouble() - 0.5) * 0.6;
			double rs = Math.max(1.9, rc * (0.5 + 0.12 * r.nextDouble()));
			double dd = Math.max(1.2, rc * (0.62 + 0.22 * r.nextDouble()) - 0.15 * rs);
			double cu = TreeKit.clamp(x.cu0 + Math.cos(ang) * dd, rs * 0.85, x.w - 1 - rs * 0.85);
			double cv = TreeKit.clamp(x.cv0 + Math.sin(ang) * dd, rs * 0.85, x.d - 1 - rs * 0.85);
			double rys = Math.max(1.3, x.ryM * (0.7 + 0.25 * r.nextDouble()));
			double lo = CLEAR + 0.9 * rys;
			double hi = x.p.h() - 0.6 - rys;
			double cy = Math.max(lo, Math.min(Math.max(lo, hi), x.cyM - x.ryM * (0.1 + 0.45 * r.nextDouble())));
			x.balls.add(new double[] {cu, cy, cv, rs, rys, rs * (0.9 + 0.2 * r.nextDouble())});
		}
	}

	// ------------------------------------------------------------------ trunk and stems

	/**
	 * Three to five oak stems that lean in under the masses of the dome, each on a foot of mangrove roots with rooted
	 * dirt round it. The middle of the plot stays open ground (a clearing to stand in).
	 */
	private static void stemsDome(X x) {
		Vox t = x.t;
		Random r = x.r;
		x.yTop = CLEAR;
		x.lead0 = new int[] {(int) Math.round(x.cu0), (int) Math.round(x.cv0)};
		x.fx0 = x.lead0[0];
		x.fx1 = x.lead0[0];
		x.fz0 = x.lead0[1];
		x.fz1 = x.lead0[1];
		int n = 3 + (x.big ? 1 : 0) + (x.rc >= 6.5 ? 1 : 0);
		double a0 = r.nextDouble() * Math.PI * 2;
		double sr = Math.max(1.6, x.rc * 0.5);
		List<int[]> feet = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double ang = a0 + i * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.5;
			int su = TreeKit.clamp((int) Math.round(x.cu0 + Math.cos(ang) * sr), 1, x.w - 2);
			int sv = TreeKit.clamp((int) Math.round(x.cv0 + Math.sin(ang) * sr), 1, x.d - 2);
			boolean near = false;
			for (int[] f : feet) {
				near |= Math.abs(f[0] - su) <= 1 && Math.abs(f[1] - sv) <= 1;
			}
			if (near) {
				continue;
			}
			feet.add(new int[] {su, sv});
			// aim at the nearest mass, at most two blocks sideways so the stem stays a thin, walkable post
			double[] best = x.balls.get(0);
			double bd = 1e9;
			for (double[] bl : x.balls) {
				double dd = Math.hypot(bl[0] - su, bl[2] - sv);
				if (dd < bd) {
					bd = dd;
					best = bl;
				}
			}
			int tu = su + TreeKit.clamp((int) Math.round(best[0] - su), -2, 2);
			int tv = sv + TreeKit.clamp((int) Math.round(best[2] - sv), -2, 2);
			t.wood(su, 0, sv, b(Blocks.MANGROVE_ROOTS));
			TreeKit.branch(t, WOOD, new int[] {su, 1, sv}, new int[] {su, CLEAR - 1, sv}, false);
			TreeKit.branch(t, WOOD, new int[] {su, CLEAR - 1, sv}, new int[] {tu, CLEAR + 1, tv}, false);
			for (int y = 1; y <= CLEAR - 1; y++) {
				t.wood(su, y, sv, b(Blocks.MANGROVE_ROOTS)); // the lower stem is a prop root
			}
			x.tips.add(new int[] {tu, CLEAR + 1, tv});
			for (int[] f : X.FACES) {
				int ou = su + f[0];
				int ov = sv + f[1];
				if (t.empty(ou, 0, ov)) {
					int h = t.hash(ou, 0, ov, 100);
					if (h < 40) {
						t.fixed(ou, 0, ov, b(Blocks.ROOTED_DIRT));
					} else if (h < 62) {
						t.woodSoft(ou, 0, ov, b(Blocks.MANGROVE_ROOTS));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ crown

	private static void crown(X x) {
		Vox t = x.t;
		Random r = x.r;
		double rc = x.rc;
		int w = x.w;
		int d = x.d;
		double sa = r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6;
		x.sx = Math.cos(sa);
		x.sz = Math.sin(sa);
		x.fb = 0.5 + 0.18 * r.nextDouble();
		int nHoles = rc < 2.6 ? 0 : rc < 3.5 ? 1 : 3 + (int) (rc * 0.7);
		double[][] holes = new double[nHoles][4];
		for (int i = 0; i < nHoles; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double el = (rc < 3.5 ? 0.4 : -0.1) + r.nextDouble() * 0.55;
			double horiz = Math.sqrt(1 - el * el);
			holes[i][0] = x.cuM + Math.cos(ang) * horiz * x.rxM * 0.9;
			holes[i][1] = x.cyM + el * x.ryM * 0.9;
			holes[i][2] = x.cvM + Math.sin(ang) * horiz * x.rzM * 0.9;
			holes[i][3] = rc < 3.5 ? 0.8 : 1.0 + r.nextDouble() * Math.min(2.8, rc * 0.36);
		}
		for (int y = CLEAR; y < x.p.h(); y++) {
			for (int vv = 0; vv < d; vv++) {
				for (int uu = 0; uu < w; uu++) {
					double dist = 99;
					for (double[] bl : x.balls) {
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
					if (dist > 0.74 && t.hash(uu + 3, y + 5, vv + 7, 100) < 28) {
						continue; // an eroded rim
					}
					if (y <= CLEAR + 2 && dist > 0.5 && t.hash(uu + 9, y, vv + 1, 100) < 32) {
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
					t.leaf(uu, y, vv, t.hash(uu, y + 17, vv, 2) == 0 ? BLOOM : GREEN); // recoloured by the shell pass
				}
			}
		}
	}

	/**
	 * The visible shell decides the look from afar: exposed leaves bloom in big patches on the sun face and the top and
	 * stay plain green on the shade side and underneath. Where a bloom patch has a free top, a cap of moss block carries
	 * a flowering azalea or pink petals: the only blocks of the crown that are really pink.
	 */
	private static void shell(X x) {
		Vox t = x.t;
		int w = x.w;
		int d = x.d;
		List<int[]> shell = new ArrayList<>();
		int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int y = CLEAR; y < x.p.h(); y++) {
			for (int vv = 0; vv < d; vv++) {
				for (int uu = 0; uu < w; uu++) {
					if (t.kind(uu, y, vv) != TreeKit.LEAF) {
						continue;
					}
					for (int[] o : n6) {
						if (!t.in(uu + o[0], y + o[1], vv + o[2]) || t.kind(uu + o[0], y + o[1], vv + o[2]) == TreeKit.EMPTY) {
							shell.add(new int[] {uu, y, vv});
							break;
						}
					}
				}
			}
		}
		for (int[] s : shell) {
			double sun = ((s[0] - x.cuM) * x.sx + (s[2] - x.cvM) * x.sz) / x.rc;
			double top = (s[1] - x.cyM) / x.ryM;
			double pf = TreeKit.clamp(x.fb + 0.02 + 1.1 * sun + 0.5 * top, 0.05, 0.98);
			double big4 = t.hash(Math.floorDiv(s[0] + 2, 4) + 21, Math.floorDiv(s[1], 3), Math.floorDiv(s[2] + 1, 4) - 8, 100) / 100.0;
			double c2 = t.hash(Math.floorDiv(s[0], 2) + 7, Math.floorDiv(s[1], 2), Math.floorDiv(s[2], 2) - 3, 100) / 100.0;
			double noise = t.hash(s[0] + 5, s[1] + 9, s[2] + 13, 100) < 22 ? c2 : big4;
			boolean flower = noise < pf;
			double isle = t.hash(Math.floorDiv(s[0] + (s[1] & 1), 2) + 31, Math.floorDiv(s[1], 2) + 2, Math.floorDiv(s[2], 2) - 17, 100) / 100.0;
			boolean pink = flower && top > -0.35 && sun > -0.7 && isle < 0.16 && t.hash(s[0] + 9, s[1], s[2] + 4, 100) < 85;
			t.clear(s[0], s[1], s[2]);
			int flank = 0; // no cap on a lone spike: it needs two leaf neighbours on its own level
			for (int[] o : n6) {
				flank += o[1] == 0 && t.kind(s[0] + o[0], s[1], s[2] + o[2]) == TreeKit.LEAF ? 1 : 0;
			}
			boolean freeTop = flank >= 2 && s[1] + 1 < x.p.h() && t.empty(s[0], s[1] + 1, s[2]);
			boolean capPatch = t.hash(Math.floorDiv(s[0] + (s[1] & 1), 2) + 13, Math.floorDiv(s[1], 2), Math.floorDiv(s[2] + (s[0] & 1), 2) + 4, 100) < 52;
			if (flower && freeTop && capPatch && t.hash(s[0] + 1, s[1] + 4, s[2] + 2, 100) < 90) {
				t.fixed(s[0], s[1], s[2], b(Blocks.MOSS_BLOCK));
				boolean bush = t.hash(s[0], s[1] + 8, s[2], 5) != 0 && (s[1] + 2 >= x.p.h() || t.empty(s[0], s[1] + 2, s[2]));
				t.cover(s[0], s[1] + 1, s[2], bush ? b(Blocks.FLOWERING_AZALEA)
						: TreeKit.petals(2 + t.hash(s[0], s[1], s[2], 3), t.hash(s[0], s[1] + 1, s[2], 4)));
			} else {
				t.leaf(s[0], s[1], s[2], pink ? PINK : flower ? BLOOM : GREEN);
			}
		}
	}

	/** Knots of rooted dirt in the underside of the crown (hanging roots or a spore blossom go under them later). */
	private static void knots(X x) {
		Vox t = x.t;
		int maxKnots = 3 + (int) (x.rc * 2.0);
		for (int vv = 1; vv < x.d - 1 && x.knots.size() < maxKnots; vv++) {
			for (int uu = 1; uu < x.w - 1 && x.knots.size() < maxKnots; uu++) {
				if (t.kind(uu, CLEAR, vv) != TreeKit.LEAF || !t.empty(uu, CLEAR - 1, vv) || t.hash(uu + 4, CLEAR, vv + 6, 100) >= 24) {
					continue;
				}
				t.fixed(uu, CLEAR, vv, b(Blocks.ROOTED_DIRT));
				x.knots.add(new int[] {uu, vv});
			}
		}
	}

	// ------------------------------------------------------------------ foot

	private static void foot(X x) {
		Vox t = x.t;
		Random r = x.r;
		double rc = x.rc;
		int nB = 1 + (rc >= 3.5 ? 1 : 0) + (rc >= 5.5 ? 1 : 0) + (rc >= 7.5 ? 1 : 0) + r.nextInt(2);
		List<int[]> taken = new ArrayList<>();
		for (int i = 0, made = 0; i < 40 && made < nB; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double dist = rc * (0.5 + 0.45 * r.nextDouble());
			int mu = (int) Math.round(x.lead0[0] + Math.cos(ang) * dist);
			int mv = (int) Math.round(x.lead0[1] + Math.sin(ang) * dist);
			if (!freeSpot(t, taken, mu, mv, x.fx0, x.fx1, x.fz0, x.fz1)) {
				continue;
			}
			made++;
			taken.add(new int[] {mu, mv});
			mound(t, r, mu, mv);
			// a cluster: one to three more mounds beside it
			for (int k = 0, extra = 1 + r.nextInt(rc >= 4.5 ? 3 : 2); k < 6 && extra > 0; k++) {
				int[] f = X.FACES[r.nextInt(4)];
				if (freeSpot(t, List.of(), mu + f[0], mv + f[1], x.fx0, x.fx1, x.fz0, x.fz1)) {
					mound(t, r, mu + f[0], mv + f[1]);
					extra--;
				}
			}
		}
		// a mossy fallen oak log on the bigger plots, well clear of the trunk
		if (rc >= 4.5 && r.nextInt(3) != 0) {
			for (int i = 0; i < 20; i++) {
				boolean alongX = r.nextBoolean();
				int len = 3 + r.nextInt(2);
				double ang = r.nextDouble() * Math.PI * 2;
				int lu0 = (int) Math.round(x.lead0[0] + Math.cos(ang) * rc * 0.85) - (alongX ? len / 2 : 0);
				int lv0 = (int) Math.round(x.lead0[1] + Math.sin(ang) * rc * 0.85) - (alongX ? 0 : len / 2);
				boolean ok = true;
				for (int k = 0; k < len && ok; k++) {
					ok = freeSpot(t, taken, lu0 + (alongX ? k : 0), lv0 + (alongX ? 0 : k), x.fx0, x.fx1, x.fz0, x.fz1);
				}
				if (!ok) {
					continue;
				}
				for (int k = 0; k < len; k++) {
					int cu = lu0 + (alongX ? k : 0);
					int cv = lv0 + (alongX ? 0 : k);
					t.wood(cu, 0, cv, WOOD.log(alongX ? Direction.Axis.X : Direction.Axis.Z));
					if (t.hash(cu, 1, cv, 2) == 0) {
						t.cover(cu, 1, cv, b(Blocks.MOSS_CARPET));
					}
					taken.add(new int[] {cu, cv});
				}
				break;
			}
		}
		int nP = 1 + (rc >= 4.5 ? 1 : 0) + (rc >= 7 ? 1 : 0);
		for (int i = 0, made = 0; i < 40 && made < nP; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double dist = rc * (0.35 + 0.35 * r.nextDouble());
			int mu = (int) Math.round(x.lead0[0] + Math.cos(ang) * dist);
			int mv = (int) Math.round(x.lead0[1] + Math.sin(ang) * dist);
			if (!freeSpot(t, taken, mu, mv, x.fx0, x.fx1, x.fz0, x.fz1)) {
				continue;
			}
			made++;
			taken.add(new int[] {mu, mv});
			t.fixed(mu, 0, mv, b(r.nextInt(3) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_AZALEA));
		}
		double gu = (x.fx0 + x.fx1) / 2.0;
		double gv = (x.fz0 + x.fz1) / 2.0;
		TreeKit.ground(t, gu, gv, rc * 1.4 + 1.0, (uu, vv, k) -> {
			int hsh = t.hash(uu, 0, vv, 100);
			// petals in drifts 2–3 blocks wide (coarse noise) plus single ones, moss between
			boolean drift = t.hash(Math.floorDiv(uu, 2) + 5, 0, Math.floorDiv(vv, 2) + 1, 100) < (k < 0.6 ? 55 : 32);
			int pet = drift ? 80 : 16;
			int moss = drift ? 14 : k < 0.8 ? 62 : 54;
			if (hsh < pet) {
				return TreeKit.petals(drift ? 2 + t.hash(uu, 1, vv, 3) : 1, t.hash(uu, 2, vv, 4));
			}
			if (hsh < pet + moss) {
				return b(Blocks.MOSS_CARPET);
			}
			return k >= 0.8 && hsh < pet + moss + 4 ? b(Blocks.SHORT_GRASS) : null;
		});
	}

	/** Hanging roots and spore blossoms: under the rooted-dirt knots and under oak with air below (never under leaves). */
	private static void hanging(CitadelCanvas c, X x) {
		Vox t = x.t;
		Plot p = x.p;
		for (int[] k : x.knots) {
			if (t.kind(k[0], CLEAR, k[1]) != TreeKit.FIXED) {
				continue;
			}
			int h = t.hash(k[0], CLEAR + 2, k[1], 3);
			p.put(c, k[0], CLEAR - 1, k[1], b(h == 0 ? Blocks.SPORE_BLOSSOM : Blocks.HANGING_ROOTS), CitadelCanvas.HANGING);
		}
		int roots = 0;
		int maxRoots = 3 + (int) (x.rc * 1.2);
		for (int y = CLEAR + 1; y < p.h() && roots < maxRoots; y++) {
			for (int vv = 0; vv < x.d && roots < maxRoots; vv++) {
				for (int uu = 0; uu < x.w && roots < maxRoots; uu++) {
					if (t.kind(uu, y, vv) != TreeKit.WOOD || !t.empty(uu, y - 1, vv) || t.hash(uu, y + 41, vv, 100) >= 35) {
						continue;
					}
					p.put(c, uu, y - 1, vv, b(t.hash(uu, y + 2, vv, 2) == 0 ? Blocks.SPORE_BLOSSOM : Blocks.HANGING_ROOTS),
							CitadelCanvas.HANGING);
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
		if (t.kind(u, CLEAR, v) == TreeKit.FIXED || t.kind(u, CLEAR, v) == TreeKit.WOOD || t.kind(u, CLEAR - 1, v) != TreeKit.EMPTY) {
			return false; // a knot of rooted dirt overhead
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
