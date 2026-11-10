package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.hq.layout.HqLayoutConfig;
import dev.t3craft.client.hq.layout.HqLayoutState;
import dev.t3craft.client.hq.layout.HqRebuild;
import dev.t3craft.client.islands.IslandsClient;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.mapgen.MapTypes;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Where the HQ menu meets feat/hq-layouts: the registry ({@link HqLayouts}), the {@code hqLayout}
 * config key ({@link HqLayoutConfig}), the built layout ({@link HqLayoutState}) and the single
 * reset + rebuild entry point ({@link HqRebuild}), plus the map type registry ({@link MapTypes}) with its
 * {@code mapType} config key ({@link MapTypeConfig}) and the code that opens each type's world. Kept apart so
 * {@link HqMenuModel} and {@link MapTypeModel} stay pure.
 */
final class HqMenuBridge {
	private HqMenuBridge() {
	}

	/** A fresh model: the configured layout, the one built in the HQ world (read from its save), whether it exists. */
	static HqMenuModel model(DemoClient demo) {
		Minecraft mc = Minecraft.getInstance();
		DemoConfig cfg = demo.loadConfig();
		String folder = cfg.spec().folderId();
		boolean exists = VanillaDemoWorld.exists(mc, folder);
		return new HqMenuModel(HqLayouts.menuOrder(), HqLayoutConfig.selected().id(), exists ? builtLayoutId(mc, folder) : null, exists,
				HqMenuBridge::persist);
	}

	/** A fresh map type model: the whole {@link MapTypes} registry and the configured {@code mapType}. */
	static MapTypeModel mapTypes() {
		return new MapTypeModel(MapTypes.all(), MapTypeConfig.selectedId(), HqMenuBridge::persistMapType);
	}

	/** Whether the menu knows how to open the world of {@code type} (the others show the command that does). */
	static boolean canOpen(MapTypes type) {
		return type.id().equals(MapTypes.CITADEL.id()) || type.id().equals(MapTypes.FLOATING_ISLANDS.id());
	}

	/** Opens (or creates) the world of {@code type} by the code the matching command uses; {@code parent} is the screen to come back to. */
	static void open(DemoClient demo, MapTypes type, Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		if (type.id().equals(MapTypes.FLOATING_ISLANDS.id())) {
			IslandsClient.open(mc, false, parent);
		} else {
			demo.open(mc, parent, false); // the citadel: the HQ world, as the button always did
		}
	}

	// ---- reset of a map type's own world (the floating islands) ---------------------------------------------------

	/**
	 * The save folder of a map type's own world that the menu can reset, or null. The citadel is not here: its
	 * reset also rebuilds, and goes through {@link HqRebuild}.
	 */
	private static @Nullable String ownFolder(MapTypes type) {
		return type.id().equals(MapTypes.FLOATING_ISLANDS.id()) ? IslandsClient.worldFolder() : null;
	}

	static boolean canReset(MapTypes type) {
		return ownFolder(type) != null;
	}

	static boolean worldExists(MapTypes type) {
		String folder = ownFolder(type);
		return folder != null && VanillaDemoWorld.exists(Minecraft.getInstance(), folder);
	}

	/** Why the world of {@code type} cannot be reset now (a world is open, or it does not exist), or null. */
	static @Nullable String resetRefusal(MapTypes type) {
		String folder = ownFolder(type);
		Minecraft mc = Minecraft.getInstance();
		if (folder == null) {
			return T3Draw.tr("t3craft.hqmenu.hq_only");
		}
		if (mc.level != null) {
			return T3Draw.tr("t3craft.hqmenu.reset_map.refuse_world");
		}
		if (!VanillaDemoWorld.exists(mc, folder)) {
			return T3Draw.tr("t3craft.hqmenu.reset_map.refuse_none", HqMenuPanel.typeName(type));
		}
		return null;
	}

	/** The question's text: the type's name and folder, and whether a zip backup is made first. */
	static String resetBody(DemoClient demo, MapTypes type) {
		String folder = ownFolder(type);
		boolean backup = HqReset.shouldBackup(demo.loadConfig());
		return T3Draw.tr(backup ? "t3craft.hqmenu.reset_map.body" : "t3craft.hqmenu.reset_map.body_nobackup", HqMenuPanel.typeName(type),
				folder == null ? "" : folder);
	}

	/**
	 * Deletes the world of {@code type}: {@link HqReset#validate} first (only a real {@code saves/<folder>}), the zip
	 * backup unless {@code demo.resetBackup} is off (a failed backup deletes nothing), then {@link HqReset#delete}. It
	 * does not create the world again: Open does, with a fresh field. Returns the result line.
	 */
	static String resetWorld(DemoClient demo, MapTypes type) {
		String why = resetRefusal(type);
		String folder = ownFolder(type);
		if (why != null || folder == null) {
			return why != null ? why : "";
		}
		Minecraft mc = Minecraft.getInstance();
		Path saves = VanillaDemoWorld.savesDir(mc);
		HqReset.Check check = HqReset.validate(saves, folder);
		Path target = check.target();
		if (target == null) {
			T3Craft.LOGGER.warn("[demo] reset {}: refused, {}", folder, check.reason());
			return T3Draw.tr("t3craft.hqmenu.reset_map.failed", check.reason());
		}
		String backup = T3Draw.tr("t3craft.hqmenu.reset_map.nobackup");
		if (HqReset.shouldBackup(demo.loadConfig())) {
			try {
				Path zip = HqReset.zip(target, VanillaDemoWorld.backupDir(mc), folder, LocalDateTime.now());
				backup = T3Draw.tr("t3craft.hqmenu.reset_map.backup", zip.getFileName().toString());
				T3Craft.LOGGER.info("[demo] reset {}: backup written to {}", folder, zip);
			} catch (IOException | RuntimeException e) {
				T3Craft.LOGGER.error("[demo] reset {}: backup failed, world left untouched", folder, e);
				return T3Draw.tr("t3craft.hqmenu.reset_map.failed", "backup failed, nothing deleted (" + e.getClass().getSimpleName() + ")");
			}
		}
		try {
			HqReset.delete(saves, folder);
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.error("[demo] reset {}: could not delete the world", folder, e);
			return T3Draw.tr("t3craft.hqmenu.reset_map.failed", "could not delete the old world (" + e.getClass().getSimpleName() + "); " + backup);
		}
		T3Craft.LOGGER.info("[demo] reset: deleted saves/{} ({})", folder, backup);
		return T3Draw.tr("t3craft.hqmenu.reset_map.done", HqMenuPanel.typeName(type), backup);
	}

	private static void persistMapType(String typeId) {
		ConfigManager.SaveResult r = MapTypeConfig.select(typeId);
		if (r.ok()) {
			T3Craft.LOGGER.info("[demo] mapType = {}", typeId);
		} else {
			T3Craft.LOGGER.warn("[demo] mapType = {} not saved: {}", typeId, r.message());
		}
	}

	/**
	 * Inside the HQ world: its live marker. From the menus (no world open): the marker of
	 * {@code saves/<folder>}, read only, and only once {@link HqReset#validate} accepts the folder.
	 */
	private static @Nullable String builtLayoutId(Minecraft mc, String folder) {
		String live = HqLayoutState.builtLayoutId();
		if (live != null || mc.level != null) {
			return live;
		}
		HqReset.Check c = HqReset.validate(VanillaDemoWorld.savesDir(mc), folder);
		Path root = c.target();
		try {
			return root == null ? null : HqLayoutState.builtLayoutId(root);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("[demo] could not read the HQ layout of saves/{} ({})", folder, e.getClass().getSimpleName());
			return null;
		}
	}

	private static void persist(String layoutId) {
		ConfigManager.SaveResult r = HqLayoutConfig.select(layoutId);
		if (r.ok()) {
			T3Craft.LOGGER.info("[demo] hqLayout = {}", layoutId);
		} else {
			T3Craft.LOGGER.warn("[demo] hqLayout = {} not saved: {}", layoutId, r.message());
		}
	}

	/** Why a reset cannot run now, or null. */
	static @Nullable String refusal() {
		return HqRebuild.refusal();
	}

	/** Saves the layout and queues the confirmed reset (backup, delete, recreate); returns a refusal or null. */
	static @Nullable String resetAndRebuild(String layoutId) {
		return HqRebuild.resetAndRebuild(layoutId);
	}
}
