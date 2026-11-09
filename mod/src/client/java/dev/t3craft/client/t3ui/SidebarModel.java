package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ThreadRow;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * <p>feat/t3-sidebar-rows: like T3 Code, the sidebar has a <b>dock</b> stuck to its bottom
 * ({@link #dock()}): <b>Working</b> (running threads that are not pinned, open by default),
 * <b>Snoozed</b> (snoozed until later, closed by default) and <b>Settled</b> (closed by default),
 * each with its count. {@link #items()} is only the scrolling part on top (Pinned + projects); a
 * thread is in exactly one place: pinned → Pinned (running or not); else running → Working; else
 * snoozed → Snoozed; else settled → Settled; else its project folder. A snooze that runs out moves
 * the thread back on the next {@link #expireSnoozes} (the view calls it every frame; it is a long
 * compare until then).
 *
 * <p>feat/ui-hq-menu-lineage: delegated tasks (T3 {@code delegate_task} children, {@link
 * dev.t3craft.client.chat.RowLineage}) are not sidebar rows (no nesting, no counter): the lineage
 * lives only in the thread header's agents menu ({@link #liveChildrenOf}), and a child opened from
 * there (or from a block, a link) is still found by {@link #find}. Provider subagents are never rows.
 *
 * <p>The selection follows the thread by key when the list reorders; {@link #move} walks thread
 * rows only (headers are skipped), top list first then the open dock sections, and never enters a
 * collapsed folder.
 */
public final class SidebarModel {
	/** Folder key of threads without a project. */
	public static final String NO_PROJECT = "";
	/** Key of the Pinned section header. */
	public static final String PINNED = "\u0000pinned";
	/** Key of the Settled section header (collapsed by default, as in T3 Code). */
	public static final String SETTLED = "\u0000settled";
	/** Key of the Working dock section (open by default). */
	public static final String WORKING = "\u0000working";
	/** Key of the Snoozed dock section (collapsed by default). */
	public static final String SNOOZED = "\u0000snoozed";

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

	/**
	 * A section of the bottom dock (Working, Snoozed, Settled): header with the count, and its thread
	 * rows when open ({@code rows} is empty while collapsed).
	 */
	public record Dock(String key, String label, int count, int attention, boolean collapsed, List<Item> rows) {
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
	/** Snoozed is closed by default too. */
	private boolean snoozedOpen;
	/** Earliest snooze end among the listed rows after the last build (0 = none). */
	private long nextWake;
	private @Nullable String selectedKey;
	/** Parent key → delegated children among {@link #all}, built on first use after each update. */
	private @Nullable Map<String, List<ThreadRow>> childIndex;

	private List<Item> items = List.of();
	private List<Dock> dock = List.of();
	private List<ThreadRow> threads = List.of();
	private List<Project> projects = List.of();

	// ---- data ---------------------------------------------------------------------------------

	/** Replaces the rows (any order). Drops a project filter whose project is gone. */
	public void update(List<ThreadRow> rows) {
		all = List.copyOf(rows);
		childIndex = null;
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

	/** Bottom dock sections (Working, Snoozed, Settled), only those with threads, top to bottom. */
	public List<Dock> dock() {
		return dock;
	}

	/** Rebuilds when a snooze ended since the last build (cheap otherwise). */
	public void expireSnoozes(long nowMs) {
		if (nextWake > 0 && nowMs >= nextWake) {
			rebuild();
		}
	}

	/** Running in T3 (spinner, Working section). */
	public static boolean working(ThreadRow r) {
		return r.state() == dev.t3craft.t3.ThreadState.WORKING;
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
		if (SETTLED.equals(folderKey)) {
			return !settledOpen;
		}
		if (SNOOZED.equals(folderKey)) {
			return !snoozedOpen;
		}
		return collapsed.contains(folderKey);
	}

	/** Opens or closes a folder (no effect while searching: every folder is open then). */
	public void toggleCollapsed(String folderKey) {
		if (SETTLED.equals(folderKey)) {
			settledOpen = !settledOpen;
		} else if (SNOOZED.equals(folderKey)) {
			snoozedOpen = !snoozedOpen;
		} else if (!collapsed.remove(folderKey)) {
			collapsed.add(folderKey);
		}
		rebuild();
	}

	// ---- lineage (feat/thread-lineage, feat/ui-hq-menu-lineage) --------------------------------

	/**
	 * A delegated task with a parent: not a sidebar row (feat/ui-hq-menu-lineage); it is reached from
	 * its parent's header agents menu.
	 */
	public static boolean hiddenDelegated(ThreadRow r) {
		return r.lineage().delegated() && r.lineage().hasParent();
	}

	/**
	 * A child the header agents menu lists: idle or active (working, waiting, needs input), never
	 * settled, done or ended in an error.
	 */
	public static boolean liveChild(ThreadRow r) {
		if (r.settled()) {
			return false;
		}
		return switch (r.state()) {
			case IDLE, WORKING, NEEDS_ATTENTION -> true;
			case DONE, ERROR -> false;
		};
	}

	/** {@link #childrenOf} filtered to {@link #liveChild}: the header's {@code N agents ▾} chip and menu. */
	public List<ThreadRow> liveChildrenOf(String parentKey) {
		List<ThreadRow> kids = childrenOf(parentKey);
		if (kids.isEmpty()) {
			return kids;
		}
		List<ThreadRow> out = new ArrayList<>(kids.size());
		for (ThreadRow r : kids) {
			if (liveChild(r)) {
				out.add(r);
			}
		}
		return out.size() == kids.size() ? kids : List.copyOf(out);
	}

	/**
	 * {@link #childrenOf} that are not {@link #liveChild}: done, settled or ended in an error (the
	 * agents menu's "Finished" section, below the live ones).
	 */
	public List<ThreadRow> finishedChildrenOf(String parentKey) {
		List<ThreadRow> kids = childrenOf(parentKey);
		if (kids.isEmpty()) {
			return kids;
		}
		List<ThreadRow> out = new ArrayList<>(kids.size());
		for (ThreadRow r : kids) {
			if (!liveChild(r)) {
				out.add(r);
			}
		}
		return List.copyOf(out);
	}

	/**
	 * Delegated children of {@code parentKey} among every row (search and filter ignored), in
	 * sidebar order, any state. Empty when it has none. Indexed once per update.
	 */
	public List<ThreadRow> childrenOf(String parentKey) {
		Map<String, List<ThreadRow>> idx = childIndex;
		if (idx == null) {
			Map<String, List<ThreadRow>> m = new HashMap<>();
			for (ThreadRow r : all) {
				String p = r.lineage().parentKey();
				if (r.lineage().delegated() && p != null && !p.equals(r.key())) {
					m.computeIfAbsent(p, k -> new ArrayList<>()).add(r);
				}
			}
			m.replaceAll((k, v) -> {
				v.sort(THREAD_ORDER);
				return List.copyOf(v);
			});
			childIndex = idx = m;
		}
		return idx.getOrDefault(parentKey, List.of());
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
		if (hiddenDelegated(r)) {
			return true; // not a sidebar row: leave the search, filter and folders as they are
		}
		if (selected() == null) {
			query = "";
			project = null;
		}
		long now = System.currentTimeMillis();
		if (r.pinned()) {
			collapsed.remove(PINNED);
		} else if (working(r)) {
			collapsed.remove(WORKING);
		} else if (r.details().snoozed(now)) {
			snoozedOpen = true;
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
		for (Dock d : dock) {
			for (Item it : d.rows()) {
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
		Map<String, List<ThreadRow>> byProject = group(all.stream().filter(r -> !hiddenDelegated(r)).toList());
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
			if (hiddenDelegated(r)) {
				continue; // feat/ui-hq-menu-lineage: delegated tasks live in the parent's header menu
			}
			matching.add(r);
		}

		long now = System.currentTimeMillis();
		List<Item> out = new ArrayList<>();
		List<ThreadRow> listed = new ArrayList<>();
		List<ThreadRow> pinned = matching.stream().filter(ThreadRow::pinned).sorted(THREAD_ORDER).toList();
		if (!pinned.isEmpty()) {
			boolean closed = !search && collapsed.contains(PINNED);
			out.add(new Item(Kind.SECTION, PINNED, "Pinned", null, pinned.size(), attention(pinned), closed, ""));
			emitAll(pinned, out, listed, closed);
		}
		long wake = 0;
		List<ThreadRow> work = new ArrayList<>();
		List<ThreadRow> snoozed = new ArrayList<>();
		List<ThreadRow> settled = new ArrayList<>();
		List<ThreadRow> active = new ArrayList<>();
		for (ThreadRow r : matching) {
			Long until = r.details().snoozedUntilMs();
			if (until != null && until > now && (wake == 0 || until < wake)) {
				wake = until;
			}
			if (r.pinned()) {
				continue; // listed above
			}
			if (working(r)) {
				work.add(r);
			} else if (r.details().snoozed(now)) {
				snoozed.add(r);
			} else if (r.settled()) {
				settled.add(r);
			} else {
				active.add(r);
			}
		}
		nextWake = wake;
		Map<String, List<ThreadRow>> folders = group(active);
		for (Map.Entry<String, List<ThreadRow>> e : folders.entrySet()) {
			List<ThreadRow> rs = e.getValue();
			boolean closed = !search && collapsed.contains(e.getKey());
			out.add(new Item(Kind.PROJECT, e.getKey(), projectLabel(rs.getFirst()), null, rs.size(), attention(rs), closed,
					String.join(", ", envLabels(rs))));
			emitAll(rs, out, listed, closed);
		}
		List<Dock> docks = new ArrayList<>();
		dockSection(docks, listed, WORKING, "Working", work, !search && collapsed.contains(WORKING));
		dockSection(docks, listed, SNOOZED, "Snoozed", snoozed, !search && !snoozedOpen);
		dockSection(docks, listed, SETTLED, "Settled", settled, !search && !settledOpen);
		dock = List.copyOf(docks);
		items = List.copyOf(out);
		threads = List.copyOf(listed);
	}

	private static void dockSection(List<Dock> docks, List<ThreadRow> listed, String key, String label, List<ThreadRow> rows, boolean closed) {
		if (rows.isEmpty()) {
			return;
		}
		List<ThreadRow> sorted = new ArrayList<>(rows);
		sorted.sort(THREAD_ORDER);
		List<Item> its = new ArrayList<>();
		emitAll(sorted, its, listed, closed);
		docks.add(new Dock(key, label, sorted.size(), attention(sorted), closed, List.copyOf(its)));
	}

	private static Item threadItem(ThreadRow r) {
		return new Item(Kind.THREAD, r.key(), r.title(), r, 0, r.needsAttention() ? 1 : 0, false, r.envLabel());
	}

	/** Rows of one place; hidden rows (closed folder) still go to {@code listed}. */
	private static void emitAll(List<ThreadRow> rows, List<Item> out, List<ThreadRow> listed, boolean closed) {
		for (ThreadRow r : rows) {
			listed.add(r);
			if (!closed) {
				out.add(threadItem(r));
			}
		}
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
