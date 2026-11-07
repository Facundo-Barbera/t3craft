package dev.t3craft.client.archive;

import com.google.gson.JsonObject;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Synthetic thread shells for the archive tests. */
final class ArchiveFixtures {
	static final long NOW = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli();
	static final long H = 3_600_000L;

	private ArchiveFixtures() {
	}

	static JsonObject json(String id, String project) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("projectId", project);
		o.addProperty("title", "Thread " + id);
		o.addProperty("status", "idle");
		o.addProperty("createdAt", Instant.ofEpochMilli(NOW - 100 * H).toString());
		return o;
	}

	static ThreadShell settled(String id, String project, long agoMs) {
		JsonObject o = json(id, project);
		o.addProperty("settledOverride", "settled");
		o.addProperty("settledAt", Instant.ofEpochMilli(NOW - agoMs).toString());
		return ThreadShell.fromJson(o);
	}

	static ThreadShell archived(String id, String project, long agoMs) {
		JsonObject o = json(id, project);
		o.addProperty("archivedAt", Instant.ofEpochMilli(NOW - agoMs).toString());
		return ThreadShell.fromJson(o);
	}

	static ThreadShell active(String id, String project) {
		return ThreadShell.fromJson(json(id, project));
	}

	static ShellState.View view(Map<String, String> projects, ThreadShell... threads) {
		return new ShellState.View(List.of(threads), projects, true);
	}

	static ArchiveEntry entry(String env, String id, String project, long agoMs) {
		return ArchiveEntry.of(env, env, settled(id, project, agoMs), project);
	}
}
