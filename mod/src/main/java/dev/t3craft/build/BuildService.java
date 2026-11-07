package dev.t3craft.build;

import java.util.List;
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

	/** Builds that are not {@link BuildHandle.Phase#DONE} yet. */
	List<BuildHandle> active();

	/** Whether builds can run now (an integrated server is up). */
	boolean available();
}
