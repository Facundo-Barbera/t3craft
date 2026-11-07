package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Binding strings and their resolution: deterministic, block data first, position fallback second. */
class BindingTest {
	@Test
	void roundTrip() {
		for (Binding b : new Binding[] {Binding.NONE, Binding.thread("vps:abc"), Binding.board(3), Binding.thread("only-id")}) {
			assertEquals(b, Binding.parse(b.wire()));
		}
		assertEquals("thread:vps:abc", Binding.thread("vps:abc").wire());
		assertEquals("board:0", Binding.board(0).wire());
		assertEquals(3, Binding.board(3).boardIndex());
	}

	@Test
	void garbageIsUnbound() {
		for (String s : new String[] {null, "", "thread:", "board:", "board:x", "board:-2", "lamp:1", "vps:abc"}) {
			assertTrue(Binding.parse(s).isNone(), String.valueOf(s));
		}
		assertTrue(Binding.thread("").isNone());
	}

	@Test
	void ownDataWinsThenPosition() {
		Binding pos = Binding.thread("vps:from-plan");
		assertEquals(Binding.thread("vps:own"), Binding.resolve("thread:vps:own", pos));
		assertEquals(pos, Binding.resolve("", pos));
		assertEquals(pos, Binding.resolve("not-a-binding", pos));
		assertEquals(Binding.NONE, Binding.resolve("", null));
		assertEquals(Binding.board(1), Binding.resolve("board:1", null));
	}

	@Test
	void lampWant() {
		Map<String, LampStatus> wanted = Map.of("thread:vps:a", LampStatus.WORKING, "board:0", LampStatus.ERROR);
		assertEquals(LampStatus.WORKING, LampApplier.want(wanted, Binding.thread("vps:a")));
		assertEquals(LampStatus.ERROR, LampApplier.want(wanted, Binding.board(0)));
		assertEquals(LampStatus.OFF, LampApplier.want(wanted, Binding.thread("vps:gone")), "unknown thread → off");
		assertEquals(LampStatus.OFF, LampApplier.want(wanted, Binding.board(7)), "unused bulb → off");
		assertEquals(LampStatus.OFF, LampApplier.want(wanted, Binding.NONE), "unbound → off");
	}

	@Test
	void moduleBulbRoundTripAndOrderFree() {
		Binding m = Binding.module(java.util.List.of("vps:b", "vps:a", "vps:b"));
		assertEquals(Binding.Kind.MODULE, m.kind());
		assertEquals("module:vps:a,vps:b", m.wire());
		assertEquals(m, Binding.parse(m.wire()));
		assertEquals(m, Binding.module(java.util.List.of("vps:a", "vps:b")));
		assertEquals(java.util.List.of("vps:a", "vps:b"), m.moduleThreads());
		assertEquals(Binding.NONE, Binding.module(java.util.List.of()), "empty module → unbound");
		assertEquals(Binding.NONE, Binding.parse("module:"));
	}

	@Test
	void moduleBulbShowsItsMostUrgentThread() {
		Map<String, LampStatus> wanted = Map.of("thread:vps:a", LampStatus.WORKING, "thread:vps:b", LampStatus.ATTENTION,
				"thread:vps:c", LampStatus.IDLE);
		assertEquals(LampStatus.ATTENTION, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:a", "vps:b", "vps:c"))));
		assertEquals(LampStatus.WORKING, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:a", "vps:c"))));
		assertEquals(LampStatus.OFF, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:gone"))));
	}

	@Test
	void roomBulbAggregatesItsThreadsNeedsYouFirst() {
		// fix/hq-rooms-real: needs you > error > working > done > idle, off when nobody is there
		Map<String, LampStatus> wanted = Map.of("thread:vps:a", LampStatus.ERROR, "thread:vps:b", LampStatus.ATTENTION,
				"thread:vps:c", LampStatus.WORKING, "thread:vps:d", LampStatus.DONE, "thread:vps:e", LampStatus.IDLE);
		assertEquals(LampStatus.ATTENTION, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:a", "vps:b", "vps:c"))));
		assertEquals(LampStatus.ERROR, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:a", "vps:c", "vps:e"))));
		assertEquals(LampStatus.WORKING, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:c", "vps:d", "vps:e"))));
		assertEquals(LampStatus.DONE, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:d", "vps:e"))));
		assertEquals(LampStatus.IDLE, LampApplier.want(wanted, Binding.module(java.util.List.of("vps:e", "vps:gone"))));
		// live: the same bulb follows the threads' current states, nothing is frozen at build time
		Map<String, LampStatus> later = Map.of("thread:vps:a", LampStatus.DONE, "thread:vps:b", LampStatus.IDLE, "thread:vps:c", LampStatus.DONE);
		assertEquals(LampStatus.DONE, LampApplier.want(later, Binding.module(java.util.List.of("vps:a", "vps:b", "vps:c"))));
		assertEquals(LampStatus.ATTENTION, LampApplier.room(java.util.List.of(LampStatus.IDLE, LampStatus.ERROR, LampStatus.ATTENTION)));
		assertEquals(LampStatus.OFF, LampApplier.room(java.util.List.of()));
	}

	@Test
	void changesListOnlyWhatDiffers() {
		Map<Long, Binding> before = Map.of(1L, Binding.thread("a"), 2L, Binding.thread("b"), 3L, Binding.NONE, 4L, Binding.board(0));
		Map<Long, Binding> after = Map.of(1L, Binding.thread("a"), 2L, Binding.NONE, 3L, Binding.thread("c"), 5L, Binding.thread("d"));
		Map<Long, Binding> ch = HqBindings.changes(before, after);
		assertEquals(Map.of(2L, Binding.NONE, 3L, Binding.thread("c"), 4L, Binding.NONE, 5L, Binding.thread("d")), ch);
		assertEquals(after, HqBindings.changes(null, after));
		assertTrue(HqBindings.changes(after, after).isEmpty());
	}

	@Test
	void lampLightAndUrgency() {
		assertEquals(0, LampStatus.OFF.light);
		for (LampStatus s : LampStatus.values()) {
			assertTrue(s.light >= 0 && s.light <= 15);
			assertEquals(s.name().toLowerCase(java.util.Locale.ROOT), s.getSerializedName());
		}
		assertTrue(LampStatus.ATTENTION.light > LampStatus.WORKING.light, "needs-you is the brightest");
		assertTrue(LampStatus.ERROR.urgency() > LampStatus.ATTENTION.urgency());
		assertTrue(LampStatus.ATTENTION.urgency() > LampStatus.WORKING.urgency());
	}

	@Test
	void beaconBinding() {
		assertEquals(Binding.BEACON, Binding.parse("beacon"));
		assertEquals("beacon", Binding.BEACON.wire());
		assertEquals(Binding.Kind.BEACON, Binding.BEACON.kind());
		assertEquals(Binding.NONE, Binding.parse("beacons"), "only the exact word");
		assertEquals(LampStatus.ERROR, LampApplier.want(Map.of("beacon", LampStatus.ERROR), Binding.BEACON));
		assertEquals(LampStatus.OFF, LampApplier.want(Map.of(), Binding.BEACON));
	}
}
