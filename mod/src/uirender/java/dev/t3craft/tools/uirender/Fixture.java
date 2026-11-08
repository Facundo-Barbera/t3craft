package dev.t3craft.tools.uirender;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * A scenario's mock data, read from {@code fixtures/<name>.json} (see docs/tools/ui-render.md for the
 * schema). It becomes the same models the screens consume: {@link ThreadRow}s for the sidebar,
 * T3 {@code subscribeThread} snapshots for the conversations, environments, projects and the
 * {@code server.getConfig} providers for the model picker. No network, no T3.
 */
final class Fixture {
	record Env(String id, String label, String status, boolean control, @Nullable Integer color) {
	}

	record Project(String env, String id, String title) {
	}

	final String name;
	final JsonObject raw;
	final String description;
	/** {@code t3} (the main screen) or {@code new-thread} (the dialog over it). */
	final String screen;
	final List<Env> envs = new ArrayList<>();
	final List<Project> projects = new ArrayList<>();
	final List<ThreadRow> rows = new ArrayList<>();
	/** {@code env:threadId} → the {@code snapshot} stream item of its conversation. */
	final Map<String, JsonObject> snapshots = new LinkedHashMap<>();
	final @Nullable String selected;
	final JsonObject serverConfig;
	final @Nullable JsonObject composer;
	final JsonArray steps;
	final int ticks;
	/** The rows come from the disk cache of an earlier session (the sidebar says so). */
	final boolean cached;

	private Fixture(String name, JsonObject raw) {
		this.name = name;
		this.raw = raw;
		this.description = str(raw, "description", "");
		this.screen = str(raw, "screen", "t3");
		this.selected = raw.has("selected") && !raw.get("selected").isJsonNull() ? raw.get("selected").getAsString() : null;
		JsonObject defaults = Assets.json("fixtures/_server-config.json");
		this.serverConfig = raw.has("serverConfig") && raw.get("serverConfig").isJsonObject() ? raw.getAsJsonObject("serverConfig")
				: defaults != null ? defaults : new JsonObject();
		this.composer = raw.has("composer") && raw.get("composer").isJsonObject() ? raw.getAsJsonObject("composer") : null;
		this.steps = raw.has("steps") && raw.get("steps").isJsonArray() ? raw.getAsJsonArray("steps") : new JsonArray();
		this.ticks = raw.has("ticks") ? raw.get("ticks").getAsInt() : 2;
		this.cached = raw.has("cached") && raw.get("cached").getAsBoolean();
	}

	static String str(JsonObject o, String key, String def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
	}

	static boolean bool(JsonObject o, String key, boolean def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : def;
	}

	static int num(JsonObject o, String key, int def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
	}

	static @Nullable Fixture load(String name) {
		JsonObject raw = Assets.json("fixtures/" + name + ".json");
		if (raw == null) {
			return null;
		}
		Fixture f = new Fixture(name, raw);
		f.read();
		return f;
	}

	/** Names of the bundled scenarios, from {@code fixtures/index.json}. */
	static List<String> names() {
		List<String> out = new ArrayList<>();
		JsonObject idx = Assets.json("fixtures/index.json");
		if (idx != null && idx.has("scenarios")) {
			for (JsonElement e : idx.getAsJsonArray("scenarios")) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	// ---- parsing ---------------------------------------------------------------------------------

	private void read() {
		for (JsonElement e : arr(raw, "environments")) {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			envs.add(new Env(id, str(o, "label", id), str(o, "status", "ok"), bool(o, "control", true), color(o)));
		}
		for (JsonElement e : arr(raw, "projects")) {
			JsonObject o = e.getAsJsonObject();
			projects.add(new Project(o.get("env").getAsString(), o.get("id").getAsString(), o.get("title").getAsString()));
		}
		JsonObject transcripts = raw.has("transcripts") && raw.get("transcripts").isJsonObject() ? raw.getAsJsonObject("transcripts") : new JsonObject();
		long now = System.currentTimeMillis();
		List<JsonObject> threads = new ArrayList<>();
		for (JsonElement e : arr(raw, "threads")) {
			threads.add(e.getAsJsonObject());
		}
		if (raw.has("generate") && raw.get("generate").isJsonObject()) {
			threads.addAll(generate(raw.getAsJsonObject("generate")));
		}
		for (JsonObject t : threads) {
			String envId = t.get("env").getAsString();
			String id = t.get("id").getAsString();
			Env env = envs.stream().filter(x -> x.id().equals(envId)).findFirst().orElse(null);
			String projectId = str(t, "project", "");
			String projectTitle = projects.stream().filter(p -> p.env().equals(envId) && p.id().equals(projectId)).map(Project::title)
					.findFirst().orElse(projectId);
			ThreadState state = state(str(t, "state", "idle"));
			boolean pending = bool(t, "pending", state == ThreadState.NEEDS_ATTENTION);
			Long at = t.has("agoMin") ? now - (long) (t.get("agoMin").getAsDouble() * 60_000L) : null;
			String key = envId + ":" + id;
			rows.add(new ThreadRow(key, envId, env == null ? envId : env.label(), id, str(t, "title", id), projectTitle, state, pending,
					t.has("activity") ? t.get("activity").getAsString() : null, bool(t, "settled", false), at, bool(t, "pinned", false)));
			JsonObject tr = t.has("transcript") && transcripts.has(t.get("transcript").getAsString())
					? transcripts.getAsJsonObject(t.get("transcript").getAsString()) : null;
			snapshots.put(key, snapshot(id, str(t, "title", id), tr));
		}
	}

	private static @Nullable Integer color(JsonObject o) {
		if (!o.has("color") || o.get("color").isJsonNull()) {
			return null;
		}
		String c = o.get("color").getAsString().replace("#", "");
		try {
			return 0xFF000000 | Integer.parseInt(c, 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static ThreadState state(String wire) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(wire)) {
				return s;
			}
		}
		return ThreadState.IDLE;
	}

	private static JsonArray arr(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	/**
	 * A {@code subscribeThread} snapshot item: {@code {kind:"snapshot", projection:{thread, runs,
	 * runtimeRequests, subagents, visibleTurnItems:[{visibility, sourceThreadId, item}]}}}, the shape
	 * {@code ChatTranscript} decodes. Items get their ordinal from their position when they have none.
	 */
	private static JsonObject snapshot(String threadId, String title, @Nullable JsonObject tr) {
		JsonObject snap = new JsonObject();
		snap.addProperty("kind", "snapshot");
		snap.addProperty("snapshotSequence", 100);
		snap.addProperty("hasMoreHistory", tr != null && bool(tr, "hasMoreHistory", false));
		if (tr != null && tr.has("historyCursor")) {
			snap.add("historyCursor", tr.get("historyCursor"));
		}
		JsonObject projection = new JsonObject();
		JsonObject thread = new JsonObject();
		thread.addProperty("title", tr != null ? str(tr, "title", title) : title);
		projection.add("thread", thread);
		projection.add("runs", tr != null && tr.has("runs") ? tr.get("runs") : new JsonArray());
		projection.add("runtimeRequests", tr != null && tr.has("runtimeRequests") ? tr.get("runtimeRequests") : new JsonArray());
		projection.add("subagents", tr != null && tr.has("subagents") ? tr.get("subagents") : new JsonArray());
		JsonArray rows = new JsonArray();
		if (tr != null) {
			int ordinal = 0;
			for (JsonElement e : arr(tr, "items")) {
				JsonObject item = e.getAsJsonObject().deepCopy();
				ordinal++;
				if (!item.has("ordinal")) {
					item.addProperty("ordinal", ordinal);
				}
				if (!item.has("status")) {
					item.addProperty("status", "completed");
				}
				if (!item.has("id")) {
					item.addProperty("id", "item-" + ordinal);
				}
				String type = str(item, "type", "");
				if (!item.has("messageId") && (type.equals("user_message") || type.equals("assistant_message"))) {
					item.addProperty("messageId", "msg-" + item.get("id").getAsString());
				}
				JsonObject row = new JsonObject();
				row.addProperty("visibility", "own");
				row.addProperty("sourceThreadId", threadId);
				row.add("item", item);
				rows.add(row);
			}
		}
		projection.add("visibleTurnItems", rows);
		snap.add("projection", projection);
		return snap;
	}

	// ---- procedural threads (many-threads) ----------------------------------------------------------

	private static final String[] VERBS = {"Fix", "Refactor", "Add", "Investigate", "Polish", "Migrate", "Document", "Profile", "Review",
			"Wire up", "Rename", "Speed up"};
	private static final String[] NOUNS = {"login redirect", "thread cache", "sidebar sorting", "model picker", "HQ lighting", "bridge auth",
			"markdown tables", "composer drafts", "NPC pathing", "render service", "settings screen", "attachment upload", "search index",
			"worktree cleanup", "keybind conflicts", "lang keys"};

	/**
	 * {@code {"threads": N, "seed": S, "pinned": P, "settledPct": X}}: N threads spread over the fixture's
	 * environments and projects with every state, relative times from minutes to days, P pinned and
	 * about X % settled. Deterministic for a seed.
	 */
	private List<JsonObject> generate(JsonObject spec) {
		int n = num(spec, "threads", 40);
		Random rnd = new Random(num(spec, "seed", 7));
		int pinned = num(spec, "pinned", 3);
		int settledPct = num(spec, "settledPct", 25);
		String[] states = {"working", "done", "done", "done", "idle", "idle", "needs_attention", "error"};
		List<JsonObject> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Project p = projects.get(rnd.nextInt(projects.size()));
			JsonObject t = new JsonObject();
			t.addProperty("env", p.env());
			t.addProperty("id", "gen-" + i);
			t.addProperty("project", p.id());
			t.addProperty("title", VERBS[rnd.nextInt(VERBS.length)] + " " + NOUNS[rnd.nextInt(NOUNS.length)] + (rnd.nextInt(5) == 0 ? " (" + (i + 1) + ")" : ""));
			String st = states[rnd.nextInt(states.length)];
			t.addProperty("state", st);
			if (st.equals("working")) {
				t.addProperty("activity", rnd.nextBoolean() ? "running tests" : "editing files");
			}
			boolean settled = rnd.nextInt(100) < settledPct && !st.equals("working") && !st.equals("needs_attention");
			t.addProperty("settled", settled);
			t.addProperty("pinned", i < pinned);
			double ago = i < 6 ? 1 + rnd.nextInt(50) : Math.pow(1.09, i) * (3 + rnd.nextInt(10));
			t.addProperty("agoMin", ago);
			out.add(t);
		}
		return out;
	}
}
