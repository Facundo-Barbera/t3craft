package dev.t3craft.client.t3ui.actions;

import dev.t3craft.t3.ThreadShell;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * T3 Code's thread status pill ({@code ThreadStatusIndicators} / {@code resolveThreadStatusPill}):
 * a coloured dot plus the label ({@link ThreadStatus}), the dot pulsing while the thread works.
 * Use the static {@link #draw} in list rows, or an instance with {@link #setBounds} +
 * {@link #setThread} + {@link #render} in a header. Draws nothing for {@link ThreadStatus#IDLE}
 * unless {@code showIdle}.
 */
public final class ThreadStatusChip {
	private static final int BG = 0x60000000;

	private final Font font;
	private final boolean showIdle;
	private int x;
	private int y;
	private int w = -1;
	private ThreadStatus status = ThreadStatus.IDLE;

	public ThreadStatusChip(Font font, boolean showIdle) {
		this.font = font;
		this.showIdle = showIdle;
	}

	/** {@code w} &lt; 0 = as wide as the label; the height is always {@link #height(Font)}. */
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public void setThread(@Nullable ThreadShell t) {
		status = t == null ? ThreadStatus.IDLE : ThreadStatus.of(t);
	}

	public void setStatus(ThreadStatus s) {
		status = s == null ? ThreadStatus.IDLE : s;
	}

	public ThreadStatus status() {
		return status;
	}

	public void render(GuiGraphicsExtractor g) {
		if (status == ThreadStatus.IDLE && !showIdle) {
			return;
		}
		draw(g, font, status, x, y, w, System.currentTimeMillis());
	}

	public static int height(Font font) {
		return font.lineHeight + 3;
	}

	/** Width of the chip for {@code s}. */
	public static int width(Font font, ThreadStatus s) {
		return 4 + 4 + 3 + font.width(s.label) + 4;
	}

	/** Draws the chip at (x, y); {@code maxW} &lt; 0 = natural width. Returns the width used. */
	public static int draw(GuiGraphicsExtractor g, Font font, ThreadStatus s, int x, int y, int maxW, long nowMs) {
		int cw = maxW < 0 ? width(font, s) : Math.min(maxW, width(font, s));
		int ch = height(font);
		g.fill(x, y, x + cw, y + ch, BG);
		int dot = s.color;
		if (s.pulse) {
			// Fade the dot in and out like T3's animate-pulse (2 s period).
			double phase = (nowMs % 2000) / 2000.0;
			int alpha = (int) (110 + 145 * (0.5 + 0.5 * Math.cos(phase * 2 * Math.PI)));
			dot = (alpha << 24) | (s.color & 0xFFFFFF);
		}
		int dy = y + ch / 2 - 2;
		g.fill(x + 4, dy, x + 8, dy + 4, dot);
		g.text(font, font.plainSubstrByWidth(s.label, Math.max(0, cw - 15)), x + 11, y + 2, s.color);
		return cw;
	}
}
