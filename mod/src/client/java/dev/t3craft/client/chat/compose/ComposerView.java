package dev.t3craft.client.chat.compose;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The chat composer, drawn like T3 Code's ({@code apps/web/src/components/chat/ChatComposer.tsx}):
 * a multi-line input that grows up to {@value #MAX_LINES} lines and then scrolls, and under it a
 * controls bar ({@code ComposerControlsBar}) with a slot on the left for selectors (model,
 * provider, effort: {@link #setControls}) and the primary button on the right
 * ({@code ComposerPrimaryActions}: send, or Stop while a turn runs and the input is empty).
 * Read-only environments get a disabled input and "Enable control".
 *
 * <p>Self-contained: any host gives it bounds and forwards input.
 * <pre>{@code
 * ComposerView c = new ComposerView(state, theme, host);
 * c.setThread(envId, threadId, title);               // every frame is fine (no-op when unchanged)
 * c.observe(transcriptView.entries(), running, activeRunId);
 * c.setBounds(rect); c.render(g, font, mouseX, mouseY);   // height: c.height(width)
 * c.mouseClicked(mx, my, button); c.keyPressed(event); c.charTyped(event);
 * }</pre>
 * It is also a {@link ConversationPanel.Footer}, so {@code conversation.setFooter(c)} puts it where
 * the reply bar was, and its optimistic rows are a {@link ConversationPanel.Extra}.
 */
public final class ComposerView implements ConversationPanel.Footer {
	public static final int MAX_LINES = 6;
	private static final int LINE_H = 10;
	private static final int PAD_X = 6;
	private static final int PAD_TOP = 4;
	private static final int BAR_H = 14;

	/** What the composer asks of its host. */
	public interface Host {
		/** "Enable control": the existing {@code OperatePairScreen} for {@code envId}. */
		void enableControl(String envId);

		/** A message just went out (the host scrolls the conversation to the newest line). */
		default void sent() {
		}
	}

	/**
	 * A control in the bar's left slot (feat/t3-actions inserts the model/provider/effort selectors
	 * here). 12 px tall, drawn left to right.
	 */
	public interface Control {
		int width(Font font);

		void render(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY);

		/** Called with clicks inside its rectangle. */
		boolean mouseClicked(double mx, double my, int button);
	}

	private final ComposerState state;
	private final Host host;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private boolean focused;
	private boolean dragging;
	private int scrollLine;
	private List<Control> controls = List.of();
	private final List<Rect> controlRects = new ArrayList<>();
	private Rect text = Rect.EMPTY;
	private Rect primary = Rect.EMPTY;
	private long accessCheckedAt;
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

	public ComposerView(ComposerState state, ChatTheme theme, Host host) {
		this.state = state;
		this.theme = theme;
		this.host = host;
	}

	public ComposerState state() {
		return state;
	}

	public void setTheme(ChatTheme t) {
		theme = t;
	}

	public void setControls(List<Control> c) {
		controls = List.copyOf(c);
	}

	public void setThread(String envId, String threadId, @Nullable String title) {
		String before = state.key();
		state.setThread(envId, threadId, title);
		if (!state.key().equals(before)) {
			scrollLine = 0;
			accessCheckedAt = System.currentTimeMillis();
			if (!state.canOperate()) {
				focused = false;
			}
		}
	}

	public void clearThread() {
		state.clearThread();
		focused = false;
	}

	/** See {@link ComposerState#observe}. */
	public void observe(List<dev.t3craft.client.chat.ChatEntry> entries, boolean running, @Nullable String activeRunId) {
		state.observe(entries, running, activeRunId);
	}

	/** The optimistic rows for {@link ConversationPanel#setExtra}. */
	public ConversationPanel.Extra extra() {
		return new ConversationPanel.Extra() {
			@Override
			public List<dev.t3craft.client.chat.ChatEntry> entries() {
				return state.pendingEntries();
			}

			@Override
			public long version() {
				return state.version();
			}
		};
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		focused = f && editable();
		dragging = false;
	}

	private boolean editable() {
		return state.key() != null && state.canOperate();
	}

	public Rect bounds() {
		return bounds;
	}

	public void setBounds(Rect r) {
		bounds = r;
	}

	public boolean contains(double mx, double my) {
		return bounds.contains(mx, my);
	}

	// ---- layout -------------------------------------------------------------------------------

	private int visibleLines() {
		return Math.clamp(state.editor().lines().size(), 1, MAX_LINES);
	}

	@Override
	public int height(int width) {
		return PAD_TOP + visibleLines() * LINE_H + 2 + BAR_H + 2;
	}

	private void keepCaretVisible() {
		ComposerText e = state.editor();
		int line = e.lineOf(e.caret());
		int vis = visibleLines();
		if (line < scrollLine) {
			scrollLine = line;
		} else if (line >= scrollLine + vis) {
			scrollLine = line - vis + 1;
		}
		scrollLine = Math.clamp(scrollLine, 0, Math.max(0, e.lines().size() - vis));
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void render(GuiGraphicsExtractor g, Font font, Rect r, int mouseX, int mouseY) {
		setBounds(r);
		render(g, font, mouseX, mouseY);
	}

	public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		ChatTheme t = theme;
		Rect r = bounds;
		if (r.w() < 40 || r.h() < 20) {
			return;
		}
		if (state.key() != null && System.currentTimeMillis() - accessCheckedAt > 1000) {
			accessCheckedAt = System.currentTimeMillis();
			state.refreshAccess();
			if (!state.canOperate()) {
				focused = false;
			}
		}
		ComposerText e = state.editor();
		ChatDraw.field(g, t, r, focused);
		text = new Rect(r.x() + PAD_X, r.y() + PAD_TOP, r.w() - 2 * PAD_X - 4, visibleLines() * LINE_H);
		e.layout(text.w(), font::width);
		keepCaretVisible();

		// ---- input
		if (e.isEmpty()) {
			String ph = state.key() == null ? I18n.get("t3craft.chat.compose.no_thread")
					: !state.canOperate() ? I18n.get("t3craft.chat.compose.read_only") : I18n.get("t3craft.chat.compose.placeholder");
			g.text(font, ChatDraw.clip(font, ph, text.w()), text.x(), text.y() + 1, t.ghost(), false);
			if (focused && ChatDraw.caretOn()) {
				g.fill(text.x(), text.y(), text.x() + 1, text.y() + 9, t.text());
			}
		} else {
			drawText(g, font, t, e);
		}
		int total = e.lines().size();
		int vis = visibleLines();
		if (total > vis) {
			ChatDraw.scrollbar(g, t, r.right() - 6, text.y(), text.h(), total * LINE_H, vis * LINE_H,
					(double) scrollLine / Math.max(1, total - vis), false);
		}

		// ---- controls bar: [slot controls] status ............ [primary]
		int by = r.bottom() - BAR_H - 1;
		g.fill(r.x() + 3, by - 1, r.right() - 3, by, ChatTheme.withAlpha(t.edge(), 90));
		int cx = r.x() + PAD_X;
		int cy = by + 1;
		controlRects.clear();
		for (Control c : controls) {
			int w = c.width(font);
			c.render(g, font, cx, cy, mouseX, mouseY);
			controlRects.add(new Rect(cx, cy, w, 12));
			cx += w + 4;
		}
		primary = drawPrimary(g, font, t, r.right() - PAD_X, cy, mouseX, mouseY);
		String status = status();
		if (status != null) {
			int color = state.error() != null ? t.errorText() : state.running() ? t.working() : t.muted();
			int room = primary.x() - 6 - cx;
			if (room > 20) {
				if (state.error() == null && state.running()) {
					ChatDraw.pulsingDot(g, t, "working", t.working(), cx, cy + 3);
					cx += 9;
					room -= 9;
				}
				String s = ChatDraw.clip(font, status, room);
				g.text(font, s, cx, cy + 2, color, false);
				if (state.error() != null && mouseX >= cx && mouseX < cx + font.width(s) && mouseY >= cy && mouseY < cy + 12) {
					g.setTooltipForNextFrame(font, Component.literal(state.error()), mouseX, mouseY);
				}
			}
		}
	}

	private @Nullable String status() {
		if (state.error() != null) {
			return "⚠ " + state.error();
		}
		return switch (state.button()) {
			case SENDING -> I18n.get("t3craft.chat.compose.sending");
			case STOPPING -> I18n.get("t3craft.chat.compose.stopping");
			default -> state.running() ? I18n.get("t3craft.chat.compose.working")
					: focused ? I18n.get("t3craft.chat.compose.hint") : null;
		};
	}

	private void drawText(GuiGraphicsExtractor g, Font font, ChatTheme t, ComposerText e) {
		String s = e.text();
		int selA = e.selectionStart();
		int selB = e.selectionEnd();
		int caretLine = e.lineOf(e.caret());
		List<ComposerText.Line> lines = e.lines();
		int vis = visibleLines();
		for (int i = scrollLine; i < Math.min(lines.size(), scrollLine + vis); i++) {
			ComposerText.Line l = lines.get(i);
			int y = text.y() + (i - scrollLine) * LINE_H;
			String row = s.substring(l.start(), l.end());
			int a = Math.max(selA, l.start());
			int b = Math.min(selB, l.end());
			boolean selNewline = selA <= l.end() && selB > l.end() && l.next() > l.end();
			if (a < b || selNewline && a <= b) {
				int x0 = text.x() + font.width(s.substring(l.start(), Math.max(l.start(), Math.min(a, l.end()))));
				int x1 = text.x() + font.width(s.substring(l.start(), Math.max(l.start(), b))) + (selNewline ? 3 : 0);
				g.fill(x0, y, x1, y + LINE_H - 1, ChatTheme.withAlpha(t.accent(), 90));
			}
			g.text(font, row, text.x(), y + 1, t.text(), false);
			if (focused && i == caretLine && ChatDraw.caretOn()) {
				int cx = text.x() + e.caretX();
				g.fill(cx, y, cx + 1, y + 9, t.text());
			}
		}
	}

	private Rect drawPrimary(GuiGraphicsExtractor g, Font font, ChatTheme t, int right, int y, int mouseX, int mouseY) {
		ComposerState.Button b = state.button();
		String label = switch (b) {
			case NONE -> "";
			case ENABLE_CONTROL -> I18n.get("t3craft.chat.reply.enable_button");
			case SEND, SEND_DISABLED -> I18n.get("t3craft.chat.compose.send");
			case SENDING -> I18n.get("t3craft.chat.compose.send");
			case STOP, STOPPING -> I18n.get("t3craft.chat.compose.stop");
		};
		if (label.isEmpty()) {
			return new Rect(right, y, 0, 12);
		}
		int w = font.width(label) + 10;
		Rect r = new Rect(right - w, y, w, 12);
		boolean enabled = b == ComposerState.Button.SEND || b == ComposerState.Button.STOP || b == ComposerState.Button.ENABLE_CONTROL;
		boolean hover = enabled && r.contains(mouseX, mouseY);
		ChatDraw.pillBox(g, t, r.x(), r.y(), r.w(), hover);
		int color = !enabled ? t.disabled() : b == ComposerState.Button.STOP ? t.errorText() : t.accent();
		g.text(font, label, r.x() + 5, r.y() + 2, color, false);
		if (hover && b == ComposerState.Button.ENABLE_CONTROL) {
			g.setTooltipForNextFrame(font, Component.translatable("t3craft.operate.enable.tooltip"), mouseX, mouseY);
		} else if (hover && b == ComposerState.Button.STOP) {
			g.setTooltipForNextFrame(font, Component.translatable("t3craft.chat.compose.stop_tooltip"), mouseX, mouseY);
		}
		return r;
	}

	// ---- input --------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!contains(mx, my)) {
			setFocused(false);
			return false;
		}
		if (!MouseButtons.isLeft(button)) {
			return true;
		}
		if (primary.w() > 0 && primary.contains(mx, my)) {
			if (state.button() == ComposerState.Button.ENABLE_CONTROL) {
				host.enableControl(state.envId());
			} else {
				boolean send = state.button() == ComposerState.Button.SEND;
				state.primary();
				if (send) {
					host.sent();
				}
			}
			return true;
		}
		for (int i = 0; i < controlRects.size() && i < controls.size(); i++) {
			if (controlRects.get(i).contains(mx, my)) {
				controls.get(i).mouseClicked(mx, my, button);
				return true;
			}
		}
		setFocused(true);
		if (focused) {
			placeCaret(mx, my, hasShift());
			dragging = true;
		}
		return true;
	}

	private static boolean hasShift() {
		return Minecraft.getInstance().hasShiftDown();
	}

	private void placeCaret(double mx, double my, boolean select) {
		ComposerText e = state.editor();
		int line = scrollLine + (int) Math.floor((my - text.y()) / LINE_H);
		if (line < 0) {
			e.setCaret(0, select);
		} else if (line >= e.lines().size()) {
			e.setCaret(e.text().length(), select);
		} else {
			e.setCaret(e.positionAt(line, (int) (mx - text.x())), select);
		}
	}

	/** Drag-selects while the button is held after a click in the input. */
	public boolean mouseDragged(double mx, double my) {
		if (!dragging || !focused) {
			return false;
		}
		placeCaret(mx, my, true);
		return true;
	}

	public boolean mouseReleased() {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!contains(mx, my)) {
			return false;
		}
		int max = Math.max(0, state.editor().lines().size() - visibleLines());
		scrollLine = Math.clamp(scrollLine + (dy > 0 ? -1 : 1), 0, max);
		return true;
	}

	/**
	 * While focused every key is the composer's (no shortcut fires) except function keys; Esc
	 * leaves the input (a second Esc reaches the host). Enter sends, Shift+Enter is a newline.
	 */
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		if (event.isEscape()) {
			setFocused(false);
			return true;
		}
		String before = state.editor().text();
		ComposerText.KeyResult r = state.editor().key(event.key(), event.hasShiftDown(), event.hasControlDownWithQuirk(), clipboard);
		if (r == ComposerText.KeyResult.SUBMIT) {
			if (state.submit()) {
				host.sent();
			}
			return true;
		}
		if (r == ComposerText.KeyResult.HANDLED) {
			if (!before.equals(state.editor().text())) {
				state.clearError();
			}
			keepCaretVisible();
			return true;
		}
		return event.key() == InputConstants.KEY_TAB;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!focused) {
			return false;
		}
		if (state.editor().typed(event.codepoint())) {
			state.clearError();
			keepCaretVisible();
		}
		return true;
	}
}
