package dev.t3craft.client.config;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * T3Craft settings as a screen of its own: {@link SettingsPanel} over the kit's backdrop. Opened from Mod
 * Menu, {@code /t3craft config} or the J key; inside {@code T3Screen} the same page opens in place of the
 * thread (⚙ in the sidebar). The panel holds the behaviour: see {@link SettingsPanel}.
 */
public final class SettingsScreen extends T3PanelScreen {
	public SettingsScreen(@Nullable Screen parent, EnvironmentActions actions) {
		super(parent, new SettingsPanel(actions));
	}
}
