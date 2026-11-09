package dev.t3craft.hq.citadel.catalog.tree;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Small helpers shared by the maple, birch and pine variants (package-private on purpose). */
final class MixedTreeKit {
	private MixedTreeKit() {
	}

	/** Like {@link Plot#set} but silently skips cells outside the box (canopies are generated generously). */
	static void put(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}

	/** Low index of the central trunk cells of an extent {@code n} (odd: the middle; even: the left middle). */
	static int lo(int n) {
		return (n - 1) / 2;
	}

	/** High index of the central trunk cells (same as {@link #lo} when odd). */
	static int hi(int n) {
		return n / 2;
	}

	static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
