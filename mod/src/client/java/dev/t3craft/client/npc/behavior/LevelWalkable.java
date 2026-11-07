// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
//
// Changes for T3Craft: split out of GridPathfinder as a Walkable over any BlockGetter.
package dev.t3craft.client.npc.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link Walkable} over the client level: a cell is walkable when the block below has a solid top,
 * the feet block is passable or a thin floor layer (carpet, at most 1/8 block) and the head block
 * is passable. Unloaded chunks read as air, i.e. not walkable.
 */
final class LevelWalkable implements Walkable {
	private static final double THIN = 0.125;
	private final BlockGetter level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	LevelWalkable(BlockGetter level) {
		this.level = level;
	}

	@Override
	public double floor(int x, int y, int z) {
		double feetTop = solidTop(x, y, z);
		if (Double.isNaN(feetTop)) {
			feetTop = 0;
		} else if (feetTop > THIN) {
			return Double.NaN; // something solid where the feet go
		}
		if (!Double.isNaN(solidTop(x, y + 1, z))) {
			return Double.NaN; // no headroom
		}
		if (feetTop == 0) {
			double belowTop = solidTop(x, y - 1, z);
			if (Double.isNaN(belowTop) || belowTop < 0.9) {
				return Double.NaN; // nothing to stand on
			}
		}
		return y + feetTop;
	}

	/** Max Y (0..1.5) of the block's collision shape, NaN when it has none. */
	@Override
	public double solidTop(int x, int y, int z) {
		cursor.set(x, y, z);
		BlockState s = level.getBlockState(cursor);
		if (s.isAir()) {
			return Double.NaN;
		}
		VoxelShape shape = s.getCollisionShape(level, cursor);
		return shape.isEmpty() ? Double.NaN : shape.max(Direction.Axis.Y);
	}
}
