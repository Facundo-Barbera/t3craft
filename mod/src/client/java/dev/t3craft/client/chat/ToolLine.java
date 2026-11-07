// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Tool line split (icon + name + argument), icon choice and the test-command pattern follow
// AgentCraft's mod/src/client/java/dev/agentcraft/client/monitor/LogRows.java (tool(), iconFor()).
package dev.t3craft.client.chat;

import dev.t3craft.client.chat.ChatEntry.Kind;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How one tool-like entry reads on a single feed line, AgentCraft monitor style: a 12 px kit icon,
 * the tool name in the tool colour and its argument after it. Pure Java (tested).
 *
 * @param icon kit icon name: bash decision edit git memory merge message read test
 * @param name tool name ("$", "Edit", "Search", "Subagent", "thinking", or the dynamic tool's name)
 * @param arg  the rest (command, path, pattern...), may be empty
 */
public record ToolLine(String icon, String name, String arg) {
	static final Pattern TEST_CMD = Pattern.compile("\\b(npm|pnpm|yarn|bun)( run)? test\\b|\\bvitest\\b|\\bjest\\b|\\bpytest\\b|\\bcargo test\\b"
			+ "|\\bgo test\\b|\\bgradlew\\S* (test|check)\\b|\\bnode --test\\b");

	public static ToolLine of(ChatEntry e) {
		if (e.kind() == Kind.REASONING) {
			String first = firstLine(e.body());
			return new ToolLine("memory", "thinking", first);
		}
		String t = firstLine(e.title());
		if (e.kind() == Kind.SUBAGENT) {
			String arg = t.startsWith("Subagent:") ? t.substring("Subagent:".length()).strip() : t;
			return new ToolLine("message", "Subagent", arg);
		}
		if (t.startsWith("$")) {
			String arg = t.substring(1).strip();
			if (arg.endsWith("[exit 0]")) {
				arg = arg.substring(0, arg.length() - "[exit 0]".length()).strip(); // success says nothing new
			}
			String cmd = arg.toLowerCase(Locale.ROOT);
			String icon = TEST_CMD.matcher(cmd).find() ? "test" : cmd.startsWith("git ") ? "git" : "bash";
			return new ToolLine(icon, "$", arg);
		}
		if (t.startsWith("Web search")) {
			return new ToolLine("read", "Web search", t.substring("Web search".length()).strip());
		}
		int sp = indexOfAny(t, ' ', ':');
		String name = sp < 0 ? t : t.substring(0, sp);
		String arg = sp < 0 ? "" : t.substring(sp + 1).strip();
		if (name.isEmpty()) {
			name = e.kind().role;
		}
		return new ToolLine(iconFor(name), name, arg);
	}

	/** Kit icon for a tool name (AgentCraft's mapping plus T3's own item names). */
	public static String iconFor(String tool) {
		return switch (tool) {
			case "Read", "NotebookRead", "Grep", "Glob", "LS", "WebFetch", "WebSearch", "Search" -> "read";
			case "Edit", "MultiEdit", "Write", "NotebookEdit" -> "edit";
			case "read_memory", "write_memory", "create_task", "update_task", "TodoWrite", "report_status" -> "memory";
			case "send_message", "Task", "Agent" -> "message";
			case "ask_user", "Approval" -> "decision";
			case "request_merge", "diff", "merge" -> "merge";
			case "git" -> "git";
			default -> "bash";
		};
	}

	/**
	 * Short label for a long snake_case tool name on narrow screens (the icon already says what kind
	 * of tool it is): request_merge → merge, create_task → new task, ...
	 */
	public static String shortName(String tool) {
		return switch (tool) {
			case "request_merge" -> "merge";
			case "create_task" -> "new task";
			case "update_task" -> "task";
			case "send_message" -> "message";
			case "read_memory" -> "read";
			case "write_memory" -> "write";
			case "report_status" -> "status";
			case "ask_user" -> "ask";
			default -> tool.replace('_', ' ');
		};
	}

	static String firstLine(String s) {
		String t = s == null ? "" : s.strip();
		int nl = t.indexOf('\n');
		return nl < 0 ? t : t.substring(0, nl).strip();
	}

	private static int indexOfAny(String s, char a, char b) {
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == a || ch == b) {
				return i;
			}
		}
		return -1;
	}
}
