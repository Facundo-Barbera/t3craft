package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/**
 * A check box with a label to its right (for a choice that belongs to a form, like "Remember this";
 * {@link T3Toggle} is for settings that apply at once). The whole row is clickable; Enter or Space flips it
 * while it has the keyboard. The check mark is drawn with fills, so it needs no font glyph.
 */
public final class T3Check extends T3Control {
	private static final int BOX = 10;

	private String label;
	private boolean value;
	private final Consumer<Boolean> onChange;

	public T3Check(String label, boolean initial, Consumer<Boolean> onChange) {
		this.label = label;
		this.value = initial;
		this.onChange = onChange;
	}

	public boolean value() {
		return value;
	}

	/** Sets the state without calling {@code onChange}. */
	public void set(boolean v) {
		this.value = v;
	}

	public T3Check setLabel(String label) {
		this.label = label;
		return this;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		boolean hover = hovered(mx, my);
		if (hover) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.hover());
		}
		Rect b = new Rect(r.x() + 4, r.y() + (r.h() - BOX) / 2, BOX, BOX);
		int fill = !enabled() ? t.inset() : value ? t.accent() : t.inset();
		int edge = !enabled() ? t.edge() : value || hover ? t.accent() : t.disabled();
		ChatDraw.round(g, b.x(), b.y(), BOX, BOX, fill);
		ChatDraw.outline(g, b.x(), b.y(), BOX, BOX, edge);
		if (value) {
			T3Draw.tick(g, b.x(), b.y(), enabled() ? t.panelHi() : t.disabled());
		}
		int tx = b.right() + 6;
		g.text(font, ChatDraw.clip(font, label, r.right() - tx - 4), tx, r.y() + (r.h() - 8) / 2, enabled() ? t.text() : t.disabled(), t.shadow());
		drawFocus(g, t);
	}

	private void flip() {
		value = !value;
		onChange.accept(value);
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (focused() && enabled() && visible() && T3Focus.isActivate(e)) {
			flip();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled() || !visible() || !MouseButtons.isLeft(button) || !bounds().contains(mx, my)) {
			return false;
		}
		flip();
		return true;
	}
}
