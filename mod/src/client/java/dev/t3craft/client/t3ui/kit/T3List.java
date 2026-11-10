package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A scrolling list of equal-height rows in a recessed well (the thread list's look): mouse wheel,
 * the kit's 6 px scrollbar (drag the thumb, click the track to page), a hover fill, an "empty" text. The
 * rows are drawn by a {@link Row}, which may hold its own {@link T3Control}s (the environment list has four
 * buttons per row) and gets the clicks.
 *
 * <p>Keyboard: the list is a Tab stop. With the keyboard on it, Up / Down / Home / End / Page Up /
 * Page Down move a cursor row (kept in view), and Enter or Space activate it ({@link Row#activated},
 * {@link #onActivate}). The cursor is a highlight of the list, not the page's selection: a page that has
 * its own selection (the piece browser) keeps handling its keys itself.
 */
public final class T3List<T> extends T3Control {
	/** How a row looks and reacts. {@code r} is the row's rectangle (already scrolled). */
	public interface Row<T> {
		void render(GuiGraphicsExtractor g, Font font, ChatTheme t, T item, Rect r, int mx, int my);

		default boolean mouseClicked(T item, Rect r, double mx, double my, int button) {
			return false;
		}

		/** Enter or Space on the cursor row. */
		default void activated(T item) {
		}

		/** Tooltip lines for the pointer over this row, or null. */
		default @Nullable List<String> tooltip(T item, Rect r, double mx, double my) {
			return null;
		}
	}

	private final Row<T> row;
	private final T3Scrollbar bar = new T3Scrollbar();
	private List<T> items = List.of();
	private int rowH;
	private double scroll;
	private String empty = "";
	private Rect view = Rect.EMPTY;
	private int cursor = -1;
	private @Nullable Consumer<T> onActivate;

	public T3List(int rowH, Row<T> row) {
		this.rowH = Math.max(8, rowH);
		this.row = row;
	}

	public T3List<T> setItems(List<T> items) {
		this.items = items;
		if (cursor >= items.size()) {
			cursor = items.size() - 1;
		}
		return this;
	}

	public T3List<T> setRowHeight(int rowH) {
		this.rowH = Math.max(8, rowH);
		return this;
	}

	/** Text shown (wrapped, centred, muted) when there are no items. */
	public T3List<T> setEmptyText(String empty) {
		this.empty = empty;
		return this;
	}

	/** Runs for the cursor row on Enter or Space (after {@link Row#activated}). */
	public T3List<T> onActivate(Consumer<T> onActivate) {
		this.onActivate = onActivate;
		return this;
	}

	public int rowHeight() {
		return rowH;
	}

	public List<T> items() {
		return items;
	}

	/** The row the keyboard cursor is on, or -1. */
	public int cursor() {
		return cursor;
	}

	/** Puts the cursor on row {@code index} (-1 = none) and scrolls it into view. */
	public T3List<T> setCursor(int index) {
		cursor = Math.max(-1, Math.min(index, items.size() - 1));
		if (cursor >= 0) {
			ensureVisible(cursor);
		}
		return this;
	}

	/** Scrolls to the first row (a new list of items). */
	public T3List<T> scrollToTop() {
		scroll = 0;
		return this;
	}

	/** Scrolls the least that shows row {@code index} wholly; call after the list got its bounds. */
	public T3List<T> ensureVisible(int index) {
		int viewH = Math.max(rowH, bounds().h() - 4);
		double top = (double) index * rowH;
		if (top < scroll) {
			scroll = top;
		} else if (top + rowH > scroll + viewH) {
			scroll = top + rowH - viewH;
		}
		return this;
	}

	private int content() {
		return items.size() * rowH;
	}

	private double maxScroll() {
		return Math.max(0, (double) content() - view.h());
	}

	private void clamp() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private boolean barShown() {
		return content() > view.h();
	}

	private int barX() {
		return view.right() - T3Scrollbar.W;
	}

	private double fraction() {
		return scroll / Math.max(1, maxScroll());
	}

	private Rect rowRect(int i) {
		return new Rect(view.x(), view.y() + i * rowH - (int) scroll, view.w() - (barShown() ? 8 : 0), rowH);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		ChatDraw.inset(g, t, r);
		view = r.inset(2);
		clamp();
		if (items.isEmpty()) {
			int y = view.y() + 8;
			for (String line : T3Draw.wrap(font, empty, view.w() - 12)) {
				g.text(font, line, view.x() + (view.w() - font.width(line)) / 2, y, t.muted(), t.shadow());
				y += T3Draw.lineHeight(font);
			}
			drawFocus(g, t);
			return;
		}
		g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
		int from = Math.max(0, (int) (scroll / rowH));
		int to = Math.min(items.size(), (int) ((scroll + view.h()) / rowH) + 1);
		for (int i = from; i < to; i++) {
			Rect rr = rowRect(i);
			if (hovered(mx, my) && view.contains(mx, my) && rr.contains(mx, my)) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
			}
			if (i == cursor && focused()) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
				if (focusRingVisible()) {
					T3Draw.focusRing(g, t, rr);
				}
			}
			row.render(g, font, t, items.get(i), rr, mx, my);
		}
		g.disableScissor();
		if (barShown()) {
			boolean hover = bar.dragging() || T3Scrollbar.over(mx, my, barX(), view.y(), view.h(), content(), view.h());
			ChatDraw.scrollbar(g, t, barX(), view.y(), view.h(), content(), view.h(), fraction(), hover);
		}
		if (cursor < 0) {
			drawFocus(g, t); // no row to carry the ring: the whole well does
		}
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!visible() || !view.contains(mx, my)) {
			return false;
		}
		if (MouseButtons.isLeft(button) && barShown()) {
			double f = bar.press(mx, my, barX(), view.y(), view.h(), content(), view.h(), fraction());
			if (f >= 0) {
				scroll = f * maxScroll();
				clamp();
				return true;
			}
		}
		int i = (int) ((my - view.y() + scroll) / rowH);
		if (i >= 0 && i < items.size()) {
			Rect rr = rowRect(i);
			if (rr.contains(mx, my)) {
				cursor = i;
				if (enabled() && MouseButtons.isLeft(button)) {
					focusIn(null, false); // the keyboard follows a click: arrows move the cursor from here
				}
				row.mouseClicked(items.get(i), rr, mx, my, button);
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		double f = bar.drag(my, view.y(), view.h(), content(), view.h());
		if (f < 0) {
			return false;
		}
		scroll = f * maxScroll();
		clamp();
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = bar.dragging();
		bar.release();
		return was;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!focused() || !enabled() || !visible() || items.isEmpty()) {
			return false;
		}
		int n = items.size();
		int page = Math.max(1, view.h() / rowH - 1);
		int k = e.key();
		int next;
		if (k == InputConstants.KEY_DOWN) {
			next = cursor < 0 ? 0 : cursor + 1;
		} else if (k == InputConstants.KEY_UP) {
			next = cursor < 0 ? n - 1 : cursor - 1;
		} else if (k == InputConstants.KEY_HOME) {
			next = 0;
		} else if (k == InputConstants.KEY_END) {
			next = n - 1;
		} else if (k == InputConstants.KEY_PAGEDOWN) {
			next = cursor < 0 ? page - 1 : cursor + page;
		} else if (k == InputConstants.KEY_PAGEUP) {
			next = cursor < 0 ? 0 : cursor - page;
		} else if (T3Focus.isActivate(e)) {
			if (cursor >= 0 && cursor < n) {
				T item = items.get(cursor);
				row.activated(item);
				if (onActivate != null) {
					onActivate.accept(item);
				}
				return true;
			}
			return false;
		} else {
			return false;
		}
		cursor = Math.max(0, Math.min(n - 1, next));
		ensureVisible(cursor);
		showFocusRing();
		return true;
	}

	@Override
	public @Nullable List<String> tooltipAt(double mx, double my) {
		if (!visible() || !view.contains(mx, my)) {
			return super.tooltipAt(mx, my);
		}
		int i = (int) ((my - view.y() + scroll) / rowH);
		return i >= 0 && i < items.size() ? row.tooltip(items.get(i), rowRect(i), mx, my) : null;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!visible() || !bounds().contains(mx, my) || !barShown()) {
			return false;
		}
		scroll -= dy * rowH;
		clamp();
		return true;
	}
}
