package dev.t3craft.client.chat;

import dev.t3craft.client.chat.Markdown.Span;
import java.util.ArrayList;
import java.util.List;

/**
 * Word wrap of styled spans into lines no wider than {@code maxWidth}, keeping each piece's x so
 * the screen can hit-test links. Breaks at spaces; a word wider than the line is split by
 * characters. Pure Java: the width of a piece comes from {@link Measure} (the game font in
 * {@link ChatScreen}, a fixed-width stub in tests).
 */
public final class TextWrap {
	private TextWrap() {
	}

	@FunctionalInterface
	public interface Measure {
		int width(Span styledText);
	}

	/** A piece of a span placed at {@code x} on its line. */
	public record Piece(Span span, int x, int width) {
	}

	public record Line(List<Piece> pieces, int width) {
		public String plain() {
			StringBuilder b = new StringBuilder();
			pieces.forEach(p -> b.append(p.span().text()));
			return b.toString();
		}

		/** The link under {@code x} on this line, or null. */
		public @org.jspecify.annotations.Nullable String urlAt(int x) {
			for (Piece p : pieces) {
				if (p.span().url() != null && x >= p.x() && x < p.x() + p.width()) {
					return p.span().url();
				}
			}
			return null;
		}
	}

	public static List<Line> wrap(List<Span> spans, int maxWidth, Measure measure) {
		int max = Math.max(maxWidth, 1);
		List<Line> lines = new ArrayList<>();
		List<Piece> cur = new ArrayList<>();
		int x = 0;
		for (Span span : spans) {
			for (String token : tokens(span.text())) {
				if (token.equals("\n")) {
					lines.add(finish(cur, x));
					cur = new ArrayList<>();
					x = 0;
					continue;
				}
				boolean space = token.isBlank();
				if (space && x == 0) {
					continue; // no leading spaces on wrapped lines
				}
				Span piece = span.withText(token);
				int w = measure.width(piece);
				if (x + w <= max) {
					cur.add(new Piece(piece, x, w));
					x += w;
					continue;
				}
				if (space) {
					lines.add(finish(cur, x));
					cur = new ArrayList<>();
					x = 0;
					continue;
				}
				if (x > 0 && w <= max) {
					lines.add(finish(cur, x));
					cur = new ArrayList<>();
					x = 0;
					cur.add(new Piece(piece, 0, w));
					x = w;
					continue;
				}
				// Word longer than a whole line: start it on a fresh line, then split by characters.
				if (x > 0) {
					lines.add(finish(cur, x));
					cur = new ArrayList<>();
					x = 0;
				}
				String rest = token;
				while (!rest.isEmpty()) {
					int take = fit(span, rest, max - x, measure);
					if (take == 0) {
						if (x == 0) {
							take = Character.charCount(rest.codePointAt(0));
						} else {
							lines.add(finish(cur, x));
							cur = new ArrayList<>();
							x = 0;
							continue;
						}
					}
					Span part = span.withText(rest.substring(0, take));
					int pw = measure.width(part);
					cur.add(new Piece(part, x, pw));
					x += pw;
					rest = rest.substring(take);
					if (!rest.isEmpty()) {
						lines.add(finish(cur, x));
						cur = new ArrayList<>();
						x = 0;
					}
				}
			}
		}
		if (!cur.isEmpty() || lines.isEmpty()) {
			lines.add(finish(cur, x));
		}
		return lines;
	}

	private static Line finish(List<Piece> pieces, int x) {
		// Trailing spaces do not count toward the line width.
		int end = pieces.size();
		while (end > 0 && pieces.get(end - 1).span().text().isBlank()) {
			end--;
		}
		List<Piece> kept = List.copyOf(pieces.subList(0, end));
		int w = kept.isEmpty() ? 0 : kept.getLast().x() + kept.getLast().width();
		return new Line(kept, w);
	}

	/** Longest prefix (in chars, whole code points) of {@code s} that fits in {@code room}. */
	private static int fit(Span style, String s, int room, Measure measure) {
		if (room <= 0) {
			return 0;
		}
		int best = 0;
		int i = 0;
		while (i < s.length()) {
			int next = i + Character.charCount(s.codePointAt(i));
			if (measure.width(style.withText(s.substring(0, next))) > room) {
				break;
			}
			best = next;
			i = next;
		}
		return best;
	}

	/** Words, runs of spaces and explicit newlines, in order. */
	static List<String> tokens(String s) {
		List<String> out = new ArrayList<>();
		StringBuilder b = new StringBuilder();
		Boolean inSpace = null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n') {
				if (!b.isEmpty()) {
					out.add(b.toString());
					b.setLength(0);
				}
				out.add("\n");
				inSpace = null;
				continue;
			}
			boolean sp = c == ' ';
			if (inSpace != null && sp != inSpace && !b.isEmpty()) {
				out.add(b.toString());
				b.setLength(0);
			}
			b.append(c);
			inSpace = sp;
		}
		if (!b.isEmpty()) {
			out.add(b.toString());
		}
		return out;
	}
}
