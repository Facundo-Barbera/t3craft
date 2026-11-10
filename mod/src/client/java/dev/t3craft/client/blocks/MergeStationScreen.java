package dev.t3craft.client.blocks;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The list a merge station opens: {@link MergeStationPanel} as a screen of its own over the kit's backdrop.
 * Read only: nothing here merges.
 */
public final class MergeStationScreen extends T3PanelScreen {
	MergeStationScreen(@Nullable Screen parent, String bindingLabel, Supplier<MergeFeed.Summary> source, Consumer<MergeFeed.Entry> openChat) {
		this(parent, new MergeStationPanel(bindingLabel, source, openChat));
	}

	/** Over any page (the gallery's fake pull requests). */
	public MergeStationScreen(@Nullable Screen parent, MergeStationPanel panel) {
		super(parent, panel);
	}

	@Override
	protected int maxWidth() {
		return 500;
	}
}
