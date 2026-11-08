package dev.t3craft.t3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * feat/thread-lineage: the parent/child index of ONE environment's threads, from T3's
 * {@code lineage.parentThreadId} / {@code relationshipToParent} and {@code creationSource}. Pure and
 * immutable; built once per shell view ({@link #of(String, ShellState.View)} memoizes by view
 * identity, so callers on any thread can ask for it every tick for free).
 *
 * <p>Two kinds of child (see {@link ThreadShell#isDelegated()}):
 * <ul>
 *   <li><b>provider subagents</b> ({@code creationSource == "provider"}): short-lived, e.g. Claude's
 *   Agent tool. Shown inside their parent only (chat, helpers).</li>
 *   <li><b>delegated tasks</b> (any other {@code creationSource}, from T3's {@code delegate_task}):
 *   long, real work in their own worktree. Listed nested under their parent and given a full NPC.</li>
 * </ul>
 * Forks also have a parent; {@link #children} returns every child, {@link #delegatedChildren} only
 * the delegated ones. Ids are T3 thread ids (not NPC ids). Cycles (bad data) are cut at
 * {@link #MAX_DEPTH}.
 *
 * <p>Used by the sidebar (nesting), the thread header (breadcrumb, children), the city (desks near
 * the parent, "↳ parent" nameplates) and meant for leader offices (descendant counts).
 */
public final class Lineage {
	/** Ancestry walks stop here (nesting deeper than this is treated as a cycle). */
	public static final int MAX_DEPTH = 16;

	public static final Lineage EMPTY = new Lineage(List.of());

	/** Children in a stable order: oldest first, then id. */
	private static final Comparator<ThreadShell> CHILD_ORDER = Comparator
			.comparingLong((ThreadShell t) -> t.createdAt() == null ? Long.MAX_VALUE : t.createdAt())
			.thenComparing(ThreadShell::id);

	private final Map<String, ThreadShell> byId = new LinkedHashMap<>();
	private final Map<String, List<ThreadShell>> children = new HashMap<>();
	private final Map<String, Counts> counts = new ConcurrentHashMap<>();

	/** Descendant counts of one thread (children of children included; provider subagents too). */
	public record Counts(int total, int live, int working, int delegatedLive) {
		public static final Counts NONE = new Counts(0, 0, 0, 0);
	}

	private Lineage(Collection<ThreadShell> threads) {
		for (ThreadShell t : threads) {
			byId.put(t.id(), t);
		}
		for (ThreadShell t : threads) {
			String p = t.parentThreadId();
			if (p != null && !p.isBlank() && !p.equals(t.id())) {
				children.computeIfAbsent(p, k -> new ArrayList<>()).add(t);
			}
		}
		children.replaceAll((k, v) -> {
			v.sort(CHILD_ORDER);
			return List.copyOf(v);
		});
	}

	/** Index over any set of threads of one environment (not memoized). */
	public static Lineage of(Collection<ThreadShell> threads) {
		return threads.isEmpty() ? EMPTY : new Lineage(threads);
	}

	private record Memo(ShellState.View view, Lineage lineage) {
	}

	private static final Map<String, Memo> MEMO = new ConcurrentHashMap<>();

	/**
	 * The index of one environment's shell view, memoized per environment by view identity (a new
	 * view object is published on every shell change). Null view → {@link #EMPTY}.
	 */
	public static Lineage of(String envId, ShellState.@Nullable View view) {
		if (view == null) {
			return EMPTY;
		}
		Memo m = MEMO.get(envId);
		if (m != null && m.view() == view) {
			return m.lineage();
		}
		Lineage l = of(view.threads());
		MEMO.put(envId, new Memo(view, l));
		return l;
	}

	/** Drops the memo (environment unpaired, reload). */
	public static void forget(String envId) {
		MEMO.remove(envId);
	}

	// ---- kinds --------------------------------------------------------------------------------

	/** See {@link ThreadShell#isDelegated()}. */
	public static boolean isDelegated(@Nullable ThreadShell t) {
		return t != null && t.isDelegated();
	}

	/** See {@link ThreadShell#isProviderSubagent()}. */
	public static boolean isProviderSubagent(@Nullable ThreadShell t) {
		return t != null && t.isProviderSubagent();
	}

	/** Gone for good: archived or deleted. */
	public static boolean gone(ThreadShell t) {
		return t.archivedAt() != null || t.deletedAt() != null;
	}

	/** Not archived, deleted or settled. */
	public static boolean live(ThreadShell t) {
		return !gone(t) && !Visibility.isSettled(t);
	}

	/** Has a run going or waits on the user ({@code waiting} counts as working, see {@link ThreadState}). */
	public static boolean working(ThreadShell t) {
		if (!live(t)) {
			return false;
		}
		ThreadState s = ThreadState.of(t);
		return s == ThreadState.WORKING || s == ThreadState.NEEDS_ATTENTION || t.activeRunId() != null;
	}

	// ---- queries ------------------------------------------------------------------------------

	public @Nullable ThreadShell thread(@Nullable String id) {
		return id == null ? null : byId.get(id);
	}

	public Collection<ThreadShell> threads() {
		return byId.values();
	}

	/** Parent thread id as T3 says (even when the parent is not in this environment's shell). */
	public @Nullable String parentId(String id) {
		ThreadShell t = byId.get(id);
		String p = t == null ? null : t.parentThreadId();
		return p == null || p.isBlank() ? null : p;
	}

	/** The parent thread, or null (top level, or parent not in this shell). */
	public @Nullable ThreadShell parent(String id) {
		return thread(parentId(id));
	}

	/** Direct children (subagents of any kind and forks), oldest first. */
	public List<ThreadShell> children(String parentId) {
		return children.getOrDefault(parentId, List.of());
	}

	/** Direct children that are delegated tasks ({@link ThreadShell#isDelegated()}), oldest first. */
	public List<ThreadShell> delegatedChildren(String parentId) {
		List<ThreadShell> all = children(parentId);
		if (all.isEmpty()) {
			return all;
		}
		List<ThreadShell> out = new ArrayList<>(all.size());
		for (ThreadShell t : all) {
			if (t.isDelegated()) {
				out.add(t);
			}
		}
		return out;
	}

	public boolean hasChildren(String id) {
		return children.containsKey(id);
	}

	/** 0 for a top-level thread (or an unknown id), 1 for its child, … (capped at {@link #MAX_DEPTH}). */
	public int depth(String id) {
		int d = 0;
		String p = parentId(id);
		while (p != null && d < MAX_DEPTH) {
			d++;
			p = byId.containsKey(p) ? parentId(p) : null;
		}
		return d;
	}

	/** The top-most known ancestor (the thread itself when top level). */
	public String rootId(String id) {
		String cur = id;
		for (int i = 0; i < MAX_DEPTH; i++) {
			String p = parentId(cur);
			if (p == null || !byId.containsKey(p)) {
				return cur;
			}
			cur = p;
		}
		return cur;
	}

	/** Ancestors from the parent up (known ones only), nearest first. */
	public List<ThreadShell> ancestors(String id) {
		List<ThreadShell> out = new ArrayList<>();
		String p = parentId(id);
		while (p != null && out.size() < MAX_DEPTH) {
			ThreadShell t = byId.get(p);
			if (t == null) {
				break;
			}
			out.add(t);
			p = parentId(p);
		}
		return out;
	}

	/** Descendant counts (memoized per index). */
	public Counts counts(String id) {
		if (!children.containsKey(id)) {
			return Counts.NONE;
		}
		Counts c = counts.get(id);
		if (c == null) {
			c = count(id);
			counts.put(id, c);
		}
		return c;
	}

	/** Descendants not archived, deleted or settled. */
	public int liveDescendants(String id) {
		return counts(id).live();
	}

	/** Descendants working or waiting on the user. */
	public int workingDescendants(String id) {
		return counts(id).working();
	}

	private Counts count(String id) {
		int total = 0;
		int live = 0;
		int working = 0;
		int delegated = 0;
		List<String> stack = new ArrayList<>();
		stack.add(id);
		java.util.Set<String> seen = new java.util.HashSet<>();
		seen.add(id);
		while (!stack.isEmpty()) {
			String cur = stack.removeLast();
			for (ThreadShell c : children(cur)) {
				if (!seen.add(c.id())) {
					continue;
				}
				total++;
				if (live(c)) {
					live++;
					if (c.isDelegated()) {
						delegated++;
					}
				}
				if (working(c)) {
					working++;
				}
				stack.add(c.id());
			}
		}
		return new Counts(total, live, working, delegated);
	}
}
