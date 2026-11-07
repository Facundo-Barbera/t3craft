package dev.t3craft.client.chat;

import com.google.gson.JsonObject;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Live, read-only feed of one T3 thread for one view. It opens no socket: it holds a shared,
 * refcounted {@code orchestration.subscribeThread} subscription on the environment's
 * {@link T3Connection} (through {@link ChatSource.Env}) and folds the stream into its
 * {@link ChatTranscript}. Older pages come from {@code GET …/threads/:id/history}, full items from
 * {@code orchestration.getTurnItem}, both via the same connection. {@link #close()} releases the
 * subscription (the last view of a thread interrupts it on the server).
 *
 * <p>Never sends {@code dispatchCommand} or anything that needs {@code orchestration:operate}.
 */
public final class ThreadFeed implements AutoCloseable {
	public enum Phase { CONNECTING, LIVE, OFFLINE, FAILED }

	/** Connection line for the view. {@code detail} is secret-free. */
	public record State(Phase phase, String detail, boolean loadingOlder, @Nullable String olderError) {
	}

	/**
	 * How long the view waits for the thread's snapshot before it says so (it keeps listening: the
	 * snapshot still turns it live if it arrives later). Never an endless "connecting".
	 */
	public static final long ANSWER_TIMEOUT_MS = 20_000;

	private final ChatSource.Env env;
	private final ChatTranscript transcript;
	private final Runnable onChange;
	private StreamRegistry.@Nullable Subscription subscription;
	private volatile boolean closed;
	private volatile boolean subscribed;
	private volatile @Nullable String ended;
	private volatile boolean loadingOlder;
	private volatile @Nullable String olderError;
	private final LongSupplier clock;
	private volatile long waitingSince;

	public ThreadFeed(ChatSource.Env env, String threadId, Runnable onChange) {
		this(env, threadId, onChange, System::currentTimeMillis);
	}

	ThreadFeed(ChatSource.Env env, String threadId, Runnable onChange, LongSupplier clock) {
		this.clock = clock;
		this.env = env;
		this.transcript = new ChatTranscript(threadId);
		this.onChange = onChange;
	}

	public ChatTranscript transcript() {
		return transcript;
	}

	public ChatSource.Env env() {
		return env;
	}

	/** True while it holds a subscription (between {@link #start()} and {@link #close()}). */
	public synchronized boolean subscribed() {
		return subscription != null;
	}

	public synchronized void start() {
		if (closed || subscription != null) {
			return;
		}
		waitingSince = clock.getAsLong();
		subscription = env.subscribeThread(transcript.threadId(), new StreamRegistry.Listener() {
			@Override
			public void onSubscribed() {
				subscribed = true;
				ended = null;
				waitingSince = clock.getAsLong(); // (re)subscribed: a fresh snapshot is on its way
				onChange.run();
			}

			@Override
			public void onItems(List<JsonObject> items) {
				if (closed) {
					return;
				}
				boolean changed = false;
				for (JsonObject item : items) {
					changed |= transcript.applyStreamItem(item);
				}
				if (changed) {
					onChange.run();
				}
			}

			@Override
			public void onEnded(@Nullable String failure) {
				subscribed = false;
				if (failure != null) {
					ended = failure.contains("NotFound") ? "thread not found on " + env.label() : T3Connection.sanitize(failure);
				}
				onChange.run();
			}
		});
	}

	/** Drops this view's subscription and takes a new one (a fresh snapshot, or the shared replay). */
	public synchronized void reconnect() {
		if (closed) {
			return;
		}
		if (subscription != null) {
			subscription.close();
			subscription = null;
		}
		ended = null;
		start();
	}

	@Override
	public synchronized void close() {
		closed = true;
		if (subscription != null) {
			subscription.close();
			subscription = null;
		}
	}

	public boolean isClosed() {
		return closed;
	}

	/** What the view shows about the connection, derived from the shared connection's status. */
	public State state() {
		return new State(phaseNow(), detailNow(), loadingOlder, olderError);
	}

	/**
	 * The thread's own stream decides first: once its snapshot is in, the view is live even while
	 * the environment's shell is still syncing (status CONNECTING). Before that, the connection
	 * status; and past {@link #ANSWER_TIMEOUT_MS} without a snapshot, a clear failure instead of an
	 * endless "connecting".
	 */
	private Phase phaseNow() {
		if (ended != null) {
			return Phase.FAILED;
		}
		if (subscribed && transcript.hasSnapshot()) {
			return Phase.LIVE;
		}
		return switch (env.status()) {
			case OK, CONNECTING -> timedOut() ? Phase.FAILED : Phase.CONNECTING;
			case OFFLINE -> Phase.OFFLINE;
			default -> Phase.FAILED;
		};
	}

	private boolean timedOut() {
		return clock.getAsLong() - waitingSince >= ANSWER_TIMEOUT_MS;
	}

	private String detailNow() {
		String e = ended;
		if (e != null) {
			return e;
		}
		if (subscribed && transcript.hasSnapshot()) {
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

	/** Loads the page before the oldest row, if any and not already loading. */
	public void loadOlder() {
		String cursor = transcript.historyCursor();
		synchronized (this) {
			if (closed || cursor == null || loadingOlder) {
				return;
			}
			loadingOlder = true;
			olderError = null;
		}
		onChange.run();
		env.threadHistory(transcript.threadId(), cursor).whenComplete((page, err) -> {
			if (closed) {
				return;
			}
			if (err != null) {
				olderError = "could not load earlier messages: " + T3Connection.sanitize(message(err));
			} else {
				try {
					transcript.applyHistoryPage(page, cursor);
				} catch (RuntimeException e) {
					olderError = "history: unexpected response";
				}
			}
			loadingOlder = false;
			onChange.run();
		});
	}

	/** The complete turn item (e.g. command output the stream left out), or null when T3 has none. */
	public CompletableFuture<@Nullable JsonObject> fullItem(String itemId) {
		return env.getTurnItem(transcript.threadId(), itemId);
	}

	private static String message(Throwable t) {
		Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
	}
}
