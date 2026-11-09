package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

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
 * Flowering azalea in a low round planter: a cherry-wood trunk (2×2 thinning to a leader; a 1×1 with a
 * flare on 5-wide plots) with diagonal roots over the moss bed, some spilling over the curb, forks into two
 * or three visible diagonal limbs. The crown is four or five rounded lobes of different sizes that cluster
 * into a dome with notches between them: plain azalea on the low lobes, flowering azalea dominant above,
 * cherry-blossom pink sprinkled on top. Pink drips hang under the rims, a lantern hangs from the front limb.
 * The planter is a dark deepslate curb one block high with a pale slab cap (gaps where blossom spills over),
 * the bed is moss with petals and azalea bushes.
 *
 * <p>Size: 5–9 × 5–9 × 9+ (the tree stops at 13 high).
 */
public final class TreeAzaleaBlossom implements Variant {
	private static final Wood WOOD = TreeKit.CHERRY;
	private static final Leaves UPPER = Leaves.of(Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES).clumps(4)
			.withAccent(leaves(Blocks.CHERRY_LEAVES), 5);
	private static final Leaves LOWER = Leaves.of(Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES).clumps(3)
			.withAccent(leaves(Blocks.CHERRY_LEAVES), 14);
	private static final Leaves CROWN = Leaves.of(Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES).clumps(5)
			.withAccent(leaves(Blocks.CHERRY_LEAVES), 3);

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
		double sc = m / 7.0;
		boolean small = m < 6;
		int yTop = H - 4; // the leader's top; the crown lobe sits on it
		int yF = TreeKit.clamp((int) Math.round(H * 0.33), 3, H - 6); // the fork

		// ---- trunk and roots
		int[][] lead;
		double tcu;
		double tcv;
		if (small) {
			int cu = (w - 1) / 2;
			int cv = (d - 1) / 2;
			lead = slender(t, cu, cv, yTop);
			tcu = cu;
			tcv = cv;
		} else {
			int ou = r.nextInt(2);
			int ov = r.nextInt(2);
			int bu = TreeKit.base(w, ou);
			int bv = TreeKit.base(d, ov);
			lead = TreeKit.trunk(t, WOOD, bu, bv, 1, yTop, ou, ov,
					(r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 1.2);
			TreeKit.roots(t, WOOD, bu, bv, 5, m >= 7 ? 2 : 1, -1, 17);
			tcu = bu + 0.5;
			tcv = bv + 0.5;
		}

		// ---- limbs: two or three diagonals out of the fork, each ending inside a lobe
		int nL = m >= 7 ? 3 : 2;
		double a0 = r.nextDouble() * Math.PI * 2;
		double step = nL == 3 ? Math.PI * 2 / 3 : Math.PI * 0.85;
		int lift = (H - 9) / 3;
		int front = 0;
		int back = 0;
		double minEz = 9;
		double maxEz = -9;
		int[][] ends = new int[nL][];
		double[] rad = new double[nL];
		double[] dirU = new double[nL];
		double[] dirV = new double[nL];
		@SuppressWarnings("unchecked")
		List<int[]>[] limbCells = new List[nL];
		for (int i = 0; i < nL; i++) {
			double ang = a0 + i * step + (r.nextDouble() - 0.5) * 0.5;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int sy = TreeKit.clamp(yF - (i == 1 ? 1 : 0), 2, yTop - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			int ly = TreeKit.clamp(yF + 1 + (i % 2) + lift, sy + 1, H - 4);
			double rx = Math.max(1.4, (1.7 + r.nextDouble() * 0.4) * sc);
			double rz = rx * (0.82 + r.nextDouble() * 0.2);
			double room = TreeKit.room(t, su, sv, ex, ez, 0);
			double reach = TreeKit.clamp(room - Math.max(rx, rz) + 0.9, 1.0, 2.6 * sc);
			int tu = TreeKit.clamp((int) Math.round(su + ex * reach), 0, w - 1);
			int tv = TreeKit.clamp((int) Math.round(sv + ez * reach), 0, d - 1);
			limbCells[i] = TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {tu, ly, tv}, i == 0 && !small);
			ends[i] = new int[] {tu, ly, tv};
			rad[i] = rx;
			dirU[i] = ex;
			dirV[i] = ez;
			if (ez < minEz) {
				minEz = ez;
				front = i;
			}
			if (ez > maxEz) {
				maxEz = ez;
				back = i;
			}
			// the higher lobes are flowering, the lower plain azalea
			boolean high = (i % 2) == 1 || nL == 2 && i == 0 && lift > 0;
			TreeKit.lobe(t, tu, ly, tv, rx, rz, 1.8, 1.1, high ? UPPER : LOWER, 0.4, i);
			TreeKit.drips(t, tu, ly, tv, rx, rz, 5 + i);
		}

		// ---- the crown dome on the leader (the highest, biggest lobe)
		int cu = lead[0][yTop];
		int cv = lead[1][yTop];
		double cr = TreeKit.clamp(2.0 * sc, 1.5, 2.5);
		TreeKit.lobe(t, cu, yTop + 1, cv, cr, cr * 0.9, 1.7, 1.2, CROWN, 0.4, 9);

		// ---- one more, smaller lobe: a low skirt under the back limb (wide plots) or a twig lobe (narrow ones)
		if (m >= 7) {
			List<int[]> cells = limbCells[back];
			int[] mid = cells.get(Math.min(cells.size() - 1, cells.size() * 2 / 3));
			TreeKit.lobe(t, mid[0] + dirU[back] * 0.5, Math.max(2, mid[1] - 1), mid[2] + dirV[back] * 0.5,
					1.4 * sc, 1.3 * sc, 1.3, 0.9, LOWER.swap(), 0.35, 21);
		} else {
			double fa = a0 + step * 0.5 + Math.PI;
			double fx = Math.cos(fa);
			double fz = Math.sin(fa);
			int sy = TreeKit.clamp(yF + 1, 2, yTop - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			int fu = TreeKit.clamp((int) Math.round(su + fx * 1.6), 0, w - 1);
			int fv = TreeKit.clamp((int) Math.round(sv + fz * 1.6), 0, d - 1);
			if (fu != su || fv != sv) {
				TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {fu, sy + 1, fv}, false);
				TreeKit.lobe(t, fu, sy + 1, fv, 1.3, 1.2, 1.4, 0.9, UPPER.swap(), 0.35, 22);
			}
		}

		// ---- the planter: a low round curb (deepslate, a pale slab cap with gaps); roots may run over it
		final double ccu = (w - 1) / 2.0;
		final double ccv = (d - 1) / 2.0;
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (!inBed(u, v, ccu, ccv) || !rim(u, v, ccu, ccv) || !t.empty(u, 0, v)) {
					continue;
				}
				int k = t.hash(u, 0, v, 6);
				t.fixed(u, 0, v, k < 3 ? b(Blocks.DEEPSLATE_BRICKS) : k < 5 ? b(Blocks.POLISHED_DEEPSLATE)
						: b(Blocks.CRACKED_DEEPSLATE_BRICKS));
				if (t.empty(u, 1, v) && t.hash(u, 1, v, 6) != 0) {
					t.fixed(u, 1, v, t.hash(u, 2, v, 4) == 0 ? slab(Blocks.SMOOTH_QUARTZ_SLAB) : slab(Blocks.POLISHED_DIORITE_SLAB));
				}
			}
		}

		// ---- the bed: moss, petals and azalea bushes inside; a few petals and moss outside
		final double fcu = tcu;
		final double fcv = tcv;
		TreeKit.ground(t, ccu, ccv, 99, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (!inBed(u, v, ccu, ccv)) {
				if (hsh < 22) {
					return TreeKit.petals(1 + t.hash(u, 1, v, 2), t.hash(u, 2, v, 4));
				}
				return hsh < 32 ? b(Blocks.MOSS_CARPET) : null;
			}
			boolean far = Math.hypot(u - fcu, v - fcv) > 1.6;
			if (hsh < 18) {
				return TreeKit.petals(1 + t.hash(u, 1, v, 4), t.hash(u, 2, v, 4));
			}
			if (far && hsh < 25) {
				return b(Blocks.FLOWERING_AZALEA);
			}
			if (far && hsh < 29) {
				return b(Blocks.AZALEA);
			}
			return hsh < 82 ? b(Blocks.MOSS_CARPET) : null;
		});

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

	/** A 1×1 trunk for the narrowest plots: bark at the foot, a flare on three faces and two root tips. */
	private static int[][] slender(Vox t, int cu, int cv, int yTop) {
		int[] lu = new int[t.h + 1];
		int[] lv = new int[t.h + 1];
		for (int y = 0; y <= t.h; y++) {
			lu[y] = cu;
			lv[y] = cv;
			if (y <= yTop && y < t.h) {
				t.wood(cu, y, cv, y == 0 ? WOOD.bark() : WOOD.log(Direction.Axis.Y));
			}
		}
		int[][] faces = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		int skip = t.hash(cu, 0, cv, 4);
		for (int k = 0; k < 4; k++) {
			if (k != skip) {
				t.woodSoft(cu + faces[k][0], 0, cv + faces[k][1], WOOD.bark());
			}
		}
		t.woodSoft(cu + faces[(skip + 2) % 4][0], 1, cv + faces[(skip + 2) % 4][1], WOOD.bark());
		// diagonal root tips at two corners, each tied to a face cell of the flare
		int[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		int c0 = t.hash(cu, 1, cv, 4);
		for (int k = 0; k < 2; k++) {
			int[] cc = corners[(c0 + k * 2) % 4];
			if (t.kind(cu + cc[0], 0, cv) == TreeKit.WOOD || t.kind(cu, 0, cv + cc[1]) == TreeKit.WOOD) {
				t.woodSoft(cu + cc[0], 0, cv + cc[1], WOOD.rootTip());
			}
		}
		return new int[][] {lu, lv};
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
