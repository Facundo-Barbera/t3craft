package dev.t3craft.client.t3ui.kit;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Who has the keyboard among the controls of one host (a {@link T3Panel} or a {@link T3Dialog}). Tab and
 * Shift+Tab walk a <em>ring</em>: the host's controls in reading order, header, body, footer. Only one
 * control has the focus at a time: a kit control (its flag, see {@link T3Control#setFocus}) or the vanilla
 * box of a {@link T3Field} (focused through the screen, as ever).
 *
 * <p>Pages create their buttons and switches again on every layout, so the focus is remembered by identity
 * (a list, a text area, a field live as long as the page) and, failing that, by position and class in the
 * ring: after a relayout the same slot gets it back. A click on a button drops the focus instead of
 * keeping a ring on it.
 */
final class T3Focus {
	private @Nullable Screen screen;
	private @Nullable T3Control kit;
	private int kitIndex = -1;
	private @Nullable Class<?> kitClass;

	void bind(@Nullable Screen s) {
		this.screen = s;
	}

	/** Tab can land on it. */
	static boolean usable(T3Control c) {
		return c.focusable() && c.enabled();
	}

	/** After a layout: hands the focus to the control now in the slot of the one that had it. */
	void restore(List<T3Control> ring) {
		for (T3Control c : ring) {
			c.bindScreen(screen);
		}
		if (kit == null || ring.contains(kit)) {
			return;
		}
		T3Control slot = kitIndex >= 0 && kitIndex < ring.size() ? ring.get(kitIndex) : null;
		if (slot != null && slot.getClass() == kitClass && usable(slot)) {
			kit = slot;
			slot.setFocus(true);
		} else {
			kit = null;
			kitIndex = -1;
			kitClass = null;
		}
	}

	/** The control of the ring that has the keyboard (a field counts), or null. */
	@Nullable T3Control current(List<T3Control> ring) {
		for (T3Control c : ring) {
			if (c.focusable() && c.hasFocus()) {
				return c;
			}
		}
		return null;
	}

	/** The usable control after (dir &gt; 0) or before the current one, wrapping around; null when none can take it. */
	@Nullable T3Control step(List<T3Control> ring, int dir) {
		int n = ring.size();
		int cur = -1;
		for (int i = 0; i < n; i++) {
			if (ring.get(i).hasFocus()) {
				cur = i;
				break;
			}
		}
		for (int k = 1; k <= n; k++) {
			int i = cur < 0 ? (dir > 0 ? k - 1 : n - k) : Math.floorMod(cur + dir * k, n);
			if (usable(ring.get(i))) {
				return ring.get(i);
			}
		}
		return null;
	}

	/** Gives {@code c} the keyboard; whoever had it loses it. */
	void focus(T3Control c, List<T3Control> ring) {
		if (kit != null && kit != c) {
			kit.setFocus(false);
		}
		if (c instanceof T3Field f) {
			kit = null;
			kitIndex = -1;
			kitClass = null;
			if (screen != null) {
				f.focus(screen); // setFocused(box) takes the focus from any other box
			}
			return;
		}
		if (screen != null && screen.getFocused() != null) {
			screen.setFocused(null); // a vanilla box (a field, the sidebar search) lets go
		}
		kit = c;
		kitIndex = ring.indexOf(c);
		kitClass = c.getClass();
		c.setFocus(true);
	}

	/** A click on {@code c}: a list or a text area keeps the keyboard, a button does not. */
	void clicked(T3Control c, List<T3Control> ring) {
		if (c instanceof T3Field) {
			clear(); // the click reaches the vanilla box, which takes the keyboard itself
		} else if (c.focusable() && c.takesClickFocus()) {
			focus(c, ring);
		} else {
			clear();
		}
	}

	/** Drops the kit focus (the page closed, a click elsewhere). A field's box is not touched. */
	void clear() {
		if (kit != null) {
			kit.setFocus(false);
		}
		kit = null;
		kitIndex = -1;
		kitClass = null;
	}
}
