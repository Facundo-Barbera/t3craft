package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayList;
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
 * The main T3 screen's right panel (#39): a column after the thread that shows one {@link RightPanels} page
 * (the piece browser first; a diff, a plan, the queue later). A header with the page's title and an
 * {@link T3Icon#X} close button, the page's {@link T3Panel} under it.
 *
 * <p>#50: with more than one registered page the header carries a switcher instead of the title (compact
 * {@link T3Tabs} when every title fits, a {@link T3Select} otherwise), and always a {@link T3Icon#MAXIMIZE_2} button
 * that hands the page to the owner to show full-size ({@link Owner#expand}). With a sub-page on top the switcher gives
 * way to that page's title and the close button reads "Back", as before.
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

		/** #50: a fresh page for {@code id} (an owner may give a page its context, the queue its thread). */
		default @Nullable T3Panel create(String id) {
			return RightPanels.create(id);
		}

		/** #50: the header's switcher asked for page {@code id}; the owner opens it ({@link #open}) and remembers it. */
		default void switchTo(String id) {
		}

		/** #50: the header's full-size button: show page {@code id} in a bigger place. False hides the button. */
		default boolean canExpand() {
			return false;
		}

		default void expand(String id) {
		}
	}

	/** Gap between the header's controls. */
	private static final int GAP = 4;

	private final T3PanelHost host;
	private final Owner owner;
	private @Nullable String id;
	/** The root page shown (not a sub-page it stacked), or null. */
	private @Nullable T3Panel page;
	private @Nullable Font font;
	/** The switcher of the last layout: its keyboard focus is handed to the next one (switching rebuilds the header). */
	private @Nullable T3Control switcher;

	public T3RightPanel(Owner owner) {
		this.owner = owner;
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
				page = null;
				owner.closed();
			}
		});
		this.host.setHeader(this::arrangeHeader);
	}

	/** The root page shown, or null when closed (the owner releases what it watches when the screen goes away). */
	public @Nullable T3Panel page() {
		return host.active() ? page : null;
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
		T3Panel p = owner.create(panelId);
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
		this.page = page;
		host.open(page);
	}

	/** Closes the page (the owner rebuilds its widgets). */
	public void close() {
		host.close();
		id = null;
		page = null;
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
		this.font = font;
		if (area.w() > 0 && area.h() > 0) {
			host.layout(area, font, t, sink);
		}
	}

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		host.render(g, font, t, mx, my, screen);
	}

	// ---- header (#50) ----------------------------------------------------------------------------------------

	/** The switcher and the full-size button, right to left from the close button; returns where the title ends. */
	private int arrangeHeader(Rect row, boolean root, Consumer<T3Control> add) {
		T3Control before = switcher;
		boolean hadKeys = before != null && before.focusRingVisible();
		switcher = null;
		int right = row.right();
		String shownId = id;
		if (owner.canExpand() && shownId != null && row.w() >= T3Control.H) {
			T3Button full = T3Button.iconOnly(T3Icon.MAXIMIZE_2, "Open full-size in the thread's place", T3Button.Style.GHOST,
					() -> owner.expand(shownId));
			full.setBounds(new Rect(right - T3Control.H, row.y(), T3Control.H, T3Control.H));
			add.accept(full);
			right -= T3Control.H + GAP;
		}
		List<RightPanels.Entry> pages = RightPanels.all();
		Font f = font;
		if (!root || pages.size() < 2 || f == null || shownId == null) {
			return right; // one page (or a sub-page on top): the title, as before
		}
		Rect area = new Rect(row.x(), row.y(), Math.max(0, right - row.x()), T3Control.H);
		int chosen = 0;
		List<String> titles = new ArrayList<>();
		int tabsW = 0;
		for (int i = 0; i < pages.size(); i++) {
			RightPanels.Entry e = pages.get(i);
			titles.add(e.title());
			tabsW += f.width(e.title()) + 16; // T3Tabs: the label and 8 px on each side
			if (e.id().equals(shownId)) {
				chosen = i;
			}
		}
		T3Control next;
		if (tabsW <= area.w()) {
			next = new T3Tabs(f, titles, chosen, i -> owner.switchTo(pages.get(i).id()));
			next.setTooltip("Side panel pages (Left / Right while focused)");
		} else {
			next = new T3Select<>(pages, RightPanels.Entry::title, pages.get(chosen), e -> owner.switchTo(e.id()));
			next.setTooltip("Side panel page: " + pages.get(chosen).title());
		}
		next.setBounds(area);
		add.accept(next);
		if (hadKeys) {
			next.focusIn(null, true); // Left / Right on the tabs switched the page: the keyboard stays on the switcher
		}
		switcher = next;
		return row.x(); // the switcher names the page
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
