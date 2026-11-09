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
 * The weeping cherry (<i>shidarezakura</i>), an umbrella or a fountain, never a dome: a trunk that is straight to
 * y = 3 (so you walk up to it) and then bows to one side one block at a time (the arch), a small flat cap of blossom on
 * its top, and 3–5 dark limbs that leave the cap, rise a block or two, curve over and run out almost level. Each limb
 * carries a roof of cherry leaves and, on its outer half, a flank on each side; from that ribbon hang the <b>curtains</b>:
 * single strands of leaves, never two side by side (a lace, not a wall), each with its own length. Most stop one block
 * above the grass, some touch it, some are stubs that end at y = 4 or higher (3 blocks of headroom), and some columns
 * have none, so every curtain has a ragged hem and gaps. One limb in a big gap grows a twig with its own curtain. Nothing
 * hangs closer than two blocks to the leader or to the trunk's foot, and two doors (2+ wide, nothing below y = 4) are
 * carved through the skirt from the trunk to the edge, so you can walk between the curtains to the trunk. Everything
 * hangs from wood or from a leaf joined to wood ({@code prune}); fallen petals lie thick under the curtains and thin
 * out beyond. Seeds change the height, the bow, the number and spread of the limbs, the twig and every strand.
 *
 * <p>Size: 6–9 × 6–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeCherryWeeping implements Variant {
	/** Dark cherry wood on all sides (no pale log ends): the limbs read against the pink blossom and the sky. */
	private static final TreeKit.Wood LIMB = new TreeKit.Wood(Blocks.CHERRY_WOOD, Blocks.CHERRY_WOOD, TreeKit.CHERRY.rootTip());

	/** The lowest y a limb or a leaf of the crown may take: 3 blocks of headroom over every cell you can walk. */
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

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x5D1A);
		int w = p.w();
		int d = p.d();
		int mn = Math.min(w, d);
		int H = Math.min(p.h(), 10 + r.nextInt(4)); // 10..13, never over the plot
		int yTop = Math.max(5, H - 3); // the fork: the leader ends here and the crown (3 layers, to y = H - 1) starts

		// ---- trunk: 2×2 root flare, a leader straight to y = 3, then it bows one block at a time; the foot moves against the bow
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		double leanAng = r.nextDouble() * Math.PI * 2;
		double amp = 0.5 + r.nextDouble() * 0.9; // 0 or 1 cell of bow on each axis (at most two steps, in the last two layers)
		double leanU = Math.cos(leanAng) * amp;
		double leanV = Math.sin(leanAng) * amp;
		int bu = TreeKit.base(w, ou); // the foot is in the middle of the plot; the bow at the top moves the crown at most one cell
		int bv = TreeKit.base(d, ov);
		TreeKit.trunk(t, TreeKit.CHERRY, bu, bv, 0, 3, ou, ov, 0, 0); // straight: the shoulder is only the root level
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 4, 1, -1, 13); // four short roots: a flare, not a lying trunk
		int cu = bu + ou;
		int cv = bv + ov;
		int tU = TreeKit.clamp(bu + ou + (int) Math.round(leanU), 0, w - 1);
		int tV = TreeKit.clamp(bv + ov + (int) Math.round(leanV), 0, d - 1);
		int steps = Math.abs(tU - cu) + Math.abs(tV - cv);
		int done = 0;
		for (int y = 4; y <= yTop; y++) {
			if (done < steps && y >= yTop - steps + 1 + done) { // the bow sits right under the cap, which covers every ledge it makes
				int du = Integer.signum(tU - cu);
				int dv = Integer.signum(tV - cv);
				if (du != 0 && dv != 0) {
					if ((y & 1) == 0) {
						dv = 0;
					} else {
						du = 0;
					}
				}
				t.wood(cu + du, y - 1, cv + dv, TreeKit.CHERRY.bark()); // joins the old and the new column
				t.leaf(cu, y, cv, tone(t, cu, y, cv)); // a tuft of blossom on the stub, so there is no ledge to stand on
				t.leaf(cu, y + 1, cv, tone(t, cu, y + 1, cv)); // and a leaf over the tuft: under the cap nothing is left to stand on
				cu += du;
				cv += dv;
				done++;
			}
			t.wood(cu, y, cv, y == yTop ? TreeKit.CHERRY.bark() : TreeKit.CHERRY.log(Direction.Axis.Y));
		}
		double bcu = bu + 0.5; // centre of the 2×2 foot
		double bcv = bv + 0.5;
		int lu = cu;
		int lv = cv;

		// ---- the cap: three thin layers of blossom round the top of the leader (an umbrella hub, not a ball)
		double r1 = mn >= 8 ? 2.0 : 1.6;
		for (int dy = 0; dy <= 2; dy++) {
			int y = yTop + dy;
			if (y >= H) {
				break;
			}
			double rr = dy == 1 ? r1 : dy == 0 ? r1 * 0.62 : r1 * 0.55;
			for (int v = lv - 3; v <= lv + 3; v++) {
				for (int u = lu - 3; u <= lu + 3; u++) {
					double edge = rr + (t.hash(u, y, v, 5) - 2) * 0.18; // ragged rim
					if (Math.hypot(u - lu, v - lv) <= edge) {
						t.leaf(u, y, v, tone(t, u, y, v));
					}
				}
			}
		}

		// ---- limbs: rise out of the cap, curve over, run out almost level; the first one continues the bow of the trunk
		int n = 4 + (mn >= 8 ? 1 : 0) + r.nextInt(2); // 4–6 limbs
		double[] ang = new double[n];
		double a0 = leanAng + (r.nextDouble() - 0.5) * 0.5;
		for (int i = 0; i < n; i++) {
			ang[i] = a0 + i * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.55; // uneven, but always in order
		}
		double[] gap = new double[n];
		int widest = 0;
		for (int i = 0; i < n; i++) {
			gap[i] = (i + 1 < n ? ang[i + 1] : ang[0] + Math.PI * 2) - ang[i];
			if (gap[i] > gap[widest]) {
				widest = i;
			}
		}
		boolean twig = mn >= 7 && gap[widest] > 1.9;

		List<List<int[]>> limbs = new ArrayList<>();
		List<double[]> dirs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double ex = Math.cos(ang[i]);
			double ez = Math.sin(ang[i]);
			double reach = reach(t, lu, lv, ex, ez, mn, i == 0 ? 1.0 : 0.82 + 0.18 * r.nextDouble());
			int ay = Math.min(H - 2, yTop + 1 + r.nextInt(2));
			int ty = TreeKit.clamp(ay - 1 - r.nextInt(2), CLEAR, ay - 1); // a long, low arch: the curtains do the falling
			int au = (int) Math.round(lu + ex * reach * 0.45);
			int av = (int) Math.round(lv + ez * reach * 0.45);
			int tu = (int) Math.round(lu + ex * reach);
			int tv = (int) Math.round(lv + ez * reach);
			List<int[]> cells = new ArrayList<>();
			cells.addAll(TreeKit.branch(t, LIMB, new int[] {lu, yTop, lv}, new int[] {au, ay, av}, false));
			cells.addAll(TreeKit.branch(t, LIMB, new int[] {au, ay, av}, new int[] {tu, ty, tv}, false));
			limbs.add(cells);
			dirs.add(new double[] {ex, ez});
			if (twig && i == widest) {
				// a twig into the widest gap, from the apex, with its own curtain
				double ta = ang[i] + 0.3 * gap[i];
				double tx = Math.cos(ta);
				double tz = Math.sin(ta);
				double tr = reach(t, lu, lv, tx, tz, mn, 0.85);
				int tty = TreeKit.clamp(ty - r.nextInt(2), CLEAR, ay - 1);
				int wu = (int) Math.round(lu + tx * tr);
				int wv = (int) Math.round(lv + tz * tr);
				limbs.add(new ArrayList<>(TreeKit.branch(t, LIMB, new int[] {au, ay, av}, new int[] {wu, tty, wv}, false)));
				dirs.add(new double[] {tx, tz});
			}
		}

		// ---- ribbons of leaves on the limbs; the columns they take are where the curtains may hang
		byte[] hang = new byte[w * d]; // 2 = the column of a limb, 1 = a flank (flanks only hang in a lace: never face to face)
		double inner = mn >= 8 ? 1.6 : 1.0; // nothing hangs nearer than this to the leader or to the foot (less on small plots)
		for (int i = 0; i < limbs.size(); i++) {
			int side = t.hash(i, 3, 9, 2) == 0 ? 1 : -1;
			ribbon(t, limbs.get(i), hang, lu, lv, inner, side, dirs.get(i)[0], dirs.get(i)[1]);
		}

		// ---- curtains: single strands (never face to face) from the lowest block of each hanging column to the grass
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (hang[v * w + u] == 0 || ((u + v) & 1) != 0 || Math.hypot(u - lu, v - lv) < inner + 0.3
						|| Math.hypot(u - bcu, v - bcv) < inner + 0.1) {
					continue; // nothing hangs close to the leader or to the foot of the trunk: that is the way in
				}
				int y0 = -1;
				for (int y = CLEAR; y < H; y++) {
					byte k = t.kind(u, y, v);
					if (k == TreeKit.WOOD || k == TreeKit.LEAF) {
						y0 = y;
						break;
					}
				}
				if (y0 < 0 || t.kind(u, y0 - 1, v) != TreeKit.EMPTY) {
					continue;
				}
				int cls = t.hash(u, 5, v, 100);
				int bottom;
				if (cls < 15) {
					continue; // no strand here: a window
				} else if (cls < 50) {
					if (y0 - 1 < CLEAR) {
						continue;
					}
					bottom = Math.max(CLEAR, y0 - 2 - t.hash(u, 6, v, 5)); // a stub: 3 blocks of headroom under it, even over a root
				} else if (cls < 56) {
					bottom = 0; // touches the grass
				} else {
					bottom = 1; // stops a block above the grass
				}
				bottom = Math.min(bottom, y0 - 1);
				for (int y = y0 - 1; y >= bottom; y--) {
					if (!t.empty(u, y, v)) {
						break;
					}
					t.leaf(u, y, v, y == bottom && t.hash(u, y, v, 100) < 30 ? TreeKit.SAKURA.b() : TreeKit.SAKURA.a());
				}
			}
		}

		// ---- two doors through the skirt, from the foot of the trunk out through the widest gaps (2+ wide, nothing below y = 4)
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (x, y) -> Double.compare(gap[y], gap[x]));
		for (int k = 0; k < Math.min(2, n); k++) {
			int i = order[k];
			double da = ang[i] + (twig && i == widest ? 0.66 : 0.5) * gap[i];
			double dx = Math.cos(da);
			double dz = Math.sin(da);
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					double au = u - bcu;
					double av = v - bcv;
					if (au * dx + av * dz > 0.5 && Math.abs(-au * dz + av * dx) <= 1.05) {
						for (int y = 0; y < CLEAR; y++) {
							if (t.kind(u, y, v) == TreeKit.LEAF) {
								t.clear(u, y, v);
							}
						}
					}
				}
			}
		}

		// ---- fallen petals: thick at the foot and under the curtains, thinning out, a little moss between
		TreeKit.ground(t, (w - 1) / 2.0, (d - 1) / 2.0, mn * 0.5 + 1.6, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			int pet = (int) (72 - 40 * k) + (hang[v * w + u] != 0 ? 18 : 0);
			if (hsh < pet) {
				return TreeKit.petals(1 + t.hash(u, 1, v, k < 0.55 ? 4 : 2), t.hash(u, 2, v, 4));
			}
			return hsh < pet + 8 ? Blocks.MOSS_CARPET.defaultBlockState() : null;
		});
		t.prune();
		t.flush(c);
	}

	/** How far a limb goes: the room to the edge of the box (the flanks need a cell), at most half the span. */
	private static double reach(Vox t, int lu, int lv, double ex, double ez, int mn, double k) {
		return TreeKit.clamp(TreeKit.room(t, lu, lv, ex, ez, 0.4), 2.2, mn * 0.5 + 0.3) * k;
	}

	/**
	 * Leaves along a limb, away from the cap: a roof of leaves over every cell (no ledge to stand on under the crown),
	 * a flank on one side of the inner half, a flank on both sides of the outer half, and marks the columns as hanging.
	 */
	private static void ribbon(Vox t, List<int[]> cells, byte[] hang, int lu, int lv, double inner, int side, double ex,
			double ez) {
		boolean tangU = Math.abs(ez) > Math.abs(ex);
		int su = tangU ? side : 0;
		int sv = tangU ? 0 : side;
		int n = cells.size();
		for (int k = 0; k < n; k++) {
			int[] q = cells.get(k);
			if (Math.hypot(q[0] - lu, q[2] - lv) < 0.9) {
				continue; // inside the cap
			}
			t.leaf(q[0], q[1] + 1, q[2], tone(t, q[0], q[1] + 1, q[2]));
			if (Math.hypot(q[0] - lu, q[2] - lv) < inner) {
				continue;
			}
			mark(t, hang, q[0], q[2], (byte) 2);
			boolean outer = k >= n / 2;
			if (t.hash(q[0], q[1] + 3, q[2], 10) < (outer ? 7 : 3)) {
				t.leaf(q[0] + su, q[1], q[2] + sv, tone(t, q[0] + su, q[1], q[2] + sv));
				mark(t, hang, q[0] + su, q[2] + sv, (byte) 1);
			}
			if (outer && t.hash(q[0], q[1] + 5, q[2], 10) < 7) {
				t.leaf(q[0] - su, q[1], q[2] - sv, tone(t, q[0] - su, q[1], q[2] - sv));
				mark(t, hang, q[0] - su, q[2] - sv, (byte) 1);
			}
		}
		// the tip always has both flanks: the curtain is widest where the limb ends
		int[] tip = cells.get(n - 1);
		for (int s = -1; s <= 1; s += 2) {
			t.leaf(tip[0] + su * s, tip[1], tip[2] + sv * s, tone(t, tip[0] + su * s, tip[1], tip[2] + sv * s));
			mark(t, hang, tip[0] + su * s, tip[2] + sv * s, (byte) 1);
		}
		mark(t, hang, tip[0], tip[2], (byte) 2);
	}

	private static void mark(Vox t, byte[] hang, int u, int v, byte level) {
		if (u >= 0 && u < t.w && v >= 0 && v < t.d) {
			hang[v * t.w + u] = (byte) Math.max(hang[v * t.w + u], level);
		}
	}

	/** Cherry pink with a few azalea leaves (about one in eleven). */
	private static BlockState tone(Vox t, int u, int y, int v) {
		return t.hash(u, y + 17, v, 100) < 9 ? TreeKit.SAKURA.b() : TreeKit.SAKURA.a();
	}

	/** The compass step of {@link TreeKit#OCT} closest to the direction {@code (dx, dz)}. */
	private static int octant(double dx, double dz) {
		int best = 0;
		double bd = -1e9;
		for (int k = 0; k < 8; k++) {
			int[] o = TreeKit.OCT[k];
			double dot = (o[0] * dx + o[1] * dz) / Math.hypot(o[0], o[1]);
			if (dot > bd) {
				bd = dot;
				best = k;
			}
		}
		return best;
	}
}
