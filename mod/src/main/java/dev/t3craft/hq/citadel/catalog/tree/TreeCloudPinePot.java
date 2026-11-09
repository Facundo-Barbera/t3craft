package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Cloud pine (niwaki) in a big stone pot, for patios: a deepslate foot, a stone-brick pot with a
 * stair lip and calcite corner finials, a twisting spruce trunk that threads two or three flat "clouds"
 * of spruce needles (each with a small dome), moss on the clouds, and — when the plot is wide enough —
 * a raked-gravel ring with a stone lantern and a mossy rock.
 *
 * <ul>
 * <li><b>[depth]</b> the pot has a foot, a body and a lip that overhangs outwards; the clouds are
 *     separated by clear trunk, each offset to a side so the silhouette zig-zags.</li>
 * <li><b>[palette]</b> deepslate tiles (dark foot) → stone bricks / mossy / cracked body → calcite
 *     finials; soil podzol and moss; trunk spruce; needles spruce with azalea highlights.</li>
 * <li><b>[finish]</b> corner finials, ferns and a flowering bush in the pot, moss on the cloud
 *     edges, a toro lantern (wall + lantern + slab roof) and a mossy rock on the gravel.</li>
 * </ul>
 * Size: 3–7 × 3–7 × 8+ (the tree stops at 12 high). Below 5×5 the pot fills the plot.
 */
public final class TreeCloudPinePot implements Variant {
	private static final BlockState WOOD = b(Blocks.SPRUCE_WOOD);

	@Override
	public String id() {
		return "tree_cloud_pine_pot";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 7;
	}

	@Override
	public int maxD() {
		return 7;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = Math.min(p.h(), 12);
		boolean ring = w >= 5 && d >= 5;
		int pu0 = ring ? 1 : 0;
		int pu1 = ring ? w - 2 : w - 1;
		int pv0 = ring ? 1 : 0;
		int pv1 = ring ? d - 2 : d - 1;
		int cu = (pu0 + pu1) / 2;
		int cv = (pv0 + pv1) / 2;

		if (ring) {
			gravel(c, p, w, d);
		}
		pot(c, p, pu0, pu1, pv0, pv1, cu, cv);

		// --- the clouds, from the lowest to the top one
		int np = 0;
		for (int y = h - 2; y >= 4; y -= 2) {
			np++;
		}
		int[] py = new int[np];
		int[] pu = new int[np];
		int[] pv = new int[np];
		for (int k = 0; k < np; k++) { // k = 0 is the top cloud
			py[k] = h - 2 - 2 * k;
			int dir = k % 2 == 1 ? -1 : 1;
			if (k > 0 && (k % 2 == 0)) {
				dir = 1;
			}
			int offU = k == 0 ? 0 : Math.min(2, Math.max(0, Math.min(cu, w - 1 - cu) - 1)) * dir;
			int offV = k == 0 ? 0 : (d >= 5 ? (k % 2 == 1 ? 1 : -1) : 0);
			pu[k] = clamp(cu + offU, 1, Math.max(1, w - 2));
			pv[k] = clamp(cv + offV, 1, Math.max(1, d - 2));
			if (w < 3) {
				pu[k] = cu;
			}
		}
		for (int k = 0; k < np; k++) {
			double g = 0.75 + 0.25 * k / Math.max(1, np - 1);
			cloud(c, p, w, d, pu[k], pv[k], py[k], g, k == 0);
		}

		// --- the trunk threads the cloud centres: S-shaped, thickest at the base
		int lu = cu;
		int lv = cv;
		int ly = 2;
		for (int k = np - 1; k >= 0; k--) {
			line(c, p, lu, ly, lv, pu[k], py[k], pv[k]);
			lu = pu[k];
			lv = pv[k];
			ly = py[k];
		}
		set(c, p, cu, 2, cv, WOOD);
		if (ring || w >= 4) {
			// buttress roots over the soil
			int[][] side = {{-1, 0}, {1, 0}};
			for (int[] s : side) {
				if (p.hash(cu + s[0], 6, cv, 2) == 0 && (cu + s[0] > pu0 && cu + s[0] < pu1)) {
					set(c, p, cu + s[0], 2, cv, stairs(Blocks.SPRUCE_STAIRS, s[0] < 0 ? Direction.EAST : Direction.WEST));
				}
			}
		}
	}

	/** One flat cloud of needles at layer {@code y} with a dome above it, centred on (cu, cv). */
	private static void cloud(CitadelCanvas c, Plot p, int w, int d, int cu, int cv, int y, double g, boolean topCloud) {
		double ra = Math.max(1, Math.min(Math.min(cu, w - 1 - cu), 3)) * g + 1.2;
		double rb = Math.max(1, Math.min(Math.min(cv, d - 1 - cv), 3)) * g + 1.2;
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double du = (u - cu) / ra;
				double dv = (v - cv) / rb;
				double e = du * du + dv * dv;
				double n = (p.hash(u, y, v, 100) - 50) / 50.0 * 0.28;
				if (e + n <= 1.0 || (u == cu && v == cv)) {
					set(c, p, u, y, v, needle(p, u, y, v));
					if (e > 0.45 && p.hash(u, y + 1, v, 3) == 0) {
						set(c, p, u, y + 1, v, b(Blocks.MOSS_CARPET)); // soft edge
					}
				}
				if (topCloud && (e + n <= 0.5 || (u == cu && v == cv))) {
					set(c, p, u, y + 1, v, needle(p, u, y + 1, v));
				}
			}
		}
		if (topCloud) {
			set(c, p, cu, y + 2, cv, leaves(Blocks.SPRUCE_LEAVES));
		}
	}

	private static BlockState needle(Plot p, int u, int y, int v) {
		int s = p.hash(u, y, v, 10);
		return s < 7 ? leaves(Blocks.SPRUCE_LEAVES) : s < 9 ? leaves(Blocks.AZALEA_LEAVES) : leaves(Blocks.OAK_LEAVES);
	}

	/** The pot: deepslate foot, stone-brick body, stair lip with calcite finials, soil and a few plants. */
	private static void pot(CitadelCanvas c, Plot p, int u0, int u1, int v0, int v1, int cu, int cv) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean eu = u == u0 || u == u1;
				boolean ev = v == v0 || v == v1;
				boolean edge = eu || ev;
				boolean corner = eu && ev;
				set(c, p, u, 0, v, corner ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES));
				if (edge) {
					int r = p.hash(u, 1, v, 6);
					set(c, p, u, 1, v, corner ? b(Blocks.POLISHED_ANDESITE)
							: r < 3 ? b(Blocks.STONE_BRICKS) : r < 5 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS));
					if (corner) {
						set(c, p, u, 2, v, b(Blocks.CALCITE));
					} else {
						Direction out = ev ? (v == v0 ? Direction.NORTH : Direction.SOUTH) : (u == u0 ? Direction.WEST : Direction.EAST);
						set(c, p, u, 2, v, stairs(Blocks.STONE_BRICK_STAIRS, out));
					}
				} else {
					set(c, p, u, 1, v, p.hash(u, 1, v, 3) == 0 ? b(Blocks.MOSS_BLOCK) : b(Blocks.PODZOL));
					if (!(u == cu && v == cv)) {
						switch (p.hash(u, 2, v, 6)) {
							case 0, 1 -> set(c, p, u, 2, v, b(Blocks.MOSS_CARPET));
							case 2 -> set(c, p, u, 2, v, b(Blocks.FERN));
							case 3 -> set(c, p, u, 2, v, b(Blocks.FLOWERING_AZALEA));
							case 4 -> set(c, p, u, 2, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
							default -> {
							}
						}
					}
				}
			}
		}
	}

	/** Raked gravel around the pot, a toro lantern in the back left corner and a mossy rock in the front right. */
	private static void gravel(CitadelCanvas c, Plot p, int w, int d) {
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (u >= 1 && u <= w - 2 && v >= 1 && v <= d - 2) {
					continue;
				}
				int r = p.hash(u, 0, v, 8);
				set(c, p, u, 0, v, r < 5 ? b(Blocks.GRAVEL) : r < 7 ? b(Blocks.ANDESITE) : b(Blocks.COBBLESTONE));
			}
		}
		set(c, p, 0, 0, d - 1, b(Blocks.MOSSY_STONE_BRICKS));
		set(c, p, 0, 1, d - 1, b(Blocks.STONE_BRICK_WALL));
		set(c, p, 0, 2, d - 1, b(Blocks.LANTERN));
		set(c, p, 0, 3, d - 1, slab(Blocks.STONE_BRICK_SLAB));
		set(c, p, w - 1, 0, 0, b(Blocks.MOSSY_COBBLESTONE));
		set(c, p, w - 1, 1, 0, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		set(c, p, w - 2, 0, 0, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
	}

	/** A thick line of wood: vertical with horizontal links where it steps sideways. */
	private static void line(CitadelCanvas c, Plot p, int ua, int ya, int va, int ub, int yb, int vb) {
		int steps = Math.max(1, yb - ya);
		int pu = ua;
		int pv = va;
		for (int i = 0; i <= steps; i++) {
			int y = ya + i;
			int tu = ua + rnd((double) (ub - ua) * i / steps);
			int tv = va + rnd((double) (vb - va) * i / steps);
			// one-wide L: first along u at the old v, then along v at the new u
			for (int u = Math.min(pu, tu); u <= Math.max(pu, tu); u++) {
				boolean move = pu != tu;
				set(c, p, u, y, pv, WOOD.setValue(BlockStateProperties.AXIS, move ? Direction.Axis.X : Direction.Axis.Y));
			}
			for (int v = Math.min(pv, tv); v <= Math.max(pv, tv); v++) {
				boolean move = pv != tv;
				set(c, p, tu, y, v, WOOD.setValue(BlockStateProperties.AXIS, move ? Direction.Axis.Z : Direction.Axis.Y));
			}
			pu = tu;
			pv = tv;
		}
	}

	private static int rnd(double x) {
		return (int) (Math.signum(x) * Math.floor(Math.abs(x) + 0.5));
	}

	private static int clamp(int x, int lo, int hi) {
		return Math.max(lo, Math.min(hi, x));
	}

	private static void set(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
