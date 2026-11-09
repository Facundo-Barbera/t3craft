package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Cloth and lantern stall. A cherry lean-to roof of stairs climbs from a striped cloth valance over the counter up
 * to the back, on stripped-cherry posts with deepslate footings. In front of the counter the lane stays open air;
 * one front post rises into a tall pole whose rod reaches over the lane and carries paper lanterns (the kit's
 * {@code paper_lantern_hanging}, a vanilla lantern when the kit is off). On the cherry counter, piles of wool bolts
 * in quiet neutrals with one accent colour per seed; behind it a shelf with goods, a chest, a stool and a rail
 * from which lengths of cloth hang. Wide, deep plots add a second counter along one side (an L) and a second pole.
 *
 * <pre>
 *  section (v →)                       plan (front at the bottom)
 *  y6  pole ─ rod                       back   [chest][shelf ····][rail + cloth]
 *  y5     lantern    ╱ roof              aisle  [stool]   [carpet]   [side counter]
 *  y4               ╱                    v1     P [valance + counter: bolts] P
 *  y3  valance ▀▀▀╱ lantern             v0     (open lane: rod and lanterns overhead)
 *  y1  bolts  P  shelf
 *  y0  counter, deepslate footings
 * </pre>
 *
 * Size: 4–10 × 3–8 × 5+.
 */
public final class StallClothLanterns implements Variant {
	@Override
	public String id() {
		return "stall_cloth_lanterns";
	}

	@Override
	public Family family() {
		return Family.STALL;
	}

	@Override
	public int minW() {
		return 4;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 10;
	}

	@Override
	public int maxD() {
		return 8;
	}

	/** [palette] the neutral wools of the cloth: they make most of the stock, the accent comes in on top. */
	private static final DyeColor[] NEUTRAL = {DyeColor.LIGHT_GRAY, DyeColor.WHITE, DyeColor.BROWN, DyeColor.LIGHT_GRAY, DyeColor.GRAY};
	/** [palette] one muted accent per seed (the city's cherry is the main colour, so the stall's accent stays small). */
	private static final DyeColor[] ACCENT = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW};
	private static final String[] TONE = {"paper", "rose", "paper", "vermilion"};

	private static BlockState wool(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}

	private static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	private static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}

	private static BlockState fence(Direction... joins) {
		BlockState s = b(Blocks.CHERRY_FENCE);
		for (Direction d : joins) {
			s = s.setValue(side(d), true);
		}
		return s;
	}

	// ------------------------------------------------------------------ the kit's lantern, with a vanilla fallback

	private static BlockState kit(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("t3craft", path);
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).defaultBlockState() : null;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState s, Property<T> p, String value) {
		return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
	}

	/** A hanging lantern: the kit's chochin of that tone, or the vanilla hanging lantern. */
	private static BlockState hangingLantern(String tone) {
		BlockState s = kit("paper_lantern_hanging");
		if (s == null) {
			return b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		}
		Property<?> prop = s.getBlock().getStateDefinition().getProperty("tone");
		return prop == null ? s : withValue(s, prop, tone);
	}

	/** The seed-derived measures of one build, local coordinates. */
	private record Geo(int w, int d, int h, int ix, int bv, int roofMax, int ya, boolean flip, DyeColor accent, String tone) {
		int lo() {
			return ix + 1;
		}

		int hi() {
			return w - 2 - ix;
		}

		int postL() {
			return ix;
		}

		int postR() {
			return w - 1 - ix;
		}

		/** Roof height over row {@code v}: the valance at v1, a stair a row up to {@code roofMax}, a ridge, and a drop to the back eave. */
		int ry(int v) {
			return v <= 1 ? 3 : Math.min(Math.min(roofMax, 4 + (v - 2)), 4 + (bv - v));
		}

		/** The pole's column (the other front post stays short unless the plot is wide). */
		int pole() {
			return flip ? postR() : postL();
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int ix = w >= 6 ? 1 : 0; // posts inset: the roof flies one block past them on each side
		int roofMax = Math.max(4, Math.min(h - 1, 5));
		int ya = Math.min(h - 1, roofMax + 2);
		int a = p.hash(5, 5, 5, ACCENT.length);
		Geo g = new Geo(w, d, h, ix, d - 1, roofMax, ya, p.hash(1, 2, 3, 2) == 1, ACCENT[a], TONE[a]);

		roof(c, p, g);
		posts(c, p, g);
		counter(c, p, g);
		if (w >= 6 && d >= 5) {
			sideCounter(c, p, g);
		}
		back(c, p, g);
		aisle(c, p, g);
		poles(c, p, g);
		lights(c, p, g);
	}

	/** One cloth block: mostly neutrals, the seed's accent (a muted terracotta) two times in nine. */
	private static BlockState cloth(Plot p, Geo g, int u, int y, int v) {
		int k = p.hash(u, y, v, 9);
		return k < 2 ? terracotta(g.accent) : wool(NEUTRAL[(k - 2) % NEUTRAL.length]);
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [depth] [finish] the front edge is a cloth valance (accent and cream stripes) over the counter; behind it
	 * the cherry stairs climb a block a row (low edge to the front), then run flat as planks. Under the
	 * valance a fringe of short cloth hangs from every other block.
	 */
	private static void roof(CitadelCanvas c, Plot p, Geo g) {
		for (int u = 0; u < g.w; u++) {
			if (u == g.pole()) {
				continue; // the pole passes here
			}
			// [depth] a thin spruce fascia (top slabs) closes the front of the roof, and a cloth valance hangs from it two
			// blocks in three (the third gap shows the goods): an accent stripe, cream and a light grey
			p.set(c, u, 3, 1, b(Blocks.SPRUCE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
			if (u % 3 != 2 && u >= g.lo() && u <= g.hi()) { // never over the side walk outside the posts
				p.set(c, u, 2, 1, u % 3 == 0 ? terracotta(g.accent) : wool(DyeColor.WHITE));
			}
		}
		for (int v = 2; v < g.d; v++) {
			int y = g.ry(v);
			int prev = g.ry(v - 1);
			BlockState s;
			if (y > prev) {
				s = stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH);
			} else if (y < prev) {
				s = stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH);
			} else {
				s = b(Blocks.SPRUCE_PLANKS);
			}
			for (int u = 0; u < g.w; u++) {
				p.set(c, u, y, v, s);
			}
		}
	}

	// ------------------------------------------------------------------ posts

	private static void posts(CitadelCanvas c, Plot p, Geo g) {
		for (int u : new int[] {g.postL(), g.postR()}) {
			post(c, p, u, 1, 3); // front: up to the valance
			post(c, p, u, g.bv, g.ry(g.bv)); // back: up to the roof
			if (g.d >= 7) {
				post(c, p, u, g.bv - 3, g.ry(g.bv - 3));
			}
		}
	}

	/** [palette] a polished-deepslate footing (the basa), a stripped cherry shaft, a plank collar at the roof. */
	private static void post(CitadelCanvas c, Plot p, int u, int v, int top) {
		p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 1; y < top - 1; y++) {
			p.set(c, u, y, v, b(Blocks.STRIPPED_CHERRY_LOG));
		}
		p.set(c, u, top - 1, v, b(Blocks.CHERRY_PLANKS));
	}

	// ------------------------------------------------------------------ the counter

	/** The cherry counter (v1): wool bolts in piles, a roll of carpet, now and then a loom. */
	private static void counter(CitadelCanvas c, Plot p, Geo g) {
		for (int u = g.lo(); u <= g.hi(); u++) {
			p.set(c, u, 0, 1, p.hash(u, 0, 1, 4) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH) : b(Blocks.CHERRY_PLANKS));
			stock(c, p, g, u, 1, u % 3 == 2);
		}
	}

	/**
	 * One pile of stock at {@code (u, 1, v)}. Under the valance a pile is one block high; where {@code tall} is
	 * allowed it may be a stack of two (wool and a folded carpet) or a roll of two wools. Now and then a bare cherry
	 * board with a loom or a lantern breaks the row.
	 */
	private static void stock(CitadelCanvas c, Plot p, Geo g, int u, int v, boolean tall) {
		int k = p.hash(u, 7, v, 8);
		if (tall && k <= 2) {
			p.set(c, u, 1, v, cloth(p, g, u, 1, v));
			p.set(c, u, 2, v, k == 0 ? cloth(p, g, u, 2, v) : carpet(NEUTRAL[p.hash(u, 2, v, NEUTRAL.length)]));
		} else if (k == 6) {
			p.set(c, u, 1, v, b(Blocks.LOOM).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		} else if (k == 7) {
			p.set(c, u, 1, v, b(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)); // a bare board
		} else {
			p.set(c, u, 1, v, cloth(p, g, u, 1, v));
		}
	}

	/** The second arm of the L: a counter along the side opposite the pole, facing into the aisle. */
	private static void sideCounter(CitadelCanvas c, Plot p, Geo g) {
		int su = g.flip ? g.lo() : g.hi();
		for (int v = 2; v <= g.bv - 2; v++) {
			p.set(c, su, 0, v, p.hash(su, 0, v, 4) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, g.flip ? Direction.EAST : Direction.WEST) : b(Blocks.CHERRY_PLANKS));
			stock(c, p, g, su, v, true);
			// [depth] a beam over the arm (a log along v under the roof) with a length of cloth hanging from it, so
			// the bolts have a ceiling and nobody can stand on them
			int beam = g.ry(v) - 1;
			p.set(c, su, beam, v, b(Blocks.SPRUCE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
			for (int y = 3; y < beam; y++) {
				p.set(c, su, y, v, y == 3 && v % 2 == 0 ? terracotta(g.accent) : wool(DyeColor.WHITE));
			}
		}
	}

	// ------------------------------------------------------------------ the back: shelf, chest, rail

	private static void back(CitadelCanvas c, Plot p, Geo g) {
		int v = g.bv;
		int cu = g.flip ? g.hi() : g.lo(); // the chest, on the side away from the L's arm
		int rod = g.ry(v) - 1;
		for (int u = g.lo(); u <= g.hi(); u++) {
			if (u == cu) {
				p.set(c, u, 0, v, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				p.set(c, u, 1, v, b(Blocks.LANTERN)); // a lantern on the chest: nobody can stand there
			} else {
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH) : b(Blocks.CHERRY_PLANKS));
				// the shelf: goods in piles of bolts, a cherry board over some of them
				if (p.hash(u, 1, v, 3) == 0) {
					p.set(c, u, 1, v, b(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
				} else {
					p.set(c, u, 1, v, cloth(p, g, u, 1, v));
				}
			}
			// [depth] the rail: a cherry fence between the posts; lengths of cloth hang from it, alternate ones longer
			p.set(c, u, rod, v, fence(Direction.EAST, Direction.WEST));
			if (rod >= 4) {
				p.set(c, u, rod - 1, v, u % 3 == 0 ? terracotta(g.accent) : wool(DyeColor.WHITE)); // a full row at y3: a ceiling over the shelf
			}
			if (((u + p.hash(4, 4, 4, 2)) & 1) == 0) {
				int low = rod >= 4 ? rod - 2 : rod - 1;
				p.set(c, u, low, v, cloth(p, g, u, low, v));
			}
		}
	}

	// ------------------------------------------------------------------ aisle

	private static void aisle(CitadelCanvas c, Plot p, Geo g) {
		int su = g.flip ? g.lo() : g.hi();
		boolean ell = g.w >= 6 && g.d >= 5;
		for (int v = 2; v < g.bv; v++) {
			for (int u = g.lo(); u <= g.hi(); u++) {
				if (ell && u == su && v <= g.bv - 2) {
					continue;
				}
				p.set(c, u, 0, v, carpet(((u + v) & 1) == 0 ? DyeColor.GRAY : DyeColor.LIGHT_GRAY));
			}
		}
		if (g.bv >= 3) { // a stool (stairs facing the counter) by the shelf
			int u = g.flip ? g.lo() : g.hi();
			if (ell && u == su) {
				u += g.flip ? 1 : -1;
			}
			p.set(c, u, 0, g.bv - 1, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
		}
	}

	// ------------------------------------------------------------------ the pole: a tall post, a rod over the lane, lanterns

	private static void poles(CitadelCanvas c, Plot p, Geo g) {
		pole(c, p, g, g.pole(), g.pole() == g.postL() ? 1 : -1, true);
		if (g.w >= 8) {
			int other = g.pole() == g.postL() ? g.postR() : g.postL();
			pole(c, p, g, other, other == g.postL() ? 1 : -1, false);
		}
	}

	/** The tall post at {@code (u, v1)}: log shaft, fence above the valance, then a rod forward and inwards. */
	private static void pole(CitadelCanvas c, Plot p, Geo g, int u, int dir, boolean main) {
		int top = main ? g.ya : Math.max(4, g.ya - 1);
		for (int y = 3; y < top; y++) {
			p.set(c, u, y, 1, fence());
		}
		p.set(c, u, top, 1, fence(Direction.NORTH));
		int len = Math.min(g.hi() - g.lo() + 1, main ? 5 : 3);
		Direction in = dir > 0 ? Direction.EAST : Direction.WEST;
		Direction out = dir > 0 ? Direction.WEST : Direction.EAST;
		p.set(c, u, top, 0, fence(Direction.SOUTH, in));
		hang(c, p, g, u, top - 1, 0);
		for (int s = 1; s <= len; s++) {
			int uu = u + dir * s;
			p.set(c, uu, top, 0, s == len ? fence(out) : fence(out, in));
			if (s % 2 == 0 || s == len) {
				hang(c, p, g, uu, top - 1, 0);
			}
		}
		if (g.h() > top + 1) {
			p.set(c, u, top + 1, 1, b(Blocks.LANTERN)); // a finial light on the pole's head
		}
	}

	private static void hang(CitadelCanvas c, Plot p, Geo g, int u, int y, int v) {
		if (y >= 3) {
			p.put(c, u, y, v, hangingLantern(g.tone), CitadelCanvas.HANGING);
		}
	}

	private static BlockState fence() {
		return b(Blocks.CHERRY_FENCE);
	}

	// ------------------------------------------------------------------ light under the roof

	private static void lights(CitadelCanvas c, Plot p, Geo g) {
		for (int v = 2; v < g.bv; v += 2) {
			int y = g.ry(v) - 1;
			if (y >= 3) {
				p.put(c, g.w / 2, y, v, hangingLantern(g.tone), CitadelCanvas.HANGING);
			}
		}
	}
}
