package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.up;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.StoneKit.Wall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A weathered stone house in an L: a front wing with its gable to the street (bay window, round
 * gable light) and a back block with a porch in the inner corner. The walls are one long
 * gradient: mossy cobblestone and mossy bricks at the foot, cracked bricks in the middle, clean
 * stone above, polished andesite and smooth stone under the roof. Cherry roofs, vines on the
 * walls, a hedge in front.
 *
 * <pre>
 *  local layout (front = v 0)
 *  v1    hedge . . . . . . .
 *  v2    ▒bay▒  . porch . .       A: u 1..wa, full depth, ridge along v
 *  ...   ▒ A ▒  ▒ porch ▒         M: u wa+1..w-2, 6 deep at the back, ridge along u
 *  d-2   ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
 * </pre>
 *
 * Size: 11–15 × 11–14 × 15+.
 */
public final class HouseStoneWeathered implements Variant {
	private static final int PLATE = 7;

	@Override
	public String id() {
		return "house_stone_weathered";
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
		return 11;
	}

	@Override
	public int minH() {
		return 15;
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
		int w = p.w();
		int d = p.d();
		int wa = w >= 14 ? 7 : 6; // front wing: u 1..wa
		int vm = d - 7; // back block: front wall at v = vm
		Wall aFront = new Wall(c, p, 2, Direction.NORTH);
		Wall aLeft = new Wall(c, p, 1, Direction.WEST);
		Wall aRight = new Wall(c, p, wa, Direction.EAST);
		Wall mFront = new Wall(c, p, vm, Direction.NORTH);
		Wall mRight = new Wall(c, p, w - 2, Direction.EAST);
		Wall back = new Wall(c, p, d - 2, Direction.SOUTH);

		floors(c, p, wa, vm, w, d);
		wallRun(aFront, 1, wa, true, true);
		wallRun(aLeft, 2, d - 2, true, true);
		wallRun(aRight, 2, vm, true, false);
		wallRun(mFront, wa, w - 2, false, true);
		wallRun(mRight, vm, d - 2, true, true);
		wallRun(back, 1, w - 2, true, true);
		vines(aFront, 1, wa, 3);
		vines(aLeft, 3, d - 3, 5);
		vines(aRight, 3, vm - 1, 7);
		vines(mRight, vm + 1, d - 3, 11);
		vines(back, 2, w - 3, 13);

		roofM(c, p, wa, vm, w, d);
		roofA(c, p, wa, vm, d);
		valley(c, p, wa, vm, w, d);
		gables(c, p, wa, vm, w, d);
		chimney(c, p, w, vm);
		bay(c, p, wa);
		windows(aFront, aLeft, aRight, mRight, back, wa, vm, w, d);
		porch(c, p, wa, vm, w);
		yard(c, p, wa, vm, w, d);
		eaves(c, p, wa, vm, w, d);
	}

	/**
	 * [depth] every long eave stands on beam tails: a dark oak log pokes out of the wall under the eave every
	 * second cell, an upside-down stair hugging the wall under it. Cells taken by shutters or the porch are skipped.
	 */
	private static void eaves(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		for (int v = 3; v <= d - 3; v += 2) {
			EaveKit.corbel(c, p, 0, PLATE - 1, v, Direction.EAST, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
		}
		for (int v = 3; v <= vm - 2; v += 2) {
			EaveKit.corbel(c, p, wa + 1, PLATE - 1, v, Direction.WEST, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
		}
		for (int u = wa + 2; u <= w - 3; u += 2) {
			EaveKit.corbel(c, p, u, PLATE - 1, d - 1, Direction.NORTH, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
			EaveKit.corbel(c, p, u, PLATE - 1, vm - 1, Direction.SOUTH, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
		}
	}

	// ------------------------------------------------------------------ walls

	private static void floors(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		for (int u = 2; u <= w - 3; u++) {
			for (int v = 3; v <= d - 3; v++) {
				boolean inA = u <= wa - 1;
				boolean inM = u >= wa && v >= vm + 1;
				if (inA || inM) {
					p.set(c, u, 0, v, b(Blocks.SPRUCE_PLANKS));
					p.set(c, u, 4, v, b(Blocks.DARK_OAK_PLANKS));
				}
			}
		}
	}

	/** [palette] one gradient in horizontal bands: mossy foot → cracked → clean → polished, little noise inside a band. */
	private static BlockState mat(Plot p, int u, int y, int v) {
		int h = p.hash(u, y, v, 10);
		return switch (y) {
			case 0 -> h < 5 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> h < 6 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS);
			case 2 -> h < 5 ? b(Blocks.CRACKED_STONE_BRICKS) : h < 6 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			case 3 -> h < 2 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			case 4 -> b(Blocks.POLISHED_ANDESITE); // belt course
			case 5 -> h < 1 ? b(Blocks.ANDESITE) : b(Blocks.STONE_BRICKS);
			case 6 -> h < 6 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.SMOOTH_STONE);
			default -> b(Blocks.CALCITE); // cornice line under the eaves
		};
	}

	private static void wallRun(Wall wl, int t0, int t1, boolean q0, boolean q1) {
		for (int t = t0; t <= t1; t++) {
			for (int y = 0; y <= PLATE; y++) {
				wl.set(t, y, 0, mat(wl.p, wl.u(t, 0), y, wl.v(t, 0)));
			}
			// [depth] foot ledge in mossy stone, belt course, plate ledge
			wl.set(t, 0, 1, wl.p.hash(wl.u(t, 1), 0, wl.v(t, 1), 3) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE));
			wl.set(t, 4, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, true));
		}
		// quoins at both ends: alternate polished andesite and stone bricks, one block proud
		for (int y = 1; y <= PLATE - 1; y++) {
			BlockState q = y % 2 == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
			if (q0) {
				wl.set(t0, y, 1, y == 1 ? b(Blocks.MOSSY_COBBLESTONE) : q);
			}
			if (q1) {
				wl.set(t1, y, 1, y == 1 ? b(Blocks.MOSSY_COBBLESTONE) : q);
			}
		}
	}

	/** [finish] vines creeping up the walls, longer at the foot. */
	private static void vines(Wall wl, int t0, int t1, int salt) {
		Direction toWall = wl.out.getOpposite();
		for (int t = t0; t <= t1; t++) {
			if (wl.p.hash(t, salt, wl.a, 4) != 0) {
				continue;
			}
			int len = 2 + wl.p.hash(t, salt, 1, 4);
			for (int y = 1; y <= 1 + len; y++) {
				wl.set(t, y, 1, vine(toWall));
			}
		}
	}

	private static BlockState vine(Direction face) {
		var v = Blocks.VINE.defaultBlockState();
		return switch (face) {
			case NORTH -> v.setValue(BlockStateProperties.NORTH, true);
			case EAST -> v.setValue(BlockStateProperties.EAST, true);
			case SOUTH -> v.setValue(BlockStateProperties.SOUTH, true);
			default -> v.setValue(BlockStateProperties.WEST, true);
		};
	}

	// ------------------------------------------------------------------ windows

	private static void window(Wall wl, int a, int y, boolean sill) {
		wl.set(a, y, 0, wl.pane());
		wl.set(a, y + 1, 0, wl.pane());
		if (sill) {
			wl.set(a, y - 1, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, false));
		}
		wl.set(a, y + 2, 1, wl.stairIn(Blocks.DEEPSLATE_BRICK_STAIRS, true)); // drip cap
		wl.set(a - 1, y, 1, wl.shutter(Blocks.DARK_OAK_TRAPDOOR));
		wl.set(a - 1, y + 1, 1, wl.shutter(Blocks.DARK_OAK_TRAPDOOR));
		wl.set(a + 1, y, 1, wl.shutter(Blocks.DARK_OAK_TRAPDOOR));
		wl.set(a + 1, y + 1, 1, wl.shutter(Blocks.DARK_OAK_TRAPDOOR));
	}

	private static void windows(Wall aFront, Wall aLeft, Wall aRight, Wall mRight, Wall back, int wa, int vm, int w, int d) {
		// front wing: upper window above the bay
		window(aFront, (1 + wa) / 2, 5, false);
		// left wall
		for (int t = 4; t <= d - 4; t += 3) {
			window(aLeft, t, 2, true);
			window(aLeft, t, 5, false);
		}
		// right wall of the wing, in the porch yard
		if (vm - 2 >= 4) {
			window(aRight, 4 + (vm - 4) / 2 - 0, 5, false);
		}
		// back wall
		for (int t = 3; t <= w - 4; t += 3) {
			window(back, t, 2, true);
			window(back, t, 5, false);
		}
		// back block right wall: one window each floor, off the middle
		int mid = (vm + d - 2) / 2;
		for (int t : new int[] {vm + 2, d - 3}) {
			if (Math.abs(t - mid) >= 1 && t != vm + 1) {
				window(mRight, t, 2, true);
			}
		}
	}

	private static void bay(CitadelCanvas c, Plot p, int wa) {
		int a = (wa - 1) / 2; // 3 wide: a .. a+2, one block in front of the wing front wall (v 2)
		for (int u = a; u <= a + 2; u++) {
			p.set(c, u, 0, 1, b(Blocks.MOSSY_COBBLESTONE));
			p.set(c, u, 1, 1, b(Blocks.MOSSY_STONE_BRICKS));
			// the wall behind opens into the bay
			for (int y = 2; y <= 3; y++) {
				p.set(c, u, y, 2, AIR);
			}
			p.set(c, u, 4, 1, up(stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH)));
		}
		for (int y = 2; y <= 3; y++) {
			p.set(c, a, y, 1, y == 3 ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
			p.set(c, a + 2, y, 1, y == 3 ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
			p.set(c, a + 1, y, 1, StoneKit.pane(true));
			// side lights
			p.set(c, a, y, 2, StoneKit.pane(false));
			p.set(c, a + 2, y, 2, StoneKit.pane(false));
		}
		// a window box on the sill
		p.set(c, a + 1, 1, 0, b(Blocks.MOSS_BLOCK));
		p.set(c, a + 1, 2, 0, b(Blocks.FLOWERING_AZALEA));
	}

	// ------------------------------------------------------------------ roofs

	private static int lvA(int u, int wa) {
		return Math.min(u, wa + 1 - u);
	}

	private static int lvM(int v, int vm, int d) {
		return Math.min(v - (vm - 1), d - 1 - v);
	}

	/** [finish] front wing roof: ridge along v, verge overhang front and back, dark oak edges, slab ridge. */
	private static void roofA(CitadelCanvas c, Plot p, int wa, int vm, int d) {
		for (int v = 1; v <= d - 1; v++) {
			for (int u = 0; u <= wa + 1; u++) {
				if (u == wa + 1 && v >= vm - 1) {
					continue; // the back block's roof takes this column: a clean valley
				}
				int l = lvA(u, wa);
				int y = PLATE + l;
				boolean eave = u == 0 || u == wa + 1 || v == 1 || v == d - 1; // [depth] dark oak barge boards on the rakes
				boolean leftHalf = u < wa + 1 - u;
				boolean middle = u == wa + 1 - u;
				if (middle) {
					p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
				} else {
					p.set(c, u, y, v, stairs(eave ? Blocks.DARK_OAK_STAIRS : Blocks.CHERRY_STAIRS, leftHalf ? Direction.EAST : Direction.WEST));
				}
			}
			int top = PLATE + lvA((wa + 1) / 2, wa);
			if (wa % 2 == 1) {
				p.set(c, (wa + 1) / 2, top + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			} else {
				p.set(c, wa / 2, top + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				p.set(c, wa / 2 + 1, top + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
	}

	/**
	 * [finish] a clean roof junction: the back block's roof is as high as the wing's ridge, so it runs into the
	 * wing's slope. Wherever the back block's slope is higher than the wing's slope (right half of the wing),
	 * its stairs take over and the gap under them is filled, which leaves one straight valley and no floating ridge end.
	 */
	private static void valley(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		int rows = d - vm + 1;
		int top = PLATE + lvM(vm - 1 + (rows - 1) / 2, vm, d);
		for (int v = vm; v <= d - 2; v++) {
			int lm = lvM(v, vm, d);
			boolean front = v - (vm - 1) < d - 1 - v;
			boolean middle = v - (vm - 1) == d - 1 - v;
			for (int u = wa; u > 0 && 2 * u > wa + 1; u--) {
				int la = lvA(u, wa);
				if (lm <= la) {
					continue;
				}
				for (int y = PLATE + la; y < PLATE + lm; y++) {
					p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
				}
				p.set(c, u, PLATE + lm, v, middle ? b(Blocks.CHERRY_PLANKS) : stairs(Blocks.CHERRY_STAIRS, front ? Direction.SOUTH : Direction.NORTH));
				if (PLATE + lm == top) {
					p.set(c, u, top + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		}
	}

	/** Back block roof, ridge along u, from the wing to the right overhang. */
	private static void roofM(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		int rows = d - vm + 1; // v vm-1 .. d-1
		for (int v = vm - 1; v <= d - 1; v++) {
			int l = lvM(v, vm, d);
			int y = PLATE + l;
			boolean eave = v == vm - 1 || v == d - 1;
			boolean front = v - (vm - 1) < d - 1 - v;
			boolean middle = v - (vm - 1) == d - 1 - v;
			for (int u = wa + 1; u <= w - 1; u++) {
				if (middle) {
					p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
				} else {
					p.set(c, u, y, v, stairs(eave || u == w - 1 ? Blocks.DARK_OAK_STAIRS : Blocks.CHERRY_STAIRS, front ? Direction.SOUTH : Direction.NORTH));
				}
			}
		}
		int top = PLATE + lvM(vm - 1 + (rows - 1) / 2, vm, d);
		for (int u = wa + 1; u <= w - 1; u++) {
			if (rows % 2 == 1) {
				p.set(c, u, top + 1, vm - 1 + rows / 2, slab(Blocks.DEEPSLATE_TILE_SLAB));
			} else {
				p.set(c, u, top + 1, vm - 1 + rows / 2 - 1, slab(Blocks.DEEPSLATE_TILE_SLAB));
				p.set(c, u, top + 1, vm - 1 + rows / 2, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
	}

	/** [depth] gable triangles in clean polished stone, with tinted lights. */
	private static void gables(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		// wing: front (v 2) and back (v d-2)
		for (int v : new int[] {2, d - 2}) {
			for (int u = 1; u <= wa; u++) {
				for (int y = PLATE + 1; y <= PLATE + lvA(u, wa) - 1; y++) {
					// [depth] a dark oak tie beam under the gable, polished stone above
					p.set(c, u, y, v, y == PLATE + 1 ? StoneKit.log(Blocks.DARK_OAK_LOG, Direction.Axis.X)
							: p.hash(u, y, v, 3) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS));
				}
			}
			int cu = (1 + wa) / 2;
			p.set(c, cu, PLATE + 2, v, b(Blocks.TINTED_GLASS));
			if (wa % 2 == 0) {
				p.set(c, cu + 1, PLATE + 2, v, b(Blocks.TINTED_GLASS));
			}
		}
		// back block: right end (u w-2)
		for (int v = vm; v <= d - 2; v++) {
			for (int y = PLATE + 1; y <= PLATE + lvM(v, vm, d) - 1; y++) {
				p.set(c, w - 2, y, v, y == PLATE + 1 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS));
			}
		}
		int mv = (vm + d - 2) / 2;
		p.set(c, w - 2, PLATE + 2, mv, b(Blocks.TINTED_GLASS));
		p.set(c, w - 2, PLATE + 2, mv + 1, b(Blocks.TINTED_GLASS));
	}

	private static void chimney(CitadelCanvas c, Plot p, int w, int vm) {
		int u = w - 4;
		int v = vm + 2;
		for (int y = PLATE + 2; y <= PLATE + 6; y++) {
			p.set(c, u, y, v, b(Blocks.BRICKS));
		}
		p.set(c, u, PLATE + 7, v, slab(Blocks.BRICK_SLAB));
	}

	// ------------------------------------------------------------------ porch, yard

	/**
	 * [depth] a porch in the inner corner: two stripped cherry posts on a beam under a slab roof
	 * with a stair edge, a recessed door, lanterns, a step.
	 */
	private static void porch(CitadelCanvas c, Plot p, int wa, int vm, int w) {
		int ua = wa + 1;
		int ub = w - 2;
		int cu = (ua + ub) / 2;
		int vo = vm - 2; // outer edge of the porch
		// flagstones
		for (int u = ua; u <= ub; u++) {
			for (int v = vo; v <= vm - 1; v++) {
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			}
		}
		// door
		p.set(c, cu, 1, vm, StoneKit.door(Blocks.DARK_OAK_DOOR, Direction.NORTH, true));
		p.set(c, cu, 2, vm, StoneKit.door(Blocks.DARK_OAK_DOOR, Direction.NORTH, false));
		p.set(c, cu, 3, vm, StoneKit.log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X)); // lintel
		// posts and beam
		for (int u : new int[] {ua, ub}) {
			for (int y = 1; y <= 2; y++) {
				p.set(c, u, y, vo, StoneKit.log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			}
		}
		for (int u = ua; u <= ub; u++) {
			p.set(c, u, 3, vo, StoneKit.log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
			p.set(c, u, 4, vo, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
			p.set(c, u, 4, vo + 1, slab(Blocks.CHERRY_SLAB));
		}
		p.put(c, ua + 1, 2, vo, StoneKit.lantern(true), CitadelCanvas.HANGING);
		p.put(c, ub - 1, 2, vo, StoneKit.lantern(true), CitadelCanvas.HANGING);
		// step and planters
		if (vo - 1 >= 0) {
			p.set(c, cu, 0, vo - 1, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
		}
		p.set(c, ua + 1, 1, vm - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
	}

	private static void yard(CitadelCanvas c, Plot p, int wa, int vm, int w, int d) {
		// hedge along the front of the porch yard, with a gap for the path
		int cu = ((wa + 1) + (w - 2)) / 2;
		for (int u = wa + 2; u <= w - 1; u++) {
			if (Math.abs(u - cu) <= 0) {
				continue;
			}
			p.set(c, u, 0, 1, b(Blocks.MOSS_BLOCK));
			p.set(c, u, 1, 1, p.hash(u, 1, 1, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
		}
		// a bench against the wing wall and flower beds
		if (vm - 3 >= 3) {
			p.set(c, wa + 1, 0, 3, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
			p.set(c, wa + 1, 0, 4, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
		}
		for (int u = wa + 3; vm >= 6 && u <= w - 2; u++) {
			if (Math.abs(u - cu) <= 1) {
				continue;
			}
			int k = p.hash(u, 1, 2, 4);
			p.set(c, u, 0, 2, b(Blocks.MOSS_BLOCK));
			p.set(c, u, 1, 2, k == 0 ? b(Blocks.FLOWERING_AZALEA) : k == 1 ? b(Blocks.AZALEA) : k == 2 ? b(Blocks.FERN) : b(Blocks.PINK_PETALS));
		}
		// back strip: barrels and a woodpile
		int bk = d - 1;
		p.set(c, 3, 0, bk, StoneKit.barrel());
		p.set(c, 4, 0, bk, StoneKit.barrel());
		for (int u = w - 6; u <= w - 3; u++) {
			p.set(c, u, 0, bk, StoneKit.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
		}
		// left strip: ferns on moss
		for (int v = 4; v <= d - 4; v += 3) {
			p.set(c, 0, 0, v, b(Blocks.MOSS_BLOCK));
			p.set(c, 0, 1, v, b(Blocks.FERN));
		}
	}
}
