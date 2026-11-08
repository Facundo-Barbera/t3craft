package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Block light of a set of block states, the way vanilla spreads it: every emitting block
 * ({@link BlockState#getLightEmission()}) is a source, light enters a neighbour that is not a solid
 * render block losing {@code max(1, dampening)} per step. Cells outside the set read as air (no sky
 * light: this is the night-time worst case). Used by the terraces generator to place its
 * floor-embedded lights and by tests to check "light ≥ 10 on every walkable cell".
 */
final class PlanLight {
	private final LongFunction<@Nullable BlockState> states;
	private final Map<Long, Integer> light = new HashMap<>();

	PlanLight(LongFunction<@Nullable BlockState> states) {
		this.states = states;
	}

	/** Light of every cell of a plan. */
	static PlanLight of(BuildPlan plan) {
		Map<Long, BlockState> m = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			m.put(p.rel().asLong(), p.state());
		}
		PlanLight l = new PlanLight(m::get);
		l.addAll(m);
		return l;
	}

	/** Adds every emitting block of {@code m} as a source and spreads it. */
	void addAll(Map<Long, BlockState> m) {
		for (Map.Entry<Long, BlockState> e : m.entrySet()) {
			int em = e.getValue().getLightEmission();
			if (em > 0) {
				source(e.getKey(), em);
			}
		}
	}

	/** Light level at a cell (0..15). */
	int at(BlockPos p) {
		return at(p.asLong());
	}

	int at(long k) {
		Integer v = light.get(k);
		return v == null ? 0 : v;
	}

	/** A new source of {@code level} at {@code k} (the cell's own block emits it) and its spread. */
	void source(long k, int level) {
		if (at(k) >= level) {
			return;
		}
		light.put(k, level);
		ArrayDeque<Long> todo = new ArrayDeque<>();
		todo.add(k);
		while (!todo.isEmpty()) {
			long c = todo.poll();
			int lv = light.get(c);
			if (lv <= 1) {
				continue;
			}
			int x = BlockPos.getX(c);
			int y = BlockPos.getY(c);
			int z = BlockPos.getZ(c);
			for (Direction d : Direction.values()) {
				long n = BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
				BlockState s = states.apply(n);
				int damp = 1;
				if (s != null) {
					if (s.isSolidRender()) {
						continue;
					}
					damp = Math.max(1, s.getLightDampening());
				}
				int nv = lv - damp;
				if (nv > at(n)) {
					light.put(n, nv);
					todo.add(n);
				}
			}
		}
	}
}
