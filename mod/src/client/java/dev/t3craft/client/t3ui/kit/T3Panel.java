package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
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
 * they always reflect the state). The body scrolls with the wheel or by dragging the scrollbar when it is
 * taller than the area; the footer ({@link #footerHeight}) does not.
 *
 * <p>Keyboard focus survives the re-arranging: the page remembers which control (by its place in the
 * reading order: header, body, footer) had it and gives it back to the fresh one. Tab brings a control that
 * is scrolled out of view into view.
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
		 * Shows a dialog over the page, or {@code null} to close it. The host lays it out once (and again on a
		 * resize), so it may hold fields, text areas and selects; it draws and routes their boxes itself.
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
	/** Stays under the body (the header, then the footer). */
	private final T3Form fixed = new T3Form();
	private final List<Label> labels = new ArrayList<>();
	private final List<Label> fixedLabels = new ArrayList<>();
	private final List<AbstractWidget> pending = new ArrayList<>();
	private final T3Scrollbar bar = new T3Scrollbar();
	private @Nullable Host host;
	private Font font;
	private ChatTheme theme = ChatTheme.warmStudio();
	private Rect viewport = Rect.EMPTY;
	private int scroll;
	private int contentH;
	/** How many of {@link #fixed}'s controls belong to the header (the rest are the footer's). */
	private int headerCount;

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
	 * A text field in the body. Where the page scrolls it is cut at the viewport's edge (a scissor), and
	 * hidden only when it is wholly outside, so it never jumps out of the page.
	 */
	protected final T3Field placeField(T3Field f, int x, int y, int w) {
		Rect r = new Rect(x, y, w, T3Control.H);
		pending.add(f.layoutClipped(theme, r, viewport));
		return form.add(f);
	}

	/**
	 * A multi-line text field in the body. Its box cannot be clipped, so it is hidden while it is not wholly
	 * inside the visible area: give it room.
	 */
	protected final T3TextArea placeTextArea(T3TextArea a, int x, int y, int w, int h) {
		Rect r = new Rect(x, y, w, h);
		boolean shown = r.y() >= viewport.y() && r.bottom() <= viewport.bottom();
		pending.add(a.layoutCulled(theme, r, shown));
		return form.add(a);
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
		// the controls are about to be made again: remember which one (not a text box, those keep their own focus) had the keyboard
		int focusOrd = -1;
		boolean focusRing = false;
		List<T3Control> before = focusOrder();
		for (int i = 0; i < before.size(); i++) {
			T3Control c = before.get(i);
			if (c.focused() && !c.takesInput()) {
				focusOrd = i;
				focusRing = c.focusRingVisible();
				break;
			}
		}
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
			if (!c.takesInput()) { // text boxes cull themselves (a field is clipped, not hidden)
				c.cull(!(c.bounds().bottom() > viewport.y() && c.bounds().y() < viewport.bottom()));
			}
		}
		List<T3Control> order = focusOrder();
		boolean inputFocused = false;
		for (T3Control c : order) {
			inputFocused |= c.takesInput() && c.focused();
		}
		if (!inputFocused && focusOrd >= 0 && focusOrd < order.size() && order.get(focusOrd).canFocus()) {
			order.get(focusOrd).focusIn(null, focusRing);
		}
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
		if (barShown()) {
			boolean hover = bar.dragging() || T3Scrollbar.over(mx, my, barX(), barY(), barH(), contentH, barH());
			ChatDraw.scrollbar(g, t, barX(), barY(), barH(), contentH, barH(), barFraction(), hover);
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

	// ---- scrollbar -------------------------------------------------------------------------------------------

	private boolean barShown() {
		return contentH > barH();
	}

	private int barX() {
		return viewport.right() - T3Scrollbar.W;
	}

	private int barY() {
		return viewport.y() + 2;
	}

	private int barH() {
		return viewport.h() - 4;
	}

	private int maxScroll() {
		return Math.max(0, contentH - barH());
	}

	private double barFraction() {
		return (double) scroll / Math.max(1, maxScroll());
	}

	private void scrollTo(int next) {
		next = Math.max(0, Math.min(maxScroll(), next));
		if (next != scroll) {
			scroll = next;
			host().relayout();
		}
	}

	// ---- keyboard focus ---------------------------------------------------------------------------------------

	/** The page's controls in reading order: header, body, footer. */
	final List<T3Control> focusOrder() {
		List<T3Control> all = new ArrayList<>();
		List<T3Control> f = fixed.controls();
		int h = Math.min(headerCount, f.size());
		all.addAll(f.subList(0, h));
		all.addAll(form.controls());
		all.addAll(f.subList(h, f.size()));
		return all;
	}

	/** Scrolls the body the least that shows the control that has the keyboard (Tab moved it out of view). */
	final void revealFocus() {
		for (T3Control c : form.controls()) {
			if (!c.focused()) {
				continue;
			}
			Rect b = c.bounds();
			int top = viewport.y() + 2;
			int bottom = viewport.bottom() - 2;
			int next = scroll;
			if (b.y() < top) {
				next -= top - b.y();
			} else if (b.bottom() > bottom) {
				next += b.bottom() - bottom;
			}
			scrollTo(next);
			return;
		}
	}

	// ---- input ----------------------------------------------------------------------------------------------------

	public final boolean mouseClicked(double mx, double my, int button) {
		if (fixed.overlayOpen()) {
			return fixed.mouseClicked(mx, my, button);
		}
		if (form.overlayOpen()) {
			return form.mouseClicked(mx, my, button);
		}
		T3Focus.dropNonInput(focusOrder());
		if (MouseButtons.isLeft(button) && barShown() && viewport.contains(mx, my)) {
			double f = bar.press(mx, my, barX(), barY(), barH(), contentH, barH(), barFraction());
			if (f >= 0) {
				scrollTo((int) Math.round(f * maxScroll()));
				return true;
			}
		}
		boolean used = fixed.mouseClicked(mx, my, button) || viewport.contains(mx, my) && form.mouseClicked(mx, my, button);
		if (used) {
			T3Focus.afterClick(focusOrder(), host().screen());
		}
		return used;
	}

	public final boolean mouseDragged(double mx, double my, int button) {
		if (bar.dragging()) {
			double f = bar.drag(my, barY(), barH(), contentH, barH());
			if (f >= 0) {
				scrollTo((int) Math.round(f * maxScroll()));
			}
			return true;
		}
		return fixed.mouseDragged(mx, my, button) || form.mouseDragged(mx, my, button);
	}

	public final boolean mouseReleased(double mx, double my, int button) {
		boolean was = bar.dragging();
		bar.release();
		boolean a = fixed.mouseReleased(mx, my, button);
		boolean b = form.mouseReleased(mx, my, button);
		return was || a || b;
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
		int max = maxScroll();
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy) * SCROLL_STEP));
		if (next == scroll) {
			return max > 0;
		}
		scroll = next;
		host().relayout();
		return true;
	}

	/** Open selects first, then the controls. Tab is the host's ({@link T3PanelHost}), which also owns the back button. */
	public final boolean keyPressed(KeyEvent e) {
		if (fixed.overlayOpen()) {
			return fixed.keyPressed(e);
		}
		if (form.overlayOpen()) {
			return form.keyPressed(e);
		}
		return fixed.keyPressed(e) || form.keyPressed(e);
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
