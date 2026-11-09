package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.deep;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stone manor round a small open court. Two storeys on a dark rusticated foot, belt course and
 * cornice, pilasters between the window bays, a hipped slate roof that slopes both outwards to the
 * eaves and inwards to the court, and a gate pavilion on the front: a three-wide arched passage
 * under a balcony and a three-storey keep with its own hip roof, leading into the court (flagstones, a bush in
 * a moss bed, corner lamp posts).
 *
 * <pre>
 *  local layout (front = v 0)          section through the gate (v →)
 *  v0   steps, lamps, balcony           y12   pediment ridge
 *  v1-2 ▐▌ gate pavilion ▐▌             y6-7  balcony window
 *  v3   ■■■■■■■ front wing              y1-3  passage, arch at the faces
 *  v6   ■ ┌court┐ ■                     y0    floor
 *  d-5  ■ └─────┘ ■
 *  d-2  ■■■■■■■■■ back wing
 * </pre>
 *
 * Size: 13–15 × 14–15 × 18+. An even width leaves one empty column on the right.
 */
public final class HouseStoneManorCourt implements Variant {
	private static final int PLATE = 9;
	private static final int BELT = 5;

	@Override
	public String id() {
		return "house_stone_manor_court";
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
		return 14;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** Geometry of one build. */
	private record G(int we, int d, int cu, int cu0, int cu1, int cv0, int cv1) {
		static G of(Plot p) {
			int we = p.w() % 2 == 1 ? p.w() : p.w() - 1;
			int d = p.d();
			return new G(we, d, (we - 1) / 2, 4, we - 5, 6, d - 5);
		}

		boolean court(int u, int v) {
			return u >= cu0 && u <= cu1 && v >= cv0 && v <= cv1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		G g = G.of(p);
		int we = g.we();
		int d = g.d();
		floors(c, p, g);
		Wall front = new Wall(c, p, 3, Direction.NORTH);
		Wall back = new Wall(c, p, d - 2, Direction.SOUTH);
		Wall left = new Wall(c, p, 1, Direction.WEST);
		Wall right = new Wall(c, p, we - 2, Direction.EAST);
		Wall iF = new Wall(c, p, g.cv0() - 1, Direction.SOUTH);
		Wall iB = new Wall(c, p, g.cv1() + 1, Direction.NORTH);
		Wall iL = new Wall(c, p, g.cu0() - 1, Direction.EAST);
		Wall iR = new Wall(c, p, g.cu1() + 1, Direction.WEST);
		wallRun(front, 1, we - 2, true);
		wallRun(back, 1, we - 2, true);
		wallRun(left, 3, d - 2, true);
		wallRun(right, 3, d - 2, true);
		wallRun(iF, g.cu0() - 1, g.cu1() + 1, false);
		wallRun(iB, g.cu0() - 1, g.cu1() + 1, false);
		wallRun(iL, g.cv0() - 1, g.cv1() + 1, false);
		wallRun(iR, g.cv0() - 1, g.cv1() + 1, false);
		corners(c, p, g);
		bays(front, 1, g.cu() - 2, true);
		bays(front, g.cu() + 2, we - 2, true);
		bays(back, 1, we - 2, true);
		bays(left, 3, d - 2, true);
		bays(right, 3, d - 2, true);
		bays(iB, g.cu0() - 1, g.cu1() + 1, false);
		bays(iL, g.cv0() - 1, g.cv1() + 1, false);
		bays(iR, g.cv0() - 1, g.cv1() + 1, false);
		arcade(iB, g.cu0() - 1, g.cu1() + 1);
		arcade(iL, g.cv0() - 1, g.cv1() + 1);
		arcade(iR, g.cv0() - 1, g.cv1() + 1);
		roof(c, p, g);
		cornerPavilions(c, p, g, front, back, left, right);
		pavilion(c, p, g);
		court(c, p, g);
		chimneys(c, p, g);
		yard(c, p, g);
	}

	// ------------------------------------------------------------------ floors and walls

	private static void floors(CitadelCanvas c, Plot p, G g) {
		for (int u = 1; u <= g.we() - 2; u++) {
			for (int v = 3; v <= g.d() - 2; v++) {
				if (g.court(u, v)) {
					continue;
				}
				p.set(c, u, 0, v, b(Blocks.SPRUCE_PLANKS));
				p.set(c, u, BELT, v, b(Blocks.DARK_OAK_PLANKS));
			}
		}
	}

	/** [palette] foot → belt → upper: deepslate, stone bricks, light stone, bleeding by hash. */
	private static BlockState mat(Plot p, int u, int y, int v) {
		return switch (y) {
			case 0, 1 -> deep(p, u, y, v);
			case 2 -> p.hash(u, y, v, 6) < 1 ? deep(p, u, y, v) : mid(p, u, y, v);
			case 3, 4 -> mid(p, u, y, v);
			case BELT -> b(Blocks.CALCITE);
			case 6 -> p.hash(u, y, v, 6) < 1 ? mid(p, u, y, v) : light(p, u, y, v);
			case 7 -> light(p, u, y, v);
			case 8 -> p.hash(u, y, v, 6) == 0 ? b(Blocks.CHISELED_STONE_BRICKS) : light(p, u, y, v);
			default -> b(Blocks.CALCITE);
		};
	}

	private static void wallRun(Wall wl, int t0, int t1, boolean outer) {
		for (int t = t0; t <= t1; t++) {
			for (int y = 0; y <= PLATE; y++) {
				wl.set(t, y, 0, mat(wl.p, wl.u(t, 0), y, wl.v(t, 0)));
			}
			if (outer) {
				wl.set(t, 0, 1, deep(wl.p, wl.u(t, 1), 0, wl.v(t, 1))); // [depth] foot ledge
			}
			wl.set(t, BELT, 1, wl.stairIn(Blocks.POLISHED_ANDESITE_STAIRS, true)); // [depth] belt course
			if (outer) {
				// [depth] cornice: inverted stairs under the eave, every cell
				wl.set(t, PLATE - 1, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, true));
			}
		}
	}

	/** [finish] quoins: light corner columns that alternate calcite and polished andesite, with a corner block outside. */
	private static void corners(CitadelCanvas c, Plot p, G g) {
		int[][] cs = {{1, 3, -1, -1}, {g.we() - 2, 3, 1, -1}, {1, g.d() - 2, -1, 1}, {g.we() - 2, g.d() - 2, 1, 1}};
		for (int[] k : cs) {
			for (int y = 1; y <= PLATE - 1; y++) {
				BlockState q = y % 2 == 0 ? b(Blocks.CALCITE) : b(Blocks.POLISHED_ANDESITE);
				p.set(c, k[0], y, k[1], q);
				p.set(c, k[0] + k[2], y, k[1] + k[3], y == 1 ? b(Blocks.POLISHED_DEEPSLATE) : q);
			}
		}
	}

	// ------------------------------------------------------------------ bays: pilasters and windows

	private static void bays(Wall wl, int t0, int t1, boolean outer) {
		List<Integer> ps = new ArrayList<>();
		ps.add(t0);
		ps.add(t1);
		boolean split = true;
		while (split) {
			split = false;
			for (int i = 0; i + 1 < ps.size(); i++) {
				if (ps.get(i + 1) - ps.get(i) > 6) {
					ps.add(i + 1, (ps.get(i) + ps.get(i + 1)) / 2);
					split = true;
					break;
				}
			}
		}
		for (int i = 0; i < ps.size(); i++) {
			if (outer) {
				pilaster(wl, ps.get(i));
			}
			if (i + 1 < ps.size()) {
				bay(wl, ps.get(i), ps.get(i + 1));
			}
		}
	}

	/** [depth] a pilaster one block proud: dark foot, polished shaft, chiseled capital. */
	private static void pilaster(Wall wl, int t) {
		for (int y = 1; y <= PLATE - 1; y++) {
			wl.set(t, y, 1, y == 1 ? b(Blocks.DEEPSLATE_BRICKS) : y == PLATE - 1 ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
		}
	}

	private static void bay(Wall wl, int a, int b2) {
		int k = b2 - a - 1; // cells between the pilasters
		if (k < 1) {
			return;
		}
		int ww = k % 2 == 1 ? 1 : 2;
		int start = a + 1 + (k - ww) / 2;
		window(wl, start, ww, 2, 3, true, k >= 3 + ww - 1 && k - ww >= 2);
		window(wl, start, ww, 6, 2, false, k - ww >= 2);
	}

	private static void window(Wall wl, int a, int width, int y, int h, boolean sill, boolean shutters) {
		for (int i = 0; i < width; i++) {
			for (int k = 0; k < h; k++) {
				wl.set(a + i, y + k, 0, wl.pane());
			}
			if (sill) {
				wl.set(a + i, y - 1, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, false));
			}
			if (y + h != BELT) {
				wl.set(a + i, y + h, 1, wl.stairIn(Blocks.DEEPSLATE_BRICK_STAIRS, true)); // drip cap
			}
		}
		if (shutters) {
			for (int k = 0; k < h; k++) {
				wl.set(a - 1, y + k, 1, wl.shutter(Blocks.SPRUCE_TRAPDOOR));
				wl.set(a + width, y + k, 1, wl.shutter(Blocks.SPRUCE_TRAPDOOR));
			}
		}
	}

	/**
	 * [depth] the court gallery: the ground floor of an inner wall opens between pillars into 1-wide
	 * round-headed arches (inverted stairs spring from both pillars), a lantern hanging in each.
	 */
	private static void arcade(Wall wl, int t0, int t1) {
		for (int t = t0 + 1; t < t1; t++) {
			if ((t - t0) % 2 == 0) {
				// pillar: polished stone, a chiseled capital
				for (int y = 1; y <= 3; y++) {
					wl.set(t, y, 0, b(Blocks.POLISHED_ANDESITE));
				}
				wl.set(t, 4, 0, b(Blocks.CHISELED_STONE_BRICKS));
				continue;
			}
			for (int y = 1; y <= 3; y++) {
				wl.set(t, y, 0, AIR);
				wl.set(t, y, 1, AIR);
			}
			wl.set(t, 4, 0, b(Blocks.STONE_BRICKS));
			wl.put(t, 3, 0, StoneKit.lantern(true), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ roof

	/** Roof level of a ring cell (0 = eave), -1 where there is no roof (outside, or the court). */
	private static int level(G g, int u, int v) {
		if (u < 0 || u > g.we() - 1 || v < 2 || v > g.d() - 1 || g.court(u, v)) {
			return -1;
		}
		int k = Math.min(Math.min(u, g.we() - 1 - u), Math.min(v - 2, g.d() - 1 - v));
		int dx = Math.max(Math.max(g.cu0() - u, 0), u - g.cu1());
		int dz = Math.max(Math.max(g.cv0() - v, 0), v - g.cv1());
		return Math.min(k, Math.max(dx, dz));
	}

	/**
	 * [finish] a hip roof with a hole: every cell is a stair that rises towards its higher
	 * neighbour (so hips and valleys come out by themselves), the eave row in light stone, a slab
	 * ridge along the middle of every wing.
	 */
	private static void roof(CitadelCanvas c, Plot p, G g) {
		for (int v = 2; v <= g.d() - 1; v++) {
			for (int u = 0; u <= g.we() - 1; u++) {
				int l = level(g, u, v);
				if (l < 0) {
					continue;
				}
				int y = PLATE + l;
				Direction high = null;
				int best = l;
				for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
					int n = level(g, u + dir.getStepX(), v + dir.getStepZ());
					if (n > best) {
						best = n;
						high = dir;
					}
				}
				if (high == null) {
					for (int du = -1; du <= 1 && high == null; du += 2) {
						for (int dv = -1; dv <= 1; dv += 2) {
							if (level(g, u + du, v + dv) > l) {
								high = du > 0 ? Direction.EAST : Direction.WEST;
								break;
							}
						}
					}
				}
				if (high == null) {
					p.set(c, u, y, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
					p.set(c, u, y - 1, v, b(Blocks.DEEPSLATE_BRICKS));
				} else {
					Block blk = l == 0 ? Blocks.STONE_BRICK_STAIRS
							: p.hash(u, y, v, 7) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					p.set(c, u, y, v, stairs(blk, high));
				}
			}
		}
	}

	private static void chimneys(CitadelCanvas c, Plot p, G g) {
		for (int u : new int[] {g.cu() - 1, g.cu() + 1}) {
			int v = g.d() - 3;
			for (int y = PLATE + 2; y <= PLATE + 5; y++) {
				p.set(c, u, y, v, p.hash(u, y, v, 3) == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS));
			}
			p.set(c, u, PLATE + 6, v, b(Blocks.STONE_BRICK_SLAB));
		}
	}

	// ------------------------------------------------------------------ corner pavilions

	/**
	 * [finish] a 3x3 block rises one storey over every outer corner (a château pavilion): light
	 * walls with a window on both faces, a dentil cornice and a small pyramid roof of its own, so
	 * the silhouette is not a flat ring.
	 */
	private static void cornerPavilions(CitadelCanvas c, Plot p, G g, Wall front, Wall back, Wall left, Wall right) {
		int we = g.we();
		int d = g.d();
		int[][] cs = {{1, 3, 1, 1}, {we - 4, 3, -1, 1}, {1, d - 4, 1, -1}, {we - 4, d - 4, -1, -1}};
		for (int[] k : cs) {
			int u0 = k[0];
			int v0 = k[1];
			for (int y = PLATE + 1; y <= PLATE + 2; y++) {
				for (int u = u0; u <= u0 + 2; u++) {
					for (int v = v0; v <= v0 + 2; v++) {
						boolean outerCorner = (u == (k[2] > 0 ? u0 : u0 + 2)) && (v == (k[3] > 0 ? v0 : v0 + 2));
						p.set(c, u, y, v, outerCorner ? (y % 2 == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.CALCITE)) : light(p, u, y, v));
					}
				}
			}
			Wall fb = k[3] > 0 ? front : back;
			Wall lr = k[2] > 0 ? left : right;
			window(fb, u0 + 1, 1, 10, 2, false, true);
			window(lr, v0 + 1, 1, 10, 2, false, true);
			for (int t = u0; t <= u0 + 2; t++) {
				fb.set(t, PLATE + 2, 1, fb.stairIn(Blocks.STONE_BRICK_STAIRS, true));
			}
			for (int t = v0; t <= v0 + 2; t++) {
				lr.set(t, PLATE + 2, 1, lr.stairIn(Blocks.STONE_BRICK_STAIRS, true));
			}
			hipLayer(c, p, u0 - 1, v0 - 1, u0 + 3, v0 + 3, PLATE + 3, Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), b(Blocks.CUT_COPPER.waxed().oxidized()));
			p.set(c, u0 + 1, PLATE + 4, v0 + 1, slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized()));
			p.set(c, u0 + 1, PLATE + 5, v0 + 1, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
			cornerCornice(c, p, g, u0, v0);
		}
	}

	/**
	 * [depth] the copper hip roofs get a cornice of their own: a ring of inverted stone-brick stairs (slabs at
	 * the four corners) round the pavilion, right under the overhanging first copper course, wherever the
	 * main roof is not already there to carry it.
	 */
	private static void cornerCornice(CitadelCanvas c, Plot p, G g, int u0, int v0) {
		for (int u = u0 - 1; u <= u0 + 3; u++) {
			for (int v = v0 - 1; v <= v0 + 3; v++) {
				boolean inU = u >= u0 && u <= u0 + 2;
				boolean inV = v >= v0 && v <= v0 + 2;
				if ((inU && inV) || level(g, u, v) >= 2) {
					continue;
				}
				BlockState st;
				if (!inU && !inV) {
					st = topSlab(Blocks.STONE_BRICK_SLAB);
				} else if (inU) {
					st = stUp(Blocks.STONE_BRICK_STAIRS, v < v0 ? Direction.SOUTH : Direction.NORTH);
				} else {
					st = stUp(Blocks.STONE_BRICK_STAIRS, u < u0 ? Direction.EAST : Direction.WEST);
				}
				p.set(c, u, PLATE + 2, v, st);
			}
		}
	}

	/** One square layer of a pyramid: rim cells are stairs rising towards the centre, the inside is solid. */
	private static void hipLayer(CitadelCanvas c, Plot p, int u0, int v0, int u1, int v1, int y, Block stair, BlockState fill) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean l = u == u0;
				boolean r = u == u1;
				boolean f = v == v0;
				boolean bk = v == v1;
				if (!(l || r || f || bk)) {
					p.set(c, u, y, v, fill);
					continue;
				}
				Direction high;
				if (l) {
					high = Direction.EAST;
				} else if (r) {
					high = Direction.WEST;
				} else if (f) {
					high = Direction.SOUTH;
				} else {
					high = Direction.NORTH;
				}
				p.set(c, u, y, v, stairs(stair, high));
			}
		}
	}

	// ------------------------------------------------------------------ gate pavilion

	private static void pavilion(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		// piers, 2 deep, from the foot up to the cornice
		for (int v = 1; v <= 2; v++) {
			for (int s : new int[] {-2, 2}) {
				for (int y = 0; y <= PLATE; y++) {
					p.set(c, cu + s, y, v, y == 0 ? b(Blocks.DEEPSLATE_BRICKS) : y == BELT || y == PLATE ? b(Blocks.POLISHED_ANDESITE) : mat(p, cu + s, y, v));
				}
			}
			// above the passage: solid from the belt up
			for (int u = cu - 1; u <= cu + 1; u++) {
				for (int y = BELT; y <= PLATE; y++) {
					p.set(c, u, y, v, mat(p, u, y, v));
				}
			}
		}
		// passage, front wall to court face
		for (int v = 1; v <= 5; v++) {
			for (int u = cu - 1; u <= cu + 1; u++) {
				p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
				for (int y = 1; y <= 3; y++) {
					p.set(c, u, y, v, AIR);
				}
				p.set(c, u, 4, v, v == 1 || v == 5 ? AIR : b(Blocks.STONE_BRICKS));
			}
		}
		for (int v : new int[] {1, 5}) {
			p.set(c, cu - 1, 4, v, up(stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST)));
			p.set(c, cu + 1, 4, v, up(stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST)));
			p.set(c, cu, 4, v, AIR);
		}
		// at the outer face: arch trim, keystone, hanging lanterns
		p.set(c, cu, BELT, 1, b(Blocks.CHISELED_STONE_BRICKS));
		p.put(c, cu - 1, 3, 1, StoneKit.lantern(true), CitadelCanvas.HANGING);
		p.put(c, cu + 1, 3, 2, StoneKit.lantern(true), CitadelCanvas.HANGING);
		// balcony window and balcony
		p.set(c, cu, 6, 1, StoneKit.pane(true));
		p.set(c, cu, 7, 1, StoneKit.pane(true));
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, BELT, 0, topSlab(Blocks.STONE_BRICK_SLAB));
			p.set(c, u, 4, 0, stUp(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			p.set(c, u, 0, 0, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH)); // steps
			boolean end = u != cu;
			p.set(c, u, 6, 0, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, u < cu + 1, end, u > cu - 1, true));
		}
		p.put(c, cu - 1, 3, 0, StoneKit.lantern(true), CitadelCanvas.HANGING);
		p.put(c, cu + 1, 3, 0, StoneKit.lantern(true), CitadelCanvas.HANGING);
		// cornice
		for (int u = cu - 2; u <= cu + 2; u++) {
			p.set(c, u, PLATE, 0, stUp(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
		}
		keep(c, p, cu);
	}

	/**
	 * [finish] the gate keep: a three-storey body over the passage, taller than the corner pavilions. Light
	 * walls with quoins, a window on the front and on both flanks with shutters, a chiseled frieze, a cornice
	 * ring of inverted stairs and its own hip roof along v with a ridge cap and a lightning rod.
	 */
	private static void keep(CitadelCanvas c, Plot p, int cu) {
		int y0 = PLATE + 1;
		int top = PLATE + 4;
		for (int y = y0; y <= top; y++) {
			for (int u = cu - 1; u <= cu + 1; u++) {
				for (int v = 1; v <= 5; v++) {
					boolean quoin = (u != cu) && (v == 1 || v == 5);
					BlockState st;
					if (y == top) {
						st = (u == cu && v == 1) ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.CALCITE);
					} else if (quoin) {
						st = y % 2 == 0 ? b(Blocks.CALCITE) : b(Blocks.POLISHED_ANDESITE);
					} else {
						st = light(p, u, y, v);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		// windows: front (with sill and shutters), both flanks and the court side
		for (int y = PLATE + 2; y <= PLATE + 3; y++) {
			p.set(c, cu, y, 1, StoneKit.pane(true));
			p.set(c, cu, y, 5, StoneKit.pane(true));
			p.set(c, cu - 1, y, 3, StoneKit.pane(false));
			p.set(c, cu + 1, y, 3, StoneKit.pane(false));
			p.set(c, cu - 1, y, 0, StoneKit.shutter(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH));
			p.set(c, cu + 1, y, 0, StoneKit.shutter(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH));
		}
		p.set(c, cu, PLATE + 1, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH)); // sill
		p.set(c, cu, PLATE + 1, 1, b(Blocks.CHISELED_STONE_BRICKS));
		// cornice ring: inverted stairs round the body, slabs at the four corners
		for (int u = cu - 2; u <= cu + 2; u++) {
			for (int v = 0; v <= 6; v++) {
				boolean inU = u >= cu - 1 && u <= cu + 1;
				boolean inV = v >= 1 && v <= 5;
				if (inU && inV) {
					continue;
				}
				BlockState st;
				if (!inU && !inV) {
					st = topSlab(Blocks.STONE_BRICK_SLAB);
				} else if (inU) {
					st = stUp(Blocks.STONE_BRICK_STAIRS, v < 1 ? Direction.SOUTH : Direction.NORTH);
				} else {
					st = stUp(Blocks.STONE_BRICK_STAIRS, u < cu ? Direction.EAST : Direction.WEST);
				}
				p.set(c, u, top, v, st);
			}
		}
		// the roof: a hip along v on the cornice
		Block copper = Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
		BlockState copperBlock = b(Blocks.CUT_COPPER.waxed().oxidized());
		hipLayer(c, p, cu - 2, 0, cu + 2, 6, top + 1, copper, copperBlock);
		hipLayer(c, p, cu - 1, 1, cu + 1, 5, top + 2, copper, copperBlock);
		for (int v = 2; v <= 4; v++) {
			p.set(c, cu, top + 3, v, slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized()));
		}
		p.set(c, cu, top + 4, 3, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
		// [depth] the keep's cornice stands on corbels: beam tails and stair brackets under the front and side runs
		for (int u : new int[] {cu - 2, cu, cu + 2}) {
			EaveKit.corbel(c, p, u, top - 1, 0, Direction.SOUTH, Blocks.DARK_OAK_LOG, Blocks.STONE_BRICK_STAIRS);
		}
		for (int v : new int[] {2, 4}) {
			EaveKit.corbel(c, p, cu - 2, top - 1, v, Direction.EAST, Blocks.DARK_OAK_LOG, Blocks.STONE_BRICK_STAIRS);
			EaveKit.corbel(c, p, cu + 2, top - 1, v, Direction.WEST, Blocks.DARK_OAK_LOG, Blocks.STONE_BRICK_STAIRS);
		}
	}

	// ------------------------------------------------------------------ court

	private static void court(CitadelCanvas c, Plot p, G g) {
		int midU = (g.cu0() + g.cu1()) / 2;
		int midV = (g.cv0() + g.cv1()) / 2;
		for (int u = g.cu0(); u <= g.cu1(); u++) {
			for (int v = g.cv0(); v <= g.cv1(); v++) {
				boolean ring = u == g.cu0() || u == g.cu1() || v == g.cv0() || v == g.cv1();
				BlockState f = switch (p.hash(u, 0, v, 6)) {
					case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
					case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
					case 2, 3 -> b(Blocks.POLISHED_ANDESITE);
					default -> b(Blocks.STONE_BRICKS);
				};
				p.set(c, u, 0, v, ring && p.hash(u, 9, v, 3) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : f);
				for (int y = 1; y <= PLATE + 1; y++) {
					p.set(c, u, y, v, AIR);
				}
			}
		}
		// centre: a moss bed with a flowering bush
		p.set(c, midU, 0, midV, b(Blocks.MOSS_BLOCK));
		p.set(c, midU, 1, midV, b(Blocks.FLOWERING_AZALEA));
		// corner lamp posts and benches
		int[][] corners = {{g.cu0(), g.cv0()}, {g.cu1(), g.cv0()}, {g.cu0(), g.cv1()}, {g.cu1(), g.cv1()}};
		for (int[] k : corners) {
			if (k[1] == g.cv0() && Math.abs(k[0] - g.cu()) <= 2) {
				continue; // the passage mouth
			}
			p.set(c, k[0], 1, k[1], StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			p.set(c, k[0], 2, k[1], StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			p.set(c, k[0], 3, k[1], StoneKit.lantern(false));
		}
		p.set(c, g.cu0(), 1, midV, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
		p.set(c, g.cu1(), 1, midV, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
	}

	// ------------------------------------------------------------------ yard

	private static void yard(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		// lamp posts at the front
		for (int s : new int[] {-4, 4}) {
			int u = cu + s;
			if (u < 1 || u > g.we() - 2) {
				continue;
			}
			p.set(c, u, 0, 0, b(Blocks.DEEPSLATE_BRICKS));
			p.set(c, u, 1, 0, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			p.set(c, u, 2, 0, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			p.set(c, u, 3, 0, StoneKit.lantern(false));
		}
		// hedges along the street, a moss bed behind them, between the lamp posts and the corners
		for (int u = 1; u <= g.we() - 2; u++) {
			if (Math.abs(u - cu) <= 4) {
				continue;
			}
			p.set(c, u, 0, 0, b(Blocks.MOSS_BLOCK));
			p.set(c, u, 1, 0, p.hash(u, 1, 0, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
			p.set(c, u, 0, 1, b(Blocks.MOSS_BLOCK));
		}
		// planters on the foot ledge, either side of the gate
		for (int s : new int[] {-3, 3}) {
			p.set(c, cu + s, 1, 2, p.hash(cu + s, 1, 2, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
		// back strip: barrels and a woodpile
		int back = g.d() - 1;
		p.set(c, 3, 0, back, StoneKit.barrel());
		p.set(c, 4, 0, back, StoneKit.barrel());
		p.set(c, 4, 1, back, slab(Blocks.SPRUCE_SLAB));
		for (int u = g.we() - 6; u <= g.we() - 3; u++) {
			p.set(c, u, 0, back, StoneKit.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
		}
		// side strips: moss beds with ferns
		for (int v = 5; v <= g.d() - 4; v += 3) {
			for (int u : new int[] {0, g.we() - 1}) {
				p.set(c, u, 0, v, b(Blocks.MOSS_BLOCK));
				p.set(c, u, 1, v, b(Blocks.FERN));
			}
		}
	}
}
