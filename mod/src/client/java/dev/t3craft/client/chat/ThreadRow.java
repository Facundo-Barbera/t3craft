package dev.t3craft.client.chat;

import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * One thread in the unified list (all environments together). Built from whatever the mod already
 * knows (the NPC model in both modes); pure Java.
 *
 * @param key        {@code <env>:<threadId>} (the NPC id shape), unique across environments
 * @param env        environment id (slug)
 * @param threadId   T3 thread id, null when the source cannot say (then no history can open)
 * @param activityMs last activity in epoch ms when the source knows it, else null (ordering falls
 *                   back to attention then title)
 */
public record ThreadRow(
		String key,
		String env,
		String envLabel,
		@Nullable String threadId,
		String title,
		String project,
		ThreadState state,
		@Nullable String activity,
		boolean settled,
		@Nullable Long activityMs) {

	public boolean needsAttention() {
		return state == ThreadState.NEEDS_ATTENTION;
	}

	public EnvBadge badge() {
		return EnvBadge.of(env, envLabel);
	}

	/** From the NPC model (direct mode ids are {@code env:threadId}; bridge ids use the same shape). */
	public static ThreadRow fromNpc(NpcInfo n) {
		String id = n.id();
		int colon = id.indexOf(':');
		String env = colon > 0 ? id.substring(0, colon) : n.envLabel();
		String thread = colon > 0 && colon < id.length() - 1 ? id.substring(colon + 1) : null;
		String project = n.groupLabel() == null || "(no project)".equals(n.groupLabel()) ? "" : n.groupLabel();
		return new ThreadRow(id, env, n.envLabel(), thread, n.title(), project, state(n.state()), n.activity(), n.settled(), null);
	}

	static ThreadState state(@Nullable String wire) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(wire)) {
				return s;
			}
		}
		return ThreadState.IDLE;
	}
}
