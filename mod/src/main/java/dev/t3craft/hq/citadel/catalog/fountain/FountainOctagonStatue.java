package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.FALL;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.away;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.oct;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stair;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.toward;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A monumental octagonal fountain: a raised octagonal plaza with a seat-high stair rim round the pool, a stepped
 * dais in the water carrying a figure (guardian, crane or finial pillar), gargoyle posts on the rim that pour into
 * the pool, rings of paving, benches facing the water, and lamps and hedges at the corners.
 *
 * <p>Concept: the plaza fountain of a castle town. Everything is an octagon (the square with its corners cut):
 * the whole plaza is a one-block platform stepped up from the ground on every side (stairs all round, so it is
 * walked up from anywhere); inside it, from the edge to the middle: the step ring, a ring of cherry benches whose
 * backs face out (they look at the fountain) with a 3-wide gap and a lamp on each side of the four entrances, the
 * paving in octagonal bands with a calcite line along each axis, the pool rim (a ring of stairs at bench height,
 * backs to the water) and the pool. In the water stands a stepped dais and on it the figure. Eight gargoyle
 * posts on the rim (the four compass points and the four diagonals) pour into the pool; the crane pours from its
 * beak and the pillar from its four capital arms.
 *
 * <pre>
 *  top view (half = 8, front at the bottom)          section along an axis
 *    L h . . . . . . . h L                              y6    ◆ figure
 *    h . b b b   b b b . h     b bench ring             y3-5  ▲▲  figure, gargoyle posts on the rim
 *    . b . : : : : : . b .     : paving bands           y2    ▟▙ dais  ┃ post
 *    . b : ▓ ≈ ≈ ≈ ▓ : b .     ▓ rim stairs             y1    ▓≈≈≈≈▓ water, rim seat
 *    .   : ▓ ≈ ● ≈ ▓ :   .     ● figure on its dais     y0    ▒▒▒▒▒▒▒ pool bed and paving
 *    . b : ▓ ≈ ≈ ≈ ▓ : b .     L lamp   h hedge         y-1   ground (stairs round the edge)
 * </pre>
 *
 * <p>Size: 9–41 × 9–41 × 7+. The radius of the pool, the width of the walk, the dais and the figure all follow the
 * plot: the figure is drawn on a 3 × 3 × 5 pattern that is scaled ×1, ×2 or ×3 in all three directions (as far as
 * the pool and the plot's height allow), never stretched; a plot that is higher than wide just stays free above.
 * The seed picks the figure (guardian with spear and shield, crane with raised wings, pillar with four spouts) and
 * the material (light stone, deepslate or weathered copper). Water is held in on every side (the rim has a stair
 * or a block on its four sides) and the streams fall inside the pool. No fire or lava anywhere.
 */
public final class FountainOctagonStatue implements Variant {
	private static final int MAX_HALF = 20;
	private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** The figures, 5 layers from the feet up, rows north (front) to south, columns west to east. */
	private static final String[][][] FIGURES = {
		// guardian: skirt, belt with a shield on the left and a spear on the right, head, helmet crest
		{{".D.", "DBD", ".D."}, {"...", "DAW", "..."}, {"...", "DBW", "..."}, {"...", "AHW", "..."}, {"...", ".AR", "..."}},
		// crane: one leg, body with wings and tail, wings up beside the neck, gold tips, head with a beak
		{{"...", ".W.", "..."}, {"...", "BBB", ".B."}, {"...", "BWB", "..."}, {"...", "AWA", "..."}, {".A.", ".H.", "..."}},
		// pillar: wide foot, shaft, capital with four arms, orb
		{{"DDD", "DBD", "DDD"}, {"...", ".B.", "..."}, {"...", ".B.", "..."}, {".B.", "BAB", ".B."}, {"...", ".A.", "..."}},
	};

	@Override
	public String id() {
		return "fountain_octagon_statue";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 7;
	}

	@Override
	public int maxW() {
		return 2 * MAX_HALF + 1;
	}

	@Override
	public int maxD() {
		return 2 * MAX_HALF + 1;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		FountainKit k = new FountainKit(c, p);
		int n = Math.min(Math.min(p.w(), p.d()), 2 * MAX_HALF + 1);
		if (n % 2 == 0) {
			n--;
		}
		int half = (n - 1) / 2;
		int h = p.h();
		Pal pal = Pal.of(k.hash(1, 0, 0, 3));
		int fig = k.hash(2, 0, 0, 3);

		// radii: edge steps at half, benches at half-1, walk lanes, rim at rp, water inside
		boolean benches = half >= 6;
		int walk = half <= 7 ? 1 : half <= 11 ? 2 : 3;
		int outerWalk = benches ? half - 2 : half - 1;
		int rpRaw = outerWalk - walk;
		int gw = rpRaw >= 9 ? 3 : rpRaw >= 6 ? 2 : 1;
		int g = Math.max(1, Math.min(gw, (h - 1) / 6));
		int rp = Math.min(rpRaw, 3 + 3 * g); // a bigger plot gets a wider plaza, not a bigger pool round a small figure
		int figHalf = g == 1 ? 1 : g == 2 ? 3 : 4;
		int rs = Math.min(Math.max(figHalf, (rp - 2) / 3), rp - 2); // radius of the dais

		for (int dz = -half; dz <= half; dz++) {
			for (int dx = -half; dx <= half; dx++) {
				if (!oct(dx, dz, half)) {
					corner(k, pal, dx, dz, half);
					continue;
				}
				int r = ring(dx, dz);
				if (r == half) {
					k.set(dx, 0, dz, stair(pick(k, dx, 0, dz, pal.edge), toward(dx, dz)));
				} else if (r < rp) {
					pool(k, pal, dx, dz, rs, g);
				} else if (r == rp) {
					rim(k, pal, dx, dz, rp, rs);
				} else if (benches && r == half - 1) {
					benchRing(k, pal, dx, dz, r - rp, rp);
				} else {
					k.set(dx, 0, dz, pave(k, pal, dx, dz, r - rp));
				}
			}
		}

		figure(k, pal, FIGURES[fig], fig, g, rp, rs, h);
	}

	// ------------------------------------------------------------------ geometry helpers

	/** The smallest octagon radius that contains the cell (the ring it is on). */
	private static int ring(int dx, int dz) {
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		while (!oct(dx, dz, r)) {
			r++;
		}
		return r;
	}

	private static Block pick(FountainKit k, int dx, int y, int dz, Block[] options) {
		return options[k.hash(dx, y, dz, options.length)];
	}

	private static BlockState pick(FountainKit k, int dx, int y, int dz, BlockState[] options) {
		return options[k.hash(dx, y, dz, options.length)];
	}

	// ------------------------------------------------------------------ pool, rim, dais

	/** A pool cell: dark bed and water, or the stepped dais the figure stands on. */
	private static void pool(FountainKit k, Pal pal, int dx, int dz, int rs, int g) {
		k.set(dx, 0, dz, pick(k, dx, 0, dz, pal.bed));
		if (!oct(dx, dz, rs)) {
			k.set(dx, 1, dz, WATER);
			return;
		}
		boolean core = oct(dx, dz, rs - 1);
		for (int y = 1; y < g; y++) {
			k.set(dx, y, dz, pick(k, dx, y, dz, pal.ped));
		}
		// [depth] the top step of the dais: a ring of stairs rising to a chiselled core
		k.set(dx, g, dz, core ? pal.pedTop : stair(pal.pedStair, toward(dx, dz)));
	}

	/** [palette] the rim: a dark base course under a ring of stairs at bench height, their backs to the water. */
	private static void rim(FountainKit k, Pal pal, int dx, int dz, int rp, int rs) {
		k.set(dx, 0, dz, pick(k, dx, 0, dz, pal.rimBase));
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		boolean post = ax == 0 || az == 0 || (rp >= 4 && ax == az);
		Direction in = null;
		int sx = 0;
		int sz = 0;
		if (post) {
			// the stream falls into the cell on the water side of the post
			Direction first = toward(dx, dz);
			Direction[] tries = {first, first.getAxis() == Direction.Axis.X ? (dz > 0 ? Direction.NORTH : Direction.SOUTH)
					: (dx > 0 ? Direction.WEST : Direction.EAST)};
			for (Direction t : tries) {
				int ix = dx + t.getStepX();
				int iz = dz + t.getStepZ();
				if (ring(ix, iz) < rp && !oct(ix, iz, rs)) {
					in = t;
					sx = ix;
					sz = iz;
					break;
				}
			}
		}
		if (in == null) {
			k.set(dx, 1, dz, stair(pick(k, dx, 1, dz, pal.rimStair), toward(dx, dz)));
			return;
		}
		// [finish] gargoyle post: base, shaft, a carved head that pours into the pool, a lantern on the compass ones
		k.set(dx, 1, dz, pick(k, dx, 1, dz, pal.rimBase));
		k.set(dx, 2, dz, pal.garg);
		k.set(dx, 3, dz, pal.garg);
		if (ax == 0 || az == 0) {
			k.set(dx, 4, dz, b(Blocks.LANTERN));
		}
		k.set(sx, 2, sz, FALL);
		k.set(sx, 3, sz, FALL);
	}

	// ------------------------------------------------------------------ plaza

	/** Paving in octagonal bands, with a calcite line along each axis to the entrances. */
	private static BlockState pave(FountainKit k, Pal pal, int dx, int dz, int m) {
		if (Math.min(Math.abs(dx), Math.abs(dz)) == 0) {
			return pal.line;
		}
		if (m == 1) {
			return pick(k, dx, 0, dz, pal.inner);
		}
		return pick(k, dx, 0, dz, (m / 2) % 2 == 0 ? pal.bandA : pal.bandB);
	}

	/** The bench ring: cherry stairs with their backs out (facing the fountain), a gap at each entrance, lamps beside it. */
	private static void benchRing(FountainKit k, Pal pal, int dx, int dz, int m, int rp) {
		k.set(dx, 0, dz, pave(k, pal, dx, dz, m));
		int lat = Math.min(Math.abs(dx), Math.abs(dz));
		if (lat <= 1) {
			return;
		}
		if (lat == 2) {
			lamp(k, dx, 1, dz);
			return;
		}
		k.set(dx, 1, dz, stair(Blocks.CHERRY_STAIRS, away(dx, dz)));
	}

	/** A lamp post: a deepslate base, two wall segments and a lantern; {@code y} is the base's layer. */
	private static void lamp(FountainKit k, int dx, int y, int dz) {
		k.set(dx, y, dz, b(Blocks.POLISHED_DEEPSLATE));
		k.set(dx, y + 1, dz, b(Blocks.DEEPSLATE_BRICK_WALL));
		k.set(dx, y + 2, dz, b(Blocks.DEEPSLATE_BRICK_WALL));
		k.set(dx, y + 3, dz, b(Blocks.LANTERN));
	}

	/** The corners of the square outside the octagon: a hedge, with a lamp in the very corner. */
	private static void corner(FountainKit k, Pal pal, int dx, int dz, int half) {
		if (Math.abs(dx) == half && Math.abs(dz) == half) {
			lamp(k, dx, 0, dz);
			return;
		}
		k.set(dx, 0, dz, b(Blocks.MOSSY_STONE_BRICKS));
		k.set(dx, 1, dz, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
	}

	// ------------------------------------------------------------------ the figure

	/**
	 * Draws the 3 × 3 × 5 pattern scaled by {@code g}: a column is {@code g} wide (the middle one {@code g} or
	 * {@code g + 1}, to stay centred) and each layer {@code g} high, so it grows without stretching. Thin parts
	 * (poles, rods) keep one block of width. The lowest layer is only built where the dais carries it.
	 */
	private static void figure(FountainKit k, Pal pal, String[][] pat, int fig, int g, int rp, int rs, int h) {
		int yb = g + 1;
		int hc = (g % 2 == 1 ? g : g + 1) / 2;
		for (int li = 0; li < 5; li++) {
			for (int zi = 0; zi < 3; zi++) {
				for (int xi = 0; xi < 3; xi++) {
					char ch = pat[li][zi].charAt(xi);
					if (ch == '.') {
						continue;
					}
					boolean thin = ch == 'W' || ch == 'R';
					int[] xr = span(xi, g, hc, thin);
					int[] zr = span(zi, g, hc, thin);
					for (int y = yb + li * g; y < yb + (li + 1) * g && y < h; y++) {
						for (int dz = zr[0]; dz <= zr[1]; dz++) {
							for (int dx = xr[0]; dx <= xr[1]; dx++) {
								if (li == 0 && !oct(dx, dz, rs)) {
									continue;
								}
								k.set(dx, y, dz, figureBlock(k, pal, ch, dx, y, dz));
							}
						}
					}
				}
			}
		}
		// streams from the figure into the pool
		int reach = hc + g + 1;
		if (fig == 1) {
			spout(k, 0, -reach, yb + 4 * g, rp, rs); // from the beak
		} else if (fig == 2) {
			for (Direction d : DIRS) {
				spout(k, d.getStepX() * reach, d.getStepZ() * reach, yb + 3 * g, rp, rs); // from the capital arms
			}
		}
	}

	private static void spout(FountainKit k, int dx, int dz, int top, int rp, int rs) {
		if (ring(dx, dz) >= rp || oct(dx, dz, rs)) {
			return;
		}
		for (int y = 2; y <= top; y++) {
			k.set(dx, y, dz, FALL);
		}
	}

	/** The extent {@code {lo, hi}} of pattern column (or row) {@code i}, in cells from the centre. */
	private static int[] span(int i, int g, int hc, boolean thin) {
		int lo = i == 1 ? -hc : i == 2 ? hc + 1 : -(hc + g);
		int hi = i == 1 ? hc : i == 2 ? hc + g : -(hc + 1);
		if (thin) {
			int m = i == 1 ? 0 : i == 2 ? lo + (hi - lo) / 2 : hi - (hi - lo) / 2;
			return new int[] {m, m};
		}
		return new int[] {lo, hi};
	}

	private static BlockState figureBlock(FountainKit k, Pal pal, char ch, int dx, int y, int dz) {
		return switch (ch) {
			case 'B' -> pick(k, dx, y, dz, pal.statB);
			case 'D' -> pick(k, dx, y, dz, pal.statD);
			case 'A' -> pal.statA;
			case 'H' -> pal.statH;
			case 'W' -> pal.pole;
			default -> pal.rod;
		};
	}

	// ------------------------------------------------------------------ materials

	/** One material set: light stone, deepslate or weathered copper. */
	private static final class Pal {
		BlockState[] bed;
		BlockState[] rimBase;
		BlockState[] inner;
		BlockState[] bandA;
		BlockState[] bandB;
		BlockState[] ped;
		BlockState[] statB;
		BlockState[] statD;
		BlockState line;
		BlockState garg;
		BlockState pedTop;
		BlockState statA;
		BlockState statH;
		BlockState pole;
		BlockState rod;
		Block[] rimStair;
		Block[] edge;
		Block pedStair;

		static Pal of(int which) {
			Pal p = new Pal();
			p.bed = new BlockState[] {b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE),
					b(Blocks.CRACKED_DEEPSLATE_TILES)};
			p.line = b(Blocks.CALCITE);
			p.rod = b(Blocks.LIGHTNING_ROD.waxed().unaffected());
			if (which == 1) { // deepslate: a dark plaza with a calcite line, copper on the figure
				p.rimBase = new BlockState[] {b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.POLISHED_BLACKSTONE_BRICKS),
						b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)};
				p.rimStair = new Block[] {Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS};
				p.garg = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
				p.inner = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE)};
				p.bandA = new BlockState[] {b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.CRACKED_DEEPSLATE_TILES)};
				p.bandB = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS)};
				p.edge = new Block[] {Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS};
				p.ped = new BlockState[] {b(Blocks.POLISHED_BLACKSTONE_BRICKS)};
				p.pedTop = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
				p.pedStair = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
				p.statB = new BlockState[] {b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS)};
				p.statD = new BlockState[] {b(Blocks.POLISHED_BLACKSTONE_BRICKS)};
				p.statA = b(Blocks.CUT_COPPER.waxed().unaffected());
				p.statH = b(Blocks.CALCITE);
				p.pole = b(Blocks.DEEPSLATE_BRICK_WALL);
			} else if (which == 2) { // weathered copper
				p.rimBase = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS)};
				p.rimStair = new Block[] {Blocks.CUT_COPPER_STAIRS.waxed().weathered(), Blocks.CUT_COPPER_STAIRS.waxed().weathered(),
						Blocks.CUT_COPPER_STAIRS.waxed().oxidized()};
				p.garg = b(Blocks.CHISELED_COPPER.waxed().oxidized());
				p.inner = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES)};
				p.bandA = new BlockState[] {b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS),
						b(Blocks.CRACKED_STONE_BRICKS)};
				p.bandB = new BlockState[] {b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_ANDESITE), b(Blocks.ANDESITE)};
				p.edge = new Block[] {Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS};
				p.ped = new BlockState[] {b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE)};
				p.pedTop = b(Blocks.CHISELED_DEEPSLATE);
				p.pedStair = Blocks.DEEPSLATE_BRICK_STAIRS;
				p.statB = new BlockState[] {b(Blocks.CUT_COPPER.waxed().weathered()), b(Blocks.CUT_COPPER.waxed().weathered()),
						b(Blocks.CUT_COPPER.waxed().oxidized())};
				p.statD = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE)};
				p.statA = b(Blocks.GOLD_BLOCK);
				p.statH = b(Blocks.CHISELED_COPPER.waxed().oxidized());
				p.pole = b(Blocks.LIGHTNING_ROD.waxed().oxidized());
			} else { // light stone
				p.rimBase = new BlockState[] {b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE),
						b(Blocks.CRACKED_DEEPSLATE_BRICKS)};
				p.rimStair = new Block[] {Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.POLISHED_DIORITE_STAIRS};
				p.garg = b(Blocks.CHISELED_STONE_BRICKS);
				p.inner = new BlockState[] {b(Blocks.POLISHED_DEEPSLATE), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES)};
				p.bandA = new BlockState[] {b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS),
						b(Blocks.CRACKED_STONE_BRICKS)};
				p.bandB = new BlockState[] {b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_ANDESITE), b(Blocks.ANDESITE)};
				p.edge = new Block[] {Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS,
						Blocks.MOSSY_STONE_BRICK_STAIRS};
				p.ped = new BlockState[] {b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE)};
				p.pedTop = b(Blocks.CHISELED_DEEPSLATE);
				p.pedStair = Blocks.DEEPSLATE_BRICK_STAIRS;
				p.statB = new BlockState[] {b(Blocks.POLISHED_DIORITE), b(Blocks.POLISHED_DIORITE), b(Blocks.CALCITE)};
				p.statD = new BlockState[] {b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE)};
				p.statA = b(Blocks.GOLD_BLOCK);
				p.statH = b(Blocks.CHISELED_QUARTZ_BLOCK);
				p.pole = b(Blocks.STONE_BRICK_WALL);
			}
			return p;
		}
	}
}
