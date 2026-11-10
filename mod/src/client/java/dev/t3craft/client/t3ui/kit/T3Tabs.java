package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/**
 * A row of text tabs with a 1 px rule under them and an accent underline on the chosen one (T3 Code's
 * settings sections). The host keeps the content of the chosen tab; {@code onSelect} fires on a change.
 */
public final class T3Tabs extends T3Control {
	private static final int PAD = 8;

	private final Font font;
	private final List<String> labels;
	private final IntConsumer onSelect;
	private int selected;

	public T3Tabs(Font font, List<String> labels, int selected, IntConsumer onSelect) {
		this.font = font;
		this.labels = labels;
		this.selected = Math.max(0, Math.min(selected, labels.size() - 1));
		this.onSelect = onSelect;
	}

	public int selected() {
		return selected;
	}

	private Rect tab(int i) {
		Rect r = bounds();
		int x = r.x();
		for (int k = 0; k < i; k++) {
			x += font.width(labels.get(k)) + 2 * PAD;
		}
		return new Rect(x, r.y(), font.width(labels.get(i)) + 2 * PAD, r.h());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), t.edge());
		for (int i = 0; i < labels.size(); i++) {
			Rect tr = tab(i);
			boolean on = i == selected;
			boolean hover = hovered(mx, my) && tr.contains(mx, my);
			g.text(f, labels.get(i), tr.x() + PAD, tr.y() + (tr.h() - 3 - 8) / 2 + 1, on ? t.accent() : hover ? t.text() : t.muted(), t.shadow());
			if (on) {
				g.fill(tr.x() + 2, tr.bottom() - 2, tr.right() - 2, tr.bottom(), t.accent());
				if (focusRingVisible()) {
					T3Draw.focusRing(g, t, tr);
				}
			}
		}
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Left / Right choose the previous / next tab while the strip has the keyboard. */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!focused() || !enabled() || !visible()) {
			return false;
		}
		int next = e.key() == InputConstants.KEY_LEFT ? selected - 1 : e.key() == InputConstants.KEY_RIGHT ? selected + 1 : selected;
		if (next == selected || next < 0 || next >= labels.size()) {
			return e.key() == InputConstants.KEY_LEFT || e.key() == InputConstants.KEY_RIGHT;
		}
		selected = next;
		showFocusRing();
		onSelect.accept(next);
		return true;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled() || !visible() || !MouseButtons.isLeft(button) || !bounds().contains(mx, my)) {
			return false;
		}
		for (int i = 0; i < labels.size(); i++) {
			if (tab(i).contains(mx, my)) {
				if (i != selected) {
					selected = i;
					onSelect.accept(i);
				}
				return true;
			}
		}
		return false;
	}
}
