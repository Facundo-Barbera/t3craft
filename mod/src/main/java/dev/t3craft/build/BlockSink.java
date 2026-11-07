package dev.t3craft.build;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where a {@link BuildJob} puts blocks, in world coordinates. {@link ServerLevelSink} is the real
 * one (integrated or dedicated server thread); tests or a future networked implementation can
 * provide their own. Integration point: anything that wants to place blocks differently (e.g. a
 * server-side mod over the network, or a dry-run preview) implements this.
 */
@FunctionalInterface
public interface BlockSink {
	BuildCursor.Outcome place(BlockPos worldPos, BlockState state, @Nullable CompoundTag blockEntity);
}
