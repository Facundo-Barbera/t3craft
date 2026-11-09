package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A drop-down choice (T3 Code's select): the field shows the current option and a caret; a click opens a
 * list under it (over it when there is no room), a click on a row chooses, anything else closes. Up/Down
 * and Enter work while it is open. The list is drawn by {@link #renderOverlay}, after everything else.
 */
public final class T3Select<T> extends T3Control {
	private static final int ROW_H = 12;
	private static final int MAX_ROWS = 8;

	private final List<T> options;
	private final Function<T, String> label;
	private final Consumer<T> onChange;
	private int selected;
	private boolean open;
	private int first;
	private int highlight;
	private Rect popup = Rect.EMPTY;

	/** @param current the option shown, or null for the first */
	public T3Select(List<T> options, Function<T, String> label, @Nullable T current, Consumer<T> onChange) {
		this.options = options;
		this.label = label;
		this.onChange = onChange;
		int i = current == null ? 0 : options.indexOf(current);
		this.selected = Math.max(0, i);
	}

	public @Nullable T value() {
		return options.isEmpty() ? null : options.get(Math.min(selected, options.size() - 1));
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		boolean hover = hovered(mx, my);
		ChatDraw.field(g, t, r, open || hover);
		String s = options.isEmpty() ? "—" : label.apply(options.get(Math.min(selected, options.size() - 1)));
		g.text(font, ChatDraw.clip(font, s, r.w() - 20), r.x() + 5, r.y() + (r.h() - 8) / 2, enabled() ? t.text() : t.disabled(), t.shadow());
		g.text(font, "▾", r.right() - 12, r.y() + (r.h() - 8) / 2, enabled() ? (open || hover ? t.accent() : t.muted()) : t.disabled(), false);
	}

	@Override
	public boolean overlayOpen() {
		return open;
	}

	@Override
	public void closeOverlay() {
		open = false;
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		if (!open) {
			return;
		}
		g.nextStratum(); // over the text drawn before it
		Rect r = bounds();
		int shown = Math.min(MAX_ROWS, options.size());
		int h = shown * ROW_H + 4;
		int y = r.bottom() + 1;
		if (y + h > screen.bottom() - 2) {
			y = Math.max(screen.y() + 2, r.y() - h - 1);
		}
		int x = Math.max(screen.x() + 2, Math.min(r.x(), screen.right() - r.w() - 2));
		popup = new Rect(x, y, r.w(), h);
		first = Math.max(0, Math.min(first, options.size() - shown));
		ChatDraw.panel(g, t, popup);
		for (int i = 0; i < shown; i++) {
			int idx = first + i;
			Rect rr = new Rect(popup.x() + 2, popup.y() + 2 + i * ROW_H, popup.w() - 4, ROW_H);
			boolean hover = rr.contains(mx, my);
			if (hover) {
				highlight = idx;
			}
			if (idx == selected) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.selected());
			} else if (idx == highlight) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
			}
			g.text(font, ChatDraw.clip(font, label.apply(options.get(idx)), rr.w() - 6), rr.x() + 3, rr.y() + 2, t.text(), t.shadow());
		}
		if (options.size() > shown) {
			ChatDraw.scrollbar(g, t, popup.right() - 8, popup.y() + 2, popup.h() - 4, options.size() * ROW_H, shown * ROW_H,
					(double) first / Math.max(1, options.size() - shown), false);
		}
	}

	private void choose(int idx) {
		if (idx >= 0 && idx < options.size()) {
			selected = idx;
			onChange.accept(options.get(idx));
		}
		open = false;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (open) {
			if (MouseButtons.isLeft(button) && popup.contains(mx, my)) {
				int row = (int) ((my - popup.y() - 2) / ROW_H);
				choose(first + row);
			} else {
				open = false; // a click anywhere else only closes the list
			}
			return true;
		}
		if (!enabled() || !visible() || options.isEmpty() || !MouseButtons.isLeft(button) || !bounds().contains(mx, my)) {
			return false;
		}
		open = true;
		highlight = selected;
		first = Math.max(0, Math.min(selected - MAX_ROWS / 2, options.size() - MAX_ROWS));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!open || !popup.contains(mx, my)) {
			return false;
		}
		first = Math.max(0, Math.min(first - (int) Math.signum(dy), Math.max(0, options.size() - MAX_ROWS)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!open) {
			return false;
		}
		int k = e.key();
		if (k == InputConstants.KEY_ESCAPE) {
			open = false;
		} else if (k == InputConstants.KEY_UP) {
			highlight = Math.max(0, highlight - 1);
			first = Math.min(first, highlight);
		} else if (k == InputConstants.KEY_DOWN) {
			highlight = Math.min(options.size() - 1, highlight + 1);
			first = Math.max(first, highlight - MAX_ROWS + 1);
		} else if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
			choose(highlight);
		}
		return true; // while open the list owns the keyboard
	}
}
