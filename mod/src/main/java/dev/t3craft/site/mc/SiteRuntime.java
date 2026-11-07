package dev.t3craft.site.mc;

import dev.t3craft.T3Craft;
import dev.t3craft.site.BlockWorld;
import dev.t3craft.site.InstantBuilder;
import dev.t3craft.site.ModuleDesigner;
import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildTuning;
import dev.t3craft.site.PacedBuilder;
import dev.t3craft.site.SiteBuilder;
import dev.t3craft.site.SiteHandle;
import dev.t3craft.site.SiteListener;
import dev.t3craft.site.SiteModule;
import dev.t3craft.site.SiteRequest;
import dev.t3craft.site.SiteService;
import dev.t3craft.site.SiteStore;
import dev.t3craft.site.StructureSource;
import dev.t3craft.site.TerrainProbe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Holds the {@link SiteService} of the running <b>integrated</b> (singleplayer) server. Every call
 * must run on that server's thread ({@code server.execute}). Dedicated/multiplayer servers are
 * never touched: T3Craft is a client-only mod and the client refuses site commands without an
 * integrated server.
 *
 * <p>Integration points (see docs/feat-site.md):
 * <ul>
 *   <li>{@link #setRequest}: base module + growth envelope ({@code feat/hq-design}); default
 *   {@link SiteRequest#GROWABLE}.</li>
 *   <li>{@link #setDesigner} / {@link #setStructure}: blocks of a module ({@code feat/hq-design});
 *   default {@link ModuleDesigner#PLACEHOLDER}.</li>
 *   <li>{@link #setBuilderFactory}: who writes the blocks ({@code feat/builders}); default a
 *   {@link PacedBuilder} ({@link #DEFAULT_BLOCKS_PER_TICK}/tick, with sound and particles), ticked
 *   here.</li>
 *   <li>{@link #handle}, {@link #expand}, {@link #modulesToRebuild}, {@link #addListener}: the
 *   expansion API for the builders.</li>
 * </ul>
 */
public final class SiteRuntime {
	private static @Nullable MinecraftServer server;
	private static @Nullable SiteService service;
	/** Pace of one front of the paced builder; it runs {@code build.frontsPerJob} fronts within the global budget. */
	public static final double DEFAULT_BLOCKS_PER_TICK = 8;
	private static final String BUDGET_RUNNER = "site";

	private static final List<PacedBuilder> pacers = new ArrayList<>();
	private static final List<SiteListener> listeners = new CopyOnWriteArrayList<>();
	private static Function<ServerLevel, SiteBuilder> builderFactory = SiteRuntime::paced;
	private static ModuleDesigner designer = ModuleDesigner.PLACEHOLDER;
	private static SiteRequest request = SiteRequest.GROWABLE;
	private static boolean registered;

	private SiteRuntime() {
	}

	/** Registers the server lifecycle hooks once (call from the client initializer). */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (s == server) {
				server = null;
				service = null;
			}
			// Interrupted builds stay BUILDING in the journal; resume finishes them next time.
			List<PacedBuilder> all = List.copyOf(pacers);
			pacers.clear();
			all.forEach(PacedBuilder::cancelAll);
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s != server || pacers.isEmpty()) {
				return;
			}
			BuildBudget.global().beginTick(s.getTickCount(), s.getCurrentSmoothedTickTime());
			for (PacedBuilder p : List.copyOf(pacers)) {
				try {
					p.tick();
				} catch (RuntimeException e) {
					T3Craft.LOGGER.error("site build failed; run /t3craft site resume", e);
					p.cancelAll();
				}
			}
			pacers.removeIf(PacedBuilder::idle);
		});
	}

	/** Default builder: paced, with placement sound and particles. Server thread. */
	public static SiteBuilder paced(ServerLevel level) {
		BuildTuning t = BuildBudget.global().tuning();
		PacedBuilder p = new PacedBuilder(new EffectsBlockWorld(new LevelBlockWorld(level)), DEFAULT_BLOCKS_PER_TICK, t.frontsPerJob(),
				(states, rates) -> BuildBudget.global().allocate(BUDGET_RUNNER, states, rates));
		pacers.add(p);
		return p;
	}

	/** Instant fallback builder (whole plan in one tick, no effects). */
	public static SiteBuilder instant(ServerLevel level) {
		return new InstantBuilder(new LevelBlockWorld(level));
	}

	public static void setBuilderFactory(Function<ServerLevel, SiteBuilder> factory) {
		builderFactory = factory;
		service = null;
	}

	/** One structure for every module (ignores the module spec). */
	public static void setStructure(StructureSource source) {
		setDesigner(ModuleDesigner.of(source));
	}

	public static void setDesigner(ModuleDesigner d) {
		designer = d;
		service = null;
	}

	/** Base module + growth envelope used for the next selection (an existing site keeps its own). */
	public static void setRequest(SiteRequest r) {
		request = r;
		service = null;
	}

	/** Lifecycle hooks; kept across worlds and service re-creation. */
	public static void addListener(SiteListener l) {
		listeners.add(l);
		if (service != null) {
			service.addListener(l);
		}
	}

	public static void removeListener(SiteListener l) {
		listeners.remove(l);
		if (service != null) {
			service.removeListener(l);
		}
	}

	/** The active site of this integrated server, or null. Server thread. */
	public static @Nullable SiteHandle handle(MinecraftServer s) {
		return service(s).handle();
	}

	/** Snapshot, level and build one more module. Server thread. */
	public static SiteService.Reply expand(MinecraftServer s, SiteModule module) {
		return service(s).expand(module);
	}

	/** Modules a move left to rebuild. Server thread. */
	public static List<SiteModule> modulesToRebuild(MinecraftServer s) {
		return service(s).modulesToRebuild();
	}

	/** The service for this integrated server (created on first use). Server thread. */
	public static SiteService service(MinecraftServer s) {
		if (s != server || service == null) {
			server = s;
			service = create(s);
		}
		return service;
	}

	public static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}

	private static SiteService create(MinecraftServer s) {
		SiteStore store = new SiteStore(s.getWorldPath(LevelResource.ROOT));
		Map<String, LevelBlockWorld> worlds = new HashMap<>();
		Map<String, LevelTerrain> terrains = new HashMap<>();
		SiteService svc = new SiteService(store, new SiteService.Env() {
			private @Nullable ServerLevel level(String dimension) {
				Identifier id = Identifier.tryParse(dimension);
				return id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
			}

			@Override
			public @Nullable BlockWorld world(String dimension) {
				ServerLevel l = level(dimension);
				return l == null ? null : worlds.computeIfAbsent(dimension, d -> new LevelBlockWorld(l));
			}

			@Override
			public @Nullable TerrainProbe terrain(String dimension) {
				ServerLevel l = level(dimension);
				return l == null ? null : terrains.computeIfAbsent(dimension, d -> new LevelTerrain(l));
			}

			@Override
			public SiteBuilder builder(String dimension) {
				ServerLevel l = level(dimension);
				if (l == null) {
					throw new IllegalStateException("dimension " + dimension + " is not loaded");
				}
				return builderFactory.apply(l);
			}

			@Override
			public long now() {
				return System.currentTimeMillis();
			}

			@Override
			public void flushWorld() {
				s.saveAllChunks(true, true, false);
			}

			@Override
			public void tell(String line) {
				T3Craft.LOGGER.info("[site] {}", line);
				for (ServerPlayer p : s.getPlayerList().getPlayers()) {
					if (s.isSingleplayerOwner(p.nameAndId())) {
						p.sendSystemMessage(Component.literal("[T3Craft] " + line));
					}
				}
			}
		}, designer, request);
		listeners.forEach(svc::addListener);
		return svc;
	}
}
