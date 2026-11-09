package dev.t3craft.client.chat;

import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * In-memory cache of thread transcripts and their stream subscriptions (feat/t3-thread-cache, see
 * {@code docs/features/t3-thread-cache.md}). Every {@link ThreadFeed} is a view over one {@link Entry}
 * here, keyed by {@code env:threadId}:
 *
 * <ul>
 *   <li><b>Transcripts</b> outlive their views: an LRU of at most {@link #MAX_ENTRIES} threads and
 *   about {@link #MAX_CHARS} chars of rows; entries a view still holds are never evicted. Reopening
 *   a thread paints the cached rows at once; the next snapshot reconciles over them
 *   ({@link ChatTranscript#applyStreamItems}: same keys replace in place, loaded older pages kept).</li>
 *   <li><b>Subscriptions linger</b>: when the last view lets go, the entry keeps its
 *   {@code subscribeThread} for {@link #LINGER_MS}, at most {@link #MAX_LINGERING} at once (the
 *   oldest released is closed first), so coming back is instant and still up to date.</li>
 *   <li><b>Prewarm</b> ({@link #prewarm}): opens a lingering subscription without a view, through a
 *   small token bucket ({@link #PREWARM_BURST} at once, one more per {@link #PREWARM_REFILL_MS}),
 *   only on environments whose connection is OK.</li>
 *   <li><b>Pages and items</b>: {@code threadHistory} pages by cursor and finished
 *   {@code getTurnItem} results by item id are kept per entry (bounded).</li>
 *   <li><b>Invalidation</b>: an entry bound to a connection that is no longer
 *   {@link ChatSource.Env#current() current} drops its subscription (a view re-acquires it on the new
 *   connection and re-subscribes); {@link #tick} drops entries of threads gone from the synced
 *   shell (archived or deleted).</li>
 * </ul>
 *
 * <p>Concurrency: the cache's own monitor guards the map, refcounts and subscriptions (the client
 * thread calls in). Stream listeners run on the connection thread and only touch the entry's
 * volatile fields and its (self-synchronized) transcript, never the cache monitor, so there is no
 * lock-order cycle with the {@link StreamRegistry}. Nothing here blocks on a future.
 */
public final class ThreadCache {
	public static final int MAX_ENTRIES = 24;
	/** Rough budget for every cached row (bodies + details), in chars (~2 bytes each). */
	public static final long MAX_CHARS = 6_000_000;
	public static final long LINGER_MS = 60_000;
	public static final int MAX_LINGERING = 4;
	public static final int MAX_PAGES = 8;
	public static final int MAX_ITEMS = 64;
	public static final int PREWARM_BURST = 3;
	public static final long PREWARM_REFILL_MS = 1_000;

	private static final ThreadCache INSTANCE = new ThreadCache(System::currentTimeMillis);

	public static ThreadCache get() {
		return INSTANCE;
	}

	/** One cached thread. Fields read by the view are volatile; structural changes go through the cache. */
	public static final class Entry {
		final String key;
		final String threadId;
		final ChatTranscript transcript;
		final List<Runnable> listeners = new CopyOnWriteArrayList<>();
		private final Map<String, JsonObject> pages = new LinkedHashMap<>(8, 0.75f, true);
		private final Map<String, JsonObject> items = new LinkedHashMap<>(16, 0.75f, true);
		volatile ChatSource.@Nullable Env env;
		/** Guarded by the cache. */
		StreamRegistry.@Nullable Subscription sub;
		/** Guarded by the cache. */
		int refs;
		/** Guarded by the cache. */
		long releasedAt;
		volatile boolean subscribed;
		volatile @Nullable String ended;
		volatile long waitingSince;
		/** {@link ChatTranscript#snapshots()} when the current subscription started. */
		volatile long snapshotsAtSubscribe;
		volatile boolean loadingOlder;
		volatile @Nullable String olderError;

		Entry(String key, String threadId) {
			this.key = key;
			this.threadId = threadId;
			this.transcript = new ChatTranscript(threadId);
		}

		public String key() {
			return key;
		}

		public ChatTranscript transcript() {
			return transcript;
		}

		/** A snapshot arrived on the current subscription (what is shown is up to date). */
		public boolean fresh() {
			return subscribed && transcript.snapshots() > snapshotsAtSubscribe;
		}

		void changed() {
			for (Runnable r : listeners) {
				try {
					r.run();
				} catch (RuntimeException ignored) {
					// a broken view must not break the stream
				}
			}
		}

		synchronized @Nullable JsonObject page(String cursor) {
			return pages.get(cursor);
		}

		synchronized void putPage(String cursor, JsonObject page) {
			pages.put(cursor, page);
			trim(pages, MAX_PAGES);
		}

		synchronized @Nullable JsonObject item(String itemId) {
			return items.get(itemId);
		}

		synchronized void putItem(String itemId, JsonObject item) {
			items.put(itemId, item);
			trim(items, MAX_ITEMS);
		}

		private static void trim(Map<String, JsonObject> m, int cap) {
			var it = m.keySet().iterator();
			while (m.size() > cap && it.hasNext()) {
				it.next();
				it.remove();
			}
		}
	}

	private final LongSupplier clock;
	/** Access order: the eldest is the least recently used. */
	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);
	private @Nullable String lastOpened;
	private double tokens = PREWARM_BURST;
	private long tokensAt;
	/** Seeds for transcripts created before their first snapshot (the disk cache). */
	private volatile Function<String, ThreadDiskCache.@Nullable Tail> seeds = k -> null;

	ThreadCache(LongSupplier clock) {
		this.clock = clock;
	}

	/** Environment id of the {@code /t3dev ui} gallery's fake data: its transcripts are kept out of {@link #recent}. */
	public static final String GALLERY_ENV = "t3dev-gallery";

	static String key(String envId, String threadId) {
		return envId + ":" + threadId;
	}

	/** Where new transcripts get their disk rows from (null = none). */
	public void setSeeds(Function<String, ThreadDiskCache.@Nullable Tail> seeds) {
		this.seeds = seeds;
	}

	/** The entry of a thread, created (and seeded from disk) when missing. Takes no reference. */
	public synchronized Entry entry(String envId, String threadId) {
		String k = key(envId, threadId);
		Entry e = entries.get(k);
		if (e == null) {
			e = new Entry(k, threadId);
			ThreadDiskCache.Tail t = seeds.apply(k);
			if (t != null) {
				e.transcript.seed(t.title(), t.entries());
			}
			entries.put(k, e);
			enforceBudget();
		}
		return e;
	}

	/** A view starts using {@code e} on {@code env}: binds it (re-subscribing after a replaced connection). */
	public synchronized void acquire(Entry e, ChatSource.Env env) {
		Entry inMap = entries.get(e.key);
		if (inMap != e) {
			if (inMap != null) {
				closeSub(inMap); // evicted meanwhile and recreated: ours wins
			}
			entries.put(e.key, e);
		}
		bind(e, env);
		e.refs++;
		enforceBudget();
	}

	/** A view lets go of {@code e}: the subscription lingers for {@link #LINGER_MS}. */
	public synchronized void release(Entry e) {
		if (e.refs > 0 && --e.refs == 0) {
			e.releasedAt = clock.getAsLong();
			enforceLingering();
		}
	}

	/** Drops the subscription of {@code e} and takes a new one (a fresh snapshot). */
	public synchronized void reconnect(Entry e) {
		closeSub(e);
		ChatSource.Env env = e.env;
		if (env != null) {
			bind(e, env);
		}
	}

	/** Marks a thread as the one the player opened last (screen-open prewarm). */
	public synchronized void noteOpened(String key) {
		lastOpened = key;
	}

	public synchronized @Nullable String lastOpened() {
		return lastOpened;
	}

	/**
	 * Warms a thread without a view: subscribes (or keeps the current subscription) and leaves it
	 * lingering. Rate-limited and skipped on environments that are not connected. Returns true when
	 * it did something.
	 */
	public synchronized boolean prewarm(ChatSource.Env env, String threadId) {
		if (env.status() != T3Connection.Status.OK || !env.current()) {
			return false;
		}
		String k = key(env.id(), threadId);
		Entry e = entries.get(k);
		if (e != null && e.sub != null && e.env != null && e.env.current()) {
			if (e.refs == 0) {
				e.releasedAt = clock.getAsLong(); // keep it a little longer
			}
			return false;
		}
		long now = clock.getAsLong();
		tokens = Math.min(PREWARM_BURST, tokens + (now - tokensAt) / (double) PREWARM_REFILL_MS);
		tokensAt = now;
		if (tokens < 1) {
			return false;
		}
		tokens -= 1;
		Entry w = entry(env.id(), threadId);
		bind(w, env);
		if (w.refs == 0) {
			w.releasedAt = now;
		}
		enforceLingering();
		return true;
	}

	/**
	 * Housekeeping, on the client thread (about once a second): lingering subscriptions past
	 * {@link #LINGER_MS} or bound to a replaced connection are closed; entries of threads missing
	 * from a synced shell are dropped ({@code liveThreads(envId)} = the shell's thread ids, null
	 * when the environment has no synced shell to judge by).
	 */
	public synchronized void tick(Function<String, @Nullable Set<String>> liveThreads) {
		long now = clock.getAsLong();
		Map<String, @Nullable Set<String>> live = new LinkedHashMap<>();
		for (Entry e : List.copyOf(entries.values())) {
			ChatSource.Env env = e.env;
			if (e.refs == 0 && e.sub != null && (now - e.releasedAt >= LINGER_MS || env == null || !env.current())) {
				closeSub(e);
			}
			int colon = e.key.indexOf(':');
			String envId = e.key.substring(0, colon);
			Set<String> ids = live.computeIfAbsent(envId, liveThreads);
			if (ids != null && !ids.contains(e.threadId) && e.refs == 0) {
				closeSub(e); // archived or deleted: out of the cache
				entries.remove(e.key);
			}
		}
	}

	/** Drops one thread from the cache (unless a view holds it). */
	public synchronized void invalidate(String key) {
		Entry e = entries.get(key);
		if (e != null && e.refs == 0) {
			closeSub(e);
			entries.remove(key);
		}
	}

	/** Entries most recently used first, with at least one row (the disk cache's candidates). */
	public synchronized List<Entry> recent(int n) {
		List<Entry> all = new ArrayList<>(entries.values());
		List<Entry> out = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0 && out.size() < n; i--) {
			Entry e = all.get(i);
			// /t3dev ui (#13): the gallery's fake threads never reach the disk cache
			if (e.transcript.hasSnapshot() && !e.key.startsWith(GALLERY_ENV + ":")) {
				out.add(e);
			}
		}
		return out;
	}

	/** Subscriptions open now (views + lingering), for /t3dev-style counters and the doc. */
	public synchronized int openSubscriptions() {
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.sub != null) {
				n++;
			}
		}
		return n;
	}

	// ---- internals (under the cache monitor) ---------------------------------------------------

	private void bind(Entry e, ChatSource.Env env) {
		ChatSource.Env old = e.env;
		if (e.sub != null && old != null && !old.current()) {
			closeSub(e); // the connection was replaced: follow the new one
		}
		e.env = env;
		if (e.sub == null) {
			open(e, env);
		}
	}

	private void open(Entry e, ChatSource.Env env) {
		e.ended = null;
		e.subscribed = false;
		e.waitingSince = clock.getAsLong();
		e.snapshotsAtSubscribe = e.transcript.snapshots();
		e.sub = env.subscribeThread(e.threadId, new StreamRegistry.Listener() {
			@Override
			public void onSubscribed() {
				e.snapshotsAtSubscribe = e.transcript.snapshots(); // a fresh snapshot is on its way
				e.subscribed = true;
				e.ended = null;
				e.waitingSince = clock.getAsLong();
				e.changed();
			}

			@Override
			public void onItems(List<JsonObject> batch) {
				if (e.transcript.applyStreamItems(batch)) {
					e.changed();
				}
			}

			@Override
			public void onEnded(@Nullable String failure) {
				e.subscribed = false;
				if (failure != null) {
					e.ended = failure.contains("NotFound") ? "thread not found on " + env.label() : T3Connection.sanitize(failure);
				}
				e.changed();
			}
		});
	}

	private void closeSub(Entry e) {
		StreamRegistry.Subscription s = e.sub;
		e.sub = null;
		e.subscribed = false;
		if (s != null) {
			s.close();
		}
	}

	/** At most {@link #MAX_LINGERING} subscriptions without a view: the oldest released goes first. */
	private void enforceLingering() {
		List<Entry> lingering = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.refs == 0 && e.sub != null) {
				lingering.add(e);
			}
		}
		lingering.sort((a, b) -> Long.compare(a.releasedAt, b.releasedAt));
		for (int i = 0; i < lingering.size() - MAX_LINGERING; i++) {
			closeSub(lingering.get(i));
		}
	}

	/** Count and size caps; entries held by a view are never evicted. */
	private void enforceBudget() {
		long chars = 0;
		for (Entry e : entries.values()) {
			chars += e.transcript.approxChars();
		}
		var it = entries.values().iterator();
		while ((entries.size() > MAX_ENTRIES || chars > MAX_CHARS) && it.hasNext()) {
			Entry e = it.next();
			if (e.refs > 0) {
				continue;
			}
			chars -= e.transcript.approxChars();
			closeSub(e);
			it.remove();
		}
	}

	/** Item statuses that can still change: such items are not cached. */
	private static final Set<String> ITEM_ACTIVE = Set.of("pending", "queued", "running", "in_progress", "inProgress", "started",
			"streaming", "preparing", "starting");

	/** True when a {@code getTurnItem} result is final (cacheable). */
	static boolean finished(@Nullable JsonObject item) {
		if (item == null) {
			return false;
		}
		JsonObject o = item.has("item") && item.get("item").isJsonObject() ? item.getAsJsonObject("item") : item;
		String status = Json.str(o, "status");
		return !Json.bool(o, "streaming") && (status == null || !ITEM_ACTIVE.contains(status));
	}
}
