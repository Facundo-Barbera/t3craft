package dev.t3craft.client.search;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.search.SearchModel.Phase;
import dev.t3craft.t3.ThreadSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SearchModelTest {
	/** Records every query and hands back a future the test completes by hand. */
	static final class FakeSearcher {
		final List<String> queries = new ArrayList<>();
		final List<CompletableFuture<ThreadSearch.Outcome>> futures = new ArrayList<>();

		CompletableFuture<ThreadSearch.Outcome> search(String q) {
			queries.add(q);
			CompletableFuture<ThreadSearch.Outcome> f = new CompletableFuture<>();
			futures.add(f);
			return f;
		}
	}

	static ThreadSearch.Outcome outcome(String q, String... ids) {
		List<ThreadSearch.Hit> hits = new ArrayList<>();
		for (String id : ids) {
			hits.add(new ThreadSearch.Hit("e", "E", id, id, "", null, null, 1, 0, 1));
		}
		return new ThreadSearch.Outcome(q, hits, 1, 1, List.of());
	}

	final FakeSearcher fake = new FakeSearcher();
	final SearchModel m = new SearchModel(fake::search, 300);

	@Test
	void debounceWaitsUntilTheTextIsStill() {
		m.setText("lo", 0);
		assertEquals(Phase.PENDING, m.phase());
		m.tick(200);
		m.setText("log", 200); // still typing: deadline moves
		m.tick(400);
		m.setText("login", 400);
		m.tick(699);
		assertTrue(fake.queries.isEmpty(), "nothing sent while typing");
		m.tick(700);
		assertEquals(List.of("login"), fake.queries, "only the final text is searched");
		assertEquals(Phase.SEARCHING, m.phase());
		m.tick(2_000);
		assertEquals(1, m.launches(), "one launch per settled text");
	}

	@Test
	void resultForTheCurrentTextIsShown() {
		m.setText("login", 0);
		m.tick(300);
		fake.futures.getFirst().complete(outcome("login", "a", "b"));
		assertEquals(Phase.DONE, m.phase());
		assertEquals(2, m.outcome().hits().size());
	}

	@Test
	void newTextCancelsTheSearchInFlightAndDropsItsLateResult() {
		m.setText("old", 0);
		m.tick(300);
		var old = fake.futures.getFirst();
		m.setText("new", 310);
		assertTrue(old.isCancelled(), "previous request cancelled");
		assertEquals(Phase.PENDING, m.phase());
		m.tick(610);
		fake.futures.get(1).complete(outcome("new", "n"));
		assertEquals("n", m.outcome().hits().getFirst().threadId());
	}

	@Test
	void staleCompletionNeverOverwritesANewerResult() {
		// a searcher whose futures ignore cancel (like a result already on its way)
		List<CompletableFuture<ThreadSearch.Outcome>> raw = new ArrayList<>();
		SearchModel sm = new SearchModel(q -> {
			CompletableFuture<ThreadSearch.Outcome> inner = new CompletableFuture<>();
			raw.add(inner);
			return inner.thenApply(x -> x); // cancelling the dependent leaves `inner` completable
		}, 0);
		sm.setText("first", 0);
		sm.tick(0);
		sm.setText("second", 1);
		sm.tick(1);
		raw.get(1).complete(outcome("second", "s"));
		raw.get(0).complete(outcome("first", "f"));
		assertEquals(Phase.DONE, sm.phase());
		assertEquals("s", sm.outcome().hits().getFirst().threadId());
	}

	@Test
	void sameQueryAfterTrimmingDoesNotSearchAgain() {
		m.setText("login", 0);
		m.tick(300);
		fake.futures.getFirst().complete(outcome("login"));
		m.setText("login  ", 400);
		m.tick(10_000);
		assertEquals(1, fake.queries.size());
		assertEquals(Phase.DONE, m.phase());
		assertEquals("login  ", m.text());
	}

	@Test
	void shortAndEmptyTextNeverSearch() {
		m.setText("a", 0);
		assertEquals(Phase.TOO_SHORT, m.phase());
		assertTrue(m.active());
		m.tick(10_000);
		m.submit();
		assertTrue(fake.queries.isEmpty());
		m.setText("   ", 0);
		assertEquals(Phase.IDLE, m.phase());
		assertFalse(m.active());
	}

	@Test
	void submitSkipsTheDebounceAndReplacesTheSearchInFlight() {
		m.setText("deploy", 0);
		m.submit();
		assertEquals(List.of("deploy"), fake.queries);
		m.submit();
		assertTrue(fake.futures.getFirst().isCancelled());
		assertEquals(2, fake.queries.size());
	}

	@Test
	void failureIsReportedSanitized() {
		m.setText("deploy", 0);
		m.tick(300);
		fake.futures.getFirst().completeExceptionally(new java.util.concurrent.CompletionException(
				new ThreadSearch.SearchException("T3: Bearer secret broke")));
		assertEquals(Phase.FAILED, m.phase());
		assertFalse(m.error().contains("secret"));
		assertNull(m.outcome());
	}

	@Test
	void clearAndCloseCancel() {
		m.setText("deploy", 0);
		m.tick(300);
		m.clear();
		assertTrue(fake.futures.getFirst().isCancelled());
		assertEquals(Phase.IDLE, m.phase());

		m.setText("deploy", 0);
		m.tick(300);
		m.close();
		assertTrue(fake.futures.get(1).isCancelled());
		assertEquals(Phase.IDLE, m.phase());
		fake.futures.get(1).complete(outcome("deploy", "late"));
		assertNull(m.outcome(), "nothing arrives after close");
	}

	@Test
	void searcherThatThrowsIsAFailureNotACrash() {
		SearchModel sm = new SearchModel(q -> {
			throw new IllegalStateException("boom");
		}, 0);
		sm.setText("abc", 0);
		sm.tick(0);
		assertEquals(Phase.FAILED, sm.phase());
		assertEquals("boom", sm.error());
	}
}
