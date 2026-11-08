package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ThreadRow;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Model of the T3 sidebar of the unified screen: thread search, project filter, grouping and order.
 * Pure Java (tested), client thread only.
 *
 * <p>Structure follows T3 Code's sidebar ({@code apps/web/src/components/Sidebar.tsx},
 * {@code Sidebar.logic.ts}): an optional <b>Pinned</b> section first, then one folder per project
 * ({@code groupSidebarThreadsByProject}). A project is <i>logical</i>: the same project name on two
 * environments is one folder (T3's {@code buildProjectGroups}); the environments show as a detail
 * of the folder and of each row, never as a filter. Folders sort by their most recent thread
 * ({@code sortProjectsForSidebar}, "updated_at"), ties by name; threads inside by most recent
 * activity, then urgency, title and key. Search ({@code searchSidebarThreads} /
 * {@code pullRequestMatchesSearchQuery}) folds case and accents and keeps rows whose title, project
 * or environment contain every word of the query; while searching every folder is open.
 *
 * <p>fix/t3-sidebar-all-threads: the rows are every listable thread (not the NPC selection). Pinned
 * threads always go to Pinned, whatever their age or settled state; other settled threads go to a
 * last <b>Settled</b> section, closed until the user opens it, with its count.
 *
 * <p>The selection follows the thread by key when the list reorders; {@link #move} walks thread
 * rows only (headers are skipped) and never enters a collapsed folder.
 */
public final class SidebarModel {
	/** Folder key of threads without a project. */
	public static final String NO_PROJECT = "";
	/** Key of the Pinned section header. */
	public static final String PINNED = "\u0000pinned";
	/** Key of the Settled section header (collapsed by default, as in T3 Code). */
	public static final String SETTLED = "\u0000settled";

	public enum Kind {
		/** "Pinned" or "Settled" section header. */
		SECTION,
		/** A project folder header. */
		PROJECT,
		/** A thread row. */
		THREAD
	}

	/**
	 * One drawn sidebar line.
	 *
	 * @param key       thread key, project key or {@link #PINNED}
	 * @param label     thread title or project name
	 * @param row       the thread (THREAD only)
	 * @param count     threads in the folder after search (headers)
	 * @param attention urgent threads in the folder (headers)
	 * @param collapsed folder closed (headers)
	 * @param envs      environment labels of the folder (PROJECT) or of the row (THREAD)
	 */
	public record Item(Kind kind, String key, String label, @Nullable ThreadRow row, int count, int attention, boolean collapsed, String envs) {
	}

	/** A project filter choice (every project with threads, search ignored). */
	public record Project(String key, String label, int threads, int attention, List<String> envLabels) {
	}

	private List<ThreadRow> all = List.of();
	private String query = "";
	private @Nullable String project;
	private final Set<String> collapsed = new HashSet<>();
	/** The Settled section is closed by default: this is its "opened" flag. */
	private boolean settledOpen;
	private @Nullable String selectedKey;

	private List<Item> items = List.of();
	private List<ThreadRow> threads = List.of();
	private List<Project> projects = List.of();

	// ---- data ---------------------------------------------------------------------------------

	/** Replaces the rows (any order). Drops a project filter whose project is gone. */
	public void update(List<ThreadRow> rows) {
		all = List.copyOf(rows);
		rebuild();
		if (project != null && projects.stream().noneMatch(p -> p.key().equals(project))) {
			project = null;
			rebuild();
		}
	}

	public List<ThreadRow> all() {
		return all;
	}

	/** Drawn lines, top to bottom. */
	public List<Item> items() {
		return items;
	}

	/** Thread rows after search and project filter, in drawn order (collapsed folders included). */
	public List<ThreadRow> threads() {
		return threads;
	}

	/** Project filter choices, in folder order. */
	public List<Project> projects() {
		return projects;
	}

	// ---- search and filter --------------------------------------------------------------------

	public String query() {
		return query;
	}

	public void setQuery(@Nullable String q) {
		String n = q == null ? "" : q;
		if (!n.equals(query)) {
			query = n;
			rebuild();
		}
	}

	public boolean searching() {
		return !fold(query).isBlank();
	}

	/** Project filter key (null = all projects). */
	public @Nullable String project() {
		return project;
	}

	public void setProject(@Nullable String key) {
		project = key == null || projects.stream().noneMatch(p -> p.key().equals(key)) ? null : key;
		rebuild();
	}

	/** Steps the project filter through "All" and each project (wraps). */
	public void cycleProject(int dir) {
		List<@Nullable String> choices = new ArrayList<>();
		choices.add(null);
		projects.forEach(p -> choices.add(p.key()));
		int i = Math.max(0, choices.indexOf(project));
		int n = choices.size();
		setProject(choices.get(((i + (dir >= 0 ? 1 : -1)) % n + n) % n));
	}

	/** Label of the current filter ("All projects" or the project name). */
	public String projectLabel() {
		if (project == null) {
			return "All projects";
		}
		for (Project p : projects) {
			if (p.key().equals(project)) {
				return p.label();
			}
		}
		return "All projects";
	}

	public boolean isCollapsed(String folderKey) {
		return SETTLED.equals(folderKey) ? !settledOpen : collapsed.contains(folderKey);
	}

	/** Opens or closes a folder (no effect while searching: every folder is open then). */
	public void toggleCollapsed(String folderKey) {
		if (SETTLED.equals(folderKey)) {
			settledOpen = !settledOpen;
		} else if (!collapsed.remove(folderKey)) {
			collapsed.add(folderKey);
		}
		rebuild();
	}

	// ---- selection ----------------------------------------------------------------------------

	public @Nullable String selectedKey() {
		return selectedKey;
	}

	/** Selects by key, even a thread that is not (yet) listed (opened from a block). */
	public void select(@Nullable String key) {
		selectedKey = key;
	}

	/**
	 * Selects {@code key} for an "open this thread" (a block click, a notification, the archive): when
	 * the thread is listed but hidden by the search or the project filter, both are cleared so it
	 * shows, and its folder is opened. Returns true when the thread is listed at all.
	 */
	public boolean focus(String key) {
		selectedKey = key;
		ThreadRow r = find(key);
		if (r == null) {
			return false;
		}
		if (selected() == null) {
			query = "";
			project = null;
		}
		if (r.pinned()) {
			collapsed.remove(PINNED);
		} else if (r.settled()) {
			settledOpen = true;
		} else {
			collapsed.remove(projectKey(r));
		}
		rebuild();
		return true;
	}

	/** The selected row if it is listed after search and filter. */
	public @Nullable ThreadRow selected() {
		for (ThreadRow r : threads) {
			if (r.key().equals(selectedKey)) {
				return r;
			}
		}
		return null;
	}

	/** Index into {@link #items()} of the selected thread, or -1. */
	public int selectedItem() {
		for (int i = 0; i < items.size(); i++) {
			Item it = items.get(i);
			if (it.kind() == Kind.THREAD && it.key().equals(selectedKey)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Moves the selection to the previous/next drawn thread row ({@code delta} rows, clamped). With
	 * nothing selected (or the selection hidden) down starts at the first thread, up at the last.
	 * Returns true when the selection changed.
	 */
	public boolean move(int delta) {
		List<String> keys = new ArrayList<>();
		for (Item it : items) {
			if (it.kind() == Kind.THREAD) {
				keys.add(it.key());
			}
		}
		if (keys.isEmpty() || delta == 0) {
			return false;
		}
		int i = keys.indexOf(selectedKey);
		int next = i < 0 ? (delta > 0 ? 0 : keys.size() - 1) : Math.max(0, Math.min(keys.size() - 1, i + delta));
		String k = keys.get(next);
		boolean changed = !Objects.equals(k, selectedKey);
		selectedKey = k;
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

	// ---- build --------------------------------------------------------------------------------

	/** T3's thread order inside a folder: most recent first, then urgency, title, key. */
	static final Comparator<ThreadRow> THREAD_ORDER = Comparator.<ThreadRow, Long>comparing(r -> r.activityMs() == null ? Long.MIN_VALUE : r.activityMs(),
			Comparator.reverseOrder())
			.thenComparingInt(r -> r.attention().ordinal())
			.thenComparing(ThreadRow::settled)
			.thenComparing(r -> r.title().toLowerCase(Locale.ROOT))
			.thenComparing(ThreadRow::key);

	/** Logical project key: the project name folded (same name on two environments = one folder). */
	public static String projectKey(ThreadRow r) {
		return r.project() == null || r.project().isBlank() ? NO_PROJECT : fold(r.project()).trim();
	}

	private static String projectLabel(ThreadRow r) {
		return r.project() == null || r.project().isBlank() ? "No project" : r.project();
	}

	private void rebuild() {
		// project choices from every row (the filter does not depend on the search)
		Map<String, List<ThreadRow>> byProject = group(all);
		List<Project> ps = new ArrayList<>();
		for (Map.Entry<String, List<ThreadRow>> e : byProject.entrySet()) {
			List<ThreadRow> rs = e.getValue();
			ps.add(new Project(e.getKey(), projectLabel(rs.getFirst()), rs.size(), (int) rs.stream().filter(ThreadRow::needsAttention).count(),
					envLabels(rs)));
		}
		projects = List.copyOf(ps);

		boolean search = searching();
		List<String> words = words(query);
		List<ThreadRow> matching = new ArrayList<>();
		for (ThreadRow r : all) {
			if (project != null && !project.equals(projectKey(r))) {
				continue;
			}
			if (search && !matches(r, words)) {
				continue;
			}
			matching.add(r);
		}

		List<Item> out = new ArrayList<>();
		List<ThreadRow> listed = new ArrayList<>();
		List<ThreadRow> pinned = matching.stream().filter(ThreadRow::pinned).sorted(THREAD_ORDER).toList();
		if (!pinned.isEmpty()) {
			boolean closed = !search && collapsed.contains(PINNED);
			out.add(new Item(Kind.SECTION, PINNED, "Pinned", null, pinned.size(), attention(pinned), closed, ""));
			for (ThreadRow r : pinned) {
				listed.add(r);
				if (!closed) {
					out.add(threadItem(r));
				}
			}
		}
		Map<String, List<ThreadRow>> folders = group(matching.stream().filter(r -> !r.pinned() && !r.settled()).toList());
		for (Map.Entry<String, List<ThreadRow>> e : folders.entrySet()) {
			List<ThreadRow> rs = e.getValue();
			boolean closed = !search && collapsed.contains(e.getKey());
			out.add(new Item(Kind.PROJECT, e.getKey(), projectLabel(rs.getFirst()), null, rs.size(), attention(rs), closed, String.join(", ", envLabels(rs))));
			for (ThreadRow r : rs) {
				listed.add(r);
				if (!closed) {
					out.add(threadItem(r));
				}
			}
		}
		List<ThreadRow> settled = matching.stream().filter(r -> !r.pinned() && r.settled()).sorted(THREAD_ORDER).toList();
		if (!settled.isEmpty()) {
			boolean closed = !search && !settledOpen;
			out.add(new Item(Kind.SECTION, SETTLED, "Settled", null, settled.size(), attention(settled), closed, String.join(", ", envLabels(settled))));
			for (ThreadRow r : settled) {
				listed.add(r);
				if (!closed) {
					out.add(threadItem(r));
				}
			}
		}
		items = List.copyOf(out);
		threads = List.copyOf(listed);
	}

	private static Item threadItem(ThreadRow r) {
		return new Item(Kind.THREAD, r.key(), r.title(), r, 0, r.needsAttention() ? 1 : 0, false, r.envLabel());
	}

	/** Folders in T3's project order: most recent thread first, ties by name; threads sorted inside. */
	private static Map<String, List<ThreadRow>> group(List<ThreadRow> rows) {
		Map<String, List<ThreadRow>> m = new LinkedHashMap<>();
		for (ThreadRow r : rows) {
			m.computeIfAbsent(projectKey(r), k -> new ArrayList<>()).add(r);
		}
		m.values().forEach(l -> l.sort(THREAD_ORDER));
		List<Map.Entry<String, List<ThreadRow>>> entries = new ArrayList<>(m.entrySet());
		entries.sort(Comparator.<Map.Entry<String, List<ThreadRow>>, Long>comparing(e -> latest(e.getValue()), Comparator.reverseOrder())
				.thenComparing(e -> e.getKey().equals(NO_PROJECT)) // "No project" after named ones on a tie
				.thenComparing(Map.Entry::getKey));
		Map<String, List<ThreadRow>> out = new LinkedHashMap<>();
		entries.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	private static long latest(List<ThreadRow> rows) {
		long best = Long.MIN_VALUE;
		for (ThreadRow r : rows) {
			if (r.activityMs() != null) {
				best = Math.max(best, r.activityMs());
			}
		}
		return best;
	}

	private static int attention(List<ThreadRow> rows) {
		return (int) rows.stream().filter(ThreadRow::needsAttention).count();
	}

	private static List<String> envLabels(List<ThreadRow> rows) {
		Set<String> s = new LinkedHashSet<>();
		rows.forEach(r -> s.add(r.envLabel() == null || r.envLabel().isBlank() ? r.env() : r.envLabel()));
		return List.copyOf(s);
	}

	// ---- search -------------------------------------------------------------------------------

	/** Lower case without accents (T3's {@code foldSearchQuery}). */
	public static String fold(@Nullable String s) {
		if (s == null) {
			return "";
		}
		String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return d.toLowerCase(Locale.ROOT);
	}

	static List<String> words(String query) {
		List<String> out = new ArrayList<>();
		for (String w : fold(query).trim().split("\\s+")) {
			if (!w.isEmpty()) {
				out.add(w);
			}
		}
		return out;
	}

	/** True when every word is in the row's title, project or environment. */
	public static boolean matches(ThreadRow r, List<String> words) {
		String hay = fold(r.title() + " " + r.project() + " " + r.envLabel() + " " + r.env());
		for (String w : words) {
			if (!hay.contains(w)) {
				return false;
			}
		}
		return true;
	}

	/** Most urgent attention among rows (folder dot, T3's {@code resolveSidebarFolderDot}). */
	public static @Nullable Attention folderDot(List<ThreadRow> rows) {
		Attention best = null;
		for (ThreadRow r : rows) {
			Attention a = r.attention();
			if (a == Attention.IDLE || a == Attention.DONE) {
				continue;
			}
			if (best == null || a.ordinal() < best.ordinal()) {
				best = a;
			}
		}
		return best;
	}
}
