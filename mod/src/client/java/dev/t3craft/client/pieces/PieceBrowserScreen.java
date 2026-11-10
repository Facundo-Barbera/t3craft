package dev.t3craft.client.pieces;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The piece browser as a screen of its own: {@link PieceBrowserPanel} over the kit's backdrop, wider than the
 * settings pages because it has three columns. Opened by the {@code K} key (rebindable), {@code /t3craft pieces},
 * Settings → Connections → "Piece browser…", and in place of the thread inside {@code T3Screen} (⋯ menu → Pieces).
 */
public final class PieceBrowserScreen extends T3PanelScreen {
	public PieceBrowserScreen(@Nullable Screen parent) {
		this(parent, new PieceBrowserPanel());
	}

	public PieceBrowserScreen(@Nullable Screen parent, PieceBrowserPanel panel) {
		super(parent, panel);
	}

	@Override
	protected int maxWidth() {
		return 760;
	}

	@Override
	protected int maxHeight() {
		return 460;
	}
}
