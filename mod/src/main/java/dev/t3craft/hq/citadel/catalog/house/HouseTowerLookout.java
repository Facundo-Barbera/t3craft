package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.hangingLantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.put;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.set;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.trapdoor;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A lived-in lookout tower: a round shaft (an octagon-ish disc in blocks) of three storeys on a
 * flared base with four buttresses, a cherry gallery that corbels out under a ring of glass and
 * cherry pillars (the lantern room), and a steep conical roof of deepslate tiles with a cherry eave
 * ring, a lantern and a lightning rod on the tip.
 *
 * <p>Palette, dark to light, bottom to top: deepslate bricks (cracked / cobbled / polished mix) for
 * the base storey, stone bricks (mossy / cracked) for the middle, calcite plaster for the top, then
 * cherry for the gallery and lantern room, and a dark roof edged in cherry.
 *
 * <pre>
 *  y17+ finial: fence, lantern, rod        radius r = rr + 0.6 is the wall ring (rr = 2 or 3)
 *  y12-16 cone, one ring smaller per row   centre (cx, cz) = ((w-1)/2, rr+2)
 *  y10-11 lantern room: pillars + glass    front margin v0: step and path, v1: talus
 *  y9 gallery floor (r + 1) and rail
 *  y7-8 calcite storey, window slits
 *  y4-5 stone storey                       floors at y3, y6 and y9, ladder against the back wall
 *  y1-2 deepslate storey, door, buttresses y0 flared base (stairs round the foot)
 * </pre>
 *
 * Size: 8–10 × 8–10 × 21+.
 */
public final class HouseTowerLookout implements Variant {
	private static final int F1 = 3;
	private static final int F2 = 6;
	private static final int GAL = 9;
	private static final int CEIL = 12;

	@Override
	public String id() {
		return "house_tower_lookout";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 8;
	}

	@Override
	public int minD() {
		return 8;
	}

	@Override
	public int minH() {
		return 21;
	}

	@Override
	public int maxW() {
		return 10;
	}

	@Override
	public int maxD() {
		return 10;
	}

	private record Scheme(Block log, Block trim, Block planks, Block fence, Block door, Block trap, Block roof, Block roofStair, Block roofSlab, Block eaveStair) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR,
					Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.CHERRY_STAIRS),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR,
					Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.DEEPSLATE_TILE_STAIRS),
	};

	/** Disc geometry of one build. */
	private record G(Plot p, Scheme s, int cx, int cz, int rr, int w, int d) {
		boolean in(int u, int v, double rad) {
			double du = u - cx;
			double dv = v - cz;
			return du * du + dv * dv <= rad * rad;
		}

		double wallR() {
			return rr + 0.6;
		}

		/** Inside the disc of radius rad with at least one of the 8 neighbours outside. */
		boolean rim8(int u, int v, double rad) {
			if (!in(u, v, rad)) {
				return false;
			}
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					if (!in(u + du, v + dv, rad)) {
						return true;
					}
				}
			}
			return false;
		}

		boolean rim4(int u, int v, double rad) {
			if (!in(u, v, rad)) {
				return false;
			}
			return !in(u + 1, v, rad) || !in(u - 1, v, rad) || !in(u, v + 1, rad) || !in(u, v - 1, rad);
		}

		boolean wall(int u, int v) {
			return rim8(u, v, wallR());
		}

		/** The direction that points from this cell towards the centre (the ascent of a roof stair). */
		Direction inward(int u, int v) {
			int dx = cx - u;
			int dz = cz - v;
			if (Math.abs(dx) > Math.abs(dz)) {
				return dx > 0 ? Direction.EAST : Direction.WEST;
			}
			return dz > 0 ? Direction.SOUTH : Direction.NORTH;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x100C_0077L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int rr = Math.min((p.w() - 2) / 2, (p.d() - 4) / 2);
		G g = new G(p, s, (p.w() - 1) / 2, rr + 2, rr, p.w(), p.d());
		base(c, g);
		shaft(c, g);
		floors(c, g);
		gallery(c, g);
		lanternRoom(c, g);
		cone(c, g);
		porch(c, g);
		buttresses(c, g);
		yard(c, g, r);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState deepslate(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 7)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			case 2 -> b(Blocks.POLISHED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 8)) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	private static BlockState plaster(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 9) == 0 ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.CALCITE);
	}

	private static Block pinkPane() {
		return Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK);
	}

	// ------------------------------------------------------------------ base

	/** The foot: the disc of the wall ring in dark stone, and a talus of stairs one block wider. */
	private static void base(CitadelCanvas c, G g) {
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (g.in(u, v, g.wallR())) {
					set(c, g.p, u, 0, v, g.wall(u, v) ? deepslate(g.p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
				} else if (g.in(u, v, g.rr + 1.6)) {
					set(c, g.p, u, 0, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, g.inward(u, v), false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ shaft

	private static BlockState shaftBlock(G g, int u, int y, int v) {
		if (y == F1 || y == F2) {
			return b(Blocks.POLISHED_DEEPSLATE); // string courses (dark band) at the floors
		}
		if (y <= 2) {
			return deepslate(g.p, u, y, v);
		}
		if (y <= 5) {
			return stone(g.p, u, y, v);
		}
		return plaster(g.p, u, y, v);
	}

	private static boolean windowCell(G g, int u, int v) {
		int du = Math.abs(u - g.cx);
		int dv = Math.abs(v - g.cz);
		return (du == 1 && dv >= g.rr) || (dv == 1 && du >= g.rr);
	}

	private static boolean cardinal(G g, int u, int v) {
		return u == g.cx || v == g.cz;
	}

	private void shaftRing(CitadelCanvas c, G g, int y0, int y1) {
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (!g.wall(u, v)) {
					continue;
				}
				for (int y = y0; y <= y1; y++) {
					BlockState st = shaftBlock(g, u, y, v);
					boolean row = y == 2 || y == 5 || y == 8;
					if (windowCell(g, u, v) && row) {
						// [depth] a slit window: glass pane set back, a stone sill sticking out below
						boolean alongU = Math.abs(g.inward(u, v).getStepX()) == 0;
						st = pane(g.s.fence() == Blocks.CHERRY_FENCE && g.p.hash(u, y, v, 3) == 0 ? pinkPane() : Blocks.GLASS_PANE, alongU);
					}
					set(c, g.p, u, y, v, st);
				}
			}
		}
	}

	private void shaft(CitadelCanvas c, G g) {
		shaftRing(c, g, 1, 8);
		// [depth] sills: upside-down stairs sticking out under each window, with a flower pot
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (!g.wall(u, v) || !windowCell(g, u, v)) {
					continue;
				}
				for (int y : new int[] {2, 5, 8}) {
					Direction out = g.inward(u, v).getOpposite();
					int su = u + out.getStepX();
					int sv = v + out.getStepZ();
					if (g.wall(su, sv) || g.in(su, sv, g.wallR())) {
						continue;
					}
					set(c, g.p, su, y - 1, sv, stair(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), true));
					if (y > 2 && g.p.hash(su, y, sv, 2) == 0) {
						set(c, g.p, su, y, sv, b(Blocks.POTTED_PINK_TULIP));
					}
				}
			}
		}
		// pilasters up the four cardinal faces (the front one is the porch)
		for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH}) {
			int pu = g.cx + dir.getStepX() * (g.rr + 1);
			int pv = g.cz + dir.getStepZ() * (g.rr + 1);
			for (int y = 1; y <= 8; y++) {
				set(c, g.p, pu, y, pv, y <= 2 ? b(Blocks.DEEPSLATE_BRICKS) : log(g.s.log(), Direction.Axis.Y));
			}
		}
	}

	// ------------------------------------------------------------------ floors, ladder, interior

	private void floors(CitadelCanvas c, G g) {
		int ladderU = g.cx;
		int ladderV = g.cz + g.rr - 1;
		for (int fy : new int[] {F1, F2}) {
			for (int u = 0; u < g.w; u++) {
				for (int v = 0; v < g.d; v++) {
					if (g.in(u, v, g.wallR()) && !g.wall(u, v) && !(u == ladderU && v == ladderV)) {
						set(c, g.p, u, fy, v, b(Blocks.SPRUCE_PLANKS));
					}
				}
			}
		}
		// ladder against the back wall, up to the gallery floor
		for (int y = 1; y <= GAL; y++) {
			set(c, g.p, ladderU, y, ladderV, b(Blocks.LADDER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}
		for (int y : new int[] {3, 7, 10}) {
			put(c, g.p, g.cx, y, g.cz, hangingLantern(), CitadelCanvas.HANGING);
		}
		set(c, g.p, g.cx - 1, 1, g.cz + 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		set(c, g.p, g.cx + 1, 4, g.cz + 1, b(Blocks.BOOKSHELF));
		set(c, g.p, g.cx - 1, 7, g.cz + 1, b(Blocks.CRAFTING_TABLE));
	}

	// ------------------------------------------------------------------ gallery

	private void gallery(CitadelCanvas c, G g) {
		double rg = g.rr + 1.6;
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (!g.in(u, v, rg)) {
					continue;
				}
				boolean outer = !g.in(u, v, g.wallR());
				if (outer) {
					// the corbelled floor ring: planks on a ring of upside-down stairs
					set(c, g.p, u, GAL, v, b(g.s.planks()));
					set(c, g.p, u, GAL - 1, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, g.inward(u, v), true));
					// rail posts and rails round the outside
					boolean post = Math.abs(u - g.cx) == Math.abs(v - g.cz) && Math.abs(u - g.cx) > 0;
					boolean door = u > g.cx && v == g.cz;
					if (post) {
						set(c, g.p, u, GAL + 1, v, log(g.s.trim(), Direction.Axis.Y));
						set(c, g.p, u, GAL + 2, v, log(g.s.trim(), Direction.Axis.Y));
					} else {
						boolean n = g.in(u, v - 1, rg) && !g.in(u, v - 1, g.wallR());
						boolean e = g.in(u + 1, v, rg) && !g.in(u + 1, v, g.wallR());
						boolean s2 = g.in(u, v + 1, rg) && !g.in(u, v + 1, g.wallR());
						boolean w2 = g.in(u - 1, v, rg) && !g.in(u - 1, v, g.wallR());
						set(c, g.p, u, GAL + 1, v, fence(g.s.fence(), n, e, s2, w2));
					}
					if (door) {
						// the way out to the gallery: a gap in the rail at the east
						set(c, g.p, u, GAL + 1, v, b(Blocks.AIR));
					}
				} else if (!g.wall(u, v)) {
					set(c, g.p, u, GAL, v, (u == g.cx && v == g.cz + g.rr - 1) ? b(Blocks.AIR) : b(Blocks.SPRUCE_PLANKS));
				}
			}
		}
	}

	// ------------------------------------------------------------------ lantern room

	private static boolean pillarCell(G g, int u, int v) {
		int du = Math.abs(u - g.cx);
		int dv = Math.abs(v - g.cz);
		return du == 0 || dv == 0 || du == dv;
	}

	private void lanternRoom(CitadelCanvas c, G g) {
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (!g.wall(u, v)) {
					continue;
				}
				// the course at the foot of the lantern room (the gallery floor level is the planks ring)
				boolean eastDoor = u > g.cx && v == g.cz;
				for (int y = GAL; y <= CEIL - 1; y++) {
					BlockState st;
					if (y == GAL) {
						st = b(g.s.planks());
					} else if (eastDoor) {
						st = y == GAL + 1 ? doorBlock(g, Direction.EAST, true) : doorBlock(g, Direction.EAST, false);
					} else if (pillarCell(g, u, v)) {
						st = log(g.s.trim(), Direction.Axis.Y);
					} else {
						boolean n = g.wall(u, v - 1);
						boolean e = g.wall(u + 1, v);
						boolean s2 = g.wall(u, v + 1);
						boolean w2 = g.wall(u - 1, v);
						st = pane(g.p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, true)
								.setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.EAST, e)
								.setValue(BlockStateProperties.SOUTH, s2).setValue(BlockStateProperties.WEST, w2);
					}
					set(c, g.p, u, y, v, st);
				}
			}
		}
		// the ceiling disc and a ring beam
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (g.in(u, v, g.wallR())) {
					set(c, g.p, u, CEIL - 1 + 1, v, g.wall(u, v) ? b(g.s.planks()) : b(g.s.roof()));
				}
			}
		}
	}

	private static BlockState doorBlock(G g, Direction facing, boolean lower) {
		return g.s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	// ------------------------------------------------------------------ cone roof

	private void cone(CitadelCanvas c, G g) {
		double r0 = g.rr + 1.6;
		int y = CEIL;
		int k = 0;
		double r = r0;
		int topY = y;
		while (r >= 0.0) {
			boolean single = !g.in(g.cx + 1, g.cz, r) && !g.in(g.cx - 1, g.cz, r) && !g.in(g.cx, g.cz + 1, r) && !g.in(g.cx, g.cz - 1, r);
			for (int u = 0; u < g.w; u++) {
				for (int v = 0; v < g.d; v++) {
					if (!g.in(u, v, r)) {
						continue;
					}
					Block stairBlock = k == 0 ? g.s.eaveStair() : g.s.roofStair();
					if (single) {
						set(c, g.p, u, y, v, slab(g.s.roofSlab(), false));
					} else if (g.rim4(u, v, r)) {
						set(c, g.p, u, y, v, stair(stairBlock, g.inward(u, v), false));
						if (k == 0) {
							// [depth] the eave ring closes underneath with an upside-down stair
							set(c, g.p, u, y - 1, v, stair(g.s.roofStair(), g.inward(u, v).getOpposite(), true).setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
						}
					} else {
						set(c, g.p, u, y, v, b(g.s.roof()));
					}
				}
			}
			topY = y;
			k++;
			y++;
			r = r0 - 0.9 * k;
			if (single) {
				break;
			}
		}
		// finial: cherry fence, a lantern and a lightning rod
		set(c, g.p, g.cx, topY + 1, g.cz, fence(g.s.fence(), false, false, false, false));
		set(c, g.p, g.cx, topY + 2, g.cz, lantern());
		set(c, g.p, g.cx, topY + 3, g.cz, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
	}

	// ------------------------------------------------------------------ porch, buttresses, yard

	/** The front door: a lintel, a hood on posts, a step and hanging lanterns. */
	private void porch(CitadelCanvas c, G g) {
		int vd = g.cz - g.rr; // front-most wall cell at the centre column
		while (!g.wall(g.cx, vd)) {
			vd++;
		}
		int cu = g.cx;
		set(c, g.p, cu, 1, vd, doorBlock(g, Direction.NORTH, true));
		set(c, g.p, cu, 2, vd, doorBlock(g, Direction.NORTH, false));
		// [depth] the doorway frame stands proud: log jambs and a lintel course
		for (int side = -1; side <= 1; side += 2) {
			for (int y = 1; y <= 2; y++) {
				set(c, g.p, cu + side, y, vd - 1, log(g.s.trim(), Direction.Axis.Y));
			}
			set(c, g.p, cu + side, 3, vd - 1, stair(g.s.eaveStair(), side < 0 ? Direction.EAST : Direction.WEST, true));
			put(c, g.p, cu + side * 2, 2, vd - 1, hangingLantern(), CitadelCanvas.HANGING);
			set(c, g.p, cu + side * 2, 1, vd - 1, b(Blocks.MOSS_BLOCK));
			set(c, g.p, cu + side * 2, 2, vd - 1, side < 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
		set(c, g.p, cu, 3, vd - 1, stair(g.s.eaveStair(), Direction.SOUTH, true));
		set(c, g.p, cu, 3, vd, stair(g.s.eaveStair(), Direction.SOUTH, false));
		set(c, g.p, cu, 0, vd - 1, b(Blocks.POLISHED_DEEPSLATE));
		set(c, g.p, cu, 0, vd - 2, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		set(c, g.p, cu - 1, 0, vd - 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		set(c, g.p, cu + 1, 0, vd - 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
	}

	/** Buttress walls at the foot of the east, west and back faces. */
	private void buttresses(CitadelCanvas c, G g) {
		for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH}) {
			int dist = g.rr + 2;
			int bu = g.cx + dir.getStepX() * dist;
			int bv = g.cz + dir.getStepZ() * dist;
			set(c, g.p, bu, 0, bv, b(Blocks.POLISHED_DEEPSLATE));
			set(c, g.p, bu, 1, bv, wall(Blocks.DEEPSLATE_BRICK_WALL, false, false, false, false, true));
			for (int side = -1; side <= 1; side += 2) {
				int su = bu + dir.getStepZ() * side;
				int sv = bv + dir.getStepX() * side;
				set(c, g.p, su, 0, sv, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
			}
		}
	}

	private void yard(CitadelCanvas c, G g, Random r) {
		// planted corners round the talus, deterministic by seed
		for (int u = 0; u < g.w; u++) {
			for (int v = 0; v < g.d; v++) {
				if (g.in(u, v, g.rr + 3.2) && !g.in(u, v, g.rr + 1.6) && v > 0 && g.p.hash(u, 0, v, 3) == 0
						&& !(Math.abs(u - g.cx) <= 1 && v < g.cz)) {
					set(c, g.p, u, 0, v, b(Blocks.MOSS_BLOCK));
					set(c, g.p, u, 1, v, g.p.hash(u, 1, v, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.FERN));
				}
			}
		}
		// a barrel and a woodpile by the door
		set(c, g.p, g.cx - 3, 0, g.cz - g.rr - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		set(c, g.p, g.cx + 3, 0, g.cz - g.rr - 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
		// a hanging shutter on the stone course (trapdoors)
		for (Direction dir : new Direction[] {Direction.NORTH}) {
			set(c, g.p, g.cx - 1, 2, g.cz - g.rr - 1, trapdoor(g.s.trap(), dir, true, false));
		}
	}
}
