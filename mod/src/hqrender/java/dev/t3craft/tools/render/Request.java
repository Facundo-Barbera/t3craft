package dev.t3craft.tools.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One render request, as an agent leaves it in {@code inbox/<agent>-<n>.json}:
 *
 * <pre>
 * { "worktree": "/abs/path/of/the/worktree",
 *   "kind": "hq" | "ui",
 *   "priority": "preview" | "final",            // default preview
 *   "args": ["--layout", "hybrid-ringcampus", "--projects", "4,10", "--only", "citadel"],
 *   // or, instead of / besides raw args (turned into the same flags, args last):
 *   "layout": "hybrid-ringcampus", "projects": [4, 10], "view": "citadel", "region": "...", "piece": "...",
 *   "scale": 1.0, "packs": "/abs/dir" }
 * </pre>
 */
record Request(String id, String agent, Path worktree, String kind, boolean preview, List<String> args, List<String> notes) {
	static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");
	private static final Pattern TRAILING_N = Pattern.compile("^(.*?)-\\d+$");

	/** structured field → flag (value arrays are comma-joined). */
	private static final Map<String, String> FIELDS = new java.util.LinkedHashMap<>();

	static {
		// fixed order: the same request always yields the same argument list (and so the same cache key)
		FIELDS.put("layout", "--layout");
		FIELDS.put("style", "--style");
		FIELDS.put("projects", "--projects");
		FIELDS.put("view", "--only");
		FIELDS.put("only", "--only");
		FIELDS.put("region", "--region");
		FIELDS.put("piece", "--piece");
		FIELDS.put("scale", "--scale");
		FIELDS.put("ss", "--ss");
		FIELDS.put("packs", "--packs");
	}

	/** The agent part of {@code <agent>-<n>}. */
	static String agentOf(String id) {
		var m = TRAILING_N.matcher(id);
		return m.matches() ? m.group(1) : id;
	}

	static Request parse(String id, String json) {
		JsonObject o;
		try {
			JsonElement e = JsonParser.parseString(json);
			if (!e.isJsonObject()) {
				throw new IllegalArgumentException("the request must be a JSON object");
			}
			o = e.getAsJsonObject();
		} catch (RuntimeException e) {
			if (e instanceof IllegalArgumentException) {
				throw e;
			}
			throw new IllegalArgumentException("not valid JSON: " + e.getMessage());
		}
		String wt = str(o, "worktree");
		if (wt == null || wt.isBlank()) {
			throw new IllegalArgumentException("missing \"worktree\" (absolute path of your worktree)");
		}
		Path worktree = Path.of(wt);
		if (!worktree.isAbsolute()) {
			throw new IllegalArgumentException("\"worktree\" must be an absolute path: " + wt);
		}
		String kind = str(o, "kind");
		kind = kind == null ? "hq" : kind.toLowerCase(Locale.ROOT);
		if (!kind.equals("hq") && !kind.equals("ui")) {
			throw new IllegalArgumentException("\"kind\" must be hq or ui, not " + kind);
		}
		String pr = str(o, "priority");
		pr = pr == null ? "preview" : pr.toLowerCase(Locale.ROOT);
		if (!pr.equals("preview") && !pr.equals("final")) {
			throw new IllegalArgumentException("\"priority\" must be preview or final, not " + pr);
		}
		List<String> args = new ArrayList<>();
		for (Map.Entry<String, String> f : FIELDS.entrySet()) {
			if (o.has(f.getKey()) && !o.get(f.getKey()).isJsonNull()) {
				args.add(f.getValue());
				args.add(value(o.get(f.getKey())));
			}
		}
		for (String flag : new String[] {"vanilla", "no-npcs"}) {
			if (o.has(flag) && o.get(flag).isJsonPrimitive() && o.get(flag).getAsBoolean()) {
				args.add("--" + flag);
			}
		}
		if (o.has("args")) {
			if (!o.get("args").isJsonArray()) {
				throw new IllegalArgumentException("\"args\" must be an array of strings");
			}
			for (JsonElement a : o.getAsJsonArray("args")) {
				args.add(value(a));
			}
		}
		List<String> notes = new ArrayList<>();
		args = stripOut(args, notes);
		for (String a : args) {
			if (a.length() > 600 || a.indexOf('\0') >= 0) {
				throw new IllegalArgumentException("argument too long or invalid");
			}
		}
		if (args.size() > 60) {
			throw new IllegalArgumentException("too many arguments (" + args.size() + ")");
		}
		return new Request(id, agentOf(id), worktree, kind, pr.equals("preview"), List.copyOf(args), List.copyOf(notes));
	}

	/** The daemon owns the output folder: {@code --out X} from the agent is dropped (and said so in the report). */
	private static List<String> stripOut(List<String> args, List<String> notes) {
		List<String> r = new ArrayList<>();
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).equals("--out")) {
				i++;
				notes.add("--out ignored: results always go to the request's outbox folder");
			} else {
				r.add(args.get(i));
			}
		}
		return r;
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static String value(JsonElement e) {
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			List<String> parts = new ArrayList<>();
			for (JsonElement x : a) {
				parts.add(x.getAsString());
			}
			return String.join(",", parts);
		}
		return e.getAsString();
	}

	/** Reads and parses an inbox file. */
	static Request read(Path file) throws java.io.IOException {
		String name = file.getFileName().toString();
		String id = name.substring(0, name.length() - ".json".length());
		return parse(id, Files.readString(file));
	}
}
