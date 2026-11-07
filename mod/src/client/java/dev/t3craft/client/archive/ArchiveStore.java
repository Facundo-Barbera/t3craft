package dev.t3craft.client.archive;

import dev.t3craft.t3.ArchiveWire;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Cache of every environment's closed threads, merged from three read-only sources:
 * <ol>
 *   <li>the shell view {@code subscribeShell} already keeps (settled threads, live, free);</li>
 *   <li>{@code getArchivedShellSnapshot} (archived threads and their projects);</li>
 *   <li>{@code getOlderSettledThreads}, page by page (50 at most per page), newest settle first.</li>
 * </ol>
 * The shell wins: a thread it lists as active again leaves the archive, and its data beats the
 * fetched copies. Older settled pages without project names borrow them from the shell or the
 * archived snapshot.
 *
 * <p><b>Refresh</b> is deliberately rare: the archived snapshot and the first older page are read
 * once when an environment comes online and then every {@link #REFRESH_MS} (10 min). More pages
 * are read only on demand ({@link #loadMore}, the catalog's "older" button). A failure backs off
 * from {@link #BACKOFF_MIN_MS} to {@link #REFRESH_MS}. {@link #refreshNow} is ignored within
 * {@link #MANUAL_MIN_MS} of the last read. At most one read of each kind is in flight per
 * environment.
 *
 * <p><b>Threads</b>: all methods on one thread (the client thread). Futures may complete anywhere;
 * their results are queued and applied in {@link #tick}. Pure: no Minecraft classes, clock passed in.
 */
public final class ArchiveStore {
	public static final long REFRESH_MS = 10 * 60_000L;
	public static final long BACKOFF_MIN_MS = 30_000L;
	public static final long MANUAL_MIN_MS = 30_000L;
	public static final int PAGE = ArchiveWire.MAX_PAGE;

	/** What the catalog shows about one environment. */
	public record EnvStatus(String env, String label, boolean connected, boolean loading, @Nullable String error, int listed,
			boolean hasMore) {
	}

	private static final class Env {
		final String id;
		String label;
		boolean connected;
		ShellState.@Nullable View view;
		Map<String, ThreadShell> shell = Map.of();
		Map<String, ThreadShell> active = Map.of();
		ArchiveWire.ArchivedSnapshot archived = ArchiveWire.ArchivedSnapshot.EMPTY;
		final Map<String, ThreadShell> older = new LinkedHashMap<>();
		ArchiveWire.@Nullable Cursor nextCursor;
		boolean hasMore;
		long nextArchivedAt;
		long nextOlderAt;
		long lastReadAt = Long.MIN_VALUE / 2;
		int archivedFailures;
		int olderFailures;
		boolean archivedInFlight;
		boolean olderInFlight;
		@Nullable String error;

		Env(String id, String label) {
			this.id = id;
			this.label = label;
		}
	}

	private final ArchiveSource source;
	private final Map<String, Env> envs = new LinkedHashMap<>();
	private final ConcurrentLinkedQueue<Runnable> results = new ConcurrentLinkedQueue<>();
	private List<ArchiveEntry> entries = List.of();
	private boolean dirty;
	private int version;

	public ArchiveStore(ArchiveSource source) {
		this.source = source;
	}

	/**
	 * Feeds one environment's state (call every tick for every paired environment; cheap when the
	 * view object did not change). {@code connected}: reads are only attempted while true.
	 */
	public void updateEnv(String env, String label, ShellState.@Nullable View view, boolean connected) {
		Env e = envs.computeIfAbsent(env, k -> new Env(k, label));
		if (!label.equals(e.label)) {
			e.label = label;
			dirty = true;
		}
		if (connected && !e.connected) {
			// came (back) online: read soon, without waiting for the old schedule
			e.nextArchivedAt = Math.min(e.nextArchivedAt, 0);
			e.nextOlderAt = Math.min(e.nextOlderAt, 0);
		}
		e.connected = connected;
		if (view != e.view) {
			e.view = view;
			Map<String, ThreadShell> closed = new LinkedHashMap<>();
			Map<String, ThreadShell> open = new HashMap<>();
			if (view != null) {
				for (ThreadShell t : view.threads()) {
					(ArchiveEntry.isCandidate(t) ? closed : open).put(t.id(), t);
				}
			}
			e.shell = closed;
			e.active = open;
			dirty = true;
		}
	}

	/** Forgets environments that are no longer paired. */
	public void retainEnvs(Set<String> ids) {
		if (envs.keySet().retainAll(ids)) {
			dirty = true;
		}
	}

	/** Applies finished reads, then starts the ones that are due. */
	public void tick(long nowMs) {
		drain();
		if (source.available()) {
			for (Env e : envs.values()) {
				if (!e.connected) {
					continue;
				}
				if (!e.archivedInFlight && nowMs >= e.nextArchivedAt) {
					fetchArchived(e, nowMs);
				}
				if (!e.olderInFlight && nowMs >= e.nextOlderAt) {
					fetchOlder(e, ArchiveWire.Cursor.before(nowMs), true, nowMs);
				}
			}
		}
		drain();
	}

	/** Reads the next older page of every environment that has more (no-op while one is in flight). */
	public void loadMore(long nowMs) {
		if (!source.available()) {
			return;
		}
		for (Env e : envs.values()) {
			if (e.connected && e.hasMore && !e.olderInFlight && e.nextCursor != null) {
				fetchOlder(e, e.nextCursor, false, nowMs);
			}
		}
		drain();
	}

	/** Re-reads everything soon, unless the last read was under {@link #MANUAL_MIN_MS} ago. Returns whether it will. */
	public boolean refreshNow(long nowMs) {
		boolean any = false;
		for (Env e : envs.values()) {
			if (nowMs - e.lastReadAt >= MANUAL_MIN_MS) {
				e.nextArchivedAt = nowMs;
				e.nextOlderAt = nowMs;
				any = true;
			}
		}
		return any;
	}

	public boolean anyMore() {
		return envs.values().stream().anyMatch(e -> e.hasMore);
	}

	public boolean loading() {
		return envs.values().stream().anyMatch(e -> e.archivedInFlight || e.olderInFlight);
	}

	/** Whether the reads beyond the shell are possible at all (false: only shell settled threads). */
	public boolean fullReads() {
		return source.available();
	}

	/** Bumps whenever {@link #entries()} may have changed. */
	public int version() {
		rebuildIfDirty();
		return version;
	}

	/** Every closed thread of every environment, newest close first. */
	public List<ArchiveEntry> entries() {
		rebuildIfDirty();
		return entries;
	}

	/** Page {@code index} (0-based, clamped) of {@code size} entries. */
	public List<ArchiveEntry> page(int index, int size) {
		List<ArchiveEntry> all = entries();
		int pages = pageCount(size);
		int i = Math.max(0, Math.min(index, pages - 1));
		return all.subList(Math.min(all.size(), i * size), Math.min(all.size(), (i + 1) * size));
	}

	public int pageCount(int size) {
		return Math.max(1, (entries().size() + Math.max(1, size) - 1) / Math.max(1, size));
	}

	public @Nullable ArchiveEntry entry(String key) {
		for (ArchiveEntry e : entries()) {
			if (e.key().equals(key)) {
				return e;
			}
		}
		return null;
	}

	public List<EnvStatus> statuses() {
		rebuildIfDirty();
		List<EnvStatus> out = new ArrayList<>();
		Map<String, Integer> counts = new HashMap<>();
		entries.forEach(x -> counts.merge(x.env(), 1, Integer::sum));
		for (Env e : envs.values()) {
			out.add(new EnvStatus(e.id, e.label, e.connected, e.archivedInFlight || e.olderInFlight, e.error, counts.getOrDefault(e.id, 0), e.hasMore));
		}
		return out;
	}

	// ---- reads --------------------------------------------------------------------------------

	private void fetchArchived(Env e, long nowMs) {
		e.archivedInFlight = true;
		e.lastReadAt = nowMs;
		e.nextArchivedAt = nowMs + REFRESH_MS;
		CompletableFuture<ArchiveWire.ArchivedSnapshot> f;
		try {
			f = source.archived(e.id);
		} catch (RuntimeException ex) {
			f = CompletableFuture.failedFuture(ex);
		}
		String id = e.id;
		f.whenComplete((snap, err) -> results.add(() -> with(id, env -> {
			env.archivedInFlight = false;
			if (err != null) {
				env.archivedFailures++;
				env.nextArchivedAt = Math.min(env.nextArchivedAt, nowMs + backoff(env.archivedFailures));
				fail(env, err);
				return;
			}
			env.archivedFailures = 0;
			env.archived = snap;
			ok(env);
		})));
	}

	private void fetchOlder(Env e, ArchiveWire.Cursor cursor, boolean first, long nowMs) {
		e.olderInFlight = true;
		e.lastReadAt = nowMs;
		if (first) {
			e.nextOlderAt = nowMs + REFRESH_MS;
		}
		CompletableFuture<ArchiveWire.OlderSettledPage> f;
		try {
			f = source.older(e.id, cursor, PAGE);
		} catch (RuntimeException ex) {
			f = CompletableFuture.failedFuture(ex);
		}
		String id = e.id;
		f.whenComplete((page, err) -> results.add(() -> with(id, env -> {
			env.olderInFlight = false;
			if (err != null) {
				if (first) {
					env.olderFailures++;
					env.nextOlderAt = Math.min(env.nextOlderAt, nowMs + backoff(env.olderFailures));
				}
				fail(env, err);
				return;
			}
			if (first) {
				env.olderFailures = 0;
			}
			if (first) {
				// a refresh of the newest page: pages loaded on demand beyond it stay (de-duplicated by id)
				Map<String, ThreadShell> rest = new LinkedHashMap<>(env.older);
				env.older.clear();
				page.threads().forEach(t -> env.older.put(t.id(), t));
				rest.forEach(env.older::putIfAbsent);
				if (rest.isEmpty() || env.nextCursor == null) {
					advance(env, page);
				}
			} else {
				page.threads().forEach(t -> env.older.put(t.id(), t));
				advance(env, page);
			}
			ok(env);
		})));
	}

	private static void advance(Env env, ArchiveWire.OlderSettledPage page) {
		List<ThreadShell> ts = page.threads();
		env.hasMore = ts.size() >= PAGE;
		env.nextCursor = ts.isEmpty() ? null : ArchiveWire.Cursor.after(ts.getLast());
		if (env.nextCursor == null) {
			env.hasMore = false;
		}
	}

	private void ok(Env env) {
		if (env.archivedFailures == 0 && env.olderFailures == 0) {
			env.error = null;
		}
		dirty = true;
	}

	/** {@link #BACKOFF_MIN_MS} doubling per consecutive failure, capped at {@link #REFRESH_MS}. */
	static long backoff(int failures) {
		return Math.min(REFRESH_MS, BACKOFF_MIN_MS << Math.min(Math.max(failures, 1) - 1, 10));
	}

	private void fail(Env env, Throwable err) {
		Throwable c = err;
		while (c.getCause() != null && (c instanceof java.util.concurrent.CompletionException || c instanceof java.util.concurrent.ExecutionException)) {
			c = c.getCause();
		}
		env.error = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
		dirty = true;
	}

	private void with(String id, Consumer<Env> body) {
		Env e = envs.get(id);
		if (e != null) {
			body.accept(e);
		}
	}

	private void drain() {
		Runnable r;
		while ((r = results.poll()) != null) {
			r.run();
		}
	}

	// ---- merge --------------------------------------------------------------------------------

	private void rebuildIfDirty() {
		if (!dirty) {
			return;
		}
		dirty = false;
		version++;
		List<ArchiveEntry> out = new ArrayList<>();
		for (Env e : envs.values()) {
			Map<String, String> titles = new HashMap<>(e.archived.projectTitles());
			if (e.view != null) {
				titles.putAll(e.view.projectTitles());
			}
			Set<String> seen = new HashSet<>();
			List<ThreadShell> ordered = new ArrayList<>(e.shell.values());
			ordered.addAll(e.archived.threads());
			ordered.addAll(e.older.values());
			for (ThreadShell t : ordered) {
				if (e.active.containsKey(t.id()) || !ArchiveEntry.isCandidate(t) || !seen.add(t.id())) {
					continue;
				}
				out.add(ArchiveEntry.of(e.id, e.label, t, titles.get(t.projectId())));
			}
		}
		out.sort(ArchiveEntry.NEWEST_FIRST);
		entries = List.copyOf(out);
	}
}
