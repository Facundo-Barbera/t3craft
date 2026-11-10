package dev.t3craft.client.t3ui.kit;

/**
 * The hit-testing and the drag of the kit's 6 px scrollbar, whose geometry is the one
 * {@code ChatDraw.scrollbar} draws (thumb height {@code max(8, h * view / content)}, thumb position
 * {@code (h - thumb) * fraction}). One instance per scrolling area. Fractions are 0..1; a negative return
 * means "not mine".
 */
final class T3Scrollbar {
	static final int W = 6;

	private boolean dragging;
	private int grab;

	boolean dragging() {
		return dragging;
	}

	void release() {
		dragging = false;
	}

	private static int thumbH(int h, int content, int view) {
		return Math.max(8, (int) Math.round((double) h * view / content));
	}

	private static int thumbY(int y, int h, int content, int view, double fraction) {
		return y + (int) Math.round((h - thumbH(h, content, view)) * Math.max(0, Math.min(1, fraction)));
	}

	/** The pointer is on the bar (a little wider than it is drawn). */
	static boolean over(double mx, double my, int x, int y, int h, int content, int view) {
		return content > view && h >= 8 && mx >= x - 2 && mx < x + W + 1 && my >= y && my < y + h;
	}

	/**
	 * A press at ({@code mx}, {@code my}) on the bar at ({@code x}, {@code y}) {@code h} tall: on the thumb it
	 * starts a drag (returns the current fraction), on the track it jumps one view towards the pointer.
	 * Negative when the bar was not hit.
	 */
	double press(double mx, double my, int x, int y, int h, int content, int view, double fraction) {
		if (!over(mx, my, x, y, h, content, view)) {
			return -1;
		}
		int ty = thumbY(y, h, content, view, fraction);
		int th = thumbH(h, content, view);
		if (my >= ty && my < ty + th) {
			dragging = true;
			grab = (int) my - ty;
			return Math.max(0, Math.min(1, fraction));
		}
		double step = (double) view / Math.max(1, content - view);
		return Math.max(0, Math.min(1, fraction + (my < ty ? -step : step)));
	}

	/** The pointer is at {@code my} while the thumb is held: the fraction it stands for, or negative when no drag is going. */
	double drag(double my, int y, int h, int content, int view) {
		if (!dragging) {
			return -1;
		}
		int span = h - thumbH(h, content, view);
		if (span <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(1, (my - grab - y) / span));
	}
}
