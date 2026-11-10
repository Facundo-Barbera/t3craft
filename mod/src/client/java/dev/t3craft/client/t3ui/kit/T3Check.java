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
 * A check box with its label on the right: a square that fills with the accent and a tick when it is on,
 * the whole row clickable. For a choice among several (a list of options to include); a single setting
 * that takes effect at once is a {@link T3Toggle}. Enter or Space flips it while it has the keyboard.
 */
public final class T3Check extends T3Control {
	private static final int BOX = 10;
	/** Top-left of the 1 x 2 px columns that make the tick, inside the 10 px box. */
	private static final int[][] TICK = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}, {8, 2}};

	private final String label;
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

	public void set(boolean v) {
		this.value = v;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		if (hovered(mx, my)) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.hover());
		}
		int bx = r.x() + 4;
		int by = r.y() + (r.h() - BOX) / 2;
		boolean on = enabled();
		if (value) {
			ChatDraw.round(g, bx, by, BOX, BOX, on ? t.accent() : t.edge());
			for (int[] p : TICK) {
				g.fill(bx + p[0], by + p[1], bx + p[0] + 1, by + p[1] + 2, t.panelHi());
			}
		} else {
			ChatDraw.round(g, bx, by, BOX, BOX, t.inset());
			ChatDraw.outline(g, bx, by, BOX, BOX, !on ? t.edge() : hovered(mx, my) ? t.accent() : t.disabled());
		}
		int tx = bx + BOX + 6;
		g.text(font, ChatDraw.clip(font, label, r.right() - tx - 4), tx, r.y() + (r.h() - 8) / 2, on ? t.text() : t.disabled(), t.shadow());
		if (hasFocus() && on) {
			T3Draw.focusRing(g, t, r);
		}
	}

	private void flip() {
		value = !value;
		onChange.accept(value);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (hasFocus() && enabled() && visible() && isActivateKey(e)) {
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
