package dev.t3craft.hq.citadel.catalog.annex;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Glasshouse: a deepslate plinth carrying walls of glass panes framed by weathered copper posts,
 * under a stepped glass roof whose gable ends and ribs (every third row) are oxidized copper
 * stairs, with a chiseled copper emblem and a copper finial on the gables. Inside, raised beds of
 * wheat, carrots, potatoes and beetroot flank a mud-brick path lit by hanging copper lanterns.
 *
 * <pre>
 *  front (w = 7)                        plan (w = 7, d = 7)
 *  y6        ▲      finial              v0  ▬ ✿ ▬ ▼ ▬ ✿ ▤   apron: step, pots, barrel
 *  y5     ▄▄███▄▄   ribs / glass top    v1  ▣ ░ ░ ▯ ░ ░ ▣   front wall, door ▯
 *  y4    ▄▀ ░▣░ ▀▄  gable panes, emblem v2  ▓ ⁂ ⁂ ▒ ⁂ ⁂ ▓   beds ⁂, path ▒
 *  y3   ▀▀▀▀▀▀▀▀▀▀  eaves + copper beam …   ▓ ⁂ ⁂ ▒ ⁂ ⁂ ▓
 *  y1-2 ▣░░▯░░▣     posts, panes, door  v5  ▓ ◙ ▤ ⚱ ▤ ◙ ▓   composters, barrels, pot
 *  y0   ▓▓▓▓▓▓▓     deepslate plinth    v6  ▣ ░ ░ ▣ ░ ░ ▣   back wall
 * </pre>
 *
 * Size: 5–8 × 5–8 × 6+ (the roof rises two steps at most; the finials need height 7).
 */
public final class AnnexGreenhouseCopper implements Variant {
	/** Plate / eaves level; glass walls are y1..y2 below it. */
	private static final int PLATE = 3;

	private static final BlockState POST = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState();
	private static final BlockState RIB_TOP = Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState();
	private static final Block RIB = Blocks.CUT_COPPER_STAIRS.waxed().oxidized();

	@Override
	public String id() {
		return "annex_greenhouse_copper";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
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
		return 6;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 8;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int vf = d >= 6 ? 1 : 0; // front wall row (v 0 is an apron on deeper plots)
		int vb = d - 1;
		int cu = w / 2;

		plinth(c, p, vf, vb, cu);
		walls(c, p, vf, vb, cu);
		roof(c, p, vf, vb, cu);
		beds(c, p, vf, vb);
		lanterns(c, p, vf, vb, cu);
		if (vf == 1) {
			apron(c, p, cu);
		} else {
			groundDoor(c, p, cu);
		}
	}

	/**
	 * Without an apron (d = 5) there is no room for a step outside the door, and a door on the plinth would
	 * leave a full block to jump from the street. So the door stands on the ground (y0..y1) with a copper
	 * lintel over it, and the step up to the floor (y0, top at 1) is the first path cell inside.
	 */
	private static void groundDoor(CitadelCanvas c, Plot p, int cu) {
		for (int y = 0; y <= 1; y++) {
			p.set(c, cu, y, 0, b(Blocks.CHERRY_DOOR)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
		}
		p.set(c, cu, 2, 0, POST);
		p.set(c, cu, 0, 1, stairs(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH));
	}

	/** Roof height over column u: the eaves at PLATE, then one step up per column, two steps at most. */
	private static int roofY(int u, int w) {
		return PLATE + Math.min(Math.min(u, w - 1 - u), 2);
	}

	/** Ribs: both gable rows and every third row between them. */
	private static boolean rib(int v, int vf, int vb) {
		return v == vf || v == vb || (v - vf) % 3 == 0;
	}

	private static BlockState pane(BlockState g, boolean alongU, boolean sideA, boolean sideB) {
		return alongU ? g.setValue(BlockStateProperties.WEST, sideA).setValue(BlockStateProperties.EAST, sideB)
				: g.setValue(BlockStateProperties.NORTH, sideA).setValue(BlockStateProperties.SOUTH, sideB);
	}

	// ------------------------------------------------------------------ plinth (y0)

	/** [palette] the heavy base: deepslate bricks, cracked by hash, chiseled at the corners, a polished threshold. */
	private static void plinth(CitadelCanvas c, Plot p, int vf, int vb, int cu) {
		int w = p.w();
		for (int u = 0; u < w; u++) {
			for (int v = vf; v <= vb; v++) {
				boolean side = u == 0 || u == w - 1;
				boolean end = v == vf || v == vb;
				if (!side && !end) {
					continue; // the inside is beds and path
				}
				BlockState st;
				if (side && end) {
					st = b(Blocks.CHISELED_DEEPSLATE);
				} else if (u == cu && v == vf) {
					st = b(Blocks.POLISHED_DEEPSLATE);
				} else {
					st = p.hash(u, 0, v, 4) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ walls (y1..y2)

	private static void walls(CitadelCanvas c, Plot p, int vf, int vb, int cu) {
		int w = p.w();
		BlockState clear = b(Blocks.GLASS_PANE);
		BlockState pink = Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK).defaultBlockState();
		for (int y = 1; y <= 2; y++) {
			// side walls: [depth] copper posts on every rib row, panes between (joined front-back)
			for (int v = vf + 1; v < vb; v++) {
				for (int u : new int[] {0, w - 1}) {
					p.set(c, u, y, v, rib(v, vf, vb) ? POST : pane(clear, false, true, true));
				}
			}
			// front wall: corner posts, a door in the middle, posts beside it on wide fronts
			for (int u = 0; u < w; u++) {
				BlockState st;
				if (u == cu) {
					st = b(Blocks.CHERRY_DOOR)
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
							.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				} else if (u == 0 || u == w - 1 || (w >= 7 && Math.abs(u - cu) == 1)) {
					st = POST;
				} else {
					// panes do not join a door; [palette] the upper panes beside the door are tinted pink
					BlockState g = y == 2 && Math.abs(u - cu) == 1 ? pink : clear;
					st = pane(g, true, u - 1 != cu, u + 1 != cu);
				}
				p.set(c, u, y, vf, st);
			}
			// back wall: corner posts and one in the middle on wide plots
			for (int u = 0; u < w; u++) {
				boolean post = u == 0 || u == w - 1 || (w >= 7 && u == cu);
				p.set(c, u, y, vb, post ? POST : pane(clear, true, true, true));
			}
		}
	}

	// ------------------------------------------------------------------ roof (y3+)

	/**
	 * [finish] the eaves are a copper gutter line on both sides; above, each column steps up: copper
	 * stairs on the rib rows (a block on the flat top), glass blocks between the ribs. The gable
	 * triangles are glass panes on a copper beam, with an emblem and a finial in the middle.
	 */
	private static void roof(CitadelCanvas c, Plot p, int vf, int vb, int cu) {
		int w = p.w();
		for (int v = vf; v <= vb; v++) {
			boolean ribRow = rib(v, vf, vb);
			p.set(c, 0, PLATE, v, stairs(RIB, Direction.EAST));
			p.set(c, w - 1, PLATE, v, stairs(RIB, Direction.WEST));
			for (int u = 1; u < w - 1; u++) {
				int y = roofY(u, w);
				boolean flat = Math.min(u, w - 1 - u) >= 2;
				BlockState st;
				if (!ribRow) {
					st = b(Blocks.GLASS);
				} else if (flat) {
					st = RIB_TOP;
				} else {
					st = stairs(RIB, u < w - 1 - u ? Direction.EAST : Direction.WEST);
				}
				p.set(c, u, y, v, st);
			}
		}
		// gables: a copper beam over the walls, panes up to the ribs
		BlockState clear = b(Blocks.GLASS_PANE);
		BlockState pink = Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK).defaultBlockState();
		boolean odd = w % 2 == 1;
		for (int v : new int[] {vf, vb}) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, PLATE, v, POST);
				boolean centre = odd ? u == cu : u == cu || u == cu - 1;
				for (int y = PLATE + 1; y < roofY(u, w); y++) {
					BlockState st;
					if (centre && odd && v == vf) {
						st = Blocks.CHISELED_COPPER.waxed().unaffected().defaultBlockState(); // [finish] emblem
					} else {
						st = pane(centre ? pink : clear, true, true, true);
					}
					p.set(c, u, y, v, st);
				}
			}
			int top = roofY(cu, w) + 1;
			if (top < p.h()) {
				p.set(c, cu, top, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------ inside (y0..y1)

	/**
	 * Raised beds along both side walls, a mud-brick path down the middle and, on the back row, a
	 * potting corner: composters and barrels on the beds, a decorated pot at the end of the path.
	 * Crops change every two rows, by seed.
	 */
	private static void beds(CitadelCanvas c, Plot p, int vf, int vb) {
		int w = p.w();
		int pathW = w % 2 == 1 ? 1 : 2;
		int bedW = (w - 2 - pathW) / 2;
		BlockState farmland = b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7);
		for (int v = vf + 1; v < vb; v++) {
			boolean back = v == vb - 1;
			for (int u = 1; u < w - 1; u++) {
				boolean bed = u <= bedW || u >= w - 1 - bedW;
				if (!bed) {
					p.set(c, u, 0, v, back ? b(Blocks.PACKED_MUD)
							: p.hash(u, 0, v, 3) == 0 ? b(Blocks.PACKED_MUD) : b(Blocks.MUD_BRICKS));
					if (back) {
						p.set(c, u, 1, v, b(Blocks.DECORATED_POT));
					}
					continue;
				}
				if (back) {
					boolean outer = u == 1 || u == w - 2;
					p.set(c, u, 0, v, outer ? b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 7)
							: b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
					continue;
				}
				p.set(c, u, 0, v, farmland);
				int side = u <= bedW ? 0 : 1;
				p.set(c, u, 1, v, crop(p.hash(side, 1, v / 2, 4)));
			}
		}
	}

	private static BlockState crop(int k) {
		return switch (k) {
			case 0 -> b(Blocks.WHEAT).setValue(BlockStateProperties.AGE_7, 7);
			case 1 -> b(Blocks.CARROTS).setValue(BlockStateProperties.AGE_7, 7);
			case 2 -> b(Blocks.POTATOES).setValue(BlockStateProperties.AGE_7, 7);
			default -> b(Blocks.BEETROOTS).setValue(BlockStateProperties.AGE_3, 3);
		};
	}

	/** [finish] copper lanterns hanging under the ridge over the path: one, or two on deep plots. */
	private static void lanterns(CitadelCanvas c, Plot p, int vf, int vb, int cu) {
		BlockState lantern = Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		int y = roofY(cu, p.w()) - 1;
		int depth = vb - vf;
		if (depth >= 6) {
			p.put(c, cu, y, vf + depth / 3, lantern, CitadelCanvas.HANGING);
			p.put(c, cu, y, vb - depth / 3, lantern, CitadelCanvas.HANGING);
		} else {
			p.put(c, cu, y, (vf + vb) / 2, lantern, CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ apron (v0)

	/** [finish] the transition to the ground: a step at the door, potted flowers beside it, a barrel and moss. */
	private static void apron(CitadelCanvas c, Plot p, int cu) {
		int w = p.w();
		for (int u = 0; u < w; u++) {
			BlockState st;
			if (u == cu) {
				st = stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH);
			} else if (Math.abs(u - cu) == 1) {
				st = p.hash(u, 0, 0, 2) == 0 ? b(Blocks.POTTED_RED_TULIP) : b(Blocks.POTTED_FERN);
			} else if (u == w - 1) {
				st = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
			} else {
				st = p.hash(u, 0, 0, 3) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.POLISHED_DEEPSLATE_SLAB);
			}
			p.set(c, u, 0, 0, st);
		}
	}
}
