package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.deep;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.in;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.light;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.mid;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.stUp;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.topSlab;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.up;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.StoneKit.Wall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stone hall-house with a round tower on its front-left corner: a deepslate foot, stone-brick body
 * that gets lighter with height, belt course and cornice, a slate gable roof with a crow-stepped
 * right gable and an outside chimney, and a cylindrical tower that stands taller than everything
 * else, with a corbelled crown and a conical slate roof ending in a lightning-rod finial.
 *
 * <pre>
 *  local layout (front = v 0, looking down)
 *  v0   . . . . . steps . . . . .
 *  v1   ◖◖◖◖◖◖◖                       tower: centre (3,3), slender 5-wide shaft, crown 7 wide
 *  v3   ◖◖◖◖◖◖◖■■■■■■■■■■■  front wall (door under a hood)
 *  ...  ◖◖◖◖◖◖◖■  hall   ■▐ chimney on the right gable
 *  d-2  . . . .■■■■■■■■■■■  back wall
 * </pre>
 *
 * Size: 13–16 × 12–15 × 18+ (the finial reaches y 17).
 */
public final class HouseStoneTower implements Variant {
	private static final int PLATE = 7;
	private static final int TC = 3; // tower centre (u and v)
	private static final int R_OUT = 5; // shaft: a slender 5x5 with the corners cut (squared radius 2.2)
	private static final int R_IN = 1; // hollow inside
	private static final int R_CROWN = 10; // corbel/eave: squared radius 3.2
	private static final int HALL_U0 = 6; // the hall starts next to the tower, not inside it

	@Override
	public String id() {
		return "house_stone_tower";
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
		return 12;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 16;
	}

	@Override
	public int maxD() {
		return 15;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int u0 = HALL_U0;
		int u1 = w - 2;
		int v0 = 3;
		int v1 = d - 2;
		Wall front = new Wall(c, p, v0, Direction.NORTH);
		Wall back = new Wall(c, p, v1, Direction.SOUTH);
		Wall left = new Wall(c, p, u0, Direction.WEST);
		Wall right = new Wall(c, p, u1, Direction.EAST);

		floors(c, p, u0, u1, v0, v1);
		wallRun(front, u0, u1);
		wallRun(back, u0, u1);
		wallRun(left, v0, v1);
		wallRun(right, v0, v1);
		windows(front, back, left, right, u1, v1);
		roof(c, p, u0, u1, v1);
		gable(right, v1, p);
		gable(left, v1, p);
		chimney(c, p, u1, v0, v1);
		porch(front, c, p, u1);
		tower(c, p);
		yard(c, p, u1, v1);
	}

	// ------------------------------------------------------------------ floors and walls

	private static void floors(CitadelCanvas c, Plot p, int u0, int u1, int v0, int v1) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
				if (!edge) {
					p.set(c, u, 0, v, b(Blocks.SPRUCE_PLANKS));
					p.set(c, u, 4, v, b(Blocks.DARK_OAK_PLANKS)); // the floor between the storeys
				}
			}
		}
	}

	/** [palette] dark → light with height; the bands bleed into each other by a per-cell hash. */
	private static BlockState wallMat(Plot p, int u, int y, int v) {
		return switch (y) {
			case 0, 1 -> deep(p, u, y, v);
			case 2 -> p.hash(u, y, v, 5) < 2 ? deep(p, u, y, v) : mid(p, u, y, v);
			case 3 -> mid(p, u, y, v);
			case 4 -> b(Blocks.CALCITE); // belt course
			case 5 -> p.hash(u, y, v, 5) < 2 ? mid(p, u, y, v) : light(p, u, y, v);
			case 6 -> light(p, u, y, v);
			default -> b(Blocks.CALCITE); // top plate under the cornice
		};
	}

	/**
	 * [depth] each wall: dark foot ledge sticking out at y0, a belt course of inverted stairs at y4,
	 * the plate course at y7.
	 */
	private static void wallRun(Wall wl, int t0, int t1) {
		for (int t = t0; t <= t1; t++) {
			for (int y = 0; y <= PLATE; y++) {
				wl.set(t, y, 0, wallMat(wl.p, wl.u(t, 0), y, wl.v(t, 0)));
			}
			wl.set(t, 0, 1, deep(wl.p, wl.u(t, 1), 0, wl.v(t, 1))); // foot ledge
			wl.set(t, 4, 1, wl.stairIn(Blocks.POLISHED_ANDESITE_STAIRS, true)); // belt course
		}
	}

	// ------------------------------------------------------------------ windows

	/**
	 * A 1×2 window: panes at the plane, shutters on both sides (open spruce trapdoors flat on the
	 * wall), a stone sill underneath on the ground floor.
	 */
	private static void window(Wall wl, int t, int y, boolean sill) {
		wl.set(t, y, 0, wl.pane());
		wl.set(t, y + 1, 0, wl.pane());
		if (sill) {
			wl.set(t, y - 1, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, false));
		}
		for (int side : new int[] {-1, 1}) {
			wl.set(t + side, y, 1, wl.shutter(Blocks.SPRUCE_TRAPDOOR));
			wl.set(t + side, y + 1, 1, wl.shutter(Blocks.SPRUCE_TRAPDOOR));
		}
	}

	private static boolean free(Wall wl, int t) {
		// not inside the tower (its disc, including the crown)
		return !in(wl.u(t, 0) - TC, wl.v(t, 0) - TC, R_CROWN) && !in(wl.u(t, 1) - TC, wl.v(t, 1) - TC, R_CROWN);
	}

	private static void windows(Wall front, Wall back, Wall left, Wall right, int u1, int v1) {
		int cu = (HALL_U0 + u1) / 2;
		// front: windows left and right of the porch, both floors; a taller one over the door
		for (int t : new int[] {cu - 3, cu + 3}) {
			if (t - 1 >= HALL_U0 && t + 1 <= u1) {
				window(front, t, 2, true);
				window(front, t, 5, false);
			}
		}
		window(front, cu, 5, false);
		// back: a window every 4
		for (int t = HALL_U0 + 2; t <= u1 - 2; t += 4) {
			window(back, t, 2, true);
			window(back, t, 5, false);
		}
		// right: around the chimney (middle)
		int mid = (3 + v1) / 2;
		for (int t = 5; t <= v1 - 2; t += 4) {
			if (Math.abs(t - mid) >= 3) {
				window(right, t, 2, true);
				window(right, t, 5, false);
			}
		}
	}

	// ------------------------------------------------------------------ roof

	/** Rows of the roof: v 2..d-1, eaves on the first and the last. */
	private static int rows(int d) {
		return d - 2;
	}

	/** Distance in rows from the nearest eave (0 = the eave row itself). */
	private static int rr(int v, int d) {
		int r = v - 2;
		return Math.min(r, rows(d) - 1 - r);
	}

	private static int maxRr(int d) {
		return (rows(d) - 1) / 2;
	}

	/** Cell y of the roof slab/stair at row distance rr >= 1 (a half block of rise per row). */
	private static int ys(int rr) {
		return PLATE + 1 + (rr - 1) / 2;
	}

	/** y of the highest block of the ridge (the cap slab sits one above). */
	private static int ridgeY(int d) {
		return ys(maxRr(d));
	}

	/** Top cell of the roof over wall row v (gable parapet reaches it). */
	private static int roofY(int v, int d) {
		int r = rr(v, d);
		return r == 0 ? PLATE : ys(r);
	}

	/**
	 * [finish] shallow gable roof (a slab ladder: half a block of rise per row), ridge along u:
	 * eaves overhang front and back (v 2 and d-1) in dark oak stairs with dentils under them, slate
	 * slabs with a few lighter courses, a full-block ridge with a slab cap. The two ends are bare:
	 * they carry the gables.
	 */
	private static void roof(CitadelCanvas c, Plot p, int u0, int u1, int v1) {
		int d = p.d();
		int rows = rows(d);
		for (int v = 2; v <= d - 1; v++) {
			int r = rr(v, d);
			boolean frontSlope = v - 2 < rows - 1 - (v - 2);
			Direction high = frontSlope ? Direction.SOUTH : Direction.NORTH;
			for (int u = u0; u <= u1; u++) {
				if (r == 0) {
					p.set(c, u, PLATE, v, stairs(Blocks.DARK_OAK_STAIRS, high));
				} else if (r == maxRr(d)) {
					p.set(c, u, ys(r), v, b(Blocks.DEEPSLATE_TILES));
					p.set(c, u, ys(r) + 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
				} else {
					boolean lowSlab = (r - 1) % 2 == 0;
					var blk = p.hash(u, r, v, 6) == 0 ? Blocks.DEEPSLATE_BRICK_SLAB : Blocks.DEEPSLATE_TILE_SLAB;
					p.set(c, u, ys(r), v, lowSlab ? slab(blk) : topSlab(blk));
				}
			}
		}
		// [depth] the front and back eaves stand on beam tails with a stair corbel under each, every second cell
		for (int u = u0; u <= u1; u += 2) {
			EaveKit.corbel(c, p, u, PLATE - 1, 2, Direction.SOUTH, Blocks.DARK_OAK_LOG, Blocks.DEEPSLATE_BRICK_STAIRS);
			EaveKit.corbel(c, p, u, PLATE - 1, d - 1, Direction.NORTH, Blocks.DARK_OAK_LOG, Blocks.DEEPSLATE_BRICK_STAIRS);
		}
	}

	/**
	 * [depth] a stone gable that rises above the roof: the wall carries on up to the slope and a
	 * crow-step (one extra block on every second row, capped by a slab) turns the slope into stairs.
	 */
	private static void gable(Wall wl, int v1, Plot p) {
		int d = p.d();
		for (int t = 3; t <= v1; t++) {
			if (in(wl.u(t, 0) - TC, wl.v(t, 0) - TC, R_OUT)) {
				continue; // the tower owns these cells
			}
			int top = roofY(t, d);
			for (int y = PLATE + 1; y <= top; y++) {
				wl.set(t, y, 0, y == PLATE + 1 ? b(Blocks.CALCITE) : light(p, wl.u(t, 0), y, wl.v(t, 0)));
			}
			if (rr(t, d) == maxRr(d)) {
				wl.set(t, top + 1, 0, slab(Blocks.STONE_BRICK_SLAB));
			} else if ((t % 2) == 1) {
				wl.set(t, top + 1, 0, b(Blocks.STONE_BRICKS));
				wl.set(t, top + 2, 0, slab(Blocks.STONE_BRICK_SLAB));
			} else {
				wl.set(t, top + 1, 0, slab(Blocks.STONE_BRICK_SLAB));
			}
			// the plate ledge sticks out under the gable
			wl.set(t, PLATE, 1, wl.stairIn(Blocks.POLISHED_ANDESITE_STAIRS, true));
		}
	}

	/**
	 * [finish] a chimney breast built into the right gable: two wide, it leaves the wall one block proud with
	 * a stair shoulder under the plate, then carries on flush in the gable plane through the roof and stands
	 * well above the ridge in a slim stack with a stone cap.
	 */
	private static void chimney(CitadelCanvas c, Plot p, int u1, int v0, int v1) {
		int cv = (v0 + v1) / 2;
		int cu = u1 + 1;
		int top = Math.min(ridgeY(p.d()) + 4, p.h() - 2);
		for (int y = 0; y <= PLATE - 2; y++) {
			for (int v = cv; v <= cv + 1; v++) {
				BlockState s = y <= 2 ? deep(p, cu, y, v) : y <= 4 ? mid(p, cu, y, v) : light(p, cu, y, v);
				p.set(c, cu, y, v, s);
			}
		}
		// shoulders: the breast steps back into the wall under the plate
		for (int v = cv; v <= cv + 1; v++) {
			p.set(c, cu, PLATE - 1, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
		}
		p.set(c, cu, PLATE - 1, cv - 1, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
		p.set(c, cu, PLATE - 1, cv + 2, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
		// the stack, flush in the gable plane
		for (int y = PLATE; y <= top; y++) {
			for (int v = cv; v <= cv + 1; v++) {
				p.set(c, u1, y, v, y == PLATE ? b(Blocks.CALCITE) : y >= top - 1 ? b(Blocks.DEEPSLATE_BRICKS) : light(p, u1, y, v));
			}
		}
		for (int v = cv; v <= cv + 1; v++) {
			p.set(c, u1, top + 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
		}
	}

	// ------------------------------------------------------------------ porch

	/**
	 * [depth] a recessed door between two pilasters, a hood of slate slabs on top, a hanging lantern
	 * on each side, planters on the foot ledge and a step.
	 */
	private static void porch(Wall f, CitadelCanvas c, Plot p, int u1) {
		int cu = (HALL_U0 + u1) / 2;
		// opening
		f.set(cu, 1, 0, f.door(Blocks.DARK_OAK_DOOR, true));
		f.set(cu, 2, 0, f.door(Blocks.DARK_OAK_DOOR, false));
		f.set(cu, 3, 0, b(Blocks.CHISELED_STONE_BRICKS)); // keystone
		// pilasters
		for (int side : new int[] {-1, 1}) {
			int t = cu + side;
			for (int y = 0; y <= 3; y++) {
				f.set(t, y, 1, y == 3 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
			}
			f.set(t, 3, 0, stUp(Blocks.DEEPSLATE_BRICK_STAIRS, f.out.getOpposite())); // [depth] arch springers
			// hood, lantern, planter
			f.set(t, 4, 2, topSlab(Blocks.DEEPSLATE_TILE_SLAB));
			f.put(t, 3, 2, StoneKit.lantern(true), CitadelCanvas.HANGING);
			f.set(t + side, 4, 1, topSlab(Blocks.DEEPSLATE_TILE_SLAB));
			f.set(t + side, 4, 2, AIR);
			f.set(t + side, 1, 1, p.hash(t, 1, 1, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
		f.set(cu, 4, 1, topSlab(Blocks.DEEPSLATE_TILE_SLAB));
		f.set(cu, 4, 2, topSlab(Blocks.DEEPSLATE_TILE_SLAB));
		// step
		f.set(cu, 0, 2, f.stairIn(Blocks.DEEPSLATE_BRICK_STAIRS, false));
	}

	// ------------------------------------------------------------------ tower

	private static void tower(CitadelCanvas c, Plot p) {
		// foot: a wider band of deepslate with its own ledge
		for (int dv = -4; dv <= 4; dv++) {
			for (int du = -4; du <= 4; du++) {
				if (in(du, dv, 13) && !in(du, dv, R_OUT)) {
					p.set(c, TC + du, 0, TC + dv, deep(p, TC + du, 0, TC + dv));
				}
			}
		}
		for (int y = 0; y <= 11; y++) {
			for (int dv = -3; dv <= 3; dv++) {
				for (int du = -3; du <= 3; du++) {
					int u = TC + du;
					int v = TC + dv;
					if (!in(du, dv, R_OUT)) {
						continue;
					}
					if (in(du, dv, R_IN)) {
						p.set(c, u, y, v, y == 0 ? b(Blocks.SPRUCE_PLANKS) : y == 4 || y == 8 ? b(Blocks.DARK_OAK_PLANKS) : AIR);
					} else {
						p.set(c, u, y, v, towerMat(p, u, y, v));
					}
				}
			}
		}
		// windows: cardinal ones low, diagonal ones high, arrow slits at the top
		int[][] cardinal = {{0, -2}, {0, 2}, {-2, 0}};
		int[][] diagonal = {{2, -1}, {1, 2}, {-2, 1}, {-1, -2}};
		for (int[] o : cardinal) {
			towerWindow(c, p, o[0], o[1], 2, true);
		}
		for (int[] o : new int[][] {{0, -2}, {0, 2}, {-2, 0}, {2, 0}}) {
			p.set(c, TC + o[0], 9, TC + o[1], b(Blocks.TINTED_GLASS));
		}
		for (int[] o : diagonal) {
			towerWindow(c, p, o[0], o[1], 6, false);
		}
		// crown: corbel (inverted stairs, deepslate), then the cone
		StoneKit.discLayer(c, p, TC, TC, 12, R_CROWN, Blocks.DEEPSLATE_BRICK_STAIRS, b(Blocks.DEEPSLATE_BRICKS), true);
		int[] radii = {8, 5, 2, 1};
		for (int i = 0; i < radii.length; i++) {
			StoneKit.discLayer(c, p, TC, TC, 13 + i, radii[i], i == 0 ? Blocks.DARK_OAK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS,
					b(Blocks.DEEPSLATE_TILES), false);
		}
		p.set(c, TC, 17, TC, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
	}

	/** [palette] the tower is the darkest mass: deepslate up to the belt, a mix, then lighter stone under the crown. */
	private static BlockState towerMat(Plot p, int u, int y, int v) {
		return switch (y) {
			case 0, 1, 2, 3 -> deep(p, u, y, v);
			case 4 -> b(Blocks.CALCITE);
			case 5, 6 -> p.hash(u, y, v, 3) == 0 ? mid(p, u, y, v) : deep(p, u, y, v);
			case 7, 8 -> mid(p, u, y, v);
			case 9 -> b(Blocks.CALCITE);
			default -> light(p, u, y, v);
		};
	}

	private static void towerWindow(CitadelCanvas c, Plot p, int du, int dv, int y, boolean sill) {
		int u = TC + du;
		int v = TC + dv;
		p.set(c, u, y, v, b(Blocks.TINTED_GLASS));
		p.set(c, u, y + 1, v, b(Blocks.TINTED_GLASS));
		if (sill) {
			Direction o = StoneKit.outward(du, dv, R_OUT);
			p.set(c, u + o.getStepX(), y - 1, v + o.getStepZ(), stairs(Blocks.STONE_BRICK_STAIRS, o.getOpposite()));
		}
	}

	// ------------------------------------------------------------------ yard (outside the walls)

	private static void yard(CitadelCanvas c, Plot p, int u1, int v1) {
		int d = p.d();
		int w = p.w();
		int cu = (HALL_U0 + u1) / 2;
		// front: a low wall along the street with a gap at the steps, a pillar and a lantern at both ends
		StoneKit.lowWallRunU(c, p, HALL_U0 + 1, w - 2, 0, cu);
		for (int u : new int[] {HALL_U0, w - 1}) {
			p.set(c, u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 1, 0, b(Blocks.CHISELED_DEEPSLATE));
			p.set(c, u, 2, 0, StoneKit.lantern(false));
		}
		// beds between the wall and the facade
		for (int u = HALL_U0 + 1; u <= w - 2; u++) {
			if (Math.abs(u - cu) <= 1) {
				continue;
			}
			p.set(c, u, 0, 1, b(Blocks.MOSS_BLOCK));
			int k = p.hash(u, 1, 1, 4);
			p.set(c, u, 1, 1, k == 0 ? b(Blocks.FLOWERING_AZALEA) : k == 1 ? b(Blocks.FERN) : k == 2 ? b(Blocks.AZALEA) : b(Blocks.POTTED_FERN));
		}
		// back strip: barrels and a woodpile
		p.set(c, 5, 0, d - 1, StoneKit.barrel());
		p.set(c, 6, 0, d - 1, StoneKit.barrel());
		p.set(c, 6, 1, d - 1, slab(Blocks.SPRUCE_SLAB));
		for (int u = u1 - 4; u <= u1 - 1; u++) {
			p.set(c, u, 0, d - 1, StoneKit.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
			if (u != u1 - 2) {
				p.set(c, u, 1, d - 1, StoneKit.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
			}
		}
		// behind the tower: a roofed well and a fern bed
		int wv = Math.max(8, d - 5);
		StoneKit.well(c, p, 0, wv);
		for (int v = 8; v < wv; v++) {
			p.set(c, 1, 0, v, b(Blocks.MOSS_BLOCK));
			p.set(c, 1, 1, v, b(Blocks.FERN));
		}
	}
}
