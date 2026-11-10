package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The main T3 screen's right panel (#39): a column after the thread that shows one {@link RightPanels} page
 * (the piece browser first; a diff, a plan, the queue later). A header with the page's title and an
 * {@link T3Icon#X} close button, the page's {@link T3Panel} under it.
 *
 * <p>It is a {@link T3PanelHost} with an id: the frame, the header, the page stack (a page may
 * {@link T3Panel.Host#show} a sub-page; its back button then reads "Back"), the confirmation modal and the
 * input routing are the host's, so a page behaves the same here as in the thread's place or in a screen of
 * its own. The owner forwards draw and input calls while {@link #active()}, like it does for its pages.
 */
public final class T3RightPanel {
	/** What the panel needs from the screen that owns it. */
	public interface Owner {
		/** The page or its data changed: {@code rebuildWidgets()} (safe before the screen is initialised). */
		void relayout();

		Screen screen();

		/** The panel closed itself (its x, or Esc on its root page). */
		void closed();
	}

	private final T3PanelHost host;
	private @Nullable String id;

	public T3RightPanel(Owner owner) {
		this.host = new T3PanelHost(new T3PanelHost.Owner() {
			@Override
			public void relayout() {
				owner.relayout();
			}

			@Override
			public Screen screen() {
				return owner.screen();
			}

			@Override
			public void left() {
				id = null;
				owner.closed();
			}
		});
	}

	/** The id of the page shown ({@link RightPanels}), or null when closed. */
	public @Nullable String id() {
		return host.active() ? id : null;
	}

	public boolean active() {
		return host.active();
	}

	/** Opens the registered page {@code id}; false (and nothing changes) when it is unknown or failed. */
	public boolean open(String panelId) {
		if (host.active() && panelId.equals(id)) {
			return true;
		}
		T3Panel p = RightPanels.create(panelId);
		if (p == null) {
			return false;
		}
		show(panelId, p);
		return true;
	}

	/** Opens {@code page} under {@code panelId} (the gallery's fake data); replaces what was shown. */
	public void show(String panelId, T3Panel page) {
		if (host.active()) {
			host.close();
		}
		id = panelId;
		host.open(page);
	}

	/** Closes the page (the owner rebuilds its widgets). */
	public void close() {
		host.close();
		id = null;
	}

	public boolean modalOpen() {
		return host.modalOpen();
	}

	public boolean overlayOpen() {
		return host.overlayOpen();
	}

	// ---- layout and drawing ---------------------------------------------------------------------------------

	/** Call from {@code init()} with the layout's right rectangle; nothing when it is empty. */
	public void layout(Rect area, Font font, ChatTheme t, Consumer<AbstractWidget> sink) {
		if (area.w() > 0 && area.h() > 0) {
			host.layout(area, font, t, sink);
		}
	}

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		host.render(g, font, t, mx, my, screen);
	}

	/** After the owner drew its widgets: open selects, the tooltip, the modal. */
	public void renderTop(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		host.renderTop(g, font, t, mx, my, screen);
	}

	public void tick() {
		host.tick();
	}

	// ---- input ----------------------------------------------------------------------------------------------

	public boolean mouseClicked(double mx, double my, int button) {
		return host.mouseClicked(mx, my, button);
	}

	public boolean mouseDragged(double mx, double my, int button) {
		return host.mouseDragged(mx, my, button);
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return host.mouseReleased(mx, my, button);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		return host.mouseScrolled(mx, my, dy);
	}

	public boolean charTyped(CharacterEvent e) {
		return host.charTyped(e);
	}

	public boolean keyPressed(KeyEvent e) {
		return host.keyPressed(e);
	}
}
