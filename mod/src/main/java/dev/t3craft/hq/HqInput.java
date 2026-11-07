package dev.t3craft.hq;

import dev.t3craft.t3.NpcView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the HQ is generated from: the projects (one wing each) and the visible threads of each
 * (one desk each). Normalised on construction: projects sorted by key, thread ids sorted and
 * de-duplicated, so the order the caller lists things in never changes the plan.
 *
 * @param projects projects sorted by {@link Project#key()}, keys unique
 */
public record HqInput(List<Project> projects) {
	/**
	 * @param key      stable project key, used in anchor names (direct mode: {@code NpcView.groupId()})
	 * @param label    shown on the wing's sign
	 * @param envLabel the environment it lives in, shown on the sign's second line ("" = none)
	 * @param threads  stable thread ids, used in anchor names (direct mode: {@code NpcView.id()})
	 */
	public record Project(String key, String label, String envLabel, List<String> threads) {
		public Project {
			Objects.requireNonNull(key, "key");
			label = label == null || label.isBlank() ? key : label;
			envLabel = envLabel == null ? "" : envLabel;
			threads = List.copyOf(new TreeSet<>(threads));
		}
	}

	public HqInput {
		TreeMap<String, Project> byKey = new TreeMap<>();
		for (Project p : projects) {
			Project old = byKey.get(p.key());
			if (old != null) {
				List<String> merged = new ArrayList<>(old.threads());
				merged.addAll(p.threads());
				p = new Project(p.key(), old.label(), old.envLabel(), merged);
			}
			byKey.put(p.key(), p);
		}
		projects = List.copyOf(byKey.values());
		TreeMap<String, String> owner = new TreeMap<>();
		for (Project p : projects) {
			for (String t : p.threads()) {
				String o = owner.put(t, p.key());
				if (o != null) {
					throw new IllegalArgumentException("thread " + t + " is in two projects: " + o + " and " + p.key());
				}
			}
		}
	}

	public static final HqInput EMPTY = new HqInput(List.of());

	public int threadCount() {
		int n = 0;
		for (Project p : projects) {
			n += p.threads().size();
		}
		return n;
	}

	/** Groups NPC views by neighbourhood ({@code groupId} = environment + project). */
	public static HqInput fromNpcs(Collection<NpcView> npcs) {
		Builder b = new Builder();
		for (NpcView v : npcs) {
			b.thread(v.groupId(), v.groupLabel(), v.envLabel(), v.id());
		}
		return b.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Accumulates threads per project; first label seen for a project wins. */
	public static final class Builder {
		private final TreeMap<String, String[]> labels = new TreeMap<>();
		private final TreeMap<String, List<String>> threads = new TreeMap<>();
		private final java.util.HashSet<String> seen = new java.util.HashSet<>();

		public Builder project(String key, String label, String envLabel) {
			labels.putIfAbsent(key, new String[] {label, envLabel});
			threads.computeIfAbsent(key, k -> new ArrayList<>());
			return this;
		}

		/** Adds a thread; a thread id already given (in any project) is ignored. */
		public Builder thread(String projectKey, String projectLabel, String envLabel, String threadId) {
			project(projectKey, projectLabel, envLabel);
			if (seen.add(threadId)) {
				threads.get(projectKey).add(threadId);
			}
			return this;
		}

		public HqInput build() {
			List<Project> out = new ArrayList<>();
			for (var e : threads.entrySet()) {
				String[] l = labels.get(e.getKey());
				out.add(new Project(e.getKey(), l[0], l[1], e.getValue()));
			}
			return new HqInput(out);
		}
	}
}
