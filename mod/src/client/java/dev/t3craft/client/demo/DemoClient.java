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
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
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
 * deletes the demo world (zip backup first by default) and creates it fresh ({@link HqReset}).
 */
public final class DemoClient implements ClientModInitializer {
	/** Ticks after joining before the build check (chunks and builders settle). */
	private static final int SETTLE_TICKS = 40;
	/** Ticks between the first-visit teleport and the crew setting off from the player. */
	private static final int TELEPORT_DELAY = 20;
	/** How often the HQ checks whether a thread took or released a station (ticks). */
	private static final int RECONCILE_TICKS = 20 * 5;

	private static volatile @Nullable DemoStructures structures;

	private enum Pending { NONE, OPEN, OPEN_CONFIRMED, OPEN_AFTER_LEAVE, REBUILD, RESET, RESET_CONFIRMED }

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
	/** Whether we installed the HQ's NPC anchors (so we only clear our own). */
	private boolean anchorsInstalled;

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
			DemoConfig cfg = loadConfig();
			if (!VanillaDemoWorld.isDemoServer(server, cfg.spec().folderId())) {
				demoRoot = null;
				return;
			}
			Path root = VanillaDemoWorld.worldRoot(server);
			activeConfig = cfg;
			demoRoot = root;
			if (DemoMarkerFile.read(root).needsSetup()) {
				VanillaDemoWorld.applySetup(server, cfg.spec());
				DemoMarkerFile.update(root, DemoMarker::withSetup);
				T3Craft.LOGGER.info("[demo] world setup applied in {}", root.getFileName());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> demoRoot = null);
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("demo", this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (run != null) {
				run.cancel();
			}
			run = null;
			structures = null;
		});
		DemoButtons.register(this);
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
						.then(literal("confirm").executes(c -> queue(Pending.RESET_CONFIRMED)))))));
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
			case CREATE -> VanillaDemoWorld.create(mc, cfg.spec(), back);
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
			case NONE -> {
			}
		}

		if (mc.level != level) {
			level = mc.level;
			ticksIn = 0;
			checked = false;
			builtBefore = null;
			enteredAtMs = System.currentTimeMillis();
			run = null; // its crews died with the old level (BuildersClient cancels them)
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
			start(mc, root, false);
		} else if (run == null && ticksIn % RECONCILE_TICKS == 0) {
			DemoHqSource h = hq;
			if (h != null && h.rosterChanged() && rosterReady(true)) {
				start(mc, root, false, true); // a thread took or released a station: diff the HQ
			}
		}
		DemoBuildRun r = run;
		if (r != null) {
			r.tick(mc, Builders.service());
			if (r.done()) {
				run = null;
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
		start(mc, root, true);
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
		start(mc, root, force, false);
	}

	/**
	 * Builds the chosen source unless the marker says this version is already there (or {@code force}).
	 * {@code quiet}: an automatic reconcile (roster change), logged instead of announced in chat.
	 */
	private void start(Minecraft mc, Path root, boolean force, boolean quiet) {
		DemoConfig cfg = activeConfig;
		DemoStructures s = structures;
		DemoMarker marker = DemoMarkerFile.read(root);
		if (s != null && mc.level != null) {
			for (DemoStructureSource src : s.all()) {
				try {
					src.prepare(marker, cfg.spec(), mc.level.getMinY());
				} catch (RuntimeException e) {
					T3Craft.LOGGER.error("[demo] structure source {} failed to prepare", src.id(), e);
				}
			}
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
		int delay = 0;
		IntegratedServer server = mc.getSingleplayerServer();
		if (marker.structure() == null && server != null && mc.player != null) {
			String name = mc.player.getGameProfile().name();
			server.execute(() -> VanillaDemoWorld.teleportToViewpoint(server, name, cfg.spec()));
			delay = TELEPORT_DELAY;
		}
		clearAnchors(); // NPCs keep to the ring layout while the HQ changes
		run = new DemoBuildRun(source, plans, root, cfg, delay, quiet ? m -> T3Craft.LOGGER.info("[demo] {}", m) : this::say,
				() -> installAnchors(source));
		String msg = "building HQ: " + source.id() + " v" + source.version() + ", " + plans.size() + " part(s)";
		if (quiet) {
			T3Craft.LOGGER.info("[demo] roster changed, {}", msg);
		} else {
			say(msg);
		}
	}

	private void status() {
		DemoConfig cfg = loadConfig();
		Minecraft mc = Minecraft.getInstance();
		Path root = demoRoot;
		DemoStructures s = structures;
		String chosen = s == null ? "-" : s.choose(cfg.structure()).map(x -> x.id() + " v" + x.version()).orElse("none");
		say("world '" + cfg.spec().folderId() + "' " + (VanillaDemoWorld.exists(mc, cfg.spec().folderId()) ? "exists" : "not created")
				+ (root != null ? ", you are in it" : "") + "; structure " + cfg.structure() + " -> " + chosen);
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
		VanillaDemoWorld.create(mc, cfg.spec(), new TitleScreen());
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
		// feat/hq-construction: one place plugs the HQ into NPCs (no ring fallback) and workstations
		dev.t3craft.client.hq.HqPresence.Standing standing = h.standing(new BlockPos(spec.originX(), spec.originY(), spec.originZ()));
		if (standing != null) {
			dev.t3craft.client.hq.HqPresence.demo(standing);
			anchorsInstalled = true;
		}
	}

	private void clearAnchors() {
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
