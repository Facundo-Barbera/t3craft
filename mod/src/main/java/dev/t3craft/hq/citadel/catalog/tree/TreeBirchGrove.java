package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A grove of two or three slender white birches of unequal height, each leaning a little away from
 * the others, with small airy oval crowns that hang a fringe of leaves. A fallen birch log (moss on top,
 * a mushroom) lies across the front and ferns, lilies of the valley and moss fill the floor.
 *
 * <p>Silhouette: several thin vertical strokes and light feathery tufts at staggered heights, against the
 * maple's single broad dome and the pine's stepped spire. Which trunks exist, where they lean and which is
 * tallest depend on the seed.
 *
 * <p>Size: 6–9 × 6–9 × 9–14 (three trunks only from 7 × 7 up).
 */
public final class TreeBirchGrove implements Variant {
	private static final BlockState TRUNK = b(Blocks.BIRCH_LOG);
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

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

	/** One trunk: base column, top column (after the lean), the leaf-crown base height and the lean's start. */
	private record Trunk(int u0, int v0, int uT, int vT, int top, int leanY) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xB12C_6A0BL);
		int w = p.w();
		int d = p.d();
		int h = p.h();
		boolean flipU = r.nextBoolean();
		boolean flipV = r.nextBoolean();
		boolean three = w >= 7 && d >= 7 && r.nextInt(3) != 0;

		int[][] base;
		if (three) {
			base = new int[][] {{2, 2}, {w - 3, 2}, {w / 2, d - 3}};
		} else {
			int bu = MixedTreeKit.clamp(w - 3 + r.nextInt(2) - 1, 2, w - 3);
			int bv = MixedTreeKit.clamp(d - 3 + r.nextInt(2) - 1, 2, d - 3);
			if (bu == 2 && bv == 2) {
				bu = 3;
			}
			int lo = w < 7 ? 1 : 2;
			bu = MixedTreeKit.clamp(bu, lo, w - 1 - lo);
			base = new int[][] {{lo, lo + 1}, {bu == lo ? w - 1 - lo : bu, d - 1 - lo}};
		}
		int[] drops = {0, h >= 11 ? 2 : 1, h >= 12 ? 3 : 2};
		int n = base.length;
		int tallest = r.nextInt(n);
		Trunk[] trunks = new Trunk[n];
		int rank = 1;
		for (int i = 0; i < n; i++) {
			int u0 = flipU ? w - 1 - base[i][0] : base[i][0];
			int v0 = flipV ? d - 1 - base[i][1] : base[i][1];
			int top = h - 4 - (i == tallest ? drops[0] : drops[rank++]);
			// lean away from the middle of the box by one block, on one axis, half-way up
			int uT = u0;
			int vT = v0;
			if (r.nextInt(4) != 0) {
				if (r.nextBoolean()) {
					int s = u0 * 2 < w - 1 ? -1 : 1;
					uT = MixedTreeKit.clamp(u0 + 2 * s, 1, w - 2);
				} else {
					int s = v0 * 2 < d - 1 ? -1 : 1;
					vT = MixedTreeKit.clamp(v0 + 2 * s, 1, d - 2);
				}
			}
			trunks[i] = new Trunk(u0, v0, uT, vT, top, Math.max(2, top / 2));
		}

		ground(c, p, trunks, flipU);
		for (Trunk t : trunks) {
			canopy(c, p, t);
		}
		for (int i = 0; i < n; i++) {
			trunk(c, p, trunks[i], i);
		}
		fallenLog(c, p, flipU);
	}

	// ------------------------------------------------------------------ ground

	private static boolean nearTrunk(Trunk[] trunks, int u, int v) {
		for (Trunk t : trunks) {
			if (Math.abs(u - t.u0()) + Math.abs(v - t.v0()) <= 1) {
				return true;
			}
		}
		return false;
	}

	private static void ground(CitadelCanvas c, Plot p, Trunk[] trunks, boolean flipU) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 1; v < p.d(); v++) { // row 0 belongs to the fallen log
				BlockState s = switch (p.hash(u, 0, v, 9)) {
					case 0, 1 -> b(Blocks.FERN);
					case 2 -> b(Blocks.LILY_OF_THE_VALLEY);
					case 3 -> b(Blocks.MOSS_CARPET);
					case 4 -> b(Blocks.WHITE_TULIP);
					default -> null;
				};
				if (nearTrunk(trunks, u, v)) {
					s = p.hash(u, 0, v, 2) == 0 ? b(Blocks.MOSS_CARPET) : b(Blocks.FERN); // moss and ferns hug the trunks
				}
				if (s != null) {
					MixedTreeKit.put(c, p, u, 0, v, s);
				}
			}
		}
	}

	private static void fallenLog(CitadelCanvas c, Plot p, boolean flipU) {
		int len = Math.min(4, p.w() - 2);
		int start = flipU ? p.w() - 1 - len : 1;
		for (int i = 0; i < len; i++) {
			int u = start + i;
			MixedTreeKit.put(c, p, u, 0, 0, MixedTreeKit.log(Blocks.BIRCH_LOG, Direction.Axis.X));
			int n = p.hash(u, 1, 0, 3);
			if (n == 0) {
				MixedTreeKit.put(c, p, u, 1, 0, b(Blocks.MOSS_CARPET));
			} else if (n == 1 && i > 0 && i < len - 1) {
				MixedTreeKit.put(c, p, u, 1, 0, b(Blocks.BROWN_MUSHROOM));
			}
		}
		MixedTreeKit.put(c, p, flipU ? start - 1 : start + len, 0, 0, b(Blocks.LILY_OF_THE_VALLEY));
	}

	// ------------------------------------------------------------------ trunk

	private static void trunk(CitadelCanvas c, Plot p, Trunk t, int index) {
		for (int y = 0; y <= t.top() + 1; y++) {
			if (y < t.leanY()) {
				MixedTreeKit.put(c, p, t.u0(), y, t.v0(), TRUNK);
			} else if (y == t.leanY()) {
				MixedTreeKit.put(c, p, t.u0(), y, t.v0(), TRUNK); // the kink: both columns in one layer
				MixedTreeKit.put(c, p, t.uT(), y, t.vT(), TRUNK);
			} else {
				MixedTreeKit.put(c, p, t.uT(), y, t.vT(), TRUNK);
			}
		}
		// [depth] two stubs of branch under the crown, on seed-picked sides
		for (int k = 0; k < 2; k++) {
			int y = t.top() - 3 - k;
			if (y < 2) {
				continue;
			}
			Direction side = SIDES[p.hash(t.uT() + k, y, t.vT() + index, 4)];
			int su = t.uT() + side.getStepX();
			int sv = t.vT() + side.getStepZ();
			if (p.has(su, y, sv) && p.hash(su, y, sv, 2) == 0) {
				MixedTreeKit.put(c, p, su, y, sv, MixedTreeKit.log(Blocks.BIRCH_LOG, side.getAxis()));
			}
		}
	}

	// ------------------------------------------------------------------ crown

	private static void canopy(CitadelCanvas c, Plot p, Trunk t) {
		double margin = Math.min(Math.min(t.uT(), p.w() - 1 - t.uT()), Math.min(t.vT(), p.d() - 1 - t.vT())) + 0.7;
		double ru = Math.min(2.1, margin);
		double ry = 2.9;
		double cy = t.top() + 0.6;
		int reach = (int) Math.ceil(ru);
		boolean[][][] g = new boolean[reach * 2 + 1][6][reach * 2 + 1]; // [du][dy + 2][dv]
		for (int dy = -2; dy <= 3; dy++) {
			int y = t.top() + dy;
			for (int du = -reach; du <= reach; du++) {
				for (int dv = -reach; dv <= reach; dv++) {
					double q = (du * du + dv * dv) / (ru * ru) + (y - cy) * (y - cy) / (ry * ry);
					int u = t.uT() + du;
					int v = t.vT() + dv;
					if (q > 1.0 || (q > 0.85 && p.hash(u, y, v, 2) == 0) || (q > 0.55 && p.hash(u, y, v, 3) == 0)) {
						continue; // [finish] airy: birch crowns are see-through
					}
					g[du + reach][dy + 2][dv + reach] = true;
				}
			}
		}
		// a fringe hanging under the leaves (bottom-up, so a row never cascades into the next)
		for (int dy = -2; dy <= 2; dy++) {
			for (int du = -reach; du <= reach; du++) {
				for (int dv = -reach; dv <= reach; dv++) {
					if (!g[du + reach][dy + 2][dv + reach] && g[du + reach][dy + 3][dv + reach]
							&& p.hash(t.uT() + du, t.top() + dy, t.vT() + dv, 3) == 0) {
						g[du + reach][dy + 2][dv + reach] = true;
					}
				}
			}
		}
		for (int dy = -2; dy <= 3; dy++) {
			int y = t.top() + dy;
			for (int du = -reach; du <= reach; du++) {
				for (int dv = -reach; dv <= reach; dv++) {
					if (!g[du + reach][dy + 2][dv + reach]) {
						continue;
					}
					int u = t.uT() + du;
					int v = t.vT() + dv;
					// [palette] mostly birch leaves, oak and azalea leaves for a lighter / deeper patch here and there
					BlockState s = switch (p.hash(u, y, v, 10)) {
						case 0, 1 -> leaves(Blocks.OAK_LEAVES);
						case 2 -> leaves(Blocks.AZALEA_LEAVES);
						default -> leaves(Blocks.BIRCH_LEAVES);
					};
					MixedTreeKit.put(c, p, u, y, v, s);
				}
			}
		}
	}
}
