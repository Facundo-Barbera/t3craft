package dev.t3craft.client.screen;

/**
 * Pure layout arithmetic shared by the mod's vanilla-style screens (only compile-time constants from Minecraft, so it is
 * unit-tested): where a status line goes so it never sits on a button row, how many wrapped lines
 * fit above a widget, where a dialog's buttons go under a text block of unknown height, and the
 * "submit" keys of a form.
 */
public final class DialogLayout {
	/**
	 * Minecraft's key codes. Not GLFW's (257/335) any more in 26.x: always go through
	 * {@code InputConstants}. Compile-time constants, so the class stays loadable in unit tests.
	 */
	public static final int KEY_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
	public static final int KEY_KP_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER;

	/** Height of a vanilla button. */
	public static final int BUTTON_H = 20;

	private DialogLayout() {
	}

	/**
	 * Y of the status line of a config screen whose lowest button row is {@code height - 52 + 4}
	 * ({@code by + 4}, 20 px tall): just under that row, so the text never overlaps a button. The
	 * old {@code height - 64} landed inside the second-to-last row ({@code by - 24}).
	 */
	public static int statusY(int height, int lineHeight) {
		int lowestRowBottom = height - 52 + 4 + BUTTON_H; // = height - 28
		return Math.min(height - lineHeight - 1, lowestRowBottom + 3);
	}

	/**
	 * How many lines of {@code lineHeight} starting at {@code startY} fit before {@code limitY}
	 * (exclusive: the last line must end at or above it). Never negative, never above {@code total}.
	 */
	public static int linesThatFit(int startY, int lineHeight, int limitY, int total) {
		if (lineHeight <= 0 || total <= 0 || limitY <= startY) {
			return 0;
		}
		return Math.min(total, (limitY - startY) / lineHeight);
	}

	/**
	 * Y of a dialog's button row: {@code preferred}, pushed down below a text block that ends at
	 * {@code textBottom} (plus a gap), and kept on screen ({@code height - BUTTON_H - 4}).
	 */
	public static int buttonsBelow(int height, int preferred, int textBottom) {
		int y = Math.max(preferred, textBottom + 6);
		return Math.max(0, Math.min(y, height - BUTTON_H - 4));
	}

	/** Enter or keypad Enter. */
	public static boolean isSubmit(int key) {
		return key == KEY_ENTER || key == KEY_KP_ENTER;
	}

	/**
	 * Row layout of the environment list: {@code rowW} wide, {@code buttonsW} of buttons. When the
	 * text column would be narrower than {@code minTextW}, the buttons go on their own line under
	 * the text ({@code stacked}), left-aligned and clamped to the row, instead of running off the
	 * left edge of the screen.
	 *
	 * @param rowH     height of one row
	 * @param buttonsX x of the first button, relative to the row's left edge (never negative)
	 * @param buttonsY y of the buttons, relative to the row's top
	 * @param textW    width available for the two text lines
	 */
	public record EnvRow(boolean stacked, int rowH, int buttonsX, int buttonsY, int textW) {
		public static final int ROW = 24;
		public static final int STACKED_ROW = 46;

		public static EnvRow of(int rowW, int buttonsW, int minTextW) {
			if (rowW - buttonsW - 16 >= minTextW) {
				return new EnvRow(false, ROW, rowW - buttonsW, 0, rowW - buttonsW - 16);
			}
			return new EnvRow(true, STACKED_ROW, Math.max(0, rowW - buttonsW), 22, Math.max(0, rowW - 12));
		}
	}

	/** Rows of {@code rowH} that fit between {@code top} and {@code bottom}; at least 1. */
	public static int rowsPerPage(int top, int bottom, int rowH) {
		return Math.max(1, (bottom - top) / Math.max(1, rowH));
	}
}
