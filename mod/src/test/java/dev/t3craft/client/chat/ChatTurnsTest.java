package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.ChatTurns.Role;
import dev.t3craft.client.chat.ChatTurns.ToolRun;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatTurnsTest {
	static ChatEntry e(String id, Kind k, String title, String body, String status, Long at) {
		return new ChatEntry("t:" + id, id, null, k, title, body, null, false, status, at, 0, false);
	}

	@Test
	void groupsTurnsAndCollapsesToolRuns() {
		List<ChatEntry> in = List.of(
				e("u1", Kind.USER, "", "fix it", "completed", 10L),
				e("r1", Kind.REASONING, "", "hmm", "completed", 11L),
				e("c1", Kind.TOOL, "$ ls -la", "", "completed", 12L),
				e("c2", Kind.TOOL, "Edit Foo.java (+1 −0)", "", "completed", 13L),
				e("a1", Kind.ASSISTANT, "", "done", "completed", 14L),
				e("c3", Kind.TOOL, "$ ./gradlew test", "", "running", 15L),
				e("s1", Kind.SYSTEM, "Compaction", "", "completed", 16L),
				e("u2", Kind.USER, "", "thanks", "completed", 17L));
		List<ChatTurns.Turn> turns = ChatTurns.group(in);
		assertEquals(List.of(Role.USER, Role.AGENT, Role.NOTICE, Role.USER), turns.stream().map(ChatTurns.Turn::role).toList());
		ChatTurns.Turn agent = turns.get(1);
		assertEquals(11L, agent.at());
		assertEquals(3, agent.blocks().size());
		ToolRun run = assertInstanceOf(ToolRun.class, agent.blocks().get(0));
		assertEquals(3, run.entries().size());
		assertEquals("run:t:r1", run.key());
		assertEquals("3 steps · thinking, $, Edit", run.summary(3));
		assertEquals("3 steps · thinking, $ +1", run.summary(2));
		assertInstanceOf(ChatTurns.Message.class, agent.blocks().get(1));
		ToolRun tail = assertInstanceOf(ToolRun.class, agent.blocks().get(2));
		assertTrue(tail.running());
		assertEquals("1 step · $", tail.summary(3));
	}

	@Test
	void thoughtsOnlySummary() {
		ToolRun r = new ToolRun("run:x", List.of(e("r", Kind.REASONING, "", "a", "completed", null), e("r2", Kind.REASONING, "", "b", "failed", null)));
		assertEquals("2 thoughts", r.summary(3));
		assertTrue(r.failed());
	}

	@Test
	void emptyAndAgentOnly() {
		assertTrue(ChatTurns.group(List.of()).isEmpty());
		List<ChatTurns.Turn> t = ChatTurns.group(List.of(e("a", Kind.ASSISTANT, "", "hi", "completed", null),
				e("q", Kind.REQUEST, "Approval: command", "rm -rf build?", "pending", null)));
		assertEquals(1, t.size());
		assertEquals(2, t.getFirst().blocks().size());
	}
}
