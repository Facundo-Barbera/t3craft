package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.DIRS;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.OUT;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.RIM;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.put;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Koi pond: an organic basin of mossy rock with a lime-washed bed (mud, clay, gravel), orange and white
 * koi swimming on the bottom, lily pads, and a small arched cherry bridge with stripped-log posts and
 * lanterns. A stone lantern, a bamboo clump, a mossy boulder cairn and a cherry tree fill the corners.
 *
 * <pre>
 *  top view (front = bottom)            side section across the bridge
 *   T . . . . . . C                          lantern  ┌─┐ crest slab y3
 *   . ~ ~ ~ ~ ~ ~ .                          post  ╷  ╱   ╲  ╷
 *   L ▓ ~ = = = ~ ▓ L    = bridge          rim ▓▓▓ ▒pier   pier▒ ▓▓▓   water y1, bed y0
 *   . ~ ~ ~ ~ ~ ~ .
 *   B . . . . . . O
 * </pre>
 *
 * Size 7–9 × 6–9 × 5+. The pond is an ellipse with a noisy edge sized from the plot; the bridge crosses
 * the middle row left to right and needs at least 3 water cells there.
 */
public final class FountainKoiPond implements Variant {
	@Override
	public String id() {
		return "fountain_koi_pond";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 5;
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
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int[][] k = new int[w][d];
		double cu = (w - 1) / 2.0;
		double cv = (d - 1) / 2.0;
		double ru = cu - 0.75;
		double rv = cv - 0.75;

		// --- 1. the pond mask: a noisy ellipse; the rim is every dry cell touching water
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double du = (u - cu) / ru;
				double dv = (v - cv) / rv;
				if (du * du + dv * dv + (p.hash(u, 0, v, 5) - 2) * 0.12 <= 1.0) {
					k[u][v] = WATER;
				}
			}
		}
		int vb = (d - 1) / 2 - 1; // the bridge row: off the axis, where the pond is a little narrower (shorter, steeper arch)
		if (waterIn(k, vb) < 3) {
			vb = (d - 1) / 2;
		}
		int wl = -1;
		int wr = -1;
		for (int u = 0; u < w; u++) {
			if (k[u][vb] == WATER) {
				wl = wl < 0 ? u : wl;
				wr = u;
			}
		}
		for (int u = Math.max(wl, 0); wl >= 0 && u <= wr; u++) {
			k[u][vb] = WATER; // the crossing row is one unbroken stretch of water
		}
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == OUT && touches(k, u, v, WATER)) {
					k[u][v] = RIM;
				}
			}
		}
		boolean bridge = wl >= 0 && wr - wl + 3 >= 5;
		int rl = wl - 1;
		int rr = wr + 1;

		// --- 2. basin: rim stone (dark base, mossy top, a few caps), bed under the water
		boolean[][] reserved = new boolean[w][d];
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				switch (k[u][v]) {
					case WATER -> {
						p.set(c, u, 0, v, PondKit.bed(p, u, v, touches(k, u, v, RIM)));
						p.set(c, u, 1, v, PondKit.water());
					}
					case RIM -> {
						p.set(c, u, 0, v, PondKit.rimBase(p, u, v));
						p.set(c, u, 1, v, PondKit.rimTop(p, u, 1, v));
						switch (p.hash(u, 7, v, 10)) { // [depth] slabs and moss break the rim line, a rare boulder
							case 0, 1 -> put(c, p, u, 2, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
							case 2 -> put(c, p, u, 2, v, b(Blocks.MOSSY_COBBLESTONE));
							case 3, 4 -> put(c, p, u, 2, v, b(Blocks.MOSS_CARPET));
							default -> {
							}
						}
					}
					default -> {
					}
				}
			}
		}

		// --- 3. koi on the bed (orange head, white or orange tail) and lily pads on the surface
		Random rng = p.rng(0x4B01L);
		List<int[]> wet = new ArrayList<>();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == WATER) {
					wet.add(new int[] {u, v});
				}
			}
		}
		for (int i = 0; i < 3 && !wet.isEmpty(); i++) {
			int[] a = wet.get(rng.nextInt(wet.size()));
			Direction t = DIRS[rng.nextInt(4)];
			int tu = a[0] + t.getStepX();
			int tv = a[1] + t.getStepZ();
			p.set(c, a[0], 0, a[1], CitadelCanvas.terracotta(i == 1 ? DyeColor.WHITE : DyeColor.ORANGE));
			if (p.has(tu, 0, tv) && k[tu][tv] == WATER) {
				p.set(c, tu, 0, tv, CitadelCanvas.terracotta(i == 2 ? DyeColor.ORANGE : DyeColor.WHITE));
			}
		}
		for (int[] a : wet) {
			boolean underBridge = bridge && Math.abs(a[1] - vb) <= 1;
			if (!underBridge && p.hash(a[0], 5, a[1], 4) == 0) {
				p.set(c, a[0], 2, a[1], b(Blocks.LILY_PAD));
			}
		}

		// --- 4. corners: stone lantern, bamboo, cairn, cherry tree (mirrored by the seed)
		boolean flip = p.hash(0, 0, 0, 2) == 1;
		int fu = flip ? w - 1 : 0; // the "first" side
		int ou = flip ? 0 : w - 1;
		if (k[fu][0] == OUT) {
			PondKit.toro(c, p, fu, 0);
			reserved[fu][0] = true;
		}
		for (int i = 0; i < 3; i++) { // cairn on the other front corner
			int bu = ou + (flip ? i / 2 : -(i / 2));
			int bv = i % 2 == 1 ? 1 : 0;
			if (p.has(bu, 0, bv) && k[bu][bv] == OUT && !reserved[bu][bv]) {
				PondKit.boulder(c, p, bu, bv, i == 0 ? 3 : 2 - i % 2);
				reserved[bu][bv] = true;
			}
		}
		PondKit.grove(c, p, k, reserved, fu, flip ? -1 : 1, d - 1, -1, 6);
		int treeU = flip ? 0 : w - 1;
		if (k[treeU][d - 1] == OUT && !reserved[treeU][d - 1]) {
			PondKit.cherry(c, p, treeU, d - 1, flip ? 1 : -1);
			reserved[treeU][d - 1] = true;
		}

		// --- 5. shore: mossy steps up to the rim, flat stone path at the bridge ends, plants elsewhere
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] != OUT || reserved[u][v]) {
					continue;
				}
				int rims = 0;
				Direction toRim = null;
				for (Direction dir : DIRS) {
					int nu = u + dir.getStepX();
					int nv = v + dir.getStepZ();
					if (p.has(nu, 0, nv) && k[nu][nv] == RIM) {
						rims++;
						toRim = dir;
					}
				}
				if (rims == 1) {
					p.set(c, u, 0, v, PondKit.shoreStep(p, u, v, toRim));
				} else if (rims > 1) {
					p.set(c, u, 0, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
				} else if (bridge && v == vb && (u < rl || u > rr)) {
					p.set(c, u, 0, v, slab(p.hash(u, 2, v, 2) == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB));
				} else {
					BlockState plant = PondKit.shorePlant(p, u, v);
					if (plant != null) {
						p.set(c, u, 0, v, plant);
					}
				}
			}
		}

		// --- 6. the arched bridge
		if (bridge) {
			buildBridge(c, p, k, vb, rl, rr);
		}
	}

	/** Water cells in row {@code v} of the mask. */
	private static int waterIn(int[][] k, int v) {
		int n = 0;
		for (int[] col : k) {
			n += col[v] == WATER ? 1 : 0;
		}
		return n;
	}

	private static boolean touches(int[][] k, int u, int v, int what) {
		for (Direction dir : DIRS) {
			int nu = u + dir.getStepX();
			int nv = v + dir.getStepZ();
			if (nu >= 0 && nu < k.length && nv >= 0 && nv < k[0].length && k[nu][nv] == what) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Cherry bridge along u at row {@code vb} from rim cell {@code rl} to {@code rr}: slab landing on the
	 * rim (y 2), a stair rise over a stone pier (y 2), then a slab crest (y 3). Side rows carry a
	 * stripped-log post with a lantern at each end, a fence on the rise and a log beam + fence on the crest.
	 */
	private static void buildBridge(CitadelCanvas c, Plot p, int[][] k, int vb, int rl, int rr) {
		int n = rr - rl + 1;
		double mid = (n - 1) / 2.0;
		for (int i = 0; i < n; i++) {
			int u = rl + i;
			int e = Math.min(i, n - 1 - i);
			Direction in = i < mid ? Direction.EAST : Direction.WEST; // towards the crest
			Direction out = in.getOpposite();
			// centre row: the deck
			if (e == 0) {
				put(c, p, u, 2, vb, slab(Blocks.CHERRY_SLAB));
			} else if (e == 1) {
				pier(c, p, u, vb);
				put(c, p, u, 2, vb, stairs(Blocks.CHERRY_STAIRS, in));
			} else {
				put(c, p, u, 3, vb, slab(Blocks.CHERRY_SLAB));
				if (e == 2 && n >= 7) { // [depth] arch ribs: upside-down stairs under the first crest cells
					put(c, p, u, 2, vb, stairs(Blocks.CHERRY_STAIRS, out).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
			// the posts live only at the ends: a 1-wide bridge leaves the pond open
			if (e == 0) {
				for (int sv = vb - 1; sv <= vb + 1; sv += 2) {
					plinth(c, p, u, sv);
					put(c, p, u, 2, sv, b(Blocks.STRIPPED_CHERRY_LOG)); // one-block base, then a slim fence post
					put(c, p, u, 3, sv, PondKit.fence(false, false, false, false));
					put(c, p, u, 4, sv, b(Blocks.LANTERN));
				}
			}
		}
		// one lantern hangs under the crest, over the koi
		int cu = rl + n / 2;
		PondKit.putLayer(c, p, cu, 2, vb, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	/** Masonry pier from the bed up to the water line, under a bridge stair. */
	private static void pier(CitadelCanvas c, Plot p, int u, int v) {
		put(c, p, u, 0, v, PondKit.rimBase(p, u, v));
		put(c, p, u, 1, v, PondKit.brick(p, u, 1, v));
	}

	/** Solid footing for a post, in case the cell is not part of the rim. */
	private static void plinth(CitadelCanvas c, Plot p, int u, int v) {
		put(c, p, u, 0, v, PondKit.rimBase(p, u, v));
		put(c, p, u, 1, v, PondKit.brick(p, u, 1, v));
	}
}
