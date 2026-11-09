package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The console terminal's block entity: only its binding ({@code thread:<id>} or empty), so
 * {@code /t3craft blocks link} works on it like on a monitor. It has no renderer: the screen is baked in the model.
 */
public final class ConsoleTerminalBlockEntity extends BoundBlockEntity {
	public ConsoleTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
