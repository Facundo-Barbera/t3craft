package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

/**
 * Every call into the vanilla world-creation / world-loading API lives here, because it is the
 * part that breaks between Minecraft versions (written against 26.3: {@code WorldOpenFlows},
 * {@code LevelSettings.DifficultySettings}, {@code net.minecraft.world.level.gamerules}). When
 * porting, only this class should need changes. No mixins.
 */
public final class VanillaDemoWorld {
	private VanillaDemoWorld() {
	}

	public static boolean exists(Minecraft mc, String folderId) {
		return mc.getLevelSource().levelExists(folderId);
	}

	/**
	 * feat/algo-map-generators: creates a creative, peaceful, no-structures void world (the same superflat "void"
	 * layers as the HQ world) in save folder {@code folderId} and joins it; for map types that build their own
	 * ground at run time. Vanilla shows its loading screens; on failure it returns to {@code parent}.
	 */
	public static void createVoidWorld(Minecraft mc, String folderId, String displayName, long seed, Screen parent) {
		LevelSettings settings = new LevelSettings(displayName, GameType.CREATIVE,
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(seed, false, false);
		mc.createWorldOpenFlows().createFreshLevel(folderId, settings, options, registries -> {
			WorldDimensions dims = registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
			return dims.replaceOverworldGenerator(registries, new FlatLevelSource(voidSettings(registries)));
		}, parent);
	}

	/**
	 * Creates the superflat (void, feat/hq-void-world, when {@code spec.voidWorld()}) creative world (cheats on, peaceful, no structures, fixed seed) and
	 * joins it. Vanilla shows its own loading screens; on failure it returns to {@code parent}.
	 * Game rules and time are applied by {@link #applySetup} once the server is up.
	 */
	static void create(Minecraft mc, DemoWorldSpec spec, Screen parent) {
		LevelSettings settings = new LevelSettings(spec.displayName(),
				DemoWorldSpec.CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL,
				new LevelSettings.DifficultySettings(DemoWorldSpec.PEACEFUL ? Difficulty.PEACEFUL : Difficulty.NORMAL, false, false),
				DemoWorldSpec.CHEATS,
				WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(spec.seed(), DemoWorldSpec.STRUCTURES, false);
		mc.createWorldOpenFlows().createFreshLevel(spec.folderId(), settings, options, registries -> {
			WorldDimensions dims = registries.lookupOrThrow(Registries.WORLD_PRESET)
					.getOrThrow(DemoWorldSpec.SUPERFLAT ? WorldPresets.FLAT : WorldPresets.NORMAL)
					.value().createWorldDimensions();
			return spec.voidWorld() ? dims.replaceOverworldGenerator(registries, new FlatLevelSource(voidSettings(registries))) : dims;
		}, parent);
	}

	/**
	 * feat/hq-void-world: the classic "The Void" superflat layers (one air layer, biome
	 * {@code minecraft:the_void}), without its decoration (no stone start platform) and without
	 * structures: nothing but what the mod places.
	 */
	private static FlatLevelGeneratorSettings voidSettings(HolderLookup.Provider registries) {
		HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
		FlatLevelGeneratorSettings base = FlatLevelGeneratorSettings.getDefault(biomes, registries.lookupOrThrow(Registries.STRUCTURE_SET),
				registries.lookupOrThrow(Registries.PLACED_FEATURE));
		HolderSet<StructureSet> none = HolderSet.direct(List.<Holder<StructureSet>>of());
		return base.withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.AIR)), Optional.of(none), biomes.getOrThrow(Biomes.THE_VOID));
	}

	/**
	 * Whether the running world is a void world: its overworld is a superflat whose layers are all air.
	 * Read from the save itself, so HQ worlds created before feat/hq-void-world stay flat.
	 */
	static boolean isVoid(MinecraftServer server) {
		ChunkGenerator g = server.overworld().getChunkSource().getGenerator();
		if (!(g instanceof FlatLevelSource flat)) {
			return false;
		}
		// vanilla stores air layers as null in getLayers() (FlatLevelSource skips nulls): a null layer is air
		for (BlockState s : flat.settings().getLayers()) {
			if (s != null && !s.isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * feat/hq-void-world: places the starter island (ground, tree, chest, bridge) right away on the
	 * server thread, before the player joins, and returns its centre cell (world coordinates).
	 */
	static DemoMarker.Cell placeStarter(MinecraftServer server, DemoWorldSpec spec, VoidIsland.Starter starter) {
		ServerLevel level = server.overworld();
		BlockPos origin = new BlockPos(spec.originX(), spec.originY(), spec.originZ());
		for (dev.t3craft.build.Placement p : VoidIsland.starterBlocks(starter)) {
			level.setBlock(origin.offset(p.pos()), p.state(), Block.UPDATE_ALL);
		}
		return new DemoMarker.Cell(origin.getX() + starter.x(), origin.getY() + starter.y(), origin.getZ() + starter.z());
	}

	/**
	 * feat/hq-spawn-start: places the single block under the HQ centre (the plan's own floor block, or a
	 * neutral one the plan replaces) when that cell is still empty. Server thread.
	 */
	static boolean placeCentreBlock(MinecraftServer server, HqCentre centre) {
		ServerLevel level = server.overworld();
		if (!level.getBlockState(centre.floor()).isAir()) {
			return false;
		}
		level.setBlock(centre.floor(), centre.floorBlock(), Block.UPDATE_ALL);
		return true;
	}

	/** The overworld's spawn cell (where {@code /setworldspawn} put it). */
	static BlockPos worldSpawn(MinecraftServer server) {
		return server.overworld().getRespawnData().pos();
	}

	/** feat/hq-spawn-start: world spawn on {@code feet} with {@code yaw}, exactly there (respawn radius 0). Server thread. */
	static void setWorldSpawn(MinecraftServer server, BlockPos feet, float yaw) {
		ServerLevel level = server.overworld();
		level.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), feet, yaw, 0.0F));
		server.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
	}

	/** Puts the player on a world cell (feet), looking south (towards the HQ). */
	static void teleportTo(MinecraftServer server, String playerName, BlockPos feet) {
		command(server, String.format(Locale.ROOT, "tp %s %d %d %d 0 10", playerName, feet.getX(), feet.getY(), feet.getZ()));
	}

	/** Opens an existing save (vanilla backup/upgrade prompts included); cancel returns to {@code parent}. */
	public static void open(Minecraft mc, String folderId, Screen parent) {
		mc.createWorldOpenFlows().openWorld(folderId, () -> mc.gui.setScreen(parent));
	}

	/** Saves and leaves the current world (blocks until the integrated server has stopped). */
	public static void leaveWorld(Minecraft mc) {
		mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
	}

	/** The {@code saves/} directory. */
	static Path savesDir(Minecraft mc) {
		return mc.getLevelSource().getBaseDir();
	}

	/** Minecraft's own {@code backups/} directory (next to {@code saves/}). */
	static Path backupDir(Minecraft mc) {
		return mc.getLevelSource().getBackupPath();
	}

	public static Path worldRoot(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	/** Whether this (integrated) server runs the save folder {@code folderId}. */
	public static boolean isDemoServer(MinecraftServer server, String folderId) {
		if (!server.isSingleplayer()) {
			return false;
		}
		@Nullable Path name = worldRoot(server).getFileName();
		return name != null && name.toString().equals(folderId);
	}

	/**
	 * One-time world setup on the server thread: forced game rules, fixed time of day, spawn point
	 * in front of the build site. Time and spawn go through vanilla commands (their syntax is far
	 * more stable than the 26.x clock API).
	 */
	static void applySetup(MinecraftServer server, DemoWorldSpec spec) {
		applySetup(server, spec, null);
	}

	/** {@link #applySetup(MinecraftServer, DemoWorldSpec)} with the world spawn on {@code spawn} (the starter island) when given. */
	public static void applySetup(MinecraftServer server, DemoWorldSpec spec, @Nullable BlockPos spawn) {
		GameRules rules = server.getGameRules();
		for (Map.Entry<DemoWorldSpec.Rule, Boolean> e : spec.rules().entrySet()) {
			rules.set(rule(e.getKey()), e.getValue(), server);
		}
		if (spawn != null) {
			rules.set(GameRules.RESPAWN_RADIUS, 0, server); // exactly on the island, never a random spot over the void
		}
		int[] view = spawn != null ? new int[] {spawn.getX(), spawn.getY(), spawn.getZ()} : DemoPlacement.viewpoint(spec, server.overworld().getMinY());
		command(server, "time set " + spec.dayTime());
		command(server, "weather clear");
		command(server, String.format(Locale.ROOT, "setworldspawn %d %d %d", view[0], view[1], view[2]));
	}

	/** Puts the player at the viewpoint, looking at the site (south, the plan's growth direction). */
	static void teleportToViewpoint(MinecraftServer server, String playerName, DemoWorldSpec spec) {
		int[] v = DemoPlacement.viewpoint(spec, server.overworld().getMinY());
		command(server, String.format(Locale.ROOT, "tp %s %d %d %d 0 10", playerName, v[0], v[1], v[2]));
	}

	private static GameRule<Boolean> rule(DemoWorldSpec.Rule r) {
		return switch (r) {
			case ADVANCE_TIME -> GameRules.ADVANCE_TIME;
			case ADVANCE_WEATHER -> GameRules.ADVANCE_WEATHER;
			case SPAWN_MOBS -> GameRules.SPAWN_MOBS;
			case SPAWN_MONSTERS -> GameRules.SPAWN_MONSTERS;
			case SPAWN_PATROLS -> GameRules.SPAWN_PATROLS;
			case SPAWN_PHANTOMS -> GameRules.SPAWN_PHANTOMS;
			case SPAWN_WANDERING_TRADERS -> GameRules.SPAWN_WANDERING_TRADERS;
		};
	}

	private static void command(MinecraftServer server, String cmd) {
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("[demo] setup command failed: {}", cmd, e);
		}
	}
}
