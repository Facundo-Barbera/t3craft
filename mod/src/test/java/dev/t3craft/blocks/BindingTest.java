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
