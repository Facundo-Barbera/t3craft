package dev.t3craft.hq.citadel.catalog.bench;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Concept: a picnic spot in weathered stone: a smooth-stone table top on polished deepslate wall
 * pedestals, with a stone-brick bench on each long side facing it. A long table carries a raised
 * block with a pot of flowers; the table ends get a lantern post and a planted stone block, moss creeps in the corners.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Proportion</b>: seats are stairs (half a block), the table top a bottom slab one block up,
 *     so the table reads 1.5 high over 0.5 seats; pedestals only under the ends and the middle leave
 *     leg room.</li>
 * <li><b>Palette</b>: deepslate (pedestals, end posts) → stone bricks mixed with mossy ones by hash
 *     (benches) → light smooth stone (top), gold lantern and green as accents.</li>
 * <li><b>Silhouette</b>: low and wide, the lantern post the only vertical, unlike the cherry bench.</li>
 * </ul>
 * Size: 2–5 × 3 × 2+ (it needs the three rows: bench, table, bench). Width 4–5 adds the end pieces.
 */
public final class BenchStoneTable implements Variant {
	private static final BlockState PEDESTAL = b(Blocks.POLISHED_DEEPSLATE_WALL); // no sides set: a round post
	private static final BlockState TOP = slab(Blocks.SMOOTH_STONE_SLAB);

	@Override
	public String id() {
		return "bench_stone_table";
	}

	@Override
	public Family family() {
		return Family.BENCH;
	}

	@Override
	public int minW() {
		return 2;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 2;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		boolean ends = w >= 4;
		int a = ends ? 1 : 0;
		int z = ends ? w - 2 : w - 1;
		int mid = (a + z) / 2;

		for (int u = a; u <= z; u++) {
			// benches: the front one faces back (towards the table), the back one faces the front
			p.set(c, u, 0, 0, stairs(benchBlock(p, u, 0), Direction.NORTH));
			p.set(c, u, 0, 2, stairs(benchBlock(p, u, 2), Direction.SOUTH));
			// table: pedestals under the ends and the middle, slab top over all
			boolean leg = u == a || u == z || (z - a >= 3 && u == mid);
			if (leg) {
				p.set(c, u, 0, 1, PEDESTAL);
			}
			p.set(c, u, 1, 1, TOP);
		}

		// [finish] a long table gets a raised stone block in the middle with a pot of flowers on it
		// (a pot straight on the bottom slab would float half a block)
		if (z - a >= 2 && p.h() >= 3) {
			p.set(c, mid, 1, 1, b(Blocks.CHISELED_STONE_BRICKS));
			p.set(c, mid, 2, 1, pot(p));
		}

		if (!ends) {
			return;
		}
		// --- table ends: a lantern post on one side, a planted block on the other
		boolean lanternLeft = p.hash(0, 0, 1, 2) == 0;
		end(c, p, 0, lanternLeft);
		end(c, p, w - 1, !lanternLeft);
		// [finish] moss and petals soften the four corners where stone meets the grass
		for (int u : new int[] {0, w - 1}) {
			for (int v : new int[] {0, 2}) {
				int k = p.hash(u, 0, v, 4);
				if (k == 0) {
					continue; // one corner in four stays plain grass
				}
				p.set(c, u, 0, v, k == 1 ? b(Blocks.MOSS_CARPET)
						: b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, k + 1)
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			}
		}
	}

	private static void end(CitadelCanvas c, Plot p, int u, boolean lantern) {
		if (lantern) {
			p.set(c, u, 0, 1, b(Blocks.CHISELED_DEEPSLATE));
			if (p.h() >= 3) {
				p.set(c, u, 1, 1, b(Blocks.POLISHED_DEEPSLATE_WALL));
				p.set(c, u, 2, 1, b(Blocks.LANTERN));
			} else {
				p.set(c, u, 1, 1, b(Blocks.LANTERN));
			}
		} else {
			p.set(c, u, 0, 1, b(Blocks.CHISELED_STONE_BRICKS));
			p.set(c, u, 1, 1, b(Blocks.FLOWERING_AZALEA));
		}
	}

	/** [palette] stone brick benches, one block in three mossy: weathered, never uniform. */
	private static Block benchBlock(Plot p, int u, int v) {
		return p.hash(u, 0, v, 3) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS;
	}

	private static BlockState pot(Plot p) {
		return switch (p.hash(0, 2, 1, 3)) {
			case 0 -> b(Blocks.POTTED_PINK_TULIP);
			case 1 -> b(Blocks.POTTED_AZALEA);
			default -> b(Blocks.POTTED_CORNFLOWER);
		};
	}
}
