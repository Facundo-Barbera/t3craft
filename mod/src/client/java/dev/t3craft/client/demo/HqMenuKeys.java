package dev.t3craft.client.demo;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.MouseButtons;

/**
 * Keys and mouse buttons of the HQ menu, pure Java (Minecraft 26.x codes are compile-time
 * constants: Enter = {@link InputConstants#KEY_RETURN} = 40, not GLFW's 257; left click = 1, not
 * 0). Escape is not here: {@code Screen} already routes it to {@code onClose} (back to the parent).
 */
public final class HqMenuKeys {
	public enum Action {
		NONE,
		/** Choose the previous / next layout. */
		PREV, NEXT,
		/** A page of layouts up / down. */
		PAGE_UP, PAGE_DOWN,
		/** First / last layout. */
		FIRST, LAST,
		/** "Open HQ" (Enter, when no button has focus: a focused button handles Enter itself). */
		OPEN
	}

	static final int KEY_UP = InputConstants.KEY_UP;
	static final int KEY_DOWN = InputConstants.KEY_DOWN;
	static final int KEY_PAGE_UP = InputConstants.KEY_PAGEUP;
	static final int KEY_PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
	static final int KEY_HOME = InputConstants.KEY_HOME;
	static final int KEY_END = InputConstants.KEY_END;
	static final int KEY_ENTER = InputConstants.KEY_RETURN;
	static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;

	private HqMenuKeys() {
	}

	public static Action action(int key) {
		if (key == KEY_UP) {
			return Action.PREV;
		}
		if (key == KEY_DOWN) {
			return Action.NEXT;
		}
		if (key == KEY_PAGE_UP) {
			return Action.PAGE_UP;
		}
		if (key == KEY_PAGE_DOWN) {
			return Action.PAGE_DOWN;
		}
		if (key == KEY_HOME) {
			return Action.FIRST;
		}
		if (key == KEY_END) {
			return Action.LAST;
		}
		if (key == KEY_ENTER || key == KEY_KP_ENTER) {
			return Action.OPEN;
		}
		return Action.NONE;
	}

	/** How far an action moves the choice in a list of {@code size} rows, {@code page} per page. */
	public static int delta(Action a, int selected, int size, int page) {
		return switch (a) {
			case PREV -> -1;
			case NEXT -> 1;
			case PAGE_UP -> -Math.max(1, page);
			case PAGE_DOWN -> Math.max(1, page);
			case FIRST -> -selected;
			case LAST -> size - 1 - selected;
			case NONE, OPEN -> 0;
		};
	}

	/** Only a left click chooses a layout row. */
	public static boolean choosesRow(int button) {
		return MouseButtons.isLeft(button);
	}
}
