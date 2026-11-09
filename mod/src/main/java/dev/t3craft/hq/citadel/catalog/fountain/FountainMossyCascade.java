package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.OUT;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.RIM;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.put;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.putLayer;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Rock pool with a mossy cascade: a crag of mixed stone (deepslate and tuff at the foot, andesite and
 * stone in the middle, mossy cobble and moss blocks on top) rises at the back, a spring tucked in a
 * notch of its crest spills over a lip and falls down the face into a plunge pool ringed with mossy
 * rim stones. Splash rocks and a few islets break the pool, vines hang over the face, cherry blossom
 * and azalea grow on top.
 *
 * <pre>
 *  section through the fall (v → back)              top view
 *            ┌spring                               ░ crag (v d-2, d-1) ░ ░ ░
 *      crag ▓▓▓▓▓▓▓                                  ▒ ░ ▒ ▓~▓ ▒ ░ ▒    ~ fall, ▓ splash rocks
 *      ▓▓▓▓▓▓▓│~  falling water (y 2..top-1)         ▒ ~ ~ ● ~ ~ ▒      ● islet
 *      ▓▓▓▓▓▓▓│~▒ splash rock                        ▒▒▒▒▒▒▒▒▒▒▒▒▒▒ rim
 *  pool ~~~~~~~~~~~   bed y0, water y1
 * </pre>
 *
 * Size 7–9 × 8–9 × 6+. The crag is up to 5 high (needs 2 spare blocks above the spring).
 */
public final class FountainMossyCascade implements Variant {
	@Override
	public String id() {
		return "fountain_mossy_cascade";
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
		return 8;
	}

	@Override
	public int minH() {
		return 6;
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
		int top = Math.min(p.h() - 2, 5);
		int fw = w >= 8 ? 2 : 1; // the fall is two blocks wide on bigger plots
		int uf = Math.max(2, Math.min(w - 2 - fw, (w - fw) / 2 + p.hash(0, 0, 0, 3) - 1)); // the fall's first column
		int[][] k = new int[w][d];
		boolean[][] reserved = new boolean[w][d];

		// --- 1. pool mask: rim ring, water inside (front corners rounded), the crag fills the two back rows
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (v >= d - 2) {
					reserved[u][v] = true;
				} else if (u >= 2 && u <= w - 3 && v >= 2 && v <= d - 3 && !(v == 2 && (u == 2 || u == w - 3))) {
					k[u][v] = WATER;
				} else if (u >= 1 && u <= w - 2 && v >= 1) {
					k[u][v] = RIM;
				}
			}
		}

		// --- 2. basin
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (k[u][v] == WATER) {
					p.set(c, u, 0, v, PondKit.bed(p, u, v, PondKit.touches(k, u, v, RIM)));
					p.set(c, u, 1, v, PondKit.water());
				} else if (k[u][v] == RIM) {
					p.set(c, u, 0, v, PondKit.rimBase(p, u, v));
					p.set(c, u, 1, v, PondKit.rimTop(p, u, 1, v));
					switch (p.hash(u, 7, v, 8)) { // [depth] caps and moss break the rim line
						case 0 -> put(c, p, u, 2, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
						case 1 -> put(c, p, u, 2, v, b(Blocks.MOSSY_COBBLESTONE));
						case 2 -> put(c, p, u, 2, v, b(Blocks.MOSS_CARPET));
						default -> {
						}
					}
				}
			}
		}

		// --- 3. the crag: gradient dark foot → mossy top, heights fall away from the spring
		for (int u = 0; u < w; u++) {
			for (int v = d - 2; v < d; v++) {
				int hgt = crag(p, u, v, uf, fw, top, d);
				boolean spring = u >= uf && u < uf + fw && v == d - 2;
				int fillTo = spring ? top - 1 : hgt;
				for (int y = 0; y <= fillTo; y++) {
					p.set(c, u, y, v, rock(p, u, y, v, fillTo - y));
				}
				if (!spring) {
					plantTop(c, p, u, hgt + 1, v);
				}
			}
		}
		// shoulders: rock tumbling down to the side margins in front of the crag
		for (int u : new int[] {0, w - 1}) {
			int sh = Math.max(2, crag(p, u, d - 2, uf, fw, top, d) - 1);
			for (int y = 0; y <= sh; y++) {
				p.set(c, u, y, d - 3, rock(p, u, y, d - 3, sh - y));
			}
			plantTop(c, p, u, sh + 1, d - 3);
			reserved[u][d - 3] = true;
		}

		// --- 4. the spring, the lip and the fall; splash rocks seal the landing so nothing spreads
		List<int[]> splash = new ArrayList<>();
		splash.add(new int[] {uf - 1, d - 3});
		splash.add(new int[] {uf + fw, d - 3});
		for (int fu = uf; fu < uf + fw; fu++) {
			p.set(c, fu, top, d - 2, PondKit.water()); // spring source in the notch
			p.set(c, fu, top, d - 3, PondKit.water().setValue(LiquidBlock.LEVEL, 1)); // the lip
			for (int y = top - 1; y >= 2; y--) {
				p.set(c, fu, y, d - 3, PondKit.fallingWater());
			}
			// [depth] a mossy brow juts over the lip: an upside-down stair with its back to the crag
			put(c, p, fu, top + 1, d - 3, CitadelCanvas.stairs(Blocks.MOSSY_COBBLESTONE_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
			splash.add(new int[] {fu, d - 4});
		}
		for (int[] s : splash) {
			p.set(c, s[0], 1, s[1], PondKit.rimTop(p, s[0], 1, s[1]));
			p.set(c, s[0], 2, s[1], p.hash(s[0], 2, s[1], 2) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.ANDESITE));
			put(c, p, s[0], 3, s[1], p.hash(s[0], 3, s[1], 2) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.MOSSY_COBBLESTONE_SLAB));
			k[s[0]][s[1]] = RIM;
		}
		// the cherry that crowns the crag, and a boulder field instead of steps on one side
		int treeU = uf + fw / 2 + (p.hash(1, 0, 0, 2) == 0 ? -2 : 2);
		treeU = Math.max(1, Math.min(w - 2, treeU));
		int treeBase = crag(p, treeU, d - 1, uf, fw, top, d) + 1;
		for (int y = 0; y < 2; y++) {
			put(c, p, treeU, treeBase + y, d - 1, b(Blocks.CHERRY_LOG));
		}
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 0; dv++) {
				for (int dy = 1; dy <= 2; dy++) {
					if (!(du == 0 && dv == 0 && dy == 1) && p.hash(treeU + du, treeBase + dy, d - 1 + dv, 4) != 0 && p.has(treeU + du, treeBase + dy, d - 1 + dv)
							&& p.at(c, treeU + du, treeBase + dy, d - 1 + dv) == null) {
						put(c, p, treeU + du, treeBase + dy, d - 1 + dv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
		}
		int sideU = p.hash(2, 0, 0, 2) == 0 ? 0 : w - 1;
		for (int v = 1; v <= 3; v++) {
			if (k[sideU][v] == OUT && !reserved[sideU][v]) {
				PondKit.boulder(c, p, sideU, v, v == 2 ? 3 : 2);
				reserved[sideU][v] = true;
			}
		}

		// --- 5. vines over the face, islets and lily pads in the pool, lanterns on the front rim
		for (int u = 1; u <= w - 2; u++) {
			if (u >= uf - 1 && u <= uf + fw) {
				continue;
			}
			int faceTop = crag(p, u, d - 2, uf, fw, top, d);
			for (int y = faceTop - 1; y >= 2; y--) {
				int len = p.hash(u, y, 3, 6);
				if (len < 2 && p.hash(u, y + 1, 3, 3) != 0) {
					BlockState vine = b(Blocks.VINE).setValue(BlockStateProperties.SOUTH, true);
					putLayer(c, p, u, y, d - 3, vine, CitadelCanvas.DECOR);
					if (len == 0 && y > 2) {
						putLayer(c, p, u, y - 1, d - 3, vine, CitadelCanvas.DECOR);
					}
				}
			}
		}
		Random rng = p.rng(0xCA5CL);
		List<int[]> wet = new ArrayList<>();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d - 4; v++) {
				if (k[u][v] == WATER) {
					wet.add(new int[] {u, v});
				}
			}
		}
		for (int i = 0; i < 2 && wet.size() > 4; i++) { // stepping stones
			int[] a = wet.remove(rng.nextInt(wet.size()));
			p.set(c, a[0], 1, a[1], b(Blocks.MOSSY_COBBLESTONE));
			put(c, p, a[0], 2, a[1], p.hash(a[0], 2, a[1], 2) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.MOSSY_STONE_BRICK_SLAB));
			k[a[0]][a[1]] = RIM;
		}
		for (int u = 2; u <= w - 3; u++) {
			for (int v = 2; v <= d - 3; v++) {
				if (k[u][v] == WATER && p.hash(u, 5, v, 4) == 0 && !(u >= uf && u < uf + fw && v == d - 3)) {
					p.set(c, u, 2, v, b(Blocks.LILY_PAD));
				}
			}
		}
		put(c, p, 1, 2, 1, b(Blocks.LANTERN));
		put(c, p, w - 2, 2, 1, b(Blocks.LANTERN));

		// --- 6. shore: steps, ferns, azaleas and petals on the margin
		PondKit.shore(c, p, k, reserved);
	}

	/** Top y of the crag at {@code (u, v)}: highest around the spring, falling away with noise. */
	private static int crag(Plot p, int u, int v, int uf, int fw, int top, int d) {
		int off = u < uf ? uf - u : u >= uf + fw ? u - (uf + fw - 1) : 0;
		int hgt = off <= 1 ? top : top - off + (p.hash(u, 0, v, 3) == 0 ? -1 : 0);
		if (v == d - 1) { // the back row rises, with notches so the back wall is never a flat slab
			hgt = off <= 1 ? Math.min(top + 1, hgt + 1) : hgt + 1 - (p.hash(u, 5, v, 3) == 0 ? 2 : 0);
		}
		return Math.max(2, hgt);
	}

	/**
	 * Crag stone in horizontal strata (reads as rock, not noise): deepslate at the foot, a tuff band,
	 * andesite and stone in the body, mossy cobble under a moss cap. ~25% of cells take the neighbour band.
	 */
	private static BlockState rock(Plot p, int u, int y, int v, int below) {
		boolean odd = p.hash(u, y, v, 4) == 0;
		if (below == 0) {
			return p.hash(u, y, v, 10) < 7 ? b(Blocks.MOSS_BLOCK) : b(Blocks.MOSSY_COBBLESTONE);
		}
		if (below == 1) {
			return odd ? b(Blocks.ANDESITE) : b(Blocks.MOSSY_COBBLESTONE);
		}
		return switch (y) {
			case 0 -> odd ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> odd ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.TUFF);
			case 2 -> odd ? b(Blocks.TUFF) : b(Blocks.ANDESITE);
			case 3 -> odd ? b(Blocks.ANDESITE) : b(Blocks.STONE);
			default -> odd ? b(Blocks.STONE) : b(Blocks.MOSSY_COBBLESTONE);
		};
	}

	/** Growth on the crest: azaleas, ferns, grass, petals, now and then a pink bush. */
	private static void plantTop(CitadelCanvas c, Plot p, int u, int y, int v) {
		switch (p.hash(u, y, v, 8)) {
			case 0 -> put(c, p, u, y, v, b(Blocks.FLOWERING_AZALEA));
			case 1 -> put(c, p, u, y, v, b(Blocks.FERN));
			case 2 -> put(c, p, u, y, v, b(Blocks.SHORT_GRASS));
			case 3 -> put(c, p, u, y, v, b(Blocks.PINK_PETALS));
			case 4 -> {
				put(c, p, u, y, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				put(c, p, u, y + 1, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				put(c, p, u + 1, y, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
			}
			case 5 -> put(c, p, u, y, v, b(Blocks.AZALEA));
			default -> {
			}
		}
	}
}
