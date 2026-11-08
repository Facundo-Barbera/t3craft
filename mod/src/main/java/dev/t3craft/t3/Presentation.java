package dev.t3craft.t3;

import org.jspecify.annotations.Nullable;

/** ThreadShell → NpcView (port of bridge/src/state/presentation.ts). */
public final class Presentation {
	static final int MAX_TITLE = 32;
	static final int MAX_ACTIVITY = 80;

	private Presentation() {
	}

	public static String clip(String s, int max) {
		String one = s.replaceAll("\\s+", " ").trim();
		return one.length() > max ? one.substring(0, max - 1) + "…" : one;
	}

	public static String npcId(String env, String threadId) {
		return env + ":" + threadId;
	}

	static @Nullable String activity(ThreadShell t, ThreadState s) {
		ThreadShell.PendingRequest p = t.pendingRuntimeRequest();
		if (s == ThreadState.NEEDS_ATTENTION && p != null) {
			return "user_input".equals(p.kind()) ? "Waiting for your answer" : "Approval needed: " + p.kind();
		}
		if (s == ThreadState.NEEDS_ATTENTION && t.hasActionableProposedPlan()) {
			return "Plan ready for review";
		}
		if (s == ThreadState.ERROR && t.lastError() != null && !t.lastError().isBlank()) {
			return clip(t.lastError(), MAX_ACTIVITY);
		}
		return t.latestMessageText() == null || t.latestMessageText().isBlank() ? null : clip(t.latestMessageText(), MAX_ACTIVITY);
	}

	static @Nullable String particle(ThreadState s) {
		return switch (s) {
			case WORKING -> "working";
			case NEEDS_ATTENTION -> "attention";
			case ERROR -> "error";
			default -> null;
		};
	}

	public static NpcView present(String env, String envLabel, ThreadShell t, @Nullable String projectTitle) {
		return present(env, envLabel, t, projectTitle, null);
	}

	/**
	 * feat/thread-lineage: with {@code parent} (the parent thread of a delegated task, same
	 * environment) the second nameplate line reads {@code ↳ <parent title>}. Its neighbourhood stays
	 * its own project (a delegate_task child shares its parent's project, hence its wing). Null parent = {@link #present(String, String, ThreadShell, String)}.
	 */
	public static NpcView present(String env, String envLabel, ThreadShell t, @Nullable String projectTitle, @Nullable ThreadShell parent) {
		NpcView v = present0(env, envLabel, t, projectTitle);
		if (parent == null || !t.isDelegated()) {
			return v;
		}
		String pt = clip(parent.title().isBlank() ? "(untitled)" : parent.title(), MAX_TITLE);
		return new NpcView(v.id(), v.env(), v.envLabel(), v.threadId(), v.title(), v.project(), v.state(), v.color(), v.glyph(), v.label(),
				parentSublabel(pt), v.activity(), v.priority(), v.settled(), v.groupId(), v.groupLabel(), v.skin(), v.particle(), v.model(),
				v.provider(), v.branch(), v.worktree());
	}

	/** Nameplate second line of a delegated task: {@code ↳ <parent title>}. */
	public static String parentSublabel(String parentTitle) {
		return "↳ " + parentTitle;
	}

	private static NpcView present0(String env, String envLabel, ThreadShell t, @Nullable String projectTitle) {
		ThreadState s = ThreadState.of(t);
		String title = clip(t.title().isBlank() ? "(untitled)" : t.title(), MAX_TITLE);
		return new NpcView(
				npcId(env, t.id()),
				env,
				envLabel,
				t.id(),
				title,
				projectTitle,
				s,
				s.color,
				s.glyph,
				s.glyph + " " + title,
				envLabel + (projectTitle == null ? "" : " · " + projectTitle),
				activity(t, s),
				s.priority,
				t.settledAt() != null,
				env + ":" + t.projectId(),
				clip(ShellState.projectLabel(projectTitle, t.projectId()), MAX_TITLE),
				env + "/" + s.wire,
				particle(s),
				Chips.model(t.meta()),
				Chips.provider(t.meta()),
				Chips.branch(t.meta()),
				Chips.worktree(t.meta().worktreePath()));
	}
}
