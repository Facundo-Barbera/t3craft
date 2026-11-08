package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * fix/hq-build-parallel: the {@link PlacementOrder} contract, for any strategy. Another order (e.g.
 * builders by proximity) reuses {@link #check} with its own factory.
 */
public class PlacementOrderContractTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** A random layered plan with some "clear, then place" pairs at one position. */
	public static BuildPlan randomPlan(Random r) {
		List<Placement> ps = new ArrayList<>();
		int w = 2 + r.nextInt(8);
		int d = 2 + r.nextInt(8);
		int h = 1 + r.nextInt(4);
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < d; z++) {
				for (int x = 0; x < w; x++) {
					if (r.nextInt(4) == 0) {
						continue;
					}
					BlockPos p = new BlockPos(x, y, z);
					if (r.nextInt(5) == 0) {
						ps.add(new Placement(p, Blocks.AIR.defaultBlockState()));
					}
					ps.add(new Placement(p, Blocks.STONE.defaultBlockState()));
				}
			}
		}
		return BuildPlan.of(ps).layered();
	}

	/**
	 * Drives {@code factory} on random plans with random fronts, random front picks and random
	 * "blocked" retries: every index once, never one index on two fronts, same-position order kept.
	 */
	public static void check(PlacementOrder.Factory factory, long seed) {
		Random r = new Random(seed);
		for (int round = 0; round < 200; round++) {
			BuildPlan plan = randomPlan(r);
			PlacementOrder o = factory.create(plan, 1 + r.nextInt(6));
			assertEquals(plan.size(), o.total());
			Set<Integer> applied = new HashSet<>();
			Map<Long, Integer> lastAt = new HashMap<>();
			int guard = 0;
			while (!o.done()) {
				assertTrue(++guard < 100_000, "order never finishes");
				Map<Integer, Integer> offered = new HashMap<>();
				for (int f = 0; f < o.fronts(); f++) {
					int i = o.peek(f);
					if (i >= 0) {
						assertEquals(i, o.peek(f), "peek is stable");
						Integer other = offered.put(i, f);
						assertTrue(other == null, "index " + i + " on two fronts");
						assertFalse(applied.contains(i), "index " + i + " handed out again");
					}
				}
				assertFalse(offered.isEmpty(), "not done but nothing open");
				List<Integer> fs = new ArrayList<>(offered.values());
				int f = fs.get(r.nextInt(fs.size()));
				if (r.nextInt(4) == 0) {
					continue; // blocked: peeked again later
				}
				int i = o.peek(f);
				long key = plan.get(i).pos().asLong();
				Integer prev = lastAt.put(key, i);
				assertTrue(prev == null || prev < i, "same position keeps plan order");
				assertEquals(o.frontOf(i), o.frontOf(i));
				o.advance(f);
				applied.add(i);
			}
			assertEquals(plan.size(), applied.size(), "every index once");
			assertEquals(0, o.pendingInStage().length);
		}
	}

	@Test
	void layeredOrderKeepsTheContract() {
		check(LayeredOrder.FACTORY, 11);
	}

	@Test
	void layeredOrderNeverGoesUpBeforeTheLayerIsDone() {
		BuildPlan plan = randomPlan(new Random(3));
		PlacementOrder o = LayeredOrder.FACTORY.create(plan, 4);
		int y = Integer.MIN_VALUE;
		while (!o.done()) {
			for (int f = 0; f < o.fronts(); f++) {
				int i = o.peek(f);
				if (i >= 0) {
					assertTrue(plan.get(i).pos().getY() >= y);
					y = plan.get(i).pos().getY();
					o.advance(f);
					break;
				}
			}
		}
	}

	@Test
	void jobsUseTheInstalledOrder() {
		PlacementOrder.Factory[] seen = new PlacementOrder.Factory[1];
		PlacementOrder.Factory spy = (plan, fronts) -> {
			seen[0] = LayeredOrder.FACTORY;
			return LayeredOrder.FACTORY.create(plan, fronts);
		};
		try {
			PlacementOrders.use(spy);
			new BuildJob(1, "x", net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE,
					randomPlan(new Random(1)), 1, null, 2);
			assertEquals(LayeredOrder.FACTORY, seen[0]);
		} finally {
			PlacementOrders.use(null);
		}
		assertEquals(LayeredOrder.FACTORY, PlacementOrders.current());
	}
}
