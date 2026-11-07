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
		assertEquals(List.of("t0000", "t0001", "t0002"), l.overflow(), "no history: the first in id order count as the oldest");
		assertTrue(l.modules().isEmpty());
		assertEquals(l, HqLayout.allocate(b.build(), l), "fixed point with a full shelf");
	}

	@Test
	void shelfKeepsTheNewestAndPushesTheOldestToTheOlderList() {
		int cap = HqLayout.ARCHIVE_CAPACITY;
		HqInput in = HqInput.EMPTY;
		HqLayout l = null;
		List<String> order = new ArrayList<>();
		// threads settle one at a time, in an order unrelated to their ids
		for (int i = 0; i < cap + 4; i++) {
			String t = String.format("env:p0-t%02d", (i * 5) % (cap + 4));
			order.add(t);
			in = settle(plus(in, "env:p0", t), t);
			HqLayout next = HqLayout.allocate(in, l);
			if (l != null) {
				for (HqLayout.Archived a : l.archive()) {
					HqLayout.Archived now = next.archived(a.threadId());
					if (now != null) {
						assertEquals(a.slot(), now.slot(), "a shelved computer never moves");
					}
				}
			}
			l = next;
			assertEquals(Math.min(i + 1, cap), l.archive().size());
		}
		List<String> newest = order.subList(4, order.size());
		for (String t : newest) {
			assertNotNull(l.archived(t), "newest on the shelf: " + t);
		}
		assertEquals(order.subList(0, 4).stream().sorted().toList(), l.overflow(), "oldest only listed");
		assertEquals(l, HqLayout.allocate(in, l), "idempotent");
		// the newest settled thread has the highest number
		String last = order.getLast();
		int lastSeq = l.archived(last).seq();
		assertTrue(l.archive().stream().allMatch(a -> a.seq() <= lastSeq));
		// one leaves the roster: its slot is refilled from the older list (lowest id first)
		HqLayout.Archived gone = l.archived(newest.getFirst());
		HqLayout after = HqLayout.allocate(minus(in, gone.threadId()), l);
		String promoted = order.subList(0, 4).stream().sorted().findFirst().orElseThrow();
		assertEquals(gone.slot(), after.archived(promoted).slot());
		assertEquals(3, after.overflow().size());
		assertTrue(after.archive().stream().allMatch(a -> a.seq() >= after.archived(promoted).seq()), "promoted counts as the oldest");
		// a newer one pushes out the oldest shelved thread: the promoted one
		HqInput newer = settle(plus(minus(in, gone.threadId()), "env:p0", "env:p0-zz"), "env:p0-zz");
		HqLayout l3 = HqLayout.allocate(newer, after);
		assertNull(l3.archived(promoted), "the oldest leaves first");
		assertEquals(after.archived(promoted).slot(), l3.archived("env:p0-zz").slot());
		assertTrue(l3.overflow().contains(promoted));
	}

	@Test
	void settledThreadFreesItsDeskAndGoesOnTheShelf() {
		HqInput in = input(1, 6);
		HqLayout l1 = HqLayout.plan(in);
		HqLayout l2 = HqLayout.allocate(settle(in, "env:p0-t4"), l1);
		assertNull(l2.seat("env:p0-t4"));
		assertNull(l2.moduleAt(0).threadAt(4), "desk freed");
		assertEquals(0, l2.archived("env:p0-t4").slot());
		HqLayout.Diff d = HqLayout.diff(l1, l2);
		assertEquals(List.of(new HqLayout.FreedDesk(0, 4, "env:p0-t4")), d.freedDesks());
		assertEquals(List.of("env:p0-t4"), d.archived().stream().map(HqLayout.Archived::threadId).toList());
		// a whole module settling removes it
		HqInput all = in;
		for (int t = 0; t < 6; t++) {
			all = settle(all, "env:p0-t" + t);
		}
		HqLayout l3 = HqLayout.allocate(all, l2);
		assertTrue(l3.modules().isEmpty());
		assertEquals(6, l3.archive().size());
		assertEquals(0, l3.archived("env:p0-t4").slot(), "kept its shelf slot");
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
		assertEquals(List.of(new HqLayout.Archived(0, "env:p1-t2", "env:p1", "env", 0)), d.archived());
		assertEquals(List.of(), d.unarchived());
		assertTrue(HqLayout.diff(l2, l2).isEmpty());
		HqLayout.Diff back = HqLayout.diff(l2, HqLayout.allocate(in, l2));
		assertEquals(List.of(2), back.removedModules());
		assertEquals(List.of("env:p1-t2"), back.unarchived());
	}

	// ------------------------------------------------------------------ envelope

	@Test
	void envelopeFormula() {
		assertEquals(new Box(-8, 0, -8, 8, 10, 13), HqLayout.envelope(0));
		assertEquals(new Box(-8, 0, -23, 17, 10, 13), HqLayout.envelope(1));
		assertEquals(new Box(-17, 0, -23, 17, 10, 13), HqLayout.envelope(2));
		for (int n = 2; n <= 12; n++) {
			int rows = (n + 1) / 2;
			assertEquals(new Box(-17, 0, -9 - 14 * rows, 17, 10, 13), HqLayout.envelope(n), "n=" + n);
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

	@Test
	void basementLayoutsKeepTheirDesksAndReshelve() {
		HqInput in = settle(input(2, 4), "env:p1-t2");
		HqLayout l = HqLayout.plan(in);
		com.google.gson.JsonObject old = l.toJson();
		old.addProperty("format", 2);
		for (var a : old.getAsJsonArray("archive")) {
			a.getAsJsonObject().remove("seq");
			a.getAsJsonObject().addProperty("slot", 140);
		}
		HqLayout read = HqLayout.fromJson(old);
		assertEquals(l.modules(), read.modules(), "desks kept");
		assertTrue(read.archive().isEmpty());
		assertEquals(l, HqLayout.allocate(in, read), "settled threads shelved afresh");
	}
}
