package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Ago;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.ThreadCache;
import dev.t3craft.client.chat.ThreadListCache;
import dev.t3craft.client.chat.ThreadDirectory;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.chat.compose.ChatComposer;
import dev.t3craft.client.chat.compose.ComposerInput;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.portable.NotificationLog;
import dev.t3craft.client.portable.PortableClient;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The one screen of the T3 key (Y), the T3 Terminal item and every block click (desks, monitors,
 * thread wall, NPCs): T3 Code's layout ({@code apps/web/src/components/AppSidebarLayout.tsx}) with
 * the sidebar on the left (search, project filter, "New thread", threads grouped by project with
 * state, relative time and needs-attention, settings / environments / archive icons at the bottom)
 * and the thread on the right (header with the project › thread breadcrumb and the state chip; the
 * conversation; the composer at the bottom). Replaces the old selector ({@code PortableScreen}) +
 * chat ({@code ThreadBrowserScreen}).
 *
 * <p>feat/t3-ui-clean: thread actions live where T3 Code has them: right-click on a sidebar thread
 * opens its menu (Settle, Snooze…, Pin/Unpin, Mark as read, Archive, Interrupt/Stop while it runs),
 * hovering a row shows settle and pin icons, and while the thread runs the header shows a small
 * Stop next to the state (the composer's send button turns into interrupt). No key-cap hint bar
 * and no time separators; the keys keep working.
 *
 * <p>Pure logic lives in {@link SidebarModel} (search/filter/grouping/order), {@link T3UiLayout}
 * (geometry and hit-testing) and {@link T3UiKeys} (keyboard); the slots for the self-contained
 * widgets of the parallel branches (composer, model picker, new-thread / register-project dialogs,
 * status chip) are in {@link T3Slots}. No static state; the conversation feed is opened in
 * {@link #init()} and closed in {@link #removed()}, so the screen can be the parent of a dialog
 * and come back to the same thread.
 */
public final class T3Screen extends Screen {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private final ThreadDirectory directory;
	private final ChatSource source;
	private final ConversationPanel.@Nullable Actions actions;
	private final T3Slots slots;
	private final SidebarModel model = new SidebarModel();
	private final ChatTheme theme = ChatTheme.warmStudio();

	private @Nullable ConversationPanel conversation;
	/**
	 * The composer (feat/chat-compose), drawn by the conversation as its footer. Owned by the screen so
	 * drafts, pending messages and errors survive the confirmation and pairing screens.
	 */
	private @Nullable ComposerView composer;
	/** Thread to open when the conversation is (re)built; follows the selection. */
	private @Nullable String key;
	private String fallbackEnvLabel = "";
	private String fallbackTitle = "";
	private @Nullable Screen parent;

	private boolean sidebarVisible;
	private T3UiKeys.Focus focus = T3UiKeys.Focus.LIST;
	private int first;
	private boolean menuOpen;
	private boolean projectMenuOpen;
	private boolean notifications;
	private boolean autoSelected;
	private int ticks;
	/** Right-click menu of a sidebar thread: its rows and where it opened; null = closed. */
	private @Nullable List<T3Slots.MenuItem> contextItems;
	private int contextX;
	private int contextY;
	/** Where the last click was (the context menu opens there). */
	private double clickX;
	private double clickY;

	private T3UiLayout layout = T3UiLayout.compute(0, 0, true);
	private T3UiLayout.SidebarRects sr = T3UiLayout.sidebar(Rect.EMPTY);
	private T3UiLayout.MainRects mr = T3UiLayout.main(Rect.EMPTY, 0);
	private @Nullable EditBox search;
	private final List<Hit> hits = new ArrayList<>();
	/** Composer controls with a popup (the model picker): input goes to them first while open. */
	private final List<T3Slots.Popup> popups = new ArrayList<>();
	/** Rows of the open menu (⋯ or project), drawn over everything. */
	private final List<Hit> menuHits = new ArrayList<>();
	private final Set<String> spansEnvs = new HashSet<>();

	/** feat/t3-thread-cache: the rows last given to the model (skip the rebuild when unchanged). */
	private @Nullable List<ThreadRow> lastRows;
	/** Screen-open prewarm done (last opened, running, pinned threads; at most {@link #PREWARM_ON_OPEN}). */
	private boolean prewarmed;
	/** Sidebar row under the pointer and since when (hover prewarm after {@link #HOVER_PREWARM_MS}). */
	private @Nullable String hoverKey;
	private long hoverSince;
	private @Nullable String hoverWarmed;
	private static final int PREWARM_ON_OPEN = 3;
	private static final long HOVER_PREWARM_MS = 250;

	private record Hit(Rect r, Runnable action, @Nullable Runnable right, @Nullable List<String> tooltip) {
	}

	/**
	 * @param actions conversation buttons and the operate query; null = read-only (an archived thread):
	 *                no header actions, no composer
	 * @param key     thread to select ({@code env:threadId}), null = the most recent one
	 */
	public T3Screen(ThreadDirectory directory, ChatSource source, ConversationPanel.@Nullable Actions actions, T3Slots slots, @Nullable String key) {
		super(Component.literal("T3"));
		this.directory = directory;
		this.source = source;
		this.actions = actions;
		this.slots = slots;
		this.key = key;
		// opened on a thread (a block click): in the compact layout show the thread, not the list
		this.sidebarVisible = T3UiLayout.startsOnSidebar(key);
		this.wideSidebar = true;
		model.select(key);
		if (key != null) {
			autoSelected = true;
		}
	}

	/** Header labels for a thread that is not listed (archived). */
	public T3Screen withFallbackLabels(String envLabel, String title) {
		fallbackEnvLabel = envLabel == null ? "" : envLabel;
		fallbackTitle = title == null ? "" : title;
		return this;
	}

	/** Screen Esc returns to (the archive, a dialog's opener); null = back to the game. */
	public T3Screen withParent(@Nullable Screen parent) {
		this.parent = parent;
		return this;
	}

	public SidebarModel model() {
		return model;
	}

	public @Nullable ConversationPanel conversation() {
		return conversation;
	}

	/** Key of the thread selected (or opened unlisted). */
	public @Nullable String selectedKey() {
		return model.selectedKey();
	}

	public boolean readOnly() {
		return actions == null;
	}

	/** Wide layout: sidebar shown (Ctrl+B). Kept apart from the compact "which panel" flag. */
	private boolean wideSidebar;

	private boolean showSidebarFlag() {
		T3UiLayout probe = T3UiLayout.compute(width, height, true);
		return probe.compact() ? sidebarVisible : wideSidebar;
	}

	// ---- lifecycle ----------------------------------------------------------------------------

	@Override
	protected void init() {
		if (composer == null && actions != null) {
			composer = ChatComposer.create(this, theme, () -> {
				if (conversation != null) {
					conversation.scrollToEnd();
				}
			});
			List<ComposerView.Control> controls = slots.composerControls(this, this::selectedTarget);
			composer.setControls(controls);
			controls.forEach(c -> {
				if (c instanceof T3Slots.Popup p) {
					popups.add(p);
				}
			});
		}
		if (conversation == null) {
			ConversationPanel c = new ConversationPanel(source, theme);
			c.setShowHeader(false);
			c.setActions(actions);
			// T3 Code shows neither a key-cap bar nor minute separators: the keys still work
			c.setShowHints(false);
			c.setShowSeparators(false);
			if (composer != null) {
				ChatComposer.attach(c, composer);
			}
			conversation = c;
			refresh();
			if (key != null) {
				open(key);
			}
		}
		layout = T3UiLayout.compute(width, height, showSidebarFlag());
		sr = T3UiLayout.sidebar(layout.sidebar());
		mr = T3UiLayout.main(layout.main(), 0); // the composer is the conversation's footer

		search = null;
		if (sr.search().w() > 0) {
			Rect s = sr.search();
			EditBox box = new EditBox(font, s.x() + 4, s.y() + 3, s.w() - 8, s.h() - 4, null, Component.literal("Search threads"));
			box.setBordered(false);
			box.setTextColor(theme.text());
			box.setHint(Component.literal("Search threads").withColor(theme.ghost()));
			box.setValue(model.query());
			box.setResponder(q -> {
				model.setQuery(q);
				first = 0;
				revealSelected();
			});
			search = addRenderableWidget(box);
			if (focus == T3UiKeys.Focus.SEARCH) {
				setFocused(box);
				box.setFocused(true);
			}
		} else if (focus == T3UiKeys.Focus.SEARCH) {
			focus = T3UiKeys.Focus.LIST;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			minecraft.gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public void removed() {
		ConversationPanel c = conversation;
		if (c != null) {
			String k = c.key();
			if (k != null) {
				key = k;
			}
			c.close();
			conversation = null;
		}
		ThreadListCache.get().save(); // feat/t3-thread-cache: list + recent tails for the next game start
		super.removed();
	}

	@Override
	public void tick() {
		refresh();
		if (conversation != null) {
			conversation.tick();
		}
		ticks++;
	}

	private void refresh() {
		List<ThreadRow> rows = directory.rows();
		// the directory hands back the same list while nothing changed: no rebuild per tick
		if (rows != lastRows && !rows.equals(lastRows)) {
			lastRows = rows;
			model.update(rows);
			spansEnvs.clear();
			rows.forEach(r -> spansEnvs.add(r.env()));
		}
		if (!autoSelected && !model.threads().isEmpty()) {
			// first fill with nothing asked for: open the first thread of the list
			autoSelected = true;
			model.move(1);
			ThreadRow r = model.selected();
			if (r != null) {
				showRow(r);
			}
		}
		if (!prewarmed && !rows.isEmpty() && conversation != null) {
			prewarmed = true;
			prewarmOnOpen(rows);
		}
		ConversationPanel c = conversation;
		if (c != null && c.key() == null && model.selected() == null && key == null) {
			c.showNothing(model.all().isEmpty() ? "No threads yet. Pair an environment from the menu (⋯ › Environments)." : "Select a thread.");
		}
	}

	// ---- prewarm (feat/t3-thread-cache) --------------------------------------------------------

	/** At most {@link #PREWARM_ON_OPEN} threads: the last one opened, then running, then pinned. */
	private void prewarmOnOpen(List<ThreadRow> rows) {
		List<ThreadRow> picks = new ArrayList<>();
		String shown = key;
		String last = ThreadCache.get().lastOpened();
		if (last != null && !last.equals(shown)) {
			rows.stream().filter(r -> r.key().equals(last)).findFirst().ifPresent(picks::add);
		}
		for (ThreadRow r : rows) {
			if (r.state() == dev.t3craft.t3.ThreadState.WORKING && !picks.contains(r)) {
				picks.add(r);
			}
		}
		for (ThreadRow r : rows) {
			if (r.pinned() && !picks.contains(r)) {
				picks.add(r);
			}
		}
		int n = 0;
		for (ThreadRow r : picks) {
			if (n >= PREWARM_ON_OPEN) {
				break;
			}
			if (!r.key().equals(shown) && prewarm(r)) {
				n++;
			}
		}
	}

	/** Hover over a sidebar thread: warm it once the pointer rests on it. */
	private void hoverPrewarm(ThreadRow r, boolean selected) {
		long now = System.currentTimeMillis();
		if (!r.key().equals(hoverKey)) {
			hoverKey = r.key();
			hoverSince = now;
			return;
		}
		if (!selected && !r.key().equals(hoverWarmed) && now - hoverSince >= HOVER_PREWARM_MS) {
			hoverWarmed = r.key();
			prewarm(r);
		}
	}

	private boolean prewarm(ThreadRow r) {
		if (r.threadId() == null || readOnly()) {
			return false;
		}
		try {
			return ThreadCache.get().prewarm(source.env(r.env()), r.threadId());
		} catch (ChatSource.Unavailable | RuntimeException e) {
			return false;
		}
	}

	// ---- selection ----------------------------------------------------------------------------

	/** Selects and shows a thread by key, even when it is not listed (opened from a block or the archive). */
	public void open(String k) {
		String before = model.query();
		boolean listed = model.focus(k);
		if (search != null && !before.equals(model.query())) {
			search.setValue(model.query());
		}
		ThreadRow r = listed ? model.find(k) : null;
		if (r != null) {
			showRow(r);
		} else {
			int colon = k.indexOf(':');
			if (colon > 0 && colon < k.length() - 1 && conversation != null) {
				conversation.show(k.substring(0, colon), fallbackEnvLabel, k.substring(colon + 1), fallbackTitle);
			}
		}
		key = k;
		notifications = false;
		revealSelected();
	}

	private void showRow(ThreadRow r) {
		key = r.key();
		ConversationPanel c = conversation;
		if (c == null) {
			return;
		}
		c.setBadge(EnvBadge.of(r.env(), r.envLabel()));
		if (r.threadId() == null) {
			c.showNothing(I18n.get("t3craft.chat.history.bridge"), ChatSource.Unavailable.Action.PAIR);
		} else {
			c.show(r.env(), r.envLabel(), r.threadId(), r.title());
		}
	}

	private void step(int dir) {
		if (model.move(dir)) {
			ThreadRow r = model.selected();
			if (r != null) {
				showRow(r);
			}
		}
		revealSelected();
	}

	private void revealSelected() {
		first = T3UiLayout.reveal(first, model.selectedItem(), model.items().size(), sr.list());
	}

	private T3Slots.@Nullable Target selectedTarget() {
		String k = model.selectedKey();
		if (k == null) {
			return null;
		}
		int colon = k.indexOf(':');
		if (colon <= 0 || colon == k.length() - 1) {
			return null;
		}
		ThreadRow r = model.find(k);
		String title = r != null ? r.title() : fallbackTitle;
		String project = r != null ? r.project() : "";
		return new T3Slots.Target(k.substring(0, colon), k.substring(colon + 1), title, project);
	}

	private void setSidebarVisible(boolean v) {
		if (layout.compact()) {
			sidebarVisible = v;
		} else {
			wideSidebar = v;
		}
		rebuildWidgets();
	}

	// ---- drawing ------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, theme.backdrop());
		g.fillGradient(0, 0, width, height, theme.backdropBottom() & 0x00FFFFFF, theme.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		menuHits.clear();
		if (layout.sidebar().w() > 0) {
			drawSidebar(g, mouseX, mouseY);
		}
		if (layout.main().w() > 0) {
			drawMain(g, mouseX, mouseY);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		if (projectMenuOpen) {
			drawProjectMenu(g, mouseX, mouseY);
		}
		if (menuOpen) {
			drawMenu(g, mouseX, mouseY);
		}
		if (contextItems != null) {
			drawItems(g, contextItems, contextX, contextY, mouseX, mouseY, () -> contextItems = null);
		}
		if (!anyMenuOpen()) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.tooltip() != null && h.r().contains(mouseX, mouseY)) {
					g.setTooltipForNextFrame(font, h.tooltip().stream().map(s -> Component.literal(s).getVisualOrderText()).toList(), mouseX, mouseY);
					break;
				}
			}
		}
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		ChatDraw.panel(g, t, layout.sidebar());
		// brand + menu
		Rect b = sr.brand();
		g.text(font, "T3", b.x() + 1, b.y() + 2, t.accent(), t.shadow());
		int attention = (int) model.all().stream().filter(ThreadRow::needsAttention).count();
		String sub = model.all().size() + " threads" + (attention > 0 ? " · " + attention + " need you" : "")
				+ (directory.cached() ? " · cached" : "");
		int menuW = 16;
		g.text(font, ChatDraw.clip(font, sub, b.w() - 20 - menuW - 4), b.x() + 16, b.y() + 2, attention > 0 ? t.needsYou() : t.muted(), t.shadow());
		Rect menu = new Rect(b.right() - menuW, b.y(), menuW, b.h());
		pill(g, menu, "⋯", menuOpen, mouseX, mouseY);
		hits.add(new Hit(menu, () -> {
			menuOpen = !menuOpen;
			projectMenuOpen = false;
		}, null, List.of("Menu: archive, environments, settings, game shortcuts")));

		// search field (the EditBox is drawn over this by super)
		ChatDraw.field(g, t, sr.search(), focus == T3UiKeys.Focus.SEARCH);

		// project filter (T3's project scope menu)
		Rect p = sr.project();
		String label = "▾ " + model.projectLabel();
		pill(g, p, label, model.project() != null, mouseX, mouseY);
		hits.add(new Hit(p, () -> {
			projectMenuOpen = !projectMenuOpen;
			menuOpen = false;
		}, () -> model.cycleProject(1), List.of("Filter by project", "Click: choose · ←/→: previous/next")));

		// New thread
		Rect n = sr.newThread();
		pill(g, n, n.w() < 60 ? "+" : "+ New thread", false, mouseX, mouseY);
		hits.add(new Hit(n, this::newThread, null, List.of("New thread (Ctrl+N)")));

		drawList(g, mouseX, mouseY);

		// footer: discreet icons, as T3 Code's sidebar bottom row (settings · environments · archive)
		Rect f = sr.footer();
		String[] glyphs = {"⚙", "☁", "▤"};
		String[] tips = {"Settings", "Environments", "Archive"};
		Runnable[] acts = {() -> slots.openSettings(this), () -> slots.openEnvironments(this), this::openArchive};
		int ix = f.x();
		for (int i = 0; i < glyphs.length; i++) {
			Rect c = new Rect(ix, f.y(), 14, f.h());
			iconButton(g, c, glyphs[i], mouseX, mouseY);
			hits.add(new Hit(c, acts[i], null, List.of(tips[i])));
			ix += 16;
		}
	}

	/** A borderless icon that lights up on hover (T3's ghost icon buttons). */
	private void iconButton(GuiGraphicsExtractor g, Rect r, String glyph, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean hover = r.contains(mouseX, mouseY) && !anyMenuOpen();
		if (hover) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.hover());
		}
		g.text(font, glyph, r.x() + (r.w() - font.width(glyph)) / 2, r.y() + (r.h() - 8) / 2, hover ? t.accent() : t.muted(), false);
	}

	private boolean anyMenuOpen() {
		return menuOpen || projectMenuOpen || contextItems != null;
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		Rect list = sr.list();
		if (list.h() <= 0) {
			return;
		}
		ChatDraw.inset(g, t, list);
		List<SidebarModel.Item> items = model.items();
		if (items.isEmpty()) {
			String msg = model.all().isEmpty() ? "No threads yet."
					: model.searching() ? "No threads match \"" + model.query() + "\"." : "No threads in this project.";
			int ly = list.y() + 4;
			for (FormattedCharSequence line : font.split(Component.literal(msg), Math.max(20, list.w() - 8))) {
				if (ly + font.lineHeight > list.bottom()) {
					break;
				}
				g.text(font, line, list.x() + 4, ly, t.muted(), t.shadow());
				ly += font.lineHeight;
			}
			return;
		}
		first = T3UiLayout.clampFirst(first, items.size(), list);
		int cap = T3UiLayout.capacity(list);
		boolean scrollbar = items.size() > cap;
		int rx0 = list.x() + 1;
		int rx1 = list.right() - (scrollbar ? 8 : 1);
		long now = System.currentTimeMillis();
		boolean showEnv = spansEnvs.size() > 1;
		for (int i = first; i < items.size() && i < first + cap; i++) {
			SidebarModel.Item it = items.get(i);
			int ry = list.y() + (i - first) * T3UiLayout.ROW_H;
			Rect rr = new Rect(rx0, ry, rx1 - rx0, T3UiLayout.ROW_H);
			boolean hover = rr.contains(mouseX, mouseY) && !anyMenuOpen();
			int ty = ry + 2;
			switch (it.kind()) {
				case SECTION, PROJECT -> {
					if (hover) {
						g.fill(rr.x(), ry, rr.right(), rr.bottom(), t.hover());
					}
					String arrow = it.collapsed() ? "▸ " : "▾ ";
					String count = Integer.toString(it.count());
					int countW = font.width(count);
					int right = rr.right() - 3 - countW;
					g.text(font, count, right, ty, t.muted(), t.shadow());
					if (it.attention() > 0) {
						ChatDraw.pulsingDot(g, t, ChatTheme.family(Attention.NEEDS_YOU), t.color(Attention.NEEDS_YOU), right - 9, ry + 3);
						right -= 11;
					}
					int color = it.kind() == SidebarModel.Kind.SECTION ? t.brass() : t.text();
					g.text(font, ChatDraw.clip(font, arrow + it.label(), right - rr.x() - 4), rr.x() + 2, ty, color, t.shadow());
					String folder = it.key();
					List<String> tip = new ArrayList<>();
					tip.add(it.label() + " · " + it.count() + (it.count() == 1 ? " thread" : " threads"));
					if (!it.envs().isEmpty()) {
						tip.add(it.envs());
					}
					tip.add("Click: open/close · Right-click: filter this project");
					hits.add(new Hit(rr, () -> model.toggleCollapsed(folder),
							it.kind() == SidebarModel.Kind.PROJECT ? () -> model.setProject(folder.equals(model.project()) ? null : folder) : null, tip));
				}
				case THREAD -> {
					ThreadRow r = it.row();
					if (r == null) {
						continue;
					}
					boolean selected = r.key().equals(model.selectedKey());
					if (hover) {
						hoverPrewarm(r, selected);
					}
					if (selected) {
						g.fill(rr.x(), ry, rr.right(), rr.bottom(), t.selected());
						g.fill(rr.x(), ry, rr.x() + 2, rr.bottom(), focus == T3UiKeys.Focus.LIST ? t.accent() : t.edge());
					} else if (hover) {
						g.fill(rr.x(), ry, rr.right(), rr.bottom(), t.hover());
					}
					int x = rr.x() + 8;
					Attention a = r.attention();
					if (a == Attention.NEEDS_YOU) {
						ChatDraw.pulsingDot(g, t, ChatTheme.family(a), t.color(a), x, ry + 3);
					} else {
						ChatDraw.dot(g, t, ChatTheme.family(a), t.color(a), x, ry + 3);
					}
					x += 10;
					if (showEnv) {
						ChatDraw.envIcon(g, font, EnvBadge.of(r.env(), r.envLabel()), x, ry + 2, 8);
						x += 11;
					}
					// T3's row: the age at the right, replaced on hover by the quick actions (settle, pin)
					T3Slots.Target rowTarget = actions == null ? null : T3ThreadActions.target(r);
					boolean quick = hover && rowTarget != null;
					String age = Ago.format(now, r.activityMs());
					int ageW = age.isEmpty() ? 0 : font.width(age) + 4;
					if (quick) {
						ageW = 2 * 11 + 2;
					} else if (!age.isEmpty()) {
						g.text(font, age, rr.right() - 2 - font.width(age), ty, t.muted(), t.shadow());
					}
					g.text(font, ChatDraw.clip(font, r.title(), rr.right() - 2 - ageW - x), x, ty, r.settled() ? t.muted() : t.text(), t.shadow());
					List<String> tip = new ArrayList<>();
					tip.add(r.title());
					String phrase = Ago.phrase(now, r.activityMs());
					tip.add((r.pinned() ? "pinned · " : "") + a.word + " · " + r.envLabel() + (r.project().isEmpty() ? "" : " · " + r.project())
							+ (phrase.isEmpty() ? "" : " · " + phrase));
					if (r.activity() != null && !r.activity().isBlank()) {
						tip.add(r.activity());
					}
					if (rowTarget != null) {
						tip.add("Right-click: thread actions");
					}
					String k = r.key();
					hits.add(new Hit(rr, () -> {
						model.select(k);
						showRow(r);
						focus = T3UiKeys.Focus.LIST;
						if (layout.compact()) {
							setSidebarVisible(false);
						}
					}, rowTarget == null ? null : () -> openContextMenu(r, rowTarget), tip));
					if (quick) {
						boolean can = T3ThreadActions.canOperate(rowTarget);
						Rect pinR = new Rect(rr.right() - 12, ry, 11, T3UiLayout.ROW_H);
						Rect settleR = new Rect(pinR.x() - 11, ry, 11, T3UiLayout.ROW_H);
						boolean pinned = r.pinned();
						quickIcon(g, settleR, "✓", can, mouseX, mouseY);
						quickIcon(g, pinR, pinned ? "★" : "☆", can, mouseX, mouseY);
						String ro = can ? "" : " (needs control)";
						hits.add(new Hit(settleR, () -> {
							if (can) {
								T3ThreadActions.settle(this, rowTarget);
							}
						}, () -> openContextMenu(r, rowTarget), List.of("Settle" + ro)));
						hits.add(new Hit(pinR, () -> {
							if (can) {
								T3ThreadActions.pin(this, rowTarget, !pinned);
							}
						}, () -> openContextMenu(r, rowTarget), List.of((pinned ? "Unpin" : "Pin") + ro)));
					}
				}
			}
		}
		if (scrollbar) {
			double frac = (double) first / Math.max(1, items.size() - cap);
			ChatDraw.scrollbar(g, t, list.right() - 7, list.y() + 1, list.h() - 2, items.size(), cap, frac, false);
		}
	}

	private void drawMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		// header panel behind the breadcrumb (T3's ChatHeader: project › thread, state)
		Rect head = new Rect(layout.main().x(), layout.main().y(), layout.main().w(), mr.title().bottom() + 2 - layout.main().y());
		ChatDraw.panel(g, t, head);
		Rect tr = mr.title();
		int x = tr.x();
		if (layout.compact()) {
			// back to the list
			Rect back = new Rect(x, tr.y(), 14, tr.h());
			pill(g, back, "‹", false, mouseX, mouseY);
			hits.add(new Hit(back, () -> setSidebarVisible(true), null, List.of("Threads (Esc)")));
			x += 18;
		} else if (!wideSidebar) {
			Rect show = new Rect(x, tr.y(), 14, tr.h());
			pill(g, show, "☰", false, mouseX, mouseY);
			hits.add(new Hit(show, () -> setSidebarVisible(true), null, List.of("Show the sidebar (Ctrl+B)")));
			x += 18;
		}
		T3Slots.Target target = selectedTarget();
		ThreadRow row = model.selectedKey() == null ? null : model.find(model.selectedKey());
		int right = tr.right();
		// state chip (feat/t3-actions ThreadStatusChip when merged, else a pill with the attention word)
		if (row != null) {
			right -= slots.drawStatusChip(g, font, t, row, right, tr.y() + 1, mouseX, mouseY) + 4;
			// while it runs: a small Stop next to the state (interrupt is the composer's ■)
			if (actions != null && target != null && T3ThreadActions.running(row, target)) {
				String stop = "■ Stop";
				int sw = font.width(stop) + 8;
				Rect sr0 = new Rect(right - sw, tr.y() + 1, sw, 12);
				boolean can = T3ThreadActions.canOperate(target);
				boolean hov = can && sr0.contains(mouseX, mouseY) && !anyMenuOpen();
				ChatDraw.round(g, sr0.x(), sr0.y(), sr0.w(), sr0.h(), hov ? ChatTheme.withAlpha(t.errorText(), 70) : t.panelHi());
				ChatDraw.outline(g, sr0.x(), sr0.y(), sr0.w(), sr0.h(), can ? ChatTheme.withAlpha(t.errorText(), 160) : t.edge());
				g.text(font, stop, sr0.x() + 4, sr0.y() + 2, can ? t.errorText() : t.disabled(), false);
				T3Slots.Target st = target;
				hits.add(new Hit(sr0, () -> {
					if (can) {
						T3ThreadActions.stop(this, st);
					}
				}, null, List.of(can ? "Stop the thread (asks first)" : "Stop · needs control (operate)")));
				right -= sw + 4;
			}
		} else if (readOnly() && target != null) {
			String ro = "archived · read-only";
			int w = font.width(ro) + 10;
			ChatDraw.pill(g, font, t, ro, right - w, tr.y() + 1, t.muted());
			right -= w + 4;
		}
		if (target != null) {
			EnvBadge badge = row != null ? EnvBadge.of(row.env(), row.envLabel()) : EnvBadge.of(target.envId(), fallbackEnvLabel);
			ChatDraw.envIcon(g, font, badge, x, tr.y() + 2, 10);
			x += 14;
			// T3's breadcrumb: project › title
			String project = target.project() == null || target.project().isBlank() ? "" : target.project();
			String title = target.title() == null || target.title().isBlank() ? target.threadId() : target.title();
			if (!project.isEmpty()) {
				String crumb = project + " › ";
				int pw = Math.min(font.width(crumb), (right - x) / 3);
				g.text(font, ChatDraw.clip(font, crumb, pw), x, tr.y() + 3, t.muted(), t.shadow());
				x += pw;
			}
			g.text(font, ChatDraw.clip(font, title, right - x), x, tr.y() + 3, t.text(), t.shadow());
		} else {
			g.text(font, notifications ? "Notifications" : "No thread selected", x, tr.y() + 3, t.muted(), t.shadow());
		}

		if (notifications) {
			drawNotifications(g, mr.conversation(), mouseX, mouseY);
		} else if (conversation != null) {
			ComposerView cv = composer;
			if (cv != null) {
				ChatComposer.sync(conversation, cv);
				cv.setBounds(Rect.EMPTY); // the conversation places it
			}
			conversation.setBounds(mr.conversation());
			conversation.render(g, font, mouseX, mouseY);
			if (cv != null && cv.bounds().w() == 0) {
				cv.setFocused(false);
			}
		}
	}

	private void drawNotifications(GuiGraphicsExtractor g, Rect area, int mouseX, int mouseY) {
		ChatTheme t = theme;
		ChatDraw.panel(g, t, area);
		Rect in = area.inset(t.pad());
		ChatDraw.inset(g, t, in);
		NotificationLog log = NotificationLog.INSTANCE;
		List<NotificationLog.Entry> entries = log.entries();
		log.markAllRead();
		if (entries.isEmpty()) {
			g.text(font, ChatDraw.clip(font, "No notifications yet this session.", in.w() - 8), in.x() + 4, in.y() + 4, t.muted(), t.shadow());
			return;
		}
		int rowH = 22;
		int y = in.y() + 2;
		for (NotificationLog.Entry e : entries) {
			if (y + rowH > in.bottom()) {
				break;
			}
			Rect rr = new Rect(in.x() + 1, y, in.w() - 2, rowH);
			if (rr.contains(mouseX, mouseY)) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
			}
			String time = CLOCK.format(Instant.ofEpochMilli(e.atMs()));
			g.text(font, time, rr.x() + 3, y + 2, t.muted(), t.shadow());
			int tx = rr.x() + 6 + font.width("00:00");
			g.text(font, ChatDraw.clip(font, e.title(), rr.right() - tx - 3), tx, y + 2, 0xFF000000 | e.color(), t.shadow());
			g.text(font, ChatDraw.clip(font, e.text(), rr.right() - tx - 3), tx, y + 12, t.muted(), t.shadow());
			String npc = e.npcId();
			hits.add(new Hit(rr, () -> {
				if (npc != null && model.find(npc) != null) {
					open(npc);
				} else {
					T3ThreadActions.notice("That thread is not listed any more (settled or filtered out).");
				}
			}, null, null));
			y += rowH;
		}
	}

	private void drawProjectMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		List<SidebarModel.Project> ps = model.projects();
		Rect anchor = sr.project();
		int rows = ps.size() + 1;
		int maxRows = Math.max(1, (height - anchor.bottom() - 4) / T3UiLayout.ROW_H);
		int shown = Math.min(rows, maxRows);
		Rect box = new Rect(anchor.x(), anchor.bottom() + 1, Math.max(anchor.w(), 120), shown * T3UiLayout.ROW_H + 4);
		ChatDraw.panel(g, t, box);
		int y = box.y() + 2;
		for (int i = 0; i < shown; i++) {
			SidebarModel.Project p = i == 0 ? null : ps.get(i - 1);
			String label = p == null ? "All projects" : p.label() + "  " + p.threads() + (p.attention() > 0 ? " !" + p.attention() : "");
			Rect rr = new Rect(box.x() + 2, y, box.w() - 4, T3UiLayout.ROW_H);
			boolean on = p == null ? model.project() == null : p.key().equals(model.project());
			if (on) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.selected());
			} else if (rr.contains(mouseX, mouseY)) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
			}
			g.text(font, ChatDraw.clip(font, label, rr.w() - 6), rr.x() + 3, y + 2, p != null && p.attention() > 0 ? t.needsYou() : t.text(), t.shadow());
			String k = p == null ? null : p.key();
			menuHits.add(new Hit(rr, () -> {
				model.setProject(k);
				first = 0;
				projectMenuOpen = false;
				revealSelected();
			}, null, null));
			y += T3UiLayout.ROW_H;
		}
	}

	/** The ⋯ menu: what the portable terminal had (archive, environments, settings, notifications) and game shortcuts. */
	private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		List<T3Slots.MenuItem> items = new ArrayList<>();
		int unread = NotificationLog.INSTANCE.unread();
		items.add(new T3Slots.MenuItem(unread > 0 ? "Notifications (" + unread + ")" : "Notifications", true, () -> {
			notifications = true;
			if (layout.compact()) {
				setSidebarVisible(false);
			}
		}));
		items.add(new T3Slots.MenuItem("New thread", true, this::newThread));
		slots.menuItems(this, selectedTarget(), items);
		items.add(new T3Slots.MenuItem("Archive", true, this::openArchive));
		items.add(new T3Slots.MenuItem("Environments", true, () -> slots.openEnvironments(this)));
		items.add(new T3Slots.MenuItem("Settings", true, () -> slots.openSettings(this)));
		items.add(new T3Slots.MenuItem(layout.compact() ? "Show thread" : wideSidebar ? "Hide sidebar (Ctrl+B)" : "Show sidebar (Ctrl+B)", true,
				() -> setSidebarVisible(layout.compact() ? false : !wideSidebar)));
		items.add(new T3Slots.MenuItem("Close", true, this::onClose));
		Rect anchor = sr.brand().w() > 0 ? sr.brand() : mr.title();
		int w = menuWidth(items, Math.max(80, width - anchor.x() - 4));
		drawItems(g, items, Math.max(2, Math.min(anchor.right() - w, width - w - 2)), anchor.bottom() + 1, mouseX, mouseY, () -> menuOpen = false);
	}

	private int menuWidth(List<T3Slots.MenuItem> items, int max) {
		int w = 10;
		for (T3Slots.MenuItem it : items) {
			w = Math.max(w, font.width(it.label()) + 12);
		}
		return Math.min(w, max);
	}

	/** A popup menu at ({@code x}, {@code y}), kept on screen; {@code close} runs before the chosen row. */
	private void drawItems(GuiGraphicsExtractor g, List<T3Slots.MenuItem> items, int x, int y, int mouseX, int mouseY, Runnable close) {
		ChatTheme t = theme;
		int w = menuWidth(items, Math.max(80, width - 4));
		int maxRows = Math.max(1, (height - 6) / T3UiLayout.ROW_H);
		int shown = Math.min(items.size(), maxRows);
		int h = shown * T3UiLayout.ROW_H + 4;
		int bx = Math.max(2, Math.min(x, width - w - 2));
		int by = Math.max(2, Math.min(y, height - h - 2));
		Rect box = new Rect(bx, by, w, h);
		ChatDraw.panel(g, t, box);
		int ry = box.y() + 2;
		for (int i = 0; i < shown; i++) {
			T3Slots.MenuItem it = items.get(i);
			Rect rr = new Rect(box.x() + 2, ry, box.w() - 4, T3UiLayout.ROW_H);
			if (it.enabled() && rr.contains(mouseX, mouseY)) {
				g.fill(rr.x(), rr.y(), rr.right(), rr.bottom(), t.hover());
			}
			g.text(font, ChatDraw.clip(font, it.label(), rr.w() - 6), rr.x() + 3, ry + 2, it.enabled() ? t.text() : t.disabled(), t.shadow());
			if (it.enabled()) {
				menuHits.add(new Hit(rr, () -> {
					close.run();
					it.action().run();
				}, null, null));
			}
			ry += T3UiLayout.ROW_H;
		}
	}

	/** Right-click on a sidebar thread: its actions menu at the pointer. */
	private void openContextMenu(ThreadRow r, T3Slots.Target target) {
		menuOpen = false;
		projectMenuOpen = false;
		contextItems = T3ThreadActions.menu(this, r, target);
		contextX = (int) clickX;
		contextY = (int) clickY;
	}

	/** A small hover icon of a sidebar row. */
	private void quickIcon(GuiGraphicsExtractor g, Rect r, String glyph, boolean enabled, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean hover = r.contains(mouseX, mouseY);
		if (hover && enabled) {
			ChatDraw.round(g, r.x(), r.y() + 1, r.w(), r.h() - 2, t.panelHi());
		}
		g.text(font, glyph, r.x() + (r.w() - font.width(glyph)) / 2, r.y() + 2, !enabled ? t.disabled() : hover ? t.accent() : t.muted(), false);
	}

	private void pill(GuiGraphicsExtractor g, Rect r, String label, boolean on, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean hover = r.contains(mouseX, mouseY);
		ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), on ? t.selected() : hover ? t.hover() : t.panelHi());
		ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), on ? t.accent() : t.edge());
		String s = ChatDraw.clip(font, label, r.w() - 6);
		g.text(font, s, r.x() + (r.w() - font.width(s)) / 2, r.y() + (r.h() - 8) / 2, hover || on ? t.accent() : t.keycapText(), false);
	}

	// ---- actions ------------------------------------------------------------------------------

	private void newThread() {
		T3Slots.Target target = selectedTarget();
		slots.newThread(this, target == null ? null : target.envId(), model.project(), target);
	}

	private void openArchive() {
		if (!slots.openArchive(this)) {
			T3ThreadActions.notice("The archive is not available right now.");
		}
	}

	// ---- input --------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		clickX = mx;
		clickY = my;
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		if (anyMenuOpen()) {
			// the open menu's rows are the last hits; anything else closes it
			for (Hit h : List.copyOf(menuHits)) {
				if (h.r().contains(mx, my)) {
					h.action().run();
					return true;
				}
			}
			menuOpen = false;
			projectMenuOpen = false;
			contextItems = null;
			return true;
		}
		if (composer != null && !composer.contains(mx, my)) {
			composer.setFocused(false);
		} else if (composer != null && search != null) {
			search.setFocused(false); // the conversation forwards the click to the composer
			setFocused(null);
			focus = T3UiKeys.Focus.LIST;
		}
		if (super.mouseClicked(event, doubleClick)) {
			syncFocusFromWidgets();
			return true;
		}
		if (focus == T3UiKeys.Focus.SEARCH && search != null) {
			search.setFocused(false);
			setFocused(null);
			focus = T3UiKeys.Focus.LIST;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.r().contains(mx, my)) {
				if (MouseButtons.isRight(button) && h.right() != null) {
					h.right().run();
				} else {
					h.action().run();
				}
				revealSelected();
				return true;
			}
		}
		return conversation != null && !notifications && conversation.mouseClicked(mx, my, button);
	}

	private void syncFocusFromWidgets() {
		if (search != null && search.isFocused()) {
			focus = T3UiKeys.Focus.SEARCH;
		}
	}

	private void focusComposer(boolean on) {
		if (composer == null) {
			return;
		}
		composer.setFocused(on);
		if (composer.focused() && search != null) {
			search.setFocused(false);
			setFocused(null);
		}
		focus = T3UiKeys.Focus.LIST;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return (composer != null && composer.mouseDragged(event.x(), event.y())) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return (composer != null && composer.mouseReleased()) || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.mouseScrolled(x, y, scrollY)) {
				return true;
			}
		}
		if (anyMenuOpen()) {
			return true;
		}
		if (sr.list().contains(x, y) && scrollY != 0) {
			first = T3UiLayout.clampFirst(first + (scrollY > 0 ? -3 : 3), model.items().size(), sr.list());
			return true;
		}
		if (composer != null && composer.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return (conversation != null && !notifications && conversation.mouseScrolled(x, y, scrollY)) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.keyPressed(e)) {
				return true;
			}
		}
		// while the composer has focus every key is its own (Esc leaves it): no screen shortcut fires
		if (ComposerInput.keyPressed(composer, e.key(), e.hasShiftDown(), e.hasControlDownWithQuirk())) {
			return true;
		}
		boolean compactSidebar = layout.compact() && layout.sidebar().w() > 0;
		boolean compactThread = layout.compact() && layout.main().w() > 0;
		if (notifications && e.isEscape() && !anyMenuOpen()) {
			notifications = false;
			return true;
		}
		T3UiKeys.Action a = T3UiKeys.decide(e.key(), e.hasControlDown(), e.hasShiftDown(), focus, anyMenuOpen(),
				model.query().isEmpty(), compactSidebar, compactThread);
		if (focus == T3UiKeys.Focus.LIST && a == T3UiKeys.Action.PASS && PortableClient.key() != null && PortableClient.key().matches(e)) {
			onClose(); // the T3 key closes the screen it opened
			return true;
		}
		switch (a) {
			case PASS -> {
				return (conversation != null && !notifications && conversation.keyPressed(e)) || super.keyPressed(e);
			}
			case TYPE -> {
				return search != null && search.keyPressed(e);
			}
			case PREV_THREAD -> step(-1);
			case NEXT_THREAD -> step(1);
			case PREV_PROJECT -> {
				model.cycleProject(-1);
				first = 0;
				revealSelected();
			}
			case NEXT_PROJECT -> {
				model.cycleProject(1);
				first = 0;
				revealSelected();
			}
			case FOCUS_SEARCH -> {
				if (layout.sidebar().w() == 0) {
					setSidebarVisible(true);
				}
				focus = T3UiKeys.Focus.SEARCH;
				if (search != null) {
					setFocused(search);
					search.setFocused(true);
				}
			}
			case CLEAR_SEARCH -> {
				if (search != null) {
					search.setValue("");
				}
				model.setQuery("");
			}
			case BLUR -> {
				if (search != null) {
					search.setFocused(false);
				}
				setFocused(null);
				focus = T3UiKeys.Focus.LIST;
			}
			case NEW_THREAD -> newThread();
			case TOGGLE_SIDEBAR -> setSidebarVisible(layout.compact() ? layout.sidebar().w() == 0 : !wideSidebar);
			case OPEN_SELECTED -> {
				ThreadRow r = model.selected();
				if (r != null) {
					showRow(r);
				}
				focus = T3UiKeys.Focus.LIST;
				if (search != null) {
					search.setFocused(false);
				}
				setFocused(null);
				setSidebarVisible(false);
			}
			case FOCUS_COMPOSER -> focusComposer(true);
			case SHOW_SIDEBAR -> setSidebarVisible(true);
			case CLOSE_MENU -> {
				menuOpen = false;
				projectMenuOpen = false;
				contextItems = null;
			}
			case CLOSE -> onClose();
		}
		return true;
	}

	/** Image files dropped on the window go to the composer (T3 Code takes dropped images too). */
	@Override
	public void onFilesDrop(List<Path> paths) {
		if (composer != null && !readOnly()) {
			composer.addFiles(paths);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (ComposerInput.owns(composer)) {
			return ComposerInput.charTyped(composer, event.codepoint());
		}
		if (focus == T3UiKeys.Focus.SEARCH) {
			return super.charTyped(event);
		}
		return false;
	}
}
