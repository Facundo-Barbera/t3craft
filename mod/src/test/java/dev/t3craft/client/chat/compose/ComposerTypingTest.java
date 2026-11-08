package dev.t3craft.client.chat.compose;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatTheme;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Typing into the composer through the same dispatch the unified T3 screen uses
 * ({@link ComposerInput}), against a fake SDL3 keyboard: like the real one, a printable key is a
 * key press plus a text event, and the text event only exists while text input is started. The
 * "focuses but cannot type" bug was the composer never starting it.
 */
class ComposerTypingTest {

	/** Minecraft's TextInputManager, reduced to what SDL does with it. */
	static final class FakeTextInput implements ComposerView.TextInput {
		Object owner;
		boolean started;

		@Override
		public void focusChanged(Object o, boolean on) {
			if (on) {
				owner = o;
				started = true;
			} else if (owner == o) {
				owner = null;
				started = false;
			}
		}

		/** What {@code Gui.setScreen} does (TextInputManager.stopTextInput()). */
		void stopAll() {
			owner = null;
			started = false;
		}
	}

	/** The keyboard: key press first (the composer swallows it), then the character if SDL emits one. */
	static void type(ComposerView c, FakeTextInput sdl, String s) {
		s.codePoints().forEach(cp -> {
			if (cp == '\n') {
				ComposerInput.keyPressed(c, InputConstants.KEY_RETURN, true, false); // Shift+Enter
				return;
			}
			int key = cp == ' ' ? InputConstants.KEY_SPACE : InputConstants.KEY_A + (Character.toLowerCase(cp) - 'a');
			ComposerInput.keyPressed(c, key, false, false);
			if (sdl.started && ComposerInput.owns(c)) {
				ComposerInput.charTyped(c, cp);
			}
		});
	}

	static ComposerView view(ComposerState state, FakeTextInput sdl) {
		ComposerView v = new ComposerView(state, ChatTheme.warmStudio(), env -> {
		}, sdl);
		v.setThread("vps", "t-1", "Thread");
		return v;
	}

	@Test
	void typesHolaMundoWithShiftEnterNewline(@TempDir Path dir) throws Exception {
		ComposerStateTest.Setup s = ComposerStateTest.setup(dir, true);
		FakeTextInput sdl = new FakeTextInput();
		ComposerView c = view(s.state(), sdl);

		c.setFocused(true); // what a click in the input (or the focus-composer key) does
		assertTrue(c.focused());
		assertTrue(sdl.started, "focusing the composer must start text input, or SDL sends no characters");
		assertSame(c, sdl.owner);

		type(c, sdl, "hola\nmundo");
		assertEquals("hola\nmundo", s.state().editor().text());
		assertTrue(s.transport().tags.isEmpty(), "Shift+Enter is a newline, not a send");
	}

	@Test
	void focusLostStopsTextInputAndRefocusRearmsIt(@TempDir Path dir) throws Exception {
		ComposerStateTest.Setup s = ComposerStateTest.setup(dir, true);
		FakeTextInput sdl = new FakeTextInput();
		ComposerView c = view(s.state(), sdl);
		c.setFocused(true);
		type(c, sdl, "hola");

		ComposerInput.keyPressed(c, InputConstants.KEY_ESCAPE, false, false);
		assertFalse(c.focused());
		assertFalse(sdl.started);
		type(c, sdl, "x");
		assertEquals("hola", s.state().editor().text(), "an unfocused composer takes nothing");

		// back from a confirmation screen: Gui.setScreen stopped text input behind our back
		c.setFocused(true);
		sdl.stopAll();
		c.setFocused(true);
		assertTrue(sdl.started);
		type(c, sdl, " mundo");
		assertEquals("hola mundo", s.state().editor().text());
	}

	@Test
	void anotherOwnersStopDoesNotKillOurs(@TempDir Path dir) throws Exception {
		ComposerStateTest.Setup s = ComposerStateTest.setup(dir, true);
		FakeTextInput sdl = new FakeTextInput();
		ComposerView c = view(s.state(), sdl);
		c.setFocused(true);
		sdl.focusChanged(new Object(), false); // e.g. the sidebar search box losing focus afterwards
		assertTrue(sdl.started);
		type(c, sdl, "ok");
		assertEquals("ok", s.state().editor().text());
	}

	@Test
	void readOnlyComposerNeverStartsTextInput(@TempDir Path dir) throws Exception {
		ComposerStateTest.Setup s = ComposerStateTest.setup(dir, false);
		FakeTextInput sdl = new FakeTextInput();
		ComposerView c = view(s.state(), sdl);
		c.setFocused(true);
		assertFalse(c.focused());
		assertFalse(sdl.started);
		type(c, sdl, "hola");
		assertEquals("", s.state().editor().text());
	}
}
