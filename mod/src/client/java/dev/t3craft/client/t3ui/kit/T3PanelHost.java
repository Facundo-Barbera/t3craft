package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The part of showing {@link T3Panel}s that every screen shares: the stack of pages (Settings →
 * Environments, Esc goes back), the paper frame with its header (title and a back / close button), the
 * confirmation modal over the page and the input routing around them. {@code T3Screen} owns one for its
 * settings and environments pages, {@link T3PanelScreen} owns one as a screen of its own.
 *
 * <p>The owner forwards its draw and input calls here while {@link #active()}, and skips its own
 * widgets for clicks and keys while {@link #modalOpen()} (the page's text boxes stay under the veil).
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
	/** The modal was laid out by {@link #layout} (once per rebuild of the screen); false = it still has to be. */
	private boolean modalLaidOut;
	/** Its initial field got the keyboard (only the first time, so a rebuild does not steal it back). */
	private boolean modalFocused;
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
		releaseModal();
		current = root;
		root.attach(this);
		owner.relayout();
	}

	private void releaseModal() {
		if (modal != null) {
			modal.release();
			modal = null;
		}
	}

	// ---- T3Panel.Host --------------------------------------------------------------------------------------

	/** Drops every page at once (the screen moved on to something else); the owner rebuilds its widgets. */
	public void close() {
		releaseModal();
		if (current != null) {
			current.dispose();
		}
		stack.forEach(T3Panel::dispose);
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
		releaseModal();
		if (current != null) {
			current.dispose();
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

	/**
	 * Opens a modal, or closes it with {@code null}. The screen is rebuilt so the modal is laid out once
	 * (its text boxes become widgets of the screen), not on every frame.
	 */
	@Override
	public void modal(@Nullable T3Dialog dialog) {
		if (dialog == modal) {
			return;
		}
		releaseModal();
		this.modal = dialog;
		modalLaidOut = false;
		modalFocused = false;
		owner.relayout();
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
		chrome.clear();
		T3Panel p = current;
		if (p == null) {
			return;
		}
		String label = stack.isEmpty() ? "✕" : "‹ Back";
		T3Button back = new T3Button(label, T3Button.Style.GHOST, this::back);
		int bw = Math.max(18, font.width(label) + 12);
		back.setBounds(new Rect(area.right() - pad - bw, area.y() + pad - 2, bw, T3Control.H));
		back.setTooltip(stack.isEmpty() ? "Close (Esc)" : "Back (Esc)");
		chrome.add(back);
		Rect inner = new Rect(area.x() + pad, area.y() + pad + HEADER_H + 2, area.w() - 2 * pad, area.h() - 2 * pad - HEADER_H - 2);
		if (modal == null) {
			p.layout(inner, font, t, sink);
			return;
		}
		// a modal is open: the page's text boxes stay under the veil but cannot be clicked, focused or typed in
		p.layout(inner, font, t, w -> {
			w.visible = false; // T3Field draws its text itself while its box is hidden
			w.setFocused(false);
			sink.accept(w);
		});
		Screen screen = owner.screen();
		modal.bind(screen);
		modal.layout(font, t, screen.width, screen.height, sink); // once: its boxes become widgets of the screen
		modalLaidOut = true;
		if (!modalFocused) {
			modalFocused = true;
			modal.focusInitial(screen);
		}
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
		if (!modalLaidOut) {
			// the screen never laid the page out (a layout with no room for it): place the modal without text boxes
			modal.layout(font, t, screen.w(), screen.h(), w -> {
			});
			modalLaidOut = true;
		}
		modal.render(g, font, t, mx, my);
		modal.renderOverlays(g, font, t, mx, my, screen);
	}

	public void tick() {
		if (current != null) {
			current.tick();
		}
	}

	/** The owning screen is going away: no control keeps the keyboard or the platform's text input. */
	public void releaseFocus() {
		if (modal != null) {
			modal.release();
		}
		if (current != null) {
			current.releaseFocus();
		}
		stack.forEach(T3Panel::releaseFocus);
	}

	// ---- input --------------------------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		if (modal != null) {
			if (!modal.mouseClicked(mx, my, button)) {
				modal.clickBox(owner.screen(), mx, my, button); // a text box of the modal takes the click
			}
			return true; // the page and the screen under the veil never see it
		}
		T3Panel p = current;
		if (p == null) {
			return false;
		}
		if (p.overlayOpen()) {
			return p.mouseClicked(mx, my, button);
		}
		return chrome.mouseClicked(mx, my, button) || p.mouseClicked(mx, my, button);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (modal != null) {
			modal.mouseScrolled(mx, my, dy);
			return true; // the page under the veil does not scroll
		}
		return current != null && current.mouseScrolled(mx, my, dy);
	}

	public boolean mouseDragged(double mx, double my, int button) {
		if (modal != null) {
			modal.mouseDragged(mx, my, button);
			return true;
		}
		return current != null && current.mouseDragged(mx, my, button);
	}

	public boolean mouseReleased(double mx, double my, int button) {
		if (modal != null) {
			modal.mouseReleased(mx, my, button);
		}
		return current != null && current.mouseReleased(mx, my, button);
	}

	/**
	 * A typed character: the modal's text box or the page's text area. False = not the host's (a page's
	 * vanilla text box, or the screen's own, takes it through {@code super.charTyped}); with a modal open
	 * the answer is always true, and only the modal's boxes hear it.
	 */
	public boolean charTyped(CharacterEvent event) {
		if (modal != null) {
			if (!modal.charTyped(event.codepoint()) && owner.screen().getFocused() instanceof EditBox box) {
				box.charTyped(event);
			}
			return true;
		}
		return current != null && current.charTyped(event.codepoint());
	}

	/** True when the key was the host's or the page's; Esc with nothing open goes back. */
	public boolean keyPressed(KeyEvent e) {
		if (modal != null) {
			if (e.isEscape()) {
				modal.cancel();
			} else if (!modal.keyPressed(e) && owner.screen().getFocused() instanceof EditBox box) {
				box.keyPressed(e); // typing, arrows, backspace in a text box of the modal
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
		return p.keyPressed(e);
	}
}
