package dev.t3craft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientEntityIdsTest {
	@Test
	void rangesDoNotOverlap() {
		for (ClientEntityIds a : ClientEntityIds.values()) {
			assertTrue(a.contains(a.base));
			assertTrue(a.contains(a.base - ClientEntityIds.SPAN + 1));
			assertFalse(a.contains(a.base + 1));
			for (ClientEntityIds b : ClientEntityIds.values()) {
				if (a != b) {
					assertFalse(b.contains(a.base), a + " base in " + b);
				}
			}
		}
	}

	@Test
	void orphansAreOnlyUntrackedIdsOfTheOwnRange() {
		List<Integer> present = List.of(42, -1_000_000, -1_000_001, -2_000_000, -5_000_000, -5_000_001, -5_000_002);
		assertEquals(List.of(-5_000_001, -5_000_002), ClientEntityIds.WORKSTATION.orphans(present, Set.of(-5_000_000)),
				"stations of released threads are swept; other ranges and server entities never");
		assertEquals(List.of(-1_000_001), ClientEntityIds.NPC.orphans(present, Set.of(-1_000_000)));
		assertTrue(ClientEntityIds.WORKSTATION.orphans(present, Set.of(-5_000_000, -5_000_001, -5_000_002)).isEmpty());
	}
}
