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
 * A slim four-storey tower house with an outside stair clinging to its east wall: a masonry ramp
 * climbs to the middle-floor door, a timber flight returns along the wall to a covered front terrace
 * (third floor), and a last flight climbs again to the attic-floor door at the back. A porch with a
 * small balcony over the front door, flower boxes on every sill and a steep dark roof with cherry
 * eaves finish the silhouette.
 *
 * <p>Palette, dark to light, bottom to top: deepslate bricks (cracked / cobbled mix), stone bricks
 * (mossy / cracked mix), calcite plaster, all framed in cherry, under a deepslate-tile roof edged in
 * cherry.
 *
 * <pre>
 *  u: 0 eave | 1..u2 body (odd, 5..7) | u2+1 inner lane | u2+2 outer lane | spare
 *  v: 0-1 porch / terrace | 2 front wall | .. | d-2 back wall | d-1 eave
 *  y: 0 plinth | 1-2 | 3 floor | 4-5 | 6 floor | 7-8 | 9 floor | 10-11 | 12 plate | 13+ roof
 *  stair: flight 1 (outer lane, v2→v5, y0-3) → landing v6 y3 → flight 2 (inner lane, v5→v3, y4-6)
 *         → terrace/landing v2 y6 → flight 3 (outer lane, v3→v5, y7-9) → landing v6 y9
 * </pre>
 *
 * Size: 9–11 × 9–11 × 19+ (ridge at y 16–18, finial above, all clipped by the plot when short).
 */
public final class HouseTowerStair implements Variant {
	private static final int B1 = 3;
	private static final int B2 = 6;
	private static final int B3 = 9;
	private static final int PLATE = 12;

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
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 19;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 11;
	}

	/** Two colourways per seed. */
	private record Scheme(Block frame, Block trim, Block roof, Block roofSlab, Block eave, Block planks, Block fence, Block door, Block trap) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
					Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR),
	};

	/** Resolved layout of one build. */
	private record L(Plot p, Scheme s, int w, int d, int u1, int u2, int iu, int ou, int su, int vf, int vb, int cu, int vm, int vd) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x70E7_5741L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int w = p.w();
		int d = p.d();
		int bw = Math.min(5, w - 4); // always a slim 5-wide body
		if (bw % 2 == 0) {
			bw--;
		}
		int u1 = 1;
		int u2 = u1 + bw - 1;
		int vf = 2;
		int vb = d - 2;
		L l = new L(p, s, w, d, u1, u2, u2 + 1, u2 + 2, u2 + 3, vf, vb, (u1 + u2) / 2, (vf + vb) / 2, vf + 4);

		plinth(c, l);
		for (int k = 1; k <= 4; k++) {
			storey(c, l, k);
		}
		floors(c, l);
		buttresses(c, l);
		porch(c, l);
		terrace(c, l);
		stairs(c, l);
		roof(c, l);
		chimney(c, l);
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

	/** [palette] second storey: stone bricks, a little mossy / cracked. */
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
		return switch (storey) {
			case 1 -> deepslate(l.p, u, y, v);
			case 2 -> stone(l.p, u, y, v);
			case 3 -> l.p.hash(u, y, v, 3) == 0 ? stone(l.p, u, y, v) : plaster(l.p, u, y, v);
			default -> plaster(l.p, u, y, v);
		};
	}

	private static int y0(int storey) {
		return 1 + 3 * (storey - 1);
	}

	private static BlockState frameY(L l) {
		return log(l.s.frame(), Direction.Axis.Y);
	}

	private static BlockState doorBlock(L l, Direction facing, boolean lower) {
		return l.s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	// ------------------------------------------------------------------ base

	private static void plinth(CitadelCanvas c, L l) {
		for (int u = l.u1; u <= l.u2; u++) {
			for (int v = l.vf; v <= l.vb; v++) {
				boolean edge = u == l.u1 || u == l.u2 || v == l.vf || v == l.vb;
				set(c, l.p, u, 0, v, edge ? deepslate(l.p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		// [depth] a polished ledge round the west and back foot of the tower
		for (int v = l.vf; v <= l.vb + 1; v++) {
			set(c, l.p, l.u1 - 1, 0, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		}
		for (int u = l.u1 - 1; u <= l.u2; u++) {
			set(c, l.p, u, 0, l.vb + 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		}
	}

	// ------------------------------------------------------------------ storeys

	private static boolean frontPost(L l, int u) {
		return u == l.u1 || u == l.u2;
	}

	private static boolean sidePost(L l, int v) {
		return v == l.vm && l.vb - l.vf >= 7; // only deep walls get a middle post
	}

	/** One window per gap between posts, centred (front and back walls). */
	private static boolean winF(L l, int u) {
		return Math.abs(u - l.cu) == 1;
	}

	/** One window per gap between posts, centred (side walls). */
	private static boolean winS(L l, int v) {
		return l.vb - l.vf >= 7 ? (v == (l.vf + l.vm) / 2 || v == (l.vm + l.vb) / 2) : v == l.vm;
	}

	private static Block glass(L l, int storey, int i) {
		return storey >= 3 && l.p.hash(i, storey, 1, 3) == 0 ? pinkPane() : Blocks.GLASS_PANE;
	}

	/** Two interior rows per storey: y0 and y0+1. Windows sit back one block behind a sill. */
	private static void storey(CitadelCanvas c, L l, int storey) {
		Plot p = l.p;
		int y0 = y0(storey);
		for (int y = y0; y <= y0 + 1; y++) {
			boolean win = storey == 4 || y == y0 + 1;
			// front and back walls
			for (int u = l.u1; u <= l.u2; u++) {
				for (int side = 0; side < 2; side++) {
					int v = side == 0 ? l.vf : l.vb;
					boolean door = side == 0 && ((storey == 1 && u == l.cu) || (storey == 2 && u == l.cu) || (storey == 3 && u == l.cu));
					BlockState st;
					if (door) {
						st = doorBlock(l, Direction.NORTH, y == y0);
					} else if (frontPost(l, u)) {
						st = frameY(l);
					} else if (win && winF(l, u) && !(storey == 1 && side == 1)) {
						set(c, p, u, y, side == 0 ? v + 1 : v - 1, pane(glass(l, storey, u), true));
						st = b(Blocks.AIR);
					} else {
						st = infill(l, storey, u, y, v);
					}
					set(c, p, u, y, v, st);
				}
			}
			// west and east walls
			for (int v = l.vf + 1; v < l.vb; v++) {
				for (int side = 0; side < 2; side++) {
					int u = side == 0 ? l.u1 : l.u2;
					boolean stairDoor = side == 1 && (storey == 2 || storey == 4) && v == l.vd;
					BlockState st;
					if (stairDoor) {
						st = doorBlock(l, Direction.EAST, y == y0);
					} else if (sidePost(l, v)) {
						st = log(l.s.frame(), Direction.Axis.Y);
					} else if (win && winS(l, v) && !(side == 1 && v == l.vd)) {
						set(c, p, side == 0 ? u + 1 : u - 1, y, v, pane(glass(l, storey, v), false));
						st = b(Blocks.AIR);
					} else {
						st = infill(l, storey, u, y, v);
					}
					set(c, p, u, y, v, st);
				}
			}
		}
		// [depth] sills with flower boxes under the windows of the front, back and west faces
		int sillY = y0 + (storey == 4 ? 0 : 1);
		for (int u = l.u1 + 1; u < l.u2; u++) {
			if (!winF(l, u)) {
				continue;
			}
			set(c, p, u, sillY - 1, l.vf - 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
			if (storey >= 2) {
				set(c, p, u, sillY, l.vf - 1, p.hash(u, storey, 0, 2) == 0 ? b(Blocks.POTTED_RED_TULIP) : b(Blocks.POTTED_PINK_TULIP));
			}
			set(c, p, u, sillY - 1, l.vb + 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
		}
		for (int v = l.vf + 1; v < l.vb; v++) {
			if (winS(l, v)) {
				set(c, p, l.u1 - 1, sillY - 1, v, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
				if (storey >= 2) {
					set(c, p, l.u1 - 1, sillY, v, b(Blocks.POTTED_FERN));
				}
			}
		}
	}

	// ------------------------------------------------------------------ floors, string courses, interior

	private static void floors(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int by : new int[] {B1, B2, B3, PLATE}) {
			for (int u = l.u1; u <= l.u2; u++) {
				for (int v = l.vf; v <= l.vb; v++) {
					boolean edge = u == l.u1 || u == l.u2 || v == l.vf || v == l.vb;
					if (edge) {
						boolean alongU = v == l.vf || v == l.vb;
						if (by == PLATE) {
							set(c, p, u, by, v, log(l.s.trim(), alongU ? Direction.Axis.X : Direction.Axis.Z));
						} else {
							// [finish] thin bands: the floor edge is wall material (one shade lighter than the course below) and
							// only a half-block ledge of stairs marks the storey; posts run through
							boolean post = alongU ? frontPost(l, u) : sidePost(l, v);
							set(c, p, u, by, v, post ? frameY(l) : infill(l, by == B1 ? 2 : by == B2 ? 3 : 4, u, by, v));
						}
					} else {
						set(c, p, u, by, v, b(Blocks.SPRUCE_PLANKS));
					}
				}
			}
			if (by == PLATE) {
				continue;
			}
			// [depth] string course: an upside-down stair round the foot of each upper storey
			for (int u = l.u1; u <= l.u2; u++) {
				if (by == B1 && Math.abs(u - l.cu) <= 1) {
					continue; // the balcony floor is there
				}
				if (by != B2) {
					set(c, p, u, by, l.vf - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, true));
				}
				set(c, p, u, by, l.vb + 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, true));
			}
			for (int v = l.vf; v <= l.vb; v++) {
				set(c, p, l.u1 - 1, by, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, true));
			}
		}
		// the interior is seen through the windows: a lantern per room and a few pieces of furniture
		int cv = (l.vf + l.vb) / 2;
		for (int storey = 1; storey <= 4; storey++) {
			put(c, p, l.cu, y0(storey) + 1, cv, hangingLantern(), CitadelCanvas.HANGING);
		}
		set(c, p, l.u1 + 1, 1, l.vb - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		set(c, p, l.u1 + 1, 4, l.vb - 1, b(Blocks.BOOKSHELF));
		set(c, p, l.u1 + 1, 5, l.vb - 1, b(Blocks.BOOKSHELF));
		set(c, p, l.u1 + 1, 7, l.vb - 1, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		set(c, p, l.u1 + 1, 10, l.vb - 1, b(Blocks.CRAFTING_TABLE));
	}

	/**
	 * [depth] stone buttresses up the west and back walls: dark at the foot, stone in the middle, calcite at the top, a
	 * block proud of the wall, between the windows. They break the long flat faces and make the body read as tapering.
	 */
	private static void buttresses(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int v = l.vf + 1; v < l.vb; v++) {
			if ((v - l.vf) % 2 == 0 && !winS(l, v) && !sidePost(l, v)) {
				for (int y = 1; y <= 8; y++) {
					set(c, p, l.u1 - 1, y, v, infill(l, y <= 2 ? 1 : y <= 5 ? 2 : 4, l.u1 - 1, y, v));
				}
			}
		}
		for (int u = l.u1 + 1; u < l.u2; u++) {
			if (!winF(l, u)) {
				for (int y = 1; y <= 8; y++) {
					set(c, p, u, y, l.vb + 1, infill(l, y <= 2 ? 1 : y <= 5 ? 2 : 4, u, y, l.vb + 1));
				}
			}
		}
	}

	// ------------------------------------------------------------------ front: porch, balcony

	/**
	 * [depth] two cherry columns on dark bases carry the terrace; between them a small balcony over the
	 * door, brackets underneath. Lanterns hang beside the door and a step leads up from the path.
	 */
	private static void porch(CitadelCanvas c, L l) {
		Plot p = l.p;
		int cu = l.cu;
		for (int side = -1; side <= 1; side += 2) {
			int u = cu + side * 2;
			set(c, p, u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
			set(c, p, u, 1, 0, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false, true));
			for (int y = 2; y <= 5; y++) {
				set(c, p, u, y, 0, log(l.s.frame(), Direction.Axis.Y));
			}
			set(c, p, u, 2, 1, stair(Blocks.STONE_BRICK_STAIRS, side < 0 ? Direction.EAST : Direction.WEST, true));
			put(c, p, u - side, 2, 0, hangingLantern(), CitadelCanvas.HANGING);
		}
		// porch floor, step and planters
		for (int u = cu - 1; u <= cu + 1; u++) {
			set(c, p, u, 0, 1, b(Blocks.POLISHED_DEEPSLATE));
		}
		set(c, p, cu, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		set(c, p, cu - 1, 0, 0, b(Blocks.MOSS_BLOCK));
		set(c, p, cu - 1, 1, 0, b(Blocks.FLOWERING_AZALEA));
		set(c, p, cu + 1, 0, 0, b(Blocks.MOSS_BLOCK));
		set(c, p, cu + 1, 1, 0, b(Blocks.AZALEA));
		// balcony floor at y3 over v0..1, three wide: planks framed by logs, brackets underneath
		for (int u = cu - 1; u <= cu + 1; u++) {
			set(c, p, u, B1, 0, u == cu ? b(l.s.planks()) : log(l.s.trim(), Direction.Axis.Z));
			set(c, p, u, B1, 1, u == cu ? b(l.s.planks()) : log(l.s.frame(), Direction.Axis.Z));
		}
		// rail: fence round the front and sides
		set(c, p, cu - 1, B1 + 1, 0, fence(l.s.fence(), false, true, true, false));
		set(c, p, cu, B1 + 1, 0, fence(l.s.fence(), false, true, false, true));
		set(c, p, cu + 1, B1 + 1, 0, fence(l.s.fence(), false, false, true, true));
		set(c, p, cu - 1, B1 + 1, 1, fence(l.s.fence(), true, false, false, false));
		set(c, p, cu + 1, B1 + 1, 1, fence(l.s.fence(), true, false, false, false));
		// the balcony door lintel is the floor beam; a name board over the front door
		set(c, p, cu, 2, l.vf - 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, true));
		put(c, p, cu, 1, l.vf - 1, hangingLantern(), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ third-floor terrace

	/**
	 * The third floor's front terrace: plank floor at y6 over v0..1 and the stair landing, rails all
	 * round, brackets and a central door under the overhanging gable.
	 */
	private static void terrace(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int u = l.u1; u <= l.iu; u++) {
			for (int v = 0; v <= 1; v++) {
				boolean edge = u == l.u1 || u == l.iu || v == 0;
				set(c, p, u, B2, v, edge ? log(l.s.trim(), u == l.u1 || u == l.iu ? Direction.Axis.Z : Direction.Axis.X) : b(l.s.planks()));
			}
			set(c, p, u, B2 - 1, 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
		}
		// rails: front, west and east edges
		for (int u = l.u1; u <= l.iu; u++) {
			set(c, p, u, B2 + 1, 0, fence(l.s.fence(), false, u < l.iu, u == l.iu && false, u > l.u1));
		}
		set(c, p, l.u1, B2 + 1, 1, fence(l.s.fence(), true, false, false, false));
		set(c, p, l.iu, B2 + 1, 1, fence(l.s.fence(), true, false, true, false));
		// the end of the front rail meets the east rail
		set(c, p, l.iu, B2 + 1, 0, fence(l.s.fence(), false, false, true, true));
		put(c, p, l.cu, B2 + 1, 0, b(Blocks.POTTED_PINK_TULIP), CitadelCanvas.DECOR);
		put(c, p, l.cu - 1, B2 - 1, 0, hangingLantern(), CitadelCanvas.HANGING);
		put(c, p, l.cu + 1, B2 - 1, 0, hangingLantern(), CitadelCanvas.HANGING);
		// a column under the east end of the terrace
		for (int y = 1; y < B2; y++) {
			set(c, p, l.iu, y, 0, log(l.s.frame(), Direction.Axis.Y));
		}
		set(c, p, l.iu, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
	}

	// ------------------------------------------------------------------ the outside stair

	private static void stairs(CitadelCanvas c, L l) {
		Plot p = l.p;
		int a = l.vf; // first row of the stair
		int vl = a + 4; // landing row of flights 1 and 3
		// --- flight 1: masonry ramp on the outer lane, steps y0..y3 towards the back
		for (int i = 0; i < 4; i++) {
			int v = a + i;
			set(c, p, l.ou, i, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
			for (int yy = 0; yy < i; yy++) {
				set(c, p, l.ou, yy, v, deepslate(p, l.ou, yy, v));
			}
			// parapet on the spare column, stepping up with the ramp
			for (int yy = 0; yy < i; yy++) {
				set(c, p, l.su, yy, v, deepslate(p, l.su, yy, v));
			}
			set(c, p, l.su, i, v, wall(Blocks.DEEPSLATE_BRICK_WALL, true, false, true, false, true));
		}
		// landing 1 at y3 (both lanes), on masonry; parapet on the east
		for (int yy = 0; yy < B1; yy++) {
			set(c, p, l.ou, yy, vl, deepslate(p, l.ou, yy, vl));
			set(c, p, l.su, yy, vl, deepslate(p, l.su, yy, vl));
		}
		set(c, p, l.ou, B1, vl, b(Blocks.POLISHED_DEEPSLATE));
		set(c, p, l.iu, B1, vl, b(Blocks.POLISHED_DEEPSLATE));
		set(c, p, l.su, B1, vl, b(Blocks.POLISHED_DEEPSLATE));
		set(c, p, l.su, B1 + 1, vl, wall(Blocks.DEEPSLATE_BRICK_WALL, true, false, true, false, true));
		// the stair tower frame at the back: a pair of cherry columns up to the last landing
		for (int lane : new int[] {l.iu, l.ou}) {
			set(c, p, lane, 0, vl + 1, b(Blocks.POLISHED_DEEPSLATE));
			for (int y = 1; y <= B3 + 1; y++) {
				set(c, p, lane, y, vl + 1, y <= B3 ? infill(l, y <= 3 ? 1 : y <= 6 ? 2 : 4, lane, y, vl + 1) : fence(l.s.fence(), false, false, false, false));
			}
		}
		for (int y = B1 + 1; y <= B3 + 1; y++) {
			if (y == B1 + 1 || y == B3 + 1) {
				continue;
			}
		}
		for (int y = 0; y <= B3; y++) {
			if (y == 0 || y == B1 || y == B2 || y == B3) {
				set(c, p, l.su, y, vl + 1, y == 0 ? b(Blocks.POLISHED_DEEPSLATE) : log(l.s.frame(), Direction.Axis.X));
			}
		}
		// rails closing landing 1 towards the back: the columns do it. Beam across the back at each landing.
		for (int y : new int[] {B1, B3}) {
			set(c, p, l.iu, y, vl + 1, log(l.s.frame(), Direction.Axis.X));
			set(c, p, l.ou, y, vl + 1, log(l.s.frame(), Direction.Axis.X));
		}

		// --- flight 2: timber stairs on the inner lane, steps y4..y6 towards the front
		for (int i = 0; i < 3; i++) {
			int v = vl - 1 - i;
			int y = B1 + 1 + i;
			set(c, p, l.iu, y, v, stair(l.s.eave(), Direction.NORTH, false));
			set(c, p, l.iu, y - 1, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, true)); // bracket
		}
		// landing 2 at y6 on the front: the terrace row v2 on both lanes
		set(c, p, l.iu, B2, a, b(l.s.planks()));
		set(c, p, l.ou, B2, a, b(l.s.planks()));
		set(c, p, l.ou, B2 - 1, a, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
		for (int yy = 1; yy < B2; yy++) {
			set(c, p, l.iu, yy, a, log(l.s.frame(), Direction.Axis.Y));
		}
		set(c, p, l.iu, 0, a, b(Blocks.POLISHED_DEEPSLATE));
		set(c, p, l.ou, B2 + 1, a - 1, fence(l.s.fence(), false, false, true, false));
		set(c, p, l.ou, B2 + 1, a, fence(l.s.fence(), true, true, false, false));
		set(c, p, l.su, B2, a, b(l.s.planks()));
		set(c, p, l.su, B2 + 1, a, fence(l.s.fence(), false, false, false, true));
		set(c, p, l.su, B2 + 1, a - 1, fence(l.s.fence(), false, false, true, false));
		set(c, p, l.su, B2, a - 1, b(l.s.planks()));
		set(c, p, l.ou, B2, a - 1, b(l.s.planks()));

		// --- flight 3: timber stairs on the outer lane, steps y7..y9 towards the back
		for (int i = 0; i < 3; i++) {
			int v = a + 1 + i;
			int y = B2 + 1 + i;
			set(c, p, l.ou, y, v, stair(l.s.eave(), Direction.SOUTH, false));
			set(c, p, l.ou, y - 1, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, true)); // bracket
			// stringer + rail on the outer edge: a stair of the same slope carries a fence
			set(c, p, l.su, y, v, stair(l.s.eave(), Direction.SOUTH, false));
			set(c, p, l.su, y + 1, v, fence(l.s.fence(), false, false, false, false));
		}
		// two cherry columns carry the flight at its ends
		for (int yy = 1; yy < B2; yy++) {
			set(c, p, l.su, yy, a, log(l.s.frame(), Direction.Axis.Y));
		}
		for (int yy = B1 + 1; yy <= B3; yy++) {
			set(c, p, l.su, yy, vl, log(l.s.frame(), Direction.Axis.Y));
		}
		// landing 3 at y9, both lanes, at the attic door
		for (int lane : new int[] {l.ou, l.iu}) {
			set(c, p, lane, B3, vl, b(l.s.planks()));
		}
		set(c, p, l.su, B3, vl, b(l.s.planks()));
		set(c, p, l.su, B3 + 1, vl, fence(l.s.fence(), true, false, true, false));
		set(c, p, l.iu, B3 + 1, vl - 1, fence(l.s.fence(), false, false, false, false));
		put(c, p, l.iu, B3 + 2, l.vd, hangingLantern(), CitadelCanvas.HANGING);
		put(c, p, l.iu, B1 + 2, l.vd, hangingLantern(), CitadelCanvas.HANGING);
		// planters under flight 2
		for (int v = a + 1; v < vl; v++) {
			set(c, p, l.iu, 0, v, b(Blocks.MOSS_BLOCK));
			set(c, p, l.iu, 1, v, p.hash(l.iu, 0, v, 3) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.FERN));
		}
	}

	// ------------------------------------------------------------------ roof

	private static int roofY(L l, int u) {
		return PLATE + 1 + Math.min(u, l.iu - u);
	}

	/** Gable roof along v: eaves at u 0 and iu, overhanging front and back. */
	private static void roof(CitadelCanvas c, L l) {
		Plot p = l.p;
		int mid = l.iu / 2;
		for (int v = 0; v < l.d; v++) {
			for (int u = 0; u <= l.iu; u++) {
				int y = roofY(l, u);
				boolean eave = u == 0 || u == l.iu;
				if (u == mid) {
					set(c, p, u, y, v, b(Blocks.DEEPSLATE_TILES));
					set(c, p, u, y + 1, v, slab(l.s.roofSlab(), false));
				} else {
					Direction up = u < mid ? Direction.EAST : Direction.WEST;
					set(c, p, u, y, v, stair(eave ? l.s.eave() : l.s.roof(), up, false));
					if (eave) {
						set(c, p, u, y - 1, v, stair(l.s.roof(), up.getOpposite(), true));
					}
				}
			}
		}
		// gables: calcite triangles at front and back with a cherry outline
		for (int v : new int[] {l.vf, l.vb}) {
			for (int u = l.u1; u <= l.u2; u++) {
				for (int y = PLATE + 1; y < roofY(l, u); y++) {
					boolean outline = y == roofY(l, u) - 1;
					BlockState st = outline ? b(l.s.planks()) : plaster(p, u, y, v);
					if (u == l.cu) {
						st = frameY(l);
					}
					set(c, p, u, y, v, st);
				}
				if (u == l.cu - 1 || u == l.cu + 1) {
					set(c, p, u, PLATE + 2, v, pane(pinkPane(), true));
				}
			}
		}
		// finial on the ridge, front and back
		int top = roofY(l, mid) + 1;
		set(c, p, mid, top + 1, 0, fence(l.s.fence(), false, false, false, false));
		set(c, p, mid, top + 2, 0, lantern());
		set(c, p, mid, top + 1, l.d - 1, fence(l.s.fence(), false, false, false, false));
		// the front overhang is carried: two cherry posts rise from the terrace rail to a beam under the roof
		for (int u : new int[] {l.u1, l.iu}) {
			for (int y = B2 + 1; y < PLATE; y++) {
				set(c, p, u, y, 0, log(l.s.frame(), Direction.Axis.Y));
			}
		}
		for (int u = l.u1; u <= l.iu; u++) {
			set(c, p, u, PLATE, 0, log(l.s.frame(), Direction.Axis.X));
			if (u > l.u1 && u < l.iu) {
				set(c, p, u, PLATE - 1, 0, stair(l.s.eave(), Direction.SOUTH, true)); // a cove under the beam
			}
		}
		// bracket under the front gable overhang
		set(c, p, mid, roofY(l, mid) - 1, 0, stair(l.s.eave(), Direction.SOUTH, true));
		set(c, p, mid, roofY(l, mid) - 1, 1, b(l.s.planks()));
	}

	/** A brick chimney on the west slope with a slab cap. */
	private static void chimney(CitadelCanvas c, L l) {
		int u = l.u1 + 1;
		int v = l.vb - 1;
		int top = roofY(l, u) + 2;
		for (int y = 10; y < top; y++) {
			set(c, l.p, u, y, v, l.p.hash(u, y, v, 3) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS));
		}
		set(c, l.p, u, top, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
	}

	// ------------------------------------------------------------------ yard

	private static void yard(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int v = l.vf + 1; v < l.vb; v++) {
			int k = p.hash(0, 0, v, 5);
			if (k == 0) {
				set(c, p, 0, 1, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			} else if (k == 1) {
				set(c, p, 0, 1, v, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
			} else if (k == 2) {
				set(c, p, 0, 1, v, b(Blocks.POTTED_FERN));
			}
		}
		set(c, p, l.u1 + 1, 1, l.d - 1, b(Blocks.COMPOSTER));
		set(c, p, l.u1 + 2, 1, l.d - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
	}
}
