package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.ThreadLaunch;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A new thread's data, validation and payload, without drawing (feat/t3-composer: the T3 screen's
 * draft thread, {@code DraftLauncher}; the old dialog is gone).
 * One {@code orchestration.launchThread} call ({@code OrchestrationV2ThreadLaunchInput} in
 * {@code packages/contracts/src/orchestrationV2.ts}): {@code commandId, threadId, projectId, title,
 * generateTitle, modelSelection, runtimeMode, interactionMode, workspaceStrategy,
 * initialMessage{messageId, text, attachments}}, like T3 Code's composer on a draft thread
 * ({@code launchThread} in {@code packages/client-runtime/src/operations/commands.ts}).
 * {@code workspaceStrategy} is one of {@code {type:"root", branch?}} (the project checkout) or
 * {@code {type:"worktree", baseRef, branch?, startFromOrigin?}} (a new worktree, T3's
 * "New worktree" env mode). Pure.
 */
public final class NewThreadForm {
	/** T3's env mode for a new thread ({@code BranchToolbarEnvModeSelector}). */
	public enum Workspace {
		/** {@code {type: "root"}}: work in the project checkout. */
		LOCAL("Local", "root"),
		/** {@code {type: "worktree", baseRef}}: a new git worktree from {@code baseRef}. */
		WORKTREE("New worktree", "worktree");

		public final String label;
		public final String wire;

		Workspace(String label, String wire) {
			this.label = label;
			this.wire = wire;
		}
	}

	/** Git ref names: no spaces, no {@code ..}, no leading {@code -}, none of {@code ~^:?*[\}. */
	private static final Pattern REF = Pattern.compile("^(?!-)(?!.*\\.\\.)(?!.*//)[^\\s~^:?*\\[\\\\]+(?<![./])$");

	public @Nullable String envId;
	public @Nullable String projectId;
	public String prompt = "";
	/** Null = the project's default model ({@link ThreadLaunch#resolveModel}). */
	public @Nullable ModelPick pick;
	public Workspace workspace = Workspace.LOCAL;
	public String baseRef = "";
	public String branch = "";
	public boolean startFromOrigin;

	/** Every problem, player-facing English, in field order. Empty = can be sent. */
	public List<String> errors(boolean canOperate, ThreadLaunch.@Nullable ModelChoice projectDefault) {
		List<String> out = new ArrayList<>();
		if (envId == null) {
			out.add("Choose an environment.");
		} else if (!canOperate) {
			out.add("Enable control (operate) for this environment first.");
		}
		if (projectId == null || projectId.isBlank()) {
			out.add("Choose a project.");
		}
		if (pick == null && projectDefault == null) {
			out.add("Choose a model.");
		}
		if (workspace == Workspace.WORKTREE) {
			if (baseRef.isBlank()) {
				out.add("Write the base branch for the worktree.");
			} else if (!validRef(baseRef.strip())) {
				out.add("The base branch is not a valid git ref.");
			}
		}
		if (!branch.isBlank() && !validRef(branch.strip())) {
			out.add("The branch name is not a valid git ref.");
		}
		String p = ThreadLaunch.validatePrompt(prompt);
		if (p != null) {
			out.add(p);
		}
		return out;
	}

	public static boolean validRef(String ref) {
		return !ref.isEmpty() && ref.length() <= 200 && REF.matcher(ref).matches() && !ref.endsWith(".lock");
	}

	/** {@code workspaceStrategy} for the current choice. */
	public JsonObject workspaceStrategy() {
		JsonObject ws = new JsonObject();
		ws.addProperty("type", workspace.wire);
		if (workspace == Workspace.WORKTREE) {
			ws.addProperty("baseRef", baseRef.strip());
		}
		if (!branch.isBlank()) {
			ws.addProperty("branch", branch.strip());
		}
		if (workspace == Workspace.WORKTREE && startFromOrigin) {
			ws.addProperty("startFromOrigin", true);
		}
		return ws;
	}

	/** Result of {@link #build}: the action, or the first error. */
	public record Built(@Nullable OperateAction action, @Nullable String error) {
	}

	/**
	 * Validates and builds the {@link OperateKind#THREAD_LAUNCH} action. The modes come from
	 * {@link #pick} (or T3Craft's defaults when the project default model is used).
	 */
	public Built build(boolean canOperate, ThreadLaunch.@Nullable ModelChoice projectDefault, Supplier<ThreadLaunch.Ids> ids) {
		List<String> errs = errors(canOperate, projectDefault);
		if (!errs.isEmpty()) {
			return new Built(null, errs.getFirst());
		}
		ThreadLaunch.Ids id = ids.get();
		JsonObject p = new JsonObject();
		p.addProperty("commandId", id.commandId());
		p.addProperty("threadId", id.threadId());
		p.addProperty("projectId", projectId);
		String title = ThreadLaunch.title(prompt);
		p.addProperty("title", title);
		p.addProperty("generateTitle", true);
		p.add("modelSelection", pick != null ? pick.modelSelection() : projectDefault.toJson());
		p.addProperty("runtimeMode", (pick != null ? pick.runtime() : Modes.Runtime.DEFAULT).wire);
		p.addProperty("interactionMode", (pick != null ? pick.interaction() : Modes.Interaction.DEFAULT).wire);
		p.add("workspaceStrategy", workspaceStrategy());
		JsonObject msg = new JsonObject();
		msg.addProperty("messageId", id.messageId());
		msg.addProperty("text", prompt);
		msg.add("attachments", new JsonArray());
		p.add("initialMessage", msg);
		return new Built(new OperateAction(OperateKind.THREAD_LAUNCH, envId, id.threadId(), title, OperateAction.LAUNCH_THREAD, p), null);
	}
}
