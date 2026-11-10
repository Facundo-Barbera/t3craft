package dev.t3craft.client.demo;

import dev.t3craft.client.hq.layout.HqLayoutPackWatcher;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.mapgen.MapTypes;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The HQ menu, opened by the "T3Craft HQ" button of the title and world-select screens ({@link DemoButtons}):
 * {@link HqMenuPanel} as a screen of its own over the kit's backdrop. A plain {@code Screen} host (not
 * {@code T3Screen}): there is no world, no sidebar and no thread here. Esc or X returns to {@code parent}.
 * See {@code docs/features/hq-menu.md} for why the reset runs from the menu with no world open.
 */
public final class HqMenuScreen extends T3PanelScreen {
	HqMenuScreen(@Nullable Screen parent, DemoClient demo) {
		super(parent, new HqMenuPanel(HqMenuBridge.model(demo), HqMenuBridge.mapTypes(), actions(demo)));
	}

	/** Over any model and actions (the gallery's fake menu). */
	public HqMenuScreen(@Nullable Screen parent, HqMenuPanel panel) {
		super(parent, panel);
	}

	@Override
	protected int maxWidth() {
		return 380;
	}

	/** The demo's real actions: open the world, the reset entry point, the layout packs. */
	static HqMenuPanel.Actions actions(DemoClient demo) {
		return new HqMenuPanel.Actions() {
			@Override
			public boolean canOpen(MapTypes type) {
				return HqMenuBridge.canOpen(type);
			}

			@Override
			public void open(Screen parent, MapTypes type) {
				HqMenuBridge.open(demo, type, parent);
			}

			@Override
			public @Nullable String refusal() {
				return HqMenuBridge.refusal();
			}

			@Override
			public String resetBody(HqLayoutInfo layout) {
				DemoConfig cfg = demo.loadConfig();
				return T3Draw.tr(HqReset.shouldBackup(cfg) ? "t3craft.hqmenu.reset.body" : "t3craft.hqmenu.reset.body_nobackup",
						cfg.spec().displayName(), cfg.spec().folderId(), T3Draw.tr(layout.nameKey()));
			}

			@Override
			public @Nullable String resetAndRebuild(String layoutId) {
				return HqMenuBridge.resetAndRebuild(layoutId);
			}

			@Override
			public boolean canReset(MapTypes type) {
				return HqMenuBridge.canReset(type);
			}

			@Override
			public boolean worldExists(MapTypes type) {
				return HqMenuBridge.worldExists(type);
			}

			@Override
			public @Nullable String refusal(MapTypes type) {
				return HqMenuBridge.resetRefusal(type);
			}

			@Override
			public String resetBody(MapTypes type) {
				return HqMenuBridge.resetBody(demo, type);
			}

			@Override
			public String resetWorld(MapTypes type) {
				return HqMenuBridge.resetWorld(demo, type);
			}

			@Override
			public String exportPacks() {
				return HqLayoutPackWatcher.exportNow();
			}

			@Override
			public String reloadPacks() {
				return HqLayoutPackWatcher.reloadNow();
			}
		};
	}
}
