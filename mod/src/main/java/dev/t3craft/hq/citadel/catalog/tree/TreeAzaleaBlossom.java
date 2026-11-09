package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Flowering azalea in a low round planter: a short 1×1 oak trunk on a one-block flare of oak bark (and a
 * diagonal root or two on wider plots) that forks into two or three visible 1-wide diagonal limbs, while the
 * leader climbs on into the crown. The crown is a domed central lobe on the leader with smaller side lobes
 * on the limbs, each a separate lobe (notches of air between them), each mixing azalea and flowering azalea
 * evenly (the dominant tone swapped from lobe to lobe) with a sparse sprinkle of cherry-blossom pink. The
 * planter is a single ring of mossy stone, mossy cobble and deepslate bricks with gaps where moss or grass
 * shows; the bed is moss with petals and azalea bushes.
 *
 * <p>Size: 5–9 × 5–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeAzaleaBlossom implements Variant {
	private static final Wood WOOD = new Wood(Blocks.OAK_LOG, Blocks.OAK_WOOD, b(Blocks.OAK_WOOD));
	private static final Leaves BLOOM = Leaves.of(Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES).clumps(2)
			.withAccent(leaves(Blocks.CHERRY_LEAVES), 6);
	private static final Leaves GREEN = Leaves.of(Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES).clumps(2)
			.withAccent(leaves(Blocks.CHERRY_LEAVES), 6);

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
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xA2A1EAL);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 13);
		int m = Math.min(w, d);
		int yC = H - 4; // the centre of the domed crown lobe
		int yF = TreeKit.clamp((int) Math.round(H * 0.3), 2, yC - 3); // the fork: a short bare trunk below it
		int lift = (H - 9) / 3;

		// ---- trunk: 1×1 oak from the ground to the crown, a one-block flare of bark at the foot
		int tu0 = (w - 1) / 2;
		int tv0 = (d - 1) / 2;
		int[][] lead = leader(t, tu0, tv0, yF, yC - 1, r.nextInt(4), r.nextInt(3) == 0);
		flare(t, tu0, tv0, m >= 7);

		// ---- limbs: two or three diagonals out of the fork, each ending under a side lobe
		int nL = m >= 7 ? 3 : 2;
		double a0 = r.nextDouble() * Math.PI * 2;
		double step = nL == 3 ? Math.PI * 2 / 3 : Math.PI * 0.9;
		int front = 0;
		double minEz = 9;
		@SuppressWarnings("unchecked")
		List<int[]>[] limbCells = new List[nL];
		int[][] ends = new int[nL][];
		double[] rads = new double[nL];
		for (int i = 0; i < nL; i++) {
			double ang = a0 + i * step + (r.nextDouble() - 0.5) * 0.5;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int sy = TreeKit.clamp(yF + (i == 1 ? 1 : 0), 2, yC - 2);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			int ly = TreeKit.clamp(yF + 2 + (i % 2) + lift, sy + 1, yC - 1);
			double rx = TreeKit.clamp(1.3 + (m - 5) * 0.22 + r.nextDouble() * 0.3, 1.3, 2.3);
			double rz = rx * (0.85 + r.nextDouble() * 0.2);
			double room = TreeKit.room(t, su, sv, ex, ez, 0);
			double reach = TreeKit.clamp(room - 0.5, 1.4, 1.2 + m * 0.25);
			int tu = TreeKit.clamp((int) Math.round(su + ex * reach), 0, w - 1);
			int tv = TreeKit.clamp((int) Math.round(sv + ez * reach), 0, d - 1);
			if (tu == su && tv == sv) {
				tu = TreeKit.clamp(su + (ex >= 0 ? 1 : -1), 0, w - 1);
			}
			limbCells[i] = TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {tu, ly - 1, tv}, false);
			ends[i] = new int[] {tu, ly, tv};
			rads[i] = rx;
			if (ez < minEz) {
				minEz = ez;
				front = i;
			}
		}

		// ---- the crown: the domed central lobe first, then the side lobes (each its own salt: notches show)
		int cu = lead[0][yC];
		int cv = lead[1][yC];
		double cr = TreeKit.clamp(2.0 + (m - 5) * 0.2, 2.0, 2.8);
		TreeKit.lobe(t, cu, yC, cv, cr, cr * 0.92, 2.2, 1.0, BLOOM, 0.4, 9);
		for (int i = 0; i < nL; i++) {
			int[] e = ends[i];
			double rx = rads[i];
			TreeKit.lobe(t, e[0], e[1], e[2], rx, rx * 0.9, 1.2, 0.9, i % 2 == 0 ? GREEN : BLOOM, 0.4, i);
			if (m >= 7) {
				TreeKit.drips(t, e[0], e[1], e[2], rx, rx * 0.9, 5 + i);
			}
		}
		// on the widest plots a fifth, small lobe on a twig off the leader, between two limbs
		if (m >= 9) {
			double fa = a0 + step * 0.5;
			int sy = TreeKit.clamp(yC - 3, yF + 1, yC - 2);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			int fu = TreeKit.clamp((int) Math.round(su + Math.cos(fa) * 2.2), 1, w - 2);
			int fv = TreeKit.clamp((int) Math.round(sv + Math.sin(fa) * 2.2), 1, d - 2);
			if (fu != su || fv != sv) {
				TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {fu, sy + 1, fv}, false);
				TreeKit.lobe(t, fu, sy + 2, fv, 1.5, 1.4, 1.0, 0.0, GREEN.swap(), 0.35, 22);
			}
		}

		// ---- the planter: one ring on y = 0 only, mossy stone, mossy cobble and deepslate, with gaps of moss or grass
		final double ccu = (w - 1) / 2.0;
		final double ccv = (d - 1) / 2.0;
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (!inBed(u, v, ccu, ccv) || !rim(u, v, ccu, ccv) || !t.empty(u, 0, v)) {
					continue;
				}
				int k = t.hash(u, 0, v, 10);
				if (k < 3) {
					t.fixed(u, 0, v, b(Blocks.MOSSY_STONE_BRICKS));
				} else if (k < 5) {
					t.fixed(u, 0, v, b(Blocks.MOSSY_COBBLESTONE));
				} else if (k < 7) {
					t.fixed(u, 0, v, b(Blocks.DEEPSLATE_BRICKS));
				} else if (k < 9) {
					t.cover(u, 0, v, b(Blocks.MOSS_CARPET));
				} else {
					t.cover(u, 0, v, b(Blocks.SHORT_GRASS));
				}
			}
		}

		// ---- the bed: moss, petals and azalea bushes inside; a few petals and moss outside
		final double fcu = tu0;
		final double fcv = tv0;
		TreeKit.ground(t, ccu, ccv, 99, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (!inBed(u, v, ccu, ccv)) {
				if (hsh < 22) {
					return TreeKit.petals(1 + t.hash(u, 1, v, 2), t.hash(u, 2, v, 4));
				}
				return hsh < 32 ? b(Blocks.MOSS_CARPET) : null;
			}
			boolean far = Math.hypot(u - fcu, v - fcv) > 1.6;
			if (hsh < 20) {
				return TreeKit.petals(1 + t.hash(u, 1, v, 4), t.hash(u, 2, v, 4));
			}
			if (far && hsh < 28) {
				return b(Blocks.FLOWERING_AZALEA);
			}
			if (far && hsh < 31) {
				return b(Blocks.AZALEA);
			}
			return hsh < 85 ? b(Blocks.MOSS_CARPET) : null;
		});
		// at least one flowering azalea bush on the bed
		int bu = TreeKit.clamp(tu0 + (r.nextBoolean() ? 2 : -2), 1, w - 2);
		int bv = TreeKit.clamp(tv0 + (r.nextBoolean() ? 1 : -1), 1, d - 2);
		if (t.kind(bu, 0, bv) == TreeKit.COVER) {
			t.clear(bu, 0, bv);
		}
		t.cover(bu, 0, bv, b(Blocks.FLOWERING_AZALEA));

		t.prune();
		t.flush(c);

		// ---- a lantern hanging under the front limb (only where it hangs from wood, with air below)
		List<int[]> cells = limbCells[front];
		int[] best = null;
		for (int[] cell : cells) {
			int u = cell[0];
			int y = cell[1];
			int v = cell[2];
			if (y < 3 || Math.abs(u - lead[0][y]) + Math.abs(v - lead[1][y]) < 2) {
				continue;
			}
			if (t.kind(u, y, v) != TreeKit.WOOD || !t.empty(u, y - 1, v) || !t.empty(u, y - 2, v)) {
				continue;
			}
			if (best == null || y < best[1]) {
				best = cell;
			}
		}
		if (best != null) {
			p.put(c, best[0], best[1] - 1, best[2], b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true),
					CitadelCanvas.HANGING);
		}
	}

	/**
	 * The 1×1 oak leader from y = 0 to {@code yTop}: straight up to the fork, then (with {@code lean}) one
	 * sideways step towards face {@code dir} two blocks above it, the step joined by a bark block.
	 */
	private static int[][] leader(Vox t, int cu, int cv, int yF, int yTop, int dir, boolean lean) {
		int[][] faces = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		int[] lu = new int[t.h + 1];
		int[] lv = new int[t.h + 1];
		int u = cu;
		int v = cv;
		int yStep = lean && yTop - yF >= 4 ? yF + 2 : -1;
		for (int y = 0; y <= t.h; y++) {
			if (y == yStep) {
				int nu = TreeKit.clamp(u + faces[dir][0], 1, t.w - 2);
				int nv = TreeKit.clamp(v + faces[dir][1], 1, t.d - 2);
				t.wood(nu, y - 1, nv, WOOD.bark());
				t.wood(u, y - 1, v, WOOD.bark());
				u = nu;
				v = nv;
			}
			lu[y] = u;
			lv[y] = v;
			if (y <= yTop && y < t.h) {
				t.wood(u, y, v, WOOD.log(Direction.Axis.Y));
			}
		}
		return new int[][] {lu, lv};
	}

	/** A one-block flare of oak bark on two or three faces of the foot; with {@code roots} a diagonal root or two. */
	private static void flare(Vox t, int cu, int cv, boolean roots) {
		int[][] faces = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		int skip = t.hash(cu, 0, cv, 4);
		int skip2 = t.hash(cu, 1, cv, 2) == 0 ? (skip + 2) % 4 : -1;
		for (int k = 0; k < 4; k++) {
			if (k != skip && k != skip2) {
				t.woodSoft(cu + faces[k][0], 0, cv + faces[k][1], WOOD.bark());
			}
		}
		if (!roots) {
			return;
		}
		// diagonal roots: from a flare face one log round the corner, then one more step out
		for (int k = 0; k < 4; k++) {
			if (k == skip || k == skip2 || t.hash(cu + k, 2, cv, 2) != 0) {
				continue;
			}
			int fu = cu + faces[k][0];
			int fv = cv + faces[k][1];
			int[] side = faces[(k + 1) % 4];
			int ru = fu + side[0];
			int rv = fv + side[1];
			if (t.empty(ru, 0, rv)) {
				t.wood(ru, 0, rv, WOOD.log(side[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
				int ou = ru + faces[k][0];
				int ov = rv + faces[k][1];
				if (t.empty(ou, 0, ov)) {
					t.wood(ou, 0, ov, WOOD.log(faces[k][0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
	}

	private static boolean inBed(int u, int v, double ccu, double ccv) {
		double du = (u - ccu) / (ccu + 0.5);
		double dv = (v - ccv) / (ccv + 0.5);
		return du * du + dv * dv <= 1.0;
	}

	private static boolean rim(int u, int v, double ccu, double ccv) {
		return !inBed(u + 1, v, ccu, ccv) || !inBed(u - 1, v, ccu, ccv) || !inBed(u, v + 1, ccu, ccv)
				|| !inBed(u, v - 1, ccu, ccv);
	}
}
