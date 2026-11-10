package dev.t3craft.client.demo;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Builders;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.debug.GuardedEvents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jspecify.annotations.Nullable;

/**
 * Demo mode ("T3Craft HQ"): its own {@code client} entrypoint. A dedicated superflat creative
 * world with a fixed folder; the title / world-select button and {@code /t3craft demo} create it
 * the first time and open it afterwards; on entering, the HQ is built at a fixed origin by the
 * animated builders, once per plan version.
 *
 * <p>Commands (merged under {@code /t3craft}): {@code demo} (asks before leaving another world),
 * {@code demo confirm}, {@code demo rebuild}, {@code demo status}; {@code debug reset-hq [confirm]}
 * deletes the demo world (zip backup first by default) and creates it fresh ({@link HqReset});
 * {@code build start} starts a build that waits for the player (feat/hq-spawn-start, {@link HqStartGate}).
 */
public final class DemoClient implements ClientModInitializer {
	/** Ticks after joining before the build check (chunks and builders settle). */
	private static final int SETTLE_TICKS = 40;
	/** Ticks between the first-visit teleport and the crew setting off from the player. */
	private static final int TELEPORT_DELAY = 20;
	/** How often the HQ checks whether a thread took or released a station (ticks). */
	private static final int RECONCILE_TICKS = 20 * 5;

	private static volatile @Nullable DemoStructures structures;

	private enum Pending { NONE, OPEN, OPEN_CONFIRMED, OPEN_AFTER_LEAVE, REBUILD, RESET, RESET_CONFIRMED, START_BUILD }

	/** feat/hq-spawn-start: the chat button runs this client command. */
	static final String START_COMMAND = "t3craft build start";
	static final String START_KEY = "key.t3craft.start_build";

	private static volatile @Nullable DemoClient instance;
	/** Shown in chat once the player is in a world again (the reset result: chat is gone meanwhile). */
	private @Nullable String notice;

	/** Set on the server thread when the integrated server runs the demo save; null otherwise. */
	private volatile @Nullable Path demoRoot;
	private volatile DemoConfig activeConfig = DemoConfig.defaults();

	private @Nullable ClientLevel level;
	private int ticksIn;
	private boolean checked;
	/** Whether the demo world held an HQ when this level was entered (read once per level). */
	private @Nullable Boolean builtBefore;
	private long enteredAtMs;
	private @Nullable DemoBuildRun run;
	private Pending pending = Pending.NONE;
	private @Nullable DemoHqSource hq;
	/** The pending layout already announced in chat (once per value). */
	private @Nullable String announcedLayout;
	/** Whether we installed the HQ's NPC anchors (so we only clear our own). */
	private boolean anchorsInstalled;
	/**
	 * fix/new-thread-hitch, fix/demo-reconcile-offthread: a start (entering the world, the player's start
	 * signal, a rebuild) or an automatic reconcile (roster or layout pack changed) whose HQ plan is being
	 * computed off the client thread, with what it was asked for. Client thread.
	 */
	private java.util.concurrent.@Nullable CompletableFuture<DemoHqSource.Prepared> preparing;
	private @Nullable StartRequest preparingRequest;
	/** feat/hq-spawn-start: the build due on entering waits for the player (walk or confirm). Client thread. */
	private final HqStartGate gate = new HqStartGate();

	/**
	 * fix/demo-reconcile-offthread: what a {@link #start} was asked for, kept while its HQ plan is computed.
	 * {@code waits}: the entry check, so a due build waits for the player ({@code hq.startBuild}).
	 */
	private record StartRequest(Path root, DemoMarker marker, boolean force, boolean quiet, boolean waits) {
	}
	private static @Nullable KeyMapping startKey;

	/**
	 * Integration point: register more {@link DemoStructureSource}s (the modular HQ, see
	 * {@link HqStructureSource}). Null before client init / after shutdown.
	 */
	public static @Nullable DemoStructures structures() {
		return structures;
	}

	@Override
	public void onInitializeClient() {
		DemoStructures s = new DemoStructures();
		s.register(new ShedStructureSource());
		hq = new DemoHqSource(DemoClient::liveNpcs);
		s.register(hq); // the real HQ (feat/hq-design); falls back to the shed if its plan fails
		structures = s;
		instance = this;

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			DemoConfig loaded = loadConfig();
			if (!VanillaDemoWorld.isDemoServer(server, loaded.spec().folderId())) {
				demoRoot = null;
				VoidGuard.stop();
				return;
			}
			Path root = VanillaDemoWorld.worldRoot(server);
			// feat/hq-void-world: void or flat is what the save is (old HQ worlds stay flat), never what the config says now
			boolean voidWorld = VanillaDemoWorld.isVoid(server);
			DemoConfig cfg = loaded.forWorld(voidWorld);
			activeConfig = cfg;
			demoRoot = root;
			int minY = server.overworld().getMinY();
			DemoMarker marker = DemoMarkerFile.read(root);
			// feat/hq-spawn-start: the player spawns in the centre of the HQ (hub), on a single block
			HqCentre centre = HqCentre.of(cfg.spec(), minY, layoutFor(root));
			List<BlockPos> oldSpawns = oldSpawns(marker, cfg.spec(), minY);
			if (voidWorld) {
				voidStart(marker, cfg.spec(), minY, centre == null ? null : centre.feet());
			}
			boolean fresh = marker.needsSetup();
			if (fresh) {
				VanillaDemoWorld.applySetup(server, cfg.spec(), centre != null ? centre.feet() : null);
				DemoMarkerFile.update(root, DemoMarker::withSetup);
				T3Craft.LOGGER.info("[demo] world setup applied in {} ({} world)", root.getFileName(), voidWorld ? "void" : "flat");
			}
			if (centre != null) {
				spawnAtCentre(server, root, centre, fresh, oldSpawns);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			demoRoot = null;
			VoidGuard.stop();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				VoidGuard.tick(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[demo] void guard failed, turned off for this session", e);
				VoidGuard.stop();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("demo", this::tick));
		// fix/hq-load-build-portal: the empty HQ plan the spawn needs is computed in the background from the start,
		// so SERVER_STARTED finds it ready instead of generating it on the server thread (the 16% freeze)
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> warmEmptyPlan());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (run != null) {
				run.cancel();
			}
			run = null;
			structures = null;
		});
		DemoButtons.register(this);
		startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(START_KEY, InputConstants.Type.KEYBOARD, InputConstants.KEY_G,
				dev.t3craft.client.config.T3CraftKeys.CATEGORY));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft")
				.then(literal("demo")
						// The chat screen closes right after a command runs: act on the next tick.
						.executes(c -> queue(Pending.OPEN))
						.then(literal("confirm").executes(c -> queue(Pending.OPEN_CONFIRMED)))
						.then(literal("rebuild").executes(c -> queue(Pending.REBUILD)))
						.then(literal("status").executes(c -> {
							status();
							return 1;
						})))
				.then(literal("debug").then(literal("reset-hq")
						.executes(c -> queue(Pending.RESET))
						.then(literal("confirm").executes(c -> queue(Pending.RESET_CONFIRMED)))))
				.then(literal("build").then(literal("start").executes(c -> queue(Pending.START_BUILD))))));
	}

	/**
	 * feat/hq-void-world, server thread: starts the {@link VoidGuard} of a void HQ world.
	 * feat/hq-spawn-start: new void worlds get no starter island any more (the player spawns in the
	 * centre of the HQ, on one block, and the island grows from there); a starter island an older world
	 * already has stays where it is and remains the guard's fallback.
	 */
	private static void voidStart(DemoMarker marker, DemoWorldSpec spec, int minY, @Nullable BlockPos centre) {
		DemoMarker.Cell c = marker.starter();
		int[] v = DemoPlacement.viewpoint(spec, minY);
		BlockPos hqSpawn = centre != null ? centre : new BlockPos(v[0], v[1], v[2]);
		BlockPos starter = c == null ? null : new VoidIsland.Starter(c.x(), c.y(), c.z()).spawn();
		VoidGuard.start(spec.originY(), minY, starter != null ? starter : hqSpawn, hqSpawn);
	}

	/** Starts the background computation of the empty HQ plan of the configured layout (never blocks, never throws). Any thread. */
	static void warmEmptyPlan() {
		try {
			dev.t3craft.hq.HqEmptyPlan.start(dev.t3craft.hq.style.HqStyles.forLayout(dev.t3craft.client.hq.layout.HqLayoutConfig.selectedId()));
			dev.t3craft.hq.HqEmptyPlan.start(dev.t3craft.hq.style.HqStyles.current());
		} catch (RuntimeException | LinkageError e) {
			T3Craft.LOGGER.debug("[demo] could not warm the empty HQ plan ({})", e.getClass().getSimpleName());
		}
	}

	/** The layout whose centre the spawn goes to: the one built in this world, else the configured one. */
	private static String layoutFor(Path root) {
		String built = builtLayoutId(root);
		return built != null ? built : dev.t3craft.client.hq.layout.HqLayoutConfig.selectedId();
	}

	/** Spawns the HQ set before feat/hq-spawn-start (starter island, viewpoints): moved to the centre only from one of these. */
	private static List<BlockPos> oldSpawns(DemoMarker marker, DemoWorldSpec spec, int minY) {
		List<BlockPos> out = new java.util.ArrayList<>();
		DemoMarker.Cell c = marker.starter();
		if (c != null) {
			out.add(new VoidIsland.Starter(c.x(), c.y(), c.z()).spawn());
		}
		int[] a = DemoPlacement.viewpoint(spec, minY);
		out.add(new BlockPos(a[0], a[1], a[2]));
		int[] b = DemoPlacement.viewpoint(spec);
		out.add(new BlockPos(b[0], b[1], b[2]));
		return out;
	}

	/**
	 * feat/hq-spawn-start, server thread: decided once per world (recorded as {@code centre} in the
	 * marker). A new world, or an older one whose spawn is still where the mod put it, gets its world
	 * spawn on the HQ centre (respawn and reconnects go there) and the single block under it. A spawn
	 * moved by hand is left alone. Nobody is teleported: players already in the world stay where they are.
	 */
	private static void spawnAtCentre(net.minecraft.server.MinecraftServer server, Path root, HqCentre centre, boolean fresh,
			List<BlockPos> oldSpawns) {
		try {
			DemoMarker m = DemoMarkerFile.read(root);
			BlockPos now = VanillaDemoWorld.worldSpawn(server);
			DemoMarker.Cell done = m.centre();
			if (done != null) {
				// already decided; if the spawn is still the centre and its block is gone (that floor not built yet), put it back
				if (now.equals(new BlockPos(done.x(), done.y(), done.z())) && now.equals(centre.feet())) {
					VanillaDemoWorld.placeCentreBlock(server, centre);
				}
				return;
			}
			if (fresh || now.equals(centre.feet()) || oldSpawns.contains(now)) {
				boolean placed = VanillaDemoWorld.placeCentreBlock(server, centre);
				VanillaDemoWorld.setWorldSpawn(server, centre.feet(), centre.yaw());
				T3Craft.LOGGER.info("[demo] world spawn on the HQ centre {} ({})", centre.feet().toShortString(),
						placed ? (centre.planFloor() != null ? "plan floor block placed" : "neutral block placed") : "floor already there");
			} else {
				T3Craft.LOGGER.info("[demo] world spawn {} was set by hand, left there (HQ centre {})", now.toShortString(),
						centre.feet().toShortString());
			}
			DemoMarkerFile.update(root, x -> x.withCentre(centre.cell()));
		} catch (RuntimeException e) {
			T3Craft.LOGGER.error("[demo] could not move the world spawn to the HQ centre", e);
		}
	}

	private int queue(Pending p) {
		pending = p;
		return 1;
	}

	// ---- open / create ----------------------------------------------------------------------

	/** From the button (parent = the screen it sits on) or a command (parent = title screen). */
	void open(Minecraft mc, @Nullable Screen parent, boolean confirmed) {
		DemoConfig cfg = loadConfig();
		String folder = cfg.spec().folderId();
		boolean inWorld = mc.level != null;
		boolean inDemo = inWorld && mc.isLocalServer() && demoRoot != null;
		DemoDecision.Action action = DemoDecision.decide(inWorld, inDemo, VanillaDemoWorld.exists(mc, folder), confirmed);
		Screen back = parent != null ? parent : new TitleScreen();
		T3Craft.LOGGER.info("[demo] {} '{}'", action, folder);
		if (action == DemoDecision.Action.OPEN || action == DemoDecision.Action.CREATE) {
			warmEmptyPlan(); // runs while the world loads
		}
		switch (action) {
			case ALREADY_HERE -> say("you are already in " + cfg.spec().displayName() + " (/t3craft demo rebuild to build again)");
			case CONFIRM_LEAVE -> mc.gui.setScreen(new ConfirmScreen(yes -> {
				if (yes) {
					pending = Pending.OPEN_AFTER_LEAVE;
				}
				mc.gui.setScreen(null);
			}, Component.translatable("t3craft.demo.confirm.title", cfg.spec().displayName()),
					Component.translatable("t3craft.demo.confirm.body")));
			case OPEN -> VanillaDemoWorld.open(mc, folder, back);
			case CREATE -> VanillaDemoWorld.create(mc, cfg.newWorldSpec(), back);
		}
	}

	// ---- client tick ------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		Pending p = pending;
		pending = Pending.NONE;
		switch (p) {
			case OPEN -> open(mc, null, false);
			case OPEN_CONFIRMED, OPEN_AFTER_LEAVE -> leaveThenOpen(mc);
			case REBUILD -> rebuild(mc);
			case RESET -> reset(mc, false);
			case RESET_CONFIRMED -> reset(mc, true);
			case START_BUILD -> confirmStart(true);
			case NONE -> {
			}
		}

		KeyMapping key = startKey;
		if (key != null) {
			while (key.consumeClick()) {
				if (mc.gui.screen() == null) {
					confirmStart(false);
				}
			}
		}

		if (mc.level != level) {
			level = mc.level;
			gate.disarm(); // feat/hq-spawn-start: left before the build started: it waits again next time
			ticksIn = 0;
			checked = false;
			builtBefore = null;
			enteredAtMs = System.currentTimeMillis();
			run = null; // its crews died with the old level (BuildersClient cancels them)
			cancelPreparing();
			dev.t3craft.client.npc.NpcGate.open();
			clearAnchors();
		}
		if (notice != null && mc.player != null) {
			say(notice);
			notice = null;
		}
		Path root = demoRoot;
		if (level == null || root == null || mc.player == null) {
			return;
		}
		if (++ticksIn < SETTLE_TICKS) {
			return;
		}
		if (!checked) {
			// fix/hq-stale-stations: reconcile against the roster only once every environment has sent
			// its first synced shell, so a world load never tears down the desks of one still connecting.
			if (builtBefore == null) {
				builtBefore = DemoMarkerFile.read(root).structure() != null;
			}
			if (!rosterReady(builtBefore)) {
				return;
			}
			checked = true;
			start(mc, root, false, false, HqStartGate.waits(HqStartGate.mode()));
		} else if (preparing != null) {
			// fix/demo-reconcile-offthread: the HQ plan is being computed: the build waits for it (pollPreparing)
		} else if (gate.armed()) {
			pollGate(mc, root); // feat/hq-spawn-start: nothing else is built until the player starts the build
		} else if (run == null && ticksIn % 20 == 0 && hq != null && hq.packsChanged()) {
			// feat/hq-layout-packs: a layout pack was reloaded: diff the HQ (within a second, no reload of the game)
			start(mc, root, false, true, false);
		} else if (run == null && ticksIn % RECONCILE_TICKS == 0) {
			DemoHqSource h = hq;
			if (h != null && h.rosterChanged() && rosterReady(true)) {
				start(mc, root, false, true, false); // a thread took or released a station: diff the HQ
			}
		}
		pollPreparing(mc, root);
		DemoBuildRun r = run;
		if (r != null) {
			r.tick(mc, Builders.service());
			if (r.done()) {
				run = null;
				dev.t3craft.client.npc.NpcGate.open(); // fix/hq-build-parallel: the HQ stands, NPCs come in
			}
		}
	}

	private void leaveThenOpen(Minecraft mc) {
		if (mc.level != null && !(mc.isLocalServer() && demoRoot != null)) {
			VanillaDemoWorld.leaveWorld(mc);
		}
		open(mc, null, true);
	}

	private void rebuild(Minecraft mc) {
		Path root = demoRoot;
		if (root == null || mc.level == null) {
			say("/t3craft demo rebuild only works inside " + activeConfig.spec().displayName() + " (use /t3craft demo)");
			return;
		}
		if (run != null) {
			run.cancel();
			run = null;
		}
		gate.disarm();
		start(mc, root, true);
	}

	// ---- start signal (feat/hq-spawn-start) ---------------------------------------------------

	/** The build due on entering is ready: wait for the player and tell them how to start it. */
	private void armGate(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		gate.arm(mc.player.getX(), mc.player.getZ());
		KeyMapping key = startKey;
		Component keyName = key != null ? key.getTranslatedKeyMessage() : Component.literal("-");
		Component button = Component.literal("[" + Component.translatable("t3craft.demo.start.button").getString() + "]")
				.withStyle(st -> st.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand(START_COMMAND))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("t3craft.demo.start.hover", "/" + START_COMMAND, keyName))));
		mc.player.sendSystemMessage(T3CraftCommands.prefix()
				.append(Component.translatable("t3craft.demo.start.body").withStyle(ChatFormatting.GRAY))
				.append(" ").append(button));
		mc.player.sendOverlayMessage(Component.translatable("t3craft.demo.start.hint").withStyle(ChatFormatting.GRAY));
		T3Craft.LOGGER.info("[demo] HQ build waiting for the player (walk, the chat button or {})", keyName.getString());
	}

	/** Button, key or command. {@code fromCommand}: say so when nothing is waiting (the key stays silent). */
	private void confirmStart(boolean fromCommand) {
		if (!gate.confirm() && fromCommand) {
			say(run != null ? "the HQ build is already running" : "no HQ build is waiting to start");
		}
	}

	private void pollGate(Minecraft mc, Path root) {
		if (mc.player == null) {
			return;
		}
		boolean move = mc.gui.screen() == null && (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
				|| mc.options.keyLeft.isDown() || mc.options.keyRight.isDown());
		HqStartGate.Signal signal = gate.tick(mc.player.getX(), mc.player.getZ(), move);
		if (signal == HqStartGate.Signal.NONE) {
			return;
		}
		gate.disarm();
		T3Craft.LOGGER.info("[demo] HQ build started by the player ({})", signal);
		start(mc, root, false);
	}

	private boolean rosterReady(boolean built) {
		T3CraftClient c = T3CraftClient.get();
		if (c == null) {
			return true;
		}
		return dev.t3craft.hq.RosterGate.decide(c.rosterSources(), c.syncedRosterSources(), built,
				System.currentTimeMillis() - enteredAtMs) == dev.t3craft.hq.RosterGate.Decision.RECONCILE;
	}

	private void start(Minecraft mc, Path root, boolean force) {
		start(mc, root, force, false, false);
	}

	/**
	 * Builds the chosen source unless the marker says this version is already there (or {@code force}).
	 * {@code quiet}: an automatic reconcile (roster change), logged instead of announced in chat.
	 * {@code waits}: the entry check; a due build waits for the player ({@code hq.startBuild}).
	 *
	 * <p>fix/demo-reconcile-offthread: the HQ is planned on a background worker ({@link DemoHqSource#prepareLater}:
	 * layout plan, old plan, diff, baked parts, fingerprint; the citadel took 4.3 s here, inside one client
	 * tick). The client thread only reads the marker and the roster now; {@link #pollPreparing} goes on
	 * with {@link #proceed} once the plan is ready, a few ticks later. Nothing is built meanwhile.
	 */
	private void start(Minecraft mc, Path root, boolean force, boolean quiet, boolean waits) {
		cancelPreparing();
		long t0 = System.nanoTime();
		try {
			DemoMarker marker = DemoMarkerFile.read(root);
			DemoHqSource h = hq;
			java.util.function.Supplier<DemoHqSource.Prepared> work = null;
			if (h != null && mc.level != null) {
				try {
					work = h.prepareLater(marker, activeConfig.spec(), mc.level.getMinY());
				} catch (RuntimeException e) {
					work = null; // the synchronous path below records the failure and falls back (it throws early, cheap)
				}
			}
			if (work == null) {
				prepareSources(mc, marker, null);
				proceed(mc, root, force, quiet, marker, waits);
				return;
			}
			preparingRequest = new StartRequest(root, marker, force, quiet, waits);
			preparing = dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_DEMO_PREPARE, work);
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_DEMO_RECONCILE, t0);
		}
	}

	/** Prepares every structure source except {@code skip} (already prepared) against {@code marker}. */
	private void prepareSources(Minecraft mc, DemoMarker marker, @Nullable DemoStructureSource skip) {
		DemoStructures s = structures;
		if (s != null && mc.level != null) {
			for (DemoStructureSource src : s.all()) {
				if (src == skip) {
					continue;
				}
				try {
					src.prepare(marker, activeConfig.spec(), mc.level.getMinY());
				} catch (RuntimeException e) {
					T3Craft.LOGGER.error("[demo] structure source {} failed to prepare", src.id(), e);
				}
			}
		}
	}

	private void pollPreparing(Minecraft mc, Path root) {
		java.util.concurrent.CompletableFuture<DemoHqSource.Prepared> f = preparing;
		if (f == null || !f.isDone()) {
			return;
		}
		StartRequest req = preparingRequest;
		preparing = null;
		preparingRequest = null;
		DemoHqSource h = hq;
		if (h == null || req == null || !root.equals(req.root()) || run != null || mc.level == null) {
			return; // another world, or a build started meanwhile: the next reconcile asks again
		}
		DemoMarker marker = req.marker();
		long t0 = System.nanoTime();
		try {
			try {
				h.adopt(f.join());
			} catch (RuntimeException e) {
				h.fail(e.getCause() != null ? e.getCause() : e);
			}
			prepareSources(mc, marker, h);
			proceed(mc, root, req.force(), req.quiet(), marker, req.waits());
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_DEMO_RECONCILE, t0);
		}
	}

	private void cancelPreparing() {
		java.util.concurrent.CompletableFuture<DemoHqSource.Prepared> f = preparing;
		if (f != null) {
			f.cancel(false);
		}
		preparing = null;
		preparingRequest = null;
	}

	/** The rest of {@link #start} once every source is prepared against {@code marker}. */
	private void proceed(Minecraft mc, Path root, boolean force, boolean quiet, DemoMarker marker, boolean waits) {
		DemoConfig cfg = activeConfig;
		DemoStructures s = structures;
		DemoHqSource h = hq;
		if (h != null && h.pendingLayout() != null && !quiet && !Objects.equals(h.pendingLayout(), announcedLayout)) {
			// feat/hq-layouts: another layout is configured, but the HQ standing here keeps its own until a reset
			announcedLayout = h.pendingLayout();
			say("HQ built with layout '" + h.layoutId() + "'; the configured layout '" + h.pendingLayout()
					+ "' applies after a reset + rebuild (/t3craft debug reset-hq)");
		}
		Optional<DemoStructureSource> chosen = s == null ? Optional.empty() : s.choose(cfg.structure());
		if (chosen.isEmpty()) {
			T3Craft.LOGGER.warn("[demo] no structure source available (structure = {})", cfg.structure());
			return;
		}
		DemoStructureSource source = chosen.get();
		if (!force && !marker.needsBuild(source)) {
			T3Craft.LOGGER.info("[demo] {} v{} already built, nothing to do", source.id(), source.version());
			installAnchors(source);
			return;
		}
		List<BuildPlan> plans;
		try {
			plans = force ? source.plans() : source.plansSince(marker);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.error("[demo] structure source {} failed", source.id(), e);
			say("HQ plan '" + source.id() + "' failed, see log");
			return;
		}
		if (plans.isEmpty()) {
			// Same content under a new marker format (or an empty diff): record it, nothing to place.
			DemoMarkerFile.update(root, m -> m.withBuilt(source));
			installAnchors(source);
			return;
		}
		if (waits && !force && !quiet) {
			// feat/hq-spawn-start: the world is loaded and a build is due: it starts when the player walks or confirms
			armGate(mc);
			return;
		}
		int delay = 0;
		IntegratedServer server = mc.getSingleplayerServer();
		// feat/hq-spawn-start: the first-build teleport only with hq.startBuild = "immediate"; otherwise the player
		// already stands where they spawned (the HQ centre) and pulling them back after they walked would be wrong
		if (marker.structure() == null && server != null && mc.player != null && !HqStartGate.waits(HqStartGate.mode())) {
			String name = mc.player.getGameProfile().name();
			DemoMarker.Cell starter = marker.starter();
			DemoMarker.Cell centre = marker.centre();
			if (cfg.spec().voidWorld() && starter != null) {
				// feat/hq-void-world: the HQ island is not there yet: wait on the starter island, the crew sets off from there
				BlockPos feet = new VoidIsland.Starter(starter.x(), starter.y(), starter.z()).spawn();
				server.execute(() -> VanillaDemoWorld.teleportTo(server, name, feet));
			} else if (centre != null) {
				BlockPos feet = new BlockPos(centre.x(), centre.y(), centre.z());
				server.execute(() -> VanillaDemoWorld.teleportTo(server, name, feet));
			} else {
				server.execute(() -> VanillaDemoWorld.teleportToViewpoint(server, name, cfg.spec()));
			}
			delay = TELEPORT_DELAY;
		}
		if (clearsAnchorsBeforeBuild(quiet)) {
			clearAnchors(); // NPCs keep to the ring layout while the HQ is (re)built from scratch
		}
		// fix/hq-build-parallel: the structure first. A full build keeps every NPC away until it stands;
		// a diff keeps the ones already at work and holds only the newcomers until their desk is built.
		dev.t3craft.client.npc.NpcGate.set(npcGateFor(quiet));
		run = new DemoBuildRun(source, plans, root, cfg, delay, quiet ? m -> T3Craft.LOGGER.info("[demo] {}", m) : this::say,
				() -> installAnchors(source));
		String msg = "building HQ: " + source.id() + " v" + source.version() + ", " + plans.size() + " part(s)";
		if (quiet) {
			T3Craft.LOGGER.info("[demo] roster changed, {}", msg);
		} else {
			say(msg);
		}
	}

	/**
	 * fix/npc-position-reset: an automatic reconcile (a thread took or released a station) keeps the
	 * standing HQ's anchors while the diff is built. Clearing them sent every NPC back to the
	 * provisional grid (their first spot) each time a new agent appeared; now the newcomer waits in the
	 * hub until its desk stands, and once the new plan is installed only NPCs whose spot moved re-plan.
	 * A manual build or rebuild still clears them.
	 */
	static boolean clearsAnchorsBeforeBuild(boolean quiet) {
		return !quiet;
	}

	/** Who may stand in the HQ world while it is built (fix/hq-build-parallel). */
	static dev.t3craft.client.npc.NpcGate.Mode npcGateFor(boolean quiet) {
		return quiet ? dev.t3craft.client.npc.NpcGate.Mode.HOLD_NEW : dev.t3craft.client.npc.NpcGate.Mode.HOLD_ALL;
	}

	private void status() {
		DemoConfig cfg = loadConfig();
		Minecraft mc = Minecraft.getInstance();
		Path root = demoRoot;
		DemoStructures s = structures;
		String chosen = s == null ? "-" : s.choose(cfg.structure()).map(x -> x.id() + " v" + x.version()).orElse("none");
		say("world '" + cfg.spec().folderId() + "' " + (VanillaDemoWorld.exists(mc, cfg.spec().folderId()) ? "exists" : "not created")
				+ (root != null ? ", you are in it (" + (activeConfig.spec().voidWorld() ? "void" : "flat") + ")" : "")
				+ "; new worlds: " + cfg.world() + "; structure " + cfg.structure() + " -> " + chosen);
		if (root != null) {
			DemoMarker m = DemoMarkerFile.read(root);
			say("built: " + (m.structure() == null ? "nothing yet" : m.structure() + " v" + m.version()));
		}
		DemoHqSource h = hq;
		if (h != null) {
			say("hq: " + h.describe() + (anchorsInstalled ? ", NPC desks active" : ""));
		}
		DemoBuildRun r = run;
		if (r != null) {
			say("running: " + r.describe());
		}
		if (gate.armed()) {
			say("build waiting for you: walk, click [" + Component.translatable("t3craft.demo.start.button").getString() + "] or /"
					+ START_COMMAND + " (hq.startBuild = " + HqStartGate.mode() + ")");
		}
	}

	// ---- reset ------------------------------------------------------------------------------

	/**
	 * For the settings button: null if a reset may be offered (then ask, then {@link #requestReset}),
	 * else why not (in another world, demo not created, mod not initialised).
	 */
	public static @Nullable String resetRefusal() {
		DemoClient d = instance;
		if (d == null) {
			return "demo mode is not initialised";
		}
		Minecraft mc = Minecraft.getInstance();
		DemoConfig cfg = d.loadConfig();
		return switch (d.resetAction(mc, cfg, false)) {
			case REFUSE_OTHER_WORLD -> "Only inside " + cfg.spec().displayName() + ": this world is never reset";
			case NOTHING_TO_RESET -> cfg.spec().displayName() + " does not exist yet";
			case CONFIRM, RESET -> null;
		};
	}

	/**
	 * feat/hq-layouts: the layout the HQ world is built with ({@code HqLayoutState}), or null when not
	 * in the HQ world or nothing is built there yet.
	 */
	public static @Nullable String builtLayoutId() {
		DemoClient d = instance;
		Path root = d == null ? null : d.demoRoot;
		return root == null ? null : builtLayoutId(root);
	}

	/** The layout recorded in the marker of the HQ world saved at {@code worldRoot} (null if nothing built). */
	public static @Nullable String builtLayoutId(Path worldRoot) {
		DemoMarker m = DemoMarkerFile.read(worldRoot);
		return dev.t3craft.client.hq.layout.HqLayoutState.fromMarker(m.structure(), m.state());
	}

	/** Queues a confirmed reset for the next client tick (after the confirm screen closes). */
	public static void requestReset() {
		DemoClient d = instance;
		if (d != null) {
			d.pending = Pending.RESET_CONFIRMED;
		}
	}

	private HqReset.Action resetAction(Minecraft mc, DemoConfig cfg, boolean confirmed) {
		boolean inWorld = mc.level != null;
		boolean inDemo = inWorld && mc.isLocalServer() && demoRoot != null;
		return HqReset.decide(inWorld, inDemo, VanillaDemoWorld.exists(mc, cfg.spec().folderId()), confirmed);
	}

	private void reset(Minecraft mc, boolean confirmed) {
		DemoConfig cfg = loadConfig();
		HqReset.Action action = resetAction(mc, cfg, confirmed);
		T3Craft.LOGGER.info("[demo] reset-hq: {} '{}'", action, cfg.spec().folderId());
		switch (action) {
			case REFUSE_OTHER_WORLD -> error("reset-hq only works inside " + cfg.spec().displayName() + "; this world is never touched");
			case NOTHING_TO_RESET -> say(cfg.spec().displayName() + " does not exist yet (/t3craft demo creates it)");
			case CONFIRM -> {
				Component confirm = Component.literal("[" + Component.translatable("t3craft.demo.reset.confirm").getString() + "]")
						.withStyle(s -> s.withColor(ChatFormatting.RED)
								.withClickEvent(new ClickEvent.RunCommand("t3craft debug reset-hq confirm"))
								.withHoverEvent(new HoverEvent.ShowText(Component.literal("/t3craft debug reset-hq confirm"))));
				if (mc.player != null) {
					mc.player.sendSystemMessage(T3CraftCommands.prefix()
							.append(Component.translatable("t3craft.demo.reset.body").withStyle(ChatFormatting.YELLOW))
							.append(" ").append(confirm));
				}
			}
			case RESET -> doReset(mc, cfg);
		}
	}

	/** Leave (saving), validate, zip, delete, recreate. Never touches anything but saves/<demo folder>. */
	private void doReset(Minecraft mc, DemoConfig cfg) {
		String folder = cfg.spec().folderId();
		if (run != null) {
			run.cancel();
			run = null;
		}
		gate.disarm();
		dev.t3craft.client.npc.NpcGate.open();
		clearAnchors();
		if (mc.level != null) {
			VanillaDemoWorld.leaveWorld(mc); // blocks until the integrated server has stopped
		}
		Path saves = VanillaDemoWorld.savesDir(mc);
		HqReset.Check check = HqReset.validate(saves, folder);
		if (!check.ok() || check.target() == null) {
			fail(mc, "refused: " + check.reason());
			return;
		}
		String backup = "no backup (demo.resetBackup = false)";
		if (HqReset.shouldBackup(cfg)) {
			try {
				Path zip = HqReset.zip(check.target(), VanillaDemoWorld.backupDir(mc), folder, LocalDateTime.now());
				backup = "backup: " + zip.getFileName();
				T3Craft.LOGGER.info("[demo] reset-hq backup written to {}", zip);
			} catch (IOException | RuntimeException e) {
				T3Craft.LOGGER.error("[demo] reset-hq backup failed, world left untouched", e);
				fail(mc, "backup failed, nothing deleted (" + e.getClass().getSimpleName() + ")");
				return;
			}
		}
		try {
			HqReset.delete(saves, folder);
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.error("[demo] reset-hq could not delete {}", folder, e);
			fail(mc, "could not delete the old world (" + e.getClass().getSimpleName() + "); " + backup);
			return;
		}
		T3Craft.LOGGER.info("[demo] reset-hq deleted saves/{} ({})", folder, backup);
		notice = cfg.spec().displayName() + " reset; " + backup;
		VanillaDemoWorld.create(mc, cfg.newWorldSpec(), new TitleScreen()); // feat/hq-void-world: void unless demo.world = "flat"
	}

	private void fail(Minecraft mc, String msg) {
		T3Craft.LOGGER.warn("[demo] reset-hq: {}", msg);
		if (mc.level != null) {
			error("reset-hq " + msg);
		} else {
			mc.gui.setScreen(new AlertScreen(() -> mc.gui.setScreen(new TitleScreen()),
					Component.translatable("t3craft.demo.reset.title"), Component.literal(msg)));
		}
	}

	private void error(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(T3CraftCommands.prefix().append(Component.literal(text).withStyle(ChatFormatting.RED)));
		} else {
			T3Craft.LOGGER.warn("[demo] {}", text);
		}
	}

	// ---- HQ anchors -------------------------------------------------------------------------

	private static List<dev.t3craft.client.npc.NpcInfo> liveNpcs() {
		T3CraftClient c = T3CraftClient.get();
		if (c == null) {
			return List.of();
		}
		// Stationed threads get desks; released ones (settled, archived, done and inactive) only go to the archive.
		return c.npcs().hqRoster();
	}

	/** Once the HQ is in the world, NPCs walk to their desks in it (feat/hq-design's {@code HqAnchorSource}). */
	private void installAnchors(DemoStructureSource source) {
		T3CraftClient c = T3CraftClient.get();
		if (!(source instanceof DemoHqSource h) || c == null || demoRoot == null) {
			return;
		}
		DemoWorldSpec spec = activeConfig.spec();
		BlockPos origin = new BlockPos(spec.originX(), spec.originY(), spec.originZ());
		// feat/hq-construction: one place plugs the HQ into NPCs (no ring fallback) and workstations
		dev.t3craft.client.hq.HqPresence.Standing standing = h.standing(origin);
		if (standing != null) {
			dev.t3craft.client.hq.HqPresence.demo(standing);
			anchorsInstalled = true;
		}
		installBindings(h.bindings(origin));
	}

	/** Monitors and lamps of the HQ follow the live roster on their own (blocks module on only). */
	private static void installBindings(@Nullable DemoHqBindings bindings) {
		dev.t3craft.client.hq.HqPresence.demoBindings(bindings == null ? null : () -> bindings.bindings(liveNpcs()));
	}

	private void clearAnchors() {
		installBindings(null);
		T3CraftClient c = T3CraftClient.get();
		if (anchorsInstalled && c != null) {
			dev.t3craft.client.hq.HqPresence.demo(null);
		}
		anchorsInstalled = false;
	}

	// ---- helpers ----------------------------------------------------------------------------

	DemoConfig loadConfig() {
		try {
			Path f = ConfigManager.get().file();
			if (Files.exists(f)) {
				JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				DemoConfig cfg = DemoConfig.fromRoot(e.isJsonObject() ? e.getAsJsonObject() : null);
				if (!cfg.warnings().isEmpty()) {
					T3Craft.LOGGER.warn("[demo] invalid config keys, using defaults for: {}", cfg.warnings());
				}
				return cfg;
			}
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[demo] could not read config, using defaults ({})", e.getClass().getSimpleName());
		}
		return DemoConfig.defaults();
	}

	private void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(T3CraftCommands.prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		} else {
			T3Craft.LOGGER.info("[demo] {}", text);
		}
	}
}
