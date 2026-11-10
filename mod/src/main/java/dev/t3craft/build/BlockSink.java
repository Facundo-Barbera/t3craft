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

	/**
	 * Whether the world already has {@code state} at {@code worldPos} (placing would be skipped), so a
	 * charged job does not ask for materials it will not use. Default: unknown, assume not.
	 */
	default boolean matches(BlockPos worldPos, BlockState state) {
		return false;
	}

	/**
	 * fix/hq-load-build-portal: whether the chunk of {@code worldPos} is loaded, so a block can be placed there now.
	 * A placement into an unloaded chunk fails for good ({@link BuildCursor.Outcome#FAILED}: the entry counts as
	 * done and is never tried again), which left holes in the HQ whenever the build ran farther than the chunks
	 * around the player. The radial build asks first and retries later instead. Default: loaded (sinks without chunks).
	 */
	default boolean loaded(BlockPos worldPos) {
		return true;
	}
}
