package dev.t3craft.hq.citadel.catalog.gazebo;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a raised lookout (mirador) on a stone podium. A two-layer podium of deepslate bricks and
 * stone bricks carries a deck at {@code y = 2}; its faces are set back one block between projecting
 * corner piers, and the recess holds a strip of planters (pots, azalea tufts, petals) under a corbel
 * course of upside-down stone-brick stairs, so the deck seems to float. A flight of stairs climbs
 * from the front along the left side (deepslate brick → stone brick → polished andesite, darker at
 * the foot) to a landing that runs back along the side. A cherry-fence balustrade with polished
 * deepslate wall posts (lanterns on them) rings the deck on a light polished-andesite cornice. Four
 * stripped-cherry posts, set one block in from the rail, carry beams and a hipped roof: a dark
 * deepslate-tile eave that overhangs the rail, upside-down cherry brackets beside the posts, cherry
 * slopes with proper outer-corner stairs on the hips, and a copper rod with a gold knob on top. A
 * lantern hangs on a chain under the roof.
 *
 * <p>Palette, dark → light, bottom → top: deepslate bricks / polished deepslate (podium foot) → stone
 * bricks, mossy and cracked mixed, chiselled on the piers (podium) → polished andesite (cornice,
 * stair top) → cherry (deck, rail, posts, roof slopes) with a dark tile eave and gold/copper accents.
 *
 * <pre>
 *  front (w 7)                           plan (w 7, d 6), front at the bottom
 *         ╿ ●      rod, gold knob          ┼ ═ ═ ═ ═ ═ ┼    ┼ wall post   ═ rail
 *      ▄▟███▙▄     hipped cherry roof      ║ ▬ ▬ ▬ ▬ ▬ ║
 *    ▄▀▀▀▀▀▀▀▀▀▄   dark eave               ▬ ▬ ║ ▬ ║ ▬ ║    landing (left), ║ posts
 *    ┼ ◥║ o ║◤ ┼   posts, brackets         ◢ ║ ▬ ▬ ▬ ▬ ║    stairs climb ◢ ◢ ◢
 *    ┼═══════┼     rail on cornice         ◢ ║ ║ ▬ ║ ▬ ║
 *  ◢ ▀▀▀▀▀▀▀▀▀     deck, corbels           ◢ ┼ ═ ═ ═ ═ ┼
 * ◢  █ ✿ ✿ ✿ █     piers, planters
 * </pre>
 *
 * Size: 5–9 × 5–9 × 8+ (the hipped roof rises to a point when the height allows; on low plots with a
 * big deck it stops at a flat cap of tile slabs, with the rod on top).
 */
public final class GazeboRaisedLookout implements Variant {
	private static final int DECK = 2; // the deck layer; people stand at y = 3
	/** The roof's lowest ring: 5, or 6 from h 9 so people under it get 3 blocks of headroom and the posts read. */
	private static int eave(Plot p) {
		return p.h() >= 9 ? 6 : 5;
	}

	@Override
	public String id() {
		return "gazebo_raised_lookout";
	}

	@Override
	public Family family() {
		return Family.GAZEBO;
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
		return 8;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	// ------------------------------------------------------------------ geometry

	/** The deck: the rectangle u ≥ 1, plus the landing column u = 0 from v = 3 back. */
	private static boolean deck(int u, int v, int w, int d) {
		return u >= 0 && u < w && v >= 0 && v < d && (u >= 1 || v >= 3);
	}

	/**
	 * The balustrade cells on the deck's outline. Two entries stay open: the landing at (0, 3) and (1, 2),
	 * beside the top step, so the flight leads straight onto the deck (on d = 5 the post at (2, 3) would
	 * otherwise shut the landing in).
	 */
	private static boolean rail(int u, int v, int w, int d) {
		if (!deck(u, v, w, d) || (u == 0 && v == 3) || (u == 1 && v == 2)) {
			return false;
		}
		return u == 0 || u == w - 1 || v == 0 || v == d - 1 || (u == 1 && v < 3);
	}

	/** The podium recess (faces set back between the piers): front and back rows, right side. */
	private static boolean recess(int u, int v, int w, int d) {
		boolean frontBack = (v == 0 || v == d - 1) && u >= 2 && u <= w - 2;
		boolean right = u == w - 1 && v >= 1 && v <= d - 2;
		return frontBack || right;
	}

	/** Distance to the edge of the roof rectangle (u 1..w-1, v 0..d-1). */
	private static int ring(int u, int v, int w, int d) {
		return Math.min(Math.min(u - 1, w - 1 - u), Math.min(v, d - 1 - v));
	}

	private static boolean post(int u, int v, int w, int d) {
		return (u == 2 || u == w - 2) && (v == 1 || v == d - 2);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		podium(c, p, w, d);
		stair(c, p);
		deckAndRail(c, p, w, d);
		posts(c, p, w, d);
		roof(c, p, w, d);
	}

	// ------------------------------------------------------------------ podium (y0..y1)

	private static void podium(CitadelCanvas c, Plot p, int w, int d) {
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (!deck(u, v, w, d)) {
					continue;
				}
				if (recess(u, v, w, d)) {
					// [depth] corbel course under the deck edge, planters at the foot
					Direction inward = v == 0 ? Direction.SOUTH : v == d - 1 ? Direction.NORTH : Direction.WEST;
					p.set(c, u, 1, v, stairs(Blocks.STONE_BRICK_STAIRS, inward).setValue(BlockStateProperties.HALF, Half.TOP));
					planter(c, p, u, v);
					continue;
				}
				boolean pier = (u == 1 || u == w - 1) && (v == 0 || v == d - 1);
				int h0 = p.hash(u, 0, v, 5);
				int h1 = p.hash(u, 1, v, 6);
				p.set(c, u, 0, v, pier ? b(Blocks.POLISHED_DEEPSLATE) : h0 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
				p.set(c, u, 1, v, pier ? b(Blocks.CHISELED_STONE_BRICKS)
						: h1 == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : h1 == 1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			}
		}
	}

	private static void planter(CitadelCanvas c, Plot p, int u, int v) {
		BlockState s = switch (p.hash(u, 0, v, 6)) {
			case 0 -> b(Blocks.POTTED_PINK_TULIP);
			case 1 -> b(Blocks.POTTED_FLOWERING_AZALEA);
			case 2 -> leaves(Blocks.FLOWERING_AZALEA_LEAVES);
			case 3 -> leaves(Blocks.AZALEA_LEAVES);
			case 4 -> b(Blocks.POTTED_FERN);
			default -> b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 2, v, 4))
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(p.hash(u, 3, v, 4)));
		};
		p.set(c, u, 0, v, s);
	}

	// ------------------------------------------------------------------ the flight of stairs (u = 0, v 0..2)

	/** Three steps climbing towards the back, darker at the foot, on solid supports. */
	private static void stair(CitadelCanvas c, Plot p) {
		Block[] steps = {Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS};
		for (int i = 0; i < 3; i++) {
			for (int y = 0; y < i; y++) {
				p.set(c, 0, y, i, y == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.STONE_BRICKS));
			}
			p.set(c, 0, i, i, stairs(steps[i], Direction.SOUTH));
		}
	}

	// ------------------------------------------------------------------ deck, cornice and balustrade (y2..y4)

	private static void deckAndRail(CitadelCanvas c, Plot p, int w, int d) {
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (!deck(u, v, w, d)) {
					continue;
				}
				boolean edge = rail(u, v, w, d) || (u == 0 && v == 3);
				// [palette] a light cornice of polished andesite under the rail, a cherry deck inside
				BlockState top;
				if (edge) {
					top = p.hash(u, DECK, v, 5) == 0 ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE);
				} else {
					top = p.hash(u, DECK, v, 5) == 0 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.CHERRY_PLANKS);
				}
				p.set(c, u, DECK, v, top);
				if (!rail(u, v, w, d)) {
					continue;
				}
				boolean n = rail(u, v - 1, w, d);
				boolean s = rail(u, v + 1, w, d);
				boolean we = rail(u - 1, v, w, d);
				boolean e = rail(u + 1, v, w, d);
				boolean straight = (n && s && !we && !e) || (we && e && !n && !s);
				if (straight) {
					p.set(c, u, DECK + 1, v, b(Blocks.CHERRY_FENCE)
							.setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.SOUTH, s)
							.setValue(BlockStateProperties.WEST, we).setValue(BlockStateProperties.EAST, e));
				} else {
					// corners and ends: stone wall posts with a lantern on top
					p.set(c, u, DECK + 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true)
							.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.WEST_WALL, we ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE));
					p.set(c, u, DECK + 2, v, b(Blocks.LANTERN));
				}
			}
		}
	}

	// ------------------------------------------------------------------ posts, beams, brackets (y3..y5)

	private static void posts(CitadelCanvas c, Plot p, int w, int d) {
		BlockState postLog = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
		int eave = eave(p);
		for (int v = 0; v < d; v++) {
			for (int u = 1; u < w; u++) {
				if (post(u, v, w, d)) {
					for (int y = 3; y < eave; y++) {
						p.set(c, u, y, v, postLog);
					}
					p.set(c, u, eave, v, b(Blocks.STRIPPED_CHERRY_WOOD));
					// [depth] brackets under the eave on the two outer faces of the post
					Direction outU = u == 2 ? Direction.WEST : Direction.EAST;
					Direction outV = v == 1 ? Direction.NORTH : Direction.SOUTH;
						// (never over a walkable deck cell: people stand at y 3 and need the headroom)
						int bu = u + outU.getStepX();
						if (eave - 1 >= 5 || !deck(bu, v, w, d) || rail(bu, v, w, d)) {
							p.set(c, bu, eave - 1, v, upside(Blocks.CHERRY_STAIRS, outU.getOpposite()));
						}
						p.set(c, u, eave - 1, v + outV.getStepZ(), upside(Blocks.CHERRY_STAIRS, outV.getOpposite()));
					continue;
				}
				// beams along the post ring, between the posts
				boolean onRing = ring(u, v, w, d) == 1;
				boolean betweenU = (v == 1 || v == d - 2) && u > 2 && u < w - 2;
				boolean betweenV = (u == 2 || u == w - 2) && v > 1 && v < d - 2;
				if (onRing && (betweenU || betweenV)) {
					p.set(c, u, eave, v, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, betweenU ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
	}

	private static BlockState upside(Block blk, Direction facing) {
		return stairs(blk, facing).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	// ------------------------------------------------------------------ hipped roof (y5..)

	private static void roof(CitadelCanvas c, Plot p, int w, int d) {
		int eave = eave(p);
		int maxK = p.h() - 1 - eave - 1; // leave the top layer for the rod
		int kTop = Math.min((Math.min(w - 1, d) - 1) / 2, maxK);
		boolean truncated = (Math.min(w - 1, d) - 1) / 2 > maxK;
		for (int v = 0; v < d; v++) {
			for (int u = 1; u < w; u++) {
				int dl = u - 1;
				int dr = w - 1 - u;
				int df = v;
				int db = d - 1 - v;
				int k = Math.min(Math.min(dl, dr), Math.min(df, db));
				if (k > kTop) {
					// a flat cap flush with the top of the last ring
					p.set(c, u, eave + kTop, v, b(Blocks.DEEPSLATE_TILE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
					continue;
				}
				int y = eave + k;
				Block mat = k == 0 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.CHERRY_STAIRS;
				if (k >= 1) {
					Support.roofUnder(c, p, u, y, v, mat); // fix/settle-sources: each ring rests on the one below, not on its edge
				}
				boolean tieU = dl == k && dr == k;
				boolean tieV = df == k && db == k;
				if ((tieU || tieV) && k == kTop && !truncated) {
					// the ridge: stripped cherry along the ridge line
					p.set(c, u, y, v, b(Blocks.STRIPPED_CHERRY_WOOD));
					continue;
				}
				boolean lr = dl == k || dr == k;
				boolean fb = df == k || db == k;
				Direction inU = dl == k ? Direction.EAST : Direction.WEST;
				Direction inV = df == k ? Direction.SOUTH : Direction.NORTH;
				if (lr && fb && !tieU && !tieV) {
					// hip corner: the tall quarter points inwards on both axes
					StairsShape shape = inV == inU.getClockWise() ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
					p.set(c, u, y, v, stairs(mat, inU).setValue(StairBlock.SHAPE, shape));
				} else if (lr && !tieU) {
					p.set(c, u, y, v, stairs(mat, inU));
				} else {
					p.set(c, u, y, v, stairs(mat, inV));
				}
			}
		}

		// finial and the hanging lantern in the middle of the roof
		int cu = 1 + (w - 1) / 2;
		int cv = d / 2;
		int top = eave + kTop;
		p.set(c, cu, top + 1, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		if (!truncated && top + 2 < p.h()) {
			p.set(c, cu, top + 1, cv, b(Blocks.GOLD_BLOCK));
			p.set(c, cu, top + 2, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		}
		// the lantern hangs at the eave level (y 5), above head height on the deck (people stand at y 3); with
		// ck = 1 the middle is on the beam ring and the lanterns on the rail posts light the deck instead
		int ck = ring(cu, cv, w, d);
		int roofAbove = eave + Math.min(ck, kTop);
		for (int y = eave; ck >= 2 && y < roofAbove; y++) { // with ck = 1 the lantern hangs from a beam
			p.put(c, cu, y, cv, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
		}
		if (!post(cu, cv, w, d)) {
			p.put(c, cu, eave - 1, cv, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}
}
