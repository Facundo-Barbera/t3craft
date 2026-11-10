package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The part of showing {@link T3Panel}s that every screen shares: the stack of pages (Settings, then
 * Environments, Esc goes back), the paper frame with its header (title and a {@link T3Icon#CHEVRON_LEFT} back /
 * {@link T3Icon#X} close button), the
 * confirmation modal over the page and the input routing around them. {@code T3Screen} owns one for its
 * settings and environments pages, {@link T3PanelScreen} owns one as a screen of its own.
 *
 * <p>The owner forwards its draw and input calls here while {@link #active()}, and skips its own
 * widgets for clicks and keys while {@link #modalOpen()} (the page's text boxes stay under the veil). The
 * owner forwards {@link #charTyped}, {@link #mouseDragged} and {@link #mouseReleased} too: typing into a
 * field of the modal and dragging a scrollbar thumb need them.
 *
 * <p>A modal ({@link #modal}) is laid out once when it opens and again when the screen is laid out (a
 * resize), not every frame, so it may hold text fields: the dialog draws and routes their boxes itself
 * (they are not widgets of the screen).
 *
 * <p>Tab walks the page's controls and then the header button.
 */
public final class T3PanelHost implements T3Panel.Host {
	/** What the host needs from the screen that owns it. */
	public interface Owner {
		/** The page, its data or the stack changed: {@code rebuildWidgets()} (safe before the screen is initialised). */
		void relayout();

		Screen screen();

		/** The last page went back: there is nothing left to show. */
		void left();
	}

	private static final int HEADER_H = 18;

	private final Owner owner;
	private final Deque<T3Panel> stack = new ArrayDeque<>();
	private final T3Form chrome = new T3Form();
	private @Nullable T3Panel current;
	private @Nullable T3Dialog modal;
	private boolean modalPlaced;
	private boolean modalFresh;
	private @Nullable Font font;
	private ChatTheme theme = ChatTheme.warmStudio();
	private Rect frame = Rect.EMPTY;
	private int pad = 8;

	public T3PanelHost(Owner owner) {
		this.owner = owner;
	}

	public boolean active() {
		return current != null;
	}

	public boolean modalOpen() {
		return modal != null;
	}

	/** True while the current page has a popup open (a select's list): it takes the pointer even outside the page's frame. */
	public boolean overlayOpen() {
		return current != null && current.overlayOpen();
	}

	/** Starts the stack with {@code root}. */
	public void open(T3Panel root) {
		stack.clear();
		modal = null;
		current = root;
		root.attach(this);
		owner.relayout();
	}

	// ---- T3Panel.Host --------------------------------------------------------------------------------------

	/** Drops every page at once (the screen moved on to something else); the owner rebuilds its widgets. */
	public void close() {
		modal = null;
		if (current != null) {
			current.closed();
		}
		stack.forEach(T3Panel::closed);
		stack.clear();
		current = null;
	}

	@Override
	public void relayout() {
		owner.relayout();
	}

	@Override
	public void show(T3Panel next) {
		if (current != null) {
			stack.push(current);
		}
		current = next;
		next.attach(this);
		owner.relayout();
	}

	@Override
	public void back() {
		modal = null;
		if (current != null) {
			current.closed();
		}
		if (stack.isEmpty()) {
			current = null;
			owner.left();
		} else {
			current = stack.pop();
			owner.relayout();
		}
	}

	@Override
	public void openScreen(Screen next) {
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(next);
	}

	@Override
	public void modal(@Nullable T3Dialog dialog) {
		this.modal = dialog;
		this.modalPlaced = false;
		this.modalFresh = dialog != null;
		if (dialog != null) {
			dialog.setScreen(null); // its boxes are the dialog's own, not the screen's
		}
	}

	@Override
	public Screen screen() {
		return owner.screen();
	}

	// ---- layout and drawing -------------------------------------------------------------------------------------

	/** Lays the frame out in {@code area}; the page gets what is left under the header. Call from {@code init()}. */
	public void layout(Rect area, Font font, ChatTheme t, Consumer<AbstractWidget> sink) {
		frame = area;
		pad = t.pad();
		this.font = font;
		this.theme = t;
		modalPlaced = false; // the modal is laid out again at the next draw (a resize, F3+T)
		chrome.clear();
		T3Panel p = current;
		if (p == null) {
			return;
		}
		// the widths of the old "X" / "< Back" text buttons, so the hit box does not move
		boolean root = stack.isEmpty();
		T3Button back = root ? new T3Button(T3Icon.X, null, T3Button.Style.GHOST, this::back)
				: new T3Button(T3Icon.CHEVRON_LEFT, "Back", T3Button.Style.GHOST, this::back);
		int bw = Math.max(18, font.width(root ? "X" : "< Back") + 12);
		back.setBounds(new Rect(area.right() - pad - bw, area.y() + pad - 2, bw, T3Control.H));
		back.setTooltip(stack.isEmpty() ? "Close (Esc)" : "Back (Esc)");
		chrome.add(back);
		Rect inner = new Rect(area.x() + pad, area.y() + pad + HEADER_H + 2, area.w() - 2 * pad, area.h() - 2 * pad - HEADER_H - 2);
		p.layout(inner, font, t, sink);
	}

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		T3Panel p = current;
		if (p == null) {
			return;
		}
		boolean covered = modal != null;
		int hx = covered ? -1 : mx;
		int hy = covered ? -1 : my;
		ChatDraw.panel(g, t, frame);
		T3Draw.text(g, font, t, ChatDraw.clip(font, p.title(), frame.w() - 2 * pad - 60), frame.x() + pad, frame.y() + pad + 2, t.accent());
		g.fill(frame.x() + pad, frame.y() + pad + HEADER_H - 2, frame.right() - pad, frame.y() + pad + HEADER_H - 1, t.edge());
		chrome.render(g, font, t, hx, hy);
		p.render(g, font, t, hx, hy);
		// the vanilla widgets (text boxes) are drawn by the owner after this; the popups go through renderTop
	}

	/** After the owner drew its widgets: open selects, the tooltip, then the modal over everything. */
	public void renderTop(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		T3Panel p = current;
		if (p == null) {
			return;
		}
		if (modal == null) {
			p.renderOverlays(g, font, t, mx, my, screen);
			List<String> tip = p.overlayOpen() ? null : chrome.tooltip(mx, my);
			if (tip == null && !p.overlayOpen()) {
				tip = p.tooltip(mx, my);
			}
			if (tip != null) {
				T3Draw.tooltip(g, font, tip, mx, my);
			}
			return;
		}
		g.nextStratum();
		T3Dialog.veil(g, t, screen.w(), screen.h());
		if (!modalPlaced) {
			modal.layoutOwned(font, t, screen.w(), screen.h());
			modalPlaced = true;
			if (modalFresh) {
				modalFresh = false;
				modal.focusInitial(null);
			}
		}
		modal.render(g, font, t, mx, my);
		modal.renderOverlays(g, font, t, mx, my, screen);
	}

	public void tick() {
		if (current != null) {
			current.tick();
		}
	}

	// ---- input --------------------------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (modal != null) {
			modal.mouseClicked(mx, my, button);
			return true;
		}
		T3Panel p = current;
		if (p == null) {
			return false;
		}
		if (p.overlayOpen()) {
			return p.mouseClicked(mx, my, button);
		}
		T3Focus.dropNonInput(chrome.controls());
		return chrome.mouseClicked(mx, my, button) || p.mouseClicked(mx, my, button);
	}

	/** A held button moved: a scrollbar thumb of the page follows it. */
	public boolean mouseDragged(double mx, double my, int button) {
		if (modal != null) {
			return modal.mouseDragged(mx, my, button);
		}
		return current != null && current.mouseDragged(mx, my, button);
	}

	public boolean mouseReleased(double mx, double my, int button) {
		if (modal != null) {
			return modal.mouseReleased(mx, my, button);
		}
		return current != null && current.mouseReleased(mx, my, button);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (modal != null) {
			modal.mouseScrolled(mx, my, dy);
			return true; // the page under the veil does not scroll
		}
		return current != null && current.mouseScrolled(mx, my, dy);
	}

	/** Typed characters go to the modal's focused box; the page's boxes are the screen's own. */
	public boolean charTyped(CharacterEvent e) {
		return modal != null && modal.charTyped(e);
	}

	/** True when the key was the host's or the page's; Esc with nothing open goes back, Tab walks the controls. */
	public boolean keyPressed(KeyEvent e) {
		if (modal != null) {
			if (e.isEscape()) {
				modal.cancel();
			} else {
				modal.keyPressed(e);
			}
			return true;
		}
		T3Panel p = current;
		if (p == null) {
			return false;
		}
		if (e.isEscape()) {
			if (!p.escape()) {
				back();
			}
			return true;
		}
		if (T3Focus.isTab(e) && !p.overlayOpen()) {
			List<T3Control> order = new ArrayList<>(p.focusOrder());
			order.addAll(chrome.controls());
			if (T3Focus.move(order, e.hasShiftDown(), owner.screen())) {
				p.revealFocus();
				return true;
			}
			return false;
		}
		return chrome.keyPressed(e) || p.keyPressed(e);
	}
}
