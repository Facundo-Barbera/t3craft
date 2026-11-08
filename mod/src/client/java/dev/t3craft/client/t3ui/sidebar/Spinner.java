package dev.t3craft.client.t3ui.sidebar;

import dev.t3craft.client.chat.ChatTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * T3 Code's "working" spinner in Minecraft pixels: eight 2×2 blocks round an 8×8 box, a bright head
 * and a fading tail turning every 90 ms. Eight fills per draw and no state, so it costs nothing next
 * to the text of a row.
 */
public final class Spinner {
	public static final int SIZE = 8;
	private static final int FRAME_MS = 90;
	/** Top-left of each 2×2 block, clockwise from the top. */
	private static final int[][] DOTS = {{3, 0}, {5, 1}, {6, 3}, {5, 5}, {3, 6}, {1, 5}, {0, 3}, {1, 1}};
	private static final int[] ALPHA = {255, 190, 130, 85, 55, 40, 40, 40};

	private Spinner() {
	}

	/** Draws the spinner with its top-left at ({@code x}, {@code y}) in {@code argb}. */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int argb) {
		int head = (int) ((System.currentTimeMillis() / FRAME_MS) % DOTS.length);
		for (int i = 0; i < DOTS.length; i++) {
			int age = Math.floorMod(head - i, DOTS.length);
			int[] d = DOTS[i];
			g.fill(x + d[0], y + d[1], x + d[0] + 2, y + d[1] + 2, ChatTheme.withAlpha(argb, ALPHA[age] * ((argb >>> 24) & 0xFF) / 255));
		}
	}
}
