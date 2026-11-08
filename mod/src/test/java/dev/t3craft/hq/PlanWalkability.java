package dev.t3craft.hq;

import dev.t3craft.client.npc.behavior.BlockShapes;
import dev.t3craft.client.npc.behavior.Collision;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where an NPC can stand and walk in a {@link BuildPlan}, judged by the client navigation's own
 * {@link Collision} (0.6 × 1.8 body, real collision boxes, body sweep between cells, stair
 * landings) over the plan's block states, the same way {@code LevelBlocks} feeds it the level.
 * Cells the plan does not set read as air (like unloaded chunks: no ground there), so nothing
 * outside the plan counts as walkable.
 */
final class PlanWalkability {
	private final Collision collision;

	PlanWalkability(BuildPlan plan) {
		this.collision = new Collision(new Shapes(plan));
	}

	/** {@link BlockShapes} over the plan (doors and fence gates passable, like {@code LevelBlocks}). */
	private record Shapes(BuildPlan plan) implements BlockShapes {
		@Override
		public List<dev.t3craft.client.npc.behavior.Box> boxes(int x, int y, int z) {
			BlockPos p = new BlockPos(x, y, z);
			Placement pl = plan.at(p);
			if (pl == null || pl.state().isAir()) {
				return List.of();
			}
			BlockState s = pl.state();
			if (s.getBlock() instanceof DoorBlock d && d.type().canOpenByHand() || s.getBlock() instanceof FenceGateBlock) {
				return List.of();
			}
			VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, p);
			List<dev.t3craft.client.npc.behavior.Box> out = new ArrayList<>();
			for (AABB a : shape.toAabbs()) {
				out.add(new dev.t3craft.client.npc.behavior.Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
			}
			return out;
		}

		@Override
		public boolean liquid(int x, int y, int z) {
			Placement pl = plan.at(new BlockPos(x, y, z));
			return pl != null && pl.state().getBlock() instanceof LiquidBlock;
		}
	}

	/** Feet height when standing in {@code feet}, or NaN when an NPC cannot stand there. */
	double floor(BlockPos feet) {
		return collision.floor(feet.getX(), feet.getY(), feet.getZ());
	}

	/** The body fits exactly at this point, standing on a floor (so navigation stops right there). */
	boolean fits(double x, double y, double z) {
		return collision.fits(x, y, z);
	}

	/** One move of the client pathfinder from standable {@code a} to standable {@code b} (neighbours, dy -1..1). */
	boolean step(BlockPos a, BlockPos b) {
		double fa = floor(a);
		double fb = floor(b);
		return !Double.isNaN(fa) && !Double.isNaN(fb)
				&& collision.passage(a.getX(), a.getY(), a.getZ(), fa, b.getX(), b.getY(), b.getZ(), fb);
	}

	/**
	 * Every standable cell reachable from {@code start} by straight moves (one level, or a step up or
	 * down) that the body can sweep through: the moves of the client {@code GridPathfinder}, whose
	 * diagonals need both straight neighbours and so reach nothing more.
	 */
	Set<BlockPos> reachable(BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		if (Double.isNaN(floor(start))) {
			return seen;
		}
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(start.immutable());
		todo.add(start.immutable());
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			double fa = floor(p);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (seen.contains(n)) {
						continue;
					}
					double fb = floor(n);
					if (Double.isNaN(fb) || !collision.passage(p.getX(), p.getY(), p.getZ(), fa, n.getX(), n.getY(), n.getZ(), fb)) {
						continue;
					}
					seen.add(n);
					todo.add(n);
				}
			}
		}
		return seen;
	}
}
