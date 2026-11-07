package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static dev.t3craft.hq.HqTestSupport.minus;
import static dev.t3craft.hq.HqTestSupport.plus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Wing/desk allocation: deterministic from scratch, stable across changes (pure, no Minecraft). */
class HqLayoutTest {
	@Test
	void freshAllocationIsOrderIndependent() {
		HqInput in = input(4, 5);
		List<HqInput.Project> shuffled = new ArrayList<>(in.projects());
		Collections.shuffle(shuffled, new Random(42));
		List<HqInput.Project> reversedThreads = new ArrayList<>();
		for (HqInput.Project p : shuffled) {
			List<String> t = new ArrayList<>(p.threads());
			Collections.reverse(t);
			reversedThreads.add(new HqInput.Project(p.key(), p.label(), p.envLabel(), t));
		}
		assertEquals(HqLayout.allocate(in, null), HqLayout.allocate(new HqInput(reversedThreads), null));
	}

	@Test
	void freshWingsAndDesksAreDense() {
		HqLayout l = HqLayout.allocate(input(3, 4), null);
		assertEquals(3, l.wings().size());
		for (int i = 0; i < 3; i++) {
			HqLayout.Wing w = l.wingAt(i);
			assertEquals("env:p" + i, w.projectKey());
			assertEquals(HqLayout.MIN_SEGMENTS, w.segments());
			for (int s = 0; s < 4; s++) {
				assertEquals("env:p" + i + "-t" + s, w.threadAt(s));
			}
		}
		assertEquals(2, l.rows());
	}

	@Test
	void existingThreadsKeepTheirSlotsWhenOthersComeAndGo() {
		HqInput in = input(1, 4);
		HqLayout l1 = HqLayout.allocate(in, null);
		HqLayout l2 = HqLayout.allocate(minus(in, "env:p0-t1"), l1);
		HqLayout.Wing w2 = l2.wing("env:p0");
		assertNull(w2.threadAt(1), "the gone thread leaves a vacant desk");
		assertEquals("env:p0-t2", w2.threadAt(2));
		assertEquals("env:p0-t3", w2.threadAt(3));
		HqLayout l3 = HqLayout.allocate(plus(minus(in, "env:p0-t1"), "env:p0", "env:p0-new"), l2);
		assertEquals("env:p0-new", l3.wing("env:p0").threadAt(1), "a new thread takes the lowest vacant slot");
		assertEquals("env:p0-t3", l3.wing("env:p0").threadAt(3));
	}

	@Test
	void wingGrowsBySegmentsAndNeverShrinksWhileItsProjectExists() {
		HqInput in = input(1, 6);
		HqLayout l1 = HqLayout.allocate(in, null);
		assertEquals(3, l1.wing("env:p0").segments());
		HqLayout l2 = HqLayout.allocate(plus(in, "env:p0", "env:p0-x"), l1);
		assertEquals(4, l2.wing("env:p0").segments());
		assertEquals("env:p0-x", l2.wing("env:p0").threadAt(6));
		HqLayout l3 = HqLayout.allocate(input(1, 0), l2);
		assertEquals(4, l3.wing("env:p0").segments(), "emptying the wing keeps its length");
		assertEquals(3, HqLayout.allocate(input(1, 0), null).wing("env:p0").segments(), "a fresh allocation compacts");
	}

	@Test
	void projectsKeepTheirWingAndFreedWingsAreReused() {
		HqLayout l1 = HqLayout.allocate(input(3, 1), null);
		HqInput without1 = new HqInput(List.of(input(3, 1).projects().get(0), input(3, 1).projects().get(2)));
		HqLayout l2 = HqLayout.allocate(without1, l1);
		assertEquals(0, l2.wing("env:p0").index());
		assertEquals(2, l2.wing("env:p2").index());
		assertNull(l2.wingAt(1));
		HqLayout l3 = HqLayout.allocate(plus(without1, "env:zz", "env:zz-t0"), l2);
		assertEquals(1, l3.wing("env:zz").index(), "a new project takes the freed wing");
		assertEquals(2, l3.wing("env:p2").index());
	}

	@Test
	void threadMovingToAnotherProjectFreesItsOldDesk() {
		HqLayout l1 = HqLayout.allocate(input(2, 2), null);
		HqInput moved = plus(minus(input(2, 2), "env:p0-t0"), "env:p1", "env:p0-t0");
		HqLayout l2 = HqLayout.allocate(moved, l1);
		assertNull(l2.wing("env:p0").threadAt(0));
		assertEquals("env:p0-t0", l2.wing("env:p1").threadAt(2));
	}

	@Test
	void jsonRoundTrip() {
		HqLayout l = HqLayout.allocate(minus(input(3, 5), "env:p1-t2"), HqLayout.allocate(input(3, 5), null));
		assertEquals(l, HqLayout.fromJson(l.toJson()));
		assertSame(HqLayout.EMPTY, HqLayout.fromJson(null));
		assertSame(HqLayout.EMPTY, HqLayout.fromJson(new com.google.gson.JsonPrimitive("junk")));
	}

	@Test
	void builderIgnoresAThreadGivenTwice() {
		HqInput in = HqInput.builder().thread("a", "A", "e", "t").thread("b", "B", "e", "t").build();
		assertEquals(List.of("t"), in.projects().get(0).threads());
		assertEquals(List.of(), in.projects().get(1).threads());
	}
}
