package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A ghost button that is only an icon (the sidebar's settings and cloud, toolbars): no box until the
 * pointer is over it, then the theme's hover wash and the accent colour; muted at rest, disabled colour when
 * disabled. {@link #setActive} marks a toggled-on state (the page it opens is showing): selected wash and
 * accent icon. Give it a tooltip with {@link #setTooltip}: the icon says nothing by itself to a screen reader.
 * Square by default ({@link #SIZE}), but it fills whatever {@link #setBounds} gives it.
 */
public final class T3IconButton extends T3Control {
	/** Default side, the height of every one-line control. */
	public static final int SIZE = T3Control.H;

	private T3Icon icon;
	private final Runnable action;
	private boolean active;

	public T3IconButton(T3Icon icon, Runnable action) {
		this.icon = icon;
		this.action = action;
	}

	public T3Icon icon() {
		return icon;
	}

	public T3IconButton setIcon(T3Icon icon) {
		this.icon = icon;
		return this;
	}

	public boolean active() {
		return active;
	}

	/** Toggled-on look (a page or panel the button opens is showing). */
	public T3IconButton setActive(boolean active) {
		this.active = active;
		return this;
	}

	public void press() {
		if (enabled() && visible()) {
			action.run();
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		boolean hover = hovered(mx, my);
		if (enabled() && (hover || active)) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), hover ? t.hover() : t.selected());
		}
		int tint = enabled() && active ? t.accent() : T3Icon.tint(t, hover, enabled());
		T3Icon.draw(g, t, icon, r.x() + (r.w() - T3Icon.SIZE) / 2, r.y() + (r.h() - T3Icon.SIZE) / 2, T3Icon.SIZE, tint);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled() || !visible() || !MouseButtons.isLeft(button) || !bounds().contains(mx, my)) {
			return false;
		}
		action.run();
		return true;
	}
}
