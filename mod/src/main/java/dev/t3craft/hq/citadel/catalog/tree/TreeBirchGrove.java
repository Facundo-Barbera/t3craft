package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A grove of two or three slender white birches of clearly different heights (100, 80 and 60 % of the
 * plot), each a 1×1 birch log leaning a little away from the others, with one or two short diagonal roots
 * (one block high, ending in moss) at the foot and one or two bare twig stubs on the white trunk. High up,
 * two or three small loose lobes sit on short diagonal twigs on alternating sides, plus a small top tuft:
 * birch leaves with clumps of oak or azalea. The foliage of different trunks keeps two cells of air between
 * them, so the white strokes of the trunks carry the design. Ferns, lilies of the valley and moss fill the floor.
 *
 * <p>Size: 6–9 × 6–9 × 9+ (two trunks on 6-wide plots, three from 7 × 7 up; the trees stop at 15 high).
 */
public final class TreeBirchGrove implements Variant {
	private static final Wood BIRCH = new Wood(Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, b(Blocks.BIRCH_WOOD));
	private static final Leaves[] TONES = {
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.OAK_LEAVES).clumps(3),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.AZALEA_LEAVES).clumps(3),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.OAK_LEAVES).clumps(4),
	};

	@Override
	public String id() {
		return "tree_birch_grove";
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

	/** A lobe to draw: centre, radii. */
	private record Lobe(int u, int y, int v, double rx, double rz, double up, double down) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xB12C_6A0BL);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 15);
		boolean flipU = r.nextBoolean();
		boolean three = w >= 7 && d >= 7;

		// trunks spread to three corners of the plot (two on 6-wide plots), so the crowns keep apart
		int[][] base = three
				? new int[][] {{1, 2}, {w - 2, 1}, {(w - 1) / 2 + (w >= 8 ? 1 : 0), d - 2}}
				: new int[][] {{1, 1}, {w - 2, d - 2}};
		int n = base.length;
		for (int[] bs : base) {
			if (flipU) {
				bs[0] = w - 1 - bs[0];
			}
		}
		double mu = 0;
		double mv = 0;
		for (int[] bs : base) {
			mu += bs[0] / (double) n;
			mv += bs[1] / (double) n;
		}

		// heights clearly different: 100 %, 80 % and 60 % of the available height (shuffled between the trunks)
		int maxTop = Math.min(13, H - 2);
		double[] fr = three ? new double[] {1.0, 0.8, 0.6} : new double[] {1.0, 0.7};
		for (int i = fr.length - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			double tmp = fr[i];
			fr[i] = fr[j];
			fr[j] = tmp;
		}
		int[] tops = new int[n];
		for (int i = 0; i < n; i++) {
			tops[i] = Math.max(4, (int) Math.round(maxTop * fr[i]));
		}

		int[] own = new int[w * d * t.h];
		List<List<Lobe>> lobes = new ArrayList<>();

		// ---- wood of every trunk first (so the foliage never hides a limb and ownership is clean)
		for (int i = 0; i < n; i++) {
			int u0 = base[i][0];
			int v0 = base[i][1];
			double ou = u0 - mu;
			double ov = v0 - mv;
			double ol = Math.hypot(ou, ov);
			double out = ol < 1e-6 ? r.nextDouble() * Math.PI * 2 : Math.atan2(ov, ou);
			double ex = Math.cos(out);
			double ez = Math.sin(out);
			double lean = Math.min(0.6 + r.nextDouble() * 0.5, TreeKit.room(t, u0, v0, ex, ez, 1.0));
			int top = tops[i];
			int[][] lead = column(t, u0, v0, top, ex * lean, ez * lean, 3);
			footRoots(t, u0, v0, 1 + t.hash(u0, 5, v0, 2), 31 + i);

			// two or three small lobes high up the trunk, each on a short diagonal twig, alternating sides
			int nl = top >= 10 ? 3 : 2;
			int lo = Math.max(3, (int) Math.round(top * 0.5));
			int hi = Math.max(lo, top - 3);
			List<Lobe> mine = new ArrayList<>();
			int side = r.nextBoolean() ? 1 : -1;
			int lowest = top;
			for (int k = 0; k < nl; k++) {
				int sy = lo + (nl == 1 ? 0 : (int) Math.round((hi - lo) * k / (double) (nl - 1)));
				sy = Math.min(sy, top - 2);
				int su = lead[0][sy];
				int sv = lead[1][sy];
				double ang = out + side * (0.6 + r.nextDouble() * 0.6);
				side = -side;
				double lx = Math.cos(ang);
				double lz = Math.sin(ang);
				double room = TreeKit.room(t, su, sv, lx, lz, 1.0);
				if (room < 1.0) {
					ang = out - (ang - out);
					lx = Math.cos(ang);
					lz = Math.sin(ang);
					room = TreeKit.room(t, su, sv, lx, lz, 1.0);
				}
				int len = room >= 2.0 && r.nextBoolean() ? 2 : 1;
				int du = (int) Math.round(lx * len);
				int dv = (int) Math.round(lz * len);
				if (du == 0 && dv == 0) {
					if (Math.abs(lx) >= Math.abs(lz)) {
						du = lx >= 0 ? 1 : -1;
					} else {
						dv = lz >= 0 ? 1 : -1;
					}
				}
				int tu = TreeKit.clamp(su + du, 1, w - 2);
				int tv = TreeKit.clamp(sv + dv, 1, d - 2);
				int ly = sy + 2; // the twig ends one under the lobe's centre: it shows right up to the leaves
				if (ly > H - 2 || tu == su && tv == sv) {
					continue;
				}
				TreeKit.branch(t, BIRCH, new int[] {su, sy, sv}, new int[] {tu, ly - 1, tv}, false);
				lowest = Math.min(lowest, sy);
				double rx = 1.25 + r.nextDouble() * 0.45;
				double rz = TreeKit.clamp(rx * (0.85 + r.nextDouble() * 0.25), 1.2, 1.7);
				mine.add(new Lobe(tu, ly, tv, rx, rz, 1.0, k == 0 ? 0.0 : 0.9));
			}
			// the small top tuft on the leader
			mine.add(new Lobe(lead[0][top], top, lead[1][top], 1.3, 1.2, 1.0, 0.0));

			// one or two bare twig stubs on the white trunk below the foliage
			int stubs = 1 + t.hash(u0, 9, v0, 2);
			int sTop = lowest - 2;
			int sLo = Math.max(2, (int) Math.round(top * 0.25));
			int[][] faces = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
			int f0 = t.hash(u0, 11, v0 + i, 4);
			for (int s = 0; s < stubs; s++) {
				int y = s == 0 ? sTop : sLo + (sTop - sLo) / 3;
				if (y < 2 || y > sTop || s == 1 && sTop - y < 2) {
					continue;
				}
				int[] fc = faces[(f0 + s * 2) % 4];
				int su = lead[0][y] + fc[0];
				int sv = lead[1][y] + fc[1];
				if (t.empty(su, y, sv) && t.kind(su, y - 1, sv) == TreeKit.EMPTY) {
					t.wood(su, y, sv, BIRCH.log(fc[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
			lobes.add(mine);
			claim(t, own, i + 1);
		}

		// ---- foliage, trunk by trunk, with a few short drips under the lobes
		for (int i = 0; i < n; i++) {
			Leaves lv = TONES[i % TONES.length];
			int k = 0;
			for (Lobe lb : lobes.get(i)) {
				TreeKit.lobe(t, lb.u(), lb.y(), lb.v(), lb.rx(), lb.rz(), lb.up(), lb.down(), k % 2 == 0 ? lv : lv.swap(),
						0.35, 10 * i + k);
				k++;
			}
			drips(t, own, lv);
			claim(t, own, i + 1);
		}

		// ---- keep air between the crowns of different trunks: a leaf goes if another trunk's wood touches
		// it, or if another trunk's leaf lies within two cells of it and that trunk is the taller one
		List<int[]> gone = new ArrayList<>();
		for (int y = 1; y < t.h; y++) {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (t.kind(u, y, v) != TreeKit.LEAF) {
						continue;
					}
					int me = own[(y * d + v) * w + u];
					boolean drop = false;
					for (int oy = -1; oy <= 1 && !drop; oy++) {
						for (int ov = -2; ov <= 2 && !drop; ov++) {
							for (int ou = -2; ou <= 2 && !drop; ou++) {
								int nu = u + ou;
								int ny = y + oy;
								int nv = v + ov;
								if (!t.in(nu, ny, nv) || t.kind(nu, ny, nv) == TreeKit.EMPTY) {
									continue;
								}
								int other = own[(ny * d + nv) * w + nu];
								if (other == 0 || other == me || me == 0) {
									continue;
								}
								byte kk = t.kind(nu, ny, nv);
								boolean near = Math.abs(ou) <= 1 && Math.abs(ov) <= 1;
								if (kk == TreeKit.WOOD && near) {
									drop = true;
								} else if (kk == TreeKit.LEAF && tops[other - 1] * 10 + other > tops[me - 1] * 10 + me) {
									drop = true;
								}
							}
						}
					}
					if (drop) {
						gone.add(new int[] {u, y, v});
					}
				}
			}
		}
		for (int[] g : gone) {
			t.clear(g[0], g[1], g[2]);
		}

		floor(t, base);
		t.prune();
		t.flush(c);
	}

	/** Marks every filled, still unowned cell as belonging to trunk {@code id}. */
	private static void claim(Vox t, int[] own, int id) {
		for (int y = 0; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					int ix = (y * t.d + v) * t.w + u;
					if (own[ix] == 0 && t.kind(u, y, v) != TreeKit.EMPTY) {
						own[ix] = id;
					}
				}
			}
		}
	}

	/**
	 * A slender 1×1 birch log from y = 0 to {@code top} that leans towards {@code (u0 + leanU, v0 + leanV)}
	 * from {@code yStart} up (smoothstep, one axis per layer, a sideways log at each step).
	 */
	private static int[][] column(Vox t, int u0, int v0, int top, double leanU, double leanV, int yStart) {
		int[] lu = new int[t.h + 1];
		int[] lv = new int[t.h + 1];
		int cu = u0;
		int cv = v0;
		int span = Math.max(1, top - yStart);
		for (int y = 0; y <= Math.min(top, t.h - 1); y++) {
			if (y > yStart) {
				double f = Math.min(1.0, (y - yStart) / (double) span);
				double e = f * f * (3 - 2 * f);
				int tu = u0 + (int) Math.round(leanU * e);
				int tv = v0 + (int) Math.round(leanV * e);
				int du = Integer.signum(tu - cu);
				int dv = Integer.signum(tv - cv);
				if (du != 0 && dv != 0) {
					if ((y & 1) == 0) {
						dv = 0;
					} else {
						du = 0;
					}
				}
				if (du != 0 || dv != 0) {
					t.wood(cu + du, y - 1, cv + dv, BIRCH.log(du != 0 ? Direction.Axis.X : Direction.Axis.Z));
				}
				cu += du;
				cv += dv;
			}
			lu[y] = cu;
			lv[y] = cv;
			t.wood(cu, y, cv, BIRCH.log(Direction.Axis.Y));
		}
		for (int y = Math.min(top, t.h - 1) + 1; y <= t.h; y++) {
			lu[y] = cu;
			lv[y] = cv;
		}
		return new int[][] {lu, lv};
	}

	/**
	 * {@code count} (1–2) short diagonal roots at the foot, one block high: a log out of a face, then (on longer
	 * roots) a log stepping round the corner, ending in a moss carpet. No bark cubes around the foot.
	 */
	private static void footRoots(Vox t, int u0, int v0, int count, int salt) {
		int[][] diag = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		int first = t.hash(u0, salt, v0, 4);
		for (int k = 0; k < Math.min(2, count); k++) {
			int[] dd = diag[(first + k * 2) % 4];
			int du = dd[0];
			int dv = dd[1];
			boolean uFirst = t.hash(u0 + k, 2, v0, 2) == 0;
			int len = 1 + t.hash(u0 + k, 1, v0 + salt, 2);
			// a plan staircase: one step on the first axis, one on the other, then the mossy tip
			int[][] cells = uFirst
					? new int[][] {{u0 + du, v0}, {u0 + du, v0 + dv}, {u0 + 2 * du, v0 + dv}}
					: new int[][] {{u0, v0 + dv}, {u0 + du, v0 + dv}, {u0 + du, v0 + 2 * dv}};
			boolean ok = true;
			for (int i = 0; i < len && ok; i++) {
				int[] prev = i == 0 ? new int[] {u0, v0} : cells[i - 1];
				boolean stepU = cells[i][0] != prev[0];
				if (!t.empty(cells[i][0], 0, cells[i][1])) {
					ok = false;
					break;
				}
				t.wood(cells[i][0], 0, cells[i][1], BIRCH.log(stepU ? Direction.Axis.X : Direction.Axis.Z));
			}
			if (ok) {
				t.cover(cells[len][0], 0, cells[len][1], b(Blocks.MOSS_CARPET));
			}
		}
	}

	/** One- and two-leaf drips under the lobes just drawn (the leaves not yet owned by a trunk). */
	private static void drips(Vox t, int[] own, Leaves lv) {
		List<int[]> seeds = new ArrayList<>();
		for (int y = 3; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF && own[(y * t.d + v) * t.w + u] == 0 && t.empty(u, y - 1, v)
							&& t.hash(u, y, v, 8) == 0) {
						seeds.add(new int[] {u, y, v});
					}
				}
			}
		}
		for (int[] s : seeds) {
			int len = 1 + t.hash(s[0], 4, s[2], 2);
			TreeKit.hang(t, s[0], s[1] - 1, s[2], len, 2, lv.a(), lv.b(), 0, 0, false);
		}
	}

	/** Ferns, lilies of the valley, moss and a white tulip now and then; moss and ferns hug the trunks. */
	private static void floor(Vox t, int[][] base) {
		for (int v = 0; v < t.d; v++) {
			for (int u = 0; u < t.w; u++) {
				boolean near = false;
				for (int[] bs : base) {
					if (Math.abs(u - bs[0]) + Math.abs(v - bs[1]) <= 2) {
						near = true;
					}
				}
				BlockState s;
				if (near) {
					int k = t.hash(u, 0, v, 4);
					s = k == 0 ? b(Blocks.MOSS_CARPET) : k == 1 ? b(Blocks.FERN) : k == 2 ? b(Blocks.MOSS_CARPET) : null;
				} else {
					s = switch (t.hash(u, 0, v, 10)) {
						case 0, 1 -> b(Blocks.FERN);
						case 2, 3 -> b(Blocks.LILY_OF_THE_VALLEY);
						case 4, 5 -> b(Blocks.MOSS_CARPET);
						case 6 -> b(Blocks.WHITE_TULIP);
						default -> null;
					};
				}
				if (s != null) {
					t.cover(u, 0, v, s);
				}
			}
		}
	}
}
