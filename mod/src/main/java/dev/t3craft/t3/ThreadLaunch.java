package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Item 23 (feat/op-launch): create a thread in a project and send its first message, in one
 * {@code orchestration.launchThread} call ({@link OperateKind#THREAD_LAUNCH}, not destructive).
 * Pure logic, no Minecraft: payload, prompt validation, project/model choices read from the
 * read-only shell, and which environments can launch. The draft launcher of the T3 screen and the
 * command ({@code /t3craft operate new}) only call this and {@code OperateUi}.
 *
 * <p>Contract ({@code t3code-ref} @ 15fe2832, {@code OrchestrationV2ThreadLaunchInput}):
 * {@code commandId, threadId?, projectId, title, generateTitle?, modelSelection, runtimeMode,
 * interactionMode, workspaceStrategy, initialMessage?{messageId?, text, attachments}}; result
 * {@code {threadId, projection, resumed, sequence?}}. T3Craft sends:
 * <ul>
 *   <li>{@code runtimeMode: "approval-required"} (the safest; T3's own default is full access) and
 *   {@code interactionMode: "default"};</li>
 *   <li>{@code workspaceStrategy: {type: "root"}} (the project checkout, no new worktree);</li>
 *   <li>a title from the prompt's first line plus {@code generateTitle: true};</li>
 *   <li>fresh {@code commandId}/{@code threadId}/{@code messageId}; no {@code creationSource}
 *   (optional, and none of T3's values names a Minecraft client).</li>
 * </ul>
 * The new thread's NPC appears through the normal shell flow; nothing is spawned here.
 */
public final class ThreadLaunch {
	/** Upper bound for the prompt; generous, but keeps a pasted log from going out by mistake. */
	public static final int MAX_PROMPT = 16_000;
	/** Seed title length (T3 regenerates it, {@code generateTitle: true}). */
	public static final int MAX_TITLE = 60;
	public static final String RUNTIME_MODE = "approval-required";
	public static final String INTERACTION_MODE = "default";

	private ThreadLaunch() {
	}

	/** A model T3 accepts as {@code modelSelection}: {@code {instanceId, model, options?}}. */
	public record ModelChoice(String instanceId, String model, @Nullable JsonObject options) {
		public ModelChoice {
			if (instanceId == null || instanceId.isBlank() || model == null || model.isBlank()) {
				throw new IllegalArgumentException("instanceId and model are required");
			}
			instanceId = instanceId.trim();
			model = model.trim();
			options = options == null ? null : options.deepCopy();
		}

		/** From a wire {@code ModelSelection} ({@code instanceId} or legacy {@code provider}); null if incomplete. */
		public static @Nullable ModelChoice fromJson(@Nullable JsonObject o) {
			if (o == null) {
				return null;
			}
			String instance = Json.str(o, "instanceId");
			if (instance == null || instance.isBlank()) {
				instance = Json.str(o, "provider");
			}
			String model = Json.str(o, "model");
			if (instance == null || instance.isBlank() || model == null || model.isBlank()) {
				return null;
			}
			JsonElement opts = o.get("options");
			return new ModelChoice(instance, model, opts instanceof JsonObject oo ? oo : null);
		}

		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("instanceId", instanceId);
			o.addProperty("model", model);
			if (options != null) {
				o.add("options", options.deepCopy());
			}
			return o;
		}

		/** {@code instance · model}. */
		public String label() {
			return instanceId + " · " + model;
		}

		/** Same instance and model (options ignored), used to de-duplicate. */
		boolean sameModel(ModelChoice o) {
			return instanceId.equals(o.instanceId) && model.equals(o.model);
		}
	}

	/** A project of an environment, as listed in the read-only shell. */
	public record ProjectChoice(String id, String label, @Nullable ModelChoice defaultModel) {
	}

	/** Everything the player chose; {@link #model} null means "the project's default". */
	public record Request(String envId, String projectId, String prompt, @Nullable ModelChoice model) {
	}

	/** Ids the payload needs; injectable so tests are deterministic. */
	public record Ids(String commandId, String threadId, String messageId) {
		public static Ids random() {
			return new Ids("cmd-" + UUID.randomUUID(), UUID.randomUUID().toString(), "msg-" + UUID.randomUUID());
		}
	}

	/** Why a request cannot go out (player-facing English), or null when it is fine. */
	public static @Nullable String validatePrompt(@Nullable String prompt) {
		if (prompt == null || prompt.isBlank()) {
			return "Write a prompt first.";
		}
		if (prompt.length() > MAX_PROMPT) {
			return "The prompt is too long (" + prompt.length() + " / " + MAX_PROMPT + " characters).";
		}
		return null;
	}

	/** Seed title: the first non-blank line, collapsed and cut to {@link #MAX_TITLE}. */
	public static String title(String prompt) {
		for (String line : prompt.split("\\R")) {
			String t = line.strip().replaceAll("\\s+", " ");
			if (!t.isEmpty()) {
				return t.length() <= MAX_TITLE ? t : t.substring(0, MAX_TITLE - 1).stripTrailing() + "…";
			}
		}
		return "New thread";
	}

	/**
	 * A short, non-sensitive description of the prompt for summaries: its length and line count,
	 * never the text itself (the audit log never sees it either).
	 */
	public static String promptSummary(String prompt) {
		int lines = prompt.isEmpty() ? 0 : prompt.split("\\R", -1).length;
		return prompt.length() + " characters, " + lines + (lines == 1 ? " line" : " lines");
	}

	/** Environments the player can launch in: the listed ones with an active operate session. */
	public static List<String> operableEnvs(Collection<String> envIds, OperateGate gate) {
		return envIds.stream().filter(gate::canOperate).toList();
	}

	/** Projects of an environment's shell, sorted by label; each with its default model if any. */
	public static List<ProjectChoice> projects(ShellState.@Nullable View view) {
		if (view == null) {
			return List.of();
		}
		Map<String, ProjectChoice> out = new LinkedHashMap<>();
		view.projectTitles().forEach((id, title) -> out.put(id, new ProjectChoice(id, title, ModelChoice.fromJson(view.projectDefaultModel(id)))));
		// Projects only known through their threads (shell without a title for them).
		for (String id : view.projectDefaultModels().keySet()) {
			out.computeIfAbsent(id, k -> new ProjectChoice(k, view.projectLabel(k), ModelChoice.fromJson(view.projectDefaultModel(k))));
		}
		for (ThreadShell t : view.threads()) {
			if (!t.projectId().isBlank()) {
				out.computeIfAbsent(t.projectId(), k -> new ProjectChoice(k, view.projectLabel(k), null));
			}
		}
		List<ProjectChoice> list = new ArrayList<>(out.values());
		list.sort(Comparator.comparing((ProjectChoice p) -> p.label().toLowerCase(java.util.Locale.ROOT)).thenComparing(ProjectChoice::id));
		return list;
	}

	/**
	 * The project a command argument names: exact id first, then a unique case-insensitive label
	 * match. Null when nothing (or more than one label) matches.
	 */
	public static @Nullable ProjectChoice findProject(List<ProjectChoice> projects, String query) {
		String q = query.strip();
		for (ProjectChoice p : projects) {
			if (p.id().equals(q)) {
				return p;
			}
		}
		List<ProjectChoice> byLabel = projects.stream().filter(p -> p.label().equalsIgnoreCase(q)).toList();
		return byLabel.size() == 1 ? byLabel.getFirst() : null;
	}

	/**
	 * Models T3 exposes for a project, from what the read-only shell already carries: the project's
	 * default first, then the distinct models of threads in that project, then of the rest of the
	 * environment. Empty when T3 exposes none (then the launch cannot pick a model).
	 */
	public static List<ModelChoice> models(ShellState.@Nullable View view, String projectId) {
		if (view == null) {
			return List.of();
		}
		List<ModelChoice> out = new ArrayList<>();
		addDistinct(out, ModelChoice.fromJson(view.projectDefaultModel(projectId)));
		for (boolean sameProject : new boolean[] {true, false}) {
			for (ThreadShell t : view.threads()) {
				if (t.projectId().equals(projectId) == sameProject) {
					addDistinct(out, fromMeta(t.meta()));
				}
			}
		}
		return out;
	}

	private static @Nullable ModelChoice fromMeta(ThreadMeta m) {
		String key = m.providerKey();
		return key == null || m.model() == null ? null : new ModelChoice(key, m.model(), null);
	}

	private static void addDistinct(List<ModelChoice> out, @Nullable ModelChoice c) {
		if (c != null && out.stream().noneMatch(c::sameModel)) {
			out.add(c);
		}
	}

	/**
	 * The model that will be sent: the chosen one, else the project's default, else the first model
	 * T3 exposes (see {@link #models}); null when there is none at all.
	 */
	public static @Nullable ModelChoice resolveModel(ShellState.@Nullable View view, String projectId, @Nullable ModelChoice chosen) {
		if (chosen != null) {
			return chosen;
		}
		List<ModelChoice> ms = models(view, projectId);
		return ms.isEmpty() ? null : ms.getFirst();
	}

	/** The {@code orchestration.launchThread} payload. Validate the prompt first. */
	public static JsonObject payload(String projectId, String prompt, ModelChoice model, Ids ids) {
		Objects.requireNonNull(projectId);
		String err = validatePrompt(prompt);
		if (err != null) {
			throw new IllegalArgumentException(err);
		}
		JsonObject p = new JsonObject();
		p.addProperty("commandId", ids.commandId());
		p.addProperty("threadId", ids.threadId());
		p.addProperty("projectId", projectId);
		p.addProperty("title", title(prompt));
		p.addProperty("generateTitle", true);
		p.add("modelSelection", model.toJson());
		p.addProperty("runtimeMode", RUNTIME_MODE);
		p.addProperty("interactionMode", INTERACTION_MODE);
		JsonObject ws = new JsonObject();
		ws.addProperty("type", "root");
		p.add("workspaceStrategy", ws);
		JsonObject msg = new JsonObject();
		msg.addProperty("messageId", ids.messageId());
		msg.addProperty("text", prompt);
		msg.add("attachments", new JsonArray());
		p.add("initialMessage", msg);
		return p;
	}

	/** Result of {@link #build}: the action, or why it cannot be built. */
	public record Built(@Nullable OperateAction action, @Nullable String error) {
		static Built error(String e) {
			return new Built(null, e);
		}
	}

	/**
	 * Validates {@code r} against the environment's shell and builds the
	 * {@link OperateKind#THREAD_LAUNCH} action. The thread id in the action is the client-generated
	 * one; T3's answer is authoritative ({@link #threadId}).
	 */
	public static Built build(Request r, ShellState.@Nullable View view, Supplier<Ids> ids) {
		String err = validatePrompt(r.prompt());
		if (err != null) {
			return Built.error(err);
		}
		if (r.projectId() == null || r.projectId().isBlank()) {
			return Built.error("Choose a project.");
		}
		if (view != null && projects(view).stream().noneMatch(p -> p.id().equals(r.projectId()))) {
			return Built.error("Unknown project " + r.projectId() + " in " + r.envId() + ".");
		}
		ModelChoice model = resolveModel(view, r.projectId(), r.model());
		if (model == null) {
			return Built.error("T3 exposes no model for this project yet: set a default model for it in T3.");
		}
		Ids id = ids.get();
		String title = title(r.prompt());
		return new Built(new OperateAction(OperateKind.THREAD_LAUNCH, r.envId(), id.threadId(), title, OperateAction.LAUNCH_THREAD,
				payload(r.projectId(), r.prompt(), model, id)), null);
	}

	/** The thread id T3 returned for an {@code OK} launch, or null. */
	public static @Nullable String threadId(OperateResult r) {
		if (!r.ok() || !(r.value() instanceof JsonObject o)) {
			return null;
		}
		String id = Json.str(o, "threadId");
		return id == null || id.isBlank() ? null : id;
	}
}
