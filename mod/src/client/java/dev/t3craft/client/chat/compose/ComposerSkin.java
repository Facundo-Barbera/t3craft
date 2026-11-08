package dev.t3craft.client.chat.compose;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look of T3 Code's composer ({@code ComposerSurface} / {@code ComposerControlsBar} /
 * {@code ComposerPrimaryActions}): one dark rounded box, ghost chips that light up on hover, a
 * round primary button (indigo send, red stop). Drawn with fills: the rounded corners are a
 * per-row inset profile, so they stay crisp at every GUI scale and need no texture (the paper
 * nine-slice kit of {@code ChatTheme} is cream, the composer is not). Shared by the composer and
 * the controls drawn in its bar (model picker chip, project / workspace chips of a draft).
 */
public final class ComposerSkin {
	private ComposerSkin() {
	}

	public static final int BOX = 0xFF1A1A1E;
	public static final int BORDER = 0xFF2F2F35;
	public static final int BORDER_FOCUS = 0xFF4B4B55;
	public static final int TEXT = 0xFFE6E6EA;
	public static final int MUTED = 0xFFA0A0AA;
	public static final int PLACEHOLDER = 0xFF6E6E78;
	public static final int HOVER = 0xFF2A2A30;
	public static final int OPEN = 0xFF34343C;
	public static final int SELECTION = 0x995A5AE0;
	public static final int SEND = 0xFF5B5BD6;
	public static final int SEND_HOVER = 0xFF6E6EE6;
	public static final int DISABLED_BG = 0xFF2A2A30;
	public static final int DISABLED_FG = 0xFF6E6E78;
	public static final int STOP = 0xFFDC3B3B;
	public static final int STOP_HOVER = 0xFFEF5050;
	public static final int ERROR = 0xFFF27C7C;
	public static final int WARN = 0xFFE8B34B;
	public static final int POPUP = 0xF6141418;

	/** Chip height in the composer bar. */
	public static final int CHIP_H = 14;

	/** Rounded box, radius 3 (rows 0/1 inset 2/1 px). */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w < 5 || h < 5) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + 2, y, x + w - 2, y + 1, color);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	/** Rounded box with a 1 px border. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		round(g, x, y, w, h, border);
		round(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Filled circle of diameter {@code d} (even sizes 10..20 look right). */
	public static void circle(GuiGraphicsExtractor g, int x, int y, int d, int color) {
		double r = d / 2.0;
		for (int row = 0; row < d; row++) {
			double dy = row + 0.5 - r;
			double half = Math.sqrt(Math.max(0, r * r - dy * dy));
			int inset = (int) Math.round(r - half);
			g.fill(x + inset, y + row, x + d - inset, y + row + 1, color);
		}
	}

	/** Width of a chip with an optional icon glyph, a label and an optional muted detail. */
	public static int chipWidth(Font font, String icon, String label, String detail) {
		int w = 8 + font.width(label);
		if (!icon.isEmpty()) {
			w += font.width(icon) + 4;
		}
		if (!detail.isEmpty()) {
			w += 4 + font.width(detail);
		}
		return w;
	}

	/**
	 * A ghost chip (T3's {@code ComposerSelectControl}): no fill at rest, a soft fill on hover or
	 * while its popover is open. Returns its width.
	 */
	public static int chip(GuiGraphicsExtractor g, Font font, int x, int y, String icon, int iconColor, String label, int labelColor,
			String detail, boolean hover, boolean open) {
		int w = chipWidth(font, icon, label, detail);
		if (hover || open) {
			round(g, x, y, w, CHIP_H, open ? OPEN : HOVER);
		}
		int tx = x + 4;
		int ty = y + (CHIP_H - 8) / 2;
		if (!icon.isEmpty()) {
			g.text(font, icon, tx, ty, iconColor, false);
			tx += font.width(icon) + 4;
		}
		g.text(font, label, tx, ty, labelColor, false);
		tx += font.width(label);
		if (!detail.isEmpty()) {
			g.text(font, detail, tx + 4, ty, MUTED, false);
		}
		return w;
	}

	/** A 5×10 paperclip drawn with fills (the game font has no paperclip glyph). */
	public static void paperclip(GuiGraphicsExtractor g, int x, int y, int c) {
		g.fill(x + 1, y, x + 4, y + 1, c);
		g.fill(x, y + 1, x + 1, y + 9, c);
		g.fill(x + 4, y + 1, x + 5, y + 7, c);
		g.fill(x + 1, y + 9, x + 3, y + 10, c);
		g.fill(x + 3, y + 8, x + 4, y + 9, c);
		g.fill(x + 2, y + 3, x + 3, y + 8, c);
	}

	/** An up arrow (send) centred in a {@code d}×{@code d} button. */
	public static void arrowUp(GuiGraphicsExtractor g, int x, int y, int d, int c) {
		int cx = x + d / 2;
		int top = y + d / 2 - 4;
		g.fill(cx - 1, top + 2, cx + 1, top + 9, c);
		g.fill(cx - 2, top + 1, cx + 2, top + 3, c);
		g.fill(cx - 3, top + 2, cx - 2, top + 4, c);
		g.fill(cx + 2, top + 2, cx + 3, top + 4, c);
		g.fill(cx - 4, top + 3, cx - 3, top + 5, c);
		g.fill(cx + 3, top + 3, cx + 4, top + 5, c);
		g.fill(cx - 1, top, cx + 1, top + 1, c);
	}

	/** A small rounded square (stop) centred in a {@code d}×{@code d} button. */
	public static void stopSquare(GuiGraphicsExtractor g, int x, int y, int d, int c) {
		int s = Math.max(4, d / 2 - 1);
		int sx = x + (d - s) / 2;
		int sy = y + (d - s) / 2;
		g.fill(sx + 1, sy, sx + s - 1, sy + s, c);
		g.fill(sx, sy + 1, sx + s, sy + s - 1, c);
	}

	/** Three dots (busy) centred in a {@code d}×{@code d} button; the lit dot walks with time. */
	public static void busyDots(GuiGraphicsExtractor g, int x, int y, int d, int c, int dim) {
		int lit = (int) ((System.currentTimeMillis() / 250) % 3);
		int cy = y + d / 2 - 1;
		int cx = x + d / 2 - 4;
		for (int i = 0; i < 3; i++) {
			g.fill(cx + i * 3, cy, cx + i * 3 + 2, cy + 2, i == lit ? c : dim);
		}
	}
}
