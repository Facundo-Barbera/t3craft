package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The "register project" dialog's data, validation and payload ({@link RegisterProjectDialog}).
 * Sends {@code projects.mutate} with {@code ProjectMutation} {@code project.create}
 * ({@code packages/contracts/src/project.ts}): {@code {type, commandId, projectId, title,
 * workspaceRoot, createWorkspaceRootIfMissing, defaultModelSelection: null}}, like
 * {@code buildProjectCreateCommand} in {@code packages/client-runtime/src/operations/projects.ts}.
 * The title defaults to the folder name ({@code inferProjectTitleFromPath}). No {@code scripts}, so
 * {@code orchestration:operate} is enough. T3 answers with the created {@code Project}. Pure.
 */
public final class RegisterProjectForm {
	public static final int MAX_PATH = 4096;
	public static final int MAX_TITLE = 120;

	public @Nullable String envId;
	public String workspaceRoot = "";
	/** Blank = the folder name. */
	public String title = "";
	/**
	 * T3 Code's own "add project" sends {@code true}; T3Craft defaults to {@code false} so a typo in
	 * the path fails instead of creating a stray folder on the host. The dialog has a toggle.
	 */
	public boolean createIfMissing;

	public record Ids(String commandId, String projectId) {
		public static Ids random() {
			return new Ids(UUID.randomUUID().toString(), UUID.randomUUID().toString());
		}
	}

	/** Path as T3 stores it: trimmed, trailing separators removed (except the root). */
	public static String normalizePath(String path) {
		String p = path.strip();
		while (p.length() > 1 && (p.endsWith("/") || p.endsWith("\\")) && !p.matches("^[A-Za-z]:[\\\\/]$")) {
			p = p.substring(0, p.length() - 1);
		}
		return p;
	}

	/** Absolute POSIX ({@code /…}), home ({@code ~/…}), Windows drive ({@code C:\…}) or UNC ({@code \\host\…}). */
	public static boolean absolute(String path) {
		return path.startsWith("/") || path.equals("~") || path.startsWith("~/") || path.matches("^[A-Za-z]:[\\\\/].*") || path.startsWith("\\\\");
	}

	/** The folder name, like {@code inferProjectTitleFromPath}. */
	public static String inferTitle(String path) {
		String leaf = ShellState.folderName(normalizePath(path));
		return leaf == null || leaf.equals("~") ? normalizePath(path) : leaf;
	}

	public String effectiveTitle() {
		return title.isBlank() ? inferTitle(workspaceRoot) : title.strip();
	}

	/**
	 * Every problem, player-facing English. {@code existingRoots} are the workspace roots already
	 * registered in that environment (may be empty when unknown).
	 */
	public List<String> errors(boolean canOperate, Collection<String> existingRoots) {
		List<String> out = new ArrayList<>();
		if (envId == null) {
			out.add("Choose an environment.");
		} else if (!canOperate) {
			out.add("Enable control (operate) for this environment first.");
		}
		String root = normalizePath(workspaceRoot);
		if (root.isEmpty()) {
			out.add("Write the workspace path.");
		} else if (!absolute(root)) {
			out.add("The workspace path must be absolute (/home/me/project, ~/project or C:\\project).");
		} else if (root.length() > MAX_PATH) {
			out.add("The workspace path is too long.");
		} else if (root.contains("\n") || root.contains("\0")) {
			out.add("The workspace path has invalid characters.");
		} else if (existingRoots.stream().map(RegisterProjectForm::normalizePath).anyMatch(root::equals)) {
			out.add("That folder is already a project in this environment.");
		}
		if (effectiveTitle().length() > MAX_TITLE) {
			out.add("The name is too long (" + effectiveTitle().length() + " / " + MAX_TITLE + ").");
		}
		return out;
	}

	public record Built(@Nullable OperateAction action, @Nullable String projectId, @Nullable String error) {
	}

	public Built build(boolean canOperate, Collection<String> existingRoots, Supplier<Ids> ids) {
		List<String> errs = errors(canOperate, existingRoots);
		if (!errs.isEmpty()) {
			return new Built(null, null, errs.getFirst());
		}
		Ids id = ids.get();
		JsonObject p = new JsonObject();
		p.addProperty("type", "project.create");
		p.addProperty("commandId", id.commandId());
		p.addProperty("projectId", id.projectId());
		p.addProperty("title", effectiveTitle());
		p.addProperty("workspaceRoot", normalizePath(workspaceRoot));
		p.addProperty("createWorkspaceRootIfMissing", createIfMissing);
		p.add("defaultModelSelection", JsonNull.INSTANCE);
		return new Built(new OperateAction(OperateKind.PROJECT_CREATE, envId, null, effectiveTitle(), OperateAction.PROJECTS_MUTATE, p),
				id.projectId(), null);
	}

	/** The project id T3 returned ({@code Project.id}), else null. */
	public static @Nullable String projectId(OperateResult r) {
		if (!r.ok() || !(r.value() instanceof JsonObject o)) {
			return null;
		}
		String id = Json.str(o, "id");
		return id == null || id.isBlank() ? null : id;
	}
}
