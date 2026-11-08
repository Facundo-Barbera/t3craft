package dev.t3craft.client.chat;

import dev.t3craft.t3.Chips;
import dev.t3craft.t3.ThreadExtras;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * What a sidebar row shows besides state and title (feat/t3-sidebar-rows), T3 Code's rich row:
 * branch / worktree, diff size, linked PR, model, unread, when the current run started and the
 * snooze. Pure Java; {@link #EMPTY} for rows that do not know (bridge, disk cache).
 *
 * @param branch         git branch, or null
 * @param worktree       the thread runs in its own worktree
 * @param prNumber       linked PR number, 0 = none
 * @param prState        "open", "draft", "merged", "closed" or null
 * @param prRepository   {@code owner/repo} of the PR, or null
 * @param additions      +N of the PR diff, -1 unknown
 * @param deletions      −N of the PR diff, -1 unknown
 * @param model          short model chip ("opus 5.5"), or null
 * @param provider       short provider ("claude", "codex"), or null
 * @param unread         a run completed since the thread was last visited
 * @param runStartedMs   start of the current run ({@code activityRunStartedAt}, else
 *                       {@code latestRunStartedAt}), epoch ms, or null
 * @param snoozedUntilMs snoozed until then (epoch ms), or null
 */
public record ThreadDetails(
		@Nullable String branch,
		boolean worktree,
		int prNumber,
		@Nullable String prState,
		@Nullable String prRepository,
		int additions,
		int deletions,
		@Nullable String model,
		@Nullable String provider,
		boolean unread,
		@Nullable Long runStartedMs,
		@Nullable Long snoozedUntilMs) {

	public static final ThreadDetails EMPTY = new ThreadDetails(null, false, 0, null, null, -1, -1, null, null, false, null, null);

	/** Whether the third row line has anything to show (branch, diff or PR). */
	public boolean hasLine3() {
		return branch != null || worktree || prNumber > 0 || additions >= 0 || deletions >= 0;
	}

	public boolean snoozed(long nowMs) {
		return snoozedUntilMs != null && snoozedUntilMs > nowMs;
	}

	/** From a shell thread (direct mode). */
	public static ThreadDetails of(ThreadShell t, ThreadState state) {
		ThreadExtras x = t.meta().extras();
		Long started = t.activityRunStartedAt() != null ? t.activityRunStartedAt() : t.latestRunStartedAt();
		Long done = t.latestRunCompletedAt();
		boolean unread = state != ThreadState.WORKING && done != null && x.lastVisitedAt() != null && done > x.lastVisitedAt();
		String model = Chips.model(t.meta());
		String provider = Chips.providerLabel(t.meta().providerKey());
		ThreadDetails d = new ThreadDetails(t.meta().branch(), t.meta().worktreePath() != null, x.prNumber(), x.prState(), x.prRepository(),
				x.additions(), x.deletions(), model, provider, unread, started, x.snoozedUntil());
		return d.equals(EMPTY) ? EMPTY : d;
	}
}
