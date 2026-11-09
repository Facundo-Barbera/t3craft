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
		back(c, p);
		roof(c, p);
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
		p.set(c, hearth, RIDGE, back, b(Blocks.DEEPSLATE_BRICK_WALL));
		// a woven mat on the aisle floor
		for (int v = 2; v < back; v++) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, 0, v, Blocks.CARPET.pick(DyeColor.GREEN).defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------ roof (y3..y4)

	/**
	 * [depth] hip roof of deepslate tiles: stairs on the eave rows and the first inner rows, a flat
	 * tile band in the middle, a cherry-stair eave line at the front and back, and the four corner
	 * tips raised one block like a tea house in the east.
	 */
	private static void roof(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			boolean eave = v == 0 || v == d - 1;
			for (int u = 0; u < w; u++) {
				BlockState s;
				if (eave) {
					s = stairs(Blocks.CHERRY_STAIRS, v == 0 ? Direction.SOUTH : Direction.NORTH);
					p.set(c, u, EAVE, v, s);
				} else {
					boolean inner = v == 1 || v == d - 2;
					s = inner ? stairs(Blocks.DEEPSLATE_TILE_STAIRS, v <= (d - 1) / 2 ? Direction.SOUTH : Direction.NORTH)
							: b(Blocks.DEEPSLATE_TILES);
					p.set(c, u, RIDGE, v, s);
				}
			}
		}
		// the roof of the side edges: tile slabs close the ends under the ridge
		for (int v = 1; v < d - 1; v++) {
			p.set(c, 0, EAVE, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			p.set(c, w - 1, EAVE, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		// [finish] upturned corner tips
		for (int u : new int[] {0, w - 1}) {
			for (int v : new int[] {0, d - 1}) {
				p.set(c, u, RIDGE, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
		if (p.h() > RIDGE + 1) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, RIDGE + 1, d / 2, slab(Blocks.CHERRY_SLAB));
			}
		}
	}

	// ------------------------------------------------------------------ lights

	private static void lights(CitadelCanvas c, Plot p) {
		int w = p.w();
		// [finish] a noren: a short red curtain hung from the front eave, with a white edge cell, so the
		// front reads as a tea house at a glance
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 2, 0, Blocks.WOOL.pick(((u + p.hash(1, 1, 1, 2)) & 1) == 0 ? DyeColor.RED : DyeColor.WHITE).defaultBlockState());
		}
	}
}
