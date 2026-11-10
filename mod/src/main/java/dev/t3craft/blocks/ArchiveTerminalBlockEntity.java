package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The archive terminal's block entity. The terminal always shows the whole archive, so it has no binding
 * ({@link #bindable()} is false); it is a {@link BoundBlockEntity} only for the options of its config page
 * (issue #43), which the client's {@code ArchiveTerminalRenderer} reads, and so the renderer can be attached to the
 * screen.
 */
public final class ArchiveTerminalBlockEntity extends BoundBlockEntity {
	public ArchiveTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean bindable() {
		return false;
	}
}
