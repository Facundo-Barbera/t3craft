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
 *
 * <p>Also owns the optional operate sockets ({@link OperateConnection}, one per environment,
 * opened on demand with the separate operate session) behind {@link #operateTransport}: the only
 * way writes reach T3. Screens never open their own sockets.
 */
public final class DirectController implements T3Connection.Listener {
	public record EnvStatus(String id, String label, T3Connection.Status status, String detail) {
	}

	private final Environments environments;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Map<String, T3Connection> connections = new LinkedHashMap<>();
	private final Map<String, ShellState.View> views = new ConcurrentHashMap<>();
	private final Map<String, OperateConnection> operate = new LinkedHashMap<>();
	private final OperateCredentials operateCredentials;
	private final Runnable onChange;
	private volatile Visibility.Options options = Visibility.Options.defaults();

	public DirectController(Environments environments, Runnable onChange) {
		this.environments = environments;
		this.onChange = onChange;
		this.operateCredentials = new OperateCredentials(environments.dir());
	}

	public OperateCredentials operateCredentials() {
		return operateCredentials;
	}

	/**
	 * The operate transport of a connected environment, or null when it is not connected here or
	 * has no operate session. Feed it to {@link OperateGate} as its {@link OperateGate.Transports}.
	 */
	public synchronized @Nullable OperateTransport operateTransport(String envId) {
		T3Connection c = connections.get(envId);
		if (c == null || !operateCredentials.has(envId, System.currentTimeMillis())) {
			return null;
		}
		return operate.computeIfAbsent(envId, id -> new OperateConnection(c.environment(),
				() -> operateCredentials.get(id, System.currentTimeMillis()), http));
	}

	/** Drops the operate socket of an environment (after enabling or revoking control). */
	public synchronized void resetOperate(String envId) {
		OperateConnection o = operate.remove(envId);
		if (o != null) {
			o.stop();
		}
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
		operate.values().forEach(OperateConnection::stop);
		operate.clear();
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
