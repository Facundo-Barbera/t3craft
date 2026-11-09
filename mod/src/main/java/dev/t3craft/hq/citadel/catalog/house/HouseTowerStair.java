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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A slim three-storey tower house with an outside stair that wraps its east and back walls, walked from the
 * front lane: a masonry flight (one step per cell, first step head-on) climbs the east wall to a landing at the
 * back corner, where a door opens on the study; a second flight turns west along the back wall to a second
 * landing, which walks straight into the open mirador at the top. The ground floor is a workshop, the first
 * floor a study, the top an arcaded belvedere under the roof. Every room is four blocks clear (a person can
 * stand on any one-block piece of furniture and still have headroom).
 *
 * <p>Palette, dark to light, bottom to top: deepslate bricks (cracked / cobbled mix), calcite plaster
 * with quartz patches, stripped cherry pillars and a calcite gable, framed in dark oak, under a
 * deepslate-tile roof edged in cherry.
 *
 * <pre>
 *  u: ox | u1-1 pilaster col | u1..u2 body (7 or 9) | iu stair lane | ou rail lane
 *  v: 0-1 porch | vf=2 front wall | vb back wall (vf+8..vf+10) | vr stair row | vs stringer row
 *  y: 0 floor | 1-4 workshop | 5 floor | 6-9 study | 10 floor | 11-14 mirador | 15 plate | 15+ roof
 *  stair: flight 1 on iu, v vb-7..vb-2, y0..5 → landing 1 (iu,ou) v vb-1..vs at y5 (study door at vb-1)
 *         → flight 2 on row vr, u u2..u2-4, y6..10 → landing 2 at y10, straight into the mirador
 * </pre>
 *
 * Size: 12–16 × 13–16 × 22+ (the roof ridge reaches y 19–21).
 */
public final class HouseTowerStair implements Variant {
	private static final int F1 = 5;
	private static final int F2 = 10;
	private static final int PLATE = 15;

	@Override
	public String id() {
		return "house_tower_stair";
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
		return 13;
	}

	@Override
	public int minH() {
		return 22;
	}

	@Override
	public int maxW() {
		return 16;
	}

	@Override
	public int maxD() {
		return 16;
	}

	/** Two colourways per seed. */
	private record Scheme(Block frame, Block trim, Block roof, Block roofSlab, Block eave, Block planks, Block fence, Block door) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
					Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR),
	};

	/** Resolved layout of one build. */
	private record L(Plot p, Scheme s, int w, int d, int u1, int u2, int iu, int ou, int cu, int vf, int vb, int vr, int vs, int vm) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x70E7_5741L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int w = p.w();
		int d = p.d();
		int bw = w >= 15 ? 9 : 7;
		int ox = Math.max(0, (w - (bw + 3)) / 2);
		int u1 = ox + 1;
		int u2 = u1 + bw - 1;
		int vf = 2;
		int bd = Math.min(d - 4, 11);
		int vb = vf + bd - 1;
		L l = new L(p, s, w, d, u1, u2, u2 + 1, u2 + 2, (u1 + u2) / 2, vf, vb, vb + 1, vb + 2, (vf + vb) / 2);

		plinth(c, l);
		walls(c, l);
		floors(c, l);
		posts(c, l);
		mirador(c, l);
		porch(c, l);
		stairs(c, l);
		roof(c, l);
		furnish(c, l);
		yard(c, l);
	}

	// ------------------------------------------------------------------ materials

	private static Block pinkPane() {
		return Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK);
	}

	/** [palette] ground storey: four deepslate textures mixed by a per-cell hash. */
	private static BlockState deepslate(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 7)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			case 2 -> b(Blocks.POLISHED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] pilasters and bands: stone bricks, a little mossy / cracked. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 8)) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** [palette] upper storeys: calcite plaster with smooth quartz patches. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 9) == 0 ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.CALCITE);
	}

	private static BlockState infill(L l, int storey, int u, int y, int v) {
		return storey == 0 ? deepslate(l.p, u, y, v) : plaster(l.p, u, y, v);
	}

	private static BlockState frameY(L l) {
		return log(l.s.frame(), Direction.Axis.Y);
	}

	private static BlockState doorBlock(L l, Direction facing, boolean lower) {
		return l.s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	private static BlockState facing(Block block, Direction dir) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	/**
	 * [palette] the stair's masonry: deepslate at the foot, a polished course, stone bricks above, with dark ribs every
	 * third cell running up the south and east faces so the long wall reads as bays.
	 */
	private static void mass(CitadelCanvas c, L l, int u, int v, int y0, int y1) {
		boolean rib = (v == l.vs && (u - l.u1) % 3 == 0) || (u == l.ou && (v - l.vb) % 3 == 0);
		boolean niche = niche(l, u, v);
		for (int y = y0; y <= y1; y++) {
			BlockState st;
			if (niche && y <= 3) {
				continue;
			}
			if (niche || y == 4) {
				st = b(Blocks.POLISHED_DEEPSLATE);
			} else if (rib) {
				st = b(Blocks.DEEPSLATE_BRICKS);
			} else if (y <= 3) {
				st = deepslate(l.p, u, y, v);
			} else {
				st = l.p.hash(u, y, v, 6) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
			}
			set(c, l.p, u, y, v, st);
		}
	}

	/** [depth] blind niches, four clear and one deep, open to the ground, in the bays between the ribs (south and east faces). */
	private static boolean niche(L l, int u, int v) {
		if (v == l.vs) {
			return u >= l.u1 + 4 && u <= l.u2 && (u - l.u1) % 3 != 0;
		}
		return u == l.ou && (v == l.vb || v == l.vb + 1);
	}

	// ------------------------------------------------------------------ layout helpers

	private static boolean perimeter(L l, int u, int v) {
		return u == l.u1 || u == l.u2 || v == l.vf || v == l.vb;
	}

	private static boolean corner(L l, int u, int v) {
		return (u == l.u1 || u == l.u2) && (v == l.vf || v == l.vb);
	}

	/** West-wall window rows (and the east study windows use the first two). */
	private static boolean winV(L l, int v) {
		return v == l.vf + 2 || v == l.vm || v == l.vb - 2;
	}

	/** The two pilaster rows on the west wall, between the windows. */
	private static int[] pilV(L l) {
		return new int[] {(l.vf + 2 + l.vm) / 2, (l.vm + l.vb - 2) / 2};
	}

	private static boolean isPilV(L l, int v) {
		int[] pv = pilV(l);
		return v == pv[0] || v == pv[1];
	}

	private static boolean window(L l, int k, int u, int v) {
		if (v == l.vf) {
			return k == 0 ? Math.abs(u - l.cu) == 2 : (Math.abs(u - l.cu) == 2 || u == l.cu);
		}
		if (u == l.u1) {
			return winV(l, v);
		}
		if (u == l.u2) {
			return k == 1 && (v == l.vf + 2 || v == l.vm);
		}
		return false;
	}

	/** Mirador pillar: the corners, every third bay on the front and back, the pilaster rows on the sides. */
	private static boolean pillar(L l, int u, int v) {
		if (corner(l, u, v)) {
			return true;
		}
		if (v == l.vf || v == l.vb) {
			boolean open = v == l.vb && (u == l.u2 - 5 || u == l.u2 - 4);
			return !open && (u - l.u1) % 3 == 0;
		}
		return isPilV(l, v);
	}

	private static boolean openCell(L l, int u, int v) {
		return v == l.vb && (u == l.u2 - 5 || u == l.u2 - 4);
	}

	// ------------------------------------------------------------------ base

	private static BlockState floorBlock(L l, int u, int v) {
		return l.p.hash(u, 0, v, 3) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS);
	}

	private static void plinth(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int u = l.u1; u <= l.u2; u++) {
			for (int v = l.vf; v <= l.vb; v++) {
				set(c, p, u, 0, v, perimeter(l, u, v) ? deepslate(p, u, 0, v) : floorBlock(l, u, v));
			}
		}
		// [depth] a polished step round the foot: front (except the porch), west and the free part of the back
		for (int u = l.u1; u <= l.u2; u++) {
			if (Math.abs(u - l.cu) <= 1) {
				continue;
			}
			set(c, p, u, 0, l.vf - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
		}
		for (int v = l.vf; v <= l.vb; v++) {
			if (!isPilV(l, v)) {
				set(c, p, l.u1 - 1, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
			}
		}
		for (int u = l.u1 - 1; u <= l.u2 - 8; u++) {
			set(c, p, u, 0, l.vr, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
		}
	}

	// ------------------------------------------------------------------ storeys

	private static void walls(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int k = 0; k < 2; k++) {
			int ya = k == 0 ? 1 : 6;
			for (int dy = 0; dy < 4; dy++) {
				int y = ya + dy;
				for (int u = l.u1; u <= l.u2; u++) {
					for (int v = l.vf; v <= l.vb; v++) {
						if (!perimeter(l, u, v) || corner(l, u, v)) {
							continue;
						}
						set(c, p, u, y, v, wallCell(l, k, dy, u, y, v));
					}
				}
			}
		}
	}

	private static BlockState wallCell(L l, int k, int dy, int u, int y, int v) {
		boolean frontDoor = k == 0 && v == l.vf && u == l.cu;
		boolean sideDoor = k == 1 && u == l.u2 && v == l.vb - 1;
		if (frontDoor || sideDoor) {
			if (dy <= 1) {
				return doorBlock(l, frontDoor ? Direction.NORTH : Direction.EAST, dy == 0);
			}
			if (dy == 2) {
				// an open trapdoor on the outer face closes the gap over the door without lowering the doorway's headroom
				return trapdoor(Blocks.DARK_OAK_TRAPDOOR, frontDoor ? Direction.SOUTH : Direction.WEST, true, false);
			}
			return infill(l, k, u, y, v);
		}
		if ((dy == 1 || dy == 2) && window(l, k, u, v)) {
			Block g = k == 1 && l.p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE;
			return pane(g, v == l.vf || v == l.vb);
		}
		return infill(l, k, u, y, v);
	}

	/** Floor slabs with a banded edge; timbered ceilings (a log under every third row). */
	private static void floors(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int by : new int[] {F1, F2}) {
			for (int u = l.u1; u <= l.u2; u++) {
				for (int v = l.vf; v <= l.vb; v++) {
					if (perimeter(l, u, v)) {
						boolean alongU = v == l.vf || v == l.vb;
						Block band = by == F1 ? l.s.frame() : l.s.trim();
						set(c, p, u, by, v, corner(l, u, v) ? frameY(l) : log(band, alongU ? Direction.Axis.X : Direction.Axis.Z));
					} else if ((v - l.vf) % 3 == 0) {
						set(c, p, u, by, v, log(l.s.frame(), Direction.Axis.X));
					} else {
						set(c, p, u, by, v, floorBlock(l, u, v));
					}
				}
			}
		}
	}

	/** Corner posts to the plate and the pilasters, which carry the eaves. */
	private static void posts(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int u : new int[] {l.u1, l.u2}) {
			for (int v : new int[] {l.vf, l.vb}) {
				for (int y = 1; y < PLATE; y++) {
					set(c, p, u, y, v, y >= F2 + 1 ? log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y) : frameY(l));
				}
			}
		}
		for (int v : pilV(l)) {
			pilaster(c, l, l.u1 - 1, v);
		}
		pilaster(c, l, l.u1, l.vf - 1);
		pilaster(c, l, l.u2, l.vf - 1);
	}

	/** [depth] a pilaster a block proud of the wall: deepslate, a band, stone, a band, cherry up to the eave. */
	private static void pilaster(CitadelCanvas c, L l, int u, int v) {
		Plot p = l.p;
		for (int y = 0; y < PLATE; y++) {
			BlockState st;
			if (y <= 4) {
				st = deepslate(p, u, y, v);
			} else if (y == F1) {
				st = log(l.s.frame(), Direction.Axis.Y);
			} else if (y < F2) {
				st = deepslate(p, u, y, v);
			} else if (y == F2) {
				st = log(l.s.frame(), Direction.Axis.Y);
			} else {
				st = log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y);
			}
			set(c, p, u, y, v, st);
		}
	}

	private static BlockState pot(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 4)) {
			case 0 -> b(Blocks.POTTED_RED_TULIP);
			case 1 -> b(Blocks.POTTED_PINK_TULIP);
			case 2 -> b(Blocks.POTTED_CORNFLOWER);
			default -> b(Blocks.POTTED_FERN);
		};
	}

	// ------------------------------------------------------------------ mirador

	private static BlockState rail(L l, int u, int v) {
		boolean alongU = v == l.vf || v == l.vb;
		return alongU ? fence(Blocks.BAMBOO_FENCE, false, true, false, true) : fence(Blocks.BAMBOO_FENCE, true, false, true, false);
	}

	/**
	 * The open top room: stripped-cherry pillars, a low stone rail between them, a dark lintel with spandrel
	 * stairs at the ends of each bay, lanterns hung in the middle of the wide bays. The back bay by the stair is
	 * open: the second landing walks straight in.
	 */
	private static void mirador(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int u = l.u1; u <= l.u2; u++) {
			for (int v = l.vf; v <= l.vb; v++) {
				if (!perimeter(l, u, v)) {
					continue;
				}
				boolean alongU = v == l.vf || v == l.vb;
				// the plate beam on top of everything
				set(c, p, u, PLATE, v, log(l.s.trim(), alongU ? Direction.Axis.X : Direction.Axis.Z));
				if (pillar(l, u, v)) {
					for (int y = F2 + 1; y < PLATE; y++) {
						set(c, p, u, y, v, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
					}
					continue;
				}
				boolean open = openCell(l, u, v);
				if (!open) {
					set(c, p, u, F2 + 1, v, rail(l, u, v));
				}
				set(c, p, u, PLATE - 1, v, b(Blocks.CALCITE));
				int du = alongU ? 1 : 0;
				int dv = alongU ? 0 : 1;
				boolean before = pillar(l, u - du, v - dv);
				boolean after = pillar(l, u + du, v + dv);
				// spandrel: an upside-down stair under the lintel, next to a pillar, in bays at least two wide (never over the open bay)
				if (!open && before != after) {
					Direction toward = before ? (alongU ? Direction.WEST : Direction.NORTH) : (alongU ? Direction.EAST : Direction.SOUTH);
					set(c, p, u, PLATE - 2, v, stair(Blocks.CHERRY_STAIRS, toward, true));
				}
				if (!open && !before && !after) {
					put(c, p, u, PLATE - 2, v, hangingLantern(), CitadelCanvas.HANGING);
				} else if (!open && p.hash(u, 0, v, 3) == 0) {
					set(c, p, u, F2 + 2, v, pot(p, u, 1, v));
				}
			}
		}
	}

	// ------------------------------------------------------------------ front porch

	private static void porch(CitadelCanvas c, L l) {
		Plot p = l.p;
		int cu = l.cu;
		for (int side = -1; side <= 1; side += 2) {
			int u = cu + side * 2;
			set(c, p, u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
			for (int y = 1; y <= 3; y++) {
				set(c, p, u, y, 0, log(l.s.trim(), Direction.Axis.Y));
			}
		}
		// canopy: a beam on the columns, a cherry lip at the front and a slab roof back to the wall
		for (int u = cu - 2; u <= cu + 2; u++) {
			set(c, p, u, 4, 0, log(l.s.frame(), Direction.Axis.X));
			set(c, p, u, 5, 0, stair(l.s.eave(), Direction.NORTH, false));
			set(c, p, u, 5, 1, slab(l.s.roofSlab(), false));
		}
		for (int u = cu - 1; u <= cu + 1; u++) {
			set(c, p, u, 6, 0, fence(Blocks.BAMBOO_FENCE, false, u < cu + 1, false, u > cu - 1));
		}
		// the lanterns on the canopy edge
		set(c, p, cu - 2, 6, 0, lantern());
		set(c, p, cu + 2, 6, 0, lantern());
		// step and porch floor, flush with the plinth
		for (int u = cu - 1; u <= cu + 1; u++) {
			set(c, p, u, 0, 1, b(Blocks.POLISHED_DEEPSLATE));
			set(c, p, u, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		}
	}

	// ------------------------------------------------------------------ the outside stair

	private static void stairs(CitadelCanvas c, L l) {
		Plot p = l.p;
		int iu = l.iu;
		int ou = l.ou;
		int u2 = l.u2;
		int vs0 = l.vb - 7;
		BlockState step = stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false);
		BlockState post = fence(l.s.fence(), false, false, false, false);
		// --- flight 1: six steps up the east wall (iu), a stringer with a rail on the outer side (ou)
		for (int i = 0; i < 6; i++) {
			int v = vs0 + i;
			set(c, p, iu, i, v, step);
			mass(c, l, iu, v, 0, i - 1);
			set(c, p, ou, i, v, step);
			mass(c, l, ou, v, 0, i - 1);
			set(c, p, ou, i + 1, v, post);
		}
		// --- landing 1 at y5, both lanes, down to the stringer row
		for (int v = l.vb - 1; v <= l.vs; v++) {
			for (int u : new int[] {iu, ou}) {
				mass(c, l, u, v, 0, F1 - 1);
				set(c, p, u, F1, v, b(Blocks.POLISHED_DEEPSLATE));
			}
		}
		for (int v = l.vb - 1; v <= l.vs; v++) {
			set(c, p, ou, F1 + 1, v, fence(l.s.fence(), true, false, v < l.vs, v == l.vs));
		}
		set(c, p, iu, F1 + 1, l.vs, fence(l.s.fence(), false, true, false, false));
		set(c, p, ou, F1 + 2, l.vb, lantern());
		// --- flight 2: five steps west along the back wall (row vr), stringer and rail on row vs
		BlockState west = stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, false);
		for (int j = 0; j < 5; j++) {
			int u = u2 - j;
			int y = F1 + 1 + j;
			set(c, p, u, y, l.vr, west);
			mass(c, l, u, l.vr, 0, y - 1);
			set(c, p, u, y, l.vs, west);
			mass(c, l, u, l.vs, 0, y - 1);
			set(c, p, u, y + 1, l.vs, post);
		}
		// --- landing 2 at y10, three cells, with the root-cellar alcove open to the back under it
		for (int u = u2 - 7; u <= u2 - 5; u++) {
			for (int v : new int[] {l.vr, l.vs}) {
				boolean alcove = u >= u2 - 6;
				for (int y = 0; y < F2; y++) {
					if (alcove && y <= 3) {
						continue;
					}
					mass(c, l, u, v, y, y);
				}
				set(c, p, u, F2, v, b(Blocks.POLISHED_DEEPSLATE));
			}
			set(c, p, u, F2 + 1, l.vs, fence(l.s.fence(), false, true, false, u > u2 - 7));
		}
		set(c, p, u2 - 7, F2 + 1, l.vr, fence(l.s.fence(), false, false, true, false));
		set(c, p, u2 - 7, F2 + 1, l.vs, fence(l.s.fence(), true, true, false, false));
		set(c, p, u2 - 5, F2 + 2, l.vs, lantern());
		// alcove: two barrels against the tower wall, a lantern on one (which also keeps its top from being a standable cell)
		set(c, p, u2 - 6, 0, l.vr, barrel());
		set(c, p, u2 - 5, 0, l.vr, barrel());
		put(c, p, u2 - 6, 3, l.vs, hangingLantern(), CitadelCanvas.HANGING);
		for (int u = l.u1 + 4; u <= u2; u++) {
			if ((u - l.u1) % 3 == 1) {
				put(c, p, u, 3, l.vs, hangingLantern(), CitadelCanvas.HANGING);
			}
		}
		put(c, p, ou, 3, l.vb, hangingLantern(), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ roof

	/**
	 * Hip roof: every ring one block in and one up, cherry eaves all round (an overhang of one block), outer-corner stairs
	 * on the hips, a tile ridge on top and a gold-capped finial with a lantern at its middle.
	 */
	private static void roof(CitadelCanvas c, L l) {
		Plot p = l.p;
		int uw = l.u1 - 1;
		int ue = l.u2 + 1;
		int vn = l.vf - 1;
		int vsr = l.vb + 1;
		int m = Math.min((ue - uw) / 2, (vsr - vn) / 2);
		int mid = (vn + vsr) / 2;
		for (int v = vn; v <= vsr; v++) {
			for (int u = uw; u <= ue; u++) {
				int du = Math.min(u - uw, ue - u);
				int dv = Math.min(v - vn, vsr - v);
				int i = Math.min(du, dv);
				int y = PLATE + i;
				if (i >= m) {
					boolean apex = u == l.cu && v == mid;
					set(c, p, u, y, v, apex ? b(Blocks.GOLD_BLOCK) : b(Blocks.DEEPSLATE_TILES));
					continue;
				}
				Block blk = i == 0 ? l.s.eave() : l.s.roof();
				Direction fu = u - uw < ue - u ? Direction.EAST : Direction.WEST;
				Direction fv = v - vn < vsr - v ? Direction.SOUTH : Direction.NORTH;
				if (du < dv) {
					set(c, p, u, y, v, stair(blk, fu, false));
				} else if (dv < du) {
					set(c, p, u, y, v, stair(blk, fv, false));
				} else {
					StairsShape shape = fu == fv.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
					set(c, p, u, y, v, stair(blk, fv, false).setValue(BlockStateProperties.STAIRS_SHAPE, shape));
				}
			}
		}
		// finial: fence rod and a lantern on the gold cap, as high as the plot allows
		int apexY = PLATE + m;
		int lanternY = Math.min(apexY + 3, p.h() - 1);
		for (int y = apexY + 1; y < lanternY; y++) {
			set(c, p, l.cu, y, mid, fence(l.s.fence(), false, false, false, false));
		}
		set(c, p, l.cu, lanternY, mid, lantern());
	}

	// ------------------------------------------------------------------ interiors

	private static void furnish(CitadelCanvas c, L l) {
		workshop(c, l);
		study(c, l);
		belvedere(c, l);
	}

	/** Ground floor: crafting, smithing, forge and storage along the walls, a clear aisle of 3 down the middle. */
	private static void workshop(CitadelCanvas c, L l) {
		Plot p = l.p;
		int wu = l.u1 + 1;
		int idx = 0;
		for (int v = l.vf + 1; v <= l.vb - 1; v++, idx++) {
			BlockState st = switch (idx) {
				case 0 -> b(Blocks.CRAFTING_TABLE);
				case 1 -> b(Blocks.SMITHING_TABLE);
				case 2 -> facing(Blocks.ANVIL, Direction.EAST);
				case 3 -> null;
				case 4 -> facing(Blocks.BLAST_FURNACE, Direction.EAST);
				case 5 -> b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
				default -> barrel();
			};
			if (st != null) {
				set(c, p, wu, 1, v, st);
			}
		}
		int bv = l.vb - 1;
		// lanterns hang over bare floor only: one over a piece of furniture would leave it a ledge with no headroom
		put(c, p, l.u1 + 2, 4, l.vm, hangingLantern(), CitadelCanvas.HANGING);
		put(c, p, l.u2 - 2, 4, l.vm, hangingLantern(), CitadelCanvas.HANGING);
		set(c, p, l.u1 + 2, 1, bv, facing(Blocks.STONECUTTER, Direction.NORTH));
		set(c, p, l.u1 + 3, 1, bv, facing(Blocks.CHEST, Direction.NORTH));
		set(c, p, l.u1 + 4, 1, bv, facing(Blocks.CHEST, Direction.NORTH));
		set(c, p, l.u2 - 1, 1, bv, barrel());
		set(c, p, l.u2 - 1, 1, l.vf + 1, barrel());
		set(c, p, l.u2 - 1, 1, l.vf + 2, barrel());
		put(c, p, l.cu, 4, l.vm, hangingLantern(), CitadelCanvas.HANGING);
	}

	/** First floor: full-height bookshelves, a map desk, lecterns and a rug. */
	private static void study(CitadelCanvas c, L l) {
		Plot p = l.p;
		int wu = l.u1 + 1;
		for (int v = l.vf + 1; v <= l.vb - 1; v++) {
			if (v == l.vf + 2) {
				set(c, p, wu, 6, v, b(Blocks.CARTOGRAPHY_TABLE));
			} else if (v == l.vm) {
				set(c, p, wu, 6, v, facing(Blocks.LECTERN, Direction.EAST));
			} else if (v == l.vb - 2) {
				set(c, p, wu, 6, v, b(Blocks.ENCHANTING_TABLE));
			} else {
				for (int y = 6; y <= 9; y++) {
					set(c, p, wu, y, v, b(Blocks.BOOKSHELF));
				}
			}
		}
		int bv = l.vb - 1;
		for (int u = l.u1 + 2; u <= l.u2 - 3; u++) {
			for (int y = 6; y <= 9; y++) {
				set(c, p, u, y, bv, b(Blocks.BOOKSHELF));
			}
		}
		set(c, p, l.cu - 2, 6, l.vf + 1, facing(Blocks.LECTERN, Direction.NORTH));
		set(c, p, l.cu + 2, 6, l.vf + 1, facing(Blocks.LECTERN, Direction.NORTH));
		set(c, p, l.u2 - 1, 6, l.vf + 1, facing(Blocks.CHEST, Direction.WEST));
		for (int u = l.cu - 1; u <= l.cu + 1; u++) {
			for (int v = l.vm - 1; v <= l.vm + 1; v++) {
				set(c, p, u, 6, v, b(Blocks.CARPET.pick(DyeColor.PINK)));
			}
		}
		put(c, p, l.cu, 9, l.vf + 2, hangingLantern(), CitadelCanvas.HANGING);
		put(c, p, l.cu, 9, l.vb - 2, hangingLantern(), CitadelCanvas.HANGING);
	}

	/** Top floor: benches along the rails, a map table, a rug. */
	private static void belvedere(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int v = l.vf + 2; v <= l.vb - 3; v++) {
			set(c, p, l.u1 + 1, 11, v, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
			set(c, p, l.u2 - 1, 11, v, stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
		}
		set(c, p, l.cu, 11, l.vm, b(Blocks.CARTOGRAPHY_TABLE));
		for (int u = l.cu - 1; u <= l.cu + 1; u++) {
			for (int v = l.vm - 1; v <= l.vm + 1; v++) {
				if (u != l.cu || v != l.vm) {
					set(c, p, u, 11, v, b(Blocks.CARPET.pick(DyeColor.PINK)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ yard

	private static void yard(CitadelCanvas c, L l) {
		Plot p = l.p;
		// flower beds between the west pilasters
		for (int v = l.vf + 1; v < l.vb; v++) {
			if (isPilV(l, v) || winV(l, v)) {
				continue;
			}
			int k = p.hash(0, 0, v, 4);
			if (k <= 1) {
				set(c, p, l.u1 - 2, 0, v, b(Blocks.MOSS_BLOCK));
				set(c, p, l.u1 - 2, 1, v, k == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			}
		}
		set(c, p, l.u1 - 2, 0, l.vf - 1, b(Blocks.MOSS_BLOCK));
		set(c, p, l.u1 - 2, 1, l.vf - 1, b(Blocks.FLOWERING_AZALEA));
	}
}
