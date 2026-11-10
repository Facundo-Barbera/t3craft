package dev.t3craft.client.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.blocks.MergeState;
import dev.t3craft.client.archive.ArchiveEntry;
import dev.t3craft.client.archive.ArchiveList;
import dev.t3craft.client.archive.ArchivePanel;
import dev.t3craft.client.archive.ArchiveScreen;
import dev.t3craft.client.blocks.MergeFeed;
import dev.t3craft.client.blocks.MergeStationPanel;
import dev.t3craft.client.blocks.MergeStationScreen;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.RowLineage;
import dev.t3craft.client.chat.ThreadDetails;
import dev.t3craft.client.chat.ThreadCache;
import dev.t3craft.client.chat.ThreadDirectory;
import dev.t3craft.client.chat.ThreadList;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentEditPanel;
import dev.t3craft.client.config.EnvironmentsPanel;
import dev.t3craft.client.config.OutboundDestinationPanel;
import dev.t3craft.client.config.OutboundPanel;
import dev.t3craft.client.config.SettingsPanel;
import dev.t3craft.client.config.SettingsScreen;
import dev.t3craft.client.demo.HqMenuModel;
import dev.t3craft.client.demo.HqMenuPanel;
import dev.t3craft.client.demo.HqMenuScreen;
import dev.t3craft.client.queue.QueueActions;
import dev.t3craft.client.queue.QueueOps;
import dev.t3craft.client.queue.QueuePanel;
import dev.t3craft.client.queue.QueueScreen;
import dev.t3craft.client.queue.QueueStore;
import dev.t3craft.client.queue.QueuedRun;
import dev.t3craft.client.screen.ConfirmActionScreen;
import dev.t3craft.client.screen.OperatePairScreen;
import dev.t3craft.client.screen.PairScreen;
import dev.t3craft.client.screen.SnoozeScreen;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.client.t3ui.T3Slots;
import dev.t3craft.client.t3ui.actions.RegisterProjectDialog;
import dev.t3craft.client.t3ui.kit.T3KitDemoPanel;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.outbound.Destination;
import dev.t3craft.outbound.OutboundConfig;
import dev.t3craft.outbound.OutboundStore;
import dev.t3craft.outbound.Template;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import dev.t3craft.t3.ThreadOps;
import dev.t3craft.t3.ThreadState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The UI gallery (issue #13): {@code /t3dev ui <scenario>} opens one of the mod's screens with fake data,
 * no network and no T3 needed, so a visual change is one command away after a restart (and F3+T
 * restyles it live, see {@link dev.t3craft.client.chat.UiStyle}). {@code /t3dev ui} lists them.
 *
 * <p>The scenarios are built in code here, not read from the {@code uiRender} fixtures: those live in a
 * tool source set that is not part of the mod jar. The ids are the same family
 * ({@code inbox} is {@code running}, {@code inbox-empty} is {@code empty}, ...); the tool stays the
 * layout check for agents (docs/tools/ui-render.md), this is the real renderer.
 *
 * <p>What is fake: the thread list, the transcripts and the environment ({@link ThreadCache#GALLERY_ENV}).
 * What is real: the screens' own code, the config, and whatever a button does outside the fake
 * environment (the settings screen saves for real; actions aimed at the fake environment fail with the
 * usual error toast). The fake transcripts never reach the disk cache ({@link ThreadCache#recent}).
 */
public final class UiGallery {
	/** One scenario: an id for the command, a line for the list, and a factory (a screen is built per open). */
	public record Scenario(String id, String description, Supplier<Screen> screen) {
	}

	private static final String ENV = ThreadCache.GALLERY_ENV;
	private static final String ENV_LABEL = "Gallery";
	private static final String GALLERY_DESTINATION = "gallery-demo";
	private static @Nullable OutboundStore outbound;

	private static final List<Scenario> SCENARIOS = List.of(
			new Scenario("inbox", "Main T3 screen: every thread state, a running conversation, the composer", () -> inbox(true, true)),
			new Scenario("inbox-readonly", "Same, read-only (no composer, no header actions: how an archived thread opens)", () -> inbox(false, true)),
			new Scenario("inbox-empty", "Main T3 screen with no threads", () -> inbox(true, false)),
			new Scenario("inbox-settings", "Main T3 screen with the settings page in the thread's place (what the sidebar's gear opens)",
					() -> inbox(true, true).withPage(new SettingsPanel(EnvironmentActions.DEFAULT))),
			new Scenario("inbox-environments", "Main T3 screen with the environment list page (fake environments)",
					() -> inbox(true, true).withPage(new EnvironmentsPanel(galleryActions()))),
			new Scenario("confirm", "Kit dialog: confirmation before a destructive action (stop a thread)", UiGallery::confirm),
			new Scenario("snooze", "Kit dialog: snooze presets for one thread", () -> new SnoozeScreen(null, new ThreadOps.Target(ENV, "t-run", "fix/hq-builders-independent"))),
			new Scenario("pair", "Kit dialog: pair an environment (three fields, access switch)", () -> new PairScreen(null)),
			new Scenario("pair-readonly", "Kit dialog: pair, read-only access (no control warning)", () -> new PairScreen(null, true)),
			new Scenario("operate-pair", "Kit dialog: enable control on a paired environment", UiGallery::operatePair),
			new Scenario("launch", "Main T3 screen with a new-thread draft in the composer (project, model and workspace chips; needs a paired environment with projects)",
					() -> inbox(true, true).withDraft(null, null)),
			new Scenario("settings", "T3Craft settings as a screen of its own, General tab (Save writes the real config)",
					() -> new SettingsScreen(null, EnvironmentActions.DEFAULT)),
			new Scenario("settings-notifications", "Settings, Notifications tab (switches and a select)",
					() -> new T3PanelScreen(null, new SettingsPanel(EnvironmentActions.DEFAULT).tab(1))),
			new Scenario("settings-connections", "Settings, Connections tab (links and the dangerous button)",
					() -> new T3PanelScreen(null, new SettingsPanel(EnvironmentActions.DEFAULT).tab(2))),
			new Scenario("environments", "Environment list with fake environments (ok, connecting, offline, one with control)",
					() -> new T3PanelScreen(null, new EnvironmentsPanel(galleryActions()))),
			new Scenario("environment-edit", "Environment edit page (name, accent swatches, switches, window and cap; Save writes the real config under the fake id 'studio')",
					() -> new T3PanelScreen(null, new EnvironmentEditPanel(galleryActions(), galleryEnvs().getFirst()))),
			new Scenario("outbound", "Outbound events page: master switch with its confirmation and a list of fake destinations (a temporary folder, not the real outbound.json)",
					() -> new T3PanelScreen(null, new OutboundPanel(galleryOutbound()))),
			new Scenario("outbound-destination", "One outbound destination page (temporary folder; Send test finds no such destination in the real config)",
					() -> new T3PanelScreen(null, new OutboundDestinationPanel(galleryOutbound(), GALLERY_DESTINATION, () -> {
					}))),
			new Scenario("register-project", "Kit dialog: register a project (environment select needs a paired environment; Register sends nothing without control)",
					() -> new RegisterProjectDialog(null, null, (env, projectId) -> {
					})),
			new Scenario("pieces", "Piece browser as a screen of its own over fake pieces (folders, accents for the folded search, a piece with no size; favorites stay in memory; Preview needs the HQ world)",
					() -> new dev.t3craft.client.pieces.PieceBrowserScreen(null, galleryPieces())),
			new Scenario("pieces-catalog", "Piece browser over the real code catalog (favorites are the real file)",
					() -> new dev.t3craft.client.pieces.PieceBrowserScreen(null)),
			new Scenario("inbox-pieces", "Main T3 screen with the piece browser (fake pieces) in the thread's place (the ⋯ menu → Pieces)",
					() -> inbox(true, true).withPage(galleryPieces())),
			new Scenario("icons", "Icon gallery: every T3Icon (Lucide) in a grid on both themes, 12 px in its three states and 32 px; wheel scrolls",
					() -> new IconGalleryScreen(null)),
			new Scenario("inbox-icons", "Main T3 screen with every sidebar icon in use (#27): pinned stars, folder and dock chevrons, Snoozed and Settled, a worktree row, the header's agents chip with active and finished children; hover a row for check / star, a folder for plus",
					UiGallery::inboxIcons),
			new Scenario("model-picker", "The composer's model picker open over a fake catalog (reasoning and extra selects, switches, access, star favorites, legacy, many providers; brain / shield icons, chip chevron)",
					ModelPickerGalleryScreen::new),
			new Scenario("composer-icons", "The chat composer with its icons: paperclip, image thumbnails (hover for the remove x), error line, model chip with chevron, send button; nothing is sent",
					ComposerIconsGalleryScreen::new),
			new Scenario("kit", "UI kit demo as a screen of its own: Tab / Enter / Space focus, list keys, draggable scrollbars, fields clipped in a scrolling page, a dialog with fields",
					() -> new T3PanelScreen(null, new T3KitDemoPanel())),
			new Scenario("inbox-kit", "The same UI kit demo inside the main T3 screen (the dialog with fields and the scrollbar drag go through T3Screen)",
					() -> inbox(true, true).withPage(new T3KitDemoPanel())),
			new Scenario("queue", "Run queue page with fake queues (two threads, held and automatic runs, one environment read-only; buttons answer locally)",
					() -> new QueueScreen(null, new QueuePanel(galleryQueue()))),
			new Scenario("archive", "Archive page with fake closed threads (search, environment and project selects, Older / Refresh)",
					() -> new ArchiveScreen(null, new ArchivePanel(galleryArchive(), e -> Minecraft.getInstance().gui.setScreen(null), new ArchiveList()))),
			new Scenario("hq-menu", "HQ menu page (the title screen's T3Craft HQ) over the real layouts, a fake built HQ; Open, Rebuild and Reset do nothing",
					UiGallery::hqMenu),
			new Scenario("merge-station", "Merge station page with fake pull requests (open, draft, one without a link); Open copies or opens the fake link",
					() -> new MergeStationScreen(null, new MergeStationPanel("t3craft (Gallery)", UiGallery::galleryMerge, e -> {
					}))),
			new Scenario("inbox-right-panel", "Main T3 screen with the right panel open on the piece browser (fake pieces, narrow layout); drag either border, double click resets, U or the header's panel icon toggles (#39; drags save the real layout block)",
					() -> inbox(true, true).withRightPanel(T3Screen.PIECES_PANEL, galleryPieces())));

	private UiGallery() {
	}

	public static List<Scenario> scenarios() {
		return SCENARIOS;
	}

	public static List<String> ids() {
		return SCENARIOS.stream().map(Scenario::id).toList();
	}

	/** A new screen for {@code id}, or null when there is no such scenario. */
	public static @Nullable Screen open(String id) {
		for (Scenario s : SCENARIOS) {
			if (s.id().equals(id)) {
				return s.screen().get();
			}
		}
		return null;
	}

	// ---- scenarios -------------------------------------------------------------------------------------

	private static Screen confirm() {
		OperateAction stop = OperateAction.dispatch(OperateKind.THREAD_STOP, ENV, "t-run", "fix/hq-builders-independent", new JsonObject());
		return new ConfirmActionScreen(null, stop, ENV_LABEL, yes -> Minecraft.getInstance().gui.setScreen(null));
	}

	/** The piece browser over {@link dev.t3craft.client.pieces.GalleryPieces} (issue #30). */
	private static dev.t3craft.client.pieces.PieceBrowserPanel galleryPieces() {
		return dev.t3craft.client.pieces.PieceBrowserPanel.withCatalog(dev.t3craft.client.pieces.GalleryPieces.entries(),
				dev.t3craft.client.pieces.GalleryPieces.LABEL);
	}

	private static List<EnvRow> galleryEnvs() {
		return List.of(
				new EnvRow("studio", "Studio Mac", "http://studio.example.ts.net:3773/", "ok", ""),
				new EnvRow("laptop", "Laptop", "http://my-laptop:3773/", "connecting", ""),
				new EnvRow("vps-env", "VPS", "https://vps.example.ts.net/", "offline", "connection refused"),
				new EnvRow("old", "Old machine", "http://10.0.0.7:3773/", "-", ""));
	}

	/** Seeded once, in a temporary folder: the outbound pages save and delete here, never in the real {@code outbound.json}. */
	private static synchronized OutboundStore galleryOutbound() {
		if (outbound == null) {
			try {
				OutboundStore s = new OutboundStore(Files.createTempDirectory("t3craft-gallery-outbound"));
				Destination phone = Destination.create(GALLERY_DESTINATION, "Phone (push)");
				Destination team = Destination.create("gallery-team", "Team channel").withTemplate(Template.CHAT).withEnabled(false)
						.withPendingOnly(false);
				s.save(new OutboundConfig(true, List.of(phone, team)));
				s.setUrl(GALLERY_DESTINATION, "https://ntfy.example.net/gallery");
				outbound = s;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return outbound;
	}

	/** Four fake paired environments for the list page; every action answers with a line and touches nothing. */
	private static EnvironmentActions galleryActions() {
		return new EnvironmentActions() {
			@Override
			public List<EnvRow> list() {
				return galleryEnvs();
			}

			@Override
			public Screen pairScreen(Screen parent) {
				return new PairScreen(parent);
			}

			@Override
			public String unpair(String id) {
				return "Gallery: would forget " + id;
			}

			@Override
			public String rename(String id, String newLabel) {
				return "Gallery: would rename " + id;
			}

			@Override
			public void reload() {
			}

			@Override
			public boolean operateEnabled(String id) {
				return id.equals("studio");
			}

			@Override
			public String revokeOperate(String id) {
				return "Gallery: would revoke control for " + id;
			}
		};
	}

	// ---- queue, archive, HQ menu, merge station (#26) ---------------------------------------------------

	/** Two fake queues: the gallery environment can operate, "laptop" is read-only. Writes answer locally, nothing is sent. */
	private static QueuePanel.Source galleryQueue() {
		long now = System.currentTimeMillis();
		QueueStore.Key run = QueueStore.Key.of(ENV, "t-run");
		QueueStore.Key other = QueueStore.Key.of("laptop", "t-docs");
		List<QueuedRun> first = List.of(
				new QueuedRun(ENV, "t-run", "run-auto", QueuedRun.QUEUED, 4, 1L, false, now - 30_000, null, true, false, "Delegated task finished: hq-gate npcs"),
				new QueuedRun(ENV, "t-run", "run-a", QueuedRun.QUEUED, 5, 2L, false, now - 20_000, null, false, true, "Also keep the layer order while you split the targets"),
				new QueuedRun(ENV, "t-run", "run-b", QueuedRun.QUEUED, 6, 3L, false, now - 10_000, null, false, true, "Then run the render check on the citadel"),
				new QueuedRun(ENV, "t-run", "run-c", QueuedRun.QUEUED, 7, 4L, false, now - 5_000, null, false, true,
						"A long queued message that does not fit on one row so the preview is clipped at the buttons"));
		List<QueuedRun> second = List.of(
				new QueuedRun("laptop", "t-docs", "run-d", QueuedRun.QUEUED, 2, 1L, true, now - 60_000, null, false, true, "Update the archive docs"),
				new QueuedRun("laptop", "t-docs", "run-e", QueuedRun.QUEUED, 3, 2L, true, now - 50_000, null, false, true, "And the changelog line"));
		List<QueueStore.Snapshot> snaps = List.of(
				new QueueStore.Snapshot(run, QueueStore.Status.OK, null, first),
				new QueueStore.Snapshot(other, QueueStore.Status.OK, null, second),
				new QueueStore.Snapshot(QueueStore.Key.of(ENV, "t-ask"), QueueStore.Status.LOADING, null, List.of()));
		QueueOps ops = new QueueOps(null, QueueActions.create()); // builds actions only; the gate is never used here
		return new QueuePanel.Source() {
			@Override
			public void open() {
			}

			@Override
			public void close() {
			}

			@Override
			public boolean readable() {
				return true;
			}

			@Override
			public String unavailableReason() {
				return "";
			}

			@Override
			public boolean anyWatched() {
				return true;
			}

			@Override
			public List<QueueStore.Snapshot> snapshots() {
				return snaps;
			}

			@Override
			public @Nullable String title(String envId, String threadId) {
				return switch (threadId) {
					case "t-run" -> "fix/hq-builders-independent";
					case "t-docs" -> "docs: archive view";
					case "t-ask" -> "feat/attention-queue";
					default -> null;
				};
			}

			@Override
			public String envLabel(String envId) {
				return envId.equals(ENV) ? ENV_LABEL : "Laptop";
			}

			@Override
			public boolean canOperate(String envId) {
				return envId.equals(ENV);
			}

			@Override
			public QueueOps ops() {
				return ops;
			}

			@Override
			public CompletableFuture<OperateResult> submit(OperateAction action, boolean confirmed) {
				return CompletableFuture.completedFuture(OperateResult.of(OperateResult.Outcome.REJECTED,
						"gallery: would send " + action.kind().wire + (confirmed ? " (confirmed)" : "")));
			}
		};
	}

	/** Fake closed threads in two environments; Older and Refresh only flip a loading flag for a moment. */
	private static ArchivePanel.Data galleryArchive() {
		long day = 86_400_000L;
		long now = System.currentTimeMillis();
		List<ArchiveEntry> entries = new ArrayList<>();
		String[][] rows = {
				{ENV, ENV_LABEL, "t-a1", "Fix login redirect loop", "p-t3", "t3craft"},
				{ENV, ENV_LABEL, "t-a2", "Bump dependencies", "p-t3", "t3craft"},
				{"laptop", "Laptop", "t-a3", "Investigate flaky render test", "p-br", "bridge"},
				{ENV, ENV_LABEL, "t-a4", "HQ gate NPCs wander off", "p-t3", "t3craft"},
				{"laptop", "Laptop", "t-a5", "Migrate settings screen to the kit", "p-br", "bridge"},
				{ENV, ENV_LABEL, "t-a6", "A very long thread title that has to be clipped before the project column starts", "p-t3", "t3craft"},
				{"laptop", "Laptop", "t-a7", "Docs: archive view", "p-docs", "docs"},
		};
		for (int i = 0; i < rows.length; i++) {
			String[] r = rows[i];
			entries.add(new ArchiveEntry(ArchiveEntry.key(r[0], r[2]), r[0], r[1], r[2], r[3], r[4], r[5], now - i * day,
					i % 3 == 1 ? ArchiveEntry.Kind.ARCHIVED : ArchiveEntry.Kind.SETTLED));
		}
		for (int i = 0; i < 18; i++) {
			entries.add(new ArchiveEntry(ArchiveEntry.key(ENV, "t-old" + i), ENV, ENV_LABEL, "t-old" + i, "Older thread " + (i + 1), "p-t3", "t3craft",
					now - (10 + i) * day, ArchiveEntry.Kind.SETTLED));
		}
		List<ArchiveEntry> fixed = List.copyOf(entries);
		return new ArchivePanel.Data() {
			@Override
			public List<ArchiveEntry> entries() {
				return fixed;
			}

			@Override
			public int version() {
				return 1;
			}

			@Override
			public String status() {
				return ENV_LABEL + " 19+ - Laptop 3 (offline)";
			}

			@Override
			public boolean loading() {
				return false;
			}

			@Override
			public boolean canLoadMore() {
				return true;
			}

			@Override
			public boolean canRefresh() {
				return true;
			}

			@Override
			public void loadMore() {
			}

			@Override
			public void refresh() {
			}
		};
	}

	/** The real layout registry, with a fake HQ built with the second layout (so "Rebuild with ..." shows); choices are not saved. */
	private static Screen hqMenu() {
		List<HqLayoutInfo> layouts = HqLayouts.menuOrder();
		String built = layouts.get(Math.min(1, layouts.size() - 1)).id();
		HqMenuModel model = new HqMenuModel(layouts, layouts.getFirst().id(), built, true, id -> {
		});
		return new HqMenuScreen(null, new HqMenuPanel(model, new HqMenuPanel.Actions() {
			@Override
			public void open(Screen parent) {
			}

			@Override
			public @Nullable String refusal() {
				return null;
			}

			@Override
			public String resetBody(HqLayoutInfo layout) {
				return "Gallery: a zip backup would be saved first, then only the HQ world would be created again with "
						+ net.minecraft.network.chat.Component.translatable(layout.nameKey()).getString() + ". Nothing is touched here.";
			}

			@Override
			public @Nullable String resetAndRebuild(String layoutId) {
				return "Gallery: would reset and rebuild with " + layoutId + ".";
			}

			@Override
			public String exportPacks() {
				return "Gallery: would export the layouts.";
			}

			@Override
			public String reloadPacks() {
				return "Gallery: would reload the layouts.";
			}
		}));
	}

	private static MergeFeed.Summary galleryMerge() {
		return new MergeFeed.Summary(MergeState.OPEN, List.of(
				new MergeFeed.Entry(ENV + ":t-run", ENV, "t-run", "fix/hq-builders-independent", "", MergeState.OPEN, 412, "example/t3craft",
						"https://example.com/example/t3craft/pull/412", 120, 34),
				new MergeFeed.Entry(ENV + ":t-ask", ENV, "t-ask", "feat/attention-queue", "", MergeState.OPEN, 415, "example/t3craft", null, 8, 2),
				new MergeFeed.Entry(ENV + ":t-err", ENV, "t-err", "fix/hq-gate-npcs with a long title that gets clipped before the diff size", "",
						MergeState.DRAFT, 417, null, null, 1024, 980),
				new MergeFeed.Entry(ENV + ":t-done", ENV, null, "docs: ui render", "", MergeState.DRAFT, 9, null, null, -1, -1)));
	}

	private static Screen operatePair() {
		return new OperatePairScreen(null, new Environments.Environment(ENV, ENV_LABEL, URI.create("http://127.0.0.1:3773/")));
	}

	/**
	 * #27: the inbox with rows that put every sidebar and header icon on screen: two pinned threads (brass
	 * star), project folders and the Pinned section (chevrons), Working, Snoozed and Settled in the dock,
	 * a worktree row with diff and PR, and a parent with delegated children (the header's agents chip, its
	 * menu with the "Finished" fold). Opens on the parent.
	 */
	private static T3Screen inboxIcons() {
		long now = System.currentTimeMillis();
		String parent = ENV + ":t-parent";
		List<ThreadRow> rows = new ArrayList<>();
		rows.add(row("t-parent", "Coordinator: UI icons wave", "t3craft", ThreadState.WORKING, false, "waiting for children", false, true, now - 30_000));
		rows.add(row("t-run", "fix/hq-builders-independent", "t3craft", ThreadState.WORKING, false, "running tests", false, false, now - 6_000));
		rows.add(row("t-pin2", "Pinned and idle", "bridge", ThreadState.IDLE, false, null, false, true, now - 900_000));
		rows.add(row("t-ask", "feat/attention-queue", "t3craft", ThreadState.NEEDS_ATTENTION, true, null, false, false, now - 120_000));
		rows.add(new ThreadRow(ENV + ":t-wt", ENV, ENV_LABEL, "t-wt", "feat/ui-icons-sidebar", "t3craft", ThreadState.DONE, false, null, false,
				now - 400_000L, false, new ThreadDetails("feat/ui-icons-sidebar-27", true, 27, "open", "example/t3craft", 141, 12, "sonnet", "claude",
						true, null, null)));
		rows.add(new ThreadRow(ENV + ":t-snz", ENV, ENV_LABEL, "t-snz", "Snoozed until later", "bridge", ThreadState.IDLE, false, null, false,
				now - 5_000_000L, false, new ThreadDetails(null, false, 0, null, null, -1, -1, null, null, false, null, now + 3_600_000L)));
		rows.add(row("t-old", "Migrate settings screen", "bridge", ThreadState.DONE, false, null, true, false, now - 3 * 86_400_000L));
		rows.add(child("t-kid1", "Child: sidebar icons", ThreadState.WORKING, false, parent, now - 20_000));
		rows.add(child("t-kid2", "Child: picker icons", ThreadState.IDLE, false, parent, now - 60_000));
		rows.add(child("t-kid3", "Child: queue icons", ThreadState.DONE, false, parent, now - 600_000));
		rows.add(child("t-kid4", "Child: archive icons", ThreadState.DONE, true, parent, now - 900_000));
		return inbox(true, rows, parent);
	}

	private static ThreadRow child(String id, String title, ThreadState state, boolean settled, String parentKey, long activityMs) {
		return new ThreadRow(ENV + ":" + id, ENV, ENV_LABEL, id, title, "t3craft", state, false, null, settled, activityMs, false, ThreadDetails.EMPTY,
				new RowLineage(parentKey, "Coordinator: UI icons wave", true));
	}

	/** The main screen over fake data. {@code operate}: composer and header actions; {@code threads}: false = an empty list. */
	private static T3Screen inbox(boolean operate, boolean threads) {
		long now = System.currentTimeMillis();
		List<ThreadRow> rows = new ArrayList<>();
		if (threads) {
			rows.add(row("t-run", "fix/hq-builders-independent", "t3craft", ThreadState.WORKING, false, "running tests", false, true, now - 6_000));
			rows.add(row("t-ask", "feat/attention-queue", "t3craft", ThreadState.NEEDS_ATTENTION, true, null, false, false, now - 120_000));
			rows.add(row("t-err", "fix/hq-gate-npcs", "t3craft", ThreadState.ERROR, false, null, false, false, now - 600_000));
			rows.add(row("t-done", "docs: ui render", "t3craft", ThreadState.DONE, false, null, false, false, now - 3_600_000));
			rows.add(row("t-idle", "Investigate login redirect", "bridge", ThreadState.IDLE, false, null, false, false, now - 7_200_000));
			rows.add(row("t-old", "Migrate settings screen", "bridge", ThreadState.DONE, false, null, true, false, now - 3 * 86_400_000L));
		}
		return inbox(operate, rows, threads ? ENV + ":t-run" : null);
	}

	/** The main screen over {@code rows}, opened on {@code select} (null = none). */
	private static T3Screen inbox(boolean operate, List<ThreadRow> rows, @Nullable String select) {
		Map<String, ThreadList.EnvInfo> envs = new LinkedHashMap<>();
		envs.put(ENV, new ThreadList.EnvInfo(ENV_LABEL, "ok", null));
		List<ThreadRow> fixed = List.copyOf(rows);
		ThreadDirectory directory = new ThreadDirectory() {
			@Override
			public List<ThreadRow> rows() {
				return fixed;
			}

			@Override
			public Map<String, ThreadList.EnvInfo> environments() {
				return envs;
			}
		};
		ConversationPanel.Actions actions = !operate ? null : new ConversationPanel.Actions() {
			@Override
			public boolean canOperate(String envId) {
				return true;
			}

			@Override
			public void enableControl(String envId) {
			}

			@Override
			public void pair() {
			}
		};
		return new T3Screen(directory, source(), actions, T3Slots.DEFAULT, select);
	}

	private static ThreadRow row(String id, String title, String project, ThreadState state, boolean pending, @Nullable String activity,
			boolean settled, boolean pinned, long activityMs) {
		return new ThreadRow(ENV + ":" + id, ENV, ENV_LABEL, id, title, project, state, pending, activity, settled, activityMs, pinned);
	}

	// ---- fake environment ---------------------------------------------------------------------------------

	/** Answers {@code subscribeThread} at once with the canned conversation; history and turn items are not available. */
	private static ChatSource source() {
		return envId -> new ChatSource.Env() {
			@Override
			public String id() {
				return ENV;
			}

			@Override
			public String label() {
				return ENV_LABEL;
			}

			@Override
			public T3Connection.Status status() {
				return T3Connection.Status.OK;
			}

			@Override
			public String statusDetail() {
				return "";
			}

			@Override
			public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
				JsonObject synced = new JsonObject();
				synced.addProperty("kind", "synchronized");
				listener.onSubscribed();
				listener.onItems(List.of(snapshot(threadId), synced));
				return () -> {
				};
			}

			@Override
			public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
				return CompletableFuture.failedFuture(new IllegalStateException("gallery: no older history"));
			}

			@Override
			public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
				return CompletableFuture.completedFuture(null);
			}
		};
	}

	/** A {@code subscribeThread} snapshot, the shape {@code ChatTranscript} decodes (same as the uiRender fixtures). */
	private static JsonObject snapshot(String threadId) {
		JsonObject snap = new JsonObject();
		snap.addProperty("kind", "snapshot");
		snap.addProperty("snapshotSequence", 100);
		snap.addProperty("hasMoreHistory", false);
		JsonObject projection = new JsonObject();
		JsonObject thread = new JsonObject();
		thread.addProperty("title", "fix/hq-builders-independent");
		projection.add("thread", thread);
		JsonArray runs = new JsonArray();
		JsonObject run = new JsonObject();
		run.addProperty("id", "run-1");
		run.addProperty("ordinal", 1);
		run.addProperty("status", "running");
		runs.add(run);
		projection.add("runs", runs);
		projection.add("runtimeRequests", new JsonArray());
		projection.add("subagents", new JsonArray());

		JsonArray rows = new JsonArray();
		List<JsonObject> items = new ArrayList<>();
		items.add(message("user_message", "Que los builders no esperen unos a otros y construyan tan rápido como antes.", false));
		items.add(message("assistant_message", "Voy a revisar `CrewCursor` y el presupuesto global antes de tocar nada.", false));
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", "command_execution");
		cmd.addProperty("input", "rg -n \"nextTarget\" mod/src");
		cmd.addProperty("output", "CrewCursor.java:88: nextTarget(...)\nCrewSchedule.java:41: nextTarget(...)");
		cmd.addProperty("exitCode", 0);
		items.add(cmd);
		items.add(message("assistant_message", "Encontré el cuello de botella: cada builder espera su turno en la cola global.", false));
		items.add(message("user_message", "Steer: y que no se rompa el orden de las capas, por favor.", false));
		items.add(message("assistant_message", "Entendido, mantengo el orden por capas mientras reparto objetivos…", true));
		int ordinal = 0;
		for (JsonObject item : items) {
			ordinal++;
			item.addProperty("ordinal", ordinal);
			item.addProperty("id", "item-" + ordinal);
			if (!item.has("status")) {
				item.addProperty("status", "completed");
			}
			String type = item.get("type").getAsString();
			if (type.equals("user_message") || type.equals("assistant_message")) {
				item.addProperty("messageId", "msg-item-" + ordinal);
			}
			JsonObject row = new JsonObject();
			row.addProperty("visibility", "own");
			row.addProperty("sourceThreadId", threadId);
			row.add("item", item);
			rows.add(row);
		}
		projection.add("visibleTurnItems", rows);
		snap.add("projection", projection);
		return snap;
	}

	private static JsonObject message(String type, String text, boolean streaming) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.addProperty("text", text);
		if (streaming) {
			o.addProperty("streaming", true);
			o.addProperty("status", "running");
		}
		return o;
	}
}
