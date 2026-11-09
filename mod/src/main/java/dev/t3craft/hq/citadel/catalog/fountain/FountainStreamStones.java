package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.OUT;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.RIM;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.put;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Meandering stream: a two-wide water channel that winds across the plot from a tall mossy rock head
 * (azalea and cherry bush on top) to a small round pool with lily pads at the other end. Pale
 * stepping stones zigzag across it; the banks are soft, built of moss, grass and coarse dirt over a dark
 * stone footing, and planted with ferns, flowers and petals. A three-bodied stone lantern, a bamboo
 * grove and a young cherry tree complete it.
 *
 * <pre>
 *  top view (front = bottom)
 *   . . ▓▓ . ~ ~ ~ . . ◯◯ T     ▓ rock head (3-4 high)   ◯ round pool
 *   . ▓▓▓▓ ~ ● ~ ~ ● ~ ◯◯ .     ● stepping stones, T cherry
 *   . ▓▓ . . ~ ~ ● ~ ~ . . L    L stone lantern
 * </pre>
 *
 * Size 7–9 × 6–7 × 4+. The channel follows a sine curve one cell either way; every water cell is walled
 * by solid rim at y 1 so no source can flow out.
 */
public final class FountainStreamStones implements Variant {
	@Override
	public String id() {
		return "fountain_stream_stones";
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
		return 6;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 7;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		boolean flip = p.hash(0, 0, 0, 2) == 1; // which side holds the head
		int[][] k = new int[w][d];
		boolean[][] reserved = new boolean[w][d];
		double phase = p.hash(0, 1, 0, 6);
		int vc0 = (d - 2) / 2;

		// --- 1. mask: channel columns i = 2..w-2 counted from the head; the last two columns are the pool
		int[] vcs = new int[w + 1];
		for (int i = 2; i <= w - 2; i++) {
			int u = flip ? w - 1 - i : i;
			int vc = vc0 + (int) Math.round(Math.sin(phase + i * 0.8));
			vcs[u] = vc;
			boolean pool = i >= w - 3;
			int lo = pool ? vc - 1 : vc;
			int hi = pool ? vc + 2 : vc + 1;
			for (int v = Math.max(1, lo); v <= Math.min(d - 2, hi); v++) {
				k[u][v] = WATER;
			}
		}
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == OUT && PondKit.touches(k, u, v, WATER)) {
					k[u][v] = RIM;
				}
			}
		}

		// --- 2. basin: dark stone footing (gradient), soft earthy top, plants on it; bed of mud, clay, gravel
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == WATER) {
					p.set(c, u, 0, v, PondKit.bed(p, u, v, PondKit.touches(k, u, v, RIM)));
					p.set(c, u, 1, v, PondKit.water());
				} else if (k[u][v] == RIM) {
					p.set(c, u, 0, v, PondKit.rimBase(p, u, v));
					int r = p.hash(u, 1, v, 10);
					BlockState top = r < 4 ? b(Blocks.MOSS_BLOCK) : r < 6 ? b(Blocks.GRASS_BLOCK) : r < 7 ? b(Blocks.COARSE_DIRT) : PondKit.rimTop(p, u, 1, v);
					p.set(c, u, 1, v, top);
					BlockState plant = r < 7 ? PondKit.shorePlant(p, u, v) : null; // [finish] banks are planted, not paved
					if (plant != null) {
						put(c, p, u, 2, v, plant);
					}
				}
			}
		}

		// --- 3. the head: a tall mossy mound where the stream begins, azalea and cherry bush on top
		int hu = flip ? w - 1 : 0;
		int hi = flip ? -1 : 1;
		int lowRow = Math.max(0, vcs[flip ? w - 3 : 2] - 1);
		int highRow = Math.min(d - 1, vcs[flip ? w - 3 : 2] + 2);
		for (int v = lowRow; v <= highRow; v++) {
			for (int col = 0; col < 2; col++) {
				int u = hu + hi * col;
				int tall = Math.min(p.h() - 2, (col == 0 ? 4 : 3) - (p.hash(u, 3, v, 3) == 0 ? 1 : 0));
				for (int y = 2; y <= tall; y++) {
					int below = tall - y;
					p.set(c, u, y, v, below == 0 ? (p.hash(u, y, v, 3) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.MOSS_BLOCK))
							: below == 1 ? b(Blocks.MOSSY_COBBLESTONE) : y == 2 ? b(Blocks.TUFF) : b(Blocks.ANDESITE));
				}
				if (k[u][v] == OUT) { // footing where the mound stands off the water
					p.set(c, u, 0, v, PondKit.rimBase(p, u, v));
					p.set(c, u, 1, v, PondKit.rimTop(p, u, 1, v));
					reserved[u][v] = true;
				}
				BlockState cap = switch (p.hash(u, 4, v, 4)) {
					case 0 -> b(Blocks.FLOWERING_AZALEA);
					case 1 -> CitadelCanvas.leaves(Blocks.CHERRY_LEAVES);
					case 2 -> b(Blocks.AZALEA);
					default -> b(Blocks.FERN);
				};
				put(c, p, u, tall + 1, v, cap);
			}
		}

		// --- 4. stepping stones zigzag across the channel (pale polished andesite, a moss cap now and then)
		for (int i = 3; i <= Math.min(6, w - 3); i++) {
			int u = flip ? w - 1 - i : i;
			int v = vcs[u] + (i % 2);
			if (p.has(u, 1, v) && k[u][v] == WATER) {
				p.set(c, u, 1, v, p.hash(u, 1, v, 4) == 0 ? b(Blocks.POLISHED_DIORITE) : b(Blocks.POLISHED_ANDESITE));
				put(c, p, u, 2, v, p.hash(u, 2, v, 3) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.STONE_BRICK_SLAB));
				k[u][v] = RIM;
			}
		}
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == WATER && p.hash(u, 5, v, 3) == 0) {
					p.set(c, u, 2, v, b(Blocks.LILY_PAD));
				}
			}
		}

		// --- 5. the lantern on the pool side, a cherry behind it, a bamboo grove behind the stream
		int lu = flip ? 0 : w - 1;
		if (k[lu][0] == OUT) {
			PondKit.toro(c, p, lu, 0);
			reserved[lu][0] = true;
		}
		if (k[lu][d - 1] == OUT) {
			PondKit.cherry(c, p, lu, d - 1, flip ? 1 : -1);
			reserved[lu][d - 1] = true;
		}
		PondKit.grove(c, p, k, reserved, w / 2 - 1, 1, d - 1, -1, 6);

		// --- 6. banks of the margin: steps, ferns, flowers
		PondKit.shore(c, p, k, reserved);
	}
}
