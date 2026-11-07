package dev.t3craft.hq;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Test plans with a thread wall reserved through the {@code thread_wall} anchors (tests of other packages). */
public final class WallPlans {
	private WallPlans() {
	}

	/**
	 * A small plan: a solid wall of {@code state} filling the box {@code from..to} (both included) and
	 * the {@link AnchorNames#THREAD_WALL} anchor on {@code from} facing {@code facing}; plus
	 * {@link AnchorNames#THREAD_WALL_END} on {@code end} when not null.
	 */
	public static BuildPlan wall(Direction facing, BlockPos from, BlockPos to, @Nullable BlockPos end, BlockState state) {
		PlanBuilder b = new PlanBuilder();
		try (PlanBuilder.Canvas c = b.module("hub", Box.of(-10, 0, -10, 10, 10, 10), java.util.List.of())) {
			c.fill(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), state);
		}
		b.anchor(Anchor.face(AnchorNames.THREAD_WALL, from, facing));
		if (end != null) {
			b.anchor(Anchor.face(AnchorNames.THREAD_WALL_END, end, facing));
		}
		return b.build();
	}

	/** The same plan without any wall anchor. */
	public static BuildPlan noWall(BlockPos from, BlockPos to, BlockState state) {
		PlanBuilder b = new PlanBuilder();
		try (PlanBuilder.Canvas c = b.module("hub", Box.of(-10, 0, -10, 10, 10, 10), java.util.List.of())) {
			c.fill(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), state);
		}
		return b.build();
	}
}
