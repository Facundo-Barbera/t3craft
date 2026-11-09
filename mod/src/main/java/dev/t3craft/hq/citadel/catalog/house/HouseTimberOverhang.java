package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gable;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gableY;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gapWin;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.groundPlant;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
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
 * A tall, narrow townhouse that turns its <b>gable to the street</b> and steps outwards as it rises:
 * a stone ground floor, a cream-plastered first floor that juts one block out (jetty) on a row of
 * cherry corbels, and an attic face that juts out again under a brick-red roof, with a hoist beam and
 * a lantern at the peak. Every window carries a flowering planter. A kitchen garden with a hedge, a
 * gate and a small arbour fills the strip beside the house.
 *
 * <pre>
 *  side section (front on the left)
 *   v0 v1 v2 v3 ....... vb
 *   ─┬─ roof rake / hoist beam   y9+   attic face at v1  (jetty 2)
 *    ▐▌  floor 2  y5–7           wall at v2             (jetty 1)
 *     ▐  ground y1–3 stone       wall at v3
 * </pre>
 *
 * Silhouette: a narrow stepped front with the ridge towards the street and a low garden beside it,
 * the opposite of the long arcade and the L-shaped corner house. Palette: deepslate and stone
 * brick, cream sandstone plaster, cherry frame, red brick roof. Size: 13–15 × 10–13 × 16+.
 */
public final class HouseTimberOverhang implements Variant {
	private static final int PLATE = 9;

	@Override
	public String id() {
		return "house_timber_overhang";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 10;
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
		return 13;
	}

	/** [palette] brick roof with a dark or a pink eave line, and shutters in cherry or deep red mangrove. */
	private record Scheme(Block roof, Block eave, Block shutter) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.CHERRY_TRAPDOOR),
			new Scheme(Blocks.BRICK_STAIRS, Blocks.CHERRY_STAIRS, Blocks.MANGROVE_TRAPDOOR),
	};

	/** The house body is seven wide at the right of the plot: {@code ul..ur}, centre {@code uc}. */
	private record Geo(int w, int d) {
		int ul() {
			return w - 8;
		}

		int ur() {
			return w - 2;
		}

		int uc() {
			return ul() + 3;
		}

		int vb() {
			return d - 2;
		}

		/** Garden gate column. */
		int gx() {
			return (ul() - 2) / 2;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x0FE12A46L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Geo g = new Geo(p.w(), p.d());
		footing(c, p, g);
		floors(c, p, g);
		for (int fl = 0; fl < 2; fl++) {
			walls(c, p, g, s, fl);
		}
		jetties(c, p, g);
		roof(c, p, g, s);
		atticFace(c, p, g);
		entrance(c, p, g);
		chimney(c, p, g);
		garden(c, p, g);
		strips(c, p, g);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] dark deepslate foot, stone bricks (some mossy, some cracked) up the ground floor. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		if (y <= 0) {
			return mix(p, u, y, v, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.COBBLED_DEEPSLATE));
		}
		if (y == 1) {
			return mix(p, u, y, v, b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.CRACKED_DEEPSLATE_BRICKS), b(Blocks.STONE_BRICKS));
		}
		return mix(p, u, y, v, b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS));
	}

	/** [palette] cream plaster: smooth and cut sandstone mixed. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE));
	}

	private static BlockState post() {
		return log(Blocks.CHERRY_LOG, Direction.Axis.Y);
	}

	private static BlockState beam(Direction.Axis axis) {
		return log(Blocks.CHERRY_LOG, axis);
	}

	// ------------------------------------------------------------------ footing and floors

	private static void footing(CitadelCanvas c, Plot p, Geo g) {
		int ul = g.ul();
		int ur = g.ur();
		int vb = g.vb();
		for (int u = ul; u <= ur; u++) {
			for (int v = 3; v <= vb; v++) {
				boolean edge = u == ul || u == ur || v == 3 || v == vb;
				p.set(c, u, 0, v, edge ? stone(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
			}
			// [depth] footing steps all round the stone floor, one block proud
			p.set(c, u, 0, 2, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
			p.set(c, u, 0, g.d() - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
		}
		for (int v = 3; v <= vb; v++) {
			p.set(c, ul - 1, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
			p.set(c, ur + 1, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
		}
	}

	private static void floors(CitadelCanvas c, Plot p, Geo g) {
		int ul = g.ul();
		int ur = g.ur();
		int vb = g.vb();
		// y4: the first jetty. The front beam sits at v2, one block ahead of the stone wall at v3.
		for (int u = ul; u <= ur; u++) {
			for (int v = 2; v <= vb; v++) {
				boolean side = u == ul || u == ur;
				boolean edge = side || v == 2 || v == vb;
				p.set(c, u, 4, v, edge ? beam(side ? Direction.Axis.Z : Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		// y8: the second jetty, a double beam at v1 and v2
		for (int u = ul; u <= ur; u++) {
			for (int v = 1; v <= vb; v++) {
				boolean side = u == ul || u == ur;
				boolean edge = side || v <= 2 || v == vb;
				p.set(c, u, 8, v, edge ? beam(side ? Direction.Axis.Z : Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
			}
		}
	}

	// ------------------------------------------------------------------ walls

	private void walls(CitadelCanvas c, Plot p, Geo g, Scheme s, int fl) {
		int ul = g.ul();
		int ur = g.ur();
		int uc = g.uc();
		int vb = g.vb();
		if (fl == 0) {
			// stone ground floor: every cell is wall except the door and the windows, which are niches
			IntPredicate winF = i -> i == uc - 2 || i == uc + 2;
			IntPredicate postF = i -> !winF.test(i) && i != uc;
			stoneSeg(c, p, s, true, 3, -1, ul, ur, postF, winF);
			IntPredicate winS = i -> i >= 5 && i <= vb - 1 && (i - 5) % 3 == 0;
			IntPredicate postS = i -> !winS.test(i);
			stoneSeg(c, p, s, false, ul, -1, 3, vb, postS, winS);
			stoneSeg(c, p, s, false, ur, 1, 3, vb, postS, winS);
			IntPredicate winB = i -> i == uc;
			stoneSeg(c, p, s, true, vb, 1, ul, ur, i -> !winB.test(i), winB);
		} else {
			IntPredicate postF = i -> i == ul || i == uc || i == ur;
			timberSeg(c, p, s, true, 2, -1, ul, ur, postF, i -> gapWin(postF, i, ul, ur), 6, 7);
			IntPredicate postS = i -> i == 2 || i == vb || ((i - 2) % 4 == 0 && vb - i >= 2);
			IntPredicate winS = i -> gapWin(postS, i, 2, vb);
			timberSeg(c, p, s, false, ul, -1, 2, vb, postS, winS, 6, 7);
			timberSeg(c, p, s, false, ur, 1, 2, vb, postS, winS, 6, 7);
			IntPredicate postB = i -> i == ul || i == ur;
			timberSeg(c, p, s, true, vb, 1, ul, ur, postB, i -> gapWin(postB, i, ul, ur), 6, 7);
		}
	}

	private void stoneSeg(CitadelCanvas c, Plot p, Scheme s, boolean alongU, int fixed, int outward, int a, int bb,
			IntPredicate post, IntPredicate win) {
		int in = fixed - outward;
		Cell stone = (i, y) -> stone(p, alongU ? i : fixed, y, alongU ? fixed : i);
		Cell rail = (i, y) -> y == 1 ? stone.at(i, y) : null;
		Cell fill = (i, y) -> plaster(p, alongU ? i : in, y, alongU ? in : i);
		TimberKit.wall(c, p, alongU, fixed, outward, a, bb, 1, 3, post, win, 2, 3, null, stone, rail, fill);
		TimberKit.dress(c, p, alongU, fixed, outward, a, bb, post, win, 2, 3, Blocks.STONE_BRICK_STAIRS, s.shutter(), true);
	}

	private void timberSeg(CitadelCanvas c, Plot p, Scheme s, boolean alongU, int fixed, int outward, int a, int bb,
			IntPredicate post, IntPredicate win, int w1, int w2) {
		int in = fixed - outward;
		Cell fill = (i, y) -> plaster(p, alongU ? i : in, y, alongU ? in : i);
		TimberKit.wall(c, p, alongU, fixed, outward, a, bb, 5, 7, post, win, w1, w2, null, (i, y) -> post(), null, fill);
		// [finish] flower boxes under every window of the first floor, shutters on the two-wide ones
		TimberKit.dress(c, p, alongU, fixed, outward, a, bb, post, win, w1, w2, Blocks.CHERRY_STAIRS, s.shutter(), true);
	}

	/** [depth] a row of upside-down cherry stairs carries each jetty. */
	private static void jetties(CitadelCanvas c, Plot p, Geo g) {
		for (int u = g.ul(); u <= g.ur(); u++) {
			p.set(c, u, 3, 2, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, true));
			p.set(c, u, 7, 1, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, true));
		}
		// the side corners get a second corbel so the overhang reads from the side as well
		for (int v = 2; v <= 3; v++) {
			p.set(c, g.ul() - 1, 4, v, stair(Blocks.CHERRY_STAIRS, Direction.EAST, true));
			p.set(c, g.ur() + 1, 4, v, stair(Blocks.CHERRY_STAIRS, Direction.WEST, true));
		}
	}

	// ------------------------------------------------------------------ roof

	private static void roof(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		gable(c, p, false, 0, g.d() - 1, g.ul() - 1, g.ur() + 1, PLATE, s.roof(), s.eave(), Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, true, true);
		// gold finial on the back end of the ridge
		p.set(c, g.uc(), PLATE + 6, g.d() - 1, b(Blocks.DEEPSLATE_TILE_WALL));
		p.set(c, g.uc(), PLATE + 7, g.d() - 1, Blocks.LIGHTNING_ROD.waxed().exposed().defaultBlockState());
	}

	/**
	 * [finish] The attic face on the second jetty (v1): plaster with a cherry bargeboard under the
	 * roof, a king post, a lit two-high window with a planter, and a hoist beam that sticks out of the
	 * peak and carries a lantern.
	 */
	private static void atticFace(CitadelCanvas c, Plot p, Geo g) {
		int ul = g.ul();
		int ur = g.ur();
		int uc = g.uc();
		for (int u = ul; u <= ur; u++) {
			int top = gableY(u, ul - 1, ur + 1, PLATE);
			for (int y = PLATE; y < top; y++) {
				BlockState st;
				if (u == uc) {
					st = (y == PLATE || y == top - 1) ? post() : pane(true, DyeColor.YELLOW);
				} else if (y == top - 1) {
					st = beam(Direction.Axis.X);
				} else {
					st = plaster(p, u, y, 1);
				}
				p.set(c, u, y, 1, st);
			}
		}
		p.set(c, uc, PLATE, 0, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, true));
		p.set(c, uc, PLATE + 1, 0, TimberKit.planter(p, uc, PLATE + 1, 0));
		p.set(c, uc, PLATE + 3, 0, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
		p.put(c, uc, PLATE + 2, 0, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ door

	private static void entrance(CitadelCanvas c, Plot p, Geo g) {
		int uc = g.uc();
		p.set(c, uc, 1, 3, CitadelCanvas.AIR);
		p.set(c, uc, 1, 4, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, uc, 2, 4, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, uc, 3, 4, pane(true, DyeColor.YELLOW));
		// [depth] a stone corbel arch over the door
		p.set(c, uc - 1, 3, 3, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
		p.set(c, uc + 1, 3, 3, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
		for (int u = uc - 1; u <= uc + 1; u++) {
			p.set(c, u, 0, 2, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		p.set(c, uc, 0, 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		// lantern posts beside the step
		for (int u : new int[] {uc - 3, uc + 3}) {
			p.set(c, u, 1, 1, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 2, 1, fence(Blocks.CHERRY_FENCE, false, false, false, false));
			p.set(c, u, 3, 1, lantern(false));
		}
	}

	private static void chimney(CitadelCanvas c, Plot p, Geo g) {
		int u = g.ur() - 1;
		int v = g.vb() - 2;
		int top = Math.min(PLATE + 6, p.h() - 2);
		for (int y = PLATE + 1; y < top; y++) {
			p.set(c, u, y, v, mix(p, u, y, v, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.DEEPSLATE_BRICKS)));
		}
		p.set(c, u, top, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
	}

	// ------------------------------------------------------------------ garden

	/**
	 * [finish] The kitchen garden beside the house: a flowering hedge round three sides, a gate with
	 * two lantern piers, a flagstone path to a cherry arbour with a bench, and mossy beds on both
	 * sides planted by hash.
	 */
	private static void garden(CitadelCanvas c, Plot p, Geo g) {
		int ul = g.ul();
		int d = g.d();
		int gx = g.gx();
		int vm = d / 2;
		for (int u = 0; u <= ul - 2; u++) {
			for (int v = 0; v <= d - 1; v++) {
				boolean border = u == 0 || v == 0 || v == d - 1;
				boolean pier = v == 0 && (u == gx - 1 || u == gx + 1);
				if (pier) {
					p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
					p.set(c, u, 1, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
					p.set(c, u, 2, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
					p.set(c, u, 3, v, lantern(false));
				} else if (border && !(v == 0 && u == gx)) {
					p.set(c, u, 0, v, b(Blocks.MOSS_BLOCK));
					p.set(c, u, 1, v, p.hash(u, 1, v, 3) == 0 ? CitadelCanvas.leaves(Blocks.AZALEA_LEAVES) : CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				} else if (u == gx) {
					p.set(c, u, 0, v, mix(p, u, 0, v, slab(Blocks.COBBLED_DEEPSLATE_SLAB, false), slab(Blocks.POLISHED_DEEPSLATE_SLAB, false), slab(Blocks.STONE_BRICK_SLAB, false)));
				} else {
					p.set(c, u, 0, v, p.hash(u, 0, v, 5) == 0 ? b(Blocks.COARSE_DIRT) : b(Blocks.MOSS_BLOCK));
					BlockState plant = groundPlant(p, u, 1, v);
					if (plant != null) {
						p.set(c, u, 1, v, plant);
					}
				}
			}
		}
		// the arbour over the path
		for (int u = gx - 1; u <= gx + 1; u++) {
			p.set(c, u, 3, vm, slab(Blocks.CHERRY_SLAB, false));
			p.set(c, u, 4, vm, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		}
		for (int u : new int[] {gx - 1, gx + 1}) {
			p.set(c, u, 1, vm, fence(Blocks.CHERRY_FENCE, false, false, false, false));
			p.set(c, u, 2, vm, fence(Blocks.CHERRY_FENCE, false, false, false, false));
		}
		// a bench at the end of the path and barrels beside it
		p.set(c, gx, 1, d - 2, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, false));
		p.set(c, gx - 1, 1, d - 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, gx + 1, 1, d - 2, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
	}

	/** Bushes, a rain barrel and a woodpile on the strips round the house body. */
	private static void strips(CitadelCanvas c, Plot p, Geo g) {
		int ul = g.ul();
		int ur = g.ur();
		int vb = g.vb();
		// v 0..2 have no footing step under them: these plants stand on the ground (y0), they used to float at y1
		for (int v = 0; v <= 2; v++) {
			BlockState plant = groundPlant(p, ul - 1, 1, v);
			if (plant != null) {
				p.set(c, ul - 1, 0, v, plant);
			}
		}
		for (int v = 3; v <= vb; v++) {
			BlockState l = switch (p.hash(ul - 1, 1, v, 4)) {
				case 0 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
				case 1 -> log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z);
				case 2 -> Blocks.POTTED_FERN.defaultBlockState();
				default -> null;
			};
			if (l != null) {
				p.set(c, ul - 1, 1, v, l);
			}
			BlockState rr = groundPlant(p, ur + 1, 1, v);
			if (rr != null) {
				p.set(c, ur + 1, 1, v, rr);
			}
		}
		for (int u = ul; u <= ur; u++) {
			BlockState plant = groundPlant(p, u, 1, g.d() - 1);
			if (plant != null) {
				p.set(c, u, 1, g.d() - 1, plant);
			}
		}
		for (int v = 0; v <= 2; v++) {
			BlockState rr = groundPlant(p, ur + 1, 1, v);
			if (rr != null) {
				p.set(c, ur + 1, 0, v, rr);
			}
		}
	}
}
