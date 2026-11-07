package dev.t3craft.build;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a {@link BuildPlan}: a block state at a position relative to the plan origin, and
 * optionally block entity data (e.g. chest contents) loaded after the block is set. Air is allowed
 * and means "clear this spot".
 */
public record Placement(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
	public Placement {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(state, "state");
		pos = pos.immutable();
		blockEntity = blockEntity == null ? null : blockEntity.copy();
	}

	public Placement(BlockPos pos, BlockState state) {
		this(pos, state, null);
	}

	/** Block entity data, copied so callers cannot mutate the plan. */
	@Override
	public @Nullable CompoundTag blockEntity() {
		return blockEntity == null ? null : blockEntity.copy();
	}

	public boolean hasBlockEntity() {
		return blockEntity != null;
	}
}
