package dev.t3craft.client.plan;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The plan as a screen of its own: {@link PlanPanel} over the kit's backdrop. Opened by {@code /t3craft plan}, the
 * "Open plan" key, the office's decision podiums (Desk) and its merge station (Review). Opening it sends nothing.
 */
public final class PlanScreen extends T3PanelScreen {
	public PlanScreen(@Nullable Screen parent, PlanPanel.View view) {
		super(parent, new PlanPanel(PlanClient.store(), view));
	}

	@Override
	protected int maxWidth() {
		return 540;
	}

	@Override
	protected int maxHeight() {
		return 420;
	}
}
