package dev.t3craft.client.search;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.ThreadSearch;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchLinesTest {
	static final long NOW = 1_800_000_000_000L;

	static ThreadSearch.Hit hit(ThreadState state, long activity, String snippet) {
		return new ThreadSearch.Hit("vps", "My VPS", "thr-1", "Fix the login redirect", "web", state, snippet, 1, activity, 2);
	}

	@Test
	void lineShowsEnvironmentTitleStateAndAge() {
		var l = SearchLines.line(hit(ThreadState.NEEDS_ATTENTION, NOW - 3 * 3_600_000L, null), NOW);
		assertEquals("[My VPS] Fix the login redirect — needs you · 3h ago", l.text());
		assertEquals(ThreadState.NEEDS_ATTENTION.color, l.color());
		assertEquals("!", l.glyph());
		assertNull(l.snippet());
		assertEquals("/t3craft search-open vps thr-1", l.command());
	}

	@Test
	void unknownStateAndActivity() {
		var l = SearchLines.line(hit(null, 0, "  multi\n  line   snippet "), NOW);
		assertEquals("[My VPS] Fix the login redirect — older", l.text());
		assertEquals(SearchLines.UNKNOWN_COLOR, l.color());
		assertEquals("multi line snippet", l.snippet());
	}

	@Test
	void ages() {
		assertEquals("now", SearchLines.age(NOW - 1_000, NOW));
		assertEquals("45s ago", SearchLines.age(NOW - 45_000, NOW));
		assertEquals("12m ago", SearchLines.age(NOW - 12 * 60_000, NOW));
		assertEquals("3d ago", SearchLines.age(NOW - 3 * 86_400_000L, NOW));
		assertEquals("", SearchLines.age(0, NOW));
	}

	@Test
	void longTitlesAndSnippetsAreClipped() {
		String longTitle = "x".repeat(200);
		var h = new ThreadSearch.Hit("e", "E", "t", longTitle, "", ThreadState.IDLE, "y".repeat(500), 1, NOW, 1);
		var l = SearchLines.line(h, NOW);
		assertEquals(SearchLines.TITLE_MAX, l.title().length());
		assertTrue(l.title().endsWith("…"));
		assertEquals(SearchLines.SNIPPET_MAX, l.snippet().length());
	}

	@Test
	void openCommandQuotesIdsThatAreNotPlainWords() {
		assertEquals("/t3craft search-open vps abc-123_x.y", SearchLines.openCommand("vps", "abc-123_x.y"));
		assertEquals("/t3craft search-open vps \"a b\\\"c\\\\\"", SearchLines.openCommand("vps", "a b\"c\\"));
	}

	@Test
	void summaries() {
		assertEquals("No T3 environment paired: /t3craft pair (read-only).",
				SearchLines.summary(new ThreadSearch.Outcome("login", List.of(), 0, 0, List.of())));
		assertTrue(SearchLines.summary(new ThreadSearch.Outcome("login", List.of(), 0, 2, List.of()))
				.startsWith("No T3 environment connected (2 paired)"));
		assertEquals("No threads match \"login\" in 2 environments.",
				SearchLines.summary(new ThreadSearch.Outcome("login", List.of(), 2, 2, List.of())));
		assertEquals("1 thread matches \"login\" in 1 environment:",
				SearchLines.summary(new ThreadSearch.Outcome("login", List.of(hit(ThreadState.DONE, NOW, null)), 1, 1, List.of())));
		var withError = new ThreadSearch.Outcome("login", List.of(), 1, 2, List.of(new ThreadSearch.EnvError("mac", "Mac", "timed out after 8 s")));
		assertEquals(List.of("Mac: timed out after 8 s"), SearchLines.errors(withError));
	}

	@Test
	void localSearchFiltersListedThreadsByTitle() {
		NpcInfo a = new NpcInfo("vps:t1", "Login page", "", "VPS", "", 0, null, 0, false, "working", "vps:p", "web", "s", null);
		NpcInfo b = new NpcInfo("vps:t2", "Fix the LOGIN bug", "", "VPS", "", 0, null, 0, false, "error", "vps:p", "web", "s", null);
		NpcInfo c = new NpcInfo("vps:t3", "Other", "", "VPS", "", 0, null, 0, false, "idle", "vps:p", "web", "s", null);
		var o = LocalSearch.filter(List.of(b, c, a), " login ", 10);
		assertEquals(List.of("t1", "t2"), o.hits().stream().map(ThreadSearch.Hit::threadId).toList(), "prefix match first");
		assertEquals("vps", o.hits().getFirst().env());
		assertEquals(ThreadState.WORKING, o.hits().getFirst().state());
		assertEquals(ThreadState.ERROR, o.hits().get(1).state());
	}
}
