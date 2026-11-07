package dev.t3craft.t3;

import org.jspecify.annotations.Nullable;

/**
 * One NPC = one T3 thread in one environment, fully resolved for display (same shape as the
 * bridge's protocol NpcView, so both modes feed the same renderer).
 */
public record NpcView(
		String id,
		String env,
		String envLabel,
		String threadId,
		String title,
		@Nullable String project,
		ThreadState state,
		int color,
		String glyph,
		String label,
		String sublabel,
		@Nullable String activity,
		int priority,
		boolean settled,
		String groupId,
		String groupLabel,
		String skin,
		@Nullable String particle) {
}
