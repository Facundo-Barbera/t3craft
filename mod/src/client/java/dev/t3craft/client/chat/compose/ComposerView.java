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
 * The chat composer, drawn like T3 Code's ({@code apps/web/src/components/chat/ChatComposer.tsx},
 * {@code ComposerSurface}, {@code ComposerControlsBar}, {@code ComposerPrimaryActions}).
 *
 * <p>feat/t3-composer: one dark rounded box ({@link ComposerSkin}), at most {@link #setMaxWidth}
 * wide and centred in its bounds:
 * <ul>
 *   <li>on top, the images waiting to go (thumbnails; hovering one shows its ✕);</li>
 *   <li>the text: grows from {@value #MIN_LINES} to {@value #MAX_LINES} lines, then scrolls;
 *   placeholder as T3; Enter sends, Shift+Enter is a newline; caret, mouse and Shift+arrow
 *   selection, double click selects a word, Ctrl+A/C/X/V, Ctrl+←/→, Home/End, Ctrl+Z / Ctrl+Y
 *   ({@link ComposerText});</li>
 *   <li>the bar: the clip on the left, then the left controls (a draft's project and workspace
 *   chips, {@link #setLeftControls}); on the right the controls ({@link #setControls}: model ·
 *   effort chip, access / plan chips) and the round primary button: ↑ send (disabled while there is
 *   nothing to send), ■ stop while a turn runs and the input is empty, dots while in flight.
 *   While a turn runs a message <i>steers</i> it (Ctrl+Enter / Ctrl+click queues it instead).</li>
 * </ul>
 * Errors (send, images) show as one discreet line just above the box (click it to dismiss).
 * Without control (operate) the bar says so and carries "Enable control" in place of the controls.
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
	public static final int MIN_LINES = 2;
	public static final int MAX_LINES = 8;
	private static final int LINE_H = 10;
	private static final int PAD_X = 9;
	private static final int PAD_TOP = 8;
	private static final int PAD_BOTTOM = 5;
	private static final int BAR_H = 16;
	private static final int BUTTON = 16;
	private static final int THUMB = 30;
	private static final int STRIP_H = THUMB + 6;
	private static final int NOTICE_H = 12;
	private static final long DOUBLE_CLICK_MS = 300;
	private static final AtomicInteger IMAGE_IDS = new AtomicInteger();

	/** What the composer asks of its host. */
	public interface Host {
		/** "Enable control": the existing {@code OperatePairScreen} for {@code envId}. */
		void enableControl(String envId);

		/** A message just went out (the host scrolls the conversation to the newest line). */
		default void sent() {
		}

		/** Esc in an empty draft thread's composer: the host discards the draft. True when it did. */
		default boolean escapeDraft() {
			return false;
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
	 * A control in the bar (the model chip, a draft's project / workspace chips):
	 * {@link ComposerSkin#CHIP_H} tall, drawn at the {@code y} given.
	 */
	public interface Control {
		int width(Font font);

		void render(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY);

		/** Called with clicks inside its rectangle. */
		boolean mouseClicked(double mx, double my, int button);

		/** False to leave it out of the bar for now (an access chip at the default access). */
		default boolean visible() {
			return true;
		}
	}

	private final ComposerState state;
	private final Host host;
	private final TextInput textInput;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private Rect box = Rect.EMPTY;
	private int maxWidth = Integer.MAX_VALUE;
	private boolean focused;
	private boolean dragging;
	private int scrollLine;
	private List<Control> controls = List.of();
	private List<Control> leftControls = List.of();
	/** Drawn controls and where: [control index into all, rect]; rebuilt each frame. */
	private final List<Control> drawnControls = new ArrayList<>();
	private final List<Rect> controlRects = new ArrayList<>();
	private Rect text = Rect.EMPTY;
	private Rect primary = Rect.EMPTY;
	private Rect clip = Rect.EMPTY;
	private Rect notice = Rect.EMPTY;
	private final ImageThumbs thumbs = new ImageThumbs();
	/** Per image on screen: [thumbnail, ✕] rectangles, same order as {@link ComposerState#images()}. */
	private final List<Rect[]> imageRects = new ArrayList<>();
	/** Path field mode (no native file dialog): the input holds a path; the draft is kept here. */
	private boolean pathMode;
	private @Nullable String pathModeDraft;
	private long accessCheckedAt;
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

	/** Every color the composer draws text with (input, placeholder, notices, buttons), as drawn. */
	public static List<Integer> textColors(ChatTheme t) {
		return List.of(ComposerSkin.TEXT, ComposerSkin.PLACEHOLDER, ComposerSkin.MUTED, ComposerSkin.ERROR, ComposerSkin.WARN,
				ComposerSkin.DISABLED_FG);
	}

	public ComposerState state() {
		return state;
	}

	public void setTheme(ChatTheme t) {
		theme = t;
	}

	/** Controls on the right of the bar, before the primary button (model · effort, access, plan). */
	public void setControls(List<Control> c) {
		controls = List.copyOf(c);
	}

	/** Controls on the left of the bar, after the clip (a draft's project and workspace chips). */
	public void setLeftControls(List<Control> c) {
		leftControls = List.copyOf(c);
	}

	/** The box is at most this wide, centred in the bounds (T3's transcript / composer column). */
	public void setMaxWidth(int w) {
		maxWidth = Math.max(80, w);
	}

	public void setThread(String envId, String threadId, @Nullable String title) {
		String before = state.key();
		if (pathMode && !(envId + ":" + threadId).equals(before)) {
			leavePathMode(false);
		}
		state.setThread(envId, threadId, title);
		afterSelect(before);
	}

	/** A draft thread (see {@link ComposerState#setDraft}); every frame is fine. */
	public void setDraft(String envId, String draftId, @Nullable String title, ComposerState.Launcher launcher) {
		String before = state.key();
		if (pathMode && !(envId + ":" + draftId).equals(before)) {
			leavePathMode(false);
		}
		state.setDraft(envId, draftId, title, launcher);
		afterSelect(before);
	}

	private void afterSelect(@Nullable String before) {
		if (!java.util.Objects.equals(state.key(), before)) {
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
			state.edited();
		}
	}

	private boolean editable() {
		return state.key() != null && state.canOperate();
	}

	public Rect bounds() {
		return bounds;
	}

	/** The dark box as last drawn (bounds minus the notice line, centred at the max width). */
	public Rect box() {
		return box;
	}

	public void setBounds(Rect r) {
		bounds = r;
	}

	public boolean contains(double mx, double my) {
		return box.contains(mx, my) || notice.contains(mx, my);
	}

	// ---- layout -------------------------------------------------------------------------------

	private int visibleLines() {
		return Math.clamp(state.editor().lines().size(), MIN_LINES, MAX_LINES);
	}

	private int textLines() {
		return Math.min(state.editor().lines().size(), MAX_LINES);
	}

	@Override
	public int height(int width) {
		return noticeH() + boxH();
	}

	private int boxH() {
		return PAD_TOP + strip() + visibleLines() * LINE_H + 4 + BAR_H + PAD_BOTTOM;
	}

	private int noticeH() {
		return noticeText() == null ? 0 : NOTICE_H;
	}

	private int strip() {
		return state.images().isEmpty() ? 0 : STRIP_H;
	}

	private void keepCaretVisible() {
		ComposerText e = state.editor();
		int line = e.lineOf(e.caret());
		int vis = textLines();
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
		Rect r = bounds;
		if (r.w() < 60 || r.h() < 30) {
			box = Rect.EMPTY;
			return;
		}
		if (state.key() != null && System.currentTimeMillis() - accessCheckedAt > 1000) {
			accessCheckedAt = System.currentTimeMillis();
			state.refreshAccess();
			if (!state.canOperate()) {
				setFocused(false);
			}
		}
		int w = Math.min(r.w(), maxWidth);
		int bx = r.x() + (r.w() - w) / 2;
		int nh = noticeH();
		box = new Rect(bx, r.bottom() - boxH(), w, boxH());
		boolean hoverBox = box.contains(mouseX, mouseY);
		ComposerSkin.box(g, box.x(), box.y(), box.w(), box.h(), ComposerSkin.BOX,
				focused ? ComposerSkin.BORDER_FOCUS : hoverBox && editable() ? 0xFF3A3A42 : ComposerSkin.BORDER);

		// ---- notice line above the box (errors of sending or of images)
		notice = Rect.EMPTY;
		String nt = noticeText();
		if (nt != null && nh > 0) {
			int ny = box.y() - NOTICE_H + 1;
			String s = ChatDraw.clip(font, nt, w - 8);
			notice = new Rect(bx + 4, ny, font.width(s) + 2, 9);
			boolean isError = state.error() != null;
			g.text(font, s, bx + 4, ny, isError ? ComposerSkin.ERROR : ComposerSkin.MUTED, false);
			if (isError && notice.contains(mouseX, mouseY)) {
				g.setTooltipForNextFrame(font, List.of(Component.literal(nt).getVisualOrderText(),
						Component.literal("Click to dismiss").withColor(0xFFA0A0A0).getVisualOrderText()), mouseX, mouseY);
			}
		}

		ComposerText e = state.editor();
		thumbs.retain(state.liveImageIds());
		int inX = box.x() + PAD_X;
		int inW = box.w() - 2 * PAD_X;
		drawImages(g, font, inX, box.y() + PAD_TOP - 2, inX + inW, mouseX, mouseY);
		text = new Rect(inX, box.y() + PAD_TOP + strip(), inW - 4, visibleLines() * LINE_H);
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
			g.text(font, ChatDraw.clip(font, placeholder(), text.w()), text.x(), text.y() + 1, ComposerSkin.PLACEHOLDER, false);
			if (focused && ChatDraw.caretOn()) {
				g.fill(text.x(), text.y(), text.x() + 1, text.y() + 9, ComposerSkin.TEXT);
			}
		} else {
			drawText(g, font, e);
		}
		int total = e.lines().size();
		int vis = textLines();
		if (total > vis) {
			drawScrollbar(g, box.right() - 5, text.y(), vis * LINE_H, total, vis);
		}

		// ---- bar: [clip] [left controls] ............ [controls] (primary)
		int barY = box.bottom() - PAD_BOTTOM - BAR_H;
		int chipY = barY + (BAR_H - ComposerSkin.CHIP_H) / 2;
		int left = box.x() + PAD_X - 4;
		int right = box.right() - PAD_X + 3;
		drawnControls.clear();
		controlRects.clear();
		clip = Rect.EMPTY;
		primary = drawPrimary(g, font, right, barY, mouseX, mouseY);
		right = primary.x() - 4;
		if (state.key() != null && !state.canOperate()) {
			// read-only: the bar says so; "Enable control" is the primary chip
			g.text(font, ChatDraw.clip(font, "Read-only", right - left - 4), left + 4, chipY + 3, ComposerSkin.MUTED, false);
			return;
		}
		if (editable()) {
			clip = new Rect(left, chipY, 15, ComposerSkin.CHIP_H);
			boolean hover = clip.contains(mouseX, mouseY);
			if (hover || pathMode) {
				ComposerSkin.round(g, clip.x(), clip.y(), clip.w(), clip.h(), pathMode ? ComposerSkin.OPEN : ComposerSkin.HOVER);
			}
			ComposerSkin.paperclip(g, clip.x() + 5, clip.y() + 2, hover || pathMode ? ComposerSkin.TEXT : ComposerSkin.MUTED);
			if (hover) {
				g.setTooltipForNextFrame(font, Component.literal(pathMode ? "Cancel the path field" : "Attach images · or paste one (Ctrl+V)"), mouseX,
						mouseY);
			}
			left += clip.w() + 3;
		}
		// right controls, right to left
		for (int i = controls.size() - 1; i >= 0; i--) {
			Control c = controls.get(i);
			if (!c.visible()) {
				continue;
			}
			int cw = c.width(font);
			if (right - cw < left + 20) {
				break;
			}
			right -= cw;
			c.render(g, font, right, chipY, mouseX, mouseY);
			drawnControls.add(c);
			controlRects.add(new Rect(right, chipY, cw, ComposerSkin.CHIP_H));
			right -= 2;
		}
		// left controls, left to right, as long as they fit
		for (Control c : leftControls) {
			if (!c.visible()) {
				continue;
			}
			int cw = c.width(font);
			if (left + cw > right - 4) {
				break;
			}
			c.render(g, font, left, chipY, mouseX, mouseY);
			drawnControls.add(c);
			controlRects.add(new Rect(left, chipY, cw, ComposerSkin.CHIP_H));
			left += cw + 2;
		}
	}

	private String placeholder() {
		if (state.key() == null) {
			return I18n.get("t3craft.chat.compose.no_thread");
		}
		if (!state.canOperate()) {
			return I18n.get("t3craft.chat.compose.read_only");
		}
		if (pathMode) {
			return "Path of an image (PNG, JPEG, GIF, WebP) · Enter attaches · Esc cancels";
		}
		return I18n.get("t3craft.chat.compose.placeholder");
	}

	private @Nullable String noticeText() {
		String err = state.error();
		if (err != null) {
			return "⚠ " + err;
		}
		String st = state.status();
		return st == null ? null : "⏱ " + st;
	}

	private static void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int h, int total, int vis) {
		g.fill(x, y, x + 2, y + h, 0x30FFFFFF);
		int th = Math.max(6, h * vis / Math.max(1, total));
		g.fill(x, y, x + 2, y + th, 0x90FFFFFF); // position follows the caret; a hint, not a control
	}

	/** The thumbnails row (T3's {@code ComposerAttachmentStrip}): hovering one shows its ✕. */
	private void drawImages(GuiGraphicsExtractor g, Font font, int x, int y, int right, int mouseX, int mouseY) {
		imageRects.clear();
		List<ImageAttachment> imgs = state.images();
		int ix = x;
		for (ImageAttachment img : imgs) {
			if (ix + THUMB > right) {
				int more = imgs.size() - imageRects.size();
				g.text(font, "+" + more, ix, y + THUMB / 2 - 4, ComposerSkin.MUTED, false);
				break;
			}
			Rect tb = new Rect(ix, y, THUMB, THUMB);
			Rect x0 = new Rect(tb.right() - 10, tb.y() + 2, 8, 8);
			imageRects.add(new Rect[] {tb, x0});
			boolean over = tb.contains(mouseX, mouseY);
			ComposerSkin.box(g, tb.x(), tb.y(), tb.w(), tb.h(), 0xFF101013, over ? ComposerSkin.BORDER_FOCUS : ComposerSkin.BORDER);
			ImageThumbs.Thumb th = thumbs.get(img);
			if (th != null) {
				double sc = Math.min((double) (THUMB - 4) / th.w(), (double) (THUMB - 4) / th.h());
				int dw = Math.max(1, (int) (th.w() * sc));
				int dh = Math.max(1, (int) (th.h() * sc));
				int dx = tb.x() + (THUMB - dw) / 2;
				int dy = tb.y() + (THUMB - dh) / 2;
				g.blit(th.id(), dx, dy, dx + dw, dy + dh, 0f, 1f, 0f, 1f);
			} else {
				String tag = ImageAttachment.extension(img.mime()).toUpperCase(java.util.Locale.ROOT);
				g.text(font, tag, tb.x() + (THUMB - font.width(tag)) / 2, tb.y() + THUMB / 2 - 4, ComposerSkin.MUTED, false);
			}
			if (over) {
				boolean overX = x0.contains(mouseX, mouseY);
				ComposerSkin.circle(g, x0.x(), x0.y(), 8, overX ? ComposerSkin.STOP : 0xE0303036);
				g.text(font, "✕", x0.x() + 1, x0.y(), 0xFFFFFFFF, false);
				g.setTooltipForNextFrame(font, Component.literal(overX ? "Remove" : img.name() + " · " + ImageAttachment.mb(img.size())), mouseX, mouseY);
			}
			ix += THUMB + 5;
		}
	}

	private void drawText(GuiGraphicsExtractor g, Font font, ComposerText e) {
		String s = e.text();
		int selA = e.selectionStart();
		int selB = e.selectionEnd();
		int caretLine = e.lineOf(e.caret());
		List<ComposerText.Line> lines = e.lines();
		int vis = textLines();
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
				g.fill(x0, y, x1, y + LINE_H - 1, ComposerSkin.SELECTION);
			}
			g.text(font, row, text.x(), y + 1, ComposerSkin.TEXT, false);
			if (focused && i == caretLine && ChatDraw.caretOn()) {
				int cx = text.x() + e.caretX();
				g.fill(cx, y, cx + 1, y + 9, ComposerSkin.TEXT);
			}
		}
	}

	private Rect drawPrimary(GuiGraphicsExtractor g, Font font, int right, int y, int mouseX, int mouseY) {
		ComposerState.Button b = state.button();
		if (b == ComposerState.Button.NONE) {
			return new Rect(right, y, 0, BUTTON);
		}
		if (b == ComposerState.Button.ENABLE_CONTROL) {
			String label = I18n.get("t3craft.chat.reply.enable_button");
			int w = ComposerSkin.chipWidth(font, "", label, "");
			Rect r = new Rect(right - w, y + (BUTTON - ComposerSkin.CHIP_H) / 2, w, ComposerSkin.CHIP_H);
			boolean hover = r.contains(mouseX, mouseY);
			ComposerSkin.round(g, r.x(), r.y(), r.w(), r.h(), hover ? ComposerSkin.SEND_HOVER : ComposerSkin.SEND);
			ComposerSkin.chip(g, font, r.x(), r.y(), "", 0xFFFFFFFF, label, 0xFFFFFFFF, "", false, false);
			if (hover) {
				g.setTooltipForNextFrame(font, Component.translatable("t3craft.operate.enable.tooltip"), mouseX, mouseY);
			}
			return r;
		}
		boolean stop = b == ComposerState.Button.STOP || b == ComposerState.Button.STOPPING;
		boolean busy = b == ComposerState.Button.SENDING || b == ComposerState.Button.STOPPING;
		boolean enabled = b == ComposerState.Button.SEND || b == ComposerState.Button.STOP;
		Rect r = new Rect(right - BUTTON, y, BUTTON, BUTTON);
		boolean hover = enabled && r.contains(mouseX, mouseY);
		int fill = stop ? (hover ? ComposerSkin.STOP_HOVER : ComposerSkin.STOP)
				: !enabled && !busy ? ComposerSkin.DISABLED_BG : hover ? ComposerSkin.SEND_HOVER : ComposerSkin.SEND;
		ComposerSkin.circle(g, r.x(), r.y(), BUTTON, fill);
		int fg = enabled || busy ? 0xFFFFFFFF : ComposerSkin.DISABLED_FG;
		if (busy) {
			ComposerSkin.busyDots(g, r.x(), r.y(), BUTTON, 0xFFFFFFFF, 0x80FFFFFF);
		} else if (stop) {
			ComposerSkin.stopSquare(g, r.x(), r.y(), BUTTON, fg);
		} else {
			ComposerSkin.arrowUp(g, r.x(), r.y(), BUTTON, fg);
		}
		if (r.contains(mouseX, mouseY)) {
			String tip = switch (b) {
				case STOP -> "Interrupt";
				case SENDING -> state.isDraft() ? "Starting the thread…" : "Submitting message";
				case STOPPING -> "Interrupting…";
				case SEND_DISABLED -> state.isDraft() ? "Write the first message" : "Nothing to send";
				default -> state.isDraft() ? "Start the thread · Enter"
						: state.unsent() > 0 ? "Queue message · Enter (goes out after the one being sent)"
							: state.running() ? "Steer message · Enter · Ctrl+Enter or Ctrl+click to queue" : "Submit message · Enter";
			};
			g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
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
		if (notice.contains(mx, my) && state.error() != null) {
			state.clearError();
			return true;
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
				state.primary(hasControl());
				if (send) {
					host.sent();
				}
			}
			return true;
		}
		for (int i = 0; i < controlRects.size() && i < drawnControls.size(); i++) {
			if (controlRects.get(i).contains(mx, my)) {
				drawnControls.get(i).mouseClicked(mx, my, button);
				return true;
			}
		}
		setFocused(true);
		if (focused) {
			int pos = positionAt(mx, my);
			long now = System.currentTimeMillis();
			if (pos == lastClickPos && now - lastClickAt < DOUBLE_CLICK_MS && !hasShift()) {
				state.editor().selectWordAt(pos);
				lastClickAt = 0;
				dragging = false;
				return true;
			}
			lastClickAt = now;
			lastClickPos = pos;
			state.editor().setCaret(pos, hasShift());
			dragging = true;
		}
		return true;
	}

	private static boolean hasShift() {
		return Minecraft.getInstance().hasShiftDown();
	}

	private static boolean hasControl() {
		return Minecraft.getInstance().hasControlDown();
	}

	private int positionAt(double mx, double my) {
		ComposerText e = state.editor();
		int line = scrollLine + (int) Math.floor((my - text.y()) / LINE_H);
		if (line < 0) {
			return 0;
		}
		if (line >= e.lines().size()) {
			return e.text().length();
		}
		return e.positionAt(line, (int) (mx - text.x()));
	}

	/** Drag-selects while the button is held after a click in the input. */
	public boolean mouseDragged(double mx, double my) {
		if (!dragging || !focused) {
			return false;
		}
		state.editor().setCaret(positionAt(mx, my), true);
		keepCaretVisible();
		return true;
	}

	public boolean mouseReleased() {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!box.contains(mx, my)) {
			return false;
		}
		int max = Math.max(0, state.editor().lines().size() - textLines());
		scrollLine = Math.clamp(scrollLine + (dy > 0 ? -1 : 1), 0, max);
		return true;
	}

	/**
	 * While focused every key is the composer's (no shortcut fires) except function keys; Esc
	 * leaves the input (in an empty draft thread it discards the draft). Enter sends (Ctrl+Enter
	 * queues while a turn runs), Shift+Enter is a newline.
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
			if (state.isDraft() && state.editor().isEmpty() && state.images().isEmpty() && host.escapeDraft()) {
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
			if (state.submit(ctrl)) {
				host.sent();
			}
			return true;
		}
		if (r == ComposerText.KeyResult.HANDLED) {
			if (!before.equals(state.editor().text())) {
				state.clearError();
				state.edited();
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
		// fix/paste-crash: a bad clipboard image (or a decoder/texture failure) must never take the game down
		List<ImageSources.Picked> imgs;
		try {
			imgs = ImageSources.clipboardImages();
		} catch (Throwable t) {
			dev.t3craft.T3Craft.LOGGER.warn("[t3craft] reading the clipboard failed", t);
			return false;
		}
		if (imgs.isEmpty()) {
			return false;
		}
		try {
			for (ImageSources.Picked p : imgs) {
				if (!addImage(p.bytes(), p.name())) {
					break;
				}
			}
		} catch (Throwable t) {
			dev.t3craft.T3Craft.LOGGER.warn("[t3craft] pasting an image failed", t);
			state.setError("Could not paste that image: " + t.getClass().getSimpleName());
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
			state.edited();
			keepCaretVisible();
		}
		return true;
	}
}
