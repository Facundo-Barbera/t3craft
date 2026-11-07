package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.t3.ThreadState;
import org.junit.jupiter.api.Test;

class StationStatusTest {
	@Test
	void mapsWireStates() {
		assertEquals(StationStatus.WORKING, StationStatus.of("working", false));
		assertEquals(StationStatus.ATTENTION, StationStatus.of("needs_attention", false));
		assertEquals(StationStatus.ERROR, StationStatus.of("error", false));
		assertEquals(StationStatus.DONE, StationStatus.of("done", false));
		assertEquals(StationStatus.IDLE, StationStatus.of("idle", false));
		assertEquals(StationStatus.IDLE, StationStatus.of("something-new", false));
		assertEquals(StationStatus.IDLE, StationStatus.of(null, false));
		assertEquals(StationStatus.WORKING, StationStatus.of("WORKING", false));
	}

	@Test
	void settledWins() {
		for (String s : new String[] {"working", "needs_attention", "error", "done", "idle", null}) {
			assertEquals(StationStatus.ARCHIVED, StationStatus.of(s, true));
		}
	}

	@Test
	void coloursMatchThreadState() {
		// Same palette as the NPC name tags / HUD, so every surface agrees.
		for (ThreadState t : ThreadState.values()) {
			assertEquals(t.color, StationStatus.of(t.wire, false).color, t.wire);
			assertEquals(t.glyph, StationStatus.of(t.wire, false).glyph, t.wire);
		}
	}

	@Test
	void backgroundIsDarkTintOfStatus() {
		assertEquals(0xD0000000 | (0xFF / 5 << 16) | (0x55 / 5 << 8) | (0x55 / 5), StationStatus.ERROR.background());
		for (StationStatus s : StationStatus.values()) {
			int bg = s.background();
			assertEquals(0xD0, bg >>> 24, s.name());
			assertTrue(((bg >> 16) & 0xFF) <= 0x33 && ((bg >> 8) & 0xFF) <= 0x33 && (bg & 0xFF) <= 0x33, s.name());
		}
	}
}
