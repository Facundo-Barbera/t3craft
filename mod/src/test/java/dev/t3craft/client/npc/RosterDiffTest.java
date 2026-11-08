package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** fix/npc-position-reset: a reconcile spawns only newcomers and removes only leavers. */
class RosterDiffTest {
	@Test
	void addingOneSpawnsOnlyIt() {
		NpcRegistry.RosterDiff d = NpcRegistry.RosterDiff.of(List.of("a", "b", "c"), List.of("a", "b", "c", "d"));
		assertEquals(List.of("d"), d.added());
		assertEquals(List.of(), d.removed());
		assertEquals(List.of("a", "b", "c"), d.kept());
	}

	@Test
	void removingOneRemovesOnlyIt() {
		NpcRegistry.RosterDiff d = NpcRegistry.RosterDiff.of(List.of("a", "b", "c"), List.of("a", "c"));
		assertEquals(List.of(), d.added());
		assertEquals(List.of("b"), d.removed());
		assertEquals(List.of("a", "c"), d.kept());
	}

	@Test
	void sameRosterTouchesNobody() {
		NpcRegistry.RosterDiff d = NpcRegistry.RosterDiff.of(List.of("a", "b"), List.of("b", "a"));
		assertEquals(List.of(), d.added());
		assertEquals(List.of(), d.removed());
		assertEquals(List.of("b", "a"), d.kept());
	}
}
