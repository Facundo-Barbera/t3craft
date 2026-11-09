package dev.t3craft.hq.citadel.catalog.planter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Concept: a heavy stone-brick planter, weathered (mossy and cracked bricks by hash) with chiseled
 * corner posts capped by slabs, holding a mounded shrub: leaf blocks that rise towards the back and
 * the middle, crowned by a flowering azalea, with tall flowers (peony, lilac) and small ones round it.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Silhouette</b>: the foliage is a mound, highest at its core and stepping down to the rim, so
 *     even a 1 × 1 planter reads as a shaped bush (leaf ball + azalea on top), not a cube.</li>
 * <li><b>Palette</b>: stone bricks / mossy / cracked (box), chiseled stone (posts), soil of rooted dirt
 *     and moss inside, three leaf types, pink and purple flowers.</li>
 * <li><b>Depth</b>: corner caps stand half a block above the rim; on deep plots the soil sits inside a
 *     rim and the flowers lean against it.</li>
 * </ul>
 * Size: 1–4 × 1–3 × 2+ (tall flowers and the azalea crown need 3).
 */
public final class PlanterStoneShrub implements Variant {
	@Override
	public String id() {
		return "planter_stone_shrub";
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
		return 2;
	}

	@Override
	public int maxW() {
		return 4;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2; // shrub core: middle column, back row (centre row on 3-deep plots)
		int cv = d == 3 ? 1 : d - 1;
		boolean posts = w >= 3; // the end cells become chiseled posts with slab caps
		boolean tall = p.h() >= 3;

		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				boolean rim = u == 0 || u == w - 1 || v == 0 || v == d - 1;
				boolean post = posts && (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
				// --- the box (y0)
				if (post) {
					p.set(c, u, 0, v, b(Blocks.CHISELED_STONE_BRICKS));
					p.set(c, u, 1, v, slab(Blocks.STONE_BRICK_SLAB)); // [finish] a cap on every corner post
					continue;
				}
				p.set(c, u, 0, v, rim ? brick(p, u, v) : p.hash(u, 0, v, 3) == 0 ? b(Blocks.MOSS_BLOCK) : b(Blocks.ROOTED_DIRT));

				// --- the planting (y1+): a mound around the core
				int dist = Math.abs(u - cu) + Math.abs(v - cv);
				if (dist == 0) {
					if (tall) {
						p.set(c, u, 1, v, leaves(Blocks.AZALEA_LEAVES));
						p.set(c, u, 2, v, b(Blocks.FLOWERING_AZALEA)); // the crown: a smaller bush on the ball
					} else {
						p.set(c, u, 1, v, b(Blocks.FLOWERING_AZALEA)); // low plot: a real bush, not a leaf cube
					}
				} else if (dist == 1 && (d >= 2 || w >= 4)) {
					p.set(c, u, 1, v, p.hash(u, 1, v, 2) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				} else {
					flower(c, p, u, v, tall);
				}
			}
		}
	}

	/** [palette] the box: plain stone bricks with one in four mossy and one in six cracked. */
	private static BlockState brick(Plot p, int u, int v) {
		int k = p.hash(u, 0, v, 12);
		return k < 3 ? b(Blocks.MOSSY_STONE_BRICKS) : k < 5 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
	}

	/** A flower on a rim or soil cell: tall ones when there is room, small ones otherwise. */
	private static void flower(CitadelCanvas c, Plot p, int u, int v, boolean tall) {
		int k = p.hash(u, 1, v, 6);
		if (tall && k < 2) {
			Block f = k == 0 ? Blocks.PEONY : Blocks.LILAC;
			p.set(c, u, 1, v, b(f).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			p.set(c, u, 2, v, b(f).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			return;
		}
		Block f = switch (k) {
			case 2 -> Blocks.ALLIUM;
			case 3 -> Blocks.PINK_TULIP;
			case 4 -> Blocks.LILY_OF_THE_VALLEY;
			default -> Blocks.CORNFLOWER;
		};
		p.set(c, u, 1, v, b(f));
	}
}
