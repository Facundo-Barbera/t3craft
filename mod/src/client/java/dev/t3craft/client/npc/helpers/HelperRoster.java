package dev.t3craft.client.npc.helpers;

import dev.t3craft.t3.Presentation;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import dev.t3craft.t3.Visibility;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Pure helper logic (no Minecraft): finds subagents in the T3 shell (read-only {@code lineage}:
 * {@code relationshipToParent == "subagent"} + {@code parentThreadId}), groups them under their
 * parent NPC with a per-parent cap and a "+n" overflow, and filters them out of the desk roster.
 */
public final class HelperRoster {
	/** Nested subagents are walked up at most this many levels to find a visible ancestor. */
	static final int MAX_DEPTH = 16;

	private HelperRoster() {
	}

	/**
	 * @param active          running subagents (one helper each, before grouping and the cap)
	 * @param subagentNpcIds  NPC ids of EVERY subagent thread seen (running or not); these never get
	 *                        a desk, an NPC of their own or a module
	 */
	public record Collected(List<HelperView> active, Set<String> subagentNpcIds) {
		public static final Collected EMPTY = new Collected(List.of(), Set.of());

		public Set<String> activeIds() {
			Set<String> s = new LinkedHashSet<>();
			active.forEach(h -> s.add(h.id()));
			return s;
		}
	}

	/** The helpers drawn next to one parent, plus how many more run beyond the cap. */
	public record Family(String parentId, List<HelperView> shown, int overflow) {
		public int total() {
			return shown.size() + overflow;
		}
	}

	public static boolean isSubagent(ThreadShell t) {
		return "subagent".equals(t.relationshipToParent());
	}

	/**
	 * A subagent gets a helper while it runs: working or waiting for the user (or with a run in
	 * flight), and not archived, deleted or settled. Done, failed and idle subagents have finished.
	 */
	public static boolean isRunning(ThreadShell t) {
		if (t.archivedAt() != null || t.deletedAt() != null || Visibility.isSettled(t)) {
			return false;
		}
		ThreadState s = ThreadState.of(t);
		return s == ThreadState.WORKING || s == ThreadState.NEEDS_ATTENTION || t.activeRunId() != null;
	}

	/** Every environment's shell (env id → view); environments in sorted order for determinism. */
	public static Collected collect(Map<String, ShellState.View> views) {
		List<HelperView> active = new ArrayList<>();
		Set<String> ids = new LinkedHashSet<>();
		for (Map.Entry<String, ShellState.View> e : new TreeMap<>(views).entrySet()) {
			Collected c = collect(e.getKey(), e.getValue().threads());
			active.addAll(c.active());
			ids.addAll(c.subagentNpcIds());
		}
		return new Collected(List.copyOf(active), Set.copyOf(ids));
	}

	/** One environment. Subagents without a {@code parentThreadId} cannot be placed: ignored. */
	public static Collected collect(String env, Collection<ThreadShell> threads) {
		List<HelperView> active = new ArrayList<>();
		Set<String> ids = new LinkedHashSet<>();
		for (ThreadShell t : threads) {
			if (!isSubagent(t)) {
				continue;
			}
			ids.add(Presentation.npcId(env, t.id()));
			String parent = t.parentThreadId();
			if (parent == null || parent.isBlank() || !isRunning(t)) {
				continue;
			}
			long started = firstNonNull(t.activityRunStartedAt(), t.latestRunStartedAt(), t.createdAt());
			active.add(new HelperView(Presentation.npcId(env, t.id()), Presentation.npcId(env, parent), t.title(), ThreadState.of(t), started));
		}
		return new Collected(List.copyOf(active), Set.copyOf(ids));
	}

	/**
	 * The desk roster: {@code items} minus every subagent. With helpers on, this is the one place
	 * subagents are kept out of desks, modules, workstations and the HQ preview (they all read the
	 * registry's NPC list, which is fed from here). Order is preserved.
	 */
	public static <T> List<T> deskRoster(List<T> items, Function<T, String> npcId, Set<String> subagentNpcIds) {
		if (subagentNpcIds.isEmpty()) {
			return items;
		}
		List<T> out = new ArrayList<>(items.size());
		for (T t : items) {
			if (!subagentNpcIds.contains(npcId.apply(t))) {
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * Groups running helpers under their nearest ancestor that has an NPC ({@code visibleParents}).
	 * Helpers whose ancestry reaches no visible NPC are dropped. At most {@code maxPerParent} are
	 * shown per parent (those waiting for the user first, then the oldest); the rest count as
	 * overflow. Shown helpers are ordered oldest first so slots stay put as helpers come and go.
	 * Parents iterate in {@code visibleParents} order.
	 */
	public static Map<String, Family> group(List<HelperView> helpers, Collection<String> visibleParents, int maxPerParent) {
		Set<String> visible = new LinkedHashSet<>(visibleParents);
		Map<String, String> parentOf = new HashMap<>();
		helpers.forEach(h -> parentOf.put(h.id(), h.parentId()));
		Map<String, List<HelperView>> byParent = new HashMap<>();
		for (HelperView h : helpers) {
			String root = root(h.parentId(), parentOf, visible);
			if (root != null && !visible.contains(h.id())) {
				byParent.computeIfAbsent(root, k -> new ArrayList<>()).add(h);
			}
		}
		int cap = Math.max(0, maxPerParent);
		Comparator<HelperView> age = Comparator.comparingLong(HelperView::startedAt).thenComparing(HelperView::id);
		Map<String, Family> out = new LinkedHashMap<>();
		for (String p : visible) {
			List<HelperView> kids = byParent.get(p);
			if (kids == null) {
				continue;
			}
			List<HelperView> pick = new ArrayList<>(kids);
			pick.sort(Comparator.comparingInt((HelperView h) -> h.state() == ThreadState.NEEDS_ATTENTION ? 0 : 1).thenComparing(age));
			List<HelperView> shown = new ArrayList<>(pick.subList(0, Math.min(cap, pick.size())));
			shown.sort(age);
			out.put(p, new Family(p, List.copyOf(shown), pick.size() - shown.size()));
		}
		return out;
	}

	/**
	 * The extra line on the parent's panel: {@code "⚒ 2 helpers"}, {@code "⚒ 3 helpers +4"} (the
	 * "+n" counts running helpers over the cap), {@code "⚒ +5"} with a cap of 0; null with none.
	 */
	public static @Nullable String panelLine(@Nullable Family f) {
		if (f == null || f.total() == 0) {
			return null;
		}
		int s = f.shown().size();
		StringBuilder b = new StringBuilder("⚒");
		if (s > 0) {
			b.append(' ').append(s).append(s == 1 ? " helper" : " helpers");
		}
		if (f.overflow() > 0) {
			b.append(" +").append(f.overflow());
		}
		return b.toString();
	}

	private static @Nullable String root(String parent, Map<String, String> parentOf, Set<String> visible) {
		String p = parent;
		for (int i = 0; i < MAX_DEPTH && p != null; i++) {
			if (visible.contains(p)) {
				return p;
			}
			p = parentOf.get(p);
		}
		return null;
	}

	private static long firstNonNull(Long... values) {
		for (Long v : values) {
			if (v != null) {
				return v;
			}
		}
		return 0L;
	}
}
