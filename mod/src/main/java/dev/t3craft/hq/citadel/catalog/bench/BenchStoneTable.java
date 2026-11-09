package dev.t3craft.hq.citadel.catalog.bench;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
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
 * A picnic table in stone under a cherry pergola: dark deepslate benches on both long sides, a pale smooth
 * quartz top on stone-brick wall legs, a flowering azalea set into the middle of a long table. Wide plots
 * add the table ends (a lantern post and a planted block); tall wide plots raise a cherry pergola over the
 * whole set: fence posts on deepslate feet, two long beams, cross rafters, blossom between them and a
 * lantern hanging over the table.
 *
 * <ul>
 * <li><b>Gradient</b>: deepslate benches (the base) → stone-brick legs → the pale quartz top → the pink
 *     pergola and blossom over it.</li>
 * <li><b>Depth</b>: seats are stairs (half a block), the top a bottom slab one block up, legs only under
 *     the ends (and the middle of a long table) to leave leg room; the pergola beams and rafters cross at
 *     two levels.</li>
 * <li><b>Silhouette</b>: low and wide when small; a framed room with a light in it when it has a pergola.</li>
 * </ul>
 * Size: 2–5 × 3 × 2+ (bench, table, bench). Width 4–5 adds the ends; width 4–5 with height 5+ the pergola.
 */
public final class BenchStoneTable implements Variant {
	private static final BlockState LEG = b(Blocks.STONE_BRICK_WALL);
	private static final BlockState TOP = slab(Blocks.SMOOTH_QUARTZ_SLAB);

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
		boolean pergola = ends && p.h() >= 5;
		int a = ends ? 1 : 0;
		int z = ends ? w - 2 : w - 1;
		int mid = (a + z) / 2;
		for (int u = a; u <= z; u++) {
			// benches: the front one faces back (towards the table), the back one faces the front
			p.set(c, u, 0, 0, stairs(seat(p, u, 0), Direction.NORTH));
			p.set(c, u, 0, 2, stairs(seat(p, u, 2), Direction.SOUTH));
			// table: wall legs under the ends and the middle of a long table, a pale slab top over all
			boolean leg = u == a || u == z || (z - a >= 3 && u == mid);
			if (leg) {
				p.set(c, u, 0, 1, LEG);
			}
			p.set(c, u, 1, 1, TOP);
		}
		// [finish] a long table carries a flowering azalea set into its middle (no pot floating on a slab)
		if (z - a >= 2) {
			p.set(c, mid, 1, 1, b(Blocks.FLOWERING_AZALEA));
		}
		if (!ends) {
			return;
		}
		// --- table ends: a lantern post on one side (unless the pergola carries the light), a planted block on the other
		boolean lanternLeft = p.hash(0, 0, 1, 2) == 0;
		end(c, p, 0, lanternLeft && !pergola);
		end(c, p, w - 1, !lanternLeft && !pergola);
		if (pergola) {
			pergola(c, p, w);
			return;
		}
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

	/** A cherry pergola over the set: four posts, two long beams (y 3), cross rafters (y 4), blossom, a lantern. */
	private static void pergola(CitadelCanvas c, Plot p, int w) {
		for (int u : new int[] {0, w - 1}) {
			for (int v : new int[] {0, 2}) {
				p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
				p.set(c, u, 1, v, b(Blocks.CHERRY_FENCE));
				p.set(c, u, 2, v, b(Blocks.CHERRY_FENCE));
			}
		}
		BlockState beam = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState rafter = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		for (int u = 0; u < w; u++) {
			p.set(c, u, 3, 0, beam);
			p.set(c, u, 3, 2, beam);
			boolean isRafter = u % 2 == 0 || u == w - 1;
			for (int v = 0; v < 3; v++) {
				if (isRafter) {
					p.set(c, u, 4, v, rafter);
				} else if (p.hash(u, 4, v, 3) != 0 && !(v == 1 && u == w / 2)) {
					p.set(c, u, 4, v, leaves(Blocks.CHERRY_LEAVES)); // blossom between the rafters, gaps by hash, open over the lantern
				}
			}
		}
		// a clump of blossom spilling over one rafter on taller plots
		if (p.h() >= 6) {
			int cu = p.hash(0, 5, 0, 2) == 0 ? 0 : w - 1;
			p.set(c, cu, 5, 0, leaves(Blocks.CHERRY_LEAVES));
			p.set(c, cu, 5, 1, leaves(Blocks.CHERRY_LEAVES));
			p.set(c, w - 1 - cu, 5, 2, leaves(Blocks.CHERRY_LEAVES));
		}
		// a lantern hangs from the middle rafter over the table
		p.put(c, w / 2, 3, 1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
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
			p.set(c, u, 1, 1, p.hash(u, 1, 1, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.POTTED_FERN));
		}
	}

	/** [palette] dark deepslate seats, polished with one in three bricks: weathered, never uniform. */
	private static Block seat(Plot p, int u, int v) {
		return p.hash(u, 0, v, 3) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.POLISHED_DEEPSLATE_STAIRS;
	}
}
