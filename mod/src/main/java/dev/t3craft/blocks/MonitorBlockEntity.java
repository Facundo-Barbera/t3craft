package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A monitor. Holds only its binding; the client's {@code MonitorRenderer} draws the bound thread's
 * screen from state the client already has (no subscription of its own). A new binding can merge or
 * split a large screen ({@link MonitorWall}: bindings must agree), so it asks the block to recompute
 * its wall next tick.
 */
public final class MonitorBlockEntity extends BoundBlockEntity {
	public MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected void bindingChanged(Level level) {
		if (getBlockState().getBlock() instanceof MonitorBlock m) {
			level.scheduleTick(getBlockPos(), m, 1);
		}
	}
}
