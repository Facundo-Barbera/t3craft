package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Tea bar: a working tea counter under a real stair-sloped roof. A cherry bar one block high with stair stools in
 * front (every other cell, a hatch gate in the bar for the vendor), a frame of stripped-cherry pillars on
 * polished-deepslate bases with a log lintel, noren tabs and paper lanterns hung from it, and behind the bar the
 * work wall: a chest, shelves of barrels and pots, a brewing stand and a hearth (campfire under a kettle, a
 * deepslate-brick flue through the roof) with an aisle of 1 to 3 cells. The roof is gable or hip by seed
 * (deepslate tile, stone brick or cherry), one stair per row up to the ridge, an eave over the stools and past
 * the side pillars. Plots 7 or more deep keep their front rows as a seating court under a slat pergola or a
 * cloth sail: one or two low tables with cushions or stair benches, lanterns on the front beam.
 *
 * <pre>
 *  section (v →)                          plan (front at the bottom)
 *  y6        ▄▄  ridge                    back   [chest][shelf][hearth][shelf][brew]
 *  y5     ▄▀▀▀▀▄  roof (stairs)           aisle  [        mat       ]
 *  y4  ▄▀▀        ▀▀▄  eave + ceiling     bar    P [pot][pot][gate][pot] P   (lantern + noren above)
 *  y3  ═ lintel ═══ frame                 stools [s]  [s]     [s]  [s]
 *  y1-2 stool  pots   shelf               court  [cushion][table][cushion]   (7+ deep: pergola over it)
 *  y0  base, bar, floor
 * </pre>
 *
 * Size: 5–14 × 4–12 × 6+ (the plots grow by √k with the citadel's scale).
 */
public final class StallTeaBar implements Variant {
	/** The first layer of the roof: the ceiling of the stall and the lowest edge of the eave. */
	private static final int EAVE = 4;

	@Override
	public String id() {
		return "stall_tea_bar";
	}

	@Override
	public Family family() {
		return Family.STALL;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 4;
	}

	@Override
	public int minH() {
		return 6;
	}

	@Override
	public int maxW() {
		return 14;
	}

	@Override
	public int maxD() {
		return 12;
	}

	/** The seed-derived measures of one build, local coordinates. */
	private record Geo(int w, int d, int h, int ox, int px0, int px1, int cd, int hd, int bar, int back, int cap, int gap, int[] cols,
			boolean hip, boolean side, int mat, boolean flip, boolean cloth, int accent) {
		boolean isCol(int u) {
			for (int c : cols) {
				if (c == u) {
					return true;
				}
			}
			return false;
		}
	}

	private static Geo geo(Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int ox = w >= 9 ? 1 : 0; // pillars inset: the roof flies one block past them on each side
		int px0 = ox;
		int px1 = w - 1 - ox;
		int hd = d <= 6 ? d : d == 7 ? 6 : d >= 10 ? 6 : 5; // rows under the stall roof
		int cd = d - hd; // rows of seating court in front
		int bar = cd + 1;
		int back = d - 1;
		int span = px1 - px0;
		int k = Math.max(1, (span + 4) / 6);
		int[] cols = new int[k + 1];
		for (int i = 0; i <= k; i++) {
			cols[i] = px0 + (span * i + k / 2) / k;
		}
		java.util.Random r = p.rng(0xC0FFEE);
		boolean flip = r.nextBoolean();
		boolean hip = r.nextBoolean();
		int mat = r.nextInt(3);
		boolean cloth = r.nextBoolean();
		int cap = Math.max(0, h - 1 - EAVE);
		boolean side = r.nextBoolean() && (w - 1) / 2 <= cap + 1; // ridge along the depth: a gable end to the street, only where the slope fits
		int accent = r.nextInt(3);
		int a = px0 + 1;
		int z = px1 - 1;
		int gap;
		if (ox == 0) {
			gap = flip ? z : a; // the hatch sits at one end of a narrow bar
		} else {
			gap = (px0 + px1) / 2 + (flip ? 1 : 0);
		}
		Geo tmp = new Geo(w, d, h, ox, px0, px1, cd, hd, bar, back, cap, gap, cols, false, false, 0, flip, false, 0);
		while (tmp.isCol(gap) && gap < z) {
			gap++;
		}
		if (gap > z) {
			gap = a;
		}
		return new Geo(w, d, h, ox, px0, px1, cd, hd, bar, back, cap, gap, cols, hip, side, mat, flip, cloth, accent);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Geo g = geo(p);
		frame(c, p, g);
		roof(c, p, g);
		bar(c, p, g);
		stools(c, p, g);
		back(c, p, g); // after the roof: the flue goes through the back eave
		aisle(c, p, g);
		if (g.cd >= 3) {
			court(c, p, g);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static BlockState log(Block block, Direction.Axis axis) {
		return b(block).setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState hanging() {
		return b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	}

	private static BlockState top(Block slabBlock) {
		return b(slabBlock).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState carpet(DyeColor col) {
		return Blocks.CARPET.pick(col).defaultBlockState();
	}

	/** The accent cloth of the noren and the cushions, by seed: a quiet red, clay orange or white. */
	private static DyeColor accent(Geo g) {
		return switch (g.accent) {
			case 0 -> DyeColor.RED;
			case 1 -> DyeColor.ORANGE;
			default -> DyeColor.WHITE;
		};
	}

	/** [palette] a polished-deepslate base, a pale stripped-cherry shaft; the beam is the caller's. */
	private static void post(CitadelCanvas c, Plot p, int u, int v, int beamY) {
		p.set(c, u, 0, v, switch (p.hash(u, 0, v, 4)) { // [palette] a mixed deepslate base, never uniform
			case 0 -> b(Blocks.DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.POLISHED_DEEPSLATE);
		});
		for (int y = 1; y < beamY; y++) {
			p.set(c, u, y, v, b(Blocks.STRIPPED_CHERRY_LOG));
		}
	}

	// ------------------------------------------------------------------ frame (pillars, lintel, side beams)

	/**
	 * [depth] pillars on bases at the bar line and at the back, a dark cherry-log lintel across the bar (the noren and
	 * lanterns hang from it) and a log beam down each side, all at y3 under the ceiling.
	 */
	private static void frame(CitadelCanvas c, Plot p, Geo g) {
		for (int u : g.cols) {
			post(c, p, u, g.bar, 3);
			post(c, p, u, g.back, 3);
			p.set(c, u, 3, g.back, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
		}
		for (int u = g.px0; u <= g.px1; u++) {
			p.set(c, u, 3, g.bar, log(Blocks.CHERRY_LOG, Direction.Axis.X)); // the lintel
		}
		for (int u : new int[] {g.px0, g.px1}) {
			for (int v = g.bar + 1; v < g.back; v++) {
				p.set(c, u, 3, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
			}
		}
	}

	// ------------------------------------------------------------------ the bar (v = bar)

	private static List<Integer> barCells(Geo g) {
		List<Integer> out = new ArrayList<>();
		for (int u = g.px0 + 1; u <= g.px1 - 1; u++) {
			if (!g.isCol(u) && u != g.gap) {
				out.add(u);
			}
		}
		return out;
	}

	/**
	 * The cherry bar, one block high (y0), with goods on every cell (pots, teapots, kettles, standing lanterns) so
	 * nobody stands on it; a paper lantern hangs from the lintel every fourth cell and a noren tab on every second.
	 * The vendor's hatch is a gate in the bar.
	 */
	private static void bar(CitadelCanvas c, Plot p, Geo g) {
		List<Integer> cells = barCells(g);
		int n = cells.size();
		DyeColor ac = accent(g);
		for (int i = 0; i < n; i++) {
			int u = cells.get(i);
			boolean lamp = i % 4 == 0 || i == n - 1;
			p.set(c, u, 0, g.bar, i % 3 == 2 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X) : b(Blocks.CHERRY_PLANKS));
			BlockState goods = switch (p.hash(u, 1, g.bar, 6)) {
				case 0, 1 -> b(Blocks.FLOWER_POT); // a teapot
				case 2 -> b(Blocks.POTTED_BAMBOO);
				case 3 -> b(Blocks.POTTED_CHERRY_SAPLING);
				case 4 -> lamp ? b(Blocks.FLOWER_POT) : b(Blocks.LANTERN);
				default -> b(Blocks.CAULDRON); // a kettle
			};
			p.set(c, u, 1, g.bar, goods);
			if (lamp) {
				p.put(c, u, 2, g.bar, hanging(), CitadelCanvas.HANGING);
			} else if (i % 2 == 1) {
				p.set(c, u, 2, g.bar, Blocks.WOOL.pick(ac).defaultBlockState()); // noren tab
			}
		}
		p.set(c, g.gap, 0, g.bar, b(Blocks.CHERRY_FENCE_GATE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	/** Stair stools on the apron, backs to the street, every other cell; the hatch stays free. */
	private static void stools(CitadelCanvas c, Plot p, Geo g) {
		int a = g.px0 + 1;
		for (int u = a; u <= g.px1 - 1; u++) {
			if (g.isCol(u) || u == g.gap || (u - a) % 2 != 0) {
				continue;
			}
			p.set(c, u, 0, g.cd, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
		}
	}

	// ------------------------------------------------------------------ the work wall (v = back)

	private enum Mod {
		CHEST, SHELF, BREW, HEARTH, STONE
	}

	private static void back(CitadelCanvas c, Plot p, Geo g) {
		List<Integer> cells = new ArrayList<>();
		for (int u = g.px0 + 1; u <= g.px1 - 1; u++) {
			if (!g.isCol(u)) {
				cells.add(u);
			}
		}
		int n = cells.size();
		if (n < 3) {
			return;
		}
		int hi = n >= 5 ? 2 + p.hash(7, 7, 7, n - 4) : n / 2;
		Mod[] mods = new Mod[n];
		int first = -1;
		int last = -1;
		for (int i = 0; i < n; i++) {
			if (i == hi) {
				mods[i] = Mod.HEARTH;
			} else if (n >= 5 && Math.abs(i - hi) == 1) {
				mods[i] = Mod.STONE;
			} else {
				mods[i] = Mod.SHELF;
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first >= 0) {
			mods[first] = Mod.CHEST;
		}
		if (last >= 0 && last != first) {
			mods[last] = Mod.BREW;
		}
		for (int i = 0; i < n; i++) {
			int u = g.flip ? cells.get(n - 1 - i) : cells.get(i);
			module(c, p, g, u, mods[i]);
		}
	}

	private static BlockState jar(Plot p, int u, int y) {
		return switch (p.hash(u, y, 9, 5)) {
			case 0 -> b(Blocks.FLOWER_POT);
			case 1 -> b(Blocks.POTTED_BAMBOO);
			case 2 -> b(Blocks.POTTED_FERN);
			case 3 -> b(Blocks.POTTED_PINK_TULIP);
			default -> b(Blocks.POTTED_CHERRY_SAPLING);
		};
	}

	private static void module(CitadelCanvas c, Plot p, Geo g, int u, Mod m) {
		int v = g.back;
		switch (m) {
			case CHEST -> {
				p.set(c, u, 0, v, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				shelf(c, p, u, v);
			}
			case SHELF -> {
				p.set(c, u, 0, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH));
				p.set(c, u, 1, v, jar(p, u, 1));
				shelf(c, p, u, v);
			}
			case BREW -> {
				p.set(c, u, 0, v, b(Blocks.CHERRY_PLANKS));
				p.set(c, u, 1, v, b(Blocks.BREWING_STAND));
				shelf(c, p, u, v);
			}
			case STONE -> {
				p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE)); // a fireproof flank
				p.set(c, u, 1, v, b(Blocks.LANTERN));
				p.set(c, u, 2, v, top(Blocks.CHERRY_SLAB)); // a board over the lantern: nobody stands on it
			}
			case HEARTH -> {
				p.set(c, u, 0, v, b(Blocks.CAMPFIRE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.LIT, true));
				p.set(c, u, 1, v, b(Blocks.CAULDRON)); // the kettle on the fire
				// [finish] a deepslate-brick flue from the hood through the back eave, clear of the ridge
				int topY = Math.min(g.h - 1, EAVE + 2);
				for (int y = 2; y <= topY; y++) {
					p.set(c, u, y, v, b(Blocks.DEEPSLATE_BRICKS));
				}
				if (topY + 1 < g.h) {
					p.set(c, u, topY + 1, v, b(Blocks.DEEPSLATE_BRICK_WALL));
				}
			}
		}
	}

	/** [depth] a shelf board (top slab) at y2 with a jar on it at y3, under the ceiling. */
	private static void shelf(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 2, v, top(Blocks.CHERRY_SLAB));
		p.set(c, u, 3, v, jar(p, u, 3));
	}

	/** A woven mat in the aisle and lanterns hung from the ceiling over it. */
	private static void aisle(CitadelCanvas c, Plot p, Geo g) {
		int a = g.px0 + 1;
		int z = g.px1 - 1;
		for (int v = g.bar + 1; v < g.back; v++) {
			for (int u = a; u <= z; u++) {
				p.set(c, u, 0, v, carpet(DyeColor.GREEN));
			}
		}
		if (g.back - g.bar >= 2) {
			int v = g.bar + (g.back - g.bar) / 2;
			int m = z - a + 1;
			for (int u : new int[] {a + m / 4, z - m / 4}) {
				p.put(c, u, 3, v, hanging(), CitadelCanvas.HANGING);
			}
		}
	}

	// ------------------------------------------------------------------ roof

	private static Block roofStairs(int mat) {
		return switch (mat) {
			case 0 -> Blocks.DEEPSLATE_TILE_STAIRS;
			case 1 -> Blocks.STONE_BRICK_STAIRS;
			default -> Blocks.CHERRY_STAIRS;
		};
	}

	private static BlockState roofSlab(int mat) {
		return slab(switch (mat) {
			case 0 -> Blocks.DEEPSLATE_TILE_SLAB;
			case 1 -> Blocks.STONE_BRICK_SLAB;
			default -> Blocks.CHERRY_SLAB;
		});
	}

	private static BlockState roofBlock(int mat) {
		return b(switch (mat) {
			case 0 -> Blocks.DEEPSLATE_TILES;
			case 1 -> Blocks.STONE_BRICKS;
			default -> Blocks.CHERRY_PLANKS;
		});
	}

	/** Rising direction of a row: towards the ridge, or null on the centre line. */
	private static Direction dirV(int a, int b) {
		return a < b ? Direction.SOUTH : a > b ? Direction.NORTH : null;
	}

	private static Direction dirU(int u, int w) {
		return u < w - 1 - u ? Direction.EAST : u > w - 1 - u ? Direction.WEST : null;
	}

	/**
	 * [depth] one stair per row: the eave stairs at y4 on the box edge, each next row one step up, a ridge slab on the
	 * centre line of an odd depth (the two middle rows face each other on an even one). The gable runs the slope front
	 * to back; the hip also slopes the two ends. Under every row the block is filled to the ceiling (y4, planks), so
	 * the roof rests on the frame. Rows above the plot's height become a flat top. Stair shapes are set by hand
	 * (vanilla's rule) so the hip corners come out as corner stairs.
	 */
	private static void roof(CitadelCanvas c, Plot p, Geo g) {
		Direction[][] rf = new Direction[g.w][g.d];
		int[][] ry = new int[g.w][g.d];
		for (int v = g.cd; v < g.d; v++) {
			for (int u = 0; u < g.w; u++) {
				int pa = g.side ? u : v - g.cd; // the sloping axis: front to back, or (side ridge) left to right
				int pb = g.side ? g.w - 1 - u : g.d - 1 - v;
				int dp = Math.min(pa, pb);
				int ds = !g.hip ? Integer.MAX_VALUE : g.side ? Math.min(v - g.cd, g.d - 1 - v) : Math.min(u, g.w - 1 - u);
				int n = Math.min(dp, ds);
				int t = Math.min(n, g.cap);
				for (int y = EAVE; y < EAVE + t; y++) {
					p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS)); // ceiling and the roof's body
				}
				if (n > g.cap) {
					p.set(c, u, EAVE + t, v, roofBlock(g.mat)); // a flat top
					continue;
				}
				Direction prim = g.side ? dirU(u, g.w) : dirV(v - g.cd, g.d - 1 - v);
				Direction sec = g.side ? dirV(v - g.cd, g.d - 1 - v) : dirU(u, g.w);
				Direction f = g.hip && ds < dp ? sec : prim;
				if (f == null) {
					p.set(c, u, EAVE + t, v, roofSlab(g.mat)); // the ridge
				} else {
					rf[u][v] = f;
					ry[u][v] = EAVE + t;
				}
			}
		}
		Block st = roofStairs(g.mat);
		for (int v = g.cd; v < g.d; v++) {
			for (int u = 0; u < g.w; u++) {
				if (rf[u][v] == null) {
					continue;
				}
				StairsShape shape = shape(rf, ry, u, v, g.w, g.d);
				p.set(c, u, ry[u][v], v, st.defaultBlockState().setValue(StairBlock.FACING, rf[u][v]).setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, shape));
			}
		}
	}

	private static Direction at(Direction[][] rf, int[][] ry, int u, int v, int y, int w, int d) {
		if (u < 0 || v < 0 || u >= w || v >= d || rf[u][v] == null || ry[u][v] != y) {
			return null;
		}
		return rf[u][v];
	}

	/** Vanilla's {@code StairBlock.getStairsShape} on the roof grid. */
	private static StairsShape shape(Direction[][] rf, int[][] ry, int u, int v, int w, int d) {
		Direction dir = rf[u][v];
		int y = ry[u][v];
		Direction fr = at(rf, ry, u + dir.getStepX(), v + dir.getStepZ(), y, w, d);
		if (fr != null && fr.getAxis() != dir.getAxis() && canTake(rf, ry, u, v, fr.getOpposite(), w, d)) {
			return fr == dir.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
		}
		Direction bk = at(rf, ry, u - dir.getStepX(), v - dir.getStepZ(), y, w, d);
		if (bk != null && bk.getAxis() != dir.getAxis() && canTake(rf, ry, u, v, bk, w, d)) {
			return bk == dir.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
		}
		return StairsShape.STRAIGHT;
	}

	private static boolean canTake(Direction[][] rf, int[][] ry, int u, int v, Direction face, int w, int d) {
		Direction n = at(rf, ry, u + face.getStepX(), v + face.getStepZ(), ry[u][v], w, d);
		return n == null || n != rf[u][v];
	}

	// ------------------------------------------------------------------ the seating court (7+ deep plots)

	/**
	 * [depth] the front rows (cd of them) become a court: posts on bases at the front, a log beam round the three open
	 * sides at y4 flush with the stall's eave, and a cover over it, by seed a slat pergola (cherry slabs on the
	 * beams, a cross rafter near the stall) or a cloth sail (two quiet terracottas in a check). Lanterns hang from the
	 * front beam only, never over a seat. One or two low tables of cherry slabs, each with two cushions (carpets) or
	 * two stair benches facing it.
	 */
	private static void court(CitadelCanvas c, Plot p, Geo g) {
		int a = g.px0 + 1;
		int z = g.px1 - 1;
		for (int u : g.cols) {
			post(c, p, u, 0, 4);
		}
		for (int u = g.px0; u <= g.px1; u++) {
			p.set(c, u, 4, 0, log(Blocks.CHERRY_LOG, Direction.Axis.X));
		}
		for (int u : new int[] {g.px0, g.px1}) {
			for (int v = 1; v < g.cd; v++) {
				p.set(c, u, 4, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
			}
		}
		for (int v = 1; v < g.cd; v++) {
			boolean rafter = v == g.cd - 1 || g.cd >= 5 && v == g.cd / 2;
			for (int u = a; u <= z; u++) {
				if (g.cloth) {
					p.set(c, u, 4, v, terracotta(DyeColor.WHITE));
				} else if (rafter) {
					p.set(c, u, 4, v, log(Blocks.CHERRY_LOG, Direction.Axis.X));
				} else if (((u - g.px0) & 1) == 1) {
					p.set(c, u, 4, v, slab(Blocks.CHERRY_SLAB)); // a slat
				}
			}
		}
		// tables
		int m = z - a + 1;
		int vt = g.cd / 2;
		List<Integer> tables = new ArrayList<>();
		if (m >= 8) {
			tables.add(a + 1);
			tables.add(z - 2);
		} else if (m >= 4) {
			tables.add(g.flip ? z - 2 : a + 1);
		}
		boolean[] seat = new boolean[g.w];
		boolean cushionsFirst = p.rng(0x51).nextBoolean();
		for (int i = 0; i < tables.size(); i++) {
			int u0 = tables.get(i);
			boolean cushions = (i == 0) == cushionsFirst;
			for (int u = u0; u < u0 + 2; u++) {
				seat[u] = true;
				p.set(c, u, 0, vt, slab(Blocks.CHERRY_SLAB)); // the low table
				if (cushions) {
					p.set(c, u, 0, vt - 1, carpet(accent(g)));
					p.set(c, u, 0, vt + 1, carpet(accent(g)));
				} else {
					p.set(c, u, 0, vt - 1, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
					p.set(c, u, 0, vt + 1, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
				}
			}
		}
		for (int v = 1; v < g.cd; v += 2) { // [finish] azalea bushes along the court's sides, clear of the tables
			for (int u : new int[] {a, z}) {
				if (!seat[u] && !g.isCol(u)) {
					p.set(c, u, 0, v, p.hash(u, 0, v, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
				}
			}
		}
		for (int u = a + 1; u <= z; u += 3) { // lanterns on the front beam, never over a seat
			if (!g.isCol(u) && !seat[u]) {
				p.put(c, u, 3, 0, hanging(), CitadelCanvas.HANGING);
			}
		}
	}
}
