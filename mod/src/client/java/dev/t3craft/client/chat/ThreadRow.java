package dev.t3craft.client.chat;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * One thread in the unified list (every environment together). Pure Java.
 *
 * @param key            {@code <env>:<threadId>} (the NPC id shape), unique across environments
 * @param env            environment id (slug)
 * @param envLabel       environment display name
 * @param threadId       T3 thread id, null when the source cannot say (then no history can open)
 * @param state          thread state (same rules as the NPCs)
 * @param pendingRequest a runtime request (approval, input) is pending, blocking or not
 * @param activity       short "what it is doing" text, or null
 * @param settled        the thread was settled in T3 (sorts after active ones of the same rank)
 * @param activityMs     last activity in epoch ms when the source knows it, else null
 * @param pinned         pinned in T3 (listed first)
 * @param details        branch, PR, diff, model, unread, run start, snooze (feat/t3-sidebar-rows);
 *                       never null, {@link ThreadDetails#EMPTY} when unknown
 */
public record ThreadRow(
		String key,
		String env,
		String envLabel,
		@Nullable String threadId,
		String title,
		String project,
		ThreadState state,
		boolean pendingRequest,
		@Nullable String activity,
		boolean settled,
		@Nullable Long activityMs,
		boolean pinned,
		ThreadDetails details) {

	public ThreadRow {
		details = details == null ? ThreadDetails.EMPTY : details;
	}

	/** Shape before {@code details}. */
	public ThreadRow(String key, String env, String envLabel, @Nullable String threadId, String title, String project, ThreadState state,
			boolean pendingRequest, @Nullable String activity, boolean settled, @Nullable Long activityMs, boolean pinned) {
		this(key, env, envLabel, threadId, title, project, state, pendingRequest, activity, settled, activityMs, pinned, ThreadDetails.EMPTY);
	}

	/** Shape before {@code pinned}. */
	public ThreadRow(String key, String env, String envLabel, @Nullable String threadId, String title, String project, ThreadState state,
			boolean pendingRequest, @Nullable String activity, boolean settled, @Nullable Long activityMs) {
		this(key, env, envLabel, threadId, title, project, state, pendingRequest, activity, settled, activityMs, false);
	}

	public Attention attention() {
		return Attention.of(state, pendingRequest);
	}

	public boolean needsAttention() {
		return attention().urgent();
	}

	/** From the NPC model (direct mode ids are {@code env:threadId}; bridge ids use the same shape). */
	public static ThreadRow fromNpc(NpcInfo n, @Nullable Long activityMs, boolean pendingRequest) {
		return fromNpc(n, activityMs, pendingRequest, false);
	}

	public static ThreadRow fromNpc(NpcInfo n, @Nullable Long activityMs, boolean pendingRequest, boolean pinned) {
		String id = n.id();
		int colon = id.indexOf(':');
		String env = colon > 0 ? id.substring(0, colon) : n.envLabel();
		String thread = colon > 0 && colon < id.length() - 1 ? id.substring(colon + 1) : null;
		String project = n.groupLabel() == null || "(no project)".equals(n.groupLabel()) ? "" : n.groupLabel();
		// bridge mode: the chips the NPC carries (branch, worktree, model) feed the rich row too
		ThreadDetails d = n.branch() == null && n.worktree() == null && n.model() == null && n.provider() == null ? ThreadDetails.EMPTY
				: new ThreadDetails(n.branch(), n.worktree() != null, 0, null, null, -1, -1, n.model(), n.provider(), false, null, null);
		return new ThreadRow(id, env, n.envLabel(), thread, n.title(), project, state(n.state()), pendingRequest, n.activity(), n.settled(),
				activityMs, pinned, d);
	}

	public static ThreadRow fromNpc(NpcInfo n) {
		return fromNpc(n, null, false);
	}

	static ThreadState state(@Nullable String wire) {
		return ThreadOrder.state(wire);
	}
}
