package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class StationPolicyTest {
	private static final long MIN = 60_000;
	private static final long NOW = 1_000_000_000L;
	private static final StationPolicy P = StationPolicy.DEFAULTS;

	private static StationPolicy.Facts f(boolean settled, boolean archived, String state, long last) {
		return new StationPolicy.Facts(settled, archived, state, last);
	}

	@Test
	void settledReleases() {
		assertEquals(StationPolicy.Verdict.SETTLED, P.judge(f(true, false, "working", NOW), NOW));
	}

	@Test
	void archivedReleases() {
		assertEquals(StationPolicy.Verdict.ARCHIVED, P.judge(f(false, true, "idle", NOW), NOW));
		assertEquals(StationPolicy.Verdict.ARCHIVED, P.judge(f(true, true, "done", NOW), NOW), "archived wins over settled");
	}

	@Test
	void doneAndInactivePastTheThresholdReleases() {
		assertEquals(StationPolicy.Verdict.DONE_IDLE, P.judge(f(false, false, "done", NOW - 5 * MIN - 1), NOW));
	}

	@Test
	void recentDoneKeepsItsStation() {
		assertEquals(StationPolicy.Verdict.KEEP, P.judge(f(false, false, "done", NOW - 5 * MIN), NOW), "exactly at the threshold stays");
		assertEquals(StationPolicy.Verdict.KEEP, P.judge(f(false, false, "done", NOW - MIN), NOW));
		assertEquals(StationPolicy.Verdict.KEEP, P.judge(f(false, false, "done", 0), NOW), "unknown activity never releases by itself");
	}

	@Test
	void otherStatesKeepTheirStationHoweverOld() {
		for (String s : List.of("working", "needs_attention", "error", "idle")) {
			assertTrue(P.holdsStation(f(false, false, s, NOW - 999 * MIN), NOW), s);
		}
	}

	@Test
	void thresholdIsConfigurable() {
		StationPolicy p = new StationPolicy(true, 30 * MIN);
		assertTrue(p.holdsStation(f(false, false, "done", NOW - 20 * MIN), NOW));
		assertFalse(p.holdsStation(f(false, false, "done", NOW - 31 * MIN), NOW));
	}

	@Test
	void disabledKeepsEverything() {
		StationPolicy p = new StationPolicy(false, 0);
		assertTrue(p.holdsStation(f(true, true, "done", 1), NOW));
	}

	@Test
	void splitUsesTheDoneClockWithoutTimestamps() {
		record T(String id, String state) {
		}
		StationPolicy.DoneClock clock = new StationPolicy.DoneClock();
		List<T> roster = List.of(new T("a", "done"), new T("b", "working"));
		StationPolicy.Split<T> s1 = P.split(roster, T::id, t -> f(false, false, t.state(), 0), clock, NOW);
		assertEquals(roster, s1.stationed(), "first seen done: keeps its station");
		StationPolicy.Split<T> s2 = P.split(roster, T::id, t -> f(false, false, t.state(), 0), clock, NOW + 5 * MIN + 1);
		assertEquals(List.of(new T("b", "working")), s2.stationed());
		assertEquals(List.of(new T("a", "done")), s2.released());
		// back to work: the clock starts over
		List<T> again = List.of(new T("a", "working"));
		P.split(again, T::id, t -> f(false, false, t.state(), 0), clock, NOW + 6 * MIN);
		List<T> doneAgain = List.of(new T("a", "done"));
		assertEquals(doneAgain, P.split(doneAgain, T::id, t -> f(false, false, t.state(), 0), clock, NOW + 7 * MIN).stationed());
	}

	@Test
	void configIsAdditiveAndTolerant() {
		assertEquals(StationPolicy.DEFAULTS, StationPolicy.parse(null));
		assertEquals(StationPolicy.DEFAULTS, StationPolicy.parse(JsonParser.parseString("{\"mode\":\"direct\"}").getAsJsonObject()));
		assertEquals(5 * MIN, StationPolicy.DEFAULTS.doneIdleMs());
		StationPolicy p = StationPolicy.parse(JsonParser.parseString("{\"stations\":{\"releaseDoneAfterMinutes\":12}}").getAsJsonObject());
		assertEquals(new StationPolicy(true, 12 * MIN), p);
		StationPolicy off = StationPolicy.parse(JsonParser.parseString("{\"stations\":{\"release\":false,\"releaseDoneAfterMinutes\":\"x\"}}")
				.getAsJsonObject());
		assertEquals(new StationPolicy(false, 5 * MIN), off);
		assertEquals(StationPolicy.DEFAULTS,
				StationPolicy.parse(JsonParser.parseString("{\"stations\":{\"releaseDoneAfterMinutes\":-3}}").getAsJsonObject()));
	}
}
