package dev.t3craft.t3;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;

/** Synthetic T3 wire JSON (shapes from the vendored contracts). */
final class Fixtures {
	static final long NOW = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli();
	static final long H = 3_600_000L;

	private Fixtures() {
	}

	static String iso(long msAgo) {
		return Instant.ofEpochMilli(NOW - msAgo).toString();
	}

	/** A thread shell JSON whose last activity was {@code ago} ms before NOW; {@code extra} is merged. */
	static JsonObject threadJson(String id, long ago, String extra) {
		String base = """
				{"createdBy":"user","creationSource":"web","id":"%s","projectId":"project-1","title":"Thread %s",
				 "providerInstanceId":"claudeAgent","modelSelection":{"instanceId":"claudeAgent","model":"m"},
				 "runtimeMode":"approval-required","interactionMode":"default","branch":null,"worktreePath":null,
				 "lineage":{"parentThreadId":null,"relationshipToParent":null,"rootThreadId":"%s"},
				 "forkedFrom":null,"activeProviderThreadId":null,"latestRunId":null,"activeRunId":null,"status":"idle",
				 "pendingRuntimeRequest":null,"latestVisibleMessage":null,"latestUserMessageAt":"%s",
				 "hasActionableProposedPlan":false,"itemCount":0,"visibleItemCount":0,
				 "createdAt":"%s","updatedAt":"%s","archivedAt":null,"settledOverride":null,"settledAt":null,"deletedAt":null}
				""".formatted(id, id, id, iso(ago), iso(ago), iso(ago));
		JsonObject o = JsonParser.parseString(base).getAsJsonObject();
		if (extra != null && !extra.isBlank()) {
			JsonObject x = JsonParser.parseString(extra).getAsJsonObject();
			x.entrySet().forEach(e -> o.add(e.getKey(), e.getValue()));
		}
		return o;
	}

	static ThreadShell thread(String id, long ago, String extra) {
		return ThreadShell.fromJson(threadJson(id, ago, extra));
	}

	static ThreadShell thread(String id, long ago) {
		return thread(id, ago, "");
	}

	static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	static final String PROJECT = """
			{"id":"project-1","title":"Synthetic project","workspaceRoot":"/srv/p1","repositoryIdentity":null,
			 "defaultModelSelection":null,"scripts":[],"createdAt":"2026-11-01T09:00:00Z","updatedAt":"2026-11-01T09:00:00Z","deletedAt":null}
			""";

	static JsonObject authoritative(long seq, JsonObject... threads) {
		JsonObject item = json("{\"kind\":\"snapshot\",\"snapshot\":{\"schemaVersion\":2,\"snapshotSequence\":" + seq
				+ ",\"threads\":[],\"archivedThreads\":[],\"projects\":[" + PROJECT + "]}}");
		for (JsonObject t : threads) {
			item.getAsJsonObject("snapshot").getAsJsonArray("threads").add(t);
		}
		return item;
	}

	static JsonObject enrichment(long seq) {
		return json("""
				{"kind":"snapshot","snapshot":{"schemaVersion":2,"snapshotSequence":%d,"threads":[],"archivedThreads":[],
				 "projects":[{"id":"project-1","title":"Synthetic project","workspaceRoot":"/srv/p1",
				   "repositoryIdentity":{"canonicalKey":"github.com/s/p1"},"defaultModelSelection":null,"scripts":[],
				   "createdAt":"2026-11-01T09:00:00Z","updatedAt":"2026-11-01T09:00:00Z","deletedAt":null}]},
				 "resolvedRepositoryIdentityRoots":["/srv/p1"]}
				""".formatted(seq));
	}
}
