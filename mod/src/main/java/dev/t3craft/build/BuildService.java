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
	 * {@link IllegalStateException} when there is no integrated server (multiplayer), or its subclass
	 * {@link BuildDeniedException} when the survival policy refuses (spectator, adventure). In survival
	 * the blocks are charged as they are placed ({@link BuildJob#missingMaterials()}).
	 */
	BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick, int crewSize);

	/** Who pays for a build. */
	enum Billing {
		/**
		 * Ask the survival policy (default of the method above): it may refuse
		 * ({@link BuildDeniedException}), build for free (creative) or charge materials (survival).
		 */
		POLICY,
		/** Always free and never refused by the policy (the demo world, trusted callers). */
		FREE
	}

	/**
	 * Like {@link #build(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, int)}, choosing who
	 * pays. The default ignores {@code billing} so other implementations keep working.
	 */
	default BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick, int crewSize,
			Billing billing) {
		return build(label, dimension, origin, rotation, plan, blocksPerTick, crewSize);
	}

	/**
	 * Like {@link #build(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, int, Billing)}, but every
	 * block goes through {@code wrap.apply(realSink)}: the caller can refuse a cell (return
	 * {@link BuildCursor.Outcome#SKIPPED} without placing) or watch what is placed. Used by the site and the
	 * HQ to keep the "never overwrite the player's changes" guard. Services that cannot wrap throw
	 * {@link UnsupportedOperationException} for a non-null {@code wrap}.
	 */
	default BuildHandle build(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			int crewSize, Billing billing, @org.jspecify.annotations.Nullable UnaryOperator<BlockSink> wrap) {
		if (wrap == null) {
			return build(label, dimension, origin, rotation, plan, blocksPerTick, crewSize, billing);
		}
		throw new UnsupportedOperationException("this build service cannot wrap its block sink");
	}

	/** Builds that are not {@link BuildHandle.Phase#DONE} yet. */
	List<BuildHandle> active();

	/** Whether builds can run now (an integrated server is up). */
	boolean available();
}
