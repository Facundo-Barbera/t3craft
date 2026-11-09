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
	private static final DyeColor[] ACCENT = {DyeColor.RED, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.YELLOW}; // brick, steel blue, sage, ochre
	/** [palette] the two other stock colours of each scheme, all terracotta: sober but easy to read from the street. */
	private static final DyeColor[][] STOCK = {
			{DyeColor.LIGHT_BLUE, DyeColor.BROWN},
			{DyeColor.YELLOW, DyeColor.BROWN},
			{DyeColor.ORANGE, DyeColor.BROWN},
			{DyeColor.LIGHT_BLUE, DyeColor.BROWN},
	};
	private static final String[] TONE = {"vermilion", "paper", "paper", "rose"};

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
		BlockState s = b(Blocks.SPRUCE_FENCE);
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
	private record Geo(int w, int d, int h, int ix, int bv, int roofMax, int ya, boolean flip, int idx, DyeColor accent, String tone) {
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
		int a = p.hash(6, 6, 6, ACCENT.length);
		Geo g = new Geo(w, d, h, ix, d - 1, roofMax, ya, p.hash(0, 0, 0, 2) == 1, a, ACCENT[a], TONE[a]);

		roof(c, p, g);
		posts(c, p, g);
		counter(c, p, g);
		if (w >= 6 && d >= 5) {
			sideCounter(c, p, g);
		}
		if (w <= 5) {
			crate(c, p, g);
		}
		back(c, p, g);
		aisle(c, p, g);
		poles(c, p, g);
		lights(c, p, g);
	}

	/** One cloth block: mostly neutrals, the seed's accent (a muted terracotta) two times in nine. */
	private static BlockState cloth(Plot p, Geo g, int u, int y, int v) {
		// bolts come in runs of two cells (one roll is two blocks wide); three sober terracottas of the seed's
		// scheme plus cream and white wool, so the goods are the loudest thing on the front
		DyeColor[] st = STOCK[g.idx];
		return switch (p.hash((u - g.lo()) / 2, 1, v, 7)) {
			case 0 -> terracotta(st[0]);
			case 1 -> terracotta(st[1]);
			case 2 -> terracotta(g.accent);
			case 3 -> terracotta(DyeColor.WHITE); // cream
			case 4 -> wool(DyeColor.WHITE);
			case 5 -> terracotta(st[0]);
			default -> terracotta(DyeColor.WHITE);
		};
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
			// [palette] [depth] the front edge of the roof is a band of the seed's terracotta (the stall's one patch
			// of colour, readable from the street); it runs post to post, never out over the side walk
			if (u >= g.postL() && u <= g.postR()) {
				p.set(c, u, 3, 1, terracotta(g.accent));
			}
		}
		for (int v = 2; v < g.d; v++) {
			int y = g.ry(v);
			int prev = g.ry(v - 1);
			BlockState s;
			if (y > prev) {
				s = stairs(Blocks.OAK_STAIRS, Direction.NORTH); // [palette] a light oak slope over dark posts: the roof does not melt into them
			} else if (y < prev) {
				s = stairs(Blocks.OAK_STAIRS, Direction.SOUTH);
			} else {
				s = b(Blocks.DARK_OAK_PLANKS); // [palette] the ridge rows in dark oak: a cap that breaks the slope's colour
			}
			for (int u = 0; u < g.w; u++) {
				BlockState t = s;
				if (y == prev) { // [depth] a flat ridge row: the eaves bevel down at both ends
					if (u == 0) {
						t = stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST);
					} else if (u == g.w - 1) {
						t = stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST);
					}
				}
				p.set(c, u, y, v, t);
			}
		}
	}

	// ------------------------------------------------------------------ posts

	private static void posts(CitadelCanvas c, Plot p, Geo g) {
		for (int u : new int[] {g.postL(), g.postR()}) {
			post(c, p, u, 1, 3, true); // front: cherry, up to the valance
			post(c, p, u, g.bv, g.ry(g.bv), false); // back: bark-brown spruce, up to the roof
			if (g.d >= 7) {
				post(c, p, u, g.bv - 3, g.ry(g.bv - 3), false);
			}
		}
	}

	/** [palette] a polished-deepslate footing (the basa), a stripped cherry shaft, a plank collar at the roof. */
	private static void post(CitadelCanvas c, Plot p, int u, int v, int top, boolean cherry) {
		p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 1; y < top - 1; y++) {
			p.set(c, u, y, v, b(cherry ? Blocks.STRIPPED_CHERRY_LOG : Blocks.SPRUCE_LOG));
		}
		p.set(c, u, top - 1, v, b(cherry ? Blocks.CHERRY_PLANKS : Blocks.SPRUCE_PLANKS));
	}

	// ------------------------------------------------------------------ the counter

	/** The cherry counter (v1): wool bolts in piles, a roll of carpet, now and then a loom. */
	private static void counter(CitadelCanvas c, Plot p, Geo g) {
		for (int u = g.lo(); u <= g.hi(); u++) {
			p.set(c, u, 0, 1, p.hash(u, 0, 1, 4) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH) : b(Blocks.CHERRY_PLANKS));
			stock(c, p, g, u, 1, true);
		}
	}

	/**
	 * One pile of stock at {@code (u, 1, v)}: a roll two blocks high (runs of two cells share a colour and a
	 * height, so the top edge is ragged), or a block with a folded carpet on it. Now and then a loom or a bare
	 * cherry board breaks the row. The cell above must be closed by the valance or a beam.
	 */
	private static void stock(CitadelCanvas c, Plot p, Geo g, int u, int v, boolean tall) {
		int k = p.hash(u, 7, v, 10);
		if (k == 8) {
			p.set(c, u, 1, v, b(Blocks.LOOM).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		} else if (k == 9) {
			p.set(c, u, 1, v, b(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)); // a bare board
		} else if (p.hash((u - g.lo()) / 2, 3, v, 3) != 0) {
			p.set(c, u, 1, v, cloth(p, g, u, 1, v));
			p.set(c, u, 2, v, cloth(p, g, u, 1, v));
		} else {
			p.set(c, u, 1, v, cloth(p, g, u, 1, v));
			p.set(c, u, 2, v, carpet(p.hash(u, 2, v, 2) == 0 ? DyeColor.WHITE : DyeColor.LIGHT_GRAY));
		}
	}

	/** On a small plot, a barrel crate of folded cloth stands in the lane's far corner, opposite the pole. */
	private static void crate(CitadelCanvas c, Plot p, Geo g) {
		int u = g.pole() == g.postL() ? g.postR() : g.postL();
		p.set(c, u, 0, 0, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, u, 1, 0, terracotta(g.accent));
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
		int len = g.w <= 5 ? 1 : Math.min(g.hi() - g.lo() + 1, main ? 5 : 3);
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
		return b(Blocks.SPRUCE_FENCE);
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
