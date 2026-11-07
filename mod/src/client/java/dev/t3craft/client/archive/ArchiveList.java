package dev.t3craft.client.archive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The archive screen's list state: search text, environment and project filters, and the page.
 * Pure (no Minecraft classes), so the terminal's screen and the GUI-only screen share one tested
 * model. The entries come from {@link ArchiveStore#entries()} (newest close first); filtering keeps
 * that order.
 *
 * <ul>
 *   <li>Search: every whitespace-separated word must appear (case-insensitive) in the title, the
 *   project, the environment label or id, or the thread id.</li>
 *   <li>Environment filter: an environment id, or null for all.</li>
 *   <li>Project filter: a project key ({@code <env>:<projectId>}, {@link ArchiveEntry#projectKey()}),
 *   or null for all. Cycling only offers the projects of the filtered environment.</li>
 *   <li>Changing the search or a filter goes back to the first page; the page is clamped to the
 *   pages there are.</li>
 * </ul>
 */
public final class ArchiveList {
	/** A filter choice: the key filtered on and what the chip shows. */
	public record Option(String key, String label) {
	}

	/** One page of the filtered list. {@code index} is clamped to {@code [0, pages - 1]}; {@code pages >= 1}. */
	public record Page(List<ArchiveEntry> rows, int index, int pages, int total) {
	}

	private String text = "";
	private @Nullable String env;
	private @Nullable String project;
	private int page;

	public String text() {
		return text;
	}

	public @Nullable String env() {
		return env;
	}

	public @Nullable String project() {
		return project;
	}

	public void setText(@Nullable String t) {
		String v = t == null ? "" : t;
		if (!v.equals(text)) {
			text = v;
			page = 0;
		}
	}

	/** Filters on environment {@code id} (null = all); drops a project filter of another environment. */
	public void setEnv(@Nullable String id) {
		if (!Objects.equals(env, id)) {
			env = id;
			if (project != null && id != null && !project.startsWith(id + ":")) {
				project = null;
			}
			page = 0;
		}
	}

	public void setProject(@Nullable String key) {
		if (!Objects.equals(project, key)) {
			project = key;
			page = 0;
		}
	}

	/** All → first environment → … → last → all. */
	public void cycleEnv(List<ArchiveEntry> all) {
		setEnv(cycle(envs(all), env));
	}

	/** All → first project (of the filtered environment) → … → all. */
	public void cycleProject(List<ArchiveEntry> all) {
		setProject(cycle(projects(all, env), project));
	}

	public void step(int dir) {
		page = Math.max(0, page + dir);
	}

	public int pageIndex() {
		return page;
	}

	public boolean matches(ArchiveEntry e) {
		if (env != null && !env.equals(e.env())) {
			return false;
		}
		if (project != null && !project.equals(e.projectKey())) {
			return false;
		}
		String q = text.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return true;
		}
		String hay = (e.title() + "\n" + e.projectLabel() + "\n" + e.envLabel() + "\n" + e.env() + "\n" + e.threadId()).toLowerCase(Locale.ROOT);
		for (String word : q.split("\\s+")) {
			if (!hay.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** The entries that pass the search and filters, in their given order. */
	public List<ArchiveEntry> filter(List<ArchiveEntry> all) {
		List<ArchiveEntry> out = new ArrayList<>();
		for (ArchiveEntry e : all) {
			if (matches(e)) {
				out.add(e);
			}
		}
		return out;
	}

	/** The current page of the filtered list ({@code size} rows per page); clamps the stored page. */
	public Page page(List<ArchiveEntry> all, int size) {
		List<ArchiveEntry> rows = filter(all);
		int per = Math.max(1, size);
		int pages = Math.max(1, (rows.size() + per - 1) / per);
		page = Math.max(0, Math.min(page, pages - 1));
		int from = page * per;
		return new Page(List.copyOf(rows.subList(from, Math.min(rows.size(), from + per))), page, pages, rows.size());
	}

	/** Environments present in {@code all}, by label. */
	public static List<Option> envs(List<ArchiveEntry> all) {
		Map<String, String> m = new TreeMap<>();
		for (ArchiveEntry e : all) {
			m.putIfAbsent(e.env(), e.envLabel());
		}
		return sorted(m);
	}

	/** Projects present in {@code all} (only those of {@code env} when not null), by label. */
	public static List<Option> projects(List<ArchiveEntry> all, @Nullable String env) {
		Map<String, String> m = new TreeMap<>();
		for (ArchiveEntry e : all) {
			if (env == null || env.equals(e.env())) {
				m.putIfAbsent(e.projectKey(), e.projectLabel());
			}
		}
		return sorted(m);
	}

	private static List<Option> sorted(Map<String, String> m) {
		List<Option> out = new ArrayList<>();
		m.forEach((k, v) -> out.add(new Option(k, v)));
		out.sort(java.util.Comparator.comparing((Option o) -> o.label().toLowerCase(Locale.ROOT)).thenComparing(Option::key));
		return out;
	}

	/** Next choice after {@code current}: null → first → … → last → null (unknown current → null). */
	static @Nullable String cycle(List<Option> options, @Nullable String current) {
		if (options.isEmpty()) {
			return null;
		}
		if (current == null) {
			return options.getFirst().key();
		}
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).key().equals(current)) {
				return i + 1 < options.size() ? options.get(i + 1).key() : null;
			}
		}
		return null;
	}

	/** Label of {@code key} among {@code options}, or null. */
	public static @Nullable String label(List<Option> options, @Nullable String key) {
		for (Option o : options) {
			if (o.key().equals(key)) {
				return o.label();
			}
		}
		return null;
	}
}
