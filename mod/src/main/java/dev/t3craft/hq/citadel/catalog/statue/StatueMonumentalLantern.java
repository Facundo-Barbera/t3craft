package dev.t3craft.hq.citadel.catalog.statue;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A monumental stone lantern (tōrō) for the hub's promenade: the lantern of a city square, 11 high on a
 * 3 × 3 or 4 × 4 footprint (8 high on a 2 × 2). Read bottom to top:
 * <ul>
 * <li><b>kiso</b> (foot): a one-block plinth, corner blocks with the potted offerings and seat steps between
 *     them (the stairs face the lantern, so they are benches for whoever sits with the lantern behind);</li>
 * <li><b>sao</b> (shaft): a slender post (a 2 × 2 stack on even footprints) on a plinth block that carries the
 *     name plaque, a chiselled band under the platform;</li>
 * <li><b>chūdai</b> (platform): upside-down stairs that flare out under the light box;</li>
 * <li><b>hibukuro</b> (light chamber): two layers of froglight, strong enough to light the whole promenade,
 *     behind tinted panes between wall posts at the corners; no open flame;</li>
 * <li><b>kasa</b> (roof): copper stairs rising to the middle, the corners curled up with outer-corner stairs
 *     (square) or cut as a hip (chamfered), one or two eaves by seed;</li>
 * <li><b>hōju</b> (jewel): a lightning rod on a single column, a small stepped pyramid on a 2 × 2.</li>
 * </ul>
 * Seed: the stone (mossy bricks with oxidised copper / pale diorite with bright copper / dark deepslate with
 * weathered copper), the roof (square or chamfered, single or double eave from h ≥ 11) and the plaque's text.
 * Palette per surface: 3–5 materials. Unlike the guardian (a figure) and the obelisk (a tapering die with
 * braziers) this one is a slender shaft under a broad, glowing head.
 *
 * <p>Size: 2–5 × 2–5 × 8+. 3+ × 3+ with h ≥ 10: the full tōrō (taller plots keep the extra height empty
 * beyond 12); otherwise a compact lamp in a 2 × 2.
 */
public final class StatueMonumentalLantern implements Variant {
	private static final String[][] MOTTOS = {
			{"", "Una luz para", "cada camino", ""},
			{"", "t3craft", "Ciudadela", ""},
			{"", "Cada proyecto", "una luz", ""},
			{"", "Sigue la luz", "al centro", ""},
	};

	@Override
	public String id() {
		return "statue_monumental_lantern";
	}

	@Override
	public Family family() {
		return Family.STATUE;
	}

	@Override
	public int minW() {
		return 2;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 5;
	}

	// ------------------------------------------------------------------ palette

	/** The stone and copper of one lantern, picked by the seed. */
	private static final class Pal {
		final int mat;
		final Block baseBlock;
		final Block baseStairs;
		final Block plinth;
		final Block wall;
		final Block shaft;
		final Block node;
		final Block platStairs;
		final Block platBlock;
		final int copper;
		final DyeColor glass;
		final Block light;
		final Plot p;

		Pal(Plot p) {
			this.p = p;
			this.mat = p.hash(7, 7, 7, 3);
			switch (mat) {
				case 0 -> { // mossy stone, oxidised copper
					baseBlock = Blocks.DEEPSLATE_BRICKS;
					baseStairs = Blocks.DEEPSLATE_BRICK_STAIRS;
					plinth = Blocks.POLISHED_DEEPSLATE;
					wall = Blocks.STONE_BRICK_WALL;
					shaft = Blocks.STONE_BRICKS;
					node = Blocks.CHISELED_STONE_BRICKS;
					platStairs = Blocks.STONE_BRICK_STAIRS;
					platBlock = Blocks.CHISELED_STONE_BRICKS;
					copper = 2;
					glass = DyeColor.YELLOW;
					light = Blocks.OCHRE_FROGLIGHT;
				}
				case 1 -> { // pale stone, bright bronze
					baseBlock = Blocks.POLISHED_ANDESITE;
					baseStairs = Blocks.POLISHED_ANDESITE_STAIRS;
					plinth = Blocks.CALCITE;
					wall = Blocks.DIORITE_WALL;
					shaft = Blocks.POLISHED_DIORITE;
					node = Blocks.CHISELED_QUARTZ_BLOCK;
					platStairs = Blocks.POLISHED_DIORITE_STAIRS;
					platBlock = Blocks.CHISELED_QUARTZ_BLOCK;
					copper = 0;
					glass = DyeColor.PINK;
					light = Blocks.PEARLESCENT_FROGLIGHT;
				}
				default -> { // dark stone, weathered copper
					baseBlock = Blocks.DEEPSLATE_TILES;
					baseStairs = Blocks.DEEPSLATE_TILE_STAIRS;
					plinth = Blocks.POLISHED_DEEPSLATE;
					wall = Blocks.POLISHED_DEEPSLATE_WALL;
					shaft = Blocks.POLISHED_DEEPSLATE;
					node = Blocks.CHISELED_DEEPSLATE;
					platStairs = Blocks.DEEPSLATE_BRICK_STAIRS;
					platBlock = Blocks.CHISELED_DEEPSLATE;
					copper = 1;
					glass = DyeColor.ORANGE;
					light = Blocks.OCHRE_FROGLIGHT;
				}
			}
		}

		boolean mossy(int u, int y, int v) {
			return mat == 0 && p.hash(u, y, v, 3) == 0;
		}

		BlockState base(int u, int v) {
			if (mat == 0 && p.hash(u, 0, v, 5) == 0) {
				return b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			}
			return b(baseBlock);
		}

		BlockState shaftBlock(int u, int y, int v) {
			if (mossy(u, y, v)) {
				return b(Blocks.MOSSY_STONE_BRICKS);
			}
			if (mat == 0 && p.hash(u, y, v, 7) == 0) {
				return b(Blocks.CRACKED_STONE_BRICKS);
			}
			return b(shaft);
		}

		BlockState post(int u, int y, int v) {
			return b(mossy(u, y, v) ? Blocks.MOSSY_STONE_BRICK_WALL : wall);
		}

		BlockState platStairs(int u, int y, int v, Direction facing) {
			return stairs(mossy(u, y, v) ? Blocks.MOSSY_STONE_BRICK_STAIRS : platStairs, facing);
		}

		BlockState cuBlock() {
			var s = Blocks.CUT_COPPER.waxed();
			return b(copper == 0 ? s.unaffected() : copper == 1 ? s.weathered() : s.oxidized());
		}

		Block cuStairs() {
			var s = Blocks.CUT_COPPER_STAIRS.waxed();
			return copper == 0 ? s.unaffected() : copper == 1 ? s.weathered() : s.oxidized();
		}

		BlockState cuSlab() {
			var s = Blocks.CUT_COPPER_SLAB.waxed();
			return slab(copper == 0 ? s.unaffected() : copper == 1 ? s.weathered() : s.oxidized());
		}

		BlockState pane() {
			return Blocks.STAINED_GLASS_PANE.pick(glass).defaultBlockState();
		}
	}

	// ------------------------------------------------------------------ build

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Pal pal = new Pal(p);
		if (p.w() < 3 || p.d() < 3 || p.h() < 10) {
			compact(c, p, pal);
			return;
		}
		int w = p.w();
		int d = p.d();
		int ul = (w - 1) / 2;
		int ur = w / 2;
		int vl = (d - 1) / 2;
		int vr = d / 2;
		boolean even = ul != ur || vl != vr;
		boolean dbl = p.h() >= 11 && p.hash(3, 3, 3, 2) == 0;
		boolean chamfer = p.hash(5, 5, 5, 2) == 1;
		int r = dbl ? 3 : 2;
		int top = Math.min(p.h(), 12) - 1;
		int roofBase = top - r; // roofTop = top - 1; the apex (rod or pyramid) is at top
		int ch0 = roofBase - 2;
		int ch1 = roofBase - 1;
		int chu = roofBase - 3;
		int shaftTop = chu - 1;

		// --- kiso: a one-block foot over the whole plot; corner blocks, seat steps between them
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				if (eu && ev) {
					p.set(c, u, 0, v, pal.base(u, v));
				} else if (ev) {
					p.set(c, u, 0, v, stairs(pal.baseStairs, v == 0 ? Direction.SOUTH : Direction.NORTH));
				} else if (eu) {
					p.set(c, u, 0, v, stairs(pal.baseStairs, u == 0 ? Direction.EAST : Direction.WEST));
				} else {
					p.set(c, u, 0, v, pal.base(u, v));
				}
			}
		}
		// [finish] the offerings: potted plants on the corner blocks, moss on the others
		int[][] corners = {{0, 0}, {w - 1, 0}, {w - 1, d - 1}, {0, d - 1}};
		int first = p.hash(1, 1, 1, 2); // which diagonal holds the pots
		BlockState[] pots = {b(Blocks.POTTED_FLOWERING_AZALEA), b(Blocks.POTTED_CHERRY_SAPLING), b(Blocks.POTTED_AZALEA), b(Blocks.POTTED_PINK_TULIP)};
		for (int i = 0; i < 4; i++) {
			if (i % 2 == first) {
				p.set(c, corners[i][0], 1, corners[i][1], pots[(i + p.hash(2, 2, 2, 2)) % 4]);
			} else if (pal.mat == 0) {
				p.set(c, corners[i][0], 1, corners[i][1], b(Blocks.MOSS_CARPET));
			}
		}

		// --- sao: a plinth block, the shaft, a chiselled band under the platform
		for (int y = 1; y <= shaftTop; y++) {
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					BlockState s;
					if (y == 1) {
						s = b(pal.plinth);
					} else if (y == shaftTop) {
						s = b(pal.node);
					} else if (even) {
						s = pal.shaftBlock(u, y, v);
					} else {
						s = pal.post(u, y, v);
					}
					p.set(c, u, y, v, s);
				}
			}
		}
		// [finish] the plaque: on the front of the plinth block, facing out
		String[] lines = MOTTOS[p.hash(4, 4, 4, MOTTOS.length)];
		boolean darkWood = pal.mat != 1;
		for (int u = ul; u <= ur; u++) {
			if (even && u != ul) {
				continue; // one plaque, not two half ones
			}
			p.put(c, u, 1, vl - 1, b(darkWood ? Blocks.DARK_OAK_WALL_SIGN : Blocks.BIRCH_WALL_SIGN)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);
			BlockPos sp = p.world(u, 1, vl - 1);
			c.data.put(sp.asLong(), SignTags.sign(darkWood ? DyeColor.WHITE : DyeColor.BLACK, false, lines));
			c.fixtures.add(sp.asLong());
		}

		// --- the head: the ring of cells round the core, layer by layer
		BlockState pane = pal.pane();
		BlockState light = b(pal.light);
		for (int v = vl - 1; v <= vr + 1; v++) {
			for (int u = ul - 1; u <= ur + 1; u++) {
				boolean core = u >= ul && u <= ur && v >= vl && v <= vr;
				boolean corner = (u < ul || u > ur) && (v < vl || v > vr);
				Direction ew = u < ul ? Direction.EAST : Direction.WEST; // towards the core along u
				Direction ns = v < vl ? Direction.SOUTH : Direction.NORTH; // towards the core along v
				if (core) {
					p.set(c, u, chu, v, b(pal.platBlock));
					p.set(c, u, ch0, v, light);
					p.set(c, u, ch1, v, light);
					for (int k = 0; k < r; k++) {
						p.set(c, u, roofBase + k, v, pal.cuBlock());
					}
					continue;
				}
				// chūdai: upside-down stairs flaring out; corner cells a top slab
				if (corner) {
					p.set(c, u, chu, v, hip(pal.platStairs, ew, ns, true));
				} else {
					Direction in = v < vl || v > vr ? ns : ew;
					p.set(c, u, chu, v, pal.platStairs(u, chu, v, in).setValue(StairBlock.HALF, Half.TOP));
				}
				// hibukuro: wall posts at the corners, panes between them
				if (corner) {
					p.set(c, u, ch0, v, pal.post(u, ch0, v));
					p.set(c, u, ch1, v, pal.post(u, ch1, v));
				} else {
					boolean alongU = v < vl || v > vr; // a north or south side: the pane runs west-east
					BlockState g = pane.setValue(BlockStateProperties.WEST, alongU).setValue(BlockStateProperties.EAST, alongU)
							.setValue(BlockStateProperties.NORTH, !alongU).setValue(BlockStateProperties.SOUTH, !alongU);
					p.set(c, u, ch0, v, g);
					p.set(c, u, ch1, v, g);
				}
				// kasa
				if (corner) {
					Direction oa = ew.getOpposite();
					Direction ob = ns.getOpposite();
					for (int k = 0; k < r; k++) {
						int y = roofBase + k;
						boolean last = k == r - 1;
						BlockState s = null;
						if (chamfer) {
							// the hip: the corner cell falls away on both sides
							if (k == 0) {
								s = hip(pal.cuStairs(), ew, ns, false);
							} else if (k == 1 && dbl) {
								s = pal.cuSlab();
							}
						} else if (last) {
							s = hip(pal.cuStairs(), oa, ob, false); // the curled-up tip
						} else {
							s = pal.cuBlock();
						}
						if (s != null) {
							p.set(c, u, y, v, s);
						}
					}
				} else {
					Direction in = v < vl || v > vr ? ns : ew;
					p.set(c, u, roofBase, v, stairs(pal.cuStairs(), in));
					if (dbl) {
						p.set(c, u, roofBase + 1, v, pal.cuSlab());
					}
				}
			}
		}

		// --- hōju: a rod on a single column, a small pyramid on a 2-wide core
		if (!even) {
			p.set(c, ul, top, vl, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		} else {
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					Direction a = u == ul ? Direction.EAST : Direction.WEST;
					Direction bb = v == vl ? Direction.SOUTH : Direction.NORTH;
					p.set(c, u, top, v, hip(pal.cuStairs(), a, bb, false));
				}
			}
		}
	}

	/** A stair whose single raised quarter is at the corner between {@code a} and {@code bb}. */
	private static BlockState hip(Block stair, Direction a, Direction bb, boolean top) {
		StairsShape shape = bb == a.getClockWise() ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
		return stairs(stair, a).setValue(StairBlock.SHAPE, shape).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	// ------------------------------------------------------------------ compact (2 x 2)

	/**
	 * The lantern in the centred 2 × 2 cells: a foot of four blocks, a 2-tall shaft with a chiselled band, a
	 * platform, a froglight lamp, a hipped copper roof. Used for 2-wide or 2-deep plots, and below 10 high.
	 */
	private static void compact(CitadelCanvas c, Plot p, Pal pal) {
		int u0 = (p.w() - 2) / 2;
		int v0 = (p.d() - 2) / 2;
		int top = Math.min(p.h(), 9) - 1;
		int roof = top - 1;
		int lamp = roof - 1;
		int plat = lamp - 1;
		int shaftTop = plat - 1;
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < 2; i++) {
				int u = u0 + i;
				int v = v0 + j;
				Direction a = i == 0 ? Direction.EAST : Direction.WEST;
				Direction bb = j == 0 ? Direction.SOUTH : Direction.NORTH;
				p.set(c, u, 0, v, pal.base(u, v));
				for (int y = 1; y <= shaftTop; y++) {
					p.set(c, u, y, v, y == 1 ? b(pal.plinth) : y == shaftTop ? b(pal.node) : pal.shaftBlock(u, y, v));
				}
				// [depth] the platform flares (a top-half hip), the roof is a hip, the apex a second one
				p.set(c, u, plat, v, hip(pal.platStairs, a, bb, true));
				p.set(c, u, lamp, v, b(pal.light));
				p.set(c, u, roof, v, pal.cuBlock());
				p.set(c, u, top, v, hip(pal.cuStairs(), a, bb, false));
			}
		}
	}
}
