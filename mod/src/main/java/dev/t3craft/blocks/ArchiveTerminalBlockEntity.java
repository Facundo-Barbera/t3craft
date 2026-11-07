package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The archive terminal's block entity. Holds no data (the terminal always shows the whole archive);
 * it exists so the client can attach {@code ArchiveTerminalRenderer} to the screen.
 */
public final class ArchiveTerminalBlockEntity extends BlockEntity {
	public ArchiveTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
