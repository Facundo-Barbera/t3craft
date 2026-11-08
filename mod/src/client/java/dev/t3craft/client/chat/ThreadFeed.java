package dev.t3craft.client.chat;

import com.google.gson.JsonObject;
import dev.t3craft.t3.T3Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Live, read-only feed of one T3 thread for one view. It opens no socket and owns no transcript: it
 * is a view over the thread's {@link ThreadCache} entry, which holds the shared, refcounted
 * {@code orchestration.subscribeThread} subscription on the environment's {@link T3Connection}
 * (through {@link ChatSource.Env}) and the {@link ChatTranscript} it folds into. Reopening a thread
 * therefore paints the cached rows at once ({@link State#refreshing()} until the new snapshot is in);
 * {@link #close()} releases the view and the subscription lingers a while (feat/t3-thread-cache).
 * Older pages come from {@code GET …/threads/:id/history}, full items from
 * {@code orchestration.getTurnItem}, both via the same connection and cached per entry.
 *
 * <p>Never sends {@code dispatchCommand} or anything that needs {@code orchestration:operate}.
 */
public final class ThreadFeed implements AutoCloseable {
	public enum Phase { CONNECTING, LIVE, OFFLINE, FAILED }

	/**
	 * Connection line for the view. {@code detail} is secret-free. {@code refreshing}: rows are shown
	 * from the cache while the fresh snapshot is on its way; {@code fromDisk}: those rows were kept on
	 * disk from an earlier game session.
	 */
	public record State(Phase phase, String detail, boolean loadingOlder, @Nullable String olderError, boolean refreshing,
			boolean fromDisk) {
	}

	/**
	 * How long the view waits for the thread's snapshot before it says so (it keeps listening: the
	 * snapshot still turns it live if it arrives later). Never an endless "connecting".
	 */
	public static final long ANSWER_TIMEOUT_MS = 20_000;

	private final ThreadCache cache;
	private final ChatSource.Env env;
	private final ThreadCache.Entry entry;
	private final Runnable onChange;
	private final LongSupplier clock;
	private boolean started;
	private volatile boolean closed;

	public ThreadFeed(ChatSource.Env env, String threadId, Runnable onChange) {
		this(env, threadId, onChange, System::currentTimeMillis);
	}

	ThreadFeed(ChatSource.Env env, String threadId, Runnable onChange, LongSupplier clock) {
		this(ThreadCache.get(), env, threadId, onChange, clock);
	}

	ThreadFeed(ThreadCache cache, ChatSource.Env env, String threadId, Runnable onChange, LongSupplier clock) {
		this.cache = cache;
		this.clock = clock;
		this.env = env;
		this.entry = cache.entry(env.id(), threadId);
		this.onChange = onChange;
	}

	public ChatTranscript transcript() {
		return entry.transcript;
	}

	public ChatSource.Env env() {
		return env;
	}

	/** True while it holds the thread (between {@link #start()} and {@link #close()}). */
	public synchronized boolean subscribed() {
		return started && !closed;
	}

	public synchronized void start() {
		if (closed || started) {
			return;
		}
		started = true;
		entry.listeners.add(onChange);
		cache.acquire(entry, env);
	}

	/** Drops the thread's subscription and takes a new one (a fresh snapshot). */
	public synchronized void reconnect() {
		if (closed) {
			return;
		}
		if (!started) {
			start();
			return;
		}
		cache.reconnect(entry);
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		entry.listeners.remove(onChange);
		if (started) {
			cache.release(entry);
		}
	}

	public boolean isClosed() {
		return closed;
	}

	/** What the view shows about the connection, derived from the shared connection's status. */
	public State state() {
		boolean refreshing = entry.ended == null && !entry.fresh() && !entry.transcript.view().entries().isEmpty();
		return new State(phaseNow(), detailNow(), entry.loadingOlder, entry.olderError, refreshing, refreshing && entry.transcript.seeded());
	}

	/**
	 * The thread's own stream decides first: once its snapshot is in, the view is live even while
	 * the environment's shell is still syncing (status CONNECTING). Before that, the connection
	 * status; and past {@link #ANSWER_TIMEOUT_MS} without a snapshot, a clear failure instead of an
	 * endless "connecting".
	 */
	private Phase phaseNow() {
		if (entry.ended != null) {
			return Phase.FAILED;
		}
		if (entry.fresh()) {
			return Phase.LIVE;
		}
		return switch (env.status()) {
			case OK, CONNECTING -> timedOut() ? Phase.FAILED : Phase.CONNECTING;
			case OFFLINE -> Phase.OFFLINE;
			default -> Phase.FAILED;
		};
	}

	private boolean timedOut() {
		return clock.getAsLong() - entry.waitingSince >= ANSWER_TIMEOUT_MS;
	}

	private String detailNow() {
		String e = entry.ended;
		if (e != null) {
			return e;
		}
		if (entry.fresh()) {
			return "live";
		}
		long s = ANSWER_TIMEOUT_MS / 1000;
		return switch (env.status()) {
			case OK -> timedOut() ? env.label() + " did not send the thread within " + s + " s (still waiting; reopen it to retry)"
					: "loading the thread";
			case CONNECTING -> timedOut() ? "could not connect to " + env.label() + " within " + s + " s"
					+ (env.statusDetail().isBlank() ? "" : " (" + T3Connection.sanitize(env.statusDetail()) + ")") + "; still trying"
					: "connecting to " + env.label();
			default -> T3Connection.sanitize(env.statusDetail());
		};
	}

	/** Loads the page before the oldest row, if any and not already loading (cached pages apply at once). */
	public void loadOlder() {
		ChatTranscript transcript = entry.transcript;
		String cursor = transcript.historyCursor();
		synchronized (entry) {
			if (closed || cursor == null || entry.loadingOlder) {
				return;
			}
			JsonObject cached = entry.page(cursor);
			if (cached != null) {
				transcript.applyHistoryPage(cached, cursor);
				entry.olderError = null;
				entry.changed();
				return;
			}
			entry.loadingOlder = true;
			entry.olderError = null;
		}
		entry.changed();
		env.threadHistory(entry.threadId, cursor).whenComplete((page, err) -> {
			if (err != null) {
				entry.olderError = "could not load earlier messages: " + T3Connection.sanitize(message(err));
			} else {
				try {
					if (page != null) {
						entry.putPage(cursor, page);
					}
					transcript.applyHistoryPage(page, cursor);
				} catch (RuntimeException e) {
					entry.olderError = "history: unexpected response";
				}
			}
			entry.loadingOlder = false;
			entry.changed();
		});
	}

	/**
	 * The complete turn item (e.g. command output the stream left out), or null when T3 has none.
	 * Finished items are cached (they no longer change): a second ask completes at once.
	 */
	public CompletableFuture<@Nullable JsonObject> fullItem(String itemId) {
		JsonObject cached = entry.item(itemId);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}
		return env.getTurnItem(entry.threadId, itemId).thenApply(item -> {
			if (ThreadCache.finished(item)) {
				entry.putItem(itemId, item);
			}
			return item;
		});
	}

	private static String message(Throwable t) {
		Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
	}
}
