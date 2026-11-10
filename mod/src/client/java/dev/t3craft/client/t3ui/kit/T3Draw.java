package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Text helpers of the UI kit: wrapping on plain strings (so every line is drawn with the theme's shadow
 * flag, never the vanilla default shadow), the colour of a {@link Tone} and the shared tooltip call.
 */
public final class T3Draw {
	/** What a line of text means; the theme decides the colour. */
	public enum Tone {
		NORMAL, MUTED, WARN, ERROR, OK, CODE, TITLE
	}

	private T3Draw() {
	}

	public static int color(ChatTheme t, Tone tone) {
		return switch (tone) {
			case NORMAL -> t.text();
			case MUTED -> t.muted();
			case WARN -> t.youText();
			case ERROR -> t.errorText();
			case OK -> t.result();
			case CODE -> t.inlineCode();
			case TITLE -> t.accent();
		};
	}

	/** Line height of wrapped text. */
	public static int lineHeight(Font font) {
		return font.lineHeight + 1;
	}

	/** A translated string (resolved now, with the current language). */
	public static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** Splits a "Label: %s" translation into what comes before the argument and what follows it. */
	public static String[] template(String key) {
		String s = Component.translatable(key, "\u0001").getString();
		int i = s.indexOf('\u0001');
		return i < 0 ? new String[] {s, ""} : new String[] {s.substring(0, i), s.substring(i + 1)};
	}

	public static void text(GuiGraphicsExtractor g, Font font, ChatTheme t, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, t.shadow());
	}

	/** {@code s} centred in {@code r} (both ways), clipped to its width. */
	public static void centered(GuiGraphicsExtractor g, Font font, ChatTheme t, String s, int x, int y, int w, int h, int color) {
		String c = ChatDraw.clip(font, s, w - 4);
		g.text(font, c, x + (w - font.width(c)) / 2, y + (h - 8) / 2, color, t.shadow());
	}

	/**
	 * Word wrap of {@code s} to {@code w} pixels. {@code \n} starts a new line, a word wider than the
	 * line is cut by characters. Never empty (a blank string is one empty line).
	 */
	public static List<String> wrap(Font font, String s, int w) {
		int max = Math.max(8, w);
		List<String> out = new ArrayList<>();
		for (String para : s.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				if (word.isEmpty()) {
					continue;
				}
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (font.width(candidate) <= max) {
					line = new StringBuilder(candidate);
					continue;
				}
				if (!line.isEmpty()) {
					out.add(line.toString());
					line = new StringBuilder();
				}
				String rest = word;
				while (font.width(rest) > max) {
					String head = font.plainSubstrByWidth(rest, max);
					if (head.isEmpty()) {
						head = rest.substring(0, 1);
					}
					out.add(head);
					rest = rest.substring(head.length());
				}
				line = new StringBuilder(rest);
			}
			out.add(line.toString());
		}
		return out;
	}

	/** {@link #wrap} cut to {@code maxLines} (the last kept line ends in an ellipsis); {@code maxLines <= 0}: no cut. */
	public static List<String> wrap(Font font, String s, int w, int maxLines) {
		List<String> lines = wrap(font, s, w);
		if (maxLines <= 0 || lines.size() <= maxLines) {
			return lines;
		}
		List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
		cut.set(maxLines - 1, ChatDraw.clip(font, cut.get(maxLines - 1) + "...", w));
		return cut;
	}

	/** The keyboard focus ring: a brass 2 px frame (the outer pixel solid, the inner one soft) just inside {@code r}. */
	public static void focusRing(GuiGraphicsExtractor g, ChatTheme t, Rect r) {
		ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), t.brass());
		if (r.w() > 4 && r.h() > 4) {
			ChatDraw.outline(g, r.x() + 1, r.y() + 1, r.w() - 2, r.h() - 2, ChatTheme.withAlpha(t.brass(), 110));
		}
	}

	/** A 7 x 4 px down-pointing triangle with its top-left at ({@code x}, {@code y}): the drop-down caret, drawn so no glyph is needed. */
	public static void caret(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x, y, x + 7, y + 1, color);
		g.fill(x + 1, y + 1, x + 6, y + 2, color);
		g.fill(x + 2, y + 2, x + 5, y + 3, color);
		g.fill(x + 3, y + 3, x + 4, y + 4, color);
	}

	/** A check mark inside a 10 x 10 box whose top-left is ({@code x}, {@code y}). */
	public static void tick(GuiGraphicsExtractor g, int x, int y, int color) {
		int[][] px = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
		for (int[] p : px) {
			g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, color);
		}
	}

	/** Draws {@code s} wrapped; returns the y under the last line. */
	public static int wrapped(GuiGraphicsExtractor g, Font font, ChatTheme t, String s, int x, int y, int w, int color, int maxLines) {
		for (String line : wrap(font, s, w, maxLines)) {
			g.text(font, line, x, y, color, t.shadow());
			y += lineHeight(font);
		}
		return y;
	}

	public static void tooltip(GuiGraphicsExtractor g, Font font, List<String> lines, int mouseX, int mouseY) {
		g.setTooltipForNextFrame(font, lines.stream().map(s -> Component.literal(s).getVisualOrderText()).toList(), mouseX, mouseY);
	}
}
