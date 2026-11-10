package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A scrolling list of equal-height rows in a recessed well (the thread list's look): mouse wheel,
 * the kit's 6 px scrollbar (drag its thumb, or click the track), a hover fill, an "empty" text. The rows are
 * drawn by a {@link Row}, which may hold its own {@link T3Control}s (the environment list has four buttons
 * per row) and gets the clicks.
 *
 * <p>Keyboard (a click or Tab gives the list the keyboard): Up / Down move a row cursor, Home / End jump
 * to the ends, PageUp / PageDown move a page, Enter or Space activate the cursor row (the same as a click
 * on it, see {@link Row#activate}). The cursor follows a click and {@link #ensureVisible}.
 */
public final class T3List<T> extends T3Control {
	/** How a row looks and reacts. {@code r} is the row's rectangle (already scrolled). */
	public interface Row<T> {
		void render(GuiGraphicsExtractor g, Font font, ChatTheme t, T item, Rect r, int mx, int my);

		default boolean mouseClicked(T item, Rect r, double mx, double my, int button) {
			return false;
		}

		/** Enter or Space on the cursor row; by default a left click on the row's top-left corner. */
		default boolean activate(T item, Rect r) {
			return mouseClicked(item, r, r.x() + 1, r.y() + 1, MouseButtons.LEFT);
		}

		/** Tooltip lines for the pointer over this row, or null. */
		default @Nullable List<String> tooltip(T item, Rect r, double mx, double my) {
			return null;
		}
	}

	/** Width of the strip at the right edge that grabs the scrollbar. */
	private static final int BAR_ZONE = 8;

	private final Row<T> row;
	private List<T> items = List.of();
	private int rowH;
	private double scroll;
	private int cursor = -1;
	private String empty = "";
	private Rect view = Rect.EMPTY;
	private boolean dragging;
	private double grab;

	public T3List(int rowH, Row<T> row) {
		this.rowH = Math.max(8, rowH);
		this.row = row;
	}

	public T3List<T> setItems(List<T> items) {
		this.items = items;
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

	public int rowHeight() {
		return rowH;
	}

	public List<T> items() {
		return items;
	}

	/** The row the keyboard is on, or -1. */
	public int cursor() {
		return cursor;
	}

	/** Scrolls to the first row (a new list of items). */
	public T3List<T> scrollToTop() {
		scroll = 0;
		cursor = -1;
		return this;
	}

	/** Scrolls the least that shows row {@code index} wholly, and puts the cursor on it; call after the list got its bounds. */
	public T3List<T> ensureVisible(int index) {
		int viewH = Math.max(rowH, bounds().h() - 4);
		double top = (double) index * rowH;
		if (top < scroll) {
			scroll = top;
		} else if (top + rowH > scroll + viewH) {
			scroll = top + rowH - viewH;
		}
		cursor = index;
		return this;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean takesClickFocus() {
		return true;
	}

	private double maxScroll() {
		return Math.max(0, (double) items.size() * rowH - view.h());
	}

	private void clamp() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		if (cursor >= items.size()) {
			cursor = items.size() - 1;
		}
	}

	private boolean scrollable() {
		return items.size() * rowH > view.h();
	}

	private Rect rowRect(int i) {
		return new Rect(view.x(), view.y() + i * rowH - (int) scroll, view.w() - (scrollable() ? BAR_ZONE : 0), rowH);
	}

	/** Height of the scrollbar thumb, as {@link ChatDraw#scrollbar} draws it. */
	private int thumbH() {
		return Math.max(8, (int) Math.round((double) view.h() * view.h() / Math.max(1, items.size() * rowH)));
	}

	/** Top of the thumb for the current scroll. */
	private int thumbTop() {
		double max = Math.max(1, maxScroll());
		return view.y() + (int) Math.round((view.h() - thumbH()) * Math.max(0, Math.min(1, scroll / max)));
	}

	private void dragThumbTo(double top) {
		double span = Math.max(1, view.h() - thumbH());
		double frac = Math.max(0, Math.min(1, (top - view.y()) / span));
		scroll = frac * maxScroll();
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
			if (hasFocus() && enabled()) {
				T3Draw.focusRing(g, t, r);
			}
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
			row.render(g, font, t, items.get(i), rr, mx, my);
			if (i == cursor && hasFocus() && enabled()) {
				ChatDraw.outline(g, rr.x(), rr.y(), rr.w(), rr.h(), ChatTheme.withAlpha(t.brass(), 170));
			}
		}
		g.disableScissor();
		int content = items.size() * rowH;
		double max = Math.max(1, content - view.h());
		boolean overThumb = scrollable() && mx >= view.right() - BAR_ZONE && mx < view.right() && my >= thumbTop() && my < thumbTop() + thumbH();
		ChatDraw.scrollbar(g, t, view.right() - 6, view.y(), view.h(), content, view.h(), scroll / max, dragging || overThumb);
		if (hasFocus() && enabled()) {
			T3Draw.focusRing(g, t, r);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!visible() || !view.contains(mx, my)) {
			return false;
		}
		if (scrollable() && mx >= view.right() - BAR_ZONE) {
			if (!enabled() || !MouseButtons.isLeft(button)) {
				return true;
			}
			int top = thumbTop();
			if (my >= top && my < top + thumbH()) {
				grab = my - top; // pick the thumb up where it was grabbed
			} else {
				grab = thumbH() / 2.0; // a click on the track puts the thumb's middle there
				dragThumbTo(my - grab);
			}
			dragging = true;
			return true;
		}
		int i = (int) ((my - view.y() + scroll) / rowH);
		if (i >= 0 && i < items.size()) {
			Rect rr = rowRect(i);
			if (rr.contains(mx, my)) {
				cursor = i;
				row.mouseClicked(items.get(i), rr, mx, my, button);
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		dragThumbTo(my - grab);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!hasFocus() || !enabled() || !visible() || items.isEmpty()) {
			return false;
		}
		int last = items.size() - 1;
		int page = Math.max(1, view.h() / rowH - 1);
		boolean had = cursor >= 0;
		int cur = had ? Math.min(cursor, last) : Math.max(0, Math.min(last, (int) (scroll / rowH)));
		int k = e.key();
		int next;
		if (k == InputConstants.KEY_UP) {
			next = had ? cur - 1 : cur;
		} else if (k == InputConstants.KEY_DOWN) {
			next = had ? cur + 1 : cur;
		} else if (k == InputConstants.KEY_HOME) {
			next = 0;
		} else if (k == InputConstants.KEY_END) {
			next = last;
		} else if (k == InputConstants.KEY_PAGEUP) {
			next = cur - page;
		} else if (k == InputConstants.KEY_PAGEDOWN) {
			next = cur + page;
		} else if (isActivateKey(e)) {
			if (had) {
				row.activate(items.get(cur), rowRect(cur));
			}
			return true;
		} else {
			return false;
		}
		ensureVisible(Math.max(0, Math.min(last, next)));
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
		if (!visible() || !bounds().contains(mx, my) || items.size() * rowH <= view.h()) {
			return false;
		}
		scroll -= dy * rowH;
		clamp();
		return true;
	}
}
