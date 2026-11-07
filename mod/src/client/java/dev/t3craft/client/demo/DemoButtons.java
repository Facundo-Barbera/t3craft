package dev.t3craft.client.demo;

import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * "T3Craft HQ" button on the title screen (right of Singleplayer) and on the world-select screen
 * (top right), added with Fabric's {@code ScreenEvents.AFTER_INIT}: no mixin.
 */
final class DemoButtons {
	private static final int W = 98;
	private static final int H = 20;

	private DemoButtons() {
	}

	static void register(DemoClient demo) {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof TitleScreen) && !(screen instanceof SelectWorldScreen)) {
				return;
			}
			if (!demo.loadConfig().titleButton()) {
				return;
			}
			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			int x;
			int y;
			if (screen instanceof TitleScreen) {
				AbstractWidget single = find(widgets, "menu.singleplayer");
				if (single != null && single.getRight() + 4 + W <= width - 2) {
					x = single.getRight() + 4;
					y = single.getY();
				} else {
					x = 4;
					y = 4;
				}
			} else {
				x = width >= 440 ? width - W - 6 : 6;
				y = 6;
			}
			widgets.add(button(mc, demo, screen, x, y));
		});
	}

	private static Button button(Minecraft mc, DemoClient demo, Screen screen, int x, int y) {
		return Button.builder(Component.translatable("t3craft.demo.button"), b -> demo.open(mc, screen, false))
				.bounds(x, y, W, H)
				.tooltip(Tooltip.create(Component.translatable("t3craft.demo.button.tooltip")))
				.build();
	}

	private static @Nullable AbstractWidget find(List<AbstractWidget> widgets, String key) {
		for (AbstractWidget w : widgets) {
			if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return w;
			}
		}
		return null;
	}
}
