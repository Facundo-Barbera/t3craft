package dev.t3craft.client.chat;

/**
 * Where the thread browser sits on a screen, in scaled GUI pixels ({@code guiWidth}/{@code guiHeight},
 * so it follows the GUI scale and the window size). Pure Java, tested.
 *
 * <p>The frame (list + conversation + command bar) is centred both ways with equal margins left/right
 * and top/bottom: at most {@value #MAX_W}×{@value #MAX_H}, never wider than {@value #MAX_RATIO}× its
 * height, never closer than {@code margin} to an edge. Below {@value #COMPACT_W}×{@value #COMPACT_H}
 * the browser switches to its compact mode (one panel at a time).
 *
 * @param frame        the whole browser
 * @param list         thread list panel (SPLIT), or {@link Rect#EMPTY} in compact mode
 * @param conversation conversation panel (SPLIT), or the whole frame in compact mode
 * @param compact      true when the frame is too small for list and conversation side by side
 */
public record BrowserLayout(Rect frame, Rect list, Rect conversation, boolean compact) {
	public static final int MAX_W = 760;
	public static final int MAX_H = 440;
	public static final double MAX_RATIO = 2.0;
	public static final int COMPACT_W = 300;
	public static final int COMPACT_H = 150;
	public static final int DEFAULT_MARGIN = 8;
	public static final int DEFAULT_GAP = 4;

	/** Layout for a screen of {@code guiW}×{@code guiH} scaled pixels with the default margin and gap. */
	public static BrowserLayout compute(int guiW, int guiH) {
		return compute(guiW, guiH, DEFAULT_MARGIN, DEFAULT_GAP);
	}

	public static BrowserLayout compute(int guiW, int guiH, int margin, int gap) {
		int sw = Math.max(0, guiW);
		int sh = Math.max(0, guiH);
		int m = Math.max(0, Math.min(margin, Math.min(sw, sh) / 4));
		int h = Math.max(0, Math.min(MAX_H, sh - 2 * m));
		int w = Math.max(0, Math.min(Math.min(MAX_W, sw - 2 * m), (int) Math.floor(h * MAX_RATIO)));
		// same parity as the screen so both margins are exactly equal
		if (((sw - w) & 1) != 0) {
			w--;
		}
		if (((sh - h) & 1) != 0) {
			h--;
		}
		w = Math.max(0, w);
		h = Math.max(0, h);
		Rect frame = new Rect((sw - w) / 2, (sh - h) / 2, w, h);
		boolean compact = w < COMPACT_W || h < COMPACT_H;
		if (compact) {
			return new BrowserLayout(frame, Rect.EMPTY, frame, true);
		}
		Rect[] split = split(frame, gap);
		return new BrowserLayout(frame, split[0], split[1], false);
	}

	/** Thread list width for a frame {@code w} wide: 30 %, between 130 and 200 px. */
	public static int listWidth(int w) {
		return Math.max(130, Math.min(200, w * 30 / 100));
	}

	/** List on the left, conversation on the right, {@code gap} apart. */
	public static Rect[] split(Rect frame, int gap) {
		return frame.splitX(listWidth(frame.w()), gap);
	}

	/**
	 * Row hit-test of the thread list, pure geometry (fix/chat-direct-connect): index into the
	 * visible rows of the row under ({@code mx}, {@code my}), or -1. {@code rows} is the area the
	 * rows are drawn in (absolute GUI pixels, so it works wherever the frame is centred),
	 * {@code first} the first row drawn (scroll), {@code count} the visible row count.
	 */
	public static int rowAt(double mx, double my, Rect rows, int rowH, int first, int count) {
		if (rowH <= 0 || !rows.contains(mx, my)) {
			return -1;
		}
		int i = first + (int) Math.floor((my - rows.y()) / rowH);
		int lastDrawn = Math.min(count, first + Math.max(1, rows.h() / rowH)) - 1;
		return i >= first && i <= lastDrawn ? i : -1;
	}

	/** True when a host rectangle is too small for the split view. */
	public static boolean isCompact(Rect r) {
		return r.w() < COMPACT_W || r.h() < COMPACT_H;
	}
}
