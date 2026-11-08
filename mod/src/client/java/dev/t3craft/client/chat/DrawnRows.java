package dev.t3craft.client.chat;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The thread rows as they were last drawn: where ({@code area}, absolute GUI pixels), how tall each
 * one is, the first one drawn (scroll) and the keys in drawn order. A click resolves against this,
 * so it opens exactly the thread under the cursor even if the list re-sorted since the frame. Pure Java.
 *
 * @param area     rectangle of the row slots ({@code capacity × rowH} tall)
 * @param rowH     row height
 * @param first    index (in the visible rows) of the first row drawn
 * @param keys     keys of the rows drawn, top to bottom
 * @param capacity rows that fit
 */
public record DrawnRows(Rect area, int rowH, int first, List<String> keys, int capacity) {
	public static final DrawnRows NONE = new DrawnRows(Rect.EMPTY, 1, 0, List.of(), 0);

	public DrawnRows {
		keys = List.copyOf(keys);
	}

	/** Index into the visible rows of the row under the point, or -1 (same rule as {@link BrowserLayout#rowAt}). */
	public int indexAt(double mx, double my) {
		return BrowserLayout.rowAt(mx, my, area, rowH, first, first + keys.size());
	}

	/** Key of the drawn row under the point, or null. */
	public @Nullable String keyAt(double mx, double my) {
		int i = indexAt(mx, my);
		return i < 0 ? null : keys.get(i - first);
	}

	/** Rectangle of the drawn row {@code index} (visible-row index), or {@link Rect#EMPTY} when not drawn. */
	public Rect rowRect(int index) {
		int slot = index - first;
		if (slot < 0 || slot >= keys.size()) {
			return Rect.EMPTY;
		}
		return new Rect(area.x(), area.y() + slot * rowH, area.w(), rowH);
	}
}
