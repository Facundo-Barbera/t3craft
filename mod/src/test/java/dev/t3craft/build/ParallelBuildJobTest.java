package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** BuildJob with fronts: parallel placement, shared budget, resume, materials per front. */
class ParallelBuildJobTest {
	static final String STONE = "minecraft:stone";
	static final String PLANKS = "minecraft:oak_planks";
	static final String GLASS = "minecraft:glass";

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** A world in a map that counts writes per position. */
	static final class CountingSink implements BlockSink {
		final Map<BlockPos, BlockState> world = new HashMap<>();
		final Map<BlockPos, Integer> writes = new HashMap<>();

		@Override
		public BuildCursor.Outcome place(BlockPos pos, BlockState state, net.minecraft.nbt.CompoundTag be) {
			if (state.equals(world.get(pos))) {
				return BuildCursor.Outcome.SKIPPED;
			}
			world.put(pos.immutable(), state);
			writes.merge(pos.immutable(), 1, Integer::sum);
			return BuildCursor.Outcome.PLACED;
		}

		@Override
		public boolean matches(BlockPos pos, BlockState state) {
			return state.equals(world.get(pos));
		}
	}

	/** 4x4 floor of stone (y=0), 4x4 of planks (y=1), layered. */
	static BuildPlan plan() {
		return BuildPlan.builder()
				.fill(0, 0, 0, 3, 0, 3, Blocks.STONE.defaultBlockState())
				.fill(0, 1, 0, 3, 1, 3, Blocks.OAK_PLANKS.defaultBlockState())
				.build().layered();
	}

	static BuildJob job(int id, BuildPlan plan, double bpt, BuildCost cost, int fronts) {
		return new BuildJob(id, "t" + id, Level.OVERWORLD, new BlockPos(id * 100, 64, 0), Rotation.NONE, plan, bpt, cost, fronts);
	}

	static BuildCost charged() {
		return BuildCost.of((server, j) -> null);
	}

	@Test
	void fourFrontsPlaceFourTimesFasterKeepingTheLayers() {
		BuildJob one = job(1, plan(), 1, null, 1);
		BuildJob four = job(2, plan(), 1, null, 4);
		assertEquals(4.0, four.rate());
		CountingSink sink = new CountingSink();
		List<Integer> ys = new ArrayList<>();
		Set<Integer> frontsSeen = new HashSet<>();
		int ticks4 = 0;
		while (four.state() != JobState.COMPLETED) {
			four.tick(sink, (j, i, p, s) -> {
				ys.add(p.getY());
				frontsSeen.add(j.frontOf(i));
			});
			ticks4++;
		}
		int ticks1 = 0;
		while (one.state() != JobState.COMPLETED) {
			one.tick(sink, (j, i, p, s) -> {
			});
			ticks1++;
		}
		assertEquals(32, ticks1);
		assertEquals(8, ticks4);
		assertEquals(Set.of(0, 1, 2, 3), frontsSeen, "one builder per front");
		for (int k = 1; k < ys.size(); k++) {
			assertTrue(ys.get(k) >= ys.get(k - 1), "nothing above before below");
		}
	}

	@Test
	void jobsShareTheGlobalBudgetUpToTheConcurrentLimit() {
		BuildBudget budget = new BuildBudget(new BuildTuning(6, 2, 4, 8, false, 40));
		List<BuildJob> jobs = List.of(job(1, plan(), 1, null, 4), job(2, plan(), 1, null, 4), job(3, plan(), 1, null, 4));
		CountingSink sink = new CountingSink();
		long tick = 0;
		int maxPerTick = 0;
		while (jobs.stream().anyMatch(j -> !j.state().isTerminal()) && tick < 1000) {
			budget.beginTick(++tick, 5);
			List<JobState> st = jobs.stream().map(BuildJob::state).toList();
			double[] rates = jobs.stream().mapToDouble(BuildJob::rate).toArray();
			double[] allow = budget.allocate("builders", st, rates);
			int placed = 0;
			for (int i = 0; i < jobs.size(); i++) {
				BuildJob j = jobs.get(i);
				if (allow[i] > 0 || j.state() != JobState.PENDING) {
					placed += j.tick(allow[i], sink, (jb, k, p, s) -> {
					});
				}
			}
			maxPerTick = Math.max(maxPerTick, placed);
			if (tick == 1) {
				assertEquals(JobState.PENDING, jobs.get(2).state(), "third job waits for a slot");
				assertEquals(3, jobs.get(0).placed());
				assertEquals(3, jobs.get(1).placed());
			}
		}
		assertEquals(6, maxPerTick, "never above the global budget");
		assertTrue(jobs.stream().allMatch(j -> j.state() == JobState.COMPLETED));
		assertEquals(96, sink.writes.size());
		assertTrue(sink.writes.values().stream().allMatch(n -> n == 1));
	}

	@Test
	void resumeWithFrontsNeitherDuplicatesNorSkips() {
		CountingSink sink = new CountingSink();
		BuildJob first = job(1, plan(), 1, null, 3);
		for (int t = 0; t < 4; t++) {
			first.tick(sink, (j, i, p, s) -> {
			});
		}
		first.pause();
		first.tick(sink, (j, i, p, s) -> {
		});
		int before = sink.writes.size();
		assertEquals(12, before);
		first.cancel(); // world closed mid-build

		// Resume = same plan again: what is there is skipped, the rest placed once.
		BuildJob again = job(1, plan(), 1, null, 3);
		while (again.state() != JobState.COMPLETED) {
			again.tick(sink, (j, i, p, s) -> {
			});
		}
		assertEquals(12, again.skipped());
		assertEquals(20, again.placed());
		assertEquals(32, sink.writes.size());
		assertTrue(sink.writes.values().stream().allMatch(n -> n == 1), "no block written twice");
	}

	@Test
	void eachFrontPaysAndAShortFrontPausesAloneThenTheJobWaits() {
		// Layer 0: stone on the first half of the rows, glass on the second → front 0 stone, front 1 glass.
		BuildPlan p = BuildPlan.builder()
				.fill(0, 0, 0, 3, 0, 1, Blocks.STONE.defaultBlockState())
				.fill(0, 0, 2, 3, 0, 3, Blocks.GLASS.defaultBlockState())
				.fill(0, 1, 0, 3, 1, 3, Blocks.OAK_PLANKS.defaultBlockState())
				.build().layered();
		BuildJob job = job(1, p, 1, charged(), 2);
		FakeSupply supply = new FakeSupply().put(STONE, 8).put(GLASS, 8).put(PLANKS, 16);
		job.useSupply(supply);
		CountingSink sink = new CountingSink();
		job.tick(sink, (j, i, pos, s) -> {
		}); // layer verified: one stone + one glass
		assertEquals(Map.of(STONE, 1, GLASS, 1), supply.taken);

		supply.stock.put(GLASS, 0); // the player used the glass mid-layer
		for (int t = 0; t < 10; t++) {
			job.tick(sink, (j, i, pos, s) -> {
			});
		}
		assertEquals(8, supply.taken.get(STONE), "the stone front finished its band");
		assertEquals(1, supply.taken.get(GLASS));
		assertEquals(JobState.PAUSED, job.state());
		assertTrue(job.waitingForMaterials());
		assertEquals(Map.of(GLASS, 7), job.missingMaterials());
		assertFalse(supply.taken.containsKey(PLANKS), "never went up a layer");

		supply.put(GLASS, 7);
		for (int t = 0; t < 2 * BuildJob.MATERIAL_RETRY_TICKS + 20 && job.state() != JobState.COMPLETED; t++) {
			job.tick(sink, (j, i, pos, s) -> {
			});
		}
		assertEquals(JobState.COMPLETED, job.state());
		assertEquals(Map.of(STONE, 8, GLASS, 8, PLANKS, 16), supply.taken);
		assertTrue(sink.writes.values().stream().allMatch(n -> n == 1));
	}

	@Test
	void layerStartChecksTheWholeLayerAcrossFronts() {
		BuildJob job = job(1, plan(), 4, charged(), 4);
		FakeSupply supply = new FakeSupply().put(STONE, 16).put(PLANKS, 15);
		job.useSupply(supply);
		CountingSink sink = new CountingSink();
		for (int t = 0; t < 5; t++) {
			job.tick(sink, (j, i, pos, s) -> {
			});
		}
		assertEquals(16, job.placed(), "stone layer done");
		assertTrue(job.waitingForMaterials(), "waits before the planks layer instead of stopping halfway");
		assertEquals(Map.of(PLANKS, 1), job.missingMaterials());
		assertFalse(supply.taken.containsKey(PLANKS));
	}
}
