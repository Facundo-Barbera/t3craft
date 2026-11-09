package dev.t3craft.hq.citadel.catalog.house;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * [depth] Visible support for roof eaves: a beam tail (log) sticking out of the wall just under the eave and
 * an upside-down stair under it that hugs the wall, so an overhang never looks like a floating plank.
 * Both helpers only write into cells that are still empty, so call them after the walls, windows and roof.
 * All directions are local (NORTH = front).
 */
final class EaveKit {
	private EaveKit() {
	}

	/** Whether local {@code (u, y, v)} is inside the box and nothing has been put there yet. */
	static boolean free(CitadelCanvas c, Plot p, int u, int y, int v) {
		if (!p.has(u, y, v)) {
			return false;
		}
		BlockState s = p.at(c, u, y, v);
		return s == null || s.isAir();
	}

	/** A log whose axis points into the wall, under the eave. {@code toWall} is the local direction from the cell to the wall. */
	static void tail(CitadelCanvas c, Plot p, int u, int y, int v, Direction toWall, Block log) {
		if (free(c, p, u, y, v)) {
			p.set(c, u, y, v, log.defaultBlockState().setValue(BlockStateProperties.AXIS, toWall.getAxis()));
		}
	}

	/** A beam tail at {@code y} and an upside-down stair, high side to the wall, right under it at {@code y - 1}. */
	static void corbel(CitadelCanvas c, Plot p, int u, int y, int v, Direction toWall, Block log, Block stair) {
		if (!free(c, p, u, y, v) || !free(c, p, u, y - 1, v)) {
			return;
		}
		tail(c, p, u, y, v, toWall, log);
		p.set(c, u, y - 1, v, CitadelCanvas.stairs(stair, toWall).setValue(BlockStateProperties.HALF, Half.TOP));
		// a pendant: a thin post hanging under the corbel makes the bracket two blocks long, readable from far away
		if (free(c, p, u, y - 2, v)) {
			p.set(c, u, y - 2, v, fenceFor(log).defaultBlockState());
		}
	}

	private static Block fenceFor(Block log) {
		if (log == Blocks.CHERRY_LOG) {
			return Blocks.CHERRY_FENCE;
		}
		if (log == Blocks.SPRUCE_LOG) {
			return Blocks.SPRUCE_FENCE;
		}
		return Blocks.DARK_OAK_FENCE;
	}
}
