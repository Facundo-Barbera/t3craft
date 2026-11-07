package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static dev.t3craft.hq.HqTestSupport.minus;
import static dev.t3craft.hq.HqTestSupport.roster;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HqDiffTest {
	private static BlockState AIR;
	private static BlockState GRASS;
	private static BuildPlan.Vacated GROUND;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		AIR = Blocks.AIR.defaultBlockState();
		GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
		GROUND = rel -> rel.getY() <= 0 ? GRASS : AIR;
	}

	private static Map<BlockPos, BlockState> world(BuildPlan plan) {
		Map<BlockPos, BlockState> w = new HashMap<>();
		plan.placements().forEach(p -> w.put(p.rel(), p.state()));
		return w;
	}

	private static void apply(Map<BlockPos, BlockState> w, HqDiff d) {
		d.placements().forEach(p -> w.put(p.rel(), p.state()));
	}

	/** Applying the diff to the old building gives exactly the new one, and every other old cell is restored. */
	private static void assertReconciles(BuildPlan before, BuildPlan after, HqDiff d) {
		Map<BlockPos, BlockState> w = world(before);
		apply(w, d);
		for (Placement p : after.placements()) {
			assertEquals(p.state(), w.get(p.rel()), "cell " + p.rel());
		}
		for (Placement p : before.placements()) {
			if (after.at(p.rel()) == null) {
				assertEquals(GROUND.at(p.rel()), w.get(p.rel()), "left over at " + p.rel());
			}
		}
	}

	@Test
	void releasedModuleIsRemoved() {
		HqInput in = input(3, 2);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqInput less = minus(minus(in, "env:p2-t0"), "env:p2-t1");
		HqGenerator.Result r2 = HqGenerator.plan(less, r1.layout());
		assertNull(r2.layout().moduleAt(2), "a module without threads is gone");
		HqDiff d = HqDiff.between(r1.plan(), r2.plan(), GROUND);
		assertTrue(d.removals().containsKey(HqGenerator.group(2)), "removals of the module: " + d.removals().keySet());
		assertTrue(HqDiff.removedGroups(r1.plan(), r2.plan()).contains(HqGenerator.group(2)));
		assertFalse(d.builds().containsKey(HqGenerator.group(2)));
		for (Placement p : d.removals().get(HqGenerator.group(2))) {
			assertEquals(GROUND.at(p.rel()), p.state(), "restored to air or ground");
		}
		assertTrue(d.parts().keySet().iterator().next().startsWith(HqDiff.REMOVE_PREFIX), "removals run first");
		assertReconciles(r1.plan(), r2.plan(), d);
	}

	@Test
	void releasedDeskIsRemoved() {
		HqInput in = input(2, 4);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqGenerator.Result r2 = HqGenerator.plan(minus(in, "env:p0-t1"), r1.layout());
		Workstation w = r1.plan().workstation("env:p0-t1");
		assertNull(r2.plan().workstation("env:p0-t1"));
		HqDiff d = HqDiff.between(r1.plan(), r2.plan(), GROUND);
		List<Placement> removed = d.removals().get(HqGenerator.group(0));
		assertTrue(removed != null && !removed.isEmpty(), "the desk is a removal: " + d.removals().keySet());
		assertTrue(removed.stream().anyMatch(p -> p.rel().equals(w.computerPos())), "computer removed");
		assertTrue(removed.stream().anyMatch(p -> p.rel().equals(w.lampPos())), "lamp removed");
		// no chairs: the NPC's standing cell is air before and after, so it is not part of the diff
		assertTrue(r1.plan().at(w.deskAnchor().block()).state().isAir(), "standing cell is free");
		assertTrue(removed.stream().noneMatch(p -> p.rel().equals(w.deskAnchor().block())), "nothing to remove where the NPC stood");
		assertTrue(removed.stream().allMatch(p -> p.state().isAir()));
		assertTrue(d.builds().isEmpty(), "nothing else changes: " + d.builds().keySet());
		assertReconciles(r1.plan(), r2.plan(), d);
		// reverse plan order: the fern (a decoration) goes before the barrel that holds it
		int fern = -1;
		int barrel = -1;
		for (int i = 0; i < removed.size(); i++) {
			if (removed.get(i).rel().equals(w.computerPos().relative(w.screenFacing().getClockWise()))) {
				fern = i;
			}
			if (removed.get(i).rel().equals(w.computerPos().below().relative(w.screenFacing().getClockWise()))) {
				barrel = i;
			}
		}
		assertTrue(fern >= 0 && barrel > fern, "decoration before its support: fern " + fern + ", barrel " + barrel);
	}

	@Test
	void sameRosterSameBuildingNoRemovals() {
		HqInput in = input(3, 4);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		List<HqInput.Entry> shuffled = new ArrayList<>(roster(in));
		Collections.shuffle(shuffled, new Random(7));
		HqGenerator.Result r2 = HqGenerator.plan(HqInput.fromRoster(shuffled), r1.layout());
		assertEquals(r1.plan().fingerprint(), r2.plan().fingerprint());
		HqDiff d = HqDiff.between(r1.plan(), r2.plan(), GROUND);
		assertTrue(d.isEmpty(), "idempotent: " + d.removedCells() + " removed, " + d.builtCells() + " built");
		assertEquals(0, d.removedCells());
		assertTrue(HqDiff.removedGroups(r1.plan(), r2.plan()).isEmpty());
	}

	@Test
	void firstBuildHasNoRemovals() {
		BuildPlan p = HqGenerator.plan(input(1, 2), null).plan();
		HqDiff d = HqDiff.between(null, p, GROUND);
		assertTrue(d.removals().isEmpty());
		assertEquals(p.size(), d.builtCells());
	}

	@Test
	void restoringUsesTheOriginalAndFallsBack() {
		BlockState stone = Blocks.STONE.defaultBlockState();
		BuildPlan.Vacated v = HqDiff.restoring(rel -> rel.getX() == 0 ? stone : null, GROUND);
		assertEquals(stone, v.at(new BlockPos(0, 3, 0)));
		assertEquals(AIR, v.at(new BlockPos(1, 3, 0)));
		assertEquals(GRASS, v.at(new BlockPos(1, 0, 0)));
	}
}
