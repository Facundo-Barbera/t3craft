package dev.t3craft.plan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Tasks a coordinator thread proposes in one of its messages, as a fenced block (docs/features/project-plan.md
 * "Propuestas del coordinador"):
 *
 * <pre>
 * ```t3craft-plan
 * {"version": 1, "tasks": [
 *   {"id": "plan-page", "title": "Plan page", "outcome": "The page lists the tasks", "dependsOn": [],
 *    "decision": "", "notes": ""}
 * ]}
 * ```
 * </pre>
 *
 * {@code id} and {@code title} are required; the rest is optional. A bare array of tasks is accepted too. Ids are
 * {@code [A-Za-z0-9._-]}, at most {@value #MAX_ID} characters; texts are cut to their limits. Pure: it only reads
 * text, the store decides what to keep ({@link PlanStore#importProposals}).
 */
public final class PlanProposals {
	public static final String FENCE = "t3craft-plan";
	public static final int MAX_ID = 64;
	public static final int MAX_TITLE = 120;
	public static final int MAX_TEXT = 4_000;
	public static final int MAX_TASKS = 50;

	private static final Pattern BLOCK = Pattern.compile("(?ms)^[ \\t]*```[ \\t]*" + Pattern.quote(FENCE) + "[ \\t]*\\R(.*?)^[ \\t]*```");
	private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_ID + "}");

	private PlanProposals() {
	}

	/** One proposed task, already validated and cut to size. */
	public record Proposed(String id, String title, String outcome, List<String> dependsOn, String decision, String notes) {
	}

	/** What a message held: the tasks, and one line per block or task that was skipped. */
	public record Parsed(List<Proposed> tasks, List<String> problems, int blocks) {
		public boolean found() {
			return blocks > 0;
		}
	}

	/** Every {@code t3craft-plan} block of {@code text}, in order. */
	public static Parsed parse(@Nullable String text) {
		List<Proposed> out = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return new Parsed(out, problems, 0);
		}
		Matcher m = BLOCK.matcher(text);
		int blocks = 0;
		while (m.find()) {
			blocks++;
			JsonElement root;
			try {
				root = JsonParser.parseString(m.group(1));
			} catch (RuntimeException e) {
				problems.add("block " + blocks + ": not valid JSON");
				continue;
			}
			JsonArray tasks = root.isJsonArray() ? root.getAsJsonArray() : root.isJsonObject() ? Json.arr(root.getAsJsonObject(), "tasks") : null;
			if (tasks == null) {
				problems.add("block " + blocks + ": no \"tasks\" array");
				continue;
			}
			for (JsonElement e : tasks) {
				if (out.size() >= MAX_TASKS) {
					problems.add("more than " + MAX_TASKS + " tasks: the rest were skipped");
					break;
				}
				if (!e.isJsonObject()) {
					problems.add("block " + blocks + ": a task is not an object");
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				String id = Json.str(o, "id");
				String title = Json.str(o, "title");
				if (id == null || !ID.matcher(id.strip()).matches()) {
					problems.add("block " + blocks + ": a task has no valid \"id\"");
					continue;
				}
				if (title == null || title.isBlank()) {
					problems.add(id.strip() + ": no \"title\"");
					continue;
				}
				String sid = id.strip();
				if (out.stream().anyMatch(p -> p.id().equals(sid))) {
					problems.add(sid + ": repeated id, the first one is kept");
					continue;
				}
				List<String> deps = new ArrayList<>();
				for (String d : PlanCodec.strings(Json.arr(o, "dependsOn"))) {
					if (ID.matcher(d).matches() && !d.equals(sid)) {
						deps.add(d);
					}
				}
				out.add(new Proposed(sid, cut(title.strip(), MAX_TITLE), cut(Json.str(o, "outcome"), MAX_TEXT), deps,
						cut(Json.str(o, "decision"), MAX_TEXT), cut(Json.str(o, "notes"), MAX_TEXT)));
			}
		}
		return new Parsed(out, problems, blocks);
	}

	/**
	 * The proposal of a thread: the newest of {@code messages} (oldest first, as a transcript) that holds at least one
	 * block. Older blocks are taken as superseded by the newer message.
	 */
	public static Parsed latest(List<String> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			Parsed p = parse(messages.get(i));
			if (p.found()) {
				return p;
			}
		}
		return new Parsed(List.of(), List.of(), 0);
	}

	private static String cut(@Nullable String s, int max) {
		if (s == null) {
			return "";
		}
		String t = s.strip();
		return t.length() <= max ? t : t.substring(0, max);
	}
}
