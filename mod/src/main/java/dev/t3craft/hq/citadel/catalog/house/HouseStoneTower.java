package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.deep;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.stUp;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.topSlab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.StoneKit.Wall;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A stone tower-house with three storeys of four clear blocks, each with its own use: a workshop and store on the
 * ground floor, a living floor above it and a library-study on top. The stair is drawn by hand: three straight
 * flights two blocks wide, one step per cell and the first step met head-on, that zig-zag from the left wall to the
 * right wall of the tower, each one ending on a landing of the floor it reaches (workshop to living on one wall, living
 * to study on the other, and in the battlement version study to the roof terrace on the first wall again). Every hole
 * in a floor has a cherry rail round it.
 *
 * <p>Look: a deepslate plinth with a sloping ledge, stone-brick body that gets lighter with height, ashlar quoins
 * (polished andesite and calcite) up the corners, belt courses, arched windows (inverted stairs on the top corners,
 * a sill and cherry shutters), a corbel cornice, and either a crenellated terrace with corner turrets and a stair up to
 * it, or a hipped deepslate-tile roof with cherry eaves and a ridge. A low wing on one side has a store on the ground
 * and a parapeted terrace on its roof that opens off the living floor; if the plot is wider than that a walled patio
 * with a well takes the rest. Palette: stone bricks, deepslate, cherry, deepslate tiles (plus calcite and andesite).
 *
 * <p>The seed picks the roof (battlements or hip) and the side of the wing.
 *
 * <pre>
 *  y: 0 floor | 1-4 workshop | 5 floor | 6-9 living | 10 floor | 11-14 study | 15 deck | 16+ roof or merlons
 *  u: [margin] [tower 9-11] [wing 4-7, shared wall on the tower's side] [patio]   (mirrored with the seed)
 *  flight 1: A columns, v iv0+1..iv0+5, y1..5   (landing at the back)
 *  flight 2: B columns, v ivN-1..ivN-5, y6..10  (landing at the front)
 *  flight 3: A columns again, y11..15           (battlement version only)
 * </pre>
 *
 * Size: 15–20 × 13–18 × 20+ (the finial reaches y 20).
 */
public final class HouseStoneTower implements Variant {
	private static final int F1 = 5;
	private static final int F2 = 10;
	private static final int DECK = 15;

	/** Roof palettes by seed: slate with a cherry eave, red brick under a dark eave, mud brick under a slate eave. */
	private static final Block[] ROOF_STAIR = {Blocks.DEEPSLATE_TILE_STAIRS, Blocks.BRICK_STAIRS, Blocks.MUD_BRICK_STAIRS};
	private static final Block[] ROOF_FILL = {Blocks.DEEPSLATE_TILES, Blocks.BRICKS, Blocks.MUD_BRICKS};
	private static final Block[] EAVE_STAIR = {Blocks.SPRUCE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS};
	private static final Block[] CAP_STAIR = ROOF_STAIR; // dormer gables roof in the main roof's own material
	private static final Block[] CAP_FILL = ROOF_FILL;
	private static final Block[] ROOF_SLAB = {Blocks.DEEPSLATE_TILE_SLAB, Blocks.BRICK_SLAB, Blocks.MUD_BRICK_SLAB};
	private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.DANDELION, Blocks.FERN};

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
		return 15;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 20;
	}

	@Override
	public int maxW() {
		return 20;
	}

	@Override
	public int maxD() {
		return 18;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Build(c, p).run();
	}

	/** One build: the layout is worked out once in the constructor, everything after only reads it. */
	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final int tw;
		final int td;
		final int cu0;
		final int cu1;
		final int cv0;
		final int cv1;
		final int cc; // centre column of the tower
		final int iv0; // first interior row
		final int ivN; // last interior row
		final boolean annexRight;
		final boolean battle;
		final int roofKind; // 0 slate/cherry, 1 brick, 2 mud brick
		final int dormMode; // 0 one front + one back, 1 two front, 2 two back, 3 none (squat roof)
		final int chimMode; // 0 far end, 1 wing end, 2 middle
		final int turretMode; // 0 four turrets, 1 back two, 2 diagonal two
		final int balconyMode; // 0 open balcony, 1 balcony under a cherry canopy
		final int wingCut; // rows the wing stops short of the tower's back wall
		final int rise; // rows the hip roof climbs
		final int shared; // u of the wall between tower and wing
		final int outer; // u of the wing's outer wall
		final int aw; // interior width of the wing
		final int av0;
		final int av1;
		final int ac; // centre of the wing (front door)
		final int[] aCols; // stair columns of flights 1 and 3 (away from the wing)
		final int[] bCols; // stair columns of flight 2 (on the wing's side)
		final int aInner;
		final int bInner;
		final int doorV; // door from the tower to the wing (ground)
		final int terraceDoorV; // door from the living floor to the wing's roof
		final Set<Integer> sideWin = new HashSet<>();
		final Set<Integer> backWin = new HashSet<>();

		Build(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			Random r = p.rng(0x570E70A1L);
			battle = r.nextBoolean();
			annexRight = r.nextBoolean();
			roofKind = r.nextInt(3);
			dormMode = r.nextInt(4);
			chimMode = r.nextInt(3);
			turretMode = r.nextInt(3);
			balconyMode = r.nextInt(2);
			boolean cutRoll = r.nextBoolean();
			rise = !battle && dormMode == 3 ? 3 : 4;
			w = p.w();
			d = p.d();
			int t = Math.max(9, Math.min(11, w - 6));
			tw = t % 2 == 0 ? t - 1 : t;
			td = Math.max(10, Math.min(12, d - 4));
			wingCut = td >= 12 && cutRoll ? 3 : 0;
			cv0 = 2;
			cv1 = cv0 + td - 1;
			iv0 = cv0 + 1;
			ivN = cv1 - 1;
			int avail = w - 2 - tw;
			aw = Math.min(7, Math.max(3, avail));
			if (annexRight) {
				cu0 = 1;
				cu1 = cu0 + tw - 1;
				shared = cu1;
				outer = shared + aw + 1;
				aCols = new int[] {cu0 + 1, cu0 + 2};
				bCols = new int[] {cu1 - 2, cu1 - 1};
				aInner = cu0 + 3;
				bInner = cu1 - 3;
			} else {
				cu1 = w - 2;
				cu0 = cu1 - tw + 1;
				shared = cu0;
				outer = shared - aw - 1;
				aCols = new int[] {cu1 - 2, cu1 - 1};
				bCols = new int[] {cu0 + 1, cu0 + 2};
				aInner = cu1 - 3;
				bInner = cu0 + 3;
			}
			cc = (cu0 + cu1) / 2;
			av0 = iv0;
			av1 = ivN - wingCut;
			ac = (shared + outer) / 2;
			doorV = (av0 + av1) / 2;
			terraceDoorV = iv0 + 1;
		}

		void run() {
			floors();
			towerWalls();
			deck();
			annex();
			flights();
			pilasters();
			windows();
			porch();
			balcony();
			wingTerrace();
			buttresses();
			rings();
			if (battle) {
				battlements();
			} else {
				hipRoof();
				chimney();
				dormers();
			}
			furnish();
			exterior();
		}

		// ------------------------------------------------------------------ small helpers

		boolean free(int u, int y, int v) {
			if (!p.has(u, y, v)) {
				return false;
			}
			BlockState s = p.at(c, u, y, v);
			return s == null || s.isAir();
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		void setFree(int u, int y, int v, BlockState s) {
			if (free(u, y, v)) {
				p.set(c, u, y, v, s);
			}
		}

		static BlockState facing(Block blk, Direction dir) {
			return b(blk).setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
		}

		/** Whether (u, v) is on the wing's footprint (its walls, interior and roof deck, not the shared wall). */
		boolean onAnnex(int u, int v) {
			int lo = Math.min(shared, outer);
			int hi = Math.max(shared, outer);
			return u >= lo && u <= hi && v >= av0 && v <= av1;
		}

		/** Local direction along a wall run: sign +1 = increasing t. */
		static Direction along(Wall wl, int sign) {
			if (wl.alongU()) {
				return sign > 0 ? Direction.EAST : Direction.WEST;
			}
			return sign > 0 ? Direction.SOUTH : Direction.NORTH;
		}

		/** Shared outward direction of the wing's side walls: away from the tower. */
		Direction annexOut() {
			return annexRight ? Direction.EAST : Direction.WEST;
		}

		Direction towerSideOut() {
			return annexRight ? Direction.WEST : Direction.EAST;
		}

		// ------------------------------------------------------------------ floors and walls

		void floors() {
			for (int u = cu0 + 1; u <= cu1 - 1; u++) {
				for (int v = cv0 + 1; v <= cv1 - 1; v++) {
					BlockState tile = p.hash(u, 0, v, 4) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES);
					set(u, 0, v, tile);
					set(u, F1, v, b(Blocks.SPRUCE_PLANKS));
					set(u, F2, v, b(Blocks.DARK_OAK_PLANKS));
				}
			}
		}

		/** [palette] dark deepslate foot, a stone-brick body that gets lighter, ashlar quoins on the corners. */
		BlockState wallMat(int idx, int len, int u, int y, int v, boolean alongU) {
			boolean corner = idx == 0 || idx == len - 1;
			if (y == F1 || y == F2) {
				if (corner) {
					return b(Blocks.POLISHED_DEEPSLATE);
				}
				return y == F1 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.MUD_BRICKS); // a dark belt course, then a warm one
			}
			if (corner) {
				return y <= 1 ? b(Blocks.POLISHED_DEEPSLATE) : quoin(u, y, v);
			}
			if (y <= 1) {
				return deep(p, u, y, v);
			}
			if (y == 14) {
				return b(Blocks.CALCITE); // the one pale course under the cornice
			}
			if ((idx == 1 || idx == len - 2) && (y % 2 == 0) == alongU) {
				return quoin(u, y, v); // long-and-short work: the long stone runs onto this wall on alternate courses
			}
			return body(u, y, v);
		}

		/** [palette] deepslate quoin: polished on even courses, bricks (the odd cracked one) on odd ones. */
		BlockState quoin(int u, int y, int v) {
			if (y % 2 == 0) {
				return b(Blocks.POLISHED_DEEPSLATE);
			}
			return p.hash(u, y, v, 6) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
		}

		/** [palette] the wall body: patches of mossy or cracked stone bricks (3-block zones, mossier low down) in clean brick. */
		BlockState body(int u, int y, int v) {
			int zone = p.hash(u / 4, y / 3, v / 4, 7);
			boolean fleck = p.hash(u, y, v, 3) != 0;
			int mossy = y <= 4 ? 3 : y <= 9 ? 2 : 1;
			if (zone < mossy) {
				return fleck ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			}
			if (zone >= 5) {
				return fleck ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			}
			return p.hash(u, y, v, 24) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
		}

		/** Plain light stone for parapets and chimneys. */
		BlockState lt(int u, int y, int v) {
			int h = p.hash(u, y, v, 12);
			return h == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : h == 1 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
		}

		static BlockState glass() {
			return Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
		}

		void towerWalls() {
			Wall front = new Wall(c, p, cv0, Direction.NORTH);
			Wall back = new Wall(c, p, cv1, Direction.SOUTH);
			Wall left = new Wall(c, p, cu0, Direction.WEST);
			Wall right = new Wall(c, p, cu1, Direction.EAST);
			run(front, cu0, cu1);
			run(back, cu0, cu1);
			run(left, cv0, cv1);
			run(right, cv0, cv1);
		}

		void run(Wall wl, int t0, int t1) {
			int len = t1 - t0 + 1;
			for (int t = t0; t <= t1; t++) {
				for (int y = 0; y <= 14; y++) {
					wl.set(t, y, 0, wallMat(t - t0, len, wl.u(t, 0), y, wl.v(t, 0), wl.alongU()));
				}
			}
		}

		/** The 13-wide deck over the tower: the ceiling of the study and, in the battlement version, the terrace floor. */
		void deck() {
			for (int u = cu0 - 1; u <= cu1 + 1; u++) {
				for (int v = cv0 - 1; v <= cv1 + 1; v++) {
					boolean ring = u < cu0 || u > cu1 || v < cv0 || v > cv1;
					if (ring) {
						if (battle) {
							set(u, DECK, v, b(Blocks.DEEPSLATE_BRICKS));
						}
					} else {
						set(u, DECK, v, p.hash(u, DECK, v, 5) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
					}
				}
			}
		}

		// ------------------------------------------------------------------ the wing

		void annex() {
			int du0 = annexRight ? shared + 1 : outer;
			int du1 = annexRight ? outer : shared - 1;
			Wall front = new Wall(c, p, av0, Direction.NORTH);
			Wall back = new Wall(c, p, av1, Direction.SOUTH);
			Wall side = new Wall(c, p, outer, annexOut());
			// walls (the shared wall is the tower's)
			for (int u = du0; u <= du1; u++) {
				for (int y = 0; y <= 4; y++) {
					front.set(u, y, 0, annexMat(u == outer ? 0 : 1, u, y, av0));
					back.set(u, y, 0, annexMat(u == outer ? 0 : 1, u, y, av1));
				}
			}
			for (int v = av0; v <= av1; v++) {
				for (int y = 0; y <= 4; y++) {
					side.set(v, y, 0, annexMat(v == av0 || v == av1 ? 0 : 1, outer, y, v));
				}
			}
			for (int u = Math.min(du0, du1) + (annexRight ? 0 : 1); u <= Math.max(du0, du1) - (annexRight ? 1 : 0); u++) {
				if (u == outer) {
					continue;
				}
				for (int v = av0 + 1; v <= av1 - 1; v++) {
					set(u, 0, v, p.hash(u, 0, v, 4) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES));
				}
			}
			// the roof deck at the height of the living floor, with a low parapet round its outer edges
			for (int u = du0; u <= du1; u++) {
				for (int v = av0; v <= av1; v++) {
					set(u, F1, v, p.hash(u, F1, v, 5) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
				}
			}
			Set<Long> par = new HashSet<>();
			for (int u = du0; u <= du1; u++) {
				for (int v = av0; v <= av1; v++) {
					if (u == outer || v == av0 || v == av1) {
						par.add(key(u, v));
					}
				}
			}
			for (long k : par) {
				int u = (int) (k >> 16);
				int v = (int) (k & 0xFFFF);
				boolean n = par.contains(key(u, v - 1));
				boolean s = par.contains(key(u, v + 1));
				boolean e = par.contains(key(u + 1, v)) || (!annexRight && u + 1 == shared);
				boolean wst = par.contains(key(u - 1, v)) || (annexRight && u - 1 == shared);
				set(u, F1 + 1, v, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, n, e, s, wst, true));
				if ((u == outer) && (v == av0 || v == av1)) {
					set(u, F1 + 2, v, StoneKit.lantern(false));
				}
			}
			// the door from the wing to the street, with a step, arch and shutters on the windows
			archDoor(front, ac, 1, Blocks.DARK_OAK_DOOR);
			front.set(ac, 0, 1, front.stairIn(Blocks.DEEPSLATE_BRICK_STAIRS, false));
			for (int v : new int[] {doorV - 2, doorV + 2}) {
				if (v > av0 + 1 && v < av1 - 1) {
					arch(side, v, 2, true);
				}
			}
			if (aw >= 4) {
				arch(back, ac, 2, true);
			}
			// the door between tower and wing
			Wall sw = new Wall(c, p, shared, annexOut());
			archDoor(sw, doorV, 1, Blocks.DARK_OAK_DOOR);
			// the door from the living floor onto the wing's roof
			archDoor(sw, terraceDoorV, F1 + 1, Blocks.CHERRY_DOOR);
		}

		static long key(int u, int v) {
			return ((long) u << 16) | (v & 0xFFFF);
		}

		BlockState annexMat(int mode, int u, int y, int v) {
			boolean corner = mode == 0;
			if (corner) {
				if (y <= 1) {
					return b(Blocks.POLISHED_DEEPSLATE);
				}
				return quoin(u, y, v);
			}
			if (y <= 1) {
				return deep(p, u, y, v);
			}
			return body(u, y, v);
		}

		// ------------------------------------------------------------------ openings

		/** An arched window of two panes and a crown: inverted stairs on the top corners, a sill and cherry shutters. */
		void arch(Wall wl, int t, int y, boolean shutters) {
			// the glass is flush with the wall plane (a pane set back inside would block the walk along the wall); the
			// depth comes from the jambs, the sill and the hood standing proud of it
			for (int yy = y; yy <= y + 2; yy++) {
				wl.set(t, yy, 0, glass());
			}
			for (int sign : new int[] {-1, 1}) {
				int tt = t + sign * 1;
				if (!solidCorner(wl, tt)) {
					// [depth] dark jambs and inverted stairs on the top corners frame the pale blue glass
					wl.set(tt, y, 0, b(Blocks.POLISHED_DEEPSLATE));
					wl.set(tt, y + 1, 0, b(Blocks.POLISHED_DEEPSLATE));
					wl.set(tt, y + 2, 0, stUp(Blocks.DEEPSLATE_BRICK_STAIRS, along(wl, sign)));
				}
			}
			// [depth] a pale diorite stair sill in the wall plane, slanting down and out (no ledge to stand on); the dark
			// belt course over the window is its lintel
			wl.set(t, y - 1, 0, wl.stairIn(Blocks.POLISHED_DIORITE_STAIRS, false));
			if (shutters && p.hash(wl.u(t, 0), y, wl.v(t, 0), 3) == 0) {
				for (int sign : new int[] {-1, 1}) {
					if (!solidCorner(wl, t + sign)) {
						setFreeWall(wl, t + sign, y, 1, wl.shutter(Blocks.CHERRY_TRAPDOOR));
						setFreeWall(wl, t + sign, y + 1, 1, wl.shutter(Blocks.CHERRY_TRAPDOOR));
					}
				}
			}
		}

		/** A two-wide window on cells t and t + 1: glass on both, a sill under both and dark jambs outside both. */
		void archPair(Wall wl, int t, int y) {
			arch(wl, t, y, false);
			for (int yy = y; yy <= y + 2; yy++) {
				wl.set(t + 1, yy, 0, glass());
			}
			wl.set(t + 1, y - 1, 0, wl.stairIn(Blocks.POLISHED_DIORITE_STAIRS, false));
			if (!solidCorner(wl, t + 2)) {
				wl.set(t + 2, y, 0, b(Blocks.POLISHED_DEEPSLATE));
				wl.set(t + 2, y + 1, 0, b(Blocks.POLISHED_DEEPSLATE));
				wl.set(t + 2, y + 2, 0, stUp(Blocks.DEEPSLATE_BRICK_STAIRS, along(wl, 1)));
			}
		}

		void setFreeWall(Wall wl, int t, int y, int o, BlockState s) {
			int u = wl.u(t, o);
			int v = wl.v(t, o);
			setFree(u, y, v, s);
		}

		/** A wall cell that is a corner of the tower or the wing: left alone by arches. */
		boolean solidCorner(Wall wl, int t) {
			int u = wl.u(t, 0);
			int v = wl.v(t, 0);
			boolean tc = (u == cu0 || u == cu1) && (v == cv0 || v == cv1);
			boolean ac2 = (u == outer || u == shared) && (v == av0 || v == av1);
			return tc || ac2;
		}

		/** A door two high at {@code y} with a glass transom and arch springers above it, in the wall {@code wl}. */
		void archDoor(Wall wl, int t, int y, Block door) {
			wl.set(t, y, 0, wl.door(door, true));
			wl.set(t, y + 1, 0, wl.door(door, false));
			wl.set(t, y + 2, 0, glass());
			for (int sign : new int[] {-1, 1}) {
				if (!solidCorner(wl, t + sign)) {
					wl.set(t + sign, y + 2, 0, stUp(Blocks.DEEPSLATE_BRICK_STAIRS, along(wl, sign)));
				}
			}
			if (p.has(wl.u(t, 0), y + 3, wl.v(t, 0))) {
				wl.set(t, y + 3, 0, b(Blocks.CHISELED_STONE_BRICKS));
			}
		}

		void windows() {
			Wall front = new Wall(c, p, cv0, Direction.NORTH);
			Wall back = new Wall(c, p, cv1, Direction.SOUTH);
			Wall aWall = new Wall(c, p, annexRight ? cu0 : cu1, annexRight ? Direction.WEST : Direction.EAST);
			Wall bWall = new Wall(c, p, shared, annexOut());
			int[] ys = {2, 7, 12};
			int vc = (cv0 + cv1) / 2;
			for (int y : ys) {
				// front: both sides of the door, and above it on the upper floors
				if (y == 2) {
					for (int sign : new int[] {-3, 3}) {
						int t = cc + sign;
						if (t >= cu0 + 1 && t <= cu1 - 1) {
							arch(front, t, y, true);
						}
					}
				} else {
					// upper floors: a pair of windows each side of the middle, two wide so they read from the street
					for (int t : new int[] {cc - 3, cc + 2}) {
						if (t >= cu0 + 1 && t + 1 <= cu1 - 1) {
							archPair(front, t, y);
						}
					}
				}
				if (y > 2 && y != 12) {
					arch(front, cc, y, true);
				}
				// back
				for (int sign : new int[] {-3, 0, 3}) {
					int t = cc + sign;
					if (t >= cu0 + 1 && t <= cu1 - 1) {
						arch(back, t, y, true);
						backWin.add(t);
					}
				}
				// the stair wall
				if (y == 2) {
					for (int sign : new int[] {-3, 0, 3}) {
						int t = vc + sign;
						if (t >= cv0 + 1 && t <= cv1 - 1) {
							arch(aWall, t, y, true);
							sideWin.add(t);
						}
					}
				} else {
					if (vc >= cv0 + 1 && vc <= cv1 - 1) {
						arch(aWall, vc, y, true);
						sideWin.add(vc);
					}
					for (int t : new int[] {vc - 3, vc + 2}) {
						if (t >= cv0 + 1 && t + 1 <= cv1 - 1) {
							archPair(aWall, t, y);
							sideWin.add(t);
							sideWin.add(t + 1);
						}
					}
				}
				// the wing's wall: only above the wing's roof, and not beside the door onto it
				for (int sign : new int[] {-3, 0, 3}) {
					int t = vc + sign;
					if (t < cv0 + 1 || t > cv1 - 1) {
						continue;
					}
					boolean bare = t > av1 || t < av0; // no wing against the wall here
					if ((y > 2 && Math.abs(t - terraceDoorV) > 2) || (y == 2 && bare)) {
						arch(bWall, t, y, true);
					}
				}
			}
		}

		void porch() {
			Wall f = new Wall(c, p, cv0, Direction.NORTH);
			archDoor(f, cc, 1, Blocks.CHERRY_DOOR);
			for (int sign : new int[] {-1, 1}) {
				f.set(cc + sign, 1, 0, b(Blocks.POLISHED_ANDESITE)); // a pale frame round the door
				f.set(cc + sign, 2, 0, b(Blocks.POLISHED_ANDESITE));
			}
			// steps three wide, pilasters with lanterns on both sides
			for (int t = cc - 1; t <= cc + 1; t++) {
				f.set(t, 0, 1, f.stairIn(Blocks.DEEPSLATE_BRICK_STAIRS, false));
			}
			for (int sign : new int[] {-2, 2}) {
				int t = cc + sign;
				for (int y = 0; y <= 3; y++) {
					f.set(t, y, 1, y == 3 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
				}
			}
		}

		// ------------------------------------------------------------------ rings round the tower

		/**
		 * A ring of stairs (or blocks on the corners) one cell outside the tower walls at height y: the high side
		 * towards the wall. Only fills what is still empty, and skips the wing.
		 */
		void ring(int y, Block stair, Block corner, boolean inverted, boolean skipFront) {
			int ru0 = cu0 - 1;
			int ru1 = cu1 + 1;
			int rv0 = cv0 - 1;
			int rv1 = cv1 + 1;
			for (int u = ru0; u <= ru1; u++) {
				for (int v = rv0; v <= rv1; v++) {
					boolean eu = u == ru0 || u == ru1;
					boolean ev = v == rv0 || v == rv1;
					if (!eu && !ev) {
						continue;
					}
					if (skipFront && v == rv0) {
						continue;
					}
					if (y <= F1 + 1 && onAnnexExt(u, v)) {
						continue;
					}
					BlockState s;
					if (eu && ev) {
						s = b(corner);
					} else {
						Direction high = ev ? (v == rv0 ? Direction.SOUTH : Direction.NORTH) : (u == ru0 ? Direction.EAST : Direction.WEST);
						s = inverted ? stUp(stair, high) : stairs(stair, high);
					}
					setFree(u, y, v, s);
				}
			}
		}

		boolean onAnnexExt(int u, int v) {
			return onAnnex(u, v);
		}

		/**
		 * [depth] an L-shaped buttress on each corner of the tower, three cells a corner, running up to the cornice so
		 * there is no ledge to stand on under it. Deepslate at the foot, ashlar above.
		 */
		/** [depth] a one-block pilaster up each wall between the windows, banded at the floor lines, under the cornice. */
		void pilasters() {
			Wall front = new Wall(c, p, cv0, Direction.NORTH);
			Wall back = new Wall(c, p, cv1, Direction.SOUTH);
			Wall aWall = new Wall(c, p, annexRight ? cu0 : cu1, annexRight ? Direction.WEST : Direction.EAST);
			int vc = (cv0 + cv1) / 2;
			for (int sign : new int[] {-2, 2}) {
				pilaster(front, cc + sign, 3); // plinth-high only: the upper floors have paired windows there
				pilaster(back, cc + sign, 13);
				pilaster(aWall, vc + sign, 3);
			}
		}

		void pilaster(Wall wl, int t, int top) {
			int u = wl.u(t, 1);
			int v = wl.v(t, 1);
			if (onAnnex(u, v)) {
				return;
			}
			for (int y = 0; y <= top; y++) {
				BlockState s;
				if (y <= 1) {
					s = deep(p, u, y, v);
				} else if (y == F2) {
					s = b(Blocks.MUD_BRICKS);
				} else if (y == F1 || y == 13) {
					s = b(Blocks.DEEPSLATE_BRICKS);
				} else {
					s = body(u, y, v);
				}
				setFree(u, y, v, s);
			}
		}

		void buttresses() {
			int[][] corners = {{cu0, cv0, -1, -1}, {cu1, cv0, 1, -1}, {cu0, cv1, -1, 1}, {cu1, cv1, 1, 1}};
			for (int[] k : corners) {
				int[][] cells = {{k[0] + k[2], k[1]}, {k[0] + k[2], k[1] + k[3]}, {k[0], k[1] + k[3]}};
				for (int ci = 0; ci < cells.length; ci++) {
					int[] cell = cells[ci];
					if (onAnnex(cell[0], cell[1])) {
						continue;
					}
					for (int y = 0; y <= 13; y++) {
						// the diagonal cell is the deepslate spine; the two flanks are stone, so the buttress is not a black slab
						BlockState s = y <= 1 ? b(Blocks.POLISHED_DEEPSLATE) : y == 13 ? b(Blocks.DEEPSLATE_BRICKS)
								: ci == 1 ? quoin(cell[0], y, cell[1]) : body(cell[0], y, cell[1]);
						if (y == F1 || y == F2) {
							s = y == F1 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.MUD_BRICKS);
						}
						set(cell[0], y, cell[1], s);
					}
				}
			}
		}

		/** A stone chimney through the back of the hip roof, with a deepslate stack. */
		/** [depth] a stone dormer with a dark glass window and a cherry cap on the front and back slopes of the hip roof. */
		void dormers() {
			if (dormMode == 3) {
				return;
			}
			if (dormMode == 1) {
				dormer(cc - 3, cv0, 1, 1);
				dormer(cc + 3, cv0, 1, 1);
			} else {
				dormer(cc, cv0, 2, 1);
			}
			if (dormMode == 2) {
				dormer(cc - 3, cv1, 1, -1);
				dormer(cc + 3, cv1, 1, -1);
			} else {
				dormer(cc, cv1, 2, -1);
			}
		}

		/**
		 * [depth] a dormer that stands proud of the slope: a pale sill course, a pale window between dark piers, a pale
		 * gable face under a little roof (gable stairs and a ridge block in the roof's cap colour). {@code half} is the
		 * half-width (1 = 3 wide, 2 = 5 wide); {@code dv} points from the face towards the middle of the roof.
		 */
		void dormer(int cu, int v, int half, int dv) {
			Block capStair = CAP_STAIR[roofKind];
			Block capFill = CAP_FILL[roofKind];
			for (int du = -half; du <= half; du++) {
				int u = cu + du;
				boolean edge = Math.abs(du) == half;
				Direction towardsMiddle = du < 0 ? Direction.EAST : Direction.WEST;
				set(u, DECK + 1, v, b(Blocks.POLISHED_DIORITE));
				set(u, DECK + 2, v, edge ? b(Blocks.STONE_BRICKS) : glass());
				for (int row = 0; row <= 1; row++) {
					int vv = v + row * dv;
					BlockState gable = edge ? stairs(capStair, towardsMiddle) : row == 0 ? b(Blocks.POLISHED_DIORITE) : b(capFill);
					BlockState ridge = Math.abs(du) == half - 1 && half == 2 ? stairs(capStair, towardsMiddle) : b(capFill);
					if (row == 0) {
						set(u, DECK + 3, vv, gable);
						if (!edge) {
							set(u, DECK + 4, vv, ridge);
						}
					} else {
						setFree(u, DECK + 3, vv, gable);
						if (!edge) {
							setFree(u, DECK + 4, vv, ridge);
						}
					}
				}
			}
		}

		void chimney() {
			int u0;
			int vTo;
			if (chimMode == 2) {
				u0 = cc - 1;
				vTo = cv1 - 3;
			} else {
				boolean far = chimMode == 0;
				u0 = annexRight == far ? cu0 : cu1 - 1;
				vTo = cv1 - 1;
			}
			for (int u = u0; u <= u0 + 1; u++) {
				for (int v = vTo - 1; v <= vTo; v++) {
					for (int y = DECK + 1; y <= Math.min(p.h() - 1, 21); y++) {
						set(u, y, v, y >= 18 ? b(Blocks.DEEPSLATE_BRICKS) : lt(u, y, v));
					}
				}
			}
		}

		void rings() {
			ring(0, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICKS, false, true); // plinth ledge
			ring(1, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, false, true);
			if (battle) {
				ring(14, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, true, false); // the cornice corbel: a thin dark-timber line under the parapet
			} else {
				// pale stone corbel, so the roof eave above it (a different colour) reads as its own step
				ring(14, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, true, false);
			}
		}

		// ------------------------------------------------------------------ stairs

		/**
		 * A flight of five steps two blocks wide on {@code cols}: the approach cell is at row {@code vs} on floor layer
		 * {@code base}, the steps run along {@code dv} (rows {@code vs + dv} to {@code vs + 5 dv}), the step on layer
		 * {@code base + 1 + j}. The floor above ({@code base + 5}) is cut over the first four steps from {@code holeFrom}
		 * (the rest of the flight runs under it) and fenced on the upper floor.
		 */
		void flight(int[] cols, int inner, int vs, int dv, int base, int holeFrom) {
			Direction dir = dv > 0 ? Direction.SOUTH : Direction.NORTH;
			Set<Long> rail = new HashSet<>();
			for (int j = 0; j < 5; j++) {
				int v = vs + dv * (j + 1);
				for (int u : cols) {
					for (int y = base + 1; y <= base + j; y++) {
						set(u, y, v, b(Blocks.STONE_BRICKS));
					}
					set(u, base + 1 + j, v, stairs(Blocks.STONE_BRICK_STAIRS, dir));
					if (j < 4 && j >= holeFrom) {
						set(u, base + 5, v, AIR);
					}
				}
				if (j < 4 && j >= holeFrom) {
					rail.add(key(inner, v));
				}
			}
			int vEnd = vs + dv * holeFrom; // the row before the first cut one
			for (int u : cols) {
				rail.add(key(u, vEnd));
			}
			rail.add(key(inner, vEnd));
			int ry = base + 6;
			for (long k : rail) {
				int u = (int) (k >> 16);
				int v = (int) (k & 0xFFFF);
				if (!p.has(u, ry, v) || free(u, base + 5, v)) {
					continue;
				}
				set(u, ry, v, Blocks.CHERRY_FENCE.defaultBlockState()
						.setValue(BlockStateProperties.NORTH, rail.contains(key(u, v - 1)))
						.setValue(BlockStateProperties.EAST, rail.contains(key(u + 1, v)))
						.setValue(BlockStateProperties.SOUTH, rail.contains(key(u, v + 1)))
						.setValue(BlockStateProperties.WEST, rail.contains(key(u - 1, v))));
			}
		}

		void flights() {
			flight(aCols, aInner, iv0, 1, 0, 0);
			flight(bCols, bInner, ivN, -1, F1, 0);
			if (battle) {
				flight(aCols, aInner, iv0, 1, F2, 1);
			}
		}

		// ------------------------------------------------------------------ roofs

		void battlements() {
			int ru0 = cu0 - 1;
			int ru1 = cu1 + 1;
			int rv0 = cv0 - 1;
			int rv1 = cv1 + 1;
			for (int u = ru0; u <= ru1; u++) {
				for (int v = rv0; v <= rv1; v++) {
					boolean eu = u == ru0 || u == ru1;
					boolean ev = v == rv0 || v == rv1;
					if (!eu && !ev) {
						continue;
					}
					set(u, 16, v, lt(u, 16, v));
					if ((u + v) % 2 == 0) {
						set(u, 17, v, b(Blocks.STONE_BRICKS));
						set(u, 18, v, p.hash(u, 18, v, 3) == 0 ? b(Blocks.STONE_BRICKS) : slab(Blocks.STONE_BRICK_SLAB));
					}
				}
			}
			// [depth] a 2x2 turret on some corners (all four, the back two or a diagonal pair, by seed), with a cap and a lantern
			int[][] corners = {{ru0, rv0, 1, 1}, {ru1, rv0, -1, 1}, {ru0, rv1, 1, -1}, {ru1, rv1, -1, -1}};
			for (int ki = 0; ki < corners.length; ki++) {
				if ((turretMode == 1 && ki < 2) || (turretMode == 2 && (ki == 1 || ki == 2))) {
					continue;
				}
				int[] k = corners[ki];
				for (int du = 0; du <= 1; du++) {
					for (int dv = 0; dv <= 1; dv++) {
						int u = k[0] + du * k[2];
						int v = k[1] + dv * k[3];
						for (int y = 16; y <= 18; y++) {
							set(u, y, v, y == 16 ? b(Blocks.DEEPSLATE_BRICKS) : y == 17 && du == 0 && dv == 0 ? glass() : b(Blocks.STONE_BRICKS));
						}
						set(u, 19, v, slab(ROOF_SLAB[0])); // slate caps on every seed: brick or mud caps read as wooden crates
					}
				}
				if (p.has(k[0], 20, k[1])) {
					set(k[0], 20, k[1], StoneKit.lantern(false));
				}
			}
			// planters and a bench on the terrace
			for (int u : new int[] {cu0, cu1}) {
				for (int v : new int[] {cv0, cv1}) {
					if (free(u, 16, v) && free(u, 17, v) && !isTurretCell(u, v)) {
						set(u, DECK, v, b(Blocks.MOSS_BLOCK));
						set(u, 16, v, p.hash(u, 16, v, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
					}
				}
			}
			int bu = annexRight ? cu1 - 1 : cu0 + 1;
			for (int v = cv0 + 4; v <= cv0 + 6 && v < cv1 - 1; v++) {
				setFree(bu, 16, v, stairs(Blocks.CHERRY_STAIRS, annexRight ? Direction.EAST : Direction.WEST));
			}
			set(cc, DECK, cv1 - 1, b(Blocks.CHISELED_STONE_BRICKS));
			set(cc, 16, cv1 - 1, StoneKit.lantern(false));
		}

		/**
		 * A truncated hip roof one block outside the walls: four rings of stairs at 45 degrees (cherry on the eave,
		 * deepslate tile above) round a flat tile top with a cherry cap and a lightning rod.
		 */
		boolean isTurretCell(int u, int v) {
			return (u <= cu0 || u >= cu1) && (v <= cv0 || v >= cv1);
		}

		void hipRoof() {
			int ru0 = cu0 - 1;
			int ru1 = cu1 + 1;
			int rv0 = cv0 - 1;
			int rv1 = cv1 + 1;
			int cx = 0;
			int cz = 0;
			int cnt = 0;
			for (int u = ru0; u <= ru1; u++) {
				for (int v = rv0; v <= rv1; v++) {
					int k = Math.min(Math.min(u - ru0, ru1 - u), Math.min(v - rv0, rv1 - v));
					int y = DECK + Math.min(k, rise);
					for (int fy = DECK + 1; fy < y; fy++) {
						set(u, fy, v, b(ROOF_FILL[roofKind]));
					}
					if (k == 0) {
						// the eave is its own colour (cherry, dark brick or slate), a step apart from the pale cornice below
						set(u, y, v, eave(EAVE_STAIR[roofKind], u, v, ru0, ru1, rv0, rv1));
					} else if (k <= rise) {
						set(u, y, v, eave(ROOF_STAIR[roofKind], u, v, ru0 + k, ru1 - k, rv0 + k, rv1 - k));
					} else {
						set(u, y, v, roofKind == 0 && p.hash(u, k, v, 6) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(ROOF_FILL[roofKind]));
						cx += u;
						cz += v;
						cnt++;
					}
				}
			}
			if (cnt > 0) {
				int mu = cx / cnt;
				int mv = cz / cnt;
				for (int ry = DECK + rise + 1; ry <= DECK + rise + 2; ry++) {
					if (p.has(mu, ry, mv)) {
						set(mu, ry, mv, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
					}
				}
			}
		}

		BlockState eave(Block stair, int u, int v, int ru0, int ru1, int rv0, int rv1) {
			boolean n = v == rv0;
			boolean s = v == rv1;
			boolean wst = u == ru0;
			boolean e = u == ru1;
			if ((n || s) && (wst || e)) {
				Direction high = n ? Direction.SOUTH : Direction.NORTH;
				boolean leftOfHigh = n ? wst : e; // looking along 'high', the corner's high quarter is on the left or the right
				return stairs(stair, high).setValue(BlockStateProperties.STAIRS_SHAPE,
						leftOfHigh ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT);
			}
			Direction high = n ? Direction.SOUTH : s ? Direction.NORTH : wst ? Direction.EAST : Direction.WEST;
			return stairs(stair, high);
		}

		/** A cherry balcony off the study, over the porch: a floor of planks on stair brackets with a rail. */
		void balcony() {
			Wall f = new Wall(c, p, cv0, Direction.NORTH);
			archDoor(f, cc, F2 + 1, Blocks.CHERRY_DOOR);
			Set<Long> rail = new HashSet<>();
			for (int u = cc - 1; u <= cc + 1; u++) {
				for (int v = 0; v <= 1; v++) {
					set(u, F2, v, b(Blocks.STONE_BRICKS));
					set(u, F2 - 1, v, stUp(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
					if (v == 0 || u != cc) {
						rail.add(key(u, v));
					}
				}
			}
			rail.remove(key(cc, 1));
			for (long k : rail) {
				int u = (int) (k >> 16);
				int v = (int) (k & 0xFFFF);
				set(u, F2 + 1, v, Blocks.CHERRY_FENCE.defaultBlockState()
						.setValue(BlockStateProperties.NORTH, rail.contains(key(u, v - 1)))
						.setValue(BlockStateProperties.EAST, rail.contains(key(u + 1, v)))
						.setValue(BlockStateProperties.SOUTH, rail.contains(key(u, v + 1)) || v == 1)
						.setValue(BlockStateProperties.WEST, rail.contains(key(u - 1, v))));
			}
			if (balconyMode == 1) {
				// a cherry canopy on two posts over the balcony (top slabs, 3 clear blocks under them)
				for (int u : new int[] {cc - 1, cc + 1}) {
					set(u, F2 + 2, 0, Blocks.CHERRY_FENCE.defaultBlockState());
					set(u, F2 + 3, 0, Blocks.CHERRY_FENCE.defaultBlockState());
				}
				for (int u = cc - 1; u <= cc + 1; u++) {
					for (int v = 0; v <= 1; v++) {
						set(u, F2 + 4, v, topSlab(Blocks.CHERRY_SLAB));
					}
				}
			}
		}

		/** The wing's roof terrace: a cherry pergola at the outer edge, a bench against the tower, planters. */
		void wingTerrace() {
			int in = annexRight ? -1 : 1; // from the outer wall towards the tower
			int pu1 = outer + in; // first cell inside the parapet
			int pu2 = pu1 + 2 * in; // pergola is three cells wide
			int vA = av0 + 2;
			int vB = av1 - 2;
			if (vB - vA < 2) {
				return;
			}
			int lo = Math.min(pu1, pu2);
			int hi = Math.max(pu1, pu2);
			for (int u = lo; u <= hi; u++) {
				for (int v = vA; v <= vB; v++) {
					set(u, F1 + 4, v, topSlab(Blocks.CHERRY_SLAB));
				}
			}
			for (int u : new int[] {pu1, pu2}) {
				for (int v : new int[] {vA, vB}) {
					for (int y = F1 + 1; y <= F1 + 3; y++) {
						set(u, y, v, Blocks.CHERRY_FENCE.defaultBlockState());
					}
				}
			}
			int tu = annexRight ? shared + 1 : shared - 1;
			Direction back = annexRight ? Direction.WEST : Direction.EAST;
			for (int v = vA + 1; v <= vB - 1; v++) {
				set(tu, F1 + 1, v, stairs(Blocks.CHERRY_STAIRS, back));
			}
			int pv = av1 - 1;
			if (tu != pu2 + in && free(tu, F1 + 1, pv) && Math.abs(tu - pu2) > 1) {
				set(tu, F1, pv, b(Blocks.MOSS_BLOCK));
				set(tu, F1 + 1, pv, b(Blocks.FLOWERING_AZALEA));
			}
		}

		// ------------------------------------------------------------------ furniture

		BlockState bedPart(Direction head, boolean isHead) {
			return b(Blocks.BED.pick(DyeColor.RED)).setValue(BlockStateProperties.HORIZONTAL_FACING, head)
					.setValue(BlockStateProperties.BED_PART, isHead ? BedPart.HEAD : BedPart.FOOT);
		}

		boolean isA(int u) {
			return u == aCols[0] || u == aCols[1];
		}

		boolean isB(int u) {
			return u == bCols[0] || u == bCols[1];
		}

		void furnish() {
			workshop();
			living();
			study();
			store();
		}

		/** Ground floor: crafting, forging and storage along the back and the wing wall; the middle and the front stay clear. */
		void workshop() {
			Block[] bench = {Blocks.CRAFTING_TABLE, Blocks.SMITHING_TABLE, Blocks.ANVIL, Blocks.GRINDSTONE, Blocks.BLAST_FURNACE, Blocks.FURNACE, Blocks.STONECUTTER};
			int idx = 0;
			for (int u = cu0 + 1; u <= cu1 - 1; u++) {
				if (u == cc && tw >= 11) {
					continue;
				}
				Block blk = bench[idx++ % bench.length];
				BlockState st;
				if (blk == Blocks.ANVIL || blk == Blocks.STONECUTTER) {
					st = facing(blk, blk == Blocks.ANVIL ? Direction.EAST : Direction.NORTH);
				} else if (blk == Blocks.BLAST_FURNACE || blk == Blocks.FURNACE) {
					st = facing(blk, Direction.NORTH).setValue(BlockStateProperties.LIT, true);
				} else if (blk == Blocks.GRINDSTONE) {
					st = b(blk).setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
				} else {
					st = b(blk);
				}
				if (u == cc && tw < 11) {
					st = StoneKit.barrel();
				}
				set(u, 1, ivN, st);
			}
			// along the wing's wall: barrels and chests, leaving the doorway clear
			int wu = annexRight ? cu1 - 1 : cu0 + 1;
			Direction chestFace = annexRight ? Direction.WEST : Direction.EAST;
			for (int v = iv0 + 3; v <= ivN - 1; v++) {
				if (Math.abs(v - doorV) <= 1) {
					continue;
				}
				set(wu, 1, v, (v + 1) % 2 == 0 ? StoneKit.barrel() : facing(Blocks.CHEST, chestFace));
			}
			// beside the flight, on the stair wall at the back
			int au = annexRight ? cu0 + 1 : cu1 - 1;
			for (int v = iv0 + 6; v <= ivN - 1; v++) {
				set(au, 1, v, StoneKit.barrel());
			}
			for (int v : new int[] {iv0 + 3, ivN - 2}) {
				p.put(c, cc, 4, v, StoneKit.lantern(true), CitadelCanvas.HANGING);
			}
		}

		/** Living floor: a bed and a chest against the front wall, a kitchen corner, a rug. */
		void living() {
			int y = F1 + 1;
			int toB = annexRight ? 1 : -1;
			Direction head = toB > 0 ? Direction.EAST : Direction.WEST;
			set(cc, y, iv0, bedPart(head, false));
			set(cc + toB, y, iv0, bedPart(head, true));
			int chestU = cc + 2 * toB;
			if (chestU >= cu0 + 3 && chestU <= cu1 - 3) {
				set(chestU, y, iv0, facing(Blocks.CHEST, Direction.SOUTH));
			}
			// kitchen in the corner by the wing: furnace and smoker against the front wall, barrel above the door side
			int bwall = annexRight ? cu1 - 1 : cu0 + 1;
			int bin = annexRight ? cu1 - 2 : cu0 + 2;
			set(bin, y, iv0, facing(Blocks.FURNACE, Direction.SOUTH).setValue(BlockStateProperties.LIT, true));
			set(bwall, y, iv0, facing(Blocks.SMOKER, Direction.SOUTH).setValue(BlockStateProperties.LIT, true));
			// rug, away from the rails and the aisle to flight 2
			int mid = (iv0 + ivN) / 2;
			for (int u = cc - 1; u <= cc + 1; u++) {
				for (int v = mid - 1; v <= mid; v++) {
					if (u != aInner) {
						set(u, y, v, b(Blocks.CARPET.pick(DyeColor.PINK)));
					}
				}
			}
			p.put(c, cc, 9, mid + 2, StoneKit.lantern(true), CitadelCanvas.HANGING);
			// shelves on the stair wall behind the landing, only when the tower is deep enough to leave it free
			int au = annexRight ? cu0 + 1 : cu1 - 1;
			for (int v = iv0 + 8; v <= ivN - 1; v++) {
				for (int yy = y; yy <= y + 3; yy++) {
					if (!(sideWin.contains(v) && yy >= y + 1)) {
						set(au, yy, v, b(Blocks.BOOKSHELF));
					}
				}
			}
		}

		/** Study: shelves up to the ceiling along the back wall and the stair wall, a desk, a lectern and an enchanting table. */
		void study() {
			int y = F2 + 1;
			for (int u = cu0 + 1; u <= cu1 - 1; u++) {
				if (isB(u) || u == bInner) {
					continue;
				}
				for (int yy = y; yy <= y + 3; yy++) {
					if (backWin.contains(u) && yy >= y + 1) {
						continue;
					}
					BlockState sh = yy == y + 1 && p.hash(u, yy, ivN, 3) == 0 ? facing(Blocks.CHISELED_BOOKSHELF, Direction.NORTH) : b(Blocks.BOOKSHELF);
					set(u, yy, ivN, sh);
				}
				if (backWin.contains(u)) {
					set(u, y, ivN, facing(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
				}
			}
			int au = annexRight ? cu0 + 1 : cu1 - 1;
			int from = battle ? iv0 + 6 : iv0 + 2;
			for (int v = from; v <= ivN - 1; v++) {
				for (int yy = y; yy <= y + 3; yy++) {
					if (sideWin.contains(v) && yy >= y + 1) {
						continue;
					}
					set(au, yy, v, b(Blocks.BOOKSHELF));
				}
			}
			int lec = annexRight ? cc - 1 : cc + 1;
			set(cc, y, ivN - 1, b(Blocks.CARTOGRAPHY_TABLE));
			set(lec, y, ivN - 1, facing(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
			if (tw >= 11) {
				set(annexRight ? cc - 2 : cc + 2, y, ivN - 1, b(Blocks.ENCHANTING_TABLE));
			}
			int mid = (iv0 + ivN) / 2;
			for (int u = cc - 1; u <= cc + 1; u++) {
				for (int v = mid - 1; v <= mid; v++) {
					if (u != bInner && u != aInner) {
						set(u, y, v, b(Blocks.CARPET.pick(DyeColor.RED)));
					}
				}
			}
			p.put(c, cc, 14, mid, StoneKit.lantern(true), CitadelCanvas.HANGING);
		}

		/** The wing's ground floor: a store with barrels and chests along the back and the outer wall. */
		void store() {
			int lo = Math.min(shared, outer) + 1;
			int hi = Math.max(shared, outer) - 1;
			int outerIn = annexRight ? outer - 1 : outer + 1;
			Direction face = annexRight ? Direction.WEST : Direction.EAST;
			for (int u = lo; u <= hi; u++) {
				set(u, 1, av1 - 1, (u + av1) % 3 == 0 ? facing(Blocks.CHEST, Direction.NORTH) : StoneKit.barrel());
			}
			for (int v = av0 + 3; v <= av1 - 2; v++) {
				set(outerIn, 1, v, v % 2 == 0 ? StoneKit.barrel() : facing(Blocks.CHEST, face));
			}
			set(outerIn, 1, av0 + 1, b(Blocks.CRAFTING_TABLE));
			p.put(c, ac, 4, (av0 + av1) / 2, StoneKit.lantern(true), CitadelCanvas.HANGING);
		}

		// ------------------------------------------------------------------ yard

		void exterior() {
			// a low wall along the street with gaps at both doors, a post and a lantern at each end of every gap
			int[] type = new int[w];
			for (int u = 0; u < w; u++) {
				type[u] = 1;
			}
			for (int g : new int[] {cc, ac}) {
				for (int u = g - 1; u <= g + 1; u++) {
					if (u >= 0 && u < w) {
						type[u] = 0;
					}
				}
				for (int u : new int[] {g - 2, g + 2}) {
					if (u >= 0 && u < w) {
						type[u] = 2;
					}
				}
			}
			for (int u = 0; u < w; u++) {
				if (type[u] == 2) {
					set(u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
					set(u, 1, 0, b(Blocks.CHISELED_DEEPSLATE));
					set(u, 2, 0, StoneKit.lantern(false));
				} else if (type[u] == 1) {
					boolean e = u + 1 < w && type[u + 1] != 0;
					boolean wst = u > 0 && type[u - 1] != 0;
					StoneKit.lowWall(c, p, u, 0, 0, false, e, false, wst);
				}
			}
			// beds between the wall and the facade, a clear way from each gap to its step
			for (int u = 0; u < w; u++) {
				if (type[u] != 1) {
					continue;
				}
				if (u >= cu0 && u <= cu1) {
					int off = Math.abs(u - cc);
					if ((off == 3 || off == 4) && free(u, 0, 1) && free(u, 1, 1)) {
						set(u, 0, 1, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // a bench by the door, facing the street
					} else {
						bed(u, 1);
					}
				} else if (onAnnexCols(u)) {
					bed(u, 1);
					bed(u, cv0);
				}
			}
			patio();
			garden();
		}

		/** The lawns behind the tower and behind the wing: a hedge on the far edge, a bench in front of it, lamps, flowers. */
		void garden() {
			int tu0 = annexRight ? 0 : cu0 - 1;
			int tu1 = annexRight ? cu1 + 1 : w - 1;
			yard(tu0, tu1, cv1 + 2, d - 1);
			if (annexRight) {
				yard(shared + 2, w - 1, av1 + 1, d - 1);
			} else {
				yard(0, shared - 2, av1 + 1, d - 1);
			}
		}

		void yard(int u0, int u1, int v0, int v1) {
			int yw = u1 - u0 + 1;
			int yd = v1 - v0 + 1;
			if (yw < 3 || yd < 1) {
				return;
			}
			int uc = (u0 + u1) / 2;
			Set<Long> used = new HashSet<>();
			if (yd >= 2) {
				for (int u = u0; u <= u1; u++) {
					if ((u - u0) % 2 == 0 && free(u, 0, v1) && free(u, 1, v1)) {
						bed(u, v1); // a low hedge of moss, azalea and ferns along the far edge
					}
					used.add(key(u, v1));
				}
				if (yw >= 5) {
					int vb = v1 - 1;
					for (int u = uc - 1; u <= uc + 1; u++) {
						if (free(u, 0, vb) && free(u, 1, vb)) {
							set(u, 0, vb, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // a bench facing the house, back to the hedge
						}
						used.add(key(u, vb));
					}
					for (int du : new int[] {-3, 3}) {
						int u = uc + du;
						if (u > u0 && u < u1 && free(u, 0, vb) && free(u, 1, vb) && free(u, 2, vb)) {
							set(u, 0, vb, b(Blocks.POLISHED_DEEPSLATE));
							set(u, 1, vb, b(Blocks.CHISELED_DEEPSLATE));
							set(u, 2, vb, StoneKit.lantern(false));
						}
						used.add(key(u, vb));
					}
				}
			}
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (used.contains(key(u, v)) || !free(u, 0, v) || !free(u, 1, v)) {
						continue;
					}
					if (p.hash(u, 0, v, 4) == 0) {
						set(u, 0, v, b(FLOWERS[p.hash(u, 1, v, FLOWERS.length)]));
					}
				}
			}
		}

		boolean onAnnexCols(int u) {
			int lo = Math.min(shared, outer);
			int hi = Math.max(shared, outer);
			return u >= lo + (annexRight ? 1 : 0) && u <= hi - (annexRight ? 0 : 1);
		}

		void bed(int u, int v) {
			if (!free(u, 0, v) || !free(u, 1, v)) {
				return;
			}
			set(u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK));
			int k = p.hash(u, 1, v, 4);
			set(u, 1, v, k == 0 ? b(Blocks.FLOWERING_AZALEA) : k == 1 ? b(Blocks.FERN) : k == 2 ? b(Blocks.AZALEA) : b(Blocks.POTTED_FERN));
		}

		/** What is left of the plot beyond the wing: a walled patio with a well and flower beds. */
		void patio() {
			int from = annexRight ? outer + 1 : 0;
			int to = annexRight ? w - 1 : outer - 1;
			int pw = to - from + 1;
			if (pw < 3) {
				return;
			}
			int pv0 = 3;
			int pv1 = cv1;
			// beds along the edges, a wall on the outer side
			for (int u = from; u <= to; u++) {
				for (int v = pv0; v <= pv1; v++) {
					boolean edge = u == from || u == to || v == pv0 || v == pv1;
					if (edge && (u + v) % 2 == 0) {
						bed(u, v);
					}
				}
			}
			if (pw >= 5 && pv1 - pv0 >= 5) {
				int wu = from + (pw - 3) / 2;
				StoneKit.well(c, p, wu, (pv0 + pv1) / 2 - 1);
			}
		}
	}
}
