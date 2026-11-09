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
 * A grove of two or three slender white birches of unequal height, each a 1×1 birch log leaning a little
 * away from the others, with a small bark flare and short diagonal roots at the foot (the tallest stands on
 * a 2×2 foot that thins to 1×1). Short diagonal twigs leave each trunk at staggered heights, alternating
 * sides, and end in small airy lobes of birch leaves with clumps of oak or azalea; a small lobe tops each
 * trunk. The foliage of different trunks is kept apart (a gap of air between them), short drips hang under
 * the lobes. A fallen mossy birch log lies across the front; ferns, lilies of the valley and moss fill the
 * floor.
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
	/** Leaf-to-other-tree neighbours that must stay air: the six faces and the four horizontal diagonals. */
	private static final int[][] NB = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
			{1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
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

		int[][] base = three
				? new int[][] {{2, 2}, {w - 3, 3}, {w / 2, d - 2}}
				: new int[][] {{1, 2}, {w - 2, d - 2}};
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

		// heights: the tallest reaches the top, the others 70–90 % of it, all different
		int tallest = r.nextInt(n);
		int maxTop = H - 3;
		int[] tops = new int[n];
		double[] fr = {0.88, 0.74};
		int rank = 0;
		for (int i = 0; i < n; i++) {
			tops[i] = i == tallest ? maxTop : Math.max(4, (int) Math.round(maxTop * (fr[rank++] + r.nextDouble() * 0.05)));
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
			double lean = Math.min(i == tallest ? 1.2 : 1.6 + r.nextDouble() * 0.6, TreeKit.room(t, u0, v0, ex, ez, 1.0));
			int top = tops[i];
			int[][] lead = column(t, u0, v0, top, ex * lean, ez * lean, 2);

			if (i == tallest) {
				// a 2×2 foot (towards the outside, away from the other trunks) that thins to three, then 1×1 from y = 2
				int fu = ex > 0 ? 1 : -1;
				int fv = ez > 0 ? 1 : -1;
				if (!t.in(u0 + fu, 0, v0)) {
					fu = -fu;
				}
				if (!t.in(u0, 0, v0 + fv)) {
					fv = -fv;
				}
				t.wood(u0, 0, v0, BIRCH.bark());
				t.wood(u0 + fu, 0, v0, BIRCH.bark());
				t.wood(u0, 0, v0 + fv, BIRCH.bark());
				t.wood(u0 + fu, 1, v0, BIRCH.log(Direction.Axis.Y));
			}
			footRoots(t, u0, v0, i == tallest ? 2 : 1 + t.hash(u0, 5, v0, 2), 31 + i);

			// twigs climbing the trunk on alternating sides of "outwards", each to a small airy lobe
			int nl = top >= 9 ? 4 : top >= 6 ? 3 : 2;
			int lo = Math.max(2, (int) Math.round(top * 0.38)) + (i & 1);
			int hi = Math.max(lo, top - 2);
			double size = i == tallest ? 1.12 : 1.0;
			List<Lobe> mine = new ArrayList<>();
			int side = r.nextBoolean() ? 1 : -1;
			for (int k = 0; k < nl; k++) {
				int sy = Math.min(top - 1, lo + (nl == 1 ? 0 : (int) Math.round((hi - lo) * k / (double) (nl - 1))));
				int su = lead[0][sy];
				int sv = lead[1][sy];
				double ang = out + side * (0.55 + r.nextDouble() * 0.6);
				side = -side;
				double lx = Math.cos(ang);
				double lz = Math.sin(ang);
				double room = TreeKit.room(t, su, sv, lx, lz, 0);
				if (room < 1.0) {
					ang += Math.PI;
					lx = Math.cos(ang);
					lz = Math.sin(ang);
					room = TreeKit.room(t, su, sv, lx, lz, 0);
				}
				int len = TreeKit.clamp((int) Math.round(Math.min(room - 0.4, 1.2 + r.nextDouble() * 1.8)), 1, 3);
				int tu = TreeKit.clamp(su + (int) Math.round(lx * len), 0, w - 1);
				int tv = TreeKit.clamp(sv + (int) Math.round(lz * len), 0, d - 1);
				if (tu == su && tv == sv) {
					continue;
				}
				int ty = Math.min(sy + (len >= 2 ? 1 : 0), H - 3);
				TreeKit.branch(t, BIRCH, new int[] {su, sy, sv}, new int[] {tu, ty, tv}, false);
				double rx = Math.min(1.8, (1.2 + r.nextDouble() * 0.5) * size);
				double rz = rx * (0.85 + r.nextDouble() * 0.2);
				mine.add(new Lobe(tu, ty, tv, rx, rz, 1.5, 0.9));
			}
			// the small top lobe on the leader
			double cr = (1.3 + r.nextDouble() * 0.3) * size;
			mine.add(new Lobe(lead[0][top], top, lead[1][top], Math.min(1.8, cr), Math.min(1.8, cr * 0.9), 1.6, 1.0));
			lobes.add(mine);
			claim(t, own, i + 1);
		}

		// ---- foliage, trunk by trunk, with short drips under each lobe
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

		// ---- keep air between the crowns of different trunks
		List<int[]> gone = new ArrayList<>();
		for (int y = 1; y < t.h; y++) {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (t.kind(u, y, v) != TreeKit.LEAF) {
						continue;
					}
					int me = own[(y * d + v) * w + u];
					for (int[] o : NB) {
						int nu = u + o[0];
						int ny = y + o[1];
						int nv = v + o[2];
						if (!t.in(nu, ny, nv) || t.kind(nu, ny, nv) == TreeKit.EMPTY) {
							continue;
						}
						int other = own[(ny * d + nv) * w + nu];
						if (other != 0 && other != me) {
							gone.add(new int[] {u, y, v});
							break;
						}
					}
				}
			}
		}
		for (int[] g : gone) {
			t.clear(g[0], g[1], g[2]);
		}

		fallenLog(t, flipU);
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

	/** A small bark flare at the foot and {@code count} short diagonal roots (a plan staircase) over the ground. */
	private static void footRoots(Vox t, int u0, int v0, int count, int salt) {
		int[][] diag = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		int first = t.hash(u0, salt, v0, 4);
		for (int k = 0; k < count; k++) {
			int[] dd = diag[(first + k * 2 + (k > 1 ? 1 : 0)) % 4];
			int du = dd[0];
			int dv = dd[1];
			int len = 2 + t.hash(u0 + k, 1, v0 + salt, 2);
			// face cell (flare, bark), corner, then one more step out on each axis
			int[][] cells = {{u0 + du, v0}, {u0 + du, v0 + dv}, {u0 + 2 * du, v0 + dv}, {u0 + 2 * du, v0 + 2 * dv}};
			for (int i = 0; i <= len && i < cells.length; i++) {
				BlockState s;
				if (i <= 1) {
					s = BIRCH.bark();
				} else {
					boolean stepU = cells[i][0] != cells[i - 1][0];
					s = BIRCH.log(stepU ? Direction.Axis.X : Direction.Axis.Z);
				}
				t.woodSoft(cells[i][0], 0, cells[i][1], s);
			}
			if (k == 0 && t.hash(u0, 2, v0, 2) == 0) {
				t.woodSoft(u0 + du, 1, v0, BIRCH.bark()); // the flare climbing the trunk
			}
		}
		// one more flare cell on a face
		int f = t.hash(u0, 3, v0 + salt, 4);
		int[][] faces = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		t.woodSoft(u0 + faces[f][0], 0, v0 + faces[f][1], BIRCH.bark());
	}

	/** One- and two-leaf drips under the lobes just drawn (the leaves not yet owned by a trunk). */
	private static void drips(Vox t, int[] own, Leaves lv) {
		List<int[]> seeds = new ArrayList<>();
		for (int y = 3; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF && own[(y * t.d + v) * t.w + u] == 0 && t.empty(u, y - 1, v)
							&& t.hash(u, y, v, 6) == 0) {
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

	/** The fallen birch across the front row, with moss and a mushroom on top. */
	private static void fallenLog(Vox t, boolean flipU) {
		int len = Math.min(3, t.w - 3);
		int start = flipU ? t.w - 1 - len : 1;
		for (int i = 0; i < len; i++) {
			int u = start + i;
			t.wood(u, 0, 0, BIRCH.log(Direction.Axis.X));
			int k = t.hash(u, 1, 0, 3);
			if (k == 0) {
				t.cover(u, 1, 0, b(Blocks.MOSS_CARPET));
			} else if (k == 1 && i > 0 && i < len - 1) {
				t.cover(u, 1, 0, b(Blocks.BROWN_MUSHROOM));
			}
		}
		t.cover(flipU ? start - 1 : start + len, 0, 0, b(Blocks.LILY_OF_THE_VALLEY));
		t.cover(flipU ? start + len : start - 1, 0, 0, b(Blocks.FERN));
	}

	/** Ferns, lilies of the valley, moss and a white tulip now and then; moss and ferns hug the trunks. */
	private static void floor(Vox t, int[][] base) {
		for (int v = 1; v < t.d; v++) {
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
