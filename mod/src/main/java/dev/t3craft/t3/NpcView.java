package dev.t3craft.t3;

import org.jspecify.annotations.Nullable;

/**
 * One NPC = one T3 thread in one environment, fully resolved for display (same shape as the
 * bridge's protocol NpcView, so both modes feed the same renderer). {@code model}, {@code provider},
 * {@code branch} and {@code worktree} are already-abbreviated chips ({@link Chips}), null when unknown.
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
		@Nullable String particle,
		@Nullable String model,
		@Nullable String provider,
		@Nullable String branch,
		@Nullable String worktree) {

	/** Without chips (model/provider/branch/worktree unknown). */
	public NpcView(String id, String env, String envLabel, String threadId, String title, @Nullable String project, ThreadState state,
			int color, String glyph, String label, String sublabel, @Nullable String activity, int priority, boolean settled,
			String groupId, String groupLabel, String skin, @Nullable String particle) {
		this(id, env, envLabel, threadId, title, project, state, color, glyph, label, sublabel, activity, priority, settled, groupId,
				groupLabel, skin, particle, null, null, null, null);
	}
}
