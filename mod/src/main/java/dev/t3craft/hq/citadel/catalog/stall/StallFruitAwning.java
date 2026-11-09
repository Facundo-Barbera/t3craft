package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Fruit stall: a terracotta awning in a sober, citadel colour (dusty cherry, clay, sage, slate or ochre
 * with cream, picked by the seed), low-contrast stripes, rising from a low lip over the street to the
 * vendor's side and flying past the posts on the front and the sides. Dark-oak posts on deepslate
 * footings, an oak counter with produce in grouped runs (melons, pumpkins, berries) and a gap to step
 * through, a chest, barrels and a bench behind it, a hung signboard over the crates at the corner.
 * The front is open air: customers walk up to the counter between the corner crates.
 *
 * <pre>
 *  section (v →)                                plan (front at the bottom)
 *  y5-6                  ▄▄▄▄ awning (rises)     back    [chest][barrel][bench]
 *  y4        ▄▄▄▄▄▄▄▄▄▄                          aisle   [   carpet   ]
 *  y3  ▄▄▄▄                                      pv      P[ counter  ]P
 *  y1-2 crates  goods  goods                     v0..    [crate]    [crate]  (open air)
 *  y0   crates  P posts, counter, chest
 * </pre>
 *
 * Size: 4–8 × 3–7 × 5+ (the plots grow by √k with the citadel's scale).
 */
public final class StallFruitAwning implements Variant {
	@Override
	public String id() {
		return "stall_fruit_awning";
	}

	@Override
	public Family family() {
		return Family.STALL;
	}

	@Override
	public int minW() {
		return 4;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 7;
	}

	/**
	 * [palette] the awning's two stripe blocks per seed: sober terracottas of the citadel (cherry
	 * twice as often, it is the city's colour), each striped with cream or a close taupe, never a saturated wool.
	 */
	private static BlockState[] stripes(int pick) {
		return switch (pick) {
			case 0, 1 -> new BlockState[] {terracotta(DyeColor.PINK), terracotta(DyeColor.WHITE)}; // dusty cherry, cream
			case 2 -> new BlockState[] {terracotta(DyeColor.ORANGE), terracotta(DyeColor.WHITE)}; // clay, cream
			case 3 -> new BlockState[] {terracotta(DyeColor.GREEN), terracotta(DyeColor.WHITE)}; // sage, cream
			case 4 -> new BlockState[] {terracotta(DyeColor.LIGHT_BLUE), terracotta(DyeColor.WHITE)}; // slate, cream
			default -> new BlockState[] {terracotta(DyeColor.WHITE), terracotta(DyeColor.LIGHT_GRAY)}; // cream, taupe
		};
	}

	/** The seed-derived measures of one build, local coordinates. */
	private record Geo(int w, int d, int h, int ix, int pv, int bv, int fy, boolean flip) {
		/** Awning height over row {@code v}: flat over the counter and the street, then stepping up to the back. */
		int ay(int v) {
			return Math.min(h - 1, fy + Math.max(0, v - pv) / 2);
		}

		/** Mirrors a column when the seed says so (chest, bench and side stack change sides). */
		int mu(int u) {
			return flip ? w - 1 - u : u;
		}

		int lo() {
			return ix + 1;
		}

		int hi() {
			return w - 2 - ix;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int ix = w >= 6 ? 1 : 0; // posts inset: the awning flies one block past them on each side
		int pv = d >= 6 ? 2 : 1; // the counter row: the awning flies 1 block past it over the street, 2 on deep plots
		int fy = h >= 7 ? 4 : 3; // tall plots get a higher lip, more air under the cloth
		Geo g = new Geo(w, d, h, ix, pv, d - 1, fy, p.hash(5, 5, 5, 2) == 1);
		BlockState[] st = stripes(p.hash(3, 3, 3, 6));

		awning(c, p, g, st);
		posts(c, p, g);
		counter(c, p, g);
		apron(c, p, g);
		backWall(c, p, g, st);
		aisle(c, p, g);
		lights(c, p, g);
	}

	// ------------------------------------------------------------------ awning

	/**
	 * [depth] stripes run front to back across the whole width (past the posts); the cloth is flat over
	 * the counter and then steps up half a block per row, a cherry cornice under every riser.
	 */
	private static void awning(CitadelCanvas c, Plot p, Geo g, BlockState[] st) {
		for (int v = 0; v < g.d; v++) {
			int y = g.ay(v);
			for (int u = 0; u < g.w; u++) {
				p.set(c, u, y, v, st[u % 2]);
			}
			if (v > 0 && y > g.ay(v - 1) && y - 1 >= 3) { // [finish] a cornice of upside-down stairs closes each riser
				for (int u = 0; u < g.w; u++) {
					p.set(c, u, y - 1, v, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
	}

	// ------------------------------------------------------------------ posts

	/** [palette] a deepslate footing, a stripped cherry shaft and a plank cap; the back posts reach the higher cloth. */
	private static void posts(CitadelCanvas c, Plot p, Geo g) {
		for (int u : new int[] {g.ix, g.w - 1 - g.ix}) {
			post(c, p, g, u, g.pv);
			if (g.d >= 4) { // a 3-deep stall lets the cloth reach back unsupported: no wall of posts
				post(c, p, g, u, g.bv);
			}
		}
	}

	private static void post(CitadelCanvas c, Plot p, Geo g, int u, int v) {
		int top = g.ay(v) - 1;
		p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 1; y < top; y++) {
			p.set(c, u, y, v, b(Blocks.STRIPPED_CHERRY_LOG));
		}
		p.set(c, u, top, v, b(Blocks.CHERRY_PLANKS));
	}

	// ------------------------------------------------------------------ the counter and its goods

	/** One produce block by kind. The red fruit (berries, apples) is a solid red block, so nobody climbs it. */
	private static BlockState goods(int kind) {
		return switch (kind) {
			case 1 -> b(Blocks.PUMPKIN);
			case 2 -> b(Blocks.NETHER_WART_BLOCK);
			default -> b(Blocks.MELON);
		};
	}

	/** [depth] goods come in runs of two cells so each kind reads from the street. */
	private static int kindAt(Plot p, int u, int salt) {
		return p.hash(u / 2, salt, 1, 3);
	}

	/**
	 * The oak counter carries produce two blocks high (kinds alternate between the tiers), up to the cloth:
	 * the goods read from the street and no air pocket is left under the awning. A gap in a wide counter
	 * lets the vendor through; long counters hang a lantern at each end.
	 */
	private static void counter(CitadelCanvas c, Plot p, Geo g) {
		int gw = g.w >= 7 ? 2 : 0;
		int g0 = (g.w - gw) / 2;
		for (int u = g.lo(); u <= g.hi(); u++) {
			if (gw > 0 && u >= g0 && u < g0 + gw) {
				p.set(c, u, 0, g.pv, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
				continue;
			}
			p.set(c, u, 0, g.pv, p.hash(u, 0, g.pv, 4) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH) : b(Blocks.CHERRY_PLANKS));
			int k = kindAt(p, u, 1);
			p.set(c, u, 1, g.pv, goods(k));
			p.set(c, u, 2, g.pv, goods((k + 1 + p.hash(u / 2, 2, 1, 2)) % 3));
		}
		if (g.fy >= 4 && g.hi() - g.lo() >= 3) {
			for (int u : new int[] {g.lo(), g.hi()}) {
				p.put(c, u, g.fy - 1, g.pv, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}
	}

	// ------------------------------------------------------------------ the front: a stepped display of crates

	/**
	 * [depth] a row of crates heaped with produce stands in front of the counter, a step lower, so the goods
	 * rise in tiers from the street (crates, then the counter's two courses). A gap in the middle (1-2 wide)
	 * leaves the way to the counter open and customers walk the street in front. Every crate hangs an open
	 * cherry trapdoor from the lip: together they make the awning's fringe. The heap reaches {@code fy - 2}.
	 */
	private static void apron(CitadelCanvas c, Plot p, Geo g) {
		int gap = g.w % 2 == 0 ? 2 : 1;
		int g0 = (g.w - gap) / 2;
		BlockState fringe = b(Blocks.CHERRY_TRAPDOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
				.setValue(BlockStateProperties.OPEN, true)
				.setValue(BlockStateProperties.HALF, Half.BOTTOM);
		for (int u = 0; u < g.w; u++) {
			if (u >= g0 && u < g0 + gap) {
				continue;
			}
			p.set(c, u, 0, 0, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			int k = kindAt(p, u, 7);
			for (int y = 1; y <= g.fy - 2; y++) {
				p.set(c, u, y, 0, goods((k + y - 1) % 3));
			}
			p.set(c, u, g.fy - 1, 0, fringe);
		}
	}

	// ------------------------------------------------------------------ behind the counter

	/**
	 * A chest first, barrels with produce next, and a bench at the end (stairs facing the counter). When the
	 * cloth is higher than 3, a dark-oak header beam spans the back over everything but the bench and the cloth drops to it.
	 */
	private static void backWall(CitadelCanvas c, Plot p, Geo g, BlockState[] st) {
		int n = g.hi() - g.lo() + 1;
		boolean beam = g.ay(g.bv) > 3;
		for (int k = 0; k < n; k++) {
			int u = g.mu(g.lo() + k);
			if (k == 0) {
				p.set(c, u, 0, g.bv, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				p.set(c, u, 1, g.bv, b(Blocks.LANTERN));
			} else if (k >= 2 && k >= n - 2 && n >= 3) {
				p.set(c, u, 0, g.bv, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH)); // the vendor's bench
				continue;
			} else {
				p.set(c, u, 0, g.bv, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH));
				p.set(c, u, 1, g.bv, goods(kindAt(p, u, 5)));
			}
			if (beam) {
				p.set(c, u, 3, g.bv, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // [depth] header beam
			}
		}
		// [depth] a tall plot's cloth drops at the back as a plain skirt down to the beam, across the whole width
		// (posts excepted), so nobody can stand on the beam and the back reads as cloth, not as a wall
		for (int u = 0; u < g.w; u++) {
			if (u == g.ix || u == g.w - 1 - g.ix) {
				continue;
			}
			for (int y = 4; y <= g.ay(g.bv) - 2; y++) {
				p.set(c, u, y, g.bv, st[0]);
			}
		}
	}

	private static void aisle(CitadelCanvas c, Plot p, Geo g) {
		for (int v = g.pv + 1; v < g.bv; v++) {
			for (int u = g.lo(); u <= g.hi(); u++) {
				p.set(c, u, 0, v, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
			}
		}
		if (g.bv - g.pv >= 2) {
			// [finish] a hay pile with a pumpkin on the flying side of the awning (the side opposite the chest),
			// as high as the cloth allows so no air pocket is left under it; wide plots add a crate of produce beside it
			int su = g.flip ? 0 : g.w - 1;
			int top = g.ay(g.pv + 1) - 2;
			for (int y = 0; y < top; y++) {
				p.set(c, su, y, g.pv + 1, b(Blocks.HAY_BLOCK));
			}
			p.set(c, su, top, g.pv + 1, b(Blocks.PUMPKIN));
			int cv = g.pv + 2;
			if (g.ix == 1 && cv < g.bv) {
				int ctop = g.ay(cv) - 2;
				p.set(c, su, 0, cv, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
				for (int y = 1; y <= ctop; y++) {
					p.set(c, su, y, cv, goods((p.hash(su, 4, cv, 3) + y) % 3));
				}
			}
		}
	}

	// ------------------------------------------------------------------ light

	private static void lights(CitadelCanvas c, Plot p, Geo g) {
		BlockState lamp = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int v = g.pv + 1; v < g.bv; v += 2) {
			int y = g.ay(v) - 1;
			if (y >= 3) { // never under 3 blocks of air: the aisle stays walkable
				p.put(c, g.w / 2, y, v, lamp, CitadelCanvas.HANGING);
			}
		}
	}
}
