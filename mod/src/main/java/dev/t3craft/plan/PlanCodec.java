package dev.t3craft.plan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The plan file ({@code config/t3craft/plan.json}) to and from JSON. Versioned and additive: unknown top-level keys
 * and unknown task keys are kept and written back, so an older build never drops what a newer one added.
 *
 * <pre>{@code
 * {"version": 1, "tasks": [{"id": "t-1a2b3c4d", "title": "…", "outcome": "…", "dependsOn": ["…"], "decision": "",
 *   "notes": "", "stage": "draft", "origin": "local", "source": {"envId": "…", "threadId": "…"},
 *   "link": {"envId": "…", "threadId": "…", "launched": true, "linkedAt": 0, "baseline": 0},
 *   "agentChecked": "", "userTested": false, "feedback": "…", "edited": false,
 *   "incoming": {"title": "…", "outcome": "…", "decision": "…", "from": {…}, "at": 0},
 *   "createdAt": 0, "updatedAt": 0}]}
 * }</pre>
 */
public final class PlanCodec {
	public static final int FORMAT = 1;

	private static final Set<String> TASK_KEYS = Set.of("id", "title", "outcome", "dependsOn", "decision", "notes", "stage", "origin",
			"source", "link", "agentChecked", "userTested", "feedback", "edited", "incoming", "createdAt", "updatedAt");
	private static final Set<String> TOP_KEYS = Set.of("version", "tasks");

	private PlanCodec() {
	}

	/** What the file held: its tasks, its version and the top-level keys this build does not know. */
	public record Doc(int version, List<PlanTask> tasks, JsonObject extra) {
		public static final Doc EMPTY = new Doc(FORMAT, List.of(), new JsonObject());
	}

	public static Doc read(@Nullable JsonObject root) {
		if (root == null) {
			return Doc.EMPTY;
		}
		int version = (int) Json.num(root, "version", FORMAT);
		List<PlanTask> tasks = new ArrayList<>();
		JsonArray arr = Json.arr(root, "tasks");
		if (arr != null) {
			for (JsonElement e : arr) {
				PlanTask t = e.isJsonObject() ? task(e.getAsJsonObject()) : null;
				if (t != null && tasks.stream().noneMatch(x -> x.id().equals(t.id()))) {
					tasks.add(t);
				}
			}
		}
		JsonObject extra = new JsonObject();
		for (var en : root.entrySet()) {
			if (!TOP_KEYS.contains(en.getKey())) {
				extra.add(en.getKey(), en.getValue().deepCopy());
			}
		}
		return new Doc(version, tasks, extra);
	}

	public static JsonObject write(Doc doc) {
		JsonObject root = doc.extra().deepCopy();
		root.addProperty("version", Math.max(FORMAT, doc.version()));
		JsonArray arr = new JsonArray();
		for (PlanTask t : doc.tasks()) {
			arr.add(task(t));
		}
		root.add("tasks", arr);
		return root;
	}

	static @Nullable PlanTask task(JsonObject o) {
		String id = Json.str(o, "id");
		if (id == null || id.isBlank()) {
			return null;
		}
		JsonObject extra = new JsonObject();
		for (var en : o.entrySet()) {
			if (!TASK_KEYS.contains(en.getKey())) {
				extra.add(en.getKey(), en.getValue().deepCopy());
			}
		}
		return new PlanTask(id, str(o, "title"), str(o, "outcome"), strings(Json.arr(o, "dependsOn")), str(o, "decision"), str(o, "notes"),
				PlanTask.Stage.fromWire(Json.str(o, "stage"), PlanTask.Stage.DRAFT), PlanTask.Origin.fromWire(Json.str(o, "origin")),
				ref(Json.obj(o, "source")), link(Json.obj(o, "link")), str(o, "agentChecked"), Json.bool(o, "userTested"),
				Json.str(o, "feedback"), Json.bool(o, "edited"), incoming(Json.obj(o, "incoming")), Json.num(o, "createdAt", 0),
				Json.num(o, "updatedAt", 0), extra);
	}

	static JsonObject task(PlanTask t) {
		JsonObject o = t.extra().deepCopy();
		o.addProperty("id", t.id());
		o.addProperty("title", t.title());
		o.addProperty("outcome", t.outcome());
		JsonArray deps = new JsonArray();
		t.dependsOn().forEach(deps::add);
		o.add("dependsOn", deps);
		o.addProperty("decision", t.decision());
		o.addProperty("notes", t.notes());
		o.addProperty("stage", t.stage().wire);
		o.addProperty("origin", t.origin().wire);
		if (t.source() != null) {
			o.add("source", ref(t.source()));
		}
		if (t.link() != null) {
			PlanTask.Link l = t.link();
			JsonObject lo = new JsonObject();
			lo.addProperty("envId", l.envId());
			lo.addProperty("threadId", l.threadId());
			lo.addProperty("launched", l.launched());
			lo.addProperty("linkedAt", l.linkedAt());
			if (l.baseline() != null) {
				lo.addProperty("baseline", l.baseline());
			}
			o.add("link", lo);
		}
		o.addProperty("agentChecked", t.agentChecked());
		o.addProperty("userTested", t.userTested());
		if (t.feedback() != null) {
			o.addProperty("feedback", t.feedback());
		}
		o.addProperty("edited", t.edited());
		if (t.incoming() != null) {
			PlanTask.Incoming in = t.incoming();
			JsonObject io = new JsonObject();
			io.addProperty("title", in.title());
			io.addProperty("outcome", in.outcome());
			io.addProperty("decision", in.decision());
			if (in.from() != null) {
				io.add("from", ref(in.from()));
			}
			io.addProperty("at", in.at());
			o.add("incoming", io);
		}
		o.addProperty("createdAt", t.createdAt());
		o.addProperty("updatedAt", t.updatedAt());
		return o;
	}

	private static String str(JsonObject o, String key) {
		String s = Json.str(o, key);
		return s == null ? "" : s;
	}

	static List<String> strings(@Nullable JsonArray a) {
		List<String> out = new ArrayList<>();
		if (a != null) {
			for (JsonElement e : a) {
				if (e.isJsonPrimitive()) {
					String s = e.getAsString().strip();
					if (!s.isEmpty() && !out.contains(s)) {
						out.add(s);
					}
				}
			}
		}
		return out;
	}

	private static PlanTask.@Nullable ThreadRef ref(@Nullable JsonObject o) {
		String env = Json.str(o, "envId");
		String thread = Json.str(o, "threadId");
		return env == null || thread == null || thread.isBlank() ? null : new PlanTask.ThreadRef(env, thread);
	}

	private static JsonObject ref(PlanTask.ThreadRef r) {
		JsonObject o = new JsonObject();
		o.addProperty("envId", r.envId());
		o.addProperty("threadId", r.threadId());
		return o;
	}

	private static PlanTask.@Nullable Link link(@Nullable JsonObject o) {
		String env = Json.str(o, "envId");
		String thread = Json.str(o, "threadId");
		if (env == null || thread == null || thread.isBlank()) {
			return null;
		}
		Long baseline = o.has("baseline") ? Json.num(o, "baseline", 0) : null;
		return new PlanTask.Link(env, thread, Json.bool(o, "launched"), Json.num(o, "linkedAt", 0), baseline);
	}

	private static PlanTask.@Nullable Incoming incoming(@Nullable JsonObject o) {
		if (o == null) {
			return null;
		}
		return new PlanTask.Incoming(str(o, "title"), str(o, "outcome"), str(o, "decision"), ref(Json.obj(o, "from")), Json.num(o, "at", 0));
	}
}
