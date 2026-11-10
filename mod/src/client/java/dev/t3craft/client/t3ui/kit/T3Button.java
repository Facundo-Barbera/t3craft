package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/**
 * A push button. {@link Style#PRIMARY} is the accent fill (the main action), {@link Style#DANGER} the error
 * fill (destructive), {@link Style#GHOST} has no box until hovered (toolbars). The default style is the
 * pill of {@code T3Screen}: raised highlight, 1 px rule, accent on hover.
 */
public final class T3Button extends T3Control {
	public enum Style {
		DEFAULT, PRIMARY, DANGER, GHOST
	}

	private String label;
	private Style style;
	private final Runnable action;
	private boolean emphasised;

	public T3Button(String label, Runnable action) {
		this(label, Style.DEFAULT, action);
	}

	public T3Button(String label, Style style, Runnable action) {
		this.label = label;
		this.style = style;
		this.action = action;
	}

	public String label() {
		return label;
	}

	public T3Button setLabel(String label) {
		this.label = label;
		return this;
	}

	public T3Button setStyle(Style style) {
		this.style = style;
		return this;
	}

	/** The button Enter would press: drawn with the brass focus ring. */
	public T3Button setEmphasised(boolean emphasised) {
		this.emphasised = emphasised;
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
		int fill;
		int edge;
		int text;
		switch (style) {
			case PRIMARY -> {
				fill = hover ? t.youText() : t.accent();
				edge = fill;
				text = t.panelHi();
			}
			case DANGER -> {
				fill = hover ? t.errorText() : t.error();
				edge = fill;
				text = t.panelHi();
			}
			case GHOST -> {
				fill = hover ? t.hover() : 0;
				edge = 0;
				text = hover ? t.accent() : t.muted();
			}
			default -> {
				fill = hover ? t.hover() : t.panelHi();
				edge = hover ? t.accent() : t.edge();
				text = hover ? t.accent() : t.keycapText();
			}
		}
		if (!enabled()) {
			fill = style == Style.GHOST ? 0 : t.inset();
			edge = style == Style.GHOST ? 0 : t.edge();
			text = t.disabled();
		} else if (emphasised && style == Style.DEFAULT) {
			edge = t.brass();
		}
		if ((fill >>> 24) != 0) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), fill);
		}
		if ((edge >>> 24) != 0) {
			ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), edge);
		}
		T3Draw.centered(g, font, t, label, r.x(), r.y(), r.w(), r.h(), text);
		drawFocus(g, t);
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Enter or Space presses the button while it has the keyboard. */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (focused() && enabled() && visible() && T3Focus.isActivate(e)) {
			action.run();
			return true;
		}
		return false;
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
