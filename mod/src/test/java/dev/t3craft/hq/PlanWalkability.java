package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where an NPC can stand and walk in a {@link BuildPlan}, with the same rule as the client
 * pathfinder's {@code LevelWalkable}: the feet cell has no collision or only a thin floor layer
 * (carpet, at most 1/8 block), the head cell has no collision, and when the feet cell is empty the
 * block below has a (nearly) full solid top. Cells the plan does not set are unknown terrain and
 * never count as walkable (conservative).
 */
final class PlanWalkability {
	static final double THIN = 0.125;

	private final BuildPlan plan;

	PlanWalkability(BuildPlan plan) {
		this.plan = plan;
	}

	/** Max Y of the collision shape of a planned cell; NaN when it has none; +inf when unknown. */
	double solidTop(BlockPos p) {
		Placement pl = plan.at(p);
		if (pl == null) {
			return Double.POSITIVE_INFINITY;
		}
		BlockState s = pl.state();
		if (s.isAir()) {
			return Double.NaN;
		}
		VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, p);
		return shape.isEmpty() ? Double.NaN : shape.max(Direction.Axis.Y);
	}

	/** Feet height when standing in {@code feet}, or NaN when an NPC cannot stand there. */
	double floor(BlockPos feet) {
		double feetTop = solidTop(feet);
		if (Double.isNaN(feetTop)) {
			feetTop = 0;
		} else if (feetTop > THIN) {
			return Double.NaN;
		}
		if (!Double.isNaN(solidTop(feet.above()))) {
			return Double.NaN;
		}
		if (feetTop == 0) {
			double below = solidTop(feet.below());
			if (Double.isNaN(below) || below < 0.9 || Double.isInfinite(below)) {
				return Double.NaN;
			}
		}
		return feet.getY() + feetTop;
	}

	boolean standable(BlockPos feet) {
		return !Double.isNaN(floor(feet));
	}

	/**
	 * Every standable cell reachable from {@code start} by 4-way moves, stepping up or down at most
	 * one block (with headroom over the start
	 * cell to step up), like the client {@code GridPathfinder}.
	 */
	Set<BlockPos> reachable(BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		if (!standable(start)) {
			return seen;
		}
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(start.immutable());
		todo.add(start.immutable());
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (seen.contains(n) || !standable(n)) {
						continue;
					}
					if (dy > 0 && !Double.isNaN(solidTop(p.above(2)))) {
						continue; // no headroom to step up
					}
					seen.add(n);
					todo.add(n);
				}
			}
		}
		return seen;
	}
}
