package dev.t3craft.client.t3ui;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Keyboard navigation of the unified T3 screen as a pure decision (tested): which action a key means
 * given what has focus. Key codes are compared with {@link InputConstants}, never literals
 * (Minecraft 26.x codes are not GLFW's).
 *
 * <p>T3 Code's bindings where they exist ({@code packages/shared/src/keybindings.ts}): Ctrl+B
 * toggles the sidebar ({@code sidebar.toggle}), ↑/↓ and Tab/Shift+Tab step threads
 * ({@code thread.previous}/{@code thread.next}), Ctrl+N new thread ({@code chat.new}), Ctrl+F
 * search. Game additions: ←/→ step the project filter, Esc backs out one level (menu → search →
 * compact thread → close).
 */
public final class T3UiKeys {
	private T3UiKeys() {
	}

	public enum Focus {
		LIST, SEARCH, COMPOSER
	}

	public enum Action {
		/** Not ours: hand the key to the conversation (PgUp/PgDn/End scroll). */
		PASS,
		/** The focused text field handles it (search or composer). */
		TYPE,
		PREV_THREAD, NEXT_THREAD, PREV_PROJECT, NEXT_PROJECT,
		FOCUS_SEARCH, CLEAR_SEARCH, BLUR,
		NEW_THREAD, TOGGLE_SIDEBAR,
		/** Compact: show the thread of the selected row. */
		OPEN_SELECTED,
		/** Enter in the list: focus the composer (ignored without one). */
		FOCUS_COMPOSER,
		/** Compact: back from the thread to the sidebar. */
		SHOW_SIDEBAR,
		CLOSE_MENU,
		CLOSE
	}

	/**
	 * @param compactSidebar compact layout showing the sidebar (Enter opens the thread)
	 * @param compactThread  compact layout showing the thread (Esc goes back to the sidebar)
	 */
	public static Action decide(int key, boolean ctrl, boolean shift, Focus focus, boolean menuOpen, boolean searchEmpty, boolean compactSidebar,
			boolean compactThread) {
		boolean esc = key == InputConstants.KEY_ESCAPE;
		boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		if (menuOpen) {
			return esc ? Action.CLOSE_MENU : Action.PASS;
		}
		if (focus == Focus.COMPOSER) {
			return esc ? Action.BLUR : Action.TYPE;
		}
		if (focus == Focus.SEARCH) {
			if (esc) {
				return searchEmpty ? Action.BLUR : Action.CLEAR_SEARCH;
			}
			if (key == InputConstants.KEY_UP) {
				return Action.PREV_THREAD;
			}
			if (key == InputConstants.KEY_DOWN) {
				return Action.NEXT_THREAD;
			}
			if (enter) {
				return compactSidebar ? Action.OPEN_SELECTED : Action.BLUR;
			}
			return Action.TYPE;
		}
		if (ctrl) {
			if (key == InputConstants.KEY_F) {
				return Action.FOCUS_SEARCH;
			}
			if (key == InputConstants.KEY_N) {
				return Action.NEW_THREAD;
			}
			if (key == InputConstants.KEY_B) {
				return Action.TOGGLE_SIDEBAR;
			}
		}
		if (esc) {
			return compactThread ? Action.SHOW_SIDEBAR : Action.CLOSE;
		}
		if (key == InputConstants.KEY_BACKSPACE && compactThread) {
			return Action.SHOW_SIDEBAR;
		}
		if (key == InputConstants.KEY_UP || (key == InputConstants.KEY_TAB && shift)) {
			return Action.PREV_THREAD;
		}
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_TAB) {
			return Action.NEXT_THREAD;
		}
		if (key == InputConstants.KEY_LEFT) {
			return Action.PREV_PROJECT;
		}
		if (key == InputConstants.KEY_RIGHT) {
			return Action.NEXT_PROJECT;
		}
		if (enter) {
			return compactSidebar ? Action.OPEN_SELECTED : Action.FOCUS_COMPOSER;
		}
		return Action.PASS;
	}
}
