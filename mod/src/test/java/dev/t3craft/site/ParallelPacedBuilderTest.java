package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildTuning;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The site's paced builder with several fronts inside the shared budget. */
class ParallelPacedBuilderTest {
	@Test
	void frontsWorkTheSameLayerAtOnceThenGoUp() {
		FakeWorld w = new FakeWorld();
		PacedBuilder b = new PacedBuilder(w, 1, 4, PacedBuilder.Allocator.UNLIMITED);
		List<SiteBuilder.Report> reports = new ArrayList<>();
		b.submit(PacedBuilderTest.wall(), PacedBuilderTest.wall().origin(), (x, y, z) -> true, reports::add);
		b.tick();
		assertEquals(4, w.writes, "four fronts, one cell each");
		assertEquals(StateKey.AIR, w.state(0, 66, 0));
		b.tick();
		assertEquals(8, w.writes);
		for (int x = 0; x <= 4; x++) {
			assertEquals("minecraft:stone_bricks", w.state(x, 65, 0), "the bottom layer is finished before the upper one starts");
		}
		b.tick();
		b.tick();
		assertTrue(b.idle());
		assertEquals(10, reports.get(0).placed());
	}

	@Test
	void jobsShareTheGlobalBudgetAndTheConcurrentLimit() {
		FakeWorld w = new FakeWorld();
		BuildBudget budget = new BuildBudget(new BuildTuning(3, 1, 4, 8, false, 40));
		long[] tick = {0};
		PacedBuilder b = new PacedBuilder(w, 8, 4, (st, rates) -> {
			budget.beginTick(++tick[0], 5);
			return budget.allocate("site", st, rates);
		});
		CellPlan a = PacedBuilderTest.wall();
		CellPlan other = new CellPlan(new Box(10, 65, 0, 14, 66, 0), new CellPlan.Origin(12, 65, 0, CellPlan.Facing.NORTH));
		for (int x = 10; x <= 14; x++) {
			other.set(x, 65, 0, "minecraft:stone");
			other.set(x, 66, 0, "minecraft:stone");
		}
		List<SiteBuilder.Report> reports = new ArrayList<>();
		b.submit(a, a.origin(), (x, y, z) -> true, reports::add);
		b.submit(other, other.origin(), (x, y, z) -> true, reports::add);
		b.tick();
		assertEquals(3, w.writes, "global budget of 3, whatever the fronts want");
		assertEquals(StateKey.AIR, w.state(10, 65, 0), "second plan waits: one job at a time");
		for (int t = 0; t < 3; t++) {
			b.tick();
		}
		assertEquals(1, reports.size());
		assertEquals(StateKey.AIR, w.state(10, 65, 0));
		for (int t = 0; t < 10; t++) {
			b.tick();
		}
		assertEquals(2, reports.size());
		assertEquals(20, w.writes);
	}

	@Test
	void resumeAfterInterruptionIsANoOpForWhatIsBuilt() {
		FakeWorld w = new FakeWorld();
		PacedBuilder b = new PacedBuilder(w, 1, 3, PacedBuilder.Allocator.UNLIMITED);
		List<SiteBuilder.Report> reports = new ArrayList<>();
		b.submit(PacedBuilderTest.wall(), PacedBuilderTest.wall().origin(), (x, y, z) -> true, reports::add);
		b.tick();
		b.tick();
		b.cancelAll(); // server stopping mid-build
		int written = w.writes;
		PacedBuilder again = new PacedBuilder(w, 1, 3, PacedBuilder.Allocator.UNLIMITED);
		again.submit(PacedBuilderTest.wall(), PacedBuilderTest.wall().origin(), (x, y, z) -> true, reports::add);
		for (int t = 0; t < 10; t++) {
			again.tick();
		}
		SiteBuilder.Report r = reports.get(1);
		assertEquals(written, r.alreadyThere());
		assertEquals(10 - written, r.placed());
		assertEquals(10, w.writes, "every cell written exactly once in total");
	}
}
