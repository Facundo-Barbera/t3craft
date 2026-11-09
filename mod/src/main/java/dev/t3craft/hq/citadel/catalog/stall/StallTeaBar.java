package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tea bar: cherry pillars, a low hipped roof of deepslate tiles with a cherry eave line and upturned
 * corner tips, a bamboo counter with stools on the apron, kettles (water cauldrons) and pots on the
 * bar, and a smoker hearth with a kettle and a chimney at the back.
 *
 * <pre>
 *  section (v →)                        plan
 *  y4   ▄▄▄▄ ridge (tiles)               back   [shelf ▒ ▒] [hearth + chimney]
 *  y3  ▀▀ eave        ▄▀▀ eave           aisle  [mat]
 *  y2   lantern  kettle shelf            v1     P [bar: kettles, pots] P
 *  y1  pots, kettles                     v0     [stool] [stool] [stool]
 *  y0  stools, bar, hearth
 * </pre>
 *
 * Size: 4–6 × 4–5 × 5+.
 */
public final class StallTeaBar implements Variant {
	private static final int EAVE = 3;
	private static final int RIDGE = 4;

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
		return 4;
	}

	@Override
	public int minD() {
		return 4;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 6;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		pillars(c, p);
		bar(c, p);
		stools(c, p);
		roof(c, p);
		back(c, p); // after the roof: the chimney rises through the back eave
		lights(c, p);
	}

	// ------------------------------------------------------------------ pillars (y0..y2)

	private static void pillars(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {0, w - 1}) {
			for (int v : new int[] {0, d - 1}) {
				p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE)); // [palette] dark footing...
				p.set(c, u, 1, v, b(Blocks.CHERRY_LOG)); // ...pink shaft...
				p.set(c, u, 2, v, b(Blocks.STRIPPED_CHERRY_LOG)); // ...pale bracket under the eave
			}
		}
		// [depth] a tie beam between the front pillars and the back ones, just under the roof
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 2, d - 1, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // the front stays open so the bar reads
		}
	}

	// ------------------------------------------------------------------ the bar (v 1)

	private static BlockState barBody(Plot p, int u) {
		return switch (p.hash(u, 0, 1, 4)) {
			case 0 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH);
			case 1 -> b(Blocks.BAMBOO_MOSAIC);
			default -> b(Blocks.STRIPPED_BAMBOO_BLOCK);
		};
	}

	private static void bar(CitadelCanvas c, Plot p) {
		int w = p.w();
		for (int u = 0; u < w; u++) {
			p.set(c, u, 0, 1, barBody(p, u));
			switch (p.hash(u, 1, 1, 5)) {
				case 0 -> p.set(c, u, 1, 1, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3)); // a kettle
				case 1, 2 -> p.set(c, u, 1, 1, b(Blocks.FLOWER_POT)); // an empty clay pot: a teapot
				case 3 -> p.set(c, u, 1, 1, b(Blocks.POTTED_BAMBOO));
				default -> p.set(c, u, 1, 1, b(Blocks.POTTED_PINK_TULIP));
			}
		}
		// [finish] the ends of the bar carry a standing lantern; the middle always has a kettle and a teapot
		p.set(c, 0, 1, 1, b(Blocks.LANTERN));
		p.set(c, w - 1, 1, 1, b(Blocks.LANTERN));
		int mid = w / 2;
		p.set(c, mid, 1, 1, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		p.set(c, mid - 1, 1, 1, b(Blocks.FLOWER_POT));
	}

	private static void stools(CitadelCanvas c, Plot p) {
		int w = p.w();
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 0, 0, slab(Blocks.CHERRY_SLAB)); // [palette] pink seats against the pale bamboo bar
		}
	}

	// ------------------------------------------------------------------ the back: shelf, hearth, chimney

	private static void back(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int back = d - 1;
		int hearth = w - 2;
		for (int u = 1; u < w - 1; u++) {
			if (u == hearth) {
				continue;
			}
			p.set(c, u, 0, back, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH));
			p.set(c, u, 1, back, p.hash(u, 1, back, 2) == 0 ? b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 2) : b(Blocks.POTTED_FERN));
		}
		p.set(c, hearth, 0, back, b(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.LIT, true));
		p.set(c, hearth, 1, back, b(Blocks.CAULDRON)); // the big kettle on the stove
		// [finish] a deepslate-brick chimney through the back eave
		p.set(c, hearth, 2, back, b(Blocks.DEEPSLATE_BRICKS));
		p.set(c, hearth, EAVE, back, b(Blocks.DEEPSLATE_BRICKS));
		p.set(c, hearth, RIDGE, back, b(Blocks.DEEPSLATE_BRICKS));
		if (p.h() > RIDGE + 1) {
			p.set(c, hearth, RIDGE + 1, back, b(Blocks.DEEPSLATE_BRICK_WALL)); // the flue clears the ridge
		}
		// a woven mat on the aisle floor
		for (int v = 2; v < back; v++) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, 0, v, Blocks.CARPET.pick(DyeColor.GREEN).defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------ roof (y3..y5)

	/**
	 * [depth] a hip roof in three tiers: a cherry eave ring at y3 sloping in from all four sides, with
	 * stripped-cherry blocks over the pillars carrying upturned slab tips; a deepslate-tile ring one
	 * row in at y4 (stairs on its edges, tiles inside); from height 6 a ridge of tile slabs with a cherry
	 * cap along u. The eaves overhang the bar and the back shelf, so the roof reads as a tea house.
	 */
	private static void roof(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				boolean l = u == 0;
				boolean r = u == w - 1;
				boolean f = v == 0;
				boolean k = v == d - 1;
				if ((l || r) && (f || k)) {
					p.set(c, u, EAVE, v, b(Blocks.STRIPPED_CHERRY_LOG)); // [finish] a pale bracket block over each pillar
				} else if (f || k || l || r) {
					Direction up = f ? Direction.SOUTH : k ? Direction.NORTH : l ? Direction.EAST : Direction.WEST;
					p.set(c, u, EAVE, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, up)); // [palette] a dark eave line parts the roof from the posts
				} else {
					boolean il = u == 1;
					boolean ir = u == w - 2;
					boolean iff = v == 1;
					boolean ik = v == d - 2;
					BlockState st;
					if ((il || ir) && (iff || ik)) {
						st = b(Blocks.CHERRY_PLANKS);
					} else if (iff || ik || il || ir) {
						Direction up = iff ? Direction.SOUTH : ik ? Direction.NORTH : il ? Direction.EAST : Direction.WEST;
						st = stairs(Blocks.CHERRY_STAIRS, up);
					} else {
						st = b(Blocks.CHERRY_PLANKS);
					}
					p.set(c, u, RIDGE, v, st);
				}
			}
		}
		if (p.h() > RIDGE + 1) {
			int va = (d - 1) / 2;
			int vb = d / 2;
			for (int u = 1; u < w - 1; u++) {
				boolean end = u == 1 || u == w - 2;
				for (int v = va; v <= vb; v++) {
					p.set(c, u, RIDGE + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB)); // [palette] the dark ridge line
				}
			}
		}
		// [finish] lanterns hang from the front eave beside the noren, symmetric about the bar
		BlockState lantern = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		p.put(c, 1, EAVE, 1, lantern, CitadelCanvas.HANGING); // under the tile ring, over the bar
		p.put(c, w - 2, EAVE, 1, lantern, CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ lights

	private static void lights(CitadelCanvas c, Plot p) {
		int w = p.w();
		// [finish] a noren: a short red curtain hung from the front eave, with a white edge cell, so the
		// front reads as a tea house at a glance
		for (int u = 1; u < w - 1; u++) {
			if (((u + p.hash(1, 1, 1, 2)) & 1) == 0) {
				p.set(c, u, 2, 0, Blocks.WOOL.pick(DyeColor.RED).defaultBlockState()); // short noren tabs, gaps between
			}
		}
	}
}
