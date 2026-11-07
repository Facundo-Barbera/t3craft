package dev.t3craft.client.queue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Live queues of the threads someone is looking at. Each {@link #watch} is refcounted per
 * {@code env:thread} and keeps one {@link QueueSource} stream open; the last close drops it.
 * {@link #setBackground} keeps a small set of extra watches (working threads, for the NPC badge).
 * Read-only. Thread-safe: watches come from the client thread, items from connection threads;
 * {@code onChange} runs outside the lock on whichever thread caused the change.
 */
public final class QueueStore {
	public enum Status {
		/** Stream open, no snapshot yet. */
		LOADING,
		/** Snapshot received: {@link #queued} is the real queue. */
		OK,
		/** Stream paused (socket went away); shows the last known queue. */
		PAUSED,
		/** No stream for this environment or the server ended it. */
		UNAVAILABLE
	}

	/** Key of a thread: {@code env:thread}, same as the NPC id. */
	public record Key(String envId, String threadId) {
		public static Key of(String envId, String threadId) {
			return new Key(envId, threadId);
		}

		/** Parses {@code env:thread}; a bare thread id has an empty env. */
		public static Key parse(String s) {
			int i = s.indexOf(':');
			return i < 0 ? new Key("", s) : new Key(s.substring(0, i), s.substring(i + 1));
		}

		@Override
		public String toString() {
			return envId.isEmpty() ? threadId : envId + ":" + threadId;
		}
	}

	/** What a view sees for one thread. {@code runs} is empty unless {@code status} is OK or PAUSED. */
	public record Snapshot(Key key, Status status, @Nullable String reason, List<QueuedRun> runs) {
		public boolean known() {
			return status == Status.OK || status == Status.PAUSED;
		}
	}

	/** One holder of a watch; {@link #close()} is idempotent. */
	public interface Watch extends AutoCloseable {
		@Override
		void close();
	}

	private final Supplier<QueueSource> source;
	private final Runnable onChange;
	private final Map<Key, Entry> entries = new LinkedHashMap<>();
	private final Map<Key, Watch> background = new LinkedHashMap<>();

	private final class Entry {
		final Key key;
		final ThreadQueue queue;
		int refs;
		Status status = Status.LOADING;
		@Nullable String reason;
		QueueSource.@Nullable Handle handle;
		final List<CompletableFuture<Snapshot>> waiting = new ArrayList<>();

		Entry(Key key) {
			this.key = key;
			this.queue = new ThreadQueue(key.envId(), key.threadId());
		}

		Snapshot snapshot() {
			return new Snapshot(key, status, reason, status == Status.OK || status == Status.PAUSED ? queue.queued() : List.of());
		}
	}

	public QueueStore(Supplier<QueueSource> source, Runnable onChange) {
		this.source = source;
		this.onChange = onChange;
	}

	public Watch watch(String envId, String threadId) {
		Key key = Key.of(envId, threadId);
		Entry e;
		boolean open;
		synchronized (this) {
			e = entries.computeIfAbsent(key, Entry::new);
			open = e.refs++ == 0;
		}
		if (open) {
			openStream(e);
		}
		return new Watch() {
			private boolean closed;

			@Override
			public void close() {
				synchronized (QueueStore.this) {
					if (closed) {
						return;
					}
					closed = true;
				}
				release(e);
			}
		};
	}

	private void openStream(Entry e) {
		QueueSource src = source.get();
		QueueSource.Handle h;
		try {
			h = src.open(e.key.envId(), e.key.threadId(), new QueueSource.Listener() {
				@Override
				public void onSubscribed() {
					update(e, () -> {
						e.queue.reset();
						e.status = Status.LOADING;
						e.reason = null;
					});
				}

				@Override
				public void onItems(List<JsonObject> items) {
					update(e, () -> {
						e.queue.apply(items);
						if (e.queue.loaded()) {
							e.status = Status.OK;
							e.reason = null;
						}
					});
				}

				@Override
				public void onEnded(@Nullable String failure) {
					update(e, () -> {
						if (failure == null) {
							e.status = e.queue.loaded() ? Status.PAUSED : Status.LOADING;
						} else {
							e.status = Status.UNAVAILABLE;
							e.reason = failure;
						}
					});
				}
			});
		} catch (RuntimeException ex) {
			h = null;
		}
		boolean stale;
		synchronized (this) {
			stale = e.refs == 0;
			if (!stale) {
				e.handle = h;
				if (h == null) {
					e.status = Status.UNAVAILABLE;
					e.reason = src.unavailableReason();
				}
			}
		}
		if (stale && h != null) {
			h.close();
		}
		if (h == null) {
			settle(e);
			onChange.run();
		}
	}

	private void update(Entry e, Runnable change) {
		synchronized (this) {
			if (entries.get(e.key) != e) {
				return; // closed meanwhile
			}
			change.run();
		}
		settle(e);
		onChange.run();
	}

	/** Completes the {@link #load} futures once the queue is known or cannot be. */
	private void settle(Entry e) {
		List<CompletableFuture<Snapshot>> done;
		Snapshot s;
		synchronized (this) {
			if (e.status == Status.LOADING || e.waiting.isEmpty()) {
				return;
			}
			done = new ArrayList<>(e.waiting);
			e.waiting.clear();
			s = e.snapshot();
		}
		done.forEach(f -> f.complete(s));
	}

	private void release(Entry e) {
		QueueSource.Handle h = null;
		List<CompletableFuture<Snapshot>> orphans = List.of();
		synchronized (this) {
			if (e.refs <= 0 || --e.refs > 0) {
				return; // already dropped by clear(), or still held
			}
			entries.remove(e.key, e);
			h = e.handle;
			e.handle = null;
			orphans = new ArrayList<>(e.waiting);
			e.waiting.clear();
		}
		if (h != null) {
			h.close();
		}
		Snapshot gone = new Snapshot(e.key, Status.UNAVAILABLE, "closed", List.of());
		orphans.forEach(f -> f.complete(gone));
		onChange.run();
	}

	/** Current state of a thread's queue (UNAVAILABLE with reason "not watched" when nobody watches it). */
	public synchronized Snapshot get(String envId, String threadId) {
		Key key = Key.of(envId, threadId);
		Entry e = entries.get(key);
		return e == null ? new Snapshot(key, Status.UNAVAILABLE, "not watched", List.of()) : e.snapshot();
	}

	/** Number of queued runs, or -1 when unknown. */
	public synchronized int count(String envId, String threadId) {
		Entry e = entries.get(Key.of(envId, threadId));
		return e == null || !(e.status == Status.OK || e.status == Status.PAUSED) ? -1 : e.queue.queued().size();
	}

	/** Every watched thread, in key order (env, thread). */
	public synchronized List<Snapshot> all() {
		List<Snapshot> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			out.add(e.snapshot());
		}
		out.sort((a, b) -> a.key().toString().compareTo(b.key().toString()));
		return out;
	}

	/**
	 * One-shot read: watches the thread until its queue is known (or unavailable), then lets go.
	 * Completes (never exceptionally) with LOADING after {@code timeoutMs}.
	 */
	public CompletableFuture<Snapshot> load(String envId, String threadId, long timeoutMs) {
		CompletableFuture<Snapshot> f = new CompletableFuture<>();
		Watch w = watch(envId, threadId);
		synchronized (this) {
			Entry e = entries.get(Key.of(envId, threadId));
			if (e != null) {
				if (e.status == Status.LOADING) {
					e.waiting.add(f);
				} else {
					f.complete(e.snapshot());
				}
			} else {
				f.complete(new Snapshot(Key.of(envId, threadId), Status.UNAVAILABLE, "closed", List.of()));
			}
		}
		return f.completeOnTimeout(new Snapshot(Key.of(envId, threadId), Status.LOADING, "timed out", List.of()), timeoutMs, TimeUnit.MILLISECONDS)
				.whenComplete((s, err) -> w.close());
	}

	/** Keeps exactly {@code keys} as background watches (opens new ones, closes the rest). */
	public void setBackground(Set<Key> keys) {
		List<Watch> close = new ArrayList<>();
		List<Key> open = new ArrayList<>();
		synchronized (this) {
			for (var it = background.entrySet().iterator(); it.hasNext(); ) {
				var en = it.next();
				if (!keys.contains(en.getKey())) {
					close.add(en.getValue());
					it.remove();
				}
			}
			for (Key k : new HashSet<>(keys)) {
				if (!background.containsKey(k)) {
					open.add(k);
				}
			}
		}
		close.forEach(Watch::close);
		for (Key k : open) {
			Watch w = watch(k.envId(), k.threadId());
			synchronized (this) {
				background.put(k, w);
			}
		}
	}

	/** Closes everything (on disconnect / reload). */
	public void clear() {
		setBackground(Set.of());
		List<Entry> all;
		synchronized (this) {
			all = new ArrayList<>(entries.values());
		}
		for (Entry e : all) {
			QueueSource.Handle h;
			synchronized (this) {
				entries.remove(e.key);
				e.refs = 0;
				h = e.handle;
				e.handle = null;
			}
			if (h != null) {
				h.close();
			}
		}
		onChange.run();
	}
}
