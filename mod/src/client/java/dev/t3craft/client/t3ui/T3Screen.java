package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.Links;
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
import dev.t3craft.client.chat.request.PendingRequest;
import dev.t3craft.client.chat.request.RequestCard;
import dev.t3craft.client.chat.request.RequestFooter;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.client.portable.NotificationLog;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.ProjectsService;
import dev.t3craft.client.portable.PortableClient;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentsPanel;
import dev.t3craft.client.config.SettingsPanel;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3PanelHost;
import dev.t3craft.client.t3ui.sidebar.SidebarView;
import dev.t3craft.client.t3ui.sidebar.WorkingStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
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
 * <p>feat/t3-sidebar-rows: the thread list is {@link SidebarView} (rich 2–3 line rows, Working /
 * Snoozed / Settled docked at the bottom, only visible rows drawn); the header shows the spinner and
 * elapsed time while the thread runs and the transcript ends with "Working… 4m 12s"
 * ({@link WorkingStatus}).
 *
 * <p>feat/t3-composer: the composer sits in T3's centred column under the transcript, and "New
 * thread" (the sidebar's square-pen icon, a project folder's plus, Ctrl+N, the ⋯ menu) opens no window: it adds a
 * {@link ThreadDraft} row under its project and the thread panel shows "project › New thread", an
 * empty transcript and the composer with the draft's project and workspace chips. The first message
 * launches the thread ({@link DraftLauncher}) and the real thread replaces the draft; Esc or the x
 * discards it.
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
	/** The UI tokens ({@code gui/ui-style.json}); swapped by {@link #syncTheme} after an F3+T. */
	private ChatTheme theme = ChatTheme.warmStudio();

	private @Nullable ConversationPanel conversation;
	/**
	 * The composer (feat/chat-compose), drawn by the conversation as its footer. Owned by the screen so
	 * drafts, pending messages and errors survive the confirmation and pairing screens.
	 */
	private @Nullable ComposerView composer;
	/**
	 * feat/t3-user-input: the card of the selected thread's pending question or approval, drawn in
	 * the composer's place ({@link RequestFooter}). Owned by the screen like the composer, so the
	 * choices survive the confirmation and pairing screens.
	 */
	private @Nullable RequestCard requestCard;
	/** Thread to open when the conversation is (re)built; follows the selection. */
	private @Nullable String key;
	private String fallbackEnvLabel = "";
	private String fallbackTitle = "";
	private @Nullable Screen parent;

	private boolean sidebarVisible;
	private T3UiKeys.Focus focus = T3UiKeys.Focus.LIST;
	private final SidebarView sidebarView = new SidebarView(model, new SidebarHost());
	/**
	 * The kit's pages (settings, environments): when one is open it takes the thread panel's place, as T3
	 * Code's settings page does; the sidebar stays. Esc or its x goes back to the thread.
	 */
	private final T3PanelHost pages = new T3PanelHost(new T3PanelHost.Owner() {
		@Override
		public void relayout() {
			if (minecraft != null) {
				rebuildWidgets();
			}
		}

		@Override
		public Screen screen() {
			return T3Screen.this;
		}

		@Override
		public void left() {
			if (minecraft != null) {
				rebuildWidgets();
			}
		}
	});
	private boolean menuOpen;
	private boolean projectMenuOpen;
	/** Agents menu filter: false = active only (default), true = all children. */
	private boolean agentsShowAll;
	/** Agents menu with the "active" filter: the finished section unfolded. */
	private boolean agentsFinishedOpen;
	private boolean notifications;
	private boolean autoSelected;
	private int ticks;
	/** Right-click menu of a sidebar thread: its rows and where it opened; null = closed. */
	private @Nullable List<T3Slots.MenuItem> contextItems;
	/**
	 * #27: rows of {@link #contextItems} drawn with an icon before their label (the agents menu's "Finished"
	 * fold chevron). By identity: {@link T3Slots.MenuItem} is a record, two rows may be equal.
	 */
	private final Map<T3Slots.MenuItem, T3Icon> itemIcons = new IdentityHashMap<>();
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

	/** feat/t3-thread-cache: the rows last given to the model (skip the rebuild when unchanged). */
	private @Nullable List<ThreadRow> lastRows;
	/** Screen-open prewarm done (last opened, running, pinned threads; at most {@link #PREWARM_ON_OPEN}). */
	private boolean prewarmed;
	/** Sidebar row under the pointer and since when (hover prewarm after {@link #HOVER_PREWARM_MS}). */
	private @Nullable String hoverKey;
	private long hoverSince;
	private @Nullable String hoverWarmed;
	private static final int PREWARM_ON_OPEN = 3;

	/** The draft thread (one at a time, like T3's draft per project), or null. */
	private @Nullable ThreadDraft draft;
	/** A draft asked for before the projects were known ({@link #withDraft}): env, projectId. */
	private @Nullable String[] pendingDraft;
	private @Nullable DraftControls draftControls;
	/** Threads launched from a draft and not listed yet: selected as soon as they show. */
	private @Nullable String awaitKey;
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
		// feat/t3-composer: a draft thread outlives its screen (closing Y does not lose it)
		if (actions != null) {
			draft = ThreadDraft.kept;
			ThreadDraft.kept = null;
		}
		if (draft != null && key == null && draft.wasSelected) {
			model.select(draft.key());
			this.key = draft.key();
			this.sidebarVisible = false;
			autoSelected = true;
		}
	}

	/** Header labels for a thread that is not listed (archived). */
	public T3Screen withFallbackLabels(String envLabel, String title) {
		fallbackEnvLabel = envLabel == null ? "" : envLabel;
		fallbackTitle = title == null ? "" : title;
		return this;
	}

	/**
	 * Opens on a new draft thread of {@code projectId} in {@code envId} (either may be null: the
	 * selection or the first project), as T3's "new thread" (a block's "new thread here").
	 */
	public T3Screen withDraft(@Nullable String envId, @Nullable String projectId) {
		pendingDraft = new String[] {envId, projectId};
		autoSelected = true;
		sidebarVisible = false;
		return this;
	}

	/** Opens with a kit page (settings, environments) in the thread panel's place: the gallery. */
	public T3Screen withPage(T3Panel page) {
		pages.open(page);
		sidebarVisible = false;
		autoSelected = true;
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
		if (draft != null && ThreadDraft.kept == draft) {
			ThreadDraft.kept = null; // back from a confirmation / pairing screen: still ours
		}
		if (composer == null && actions != null) {
			composer = ChatComposer.create(this, theme, () -> {
				if (conversation != null) {
					conversation.scrollToEnd();
				}
			}, () -> {
				if (selectedDraft() != null) {
					discardDraft();
					return true;
				}
				return false;
			});
			composer.setMaxWidth(T3UiLayout.COLUMN_MAX);
			List<ComposerView.Control> controls = slots.composerControls(this, this::selectedTarget, this::selectedDraft);
			composer.setControls(controls);
			controls.forEach(c -> {
				if (c instanceof T3Slots.Popup p) {
					popups.add(p);
				}
			});
			draftControls = new DraftControls(font, this::selectedDraft, this::moveDraft);
			popups.addAll(draftControls.popups());
			Screen self = this;
			requestCard = new RequestCard(new RequestCard.Host() {
				@Override
				public boolean canOperate(String envId) {
					return OperateUi.gate().canOperate(envId);
				}

				@Override
				public void enableControl(String envId) {
					ActionsClient.enableControl(self, envId);
				}

				@Override
				public void run(dev.t3craft.t3.OperateAction action, java.util.function.Consumer<dev.t3craft.t3.OperateResult> done) {
					OperateUi.run(action, OperateGate.Origin.UI, self, done);
				}
			});
			requestCard.setMaxWidth(T3UiLayout.COLUMN_MAX);
		}
		if (conversation == null) {
			ConversationPanel c = new ConversationPanel(source, theme);
			c.setShowHeader(false);
			c.setActions(actions);
			// T3 Code shows neither a key-cap bar nor minute separators: the keys still work
			c.setShowHints(false);
			c.setShowSeparators(false);
			c.setColumn(T3UiLayout.COLUMN_MAX); // T3's centred transcript + composer column
			c.setTrailer(new WorkingStatus.Trailer(() -> model.selectedKey() == null ? null : model.find(model.selectedKey())));
			// feat/chat-links: thread links in messages select the thread here
			c.setDirectory(directory);
			c.setThreadLinks(this::openThreadLink);
			if (composer != null) {
				ChatComposer.attach(c, composer);
				if (requestCard != null) {
					c.setFooter(new RequestFooter(requestCard, composer)); // the card takes the composer's place
				}
			}
			conversation = c;
			refresh();
			if (draft != null && draft.is(key)) {
				model.focus(key);
				showRow(draft.row());
			} else if (key != null) {
				open(key);
			}
		}
		layout = T3UiLayout.compute(width, height, showSidebarFlag());
		sr = T3UiLayout.sidebar(layout.sidebar());
		mr = T3UiLayout.main(layout.main(), 0); // the composer is the conversation's footer

		search = null;
		if (sr.search().w() > 0) {
			Rect s = sr.search();
			EditBox box = new EditBox(font, s.x() + 14, s.y() + 3, s.w() - 18, s.h() - 4, null, Component.literal("Search"));
			box.setBordered(false);
			box.setTextColor(theme.text());
			box.setHint(Component.literal("Search").withColor(theme.ghost()));
			box.setValue(model.query());
			box.setResponder(q -> {
				model.setQuery(q);
				sidebarView.scrollToTop();
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
		if (pages.active() && layout.main().w() > 0) {
			pages.layout(layout.main(), font, theme, this::addRenderableWidget);
			for (var child : children()) {
				if (child instanceof AbstractWidget pageBox && pageBox.isFocused()) {
					setFocused(pageBox); // rebuildWidgets() dropped the screen's focus; the new box kept its own flag
				}
			}
		}
	}

	// ---- kit pages (settings, environments) ---------------------------------------------------------

	/** Shows {@code page} in the thread panel's place (compact: the sidebar gives way to it). */
	private void openPage(T3Panel page) {
		menuOpen = false;
		projectMenuOpen = false;
		contextItems = null;
		notifications = false;
		if (composer != null) {
			composer.setFocused(false); // its keys must not swallow the page's
		}
		if (requestCard != null) {
			requestCard.blurOther();
		}
		if (layout.compact() && layout.sidebar().w() > 0) {
			sidebarVisible = false;
		}
		pages.open(page);
	}

	private void openSettingsPage() {
		openPage(new SettingsPanel(EnvironmentActions.DEFAULT));
	}

	private void openEnvironmentsPage() {
		openPage(new EnvironmentsPanel(EnvironmentActions.DEFAULT));
	}

	/** The piece browser (feat/piece-browser-ui) in the thread panel's place, from the ⋯ menu. */
	private void openPiecesPage() {
		openPage(new dev.t3craft.client.pieces.PieceBrowserPanel());
	}

	/** A thread or a new draft was asked for by the user: the page makes way. */
	private void closePages() {
		if (pages.active()) {
			pages.close();
			rebuildWidgets();
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
		if (composer != null) {
			composer.state().flush(); // feat/t3-composer: the text survives closing the screen (and the game)
			composer.setFocused(false);
		}
		if (draftControls != null) {
			draftControls.closeAll();
		}
		if (requestCard != null) {
			requestCard.blurOther();
		}
		if (draft != null) {
			draft.wasSelected = draft.is(model.selectedKey());
			ThreadDraft.kept = draft;
		}
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
		syncTheme();
		pages.tick();
		refresh();
		if (conversation != null) {
			conversation.tick();
		}
		ticks++;
	}

	/** F3+T reloaded {@code gui/ui-style.json} (#12): hand the new tokens to the parts that hold one and re-lay the screen. */
	private void syncTheme() {
		ChatTheme current = ChatTheme.warmStudio();
		if (current == theme) {
			return;
		}
		theme = current;
		if (conversation != null) {
			conversation.setTheme(current);
		}
		if (composer != null) {
			composer.setTheme(current);
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	private void refresh() {
		List<ThreadRow> listed = directory.rows();
		if (pendingDraft != null && !readOnly() && !ActionsClient.projects().all().isEmpty()) {
			String[] pd = pendingDraft;
			pendingDraft = null;
			ProjectsService.Project p = slots.project(pd[0], pd[1]);
			openDraft(p != null ? p : slots.draftProject(pd[0], null, null));
		}
		if (awaitKey != null && listed.stream().anyMatch(r -> r.key().equals(awaitKey))) {
			String k = awaitKey;
			awaitKey = null;
			lastRows = null;
			model.update(withDraft(listed));
			if (k.equals(model.selectedKey()) || model.selectedKey() == null) {
				open(k);
			}
		}
		List<ThreadRow> rows = withDraft(listed);
		// the directory hands back the same list while nothing changed: no rebuild per tick
		if (rows != lastRows && !rows.equals(lastRows)) {
			lastRows = rows;
			model.update(rows);
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
		if (!prewarmed && !listed.isEmpty() && conversation != null) {
			prewarmed = true;
			prewarmOnOpen(listed);
		}
		ConversationPanel c = conversation;
		if (c != null && c.key() == null && model.selected() == null && key == null && draft == null) {
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

	/**
	 * A {@code t3-thread://} link clicked in the conversation (feat/chat-links): the listed thread is
	 * selected; one the list does not have is asked for in its environment, when that is known.
	 */
	private void openThreadLink(Links.ThreadRef ref, @Nullable ThreadRow row) {
		String k = null;
		if (row != null && row.threadId() != null) {
			k = row.key();
		} else {
			String env = ConversationPanel.unlistedEnv(directory, ref);
			if (env != null) {
				k = env + ":" + ref.threadId();
				fallbackTitle = "";
				fallbackEnvLabel = "";
			}
		}
		if (k == null || k.equals(model.selectedKey())) {
			return;
		}
		if (layout.compact() && sidebarVisible) {
			setSidebarVisible(false);
		}
		open(k);
	}

	private void showRow(ThreadRow r) {
		key = r.key();
		ConversationPanel c = conversation;
		if (c == null) {
			return;
		}
		if (draft != null && draft.is(r.key())) {
			c.showNothing(""); // a draft: empty transcript, the composer launches it
			if (composer != null) {
				composer.setFocused(true);
			}
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
		sidebarView.reveal(model.selectedKey());
	}

	private T3Slots.@Nullable Target selectedTarget() {
		String k = model.selectedKey();
		if (k == null || draft != null && draft.is(k)) {
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
		if (pages.active()) {
			pages.renderTop(g, font, theme, mouseX, mouseY, new Rect(0, 0, width, height));
		}
		if (anyMenuOpen()) {
			g.nextStratum(); // menus over the text drawn below them
		}
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
			List<String> tip = sidebarView.tooltip(mouseX, mouseY);
			for (int i = hits.size() - 1; tip == null && i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.tooltip() != null && h.r().contains(mouseX, mouseY)) {
					tip = h.tooltip();
				}
			}
			if (tip != null) {
				g.setTooltipForNextFrame(font, tip.stream().map(s -> Component.literal(s).getVisualOrderText()).toList(), mouseX, mouseY);
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
		pillIcon(g, menu, T3Icon.ELLIPSIS, menuOpen, mouseX, mouseY);
		hits.add(new Hit(menu, () -> {
			menuOpen = !menuOpen;
			projectMenuOpen = false;
		}, null, List.of("Menu: archive, environments, settings, game shortcuts")));

		// search row (T3's: search, then the filter and compose icons); the EditBox is drawn over this by super
		Rect sf = sr.search();
		ChatDraw.field(g, t, sf, focus == T3UiKeys.Focus.SEARCH);
		int glass = Math.min(10, sf.h() - 4);
		T3Icon.draw(g, t, T3Icon.SEARCH, sf.x() + 3, sf.y() + (sf.h() - glass) / 2, glass, t.muted());

		// project filter (T3's project scope menu), lit while a project is chosen
		Rect p = sr.project();
		iconButton(g, p, T3Icon.CHEVRON_DOWN, mouseX, mouseY);
		if (model.project() != null) {
			g.fill(p.x() + 3, p.bottom() - 2, p.right() - 3, p.bottom() - 1, t.accent());
		}
		hits.add(new Hit(p, () -> {
			projectMenuOpen = !projectMenuOpen;
			menuOpen = false;
		}, () -> model.cycleProject(1), List.of("Filter by project · " + model.projectLabel(), "Click: choose · ←/→: previous/next")));

		// new thread: a draft row under the project, no window (T3's compose icon)
		Rect n = sr.newThread();
		iconButton(g, n, T3Icon.SQUARE_PEN, mouseX, mouseY);
		hits.add(new Hit(n, this::newThread, null, List.of("New thread (Ctrl+N)")));

		drawList(g, mouseX, mouseY);

		// footer: discreet icons, as T3 Code's sidebar bottom row (settings · environments · archive)
		Rect f = sr.footer();
		T3Icon[] icons = {T3Icon.SETTINGS, T3Icon.CLOUD, T3Icon.ARCHIVE};
		String[] tips = {"Settings", "Environments", "Archive"};
		Runnable[] acts = {this::openSettingsPage, this::openEnvironmentsPage, this::openArchive};
		int ix = f.x();
		for (int i = 0; i < icons.length; i++) {
			Rect c = new Rect(ix, f.y(), 14, f.h());
			iconButton(g, c, icons[i], mouseX, mouseY);
			hits.add(new Hit(c, acts[i], null, List.of(tips[i])));
			ix += 16;
		}
	}

	/**
	 * A borderless icon that lights up on hover (T3's ghost icon buttons, {@link dev.t3craft.client.t3ui.kit.T3IconButton}'s
	 * look): muted at rest, hover wash and accent under the pointer. {@link T3Icon#SIZE}, smaller only when the
	 * rectangle is (the sidebar footer is 12 px high). The rectangle stays the hit box.
	 */
	private void iconButton(GuiGraphicsExtractor g, Rect r, T3Icon icon, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean hover = r.contains(mouseX, mouseY) && !anyMenuOpen();
		if (hover) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.hover());
		}
		int size = Math.min(T3Icon.SIZE, Math.min(r.w(), r.h()));
		T3Icon.draw(g, t, icon, r.x() + (r.w() - size) / 2, r.y() + (r.h() - size) / 2, size, hover, true);
	}

	private boolean anyMenuOpen() {
		return menuOpen || projectMenuOpen || contextItems != null;
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		sidebarView.render(g, font, theme, sr.list(), mouseX, mouseY);
	}

	/** What the sidebar list asks of the screen (open, prewarm, quick actions, context menu). */
	private final class SidebarHost implements SidebarView.Host {
		@Override
		public boolean menuOpen() {
			return anyMenuOpen();
		}

		@Override
		public void open(ThreadRow r) {
			closePages();
			model.select(r.key());
			showRow(r);
			focus = T3UiKeys.Focus.LIST;
			if (layout.compact()) {
				setSidebarVisible(false);
			}
		}

		@Override
		public void hover(ThreadRow r, boolean selected) {
			hoverPrewarm(r, selected);
		}

		@Override
		public T3Slots.@Nullable Target target(ThreadRow r) {
			return actions == null ? null : T3ThreadActions.target(r);
		}

		@Override
		public boolean canOperate(T3Slots.Target t) {
			return T3ThreadActions.canOperate(t);
		}

		@Override
		public void settle(T3Slots.Target t) {
			T3ThreadActions.settle(T3Screen.this, t);
		}

		@Override
		public void pin(T3Slots.Target t, boolean pin) {
			T3ThreadActions.pin(T3Screen.this, t, pin);
		}

		@Override
		public void context(ThreadRow r, T3Slots.Target t) {
			openContextMenu(r, t);
		}

		@Override
		public @Nullable String draftKey() {
			ThreadDraft d = draft;
			return d == null ? null : d.key();
		}

		@Override
		public void discardDraft() {
			T3Screen.this.discardDraft();
		}

		@Override
		public boolean canNewThread() {
			return !readOnly();
		}

		@Override
		public void newThreadIn(String folder) {
			openDraft(slots.draftProject(null, folder, null));
		}
	}

	private void drawMain(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ChatTheme t = theme;
		if (pages.active()) {
			pages.render(g, font, t, mouseX, mouseY, new Rect(0, 0, width, height));
			return;
		}
		// header panel behind the breadcrumb (T3's ChatHeader: project › thread, state)
		Rect head = new Rect(layout.main().x(), layout.main().y(), layout.main().w(), mr.title().bottom() + 2 - layout.main().y());
		ChatDraw.panel(g, t, head);
		Rect tr = mr.title();
		int x = tr.x();
		if (layout.compact()) {
			// back to the list
			Rect back = new Rect(x, tr.y(), 14, tr.h());
			pillIcon(g, back, T3Icon.CHEVRON_LEFT, false, mouseX, mouseY);
			hits.add(new Hit(back, () -> setSidebarVisible(true), null, List.of("Threads (Esc)")));
			x += 18;
		} else if (!wideSidebar) {
			Rect show = new Rect(x, tr.y(), 14, tr.h());
			pillIcon(g, show, T3Icon.PANEL_LEFT, false, mouseX, mouseY);
			hits.add(new Hit(show, () -> setSidebarVisible(true), null, List.of("Show the sidebar (Ctrl+B)")));
			x += 18;
		}
		ThreadDraft d = selectedDraft();
		if (d != null) {
			drawDraftHeader(g, d, tr, x, mouseX, mouseY);
		}
		T3Slots.Target target = selectedTarget();
		ThreadRow row = model.selectedKey() == null || d != null ? null : model.find(model.selectedKey());
		int right = tr.right();
		// state chip (feat/t3-actions ThreadStatusChip); feat/t3-composer: no "■ Stop" next to it any more,
		// the composer's round button interrupts and the row's right-click menu stops the thread
		if (row != null) {
			right -= slots.drawStatusChip(g, font, t, row, right, tr.y() + 1, mouseX, mouseY) + 4;
			right -= WorkingStatus.drawHeader(g, font, t, row, right, tr.y() + 1);
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
			// feat/thread-lineage: a parent lists its delegated children (state, click to open).
			// feat/ui-hq-menu-lineage: the only place the lineage shows (not the sidebar).
			// fix/chat-send-agents-menu: the chip counts the active children (idle, working, waiting,
			// needs input); with none active but some finished it reads "0/N agents"
			List<ThreadRow> kids = row == null ? List.of() : model.liveChildrenOf(row.key());
			List<ThreadRow> finished = row == null ? List.of() : model.finishedChildrenOf(row.key());
			if (!kids.isEmpty() || !finished.isEmpty()) {
				String label = kids.isEmpty() ? "0/" + finished.size() + (finished.size() == 1 ? " agent" : " agents")
						: kids.size() + (kids.size() == 1 ? " agent" : " agents");
				int w = font.width(label) + 10 + CHIP_ICON + 2;
				if (right - w > x + 60) {
					Rect chipR = new Rect(right - w, tr.y(), w, tr.h());
					pillLabelIcon(g, chipR, label, T3Icon.CHEVRON_DOWN, false, mouseX, mouseY);
					List<String> tip = new ArrayList<>();
					tip.add("Delegated agents");
					for (ThreadRow k : kids) {
						tip.add(childLine(k));
					}
					if (!finished.isEmpty()) {
						tip.add(finished.size() + " finished");
					}
					tip.add("Click: open one");
					String parentKey = row.key();
					hits.add(new Hit(chipR, () -> openChildrenMenu(parentKey, chipR), null, tip));
					right = chipR.x() - 4;
				}
			}
			dev.t3craft.client.chat.RowLineage lin = row == null ? dev.t3craft.client.chat.RowLineage.NONE : row.lineage();
			if (lin.delegated() && lin.hasParent()) {
				// feat/thread-lineage: a delegated child's breadcrumb is "parent › child", the parent clickable
				String pt = lin.parentTitle().isBlank() ? "parent" : lin.parentTitle();
				String parentKey = lin.parentKey();
				String crumb = "↳ " + pt;
				int pw = Math.min(font.width(crumb), (right - x) / 2);
				Rect pr = new Rect(x, tr.y(), pw, tr.h());
				boolean hov = parentKey != null && pr.contains(mouseX, mouseY);
				g.text(font, ChatDraw.clip(font, crumb, pw), x, tr.y() + 3, hov ? t.accent() : t.muted(), t.shadow());
				if (parentKey != null) {
					hits.add(new Hit(pr, () -> open(parentKey), null, List.of("Open the parent thread", pt)));
				}
				x += pw;
				String sep = " › ";
				g.text(font, sep, x, tr.y() + 3, t.muted(), t.shadow());
				x += font.width(sep);
			} else if (!project.isEmpty()) {
				String crumb = project + " › ";
				int pw = Math.min(font.width(crumb), (right - x) / 3);
				g.text(font, ChatDraw.clip(font, crumb, pw), x, tr.y() + 3, t.muted(), t.shadow());
				x += pw;
			}
			g.text(font, ChatDraw.clip(font, title, right - x), x, tr.y() + 3, t.text(), t.shadow());
		} else if (d == null) {
			g.text(font, notifications ? "Notifications" : "No thread selected", x, tr.y() + 3, t.muted(), t.shadow());
		}

		if (notifications) {
			drawNotifications(g, mr.conversation(), mouseX, mouseY);
		} else if (conversation != null) {
			ComposerView cv = composer;
			if (cv != null) {
				if (d != null) {
					ThreadDraft dd = d;
					cv.setDraft(d.envId, d.id, "New thread", (text, images, done) -> DraftLauncher.launch(this, dd, text, images, this::launched, done));
					cv.observe(List.of(), false, null);
					cv.setLeftControls(draftControls == null ? List.of() : draftControls.controls());
				} else {
					ChatComposer.sync(conversation, cv);
					cv.setLeftControls(List.of());
				}
				syncRequestCard(d != null);
				cv.setBounds(Rect.EMPTY); // the conversation places it
			}
			conversation.setBounds(mr.conversation());
			conversation.render(g, font, mouseX, mouseY);
			if (cv != null && cv.bounds().w() == 0) {
				cv.setFocused(false);
			}
		}
	}

	/** One child in the header list: state glyph, title, state word (feat/thread-lineage). */
	private static String childLine(ThreadRow k) {
		String state = SidebarModel.working(k) ? "working" : k.settled() && k.state() != dev.t3craft.t3.ThreadState.ERROR ? "settled" : k.attention().word;
		return k.state().glyph + " " + k.title() + " · " + state;
	}

	/**
	 * The header's children popup (fix/chat-send-agents-menu): a filter row (active / all), the
	 * active children (idle, working, waiting, needs input), then the finished ones (done, settled,
	 * error) under their own header. With the "active" filter the finished section is folded behind
	 * a "▸ Finished (N)" row (one click unfolds it, dimmed); with "all" it is always shown. Choosing a
	 * child opens that thread; the filter and fold rows re-open the menu with the new choice.
	 */
	private void openChildrenMenu(String parentKey, Rect anchor) {
		menuOpen = false;
		projectMenuOpen = false;
		List<ThreadRow> kids = model.liveChildrenOf(parentKey);
		List<ThreadRow> finished = model.finishedChildrenOf(parentKey);
		List<T3Slots.MenuItem> items = new ArrayList<>();
		itemIcons.clear();
		String filter = agentsShowAll ? "Show: ○ Active  ● All" : "Show: ● Active  ○ All";
		items.add(new T3Slots.MenuItem(filter, true, () -> {
			agentsShowAll = !agentsShowAll;
			openChildrenMenu(parentKey, anchor);
		}, true));
		if (kids.isEmpty()) {
			items.add(new T3Slots.MenuItem("No active agents", false, () -> {
			}));
		}
		for (ThreadRow k : kids) {
			String key = k.key();
			items.add(new T3Slots.MenuItem(childLine(k), true, () -> open(key)));
		}
		if (!finished.isEmpty()) {
			boolean shown = agentsShowAll || agentsFinishedOpen;
			if (agentsShowAll) {
				items.add(new T3Slots.MenuItem("── Finished (" + finished.size() + ") ──", false, () -> {
				}));
			} else {
				T3Slots.MenuItem fold = new T3Slots.MenuItem("Finished (" + finished.size() + ")", true, () -> {
					agentsFinishedOpen = !agentsFinishedOpen;
					openChildrenMenu(parentKey, anchor);
				}, true);
				items.add(fold);
				itemIcons.put(fold, shown ? T3Icon.CHEVRON_DOWN : T3Icon.CHEVRON_RIGHT); // #27: drawn, not "▾ " / "▸ "
			}
			if (shown) {
				for (ThreadRow k : finished) {
					String key = k.key();
					items.add(new T3Slots.MenuItem("  " + childLine(k), true, () -> open(key), !agentsShowAll));
				}
			}
		}
		contextItems = items;
		contextX = anchor.x();
		contextY = anchor.bottom() + 1;
	}

	/**
	 * feat/t3-user-input: points the request card at the thread the conversation shows and its
	 * pending requests (from the live stream's projection: runtime requests + their turn items).
	 */
	private void syncRequestCard(boolean draftSelected) {
		RequestCard card = requestCard;
		ConversationPanel c = conversation;
		if (card == null) {
			return;
		}
		String k = c == null || draftSelected ? null : c.key();
		int colon = k == null ? -1 : k.indexOf(':');
		if (c == null || colon <= 0 || colon == k.length() - 1) {
			card.clear();
			return;
		}
		var feed = c.feed();
		List<PendingRequest> pending = feed == null ? List.of() : feed.transcript().pendingRequests();
		T3Slots.Target t = selectedTarget();
		card.setThread(k.substring(0, colon), k.substring(colon + 1), t == null ? null : t.title(), pending);
		card.setMaxHeight(mr.conversation().h() * 3 / 5);
		if (card.expanded() && composer != null && composer.focused()) {
			composer.setFocused(false);
		}
	}

	/** "project › New thread" and an x icon that discards the draft (T3's draft header). */
	private void drawDraftHeader(GuiGraphicsExtractor g, ThreadDraft d, Rect tr, int x, int mouseX, int mouseY) {
		ChatTheme t = theme;
		Rect close = new Rect(tr.right() - 14, tr.y(), 14, tr.h());
		iconButton(g, close, T3Icon.X, mouseX, mouseY);
		hits.add(new Hit(close, this::discardDraft, null, List.of("Discard the draft (Esc)")));
		int right = close.x() - 4;
		ChatDraw.envIcon(g, font, EnvBadge.of(d.envId, d.envLabel), x, tr.y() + 2, 10);
		x += 14;
		String crumb = d.projectLabel + " › ";
		int pw = Math.min(font.width(crumb), (right - x) / 2);
		g.text(font, ChatDraw.clip(font, crumb, pw), x, tr.y() + 3, t.muted(), t.shadow());
		x += pw;
		g.text(font, ChatDraw.clip(font, d.launching ? "Starting…" : "New thread", right - x), x, tr.y() + 3, t.text(), t.shadow());
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
		int bw = Math.max(anchor.w(), 140);
		Rect box = new Rect(Math.max(2, Math.min(anchor.right() - bw, width - bw - 2)), anchor.bottom() + 1, bw, shown * T3UiLayout.ROW_H + 4);
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
				sidebarView.scrollToTop();
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
			closePages();
			notifications = true;
			if (layout.compact()) {
				setSidebarVisible(false);
			}
		}));
		items.add(new T3Slots.MenuItem("New thread", true, this::newThread));
		slots.menuItems(this, selectedTarget(), items);
		items.add(new T3Slots.MenuItem("Archive", true, this::openArchive));
		items.add(new T3Slots.MenuItem("Environments", true, this::openEnvironmentsPage));
		items.add(new T3Slots.MenuItem("Pieces", true, this::openPiecesPage));
		items.add(new T3Slots.MenuItem("Settings", true, this::openSettingsPage));
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
			w = Math.max(w, font.width(it.label()) + 12 + (itemIcons.containsKey(it) ? MENU_ICON + 3 : 0));
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
			int color = !it.enabled() ? t.disabled() : it.dim() ? t.muted() : t.text();
			T3Icon icon = itemIcons.get(it);
			int tx = rr.x() + 3;
			if (icon != null) {
				T3Icon.draw(g, t, icon, tx, rr.y() + (rr.h() - MENU_ICON) / 2, MENU_ICON, color);
				tx += MENU_ICON + 3;
			}
			g.text(font, ChatDraw.clip(font, it.label(), rr.right() - 3 - tx), tx, ry + 2, color, t.shadow());
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
		itemIcons.clear();
		contextItems = T3ThreadActions.menu(this, r, target);
		contextX = (int) clickX;
		contextY = (int) clickY;
	}

	/** Icon size inside a popup menu row ({@link T3UiLayout#ROW_H} high). */
	private static final int MENU_ICON = 8;
	/** Chevron size inside a header chip ("2 agents" + chevron). */
	private static final int CHIP_ICON = 8;

	/** The header pill box (raised highlight, 1 px rule, accent rule when on); returns whether it is lit (hover or on). */
	private boolean pillBox(GuiGraphicsExtractor g, Rect r, boolean on, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean hover = r.contains(mouseX, mouseY);
		ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), on ? t.selected() : hover ? t.hover() : t.panelHi());
		ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), on ? t.accent() : t.edge());
		return hover || on;
	}

	/** A pill with an icon only (the menu, back and show-sidebar buttons): muted, accent when lit. */
	private void pillIcon(GuiGraphicsExtractor g, Rect r, T3Icon icon, boolean on, int mouseX, int mouseY) {
		boolean lit = pillBox(g, r, on, mouseX, mouseY);
		int size = Math.max(4, Math.min(T3Icon.SIZE, Math.min(r.w(), r.h()) - 2));
		T3Icon.draw(g, theme, icon, r.x() + (r.w() - size) / 2, r.y() + (r.h() - size) / 2, size, lit, true);
	}

	/** A pill with a label and a small icon after it (the header's agents chip and its chevron). */
	private void pillLabelIcon(GuiGraphicsExtractor g, Rect r, String label, T3Icon icon, boolean on, int mouseX, int mouseY) {
		ChatTheme t = theme;
		boolean lit = pillBox(g, r, on, mouseX, mouseY);
		int color = lit ? t.accent() : t.keycapText();
		String s = ChatDraw.clip(font, label, r.w() - 10 - CHIP_ICON - 2);
		int x = r.x() + (r.w() - font.width(s) - 2 - CHIP_ICON) / 2;
		g.text(font, s, x, r.y() + (r.h() - 8) / 2, color, false);
		T3Icon.draw(g, t, icon, x + font.width(s) + 2, r.y() + (r.h() - CHIP_ICON) / 2, CHIP_ICON, lit ? t.accent() : t.muted());
	}

	// ---- actions ------------------------------------------------------------------------------

	private void newThread() {
		if (readOnly()) {
			return;
		}
		closePages();
		T3Slots.Target target = selectedTarget();
		ThreadDraft d = selectedDraft();
		openDraft(slots.draftProject(target != null ? target.envId() : d != null ? d.envId : null, model.project(), target));
	}

	// ---- draft thread (feat/t3-composer) ---------------------------------------------------------

	private @Nullable ThreadDraft selectedDraft() {
		ThreadDraft d = draft;
		return d != null && d.is(model.selectedKey()) && !notifications ? d : null;
	}

	/** The rows plus the draft's row (under its project, first in the folder). */
	private List<ThreadRow> withDraft(List<ThreadRow> rows) {
		ThreadDraft d = draft;
		if (d == null) {
			return rows;
		}
		List<ThreadRow> out = new ArrayList<>(rows.size() + 1);
		out.add(d.row());
		out.addAll(rows);
		return out;
	}

	/** "New thread": a draft row under {@code p}, selected, composer focused. No window. */
	private void openDraft(ProjectsService.@Nullable Project p) {
		if (p == null) {
			T3ThreadActions.notice("No project to start a thread in: register one (⋯ › Register project…) or pair an environment.");
			return;
		}
		String back = draft != null && draft.is(model.selectedKey()) ? draft.returnKey : model.selectedKey();
		if (draft == null) {
			draft = new ThreadDraft(p);
		} else if (!draft.envId.equals(p.envId()) || !draft.projectId.equals(p.projectId())) {
			moveDraft(p); // one draft at a time: it moves to the project asked for, its text kept
		}
		draft.returnKey = back;
		notifications = false;
		lastRows = null;
		refresh();
		model.focus(draft.key());
		ThreadRow r = model.find(draft.key());
		if (r != null) {
			showRow(r);
		}
		revealSelected();
		if (layout.compact()) {
			setSidebarVisible(false);
		}
		focusComposer(true);
	}

	/** The draft's project chip picked another project (maybe on another environment). */
	private void moveDraft(ProjectsService.Project p) {
		ThreadDraft d = draft;
		if (d == null) {
			return;
		}
		boolean selected = d.is(model.selectedKey());
		String oldKey = d.key();
		d.setProject(p);
		if (composer != null) {
			if (composer.state().key() != null && composer.state().key().equals(oldKey)) {
				composer.state().rekeyDraft(d.envId, d.id);
			} else {
				composer.state().moveStored(oldKey, d.key());
			}
		}
		lastRows = null;
		refresh();
		if (selected) {
			model.select(d.key());
			key = d.key();
			revealSelected();
		}
	}

	/** Esc / the x: drops the draft and goes back to the thread shown before. */
	private void discardDraft() {
		ThreadDraft d = draft;
		if (d == null) {
			return;
		}
		boolean selected = d.is(model.selectedKey());
		draft = null;
		if (draftControls != null) {
			draftControls.closeAll();
		}
		if (composer != null) {
			composer.state().discard(d.key());
			composer.setFocused(false);
		}
		lastRows = null;
		refresh();
		if (selected) {
			String back = d.returnKey;
			if (back != null && model.find(back) != null) {
				open(back);
			} else {
				model.select(null);
				key = null;
				if (model.move(1) && model.selected() != null) {
					showRow(model.selected());
				} else if (conversation != null) {
					conversation.showNothing("Select a thread.");
				}
			}
		}
	}

	/** The draft's thread exists: it replaces the draft and stays selected. */
	private void launched(String envId, String threadId) {
		ThreadDraft d = draft;
		if (d != null && composer != null) {
			// what was written while it launched follows it (queued after the first turn)
			composer.state().carryDraft(d.key(), envId, threadId);
			composer.state().discard(d.key());
		}
		draft = null;
		if (draftControls != null) {
			draftControls.closeAll();
		}
		String k = envId + ":" + threadId;
		awaitKey = k;
		lastRows = null;
		refresh();
		open(k);
		if (conversation != null) {
			conversation.scrollToEnd();
		}
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
		if (pages.modalOpen()) {
			return pages.mouseClicked(mx, my, button); // a confirmation over the page takes every click
		}
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
		if (pages.active() && (layout.main().contains(mx, my) || pages.overlayOpen())) {
			if (search != null && search.isFocused()) {
				search.setFocused(false);
				setFocused(null);
				focus = T3UiKeys.Focus.LIST;
			}
			// the page's own controls first, then its text boxes (vanilla widgets)
			return pages.mouseClicked(mx, my, button) || super.mouseClicked(event, doubleClick);
		}
		if (requestCard != null && !requestCard.contains(mx, my)) {
			requestCard.blurOther();
		} else if (requestCard != null && search != null) {
			search.setFocused(false); // the conversation forwards the click to the card
			setFocused(null);
			focus = T3UiKeys.Focus.LIST;
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
		if (sidebarView.mouseClicked(mx, my, button)) {
			return true;
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
		if (pages.active() && pages.mouseDragged(event.x(), event.y(), event.button())) {
			return true; // a scrollbar thumb of the kit page
		}
		return (composer != null && composer.mouseDragged(event.x(), event.y())) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pages.active() && pages.mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return (composer != null && composer.mouseReleased()) || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.mouseScrolled(x, y, scrollY)) {
				return true;
			}
		}
		if (pages.modalOpen() || pages.active() && layout.main().contains(x, y)) {
			pages.mouseScrolled(x, y, scrollY);
			return true;
		}
		if (anyMenuOpen()) {
			return true;
		}
		if (sr.list().contains(x, y) && sidebarView.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		if (requestCard != null && requestCard.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		if (composer != null && composer.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return (conversation != null && !notifications && conversation.mouseScrolled(x, y, scrollY)) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (pages.active() && layout.main().w() > 0 && !anyMenuOpen()) {
			// a kit page: its select / modal / Esc first, then the focused text box; no thread shortcuts apply
			return pages.keyPressed(e) || super.keyPressed(e);
		}
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.keyPressed(e)) {
				return true;
			}
		}
		// feat/t3-user-input: the card's "Other" field owns the keyboard while focused
		if (requestCard != null && requestCard.owns() && requestCard.keyPressed(e.key(), e.hasShiftDown(), e.hasControlDownWithQuirk())) {
			return true;
		}
		// while the composer has focus every key is its own (Esc leaves it): no screen shortcut fires
		if (ComposerInput.keyPressed(composer, e.key(), e.hasShiftDown(), e.hasControlDownWithQuirk())) {
			return true;
		}
		// the open card: 1–9 choose, Enter next / send, ←/→ question, Tab "Other", Esc hides it
		if (requestCard != null && focus != T3UiKeys.Focus.SEARCH && !anyMenuOpen() && !notifications
				&& requestCard.keyPressed(e.key(), e.hasShiftDown(), e.hasControlDownWithQuirk())) {
			return true;
		}
		if (e.isEscape() && !anyMenuOpen() && selectedDraft() != null && focus == T3UiKeys.Focus.LIST) {
			discardDraft(); // T3: Esc leaves a draft thread
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
				sidebarView.scrollToTop();
				revealSelected();
			}
			case NEXT_PROJECT -> {
				model.cycleProject(1);
				sidebarView.scrollToTop();
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
		if (pages.active()) {
			return pages.modalOpen() ? pages.charTyped(event) : super.charTyped(event); // the modal's box, the page's text box, or the sidebar search
		}
		for (T3Slots.Popup p : popups) {
			if (p.isOpen() && p.charTyped(event.codepoint())) {
				return true;
			}
		}
		if (requestCard != null && requestCard.owns()) {
			return requestCard.charTyped(event.codepoint());
		}
		if (ComposerInput.owns(composer)) {
			return ComposerInput.charTyped(composer, event.codepoint());
		}
		if (focus == T3UiKeys.Focus.SEARCH) {
			return super.charTyped(event);
		}
		return false;
	}
}
