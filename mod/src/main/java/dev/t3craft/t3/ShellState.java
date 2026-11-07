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
	public record Project(String id, String title, String workspaceRoot, @Nullable JsonElement repositoryIdentity) {
		static @Nullable Project fromJson(@Nullable JsonObject o) {
			String id = Json.str(o, "id");
			if (id == null) {
				return null;
			}
			JsonElement identity = o.get("repositoryIdentity");
			return new Project(id, Objects.requireNonNullElse(Json.str(o, "title"), id),
					Objects.requireNonNullElse(Json.str(o, "workspaceRoot"), ""),
					identity == null || identity.isJsonNull() ? null : identity);
		}
	}

	private final Map<String, ThreadShell> threads = new LinkedHashMap<>();
	private final Map<String, Project> projects = new LinkedHashMap<>();
	private long sequence = -1;
	private boolean synchronizedFlag;

	public Collection<ThreadShell> threads() {
		return threads.values();
	}

	public @Nullable ThreadShell thread(String id) {
		return threads.get(id);
	}

	public @Nullable Project project(String id) {
		return projects.get(id);
	}

	public @Nullable String projectTitle(String projectId) {
		Project p = projects.get(projectId);
		return p == null ? null : p.title();
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
				Project p = Project.fromJson(Json.obj(item, "project"));
				if (p != null) {
					projects.put(p.id(), p);
				}
				return p != null;
			}
			case "project.removed" -> {
				bump(item);
				String id = Json.str(item, "projectId");
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
			}
		}
	}

	/** Same rules as upstream client-runtime shellReducer.mergeShellSnapshotProjects (enrichment). */
	private boolean applyEnrichment(@Nullable JsonObject snap, @Nullable JsonArray roots) {
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
