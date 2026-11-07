package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacedBuilderTest {
	static CellPlan wall() {
		CellPlan p = new CellPlan(new Box(0, 65, 0, 4, 66, 0), new CellPlan.Origin(2, 65, 0, CellPlan.Facing.NORTH));
		for (int x = 0; x <= 4; x++) {
			p.set(x, 65, 0, "minecraft:stone_bricks");
			p.set(x, 66, 0, "minecraft:stone_bricks");
		}
		return p;
	}

	@Test
	void writesAFewCellsPerTickBottomUpAndReportsOnce() {
		FakeWorld w = new FakeWorld();
		PacedBuilder b = new PacedBuilder(w, 3);
		List<SiteBuilder.Report> reports = new ArrayList<>();
		SiteBuilder.Handle h = b.submit(wall(), wall().origin(), (x, y, z) -> true, reports::add);
		b.tick();
		assertEquals(3, w.writes);
		assertEquals("minecraft:stone_bricks", w.state(2, 65, 0));
		assertEquals(StateKey.AIR, w.state(0, 66, 0), "upper layer comes later");
		for (int i = 0; i < 10; i++) {
			b.tick();
		}
		assertTrue(h.finished());
		assertTrue(b.idle());
		assertEquals(1, reports.size());
		assertEquals(10, reports.get(0).placed());
		assertFalse(reports.get(0).cancelled());
	}

	@Test
	void guardIsAskedRightBeforeEachCellAndRebuildIsANoOp() {
		FakeWorld w = new FakeWorld();
		PacedBuilder b = new PacedBuilder(w, 2);
		List<SiteBuilder.Report> reports = new ArrayList<>();
		b.submit(wall(), wall().origin(), (x, y, z) -> !(x == 4 && y == 66), reports::add);
		b.tick();
		// the player puts a block where the builder has not been yet
		w.put(4, 66, 0, "minecraft:glass", null);
		for (int i = 0; i < 10; i++) {
			b.tick();
		}
		assertEquals("minecraft:glass", w.state(4, 66, 0), "never overwritten");
		assertEquals(1, reports.get(0).skipped());
		int writes = w.writes;
		b.submit(wall(), wall().origin(), (x, y, z) -> !(x == 4 && y == 66), reports::add);
		b.tick();
		b.tick();
		assertEquals(writes, w.writes, "already built: nothing rewritten");
		assertEquals(9, reports.get(1).alreadyThere());
	}

	@Test
	void cancelReportsOnceAsCancelled() {
		FakeWorld w = new FakeWorld();
		PacedBuilder b = new PacedBuilder(w, 1);
		List<SiteBuilder.Report> reports = new ArrayList<>();
		SiteBuilder.Handle h = b.submit(wall(), wall().origin(), (x, y, z) -> true, reports::add);
		b.tick();
		h.cancel();
		h.cancel();
		b.tick();
		assertEquals(1, reports.size());
		assertTrue(reports.get(0).cancelled());
		assertEquals(1, w.writes);
		b.submit(wall(), wall().origin(), (x, y, z) -> true, reports::add);
		b.cancelAll();
		assertEquals(2, reports.size());
		assertTrue(b.idle());
	}

	@Test
	void siteFrameTurnsPlansClockwiseToTheEntrance() {
		CellPlan.Origin north = new CellPlan.Origin(10, 65, 20, CellPlan.Facing.NORTH);
		CellPlan.Origin east = new CellPlan.Origin(10, 65, 20, CellPlan.Facing.EAST);
		CellPlan.Origin south = new CellPlan.Origin(10, 65, 20, CellPlan.Facing.SOUTH);
		CellPlan.Origin west = new CellPlan.Origin(10, 65, 20, CellPlan.Facing.WEST);
		// one step "forward" (towards the front, -z in plan space) goes towards the facing
		assertArrayEquals(new int[] {10, 65, 19}, SiteFrame.toWorld(north, 0, 0, -1));
		assertArrayEquals(new int[] {11, 65, 20}, SiteFrame.toWorld(east, 0, 0, -1));
		assertArrayEquals(new int[] {10, 65, 21}, SiteFrame.toWorld(south, 0, 0, -1));
		assertArrayEquals(new int[] {9, 65, 20}, SiteFrame.toWorld(west, 0, 0, -1));
		for (CellPlan.Origin o : List.of(north, east, south, west)) {
			int[] w = SiteFrame.toWorld(o, 3, 2, -5);
			assertArrayEquals(new int[] {3, 2, -5}, SiteFrame.toPlan(o, w[0], w[1], w[2]));
		}
		assertEquals(new Box(-23, 0, -15, 7, 0, 15), SiteRequest.GROWABLE.envelope().toWorld(new CellPlan.Origin(0, 0, 0, CellPlan.Facing.EAST), 0));
	}

	@Test
	void requestsAndModulesAreValidated() {
		assertThrows(IllegalArgumentException.class, () -> new SiteRequest(SiteRequest.SQUARE.base(), new Rect(-3, -3, 3, 3)), "base outside envelope");
		assertThrows(IllegalArgumentException.class, () -> new SiteRequest(SiteRequest.SQUARE.base(), new Rect(-40, -7, 40, 7)), "too large");
		assertThrows(IllegalArgumentException.class, () -> new SiteModule("Bad Id", "", new Rect(0, 0, 1, 1)));
		SiteModule m = new SiteModule("project-a", "desks=4", new Rect(8, -7, 15, 7));
		assertEquals(m, SiteModule.fromTag(m.toTag()));
		assertEquals(SiteRequest.GROWABLE, SiteRequest.fromTag(SiteRequest.GROWABLE.toTag()));
		assertEquals(new Rect(-7, -7, 7, 7), SiteRequest.SQUARE.envelope());
		assertTrue(SiteRequest.GROWABLE.frontReach() > SiteLimits.FOOTPRINT / 2);
		assertNull(SiteJournal.EMPTY.top());
		assertNotNull(SiteRequest.GROWABLE.base());
	}
}
