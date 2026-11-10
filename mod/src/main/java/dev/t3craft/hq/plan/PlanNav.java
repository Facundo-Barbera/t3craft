package dev.t3craft.hq.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Where an NPC can stand and walk among a set of block states, for generators: the same rules as
 * the client navigation's {@code Collision} (0.6 × 1.8 body, collision boxes, a floor in the cell
 * itself up to half a block or the top of the cell below, moves of at most one block up or down
 * that the body can sweep), so a generator can find every cell NPCs may reach and light it.
 * Cells outside the set read as air. No doors or gates (the generators that use it place none).
 */
final class PlanNav {
	private static final double HALF = 0.3;
	private static final double HEIGHT = 1.8;
	private static final double EPS = 1e-7;

	private final LongFunction<@Nullable BlockState> states;
	private final Map<BlockState, double[][]> shapes = new IdentityHashMap<>();
	private final Map<Long, Double> floors = new HashMap<>();

	PlanNav(LongFunction<@Nullable BlockState> states) {
		this.states = states;
	}

	private double[][] boxes(int x, int y, int z) {
		BlockState s = states.apply(BlockPos.asLong(x, y, z));
		if (s == null || s.isAir()) {
			return new double[0][];
		}
		double[][] b = shapes.get(s);
		if (b == null) {
			List<double[]> out = new ArrayList<>();
			for (AABB a : s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
				out.add(new double[] {a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ});
			}
			b = out.toArray(new double[0][]);
			shapes.put(s, b);
		}
		return b;
	}

	private boolean liquid(int x, int y, int z) {
		BlockState s = states.apply(BlockPos.asLong(x, y, z));
		return s != null && s.getBlock() instanceof LiquidBlock;
	}

	private boolean collides(double x0, double y0, double z0, double x1, double y1, double z1) {
		int bx0 = (int) Math.floor(x0 + EPS);
		int bx1 = (int) Math.floor(x1 - EPS);
		int by0 = (int) Math.floor(y0 + EPS) - 1;
		int by1 = (int) Math.floor(y1 - EPS);
		int bz0 = (int) Math.floor(z0 + EPS);
		int bz1 = (int) Math.floor(z1 - EPS);
		for (int bx = bx0; bx <= bx1; bx++) {
			for (int by = by0; by <= by1; by++) {
				for (int bz = bz0; bz <= bz1; bz++) {
					for (double[] b : boxes(bx, by, bz)) {
						if (bx + b[0] < x1 - EPS && bx + b[3] > x0 + EPS && by + b[1] < y1 - EPS && by + b[4] > y0 + EPS && bz + b[2] < z1 - EPS
								&& bz + b[5] > z0 + EPS) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean bodyCollides(double x, double y, double z) {
		return collides(x - HALF, y, z - HALF, x + HALF, y + HEIGHT, z + HALF);
	}

	private static boolean underFeet(double[] b) {
		return b[0] < 0.5 + HALF && b[3] > 0.5 - HALF && b[2] < 0.5 + HALF && b[5] > 0.5 - HALF && b[0] <= 0.5 && b[3] >= 0.5 && b[2] <= 0.5
				&& b[5] >= 0.5;
	}

	/** Feet height when standing in this cell, or NaN. */
	double floor(int x, int y, int z) {
		long k = BlockPos.asLong(x, y, z);
		Double cached = floors.get(k);
		if (cached != null) {
			return cached;
		}
		double f = computeFloor(x, y, z);
		floors.put(k, f);
		return f;
	}

	private double computeFloor(int x, int y, int z) {
		if (liquid(x, y, z) || liquid(x, y + 1, z)) {
			return Double.NaN;
		}
		double f = Double.NaN;
		for (double[] b : boxes(x, y, z)) {
			if (b[4] <= 0.5 + EPS && underFeet(b) && (Double.isNaN(f) || y + b[4] > f)) {
				f = y + b[4];
			}
		}
		if (Double.isNaN(f)) {
			for (double[] b : boxes(x, y - 1, z)) {
				if (b[4] > 0.5 + EPS && b[4] <= 1.0 + EPS && underFeet(b) && (Double.isNaN(f) || y - 1 + b[4] > f)) {
					f = y - 1 + b[4];
				}
			}
			if (Double.isNaN(f) || liquid(x, y - 1, z) && f < y - 0.5) {
				return Double.NaN;
			}
		}
		return bodyCollides(x + 0.5, f, z + 0.5) ? Double.NaN : f;
	}

	private boolean passage(int ax, int az, double fa, int bx, int bz, double fb) {
		if (Math.abs(fb - fa) > 1.0 + 1e-6) {
			return false;
		}
		double y = Math.max(fa, fb);
		if (!collides(Math.min(ax, bx) + 0.5 - HALF, y, Math.min(az, bz) + 0.5 - HALF, Math.max(ax, bx) + 0.5 + HALF, y + HEIGHT,
				Math.max(az, bz) + 0.5 + HALF)) {
			return true;
		}
		for (int s = 0; s <= 5; s++) {
			double t = s / 5.0;
			if (bodyCollides(ax + 0.5 + (bx - ax) * t, y, az + 0.5 + (bz - az) * t)) {
				return false;
			}
		}
		return true;
	}

	/** Every standable cell reachable from {@code start} (feet cells), in discovery order. */
	Set<BlockPos> reachable(BlockPos start) {
		Set<BlockPos> seen = new LinkedHashSet<>();
		if (Double.isNaN(floor(start.getX(), start.getY(), start.getZ()))) {
			return seen;
		}
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(start.immutable());
		todo.add(start.immutable());
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			double fa = floor(p.getX(), p.getY(), p.getZ());
			for (Direction d : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (seen.contains(n)) {
						continue;
					}
					double fb = floor(n.getX(), n.getY(), n.getZ());
					if (Double.isNaN(fb) || !passage(p.getX(), p.getZ(), fa, n.getX(), n.getZ(), fb)) {
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
