package dev.t3craft.build;

import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * Entry point for other parts of the mod that want something built with animated builders
 * (integration point; get it with {@link Builders#service()}). Call from the client thread.
 */
public interface BuildService {
	/**
	 * Sends {@code crewSize} builders to the site; once there they build {@code plan} at
	 * {@code origin} (rotated) at {@code blocksPerTick}, then leave. Throws
	 * {@link IllegalStateException} when there is no integrated server (multiplayer).
	 */
	BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick, int crewSize);

	/**
	 * Same as {@link #build(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, int)}, but every
	 * block goes through {@code wrap.apply(realSink)}: the caller can refuse a cell (return
	 * {@link BuildCursor.Outcome#SKIPPED} without placing) or watch what is placed. Used by the site to
	 * keep its "never overwrite the player's changes" guard. Services that cannot wrap throw
	 * {@link UnsupportedOperationException}.
	 */
	default BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			int crewSize, UnaryOperator<BlockSink> wrap) {
		throw new UnsupportedOperationException("this build service cannot wrap its block sink");
	}

	/** Builds that are not {@link BuildHandle.Phase#DONE} yet. */
	List<BuildHandle> active();

	/** Whether builds can run now (an integrated server is up). */
	boolean available();
}
