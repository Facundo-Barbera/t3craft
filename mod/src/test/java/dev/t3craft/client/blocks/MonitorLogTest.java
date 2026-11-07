package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Live log model: tail truncation, icons, diff counts, "needs you" and the caret. */
class MonitorLogTest {
	static int n;

	static ChatEntry e(Kind kind, String title, String body) {
		return e(kind, title, body, "completed");
	}

	static ChatEntry e(Kind kind, String title, String body, String status) {
		n++;
		return new ChatEntry("T:i" + n, "i" + n, null, kind, title, body, null, false, status, null, n, false);
	}

	static ChatTranscript.View view(ChatTranscript.Status st, ChatEntry... entries) {
		return new ChatTranscript.View(1, "T", "Fix login loop", List.of(entries), true, false, null, st);
	}

	static final ChatTranscript.Status WORKING = new ChatTranscript.Status(ThreadState.WORKING, "running", null);

	@Test
	void toolRowsCarryIconNameArgumentAndCounts() {
		MonitorLog.Model m = MonitorLog.of(view(WORKING,
				e(Kind.TOOL, "$ ./gradlew test", ""),
				e(Kind.TOOL, "$ git status  [exit 0]", ""),
				e(Kind.TOOL, "Edit mod/src/Foo.java (+12 −3)", ""),
				e(Kind.TOOL, "Read README.md", ""),
				e(Kind.SUBAGENT, "Subagent: explore repo", ""),
				e(Kind.REASONING, "Thinking", "weighing options\nmore")), null, null, 20, 40);
		List<MonitorLog.Row> r = m.rows();
		assertEquals(6, r.size());
		assertEquals("test", r.get(0).icon());
		assertEquals("$", r.get(0).name());
		assertEquals("./gradlew test", r.get(0).text());
		assertEquals("git", r.get(1).icon());
		assertEquals("git status", r.get(1).text(), "exit 0 says nothing new");
		assertEquals("edit", r.get(2).icon());
		assertEquals("mod/src/Foo.java", r.get(2).text());
		assertEquals(12, r.get(2).adds());
		assertEquals(3, r.get(2).dels());
		assertTrue(r.get(2).hasCounts());
		assertFalse(r.get(3).hasCounts());
		assertEquals("read", r.get(3).icon());
		assertEquals("message", r.get(4).icon());
		assertEquals("memory", r.get(5).icon());
		assertEquals("thinking", r.get(5).name());
	}

	@Test
	void failedCommandIsRed() {
		MonitorLog.Row r = MonitorLog.tool(e(Kind.TOOL, "$ npm run build  [exit 2]", ""), 40);
		assertEquals(Kind.ERROR.color, r.color());
		assertEquals("npm run build", r.text());
		assertEquals(Kind.TOOL.color, MonitorLog.tool(e(Kind.TOOL, "$ ls", ""), 40).color());
	}

	@Test
	void tailKeepsTheNewestRowsAndTruncates() {
		List<ChatEntry> es = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			es.add(e(Kind.TOOL, "$ step " + i, ""));
		}
		es.add(e(Kind.TOOL, "Edit a/very/long/path/to/some/deeply/nested/MonitorRenderer.java (+1 −1)", ""));
		MonitorLog.Model m = MonitorLog.of(view(WORKING, es.toArray(ChatEntry[]::new)), null, null, 8, 24);
		assertEquals(8, m.rows().size());
		assertEquals("step 23", m.rows().getFirst().text(), "oldest shown row (7 steps + the edit)");
		MonitorLog.Row last = m.rows().getLast();
		assertTrue(last.text().startsWith(MonitorLog.ELLIPSIS), "paths keep their end: " + last.text());
		assertEquals(MonitorLog.ELLIPSIS + "derer.java", last.text(), "11 columns left after icon, name and counts");
		for (MonitorLog.Row r : m.rows()) {
			int width = 2 + r.name().length() + 1 + r.text().length() + (r.hasCounts() ? (" +" + r.adds() + " -" + r.dels()).length() : 0);
			assertTrue(width <= 24, "fits: " + r);
		}
	}

	@Test
	void proseWrapsToTwoLinesWithoutMarkdown() {
		MonitorLog.Model m = MonitorLog.of(view(WORKING,
				e(Kind.ASSISTANT, "", "## Plan\nI will **fix** the `redirect` loop in the login handler and then run all the tests again")), null, null, 10, 20);
		assertEquals(2, m.rows().size());
		assertEquals("Plan I will fix the", m.rows().get(0).text());
		assertTrue(m.rows().get(1).text().endsWith(MonitorLog.ELLIPSIS));
		assertTrue(m.rows().get(1).text().length() <= 20);
		assertFalse(m.rows().get(0).text().contains("*"));
	}

	@Test
	void cursorWhileWorkingOnly() {
		assertTrue(MonitorLog.of(view(WORKING), null, null, 5, 20).cursor());
		ChatTranscript.Status done = new ChatTranscript.Status(ThreadState.DONE, "completed", null);
		MonitorLog.Model m = MonitorLog.of(view(done), null, null, 5, 20);
		assertFalse(m.cursor());
		assertNull(m.needsYou());
		assertEquals(StationStatus.DONE, m.status());
		assertEquals(StationStatus.WORKING, MonitorLog.of(view(done), StationStatus.WORKING, null, 5, 20).status(), "the shell's status wins");
	}

	@Test
	void pendingRequestShowsNeedsYouAndNoCursor() {
		ChatTranscript.Status st = new ChatTranscript.Status(ThreadState.NEEDS_ATTENTION, "waiting for you",
				"You: approve a tool call, asked by this thread's agent");
		MonitorLog.Model m = MonitorLog.of(view(st, e(Kind.REQUEST, "Approval: command", "rm -rf build")), StationStatus.WORKING, null, 5, 60);
		assertEquals(StationStatus.ATTENTION, m.status(), "a pending request beats 'working'");
		assertEquals("approve a tool call, asked by this thread's agent", m.needsYou());
		assertFalse(m.cursor());
		assertEquals("decision", m.rows().getLast().icon());
		assertEquals(MonitorLog.Kind.REQUEST, m.rows().getLast().kind());

		MonitorLog.Model idleAttention = MonitorLog.of(view(new ChatTranscript.Status(ThreadState.IDLE, "idle", null)), StationStatus.ATTENTION, null, 5, 60);
		assertEquals("waiting for you", idleAttention.needsYou());
	}

	@Test
	void titleFallsBackAndInheritedRowsAreSkipped() {
		ChatEntry inherited = new ChatEntry("P:x", "x", null, Kind.TOOL, "$ parent", "", null, false, "completed", null, 0, true);
		ChatTranscript.View v = new ChatTranscript.View(1, "T", "", List.of(inherited, e(Kind.TOOL, "$ mine", "")), true, false, null, WORKING);
		MonitorLog.Model m = MonitorLog.of(v, null, "Shell title", 5, 30);
		assertEquals("Shell title", m.title());
		assertEquals(1, m.rows().size());
		assertEquals("mine", m.rows().getFirst().text());
	}

	@Test
	void clipHelpers() {
		assertEquals("abc", MonitorLog.clip("abc", 3));
		assertEquals("ab…", MonitorLog.clip("abcd", 3));
		assertEquals("…cd", MonitorLog.clipLeft("abcd", 3));
		assertEquals(List.of("aaaa", "aa b"), MonitorLog.wrap("aaaaaa b", 4));
	}
}
