package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * A scrolling list of equal-height rows in a recessed well (the thread list's look): mouse wheel,
 * the kit's 6 px scrollbar, a hover fill, an "empty" text. The rows are drawn by a {@link Row}, which may
 * hold its own {@link T3Control}s (the environment list has four buttons per row) and gets the clicks.
 */
public final class T3List<T> extends T3Control {
	/** How a row looks and reacts. {@code r} is the row's rectangle (already scrolled). */
	public interface Row<T> {
		void render(GuiGraphicsExtractor g, Font font, ChatTheme t, T item, Rect r, int mx, int my);

		default boolean mouseClicked(T item, Rect r, double mx, double my, int button) {
			return false;
		}

		/** Tooltip lines for the pointer over this row, or null. */
		default @Nullable List<String> tooltip(T item, Rect r, double mx, double my) {
			return null;
		}
	}

	private final Row<T> row;
	private List<T> items = List.of();
	private int rowH;
	private double scroll;
	private String empty = "";
	private Rect view = Rect.EMPTY;

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

	private void clamp() {
		double max = Math.max(0, (double) items.size() * rowH - view.h());
		scroll = Math.max(0, Math.min(scroll, max));
	}

	private Rect rowRect(int i) {
		boolean bar = items.size() * rowH > view.h();
		return new Rect(view.x(), view.y() + i * rowH - (int) scroll, view.w() - (bar ? 8 : 0), rowH);
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
		}
		g.disableScissor();
		int content = items.size() * rowH;
		double max = Math.max(1, content - view.h());
		ChatDraw.scrollbar(g, t, view.right() - 6, view.y(), view.h(), content, view.h(), scroll / max, false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!visible() || !view.contains(mx, my)) {
			return false;
		}
		int i = (int) ((my - view.y() + scroll) / rowH);
		if (i >= 0 && i < items.size()) {
			Rect rr = rowRect(i);
			if (rr.contains(mx, my)) {
				row.mouseClicked(items.get(i), rr, mx, my, button);
			}
		}
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
