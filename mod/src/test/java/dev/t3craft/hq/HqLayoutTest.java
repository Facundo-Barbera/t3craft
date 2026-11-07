package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static dev.t3craft.hq.HqTestSupport.minus;
import static dev.t3craft.hq.HqTestSupport.plus;
import static dev.t3craft.hq.HqTestSupport.roster;
import static dev.t3craft.hq.HqTestSupport.settle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Module/desk/archive allocation: idempotent, deterministic, stable across changes. */
class HqLayoutTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	// ------------------------------------------------------------------ idempotence

	@Test
	void rosterOrderDoesNotMatter() {
		HqInput in = settle(settle(input(4, 9), "env:p1-t3"), "env:p2-t0");
		List<HqInput.Entry> lines = roster(in);
		for (int seed = 0; seed < 5; seed++) {
			List<HqInput.Entry> shuffled = new ArrayList<>(lines);
			Collections.shuffle(shuffled, new Random(seed));
			assertEquals(in, HqInput.fromRoster(shuffled));
			assertEquals(HqLayout.plan(in), HqLayout.plan(HqInput.fromRoster(shuffled)));
		}
	}

	@Test
	void duplicateLinesAndLabelsAreOrderIndependent() {
		HqInput.Entry a = new HqInput.Entry("vps", "VPS", "x", "Alpha", "t1", true);
		HqInput.Entry b = new HqInput.Entry("vps", "VPS", "x", "Beta", "t1", false);
		HqInput ab = HqInput.fromRoster(List.of(a, b));
		HqInput ba = HqInput.fromRoster(List.of(b, a));
		assertEquals(ab, ba);
		assertEquals(List.of("t1"), ab.projects().get(0).threads(), "active if any line says active");
		assertEquals("Alpha", ab.projects().get(0).label());
	}

	@Test
	void planIsAFixedPoint() {
		HqInput in = settle(input(3, 8), "env:p0-t2");
		HqLayout l = HqLayout.plan(in);
		assertEquals(l, HqLayout.allocate(in, l));
	}

	// ------------------------------------------------------------------ modules

	@Test
	void freshModulesAreDense() {
		HqLayout l = HqLayout.plan(input(3, 4));
		assertEquals(3, l.modules().size());
		for (int i = 0; i < 3; i++) {
			HqLayout.Module m = l.moduleAt(i);
			assertEquals("env:p" + i, m.projectKey());
			assertEquals(HqLayout.Type.WING, m.type());
			for (int s = 0; s < 4; s++) {
				assertEquals("env:p" + i + "-t" + s, m.threadAt(s));
			}
		}
		assertEquals(2, l.rows());
		assertEquals(net.minecraft.world.level.block.Rotation.NONE, l.moduleAt(0).rotation());
		assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_180, l.moduleAt(1).rotation());
		assertEquals(new net.minecraft.core.BlockPos(4, 0, -16), l.moduleAt(0).origin());
		assertEquals(new net.minecraft.core.BlockPos(-4, 0, -16), l.moduleAt(1).origin());
	}

	@Test
	void fullModuleAddsAnIdenticalModuleOfTheSameProject() {
		HqLayout l = HqLayout.plan(input(2, 7));
		assertEquals(4, l.modules().size());
		assertEquals("env:p0", l.moduleAt(0).projectKey());
		assertEquals("env:p0", l.moduleAt(1).projectKey());
		assertEquals(HqLayout.DESKS, l.moduleAt(0).desks().size());
		assertEquals("env:p0-t6", l.moduleAt(1).threadAt(0));
		assertEquals("env:p1", l.moduleAt(2).projectKey());
		assertEquals(new HqLayout.Seat(3, 0), l.seat("env:p1-t6"));
	}

	@Test
	void annexTakesTheLowestFreeIndexAndKeepsTheRest() {
		HqInput in = plus(input(1, 6), "env:q", "env:q-t0");
		HqLayout l1 = HqLayout.plan(in);
		assertEquals(1, l1.modulesOf("env:q").get(0).index());
		HqLayout l2 = HqLayout.allocate(plus(in, "env:p0", "env:p0-x"), l1);
		assertEquals(2, l2.moduleAt(2).index());
		assertEquals("env:p0", l2.moduleAt(2).projectKey());
		assertEquals(new HqLayout.Seat(2, 0), l2.seat("env:p0-x"));
		assertEquals(l1.moduleAt(0), l2.moduleAt(0));
		assertEquals(l1.moduleAt(1), l2.moduleAt(1));
	}

	@Test
	void emptyModuleIsRemovedAndItsIndexReused() {
		HqInput in = input(3, 1);
		HqLayout l1 = HqLayout.plan(in);
		HqLayout l2 = HqLayout.allocate(settle(in, "env:p1-t0"), l1);
		assertNull(l2.moduleAt(1));
		assertEquals(2, l2.moduleAt(2).index());
		HqLayout l3 = HqLayout.allocate(plus(settle(in, "env:p1-t0"), "env:zz", "env:zz-t0"), l2);
		assertEquals(1, l3.modulesOf("env:zz").get(0).index(), "a new module takes the freed index");
		assertEquals(l2.moduleAt(2), l3.moduleAt(2));
	}

	// ------------------------------------------------------------------ desks

	@Test
	void settledThreadFreesItsDeskAndANewThreadReusesIt() {
		HqInput in = input(1, 4);
		HqLayout l1 = HqLayout.plan(in);
		HqInput settled = settle(in, "env:p0-t1");
		HqLayout l2 = HqLayout.allocate(settled, l1);
		assertNull(l2.moduleAt(0).threadAt(1));
		assertEquals("env:p0-t3", l2.moduleAt(0).threadAt(3));
		assertNull(l2.seat("env:p0-t1"));
		HqLayout l3 = HqLayout.allocate(plus(settled, "env:p0", "env:p0-new"), l2);
		assertEquals(new HqLayout.Seat(0, 1), l3.seat("env:p0-new"), "a new thread takes the lowest free desk");
	}

	@Test
	void freeDeskInAnyModuleOfTheProjectIsUsedBeforeAnAnnex() {
		HqInput in = input(1, 8);
		HqLayout l1 = HqLayout.plan(in);
		HqInput s = settle(in, "env:p0-t2");
		HqLayout l2 = HqLayout.allocate(plus(s, "env:p0", "env:p0-new"), HqLayout.allocate(s, l1));
		assertEquals(new HqLayout.Seat(0, 2), l2.seat("env:p0-new"));
		assertEquals(2, l2.modules().size());
	}

	@Test
	void threadMovingToAnotherProjectFreesItsOldDesk() {
		HqLayout l1 = HqLayout.plan(input(2, 2));
		HqInput moved = plus(minus(input(2, 2), "env:p0-t0"), "env:p1", "env:p0-t0");
		HqLayout l2 = HqLayout.allocate(moved, l1);
		assertNull(l2.moduleAt(0).threadAt(0));
		assertEquals(new HqLayout.Seat(1, 2), l2.seat("env:p0-t0"));
	}

	// ------------------------------------------------------------------ archive

	@Test
	void archiveSlotsAreStableAndReused() {
		HqInput in = input(1, 5);
		HqLayout l1 = HqLayout.plan(settle(settle(in, "env:p0-t3"), "env:p0-t1"));
		assertEquals(0, l1.archived("env:p0-t1").slot(), "fresh: settled threads in id order");
		assertEquals(1, l1.archived("env:p0-t3").slot());
		HqLayout l2 = HqLayout.allocate(settle(minus(in, "env:p0-t1"), "env:p0-t3"), l1);
		assertNull(l2.archived("env:p0-t1"), "gone from the roster: slot freed");
		assertEquals(1, l2.archived("env:p0-t3").slot(), "kept");
		HqLayout l3 = HqLayout.allocate(settle(settle(minus(in, "env:p0-t1"), "env:p0-t3"), "env:p0-t4"), l2);
		assertEquals(0, l3.archived("env:p0-t4").slot(), "lowest free slot");
		HqLayout l4 = HqLayout.allocate(settle(minus(in, "env:p0-t1"), "env:p0-t4"), l3);
		assertNull(l4.archived("env:p0-t3"), "active again: leaves the archive");
		assertNotNull(l4.seat("env:p0-t3"));
	}

	@Test
	void fullArchiveOverflows() {
		HqInput.Builder b = HqInput.builder();
		int n = HqLayout.ARCHIVE_CAPACITY + 3;
		for (int i = 0; i < n; i++) {
			b.thread("env:p", "P", "env", "env", String.format("t%04d", i), true);
		}
		HqLayout l = HqLayout.plan(b.build());
		assertEquals(HqLayout.ARCHIVE_CAPACITY, l.archive().size());
		assertEquals(List.of(String.format("t%04d", n - 3), String.format("t%04d", n - 2), String.format("t%04d", n - 1)), l.overflow());
		assertTrue(l.modules().isEmpty());
	}

	// ------------------------------------------------------------------ diff

	@Test
	void diffListsModulesDesksAndArchive() {
		HqInput in = input(2, 6);
		HqLayout l1 = HqLayout.plan(in);
		HqInput next = plus(settle(in, "env:p1-t2"), "env:p0", "env:p0-x");
		HqLayout l2 = HqLayout.allocate(next, l1);
		HqLayout.Diff d = HqLayout.diff(l1, l2);
		assertEquals(List.of(2), d.addedModules());
		assertEquals(List.of(), d.removedModules());
		assertEquals(List.of(new HqLayout.FreedDesk(1, 2, "env:p1-t2")), d.freedDesks());
		assertEquals(java.util.Map.of("env:p0-x", new HqLayout.Seat(2, 0)), d.seated());
		assertEquals(List.of(new HqLayout.Archived(0, "env:p1-t2", "env:p1", "env")), d.archived());
		assertEquals(List.of(), d.unarchived());
		assertTrue(HqLayout.diff(l2, l2).isEmpty());
		HqLayout.Diff back = HqLayout.diff(l2, HqLayout.allocate(in, l2));
		assertEquals(List.of(2), back.removedModules());
		assertEquals(List.of("env:p1-t2"), back.unarchived());
	}

	// ------------------------------------------------------------------ envelope

	@Test
	void envelopeFormula() {
		assertEquals(new Box(-8, -5, -8, 8, 10, 13), HqLayout.envelope(0));
		assertEquals(new Box(-8, -5, -23, 17, 10, 13), HqLayout.envelope(1));
		assertEquals(new Box(-17, -5, -23, 17, 10, 13), HqLayout.envelope(2));
		for (int n = 2; n <= 12; n++) {
			int rows = (n + 1) / 2;
			assertEquals(new Box(-17, -5, -9 - 14 * rows, 17, 10, 13), HqLayout.envelope(n), "n=" + n);
		}
	}

	@Test
	void jsonRoundTrip() {
		HqInput in = settle(input(3, 7), "env:p1-t2");
		HqLayout l = HqLayout.allocate(minus(in, "env:p2-t2"), HqLayout.plan(in));
		assertEquals(l, HqLayout.fromJson(l.toJson()));
		assertSame(HqLayout.EMPTY, HqLayout.fromJson(null));
		assertSame(HqLayout.EMPTY, HqLayout.fromJson(new com.google.gson.JsonPrimitive("junk")));
	}
}
