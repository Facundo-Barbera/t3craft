package dev.t3craft.t3;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct mode: one {@link T3Connection} per paired environment. Keeps each environment's latest
 * shell view (the filter's source, never trimmed) and turns them into the NPC list with
 * {@link Visibility} + {@link Presentation}. {@code onChange} runs on a connection thread; the
 * caller hops to its own thread.
 */
public final class DirectController implements T3Connection.Listener {
	public record EnvStatus(String id, String label, T3Connection.Status status, String detail) {
	}

	private final Environments environments;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Map<String, T3Connection> connections = new LinkedHashMap<>();
	private final Map<String, ShellState.View> views = new ConcurrentHashMap<>();
	private final Runnable onChange;
	private volatile Visibility.Options options = Visibility.Options.defaults();

	public DirectController(Environments environments, Runnable onChange) {
		this.environments = environments;
		this.onChange = onChange;
	}

	public Environments environments() {
		return environments;
	}

	public HttpClient http() {
		return http;
	}

	public void setOptions(Visibility.Options o) {
		options = o;
	}

	/** (Re)reads environments.json + credentials and connects to each environment. */
	public synchronized void start() {
		stop();
		for (Environments.Environment env : environments.list()) {
			T3Connection c = new T3Connection(env, () -> environments.credential(env.id()), this, http);
			connections.put(env.id(), c);
			c.start();
		}
		onChange.run();
	}

	public synchronized void stop() {
		connections.values().forEach(T3Connection::stop);
		connections.clear();
		views.clear();
	}

	public synchronized void reconnectAll() {
		connections.values().forEach(T3Connection::reconnect);
	}

	public synchronized List<EnvStatus> statuses() {
		List<EnvStatus> out = new ArrayList<>();
		connections.values().forEach(c -> out.add(new EnvStatus(c.environment().id(), c.environment().label(), c.status(), c.detail())));
		return out;
	}

	/** Filtered, ranked NPCs of every environment (cap applies per environment). */
	public List<NpcView> npcs(long nowMs) {
		List<NpcView> out = new ArrayList<>();
		Map<String, String> labels = new LinkedHashMap<>();
		synchronized (this) {
			connections.values().forEach(c -> labels.put(c.environment().id(), c.environment().label()));
		}
		labels.forEach((id, label) -> {
			ShellState.View v = views.get(id);
			if (v == null) {
				return;
			}
			for (ThreadShell t : Visibility.select(v.threads(), nowMs, options)) {
				out.add(Presentation.present(id, label, t, v.projectTitle(t.projectId())));
			}
		});
		out.sort(Comparator.comparingInt(NpcView::priority).thenComparing(NpcView::envLabel).thenComparing(NpcView::title));
		return out;
	}

	/**
	 * Read-only {@code searchThreads} on every connected environment (each over its existing
	 * connection), merged with {@link ThreadSearch#merge}. Environments that fail or time out are
	 * reported in {@link ThreadSearch.Outcome#errors()}; the rest still count. With no connected
	 * environment the outcome is empty with {@code searched == 0}. Cancelling the returned future
	 * cancels (and interrupts) every per-environment request. Completes on a connection thread.
	 */
	public CompletableFuture<ThreadSearch.Outcome> search(String query, int limit, long timeoutMs) {
		List<T3Connection> live = new ArrayList<>();
		int paired;
		synchronized (this) {
			paired = connections.size();
			connections.values().stream().filter(c -> c.status() == T3Connection.Status.OK).forEach(live::add);
		}
		String q = RpcFrames.searchQuery(query);
		if (live.isEmpty()) {
			return CompletableFuture.completedFuture(new ThreadSearch.Outcome(q, List.of(), 0, paired, List.of()));
		}
		List<CompletableFuture<Object>> parts = new ArrayList<>();
		for (T3Connection c : live) {
			Environments.Environment env = c.environment();
			CompletableFuture<ThreadSearch.Decoded> raw = c.searchThreads(q, limit, timeoutMs);
			parts.add(raw.handle((d, err) -> err == null
					? ThreadSearch.enrich(env.id(), env.label(), q, d, views.get(env.id()))
					: new ThreadSearch.EnvError(env.id(), env.label(), describe(err))));
			// cancelling the merged future must reach the request itself
			parts.getLast().whenComplete((x, e) -> {
				if (e instanceof CancellationException) {
					raw.cancel(true);
				}
			});
		}
		CompletableFuture<ThreadSearch.Outcome> out = CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(v -> {
			List<List<ThreadSearch.Hit>> hits = new ArrayList<>();
			List<ThreadSearch.EnvError> errors = new ArrayList<>();
			for (CompletableFuture<Object> p : parts) {
				Object r = p.join();
				if (r instanceof ThreadSearch.EnvError e) {
					errors.add(e);
				} else {
					@SuppressWarnings("unchecked")
					List<ThreadSearch.Hit> h = (List<ThreadSearch.Hit>) r;
					hits.add(h);
				}
			}
			return new ThreadSearch.Outcome(q, ThreadSearch.merge(hits, limit), live.size(), paired, List.copyOf(errors));
		});
		out.whenComplete((r, e) -> {
			if (out.isCancelled()) {
				parts.forEach(p -> p.cancel(true));
			}
		});
		return out;
	}

	static String describe(Throwable err) {
		Throwable c = err;
		while ((c instanceof CompletionException || c instanceof java.util.concurrent.ExecutionException) && c.getCause() != null) {
			c = c.getCause();
		}
		if (c instanceof ThreadSearch.SearchException) {
			return c.getMessage();
		}
		if (c instanceof CancellationException) {
			return "cancelled";
		}
		return T3Connection.sanitize(c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage()));
	}

	@Override
	public void onStatus(String envId, T3Connection.Status status, String detail) {
		onChange.run();
	}

	@Override
	public void onShell(String envId, ShellState.View view) {
		views.put(envId, view);
		onChange.run();
	}
}
