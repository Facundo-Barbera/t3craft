package dev.t3craft.client.workstation;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Truncation and word wrap for the in-world screens, by character count (a TextDisplay has a fixed
 * line width, and the screen font is roughly monospaced for the ASCII we mostly show). Pure: no
 * Minecraft classes.
 */
public final class TextFit {
	public static final String ELLIPSIS = "…";

	private TextFit() {
	}

	/** Collapses whitespace (newlines included), drops control characters, trims. Null → "". */
	public static String normalize(@Nullable String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder(s.length());
		boolean space = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c) || Character.isISOControl(c)) {
				space = b.length() > 0;
				continue;
			}
			if (space) {
				b.append(' ');
				space = false;
			}
			b.append(c);
		}
		return b.toString();
	}

	/** One line of at most {@code max} characters, ending in "…" when cut. */
	public static String clip(@Nullable String s, int max) {
		String one = normalize(s);
		if (max <= 0) {
			return "";
		}
		if (one.length() <= max) {
			return one;
		}
		if (max == 1) {
			return ELLIPSIS;
		}
		return one.substring(0, max - 1).stripTrailing() + ELLIPSIS;
	}

	/**
	 * Word-wraps {@code s} into at most {@code maxLines} lines of at most {@code width} characters.
	 * Words longer than a line are split. When the text does not fit, the last line ends in "…".
	 * Empty input → no lines.
	 */
	public static List<String> wrap(@Nullable String s, int width, int maxLines) {
		String text = normalize(s);
		List<String> lines = new ArrayList<>();
		if (text.isEmpty() || width <= 0 || maxLines <= 0) {
			return lines;
		}
		int i = 0;
		int n = text.length();
		while (i < n && lines.size() < maxLines) {
			if (n - i <= width) {
				lines.add(text.substring(i));
				i = n;
				break;
			}
			int cut = text.lastIndexOf(' ', i + width);
			if (cut <= i) {
				// No space in range: hard-split the long word.
				lines.add(text.substring(i, i + width));
				i += width;
			} else {
				lines.add(text.substring(i, cut));
				i = cut + 1;
			}
			while (i < n && text.charAt(i) == ' ') {
				i++;
			}
		}
		if (i < n) {
			int last = lines.size() - 1;
			String l = lines.get(last);
			lines.set(last, (l.length() >= width ? l.substring(0, width - 1) : l).stripTrailing() + ELLIPSIS);
		}
		return lines;
	}
}
