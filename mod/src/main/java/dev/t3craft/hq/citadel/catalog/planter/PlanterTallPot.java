package dev.t3craft.hq.citadel.catalog.planter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Concept: a tall glazed pot on a dark foot, planted (by seed) with a clump of bamboo or a dwarf
 * cherry tree. Pink terracotta and calcite banding for the cherry, bamboo blocks for the bamboo, so
 * the pot matches what grows in it; small flower pots and petals round its foot on wider plots.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Gradient</b>: polished deepslate foot → coloured body → light calcite lip (on tall plots),
 *     then the plant: the vertical, slender opposite of the low stone planter.</li>
 * <li><b>Bamboo done right</b>: thick stalks, no leaves low, small leaves under the top, large on top;
 *     a second shorter stalk on wider plots so the clump is not a single stick.</li>
 * <li><b>Dwarf cherry</b>: a log trunk with a leaf crown, wider and two-tiered when the plot allows,
 *     pink petals fallen at the foot.</li>
 * </ul>
 * Size: 1–3 × 1–3 × 3+ (the plant grows up to 4 blocks over the pot when there is height). A 1 × 1 plot
 * gets its own design: an urn on a stand (see {@code single}).
 */
public final class PlanterTallPot implements Variant {
	@Override
	public String id() {
		return "planter_tall_pot";
	}

	@Override
	public Family family() {
		return Family.PLANTER;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 3;
	}

	@Override
	public int maxW() {
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		if (w == 1 && d == 1) {
			single(c, p);
			return;
		}
		int cu = w / 2;
		int cv = d / 2;
		boolean bamboo = p.hash(0, 0, 0, 2) == 0;
		int top = p.h() - 1;
		boolean wide = w >= 3 && d >= 3; // a stepped two-tier pot: brown clay stairs climbing to the plant
		int lip = p.h() >= 5 && !wide ? 2 : 1; // pot body y1 (and a calcite lip at y2 on tall plots)
		int plantTop = Math.min(top, lip + (bamboo ? 4 : 3)); // the cherry trunk is short; the crown is bounded by the plot

		// --- the pot
		p.set(c, cu, 0, cv, p.hash(cu, 0, cv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		BlockState body = bamboo ? b(Blocks.BAMBOO_BLOCK) : b(Blocks.MUD_BRICKS); // brown clay body (pink terracotta rendered as a dark red block); bamboo block has axis Y by default
		p.set(c, cu, 1, cv, body);
		if (lip == 2) {
			p.set(c, cu, 2, cv, body); // a two-high glazed body (no white band: stacked stripes read as a candy cane)
		}

		// --- the plant
		if (bamboo) {
			bamboo(c, p, cu, cv, lip + 1, plantTop);
		} else {
			cherry(c, p, cu, cv, lip + 1, plantTop);
		}

		if (wide) {
			// the four corners: low moss, petals, a flowering bush or a slab, never the same twice
			for (int[] k : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
				int h = p.hash(k[0], 0, k[1], 4);
				p.set(c, k[0], 0, k[1], h == 0 ? b(Blocks.FLOWERING_AZALEA) : h == 1 ? b(Blocks.MOSS_CARPET)
						: h == 2 ? petals(p, k[0], k[1]) : slab(Blocks.MUD_BRICK_SLAB));
			}
		}

		// --- round the foot: small pots, petals, a slab step (only where the plot leaves room)
		Direction[] around = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
		for (Direction dir : around) {
			int u = cu + dir.getStepX();
			int v = cv + dir.getStepZ();
			if (!p.has(u, 0, v)) {
				continue;
			}
			if (wide) {
				// [depth] a stepped pot: a dark brick tier at the ground, clay stairs on it climbing to the body
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
				p.set(c, u, 1, v, stairs(Blocks.MUD_BRICK_STAIRS, dir.getOpposite()));
				continue;
			}
			int k = p.hash(u, 0, v, 4);
			BlockState s = switch (k) {
				case 0 -> b(Blocks.POTTED_PINK_TULIP);
				case 1 -> slab(Blocks.POLISHED_DEEPSLATE_SLAB);
				case 2 -> bamboo ? b(Blocks.MOSS_CARPET) : petals(p, u, v);
				default -> bamboo ? b(Blocks.POTTED_BAMBOO) : b(Blocks.POTTED_CHERRY_SAPLING);
			};
			if (p.at(c, u, 0, v) == null) {
				p.set(c, u, 0, v, s);
			}
		}
	}

	/**
	 * The 1 × 1 design: an urn on a stand. A slim deepslate wall stem (on a polished foot when there is
	 * height), an iron cauldron as the bowl with its legs and rim, and a flowering azalea bush spilling over
	 * it. Three different silhouettes stacked (thin post, open bowl, round bush) instead of three cubes.
	 */
	private static void single(CitadelCanvas c, Plot p) {
		int y = 0;
		if (p.h() >= 4) {
			p.set(c, 0, y++, 0, p.hash(0, 0, 0, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		}
		p.set(c, 0, y++, 0, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
		p.set(c, 0, y++, 0, b(Blocks.CAULDRON));
		p.set(c, 0, y, 0, p.hash(0, 1, 0, 3) == 0 ? b(Blocks.AZALEA) : b(Blocks.FLOWERING_AZALEA));
	}

	/** A thick bamboo stalk from y0 to y1 with the right leaves, and a shorter one beside it if there is room. */
	private static void bamboo(CitadelCanvas c, Plot p, int u, int v, int y0, int y1) {
		stalk(c, p, u, v, y0, y1);
		int su = p.has(u + 1, 0, v) ? u + 1 : p.has(u - 1, 0, v) ? u - 1 : -1;
		if (su >= 0 && y1 - 1 > y0) {
			// [silhouette] the second stalk grows from a small bamboo-mosaic pot of its own, two shorter
			p.set(c, su, 0, v, b(Blocks.BAMBOO_MOSAIC));
			stalk(c, p, su, v, 1, y1 - 2);
		}
	}

	private static void stalk(CitadelCanvas c, Plot p, int u, int v, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			BambooLeaves l = y == y1 ? BambooLeaves.LARGE : y == y1 - 1 ? BambooLeaves.SMALL : BambooLeaves.NONE;
			p.set(c, u, y, v, b(Blocks.BAMBOO).setValue(BlockStateProperties.AGE_1, 1).setValue(BlockStateProperties.BAMBOO_LEAVES, l));
		}
	}

	/** A dwarf cherry: a log trunk with a leaf top, and a plus-shaped tier under it on plots wider than 1. */
	private static void cherry(CitadelCanvas c, Plot p, int u, int v, int y0, int y1) {
		if (y1 == y0) {
			// no room for a trunk: a flowering azalea bush (a real shrub shape, not a leaf cube) on the pot
			p.set(c, u, y0, v, b(Blocks.FLOWERING_AZALEA));
			return;
		}
		for (int y = y0; y < y1; y++) {
			p.set(c, u, y, v, b(Blocks.CHERRY_LOG));
		}
		// crown: a ring of leaves round the trunk top (corners too, one in four open), a plus above it
		// and a crown leaf; the trunk is short so the crown sits low and reads as a tree, not a lollipop
		int tier = y1 - 1;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				int lu = u + du;
				int lv = v + dv;
				if ((du == 0 && dv == 0) || !p.has(lu, tier, lv) || p.hash(lu, tier, lv, 4) == 0) {
					continue;
				}
				p.set(c, lu, tier, lv, leaves(Blocks.CHERRY_LEAVES));
			}
		}
		for (Direction dir : new Direction[] {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
			int lu = u + dir.getStepX();
			int lv = v + dir.getStepZ();
			if (p.has(lu, y1, lv)) {
				p.set(c, lu, y1, lv, leaves(Blocks.CHERRY_LEAVES));
			}
		}
		p.set(c, u, y1, v, leaves(Blocks.CHERRY_LEAVES));
		if (y1 + 1 < p.h()) {
			p.set(c, u, y1 + 1, v, leaves(Blocks.CHERRY_LEAVES));
		}
	}

	private static BlockState petals(Plot p, int u, int v) {
		return b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 1, v, 4))
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
	}
}
