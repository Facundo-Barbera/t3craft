package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.StationPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StationRosterTest {
	private static final long MIN = 60_000;
	private static final long NOW = 5_000_000_000L;

	private static NpcInfo npc(String id, String state, boolean settled) {
		return new NpcInfo(id, id, id, "vps", "", 0xFFFFFF, null, 0, settled, state, "vps:p", "p", "", null);
	}

	@Test
	void releasedThreadsLeaveTheWorldAndGoToTheArchive() {
		StationRoster r = new StationRoster();
		r.replaceAll(List.of(npc("work", "working", false), npc("old", "done", false), npc("new", "done", false), npc("s", "idle", true)),
				Map.of("old", NOW - 6 * MIN, "new", NOW - MIN, "work", NOW - 600 * MIN));
		assertTrue(r.regate(NOW));
		assertEquals(List.of("work", "new"), r.stationed().stream().map(NpcInfo::id).toList());
		assertEquals(List.of("old", "s"), r.released().stream().map(NpcInfo::id).toList());
		assertTrue(r.released().stream().allMatch(NpcInfo::settled), "released threads are archived (settled)");
		assertFalse(r.regate(NOW), "same roster: no change");
	}

	@Test
	void doneThreadReleasesWhenItsTimeComes() {
		StationRoster r = new StationRoster();
		r.replaceAll(List.of(npc("a", "done", false)), Map.of("a", NOW - 4 * MIN));
		r.regate(NOW);
		assertEquals(1, r.stationed().size());
		assertTrue(r.regate(NOW + 2 * MIN), "past 5 minutes the station is released");
		assertTrue(r.stationed().isEmpty());
		assertEquals(1, r.released().size());
	}

	@Test
	void bridgeWithoutTimestampsCountsFromFirstSeenDone() {
		StationRoster r = new StationRoster();
		r.replaceAll(List.of(npc("a", "done", false)), null);
		r.regate(NOW);
		assertEquals(1, r.stationed().size());
		r.upsert(npc("a", "done", false));
		assertTrue(r.regate(NOW + 5 * MIN + 1));
		assertTrue(r.stationed().isEmpty());
	}

	@Test
	void policyCanBeTurnedOff() {
		StationRoster r = new StationRoster();
		r.setPolicy(new StationPolicy(false, 0));
		r.replaceAll(List.of(npc("s", "done", true)), Map.of("s", 1L));
		r.regate(NOW);
		assertEquals(1, r.stationed().size());
		assertTrue(r.released().isEmpty());
	}
}
