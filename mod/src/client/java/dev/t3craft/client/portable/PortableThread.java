package dev.t3craft.client.portable;

import org.jspecify.annotations.Nullable;

/**
 * One thread row of the portable terminal, independent of the source (direct mode or bridge).
 * Built by the client adapter from the NPC model the mod already keeps, so the terminal shows
 * exactly what the world shows (same filter, same state words and colours).
 *
 * @param id       NPC id, {@code <env>:<threadId>}
 * @param env      environment id (slug), or "" when the source does not say
 * @param threadId T3 thread id, or null when it cannot be derived (history then unavailable)
 * @param state    state wire name: needs_attention, error, working, done, idle
 */
public record PortableThread(
		String id,
		String env,
		String envLabel,
		@Nullable String threadId,
		String title,
		String project,
		String state,
		int color,
		@Nullable String activity,
		int priority,
		boolean settled) {

	public boolean needsAttention() {
		return "needs_attention".equals(state);
	}

	/**
	 * Splits an NPC id {@code <env>:<threadId>} (both modes use this shape; environment slugs never
	 * contain ':'). Returns {env, threadId} or null when there is no separator.
	 */
	public static String @Nullable [] splitId(String npcId) {
		int i = npcId.indexOf(':');
		if (i <= 0 || i == npcId.length() - 1) {
			return null;
		}
		return new String[] {npcId.substring(0, i), npcId.substring(i + 1)};
	}
}
