package dev.t3craft.client.portable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot the portable screen draws. {@code connection} says whether there is any
 * source at all; the screen shows an honest empty/disconnected state instead of inventing rows.
 */
public record PortableState(Connection connection, String sourceLabel, List<PortableEnv> environments, List<PortableThread> threads) {
	public enum Connection {
		/** Direct mode with no paired environment (or bridge mode without a configured link). */
		NOT_CONFIGURED,
		/** Configured but no environment / bridge is currently reachable. */
		DISCONNECTED,
		/** At least one environment answers. */
		CONNECTED
	}

	public static final PortableState EMPTY = new PortableState(Connection.NOT_CONFIGURED, "", List.of(), List.of());

	public PortableState {
		environments = List.copyOf(environments);
		threads = List.copyOf(threads);
	}

	/**
	 * Builds a state from raw environment statuses and thread rows: threads sorted by priority
	 * (needs attention first), then environment, then title; per-environment counts filled in.
	 * Environments are {id → {label, status, detail}} in display order.
	 *
	 * @param configured false when there is nothing to connect to (direct: no paired environment)
	 * @param linkUp     bridge mode: whether the mod⇄bridge link is up (false → DISCONNECTED even if
	 *                   stale rows remain); null in direct mode, where environment statuses decide
	 */
	public static PortableState of(String sourceLabel, Map<String, String[]> envStatuses, List<PortableThread> threads, boolean configured,
			@Nullable Boolean linkUp) {
		List<PortableThread> sorted = new ArrayList<>(threads);
		sorted.sort(Comparator.comparingInt(PortableThread::priority)
				.thenComparing(PortableThread::envLabel)
				.thenComparing(PortableThread::title));
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (PortableThread t : sorted) {
			int[] c = counts.computeIfAbsent(t.env(), k -> new int[2]);
			c[0]++;
			if (t.needsAttention()) {
				c[1]++;
			}
		}
		List<PortableEnv> envs = new ArrayList<>();
		boolean anyOk = false;
		for (Map.Entry<String, String[]> e : envStatuses.entrySet()) {
			String[] v = e.getValue();
			int[] c = counts.getOrDefault(e.getKey(), new int[2]);
			PortableEnv env = new PortableEnv(e.getKey(), v.length > 0 ? v[0] : e.getKey(), v.length > 1 ? v[1] : "?", v.length > 2 ? v[2] : "", c[0], c[1]);
			anyOk |= env.ok();
			envs.add(env);
		}
		Connection conn;
		if (!configured) {
			conn = Connection.NOT_CONFIGURED;
		} else if (Boolean.FALSE.equals(linkUp)) {
			conn = Connection.DISCONNECTED;
		} else if (anyOk || (Boolean.TRUE.equals(linkUp) && envs.isEmpty())) {
			// bridge up but it has not reported per-environment statuses yet
			conn = Connection.CONNECTED;
		} else {
			conn = Connection.DISCONNECTED;
		}
		return new PortableState(conn, sourceLabel, envs, sorted);
	}

	public int needsAttention() {
		return (int) threads.stream().filter(PortableThread::needsAttention).count();
	}

	public List<PortableThread> threadsOf(@Nullable String envId) {
		if (envId == null) {
			return threads;
		}
		return threads.stream().filter(t -> envId.equals(t.env())).toList();
	}
}
