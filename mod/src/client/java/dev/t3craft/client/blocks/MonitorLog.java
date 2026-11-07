// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Row model (tool rows with kit icon + name + argument that never wrap, prose that wraps a little,
// diff counts in green/red, a tail with the newest row at the bottom, the caret while working and
// "waiting for you") follows AgentCraft's LogRows and MonitorScreen
// (mod/src/client/java/dev/agentcraft/client/monitor/).
package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.chat.ToolLine;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a monitor's live log shows: a tail of one-line rows built from the thread's transcript (the
 * same {@link ChatEntry} rows and {@link ToolLine} split the chat uses), the status, a caret while
 * the agent works and a "needs you" line when a request waits on the player. Pure Java (tested); the
 * renderer only measures and draws it. Widths are in characters ({@code cols}); the renderer still
 * trims by pixel width, the clip here bounds memory and keeps rows single-line.
 */
public final class MonitorLog {
	public static final String ELLIPSIS = "…";
	static final int MAX_PROSE_LINES = 2;
	static final int MUTED = 0xFF8A8A8A;
	static final int ADD = 0xFF55FF55;
	static final int DEL = 0xFFFF5555;
	private static final Pattern COUNTS = Pattern.compile("\\s*\\(\\+(\\d+) [−-](\\d+)\\)\\s*$");
	private static final Pattern EXIT = Pattern.compile("\\[exit (-?\\d+)]\\s*$");

	public enum Kind { TOOL, PROSE, USER, REQUEST, ERROR, SYSTEM }

	/**
	 * One screen row. {@code icon}: kit icon name (bash decision edit git memory merge message read
	 * test) or null; {@code name}: tool name drawn in the tool colour before {@code text}; {@code adds}
	 * / {@code dels}: diff counts (-1 = none).
	 */
	public record Row(Kind kind, @Nullable String icon, String name, String text, int color, int adds, int dels) {
		public boolean hasCounts() {
			return adds >= 0 || dels >= 0;
		}
	}

	/** The whole live screen. */
	public record Model(String title, StationStatus status, List<Row> rows, boolean cursor, @Nullable String needsYou) {
	}

	private MonitorLog() {
	}

	/**
	 * @param known   the thread's status as the shell says (the NPC registry), null when unknown;
	 *                a pending request in the transcript still wins
	 * @param maxRows rows of the tail (the newest are kept)
	 * @param cols    characters per row
	 */
	public static Model of(ChatTranscript.View view, @Nullable StationStatus known, @Nullable String fallbackTitle, int maxRows, int cols) {
		cols = Math.max(4, cols);
		maxRows = Math.max(0, maxRows);
		ChatTranscript.Status st = view.status();
		StationStatus status = known != null ? known : of(st.state());
		String needs = st.needs();
		if (needs != null) {
			status = StationStatus.ATTENTION;
			needs = needs.startsWith("You: ") ? needs.substring(5) : needs;
		} else if (status == StationStatus.ATTENTION) {
			needs = "waiting for you";
		}
		List<Row> tail = new ArrayList<>();
		List<ChatEntry> entries = view.entries();
		for (int i = entries.size() - 1; i >= 0 && tail.size() < maxRows; i--) {
			ChatEntry e = entries.get(i);
			if (e.inherited()) {
				continue;
			}
			List<Row> rows = rows(e, cols);
			for (int k = rows.size() - 1; k >= 0 && tail.size() < maxRows; k--) {
				tail.addFirst(rows.get(k));
			}
		}
		String title = view.title().isBlank() ? (fallbackTitle == null ? "" : fallbackTitle) : view.title();
		return new Model(clip(title, cols), status, List.copyOf(tail), status == StationStatus.WORKING && needs == null,
				needs == null ? null : clip(needs, cols));
	}

	/** The rows of one transcript entry (top to bottom). */
	static List<Row> rows(ChatEntry e, int cols) {
		return switch (e.kind()) {
			case TOOL, SUBAGENT, REASONING -> List.of(tool(e, cols));
			case ASSISTANT -> prose(e.body(), cols);
			case USER -> {
				String first = firstLine(plain(e.body()));
				yield first.isEmpty() ? List.of() : List.of(new Row(Kind.USER, null, "", clip("› " + first, cols), ChatEntry.Kind.USER.color, -1, -1));
			}
			case REQUEST -> List.of(new Row(Kind.REQUEST, "decision", "", clip(e.title(), cols - 2), ChatEntry.Kind.REQUEST.color, -1, -1));
			case ERROR -> List.of(new Row(Kind.ERROR, null, "", clip("✖ " + firstLine(e.body().isBlank() ? e.title() : e.body()), cols),
					ChatEntry.Kind.ERROR.color, -1, -1));
			case PLAN, TODO -> List.of(new Row(Kind.TOOL, "memory", e.kind().role, clip(e.title(), Math.max(1, cols - 2 - e.kind().role.length() - 1)),
					ChatEntry.Kind.PLAN.color, -1, -1));
			case SYSTEM -> {
				String b = firstLine(e.body());
				String t = b.isEmpty() ? e.title() : e.title().isEmpty() ? b : e.title() + ": " + b;
				yield t.isBlank() ? List.of() : List.of(new Row(Kind.SYSTEM, null, "", clip(t, cols), MUTED, -1, -1));
			}
		};
	}

	static Row tool(ChatEntry e, int cols) {
		ToolLine tl = ToolLine.of(e);
		String arg = tl.arg();
		int adds = -1;
		int dels = -1;
		Matcher m = COUNTS.matcher(arg);
		if (m.find()) {
			adds = Integer.parseInt(m.group(1));
			dels = Integer.parseInt(m.group(2));
			arg = arg.substring(0, m.start());
		}
		int color = ChatEntry.Kind.TOOL.color;
		Matcher x = EXIT.matcher(arg);
		if (x.find()) {
			if (!"0".equals(x.group(1))) {
				color = ChatEntry.Kind.ERROR.color;
			}
			arg = arg.substring(0, x.start()).strip();
		}
		if ("failed".equals(e.status())) {
			color = ChatEntry.Kind.ERROR.color;
		}
		String name = tl.name().contains("_") ? ToolLine.shortName(tl.name()) : tl.name();
		int countsWidth = adds >= 0 ? (" +" + adds + " -" + dels).length() : 0;
		// icon (≈2 columns) + name + space + argument + counts
		int room = Math.max(1, cols - 2 - name.length() - 1 - countsWidth);
		boolean pathLike = "edit".equals(tl.icon()) || "read".equals(tl.icon()) && !arg.contains(" ");
		String shown = pathLike ? clipLeft(arg, room) : clip(arg, room);
		return new Row(Kind.TOOL, tl.icon(), clip(name, Math.max(1, cols - 2)), shown, color, adds, dels);
	}

	static List<Row> prose(String body, int cols) {
		List<String> lines = wrap(plain(body), cols);
		List<Row> out = new ArrayList<>(Math.min(lines.size(), MAX_PROSE_LINES));
		int n = Math.min(lines.size(), MAX_PROSE_LINES);
		for (int i = 0; i < n; i++) {
			String l = lines.get(i);
			if (i == n - 1 && lines.size() > n) {
				l = clip(l + " " + ELLIPSIS, cols);
			}
			out.add(new Row(Kind.PROSE, null, "", l, ChatEntry.Kind.ASSISTANT.color, -1, -1));
		}
		return out;
	}

	/** Prose without Markdown markup a screen cannot show ({@code `code`}, **bold**, # headings, list bullets). */
	static String plain(String s) {
		String t = (s == null ? "" : s).replace("\r", "").replace("\t", "  ").replace("`", "").replace("**", "").replace("__", "");
		StringBuilder out = new StringBuilder(t.length());
		for (String line : t.split("\n", -1)) {
			String l = line.strip();
			int h = 0;
			while (h < l.length() && l.charAt(h) == '#') {
				h++;
			}
			if (h > 0 && h < l.length() && l.charAt(h) == ' ') {
				l = l.substring(h + 1);
			}
			if (l.startsWith("- ") || l.startsWith("* ")) {
				l = "· " + l.substring(2);
			}
			if (!l.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(l);
			}
		}
		return out.toString();
	}

	/** Greedy word wrap to {@code cols} characters; words longer than a line are cut. */
	static List<String> wrap(String text, int cols) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String w : text.strip().split("\\s+")) {
			if (w.isEmpty()) {
				continue;
			}
			while (w.length() > cols) {
				if (!line.isEmpty()) {
					out.add(line.toString());
					line.setLength(0);
				}
				out.add(w.substring(0, cols));
				w = w.substring(cols);
			}
			if (line.isEmpty()) {
				line.append(w);
			} else if (line.length() + 1 + w.length() <= cols) {
				line.append(' ').append(w);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(w);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	static String clip(String s, int max) {
		String t = s == null ? "" : s;
		if (t.length() <= max) {
			return t;
		}
		return max <= 1 ? ELLIPSIS : t.substring(0, max - 1) + ELLIPSIS;
	}

	/** Keeps the end (file name) of a path: {@code …/client/blocks/MonitorLog.java}. */
	static String clipLeft(String s, int max) {
		String t = s == null ? "" : s;
		if (t.length() <= max) {
			return t;
		}
		return max <= 1 ? ELLIPSIS : ELLIPSIS + t.substring(t.length() - (max - 1));
	}

	static String firstLine(String s) {
		String t = s == null ? "" : s.strip();
		int nl = t.indexOf('\n');
		return nl < 0 ? t : t.substring(0, nl).strip();
	}

	static StationStatus of(ThreadState s) {
		return switch (s) {
			case NEEDS_ATTENTION -> StationStatus.ATTENTION;
			case ERROR -> StationStatus.ERROR;
			case WORKING -> StationStatus.WORKING;
			case DONE -> StationStatus.DONE;
			case IDLE -> StationStatus.IDLE;
		};
	}
}
