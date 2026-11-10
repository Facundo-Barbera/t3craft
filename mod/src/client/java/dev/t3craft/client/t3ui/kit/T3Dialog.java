package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

/**
 * A modal: a paper panel centred on the screen with a title and a vertical stack of blocks (wrapped text,
 * labelled fields, controls, a button row, a raw vanilla widget). Built with the fluent methods, laid out
 * with {@link #layout} (every {@code init()}) and drawn with {@link #render}. Two hosts use it:
 * {@link T3DialogScreen} (a screen of its own, over a parent) and {@link T3Panel.Host} implementations
 * ({@code T3Screen}, {@link T3PanelScreen}) for confirmations over a panel.
 *
 * <p>Rules for the stack: the position of a block depends only on the blocks above it, so a block whose
 * height changes while the dialog is open (a {@link #text(Supplier, Supplier, int) dynamic text} with
 * reserved lines) must be below every field. The panel grows downward to follow it; its top stays put.
 *
 * <p>A dialog is laid out once per {@code init()}, never per frame, so it may hold {@link T3Field}s and
 * {@link T3TextArea}s also when a host shows it over a page ({@link T3Panel.Host#modal}): the host lays it
 * out when it is opened and on every rebuild. Keep the fields in the page (or screen) that opens it so
 * they keep their text. Tab / Shift+Tab walk its controls; a focused button or switch takes Enter / Space.
 */
public final class T3Dialog {
	private static final int GAP = 4;
	private static final int TITLE_H = 18;

	private interface Block {
		int height(Font font, int w);

		/** Fixes the block at ({@code x}, {@code y}); controls go to {@code form}, vanilla widgets to {@code sink}. */
		void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form);

		void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my);
	}

	private final String title;
	private final int maxWidth;
	private final List<Block> blocks = new ArrayList<>();
	private final T3Form form = new T3Form();
	/** Who has the keyboard; a host that rebuilds the dialog (T3DialogScreen) shares one so it survives. */
	private T3Focus focus = new T3Focus();
	private @Nullable Runnable onCancel;
	private @Nullable Runnable defaultAction;
	private @Nullable T3Field initialFocus;

	private int[] ys = new int[0];
	private int left;
	private int top;
	private int width;
	private int pad;
	private int innerW;

	public T3Dialog(String title, int maxWidth) {
		this.title = title;
		this.maxWidth = maxWidth;
	}

	// ---- building ------------------------------------------------------------------------------------

	/** A wrapped paragraph. */
	public T3Dialog text(String s, Tone tone) {
		blocks.add(new TextBlock(() -> s, () -> tone, 0));
		return this;
	}

	/**
	 * A paragraph that changes while the dialog is open (a status line). It always takes {@code reserveLines}
	 * lines (longer text ends in an ellipsis), so the dialog does not jump.
	 */
	public T3Dialog text(Supplier<String> s, Supplier<Tone> tone, int reserveLines) {
		blocks.add(new TextBlock(s, tone, Math.max(1, reserveLines)));
		return this;
	}

	/** "Label: value" where the label is muted and the value wraps under its own column. */
	public T3Dialog kv(String label, String value) {
		blocks.add(new KvBlock(label, value));
		return this;
	}

	/** {@link #kv} from a "Label: %s" translation key, so the label stays translatable. */
	public T3Dialog kvKey(String langKey, String value) {
		String[] parts = T3Draw.template(langKey);
		return kv(parts[0].stripTrailing(), value + parts[1]);
	}

	public T3Dialog gap(int px) {
		blocks.add(new GapBlock(px));
		return this;
	}

	/** A control across the full width, one line tall. */
	public T3Dialog control(T3Control c) {
		blocks.add(new RowBlock(List.of(c)));
		return this;
	}

	/** Controls side by side with equal widths. */
	public T3Dialog buttons(T3Control... cs) {
		blocks.add(new RowBlock(List.of(cs)));
		return this;
	}

	/** A muted label over a text field. */
	public T3Dialog field(String label, T3Field f) {
		blocks.add(new FieldBlock(label, f));
		return this;
	}

	/** A muted label over a multi-line text box ({@link T3TextArea#height()} tall). */
	public T3Dialog area(String label, T3TextArea a) {
		blocks.add(new AreaBlock(label, a));
		return this;
	}

	/** A muted label over a control (a select). */
	public T3Dialog labelled(String label, T3Control c) {
		blocks.add(new LabelledBlock(label, c));
		return this;
	}

	/** A vanilla widget {@code h} tall, created by the factory at its rectangle (a multi-line box). */
	public T3Dialog widget(int h, Function<Rect, AbstractWidget> factory) {
		blocks.add(new WidgetBlock(h, factory));
		return this;
	}

	/** Esc and {@link #cancel()}. */
	public T3Dialog onCancel(Runnable r) {
		this.onCancel = r;
		return this;
	}

	/** What Enter does. */
	public T3Dialog defaultAction(Runnable r) {
		this.defaultAction = r;
		return this;
	}

	/** The field that has the keyboard when the dialog opens. */
	public T3Dialog initialFocus(T3Field f) {
		this.initialFocus = f;
		return this;
	}

	/**
	 * A yes/no question: {@code lines} as paragraphs, an optional {@code warning}, then the confirm button
	 * ({@code danger}: error fill, and Enter means "no") and Cancel. {@code onNo} also runs on Esc.
	 */
	public static T3Dialog confirm(String title, List<String> lines, @Nullable String warning, String yes, boolean danger, Runnable onYes,
			Runnable onNo) {
		T3Dialog d = new T3Dialog(title, 340);
		for (String l : lines) {
			d.text(l, Tone.NORMAL);
		}
		if (warning != null) {
			d.text(warning, Tone.ERROR);
		}
		d.gap(2);
		T3Button no = new T3Button(CommonComponents.GUI_CANCEL.getString(), onNo);
		T3Button ok = new T3Button(yes, danger ? T3Button.Style.DANGER : T3Button.Style.PRIMARY, onYes);
		no.setEmphasised(danger);
		d.buttons(ok, no);
		d.onCancel(onNo);
		d.defaultAction(danger ? onNo : onYes);
		return d;
	}

	// ---- layout and drawing ----------------------------------------------------------------------------

	public static void veil(GuiGraphicsExtractor g, ChatTheme t, int screenW, int screenH) {
		g.fill(0, 0, screenW, screenH, t.backdrop());
	}

	/** The screen that shows the dialog: Tab gives the keyboard to its text boxes through it. Call before {@link #layout}. */
	public void bind(Screen screen) {
		focus.bind(screen);
	}

	/** Uses {@code shared} to remember the focus, so a dialog built again by the same screen keeps it. */
	void shareFocus(T3Focus shared) {
		this.focus = shared;
	}

	/**
	 * Fixes every block. Call it once per {@code init()} (and again when the screen is rebuilt), not per
	 * frame: a {@link T3Field}'s box is created here and added through {@code sink}.
	 */
	public void layout(Font font, ChatTheme t, int screenW, int screenH, Consumer<AbstractWidget> sink) {
		form.clear();
		form.useFocus(focus, form::controls);
		pad = t.pad() + 2;
		width = Math.max(120, Math.min(maxWidth, screenW - 16));
		innerW = width - 2 * pad;
		int h = 2 * pad + TITLE_H - GAP;
		for (Block b : blocks) {
			h += b.height(font, innerW) + GAP;
		}
		left = (screenW - width) / 2;
		top = Math.max(4, (screenH - h) / 2 - 6);
		ys = new int[blocks.size()];
		int y = top + pad + TITLE_H;
		for (int i = 0; i < blocks.size(); i++) {
			Block b = blocks.get(i);
			ys[i] = y;
			b.place(font, t, left + pad, y, innerW, sink, form);
			y += b.height(font, innerW) + GAP;
		}
		focus.restore(form.controls());
	}

	/** The panel as last drawn, for hit-testing. */
	public Rect rect(Font font) {
		return new Rect(left, top, width, bottom(font) + pad - top);
	}

	private int bottom(Font font) {
		if (blocks.isEmpty() || ys.length != blocks.size()) {
			return top + pad + TITLE_H; // not laid out yet
		}
		int last = blocks.size() - 1;
		return ys[last] + blocks.get(last).height(font, innerW);
	}

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		ChatDraw.panel(g, t, new Rect(left, top, width, bottom(font) + pad - top));
		T3Draw.text(g, font, t, ChatDraw.clip(font, title, innerW), left + pad, top + pad, t.accent());
		g.fill(left + pad, top + pad + 11, left + width - pad, top + pad + 12, t.edge());
		for (Block b : blocks) {
			b.render(g, font, t, mx, my);
		}
	}

	public void renderOverlays(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		form.renderOverlays(g, font, t, mx, my, screen);
	}

	public @Nullable List<String> tooltip(double mx, double my) {
		return form.tooltip(mx, my);
	}

	/** Call after {@link #layout} and after the host added the widgets. */
	public void focusInitial(Screen host) {
		if (initialFocus != null && focus.current(form.controls()) == null) {
			initialFocus.focus(host);
		}
	}

	/**
	 * A click that no kit control took, on a text box of the dialog: gives it the keyboard and puts the
	 * caret where it was clicked. For a host (a modal over a page) that does not pass clicks to the
	 * screen's own widgets; a {@link T3DialogScreen} lets vanilla do it.
	 */
	public void clickBox(Screen host, double mx, double my, int button) {
		for (T3Control c : form.controls()) {
			if (c instanceof T3Field f && f.clickAt(host, mx, my, button)) {
				return;
			}
		}
	}

	// ---- input -------------------------------------------------------------------------------------------

	public boolean overlayOpen() {
		return form.overlayOpen();
	}

	public boolean mouseClicked(double mx, double my, int button) {
		return form.mouseClicked(mx, my, button);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		return form.mouseScrolled(mx, my, dy);
	}

	public boolean mouseDragged(double mx, double my, int button) {
		return form.mouseDragged(mx, my, button);
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return form.mouseReleased(mx, my, button);
	}

	/** A typed character goes to the control that has the keyboard (a text area). */
	public boolean charTyped(int codepoint) {
		return form.charTyped(codepoint);
	}

	/**
	 * Open popups first, then Tab / Shift+Tab (next / previous control), then the controls (a focused
	 * button or switch takes Enter and Space, a field its own Enter), then the dialog's default action.
	 * Esc is the host's ({@link #cancel()}).
	 */
	public boolean keyPressed(KeyEvent e) {
		if (!form.overlayOpen() && e.key() == InputConstants.KEY_TAB) {
			T3Control next = focus.step(form.controls(), e.hasShiftDown() ? -1 : 1);
			if (next != null) {
				focus.focus(next, form.controls());
				return true;
			}
		}
		if (form.keyPressed(e)) {
			return true;
		}
		if (defaultAction != null && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) {
			defaultAction.run();
			return true;
		}
		return false;
	}

	/** Drops the keyboard focus (the dialog closed). */
	public void release() {
		focus.clear();
	}

	public void cancel() {
		if (form.overlayOpen()) {
			form.closeOverlays();
		} else if (onCancel != null) {
			onCancel.run();
		}
	}

	// ---- blocks --------------------------------------------------------------------------------------------

	private static final class TextBlock implements Block {
		private final Supplier<String> text;
		private final Supplier<Tone> tone;
		private final int reserve;
		private int x;
		private int y;
		private int w;

		TextBlock(Supplier<String> text, Supplier<Tone> tone, int reserve) {
			this.text = text;
			this.tone = tone;
			this.reserve = reserve;
		}

		@Override
		public int height(Font font, int width) {
			int lines = reserve > 0 ? reserve : T3Draw.wrap(font, text.get(), width).size();
			return lines * T3Draw.lineHeight(font);
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			this.x = x;
			this.y = y;
			this.w = w;
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			T3Draw.wrapped(g, font, t, text.get(), x, y, w, T3Draw.color(t, tone.get()), reserve);
		}
	}

	private static final class KvBlock implements Block {
		private final String label;
		private final String value;
		private int x;
		private int y;
		private int w;

		KvBlock(String label, String value) {
			this.label = label;
			this.value = value;
		}

		@Override
		public int height(Font font, int width) {
			int lw = font.width(label) + 3;
			return Math.max(1, T3Draw.wrap(font, value, width - lw).size()) * T3Draw.lineHeight(font);
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			this.x = x;
			this.y = y;
			this.w = w;
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			int lw = font.width(label) + 3;
			T3Draw.text(g, font, t, label, x, y, t.muted());
			T3Draw.wrapped(g, font, t, value, x + lw, y, w - lw, t.text(), 0);
		}
	}

	private static final class GapBlock implements Block {
		private final int px;

		GapBlock(int px) {
			this.px = px;
		}

		@Override
		public int height(Font font, int w) {
			return px;
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		}
	}

	private static final class RowBlock implements Block {
		private final List<T3Control> controls;

		RowBlock(List<T3Control> controls) {
			this.controls = controls;
		}

		@Override
		public int height(Font font, int w) {
			return T3Control.H;
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			int n = Math.max(1, controls.size());
			int cw = (w - (n - 1) * GAP) / n;
			for (int i = 0; i < controls.size(); i++) {
				T3Control c = controls.get(i);
				c.setBounds(new Rect(x + i * (cw + GAP), y, cw, T3Control.H));
				form.add(c);
			}
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			controls.forEach(c -> c.render(g, font, t, mx, my));
		}
	}

	private static final class FieldBlock implements Block {
		private static final int LABEL_H = 11;
		private final String label;
		private final T3Field field;
		private int x;
		private int y;

		FieldBlock(String label, T3Field field) {
			this.label = label;
			this.field = field;
		}

		@Override
		public int height(Font font, int w) {
			return LABEL_H + T3Control.H;
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			this.x = x;
			this.y = y;
			sink.accept(field.layout(t, new Rect(x, y + LABEL_H, w, T3Control.H), true));
			form.add(field); // Tab reaches it; the box itself is the sink's
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			T3Draw.text(g, font, t, label, x, y, t.muted());
			field.render(g, font, t, mx, my);
		}
	}

	private static final class AreaBlock implements Block {
		private static final int LABEL_H = 11;
		private final String label;
		private final T3TextArea area;
		private int x;
		private int y;

		AreaBlock(String label, T3TextArea area) {
			this.label = label;
			this.area = area;
		}

		@Override
		public int height(Font font, int w) {
			return LABEL_H + area.height();
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			this.x = x;
			this.y = y;
			area.setBounds(new Rect(x, y + LABEL_H, w, area.height()));
			form.add(area);
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			T3Draw.text(g, font, t, label, x, y, t.muted());
			area.render(g, font, t, mx, my);
		}
	}

	private static final class LabelledBlock implements Block {
		private static final int LABEL_H = 11;
		private final String label;
		private final T3Control control;
		private int x;
		private int y;

		LabelledBlock(String label, T3Control control) {
			this.label = label;
			this.control = control;
		}

		@Override
		public int height(Font font, int w) {
			return LABEL_H + T3Control.H;
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			this.x = x;
			this.y = y;
			control.setBounds(new Rect(x, y + LABEL_H, w, T3Control.H));
			form.add(control);
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
			T3Draw.text(g, font, t, label, x, y, t.muted());
			control.render(g, font, t, mx, my);
		}
	}

	private static final class WidgetBlock implements Block {
		private final int h;
		private final Function<Rect, AbstractWidget> factory;

		WidgetBlock(int h, Function<Rect, AbstractWidget> factory) {
			this.h = h;
			this.factory = factory;
		}

		@Override
		public int height(Font font, int w) {
			return h;
		}

		@Override
		public void place(Font font, ChatTheme t, int x, int y, int w, Consumer<AbstractWidget> sink, T3Form form) {
			sink.accept(factory.apply(new Rect(x, y, w, h)));
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		}
	}
}
