package dev.t3craft.client.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Model of the unified thread list: one list across every environment, sorted by attention then
 * recent activity, with an environment filter (a set of hidden environments: environments are only
 * a filter, never a section) and a selection that follows the thread (by key) when the list
 * reorders. Pure Java, not thread-safe (client thread only).
 *
 * <p>Order ({@link #ORDER}): {@link Attention} (needs you → error → approval pending → working →
 * done → idle), then most recent activity first (unknown last), then active before settled, then
 * title, then key (stable).
 */
public final class ThreadList {
	/** One environment as a filter chip. */
	public record Env(String id, String label, String status, int threads, int attention, EnvBadge badge) {
	}

	/** Environment as given by the host: label, connection status wire, optional colour. */
	public record EnvInfo(String label, String status, @Nullable Integer color) {
	}

	/** {@link ThreadOrder#ROWS}: the same order the portable terminal and the held screen use. */
	public static final Comparator<ThreadRow> ORDER = ThreadOrder.ROWS;

	private List<ThreadRow> all = List.of();
	private List<ThreadRow> visible = List.of();
	private List<Env> envs = List.of();
	private Map<String, EnvBadge> badges = Map.of();
	private Set<String> hidden = Set.of();
	private @Nullable String selectedKey;

	/** Replaces the rows (any order) and the environments (id → info, display order). */
	public void update(List<ThreadRow> rows, Map<String, EnvInfo> environments) {
		List<ThreadRow> sorted = new ArrayList<>(rows);
		sorted.sort(ORDER);
		all = List.copyOf(sorted);
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, String> labels = new LinkedHashMap<>();
		for (ThreadRow r : all) {
			int[] c = counts.computeIfAbsent(r.env(), k -> new int[2]);
			c[0]++;
			if (r.needsAttention()) {
				c[1]++;
			}
			labels.putIfAbsent(r.env(), r.envLabel());
		}
		List<Env> out = new ArrayList<>();
		Map<String, EnvBadge> b = new LinkedHashMap<>();
		for (Map.Entry<String, EnvInfo> e : environments.entrySet()) {
			EnvInfo v = e.getValue();
			int[] c = counts.getOrDefault(e.getKey(), new int[2]);
			String label = v.label() == null || v.label().isBlank() ? labels.getOrDefault(e.getKey(), e.getKey()) : v.label();
			EnvBadge badge = EnvBadge.of(e.getKey(), label, v.color());
			out.add(new Env(e.getKey(), label, v.status() == null ? "" : v.status(), c[0], c[1], badge));
			b.put(e.getKey(), badge);
			labels.remove(e.getKey());
		}
		// Environments that have rows but no status entry (bridge mode before its status arrives).
		labels.forEach((id, label) -> {
			int[] c = counts.get(id);
			EnvBadge badge = EnvBadge.of(id, label);
			out.add(new Env(id, label, "", c[0], c[1], badge));
			b.put(id, badge);
		});
		envs = List.copyOf(out);
		badges = Map.copyOf(b);
		refilter();
	}

	private void refilter() {
		visible = hidden.isEmpty() ? all : all.stream().filter(r -> !hidden.contains(r.env())).toList();
	}

	/** Every row of every environment, in display order. */
	public List<ThreadRow> all() {
		return all;
	}

	/** Rows after the environment filter, in display order. */
	public List<ThreadRow> visible() {
		return visible;
	}

	public List<Env> environments() {
		return envs;
	}

	/** Badge of an environment (configured colour when known). */
	public EnvBadge badge(ThreadRow r) {
		EnvBadge b = badges.get(r.env());
		return b != null ? b : EnvBadge.of(r.env(), r.envLabel());
	}

	// ---- filter -------------------------------------------------------------------------------

	/**
	 * Hidden environment ids. Unknown ids are kept (an environment can come back later, and the set
	 * is what the config persists).
	 */
	public Set<String> hidden() {
		return hidden;
	}

	public void setHidden(Set<String> ids) {
		hidden = Set.copyOf(new LinkedHashSet<>(ids));
		refilter();
	}

	public boolean isShown(String envId) {
		return !hidden.contains(envId);
	}

	/** Shows or hides one environment. Hiding the last shown environment shows them all instead. */
	public void toggle(String envId) {
		Set<String> h = new LinkedHashSet<>(hidden);
		if (!h.remove(envId)) {
			h.add(envId);
		}
		if (!envs.isEmpty() && envs.stream().allMatch(e -> h.contains(e.id()))) {
			h.clear();
		}
		setHidden(h);
	}

	/** Shows only {@code envId}; called again on the same one, shows every environment. */
	public void solo(String envId) {
		Set<String> h = new LinkedHashSet<>();
		envs.forEach(e -> {
			if (!e.id().equals(envId)) {
				h.add(e.id());
			}
		});
		boolean alreadySolo = envs.stream().allMatch(e -> e.id().equals(envId) ? !hidden.contains(e.id()) : hidden.contains(e.id()));
		setHidden(alreadySolo ? Set.of() : h);
	}

	public void showAll() {
		setHidden(Set.of());
	}

	/** True when at least one known environment is hidden. */
	public boolean filtered() {
		return envs.stream().anyMatch(e -> hidden.contains(e.id()));
	}

	/** Urgent threads (needs you, error, approval pending) among the visible ones. */
	public int attention() {
		return (int) visible.stream().filter(ThreadRow::needsAttention).count();
	}

	// ---- selection ----------------------------------------------------------------------------

	public @Nullable String selectedKey() {
		return selectedKey;
	}

	/** The selected row if it is visible under the current filter. */
	public @Nullable ThreadRow selected() {
		int i = selectedIndex();
		return i < 0 ? null : visible.get(i);
	}

	/** Index of the selection in {@link #visible()}, or -1. */
	public int selectedIndex() {
		if (selectedKey == null) {
			return -1;
		}
		for (int i = 0; i < visible.size(); i++) {
			if (visible.get(i).key().equals(selectedKey)) {
				return i;
			}
		}
		return -1;
	}

	/** Selects by key even when that thread is not (yet) in the list. */
	public void select(@Nullable String key) {
		selectedKey = key;
	}

	/**
	 * Moves the selection by {@code delta} rows within the visible list (clamped). With nothing
	 * selected, down starts at the top and up at the bottom. Returns true when it changed.
	 */
	public boolean move(int delta) {
		if (visible.isEmpty()) {
			return false;
		}
		int i = selectedIndex();
		int next = i < 0 ? (delta >= 0 ? 0 : visible.size() - 1) : Math.max(0, Math.min(visible.size() - 1, i + delta));
		String key = visible.get(next).key();
		boolean changed = !Objects.equals(key, selectedKey);
		selectedKey = key;
		return changed;
	}

	public @Nullable ThreadRow find(String key) {
		for (ThreadRow r : all) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return null;
	}
}
