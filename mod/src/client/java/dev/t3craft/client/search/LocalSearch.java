package dev.t3craft.client.search;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.ThreadSearch;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fallback when there is no direct connection to search over (bridge mode): filters the threads the
 * mod already lists by title, in the same {@link ThreadSearch.Hit} shape and order. Pure.
 */
public final class LocalSearch {
	private LocalSearch() {
	}

	public static ThreadSearch.Outcome filter(List<NpcInfo> npcs, String query, int limit) {
		String q = RpcFrames.searchQuery(query);
		String needle = q.toLowerCase(Locale.ROOT);
		List<ThreadSearch.Hit> hits = new ArrayList<>();
		for (NpcInfo n : npcs) {
			String title = n.title() == null ? "" : n.title();
			String lower = title.toLowerCase(Locale.ROOT);
			if (needle.isEmpty() || !lower.contains(needle)) {
				continue;
			}
			int sep = n.id().indexOf(':');
			String env = sep > 0 ? n.id().substring(0, sep) : "";
			String thread = sep > 0 ? n.id().substring(sep + 1) : n.id();
			double relevance = lower.startsWith(needle) ? 1.4 : 1.3;
			hits.add(new ThreadSearch.Hit(env, n.envLabel(), thread, title, n.groupLabel(), state(n.state()), null, relevance, 0, 0));
		}
		return new ThreadSearch.Outcome(q, ThreadSearch.merge(List.of(hits), limit), 1, 1, List.of());
	}

	static ThreadState state(String wire) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(wire)) {
				return s;
			}
		}
		return ThreadState.IDLE;
	}
}
