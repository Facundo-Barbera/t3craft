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
 * A labelled on/off switch (T3 Code's settings rows): the label on the left, a small track with a knob
 * on the right, the whole row clickable. Replaces the "Label: ON/OFF" green/red vanilla button.
 */
public final class T3Toggle extends T3Control {
	private static final int TRACK_W = 20;
	private static final int TRACK_H = 10;
	private static final int KNOB = 8;

	private final String label;
	private boolean value;
	private final Consumer<Boolean> onChange;

	public T3Toggle(String label, boolean initial, Consumer<Boolean> onChange) {
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
	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		if (hovered(mx, my)) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.hover());
		}
		int textW = r.w() - TRACK_W - 14;
		g.text(font, ChatDraw.clip(font, label, textW), r.x() + 4, r.y() + (r.h() - 8) / 2, enabled() ? t.text() : t.disabled(), t.shadow());
		Rect track = new Rect(r.right() - TRACK_W - 4, r.y() + (r.h() - TRACK_H) / 2, TRACK_W, TRACK_H);
		ChatDraw.round(g, track.x(), track.y(), track.w(), track.h(), !enabled() ? t.edge() : value ? t.accent() : t.disabled());
		int kx = value ? track.right() - KNOB - 1 : track.x() + 1;
		ChatDraw.round(g, kx, track.y() + 1, KNOB, KNOB, t.panelHi());
		if (hasFocus() && enabled()) {
			T3Draw.focusRing(g, t, r);
		}
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Enter or Space flips the switch while it has the keyboard. */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (hasFocus() && enabled() && visible() && isActivateKey(e)) {
			flip();
			return true;
		}
		return false;
	}

	private void flip() {
		value = !value;
		onChange.accept(value);
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
