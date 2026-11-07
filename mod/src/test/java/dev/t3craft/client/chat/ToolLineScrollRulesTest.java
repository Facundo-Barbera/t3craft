package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.ChatEntry.Kind;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ToolLineScrollRulesTest {
	static ChatEntry tool(Kind k, String title, String body) {
		return new ChatEntry("t:x", "x", null, k, title, body, null, false, "completed", null, 0, false);
	}

	@Test
	void toolLines() {
		assertEquals(new ToolLine("bash", "$", "ls -la"), ToolLine.of(tool(Kind.TOOL, "$ ls -la  [exit 0]", "")));
		assertEquals("false  [exit 1]", ToolLine.of(tool(Kind.TOOL, "$ false  [exit 1]", "")).arg());
		assertEquals("test", ToolLine.of(tool(Kind.TOOL, "$ ./gradlew test", "")).icon());
		assertEquals("test", ToolLine.of(tool(Kind.TOOL, "$ npm run test", "")).icon());
		assertEquals("git", ToolLine.of(tool(Kind.TOOL, "$ git status", "")).icon());
		assertEquals(new ToolLine("edit", "Edit", "Foo.java (+1 −0)"), ToolLine.of(tool(Kind.TOOL, "Edit Foo.java (+1 −0)", "")));
		assertEquals(new ToolLine("read", "Search", "TODO"), ToolLine.of(tool(Kind.TOOL, "Search TODO", "")));
		assertEquals(new ToolLine("read", "Web search", "fabric api"), ToolLine.of(tool(Kind.TOOL, "Web search fabric api", "")));
		assertEquals(new ToolLine("message", "Subagent", "Researcher"), ToolLine.of(tool(Kind.SUBAGENT, "Subagent: Researcher", "")));
		assertEquals(new ToolLine("memory", "thinking", "first"), ToolLine.of(tool(Kind.REASONING, "", "first\nsecond")));
		assertEquals("memory", ToolLine.of(tool(Kind.TOOL, "TodoWrite", "")).icon());
		assertEquals("merge", ToolLine.iconFor("request_merge"));
		assertEquals("new task", ToolLine.shortName("create_task"));
		assertEquals("my tool", ToolLine.shortName("my_tool"));
	}

	@Test
	void scrollFollowsUntilScrolledUp() {
		FeedScroll s = new FeedScroll();
		s.update(100, 40);
		assertTrue(s.following());
		assertEquals(60, s.offset());
		s.update(150, 40);
		assertEquals(110, s.offset(), "follows new content");
		s.scrollBy(-30);
		assertFalse(s.following());
		s.update(200, 40);
		assertEquals(80, s.offset(), "holds position");
		s.prepended(50);
		assertEquals(130, s.offset());
		s.scrollBy(1000);
		assertTrue(s.following());
		s.toTop();
		assertTrue(s.atTop());
		assertFalse(s.following());
		s.toBottom();
		assertEquals(1.0, s.fraction());
		FeedScroll small = new FeedScroll().update(10, 40);
		assertFalse(small.scrollable());
		assertEquals(0, small.offset());
	}

	@Test
	void minuteSeparators() {
		ZoneId z = ZoneId.of("UTC");
		long now = ZonedDateTime.of(2026, 11, 7, 18, 0, 0, 0, z).toInstant().toEpochMilli();
		long t1 = ZonedDateTime.of(2026, 11, 7, 14, 5, 10, 0, z).toInstant().toEpochMilli();
		long t2 = t1 + 20_000; // same minute
		long t3 = t1 + 60_000; // next minute
		long old = ZonedDateTime.of(2026, 11, 3, 9, 30, 0, 0, z).toInstant().toEpochMilli();
		assertEquals("14:05", FeedRules.separator(null, t1, z, now));
		assertNull(FeedRules.separator(t1, t2, z, now));
		assertEquals("14:06", FeedRules.separator(t2, t3, z, now));
		assertEquals("Tue 3 Nov · 09:30", FeedRules.separator(null, old, z, now));
		assertEquals("14:05", FeedRules.separator(old, t1, z, now));
		assertNull(FeedRules.separator(t1, null, z, now));
	}
}
