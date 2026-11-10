package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A push button. {@link Style#PRIMARY} is the accent fill (the main action), {@link Style#DANGER} the error
 * fill (destructive), {@link Style#GHOST} has no box until hovered (toolbars). The default style is the
 * pill of {@code T3Screen}: raised highlight, 1 px rule, accent on hover.
 *
 * <p>It can carry a {@link T3Icon}: icon + label, or an icon alone when the label is {@code null} or empty
 * ({@link #iconOnly} gives it its tooltip too). On the filled styles ({@code PRIMARY}, {@code DANGER}) the icon
 * takes the label's colour; on {@code DEFAULT} and {@code GHOST} it follows {@link T3Icon#tint}: muted at
 * rest, accent under the pointer, disabled when disabled. For a borderless icon button in a toolbar or the
 * sidebar see {@link T3IconButton}.
 */
public final class T3Button extends T3Control {
	public enum Style {
		DEFAULT, PRIMARY, DANGER, GHOST
	}

	/** Space between the icon and the label. */
	private static final int ICON_GAP = 4;

	private String label;
	private @Nullable T3Icon icon;
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

	/** An icon button; with a {@code label} (may be null) the icon goes before it. */
	public T3Button(T3Icon icon, @Nullable String label, Runnable action) {
		this(icon, label, Style.DEFAULT, action);
	}

	public T3Button(T3Icon icon, @Nullable String label, Style style, Runnable action) {
		this(label == null ? "" : label, style, action);
		this.icon = icon;
	}

	/** An icon-only button with its tooltip (the icon says nothing by itself). */
	public static T3Button iconOnly(T3Icon icon, String tooltip, Style style, Runnable action) {
		T3Button b = new T3Button(icon, null, style, action);
		b.setTooltip(tooltip);
		return b;
	}

	public String label() {
		return label;
	}

	public @Nullable T3Icon icon() {
		return icon;
	}

	public T3Button setIcon(@Nullable T3Icon icon) {
		this.icon = icon;
		return this;
	}

	/** Width that fits the icon and the label with 6 px on each side. */
	public int preferredWidth(Font font) {
		int content = (icon == null ? 0 : T3Icon.SIZE) + (icon != null && !label.isEmpty() ? ICON_GAP : 0) + (label.isEmpty() ? 0 : font.width(label));
		return content + 12;
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
		boolean filled = enabled() && (style == Style.PRIMARY || style == Style.DANGER);
		int iconTint = filled ? text : T3Icon.tint(t, hover, enabled());
		if (icon == null) {
			T3Draw.centered(g, font, t, label, r.x(), r.y(), r.w(), r.h(), text);
		} else if (label.isEmpty()) {
			T3Icon.draw(g, t, icon, r.x() + (r.w() - T3Icon.SIZE) / 2, r.y() + (r.h() - T3Icon.SIZE) / 2, T3Icon.SIZE, iconTint);
		} else {
			String shown = ChatDraw.clip(font, label, r.w() - 4 - T3Icon.SIZE - ICON_GAP);
			int x = r.x() + (r.w() - (T3Icon.SIZE + ICON_GAP + font.width(shown))) / 2;
			T3Icon.draw(g, t, icon, x, r.y() + (r.h() - T3Icon.SIZE) / 2, T3Icon.SIZE, iconTint);
			g.text(font, shown, x + T3Icon.SIZE + ICON_GAP, r.y() + (r.h() - 8) / 2, text, t.shadow());
		}
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
