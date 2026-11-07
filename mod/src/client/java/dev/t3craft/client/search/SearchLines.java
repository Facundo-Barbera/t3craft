package dev.t3craft.client.search;

import dev.t3craft.client.workstation.AgeFormat;
import dev.t3craft.t3.ThreadSearch;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Text of search results, shared by {@code /t3craft search} and the terminal's search box. Pure
 * (no Minecraft classes): the command turns a {@link Line} into a clickable chat component.
 */
public final class SearchLines {
	private SearchLines() {
	}

	/** Subcommand a result line runs when clicked (hidden helper of {@code /t3craft search}). */
	public static final String OPEN_LITERAL = "search-open";
	static final int TITLE_MAX = 60;
	static final int SNIPPET_MAX = 120;
	/** Colour of a thread that is not in the live shell (an older settled one the server found). */
	public static final int UNKNOWN_COLOR = 0x8A949E;
	private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_.+\\-]+");

	/**
	 * One result: {@code [env] title — state · age}. {@code age} is "" when the activity time is
	 * unknown; {@code snippet} is null for title-only hits.
	 */
	public record Line(String envLabel, String title, String state, int color, String glyph, String age, @Nullable String snippet,
			String command) {
		/** Plain text of the line, as it reads in the chat. */
		public String text() {
			return "[" + envLabel + "] " + title + " — " + state + (age.isEmpty() ? "" : " · " + age);
		}
	}

	public static Line line(ThreadSearch.Hit h, long nowMs) {
		ThreadState s = h.state();
		return new Line(
				h.envLabel(),
				clip(h.title(), TITLE_MAX),
				s == null ? "older" : s.word,
				s == null ? UNKNOWN_COLOR : s.color,
				s == null ? "·" : s.glyph,
				age(h.activityMs(), nowMs),
				h.snippet() == null ? null : clip(h.snippet().replaceAll("\\s+", " "), SNIPPET_MAX),
				openCommand(h.env(), h.threadId()));
	}

	public static List<Line> lines(List<ThreadSearch.Hit> hits, long nowMs) {
		List<Line> out = new ArrayList<>(hits.size());
		hits.forEach(h -> out.add(line(h, nowMs)));
		return out;
	}

	/** "3h ago", "now", or "" when unknown. */
	static String age(long activityMs, long nowMs) {
		if (activityMs <= 0) {
			return "";
		}
		String a = AgeFormat.format(nowMs - activityMs);
		return "now".equals(a) ? a : a + " ago";
	}

	/** The click command; env/thread ids are quoted when they are not plain Brigadier words. */
	public static String openCommand(String env, String threadId) {
		return "/t3craft " + OPEN_LITERAL + " " + quote(env) + " " + quote(threadId);
	}

	static String quote(String s) {
		if (BARE.matcher(s).matches()) {
			return s;
		}
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/**
	 * Header of a result list, or the reason there are no results. {@code null} outcome = still
	 * searching.
	 */
	public static String summary(ThreadSearch.Outcome o) {
		if (o.noEnvironmentConnected()) {
			return o.paired() == 0
					? "No T3 environment paired: /t3craft pair (read-only)."
					: "No T3 environment connected (" + o.paired() + " paired). Search needs a live connection; see /t3craft status.";
		}
		String where = o.searched() == 1 ? "1 environment" : o.searched() + " environments";
		if (o.hits().isEmpty()) {
			return "No threads match \"" + o.query() + "\" in " + where + ".";
		}
		return o.hits().size() + (o.hits().size() == 1 ? " thread matches" : " threads match") + " \"" + o.query() + "\" in " + where + ":";
	}

	public static List<String> errors(ThreadSearch.Outcome o) {
		List<String> out = new ArrayList<>();
		o.errors().forEach(e -> out.add(e.envLabel() + ": " + e.message()));
		return out;
	}

	static String clip(String s, int max) {
		String t = s.strip();
		return t.length() <= max ? t : t.substring(0, max - 1).stripTrailing() + "…";
	}
}
