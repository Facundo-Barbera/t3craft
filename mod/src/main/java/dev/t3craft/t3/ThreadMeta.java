package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * Model and workspace of a thread, decoded tolerantly from the shell JSON
 * ({@code OrchestrationV2ThreadShell}: {@code providerInstanceId}, {@code modelSelection},
 * {@code branch}, {@code worktreePath}). Every field is null when absent or blank; older and newer
 * T3 versions spell some of them differently, and those aliases are accepted too:
 *
 * <ul>
 *   <li>{@code modelSelection} as an object ({@code instanceId} or legacy {@code provider},
 *       {@code model}) or as a bare model string; legacy top-level {@code model} / {@code provider}.
 *   <li>{@code branch} or {@code gitBranch}; {@code worktreePath} or {@code worktree}.
 * </ul>
 *
 * <p>{@code extras} (feat/t3-sidebar-rows): snooze/read watermarks and the linked PR with its diff
 * size ({@link ThreadExtras}), decoded from the same shell object; never null.
 */
public record ThreadMeta(
		@Nullable String provider,
		@Nullable String instanceId,
		@Nullable String model,
		@Nullable String branch,
		@Nullable String worktreePath,
		ThreadExtras extras) {

	public static final ThreadMeta EMPTY = new ThreadMeta(null, null, null, null, null);

	/** Shape before {@code extras}, kept so existing callers still compile. */
	public ThreadMeta(@Nullable String provider, @Nullable String instanceId, @Nullable String model, @Nullable String branch,
			@Nullable String worktreePath) {
		this(provider, instanceId, model, branch, worktreePath, ThreadExtras.EMPTY);
	}

	public ThreadMeta {
		extras = extras == null ? ThreadExtras.EMPTY : extras;
		provider = blankToNull(provider);
		instanceId = blankToNull(instanceId);
		model = blankToNull(model);
		branch = blankToNull(branch);
		worktreePath = blankToNull(worktreePath);
	}

	public static ThreadMeta fromJson(@Nullable JsonObject o) {
		if (o == null) {
			return EMPTY;
		}
		String provider = null;
		String instance = null;
		String model = null;
		JsonElement sel = o.get("modelSelection");
		if (sel instanceof JsonObject s) {
			provider = Json.str(s, "provider");
			instance = Json.str(s, "instanceId");
			model = Json.str(s, "model");
		} else if (sel != null && sel.isJsonPrimitive()) {
			model = sel.getAsString();
		}
		if (instance == null) {
			instance = Json.str(o, "providerInstanceId");
		}
		if (provider == null) {
			provider = Json.str(o, "provider");
		}
		if (model == null) {
			model = Json.str(o, "model");
		}
		String branch = first(Json.str(o, "branch"), Json.str(o, "gitBranch"));
		String worktree = first(Json.str(o, "worktreePath"), Json.str(o, "worktree"));
		return new ThreadMeta(provider, instance, model, branch, worktree, ThreadExtras.fromJson(o));
	}

	/** Instance id when known, else the legacy provider name. */
	public @Nullable String providerKey() {
		return instanceId != null ? instanceId : provider;
	}

	private static @Nullable String first(@Nullable String a, @Nullable String b) {
		return blankToNull(a) != null ? a : b;
	}

	private static @Nullable String blankToNull(@Nullable String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
