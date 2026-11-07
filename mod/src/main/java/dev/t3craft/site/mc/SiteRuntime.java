package dev.t3craft.site.mc;

import dev.t3craft.T3Craft;
import dev.t3craft.site.BlockWorld;
import dev.t3craft.site.InstantBuilder;
import dev.t3craft.site.SiteBuilder;
import dev.t3craft.site.SiteService;
import dev.t3craft.site.SiteStore;
import dev.t3craft.site.StructureSource;
import dev.t3craft.site.TerrainProbe;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
 * <p>Integration points for Taller:
 * <ul>
 *   <li>{@link #setBuilderFactory}: plug the animated NPC builders ({@code feat/builders}); default
 *   {@link InstantBuilder}.</li>
 *   <li>{@link #setStructure}: plug the HQ design ({@code feat/hq-design}); default
 *   {@link StructureSource#PLACEHOLDER}.</li>
 * </ul>
 */
public final class SiteRuntime {
	private static @Nullable MinecraftServer server;
	private static @Nullable SiteService service;
	private static Function<ServerLevel, SiteBuilder> builderFactory = level -> new InstantBuilder(new LevelBlockWorld(level));
	private static StructureSource structure = StructureSource.PLACEHOLDER;
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
		});
	}

	public static void setBuilderFactory(Function<ServerLevel, SiteBuilder> factory) {
		builderFactory = factory;
		service = null;
	}

	public static void setStructure(StructureSource source) {
		structure = source;
		service = null;
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
		return new SiteService(store, new SiteService.Env() {
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
		}, structure);
	}
}
