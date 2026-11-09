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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A grove of two to four slender white birches of clearly different heights. The trunks are bare, 1×1 birch logs
 * with nothing on them below y 5 (no roots, no stubs: you walk between them and under them); they stand at least
 * three cells apart (two blocks of air) and a few lean a block or two inside their crown. Each crown is a light,
 * tall <em>vertical oval</em> of birch leaves (with clumps of oak or azalea), a ragged edge and small holes of sky
 * so the white bark shows through; on the taller trees a second, smaller oval hangs on a one-block twig lower down.
 * The crowns of different trunks never touch each other. The first leaf is always at y 5 or higher.
 *
 * <p>The floor is a mossy forest floor in patches: moss carpet, ferns and large ferns, tall grass, wild flowers
 * (lily of the valley, oxeye daisy, cornflower, azure bluet, white tulip), a few mushrooms at the trunks. When the
 * plot has room (and clear sky over the spot) it also gets a birch bench between stripped-log ends or a mossy
 * boulder: one from 7 wide, two from 14 and three from 24.
 *
 * <p>Size: 6–48 × 6–48 × 10+. The grove itself stays within about {@code 8 + 0.8 (m − 9)} cells of the centre
 * and never has more than four trunks; a bigger plot makes the trees taller (up to 22), their crowns wider (up to
 * 2.3 radius), the spacing wider, and it gets more floor and more seats.
 */
public final class TreeBirchGrove implements Variant {
	private static final Wood BIRCH = new Wood(Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, b(Blocks.BIRCH_WOOD));
	private static final Leaves[] TONES = {
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.OAK_LEAVES).clumps(5),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.AZALEA_LEAVES).clumps(4),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.OAK_LEAVES).clumps(6),
	};
	/** The lowest layer of any leaf: 4 blocks of free air over the ground. */
	private static final int MIN_Y = 4;
	/** The lowest layer of a crown on a tree of the grove (bark shows below it). */
	private static final int CROWN_Y = 4;

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
		return 10;
	}

	@Override
	public int maxW() {
		return 48;
	}

	@Override
	public int maxD() {
		return 48;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xB12C_6A0BL);
		int w = p.w();
		int d = p.d();
		int m = Math.min(w, d);
		int maxTop = TreeKit.clamp(Math.min(p.h() - 2, 10 + (m - 6)), 8, 28);

		// ---- where the trunks stand: best-candidate sampling in a window round the centre, at least minCheb apart
		int maxN = m >= 11 ? 4 : m >= 8 ? 3 : 2;
		int n = 2 + r.nextInt(maxN - 1);
		if (m >= 10) {
			n = Math.max(n, 3);
		}
		int minCheb = m >= 14 ? 6 : m >= 10 ? 5 : 3;
		double half = Math.min((m - 1) / 2.0, 4.0 + Math.max(0, m - 9) * 0.35);
		double cu0 = (w - 1) / 2.0;
		double cv0 = (d - 1) / 2.0;
		int uLo = Math.max(1, (int) Math.ceil(cu0 - half));
		int uHi = Math.min(w - 2, (int) Math.floor(cu0 + half));
		int vLo = Math.max(1, (int) Math.ceil(cv0 - half));
		int vHi = Math.min(d - 2, (int) Math.floor(cv0 + half));
		List<int[]> pts = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int[] best = null;
			double bestScore = -1;
			for (int k = 0; k < 90; k++) {
				int u = uLo + r.nextInt(uHi - uLo + 1);
				int v = vLo + r.nextInt(vHi - vLo + 1);
				double dmin = 99;
				boolean ok = true;
				for (int[] q : pts) {
					int cheb = Math.max(Math.abs(q[0] - u), Math.abs(q[1] - v));
					if (cheb < minCheb) {
						ok = false;
						break;
					}
					dmin = Math.min(dmin, Math.hypot(q[0] - u, q[1] - v));
				}
				if (!ok) {
					continue;
				}
				double score = (pts.isEmpty() ? 5 : dmin) + r.nextDouble() * 1.6;
				if (score > bestScore) {
					bestScore = score;
					best = new int[] {u, v};
				}
			}
			if (best == null) {
				break;
			}
			pts.add(best);
		}
		if (pts.size() < 2) {
			pts.clear();
			pts.add(new int[] {1, 1});
			pts.add(new int[] {w - 2, d - 2});
		}
		n = pts.size();
		double mu = 0;
		double mv = 0;
		for (int[] q : pts) {
			mu += q[0] / (double) n;
			mv += q[1] / (double) n;
		}

		// ---- heights, clearly different (shuffled between the trunks), and each crown's vertical extent
		double[] fr = new double[n];
		for (int i = 0; i < n; i++) {
			fr[i] = 1.0 - 0.38 * i / (n - 1.0);
		}
		for (int i = n - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			double tmp = fr[i];
			fr[i] = fr[j];
			fr[j] = tmp;
		}
		int[] top = new int[n];
		double[] ry = new double[n];
		int[] cb = new int[n];
		int[] offU = new int[n];
		int[] offV = new int[n];
		for (int i = 0; i < n; i++) {
			top[i] = TreeKit.clamp((int) Math.round(maxTop * fr[i]) + r.nextInt(2) - (fr[i] > 0.99 ? 1 : 0), 8, maxTop);
			double want = TreeKit.clamp(0.4 * top[i], 2.4, 6.5);
			ry[i] = Math.min(want, (top[i] + 1 - CROWN_Y) / 2.0);
			cb[i] = (int) Math.ceil(top[i] + 1 - 2 * ry[i]);
			// a lean of one or two blocks inside the crown, away from the middle of the grove
			double ou = pts.get(i)[0] - mu;
			double ov = pts.get(i)[1] - mv;
			double ang = Math.hypot(ou, ov) < 1e-6 ? r.nextDouble() * Math.PI * 2 : Math.atan2(ov, ou) + (r.nextDouble() - 0.5) * 0.9;
			int amount = (m >= 10 && top[i] >= 12 && r.nextBoolean()) ? 2 : (r.nextInt(3) == 0 ? 0 : 1);
			offU[i] = (int) Math.round(Math.cos(ang) * amount * 1.2);
			offV[i] = (int) Math.round(Math.sin(ang) * amount * 1.2);
			int fu = pts.get(i)[0] + offU[i];
			int fv = pts.get(i)[1] + offV[i];
			if (fu < 1 || fu > w - 2 || fv < 1 || fv > d - 2) {
				offU[i] = 0;
				offV[i] = 0;
			}
		}
		// the tops keep the spacing too (a lean never closes the gap)
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j) {
					continue;
				}
				int cheb = Math.max(Math.abs(pts.get(i)[0] + offU[i] - pts.get(j)[0] - offU[j]),
						Math.abs(pts.get(i)[1] + offV[i] - pts.get(j)[1] - offV[j]));
				if (cheb < 3) {
					offU[i] = 0;
					offV[i] = 0;
					offU[j] = 0;
					offV[j] = 0;
				}
			}
		}

		// ---- the wood of every trunk (bare below the crown), then the crown of each
		int[][][] lead = new int[n][][];
		for (int i = 0; i < n; i++) {
			int u0 = pts.get(i)[0];
			int v0 = pts.get(i)[1];
			List<int[]> steps = new ArrayList<>();
			int su = Integer.signum(offU[i]);
			int sv = Integer.signum(offV[i]);
			int au = Math.abs(offU[i]);
			int av = Math.abs(offV[i]);
			int yS = Math.max(cb[i] - 1, MIN_Y);
			while (au > 0 || av > 0) {
				boolean useU = au >= av && au > 0;
				if (yS < top[i] - 1) {
					steps.add(new int[] {yS, useU ? su : 0, useU ? 0 : sv});
				}
				if (useU) {
					au--;
				} else {
					av--;
				}
				yS += 3;
			}
			lead[i] = column(t, u0, v0, top[i], steps);
		}
		int[] own = new int[w * d * t.h];
		for (int i = 0; i < n; i++) {
			// the gap to the nearest other trunk sets how wide this crown may be
			double nd = 99;
			for (int j = 0; j < n; j++) {
				if (j != i) {
					nd = Math.min(nd, Math.hypot(pts.get(i)[0] + offU[i] - pts.get(j)[0] - offU[j],
							pts.get(i)[1] + offV[i] - pts.get(j)[1] - offV[j]));
				}
			}
			double rMax = TreeKit.clamp(1.3 + (m - 6) * 0.09, 1.3, 2.2);
			double rr = TreeKit.clamp(Math.min(rMax * (0.88 + r.nextDouble() * 0.2), nd / 2.0 - 0.45), 1.15, 2.6);
			double cy = top[i] + 1 - ry[i];
			int cyi = (int) Math.round(cy);
			Leaves lv = TONES[i % TONES.length];
			int id = i + 1;
			double rz = rr * (0.9 + r.nextDouble() * 0.2);
			// one or two bare layers across the crown (a ring of sky with the white trunk showing): one free cell high, so
			// nobody can stand in it, and the tiers stay joined to the trunk
			int ng = ry[i] >= 3.6 ? 2 : ry[i] >= 2.4 ? 1 : 0;
			int[] skip = new int[ng];
			for (int g = 0; g < ng; g++) {
				skip[g] = (int) Math.round(cy + (ng == 1 ? -0.3 : g == 0 ? -0.35 : 0.5) * ry[i]);
			}
			oval(t, lead[i][0][cyi], cy, lead[i][1][cyi], rr, ry[i], rz, lv, id, 3 + 10 * i, skip);
			// the cap: leaves over the leader's last log and round it, so no pale log end shows
			int tu0 = lead[i][0][top[i]];
			int tv0 = lead[i][1][top[i]];
			BlockState cap = lv.a();
			t.leafOf(tu0, top[i] + 1, tv0, cap, id);
			int[][] nb4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] o : nb4) {
				t.leafOf(tu0 + o[0], top[i], tv0 + o[1], cap, id);
			}
			// a smaller oval on a one-block twig, lower down, on the taller trees (two on the tallest)
			int extra = top[i] >= 14 ? 2 : top[i] >= 10 ? 1 : 0;
			double side = r.nextDouble() * Math.PI * 2;
			for (int k = 0; k < extra; k++) {
				double ryL = ry[i] * (k == 0 ? 0.62 : 0.5);
				double cyL = cy - ry[i] * (k == 0 ? 1.0 : 1.5) - 0.2;
				if (cyL - ryL < CROWN_Y + 0.4) {
					continue;
				}
				int cL = (int) Math.round(cyL);
				int su = lead[i][0][cL];
				int sv = lead[i][1][cL];
				double ang = side + k * Math.PI + (r.nextDouble() - 0.5) * 0.5;
				int du = Math.abs(Math.cos(ang)) >= Math.abs(Math.sin(ang)) ? (Math.cos(ang) >= 0 ? 1 : -1) : 0;
				int dv = du == 0 ? (Math.sin(ang) >= 0 ? 1 : -1) : 0;
				int tu = su + du;
				int tv = sv + dv;
				if (tu < 1 || tu > w - 2 || tv < 1 || tv > d - 2) {
					tu = su - du;
					tv = sv - dv;
					if (tu < 1 || tu > w - 2 || tv < 1 || tv > d - 2) {
						continue;
					}
				}
				t.wood(tu, cL, tv, BIRCH.log(tu != su ? Direction.Axis.X : Direction.Axis.Z));
				oval(t, tu, cyL, tv, rr * 0.78, ryL, rz * 0.78, k % 2 == 0 ? lv.swap() : lv, id, 5 + 10 * i + k, skip);
			}
			claim(t, own, id);
		}
		gaps(t);
		t.prune();

		// ---- seats: a bench or a boulder where the plot gives room and clear sky
		int seats = (m >= 7 ? 1 : 0) + (m >= 18 ? 1 : 0);
		for (int k = 0; k < seats; k++) {
			seat(t, r, pts, mu, mv);
		}
		floor(t, pts, mu, mv, half);
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
	 * A slender 1×1 birch log from y = 0 to {@code top}, with a one-block jog at each {@code {y, du, dv}} step (a bark
	 * joint on the layer below). The last cell is bark, never a pale log end. Nothing but the trunk: no roots, no stubs.
	 */
	private static int[][] column(Vox t, int u0, int v0, int top, List<int[]> steps) {
		int[] lu = new int[t.h + 1];
		int[] lv = new int[t.h + 1];
		int cu = u0;
		int cv = v0;
		for (int y = 0; y <= Math.min(top, t.h - 1); y++) {
			for (int[] st : steps) {
				if (st[0] == y) {
					t.wood(cu + st[1], y - 1, cv + st[2], BIRCH.bark());
					cu += st[1];
					cv += st[2];
				}
			}
			lu[y] = cu;
			lv[y] = cv;
			t.wood(cu, y, cv, y == top ? BIRCH.bark() : BIRCH.log(Direction.Axis.Y));
		}
		for (int y = Math.min(top, t.h - 1) + 1; y <= t.h; y++) {
			lu[y] = cu;
			lv[y] = cv;
		}
		return new int[][] {lu, lv};
	}

	private static boolean contains(int[] a, int y) {
		for (int v : a) {
			if (v == y) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A vertical oval of leaves: an ellipsoid {@code rx × ry × rz} with a ragged skin and small single-cell holes (on
	 * every other layer, so two holes are never stacked) that let the trunk and the sky show. Never a ball and never
	 * a cube; nothing under {@link #MIN_Y}.
	 */
	private static void oval(Vox t, double cu, double cy, double cv, double rx, double ry, double rz, Leaves lv, int id,
			int salt, int[] skip) {
		for (int y = (int) Math.floor(cy - ry - 1); y <= (int) Math.ceil(cy + ry + 1); y++) {
			if (y < MIN_Y || y >= t.h || contains(skip, y)) {
				continue;
			}
			double dy = (y - cy) / ry;
			for (int v = (int) Math.floor(cv - rz - 1.5); v <= (int) Math.ceil(cv + rz + 1.5); v++) {
				for (int u = (int) Math.floor(cu - rx - 1.5); u <= (int) Math.ceil(cu + rx + 1.5); u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double q = du * du + dy * dy + dv * dv;
					double jit = (t.hash(u + salt * 13, y + salt * 7, v + salt * 3, 9) - 4) / 4.0 * 0.42;
					if (q > 1.0 + jit) {
						continue;
					}
					if (q > 0.18 && q < 0.97 && t.kind(u, y - 1, v) == TreeKit.LEAF && t.hash(u + salt, y, v + salt * 5, 4) < 1) {
						continue; // a hole of sky (never over another hole, so nobody can stand in it)
					}
					t.leafOf(u, y, v, TreeKit.tone(t, lv, u, y, v, dy, q, salt), id);
				}
			}
		}
	}

	/**
	 * Closes the small ledges: an empty cell with a solid cell under it, room to stand (two free cells) and a roof of
	 * leaves a block or two over it would be a shut-in pocket for the walking audit. It becomes a leaf.
	 */
	private static void gaps(Vox t) {
		BlockState lf = TONES[0].a();
		for (int pass = 0; pass < 2; pass++) {
			for (int y = MIN_Y; y < t.h - 2; y++) {
				for (int v = 0; v < t.d; v++) {
					for (int u = 0; u < t.w; u++) {
						if (!t.empty(u, y, v) || !t.empty(u, y + 1, v)) {
							continue;
						}
						byte below = t.kind(u, y - 1, v);
						if (below != TreeKit.LEAF && below != TreeKit.WOOD) {
							continue;
						}
						byte k2 = t.kind(u, y + 2, v);
						byte k3 = t.kind(u, y + 3, v);
						if (k2 == TreeKit.LEAF || k2 == TreeKit.WOOD || k3 == TreeKit.LEAF || k3 == TreeKit.WOOD) {
							t.leaf(u, y, v, lf);
						}
					}
				}
			}
		}
	}

	/**
	 * A birch bench (stripped-log ends, slab seat) or a mossy boulder on a free, open spot of the floor, the one
	 * nearest the middle of the grove (so the clearing there has a use).
	 */
	private static void seat(Vox t, Random r, List<int[]> trunks, double mu, double mv) {
		boolean bench = r.nextInt(3) != 0;
		int len = bench ? 3 : 2;
		int bu = -1;
		int bv = -1;
		boolean bAlong = true;
		double bd = 1e9;
		for (int tries = 0; tries < 80; tries++) {
			boolean alongU = r.nextBoolean();
			int u = 1 + r.nextInt(Math.max(1, t.w - 2 - (alongU ? len : 1)));
			int v = 1 + r.nextInt(Math.max(1, t.d - 2 - (alongU ? 1 : len)));
			int su = alongU ? len : 1;
			int sv = alongU ? 1 : len;
			boolean ok = true;
			for (int dv = -1; dv <= sv && ok; dv++) {
				for (int du = -1; du <= su && ok; du++) {
					int cu = u + du;
					int cv = v + dv;
					if (!t.in(cu, 0, cv)) {
						continue;
					}
					if (t.kind(cu, 0, cv) != TreeKit.EMPTY && t.kind(cu, 0, cv) != TreeKit.COVER) {
						ok = false;
					}
					for (int[] q : trunks) {
						if (Math.hypot(q[0] - cu, q[1] - cv) < 2.3) {
							ok = false;
						}
					}
					for (int y = 1; y < Math.min(t.h, 10) && ok; y++) {
						byte kk = t.kind(cu, y, cv);
						if (kk == TreeKit.LEAF || kk == TreeKit.WOOD || kk == TreeKit.FIXED) {
							ok = false;
						}
					}
				}
			}
			if (!ok) {
				continue;
			}
			double dist = Math.hypot(u + su / 2.0 - mu, v + sv / 2.0 - mv);
			if (dist < bd) {
				bd = dist;
				bu = u;
				bv = v;
				bAlong = alongU;
			}
		}
		if (bu < 0) {
			return;
		}
		int u = bu;
		int v = bv;
		boolean alongU = bAlong;
		if (bench) {
			// birch stairs facing the clearing, a row of slabs for the back
			Direction face;
			int bu2;
			int bv2;
			if (alongU) {
				face = mv > v ? Direction.SOUTH : Direction.NORTH;
				bu2 = 0;
				bv2 = face == Direction.SOUTH ? -1 : 1;
			} else {
				face = mu > u ? Direction.EAST : Direction.WEST;
				bu2 = face == Direction.EAST ? -1 : 1;
				bv2 = 0;
			}
			BlockState stair = b(Blocks.BIRCH_STAIRS).setValue(BlockStateProperties.HORIZONTAL_FACING, face);
			for (int i = 0; i < 3; i++) {
				int cu = alongU ? u + i : u;
				int cv = alongU ? v : v + i;
				t.fixed(cu, 0, cv, stair);
				t.fixed(cu + bu2, 0, cv + bv2, b(Blocks.BIRCH_SLAB));
			}
		} else {
			t.fixed(u, 0, v, b(Blocks.MOSSY_COBBLESTONE));
			t.fixed(alongU ? u + 1 : u, 0, alongU ? v : v + 1, b(Blocks.MOSSY_COBBLESTONE_SLAB));
			if (t.hash(u, 1, v, 2) == 0) {
				t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
			}
		}
	}

	private static BlockState tall(Block blk, boolean lower) {
		return blk.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	private static BlockState flower(Vox t, int u, int v) {
		return switch (t.hash(u, 7, v, 6)) {
			case 0 -> b(Blocks.LILY_OF_THE_VALLEY);
			case 1 -> b(Blocks.OXEYE_DAISY);
			case 2 -> b(Blocks.CORNFLOWER);
			case 3 -> b(Blocks.AZURE_BLUET);
			case 4 -> b(Blocks.WHITE_TULIP);
			default -> b(Blocks.LILY_OF_THE_VALLEY);
		};
	}

	/** A mossy forest floor in patches (moss and ferns, meadow, wild flowers, large ferns, quiet), mushrooms at the trunks. */
	private static void floor(Vox t, List<int[]> trunks, double mu, double mv, double half) {
		for (int v = 0; v < t.d; v++) {
			for (int u = 0; u < t.w; u++) {
				if (!t.empty(u, 0, v)) {
					continue;
				}
				double near = 99;
				for (int[] q : trunks) {
					near = Math.min(near, Math.hypot(q[0] - u, q[1] - v));
				}
				int roll = t.hash(u, 0, v, 100);
				// patches 3 across, shifted row by row so they do not form a grid
				int patch = t.hash(Math.floorDiv(u + t.hash(0, 1, Math.floorDiv(v, 3), 3), 3), 3, Math.floorDiv(v, 3), 5);
				// the middle of the grove is a quiet clearing, the edge is wilder
				if (Math.hypot(u - mu, v - mv) < Math.max(2.5, half * 0.6) && t.hash(u, 5, v, 3) != 0) {
					patch = 4;
				}
				BlockState s = null;
				boolean dbl = false;
				if (near <= 1.6) {
					s = roll < 52 ? b(Blocks.MOSS_CARPET) : roll < 66 ? b(Blocks.FERN) : roll < 82 ? b(Blocks.BROWN_MUSHROOM) : null;
				} else {
					switch (patch) {
						case 0 -> { // moss and ferns
							if (roll < 40) {
								s = b(Blocks.MOSS_CARPET);
							} else if (roll < 50) {
								s = b(Blocks.FERN);
							} else if (roll < 54) {
								s = tall(Blocks.LARGE_FERN, true);
								dbl = true;
							}
						}
						case 1 -> { // meadow: tall grass, short grass, a few flowers
							if (roll < 12) {
								s = tall(Blocks.TALL_GRASS, true);
								dbl = true;
							} else if (roll < 52) {
								s = b(Blocks.SHORT_GRASS);
							} else if (roll < 66) {
								s = b(Blocks.MOSS_CARPET);
							} else if (roll < 82) {
								s = flower(t, u, v);
							}
						}
						case 2 -> { // wild flowers
							if (roll < 46) {
								s = flower(t, u, v);
							} else if (roll < 62) {
								s = b(Blocks.SHORT_GRASS);
							} else if (roll < 80) {
								s = b(Blocks.MOSS_CARPET);
							}
						}
						case 3 -> { // big ferns
							if (roll < 10) {
								s = b(Blocks.FERN);
							} else if (roll < 18) {
								s = tall(Blocks.LARGE_FERN, true);
								dbl = true;
							} else if (roll < 56) {
								s = b(Blocks.MOSS_CARPET);
							} else if (roll < 70) {
								s = b(Blocks.SHORT_GRASS);
							}
						}
						default -> { // quiet: mostly open, for walking
							if (roll < 24) {
								s = b(Blocks.MOSS_CARPET);
							} else if (roll < 40) {
								s = b(Blocks.SHORT_GRASS);
							} else if (roll < 54) {
								s = flower(t, u, v);
							}
						}
					}
				}
				if (s == null) {
					continue;
				}
				if (dbl) {
					if (t.empty(u, 1, v)) {
						t.fixed(u, 0, v, s);
						t.fixed(u, 1, v, tall(s.getBlock(), false));
					}
				} else {
					t.cover(u, 0, v, s);
				}
			}
		}
	}
}
