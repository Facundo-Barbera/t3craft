package dev.t3craft.build;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A {@link BuildPlan} placed at an origin in a dimension, with a rotation and a per-tick block
 * budget. Ticked by {@link BuildManager} on the server thread; {@link #pause()},
 * {@link #resume()}, {@link #cancel()} and progress getters are safe from any thread.
 *
 * <p>World position of entry {@code i}: {@code origin + rotate(plan[i].pos)}; its state is rotated
 * too ({@link BlockState#rotate}), so facing blocks (doors, stairs, torches) turn with the plan.
 */
public final class BuildJob {
	/** Called for every block actually placed (not for skipped ones), on the server thread. */
	@FunctionalInterface
	public interface PlacedCallback {
		void placed(BuildJob job, int index, BlockPos worldPos, BlockState worldState);
	}

	private final int id;
	private final String label;
	private final ResourceKey<Level> dimension;
	private final BlockPos origin;
	private final Rotation rotation;
	private final BuildPlan plan;
	private final BuildCursor cursor;

	public BuildJob(int id, String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick) {
		this.id = id;
		this.label = Objects.requireNonNull(label, "label");
		this.dimension = Objects.requireNonNull(dimension, "dimension");
		this.origin = origin.immutable();
		this.rotation = Objects.requireNonNull(rotation, "rotation");
		this.plan = Objects.requireNonNull(plan, "plan");
		this.cursor = new BuildCursor(plan.size(), blocksPerTick);
	}

	/** Places up to the budget into {@code sink}. Returns the number of entries processed. */
	public int tick(BlockSink sink, PlacedCallback onPlaced) {
		return cursor.tick(i -> {
			Placement p = plan.get(i);
			BlockPos wp = worldPos(i);
			BlockState ws = worldState(i);
			BuildCursor.Outcome o = sink.place(wp, ws, p.blockEntity());
			if (o == BuildCursor.Outcome.PLACED) {
				onPlaced.placed(this, i, wp, ws);
			}
			return o;
		});
	}

	public BlockPos worldPos(int index) {
		return origin.offset(plan.get(index).pos().rotate(rotation));
	}

	public BlockState worldState(int index) {
		return plan.get(index).state().rotate(rotation);
	}

	/** World-space bounding box of the whole plan. */
	public BoundingBox worldBounds() {
		return boundsOf(plan, origin, rotation);
	}

	/** World-space bounding box a plan would occupy at {@code origin} with {@code rotation}. */
	public static BoundingBox boundsOf(BuildPlan plan, BlockPos origin, Rotation rotation) {
		BlockPos a = origin.offset(plan.min().rotate(rotation));
		BlockPos b = origin.offset(plan.max().rotate(rotation));
		return BoundingBox.fromCorners(a, b);
	}

	public boolean pause() {
		return cursor.pause();
	}

	public boolean resume() {
		return cursor.resume();
	}

	public boolean cancel() {
		return cursor.cancel();
	}

	public JobState state() {
		return cursor.state();
	}

	public double progress() {
		return cursor.progress();
	}

	public int done() {
		return cursor.done();
	}

	public int total() {
		return cursor.total();
	}

	public int placed() {
		return cursor.placed();
	}

	public int skipped() {
		return cursor.skipped();
	}

	public int failed() {
		return cursor.failed();
	}

	public int id() {
		return id;
	}

	public String label() {
		return label;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	public BuildPlan plan() {
		return plan;
	}

	public double blocksPerTick() {
		return cursor.blocksPerTick();
	}

	@Override
	public String toString() {
		return "BuildJob#" + id + "(" + label + ", " + state() + ", " + done() + "/" + total() + ")";
	}
}
