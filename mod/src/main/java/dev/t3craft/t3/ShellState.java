package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reducer over {@code orchestration.subscribeShell} items for one environment (Java port of
 * bridge/src/t3/shell.ts @ bfaebf1).
 * <ul>
 *   <li>An <b>authoritative</b> snapshot (no {@code resolvedRepositoryIdentityRoots}) replaces
 *   everything.</li>
 *   <li>An <b>enrichment</b> snapshot (with {@code resolvedRepositoryIdentityRoots}, always
 *   {@code threads: []}) only updates repository identity of known projects; threads and sequence
 *   stay.</li>
 *   <li>{@code synchronized} marks the end of the initial load (only sent when the subscription
 *   asked for {@code requestCompletionMarker: true}).</li>
 * </ul>
 * Archived threads are dropped. Not thread-safe; owned by one connection thread.
 */
public final class ShellState {
	/**
	 * A project shell. {@code title} is T3's title as sent (may be blank on some servers); use
	 * {@link #displayName()} for display.
	 */
	public record Project(String id, String title, String workspaceRoot, @Nullable JsonElement repositoryIdentity) {
		static @Nullable Project fromJson(@Nullable JsonObject o) {
			String id = Json.str(o, "id");
			if (id == null) {
				return null;
			}
			JsonElement identity = o.get("repositoryIdentity");
			return new Project(id, Objects.requireNonNullElse(Json.str(o, "title"), ""),
					Objects.requireNonNullElse(Json.str(o, "workspaceRoot"), ""),
					identity == null || identity.isJsonNull() ? null : identity);
		}

		/** Title; else the repository name; else the workspace folder name; else null. */
		public @Nullable String displayName() {
			if (!title.isBlank()) {
				return title.trim();
			}
			if (repositoryIdentity != null && repositoryIdentity.isJsonObject()) {
				JsonObject ri = repositoryIdentity.getAsJsonObject();
				for (String k : new String[] {"name", "displayName"}) {
					String v = Json.str(ri, k);
					if (v != null && !v.isBlank()) {
						return v.contains("/") ? v.substring(v.lastIndexOf('/') + 1) : v;
					}
				}
			}
			return folderName(workspaceRoot);
		}
	}

	/** Last path segment of a POSIX or Windows path, or null. */
	public static @Nullable String folderName(@Nullable String path) {
		if (path == null) {
			return null;
		}
		String p = path.replace('\\', '/').replaceAll("/+$", "");
		String leaf = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
		return leaf.isBlank() ? null : leaf;
	}

	private final Map<String, ThreadShell> threads = new LinkedHashMap<>();
	private final Map<String, Project> projects = new LinkedHashMap<>();
	/** feat/op-launch: raw {@code defaultModelSelection} per project id (absent when null/missing). */
	private final Map<String, JsonObject> defaultModels = new LinkedHashMap<>();
	private long sequence = -1;
	private boolean synchronizedFlag;
	private boolean hadAuthoritative;

	public Collection<ThreadShell> threads() {
		return threads.values();
	}

	public @Nullable ThreadShell thread(String id) {
		return threads.get(id);
	}

	public @Nullable Project project(String id) {
		return projects.get(id);
	}

	/** Display name of a known project (see {@link Project#displayName()}), or null if unknown. */
	public @Nullable String projectTitle(String projectId) {
		Project p = projects.get(projectId);
		return p == null ? null : p.displayName();
	}

	/**
	 * Immutable copy for other threads: visible threads + project display names + sync flag, and
	 * (feat/op-launch) each project's raw {@code defaultModelSelection} (only projects that have one).
	 */
	public record View(java.util.List<ThreadShell> threads, Map<String, String> projectTitles, boolean synced,
			Map<String, JsonObject> projectDefaultModels) {
		public View {
			projectDefaultModels = projectDefaultModels == null ? Map.of() : projectDefaultModels;
		}

		/** Pre-feat/op-launch shape, kept so existing callers still compile. */
		public View(java.util.List<ThreadShell> threads, Map<String, String> projectTitles, boolean synced) {
			this(threads, projectTitles, synced, Map.of());
		}

		/** A copy of the project's {@code defaultModelSelection}, or null when it has none. */
		public @Nullable JsonObject projectDefaultModel(String projectId) {
			JsonObject m = projectDefaultModels.get(projectId);
			return m == null ? null : m.deepCopy();
		}

		/** Display name of the project, or null when the project is unknown. */
		public @Nullable String projectTitle(String projectId) {
			return projectTitles.get(projectId);
		}

		/** Never null: display name, else {@code project <id prefix>}, else {@code (no project)}. */
		public String projectLabel(String projectId) {
			return ShellState.projectLabel(projectTitles.get(projectId), projectId);
		}
	}

	static String projectLabel(@Nullable String name, @Nullable String projectId) {
		if (name != null && !name.isBlank()) {
			return name;
		}
		if (projectId == null || projectId.isBlank()) {
			return "(no project)";
		}
		String id = projectId.startsWith("mcp:") ? projectId.substring(4) : projectId;
		return "project " + (id.length() > 8 ? id.substring(0, 8) : id);
	}

	public View view() {
		Map<String, String> titles = new LinkedHashMap<>();
		projects.forEach((id, p) -> {
			String name = p.displayName();
			if (name != null) {
				titles.put(id, name);
			}
		});
		Map<String, JsonObject> models = new LinkedHashMap<>();
		defaultModels.forEach((id, m) -> models.put(id, m.deepCopy()));
		return new View(java.util.List.copyOf(threads.values()), Map.copyOf(titles), synchronizedFlag, java.util.Collections.unmodifiableMap(models));
	}

	public long sequence() {
		return sequence;
	}

	public boolean isSynchronized() {
		return synchronizedFlag;
	}

	/** Applies one stream item; returns whether visible state changed. Unknown kinds are ignored. */
	public boolean apply(JsonObject item) {
		String kind = Json.str(item, "kind");
		if (kind == null) {
			return false;
		}
		switch (kind) {
			case "snapshot" -> {
				JsonObject snap = Json.obj(item, "snapshot");
				if (item.has("resolvedRepositoryIdentityRoots")) {
					return applyEnrichment(snap, Json.arr(item, "resolvedRepositoryIdentityRoots"));
				}
				threads.clear();
				projects.clear();
				defaultModels.clear();
				JsonArray ts = Json.arr(snap, "threads");
				if (ts != null) {
					for (JsonElement e : ts) {
						ThreadShell t = e.isJsonObject() ? ThreadShell.fromJson(e.getAsJsonObject()) : null;
						if (t != null) {
							threads.put(t.id(), t);
						}
					}
				}
				putProjects(Json.arr(snap, "projects"));
				sequence = Json.num(snap, "snapshotSequence", sequence);
				hadAuthoritative = true;
				return true;
			}
			case "synchronized" -> {
				synchronizedFlag = true;
				return false;
			}
			case "thread.updated" -> {
				bump(item);
				ThreadShell t = ThreadShell.fromJson(Json.obj(item, "thread"));
				if (t == null) {
					return false;
				}
				if ("active".equals(Json.str(item, "location"))) {
					threads.put(t.id(), t);
				} else {
					threads.remove(t.id());
				}
				return true;
			}
			case "thread.removed" -> {
				bump(item);
				String id = Json.str(item, "threadId");
				return id != null && threads.remove(id) != null;
			}
			case "project.updated" -> {
				bump(item);
				JsonObject raw = Json.obj(item, "project");
				Project p = Project.fromJson(raw);
				if (p != null) {
					projects.put(p.id(), p);
					rememberDefaultModel(p.id(), raw);
				}
				return p != null;
			}
			case "project.removed" -> {
				bump(item);
				String id = Json.str(item, "projectId");
				if (id != null) {
					defaultModels.remove(id);
				}
				return id != null && projects.remove(id) != null;
			}
			default -> {
				return false;
			}
		}
	}

	private void bump(JsonObject item) {
		sequence = Math.max(sequence, Json.num(item, "sequence", sequence));
	}

	private void putProjects(@Nullable JsonArray ps) {
		if (ps == null) {
			return;
		}
		for (JsonElement e : ps) {
			Project p = e.isJsonObject() ? Project.fromJson(e.getAsJsonObject()) : null;
			if (p != null) {
				projects.put(p.id(), p);
				rememberDefaultModel(p.id(), e.getAsJsonObject());
			}
		}
	}

	private void rememberDefaultModel(String projectId, @Nullable JsonObject raw) {
		JsonElement m = raw == null ? null : raw.get("defaultModelSelection");
		if (m instanceof JsonObject o) {
			defaultModels.put(projectId, o.deepCopy());
		} else {
			defaultModels.remove(projectId);
		}
	}

	/**
	 * Same rules as upstream client-runtime shellReducer.mergeShellSnapshotProjects (enrichment):
	 * with no authoritative snapshot yet, the refresh's projects are adopted as they are; afterwards
	 * it only updates repository identity of known projects (threads and sequence untouched).
	 */
	private boolean applyEnrichment(@Nullable JsonObject snap, @Nullable JsonArray roots) {
		if (!hadAuthoritative) {
			int before = projects.size();
			putProjects(Json.arr(snap, "projects"));
			return projects.size() != before;
		}
		java.util.Set<String> resolved = new java.util.HashSet<>();
		if (roots != null) {
			for (JsonElement r : roots) {
				if (r.isJsonPrimitive()) {
					resolved.add(r.getAsString());
				}
			}
		}
		JsonArray ps = Json.arr(snap, "projects");
		if (ps == null) {
			return false;
		}
		boolean changed = false;
		for (JsonElement e : ps) {
			Project candidate = e.isJsonObject() ? Project.fromJson(e.getAsJsonObject()) : null;
			if (candidate == null) {
				continue;
			}
			Project current = projects.get(candidate.id());
			if (current == null || !current.workspaceRoot().equals(candidate.workspaceRoot())) {
				continue;
			}
			boolean accept = resolved.contains(current.workspaceRoot())
					? !Objects.equals(current.repositoryIdentity(), candidate.repositoryIdentity())
					: current.repositoryIdentity() == null && candidate.repositoryIdentity() != null;
			if (accept) {
				projects.put(current.id(), new Project(current.id(), current.title(), current.workspaceRoot(), candidate.repositoryIdentity()));
				changed = true;
			}
		}
		return changed;
	}
}
