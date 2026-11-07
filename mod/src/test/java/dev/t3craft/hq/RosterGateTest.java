package dev.t3craft.hq;

import static dev.t3craft.hq.RosterGate.Decision.RECONCILE;
import static dev.t3craft.hq.RosterGate.Decision.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RosterGateTest {
	@Test
	void waitsForTheFirstSyncOfEveryEnvironment() {
		assertEquals(WAIT, RosterGate.decide(Set.of("vps", "laptop"), Set.of(), true, 0));
		assertEquals(WAIT, RosterGate.decide(Set.of("vps", "laptop"), Set.of("vps"), true, 10 * 60_000), "an old HQ is never torn down early");
		assertEquals(RECONCILE, RosterGate.decide(Set.of("vps", "laptop"), Set.of("vps", "laptop"), true, 0));
	}

	@Test
	void nothingPairedReconcilesAtOnce() {
		assertEquals(RECONCILE, RosterGate.decide(Set.of(), Set.of(), true, 0));
	}

	@Test
	void firstBuildStopsWaitingAfterTheTimeout() {
		assertEquals(WAIT, RosterGate.decide(Set.of("vps"), Set.of(), false, RosterGate.FIRST_BUILD_TIMEOUT_MS - 1));
		assertEquals(RECONCILE, RosterGate.decide(Set.of("vps"), Set.of(), false, RosterGate.FIRST_BUILD_TIMEOUT_MS));
	}

	@Test
	void bridgeWaitsForItsFirstSnapshot() {
		assertEquals(WAIT, RosterGate.decide(Set.of("bridge"), Set.of(), true, 0));
		assertEquals(RECONCILE, RosterGate.decide(Set.of("bridge"), Set.of("bridge"), true, 0));
	}
}
