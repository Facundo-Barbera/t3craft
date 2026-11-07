package dev.t3craft.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PerfStatsTest {
	static final long SECOND = 1_000_000_000L;

	@Test
	void ratesOverTheLastWindow() {
		AtomicLong now = new AtomicLong();
		PerfStats s = new PerfStats(now::get);
		s.add("nav.nodes", 500);
		assertFalse(s.roll(SECOND), "window not over yet");
		now.set(SECOND);
		assertTrue(s.roll(SECOND));
		assertEquals(500, s.rate("nav.nodes"), 1e-9);
		s.add("nav.nodes", 100);
		now.set(3 * SECOND);
		assertTrue(s.roll(SECOND));
		assertEquals(50, s.rate("nav.nodes"), 1e-9, "100 over two seconds");
		assertEquals(600, s.total("nav.nodes"));
		now.set(4 * SECOND);
		s.roll(SECOND);
		assertEquals(0, s.rate("nav.nodes"), 1e-9, "quiet second");
	}

	@Test
	void timersFormatAsMillisecondsPerSecond() {
		PerfStats.Rate r = new PerfStats.Rate("tick.npcs.ns", 0, 3_500_000);
		assertTrue(r.timer());
		assertEquals("tick.npcs 3.50 ms/s", PerfStats.format(r));
		assertEquals("display.set_text 12/s (total 40)", PerfStats.format(new PerfStats.Rate("display.set_text", 40, 12)));
	}

	@Test
	void resetForgetsEverything() {
		AtomicLong now = new AtomicLong();
		PerfStats s = new PerfStats(now::get);
		s.add("a", 3);
		now.set(SECOND);
		s.roll(SECOND);
		s.reset();
		assertEquals(0, s.total("a"));
		assertTrue(s.rates().isEmpty());
	}
}
