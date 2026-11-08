package dev.t3craft.client.t3ui.sidebar;

import dev.t3craft.client.chat.ChatDraw;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;

/**
 * Truncated ("…") strings by text and width, so a sidebar with hundreds of threads does not measure
 * every title glyph by glyph each frame: only the visible rows ask, and a row asks once per width.
 * Cleared when it grows past {@value #MAX} entries or the font changes. Client thread only.
 */
final class ClipCache {
	private static final int MAX = 4096;

	private final Map<String, String> clips = new HashMap<>();
	private final Map<String, Integer> widths = new HashMap<>();
	private Font font;

	private void check(Font f) {
		if (f != font || clips.size() > MAX || widths.size() > MAX) {
			clips.clear();
			widths.clear();
			font = f;
		}
	}

	String clip(Font f, String s, int w) {
		if (s == null || s.isEmpty() || w <= 0) {
			return "";
		}
		check(f);
		String key = w + "\u0001" + s;
		String c = clips.get(key);
		if (c == null) {
			c = ChatDraw.clip(f, s, w);
			clips.put(key, c);
		}
		return c;
	}

	int width(Font f, String s) {
		if (s == null || s.isEmpty()) {
			return 0;
		}
		check(f);
		Integer w = widths.get(s);
		if (w == null) {
			w = f.width(s);
			widths.put(s, w);
		}
		return w;
	}
}
