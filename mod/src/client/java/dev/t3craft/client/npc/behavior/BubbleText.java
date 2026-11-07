package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Speech-bubble text: the thread's latest summary, word-wrapped and clipped. Pure, testable. */
public final class BubbleText {
	public static final int WIDTH = 34;
	public static final int MAX_LINES = 3;

	private BubbleText() {
	}

	/** Fallback line when the thread has no summary yet. */
	static String fallback(Mode mode, boolean error) {
		return switch (mode) {
			case WORKING -> "Working on it…";
			case ATTENTION -> error ? "Something went wrong" : "I need you!";
			case COMPLETED -> "Done!";
			case IDLE -> "…";
		};
	}

	/** Lines for the bubble: emote glyph first, then the wrapped summary. */
	public static List<String> lines(@Nullable String summary, Mode mode, boolean error, Emote emote) {
		String text = summary == null || summary.isBlank() ? fallback(mode, error) : summary.strip().replaceAll("\\s+", " ");
		List<String> out = wrap(text, WIDTH, MAX_LINES);
		if (!emote.glyph.isEmpty() && !out.isEmpty()) {
			out.set(0, emote.glyph + " " + out.getFirst());
		}
		return out;
	}

	static List<String> wrap(String text, int width, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : text.split(" ")) {
			while (word.length() > width) {
				// hard-split very long tokens (paths, URLs)
				if (!cur.isEmpty()) {
					lines.add(cur.toString());
					cur.setLength(0);
				}
				lines.add(word.substring(0, width));
				word = word.substring(width);
			}
			if (cur.isEmpty()) {
				cur.append(word);
			} else if (cur.length() + 1 + word.length() <= width) {
				cur.append(' ').append(word);
			} else {
				lines.add(cur.toString());
				cur.setLength(0);
				cur.append(word);
			}
		}
		if (!cur.isEmpty()) {
			lines.add(cur.toString());
		}
		if (lines.size() > maxLines) {
			List<String> clipped = new ArrayList<>(lines.subList(0, maxLines));
			String last = clipped.getLast();
			clipped.set(maxLines - 1, (last.length() >= width ? last.substring(0, width - 1) : last) + "…");
			return clipped;
		}
		return lines;
	}
}
