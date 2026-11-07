package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChargedBuildJobTest {
	static final String STONE = "minecraft:stone";
	static final String PLANKS = "minecraft:oak_planks";

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** A world in a map. */
	static final class MapSink implements BlockSink {
		final Map<BlockPos, BlockState> world = new HashMap<>();

		@Override
		public BuildCursor.Outcome place(BlockPos pos, BlockState state, net.minecraft.nbt.CompoundTag be) {
			if (state.equals(world.get(pos))) {
				return BuildCursor.Outcome.SKIPPED;
			}
			world.put(pos.immutable(), state);
			return BuildCursor.Outcome.PLACED;
		}

		@Override
		public boolean matches(BlockPos pos, BlockState state) {
			return state.equals(world.get(pos));
		}
	}

	/** y=0: 4 stone; y=1: 4 planks. */
	static BuildPlan plan() {
		return BuildPlan.builder()
				.fill(0, 0, 0, 1, 0, 1, Blocks.STONE.defaultBlockState())
				.fill(0, 1, 0, 1, 1, 1, Blocks.OAK_PLANKS.defaultBlockState())
				.build();
	}

	static BuildJob job(BuildCost cost) {
		return new BuildJob(1, "test", Level.OVERWORLD, BlockPos.ZERO, Rotation.NONE, plan(), 100, cost);
	}

	static final BuildJob.PlacedCallback NONE = (j, i, p, s) -> {
	};

	static BuildCost charged() {
		return BuildCost.of((server, j) -> null);
	}

	@Test
	void freeJobPlacesEverythingWithoutCharging() {
		BuildJob job = job(null);
		MapSink sink = new MapSink();
		job.tick(sink, NONE);
		job.tick(sink, NONE);
		assertEquals(JobState.COMPLETED, job.state());
		assertEquals(8, sink.world.size());
		assertFalse(job.charged());
		assertEquals(Map.of(), job.requiredMaterials());
		assertEquals(Map.of(), job.missingMaterials());
	}

	@Test
	void chargedJobConsumesPerLayerPausesAndResumesByItself() {
		BuildJob job = job(charged());
		FakeSupply supply = new FakeSupply().put(STONE, 4);
		job.useSupply(supply);
		MapSink sink = new MapSink();
		assertEquals(Map.of(STONE, 4, PLANKS, 4), job.requiredMaterials());

		job.tick(sink, NONE);
		assertEquals(4, job.placed(), "the stone layer");
		assertEquals(JobState.PAUSED, job.state());
		assertTrue(job.waitingForMaterials());
		assertEquals(Map.of(PLANKS, 4), job.missingMaterials());
		assertTrue(job.pollMaterialsChanged());
		assertFalse(job.pollMaterialsChanged());
		assertEquals(Map.of(STONE, 4), supply.taken);

		// Not enough yet: still waiting after the retry interval.
		supply.put(PLANKS, 3);
		for (int t = 0; t < BuildJob.MATERIAL_RETRY_TICKS; t++) {
			job.tick(sink, NONE);
		}
		assertEquals(JobState.PAUSED, job.state());
		assertEquals(Map.of(PLANKS, 1), job.missingMaterials());

		supply.put(PLANKS, 1);
		for (int t = 0; t < BuildJob.MATERIAL_RETRY_TICKS && job.state() != JobState.COMPLETED; t++) {
			job.tick(sink, NONE);
		}
		job.tick(sink, NONE);
		assertEquals(JobState.COMPLETED, job.state());
		assertFalse(job.waitingForMaterials());
		assertEquals(Map.of(STONE, 4, PLANKS, 4), job.spentMaterials());
		assertEquals(Map.of(STONE, 0, PLANKS, 0), supply.stock);
	}

	@Test
	void userPauseWinsOverAutoResume() {
		BuildJob job = job(charged());
		FakeSupply supply = new FakeSupply();
		job.useSupply(supply);
		MapSink sink = new MapSink();
		job.tick(sink, NONE);
		assertTrue(job.waitingForMaterials());
		assertTrue(job.pause());
		supply.put(STONE, 4).put(PLANKS, 4);
		for (int t = 0; t < 3 * BuildJob.MATERIAL_RETRY_TICKS; t++) {
			job.tick(sink, NONE);
		}
		assertEquals(0, job.placed(), "paused by the player: no auto-resume");
		assertTrue(job.resume());
		job.tick(sink, NONE);
		assertEquals(8, job.placed(), "resume re-checks right away");
		job.tick(sink, NONE);
		assertEquals(JobState.COMPLETED, job.state());
	}

	@Test
	void noSupplyMeansWaitingNotFree() {
		BuildJob job = job(charged()); // opener returns null → EMPTY; never opened here
		MapSink sink = new MapSink();
		job.tick(sink, NONE);
		assertEquals(0, sink.world.size());
		assertEquals(Map.of(STONE, 4), job.missingMaterials());
	}

	@Test
	void existingBlocksAreSkippedForFree() {
		BuildJob job = job(charged());
		MapSink sink = new MapSink();
		for (int x = 0; x <= 1; x++) {
			for (int z = 0; z <= 1; z++) {
				sink.world.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
			}
		}
		FakeSupply supply = new FakeSupply().put(PLANKS, 4);
		job.useSupply(supply);
		job.tick(sink, NONE);
		job.tick(sink, NONE);
		assertEquals(JobState.COMPLETED, job.state());
		assertEquals(Map.of(PLANKS, 4), supply.taken);
	}
}
