package dev.t3craft.client.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.ThreadCache;
import dev.t3craft.client.chat.ThreadDirectory;
import dev.t3craft.client.chat.ThreadList;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.EnvironmentsPanel;
import dev.t3craft.client.config.SettingsPanel;
import dev.t3craft.client.config.SettingsScreen;
import dev.t3craft.client.queue.QueueScreen;
import dev.t3craft.client.screen.ConfirmActionScreen;
import dev.t3craft.client.screen.OperatePairScreen;
import dev.t3craft.client.screen.PairScreen;
import dev.t3craft.client.screen.SnoozeScreen;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.client.t3ui.T3Slots;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import dev.t3craft.t3.ThreadOps;
import dev.t3craft.t3.ThreadState;
import java.net.URI;
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
			new Scenario("pieces", "Piece browser as a screen of its own (the code catalog; Preview needs a world)",
					() -> new dev.t3craft.client.pieces.PieceBrowserScreen(null)),
			new Scenario("inbox-pieces", "Main T3 screen with the piece browser in the thread's place (the ⋯ menu → Pieces)",
					() -> inbox(true, true).withPage(new dev.t3craft.client.pieces.PieceBrowserPanel())),
			new Scenario("icons", "Icon gallery: every T3Icon (Lucide) in a grid on both themes, 12 px in its three states and 32 px; wheel scrolls",
					() -> new IconGalleryScreen(null)),
			new Scenario("model-picker", "The composer's model picker open over a fake catalog (reasoning and extra selects, switches, access, favorites, legacy, many providers)",
					ModelPickerGalleryScreen::new),
			new Scenario("queue", "Queued runs (reads the live queue, empty without a connection)", () -> new QueueScreen(null, null)));

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

	/** Four fake paired environments for the list page; every action answers with a line and touches nothing. */
	private static EnvironmentActions galleryActions() {
		return new EnvironmentActions() {
			@Override
			public List<EnvRow> list() {
				return List.of(
						new EnvRow("studio", "Studio Mac", "http://studio.example.ts.net:3773/", "ok", ""),
						new EnvRow("laptop", "Laptop", "http://my-laptop:3773/", "connecting", ""),
						new EnvRow("vps-env", "VPS", "https://vps.example.ts.net/", "offline", "connection refused"),
						new EnvRow("old", "Old machine", "http://10.0.0.7:3773/", "-", ""));
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

	private static Screen operatePair() {
		return new OperatePairScreen(null, new Environments.Environment(ENV, ENV_LABEL, URI.create("http://127.0.0.1:3773/")));
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
		return new T3Screen(directory, source(), actions, T3Slots.DEFAULT, threads ? ENV + ":t-run" : null);
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
