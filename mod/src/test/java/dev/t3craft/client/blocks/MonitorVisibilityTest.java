package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.blocks.MonitorVisibility.Observation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Visibility policy: acquire near and visible, keep with hysteresis, release, dedupe by thread. */
class MonitorVisibilityTest {
	// radius 16, hysteresis 4, release after 10 s
	static final MonitorSettings S = MonitorSettings.DEFAULT;

	static Observation o(long pos, String key, double d, boolean visible) {
		return new Observation(pos, key, d, visible);
	}

	@Test
	void acquiresOnlyWhenCloseAndVisible() {
		MonitorVisibility v = new MonitorVisibility(S);
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 20, true)), 0), "too far");
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 10, false)), 100), "close but not visible");
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 10, true)), 200));
		assertTrue(v.live(1));
	}

	@Test
	void distanceHysteresis() {
		MonitorVisibility v = new MonitorVisibility(S);
		v.update(List.of(o(1, "e:a", 15, true)), 0);
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 19, true)), 50), "inside radius + hysteresis: keeps");
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 20.5, true)), 100), "beyond: releases");
		assertFalse(v.live(1));
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 18, true)), 150), "re-acquire needs the inner radius");
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 16, true)), 200));
	}

	@Test
	void outOfViewReleasesAfterGrace() {
		MonitorVisibility v = new MonitorVisibility(S);
		v.update(List.of(o(1, "e:a", 5, true)), 0);
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 5, false)), 9_000), "turned around briefly: keeps");
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 5, true)), 9_500), "looked back: timer refreshed");
		assertEquals(Set.of("e:a"), v.update(List.of(o(1, "e:a", 5, false)), 19_000));
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 5, false)), 19_600), "out of view > 10 s: releases");
	}

	@Test
	void oneKeyPerThreadAcrossMonitors() {
		MonitorVisibility v = new MonitorVisibility(S);
		Set<String> w = v.update(List.of(o(1, "e:a", 3, true), o(2, "e:a", 4, true), o(3, "e:b", 5, true), o(4, null, 2, true)), 0);
		assertEquals(Set.of("e:a", "e:b"), w);
		assertFalse(v.live(4), "unbound monitor never subscribes");
		// one of the two "e:a" monitors goes away: the thread stays wanted through the other
		assertEquals(Set.of("e:a", "e:b"), v.update(List.of(o(2, "e:a", 4, true), o(3, "e:b", 5, true)), 100));
		assertEquals(2, v.tracked(), "an unreported monitor is forgotten");
	}

	@Test
	void rebindingFollowsTheNewThread() {
		MonitorVisibility v = new MonitorVisibility(S);
		v.update(List.of(o(1, "e:a", 3, true)), 0);
		assertEquals(Set.of("e:b"), v.update(List.of(o(1, "e:b", 3, true)), 100));
	}

	@Test
	void liveLogOffWantsNothing() {
		MonitorSettings off = new MonitorSettings(false, 16, 4, 10_000, MonitorSettings.Visibility.FRUSTUM, 64, 8);
		MonitorVisibility v = new MonitorVisibility(off);
		assertEquals(Set.of(), v.update(List.of(o(1, "e:a", 1, true)), 0));
	}

	@Test
	void viewCone() {
		// looking down +Z
		assertTrue(MonitorVisibility.inCone(0, 0, 10, 0, 0, 1, 45));
		assertTrue(MonitorVisibility.inCone(5, 0, 10, 0, 0, 1, 45), "26.6 degrees off axis");
		assertFalse(MonitorVisibility.inCone(10, 0, 5, 0, 0, 1, 45), "63 degrees off axis");
		assertFalse(MonitorVisibility.inCone(0, 0, -10, 0, 0, 1, 80), "behind");
		assertEquals(45, MonitorVisibility.horizontalHalfAngle(90, 1), 1e-9);
		assertTrue(MonitorVisibility.horizontalHalfAngle(70, 16 / 9.0) > 35);
	}
}
