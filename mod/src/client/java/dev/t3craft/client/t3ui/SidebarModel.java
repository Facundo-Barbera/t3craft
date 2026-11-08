package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.RowLineage;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.t3.Lineage;
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
 * <p>feat/thread-lineage: delegated tasks (T3 {@code delegate_task} children, {@link
 * dev.t3craft.client.chat.RowLineage}) nest under their parent like in T3 Code: right after it,
 * one level deeper ({@link Nest#depth()}), in the parent's place (folder, Pinned, Working…). The
 * parent row carries the count ({@link Nest#children()}) and folds its children
 * ({@link #toggleChildren}). A child goes to the root of its own place, with its parent's title
 * ({@link Nest#parentTitle()}), when the parent is not listed here (settled, another project under
 * the filter, another environment, not matching the search), or when the child itself is pinned or
 * settled. Provider subagents are never rows.
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
	public record Item(Kind kind, String key, String label, @Nullable ThreadRow row, int count, int attention, boolean collapsed, String envs,
			Nest nest) {
		public Item {
			nest = nest == null ? Nest.NONE : nest;
		}

		/** Shape before {@code nest} (feat/thread-lineage). */
		public Item(Kind kind, String key, String label, @Nullable ThreadRow row, int count, int attention, boolean collapsed, String envs) {
			this(kind, key, label, row, count, attention, collapsed, envs, Nest.NONE);
		}
	}

	/**
	 * feat/thread-lineage: a thread row's place in its family.
	 *
	 * @param depth       0 for a root row, 1 for a delegated child nested under it, …
	 * @param children    delegated children nested under this row (the "N agents" counter)
	 * @param working     of those children (and theirs), how many run or need the user
	 * @param collapsed   the children are folded away
	 * @param last        last child of its parent (the guide line stops at the middle)
	 * @param guides      bit {@code L-1} set: the guide line of ancestor level {@code L} (1 ≤ L < depth)
	 *                    runs on past this row
	 * @param parentTitle a root row of a delegated task whose parent is not listed here: the parent's
	 *                    title ("" when unknown); null otherwise
	 * @param delegated   the row is a delegated task
	 */
	public record Nest(int depth, int children, int working, boolean collapsed, boolean last, int guides, @Nullable String parentTitle,
			boolean delegated) {
		public static final Nest NONE = new Nest(0, 0, 0, false, false, 0, null, false);

		/** Orphaned delegated task drawn at the root with "↳ parent: …". */
		public boolean orphan() {
			return parentTitle != null;
		}

		public boolean guide(int level) {
			return level >= 1 && level < 32 && (guides & (1 << (level - 1))) != 0;
		}
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
	/** Parents whose delegated children are folded (open by default). */
	private final Set<String> kidsCollapsed = new HashSet<>();
	/** Parent key → delegated children among {@link #all}, built on first use after each update. */
	private @Nullable Map<String, List<ThreadRow>> childIndex;
	/** Child key → key of the row it is nested under, as of the last build. */
	private Map<String, String> nestedUnder = Map.of();

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

	// ---- lineage (feat/thread-lineage) ---------------------------------------------------------

	/** The children of {@code parentKey} are folded (no effect while searching: all open then). */
	public boolean childrenCollapsed(String parentKey) {
		return kidsCollapsed.contains(parentKey);
	}

	/** Folds or unfolds the delegated children nested under {@code parentKey}. */
	public void toggleChildren(String parentKey) {
		if (!kidsCollapsed.remove(parentKey)) {
			kidsCollapsed.add(parentKey);
		}
		rebuild();
	}

	/** Key of the row {@code key} is nested under in the current list, or null (root row). */
	public @Nullable String nestedUnder(String key) {
		return nestedUnder.get(key);
	}

	/**
	 * Delegated children of {@code parentKey} among every listed row (search and filter ignored),
	 * in sidebar order, for the thread header. Empty when it has none. Indexed once per update.
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
		if (selected() == null) {
			query = "";
			project = null;
		}
		// feat/thread-lineage: a nested child shows inside its parent's place, unfolded
		rebuild();
		for (int i = 0; i < Lineage.MAX_DEPTH; i++) {
			String up = nestedUnder.get(r.key());
			ThreadRow p = up == null ? null : find(up);
			if (p == null) {
				break;
			}
			kidsCollapsed.remove(p.key());
			r = p;
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

		long now = System.currentTimeMillis();
		Family fam = family(matching, now);
		nestedUnder = fam.under();

		List<Item> out = new ArrayList<>();
		List<ThreadRow> listed = new ArrayList<>();
		List<ThreadRow> pinned = matching.stream().filter(r -> r.pinned() && !fam.under().containsKey(r.key())).sorted(THREAD_ORDER).toList();
		if (!pinned.isEmpty()) {
			boolean closed = !search && collapsed.contains(PINNED);
			List<ThreadRow> famRows = withKids(pinned, fam);
			out.add(new Item(Kind.SECTION, PINNED, "Pinned", null, famRows.size(), attention(famRows), closed, ""));
			emitAll(pinned, fam, out, listed, closed, search);
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
			if (r.pinned() || fam.under().containsKey(r.key())) {
				continue; // pinned roots are listed above; nested children follow their parent
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
			List<ThreadRow> famRows = withKids(rs, fam);
			out.add(new Item(Kind.PROJECT, e.getKey(), projectLabel(rs.getFirst()), null, famRows.size(), attention(famRows), closed,
					String.join(", ", envLabels(famRows))));
			emitAll(rs, fam, out, listed, closed, search);
		}
		List<Dock> docks = new ArrayList<>();
		dockSection(docks, listed, fam, search, WORKING, "Working", work, !search && collapsed.contains(WORKING));
		dockSection(docks, listed, fam, search, SNOOZED, "Snoozed", snoozed, !search && !snoozedOpen);
		dockSection(docks, listed, fam, search, SETTLED, "Settled", settled, !search && !settledOpen);
		dock = List.copyOf(docks);
		items = List.copyOf(out);
		threads = List.copyOf(listed);
	}

	private void dockSection(List<Dock> docks, List<ThreadRow> listed, Family fam, boolean search, String key, String label, List<ThreadRow> rows,
			boolean closed) {
		if (rows.isEmpty()) {
			return;
		}
		List<ThreadRow> sorted = new ArrayList<>(rows);
		sorted.sort(THREAD_ORDER);
		List<Item> its = new ArrayList<>();
		emitAll(sorted, fam, its, listed, closed, search);
		List<ThreadRow> famRows = withKids(sorted, fam);
		docks.add(new Dock(key, label, famRows.size(), attention(famRows), closed, List.copyOf(its)));
	}

	private static Item threadItem(ThreadRow r) {
		return threadItem(r, Nest.NONE);
	}

	private static Item threadItem(ThreadRow r, Nest nest) {
		return new Item(Kind.THREAD, r.key(), r.title(), r, 0, r.needsAttention() ? 1 : 0, false, r.envLabel(), nest);
	}

	// ---- nesting (feat/thread-lineage) ----------------------------------------------------------

	/** Who is nested under whom in one build: child key → parent key, parent key → children (sorted). */
	private record Family(Map<String, String> under, Map<String, List<ThreadRow>> kids) {
	}

	/** Where a row goes on its own: Pinned, Working, Snoozed, Settled or its project folder. */
	private static String place(ThreadRow r, long now) {
		if (r.pinned()) {
			return PINNED;
		}
		if (working(r)) {
			return WORKING;
		}
		if (r.details().snoozed(now)) {
			return SNOOZED;
		}
		if (r.settled()) {
			return SETTLED;
		}
		return projectKey(r);
	}

	/**
	 * A delegated child nests under its parent when the parent is among {@code rows} (listed after
	 * search and filter) and neither is in Settled and the child is not pinned (pinned rows stay in
	 * Pinned). Cycles in bad data are cut: a row never nests under its own descendant.
	 */
	private static Family family(List<ThreadRow> rows, long now) {
		Map<String, ThreadRow> byKey = new HashMap<>();
		for (ThreadRow r : rows) {
			byKey.put(r.key(), r);
		}
		Map<String, String> under = new HashMap<>();
		for (ThreadRow r : rows) {
			RowLineage l = r.lineage();
			if (!l.delegated() || l.parentKey() == null || l.parentKey().equals(r.key()) || r.pinned() || SETTLED.equals(place(r, now))) {
				continue;
			}
			ThreadRow p = byKey.get(l.parentKey());
			if (p == null || SETTLED.equals(place(p, now))) {
				continue;
			}
			under.put(r.key(), p.key());
		}
		// cut cycles: walk each chain, drop the link that closes a loop
		for (String k : new ArrayList<>(under.keySet())) {
			Set<String> seen = new HashSet<>();
			String cur = k;
			while (cur != null && under.containsKey(cur)) {
				if (!seen.add(cur) || seen.size() > Lineage.MAX_DEPTH) {
					under.remove(cur);
					break;
				}
				cur = under.get(cur);
			}
		}
		Map<String, List<ThreadRow>> kids = new HashMap<>();
		for (Map.Entry<String, String> e : under.entrySet()) {
			kids.computeIfAbsent(e.getValue(), x -> new ArrayList<>()).add(byKey.get(e.getKey()));
		}
		kids.values().forEach(l -> l.sort(THREAD_ORDER));
		return new Family(Map.copyOf(under), Map.copyOf(kids));
	}

	/** {@code roots} plus all their nested descendants (counts and attention of a section). */
	private static List<ThreadRow> withKids(List<ThreadRow> roots, Family fam) {
		if (fam.kids().isEmpty()) {
			return roots;
		}
		List<ThreadRow> out = new ArrayList<>(roots.size());
		for (ThreadRow r : roots) {
			addTree(r, fam, out, 0);
		}
		return out;
	}

	private static void addTree(ThreadRow r, Family fam, List<ThreadRow> out, int depth) {
		out.add(r);
		if (depth >= Lineage.MAX_DEPTH) {
			return;
		}
		for (ThreadRow k : fam.kids().getOrDefault(r.key(), List.of())) {
			addTree(k, fam, out, depth + 1);
		}
	}

	private void emitAll(List<ThreadRow> roots, Family fam, List<Item> out, List<ThreadRow> listed, boolean closed, boolean search) {
		for (ThreadRow r : roots) {
			emit(r, fam, out, listed, closed, search, 0, 0, false);
		}
	}

	/** One row and (unless folded) its nested children, depth first. Hidden rows still go to {@code listed}. */
	private void emit(ThreadRow r, Family fam, List<Item> out, List<ThreadRow> listed, boolean closed, boolean search, int depth, int guides,
			boolean last) {
		listed.add(r);
		List<ThreadRow> ks = depth >= Lineage.MAX_DEPTH ? List.of() : fam.kids().getOrDefault(r.key(), List.of());
		boolean folded = !search && !ks.isEmpty() && kidsCollapsed.contains(r.key());
		if (!closed) {
			String orphan = null;
			if (depth == 0 && r.lineage().delegated() && r.lineage().hasParent()) {
				orphan = r.lineage().parentTitle();
				if (orphan.isBlank() && r.lineage().parentKey() != null) {
					ThreadRow p = find(r.lineage().parentKey());
					orphan = p == null ? "" : p.title();
				}
			}
			int busy = 0;
			if (!ks.isEmpty()) {
				for (ThreadRow d : withKids(ks, fam)) {
					if (working(d) || d.needsAttention()) {
						busy++;
					}
				}
			}
			out.add(threadItem(r, new Nest(depth, ks.size(), busy, folded, last, guides, orphan, r.lineage().delegated())));
		}
		int childGuides = depth >= 1 && !last && depth <= 31 ? guides | (1 << (depth - 1)) : guides;
		for (int i = 0; i < ks.size(); i++) {
			emit(ks.get(i), fam, out, listed, closed || folded, search, depth + 1, childGuides, i == ks.size() - 1);
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
