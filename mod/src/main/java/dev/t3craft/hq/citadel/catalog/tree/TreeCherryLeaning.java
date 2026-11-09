package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;

/**
 * The leaning cherry, the one that grows out over water or down a slope. A short bare stump at one side of the
 * plot (a 2×2 foot one block high, then a 1×1 column), then the trunk leaves it <b>face-connected</b> (every log shares a
 * face with the next, no diagonal steps) at 25–45° or almost flat, reaches an <b>elbow</b>, and from there turns
 * steeply up to the tip. One or two limbs <b>rise again</b> out of the trunk and each carries its own cloud, so
 * the crown is two or three flat pads of different size and height with sky between them, all on the leaning side.
 * The roots are heaped on the side away from the lean, and fallen petals lie under the whole canopy.
 *
 * <p><b>The overhang is the point:</b> the lean starts at y = 3, so under the trunk and under every cloud there are
 * at least 3 free blocks (nothing hangs below y = 3), and you can walk under it. Nothing leaves the plot.
 *
 * <p>The seed picks the lean direction (axis or diagonal), the pitch, the reach, where the elbow is, and the number,
 * place and side of the rising limbs. On a 5×5 plot the lean is short and the two clouds are close: compact.
 *
 * <p>Size: 5–9 × 5–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeCherryLeaning implements Variant {
	/** Cherry pink, with flowering azalea only as a sprinkle on the upper shell (a few percent of the leaves). */
	private static final TreeKit.Leaves PINK = new TreeKit.Leaves(leaves(Blocks.CHERRY_LEAVES), leaves(Blocks.CHERRY_LEAVES),
			leaves(Blocks.FLOWERING_AZALEA_LEAVES), 3, 9);

	/** The lean starts here: y = 0..2 stay free under the trunk and under the canopy. */
	private static final int YS = 3;

	/** Lean directions {du, dv}: axis leans more often than diagonals (a diagonal can point straight at the viewer). */
	private static final int[][] DIRS = {{1, 0}, {1, 0}, {1, 0}, {-1, 0}, {-1, 0}, {-1, 0}, {0, 1}, {0, 1}, {0, 1}, {0, -1}, {0, -1}, {0, -1},
			{1, -1}, {1, -1}, {-1, 1}, {-1, 1}, {1, 1}, {-1, -1}};

	@Override
	public String id() {
		return "tree_cherry_leaning";
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
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	/** A cloud: centre, semi-axes. */
	private record Pad(int u, int y, int v, double rx, double rz) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x1EA5);
		int w = p.w();
		int d = p.d();
		int mn = Math.min(w, d);
		int hh = Math.min(p.h(), 13);

		// ---- direction and foot
		int[] dir = DIRS[r.nextInt(DIRS.length)];
		int du = dir[0];
		int dv = dir[1];
		boolean diag = du != 0 && dv != 0;
		int inset = 1; // how far from the edge the tip of the trunk stays (its cloud shrinks to fit)
		int in0 = 0; // the foot stands at the edge: all the room goes to the lean (the roots spread sideways)
		int lu;
		int lv;
		int bu;
		int bv;
		if (du != 0) {
			bu = du > 0 ? in0 : w - 2 - in0;
			lu = bu + (du > 0 ? 1 : 0);
		} else {
			lu = TreeKit.clamp((w - 1) / 2 + r.nextInt(3) - 1, inset, w - 1 - inset);
			bu = TreeKit.clamp(lu - r.nextInt(2), 0, w - 2);
		}
		if (dv != 0) {
			bv = dv > 0 ? in0 : d - 2 - in0;
			lv = bv + (dv > 0 ? 1 : 0);
		} else {
			lv = TreeKit.clamp((d - 1) / 2 + r.nextInt(3) - 1, inset, d - 1 - inset);
			bv = TreeKit.clamp(lv - r.nextInt(2), 0, d - 2);
		}

		// ---- reach and pitch: k cells along the direction, the seed picks how much of the room and how steep
		int kmax = 99;
		if (du != 0) {
			kmax = Math.min(kmax, du > 0 ? (w - 1 - inset) - lu : lu - inset);
		}
		if (dv != 0) {
			kmax = Math.min(kmax, dv > 0 ? (d - 1 - inset) - lv : lv - inset);
		}
		kmax = Math.max(1, kmax);
		int k = kmax - (kmax >= 4 && r.nextInt(3) == 0 ? 1 : 0);
		double m = 0.3 + 0.55 * r.nextDouble(); // rise per cell of the lean, 20°..45° plus the plan staircase of a diagonal
		double fe = 0.5 + 0.22 * r.nextDouble(); // where the elbow is
		int ke = TreeKit.clamp((int) Math.round(fe * k), 1, k);
		int ytip = TreeKit.clamp(YS + (int) Math.round(m * k * (diag ? 1.3 : 1.0)) + 1, YS + 1, hh - 4);
		int yElbow = TreeKit.clamp(YS + (int) Math.round(m * 0.7 * ke * (diag ? 1.3 : 1.0)), YS, ytip - 1);
		int tu = lu + k * du;
		int tv = lv + k * dv;

		// ---- foot: tapered 2×2 (4 cells, then 3), then the 1×1 column to the start of the lean
		for (int a = 0; a < 2; a++) {
			for (int e = 0; e < 2; e++) {
				t.wood(bu + a, 0, bv + e, TreeKit.CHERRY.bark());
			}
		}
		for (int y = 1; y < YS; y++) {
			t.wood(lu, y, lv, TreeKit.CHERRY.log(net.minecraft.core.Direction.Axis.Y));
		}
		// a buttress on the back of the column, one block high: the trunk looks pulled by the lean
		int backU = lu - du;
		int backV = lv - dv;
		if (diag) {
			backU = lu;
		}
		if (backU >= bu && backU <= bu + 1 && backV >= bv && backV <= bv + 1) {
			t.wood(backU, 1, backV, TreeKit.CHERRY.bark());
		}
		int bias = octant(-du, -dv);
		TreeKit.roots(t, TreeKit.CHERRY, bu, bv, 3, 2, bias, 11);

		// ---- trunk: out along the lean, the elbow, then up to the tip (every cell shares a face with the next)
		int eu = lu + ke * du;
		int ev = lv + ke * dv;
		List<int[]> segA = TreeKit.branch(t, TreeKit.CHERRY, new int[] {lu, YS, lv}, new int[] {eu, yElbow, ev}, false);
		// a heavier trunk where it leaves the foot (6+ wide, straight leans): a second log beside the first ones, never lower than y = 3
		if (mn >= 7 && !diag) {
			int side = r.nextBoolean() ? 1 : -1;
			int pu = -dv * side;
			int pv = du * side;
			for (int i = 1; i < Math.min(4, segA.size()); i++) {
				int[] q = segA.get(i);
				t.woodSoft(q[0] + pu, q[1], q[2] + pv, TreeKit.CHERRY.bark());
			}
		}
		List<int[]> segB = TreeKit.branch(t, TreeKit.CHERRY, new int[] {eu, yElbow, ev}, new int[] {tu, ytip, tv}, false);

		// ---- clouds
		double ra = TreeKit.clamp(0.25 * mn + 0.55, 1.9, 2.8);
		List<Pad> pads = new ArrayList<>();
		pads.add(padClamped(tu + du, ytip + 1, tv + dv, ra, w, d)); // the cloud sits beyond the tip: the trunk enters it from the side

		// rising limb 1: from the first half of the lean, up and a little outwards
		int i1 = TreeKit.clamp((int) Math.round(segA.size() * (0.3 + 0.3 * r.nextDouble())), 1, Math.max(1, segA.size() - 1));
		int[] s1 = segA.get(Math.min(i1, segA.size() - 1));
		int rb1 = 2 + r.nextInt(2);
		int kickU = diag ? (r.nextBoolean() ? du : 0) : du;
		int kickV = diag ? (kickU == 0 ? dv : 0) : dv;
		double r1 = Math.max(1.6, ra * (0.74 + 0.1 * r.nextDouble()));
		Pad p1 = padClamped(s1[0] + kickU, Math.min(s1[1] + rb1, hh - 3), s1[2] + kickV, r1, w, d);
		pads.add(p1);
		TreeKit.branch(t, TreeKit.CHERRY, s1, new int[] {p1.u, p1.y, p1.v}, false);

		// rising limb 2 (6+ wide): from near the elbow, kicked to one side, lower and smaller
		if (mn >= 6) {
			int side = r.nextBoolean() ? 1 : -1;
			int pu = -dv * side;
			int pv = du * side;
			if (pu == 0 && pv == 0) {
				pu = side;
			}
			int[] s2 = segB.size() > 2 ? segB.get(1 + r.nextInt(Math.min(2, segB.size() - 1))) : segA.get(segA.size() - 1);
			double r2 = Math.max(1.5, ra * (0.55 + 0.12 * r.nextDouble()));
			int ry = Math.min(s2[1] + 2 + r.nextInt(2), hh - 3);
			Pad p2 = padClamped(s2[0] + pu * (1 + r.nextInt(2)), ry, s2[2] + pv * (1 + r.nextInt(2)), r2, w, d);
			if (Math.hypot(p2.u - tu, p2.v - tv) >= 2.2 || Math.abs(p2.y - (ytip + 1)) >= 2) {
				pads.add(p2);
				TreeKit.branch(t, TreeKit.CHERRY, s2, new int[] {p2.u, p2.y, p2.v}, false);
			}
		}

		for (Pad q : pads) {
			t.wood(q.u, q.y, q.v, TreeKit.CHERRY.bark()); // the end of a limb inside its cloud: no pale log end showing through a gap
		}
		t.wood(tu, ytip, tv, TreeKit.CHERRY.bark());
		int salt = 1;
		for (Pad q : pads) {
			TreeKit.lobe(t, q.u, q.y, q.v, q.rx, q.rz, Math.max(q.rx, q.rz) >= 2.3 ? 2.0 : 1.0, 1.0, PINK, 0.45, salt++);
		}
		// a little hem under each pad's rim, never below y = 3
		for (Pad q : pads) {
			int y = q.y - 1;
			for (int v = (int) (q.v - q.rz - 1); v <= q.v + q.rz + 1; v++) {
				for (int u = (int) (q.u - q.rx - 1); u <= q.u + q.rx + 1; u++) {
					if (y - 1 >= YS && t.kind(u, y, v) == TreeKit.LEAF && t.empty(u, y - 1, v) && t.hash(u, y + 5, v, 100) < 22) {
						TreeKit.hang(t, u, y - 1, v, 1 + t.hash(u, 3, v, 2), YS, PINK.a(), PINK.a(), 0, 0, false);
					}
				}
			}
		}
		// holes: a few leaves are left out so the sky shows through (never next to wood, so nothing is cut off)
		for (int y = YS; y < hh; y++) {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF && t.hash(u + 3, y + 5, v + 9, 100) < 6 && !nextToWood(t, u, y, v)) {
						t.clear(u, y, v);
					}
				}
			}
		}
		// no ledge under a cloud: where a trunk top or a cloud top has a roof of foliage within reach above it (and room to stand),
		// the gap is filled with leaves, so nothing standable is left that nobody can reach
		for (int y = YS; y < hh - 3; y++) {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					byte kd = t.kind(u, y, v);
					if ((kd == TreeKit.WOOD || kd == TreeKit.LEAF) && t.empty(u, y + 1, v) && t.empty(u, y + 2, v)) {
						int j = -1;
						for (int yy = y + 3; yy <= Math.min(hh - 1, y + 9) && j < 0; yy++) {
							byte k2 = t.kind(u, yy, v);
							if (k2 == TreeKit.WOOD || k2 == TreeKit.LEAF) {
								j = yy;
							}
						}
						for (int yy = y + 1; j > 0 && yy < j; yy++) {
							t.leaf(u, yy, v, PINK.a());
						}
					}
				}
			}
		}
		t.prune();

		// ---- fallen petals: thick under the canopy and the trunk, a few round the foot, moss between
		boolean[] shade = new boolean[w * d];
		for (int y = YS; y < hh; y++) {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					byte kd = t.kind(u, y, v);
					if (kd == TreeKit.LEAF || kd == TreeKit.WOOD) {
						shade[v * w + u] = true;
					}
				}
			}
		}
		final int fu = bu;
		final int fv = bv;
		TreeKit.ground(t, lu, lv, 99, (u, v, kk) -> {
			boolean under = shade[v * w + u];
			boolean near = under || (u > 0 && shade[v * w + u - 1]) || (u < w - 1 && shade[v * w + u + 1])
					|| (v > 0 && shade[(v - 1) * w + u]) || (v < d - 1 && shade[(v + 1) * w + u]);
			int hsh = t.hash(u, 0, v, 100);
			int pet = under ? 72 : near ? 40 : 0;
			if (Math.hypot(u - fu - 0.5, v - fv - 0.5) < 3.2) {
				pet = Math.max(pet, 50);
			}
			if (hsh < pet) {
				return TreeKit.petals(1 + t.hash(u, 1, v, under ? 4 : 2), t.hash(u, 2, v, 4));
			}
			return near && hsh < pet + 9 ? b(Blocks.MOSS_CARPET) : null;
		});
		t.flush(c);
	}

	/** A cloud whose centre is moved inside the plot so that it fits. */
	private static Pad padClamped(int cu, int cy, int cv, double rr, int w, int d) {
		int in = Math.max(1, (int) Math.floor(rr - 0.5));
		int u = TreeKit.clamp(cu, Math.min(in, (w - 1) / 2), Math.max(w - 1 - in, (w - 1) / 2));
		int v = TreeKit.clamp(cv, Math.min(in, (d - 1) / 2), Math.max(d - 1 - in, (d - 1) / 2));
		double rx = Math.min(rr, Math.min(u, w - 1 - u) + 0.9);
		double rz = Math.min(rr, Math.min(v, d - 1 - v) + 0.9);
		return new Pad(u, Math.max(cy, YS + 2), v, Math.max(1.5, rx), Math.max(1.5, rz));
	}

	private static boolean nextToWood(Vox t, int u, int y, int v) {
		return t.kind(u + 1, y, v) == TreeKit.WOOD || t.kind(u - 1, y, v) == TreeKit.WOOD || t.kind(u, y + 1, v) == TreeKit.WOOD
				|| t.kind(u, y - 1, v) == TreeKit.WOOD || t.kind(u, y, v + 1) == TreeKit.WOOD || t.kind(u, y, v - 1) == TreeKit.WOOD;
	}

	private static int octant(int du, int dv) {
		for (int k = 0; k < 8; k++) {
			if (TreeKit.OCT[k][0] == Integer.signum(du) && TreeKit.OCT[k][1] == Integer.signum(dv)) {
				return k;
			}
		}
		return 0;
	}
}
