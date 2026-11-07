package dev.t3craft.client.npc.helpers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HelperRosterTest {
	static ThreadShell thread(String id, String extra) {
		JsonObject o = JsonParser.parseString("""
				{"id":"%s","projectId":"p1","title":"T %s","status":"idle",
				 "lineage":{"parentThreadId":null,"relationshipToParent":null,"rootThreadId":"%s"},
				 "createdAt":"2026-11-07T10:00:00Z"}
				""".formatted(id, id, id)).getAsJsonObject();
		JsonParser.parseString(extra.isBlank() ? "{}" : extra).getAsJsonObject().entrySet().forEach(e -> o.add(e.getKey(), e.getValue()));
		return ThreadShell.fromJson(o);
	}

	static ThreadShell sub(String id, String parent, String status, String extra) {
		String lineage = "\"lineage\":{\"parentThreadId\":\"" + parent + "\",\"relationshipToParent\":\"subagent\",\"rootThreadId\":\"" + parent + "\"}";
		String body = "{\"status\":\"" + status + "\"," + lineage + (extra.isBlank() ? "" : "," + extra) + "}";
		return thread(id, body);
	}

	static HelperView h(String id, String parent, ThreadState s, long started) {
		return new HelperView(id, parent, id, s, started);
	}

	static List<String> ids(List<HelperView> xs) {
		return xs.stream().map(HelperView::id).toList();
	}

	@Test
	void decodesParentThreadIdFromLineage() {
		ThreadShell t = sub("s", "p", "running", "");
		assertEquals("p", t.parentThreadId());
		assertTrue(HelperRoster.isSubagent(t));
		assertNull(thread("top", "").parentThreadId());
		assertFalse(HelperRoster.isSubagent(thread("top", "")));
	}

	@Test
	void collectKeepsRunningSubagentsAndRemembersAllSubagentIds() {
		var threads = List.of(
				thread("p", "{\"status\":\"running\"}"),
				sub("run", "p", "running", ""),
				sub("ask", "p", "waiting", ""),
				sub("turn", "p", "idle", "\"activeRunId\":\"r\""),
				sub("done", "p", "completed", ""),
				sub("failed", "p", "failed", ""),
				sub("archived", "p", "running", "\"archivedAt\":\"2026-11-07T11:00:00Z\""),
				sub("settled", "p", "running", "\"settledOverride\":\"settled\""),
				thread("orphan", "{\"status\":\"running\",\"lineage\":{\"parentThreadId\":null,\"relationshipToParent\":\"subagent\"}}"),
				thread("fork", "{\"status\":\"running\",\"lineage\":{\"parentThreadId\":\"p\",\"relationshipToParent\":\"fork\"}}"));
		HelperRoster.Collected c = HelperRoster.collect("env", threads);
		assertEquals(List.of("env:run", "env:ask", "env:turn"), ids(c.active()));
		assertTrue(c.active().stream().allMatch(v -> v.parentId().equals("env:p")));
		assertEquals(ThreadState.NEEDS_ATTENTION, c.active().get(1).state());
		assertEquals(Set.of("env:run", "env:ask", "env:turn", "env:done", "env:failed", "env:archived", "env:settled", "env:orphan"),
				c.subagentNpcIds());
	}

	@Test
	void collectAcrossEnvironmentsNamespacesIds() {
		var a = new ShellState.View(List.of(sub("s", "p", "running", "")), Map.of(), true);
		var b = new ShellState.View(List.of(sub("s", "p", "running", "")), Map.of(), true);
		HelperRoster.Collected c = HelperRoster.collect(Map.of("b", b, "a", a));
		assertEquals(List.of("a:s", "b:s"), ids(c.active()));
		assertEquals(List.of("a:p", "b:p"), c.active().stream().map(HelperView::parentId).toList());
	}

	@Test
	void deskRosterDropsSubagentsAndKeepsOrder() {
		List<String> roster = List.of("e:p2", "e:sub1", "e:p1", "e:sub2");
		assertEquals(List.of("e:p2", "e:p1"), HelperRoster.deskRoster(roster, x -> x, Set.of("e:sub1", "e:sub2", "e:unrelated")));
		assertSame(roster, HelperRoster.deskRoster(roster, x -> x, Set.of()));
	}

	@Test
	void groupsUnderParentWithCapAndOverflow() {
		var helpers = List.of(
				h("a", "P", ThreadState.WORKING, 1),
				h("b", "P", ThreadState.WORKING, 2),
				h("c", "P", ThreadState.WORKING, 3),
				h("d", "P", ThreadState.WORKING, 4),
				h("e", "P", ThreadState.WORKING, 5),
				h("x", "Q", ThreadState.WORKING, 1),
				h("lost", "GONE", ThreadState.WORKING, 1));
		Map<String, HelperRoster.Family> f = HelperRoster.group(helpers, List.of("Q", "P"), 3);
		assertEquals(List.of("Q", "P"), List.copyOf(f.keySet()));
		assertEquals(List.of("a", "b", "c"), ids(f.get("P").shown()));
		assertEquals(2, f.get("P").overflow());
		assertEquals(5, f.get("P").total());
		assertEquals(List.of("x"), ids(f.get("Q").shown()));
		assertEquals(0, f.get("Q").overflow());
	}

	@Test
	void helpersWaitingForTheUserWinTheCapButSlotsStayOldestFirst() {
		var helpers = List.of(
				h("old", "P", ThreadState.WORKING, 1),
				h("mid", "P", ThreadState.WORKING, 2),
				h("asks", "P", ThreadState.NEEDS_ATTENTION, 9));
		HelperRoster.Family f = HelperRoster.group(helpers, List.of("P"), 2).get("P");
		assertEquals(List.of("old", "asks"), ids(f.shown()));
		assertEquals(1, f.overflow());
	}

	@Test
	void capZeroLeavesOnlyTheCount() {
		HelperRoster.Family f = HelperRoster.group(List.of(h("a", "P", ThreadState.WORKING, 1)), List.of("P"), 0).get("P");
		assertEquals(List.of(), f.shown());
		assertEquals(1, f.overflow());
		assertEquals("⚒ +1", HelperRoster.panelLine(f));
	}

	@Test
	void nestedSubagentsJoinTheNearestVisibleAncestor() {
		var helpers = List.of(
				h("child", "P", ThreadState.WORKING, 1),
				h("grandchild", "child", ThreadState.WORKING, 2),
				h("loopA", "loopB", ThreadState.WORKING, 3),
				h("loopB", "loopA", ThreadState.WORKING, 4));
		Map<String, HelperRoster.Family> f = HelperRoster.group(helpers, List.of("P"), 5);
		assertEquals(List.of("child", "grandchild"), ids(f.get("P").shown()));
		assertEquals(1, f.size());
	}

	@Test
	void aSubagentThatHasItsOwnNpcIsNotAlsoAHelper() {
		// With includeSubagents and helpers off a subagent can be a top-level NPC; never draw it twice.
		Map<String, HelperRoster.Family> f = HelperRoster.group(List.of(h("s", "P", ThreadState.WORKING, 1)), List.of("P", "s"), 3);
		assertTrue(f.isEmpty());
	}

	@Test
	void panelLine() {
		assertNull(HelperRoster.panelLine(null));
		assertNull(HelperRoster.panelLine(new HelperRoster.Family("P", List.of(), 0)));
		assertEquals("⚒ 1 helper", HelperRoster.panelLine(new HelperRoster.Family("P", List.of(h("a", "P", ThreadState.WORKING, 1)), 0)));
		assertEquals("⚒ 2 helpers +4", HelperRoster.panelLine(new HelperRoster.Family("P",
				List.of(h("a", "P", ThreadState.WORKING, 1), h("b", "P", ThreadState.WORKING, 2)), 4)));
	}

	@Test
	void variantIsStableAndInRange() {
		HelperView v = h("env:abc", "P", ThreadState.WORKING, 1);
		assertEquals(v.variant(7), h("env:abc", "Q", ThreadState.DONE, 9).variant(7));
		for (String id : List.of("a", "b", "c", "dddd", "")) {
			int x = h(id, "P", ThreadState.WORKING, 0).variant(7);
			assertTrue(x >= 0 && x < 7);
		}
		assertEquals(0, v.variant(0));
	}
}
