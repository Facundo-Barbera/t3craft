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
import dev.t3craft.hq.HqAnchorSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Demo mode ("T3Craft HQ"): its own {@code client} entrypoint. A dedicated superflat creative
 * world with a fixed folder; the title / world-select button and {@code /t3craft demo} create it
 * the first time and open it afterwards; on entering, the HQ is built at a fixed origin by the
 * animated builders, once per plan version.
 *
 * <p>Commands (merged under {@code /t3craft}): {@code demo} (asks before leaving another world),
 * {@code demo confirm}, {@code demo rebuild}, {@code demo status}.
 */
public final class DemoClient implements ClientModInitializer {
	/** Ticks after joining before the build check (chunks and builders settle). */
	private static final int SETTLE_TICKS = 40;
	/** Ticks between the first-visit teleport and the crew setting off from the player. */
	private static final int TELEPORT_DELAY = 20;

	private static volatile @Nullable DemoStructures structures;

	private enum Pending { NONE, OPEN, OPEN_CONFIRMED, OPEN_AFTER_LEAVE, REBUILD }

	/** Set on the server thread when the integrated server runs the demo save; null otherwise. */
	private volatile @Nullable Path demoRoot;
	private volatile DemoConfig activeConfig = DemoConfig.defaults();

	private @Nullable ClientLevel level;
	private int ticksIn;
	private boolean checked;
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
						})))));
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
			case NONE -> {
			}
		}

		if (mc.level != level) {
			level = mc.level;
			ticksIn = 0;
			checked = false;
			run = null; // its crews died with the old level (BuildersClient cancels them)
			clearAnchors();
		}
		Path root = demoRoot;
		if (level == null || root == null || mc.player == null) {
			return;
		}
		if (++ticksIn < SETTLE_TICKS) {
			return;
		}
		if (!checked) {
			checked = true;
			start(mc, root, false);
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

	/** Builds the chosen source unless the marker says this version is already there (or {@code force}). */
	private void start(Minecraft mc, Path root, boolean force) {
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
		run = new DemoBuildRun(source, plans, root, cfg, delay, this::say, () -> installAnchors(source));
		say("building HQ: " + source.id() + " v" + source.version() + ", " + plans.size() + " part(s)");
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

	// ---- HQ anchors -------------------------------------------------------------------------

	private static List<dev.t3craft.client.npc.NpcInfo> liveNpcs() {
		T3CraftClient c = T3CraftClient.get();
		return c == null ? List.of() : c.npcs().all();
	}

	/** Once the HQ is in the world, NPCs walk to their desks in it (feat/hq-design's {@code HqAnchorSource}). */
	private void installAnchors(DemoStructureSource source) {
		T3CraftClient c = T3CraftClient.get();
		if (!(source instanceof DemoHqSource h) || c == null || demoRoot == null) {
			return;
		}
		DemoWorldSpec spec = activeConfig.spec();
		HqAnchorSource anchors = h.anchors(new BlockPos(spec.originX(), spec.originY(), spec.originZ()));
		if (anchors != null) {
			c.npcs().behavior().setAnchorSource(anchors);
			anchorsInstalled = true;
		}
	}

	private void clearAnchors() {
		T3CraftClient c = T3CraftClient.get();
		if (anchorsInstalled && c != null) {
			c.npcs().behavior().setAnchorSource(null);
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
