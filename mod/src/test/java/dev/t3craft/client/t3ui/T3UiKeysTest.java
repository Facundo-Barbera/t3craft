package dev.t3craft.client.t3ui;

import static dev.t3craft.client.t3ui.T3UiKeys.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.t3ui.T3UiKeys.Action;
import dev.t3craft.client.t3ui.T3UiKeys.Focus;
import org.junit.jupiter.api.Test;

class T3UiKeysTest {
	static Action list(int key) {
		return T3UiKeys.decide(key, false, false, Focus.LIST, false, true, false, false);
	}

	static Action ctrl(int key) {
		return T3UiKeys.decide(key, true, false, Focus.LIST, false, true, false, false);
	}

	@Test
	void listKeysStepThreadsAndProjects() {
		assertEquals(PREV_THREAD, list(InputConstants.KEY_UP));
		assertEquals(NEXT_THREAD, list(InputConstants.KEY_DOWN));
		assertEquals(NEXT_THREAD, list(InputConstants.KEY_TAB));
		assertEquals(PREV_THREAD, T3UiKeys.decide(InputConstants.KEY_TAB, false, true, Focus.LIST, false, true, false, false));
		assertEquals(PREV_PROJECT, list(InputConstants.KEY_LEFT));
		assertEquals(NEXT_PROJECT, list(InputConstants.KEY_RIGHT));
		assertEquals(PASS, list(InputConstants.KEY_PAGEUP), "the conversation scrolls");
		assertEquals(PASS, list(InputConstants.KEY_END));
		assertEquals(CLOSE, list(InputConstants.KEY_ESCAPE));
		assertEquals(FOCUS_COMPOSER, list(InputConstants.KEY_RETURN));
	}

	@Test
	void t3CodeShortcuts() {
		assertEquals(FOCUS_SEARCH, ctrl(InputConstants.KEY_F));
		assertEquals(NEW_THREAD, ctrl(InputConstants.KEY_N));
		assertEquals(TOGGLE_SIDEBAR, ctrl(InputConstants.KEY_B));
		assertEquals(PASS, list(InputConstants.KEY_F), "plain letters do nothing in the list");
	}

	@Test
	void searchFieldTypesAndStillStepsThreads() {
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_A, false, false, Focus.SEARCH, false, false, false, false));
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_BACKSPACE, false, false, Focus.SEARCH, false, false, false, false));
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_N, true, false, Focus.SEARCH, false, false, false, false), "Ctrl+N is the field's");
		assertEquals(NEXT_THREAD, T3UiKeys.decide(InputConstants.KEY_DOWN, false, false, Focus.SEARCH, false, false, false, false));
		assertEquals(PREV_THREAD, T3UiKeys.decide(InputConstants.KEY_UP, false, false, Focus.SEARCH, false, false, false, false));
		// Esc: clear first, then leave the field
		assertEquals(CLEAR_SEARCH, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.SEARCH, false, false, false, false));
		assertEquals(BLUR, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.SEARCH, false, true, false, false));
		assertEquals(BLUR, T3UiKeys.decide(InputConstants.KEY_RETURN, false, false, Focus.SEARCH, false, false, false, false));
		assertEquals(OPEN_SELECTED, T3UiKeys.decide(InputConstants.KEY_RETURN, false, false, Focus.SEARCH, false, false, true, false));
	}

	@Test
	void composerGetsEverythingButEsc() {
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_UP, false, false, Focus.COMPOSER, false, true, false, false));
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_RETURN, false, false, Focus.COMPOSER, false, true, false, false));
		assertEquals(TYPE, T3UiKeys.decide(InputConstants.KEY_B, true, false, Focus.COMPOSER, false, true, false, false));
		assertEquals(BLUR, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.COMPOSER, false, true, false, false));
	}

	@Test
	void openMenuOnlyClosesOnEsc() {
		assertEquals(CLOSE_MENU, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.LIST, true, true, false, false));
		assertEquals(PASS, T3UiKeys.decide(InputConstants.KEY_DOWN, false, false, Focus.LIST, true, true, false, false));
	}

	@Test
	void compactBacksOutOneLevel() {
		// compact, showing the list: Enter opens the thread
		assertEquals(OPEN_SELECTED, T3UiKeys.decide(InputConstants.KEY_RETURN, false, false, Focus.LIST, false, true, true, false));
		assertEquals(OPEN_SELECTED, T3UiKeys.decide(InputConstants.KEY_NUMPADENTER, false, false, Focus.LIST, false, true, true, false));
		assertEquals(CLOSE, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.LIST, false, true, true, false));
		// compact, showing the thread: Esc / Backspace go back to the list
		assertEquals(SHOW_SIDEBAR, T3UiKeys.decide(InputConstants.KEY_ESCAPE, false, false, Focus.LIST, false, true, false, true));
		assertEquals(SHOW_SIDEBAR, T3UiKeys.decide(InputConstants.KEY_BACKSPACE, false, false, Focus.LIST, false, true, false, true));
		assertEquals(PASS, list(InputConstants.KEY_BACKSPACE), "wide: Backspace is not ours");
	}
}
