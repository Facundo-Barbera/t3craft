package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerText;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A multi-line text box of fixed height ({@code rows} lines) that scrolls. Unlike {@link T3Field} it is
 * drawn by the kit, so it clips with its panel and has no vanilla widget to host: the text model, the
 * keyboard (arrows, Home / End, Ctrl+A/C/V/X/Z/Y, word moves) and the wrapping are the chat composer's
 * ({@link ComposerText}). Enter inserts a newline; Ctrl+Enter runs {@link #onSubmit}; Tab leaves the box
 * (the host moves the focus); the mouse places the caret, drags a selection and picks a word on a double
 * click; the wheel scrolls when the text is longer than the box.
 *
 * <p>Keep one per input for the life of the screen, like {@link T3Field}: the text lives here, and the
 * host only places it with {@link #height()} tall bounds. Typed characters reach it through
 * {@link T3Form#charTyped}.
 */
public final class T3TextArea extends T3Control {
	private static final int PAD = 4;
	/** Room kept at the right for the scrollbar, so the wrapping does not change when it appears. */
	private static final int BAR = 8;

	private final Font font;
	private final int rows;
	private final ComposerText text = new ComposerText();
	private int maxLength = 2000;
	private @Nullable String hint;
	private @Nullable Consumer<String> onChange;
	private @Nullable Runnable onSubmit;
	private int scrollLine;
	private int laidOutW = -1;
	private boolean dragging;
	private long lastClickAt;
	private int lastClickPos = -1;
	private final ComposerText.Clipboard clipboard = new ComposerText.Clipboard() {
		@Override
		public String get() {
			return Minecraft.getInstance().keyboardHandler.getClipboard();
		}

		@Override
		public void set(String s) {
			Minecraft.getInstance().keyboardHandler.setClipboard(s);
		}
	};

	/** @param rows lines of text that show at once (at least 2) */
	public T3TextArea(Font font, int rows) {
		this.font = font;
		this.rows = Math.max(2, rows);
	}

	public T3TextArea maxLength(int n) {
		this.maxLength = Math.max(1, Math.min(n, ComposerText.MAX_CHARS));
		return this;
	}

	public T3TextArea hint(String hint) {
		this.hint = hint;
		return this;
	}

	public T3TextArea onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	/** Runs on Ctrl+Enter while the box has the keyboard. */
	public T3TextArea onSubmit(Runnable onSubmit) {
		this.onSubmit = onSubmit;
		return this;
	}

	public String value() {
		return text.text();
	}

	/** Replaces the text (no {@code onChange}); the caret goes to the end. */
	public void setValue(String v) {
		text.set(v.length() > maxLength ? v.substring(0, maxLength) : v);
		scrollLine = 0;
		scrollToCaret();
	}

	/** Height to give the box in {@link T3Panel#place} or a dialog: its rows plus the padding. */
	public int height() {
		return rows * T3Draw.lineHeight(font) + 2 * PAD;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean takesClickFocus() {
		return true;
	}

	/** SDL only delivers typed characters while text input is started (see {@code ComposerView.TextInput}). */
	@Override
	protected void focusChanged(boolean on) {
		Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, on);
	}

	// ---- layout and drawing --------------------------------------------------------------------------

	private void ensureLayout() {
		int w = Math.max(16, bounds().w() - 2 * PAD - BAR);
		if (w != laidOutW) {
			laidOutW = w;
			text.layout(w, s -> font.width(s));
		}
	}

	private Rect inner() {
		Rect r = bounds();
		return new Rect(r.x() + PAD, r.y() + PAD, r.w() - 2 * PAD - BAR, rows * T3Draw.lineHeight(font));
	}

	private void clampScroll(int lineCount) {
		scrollLine = Math.max(0, Math.min(scrollLine, Math.max(0, lineCount - rows)));
	}

	private void scrollToCaret() {
		ensureLayout();
		int li = text.lineOf(text.caret());
		if (li < scrollLine) {
			scrollLine = li;
		} else if (li >= scrollLine + rows) {
			scrollLine = li - rows + 1;
		}
		clampScroll(text.lines().size());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		Rect r = bounds();
		boolean focus = hasFocus() && enabled();
		ChatDraw.field(g, t, r, focus);
		if (focus) {
			ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), t.brass());
		}
		ensureLayout();
		List<ComposerText.Line> lines = text.lines();
		clampScroll(lines.size());
		int lh = T3Draw.lineHeight(f);
		Rect in = inner();
		String s = text.text();
		g.enableScissor(in.x(), in.y(), in.right(), in.bottom());
		if (s.isEmpty() && hint != null) {
			g.text(f, ChatDraw.clip(f, hint, in.w()), in.x(), in.y(), t.ghost(), t.shadow());
		}
		int selA = text.selectionStart();
		int selB = text.selectionEnd();
		int textColor = enabled() ? t.text() : t.disabled();
		for (int row = 0; row < rows; row++) {
			int li = scrollLine + row;
			if (li >= lines.size()) {
				break;
			}
			ComposerText.Line l = lines.get(li);
			int y = in.y() + row * lh;
			if (selB > selA && selB > l.start() && selA <= l.end()) {
				int a = Math.max(selA, l.start());
				int b = Math.min(selB, l.end());
				int x0 = f.width(s.substring(l.start(), a));
				int x1 = f.width(s.substring(l.start(), b)) + (selB > l.end() ? 3 : 0);
				g.fill(in.x() + x0, y - 1, in.x() + x1, y + lh - 1, t.selected());
			}
			g.text(f, s.substring(l.start(), l.end()), in.x(), y, textColor, t.shadow());
		}
		if (focus) {
			int row = text.lineOf(text.caret()) - scrollLine;
			if (row >= 0 && row < rows) {
				int cx = in.x() + text.caretX();
				int cy = in.y() + row * lh;
				if ((System.currentTimeMillis() / 530) % 2 == 0) {
					g.fill(cx, cy - 1, cx + 1, cy + lh - 1, t.text());
				}
				Minecraft.getInstance().textInputManager().setTextInputArea(cx, cy, cx + 1, cy + lh);
			}
		}
		g.disableScissor();
		if (lines.size() > rows) {
			double max = Math.max(1, lines.size() - rows);
			ChatDraw.scrollbar(g, t, r.right() - 8, in.y(), in.h(), lines.size() * lh, rows * lh, scrollLine / max, dragging);
		}
	}

	// ---- keyboard ----------------------------------------------------------------------------------------

	private void afterEdit(String before) {
		String now = text.text();
		if (now.length() > maxLength) {
			text.replaceAll(now.substring(0, maxLength), text.caret());
			now = text.text();
		}
		scrollToCaret();
		if (onChange != null && !now.equals(before)) {
			onChange.accept(now);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (!hasFocus() || !enabled() || !visible()) {
			return false;
		}
		int k = e.key();
		boolean ctrl = e.hasControlDownWithQuirk();
		boolean shift = e.hasShiftDown();
		ensureLayout();
		String before = text.text();
		if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
			if (ctrl && onSubmit != null) {
				onSubmit.run();
			} else if (before.length() < maxLength || text.hasSelection()) {
				text.insert("\n");
				afterEdit(before);
			}
			return true;
		}
		if (k == InputConstants.KEY_PAGEUP || k == InputConstants.KEY_PAGEDOWN) {
			int li = text.lineOf(text.caret()) + (k == InputConstants.KEY_PAGEUP ? -rows : rows);
			int target = Math.max(0, Math.min(text.lines().size() - 1, li));
			text.setCaret(text.positionAt(target, text.caretX()), shift);
			scrollToCaret();
			return true;
		}
		if (k == InputConstants.KEY_TAB) {
			return false; // the host moves the focus
		}
		ComposerText.KeyResult res = text.key(k, shift, ctrl, clipboard);
		if (res == ComposerText.KeyResult.UNHANDLED) {
			return false; // Esc, function keys
		}
		afterEdit(before);
		return true;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!hasFocus() || !enabled() || !visible()) {
			return false;
		}
		String before = text.text();
		if (before.length() >= maxLength && !text.hasSelection()) {
			return true; // full: swallow it
		}
		ensureLayout();
		if (!text.typed(codepoint)) {
			return false;
		}
		afterEdit(before);
		return true;
	}

	// ---- mouse -------------------------------------------------------------------------------------------

	/** Text position under the pointer; a pointer above or below the box picks the first or last visible line. */
	private int positionAt(double mx, double my) {
		ensureLayout();
		Rect in = inner();
		int lh = T3Draw.lineHeight(font);
		int li = scrollLine + (int) Math.floor((my - in.y()) / lh);
		li = Math.max(0, Math.min(text.lines().size() - 1, li));
		return text.positionAt(li, (int) (mx - in.x()));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled() || !visible() || !MouseButtons.isLeft(button) || !bounds().contains(mx, my)) {
			return false;
		}
		int pos = positionAt(mx, my);
		long now = System.currentTimeMillis();
		if (pos == lastClickPos && now - lastClickAt < 350) {
			text.selectWordAt(pos);
		} else {
			text.setCaret(pos, Minecraft.getInstance().hasShiftDown());
		}
		lastClickAt = now;
		lastClickPos = pos;
		dragging = true;
		scrollToCaret();
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		text.setCaret(positionAt(mx, my), true);
		scrollToCaret();
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!visible() || !bounds().contains(mx, my) || text.lines().size() <= rows) {
			return false;
		}
		scrollLine -= (int) Math.signum(dy) * 2;
		clampScroll(text.lines().size());
		return true;
	}
}
