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
import org.jspecify.annotations.Nullable;

/**
 * Every call into the vanilla world-creation / world-loading API lives here, because it is the
 * part that breaks between Minecraft versions (written against 26.3: {@code WorldOpenFlows},
 * {@code LevelSettings.DifficultySettings}, {@code net.minecraft.world.level.gamerules}). When
 * porting, only this class should need changes. No mixins.
 */
final class VanillaDemoWorld {
	private VanillaDemoWorld() {
	}

	static boolean exists(Minecraft mc, String folderId) {
		return mc.getLevelSource().levelExists(folderId);
	}

	/**
	 * Creates the superflat creative world (cheats on, peaceful, no structures, fixed seed) and
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
		mc.createWorldOpenFlows().createFreshLevel(spec.folderId(), settings, options,
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
						.getOrThrow(DemoWorldSpec.SUPERFLAT ? WorldPresets.FLAT : WorldPresets.NORMAL)
						.value().createWorldDimensions(),
				parent);
	}

	/** Opens an existing save (vanilla backup/upgrade prompts included); cancel returns to {@code parent}. */
	static void open(Minecraft mc, String folderId, Screen parent) {
		mc.createWorldOpenFlows().openWorld(folderId, () -> mc.gui.setScreen(parent));
	}

	/** Saves and leaves the current world (blocks until the integrated server has stopped). */
	static void leaveWorld(Minecraft mc) {
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

	static Path worldRoot(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	/** Whether this (integrated) server runs the save folder {@code folderId}. */
	static boolean isDemoServer(MinecraftServer server, String folderId) {
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
		GameRules rules = server.getGameRules();
		for (Map.Entry<DemoWorldSpec.Rule, Boolean> e : spec.rules().entrySet()) {
			rules.set(rule(e.getKey()), e.getValue(), server);
		}
		int[] view = DemoPlacement.viewpoint(spec, server.overworld().getMinY());
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
