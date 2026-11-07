package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreadSearchTest {
	static ShellState.View view(ThreadShell... threads) {
		return new ShellState.View(List.of(threads), Map.of("project-1", "Synthetic project"), true);
	}

	static ThreadSearch.Hit hit(String env, String label, String id, double relevance, long activity) {
		return new ThreadSearch.Hit(env, label, id, "T " + id, "", ThreadState.IDLE, null, relevance, activity, 1);
	}

	// ---- decode -------------------------------------------------------------------------------

	@Test
	void decodesFullMatchesAndThreadRows() {
		var d = ThreadSearch.decode(JsonParser.parseString("""
				{"matches":[{"threadId":"a","projectId":"project-1","source":"assistant","snippet":"  the fix landed  ",
				  "messageCreatedAt":"%s","quality":1.25,"hits":3,"messageId":"m1"}],
				 "snapshotSequence":7,"threads":[%s]}
				""".formatted(iso(2 * H), threadJson("a", H, "{\"title\":\"Fix the login\"}"))));
		assertEquals(1, d.matches().size());
		var m = d.matches().getFirst();
		assertEquals("a", m.threadId());
		assertEquals("project-1", m.projectId());
		assertEquals("assistant", m.source());
		assertEquals("the fix landed", m.snippet());
		assertEquals(NOW - 2 * H, m.messageCreatedAt());
		assertEquals(1.25, m.quality());
		assertEquals(3, m.hits());
		assertEquals("m1", m.messageId());
		assertEquals("Fix the login", d.threads().getFirst().title());
	}

	@Test
	void missingFieldsGetDefaultsAndBadEntriesAreSkipped() {
		var d = ThreadSearch.decode(JsonParser.parseString("""
				{"matches":[{"threadId":"old"},{"projectId":"p"},{"threadId":""},42,null,
				  {"threadId":"q","quality":"high","hits":-2,"messageCreatedAt":"not a date","snippet":null}],
				 "threads":[{"title":"no id"},7]}
				"""));
		assertEquals(List.of("old", "q"), d.matches().stream().map(ThreadSearch.Match::threadId).toList());
		var old = d.matches().getFirst();
		assertEquals("", old.projectId());
		assertEquals("", old.source());
		assertEquals("", old.snippet());
		assertNull(old.messageCreatedAt());
		assertNull(old.quality(), "older servers omit quality");
		assertNull(old.hits());
		assertNull(old.messageId());
		var q = d.matches().get(1);
		assertNull(q.quality(), "a non-number quality is ignored");
		assertEquals(0, q.hits());
		assertNull(q.messageCreatedAt());
		assertTrue(d.threads().isEmpty());
	}

	@Test
	void nonObjectOrMissingValueIsEmpty() {
		assertEquals(ThreadSearch.Decoded.EMPTY, ThreadSearch.decode(null));
		assertEquals(ThreadSearch.Decoded.EMPTY, ThreadSearch.decode(JsonParser.parseString("[]")));
		assertTrue(ThreadSearch.decode(JsonParser.parseString("{}")).matches().isEmpty());
		assertTrue(ThreadSearch.decode(JsonParser.parseString("{\"matches\":\"x\"}")).matches().isEmpty());
	}

	@Test
	void duplicateThreadKeepsBestQuality() {
		var d = ThreadSearch.decode(JsonParser.parseString("""
				{"matches":[{"threadId":"a","quality":0.3,"snippet":"weak"},{"threadId":"a","quality":1,"snippet":"strong"}]}
				"""));
		assertEquals(1, d.matches().size());
		assertEquals("strong", d.matches().getFirst().snippet());
	}

	// ---- enrich -------------------------------------------------------------------------------

	@Test
	void enrichUsesShellForTitleStateProjectAndActivity() {
		var d = ThreadSearch.decode(JsonParser.parseString("""
				{"matches":[{"threadId":"a","projectId":"project-1","snippet":"deploy failed","quality":1,"messageCreatedAt":"%s"},
				  {"threadId":"gone","projectId":"project-9","snippet":"elsewhere"}]}
				""".formatted(iso(5 * H))));
		var hits = ThreadSearch.enrich("vps", "My VPS", "deploy", d, view(thread("a", H, "{\"status\":\"running\"}")));
		assertEquals(2, hits.size());
		var a = hits.getFirst();
		assertEquals("vps", a.env());
		assertEquals("My VPS", a.envLabel());
		assertEquals("Thread a", a.title());
		assertEquals("Synthetic project", a.project());
		assertEquals(ThreadState.WORKING, a.state());
		assertEquals("deploy failed", a.snippet());
		assertEquals(NOW - H, a.activityMs(), "latest of shell activity and message time");
		var gone = hits.get(1);
		assertNull(gone.state(), "not in the shell: state unknown");
		assertEquals("thread gone", gone.title());
		assertEquals("", gone.project());
		assertEquals(ThreadSearch.UNKNOWN_QUALITY, gone.relevance());
	}

	@Test
	void serverThreadRowsWinOverShellAndDeletedAreDropped() {
		var d = ThreadSearch.decode(JsonParser.parseString("""
				{"matches":[{"threadId":"a","quality":1},{"threadId":"d","quality":1}],
				 "threads":[%s,%s]}
				""".formatted(threadJson("a", H, "{\"title\":\"Renamed\"}"), threadJson("d", H, "{\"deletedAt\":\"" + iso(1) + "\"}"))));
		var hits = ThreadSearch.enrich("vps", "VPS", "zz", d, view(thread("a", 3 * H)));
		assertEquals(List.of("Renamed"), hits.stream().map(ThreadSearch.Hit::title).toList());
	}

	@Test
	void localTitleMatchesAreAddedAndBoostServerMatches() {
		var d = ThreadSearch.decode(JsonParser.parseString("{\"matches\":[{\"threadId\":\"b\",\"quality\":0.3,\"snippet\":\"…login…\"}]}"));
		var v = view(
				thread("a", H, "{\"title\":\"Login page\"}"),
				thread("b", H, "{\"title\":\"Fix LOGIN redirect\"}"),
				thread("c", H, "{\"title\":\"Unrelated\"}"),
				thread("x", H, "{\"title\":\"login deleted\",\"deletedAt\":\"" + iso(1) + "\"}"));
		var hits = ThreadSearch.enrich("vps", "VPS", "  login ", d, v);
		assertEquals(List.of("b", "a"), hits.stream().map(ThreadSearch.Hit::threadId).toList());
		assertEquals(ThreadSearch.TITLE_QUALITY, hits.getFirst().relevance(), "contains → title relevance beats 0.3");
		assertEquals("…login…", hits.getFirst().snippet());
		var a = hits.get(1);
		assertEquals(ThreadSearch.TITLE_QUALITY + ThreadSearch.TITLE_PREFIX_BONUS, a.relevance(), "prefix ranks higher");
		assertNull(a.snippet());
		assertEquals(0, a.hits());
	}

	@Test
	void enrichWithoutViewStillWorks() {
		var d = ThreadSearch.decode(JsonParser.parseString("{\"matches\":[{\"threadId\":\"abcdefghijk\"}]}"));
		var hits = ThreadSearch.enrich("e", "E", "query", d, null);
		assertEquals("thread abcdefgh", hits.getFirst().title());
	}

	// ---- merge --------------------------------------------------------------------------------

	@Test
	void mergeOrdersByRelevanceThenActivityAcrossEnvironments() {
		var vps = List.of(hit("vps", "VPS", "v1", 1.0, 100), hit("vps", "VPS", "v2", 0.3, 900));
		var mac = List.of(hit("mac", "Mac", "m1", 1.0, 500), hit("mac", "Mac", "m2", 1.3, 1));
		var merged = ThreadSearch.merge(List.of(vps, mac), 10);
		assertEquals(List.of("m2", "m1", "v1", "v2"), merged.stream().map(ThreadSearch.Hit::threadId).toList());
	}

	@Test
	void mergeIsStableRegardlessOfInputOrderAndRespectsLimit() {
		List<ThreadSearch.Hit> all = new ArrayList<>(List.of(
				hit("b", "Beta", "x", 1, 10), hit("a", "Alpha", "x", 1, 10), hit("a", "Alpha", "w", 1, 10),
				hit("c", "Gamma", "z", 0.8, 50)));
		var expected = List.of("Alpha/w", "Alpha/x", "Beta/x", "Gamma/z");
		for (int i = 0; i < 5; i++) {
			Collections.shuffle(all, new java.util.Random(i));
			var merged = ThreadSearch.merge(List.of(List.copyOf(all.subList(0, 2)), List.copyOf(all.subList(2, 4))), 10);
			assertEquals(expected, merged.stream().map(h -> h.envLabel() + "/" + h.threadId()).toList());
		}
		assertEquals(2, ThreadSearch.merge(List.of(all), 2).size());
		assertTrue(ThreadSearch.merge(List.of(all), 0).isEmpty());
	}

	@Test
	void searchExceptionIsSanitized() {
		var e = new ThreadSearch.SearchException("T3: boom Bearer secret-token at /ws?wsTicket=abc");
		assertFalse(e.getMessage().contains("secret-token"));
		assertFalse(e.getMessage().contains("abc"));
	}
}
