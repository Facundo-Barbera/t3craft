package dev.t3craft.hq;

import dev.t3craft.t3.NpcView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the HQ is generated from: the roster of threads, grouped by project. Normalised on
 * construction: projects sorted by key, thread ids sorted and de-duplicated, so the order the caller
 * lists things in never changes the plan (idempotence).
 *
 * <p>Active threads get a desk in one of their project's modules; settled threads get no desk, their
 * computer goes onto the "Archived" shelf in the hub.
 *
 * @param projects projects sorted by {@link Project#key()}, keys unique
 */
public record HqInput(List<Project> projects) {
	/**
	 * One roster line: a thread of a project in an environment.
	 *
	 * @param env          environment key (e.g. {@code vps}); picks the colour of the module's runner
	 * @param envLabel     environment name shown on the module's sign
	 * @param project      project id inside the environment
	 * @param projectLabel project name shown on the module's sign
	 * @param threadId     thread id, unique across the roster (anchor names use it)
	 * @param settled      true when the thread is done: it frees its desk and its computer is archived
	 */
	public record Entry(String env, String envLabel, String project, String projectLabel, String threadId, boolean settled) {
		public Entry {
			Objects.requireNonNull(env, "env");
			Objects.requireNonNull(project, "project");
			Objects.requireNonNull(threadId, "threadId");
		}

		/** The project key used by the HQ: {@code env:project} (same as {@code NpcView.groupId()}). */
		public String projectKey() {
			return env + ":" + project;
		}
	}

	/**
	 * @param key      stable project key, used in anchor names (direct mode: {@code NpcView.groupId()})
	 * @param label    shown on the module's sign
	 * @param envKey   environment key: stable runner colour per environment ("" = none)
	 * @param envLabel the environment it lives in, shown on the sign ("" = none)
	 * @param threads  active thread ids (direct mode: {@code NpcView.id()})
	 * @param settled  settled thread ids (archived, no desk)
	 */
	public record Project(String key, String label, String envKey, String envLabel, List<String> threads, List<String> settled) {
		public Project {
			Objects.requireNonNull(key, "key");
			label = label == null || label.isBlank() ? key : label;
			envKey = envKey == null ? "" : envKey;
			envLabel = envLabel == null ? "" : envLabel;
			TreeSet<String> active = new TreeSet<>(threads);
			TreeSet<String> done = new TreeSet<>(settled);
			done.removeAll(active);
			threads = List.copyOf(active);
			settled = List.copyOf(done);
		}

		public Project(String key, String label, String envKey, String envLabel, List<String> threads) {
			this(key, label, envKey, envLabel, threads, List.of());
		}
	}

	public HqInput {
		TreeMap<String, Project> byKey = new TreeMap<>();
		for (Project p : projects) {
			Project old = byKey.get(p.key());
			if (old != null) {
				List<String> t = new ArrayList<>(old.threads());
				t.addAll(p.threads());
				List<String> s = new ArrayList<>(old.settled());
				s.addAll(p.settled());
				p = new Project(p.key(), old.label(), old.envKey(), old.envLabel(), t, s);
			}
			byKey.put(p.key(), p);
		}
		projects = List.copyOf(byKey.values());
		TreeMap<String, String> owner = new TreeMap<>();
		for (Project p : projects) {
			for (List<String> ids : List.of(p.threads(), p.settled())) {
				for (String t : ids) {
					String o = owner.put(t, p.key());
					if (o != null) {
						throw new IllegalArgumentException("thread " + t + " is in two projects: " + o + " and " + p.key());
					}
				}
			}
		}
	}

	public static final HqInput EMPTY = new HqInput(List.of());

	/** Active threads (the ones with a desk). */
	public int threadCount() {
		int n = 0;
		for (Project p : projects) {
			n += p.threads().size();
		}
		return n;
	}

	public @org.jspecify.annotations.Nullable Project project(String key) {
		for (Project p : projects) {
			if (p.key().equals(key)) {
				return p;
			}
		}
		return null;
	}

	/** The roster in any order: same set of lines, same input. */
	public static HqInput fromRoster(Collection<Entry> roster) {
		Builder b = new Builder();
		for (Entry e : roster) {
			b.thread(e.projectKey(), e.projectLabel(), e.env(), e.envLabel(), e.threadId(), e.settled());
		}
		return b.build();
	}

	/** Groups NPC views by neighbourhood ({@code groupId} = environment + project). */
	public static HqInput fromNpcs(Collection<NpcView> npcs) {
		Builder b = new Builder();
		for (NpcView v : npcs) {
			b.thread(v.groupId(), v.groupLabel(), v.env(), v.envLabel(), v.id(), v.settled());
		}
		return b.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Accumulates threads per project. Labels: the smallest label seen wins (so the order of the
	 * roster never matters). A thread id listed in two projects goes to the smallest project key;
	 * it counts as active if any of its lines there says it is active.
	 */
	public static final class Builder {
		private final TreeMap<String, String[]> labels = new TreeMap<>();
		private final Map<String, String> projectOf = new HashMap<>();
		private final Map<String, Boolean> settledOf = new HashMap<>();
		private final TreeMap<String, List<String>> order = new TreeMap<>();

		public Builder project(String key, String label, String envKey, String envLabel) {
			String[] next = {label == null ? "" : label, envKey == null ? "" : envKey, envLabel == null ? "" : envLabel};
			labels.merge(key, next, (a, b) -> Arrays3.min(a, b));
			order.computeIfAbsent(key, k -> new ArrayList<>());
			return this;
		}

		public Builder thread(String projectKey, String projectLabel, String envKey, String envLabel, String threadId, boolean settled) {
			project(projectKey, projectLabel, envKey, envLabel);
			String owner = projectOf.get(threadId);
			if (owner == null || projectKey.compareTo(owner) < 0) {
				if (owner != null) {
					order.get(owner).remove(threadId);
				}
				projectOf.put(threadId, projectKey);
				settledOf.put(threadId, settled);
				order.get(projectKey).add(threadId);
			} else if (owner.equals(projectKey) && !settled) {
				settledOf.put(threadId, false);
			}
			return this;
		}

		/** An active thread. */
		public Builder thread(String projectKey, String projectLabel, String envKey, String envLabel, String threadId) {
			return thread(projectKey, projectLabel, envKey, envLabel, threadId, false);
		}

		public HqInput build() {
			List<Project> out = new ArrayList<>();
			for (var e : order.entrySet()) {
				String[] l = labels.get(e.getKey());
				List<String> active = new ArrayList<>();
				List<String> settled = new ArrayList<>();
				for (String t : e.getValue()) {
					(settledOf.get(t) ? settled : active).add(t);
				}
				out.add(new Project(e.getKey(), l[0], l[1], l[2], active, settled));
			}
			return new HqInput(out);
		}
	}

	private static final class Arrays3 {
		static String[] min(String[] a, String[] b) {
			return java.util.Arrays.compare(a, b) <= 0 ? a : b;
		}
	}
}
