package dev.t3craft.outbound;

/** Small string helpers shared by the templates. */
final class Text {
	private Text() {
	}

	/** At most {@code max} chars, with an ellipsis when cut. */
	static String clip(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		return max <= 1 ? s.substring(0, Math.max(0, max)) : s.substring(0, max - 1) + "…";
	}

	/** Control characters (newlines included) become spaces; the result is trimmed. */
	static String oneLine(String s) {
		return s.replaceAll("\\p{Cntrl}+", " ").strip();
	}
}
