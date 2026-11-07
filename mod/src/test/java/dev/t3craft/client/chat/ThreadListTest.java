package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.t3.ThreadState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThreadListTest {
	static ThreadRow row(String env, String id, ThreadState s, boolean pending, Long at) {
		return new ThreadRow(env + ":" + id, env, env.toUpperCase(), id, "t-" + id, "", s, pending, null, false, at);
	}

	static Map<String, ThreadList.EnvInfo> envs(String... ids) {
		Map<String, ThreadList.EnvInfo> m = new LinkedHashMap<>();
		for (String id : ids) {
			m.put(id, new ThreadList.EnvInfo(id, "ok", null));
		}
		return m;
	}

	@Test
	void ordersByAttentionThenRecentActivity() {
		ThreadList l = new ThreadList();
		l.update(List.of(
				row("a", "idleNew", ThreadState.IDLE, false, 900L),
				row("b", "working", ThreadState.WORKING, false, 100L),
				row("a", "pending", ThreadState.WORKING, true, 50L),
				row("b", "error", ThreadState.ERROR, false, 10L),
				row("a", "needsOld", ThreadState.NEEDS_ATTENTION, true, 1L),
				row("b", "needsNew", ThreadState.NEEDS_ATTENTION, false, 5L),
				row("a", "doneUnknown", ThreadState.DONE, false, null),
				row("b", "doneNew", ThreadState.DONE, false, 70L)), envs("a", "b"));
		assertEquals(List.of("needsNew", "needsOld", "error", "pending", "working", "doneNew", "doneUnknown", "idleNew"),
				l.visible().stream().map(ThreadRow::threadId).toList());
		assertEquals(4, l.attention());
	}

	@Test
	void settledSortsAfterActiveWithSameRankAndTime() {
		ThreadRow active = row("a", "x", ThreadState.DONE, false, 5L);
		ThreadRow settled = new ThreadRow("a:y", "a", "A", "y", "a-first", "", ThreadState.DONE, false, null, true, 5L);
		ThreadList l = new ThreadList();
		l.update(List.of(settled, active), envs("a"));
		assertEquals("x", l.visible().getFirst().threadId());
	}

	@Test
	void filterTogglesSoloAndAll() {
		ThreadList l = new ThreadList();
		l.update(List.of(row("a", "1", ThreadState.IDLE, false, 1L), row("b", "2", ThreadState.IDLE, false, 2L),
				row("c", "3", ThreadState.IDLE, false, 3L)), envs("a", "b", "c"));
		l.toggle("b");
		assertEquals(Set.of("b"), l.hidden());
		assertEquals(List.of("3", "1"), l.visible().stream().map(ThreadRow::threadId).toList());
		assertTrue(l.filtered());
		l.solo("c");
		assertEquals(Set.of("a", "b"), l.hidden());
		l.solo("c"); // again: show all
		assertTrue(l.hidden().isEmpty());
		l.toggle("a");
		l.toggle("b");
		l.toggle("c"); // hiding the last one shows them all
		assertTrue(l.hidden().isEmpty());
		l.setHidden(Set.of("gone", "a"));
		assertEquals(2, l.visible().size());
		assertTrue(l.hidden().contains("gone"), "unknown ids are kept for the config");
		l.showAll();
		assertFalse(l.filtered());
	}

	@Test
	void selectionFollowsKeyAndMovesWithinVisible() {
		ThreadList l = new ThreadList();
		l.update(List.of(row("a", "1", ThreadState.IDLE, false, 3L), row("b", "2", ThreadState.IDLE, false, 2L),
				row("a", "3", ThreadState.IDLE, false, 1L)), envs("a", "b"));
		assertNull(l.selected());
		assertTrue(l.move(1));
		assertEquals("a:1", l.selectedKey());
		l.move(1);
		assertEquals("b:2", l.selectedKey());
		l.update(List.of(row("a", "1", ThreadState.IDLE, false, 3L), row("b", "2", ThreadState.NEEDS_ATTENTION, false, 2L),
				row("a", "3", ThreadState.IDLE, false, 1L)), envs("a", "b"));
		assertEquals(0, l.selectedIndex(), "selection follows the thread when it jumps to the top");
		assertFalse(l.move(-1));
		l.toggle("b");
		assertNull(l.selected(), "hidden by the filter");
		l.select("a:3");
		assertEquals("a:3", l.selected().key());
	}

	@Test
	void environmentsCarryCountsAndConfiguredColour() {
		ThreadList l = new ThreadList();
		Map<String, ThreadList.EnvInfo> m = new LinkedHashMap<>();
		m.put("a", new ThreadList.EnvInfo("Alpha", "ok", 0x123456));
		l.update(List.of(row("a", "1", ThreadState.NEEDS_ATTENTION, false, 1L), row("z", "2", ThreadState.IDLE, false, 1L)), m);
		assertEquals(2, l.environments().size());
		ThreadList.Env a = l.environments().getFirst();
		assertEquals(1, a.threads());
		assertEquals(1, a.attention());
		assertEquals(0xFF123456, a.badge().background());
		assertEquals("A", a.badge().initial());
		assertEquals("z", l.environments().get(1).id(), "rows without a status entry still get a chip");
	}
}
