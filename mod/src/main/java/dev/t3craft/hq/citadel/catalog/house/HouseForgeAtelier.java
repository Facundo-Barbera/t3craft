package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.barrel;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.door;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.trap;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A forge-atelier: a sooty stone hall with a huge corbelled brick chimney that smokes above the ridge,
 * and a deep open work porch under a patinated copper lean-to roof where the anvil, grindstone,
 * smithing table and water trough stand. Wide shop windows show the hearth glowing inside.
 *
 * <p>Concept: Hanseatic smithies and a Japanese-style engawa porch in the citadel palette: dark
 * deepslate base rising to tuff and plaster, dark-oak frame, deepslate-tile main roof, and a copper
 * porch roof that ages from orange at the wall to verdigris at the eave. Memorable for the chimney
 * (three blocks square, stepped shoulders, a smoking campfire pot) and the warm hearth glow.
 *
 * <pre>
 *  section (v →)                      plan
 *   y15   ▓ chimney pot               v0-2  porch: posts, rail, anvil/grindstone/trough
 *  y12/9      /\  main roof           v3    front wall: shop windows, door
 *  y7  porch ╱  ╲  copper lean-to     v4-vb hall: hearth in the back-right corner
 *  y4  ═ beam ═                       vb+1  woodpile, charcoal, barrels
 *  y1-3 posts, rail
 * </pre>
 *
 * Size: 11–15 × 10–14 × 16+.
 */
public final class HouseForgeAtelier implements Variant {
	private static final int VF = 3; // front wall row (the porch is v 0..2)
	private static final int PLATE = 7; // main roof starts here; walls y1..y6

	@Override
	public String id() {
		return "house_forge_atelier";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 11;
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
		return 14;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xF026_E001L);
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2 - 1; // the door, a little left of centre: the chimney owns the right side
		int vb = d - 2;
		int uc = w - 3; // chimney centre (shaft w-4..w-2, ring w-5..w-1)
		int vc = vb - 1; // chimney centre (shaft vb-2..vb, ring vb-3..d-1)

		hallWalls(c, p, cu, vb, uc, vc);
		hearth(c, p, uc, vc);
		mainRoof(c, p, vb);
		gables(c, p, vb, uc, vc);
		chimney(c, p, uc, vc);
		porch(c, p, r, cu);
		yard(c, p, r, cu, vb, uc, vc);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] the soot-black base: deepslate bricks, cracked and cobbled patches. */
	private static BlockState base(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] the middle band: tuff bricks, a bit of polished tuff. */
	private static BlockState mid(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 4) == 0 ? b(Blocks.POLISHED_TUFF) : b(Blocks.TUFF_BRICKS);
	}

	/** [palette] the top band: plaster (calcite) with the odd smooth-stone block. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 6) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE);
	}

	/** The wall by height: dark → light from the ground up. */
	private static BlockState wallAt(Plot p, int u, int y, int v) {
		if (y <= 2) {
			return base(p, u, y, v);
		}
		return y <= 4 ? mid(p, u, y, v) : plaster(p, u, y, v);
	}

	private static BlockState frame(Direction.Axis axis) {
		return log(Blocks.DARK_OAK_LOG, axis);
	}

	/** Copper by patina level: 0 orange (new) … 3 verdigris, waxed so it stays as it is. */
	private static Block copperStairs(int lvl) {
		return switch (lvl) {
			case 0 -> Blocks.CUT_COPPER_STAIRS.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER_STAIRS.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER_STAIRS.waxed().weathered();
			default -> Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
		};
	}

	private static Block copperSlab(int lvl) {
		return switch (lvl) {
			case 0 -> Blocks.CUT_COPPER_SLAB.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER_SLAB.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER_SLAB.waxed().weathered();
			default -> Blocks.CUT_COPPER_SLAB.waxed().oxidized();
		};
	}

	// ------------------------------------------------------------------ the hall (v 3..vb)

	private static boolean sidePost(int v, int vb) {
		return v == VF || v == vb || (v - VF) % 4 == 0;
	}

	private static boolean frontPost(int u, int w, int cu) {
		return u == 1 || u == w - 2 || Math.abs(u - cu) == 2 || (u - 1) % 5 == 0 && u > cu + 2 && u < w - 3;
	}

	private static void hallWalls(CitadelCanvas c, Plot p, int cu, int vb, int uc, int vc) {
		int w = p.w();
		// floor
		for (int u = 1; u <= w - 2; u++) {
			for (int v = VF; v <= vb; v++) {
				p.set(c, u, 0, v, (u == 1 || u == w - 2 || v == VF || v == vb) ? base(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		// front wall (v = VF): shop windows between posts, door at cu
		for (int u = 1; u <= w - 2; u++) {
			boolean post = frontPost(u, w, cu);
			for (int y = 1; y <= 6; y++) {
				BlockState st;
				if (y == 6) {
					st = frame(Direction.Axis.X); // plate
				} else if (post) {
					st = frame(Direction.Axis.Y);
				} else if (u == cu) {
					st = y <= 2 ? door(Blocks.DARK_OAK_DOOR, Direction.NORTH, y == 2) : y == 3 ? frame(Direction.Axis.X) : plaster(p, u, y, VF);
				} else if (y == 1) {
					st = base(p, u, y, VF);
				} else if (y >= 2 && y <= 4 && Math.abs(u - cu) <= 1) {
					st = y == 4 ? plaster(p, u, y, VF) : clearPane(true); // sidelights by the door
				} else if (y >= 2 && y <= 4) {
					st = clearPane(true); // the wide shop windows
				} else {
					st = plaster(p, u, y, VF);
				}
				p.set(c, u, y, VF, st);
			}
		}
		// side walls and back wall
		for (int side = 0; side < 2; side++) {
			int u = side == 0 ? 1 : w - 2;
			for (int v = VF + 1; v <= vb; v++) {
				boolean chimneyBreast = side == 1 && v >= vc - 1 && v <= vc + 1;
				for (int y = 1; y <= 6; y++) {
					BlockState st;
					if (chimneyBreast) {
						st = base(p, u, y, v);
					} else if (y == 6) {
						st = frame(Direction.Axis.Z);
					} else if (sidePost(v, vb)) {
						st = frame(Direction.Axis.Y);
					} else if (y == 3 || y == 4) {
						st = clearPane(false);
					} else {
						st = wallAt(p, u, y, v);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		for (int u = 2; u <= w - 3; u++) {
			for (int y = 1; y <= 6; y++) {
				boolean post = u == cu || u == 4 || u == w - 5;
				BlockState st = y == 6 ? frame(Direction.Axis.X) : post ? frame(Direction.Axis.Y) : y == 3 && u % 2 == 0 ? clearPane(true) : wallAt(p, u, y, vb);
				p.set(c, u, y, vb, st);
			}
		}
		// [depth] stone buttress feet and sills, a rim of dark stone round the hall
		for (int v = VF + 1; v <= vb; v++) {
			if (!sidePost(v, vb)) {
				p.set(c, 0, 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
			}
		}
		// the chimney breast on the east flank
		for (int v = vc - 1; v <= vc + 1; v++) {
			for (int y = 1; y <= 5; y++) {
				p.set(c, w - 1, y, v, base(p, w - 1, y, v));
			}
			p.set(c, w - 1, 6, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, false));
		}
		p.set(c, w - 1, 0, vc - 2, base(p, w - 1, 0, vc - 2));
		p.set(c, w - 1, 0, vc + 2, base(p, w - 1, 0, vc + 2));
	}

	/** The hearth in the back-right corner: lit furnaces under a brick arch, glowing through the shop windows. */
	private static void hearth(CitadelCanvas c, Plot p, int uc, int vc) {
		// chimney base (3x3 footprint) as a solid breast of fire-brick, hearth mouth on its west face
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				for (int y = 1; y <= 6; y++) {
					boolean mouth = du == -1 && dv == 0;
					BlockState st = p.hash(uc + du, y, vc + dv, 3) == 0 ? b(Blocks.BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
					if (mouth && y == 1) {
						st = facing(Blocks.FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true);
					} else if (mouth && y == 2) {
						st = facing(Blocks.BLAST_FURNACE, Direction.WEST).setValue(BlockStateProperties.LIT, true);
					} else if (mouth && y == 3) {
						st = stair(Blocks.BRICK_STAIRS, Direction.WEST, true);
					} else if (du == -1 && y == 3 && Math.abs(dv) == 1) {
						st = stair(Blocks.BRICK_STAIRS, dv < 0 ? Direction.NORTH : Direction.SOUTH, true);
					}
					p.set(c, uc + du, y, vc + dv, st);
				}
			}
		}
		// [finish] anvil and a quench trough in front of the hearth
		p.set(c, uc - 3, 1, vc, facing(Blocks.ANVIL, Direction.NORTH));
		p.set(c, uc - 2, 1, vc + 1, b(Blocks.WATER_CAULDRON));
		p.set(c, uc - 3, 1, vc - 2, b(Blocks.SMITHING_TABLE));
		p.set(c, uc - 2, 1, vc - 1, b(Blocks.COAL_BLOCK));
		p.put(c, uc - 3, 5, vc, lantern(true), CitadelCanvas.HANGING);
		p.put(c, 3, 5, VF + 3, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ roofs

	private static int roofY(int v, int d) {
		return PLATE + Math.min(v - VF, d - 1 - v);
	}

	/**
	 * [finish] the main roof: deepslate tiles over the hall, ridge along u, eave rows in blackstone brick, a ridge of
	 * oxidized copper slabs, rakes one block proud of the gables.
	 */
	private static void mainRoof(CitadelCanvas c, Plot p, int vb) {
		int w = p.w();
		int d = p.d();
		int vr = (VF + d - 1) / 2;
		for (int v = VF; v < d; v++) {
			int y = roofY(v, d);
			boolean frontSlope = v < vr || (v == vr && (VF + d - 1) % 2 == 1);
			boolean ridgeRow = (VF + d - 1) % 2 == 0 && v == vr;
			boolean eave = v == VF || v == d - 1;
			for (int u = 0; u < w; u++) {
				boolean rake = u == 0 || u == w - 1;
				BlockState st;
				if (ridgeRow) {
					st = b(Blocks.DEEPSLATE_TILES);
				} else {
					Block m = eave || rake ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					st = stair(m, frontSlope ? Direction.SOUTH : Direction.NORTH, false);
				}
				p.set(c, u, y, v, st);
				// [depth] rake underside and eave underside
				if (rake && y - 1 >= PLATE && v > VF && v < d - 1) {
					p.set(c, u, y - 1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, frontSlope ? Direction.NORTH : Direction.SOUTH, true));
				}
			}
		}
		// ridge cap of oxidized copper slabs
		int top = roofY(vr, d);
		for (int u = 0; u < w; u++) {
			p.set(c, u, top + 1, vr, slab(copperSlab(3 - p.hash(u, 0, 0, 2)), false));
			if ((VF + d - 1) % 2 == 1) {
				p.set(c, u, top + 1, vr + 1, slab(copperSlab(3 - p.hash(u, 1, 0, 2)), false));
			}
		}
	}

	private static void gables(CitadelCanvas c, Plot p, int vb, int uc, int vc) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {1, w - 2}) {
			for (int v = VF + 1; v <= vb; v++) {
				boolean breast = u == w - 2 && v >= vc - 1 && v <= vc + 1;
				for (int y = PLATE; y < roofY(v, d); y++) {
					BlockState st = y == roofY(v, d) - 1 && y > PLATE ? b(Blocks.TUFF_BRICKS) : plaster(p, u, y, v);
					if (breast) {
						st = base(p, u, y, v);
					} else if (y == PLATE + 1 && v == (VF + vb) / 2) {
						st = clearPane(false);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		// front and back wall tops above the plate, under the roof
		for (int v : new int[] {VF}) {
			for (int u = 1; u <= w - 2; u++) {
				p.set(c, u, PLATE, v, frame(Direction.Axis.X));
			}
		}
	}

	// ------------------------------------------------------------------ chimney

	/**
	 * [finish] the big chimney: a 3x3 brick-and-deepslate shaft rising through the roof, a flared corbel of
	 * upside-down stairs at the top, a capped pot with a smoking campfire.
	 */
	private static void chimney(CitadelCanvas c, Plot p, int uc, int vc) {
		int d = p.d();
		int ridge = roofY((VF + d - 1) / 2, d);
		int top = Math.min(ridge + 4, p.h() - 2);
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				for (int y = 7; y <= top; y++) {
					BlockState st = p.hash(uc + du, y, vc + dv, 3) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS);
					p.set(c, uc + du, y, vc + dv, st);
				}
			}
		}
		// corbel ring: upside-down stairs one block out, facing outwards
		for (int k = -1; k <= 1; k++) {
			p.set(c, uc + k, top, vc - 2, stair(Blocks.BRICK_STAIRS, Direction.SOUTH, true));
			p.set(c, uc + k, top, vc + 2, stair(Blocks.BRICK_STAIRS, Direction.NORTH, true));
			p.set(c, uc - 2, top, vc + k, stair(Blocks.BRICK_STAIRS, Direction.EAST, true));
			p.set(c, uc + 2, top, vc + k, stair(Blocks.BRICK_STAIRS, Direction.WEST, true));
		}
		// cap: a slab ring round an open pot with the campfire
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du == 0 && dv == 0) {
					p.set(c, uc, top + 1, vc, b(Blocks.CAMPFIRE).setValue(BlockStateProperties.SIGNAL_FIRE, true));
				} else {
					p.set(c, uc + du, top + 1, vc + dv, slab(copperSlab(3), false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ the work porch (v 0..2)

	private static void porch(CitadelCanvas c, Plot p, Random r, int cu) {
		int w = p.w();
		// floor: polished blackstone pavers with a brick runner to the door
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 0; v <= 2; v++) {
				BlockState st = u == cu ? b(Blocks.BRICKS) : p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				p.set(c, u, 0, v, st);
			}
		}
		// posts and the beam at the front edge (v 0), side beams to the wall
		for (int u = 1; u <= w - 2; u++) {
			boolean post = u == 1 || u == w - 2 || Math.abs(u - cu) == 2 || ((u - cu) % 4 == 0 && u > cu + 2 && u < w - 3);
			if (post) {
				for (int y = 1; y <= 3; y++) {
					p.set(c, u, y, 0, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
				}
				p.set(c, u, 0, 0, b(Blocks.DEEPSLATE_BRICKS)); // plinth foot
			}
			p.set(c, u, 4, 0, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
			// rail between posts, except at the steps
			if (!post && Math.abs(u - cu) > 1) {
				boolean l = u - 1 >= 1;
				boolean rr = u + 1 <= w - 2;
				p.set(c, u, 1, 0, fence(Blocks.DARK_OAK_FENCE, false, rr, false, l));
			}
		}
		for (int v = 1; v <= 2; v++) {
			p.set(c, 1, 4, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
			p.set(c, w - 2, 4, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z));
		}
		// steps down to the path at the door
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 0, 0, stair(Blocks.BRICK_STAIRS, Direction.SOUTH, false));
		}
		// the copper lean-to: orange at the wall, verdigris at the eave (v0 oxidized, v1 weathered, v2 exposed)
		for (int v = 0; v <= 2; v++) {
			int lvl = 3 - v;
			for (int u = 0; u < w; u++) {
				int level = Math.max(1, Math.min(3, lvl - (p.hash(u, v, 0, 7) == 0 ? 1 : 0)));
				p.set(c, u, 5 + v, v, stair(copperStairs(level), Direction.SOUTH, false));
				if ((u == 0 || u == w - 1) && v <= 1) {
					p.set(c, u, 4 + v, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, true)); // [depth] eave brackets
				}
			}
		}
		// hanging lanterns beside the door
		BlockState hanging = lantern(true);
		p.put(c, cu - 2, 3, 1, hanging, CitadelCanvas.HANGING);
		p.put(c, cu + 2, 3, 1, hanging, CitadelCanvas.HANGING);
		// the tools: a seed-shuffled row along the front wall
		Block[] props = {Blocks.ANVIL, Blocks.GRINDSTONE, Blocks.SMITHING_TABLE, Blocks.WATER_CAULDRON, Blocks.BARREL, Blocks.COAL_BLOCK};
		int k = r.nextInt(props.length);
		for (int u = 2; u <= w - 3; u++) {
			if (Math.abs(u - cu) <= 1) {
				continue;
			}
			Block pr = props[(k + u) % props.length];
			BlockState st = pr == Blocks.ANVIL ? facing(pr, Direction.EAST) : pr == Blocks.BARREL ? barrel() : b(pr);
			p.set(c, u, 1, 2, st);
		}
		// shutters: open trapdoors on the front wall each side of the windows
		for (int u = 2; u <= w - 3; u++) {
			if (Math.abs(u - cu) == 3 || u == w - 3) {
				p.set(c, u, 3, 2, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
			}
		}
	}

	// ------------------------------------------------------------------ yard (behind and beside the hall)

	private static void yard(CitadelCanvas c, Plot p, Random r, int cu, int vb, int uc, int vc) {
		int w = p.w();
		int d = p.d();
		// woodpile along the west flank
		for (int v = VF + 1; v < vb; v++) {
			int k = p.hash(0, 0, v, 4);
			if (k <= 1) {
				p.set(c, 0, 0, v, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
				if (k == 0) {
					p.set(c, 0, 1, v, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
				}
			} else if (k == 2) {
				p.set(c, 0, 0, v, barrel());
			}
		}
		// back strip: charcoal heap, barrels and a water trough
		for (int u = 1; u <= w - 6; u++) {
			switch (p.hash(u, 0, d - 1, 5)) {
				case 0 -> p.set(c, u, 0, d - 1, b(Blocks.COAL_BLOCK));
				case 1 -> p.set(c, u, 0, d - 1, barrel());
				case 2 -> p.set(c, u, 0, d - 1, b(Blocks.WATER_CAULDRON));
				default -> {
				}
			}
		}
		// a lantern post at the corner of the porch
		int lx = r.nextBoolean() ? 0 : w - 1;
		p.set(c, lx, 0, 0, b(Blocks.COBBLED_DEEPSLATE_WALL));
		p.set(c, lx, 1, 0, lantern(false));
	}
}
