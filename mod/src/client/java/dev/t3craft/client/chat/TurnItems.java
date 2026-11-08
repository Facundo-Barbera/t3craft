package dev.t3craft.client.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * T3 turn item JSON ({@code OrchestrationV2TurnItem}, discriminated by {@code type}) → {@link ChatEntry}.
 * Tolerant: unknown types become a short system row when they carry a title, otherwise they are
 * skipped. Tool output is capped so a huge log cannot freeze the screen.
 */
public final class TurnItems {
	static final int MAX_DETAIL_CHARS = 8_000;
	static final int MAX_BODY_CHARS = 60_000;

	private TurnItems() {
	}

	public static @Nullable ChatEntry decode(@Nullable JsonObject item, @Nullable String sourceThreadId, boolean inherited) {
		String id = Json.str(item, "id");
		if (item == null || id == null || id.isEmpty()) {
			return null;
		}
		String type = String.valueOf(Json.str(item, "type"));
		String source = sourceThreadId != null ? sourceThreadId : String.valueOf(Json.str(item, "threadId"));
		String status = orEmpty(Json.str(item, "status"));
		String title = orEmpty(Json.str(item, "title"));
		Long at = firstTime(item, "completedAt", "startedAt", "updatedAt", "createdAt");
		long ordinal = Json.num(item, "ordinal", 0);
		boolean streaming = Json.bool(item, "streaming");
		Kind kind;
		String head;
		String body;
		String detail = null;
		String messageId = null;
		switch (type) {
			case "user_message" -> {
				kind = Kind.USER;
				messageId = Json.str(item, "messageId");
				// feat/t3-composer: T3 marks messages that steered the running turn ("↪ Steer")
				String intent = Json.str(item, "inputIntent");
				head = "steer".equals(intent) || "promoted_queued_to_steer".equals(intent) ? ChatEntry.STEER : "";
				body = orEmpty(Json.str(item, "text"));
				int n = size(Json.arr(item, "attachments"));
				if (n > 0) {
					body += "\n\n_(" + n + " attachment" + (n == 1 ? "" : "s") + ")_";
				}
			}
			case "assistant_message" -> {
				kind = Kind.ASSISTANT;
				messageId = Json.str(item, "messageId");
				head = "";
				body = orEmpty(Json.str(item, "text"));
			}
			case "reasoning" -> {
				kind = Kind.REASONING;
				head = title.isBlank() ? "Thinking" : title;
				body = orEmpty(Json.str(item, "text"));
			}
			case "proposed_plan" -> {
				kind = Kind.PLAN;
				head = title.isBlank() ? "Proposed plan" : title;
				body = orEmpty(Json.str(item, "markdown"));
			}
			case "todo_list" -> {
				kind = Kind.TODO;
				head = title.isBlank() ? "To-do" : title;
				StringBuilder b = new StringBuilder();
				String explanation = Json.str(item, "explanation");
				if (explanation != null && !explanation.isBlank()) {
					b.append(explanation.strip()).append("\n\n");
				}
				for (JsonObject step : objects(Json.arr(item, "steps"))) {
					String s = Json.str(step, "status");
					String box = "completed".equals(s) ? "[x]" : "[ ]";
					b.append("- ").append(box).append(' ').append(orEmpty(Json.str(step, "text")))
							.append("running".equals(s) ? " _(in progress)_" : "").append('\n');
				}
				body = b.toString().strip();
			}
			case "user_input_request" -> {
				kind = Kind.REQUEST;
				head = "Question for you";
				StringBuilder b = new StringBuilder();
				for (JsonObject q : objects(Json.arr(item, "questions"))) {
					String header = Json.str(q, "header");
					if (header != null && !header.isBlank()) {
						b.append("**").append(header.strip()).append("**\n");
					}
					b.append(orEmpty(Json.str(q, "question"))).append('\n');
					for (JsonObject o : objects(Json.arr(q, "options"))) {
						b.append("- ").append(orEmpty(Json.str(o, "label")));
						String d = Json.str(o, "description");
						if (d != null && !d.isBlank()) {
							b.append(": ").append(d.strip());
						}
						b.append('\n');
					}
					b.append('\n');
				}
				body = b.toString().strip();
				if (Json.obj(item, "questionAnswer") != null) {
					body += "\n\n_(answered)_";
				}
			}
			case "approval_request" -> {
				kind = Kind.REQUEST;
				head = "Approval: " + orElse(Json.str(item, "requestKind"), "permission");
				StringBuilder b = new StringBuilder();
				appendLine(b, Json.str(item, "prompt"));
				appendLine(b, Json.str(item, "reason"));
				body = b.toString().strip();
			}
			case "file_change" -> {
				kind = Kind.TOOL;
				long add = Json.num(item, "additions", -1);
				long del = Json.num(item, "deletions", -1);
				head = "Edit " + orEmpty(Json.str(item, "fileName")) + (add >= 0 || del >= 0
						? " (+" + Math.max(add, 0) + " −" + Math.max(del, 0) + ")" : "");
				body = "";
				String diff = Json.str(item, "diffStr");
				if (diff == null) {
					StringBuilder b = new StringBuilder();
					for (JsonObject c : objects(Json.arr(item, "changes"))) {
						b.append(orEmpty(Json.str(c, "operation"))).append(' ').append(orEmpty(Json.str(c, "path"))).append('\n');
					}
					diff = b.isEmpty() ? null : b.toString();
				}
				detail = diff;
			}
			case "command_execution" -> {
				kind = Kind.TOOL;
				head = "$ " + firstLine(orEmpty(Json.str(item, "input")));
				body = "";
				StringBuilder b = new StringBuilder();
				String input = orEmpty(Json.str(item, "input"));
				if (input.contains("\n")) {
					b.append("$ ").append(input).append("\n\n");
				}
				String output = Json.str(item, "output");
				if (output != null) {
					b.append(output);
				} else if (Json.bool(item, "outputOmitted")) {
					b.append("(output not sent; open the thread in T3 to see it)");
				}
				long exit = Json.num(item, "exitCode", Long.MIN_VALUE);
				if (exit != Long.MIN_VALUE) {
					head += "  [exit " + exit + "]";
				}
				detail = b.isEmpty() ? null : b.toString();
			}
			case "file_search" -> {
				kind = Kind.TOOL;
				head = "Search " + orElse(Json.str(item, "pattern"), title);
				body = "";
				StringBuilder b = new StringBuilder();
				for (JsonObject r : objects(Json.arr(item, "results"))) {
					b.append(orEmpty(Json.str(r, "fileName")));
					long line = Json.num(r, "line", -1);
					if (line > 0) {
						b.append(':').append(line);
					}
					String p = Json.str(r, "preview");
					if (p != null && !p.isBlank()) {
						b.append("  ").append(p.strip());
					}
					b.append('\n');
				}
				detail = b.isEmpty() ? null : b.toString();
			}
			case "web_search" -> {
				kind = Kind.TOOL;
				head = "Web search " + String.join(" · ", strings(Json.arr(item, "patterns")));
				body = "";
				StringBuilder b = new StringBuilder();
				for (JsonObject r : objects(Json.arr(item, "results"))) {
					String t = Json.str(r, "title");
					String u = Json.str(r, "url");
					b.append(t == null ? "" : t + " ").append(u == null ? "" : u).append('\n');
				}
				detail = b.isEmpty() ? null : b.toString();
			}
			case "dynamic_tool" -> {
				kind = Kind.TOOL;
				head = orElse(Json.str(item, "toolName"), orElse(title, "Tool call"));
				body = "";
				StringBuilder b = new StringBuilder();
				JsonElement in = item.get("input");
				if (in != null && !in.isJsonNull()) {
					b.append("input: ").append(compact(in)).append('\n');
				}
				JsonElement out = item.get("output");
				if (out != null && !out.isJsonNull()) {
					b.append("output: ").append(compact(out));
				} else if (Json.bool(item, "outputOmitted")) {
					b.append("(output not sent)");
				}
				detail = b.isEmpty() ? null : b.toString();
			}
			case "subagent" -> {
				kind = Kind.SUBAGENT;
				String prompt = orEmpty(Json.str(item, "prompt"));
				head = title.isBlank() ? "Subagent: " + firstLine(prompt) : "Subagent: " + title;
				body = "";
				StringBuilder b = new StringBuilder();
				if (!prompt.isBlank()) {
					b.append("Task: ").append(prompt.strip()).append("\n\n");
				}
				appendLine(b, Json.str(item, "progress"));
				String result = Json.str(item, "result");
				if (result != null && !result.isBlank()) {
					b.append("Result: ").append(result.strip());
				}
				detail = b.isEmpty() ? null : b.toString().strip();
			}
			case "error" -> {
				kind = Kind.ERROR;
				JsonObject failure = Json.obj(item, "failure");
				head = orElse(Json.str(failure, "class"), "error");
				body = orElse(Json.str(failure, "message"), title.isBlank() ? "Error" : title);
			}
			case "notification" -> {
				kind = Kind.SYSTEM;
				head = orElse(Json.str(item, "outcome"), "notification");
				body = orEmpty(Json.str(item, "summary"));
				String d = Json.str(item, "detail");
				if (d != null && !d.isBlank()) {
					body += "\n" + d.strip();
				}
			}
			case "system_notice", "run_interrupt_request", "run_interrupt_result" -> {
				kind = Kind.SYSTEM;
				head = "run_interrupt_request".equals(type) ? "Interrupt requested" : "run_interrupt_result".equals(type) ? "Interrupted" : "Notice";
				body = orEmpty(Json.str(item, "message"));
			}
			case "compaction" -> {
				kind = Kind.SYSTEM;
				head = "Context compacted";
				body = orEmpty(Json.str(item, "summary"));
			}
			case "handoff" -> {
				kind = Kind.SYSTEM;
				head = "Handoff" + (Json.str(item, "toModel") == null ? "" : " → " + Json.str(item, "toModel"));
				body = orEmpty(Json.str(item, "summary"));
			}
			case "checkpoint" -> {
				kind = Kind.SYSTEM;
				head = "Checkpoint";
				body = size(Json.arr(item, "files")) + " file(s)";
			}
			case "fork", "thread_created" -> {
				kind = Kind.SYSTEM;
				head = "fork".equals(type) ? "Forked" : "Thread created";
				body = orElse(title, orEmpty(Json.str(item, "targetModel")));
			}
			case "secret_request", "link_request" -> {
				kind = Kind.REQUEST;
				head = "secret_request".equals(type) ? "Secret requested: " + orEmpty(Json.str(item, "label")) : "Link requested";
				StringBuilder b = new StringBuilder();
				appendLine(b, Json.str(item, "reason"));
				appendLine(b, Json.str(item, "url"));
				body = b.toString().strip();
			}
			default -> {
				if (title.isBlank()) {
					return null;
				}
				kind = Kind.SYSTEM;
				head = type;
				body = title;
			}
		}
		return new ChatEntry(source + ":" + id, id, messageId, kind, clip(head, 200), clip(body, MAX_BODY_CHARS),
				detail == null ? null : clip(detail, MAX_DETAIL_CHARS), streaming, status, at, ordinal, inherited);
	}

	/** {@code OrchestrationV2ProjectedTurnItem} row ({@code {visibility, sourceThreadId, item}}). */
	public static @Nullable ChatEntry decodeRow(@Nullable JsonObject row, String threadId) {
		if (row == null) {
			return null;
		}
		String source = Json.str(row, "sourceThreadId");
		String visibility = Json.str(row, "visibility");
		boolean inherited = "inherited".equals(visibility) || (source != null && !source.equals(threadId));
		return decode(Json.obj(row, "item"), source, inherited);
	}

	static String clip(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max) + "\n… (" + (s.length() - max) + " more characters)";
	}

	static String firstLine(String s) {
		String t = s.strip();
		int nl = t.indexOf('\n');
		String line = nl < 0 ? t : t.substring(0, nl) + " …";
		return line.length() > 120 ? line.substring(0, 119) + "…" : line;
	}

	private static String compact(JsonElement e) {
		String s = e.isJsonPrimitive() ? e.getAsString() : e.toString();
		return s.length() > 2_000 ? s.substring(0, 2_000) + "…" : s;
	}

	private static void appendLine(StringBuilder b, @Nullable String s) {
		if (s != null && !s.isBlank()) {
			b.append(s.strip()).append("\n\n");
		}
	}

	private static @Nullable Long firstTime(JsonObject o, String... keys) {
		for (String k : keys) {
			Long t = Json.time(o, k);
			if (t != null) {
				return t;
			}
		}
		return null;
	}

	static List<JsonObject> objects(@Nullable JsonArray a) {
		List<JsonObject> out = new ArrayList<>();
		if (a != null) {
			for (JsonElement e : a) {
				if (e.isJsonObject()) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	private static List<String> strings(@Nullable JsonArray a) {
		List<String> out = new ArrayList<>();
		if (a != null) {
			for (JsonElement e : a) {
				if (e.isJsonPrimitive()) {
					out.add(e.getAsString());
				}
			}
		}
		return out;
	}

	private static int size(@Nullable JsonArray a) {
		return a == null ? 0 : a.size();
	}

	private static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}

	private static String orElse(@Nullable String s, String def) {
		return s == null || s.isBlank() ? def : s;
	}
}
