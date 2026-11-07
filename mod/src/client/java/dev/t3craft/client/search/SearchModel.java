package dev.t3craft.client.search;

import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.ThreadSearch;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * State of one search field, with no Minecraft classes so it can be unit-tested and moved with the
 * {@link SearchBox} wherever the terminal UI ends up.
 * <ul>
 *   <li><b>Debounce:</b> {@link #setText} only arms a deadline; {@link #tick} launches the search
 *   once the text has been still for {@code debounceMs}. {@link #submit} (Enter) launches now.</li>
 *   <li><b>Cancellation:</b> every new text, clear or close cancels the request in flight, and a
 *   result that arrives for an older text is dropped (generation check), so a slow answer never
 *   overwrites a newer one.</li>
 * </ul>
 * The clock is passed in (epoch or monotonic ms, the caller's choice). Thread-safe: results may
 * complete on a connection thread while the screen reads on the client thread.
 */
public final class SearchModel {
	public enum Phase {
		/** Empty field: the caller shows its normal list. */
		IDLE,
		/** One character: T3 needs at least {@link RpcFrames#SEARCH_MIN_LENGTH}. */
		TOO_SHORT,
		/** Typing: the search starts when the text has been still for the debounce. */
		PENDING,
		SEARCHING,
		DONE,
		FAILED
	}

	public static final long DEFAULT_DEBOUNCE_MS = 350;

	private final Function<String, CompletableFuture<ThreadSearch.Outcome>> searcher;
	private final long debounceMs;
	private String text = "";
	private Phase phase = Phase.IDLE;
	private long dueAtMs;
	private int generation;
	private @Nullable CompletableFuture<ThreadSearch.Outcome> inFlight;
	private ThreadSearch.@Nullable Outcome outcome;
	private @Nullable String error;
	private int launches;

	public SearchModel(Function<String, CompletableFuture<ThreadSearch.Outcome>> searcher, long debounceMs) {
		this.searcher = searcher;
		this.debounceMs = Math.max(0, debounceMs);
	}

	/** The field changed. Same query (after trimming) as before: nothing happens. */
	public synchronized void setText(@Nullable String newText, long nowMs) {
		String t = newText == null ? "" : newText;
		if (RpcFrames.searchQuery(t).equals(RpcFrames.searchQuery(text)) && phase != Phase.IDLE) {
			text = t;
			return;
		}
		text = t;
		supersede();
		outcome = null;
		error = null;
		String q = query();
		if (q.isEmpty()) {
			phase = Phase.IDLE;
		} else if (q.length() < RpcFrames.SEARCH_MIN_LENGTH) {
			phase = Phase.TOO_SHORT;
		} else {
			phase = Phase.PENDING;
			dueAtMs = nowMs + debounceMs;
		}
	}

	/** Call every tick: launches the pending search once the debounce has elapsed. */
	public synchronized void tick(long nowMs) {
		if (phase == Phase.PENDING && nowMs >= dueAtMs) {
			launch();
		}
	}

	/** Enter: search now (skips the debounce). Re-runs a finished search for the same text. */
	public synchronized void submit() {
		if (query().length() < RpcFrames.SEARCH_MIN_LENGTH) {
			return;
		}
		supersede();
		launch();
	}

	public synchronized void clear() {
		setText("", 0);
	}

	/** The screen closed: drop the request in flight. */
	public synchronized void close() {
		supersede();
		if (phase == Phase.PENDING || phase == Phase.SEARCHING) {
			phase = Phase.IDLE;
		}
	}

	private void launch() {
		int g = generation;
		String q = query();
		phase = Phase.SEARCHING;
		launches++;
		CompletableFuture<ThreadSearch.Outcome> f;
		try {
			f = searcher.apply(q);
		} catch (RuntimeException e) {
			f = CompletableFuture.failedFuture(e);
		}
		inFlight = f;
		f.whenComplete((r, e) -> deliver(g, r, e));
	}

	private synchronized void deliver(int g, ThreadSearch.@Nullable Outcome r, @Nullable Throwable e) {
		if (g != generation) {
			return; // a newer text (or close) superseded this search
		}
		inFlight = null;
		if (e != null || r == null) {
			phase = Phase.FAILED;
			error = describe(e);
			outcome = null;
		} else {
			phase = Phase.DONE;
			outcome = r;
			error = null;
		}
	}

	/** Bump the generation first, so the cancelled future's callback is already stale. */
	private void supersede() {
		generation++;
		cancelInFlight();
	}

	private void cancelInFlight() {
		CompletableFuture<ThreadSearch.Outcome> f = inFlight;
		inFlight = null;
		if (f != null && !f.isDone()) {
			f.cancel(true);
		}
	}

	static String describe(@Nullable Throwable e) {
		Throwable c = e;
		while (c != null && c.getCause() != null && (c instanceof java.util.concurrent.CompletionException || c instanceof java.util.concurrent.ExecutionException)) {
			c = c.getCause();
		}
		if (c == null) {
			return "search failed";
		}
		String m = c.getMessage();
		return dev.t3craft.t3.T3Connection.sanitize(m == null || m.isBlank() ? c.getClass().getSimpleName() : m);
	}

	public synchronized String text() {
		return text;
	}

	/** The query as it is sent: trimmed and cut to T3's limit. */
	public synchronized String query() {
		return RpcFrames.searchQuery(text);
	}

	public synchronized Phase phase() {
		return phase;
	}

	/** True while the field has text: the caller shows search results instead of its list. */
	public synchronized boolean active() {
		return phase != Phase.IDLE;
	}

	public synchronized ThreadSearch.@Nullable Outcome outcome() {
		return outcome;
	}

	public synchronized @Nullable String error() {
		return error;
	}

	/** How many searches were started (for tests and diagnostics). */
	public synchronized int launches() {
		return launches;
	}
}
