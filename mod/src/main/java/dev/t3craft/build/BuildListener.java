package dev.t3craft.build;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Job events from {@link BuildManager}, always called on the server thread. Implementations that
 * live on the client (the animated crew) must hand the events over to their own thread and must not
 * touch server objects. All methods default to no-op.
 */
public interface BuildListener {
	default void started(BuildJob job) {
	}

	default void placed(BuildJob job, int index, BlockPos worldPos, BlockState worldState) {
	}

	/**
	 * A charged job started waiting for materials ({@code missing}: item id → count lacking) or got
	 * them and resumed ({@code missing} empty). Same data as {@link BuildJob#missingMaterials()}.
	 */
	default void materials(BuildJob job, Map<String, Integer> missing) {
	}

	/** The job reached {@link JobState#COMPLETED} or {@link JobState#CANCELLED}, or the manager was cleared. */
	default void finished(BuildJob job, JobState state) {
	}
}
