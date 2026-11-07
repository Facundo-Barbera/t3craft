package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A status lamp or board bulb. Its colour and light are the {@code status} block state, which
 * {@link LampApplier} sets on the integrated server from the client's thread states. Server-side
 * instances register themselves in {@link LiveLamps} while loaded, so the applier never scans chunks.
 */
public final class LampBlockEntity extends BoundBlockEntity {
	public LampBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			LiveLamps.add(this);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		Level level = getLevel();
		if (level != null && !level.isClientSide()) {
			LiveLamps.add(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		LiveLamps.remove(this);
	}
}
