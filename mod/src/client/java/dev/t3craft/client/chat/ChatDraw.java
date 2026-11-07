// Layout ideas adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small drawing helpers shared by the chat components (flat fills, no textures): environment
 * badge, key-cap hints, pill. The key-cap footer and the pill follow AgentCraft's console; the
 * colours are {@link ChatTheme}'s.
 */
public final class ChatDraw {
	private ChatDraw() {
	}

	/** Environment badge: coloured block with 1-3 letters, 10 px tall. Returns its width. */
	public static int badge(GuiGraphicsExtractor g, Font font, EnvBadge b, int x, int y) {
		int w = font.width(b.text()) + 4;
		g.fill(x, y, x + w, y + 10, b.background());
		g.text(font, b.text(), x + 2, y + 1, b.foreground(), false);
		return w;
	}

	public static int badgeWidth(Font font, EnvBadge b) {
		return font.width(b.text()) + 4;
	}

	/** Rounded-looking pill with text (1 px corners cut). Returns its width. */
	public static int pill(GuiGraphicsExtractor g, Font font, String s, int x, int y, int bg, int fg) {
		int w = font.width(s) + 8;
		g.fill(x + 1, y, x + w - 1, y + 11, bg);
		g.fill(x, y + 1, x + w, y + 10, bg);
		g.text(font, s, x + 4, y + 2, fg, false);
		return w;
	}

	/**
	 * Key-cap hints: pairs of {key, what}, e.g. {"↑↓", "threads", "Esc", "close"}. Returns the width
	 * used. Stops before exceeding {@code maxW}.
	 */
	public static int hints(GuiGraphicsExtractor g, Font font, int x, int y, int maxW, String... pairs) {
		int cx = x;
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			int kw = font.width(pairs[i]) + 6;
			int need = kw + 3 + font.width(pairs[i + 1]) + 10;
			if (cx - x + need > maxW) {
				break;
			}
			g.fill(cx, y - 1, cx + kw, y + 9, ChatTheme.KEYCAP_BG);
			g.fill(cx, y + 9, cx + kw, y + 10, ChatTheme.BORDER);
			g.text(font, pairs[i], cx + 3, y, ChatTheme.TEXT, false);
			cx += kw + 3;
			g.text(font, pairs[i + 1], cx, y, ChatTheme.DIM, false);
			cx += font.width(pairs[i + 1]) + 10;
		}
		return cx - x;
	}

	public static String clip(Font font, String s, int w) {
		if (w <= 0) {
			return "";
		}
		if (font.width(s) <= w) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
	}
}
