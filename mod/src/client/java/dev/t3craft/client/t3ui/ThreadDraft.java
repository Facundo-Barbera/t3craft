package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.t3ui.actions.ModelPick;
import dev.t3craft.client.t3ui.actions.NewThreadForm;
import dev.t3craft.client.t3ui.actions.ProjectsService;
import dev.t3craft.t3.ThreadState;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * A new thread that does not exist yet, as T3 Code's draft thread ({@code composerDraftStore}'s
 * draft threads, {@code DraftHeroProjectHeading}): "+ New thread" opens no window, it adds a row
 * under its project in the sidebar and the thread panel shows "project › New thread", an empty
 * transcript and the composer. The composer's bar carries the project chip (environment + project,
 * {@link DraftControls}) and the workspace chip (Local / New worktree with its base branch and an
 * optional branch name: T3's {@code BranchToolbarEnvModeSelector} + {@code WorktreeBaseBranchPicker}).
 * The first message launches it ({@link DraftLauncher}, {@code orchestration.launchThread} as
 * {@link NewThreadForm} builds it) and the real thread replaces the row. Client thread only.
 * Closing the screen keeps it ({@link #kept}): the next T3 screen shows the row again, selected
 * when it was, with its text (the composer's draft store keys it by {@code env:id}).
 */
final class ThreadDraft {
	private static final AtomicInteger IDS = new AtomicInteger();
	/** The draft of the last T3 screen that closed with one, handed to the next screen. */
	static @Nullable ThreadDraft kept;
	/** It was the selected row when its screen closed. */
	boolean wasSelected;

	/** Local id, never sent (the composer keys the text by {@code env:id}). */
	final String id = "draft-" + System.currentTimeMillis() + "-" + IDS.incrementAndGet();
	final long createdAt = System.currentTimeMillis();
	String envId;
	String envLabel;
	String projectId;
	String projectLabel;
	/** The workspace part of the launch (prompt and model are filled at send time). */
	final NewThreadForm workspace = new NewThreadForm();
	/** Model / effort / modes chosen in the composer's model chip (null = project default). */
	@Nullable ModelPick pick;
	/** Thread shown before the draft was opened: back to it when the draft is discarded. */
	@Nullable String returnKey;
	/** Launch in flight (the row says "Starting…"). */
	boolean launching;

	ThreadDraft(ProjectsService.Project p) {
		envId = p.envId();
		envLabel = p.envLabel();
		projectId = p.projectId();
		projectLabel = p.label();
		workspace.baseRef = "main";
	}

	String key() {
		return envId + ":" + id;
	}

	boolean is(@Nullable String key) {
		return key != null && key.equals(key());
	}

	void setProject(ProjectsService.Project p) {
		envId = p.envId();
		envLabel = p.envLabel();
		projectId = p.projectId();
		projectLabel = p.label();
	}

	/** The sidebar row: under its project, newest (so it sorts first in the folder). */
	ThreadRow row() {
		return new ThreadRow(key(), envId, envLabel, null, launching ? "Starting…" : "New thread", projectLabel, ThreadState.IDLE, false, null, false,
				Long.MAX_VALUE, false);
	}
}
