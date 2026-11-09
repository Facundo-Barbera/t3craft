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
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A bridge house: two towers of different height (three storeys and two) joined at the middle floor
 * by a covered timber gallery, with a stone arch and an open passage underneath. Each tower has a
 * hip roof ending in a lantern finial; the gallery has its own small gable roof. The path through
 * the arch is left walkable at ground level (front to back is the long way round: the passage runs
 * along the frontage).
 *
 * <pre>
 *  u:  0 . . 6 | 7 .. w-8 | w-7 . . w-1       tall tower | gallery gap | low tower
 *  v:  0 eave | 1 front wall .. 5 back wall | 6 eave | 7.. back garden
 *  y:  0 plinth | 1-3 | 4 floor | 5-7 | 8 floor (low tower plate) | 9-11 | 12 plate | 13-16 hip roof
 * </pre>
 *
 * Size: 15–17 × 7–9 × 19+.
 */
public final class HouseTowerBridge implements Variant {
	private static final int TA = 1; // front wall row of both towers

	@Override
	public String id() {
		return "house_tower_bridge";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 15;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 19;
	}

	@Override
	public int maxW() {
		return 17;
	}

	@Override
	public int maxD() {
		return 9;
	}

	private record Scheme(Block log, Block trim, Block planks, Block fence, Block door, Block trap, Block roof, Block roofStair, Block roofSlab, Block eaveStair) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR,
					Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.CHERRY_STAIRS),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR,
					Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.DEEPSLATE_TILE_STAIRS),
	};

	private record L(Plot p, Scheme s, int w, int d, int ucl, int ucr, int cz) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xB41D_6E01L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int w = p.w();
		L l = new L(p, s, w, p.d(), 3, w - 4, TA + 2);
		tower(c, l, l.ucl, 3, true);
		tower(c, l, l.ucr, 2, false);
		gallery(c, l);
		arch(c, l);
		yard(c, l);
	}

	// ------------------------------------------------------------------ materials

	private static Block pinkPane() {
		return Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK);
	}

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

	private static BlockState doorBlock(L l, Direction facing, boolean lower) {
		return l.s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	private static BlockState shaped(Block block, Direction facing, StairsShape shape) {
		return stair(block, facing, false).setValue(BlockStateProperties.STAIRS_SHAPE, shape);
	}

	private static int y0(int storey) {
		return 1 + 4 * (storey - 1);
	}

	// ------------------------------------------------------------------ towers

	/** A 5×5 tower centred on column {@code cu}: walls u cu-2..cu+2, v TA..TA+4. */
	private void tower(CitadelCanvas c, L l, int cu, int storeys, boolean tall) {
		Plot p = l.p;
		int va = TA;
		int vb = TA + 4;
		int plate = y0(storeys) - 1 + 4; // the floor row above the top storey
		plate = 4 * storeys; // y4 for 1 storey, y8 for 2, y12 for 3
		// plinth
		for (int u = cu - 2; u <= cu + 2; u++) {
			for (int v = va; v <= vb; v++) {
				boolean edge = u == cu - 2 || u == cu + 2 || v == va || v == vb;
				set(c, p, u, 0, v, edge ? deepslate(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		for (int st = 1; st <= storeys; st++) {
			int y0 = y0(st);
			for (int y = y0; y <= y0 + 2; y++) {
				for (int u = cu - 2; u <= cu + 2; u++) {
					for (int v = va; v <= vb; v++) {
						boolean edge = u == cu - 2 || u == cu + 2 || v == va || v == vb;
						if (!edge) {
							continue;
						}
						set(c, p, u, y, v, wallBlock(l, cu, st, y, y0, u, v, va, vb));
					}
				}
			}
			windows(c, l, cu, st, y0, va, vb);
			// floor / plate row above this storey
			int fy = y0 + 3;
			for (int u = cu - 2; u <= cu + 2; u++) {
				for (int v = va; v <= vb; v++) {
					boolean edge = u == cu - 2 || u == cu + 2 || v == va || v == vb;
					boolean ladder = u == cu && v == vb - 1;
					if (edge) {
						set(c, p, u, fy, v, log(l.s.log(), (v == va || v == vb) ? Direction.Axis.X : Direction.Axis.Z));
					} else if (!ladder || fy == plate) {
						set(c, p, u, fy, v, b(Blocks.SPRUCE_PLANKS));
					}
				}
			}
			// [depth] string course under every storey above the first: upside-down stairs round the foot
			if (st > 1) {
				for (int u = cu - 2; u <= cu + 2; u++) {
					set(c, p, u, y0 - 1, va - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, true));
					set(c, p, u, y0 - 1, vb + 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, true));
				}
				for (int v = va; v <= vb; v++) {
					set(c, p, cu - 3, y0 - 1, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, true));
					set(c, p, cu + 3, y0 - 1, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, true));
				}
			}
			put(c, p, cu, y0 + 2, l.cz, hangingLantern(), CitadelCanvas.HANGING);
			for (int y = y0; y <= y0 + 2 && y <= plate - 1; y++) {
				if (st < storeys || true) {
					set(c, p, cu, y, vb - 1, b(Blocks.LADDER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				}
			}
		}
		hip(c, l, cu, plate);
		porch(c, l, cu);
		corners(c, l, cu, plate);
	}

	private static Direction outOf(int cu, int u, int v, int va, int vb) {
		if (v == va) {
			return Direction.NORTH;
		}
		if (v == vb) {
			return Direction.SOUTH;
		}
		return u == cu - 2 ? Direction.WEST : Direction.EAST;
	}

	private static boolean corner(int cu, int u, int v, int va, int vb) {
		return (u == cu - 2 || u == cu + 2) && (v == va || v == vb);
	}

	private static boolean isMid(L l, int cu, int u, int v, int va, int vb) {
		Direction out = outOf(cu, u, v, va, vb);
		return out.getAxis() == Direction.Axis.Z ? u == cu : v == l.cz;
	}

	private static boolean isWin(L l, int cu, int st, int y, int y0, int u, int v, int va, int vb) {
		if (corner(cu, u, v, va, vb) || !isMid(l, cu, u, v, va, vb)) {
			return false;
		}
		Direction out = outOf(cu, u, v, va, vb);
		if (out == Direction.NORTH && st == 1) {
			return false; // the front door
		}
		if (st == 2 && ((out == Direction.EAST && cu == l.ucl) || (out == Direction.WEST && cu == l.ucr))) {
			return false; // the gallery door
		}
		return st == 1 ? y == y0 + 1 : y >= y0 + 1;
	}

	private BlockState wallBlock(L l, int cu, int st, int y, int y0, int u, int v, int va, int vb) {
		Plot p = l.p;
		if (corner(cu, u, v, va, vb)) {
			return log(l.s.log(), Direction.Axis.Y);
		}
		Direction out = outOf(cu, u, v, va, vb);
		boolean mid = isMid(l, cu, u, v, va, vb);
		if (out == Direction.NORTH && mid && st == 1) {
			return y == y0 + 2 ? log(l.s.log(), Direction.Axis.X) : doorBlock(l, Direction.NORTH, y == y0);
		}
		boolean bridgeSide = st == 2 && mid && ((out == Direction.EAST && cu == l.ucl) || (out == Direction.WEST && cu == l.ucr));
		if (bridgeSide) {
			return y == y0 + 2 ? log(l.s.log(), Direction.Axis.X) : b(Blocks.AIR);
		}
		if (isWin(l, cu, st, y, y0, u, v, va, vb)) {
			return b(Blocks.AIR);
		}
		return switch (st) {
			case 1 -> deepslate(p, u, y, v);
			case 2 -> stone(p, u, y, v);
			default -> plaster(p, u, y, v);
		};
	}

	/** Glass set back behind each window opening, a sill and shutters outside. */
	private void windows(CitadelCanvas c, L l, int cu, int st, int y0, int va, int vb) {
		Plot p = l.p;
		for (int u = cu - 2; u <= cu + 2; u++) {
			for (int v = va; v <= vb; v++) {
				if (!(u == cu - 2 || u == cu + 2 || v == va || v == vb)) {
					continue;
				}
				boolean any = false;
				Direction out = outOf(cu, u, v, va, vb);
				for (int y = y0; y <= y0 + 2; y++) {
					if (isWin(l, cu, st, y, y0, u, v, va, vb)) {
						any = true;
						Direction in = out.getOpposite();
						boolean alongU = out.getAxis() == Direction.Axis.Z;
						set(c, p, u + in.getStepX(), y, v + in.getStepZ(), pane(p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, alongU));
					}
				}
				if (any) {
					set(c, p, u + out.getStepX(), y0, v + out.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), true));
					if (st > 1) {
						set(c, p, u + out.getStepX(), y0 + 1, v + out.getStepZ(), b(Blocks.POTTED_PINK_TULIP));
					}
				}
			}
		}
	}

	private void corners(CitadelCanvas c, L l, int cu, int plate) {
		// kept for symmetry with the stair house: nothing extra; the corner posts are already logs.
	}

	private void porch(CitadelCanvas c, L l, int cu) {
		Plot p = l.p;
		int vd = TA;
		set(c, p, cu, 0, vd - 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		for (int side = -1; side <= 1; side += 2) {
			set(c, p, cu + side, 0, vd - 1, b(Blocks.MOSS_BLOCK));
			set(c, p, cu + side, 1, vd - 1, side < 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			set(c, p, cu + side * 2, 0, vd - 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
			put(c, p, cu + side, 3, vd - 1, hangingLantern(), CitadelCanvas.HANGING);
		}
		// hood over the door
		set(c, p, cu, 3, vd - 1, stair(l.s.eaveStair(), Direction.SOUTH, true));
		set(c, p, cu - 1, 3, vd - 1, stair(l.s.eaveStair(), Direction.EAST, true));
		set(c, p, cu + 1, 3, vd - 1, stair(l.s.eaveStair(), Direction.WEST, true));
	}

	// ------------------------------------------------------------------ hip roof

	/** Pyramid roof, half-size 3 at the eave, one block smaller per row; corner stairs shaped. */
	private void hip(CitadelCanvas c, L l, int cu, int plate) {
		Plot p = l.p;
		int cv = l.cz;
		for (int k = 0; k <= 3; k++) {
			int half = 3 - k;
			int y = plate + 1 + k;
			for (int du = -half; du <= half; du++) {
				for (int dv = -half; dv <= half; dv++) {
					boolean ring = Math.abs(du) == half || Math.abs(dv) == half;
					int u = cu + du;
					int v = cv + dv;
					Block sb = k == 0 ? l.s.eaveStair() : l.s.roofStair();
					if (half == 0) {
						set(c, p, u, y, v, slab(l.s.roofSlab(), false));
					} else if (!ring) {
						set(c, p, u, y, v, b(l.s.roof()));
					} else {
						boolean north = dv == -half;
						boolean south = dv == half;
						boolean west = du == -half;
						boolean east = du == half;
						BlockState st;
						if (north && west) {
							st = shaped(sb, Direction.SOUTH, StairsShape.OUTER_LEFT);
						} else if (north && east) {
							st = shaped(sb, Direction.SOUTH, StairsShape.OUTER_RIGHT);
						} else if (south && west) {
							st = shaped(sb, Direction.NORTH, StairsShape.OUTER_RIGHT);
						} else if (south && east) {
							st = shaped(sb, Direction.NORTH, StairsShape.OUTER_LEFT);
						} else if (north) {
							st = stair(sb, Direction.SOUTH, false);
						} else if (south) {
							st = stair(sb, Direction.NORTH, false);
						} else if (west) {
							st = stair(sb, Direction.EAST, false);
						} else {
							st = stair(sb, Direction.WEST, false);
						}
						set(c, p, u, y, v, st);
						if (k == 0) {
							// the underside of the eave: an upside-down stair in the roof material
							Direction face = north ? Direction.NORTH : south ? Direction.SOUTH : west ? Direction.WEST : Direction.EAST;
							set(c, p, u, y - 1, v, stair(l.s.roofStair(), face, true));
						}
					}
				}
			}
		}
		int top = plate + 5;
		set(c, p, cu, top, cv, fence(l.s.fence(), false, false, false, false));
		set(c, p, cu, top + 1, cv, lantern());
	}

	// ------------------------------------------------------------------ gallery

	private void gallery(CitadelCanvas c, L l) {
		Plot p = l.p;
		int u1 = l.ucl + 2; // the wall of the tall tower (door cell)
		int u2 = l.ucr - 2; // the wall of the low tower
		int cz = l.cz;
		for (int u = u1 + 1; u < u2; u++) {
			// floor at y4: logs along the edges, planks in the middle
			for (int v = cz - 1; v <= cz + 1; v++) {
				set(c, p, u, 4, v, v == cz ? b(l.s.planks()) : log(l.s.log(), Direction.Axis.X));
			}
			for (int v : new int[] {cz - 1, cz + 1}) {
				boolean post = (u - u1) % 2 == 1 || u == u1 + 1 || u == u2 - 1;
				Direction out = v < cz ? Direction.NORTH : Direction.SOUTH;
				for (int y = 5; y <= 6; y++) {
					if (post) {
						set(c, p, u, y, v, log(l.s.trim(), Direction.Axis.Y));
					} else if (y == 5) {
						set(c, p, u, y, v, fence(l.s.fence(), false, true, false, true));
					} else {
						// [depth] a lattice: panes set back behind the rail
						set(c, p, u, y, v, pane(p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, true));
					}
				}
				set(c, p, u, 7, v, log(l.s.log(), Direction.Axis.X));
				// brackets under the floor beam
				set(c, p, u, 3, v, stair(Blocks.DARK_OAK_STAIRS, out.getOpposite(), true));
				// flower boxes on the rail
				if (!post && p.hash(u, 5, v, 2) == 0) {
					put(c, p, u, 6, v + out.getStepZ(), b(Blocks.POTTED_PINK_TULIP), CitadelCanvas.DECOR);
				}
			}
			// roof along u: eave row at cz±2, then cz±1, ridge at cz
			for (int dv = -2; dv <= 2; dv++) {
				int v = cz + dv;
				int y = 8 + (2 - Math.abs(dv));
				if (dv == 0) {
					set(c, p, u, y, v, b(l.s.roof()));
					set(c, p, u, y + 1, v, slab(l.s.roofSlab(), false));
				} else {
					Direction up = dv < 0 ? Direction.SOUTH : Direction.NORTH;
					set(c, p, u, y, v, stair(Math.abs(dv) == 2 ? l.s.eaveStair() : l.s.roofStair(), up, false));
				}
			}
			// ceiling and lamp
			set(c, p, u, 7, cz, b(l.s.planks()));
		}
		for (int u = u1 + 1; u < u2; u += 3) {
			put(c, p, u, 6, cz, hangingLantern(), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ arch

	/** A stone arch under the gallery: corbels either side of a slab keystone, the passage open below. */
	private void arch(CitadelCanvas c, L l) {
		Plot p = l.p;
		int u1 = l.ucl + 3;
		int u2 = l.ucr - 3;
		int cz = l.cz;
		for (int v = cz - 1; v <= cz + 1; v++) {
			for (int u = u1; u <= u2; u++) {
				set(c, p, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
			}
			set(c, p, u1, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST, true));
			set(c, p, u2, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, true));
			for (int u = u1 + 1; u < u2; u++) {
				set(c, p, u, 3, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
			}
		}
		// jambs: a deepslate wall course on each pier, the passage floor edged
		put(c, p, (u1 + u2) / 2, 2, cz, hangingLantern(), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ yard

	private void yard(CitadelCanvas c, L l) {
		Plot p = l.p;
		for (int u = 0; u < l.w; u++) {
			if (p.hash(u, 0, 1, 4) == 0 && u > l.ucl + 2 && u < l.ucr - 2) {
				continue;
			}
		}
		set(c, p, 1, 1, l.d - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		set(c, p, 2, 1, l.d - 1, b(Blocks.COMPOSTER));
		set(c, p, l.w - 3, 1, l.d - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		for (int side = -1; side <= 1; side += 2) {
			int cu = side < 0 ? l.ucl : l.ucr;
			set(c, p, cu + side * 3, 0, TA + 2, b(Blocks.MOSS_BLOCK));
			set(c, p, cu + side * 3, 1, TA + 2, b(Blocks.FLOWERING_AZALEA));
		}
		set(c, p, (l.ucl + l.ucr) / 2, 1, TA - 1, trapdoor(l.s.trap(), Direction.SOUTH, true, false));
	}
}
