package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.LampStatus;
import org.junit.jupiter.api.Test;

/** Breathing curves, motes and who draws the hub beacon. */
class LampGlowTest {
	private static final double STEP = 0.05;

	private static float min(LampStatus s, double span) {
		float m = Float.MAX_VALUE;
		for (double t = 0; t <= span; t += STEP) {
			m = Math.min(m, LampGlow.level(s, t));
		}
		return m;
	}

	private static float max(LampStatus s, double span) {
		float m = -Float.MAX_VALUE;
		for (double t = 0; t <= span; t += STEP) {
			m = Math.max(m, LampGlow.level(s, t));
		}
		return m;
	}

	@Test
	void levelsStayInRange() {
		for (LampStatus s : LampStatus.values()) {
			for (double t = 0; t < 20; t += 0.37) {
				float l = LampGlow.level(s, t);
				assertTrue(l >= 0f && l <= 1f, s + " at " + t + ": " + l);
			}
		}
		assertEquals(0f, max(LampStatus.OFF, 10), "off is dark");
	}

	@Test
	void steadyStatesHold() {
		for (LampStatus s : new LampStatus[] {LampStatus.OFF, LampStatus.IDLE, LampStatus.DONE}) {
			assertFalse(LampGlow.breathes(s), s.name());
			assertEquals(min(s, 10), max(s, 10), 1e-6f, s + " is steady");
		}
		assertTrue(LampGlow.level(LampStatus.DONE, 0) > LampGlow.level(LampStatus.IDLE, 0));
	}

	@Test
	void needsYouBreathesSlowAndDeep() {
		assertTrue(LampGlow.breathes(LampStatus.ATTENTION));
		// one slow breath every 4 s: dim at 0, full at 2 s, dim again at 4 s
		assertEquals(LampGlow.level(LampStatus.ATTENTION, 0), LampGlow.level(LampStatus.ATTENTION, 4), 1e-5f);
		assertEquals(1f, LampGlow.level(LampStatus.ATTENTION, 2), 1e-5f);
		assertTrue(LampGlow.level(LampStatus.ATTENTION, 1) > LampGlow.level(LampStatus.ATTENTION, 0.5), "rising");
		float depth = max(LampStatus.ATTENTION, 4) - min(LampStatus.ATTENTION, 4);
		for (LampStatus s : new LampStatus[] {LampStatus.ERROR, LampStatus.WORKING}) {
			assertTrue(depth > max(s, 4) - min(s, 4), "needs you breathes deepest, deeper than " + s);
		}
		assertTrue(LampGlow.ATTENTION_HZ < LampGlow.WORKING_HZ && LampGlow.ATTENTION_HZ < LampGlow.ERROR_HZ, "and slowest");
	}

	@Test
	void errorAndWorkingPulse() {
		assertTrue(LampGlow.breathes(LampStatus.ERROR));
		assertTrue(LampGlow.breathes(LampStatus.WORKING));
		assertEquals(LampGlow.level(LampStatus.ERROR, 0), LampGlow.level(LampStatus.ERROR, 1 / LampGlow.ERROR_HZ), 1e-5f);
		assertTrue(max(LampStatus.ERROR, 2) - min(LampStatus.ERROR, 2) > 0.2f);
		assertTrue(max(LampStatus.WORKING, 4) - min(LampStatus.WORKING, 4) > 0.1f);
	}

	@Test
	void alphaAndColour() {
		assertEquals(30, LampGlow.alpha(0f, 30, 200));
		assertEquals(200, LampGlow.alpha(1f, 30, 200));
		assertEquals(200, LampGlow.alpha(3f, 30, 200), "clamped");
		assertEquals(0x80FFAA00, LampGlow.argb(LampStatus.ATTENTION.color, 0x80));
	}

	@Test
	void motesOnlyWhenTheLampAsksForYou() {
		for (LampStatus s : LampStatus.values()) {
			boolean attention = s == LampStatus.ATTENTION;
			assertEquals(attention, LampGlow.motes(Binding.Kind.THREAD, s), "desk lamp " + s);
			assertEquals(attention, LampGlow.motes(Binding.Kind.BEACON, s), "beacon " + s);
			assertEquals(attention, LampGlow.motes(Binding.Kind.NONE, s), "unbound " + s);
			// a lit board bulb = a thread waiting on you (needs you or error)
			assertEquals(attention || s == LampStatus.ERROR, LampGlow.motes(Binding.Kind.BOARD, s), "board bulb " + s);
		}
	}

	@Test
	void beaconModes() {
		assertEquals(LampGlow.BeaconMode.NONE, LampGlow.beaconMode(false, false), "no HQ, no beacon");
		assertEquals(LampGlow.BeaconMode.NONE, LampGlow.beaconMode(false, true));
		assertEquals(LampGlow.BeaconMode.BLOCK, LampGlow.beaconMode(true, true), "module on: the lamp's BER");
		assertEquals(LampGlow.BeaconMode.CLIENT, LampGlow.beaconMode(true, false), "module off: client-only glow");
	}
}
