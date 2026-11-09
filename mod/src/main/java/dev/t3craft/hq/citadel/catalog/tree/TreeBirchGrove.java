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
 * A grove of slender white birches. The trunks are bare, 1×1 birch logs with nothing on them below y 4 (no roots, no
 * stubs: you walk between them and under them); they stand at least three cells apart (two blocks of air), most of
 * them in small groups, straight and bare.
 *
 * <p>The crown is <em>not</em> a mass: a slim spire of leaves round the tip, and below it a few small, airy puffs of
 * light birch leaves, each on its own thin twig (one or two logs), turning from layer to layer, so the white trunk shows
 * almost all the way up. Leaves are birch (light, no biome tint) with a per-seed second tone (azalea, flowering azalea
 * or a little spruce); there is no oak. Nothing below y 4: you walk under it with four blocks of air.
 *
 * <p>The ground is a mossy forest floor in patches (moss, ferns, large ferns, tall grass, wild flowers, saplings,
 * mushrooms at the trunks). From 10 wide there is a <b>clearing</b> with a lantern post and one to three birch benches
 * (or a mossy boulder) facing it; from 12 wide a winding <b>senda</b> of stepping slabs leads in from the front; from
 * 12 wide there is an understory too (azalea bushes, from 14 fallen logs).
 *
 * <p>Size: 6–48 × 6–48 × 10+. Small plots hold two to four trunks; from 14 wide the count grows with the area (5 up to
 * 16), in two to four clumps with meadow between them, and the whole plot gets filled without closing it (the senda and
 * the clearing stay free of trunks).
 */
public final class TreeBirchGrove implements Variant {
	private static final Wood BIRCH = new Wood(Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, b(Blocks.BIRCH_WOOD));
	/** One palette per seed: light birch leaves plus a second, small tone. No oak (biome tint makes it dark). */
	private static final Leaves[] PAL = {
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.AZALEA_LEAVES).clumps(9),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.SPRUCE_LEAVES).clumps(14),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES).clumps(7),
			Leaves.of(Blocks.BIRCH_LEAVES, Blocks.AZALEA_LEAVES).clumps(4),
	};
	/** The lowest layer of any leaf: 4 blocks of free air over the ground. */
	private static final int MIN_Y = 4;
	private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

	private interface Valid {
		boolean test(int u, int v, int minCheb);
	}

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
		final int w = p.w();
		final int d = p.d();
		int m = Math.min(w, d);
		boolean big = m >= 14;
		int maxTop = TreeKit.clamp(Math.min(p.h() - 2, 10 + (m - 6)), 8, 28);
		int pal = r.nextInt(PAL.length);

		// ---- the clearing (a rest corner) and the senda that leads to it
		double cu0 = (w - 1) / 2.0;
		double cv0 = (d - 1) / 2.0;
		final double[] cl = {cu0, cv0, 2.5};
		if (m >= 10) {
			cl[0] = cu0 + (r.nextDouble() - 0.5) * 0.3 * w;
			cl[1] = cv0 + (r.nextDouble() - 0.35) * 0.28 * d;
			cl[2] = TreeKit.clamp(2.8 + (m - 10) * 0.1, 2.8, 5.2);
		}
		final boolean[] sd = new boolean[w * d];
		if (m >= 12) {
			senda(sd, r, w, d, cl, m);
		}

		// ---- where the trunks stand
		int mg = big ? 2 : 1;
		final int uLo = mg;
		final int uHi = w - 1 - mg;
		final int vLo = mg;
		final int vHi = d - 1 - mg;
		final List<int[]> pts = new ArrayList<>();
		int n;
		if (!big) {
			int maxN = m >= 11 ? 4 : m >= 8 ? 3 : 2;
			n = 2 + r.nextInt(maxN - 1);
			if (m >= 10) {
				n = Math.max(n, 3);
			}
		} else {
			n = TreeKit.clamp((int) Math.round((w - 4) * (d - 4) / (double) (36 + r.nextInt(24))), 5, 16);
		}
		final boolean hasClear = m >= 10;
		Valid valid = (u, v, minCheb) -> {
			if (u < uLo || u > uHi || v < vLo || v > vHi) {
				return false;
			}
			if (hasClear && Math.hypot(u - cl[0], v - cl[1]) < cl[2] + 0.6) {
				return false;
			}
			for (int dv = -2; dv <= 2; dv++) {
				for (int du = -2; du <= 2; du++) {
					int a = u + du;
					int bb = v + dv;
					if (a >= 0 && a < w && bb >= 0 && bb < d && sd[bb * w + a]) {
						return false;
					}
				}
			}
			for (int[] q : pts) {
				if (Math.max(Math.abs(q[0] - u), Math.abs(q[1] - v)) < minCheb) {
					return false;
				}
			}
			return true;
		};
		int minCheb = !big ? (m >= 10 ? 5 : 3) : 6;
		for (int i = 0; i < n; i++) {
			int[] best = null;
			boolean join = big && i > 0 && r.nextInt(10) < 7;
			if (join) {
				for (int k = 0; k < 80 && best == null; k++) {
					int[] a = pts.get(r.nextInt(pts.size()));
					double ang = r.nextDouble() * Math.PI * 2;
					double rad = 3.0 + r.nextDouble() * 1.8;
					int u = (int) Math.round(a[0] + Math.cos(ang) * rad);
					int v = (int) Math.round(a[1] + Math.sin(ang) * rad);
					if (valid.test(u, v, 3)) {
						best = new int[] {u, v};
					}
				}
			}
			for (int pass = 0; pass < 2 && best == null; pass++) {
				double bestScore = -1;
				int mc = pass == 0 ? minCheb : 3;
				if (pass == 1 && !big) {
					break;
				}
				for (int k = 0; k < 90; k++) {
					int u = uLo + r.nextInt(uHi - uLo + 1);
					int v = vLo + r.nextInt(vHi - vLo + 1);
					if (!valid.test(u, v, mc)) {
						continue;
					}
					double dmin = 99;
					for (int[] q : pts) {
						dmin = Math.min(dmin, Math.hypot(q[0] - u, q[1] - v));
					}
					double score = (pts.isEmpty() ? 5 : dmin) + r.nextDouble() * 1.6;
					if (score > bestScore) {
						bestScore = score;
						best = new int[] {u, v};
					}
				}
			}
			if (best != null) {
				pts.add(best);
			}
		}
		if (pts.size() < 2) {
			pts.clear();
			pts.add(new int[] {1, 1});
			pts.add(new int[] {w - 2, d - 2});
		}
		n = pts.size();

		// ---- heights (clearly different, shuffled) and the crown base of each
		double[] fr = new double[n];
		for (int i = 0; i < n; i++) {
			fr[i] = 1.0 - 0.5 * i / (n - 1.0);
		}
		for (int i = n - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			double tmp = fr[i];
			fr[i] = fr[j];
			fr[j] = tmp;
		}
		int[] top = new int[n];
		int[] cb = new int[n];
		int[] fu = new int[n];
		int[] fv = new int[n];
		for (int i = 0; i < n; i++) {
			top[i] = TreeKit.clamp((int) Math.round(maxTop * fr[i]) + r.nextInt(2) - (fr[i] > 0.99 ? 1 : 0), 7, maxTop);
			cb[i] = TreeKit.clamp((int) Math.round(top[i] * 0.48), MIN_Y, Math.max(MIN_Y, top[i] - 3));
			fu[i] = pts.get(i)[0];
			fv[i] = pts.get(i)[1];
		}
		// ---- the wood of every trunk (bare below the crown), then the crowns
		int[][][] lead = new int[n][][];
		for (int i = 0; i < n; i++) {
			lead[i] = column(t, pts.get(i)[0], pts.get(i)[1], top[i], new ArrayList<>());
		}
		int[] ids = {1};
		for (int i = 0; i < n; i++) {
			Leaves lv = r.nextInt(6) == 0 ? PAL[(pal + 1) % PAL.length] : PAL[pal];
			crown(t, r, i, lead[i], top[i], cb[i], lv, ids, fu, fv, w, d);
		}
		gaps(t, PAL[pal].a());
		unperch(t);
		t.prune();

		// ---- the rest corner: seats and a lantern post at the clearing, a few fallen logs, bushes, then the floor
		List<int[]> seated = new ArrayList<>();
		int seats = (m >= 7 ? 1 : 0) + (m >= 14 ? 1 : 0) + (m >= 26 ? 1 : 0);
		for (int k = 0; k < seats; k++) {
			seat(t, r, pts, cl[0], cl[1], sd, seated);
		}
		if (m >= 10) {
			lamp(t, r, cl, sd);
		}
		if (big) {
			fallenLogs(t, r, pts, cl, sd, w * d / 150);
		}
		if (m >= 12) {
			bushes(t, r, pts, cl, sd, PAL[pal], w * d / 36);
		}
		floor(t, pts, cl, sd);
		t.flush(c);
	}

	/** The senda: a winding strip (one or two cells wide) from the front edge to the clearing; marks {@code sd}. */
	private static void senda(boolean[] sd, Random r, int w, int d, double[] cl, int m) {
		double us = TreeKit.clamp(w / 2.0 + (r.nextDouble() - 0.5) * w * 0.5, 2, w - 3);
		double vs = 0;
		double ue = cl[0];
		double ve = cl[1];
		double len = Math.hypot(ue - us, ve - vs);
		double amp = TreeKit.clamp(1.0 + m * 0.07, 1.2, 3.2);
		double phase = r.nextDouble() * Math.PI * 2;
		double freq = r.nextBoolean() ? 1.0 : 1.5;
		double nu = -(ve - vs) / len;
		double nv = (ue - us) / len;
		int steps = (int) Math.ceil(len * 2);
		for (int s = 0; s <= steps; s++) {
			double q = s / (double) steps;
			double off = Math.sin(q * Math.PI * 2 * freq + phase) * amp * Math.sin(Math.PI * q);
			double pu = us + (ue - us) * q + nu * off;
			double pv = vs + (ve - vs) * q + nv * off;
			int iu = (int) Math.round(pu);
			int iv = (int) Math.round(pv);
			mark(sd, w, d, iu, iv);
			if (Math.abs(ue - us) < Math.abs(ve - vs)) {
				mark(sd, w, d, iu + 1, iv);
			} else {
				mark(sd, w, d, iu, iv + 1);
			}
		}
	}

	private static void mark(boolean[] sd, int w, int d, int u, int v) {
		if (u >= 1 && u < w - 1 && v >= 0 && v < d - 1) {
			sd[v * w + u] = true;
		}
	}

	/**
	 * A slim spire of leaves round the tip and, lower in the crown, small airy puffs of leaves each on its own thin
	 * twig (one or two logs), turning from layer to layer and away from the other trunks. The trunk shows between.
	 */
	private static void crown(Vox t, Random r, int i, int[][] lead, int top, int cb, Leaves lv, int[] ids, int[] fu, int[] fv,
			int w, int d) {
		int[] lu = lead[0];
		int[] lvv = lead[1];
		// the spire: a narrow oval over the tip, capped with leaves over the last log
		int idS = ids[0]++;
		double ryS = TreeKit.clamp(0.28 * top + 0.4, 2.2, 3.6);
		oval(t, lu[top], top + 0.6 - ryS * 0.5, lvv[top], 1.15 + r.nextDouble() * 0.3, ryS, 1.15 + r.nextDouble() * 0.3, lv, idS,
				3 + 10 * i);
		BlockState cap = lv.a();
		t.leafOf(lu[top], top + 1, lvv[top], cap, idS);
		for (int[] o : DIRS) {
			t.leafOf(lu[top] + o[0], top, lvv[top] + o[1], cap, idS);
		}
		// the puffs on twigs
		int y = cb + r.nextInt(2);
		int prev = -1;
		int k = 0;
		while (y <= top - 3) {
			int len = 1 + (t.hash(i, y, k, 2) == 0 ? 1 : 0) + (top >= 11 && r.nextInt(3) == 0 ? 1 : 0);
			len = Math.min(len, 2);
			int bestDir = -1;
			double bestScore = -1e9;
			for (int dr = 0; dr < 4; dr++) {
				int cu = lu[y] + DIRS[dr][0] * (len + 1);
				int cv = lvv[y] + DIRS[dr][1] * (len + 1);
				double near = 6;
				for (int j = 0; j < fu.length; j++) {
					if (j != i) {
						near = Math.min(near, Math.hypot(cu - fu[j], cv - fv[j]));
					}
				}
				double score = near + r.nextDouble() * 1.5 - (dr == prev ? 1.4 : 0);
				if (cu < 1 || cu > w - 2 || cv < 1 || cv > d - 2) {
					score -= 5;
				}
				if (score > bestScore) {
					bestScore = score;
					bestDir = dr;
				}
			}
			int du = DIRS[bestDir][0];
			int dv = DIRS[bestDir][1];
			// the twig: thin birch fence posts (a fence cannot be stood on), joined to the trunk and to each other
			for (int s = 1; s <= len; s++) {
				BlockState fe = b(Blocks.BIRCH_FENCE);
				fe = side(fe, -du, -dv, true);
				fe = side(fe, du, dv, s < len);
				t.wood(lu[y] + du * s, y, lvv[y] + dv * s, fe);
			}
			double rx = 1.15 + r.nextDouble() * 0.35;
			double ry = 1.0 + r.nextDouble() * 0.7;
			oval(t, lu[y] + du * (len + 1), y + r.nextInt(2) * 0.5, lvv[y] + dv * (len + 1), rx, ry, rx * (0.9 + r.nextDouble() * 0.2),
					lv, ids[0]++, 5 + 10 * i + k);
			// now and then a tiny tuft right against the trunk on another side
			if (r.nextInt(3) == 0 && y + 1 <= top - 3) {
				int o2 = (bestDir + 1 + r.nextInt(3)) % 4;
				oval(t, lu[y + 1] + DIRS[o2][0], y + 1, lvv[y + 1] + DIRS[o2][1], 0.8, 0.9, 0.8, lv, ids[0]++, 7 + 10 * i + k);
			}
			prev = bestDir;
			y += 2 + (r.nextInt(3) == 0 ? 1 : 0);
			k++;
		}
	}

	/** Sets the fence side that faces the local step {@code (du, dv)} (+u east, +v south, the front is north). */
	private static BlockState side(BlockState st, int du, int dv, boolean on) {
		if (du > 0) {
			return st.setValue(BlockStateProperties.EAST, on);
		} else if (du < 0) {
			return st.setValue(BlockStateProperties.WEST, on);
		} else if (dv > 0) {
			return st.setValue(BlockStateProperties.SOUTH, on);
		}
		return st.setValue(BlockStateProperties.NORTH, on);
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

	/** The leaf of a cell: the dominant light birch, with the second tone in pairs here and there. */
	private static BlockState leafAt(Vox t, Leaves lv, int u, int y, int v, int salt) {
		int pct = 100 / Math.max(2, lv.clumpOneIn());
		return t.hash(u + salt, y, Math.floorDiv(v, 2), 100) < pct ? lv.b() : lv.a();
	}

	/**
	 * A small airy oval of leaves: an ellipsoid {@code rx × ry × rz} with a ragged skin, thinned on the outside and with
	 * single-cell holes (never over another hole) that let the trunk and the sky show. Nothing under {@link #MIN_Y}.
	 */
	private static void oval(Vox t, double cu, double cy, double cv, double rx, double ry, double rz, Leaves lv, int id, int salt) {
		for (int y = (int) Math.floor(cy - ry - 1); y <= (int) Math.ceil(cy + ry + 1); y++) {
			if (y < MIN_Y || y >= t.h) {
				continue;
			}
			double dy = (y - cy) / ry;
			for (int v = (int) Math.floor(cv - rz - 1.5); v <= (int) Math.ceil(cv + rz + 1.5); v++) {
				for (int u = (int) Math.floor(cu - rx - 1.5); u <= (int) Math.ceil(cu + rx + 1.5); u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double q = du * du + dy * dy + dv * dv;
					double jit = (t.hash(u + salt * 13, y + salt * 7, v + salt * 3, 9) - 4) / 4.0 * 0.5;
					if (q > 1.0 + jit) {
						continue;
					}
					if (q > 0.5 && t.hash(u + salt * 5, y, v + salt, 100) < 16) {
						continue; // thinned skin
					}
					if (q > 0.18 && q < 0.97 && t.kind(u, y - 1, v) == TreeKit.LEAF && t.hash(u + salt, y, v + salt * 5, 4) < 1) {
						continue; // a hole of sky (never over another hole, so nobody can stand in it)
					}
					t.leafOf(u, y, v, leafAt(t, lv, u, y, v, salt), id);
				}
			}
		}
	}

	/**
	 * Closes the small ledges: an empty cell with a solid cell under it, room to stand (two free cells) and a roof of
	 * leaves a block or two over it would be a shut-in pocket for the walking audit. It becomes a leaf.
	 */
	private static void gaps(Vox t, BlockState lf) {
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
	 * Nobody can reach a ledge in the crowns, and the walking audit flags a standable, unreached cell with anything
	 * within 8 blocks over it. So a leaf that is the top of its column, with two free cells over it and something
	 * higher up in that column, is taken away (and what that exposes below, the same way).
	 */
	private static void unperch(Vox t) {
		for (int y = t.h - 3; y >= MIN_Y; y--) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y - 1, v) != TreeKit.LEAF || !t.empty(u, y, v) || !t.empty(u, y + 1, v)) {
						continue;
					}
					boolean roof = false;
					for (int k = y + 2; k <= y + 9 && !roof; k++) {
						byte kk = t.kind(u, k, v);
						roof = kk == TreeKit.LEAF || kk == TreeKit.WOOD || kk == TreeKit.FIXED;
					}
					if (roof) {
						t.clear(u, y - 1, v);
					}
				}
			}
		}
	}

	/** True when the cell is free ground (nothing, or a plant) with no trunk within {@code clear} and not on the senda. */
	private static boolean freeGround(Vox t, int u, int v, List<int[]> trunks, double clear, boolean[] sd) {
		if (!t.in(u, 0, v) || sd[v * t.w + u]) {
			return false;
		}
		byte k = t.kind(u, 0, v);
		if (k != TreeKit.EMPTY && k != TreeKit.COVER) {
			return false;
		}
		for (int[] q : trunks) {
			if (Math.hypot(q[0] - u, q[1] - v) < clear) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A birch bench (stripped-log ends, slab seat) or a mossy boulder on a free, open spot of the floor, the one
	 * nearest the clearing (so it has a use), at least 4 from the seats already there.
	 */
	private static void seat(Vox t, Random r, List<int[]> trunks, double mu, double mv, boolean[] sd, List<int[]> placed) {
		boolean bench = r.nextInt(4) != 0;
		int len = bench ? 3 : 2;
		int bu = -1;
		int bv = -1;
		boolean bAlong = true;
		double bd = 1e9;
		for (int tries = 0; tries < 120; tries++) {
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
					if (sd[cv * t.w + cu] && du >= 0 && du < su && dv >= 0 && dv < sv) {
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
			double ccu = u + su / 2.0;
			double ccv = v + sv / 2.0;
			for (int[] q : placed) {
				if (Math.hypot(q[0] - ccu, q[1] - ccv) < 5) {
					ok = false;
				}
			}
			if (!ok) {
				continue;
			}
			double dist = Math.hypot(ccu - mu, ccv - mv);
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
		placed.add(new int[] {u, v});
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

	/** A birch fence post with a lantern on it at the edge of the clearing, so the rest corner has light. */
	private static void lamp(Vox t, Random r, double[] cl, boolean[] sd) {
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int k = 0; k < 24; k++) {
			double ang = a0 + k * 0.52;
			int u = (int) Math.round(cl[0] + Math.cos(ang) * (cl[2] - 0.6));
			int v = (int) Math.round(cl[1] + Math.sin(ang) * (cl[2] - 0.6));
			if (!t.in(u, 3, v) || sd[v * t.w + u]) {
				continue;
			}
			boolean free = true;
			for (int y = 0; y <= 3; y++) {
				byte kk = t.kind(u, y, v);
				if (kk != TreeKit.EMPTY && !(y == 0 && kk == TreeKit.COVER)) {
					free = false;
				}
			}
			// not hard against a seat
			for (int dv = -1; dv <= 1 && free; dv++) {
				for (int du = -1; du <= 1; du++) {
					if (t.kind(u + du, 0, v + dv) == TreeKit.FIXED) {
						free = false;
					}
				}
			}
			if (!free) {
				continue;
			}
			// the post, and an arm towards the clearing with the lantern hanging from it (a fence top cannot be stood on)
			int au = Math.abs(cl[0] - u) >= Math.abs(cl[1] - v) ? (int) Math.signum(cl[0] - u) : 0;
			int av = au == 0 ? (int) Math.signum(cl[1] - v) : 0;
			if (au == 0 && av == 0) {
				au = 1;
			}
			if (!t.in(u + au, 3, v + av) || t.kind(u + au, 2, v + av) != TreeKit.EMPTY || t.kind(u + au, 3, v + av) != TreeKit.EMPTY) {
				au = -au;
				av = -av;
			}
			if (!t.in(u + au, 3, v + av) || t.kind(u + au, 2, v + av) != TreeKit.EMPTY || t.kind(u + au, 3, v + av) != TreeKit.EMPTY) {
				continue;
			}
			t.fixed(u, 0, v, b(Blocks.BIRCH_FENCE));
			t.fixed(u, 1, v, b(Blocks.BIRCH_FENCE));
			t.fixed(u, 2, v, b(Blocks.BIRCH_FENCE));
			t.fixed(u, 3, v, side(b(Blocks.BIRCH_FENCE), au, av, true));
			t.fixed(u + au, 3, v + av, side(b(Blocks.BIRCH_FENCE), -au, -av, true));
			t.fixed(u + au, 2, v + av, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
			return;
		}
	}

	/** A few fallen birch logs (two or three long, a little moss on them) well away from the senda, the clearing and the trunks. */
	private static void fallenLogs(Vox t, Random r, List<int[]> trunks, double[] cl, boolean[] sd, int count) {
		int placed = 0;
		for (int tries = 0; tries < 160 && placed < count; tries++) {
			int len = 2 + r.nextInt(2);
			boolean alongU = r.nextBoolean();
			int u = 1 + r.nextInt(Math.max(1, t.w - 2 - (alongU ? len : 1)));
			int v = 1 + r.nextInt(Math.max(1, t.d - 2 - (alongU ? 1 : len)));
			boolean ok = true;
			for (int i = -1; i <= len && ok; i++) {
				for (int j = -1; j <= 1 && ok; j++) {
					int cu = alongU ? u + i : u + j;
					int cv = alongU ? v + j : v + i;
					if (!t.in(cu, 0, cv)) {
						continue;
					}
					if (sd[cv * t.w + cu] || (t.kind(cu, 0, cv) != TreeKit.EMPTY && t.kind(cu, 0, cv) != TreeKit.COVER)) {
						ok = false;
					}
					if (Math.hypot(cu - cl[0], cv - cl[1]) < cl[2] + 1) {
						ok = false;
					}
					for (int[] q : trunks) {
						if (Math.hypot(q[0] - cu, q[1] - cv) < 2.2) {
							ok = false;
						}
					}
				}
			}
			if (!ok) {
				continue;
			}
			Direction.Axis ax = alongU ? Direction.Axis.X : Direction.Axis.Z;
			for (int i = 0; i < len; i++) {
				int cu = alongU ? u + i : u;
				int cv = alongU ? v : v + i;
				t.fixed(cu, 0, cv, BIRCH.log(ax));
				if (t.hash(cu, 1, cv, 3) == 0) {
					t.fixed(cu, 1, cv, b(Blocks.MOSS_CARPET));
				} else if (t.hash(cu, 2, cv, 6) == 0) {
					t.fixed(cu, 1, cv, b(Blocks.BROWN_MUSHROOM));
				}
			}
			placed++;
		}
	}

	/** Azalea-leaf bushes of one or two blocks in the understory: some with a flowering top. Never on the senda or the clearing. */
	private static void bushes(Vox t, Random r, List<int[]> trunks, double[] cl, boolean[] sd, Leaves lv, int count) {
		for (int k = 0; k < count; k++) {
			int u = 1 + r.nextInt(t.w - 2);
			int v = 1 + r.nextInt(t.d - 2);
			if (!freeGround(t, u, v, trunks, 2.4, sd) || Math.hypot(u - cl[0], v - cl[1]) < cl[2] + 0.8) {
				continue;
			}
			boolean flowering = r.nextInt(4) == 0;
			t.fixed(u, 0, v, flowering ? b(Blocks.FLOWERING_AZALEA_LEAVES) : lv.b());
			if (r.nextInt(3) == 0) {
				t.fixed(u, 1, v, lv.a());
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

	/** A stepping slab of the senda: mostly mossy cobble and stone, a cell now and then left to grass. */
	private static BlockState stepping(Vox t, int u, int v) {
		int h = t.hash(u, 0, v, 8);
		return switch (h) {
			case 0, 1, 2 -> b(Blocks.MOSSY_COBBLESTONE_SLAB);
			case 3, 4 -> b(Blocks.STONE_SLAB);
			case 5 -> b(Blocks.ANDESITE_SLAB);
			case 6 -> b(Blocks.COBBLESTONE_SLAB);
			default -> null;
		};
	}

	/**
	 * A mossy forest floor in patches (moss and ferns, meadow, wild flowers, large ferns, quiet), mushrooms at the
	 * trunks, a quiet clearing and the stepping-stone senda.
	 */
	private static void floor(Vox t, List<int[]> trunks, double[] cl, boolean[] sd) {
		for (int v = 0; v < t.d; v++) {
			for (int u = 0; u < t.w; u++) {
				if (!t.empty(u, 0, v)) {
					continue;
				}
				if (sd[v * t.w + u]) {
					BlockState st = stepping(t, u, v);
					if (st != null) {
						t.fixed(u, 0, v, st);
					}
					continue;
				}
				boolean besideSenda = false;
				for (int dv = -1; dv <= 1 && !besideSenda; dv++) {
					for (int du = -1; du <= 1; du++) {
						int a = u + du;
						int bb = v + dv;
						if (a >= 0 && a < t.w && bb >= 0 && bb < t.d && sd[bb * t.w + a]) {
							besideSenda = true;
							break;
						}
					}
				}
				double near = 99;
				for (int[] q : trunks) {
					near = Math.min(near, Math.hypot(q[0] - u, q[1] - v));
				}
				int roll = t.hash(u, 0, v, 100);
				// patches 3 across, shifted row by row so they do not form a grid
				int patch = t.hash(Math.floorDiv(u + t.hash(0, 1, Math.floorDiv(v, 3), 3), 3), 3, Math.floorDiv(v, 3), 5);
				// the clearing is quiet, the edge is wilder
				if (Math.hypot(u - cl[0], v - cl[1]) < cl[2] && t.hash(u, 5, v, 3) != 0) {
					patch = 4;
				}
				BlockState s = null;
				boolean dbl = false;
				if (besideSenda) {
					s = roll < 30 ? b(Blocks.MOSS_CARPET) : roll < 40 ? b(Blocks.SHORT_GRASS) : null;
				} else if (near <= 1.6) {
					s = roll < 52 ? b(Blocks.MOSS_CARPET) : roll < 66 ? b(Blocks.FERN) : roll < 82 ? b(Blocks.BROWN_MUSHROOM) : null;
				} else {
					switch (patch) {
						case 0 -> { // moss and ferns, a young birch now and then
							if (roll < 40) {
								s = b(Blocks.MOSS_CARPET);
							} else if (roll < 50) {
								s = b(Blocks.FERN);
							} else if (roll < 54) {
								s = tall(Blocks.LARGE_FERN, true);
								dbl = true;
							} else if (roll < 56) {
								s = b(Blocks.BIRCH_SAPLING);
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
