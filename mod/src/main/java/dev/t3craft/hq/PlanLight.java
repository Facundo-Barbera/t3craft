package dev.t3craft.hq;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Block light of a plan, the way the game spreads it: every emitter starts at its
 * {@link BlockState#getLightEmission()}, each step to a neighbour costs {@code max(1, dampening)}
 * and fully dampening blocks stop it. Cells the plan does not set count as air. Pure (no world),
 * so the generator can light its walkable cells and the tests and renders can measure them.
 */
public final class PlanLight {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final LongFunction<@Nullable BlockState> at;
	private final Map<Long, Integer> light = new HashMap<>();
	@SuppressWarnings("unchecked")
	private final ArrayDeque<Long>[] queue = new ArrayDeque[16];

	/** @param at the state at a {@link BlockPos#asLong} position, null for "not in the plan" (air) */
	public PlanLight(LongFunction<@Nullable BlockState> at) {
		this.at = at;
		for (int i = 0; i < queue.length; i++) {
			queue[i] = new ArrayDeque<>();
		}
	}

	/** Light of a whole plan from all its emitters. */
	public static PlanLight of(BuildPlan plan) {
		PlanLight l = new PlanLight(k -> {
			Placement p = plan.at(BlockPos.of(k));
			return p == null ? null : p.state();
		});
		for (Placement p : plan.placements()) {
			int e = p.state().getLightEmission();
			if (e > 0) {
				l.add(p.rel().asLong(), e);
			}
		}
		l.run();
		return l;
	}

	/** Adds an emitter of {@code level} at {@code pos} (call {@link #run()} to spread it). */
	public void add(long pos, int level) {
		if (level > get(pos)) {
			light.put(pos, level);
			queue[Math.min(15, level)].add(pos);
		}
	}

	/** Spreads every pending emitter. */
	public void run() {
		for (int lvl = 15; lvl >= 2; lvl--) {
			ArrayDeque<Long> q = queue[lvl];
			while (!q.isEmpty()) {
				long p = q.poll();
				if (get(p) != lvl) {
					continue;
				}
				for (Direction d : Direction.values()) {
					long n = BlockPos.offset(p, d);
					BlockState s = at.apply(n);
					int damp = (s == null ? AIR : s).getLightDampening();
					if (damp >= 15) {
						continue;
					}
					int nl = lvl - Math.max(1, damp);
					if (nl > get(n)) {
						light.put(n, nl);
						queue[nl].add(n);
					}
				}
			}
		}
		queue[1].clear();
		queue[0].clear();
	}

	/** Block light at a position (0 where no light reaches). */
	public int get(long pos) {
		Integer v = light.get(pos);
		return v == null ? 0 : v;
	}

	public int get(BlockPos pos) {
		return get(pos.asLong());
	}
}
