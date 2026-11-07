package dev.t3craft.client.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Model of the unified thread list: one list across environments, sorted by attention then
 * activity, with an optional environment filter and a selection that follows the thread (by key)
 * when the list reorders. Pure Java, not thread-safe (client thread only).
 *
 * <p>Order: state priority (needs you → error → working → done → idle), then most recent activity
 * first (when known), then settled last, then title, then key (stable).
 */
public final class ThreadList {
	/** One environment as a filter choice. */
	public record Env(String id, String label, String status, int threads, int attention) {
		public EnvBadge badge() {
			return EnvBadge.of(id, label);
		}
	}

	static final Comparator<ThreadRow> ORDER = Comparator.<ThreadRow>comparingInt(r -> r.state().priority)
			.thenComparing(r -> r.activityMs() == null ? Long.MIN_VALUE : r.activityMs(), Comparator.reverseOrder())
			.thenComparing(ThreadRow::settled)
			.thenComparing(r -> r.title().toLowerCase(java.util.Locale.ROOT))
			.thenComparing(ThreadRow::key);

	private List<ThreadRow> all = List.of();
	private List<ThreadRow> visible = List.of();
	private List<Env> envs = List.of();
	private @Nullable String filter;
	private @Nullable String selectedKey;

	/** Replaces the rows (any order) and the environments (id → {label, status, detail}). */
	public void update(List<ThreadRow> rows, Map<String, String[]> environments) {
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
		for (Map.Entry<String, String[]> e : environments.entrySet()) {
			String[] v = e.getValue();
			int[] c = counts.getOrDefault(e.getKey(), new int[2]);
			out.add(new Env(e.getKey(), v.length > 0 ? v[0] : e.getKey(), v.length > 1 ? v[1] : "", c[0], c[1]));
			labels.remove(e.getKey());
		}
		// Environments that have rows but no status entry (bridge mode before its status arrives).
		labels.forEach((id, label) -> {
			int[] c = counts.get(id);
			out.add(new Env(id, label, "", c[0], c[1]));
		});
		envs = List.copyOf(out);
		if (filter != null && envs.stream().noneMatch(e -> e.id().equals(filter))) {
			filter = null;
		}
		refilter();
	}

	private void refilter() {
		visible = filter == null ? all : all.stream().filter(r -> filter.equals(r.env())).toList();
	}

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

	public @Nullable String filter() {
		return filter;
	}

	public @Nullable Env filterEnv() {
		return filter == null ? null : envs.stream().filter(e -> e.id().equals(filter)).findFirst().orElse(null);
	}

	/** {@code null} shows every environment. Unknown ids are ignored. */
	public void setFilter(@Nullable String envId) {
		if (envId != null && envs.stream().noneMatch(e -> e.id().equals(envId))) {
			return;
		}
		filter = envId;
		refilter();
	}

	/** All → first env → … → last env → All ({@code dir} = ±1). */
	public void cycleFilter(int dir) {
		List<@Nullable String> choices = new ArrayList<>();
		choices.add(null);
		envs.forEach(e -> choices.add(e.id()));
		int i = choices.indexOf(filter);
		int n = choices.size();
		setFilter(choices.get(Math.floorMod(i + (dir >= 0 ? 1 : -1), n)));
	}

	public int attention() {
		return (int) all.stream().filter(ThreadRow::needsAttention).count();
	}

	public @Nullable String selectedKey() {
		return selectedKey;
	}

	/** The selected row if it is visible under the current filter. */
	public @Nullable ThreadRow selected() {
		if (selectedKey == null) {
			return null;
		}
		for (ThreadRow r : visible) {
			if (r.key().equals(selectedKey)) {
				return r;
			}
		}
		return null;
	}

	/** Index of the selection in {@link #visible()}, or -1. */
	public int selectedIndex() {
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
