package dev.t3craft.client.chat.compose;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The text model of the chat composer: a multi-line string with a caret and a selection, plus the
 * keyboard behaviour of T3 Code's composer textarea ({@code apps/web/src/components/chat/ChatComposer.tsx}):
 * Enter sends, Shift+Enter inserts a newline, Ctrl+A/C/V/X, arrows (Shift extends the selection,
 * Ctrl moves by word), Home/End (visual line), Backspace/Delete (Ctrl: by word). Pure Java: key
 * codes are {@link InputConstants} compile-time constants (Minecraft 26.x codes, not GLFW's), the
 * clipboard is an interface and visual lines come from {@link #wrap} with any width function.
 */
public final class ComposerText {
	/** Upper bound (same as the new-thread prompt): a pasted log never goes out by mistake. */
	public static final int MAX_CHARS = 16_000;

	/** System clipboard; a fake in tests. */
	public interface Clipboard {
		String get();

		void set(String text);
	}

	/** What a key did. */
	public enum KeyResult {
		/** Consumed (edited, moved, copied, or just swallowed so no shortcut fires). */
		HANDLED,
		/** Enter without Shift: send the message. */
		SUBMIT,
		/** Not ours (Esc, Tab, F-keys…): the host decides. */
		UNHANDLED
	}

	/**
	 * One visual line: {@code text[start, end)} is drawn; the next line starts at {@code next}
	 * ({@code end + 1} after a newline or a soft-wrapped space, {@code end} after a hard break).
	 */
	public record Line(int start, int end, int next) {
	}

	private final StringBuilder text = new StringBuilder();
	private int caret;
	private int anchor;
	/** Remembered x for repeated ↑/↓ (pixels), -1 when unset. */
	private int goalX = -1;
	private List<Line> lines = List.of(new Line(0, 0, 0));
	private ToIntFunction<String> measure = String::length;

	public String text() {
		return text.toString();
	}

	public boolean isEmpty() {
		return text.isEmpty();
	}

	public boolean isBlank() {
		return text.toString().isBlank();
	}

	public int caret() {
		return caret;
	}

	public int selectionStart() {
		return Math.min(caret, anchor);
	}

	public int selectionEnd() {
		return Math.max(caret, anchor);
	}

	public boolean hasSelection() {
		return caret != anchor;
	}

	public String selected() {
		return text.substring(selectionStart(), selectionEnd());
	}

	/** Replaces everything; caret at the end (or {@code caretAt}, clamped). */
	public void set(String s, int caretAt) {
		text.setLength(0);
		text.append(clean(s, MAX_CHARS));
		caret = anchor = Math.clamp(caretAt, 0, text.length());
		goalX = -1;
		relayout();
	}

	public void set(String s) {
		set(s, Integer.MAX_VALUE);
	}

	public void clear() {
		set("", 0);
	}

	public void setCaret(int pos, boolean select) {
		caret = Math.clamp(pos, 0, text.length());
		if (!select) {
			anchor = caret;
		}
		goalX = -1;
	}

	public void selectAll() {
		anchor = 0;
		caret = text.length();
	}

	/** Types or pastes {@code s} over the selection. Control characters other than newline are dropped. */
	public void insert(String s) {
		int room = MAX_CHARS - (text.length() - (selectionEnd() - selectionStart()));
		String in = clean(s, Math.max(0, room));
		int a = selectionStart();
		text.replace(a, selectionEnd(), in);
		caret = anchor = a + in.length();
		goalX = -1;
		relayout();
	}

	static String clean(String s, int max) {
		StringBuilder b = new StringBuilder(Math.min(s.length(), max));
		String n = s.replace("\r\n", "\n").replace('\r', '\n');
		for (int i = 0; i < n.length() && b.length() < max; i++) {
			char c = n.charAt(i);
			if (c == '\t') {
				b.append("  ");
			} else if (c == '\n' || c >= 0x20 && c != 0x7F) {
				b.append(c);
			}
		}
		return b.length() > max ? b.substring(0, max) : b.toString();
	}

	public void backspace(boolean word) {
		if (!deleteSelection()) {
			int from = word ? wordLeft(caret) : prevCodePoint(caret);
			remove(from, caret);
		}
	}

	public void delete(boolean word) {
		if (!deleteSelection()) {
			int to = word ? wordRight(caret) : nextCodePoint(caret);
			remove(caret, to);
		}
	}

	private boolean deleteSelection() {
		if (!hasSelection()) {
			return false;
		}
		remove(selectionStart(), selectionEnd());
		return true;
	}

	private void remove(int a, int b) {
		if (a < b) {
			text.delete(a, b);
		}
		caret = anchor = a;
		goalX = -1;
		relayout();
	}

	private int prevCodePoint(int p) {
		return p <= 0 ? 0 : text.offsetByCodePoints(p, -1);
	}

	private int nextCodePoint(int p) {
		return p >= text.length() ? text.length() : text.offsetByCodePoints(p, 1);
	}

	/** Start of the word left of {@code p} (skips spaces first), like a browser textarea. */
	int wordLeft(int p) {
		int i = p;
		while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
			i--;
		}
		while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
			i--;
		}
		return i;
	}

	/** End of the word right of {@code p} (skips spaces first). */
	int wordRight(int p) {
		int i = p;
		int n = text.length();
		while (i < n && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		while (i < n && !Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		return i;
	}

	// ---- visual lines -------------------------------------------------------------------------

	/** Wraps at {@code width} with {@code measure} (pixels of a string). Kept until the next edit. */
	public void layout(int width, ToIntFunction<String> measure) {
		this.measure = measure;
		this.width = Math.max(1, width);
		relayout();
	}

	private int width = Integer.MAX_VALUE;

	private void relayout() {
		lines = wrap(text.toString(), width, measure);
	}

	public List<Line> lines() {
		return lines;
	}

	/** Index of the visual line holding {@code p}. */
	public int lineOf(int p) {
		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			boolean last = i == lines.size() - 1;
			if (p >= l.start() && p <= l.end() && (last || p < l.end() || l.end() < l.next())) {
				return i;
			}
		}
		return lines.size() - 1;
	}

	/**
	 * Greedy word wrap: breaks after the last space that fits (the space is consumed), or inside a
	 * word longer than the line. Newlines always break. Never returns an empty list.
	 */
	public static List<Line> wrap(String s, int width, ToIntFunction<String> measure) {
		List<Line> out = new ArrayList<>();
		int start = 0;
		int n = s.length();
		while (true) {
			int nl = s.indexOf('\n', start);
			int stop = nl < 0 ? n : nl;
			// wrap [start, stop)
			int ls = start;
			while (measure.applyAsInt(s.substring(ls, stop)) > width) {
				int fit = ls;
				while (fit < stop && measure.applyAsInt(s.substring(ls, fit + 1)) <= width) {
					fit++;
				}
				int space = s.lastIndexOf(' ', fit);
				if (space >= ls && space < stop && space > ls) {
					out.add(new Line(ls, space, space + 1));
					ls = space + 1;
				} else {
					int cut = Math.max(fit, ls + 1);
					out.add(new Line(ls, cut, cut));
					ls = cut;
				}
			}
			if (nl < 0) {
				out.add(new Line(ls, stop, stop));
				return out;
			}
			out.add(new Line(ls, stop, stop + 1));
			start = stop + 1;
		}
	}

	/** Caret x (pixels) inside its visual line. */
	public int caretX() {
		Line l = lines.get(lineOf(caret));
		return measure.applyAsInt(text.substring(l.start(), Math.min(caret, l.end())));
	}

	/** Text position nearest to pixel {@code x} on visual line {@code index}. */
	public int positionAt(int index, int x) {
		Line l = lines.get(Math.clamp(index, 0, lines.size() - 1));
		int best = l.start();
		for (int p = l.start(); p <= l.end(); p++) {
			int w = measure.applyAsInt(text.substring(l.start(), p));
			if (w <= x) {
				best = p;
			}
			if (p < l.end()) {
				int next = measure.applyAsInt(text.substring(l.start(), p + 1));
				if (2 * x < w + next) {
					return p;
				}
			}
		}
		return best;
	}

	private void vertical(int dir, boolean select) {
		int li = lineOf(caret);
		int gx = goalX >= 0 ? goalX : caretX();
		int target = li + dir;
		int pos;
		if (target < 0) {
			pos = 0;
		} else if (target >= lines.size()) {
			pos = text.length();
		} else {
			pos = positionAt(target, gx);
		}
		caret = pos;
		if (!select) {
			anchor = caret;
		}
		goalX = gx;
	}

	// ---- keys ---------------------------------------------------------------------------------

	/**
	 * One key press. {@code ctrl} is the platform shortcut modifier (Cmd on macOS: the host passes
	 * {@code hasControlDownWithQuirk}). Everything that is not explicitly the host's (Esc, Tab,
	 * function keys) is swallowed, so no shortcut fires while the player types.
	 */
	public KeyResult key(int key, boolean shift, boolean ctrl, @Nullable Clipboard clipboard) {
		switch (key) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (shift) {
					insert("\n");
					return KeyResult.HANDLED;
				}
				return KeyResult.SUBMIT;
			}
			case InputConstants.KEY_BACKSPACE -> backspace(ctrl);
			case InputConstants.KEY_DELETE -> delete(ctrl);
			case InputConstants.KEY_LEFT -> {
				if (hasSelection() && !shift) {
					setCaret(selectionStart(), false);
				} else {
					setCaret(ctrl ? wordLeft(caret) : prevCodePoint(caret), shift);
				}
			}
			case InputConstants.KEY_RIGHT -> {
				if (hasSelection() && !shift) {
					setCaret(selectionEnd(), false);
				} else {
					setCaret(ctrl ? wordRight(caret) : nextCodePoint(caret), shift);
				}
			}
			case InputConstants.KEY_UP -> vertical(-1, shift);
			case InputConstants.KEY_DOWN -> vertical(1, shift);
			case InputConstants.KEY_HOME -> setCaret(ctrl ? 0 : lines.get(lineOf(caret)).start(), shift);
			case InputConstants.KEY_END -> setCaret(ctrl ? text.length() : lines.get(lineOf(caret)).end(), shift);
			case InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB -> {
				return KeyResult.UNHANDLED;
			}
			default -> {
				if (ctrl && key == InputConstants.KEY_A) {
					selectAll();
				} else if (ctrl && key == InputConstants.KEY_C) {
					if (clipboard != null && hasSelection()) {
						clipboard.set(selected());
					}
				} else if (ctrl && key == InputConstants.KEY_X) {
					if (clipboard != null && hasSelection()) {
						clipboard.set(selected());
						deleteSelection();
					}
				} else if (ctrl && key == InputConstants.KEY_V) {
					if (clipboard != null) {
						String s = clipboard.get();
						if (s != null && !s.isEmpty()) {
							insert(s);
						}
					}
				} else if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
					return KeyResult.UNHANDLED; // F3 debug, F11 fullscreen…
				}
				// any other key: printable input arrives through charTyped; swallow the press
			}
		}
		return KeyResult.HANDLED;
	}

	/** A typed character (from {@code charTyped}). */
	public boolean typed(int codepoint) {
		if (Character.isISOControl(codepoint) || !Character.isValidCodePoint(codepoint)) {
			return false;
		}
		insert(new String(Character.toChars(codepoint)));
		return true;
	}
}
