package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.hq.layout.HqLayoutConfig;
import dev.t3craft.client.hq.layout.HqLayoutState;
import dev.t3craft.client.hq.layout.HqRebuild;
import dev.t3craft.hq.layout.HqLayouts;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Where the HQ menu meets feat/hq-layouts: the registry ({@link HqLayouts}), the {@code hqLayout}
 * config key ({@link HqLayoutConfig}), the built layout ({@link HqLayoutState}) and the single
 * reset + rebuild entry point ({@link HqRebuild}). Kept apart so {@link HqMenuModel} stays pure.
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
		return new HqMenuModel(HqLayouts.all(), HqLayoutConfig.selected().id(), exists ? builtLayoutId(mc, folder) : null, exists,
				HqMenuBridge::persist);
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
