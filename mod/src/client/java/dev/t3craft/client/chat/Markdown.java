package dev.t3craft.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Small, tolerant Markdown subset for the chat screen: ATX headings, paragraphs, bullet and
 * numbered lists (nested by indent), fenced code blocks, block quotes, horizontal rules, and inline
 * {@code **bold**}, {@code *italic*}/{@code _italic_}, {@code `code`}, {@code [text](url)} and bare
 * {@code http(s)://} links. Anything else is kept as plain text. Never throws.
 *
 * <p>Pure Java (no Minecraft classes) so it is unit-tested; {@link ChatScreen} turns the result
 * into styled lines.
 */
public final class Markdown {
	private Markdown() {
	}

	/** One inline run of text with its style. {@code url} is non-null for links. */
	public record Span(String text, boolean bold, boolean italic, boolean code, @Nullable String url) {
		public static Span plain(String text) {
			return new Span(text, false, false, false, null);
		}

		Span withText(String t) {
			return new Span(t, bold, italic, code, url);
		}

		boolean sameStyle(Span o) {
			return bold == o.bold && italic == o.italic && code == o.code && java.util.Objects.equals(url, o.url);
		}
	}

	public sealed interface Block {
	}

	public record Heading(int level, List<Span> spans) implements Block {
	}

	public record Paragraph(List<Span> spans) implements Block {
	}

	/** {@code depth} 0 = top level; {@code marker} is "•" or the original "3." numbering. */
	public record ListItem(int depth, String marker, List<Span> spans) implements Block {
	}

	public record CodeBlock(@Nullable String language, List<String> lines) implements Block {
	}

	public record Quote(List<Span> spans) implements Block {
	}

	public record Rule() implements Block {
	}

	private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
	private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})\\s*([^`\\s]*)?.*$");
	private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
	private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d{1,9}[.)])\\s+(.*)$");
	private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])(\\s*\\1){2,}\\s*$");
	private static final Pattern QUOTE = Pattern.compile("^ {0,3}>\\s?(.*)$");
	private static final Pattern TASK = Pattern.compile("^\\[([ xX])]\\s+(.*)$");

	public static List<Block> parse(@Nullable String source) {
		List<Block> out = new ArrayList<>();
		if (source == null || source.isEmpty()) {
			return out;
		}
		String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split("\n", -1);
		StringBuilder para = new StringBuilder();
		StringBuilder quote = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Matcher fence = FENCE.matcher(line);
			if (fence.matches()) {
				flush(out, para, quote);
				String marker = fence.group(1);
				String lang = fence.group(2) == null || fence.group(2).isEmpty() ? null : fence.group(2);
				List<String> code = new ArrayList<>();
				int j = i + 1;
				for (; j < lines.length; j++) {
					String l = lines[j];
					if (isClosingFence(l, marker)) {
						break;
					}
					code.add(l);
				}
				out.add(new CodeBlock(lang, code));
				i = j; // skips the closing fence (or runs to the end when unclosed)
				continue;
			}
			if (line.isBlank()) {
				flush(out, para, quote);
				continue;
			}
			Matcher m;
			if ((m = HEADING.matcher(line)).matches()) {
				flush(out, para, quote);
				out.add(new Heading(m.group(1).length(), inline(m.group(2))));
				continue;
			}
			if (RULE.matcher(line).matches()) {
				flush(out, para, quote);
				out.add(new Rule());
				continue;
			}
			if ((m = QUOTE.matcher(line)).matches()) {
				if (!para.isEmpty()) {
					flushPara(out, para);
				}
				if (!quote.isEmpty()) {
					quote.append(' ');
				}
				quote.append(m.group(1).strip());
				continue;
			}
			if ((m = BULLET.matcher(line)).matches()) {
				flush(out, para, quote);
				String body = m.group(2);
				String marker = "•";
				Matcher task = TASK.matcher(body);
				if (task.matches()) {
					marker = task.group(1).isBlank() ? "☐" : "☑";
					body = task.group(2);
				}
				out.add(new ListItem(depth(m.group(1)), marker, inline(body)));
				continue;
			}
			if ((m = NUMBERED.matcher(line)).matches()) {
				flush(out, para, quote);
				out.add(new ListItem(depth(m.group(1)), m.group(2), inline(m.group(3))));
				continue;
			}
			// Lazy continuation of a list item: indented text right after one.
			if (para.isEmpty() && quote.isEmpty() && !out.isEmpty() && out.getLast() instanceof ListItem li
					&& line.startsWith("  ") && i > 0 && !lines[i - 1].isBlank()) {
				List<Span> merged = new ArrayList<>(li.spans());
				merged.add(Span.plain(" "));
				merged.addAll(inline(line.strip()));
				out.set(out.size() - 1, new ListItem(li.depth(), li.marker(), normalize(merged)));
				continue;
			}
			if (!quote.isEmpty()) {
				flushQuote(out, quote);
			}
			if (!para.isEmpty()) {
				para.append(' ');
			}
			para.append(line.strip());
		}
		flush(out, para, quote);
		return out;
	}

	/** Same fence character, at least as long as the opener, nothing else on the line. */
	private static boolean isClosingFence(String line, String opener) {
		String t = line.strip();
		if (t.length() < opener.length()) {
			return false;
		}
		char c = opener.charAt(0);
		for (int k = 0; k < t.length(); k++) {
			if (t.charAt(k) != c) {
				return false;
			}
		}
		return true;
	}

	private static int depth(String indent) {
		return Math.min(indent.length() / 2, 6);
	}

	private static void flush(List<Block> out, StringBuilder para, StringBuilder quote) {
		flushPara(out, para);
		flushQuote(out, quote);
	}

	private static void flushPara(List<Block> out, StringBuilder para) {
		if (!para.isEmpty()) {
			out.add(new Paragraph(inline(para.toString())));
			para.setLength(0);
		}
	}

	private static void flushQuote(List<Block> out, StringBuilder quote) {
		if (!quote.isEmpty()) {
			out.add(new Quote(inline(quote.toString())));
			quote.setLength(0);
		}
	}

	private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s<>()\\[\\]`]+[^\\s<>()\\[\\]`.,;:!?'\"]");

	/** Inline markup → spans. Unmatched markers stay literal. */
	public static List<Span> inline(@Nullable String text) {
		List<Span> out = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return out;
		}
		parseInline(text, false, false, out);
		return normalize(out);
	}

	private static void parseInline(String s, boolean bold, boolean italic, List<Span> out) {
		StringBuilder buf = new StringBuilder();
		int i = 0;
		int n = s.length();
		while (i < n) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < n && "\\`*_[]()#+-.!>~".indexOf(s.charAt(i + 1)) >= 0) {
				buf.append(s.charAt(i + 1));
				i += 2;
				continue;
			}
			if (c == '`') {
				int ticks = run(s, i, '`');
				int close = s.indexOf("`".repeat(ticks), i + ticks);
				if (close > i) {
					emit(out, buf, bold, italic);
					String code = s.substring(i + ticks, close);
					if (ticks > 1 && code.startsWith(" ") && code.endsWith(" ") && code.length() > 1) {
						code = code.substring(1, code.length() - 1);
					}
					out.add(new Span(code, bold, italic, true, null));
					i = close + ticks;
					continue;
				}
			}
			if (c == '[') {
				int closeText = matching(s, i, '[', ']');
				if (closeText > i && closeText + 1 < n && s.charAt(closeText + 1) == '(') {
					int closeUrl = s.indexOf(')', closeText + 2);
					if (closeUrl > closeText) {
						String url = s.substring(closeText + 2, closeUrl).strip();
						int sp = url.indexOf(' ');
						if (sp > 0) {
							url = url.substring(0, sp); // drop a "title"
						}
						emit(out, buf, bold, italic);
						String label = s.substring(i + 1, closeText);
						List<Span> inner = new ArrayList<>();
						parseInline(label.isEmpty() ? url : label, bold, italic, inner);
						for (Span sp2 : inner) {
							out.add(new Span(sp2.text(), sp2.bold(), sp2.italic(), sp2.code(), url));
						}
						i = closeUrl + 1;
						continue;
					}
				}
			}
			if ((c == '*' || c == '_') && !(c == '_' && i > 0 && Character.isLetterOrDigit(s.charAt(i - 1)))) {
				int k = run(s, i, c);
				if (k >= 2) {
					String marker = String.valueOf(c).repeat(2);
					int close = findClose(s, i + 2, marker);
					if (close > i + 2) {
						emit(out, buf, bold, italic);
						parseInline(s.substring(i + 2, close), true, italic, out);
						i = close + 2;
						continue;
					}
				}
				if (i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))) {
					int close = findClose(s, i + 1, String.valueOf(c));
					if (close > i + 1 && !(c == '_' && close + 1 < n && Character.isLetterOrDigit(s.charAt(close + 1)))) {
						emit(out, buf, bold, italic);
						parseInline(s.substring(i + 1, close), bold, true, out);
						i = close + 1;
						continue;
					}
				}
			}
			if ((c == 'h') && (s.startsWith("http://", i) || s.startsWith("https://", i))
					&& (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) {
				Matcher m = BARE_URL.matcher(s);
				if (m.find(i) && m.start() == i) {
					emit(out, buf, bold, italic);
					out.add(new Span(m.group(), bold, italic, false, m.group()));
					i = m.end();
					continue;
				}
			}
			buf.append(c);
			i++;
		}
		emit(out, buf, bold, italic);
	}

	private static int run(String s, int i, char c) {
		int k = 0;
		while (i + k < s.length() && s.charAt(i + k) == c) {
			k++;
		}
		return k;
	}

	private static int matching(String s, int open, char o, char c) {
		int depth = 0;
		for (int j = open; j < s.length(); j++) {
			char ch = s.charAt(j);
			if (ch == '\\') {
				j++;
			} else if (ch == o) {
				depth++;
			} else if (ch == c && --depth == 0) {
				return j;
			}
		}
		return -1;
	}

	/** Closing marker not preceded by whitespace and outside inline code. */
	private static int findClose(String s, int from, String marker) {
		for (int j = from; j <= s.length() - marker.length(); j++) {
			char ch = s.charAt(j);
			if (ch == '\\') {
				j++;
				continue;
			}
			if (ch == '`') {
				int close = s.indexOf('`', j + 1);
				if (close > j) {
					j = close;
					continue;
				}
			}
			if (s.startsWith(marker, j) && !Character.isWhitespace(s.charAt(j - 1))) {
				if (marker.length() == 2) {
					// "***" after "**…*x": the inner "*" closes first, the last two close the bold.
					return j + run(s, j, marker.charAt(0)) - 2;
				}
				if (marker.length() == 1 && j + 1 < s.length() && s.charAt(j + 1) == marker.charAt(0)) {
					j++; // part of a "**": not the single closer
					continue;
				}
				return j;
			}
		}
		return -1;
	}

	private static void emit(List<Span> out, StringBuilder buf, boolean bold, boolean italic) {
		if (!buf.isEmpty()) {
			out.add(new Span(buf.toString(), bold, italic, false, null));
			buf.setLength(0);
		}
	}

	/** Merges adjacent spans with the same style and drops empty ones. */
	static List<Span> normalize(List<Span> in) {
		List<Span> out = new ArrayList<>();
		for (Span s : in) {
			if (s.text().isEmpty()) {
				continue;
			}
			if (!out.isEmpty() && out.getLast().sameStyle(s)) {
				out.set(out.size() - 1, out.getLast().withText(out.getLast().text() + s.text()));
			} else {
				out.add(s);
			}
		}
		return out;
	}

	/** Plain text of some spans (for copy, tests, narration). */
	public static String plain(List<Span> spans) {
		StringBuilder b = new StringBuilder();
		spans.forEach(s -> b.append(s.text()));
		return b.toString();
	}
}
