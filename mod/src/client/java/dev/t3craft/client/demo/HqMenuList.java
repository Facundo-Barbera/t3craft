package dev.t3craft.client.demo;

import org.jspecify.annotations.Nullable;

/**
 * Geometry of the HQ menu's scrolling layout list, pure Java and unit tested: a viewport
 * {@code (x, top, w, viewH)} over {@code count} rows of {@code rowH}, scrolled by {@code scroll}
 * pixels. Hit-testing accounts for the scroll and only accepts points inside the viewport, so a
 * row scrolled out of view (or half-hidden under the edge) is never clicked by mistake.
 */
public final class HqMenuList {
	private int x;
	private int top;
	private int w;
	private int viewH;
	private int rowH;
	private int count;
	private int scroll;

	public HqMenuList(int x, int top, int w, int viewH, int rowH, int count) {
		layout(x, top, w, viewH, rowH, count);
	}

	/** Re-lays the viewport (resize, GUI scale), keeping the scroll in range. */
	public void layout(int x, int top, int w, int viewH, int rowH, int count) {
		this.x = x;
		this.top = top;
		this.w = Math.max(0, w);
		this.viewH = Math.max(0, viewH);
		this.rowH = Math.max(1, rowH);
		this.count = Math.max(0, count);
		scroll = clamp(scroll);
	}

	public int x() {
		return x;
	}

	public int top() {
		return top;
	}

	public int width() {
		return w;
	}

	public int viewHeight() {
		return viewH;
	}

	public int bottom() {
		return top + viewH;
	}

	public int rowHeight() {
		return rowH;
	}

	public int scroll() {
		return scroll;
	}

	public int maxScroll() {
		return Math.max(0, count * rowH - viewH);
	}

	public boolean scrollable() {
		return maxScroll() > 0;
	}

	public void setScroll(int px) {
		scroll = clamp(px);
	}

	/**
	 * Mouse wheel: {@code amount > 0} scrolls up (towards the first row). One notch is one row.
	 * Returns whether the scroll moved.
	 */
	public boolean wheel(double amount) {
		if (amount == 0) {
			return false;
		}
		int before = scroll;
		setScroll(scroll - (int) Math.signum(amount) * rowH);
		return scroll != before;
	}

	/** Screen y of row {@code index}'s top (may lie outside the viewport). */
	public int rowY(int index) {
		return top + index * rowH - scroll;
	}

	/** Whether any part of the row is inside the viewport (so it is drawn, clipped). */
	public boolean visible(int index) {
		int y = rowY(index);
		return index >= 0 && index < count && y + rowH > top && y < bottom();
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= top && my < bottom();
	}

	/** Row under the point, or -1: outside the viewport, past the last row, or no rows. */
	public int rowAt(double mx, double my) {
		if (!contains(mx, my)) {
			return -1;
		}
		int i = (int) Math.floor((my - top + scroll) / rowH);
		return i >= 0 && i < count ? i : -1;
	}

	/** Scrolls the least needed so row {@code index} is fully visible (keyboard selection). */
	public void ensureVisible(int index) {
		if (index < 0 || index >= count) {
			return;
		}
		int rowTop = index * rowH;
		if (rowTop < scroll) {
			setScroll(rowTop);
		} else if (rowTop + rowH > scroll + viewH) {
			setScroll(rowTop + rowH - viewH);
		}
	}

	/** Rows per page for Page Up / Page Down: at least 1. */
	public int pageRows() {
		return Math.max(1, viewH / rowH);
	}

	/** Scrollbar thumb {@code [y, height]} along the viewport's right edge, or null if nothing scrolls. */
	public int @Nullable [] thumb() {
		int max = maxScroll();
		if (max <= 0 || viewH <= 0) {
			return null;
		}
		int total = count * rowH;
		int h = Math.max(8, Math.min(viewH, viewH * viewH / total));
		int y = top + (int) ((long) (viewH - h) * scroll / max);
		return new int[] {y, h};
	}

	private int clamp(int px) {
		return Math.max(0, Math.min(maxScroll(), px));
	}
}
