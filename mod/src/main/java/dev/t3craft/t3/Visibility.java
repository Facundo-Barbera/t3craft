package dev.t3craft.t3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which threads of ONE environment get an NPC (port of bridge/src/state/visibility.ts):
 * never archived/deleted/settled/provider-subagent/side-chat (delegated tasks do show, feat/thread-lineage); shown when they need the user, are working or
 * have a turn in progress, or had message/run activity within the recent window. Ranked by tier
 * then most recent activity, capped per environment. Pure: the shell state is never modified.
 */
public final class Visibility {
	public static final long DEFAULT_RECENT_WINDOW_MS = 24L * 3_600_000L;
	public static final int DEFAULT_MAX_PER_ENVIRONMENT = 40;

	public record Options(long recentWindowMs, int maxPerEnvironment, boolean includeSubagents) {
		public static Options defaults() {
			return new Options(DEFAULT_RECENT_WINDOW_MS, DEFAULT_MAX_PER_ENVIRONMENT, false);
		}
	}

	private Visibility() {
	}

	public static boolean isSettled(ThreadShell t) {
		return "settled".equals(t.settledOverride()) || (t.settledAt() != null && !"active".equals(t.settledOverride()));
	}

	/** Messages and runs (plus creation); not updatedAt, so metadata touches don't keep a thread visible. */
	public static long lastActivity(ThreadShell t) {
		long m = 0;
		for (Long v : new Long[] {t.createdAt(), t.latestMessageAt(), t.latestUserMessageAt(), t.latestRunRequestedAt(),
				t.latestRunStartedAt(), t.latestRunCompletedAt(), t.activityRunStartedAt()}) {
			if (v != null && v > m) {
				m = v;
			}
		}
		return m;
	}

	/** 0 needs the user, 1 working/turn in progress, 2 recent only, -1 hidden. */
	public static int tier(ThreadShell t, long nowMs, Options o) {
		if (t.archivedAt() != null || t.deletedAt() != null) {
			return -1;
		}
		// feat/thread-lineage: delegated tasks (delegate_task children) are real threads and show like
		// any other; only provider subagents and side chats stay hidden.
		if (!o.includeSubagents() && ((t.isSubagent() && !t.isDelegated()) || t.sideChat())) {
			return -1;
		}
		if (isSettled(t)) {
			return -1;
		}
		ThreadState s = ThreadState.of(t);
		if (s == ThreadState.NEEDS_ATTENTION) {
			return 0;
		}
		if (s == ThreadState.WORKING || t.activeRunId() != null) {
			return 1;
		}
		return nowMs - lastActivity(t) <= o.recentWindowMs() ? 2 : -1;
	}

	public static List<ThreadShell> select(Iterable<ThreadShell> threads, long nowMs, Options o) {
		record Ranked(ThreadShell t, int tier, long last) {
		}
		List<Ranked> ranked = new ArrayList<>();
		for (ThreadShell t : threads) {
			int tier = tier(t, nowMs, o);
			if (tier >= 0) {
				ranked.add(new Ranked(t, tier, lastActivity(t)));
			}
		}
		ranked.sort(Comparator.comparingInt(Ranked::tier)
				.thenComparing(Comparator.comparingLong(Ranked::last).reversed())
				.thenComparing(r -> r.t().id()));
		List<ThreadShell> out = new ArrayList<>();
		for (int i = 0; i < ranked.size() && i < Math.max(0, o.maxPerEnvironment()); i++) {
			out.add(ranked.get(i).t());
		}
		return out;
	}
}
