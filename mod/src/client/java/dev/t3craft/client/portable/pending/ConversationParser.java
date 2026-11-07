package dev.t3craft.client.portable.pending;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Turns T3's bounded thread snapshot ({@code GET /api/orchestration/threads/:id/bounded} →
 * {@code OrchestrationV2ThreadBoundedSnapshot}: {@code projection.visibleTurnItems[].item},
 * {@code hasMoreHistory}) into conversation lines. Minimal on purpose: messages and plans in full,
 * tools and requests as one line, everything else (runs, nodes, reasoning, checkpoints…) skipped.
 * The full chat (markdown, foldable tools, paging) is feat/chat's screen.
 */
public final class ConversationParser {
	public static final int MAX_LINES = 120;
	public static final int MAX_TEXT = 2000;

	private ConversationParser() {
	}

	public record Parsed(List<Conversation.Line> lines, boolean hasOlder) {
	}

	public static Parsed parse(@Nullable JsonElement body) {
		JsonObject root = body != null && body.isJsonObject() ? body.getAsJsonObject() : null;
		JsonObject projection = Json.obj(root, "projection");
		JsonArray items = Json.arr(projection, "visibleTurnItems");
		List<Conversation.Line> out = new ArrayList<>();
		if (items != null) {
			for (JsonElement e : items) {
				JsonObject wrapper = e.isJsonObject() ? e.getAsJsonObject() : null;
				JsonObject item = Json.obj(wrapper, "item");
				Conversation.Line line = line(item);
				if (line != null) {
					out.add(line);
				}
			}
		}
		boolean older = Json.bool(root, "hasMoreHistory") || out.size() > MAX_LINES;
		if (out.size() > MAX_LINES) {
			out = new ArrayList<>(out.subList(out.size() - MAX_LINES, out.size()));
		}
		return new Parsed(out, older);
	}

	static Conversation.@Nullable Line line(@Nullable JsonObject item) {
		String type = Json.str(item, "type");
		if (type == null) {
			return null;
		}
		String id = String.valueOf(Json.str(item, "id"));
		Long at = Json.time(item, "startedAt");
		if (at == null) {
			at = Json.time(item, "updatedAt");
		}
		boolean streaming = Json.bool(item, "streaming");
		String title = Json.str(item, "title");
		return switch (type) {
			case "user_message" -> Json.bool(item, "workflowScript")
					? new Conversation.Line(id, Conversation.Role.YOU, "(workflow script)", at, false)
					: text(id, Conversation.Role.YOU, Json.str(item, "text"), at, false);
			case "assistant_message" -> text(id, Conversation.Role.AGENT, Json.str(item, "text"), at, streaming);
			case "proposed_plan" -> text(id, Conversation.Role.PLAN, Json.str(item, "markdown"), at, streaming);
			case "command_execution" -> tool(id, "$ " + firstLine(Json.str(item, "input")), at);
			case "file_change" -> {
				String f = String.valueOf(Json.str(item, "fileName"));
				long add = Json.num(item, "additions", -1);
				long del = Json.num(item, "deletions", -1);
				yield tool(id, "edit " + f + (add >= 0 ? " +" + add : "") + (del >= 0 ? " -" + del : ""), at);
			}
			case "web_search", "file_search", "dynamic_tool", "subagent" -> tool(id, title != null ? title : type.replace('_', ' '), at);
			case "approval_request", "user_input_request" -> new Conversation.Line(id, Conversation.Role.NEEDS_YOU,
					title != null ? title : type.equals("user_input_request") ? "Waiting for your answer" : "Approval requested", at, false);
			case "error" -> {
				JsonObject failure = Json.obj(item, "failure");
				String msg = Json.str(failure, "message");
				yield new Conversation.Line(id, Conversation.Role.ERROR, clip(msg != null ? msg : title != null ? title : "Provider error"), at, false);
			}
			case "system_notice", "notification" -> title == null ? null : new Conversation.Line(id, Conversation.Role.SYSTEM, clip(title), at, false);
			default -> null;
		};
	}

	private static Conversation.@Nullable Line text(String id, Conversation.Role role, @Nullable String text, @Nullable Long at, boolean streaming) {
		if (text == null || (text.isBlank() && !streaming)) {
			return null;
		}
		return new Conversation.Line(id, role, clip(text.strip()), at, streaming);
	}

	private static Conversation.Line tool(String id, String text, @Nullable Long at) {
		return new Conversation.Line(id, Conversation.Role.TOOL, clip(text), at, false);
	}

	static String firstLine(@Nullable String s) {
		if (s == null) {
			return "";
		}
		String t = s.strip();
		int nl = t.indexOf('\n');
		return nl < 0 ? t : t.substring(0, nl) + " …";
	}

	static String clip(String s) {
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "… (" + (s.length() - MAX_TEXT) + " more)" : s;
	}
}
