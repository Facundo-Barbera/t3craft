package dev.t3craft.t3;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

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
	 * The single live connection of one environment, or null when it is not paired/started. Views
	 * read thread streams through it ({@link T3Connection#subscribeThread}); never open another.
	 */
	public synchronized @Nullable T3Connection connection(String envId) {
		return connections.get(envId);
	}

	/** Last shell view of one environment (threads with their timestamps), or null before the first sync. */
	public ShellState.@Nullable View view(String envId) {
		return views.get(envId);
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
