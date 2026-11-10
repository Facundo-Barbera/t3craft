package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keyboard focus across a list of controls (docs/features/ui-kit.md, "Keyboard"): Tab and Shift+Tab walk
 * the {@link T3Control#focusable() focusable} ones in the given order, wrapping at the ends; Enter and Space
 * are the activation keys. The hosts ({@link T3PanelHost}, {@link T3Dialog}) call these with their controls in
 * reading order. There is exactly one focused control: moving the focus takes it from the previous one,
 * and from a vanilla text box when {@code screen} is given.
 */
public final class T3Focus {
	private T3Focus() {
	}

	public static boolean isTab(KeyEvent e) {
		return e.key() == InputConstants.KEY_TAB;
	}

	public static boolean isEnter(KeyEvent e) {
		return e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER;
	}

	/** Enter or Space: what presses a focused button or flips a focused switch. */
	public static boolean isActivate(KeyEvent e) {
		return isEnter(e) || e.key() == InputConstants.KEY_SPACE;
	}

	/**
	 * Moves the focus to the next (or, with {@code back}, the previous) focusable control of {@code order}.
	 * With none focused it starts from the first (the last going back). False when nothing can take it.
	 */
	public static boolean move(List<? extends T3Control> order, boolean back, @Nullable Screen screen) {
		int n = order.size();
		if (n == 0) {
			return false;
		}
		int cur = -1;
		for (int i = 0; i < n; i++) {
			if (order.get(i).focused()) {
				cur = i;
				break;
			}
		}
		int step = back ? -1 : 1;
		int start = cur >= 0 ? cur : (back ? n : -1);
		for (int k = 1; k <= n; k++) {
			T3Control target = order.get(Math.floorMod(start + step * k, n));
			if (!target.canFocus()) {
				continue;
			}
			for (T3Control c : order) {
				if (c != target && c.focused()) {
					c.focusOut(screen);
				}
			}
			target.focusIn(screen, true);
			return true;
		}
		return false;
	}

	/** Takes the focus from every control of {@code order}. */
	public static void clear(List<? extends T3Control> order, @Nullable Screen screen) {
		for (T3Control c : order) {
			if (c.focused()) {
				c.focusOut(screen);
			}
		}
	}

	/** A click: switches, buttons and the like lose the focus (a click leaves no ring behind); text boxes keep theirs. */
	static void dropNonInput(List<? extends T3Control> order) {
		for (T3Control c : order) {
			if (c.focused() && !c.takesInput()) {
				c.focusOut(null);
			}
		}
	}

	/** After a click a control took the focus for itself (a list): it is the only one that keeps it. */
	static void afterClick(List<? extends T3Control> order, @Nullable Screen screen) {
		T3Control keep = null;
		for (T3Control c : order) {
			if (c.focused() && !c.takesInput()) {
				keep = c;
				break;
			}
		}
		if (keep == null) {
			return;
		}
		for (T3Control c : order) {
			if (c != keep && c.focused()) {
				c.focusOut(screen);
			}
		}
	}
}
