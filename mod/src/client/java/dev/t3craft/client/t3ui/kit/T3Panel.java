package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A page of the UI kit: the settings, the environment list. It is not a screen: a {@link T3PanelHost} puts
 * it inside {@code T3Screen} (like T3 Code's settings page, in the place of the thread) or inside a
 * {@link T3PanelScreen} (Mod Menu, the J key, {@code /t3craft config}, the gallery).
 *
 * <p>A subclass keeps its state in fields and describes the page in {@link #arrange}: it places controls
 * with {@link #place}, {@link #placeField} and {@link #pin} and text with {@link #text}. The host calls
 * {@code layout} on every {@code init()}, and the page is arranged again (controls are created fresh, so
 * they always reflect the state). The body scrolls with the wheel when it is taller than the area; the
 * footer ({@link #footerHeight}) does not.
 */
public abstract class T3Panel {
	/** What a page may ask of whatever shows it. */
	public interface Host {
		/** Lay the page out again (a tab changed, the data changed). */
		void relayout();

		/** Shows {@code next} on top of this page; {@link #back()} returns. */
		void show(T3Panel next);

		/** Leaves this page: the previous one, or the host itself when there is none. */
		void back();

		/** Opens a screen over the host; closing it comes back here. */
		void openScreen(Screen next);

		/**
		 * Shows a modal over the page (a confirmation, a small form), or {@code null} to close it. The host
		 * lays it out once, when this is called and on every rebuild of the screen, so it may hold
		 * {@link T3Field}s and {@link T3TextArea}s: keep them in the page, as the page's own, and they keep
		 * their text. The page below stays visible under the veil but takes no input.
		 */
		void modal(@Nullable T3Dialog dialog);

		/** The screen showing the page (the parent of screens opened with {@link #openScreen}). */
		Screen screen();
	}

	private record Label(int x, int y, String text, Tone tone) {
	}

	private static final int SCROLL_STEP = 14;

	/** Scrolls with the body. */
	private final T3Form form = new T3Form();
	/** Stays under the body (the footer). */
	private final T3Form fixed = new T3Form();
	private final List<Label> labels = new ArrayList<>();
	private final List<Label> fixedLabels = new ArrayList<>();
	private final List<AbstractWidget> pending = new ArrayList<>();
	/** Who has the keyboard; Tab walks {@link #ring}: header, body, footer. */
	private final T3Focus focus = new T3Focus();
	private List<T3Control> ring = List.of();
	/** How many of {@code fixed}'s controls belong to the header (the rest are the footer's). */
	private int headerCount;
	private @Nullable Host host;
	private Font font;
	private ChatTheme theme = ChatTheme.warmStudio();
	private Rect viewport = Rect.EMPTY;
	private int scroll;
	private int contentH;

	public final void attach(Host host) {
		this.host = host;
	}

	protected final Host host() {
		return Objects.requireNonNull(host, "panel not attached");
	}

	protected final Font font() {
		return font;
	}

	protected final ChatTheme theme() {
		return theme;
	}

	/** Header text. */
	public abstract String title();

	/** Height of the pinned header over the scrolling body (tabs, a toolbar); 0 = none. */
	protected int headerHeight() {
		return 0;
	}

	/** Height of the pinned footer under the scrolling body; 0 = none. */
	protected int footerHeight() {
		return 0;
	}

	/** Places the header controls with {@link #pin} and {@link #pinText}. */
	protected void arrangeHeader(Rect header) {
	}

	/**
	 * Places the body's content. {@code body} is the visible area (padded); start at {@code y0}, which
	 * is the body's top minus the scroll. Returns the y under the last item.
	 */
	protected abstract int arrange(Rect body, int y0);

	/** Places the footer controls with {@link #pin} and {@link #pinText}. */
	protected void arrangeFooter(Rect footer) {
	}

	/** The page left the host (back, or the host closed). */
	public void closed() {
	}

	public void tick() {
	}

	// ---- placing ---------------------------------------------------------------------------------------

	protected final <C extends T3Control> C place(C c, int x, int y, int w, int h) {
		c.setBounds(new Rect(x, y, w, h));
		return form.add(c);
	}

	/**
	 * A text field in the body. The vanilla box cannot be clipped, so it is live only when wholly inside
	 * the visible area; while the field is partly out, the kit draws its frame and text itself, clipped
	 * with the page (so it slides out of view, no jump at the edge), and when it is wholly out it is hidden.
	 */
	protected final T3Field placeField(T3Field f, int x, int y, int w) {
		Rect r = new Rect(x, y, w, T3Control.H);
		boolean whole = r.y() >= viewport.y() && r.bottom() <= viewport.bottom();
		boolean inView = r.bottom() > viewport.y() && r.y() < viewport.bottom();
		pending.add(f.layout(theme, r, whole));
		f.setVisible(inView);
		return form.add(f);
	}

	/** A text field in the header or footer: always shown, never scrolled (a search box). */
	protected final T3Field pinField(T3Field f, int x, int y, int w) {
		pending.add(f.layout(theme, new Rect(x, y, w, T3Control.H), true));
		return fixed.add(f);
	}

	protected final <C extends T3Control> C pin(C c, int x, int y, int w, int h) {
		c.setBounds(new Rect(x, y, w, h));
		return fixed.add(c);
	}

	/** Wrapped text in the body; returns the y under it. */
	protected final int text(int x, int y, int w, String s, Tone tone) {
		for (String line : T3Draw.wrap(font, s, w)) {
			labels.add(new Label(x, y, line, tone));
			y += T3Draw.lineHeight(font);
		}
		return y;
	}

	/** One line in the footer. */
	protected final void pinText(int x, int y, String s, Tone tone) {
		fixedLabels.add(new Label(x, y, s, tone));
	}

	// ---- host calls ---------------------------------------------------------------------------------------

	public final void layout(Rect area, Font font, ChatTheme t, Consumer<AbstractWidget> sink) {
		this.font = font;
		this.theme = t;
		int hh = Math.max(0, Math.min(headerHeight(), area.h() / 3));
		int fh = Math.max(0, Math.min(footerHeight(), area.h() / 3));
		Rect header = new Rect(area.x(), area.y(), area.w(), hh);
		viewport = new Rect(area.x(), area.y() + hh, area.w(), area.h() - hh - fh);
		Rect footer = new Rect(area.x(), viewport.bottom(), area.w(), fh);
		Rect body = viewport.inset(2);
		for (int pass = 0; pass < 2; pass++) {
			form.clear();
			fixed.clear();
			labels.clear();
			fixedLabels.clear();
			pending.clear();
			int y0 = body.y() - scroll;
			int bottom = arrange(body, y0);
			contentH = bottom - y0;
			int max = Math.max(0, contentH - body.h());
			if (scroll <= max) {
				break;
			}
			scroll = max; // the window grew: arrange again from the new offset
		}
		arrangeHeader(header);
		headerCount = fixed.controls().size();
		arrangeFooter(footer);
		for (T3Control c : form.controls()) {
			if (!(c instanceof T3Field)) {
				c.setVisible(c.bounds().bottom() > viewport.y() && c.bounds().y() < viewport.bottom());
			}
		}
		List<T3Control> r = new ArrayList<>(fixed.controls().subList(0, headerCount));
		r.addAll(form.controls());
		r.addAll(fixed.controls().subList(headerCount, fixed.controls().size()));
		ring = r;
		form.useFocus(focus, () -> ring);
		fixed.useFocus(focus, () -> ring);
		focus.bind(host().screen());
		focus.restore(ring);
		pending.forEach(sink);
	}

	public final void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
		for (Label l : labels) {
			if (l.y() + font.lineHeight > viewport.y() && l.y() < viewport.bottom()) {
				T3Draw.text(g, font, t, l.text(), l.x(), l.y(), T3Draw.color(t, l.tone()));
			}
		}
		form.render(g, font, t, viewport.contains(mx, my) ? mx : -1, my);
		g.disableScissor();
		if (contentH > viewport.h() - 4) {
			double max = Math.max(1, contentH - (viewport.h() - 4));
			ChatDraw.scrollbar(g, t, viewport.right() - 6, viewport.y() + 2, viewport.h() - 4, contentH, viewport.h() - 4, scroll / max, false);
		}
		for (Label l : fixedLabels) {
			T3Draw.text(g, font, t, l.text(), l.x(), l.y(), T3Draw.color(t, l.tone()));
		}
		fixed.render(g, font, t, mx, my);
	}

	/** After the host drew the vanilla widgets: open selects. */
	public final void renderOverlays(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		form.renderOverlays(g, font, t, mx, my, screen);
		fixed.renderOverlays(g, font, t, mx, my, screen);
	}

	public final boolean overlayOpen() {
		return form.overlayOpen() || fixed.overlayOpen();
	}

	public final @Nullable List<String> tooltip(double mx, double my) {
		List<String> tip = fixed.tooltip(mx, my);
		return tip != null ? tip : viewport.contains(mx, my) ? form.tooltip(mx, my) : null;
	}

	public final boolean mouseClicked(double mx, double my, int button) {
		if (fixed.overlayOpen()) {
			return fixed.mouseClicked(mx, my, button);
		}
		if (form.overlayOpen()) {
			return form.mouseClicked(mx, my, button);
		}
		if (fixed.mouseClicked(mx, my, button)) {
			return true;
		}
		return viewport.contains(mx, my) && form.mouseClicked(mx, my, button);
	}

	public final boolean mouseScrolled(double mx, double my, double dy) {
		if (overlayOpen()) {
			return fixed.mouseScrolled(mx, my, dy) || form.mouseScrolled(mx, my, dy);
		}
		if (!viewport.contains(mx, my)) {
			return false;
		}
		if (form.mouseScrolled(mx, my, dy)) {
			return true; // a list inside the page
		}
		int max = Math.max(0, contentH - (viewport.h() - 4));
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy) * SCROLL_STEP));
		if (next == scroll) {
			return max > 0;
		}
		scroll = next;
		host().relayout();
		return true;
	}

	/**
	 * Open selects first, then Tab / Shift+Tab (the next / previous control of the page, header to footer,
	 * wrapping), then the controls: a button, switch, list or text area reacts only while it has the
	 * keyboard (a page may also pin an invisible {@link T3Control} to catch keys of its own).
	 */
	public final boolean keyPressed(KeyEvent e) {
		if (fixed.overlayOpen()) {
			return fixed.keyPressed(e);
		}
		if (form.overlayOpen()) {
			return form.keyPressed(e);
		}
		if (e.key() == InputConstants.KEY_TAB && moveFocus(e.hasShiftDown() ? -1 : 1)) {
			return true;
		}
		return fixed.keyPressed(e) || form.keyPressed(e);
	}

	/** Tab: the keyboard goes to the next usable control; a body control out of view is scrolled in. */
	private boolean moveFocus(int dir) {
		T3Control next = focus.step(ring, dir);
		if (next == null) {
			return false;
		}
		boolean scrolled = reveal(next);
		focus.focus(next, ring);
		if (scrolled) {
			host().relayout(); // the controls are made again; the focus comes back to the same slot
		}
		return true;
	}

	/** Scrolls the body the least that shows {@code c} (a pinned control never needs it); true when it moved. */
	private boolean reveal(T3Control c) {
		if (fixed.controls().contains(c)) {
			return false;
		}
		Rect b = c.bounds();
		int top = viewport.y() + 2;
		int bottom = viewport.bottom() - 2;
		int shift = 0;
		if (b.y() < top || b.h() > bottom - top && b.bottom() > bottom) {
			shift = b.y() - top; // above, or taller than the area: show its top
		} else if (b.bottom() > bottom) {
			shift = b.bottom() - bottom;
		}
		int max = Math.max(0, contentH - (viewport.h() - 4));
		int next = Math.max(0, Math.min(max, scroll + shift));
		if (shift == 0 || next == scroll) {
			return false;
		}
		scroll = next;
		return true;
	}

	/** A typed character goes to the control that has the keyboard (a text area; a field's box is vanilla). */
	public final boolean charTyped(int codepoint) {
		return fixed.charTyped(codepoint) || form.charTyped(codepoint);
	}

	public final boolean mouseDragged(double mx, double my, int button) {
		return fixed.mouseDragged(mx, my, button) || form.mouseDragged(mx, my, button);
	}

	public final boolean mouseReleased(double mx, double my, int button) {
		boolean a = fixed.mouseReleased(mx, my, button);
		boolean b = form.mouseReleased(mx, my, button);
		return a || b;
	}

	/** Nothing of the page keeps the keyboard (a text area stops the platform's text input). */
	final void releaseFocus() {
		focus.clear();
	}

	/** The page left its host. */
	final void dispose() {
		releaseFocus();
		closed();
	}

	/** Esc: closes an open select; false = nothing to close, the host goes back. */
	public final boolean escape() {
		if (overlayOpen()) {
			form.closeOverlays();
			fixed.closeOverlays();
			return true;
		}
		return false;
	}

	/** A yes/no question over the page; both answers close it, then {@code onYes} runs for yes. */
	protected final void confirm(String title, List<String> lines, @Nullable String warning, String yes, boolean danger, Runnable onYes) {
		host().modal(T3Dialog.confirm(title, lines, warning, yes, danger, () -> {
			host().modal(null);
			onYes.run();
		}, () -> host().modal(null)));
	}
}
