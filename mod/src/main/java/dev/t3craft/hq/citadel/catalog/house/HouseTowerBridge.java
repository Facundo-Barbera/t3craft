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
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Two towers of different height joined at the middle floor by a gallery you walk across, with a stone
 * passage open underneath. Everything is walkable end to end and every room is four blocks clear.
 *
 * <p><b>The tall tower</b> (three storeys) has a 2-wide masonry flight from the workshop to the dwelling (first step
 * head-on, the floor cut over the run, a rail round the cut, a landing at the top) and an L-shaped flight with a landing
 * on to the mirador. <b>The low tower</b> (two storeys: study, loft) has an L-shaped timber-and-stone flight with a landing.
 * Both stairs sit on the side away from the bridge, so the gallery door opens on free floor.
 *
 * <p><b>The gallery</b> is five wide (three clear) and four high, with a window or an arcade every bay, and one of three
 * types by seed: a timber gallery with glass and a gable roof, a stone gallery with glass and a roof terrace (parapet,
 * lamps, azaleas), or an open arcade with railings under a gable. It rests on log beams along both edges that run from
 * tower to tower, on stone corbels at both ends and, on a long span, on a pier with its own corbels, so it never floats.
 *
 * <p>Palette, dark to light: deepslate bricks (cracked / cobbled / polished mix), stone bricks (mossy / cracked),
 * calcite plaster, framed in cherry or dark oak, under deepslate-tile or cherry roofs. Variation by seed: which tower is
 * on the left, the gallery type, the colourway, the height of the low tower's roof and of the tall tower's spire.
 *
 * <p>The towers grow with the plot (interior 7, 9 or 11 for the tall one), the building is centred and the rest of the plot
 * stays free for the leader's yard.
 *
 * <pre>
 *  u: margin | tall tower (n+2) | gap (3..11, the gallery) | low tower (n)
 *  v: 0-1 porch | 2.. tall tower (n+2 deep) | the low tower is one cell back so both centre lines meet
 *  y: 0 floor | 1-4 storey 1 | 5 floor | 6-9 storey 2 (gallery floor 5, walk 6) | 10 floor | 11-14 storey 3 | 15 plate | roof
 * </pre>
 *
 * Size: 21–30 × 13–22 × 21+ (the towers use what the plot offers: n = 7, 9 or 11).
 */
public final class HouseTowerBridge implements Variant {
	private static final int VF = 2; // front wall row of the tall tower

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
		return 21;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 21;
	}

	@Override
	public int maxW() {
		return 30;
	}

	@Override
	public int maxD() {
		return 22;
	}

	private record Scheme(Block log, Block trim, Block planks, Block fence, Block door, Block roof, Block roofStair, Block roofSlab, Block eaveStair) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR,
					Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.CHERRY_STAIRS),
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR,
					Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.DEEPSLATE_TILE_STAIRS),
	};

	/**
	 * One tower: wall rectangle {@code u0..u1 × v0..v1}, interior {@code n × n} (logical cells {@code a, b}: {@code a} runs
	 * from the side away from the bridge to the bridge, {@code b} from the front to the back).
	 */
	private record Tw(int u0, int u1, int v0, int v1, int n, boolean flip, int storeys) {
		int size() {
			return n + 2;
		}

		int cu() {
			return (u0 + u1) / 2;
		}

		int cv() {
			return (v0 + v1) / 2;
		}

		/** The actual u of logical column {@code a}. */
		int iu(int a) {
			return flip ? u1 - 1 - a : u0 + 1 + a;
		}

		int iv(int bb) {
			return v0 + 1 + bb;
		}

		/** The local direction towards the bridge. */
		Direction east() {
			return flip ? Direction.WEST : Direction.EAST;
		}

		Direction west() {
			return east().getOpposite();
		}

		int plate() {
			return 5 * storeys;
		}
	}

	private record L(Plot p, Scheme s, Tw a, Tw b, int gu0, int gu1, int vc, int bridge, int hmode) {
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xB41D_6E01L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		boolean aLeft = r.nextBoolean();
		int bridge = r.nextInt(3);
		int hmode = r.nextInt(4);
		int w = p.w();
		int d = p.d();
		int n = 7;
		if (w >= 29 && d >= 16) {
			n = 11;
		} else if (w >= 25 && d >= 14) {
			n = 9;
		}
		int nb = n - 2;
		int ta = n + 2;
		int tb = nb + 2;
		int g = Math.max(3, Math.min(11, w - ta - tb - 4));
		int total = ta + g + tb;
		int m = Math.max(0, (w - total) / 2);
		Tw a;
		Tw bt;
		int gu0;
		if (aLeft) {
			a = new Tw(m, m + ta - 1, VF, VF + ta - 1, n, false, 3);
			gu0 = m + ta;
			bt = new Tw(gu0 + g, gu0 + g + tb - 1, VF + 1, VF + tb, nb, true, 2);
		} else {
			bt = new Tw(m, m + tb - 1, VF + 1, VF + tb, nb, false, 2);
			gu0 = m + tb;
			a = new Tw(gu0 + g, gu0 + g + ta - 1, VF, VF + ta - 1, n, true, 3);
		}
		int vc = a.v0() + 1 + (n - 1) / 2;
		L l = new L(p, s, a, bt, gu0, gu0 + g - 1, vc, bridge, hmode);
		tower(c, l, l.a);
		tower(c, l, l.b);
		stairs(c, l);
		furnish(c, l);
		hip(c, l, l.a, (hmode & 2) != 0 ? 3 : 1, 0);
		hip(c, l, l.b, 1, (hmode & 1) != 0 ? 1 : 0);
		gallery(c, l);
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

	private static BlockState facing(Block block, Direction dir) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	private static BlockState bed(Direction head, boolean isHead) {
		return b(Blocks.BED.pick(DyeColor.PINK)).setValue(BlockStateProperties.HORIZONTAL_FACING, head)
				.setValue(BlockStateProperties.BED_PART, isHead ? BedPart.HEAD : BedPart.FOOT);
	}

	private static BlockState air() {
		return b(Blocks.AIR);
	}

	private static int base(int storey) {
		return 1 + 5 * (storey - 1);
	}

	// ------------------------------------------------------------------ towers

	private static boolean perim(Tw t, int u, int v) {
		return u == t.u0() || u == t.u1() || v == t.v0() || v == t.v1();
	}

	private static boolean corner(Tw t, int u, int v) {
		return (u == t.u0() || u == t.u1()) && (v == t.v0() || v == t.v1());
	}

	/** The outward direction of a wall cell. */
	private static Direction out(Tw t, int u, int v) {
		if (v == t.v0()) {
			return Direction.NORTH;
		}
		if (v == t.v1()) {
			return Direction.SOUTH;
		}
		return u == t.u0() ? Direction.WEST : Direction.EAST;
	}

	/** Offset from the middle of the wall the cell is in. */
	private static int dOf(Tw t, int u, int v, Direction out) {
		return out.getAxis() == Direction.Axis.Z ? u - t.cu() : v - t.cv();
	}

	private static boolean window(Tw t, int s, Direction out, int d, int dy) {
		int ad = Math.abs(d);
		if (ad < 2 || ad % 2 != 0) {
			return false;
		}
		if (s == 2 && out == t.east()) {
			return false; // the gallery door and its posts
		}
		return s == 3 ? dy >= 1 && dy <= 3 : dy >= 1 && dy <= 2;
	}

	private BlockState wallBlock(L l, Tw t, int s, int y, int u, int v) {
		Plot p = l.p;
		int dy = y - base(s);
		if (corner(t, u, v)) {
			return log(l.s.log(), Direction.Axis.Y);
		}
		Direction o = out(t, u, v);
		int d = dOf(t, u, v, o);
		if (s == 1 && o == Direction.NORTH && d == 0) {
			if (dy <= 1) {
				return doorBlock(l, Direction.NORTH, dy == 0);
			}
			if (dy == 2) {
				return log(l.s.log(), Direction.Axis.X);
			}
		}
		if (s == 2 && o == t.east() && Math.abs(d) <= 1) {
			if (Math.abs(d) == 1 && dy == 3) {
				return stair(Blocks.STONE_BRICK_STAIRS, v < l.vc() ? Direction.NORTH : Direction.SOUTH, true);
			}
			return air();
		}
		if (window(t, s, o, d, dy)) {
			return pane(p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, o.getAxis() == Direction.Axis.Z);
		}
		if (dy == 3 && s < 3) {
			return s == 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.CHISELED_STONE_BRICKS);
		}
		return switch (s) {
			case 1 -> deepslate(p, u, y, v);
			case 2 -> stone(p, u, y, v);
			default -> plaster(p, u, y, v);
		};
	}

	private void tower(CitadelCanvas c, L l, Tw t) {
		Plot p = l.p;
		// plinth
		for (int u = t.u0(); u <= t.u1(); u++) {
			for (int v = t.v0(); v <= t.v1(); v++) {
				boolean edge = perim(t, u, v);
				set(c, p, u, 0, v, edge ? deepslate(p, u, 0, v) : p.hash(u, 0, v, 3) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		for (int s = 1; s <= t.storeys(); s++) {
			int bs = base(s);
			for (int y = bs; y <= bs + 3; y++) {
				for (int u = t.u0(); u <= t.u1(); u++) {
					for (int v = t.v0(); v <= t.v1(); v++) {
						if (perim(t, u, v)) {
							set(c, p, u, y, v, wallBlock(l, t, s, y, u, v));
						}
					}
				}
			}
			// windows: a sill outside under each, from the second storey up
			for (int u = t.u0(); u <= t.u1(); u++) {
				for (int v = t.v0(); v <= t.v1(); v++) {
					if (!perim(t, u, v) || corner(t, u, v)) {
						continue;
					}
					Direction o = out(t, u, v);
					if (s >= 2 && window(t, s, o, dOf(t, u, v, o), 1)) {
						set(c, p, u + o.getStepX(), bs, v + o.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, o.getOpposite(), true));
					}
				}
			}
			// floor (or the plate) above this storey
			int fy = bs + 4;
			for (int u = t.u0(); u <= t.u1(); u++) {
				for (int v = t.v0(); v <= t.v1(); v++) {
					if (corner(t, u, v)) {
						set(c, p, u, fy, v, log(l.s.log(), Direction.Axis.Y));
					} else if (perim(t, u, v)) {
						set(c, p, u, fy, v, log(l.s.log(), (v == t.v0() || v == t.v1()) ? Direction.Axis.X : Direction.Axis.Z));
					} else {
						set(c, p, u, fy, v, p.hash(u, fy, v, 3) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS));
					}
				}
			}
			if (s < t.storeys()) {
				cornice(c, l, t, bs + 3);
			}
			pilasters(c, l, t, s);
		}
		porch(c, l, t);
	}

	/** [depth] a dark cornice of upside-down stairs round the foot of the storey above (not where the gallery meets the wall). */
	private void cornice(CitadelCanvas c, L l, Tw t, int y) {
		Plot p = l.p;
		for (int u = t.u0(); u <= t.u1(); u++) {
			set(c, p, u, y, t.v0() - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, true));
			set(c, p, u, y, t.v1() + 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, true));
		}
		for (int v = t.v0(); v <= t.v1(); v++) {
			boolean bridgeEast = t.east() == Direction.EAST;
			boolean gal = Math.abs(v - l.vc()) <= 2;
			if (!(gal && !bridgeEast)) {
				set(c, p, t.u0() - 1, y, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, true));
			}
			if (!(gal && bridgeEast)) {
				set(c, p, t.u1() + 1, y, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, true));
			}
		}
	}

	/** [depth] a stripped-log pilaster standing out of the middle of each wall, storey by storey. */
	private void pilasters(CitadelCanvas c, L l, Tw t, int s) {
		Plot p = l.p;
		int bs = base(s);
		for (int y = bs; y <= bs + 3; y++) {
			BlockState lg = log(l.s.trim(), Direction.Axis.Y);
			if (s >= 2) { // the porch and the passage stay clear below
				set(c, p, t.cu(), y, t.v0() - 1, lg);
			}
			set(c, p, t.cu(), y, t.v1() + 1, lg);
			boolean eastBridge = t.east() == Direction.EAST;
			if (!(eastBridge && s <= 2)) {
				set(c, p, t.u1() + 1, y, t.cv(), lg);
			}
			if (!(!eastBridge && s <= 2)) {
				set(c, p, t.u0() - 1, y, t.cv(), lg);
			}
		}
	}

	private void porch(CitadelCanvas c, L l, Tw t) {
		Plot p = l.p;
		int cu = t.cu();
		int vd = t.v0() - 1;
		set(c, p, cu, 0, vd, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		for (int side = -1; side <= 1; side += 2) {
			set(c, p, cu + side, 0, vd, b(Blocks.MOSS_BLOCK));
			set(c, p, cu + side, 1, vd, side < 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			set(c, p, cu + side * 2, 0, vd, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
			put(c, p, cu + side, 3, vd, hangingLantern(), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ stairs

	/** One cell of a flight: logical (a, bb), the layer of the block, and what it is (see {@link #dirOf}). */
	private record Step(int a, int bb, int y, int f) {
	}

	/** 0 landing, 1 north, 2 south, 3 towards the bridge, 4 away from it. */
	private static Direction dirOf(Tw t, int f) {
		return switch (f) {
			case 1 -> Direction.NORTH;
			case 2 -> Direction.SOUTH;
			case 3 -> t.east();
			default -> t.west();
		};
	}

	private static int key(int u, int v) {
		return u << 10 | v;
	}

	private static boolean inside(Tw t, int u, int v) {
		return u > t.u0() && u < t.u1() && v > t.v0() && v < t.v1();
	}

	/**
	 * Builds a flight between two floors: stairs (one per cell, one block up each) and landings on a solid core, the floor
	 * above cut over every cell the head would meet it, and a fence round the cut on the upper floor except at the cells
	 * walked off onto.
	 */
	private static void climb(CitadelCanvas c, Plot p, Tw t, int lower, int upper, Block stairBlock, BlockState core, Block fenceBlock, Step[] steps,
			int[][] exits) {
		Set<Integer> holes = new HashSet<>();
		Set<Integer> cells = new HashSet<>();
		for (Step st : steps) {
			int u = t.iu(st.a());
			int v = t.iv(st.bb());
			for (int y = lower; y < st.y(); y++) {
				set(c, p, u, y, v, core);
			}
			set(c, p, u, st.y(), v, st.f() == 0 ? core : stair(stairBlock, dirOf(t, st.f()), false));
			cells.add(key(u, v));
			if (st.y() < upper - 1) {
				set(c, p, u, upper - 1, v, air());
				holes.add(key(u, v));
			}
		}
		for (int[] e : exits) {
			cells.add(key(t.iu(e[0]), t.iv(e[1])));
		}
		Set<Integer> rail = new HashSet<>();
		int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int hk : holes) {
			int hu = hk >> 10;
			int hv = hk & 1023;
			for (int[] dd : dirs) {
				int nu = hu + dd[0];
				int nv = hv + dd[1];
				int nk = key(nu, nv);
				if (inside(t, nu, nv) && !holes.contains(nk) && !cells.contains(nk)) {
					rail.add(nk);
				}
			}
		}
		for (int rk : rail) {
			int ru = rk >> 10;
			int rv = rk & 1023;
			set(c, p, ru, upper, rv, fence(fenceBlock, rail.contains(key(ru, rv - 1)), rail.contains(key(ru + 1, rv)), rail.contains(key(ru, rv + 1)),
					rail.contains(key(ru - 1, rv))));
		}
	}

	private void stairs(CitadelCanvas c, L l) {
		Plot p = l.p;
		Tw a = l.a;
		BlockState stoneCore = b(Blocks.STONE_BRICKS);
		// tall tower, flight 1 (2 wide, straight, on the west columns): ground floor to the dwelling. Approach (a 0..1, b 0).
		Step[] f1 = new Step[10];
		for (int j = 0; j < 5; j++) {
			f1[2 * j] = new Step(0, 1 + j, 1 + j, 2);
			f1[2 * j + 1] = new Step(1, 1 + j, 1 + j, 2);
		}
		climb(c, p, a, 1, 6, Blocks.STONE_BRICK_STAIRS, stoneCore, Blocks.DARK_OAK_FENCE, f1, new int[][] {{0, 6}, {1, 6}});
		// flight 2 (an L with a landing): dwelling to the mirador, starting at the exit of flight 1
		Step[] f2 = {new Step(2, 6, 6, 3), new Step(3, 6, 7, 3), new Step(4, 6, 8, 3), new Step(5, 6, 8, 0), new Step(5, 5, 9, 1), new Step(5, 4, 10, 1)};
		climb(c, p, a, 6, 11, Blocks.STONE_BRICK_STAIRS, stoneCore, Blocks.DARK_OAK_FENCE, f2, new int[][] {{5, 3}});
		// low tower: an L with a landing, the loft above the study
		Tw bt = l.b;
		Step[] f3 = {new Step(0, 1, 1, 2), new Step(0, 2, 2, 2), new Step(0, 3, 3, 2), new Step(0, 4, 3, 0), new Step(1, 4, 4, 3), new Step(2, 4, 5, 3)};
		climb(c, p, bt, 1, 6, Blocks.SPRUCE_STAIRS, b(Blocks.SPRUCE_PLANKS), l.s.fence(), f3, new int[][] {{3, 4}});
	}

	// ------------------------------------------------------------------ furniture

	private static void fi(CitadelCanvas c, Plot p, Tw t, int a, int bb, int y, BlockState s) {
		set(c, p, t.iu(a), y, t.iv(bb), s);
	}

	private static void hang(CitadelCanvas c, Plot p, Tw t, int a, int bb, int y) {
		put(c, p, t.iu(a), y, t.iv(bb), hangingLantern(), CitadelCanvas.HANGING);
	}

	private void furnish(CitadelCanvas c, L l) {
		Plot p = l.p;
		Tw a = l.a;
		int e = a.n() - 1;
		int bk = a.n() - 1;
		Direction west = a.west();
		Direction east = a.east();
		// ---- tall tower, ground floor: the workshop (workbenches on the back wall, stores along the bridge side)
		fi(c, p, a, 2, bk, 1, barrel());
		fi(c, p, a, 3, bk, 1, b(Blocks.CRAFTING_TABLE));
		fi(c, p, a, 4, bk, 1, facing(Blocks.FURNACE, Direction.NORTH));
		fi(c, p, a, 5, bk, 1, b(Blocks.SMITHING_TABLE));
		fi(c, p, a, e, bk, 1, facing(Blocks.ANVIL, Direction.NORTH));
		fi(c, p, a, e, 1, 1, barrel());
		fi(c, p, a, e, 2, 1, barrel());
		fi(c, p, a, e, 3, 1, facing(Blocks.CHEST, west));
		fi(c, p, a, e, 4, 1, facing(Blocks.LOOM, west));
		fi(c, p, a, e, 5, 1, barrel());
		hang(c, p, a, 4, 3, 4);
		// ---- first floor: the dwelling (two beds on the front wall, a table, a kitchen, a bookcase)
		fi(c, p, a, 2, 0, 6, bed(east, false));
		fi(c, p, a, 3, 0, 6, bed(east, true));
		fi(c, p, a, e - 1, 0, 6, bed(east, false));
		fi(c, p, a, e, 0, 6, bed(east, true));
		fi(c, p, a, 4, 0, 6, barrel());
		fi(c, p, a, e, 1, 6, facing(Blocks.CHEST, west));
		fi(c, p, a, 4, 2, 6, slab(Blocks.DARK_OAK_SLAB, false));
		fi(c, p, a, 3, 2, 6, stair(Blocks.SPRUCE_STAIRS, west, false));
		fi(c, p, a, 5, 2, 6, stair(Blocks.SPRUCE_STAIRS, east, false));
		fi(c, p, a, 4, 4, 6, b(Blocks.BOOKSHELF));
		fi(c, p, a, 4, 5, 6, b(Blocks.BOOKSHELF));
		fi(c, p, a, e, bk, 6, facing(Blocks.SMOKER, west));
		fi(c, p, a, e, bk - 1, 6, barrel());
		hang(c, p, a, 4, 2, 9);
		// ---- second floor: the mirador (map table, shelves, benches under the windows)
		fi(c, p, a, 3, 2, 11, b(Blocks.CARTOGRAPHY_TABLE));
		for (int x : new int[] {1, 2, 4, 5}) {
			fi(c, p, a, x, 0, 11, stair(l.s.eaveStair(), Direction.NORTH, false));
		}
		for (int bb : new int[] {0, 1, 2, 5, 6}) {
			fi(c, p, a, 0, bb, 11, b(Blocks.BOOKSHELF));
		}
		fi(c, p, a, 0, 3, 11, facing(Blocks.CHEST, east));
		fi(c, p, a, 1, 4, 11, facing(Blocks.LECTERN, east).setValue(BlockStateProperties.HAS_BOOK, true));
		for (int bb = 0; bb <= 2; bb++) {
			fi(c, p, a, e, bb, 11, stair(l.s.eaveStair(), east, false));
		}
		fi(c, p, a, e, 4, 11, barrel());
		hang(c, p, a, 3, 3, 14);
		// ---- low tower: the study, then the loft
		Tw t = l.b;
		int be = t.n() - 1;
		Direction tw = t.west();
		Direction te = t.east();
		for (int bb = 1; bb <= 3; bb++) {
			fi(c, p, t, be, bb, 1, b(Blocks.BOOKSHELF));
			fi(c, p, t, be, bb, 2, b(Blocks.BOOKSHELF));
		}
		fi(c, p, t, be - 1, 2, 1, facing(Blocks.LECTERN, te).setValue(BlockStateProperties.HAS_BOOK, true));
		fi(c, p, t, be - 2, 2, 1, stair(Blocks.SPRUCE_STAIRS, tw, false));
		fi(c, p, t, be, be, 1, facing(Blocks.CHEST, Direction.NORTH));
		fi(c, p, t, be - 1, be, 1, barrel());
		hang(c, p, t, 2, 2, 4);
		fi(c, p, t, be - 2, 0, 6, bed(te, false));
		fi(c, p, t, be - 1, 0, 6, bed(te, true));
		fi(c, p, t, be, 0, 6, facing(Blocks.CHEST, tw));
		fi(c, p, t, be, be, 6, barrel());
		fi(c, p, t, be - 2, 2, 6, slab(Blocks.DARK_OAK_SLAB, false));
		fi(c, p, t, be - 2, 1, 6, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
		hang(c, p, t, be - 2, 2, 9);
	}

	// ------------------------------------------------------------------ hip roofs

	/**
	 * Pyramid roof from the plate up, one block in per row, solid inside so every row rests on the one below; ends in a
	 * fence-and-lantern finial. {@code lessRings} lowers the top of the roof (a flat crown); {@code spire} is the finial
	 * height in blocks.
	 */
	private void hip(CitadelCanvas c, L l, Tw t, int spire, int lessRings) {
		Plot p = l.p;
		int plate = t.plate();
		int h0 = (t.size() - 1) / 2 + 1;
		int kmax = Math.min(h0 - 1, p.h() - 3 - plate - (spire - 1));
		kmax = Math.max(1, kmax - lessRings);
		int cu = t.cu();
		int cv = t.cv();
		boolean eastBridge = t.east() == Direction.EAST;
		for (int k = 0; k <= kmax; k++) {
			int half = h0 - k;
			int y = plate + k;
			Block sb = k == 0 ? l.s.eaveStair() : l.s.roofStair();
			for (int du = -half; du <= half; du++) {
				for (int dv = -half; dv <= half; dv++) {
					boolean ring = Math.abs(du) == half || Math.abs(dv) == half;
					int u = cu + du;
					int v = cv + dv;
					if (!ring) {
						if (k >= 1) {
							set(c, p, u, y, v, b(l.s.roof()));
						}
						continue;
					}
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
						// the underside of the eave, except where the gallery meets the tower
						boolean bridgeCol = eastBridge ? du == half : du == -half;
						if (!(bridgeCol && Math.abs(v - l.vc()) <= 3)) {
							Direction face = north ? Direction.NORTH : south ? Direction.SOUTH : west ? Direction.WEST : Direction.EAST;
							set(c, p, u, y - 1, v, stair(l.s.roofStair(), face, true));
						}
					}
				}
			}
		}
		int top = plate + kmax + 1;
		for (int i = 0; i < spire; i++) {
			set(c, p, cu, top + i, cv, fence(l.s.fence(), false, false, false, false));
		}
		set(c, p, cu, top + spire, cv, lantern());
	}

	// ------------------------------------------------------------------ gallery

	/**
	 * The walk between the towers at the middle floor: a 5-wide deck (3 clear, 4 high) on log beams, with corbels at
	 * both ends and a pier on a long span.
	 */
	private void gallery(CitadelCanvas c, L l) {
		Plot p = l.p;
		Scheme s = l.s;
		int vc = l.vc();
		int g = l.gu1() - l.gu0() + 1;
		boolean stoneG = l.bridge() == 1;
		boolean open = l.bridge() == 2;
		for (int u = l.gu0(); u <= l.gu1(); u++) {
			int i = u - l.gu0();
			boolean post = i == 0 || i == g - 1 || i % 3 == 0;
			// deck
			for (int v = vc - 2; v <= vc + 2; v++) {
				boolean edge = Math.abs(v - vc) == 2;
				set(c, p, u, 5, v, edge ? log(s.log(), Direction.Axis.X) : p.hash(u, 5, v, 3) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(s.planks()));
			}
			for (int sd = -1; sd <= 1; sd += 2) {
				int v = vc + 2 * sd;
				// beam under each edge, tower to tower, and a stone corbel at the foot of each end
				set(c, p, u, 4, v, log(s.log(), Direction.Axis.X));
				if (i == 0) {
					set(c, p, u, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, true));
				}
				if (i == g - 1) {
					set(c, p, u, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST, true));
				}
				for (int y = 6; y <= 9; y++) {
					BlockState st;
					if (post) {
						st = stoneG ? b(Blocks.POLISHED_DEEPSLATE) : log(s.trim(), Direction.Axis.Y);
					} else if (open) {
						st = y == 6 ? fence(s.fence(), false, true, false, true) : null;
					} else if (y == 6) {
						st = stoneG ? stone(p, u, y, v) : b(s.planks());
					} else if (y == 9) {
						st = stoneG ? b(Blocks.STONE_BRICKS) : b(s.planks());
					} else {
						st = pane(p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, true);
					}
					if (st != null) {
						set(c, p, u, y, v, st);
					}
				}
				set(c, p, u, 10, v, log(s.log(), Direction.Axis.X));
			}
			for (int v = vc - 1; v <= vc + 1; v++) {
				set(c, p, u, 10, v, b(s.planks()));
			}
			if (stoneG) {
				flatRoof(c, l, u, i);
			} else {
				gableRoof(c, l, u);
			}
			if (i % 3 == 1) {
				put(c, p, u, 9, vc, hangingLantern(), CitadelCanvas.HANGING);
			}
		}
		// a pier with its own corbels on a long span
		if (g >= 7) {
			int um = l.gu0() + g / 2;
			for (int sd = -1; sd <= 1; sd += 2) {
				int v = vc + 2 * sd;
				for (int y = 1; y <= 3; y++) {
					set(c, p, um, y, v, deepslate(p, um, y, v));
				}
				set(c, p, um - 1, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST, true));
				set(c, p, um + 1, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, true));
			}
		}
	}

	private void gableRoof(CitadelCanvas c, L l, int u) {
		Plot p = l.p;
		Scheme s = l.s;
		int vc = l.vc();
		for (int dv = -3; dv <= 3; dv++) {
			int v = vc + dv;
			Direction up = dv < 0 ? Direction.SOUTH : Direction.NORTH;
			int ad = Math.abs(dv);
			if (ad == 3) {
				set(c, p, u, 10, v, stair(s.eaveStair(), up, false));
			} else if (ad == 2) {
				set(c, p, u, 11, v, stair(s.roofStair(), up, false));
			} else if (ad == 1) {
				set(c, p, u, 11, v, b(s.roof()));
				set(c, p, u, 12, v, stair(s.roofStair(), up, false));
			} else {
				set(c, p, u, 11, v, b(s.roof()));
				set(c, p, u, 12, v, b(s.roof()));
				set(c, p, u, 13, v, slab(s.roofSlab(), false));
			}
		}
	}

	/** A roof terrace: the roof is flat, with a stone parapet, a lamp on the posts and azaleas in the middle. */
	private void flatRoof(CitadelCanvas c, L l, int u, int i) {
		Plot p = l.p;
		Scheme s = l.s;
		int vc = l.vc();
		int g = l.gu1() - l.gu0() + 1;
		boolean post = i == 0 || i == g - 1 || i % 3 == 0;
		for (int sd = -1; sd <= 1; sd += 2) {
			int v = vc + 3 * sd;
			Direction in = sd < 0 ? Direction.SOUTH : Direction.NORTH;
			set(c, p, u, 10, v, stair(s.eaveStair(), in, true));
			set(c, p, u, 12, v, wall(Blocks.STONE_BRICK_WALL, false, true, false, true, post));
			if (post) {
				set(c, p, u, 13, v, lantern());
			}
		}
		for (int dv = -3; dv <= 3; dv++) {
			set(c, p, u, 11, vc + dv, b(s.roof()));
		}
		if (i % 2 == 0) {
			set(c, p, u, 12, vc, p.hash(u, 12, vc, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
	}

	// ------------------------------------------------------------------ yard

	private void yard(CitadelCanvas c, L l) {
		Plot p = l.p;
		int back = p.d() - 1;
		if (back >= l.a.v1() + 1) {
			set(c, p, l.a.cu() - 2, 0, back, b(Blocks.COMPOSTER));
			set(c, p, l.a.cu() + 2, 0, back, barrel());
		}
		if (back >= l.b.v1() + 1) {
			set(c, p, l.b.cu() - 1, 0, l.b.v1() + 1, barrel());
			set(c, p, l.b.cu() + 1, 0, l.b.v1() + 1, b(Blocks.COMPOSTER));
		}
		// lamp posts at the mouth of the passage under the gallery
		int g = l.gu1() - l.gu0() + 1;
		if (g >= 5) {
			for (int u : new int[] {l.gu0(), l.gu1()}) {
				for (int y = 0; y <= 2; y++) {
					set(c, p, u, y, 1, fence(l.s.fence(), false, false, false, false));
				}
				set(c, p, u, 3, 1, lantern());
			}
		}
	}
}
