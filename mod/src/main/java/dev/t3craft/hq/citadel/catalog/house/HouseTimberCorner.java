package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gable;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gapWin;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.groundPlant;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lowWall;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.stair;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TimberKit.Cell;
import java.util.Random;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A corner house: an L of two gabled wings (one along the street, one along the side street) with a
 * three-storey <b>mirador</b> tower at the outer corner. The tower's glazed lantern room is capped by
 * an oxidised-copper pyramid with a gold ball and a lightning rod; a cantilevered cherry balcony
 * hangs over the front door and a walled garden fills the inner angle of the L.
 *
 * <pre>
 *  top view (v down, front = top)      u 0 1 2 3 4 5 6 7 8 9 ...
 *   v1                                  . ┌─T─────┐ . A . . . .      T = mirador tower (5×5, u1..5 v2..6)
 *   v2-6                                . │ T T T │ A A A A A .      A = front wing (ridge along u)
 *   v7-…                                . B B B B B ┆ garden ┆       B = side wing (ridge along v)
 * </pre>
 *
 * Silhouette: one tall pointed tower and two low gables at right angles, nothing like the straight
 * jettied gable of the cottage. Palette: cherry frame, calcite and white plaster, deepslate footing,
 * clay (or slate) roofs, teal copper and gold accents. Size: 12–15 × 12–15 × 16+.
 */
public final class HouseTimberCorner implements Variant {
	private static final int BEAM = 4;
	private static final int PLATE = 8;

	@Override
	public String id() {
		return "house_timber_corner";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 12;
	}

	@Override
	public int minD() {
		return 12;
	}

	@Override
	public int minH() {
		return 16;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** [palette] two roof schemes per seed: warm clay or cool slate, always under the same copper tower. */
	private record Scheme(Block roof, Block eave, Block shutter) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.MUD_BRICK_STAIRS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_TRAPDOOR),
			new Scheme(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.BAMBOO_TRAPDOOR),
	};

	/** The footprint: tower (u1..5, v2..6), front wing A (u6..ue, v2..6) and side wing B (u1..5, v7..ub). */
	private record Geo(int w, int d) {
		int ue() {
			return w - 2;
		}

		int ub() {
			return d - 2;
		}

		int cu() {
			return (6 + ue()) / 2;
		}

		boolean fp(int u, int v) {
			return (u >= 1 && u <= 5 && v >= 2 && v <= 6) || (u >= 6 && u <= ue() && v >= 2 && v <= 6)
					|| (u >= 1 && u <= 5 && v >= 7 && v <= ub());
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xC0A3_5A11L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Geo g = new Geo(p.w(), p.d());
		footing(c, p, g);
		floors(c, p, g);
		for (int fl = 0; fl < 2; fl++) {
			walls(c, p, g, s, fl);
		}
		gables(c, p, g);
		roofs(c, p, g, s);
		mirador(c, p);
		porch(c, p, g, s);
		balcony(c, p, g);
		chimney(c, p, g);
		yard(c, p, g);
		strips(c, p, g);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] dark footing that lightens upwards: polished/cobbled deepslate at y0, bricks and a little tuff above. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		if (y <= 0) {
			return mix(p, u, y, v, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.COBBLED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS));
		}
		return mix(p, u, y, v, b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.TUFF_BRICKS), b(Blocks.CRACKED_DEEPSLATE_BRICKS));
	}

	/** [palette] plaster is mostly calcite with white terracotta patches: never a flat colour. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.CALCITE), CitadelCanvas.terracotta(DyeColor.WHITE));
	}

	/** [palette] light stripped cherry posts against the dark cherry beams: the frame reads as a frame. */
	private static BlockState post() {
		return log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
	}

	private static BlockState beam(Direction.Axis axis) {
		return log(Blocks.CHERRY_LOG, axis);
	}

	private static Block copperStairs(boolean exposed) {
		return exposed ? Blocks.CUT_COPPER_STAIRS.waxed().exposed() : Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	}

	private static BlockState copperBlock(boolean exposed) {
		return (exposed ? Blocks.CUT_COPPER.waxed().exposed() : Blocks.CUT_COPPER.waxed().oxidized()).defaultBlockState();
	}

	// ------------------------------------------------------------------ footing and floors

	private static void footing(CitadelCanvas c, Plot p, Geo g) {
		for (int u = 0; u < g.w(); u++) {
			for (int v = 0; v < g.d(); v++) {
				if (g.fp(u, v)) {
					boolean edge = !g.fp(u - 1, v) || !g.fp(u + 1, v) || !g.fp(u, v - 1) || !g.fp(u, v + 1);
					p.set(c, u, 0, v, edge ? stone(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
				} else {
					// [depth] a footing step round the house, one block proud of the wall
					Direction f = g.fp(u, v + 1) ? Direction.SOUTH : g.fp(u, v - 1) ? Direction.NORTH
							: g.fp(u + 1, v) ? Direction.EAST : g.fp(u - 1, v) ? Direction.WEST : null;
					if (f != null) {
						p.set(c, u, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, f, false));
					}
				}
			}
		}
	}

	private static void floors(CitadelCanvas c, Plot p, Geo g) {
		for (int u = 0; u < g.w(); u++) {
			for (int v = 0; v < g.d(); v++) {
				if (!g.fp(u, v)) {
					continue;
				}
				boolean edge = !g.fp(u - 1, v) || !g.fp(u + 1, v) || !g.fp(u, v - 1) || !g.fp(u, v + 1);
				if (edge) {
					Direction.Axis ax = (!g.fp(u, v - 1) || !g.fp(u, v + 1)) ? Direction.Axis.X : Direction.Axis.Z;
					p.set(c, u, BEAM, v, beam(ax));
					p.set(c, u, PLATE, v, beam(ax));
				} else {
					p.set(c, u, BEAM, v, b(Blocks.SPRUCE_PLANKS));
				}
			}
		}
		// the tower carries on up: its own ring of plate beams and a floor for the lantern room
		for (int u = 1; u <= 5; u++) {
			p.set(c, u, PLATE, 2, beam(Direction.Axis.X));
			p.set(c, u, PLATE, 6, beam(Direction.Axis.X));
		}
		for (int v = 3; v <= 5; v++) {
			p.set(c, 1, PLATE, v, beam(Direction.Axis.Z));
			p.set(c, 5, PLATE, v, beam(Direction.Axis.Z));
		}
		p.fill(c, 2, PLATE, 3, 4, PLATE, 5, b(Blocks.SPRUCE_PLANKS));
	}

	// ------------------------------------------------------------------ walls

	private void walls(CitadelCanvas c, Plot p, Geo g, Scheme s, int fl) {
		int ue = g.ue();
		int ub = g.ub();
		int cu = g.cu();
		// front wall (v 2), tower and wing A: a three-wide bay window on the tower, windows in the wing
		IntPredicate postF = i -> i == 1 || i == 5 || i == ue || i == cu - 1 || i == cu + 1;
		IntPredicate winF = i -> !postF.test(i) && i != cu && (i <= 4 || gapWin(postF, i, 5, ue));
		seg(c, p, s, fl, true, 2, -1, 1, ue, postF, winF);
		// left wall (u 1), tower and wing B
		IntPredicate postL = i -> i == 2 || i == 6 || i == ub || (i > 6 && (i - 6) % 4 == 0 && ub - i >= 2);
		IntPredicate winL = i -> !postL.test(i) && ((i >= 3 && i <= 5) || (i > 6 && gapWin(postL, i, 6, ub)));
		seg(c, p, s, fl, false, 1, -1, 2, ub, postL, winL);
		// wing B, right wall (u 5, facing the garden) and back wall
		IntPredicate winR = i -> i > 6 && gapWin(postL, i, 6, ub);
		seg(c, p, s, fl, false, 5, 1, 6, ub, postL, winR);
		IntPredicate postB = i -> i == 1 || i == 5;
		seg(c, p, s, fl, true, ub, 1, 1, 5, postB, i -> gapWin(postB, i, 1, 5));
		// wing A, back wall (v 6, facing the garden) and end wall (u ue)
		IntPredicate postAb = i -> i == 5 || i == ue || ((i - 5) % 4 == 0 && ue - i >= 2);
		seg(c, p, s, fl, true, 6, 1, 5, ue, postAb, i -> gapWin(postAb, i, 5, ue));
		IntPredicate postE = i -> i == 2 || i == 6;
		seg(c, p, s, fl, false, ue, 1, 2, 6, postE, i -> gapWin(postE, i, 2, 6));
	}

	/** One wall segment on one floor: [depth] recessed infill, a stone band under the ground-floor posts, sills and shutters. */
	private void seg(CitadelCanvas c, Plot p, Scheme s, int fl, boolean alongU, int fixed, int outward, int a, int b,
			IntPredicate post, IntPredicate win) {
		int y1 = fl == 0 ? 1 : 5;
		int y2 = fl == 0 ? 3 : 7;
		int w1 = fl == 0 ? 2 : 6;
		int w2 = fl == 0 ? 3 : 7;
		int in = fixed - outward;
		Cell rail = null;
		if (fl == 0) {
			rail = (i, y) -> y == 1 ? stone(p, alongU ? i : fixed, 1, alongU ? fixed : i) : null;
		}
		Cell fill = (i, y) -> plaster(p, alongU ? i : in, y, alongU ? in : i);
		TimberKit.wall(c, p, alongU, fixed, outward, a, b, y1, y2, post, win, w1, w2, null, (i, y) -> post(), rail, fill);
		TimberKit.dress(c, p, alongU, fixed, outward, a, b, post, win, w1, w2, Blocks.CHERRY_STAIRS, s.shutter(), false);
	}

	/** [palette] the small triangles under the two gable ends: plaster, a king post and a pink light. */
	private static void gables(CitadelCanvas c, Plot p, Geo g) {
		int ue = g.ue();
		for (int v = 2; v <= 6; v++) {
			int top = PLATE + Math.min(v - 1, 7 - v);
			for (int y = PLATE + 1; y < top; y++) {
				BlockState st = v == 4 ? (y == PLATE + 1 ? pane(false, DyeColor.PINK) : post()) : plaster(p, ue, y, v);
				p.set(c, ue, y, v, st);
			}
		}
		int ub = g.ub();
		for (int u = 1; u <= 5; u++) {
			int top = PLATE + Math.min(u, 6 - u);
			for (int y = PLATE + 1; y < top; y++) {
				BlockState st = u == 3 ? (y == PLATE + 1 ? pane(true, DyeColor.PINK) : post()) : plaster(p, u, y, ub);
				p.set(c, u, y, ub, st);
			}
		}
	}

	// ------------------------------------------------------------------ roofs

	private static void roofs(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		Block ridge = Blocks.DEEPSLATE_BRICKS;
		Block cap = Blocks.DEEPSLATE_BRICK_SLAB;
		gable(c, p, true, 6, g.w() - 1, 1, 7, PLATE, s.roof(), s.eave(), ridge, cap, false, true);
		gable(c, p, false, 7, g.d() - 1, 0, 6, PLATE, s.roof(), s.eave(), ridge, cap, false, true);
		// the valley where the two wings meet
		p.set(c, 6, PLATE, 7, b(Blocks.DEEPSLATE_TILES));
	}

	// ------------------------------------------------------------------ the mirador

	/**
	 * [finish] Lantern room (y9–10): corner posts and glass on every side with a pink pane in the
	 * middle; then a copper pyramid that starts as a 7×7 cornice, with exposed-copper corner ribs, a
	 * gold ball and a lightning rod.
	 */
	private static void mirador(CitadelCanvas c, Plot p) {
		for (int u = 1; u <= 5; u++) {
			for (int v = 2; v <= 6; v++) {
				boolean edge = u == 1 || u == 5 || v == 2 || v == 6;
				if (!edge) {
					continue;
				}
				boolean corner = (u == 1 || u == 5) && (v == 2 || v == 6);
				boolean alongU = v == 2 || v == 6;
				boolean centre = alongU ? u == 3 : v == 4;
				for (int y = 9; y <= 10; y++) {
					p.set(c, u, y, v, corner ? post() : pane(alongU, centre ? DyeColor.PINK : null));
				}
			}
		}
		p.put(c, 3, 10, 4, lantern(true), CitadelCanvas.HANGING);
		// the copper pyramid, centre (3, 4)
		for (int ring = 0; ring <= 3; ring++) {
			int half = 3 - ring;
			int y = 11 + ring;
			for (int u = 3 - half; u <= 3 + half; u++) {
				for (int v = 4 - half; v <= 4 + half; v++) {
					boolean eu = u == 3 - half || u == 3 + half;
					boolean ev = v == 4 - half || v == 4 + half;
					BlockState st;
					if (half == 0) {
						st = b(Blocks.GOLD_BLOCK);
					} else if (eu && ev) {
						st = copperBlock(true); // [palette] exposed copper ribs on the hips
					} else if (eu || ev) {
						Direction f = u == 3 - half ? Direction.EAST : u == 3 + half ? Direction.WEST : v == 4 - half ? Direction.SOUTH : Direction.NORTH;
						st = stair(copperStairs(false), f, false);
					} else {
						st = copperBlock(false);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		p.set(c, 3, 15, 4, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
		// [depth] a cove under the cornice on the two street sides
		for (int v = 2; v <= 6; v++) {
			p.set(c, 0, 10, v, stair(copperStairs(true), Direction.EAST, true));
		}
		for (int u = 1; u <= 5; u++) {
			p.set(c, u, 10, 1, stair(copperStairs(true), Direction.SOUTH, true));
		}
	}

	// ------------------------------------------------------------------ door and balcony

	/** [finish] recessed door with a lit transom, a step and lanterns; the balcony above shelters it. */
	private void porch(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		int cu = g.cu();
		p.set(c, cu, 1, 2, CitadelCanvas.AIR);
		p.set(c, cu, 1, 3, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, cu, 2, 3, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cu, 3, 3, pane(true, DyeColor.YELLOW));
		p.set(c, cu, 0, 2, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		p.set(c, cu - 1, 0, 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		p.set(c, cu, 0, 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		p.set(c, cu + 1, 0, 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		p.set(c, cu, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		// flanking planters
		p.set(c, cu - 2, 1, 1, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		p.set(c, cu + 2, 1, 1, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
	}

	/**
	 * [depth] A cantilevered balcony over the door: a deck two blocks deep on a row of upside-down
	 * stair corbels, a front beam, a cherry fence rail with lantern posts, pots, and a glazed door
	 * from the upper floor.
	 */
	private void balcony(CitadelCanvas c, Plot p, Geo g) {
		int cu = g.cu();
		int u1 = cu - 2;
		int u2 = cu + 2;
		for (int u = u1; u <= u2; u++) {
			p.set(c, u, BEAM, 1, b(Blocks.CHERRY_PLANKS));
			p.set(c, u, BEAM, 0, beam(Direction.Axis.X));
			p.set(c, u, 3, 1, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, true));
			p.set(c, u, 5, 0, fence(Blocks.CHERRY_FENCE, false, u < u2, false, u > u1));
		}
		p.set(c, u1, 5, 1, fence(Blocks.CHERRY_FENCE, true, false, false, false));
		p.set(c, u2, 5, 1, fence(Blocks.CHERRY_FENCE, true, false, false, false));
		p.set(c, u1, 5, 0, fence(Blocks.CHERRY_FENCE, false, true, true, false));
		p.set(c, u2, 5, 0, fence(Blocks.CHERRY_FENCE, false, false, true, true));
		p.set(c, u1, 6, 0, lantern(false));
		p.set(c, u2, 6, 0, lantern(false));
		p.set(c, cu - 1, 5, 1, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		p.set(c, cu + 1, 5, 1, Blocks.POTTED_CHERRY_SAPLING.defaultBlockState());
		p.put(c, cu - 1, 3, 0, lantern(true), CitadelCanvas.HANGING);
		p.put(c, cu + 1, 3, 0, lantern(true), CitadelCanvas.HANGING);
		// glazed door onto the balcony
		p.set(c, cu, 5, 3, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, cu, 6, 3, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cu, 7, 3, pane(true, DyeColor.PINK));
	}

	// ------------------------------------------------------------------ chimney

	private static void chimney(CitadelCanvas c, Plot p, Geo g) {
		int u = g.ue() - 2;
		for (int y = PLATE + 1; y <= 13; y++) {
			p.set(c, u, y, 5, mix(p, u, y, 5, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.DEEPSLATE_BRICKS)));
		}
		p.set(c, u, 14, 5, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
	}

	// ------------------------------------------------------------------ garden (the inner angle of the L)

	private static boolean gardenWall(Geo g, int u, int v, int gv) {
		int w = g.w();
		int d = g.d();
		return (u == w - 1 && v >= 7 && v <= d - 1 && v != gv && v != gv + 1) || (v == d - 1 && u >= 6 && u <= w - 1);
	}

	/**
	 * [finish] A walled garden: a low deepslate wall with a gate gap flanked by lantern piers, a paved
	 * path in cherry-friendly slate, mossy beds with azaleas and petals under the windows, a bench with
	 * its back to the house, a lamp post, a sapling and a barrel corner.
	 */
	private static void yard(CitadelCanvas c, Plot p, Geo g) {
		int w = g.w();
		int d = g.d();
		int gv = (7 + d - 1) / 2;
		int cx = (6 + w - 1) / 2;
		for (int u = 6; u <= w - 1; u++) {
			for (int v = 7; v <= d - 1; v++) {
				if (g.fp(u - 1, v) || g.fp(u, v - 1)) {
					continue; // the footing step round the house stays
				}
				if (gardenWall(g, u, v, gv)) {
					p.set(c, u, 0, v, mix(p, u, 0, v, b(Blocks.DEEPSLATE_BRICKS), b(Blocks.CRACKED_DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS)));
					boolean n = gardenWall(g, u, v - 1, gv);
					boolean e = gardenWall(g, u + 1, v, gv);
					boolean sa = gardenWall(g, u, v + 1, gv);
					boolean we = gardenWall(g, u - 1, v, gv);
					Block wb = p.hash(u, 1, v, 3) == 0 ? Blocks.COBBLED_DEEPSLATE_WALL : Blocks.DEEPSLATE_BRICK_WALL;
					p.set(c, u, 1, v, lowWall(wb, n, e, sa, we));
					continue;
				}
				boolean path = (v == gv || v == gv + 1) && u >= cx - 1;
				if (path) {
					p.set(c, u, 0, v, mix(p, u, 0, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false), slab(Blocks.DEEPSLATE_TILE_SLAB, false), slab(Blocks.STONE_BRICK_SLAB, false)));
					continue;
				}
				// [palette] garden soil: moss with patches of coarse dirt, planted by hash
				p.set(c, u, 0, v, p.hash(u, 0, v, 5) == 0 ? b(Blocks.COARSE_DIRT) : b(Blocks.MOSS_BLOCK));
				BlockState plant = groundPlant(p, u, 1, v);
				if (plant != null) {
					p.set(c, u, 1, v, plant);
				}
			}
		}
		// gate piers with lanterns
		for (int v : new int[] {gv - 1, gv + 2}) {
			p.set(c, w - 1, 0, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, w - 1, 1, v, b(Blocks.DEEPSLATE_BRICKS));
			p.set(c, w - 1, 2, v, lantern(false));
		}
		// bench against the side wing (back to the wall)
		int ub = g.ub();
		for (int v = ub - 2; v <= ub - 1; v++) {
			p.set(c, 6, 1, v, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
		}
		p.set(c, 6, 1, ub - 3, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		// lamp post at the plaza
		p.set(c, cx - 2, 1, gv - 2, b(Blocks.POLISHED_DEEPSLATE));
		p.set(c, cx - 2, 2, gv - 2, fence(Blocks.CHERRY_FENCE, false, false, false, false));
		p.set(c, cx - 2, 3, gv - 2, fence(Blocks.CHERRY_FENCE, false, false, false, false));
		p.set(c, cx - 2, 4, gv - 2, lantern(false));
		// barrels in the far corner
		p.set(c, w - 2, 1, d - 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, w - 3, 1, d - 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, w - 2, 2, d - 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, w - 2, 1, d - 3, b(Blocks.COMPOSTER));
	}

	/** Bushes and pots on the footing round the street fronts and the back of the side wing. */
	private static void strips(CitadelCanvas c, Plot p, Geo g) {
		int cu = g.cu();
		for (int u = 1; u <= g.ue(); u++) {
			if (Math.abs(u - cu) < 3) {
				continue;
			}
			BlockState plant = groundPlant(p, u, 1, 1);
			if (plant != null) {
				p.set(c, u, 1, 1, plant);
			}
		}
		for (int v = 2; v <= g.ub(); v++) {
			// [finish] a hedge of flowering azalea along the side street, with gaps
			BlockState st = switch (p.hash(0, 1, v, 4)) {
				case 0, 1 -> CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
				case 2 -> CitadelCanvas.leaves(Blocks.AZALEA_LEAVES);
				default -> null;
			};
			if (st != null) {
				p.set(c, 0, 1, v, st);
			}
		}
		for (int v = 2; v <= 6; v++) {
			BlockState plant = groundPlant(p, g.w() - 1, 1, v);
			if (plant != null) {
				p.set(c, g.w() - 1, 1, v, plant);
			}
		}
		// back of the side wing: a woodpile and a barrel
		int back = g.d() - 1;
		p.set(c, 1, 1, back, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, 2, 1, back, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		p.set(c, 3, 1, back, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		p.set(c, 2, 2, back, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
	}
}
