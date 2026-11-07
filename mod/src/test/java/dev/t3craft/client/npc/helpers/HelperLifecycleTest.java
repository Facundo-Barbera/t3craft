package dev.t3craft.client.npc.helpers;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.t3.ThreadState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HelperLifecycleTest {
	static HelperView h(String id, String parent) {
		return new HelperView(id, parent, id, ThreadState.WORKING, id.hashCode());
	}

	static List<String> ids(List<HelperLifecycle.Placed> xs) {
		return xs.stream().map(p -> p.view().id()).toList();
	}

	static Map<HelperLifecycle.Reason, List<String>> gone(HelperLifecycle.Diff d) {
		Map<HelperLifecycle.Reason, List<String>> m = new java.util.EnumMap<>(HelperLifecycle.Reason.class);
		d.despawned().forEach(x -> m.computeIfAbsent(x.reason(), k -> new java.util.ArrayList<>()).add(x.last().view().id()));
		return m;
	}

	/** Runs the same pipeline as the director: group, then diff. */
	static HelperLifecycle.Diff step(HelperLifecycle life, List<HelperView> running, List<String> parents, int cap) {
		var families = HelperRoster.group(running, parents, cap);
		Set<String> runningIds = new java.util.HashSet<>();
		running.forEach(v -> runningIds.add(v.id()));
		return life.update(families, runningIds, parents);
	}

	@Test
	void appearsWhenStartedAndDisappearsWhenFinished() {
		HelperLifecycle life = new HelperLifecycle();
		var d1 = step(life, List.of(h("a", "P")), List.of("P"), 3);
		assertEquals(List.of("a"), ids(d1.spawned()));
		assertTrue(d1.despawned().isEmpty());

		assertTrue(step(life, List.of(h("a", "P")), List.of("P"), 3).isEmpty());

		var d3 = step(life, List.of(), List.of("P"), 3);
		assertEquals(Map.of(HelperLifecycle.Reason.FINISHED, List.of("a")), gone(d3));
		assertTrue(life.shown().isEmpty());
	}

	@Test
	void releasedTogetherWithTheParent() {
		HelperLifecycle life = new HelperLifecycle();
		step(life, List.of(h("a", "P"), h("b", "P"), h("x", "Q")), List.of("P", "Q"), 3);
		var d = step(life, List.of(h("a", "P"), h("b", "P"), h("x", "Q")), List.of("Q"), 3);
		assertEquals(Map.of(HelperLifecycle.Reason.PARENT_GONE, List.of("a", "b")), gone(d));
		assertEquals(Set.of("x"), life.shown().keySet());
	}

	@Test
	void capOverflowHidesWithoutFinishing() {
		HelperLifecycle life = new HelperLifecycle();
		step(life, List.of(h("a", "P"), h("b", "P")), List.of("P"), 2);
		var d = step(life, List.of(h("a", "P"), h("b", "P")), List.of("P"), 1);
		assertEquals(Map.of(HelperLifecycle.Reason.HIDDEN, List.of("b")), gone(d));
		// a's slot count changed (2 → 1): it is updated so it re-centres behind the parent.
		assertEquals(List.of("a"), ids(d.updated()));
		assertEquals(0, d.updated().get(0).index());
		assertEquals(1, d.updated().get(0).count());
	}

	@Test
	void slotsFollowGroupOrder() {
		HelperLifecycle life = new HelperLifecycle();
		var d = step(life, List.of(new HelperView("late", "P", "late", ThreadState.WORKING, 20),
				new HelperView("early", "P", "early", ThreadState.WORKING, 10)), List.of("P"), 3);
		var byId = new java.util.HashMap<String, HelperLifecycle.Placed>();
		d.spawned().forEach(p -> byId.put(p.view().id(), p));
		assertEquals(0, byId.get("early").index());
		assertEquals(1, byId.get("late").index());
		assertEquals(2, byId.get("late").count());
	}

	@Test
	void stateChangeIsAnUpdateNotARespawn() {
		HelperLifecycle life = new HelperLifecycle();
		step(life, List.of(h("a", "P")), List.of("P"), 3);
		var asks = new HelperView("a", "P", "a", ThreadState.NEEDS_ATTENTION, "a".hashCode());
		var d = step(life, List.of(asks), List.of("P"), 3);
		assertTrue(d.spawned().isEmpty());
		assertTrue(d.despawned().isEmpty());
		assertEquals(ThreadState.NEEDS_ATTENTION, d.updated().get(0).view().state());
	}

	@Test
	void resetForgetsSilently() {
		HelperLifecycle life = new HelperLifecycle();
		step(life, List.of(h("a", "P")), List.of("P"), 3);
		life.reset();
		var d = step(life, List.of(h("a", "P")), List.of("P"), 3);
		assertEquals(List.of("a"), ids(d.spawned()));
	}
}
