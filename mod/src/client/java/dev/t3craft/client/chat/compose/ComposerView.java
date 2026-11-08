package dev.t3craft.client.chat.compose;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * <p>feat/t3-ui-clean: laid out like T3 Code's composer: image thumbnails (each with a ✕) above the
 * text, and under it the bar with the clip button (image file picker, {@link ImageSources}), the
 * model picker and the round send button, which turns into stop (■) while a turn runs and the
 * input is empty. Ctrl+V pastes a clipboard image when there is one (text otherwise). When the
 * platform has no file dialog the clip button turns the input into a path field.
 */
public final class ComposerView implements ConversationPanel.Footer {
	public static final int MAX_LINES = 6;
	private static final int LINE_H = 10;
	private static final int PAD_X = 6;
	private static final int PAD_TOP = 4;
	private static final int BAR_H = 14;
	private static final int THUMB = 30;
	private static final int STRIP_H = THUMB + 4;
	private static final AtomicInteger IMAGE_IDS = new AtomicInteger();

	/** What the composer asks of its host. */
	public interface Host {
		/** "Enable control": the existing {@code OperatePairScreen} for {@code envId}. */
		void enableControl(String envId);

		/** A message just went out (the host scrolls the conversation to the newest line). */
		default void sent() {
		}
	}

	/**
	 * The platform's text input (IME) switch. Minecraft 26.x runs on SDL3, and SDL only delivers
	 * text events ({@code charTyped}) while text input is started: vanilla {@code EditBox} starts
	 * it when it gains focus ({@code Minecraft.onTextInputFocusChange}). A custom field that never
	 * asks receives key presses but no characters, which is the "focuses but cannot type" bug.
	 */
	public interface TextInput {
		/** Start ({@code on}) or stop text input for {@code owner} (stop is a no-op if someone else owns it). */
		void focusChanged(Object owner, boolean on);

		/** Where the IME candidate window goes, in GUI coordinates ({@code x1, y1, x2, y2}). */
		default void area(int x1, int y1, int x2, int y2) {
		}

		/** The real one: Minecraft's {@code TextInputManager}. */
		TextInput MINECRAFT = new TextInput() {
			@Override
			public void focusChanged(Object owner, boolean on) {
				Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, on);
			}

			@Override
			public void area(int x1, int y1, int x2, int y2) {
				Minecraft.getInstance().textInputManager().setTextInputArea(x1, y1, x2, y2);
			}
		};
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
	private final TextInput textInput;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private boolean focused;
	private boolean dragging;
	private int scrollLine;
	private List<Control> controls = List.of();
	private final List<Rect> controlRects = new ArrayList<>();
	private Rect text = Rect.EMPTY;
	private Rect primary = Rect.EMPTY;
	private Rect clip = Rect.EMPTY;
	private final ImageThumbs thumbs = new ImageThumbs();
	/** Per image on screen: [thumbnail, ✕] rectangles, same order as {@link ComposerState#images()}. */
	private final List<Rect[]> imageRects = new ArrayList<>();
	/** Path field mode (no native file dialog): the input holds a path; the draft is kept here. */
	private boolean pathMode;
	private @Nullable String pathModeDraft;
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
		this(state, theme, host, TextInput.MINECRAFT);
	}

	public ComposerView(ComposerState state, ChatTheme theme, Host host, TextInput textInput) {
		this.state = state;
		this.theme = theme;
		this.host = host;
		this.textInput = textInput;
	}

	/**
	 * A text color as drawn: {@code drawString} skips colors whose alpha byte is 0 (a bare
	 * {@code 0xRRGGBB}), so a theme value without alpha is made opaque instead of vanishing.
	 */
	public static int ink(int argb) {
		return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
	}

	/** Every color the composer draws text with (input, placeholder, status, buttons), as drawn. */
	public static List<Integer> textColors(ChatTheme t) {
		return List.of(ink(t.text()), ink(t.ghost()), ink(t.muted()), ink(t.working()), ink(t.errorText()), ink(t.disabled()),
				ink(t.accent()));
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
		if (pathMode && !(envId + ":" + threadId).equals(before)) {
			leavePathMode(false);
		}
		state.setThread(envId, threadId, title);
		if (!state.key().equals(before)) {
			scrollLine = 0;
			accessCheckedAt = System.currentTimeMillis();
			if (!state.canOperate()) {
				setFocused(false);
			}
		}
	}

	public void clearThread() {
		if (pathMode) {
			leavePathMode(false);
		}
		state.clearThread();
		setFocused(false);
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
		boolean was = focused;
		focused = f && editable();
		dragging = false;
		if (focused) {
			textInput.focusChanged(this, true); // idempotent; also re-arms it after Gui.setScreen stopped it
		} else if (was) {
			textInput.focusChanged(this, false);
		}
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
		return PAD_TOP + strip() + visibleLines() * LINE_H + 2 + BAR_H + 2;
	}

	private int strip() {
		return state.images().isEmpty() ? 0 : STRIP_H;
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
				setFocused(false);
			}
		}
		ComposerText e = state.editor();
		ChatDraw.field(g, t, r, focused);
		thumbs.retain(state.liveImageIds());
		drawImages(g, font, t, r.x() + PAD_X, r.y() + PAD_TOP, r.right() - PAD_X, mouseX, mouseY);
		text = new Rect(r.x() + PAD_X, r.y() + PAD_TOP + strip(), r.w() - 2 * PAD_X - 4, visibleLines() * LINE_H);
		e.layout(text.w(), font::width);
		keepCaretVisible();
		if (focused) {
			// Gui.setScreen stops text input (e.g. back from a confirmation screen): keep it on while focused
			textInput.focusChanged(this, true);
			int cy = text.y() + (e.lineOf(e.caret()) - scrollLine) * LINE_H;
			textInput.area(text.x(), cy, text.right(), cy + LINE_H);
		}

		// ---- input
		if (e.isEmpty()) {
			String ph = state.key() == null ? I18n.get("t3craft.chat.compose.no_thread")
					: !state.canOperate() ? I18n.get("t3craft.chat.compose.read_only")
					: pathMode ? "Path of an image (PNG, JPEG, GIF, WebP) · Enter attaches · Esc cancels"
					: I18n.get("t3craft.chat.compose.placeholder");
			g.text(font, ChatDraw.clip(font, ph, text.w()), text.x(), text.y() + 1, ink(t.ghost()), false);
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

		// ---- controls bar: [clip] [slot controls] status ............ [primary]
		int by = r.bottom() - BAR_H - 1;
		int cx = r.x() + PAD_X - 2;
		int cy = by + 1;
		clip = Rect.EMPTY;
		if (editable()) {
			clip = new Rect(cx, cy, 13, 12);
			boolean hover = clip.contains(mouseX, mouseY);
			if (hover || pathMode) {
				ChatDraw.round(g, clip.x(), clip.y(), clip.w(), clip.h(), t.hover());
			}
			paperclip(g, clip.x() + 4, clip.y() + 1, ink(hover || pathMode ? t.accent() : t.muted()));
			if (hover) {
				g.setTooltipForNextFrame(font, Component.literal(pathMode ? "Cancel the path field" : "Attach images (or paste one with Ctrl+V)"),
						mouseX, mouseY);
			}
			cx += clip.w() + 4;
		}
		// T3's bar: clip on the left; model picker and the round send button on the right
		primary = drawPrimary(g, font, t, r.right() - PAD_X, cy, mouseX, mouseY);
		controlRects.clear();
		int controlsW = 0;
		for (Control c : controls) {
			controlsW += c.width(font) + 4;
		}
		int kx = Math.max(cx, primary.x() - 4 - controlsW);
		int controlsLeft = kx;
		for (Control c : controls) {
			int w = c.width(font);
			c.render(g, font, kx, cy, mouseX, mouseY);
			controlRects.add(new Rect(kx, cy, w, 12));
			kx += w + 4;
		}
		String status = status();
		if (status != null) {
			int color = state.error() != null ? t.errorText() : state.running() ? t.working() : t.muted();
			int room = controlsLeft - 6 - cx;
			if (room > 20) {
				if (state.error() == null && state.running()) {
					ChatDraw.pulsingDot(g, t, "working", t.working(), cx, cy + 3);
					cx += 9;
					room -= 9;
				}
				String s = ChatDraw.clip(font, status, room);
				g.text(font, s, cx, cy + 2, ink(color), false);
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
			default -> state.running() ? I18n.get("t3craft.chat.compose.working") : null;
		};
	}

	/** A 5×10 paperclip drawn with fills (the game font has no paperclip glyph). */
	private static void paperclip(GuiGraphicsExtractor g, int x, int y, int c) {
		g.fill(x + 1, y, x + 4, y + 1, c); // top bend
		g.fill(x, y + 1, x + 1, y + 9, c); // outer left
		g.fill(x + 4, y + 1, x + 5, y + 7, c); // outer right
		g.fill(x + 1, y + 9, x + 3, y + 10, c); // bottom bend
		g.fill(x + 3, y + 8, x + 4, y + 9, c);
		g.fill(x + 2, y + 3, x + 3, y + 8, c); // inner wire
	}

	/** The thumbnails row (T3's image chips): each one with a ✕ in its corner; hover shows name and size. */
	private void drawImages(GuiGraphicsExtractor g, Font font, ChatTheme t, int x, int y, int right, int mouseX, int mouseY) {
		imageRects.clear();
		List<ImageAttachment> imgs = state.images();
		int ix = x;
		for (ImageAttachment img : imgs) {
			if (ix + THUMB > right) {
				int more = imgs.size() - imageRects.size();
				g.text(font, "+" + more, ix, y + THUMB / 2 - 4, ink(t.muted()), false);
				break;
			}
			Rect box = new Rect(ix, y, THUMB, THUMB);
			Rect x0 = new Rect(box.right() - 9, box.y() + 1, 8, 8);
			imageRects.add(new Rect[] {box, x0});
			ChatDraw.round(g, box.x(), box.y(), box.w(), box.h(), ChatTheme.withAlpha(t.edge(), 140));
			ImageThumbs.Thumb th = thumbs.get(img);
			if (th != null) {
				// fit inside the box, centred
				double sc = Math.min((double) (THUMB - 2) / th.w(), (double) (THUMB - 2) / th.h());
				int dw = Math.max(1, (int) (th.w() * sc));
				int dh = Math.max(1, (int) (th.h() * sc));
				int dx = box.x() + (THUMB - dw) / 2;
				int dy = box.y() + (THUMB - dh) / 2;
				g.blit(th.id(), dx, dy, dx + dw, dy + dh, 0f, 1f, 0f, 1f);
			} else {
				String tag = ImageAttachment.extension(img.mime()).toUpperCase(java.util.Locale.ROOT);
				g.text(font, tag, box.x() + (THUMB - font.width(tag)) / 2, box.y() + THUMB / 2 - 4, ink(t.muted()), false);
			}
			boolean overX = x0.contains(mouseX, mouseY);
			ChatDraw.round(g, x0.x(), x0.y(), x0.w(), x0.h(), overX ? ChatTheme.withAlpha(t.errorText(), 230) : 0xC0202020);
			g.text(font, "✕", x0.x() + 1, x0.y(), 0xFFFFFFFF, false);
			if (overX) {
				g.setTooltipForNextFrame(font, Component.literal("Remove"), mouseX, mouseY);
			} else if (box.contains(mouseX, mouseY)) {
				g.setTooltipForNextFrame(font, Component.literal(img.name() + " · " + ImageAttachment.mb(img.size())), mouseX, mouseY);
			}
			ix += THUMB + 4;
		}
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
			g.text(font, row, text.x(), y + 1, ink(t.text()), false);
			if (focused && i == caretLine && ChatDraw.caretOn()) {
				int cx = text.x() + e.caretX();
				g.fill(cx, y, cx + 1, y + 9, t.text());
			}
		}
	}

	private Rect drawPrimary(GuiGraphicsExtractor g, Font font, ChatTheme t, int right, int y, int mouseX, int mouseY) {
		ComposerState.Button b = state.button();
		if (b == ComposerState.Button.NONE) {
			return new Rect(right, y, 0, 12);
		}
		if (b == ComposerState.Button.ENABLE_CONTROL) {
			String label = I18n.get("t3craft.chat.reply.enable_button");
			int w = font.width(label) + 10;
			Rect r = new Rect(right - w, y, w, 12);
			boolean hover = r.contains(mouseX, mouseY);
			ChatDraw.pillBox(g, t, r.x(), r.y(), r.w(), hover);
			g.text(font, label, r.x() + 5, r.y() + 2, ink(t.accent()), false);
			if (hover) {
				g.setTooltipForNextFrame(font, Component.translatable("t3craft.operate.enable.tooltip"), mouseX, mouseY);
			}
			return r;
		}
		// T3's round primary button: ↑ send (steers a running turn), ■ stop, … in flight
		boolean stop = b == ComposerState.Button.STOP || b == ComposerState.Button.STOPPING;
		boolean enabled = b == ComposerState.Button.SEND || b == ComposerState.Button.STOP;
		String glyph = switch (b) {
			case STOP -> "■";
			case SENDING, STOPPING -> "…";
			default -> "↑";
		};
		Rect r = new Rect(right - 16, y, 16, 12);
		boolean hover = enabled && r.contains(mouseX, mouseY);
		int fill = !enabled ? ChatTheme.withAlpha(t.edge(), 120) : stop ? ChatTheme.withAlpha(t.errorText(), hover ? 255 : 200)
				: ChatTheme.withAlpha(t.accent(), hover ? 255 : 210);
		ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), fill);
		g.text(font, glyph, r.x() + (r.w() - font.width(glyph)) / 2 + 1, r.y() + 2, enabled ? 0xFFFFFFFF : ink(t.disabled()), false);
		if (hover) {
			Component tip = stop ? Component.translatable("t3craft.chat.compose.stop_tooltip")
					: Component.literal(state.running() ? "Send (steers the running turn) · Enter" : "Send · Enter (Shift+Enter: new line)");
			g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
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
		for (int i = 0; i < imageRects.size() && i < state.images().size(); i++) {
			Rect[] ir = imageRects.get(i);
			if (ir[1].contains(mx, my) || ir[0].contains(mx, my) && hasShift()) {
				state.removeImage(state.images().get(i).localId());
				return true;
			}
			if (ir[0].contains(mx, my)) {
				return true;
			}
		}
		if (clip.w() > 0 && clip.contains(mx, my)) {
			if (pathMode) {
				leavePathMode(false);
			} else {
				pickImages();
			}
			return true;
		}
		if (primary.w() > 0 && primary.contains(mx, my)) {
			if (pathMode) {
				leavePathMode(true);
			} else if (state.button() == ComposerState.Button.ENABLE_CONTROL) {
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
		return keyPressed(event.key(), event.hasShiftDown(), event.hasControlDownWithQuirk());
	}

	/** {@link #keyPressed(KeyEvent)} on its parts ({@code ctrl} is Cmd on macOS). */
	public boolean keyPressed(int key, boolean shift, boolean ctrl) {
		if (!focused) {
			return false;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (pathMode) {
				leavePathMode(false);
				return true;
			}
			setFocused(false);
			return true;
		}
		if (pathMode && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && !shift) {
			leavePathMode(true);
			return true;
		}
		if (ctrl && !shift && key == InputConstants.KEY_V && !pathMode && pasteImages()) {
			return true;
		}
		String before = state.editor().text();
		ComposerText.KeyResult r = state.editor().key(key, shift, ctrl, clipboard);
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
		return key == InputConstants.KEY_TAB;
	}

	// ---- images -------------------------------------------------------------------------------

	/** Adds image bytes to the composer (paste, file, drop); the refusal, if any, shows as the error. */
	public boolean addImage(byte[] bytes, @Nullable String name) {
		return state.addImage("img-" + IMAGE_IDS.incrementAndGet(), name, bytes) == null;
	}

	/** Reads and adds image files (the dialog's result, files dropped on the window). */
	public void addFiles(List<Path> files) {
		for (Path p : files) {
			try {
				ImageSources.Picked f = ImageSources.read(p);
				if (!addImage(f.bytes(), f.name())) {
					return; // the refusal is shown; stop at the first one
				}
			} catch (IOException | RuntimeException e) {
				state.setError("Could not read " + p.getFileName() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
				return;
			}
		}
	}

	/** The clip button: the native file dialog, or the path field when the platform has none. */
	public void pickImages() {
		if (!editable()) {
			return;
		}
		boolean opened = ImageSources.pickFile(this::addFiles, state::setError);
		if (!opened) {
			enterPathMode();
		}
	}

	/** Ctrl+V: true when the clipboard held images (added), false to paste text instead. */
	private boolean pasteImages() {
		List<ImageSources.Picked> imgs = ImageSources.clipboardImages();
		if (imgs.isEmpty()) {
			return false;
		}
		for (ImageSources.Picked p : imgs) {
			if (!addImage(p.bytes(), p.name())) {
				break;
			}
		}
		return true;
	}

	private void enterPathMode() {
		if (pathMode || !editable()) {
			return;
		}
		pathMode = true;
		pathModeDraft = state.editor().text();
		state.editor().clear();
		setFocused(true);
	}

	/** Leaves the path field; with {@code attach} the typed path is read and added first. */
	private void leavePathMode(boolean attach) {
		if (!pathMode) {
			return;
		}
		String typed = state.editor().text().strip();
		pathMode = false;
		String draft = pathModeDraft == null ? "" : pathModeDraft;
		pathModeDraft = null;
		state.editor().set(draft, draft.length());
		if (attach && !typed.isEmpty()) {
			try {
				addFiles(List.of(ImageSources.parsePath(typed)));
			} catch (RuntimeException e) {
				state.setError("Not a valid path: " + typed);
			}
		}
	}

	public boolean pathMode() {
		return pathMode;
	}

	public boolean charTyped(CharacterEvent event) {
		return charTyped(event.codepoint());
	}

	/** A typed character; only arrives while text input is started (see {@link TextInput}). */
	public boolean charTyped(int codepoint) {
		if (!focused) {
			return false;
		}
		if (state.editor().typed(codepoint)) {
			state.clearError();
			keepCaretVisible();
		}
		return true;
	}
}
