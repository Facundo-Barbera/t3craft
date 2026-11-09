package dev.t3craft.client.config;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Paired T3 environments as a screen of its own: {@link EnvironmentsPanel} over the kit's backdrop (the
 * portable terminal's button, the environments key, Mod Menu → Settings → Connections). Inside {@code T3Screen}
 * the same page opens in place of the thread (☁ in the sidebar).
 */
public final class EnvironmentsScreen extends T3PanelScreen {
	public EnvironmentsScreen(@Nullable Screen parent, EnvironmentActions actions) {
		super(parent, new EnvironmentsPanel(actions));
	}
}
