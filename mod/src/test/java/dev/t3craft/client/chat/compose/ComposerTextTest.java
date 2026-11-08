package dev.t3craft.client.chat.compose;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.compose.ComposerText.KeyResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Editing behaviour of the composer input, pure Java (Minecraft 26.x key codes, fake clipboard). */
class ComposerTextTest {
	static final class FakeClipboard implements ComposerText.Clipboard {
		String value = "";

		@Override
		public String get() {
			return value;
		}

		@Override
		public void set(String text) {
			value = text;
		}
	}

	private static KeyResult key(ComposerText t, int key) {
		return t.key(key, false, false, null);
	}

	private static KeyResult shift(ComposerText t, int key) {
		return t.key(key, true, false, null);
	}

	private static KeyResult ctrl(ComposerText t, int key, ComposerText.Clipboard cb) {
		return t.key(key, false, true, cb);
	}

	private static void type(ComposerText t, String s) {
		s.codePoints().forEach(t::typed);
	}

	@Test
	void typingInsertsAtTheCaret() {
		ComposerText t = new ComposerText();
		type(t, "helo");
		key(t, InputConstants.KEY_LEFT);
		type(t, "l");
		assertEquals("hello", t.text());
		assertEquals(4, t.caret());
		assertFalse(t.typed('\u0007'), "control characters are dropped");
		assertEquals("hello", t.text());
	}

	@Test
	void enterSubmitsShiftEnterInsertsNewline() {
		ComposerText t = new ComposerText();
		type(t, "line one");
		assertEquals(KeyResult.HANDLED, shift(t, InputConstants.KEY_RETURN));
		type(t, "two");
		assertEquals("line one\ntwo", t.text());
		assertEquals(KeyResult.SUBMIT, key(t, InputConstants.KEY_RETURN));
		assertEquals(KeyResult.SUBMIT, key(t, InputConstants.KEY_NUMPADENTER));
		assertEquals("line one\ntwo", t.text(), "Enter does not insert anything");
	}

	@Test
	void shortcutsOfTheHostAreNotSwallowedButOthersAre() {
		ComposerText t = new ComposerText();
		assertEquals(KeyResult.UNHANDLED, key(t, InputConstants.KEY_ESCAPE));
		assertEquals(KeyResult.UNHANDLED, key(t, InputConstants.KEY_TAB));
		assertEquals(KeyResult.UNHANDLED, key(t, InputConstants.KEY_F11));
		assertEquals(KeyResult.HANDLED, key(t, InputConstants.KEY_E), "letters never reach a browser shortcut");
		assertEquals(KeyResult.HANDLED, key(t, InputConstants.KEY_PAGEUP));
	}

	@Test
	void selectAllCopyCutPaste() {
		ComposerText t = new ComposerText();
		FakeClipboard cb = new FakeClipboard();
		type(t, "hello world");
		ctrl(t, InputConstants.KEY_A, cb);
		assertEquals("hello world", t.selected());
		ctrl(t, InputConstants.KEY_C, cb);
		assertEquals("hello world", cb.value);
		assertEquals("hello world", t.text(), "copy keeps the text");
		key(t, InputConstants.KEY_END);
		for (int i = 0; i < 5; i++) {
			shift(t, InputConstants.KEY_LEFT);
		}
		assertEquals("world", t.selected());
		ctrl(t, InputConstants.KEY_X, cb);
		assertEquals("world", cb.value);
		assertEquals("hello ", t.text());
		cb.value = "there\r\nfriend";
		ctrl(t, InputConstants.KEY_V, cb);
		assertEquals("hello there\nfriend", t.text(), "CRLF normalised on paste");
		ctrl(t, InputConstants.KEY_A, cb);
		type(t, "x");
		assertEquals("x", t.text(), "typing replaces the selection");
	}

	@Test
	void wordwiseDeleteAndMove() {
		ComposerText t = new ComposerText();
		type(t, "fix the  build now");
		t.key(InputConstants.KEY_BACKSPACE, false, true, null);
		assertEquals("fix the  build ", t.text());
		t.key(InputConstants.KEY_BACKSPACE, false, true, null);
		assertEquals("fix the  ", t.text());
		t.key(InputConstants.KEY_HOME, false, false, null);
		t.key(InputConstants.KEY_RIGHT, false, true, null);
		assertEquals(3, t.caret());
		t.key(InputConstants.KEY_DELETE, false, true, null);
		assertEquals("fix  ", t.text());
		t.key(InputConstants.KEY_LEFT, false, true, null);
		assertEquals(0, t.caret());
		t.key(InputConstants.KEY_DELETE, false, false, null);
		assertEquals("ix  ", t.text());
		t.key(InputConstants.KEY_END, false, false, null);
		t.key(InputConstants.KEY_BACKSPACE, false, false, null);
		assertEquals("ix ", t.text());
	}

	@Test
	void arrowsCollapseTheSelection() {
		ComposerText t = new ComposerText();
		type(t, "abcdef");
		t.setCaret(2, false);
		t.setCaret(4, true);
		key(t, InputConstants.KEY_LEFT);
		assertEquals(2, t.caret());
		assertFalse(t.hasSelection());
		t.setCaret(4, true);
		key(t, InputConstants.KEY_RIGHT);
		assertEquals(4, t.caret());
	}

	@Test
	void wrapsAtWordsAndNewlines() {
		// one "pixel" per character
		List<ComposerText.Line> lines = ComposerText.wrap("hello big world\nx", 9, String::length);
		assertEquals(List.of(new ComposerText.Line(0, 9, 10), new ComposerText.Line(10, 15, 16), new ComposerText.Line(16, 17, 17)), lines);
		List<ComposerText.Line> hard = ComposerText.wrap("abcdefghij", 4, String::length);
		assertEquals(3, hard.size());
		assertEquals(new ComposerText.Line(0, 4, 4), hard.get(0));
		assertEquals(1, ComposerText.wrap("", 10, String::length).size());
	}

	@Test
	void homeEndAndUpDownFollowVisualLines() {
		ComposerText t = new ComposerText();
		t.layout(9, String::length);
		type(t, "hello big world");
		shift(t, InputConstants.KEY_RETURN);
		type(t, "xy");
		assertEquals(3, t.lines().size());
		key(t, InputConstants.KEY_UP);
		assertEquals(1, t.lineOf(t.caret()));
		assertEquals(12, t.caret(), "column 2 of \"world\"");
		key(t, InputConstants.KEY_HOME);
		assertEquals(10, t.caret());
		key(t, InputConstants.KEY_UP);
		assertEquals(0, t.caret());
		key(t, InputConstants.KEY_END);
		assertEquals(9, t.caret());
		key(t, InputConstants.KEY_UP);
		assertEquals(0, t.caret(), "up on the first line goes to the start");
		t.key(InputConstants.KEY_END, false, true, null);
		assertEquals(t.text().length(), t.caret(), "Ctrl+End: end of text");
		key(t, InputConstants.KEY_DOWN);
		assertEquals(t.text().length(), t.caret());
	}

	@Test
	void lengthIsBounded() {
		ComposerText t = new ComposerText();
		t.insert("a".repeat(ComposerText.MAX_CHARS + 50));
		assertEquals(ComposerText.MAX_CHARS, t.text().length());
		t.insert("more");
		assertEquals(ComposerText.MAX_CHARS, t.text().length());
	}
}
