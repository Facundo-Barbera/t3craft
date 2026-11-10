package dev.t3craft.client.queue;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The run queue as a screen of its own: {@link QueuePanel} over the kit's backdrop ({@code /t3craft queue},
 * the gallery). Watches the focused thread plus the working threads while it is shown; leaving it (Esc, X, or
 * another screen taking its place) drops the watches, and they come back when it is laid out again.
 */
public final class QueueScreen extends T3PanelScreen {
	private final QueuePanel panel;

	public QueueScreen(@Nullable Screen parent, QueueStore.@Nullable Key focus) {
		this(parent, new QueuePanel(QueuePanel.live(focus)));
	}

	/** Over any source (the gallery's fake queues). */
	public QueueScreen(@Nullable Screen parent, QueuePanel panel) {
		super(parent, panel);
		this.panel = panel;
	}

	@Override
	protected int maxWidth() {
		return 500;
	}

	@Override
	public void removed() {
		panel.release();
		super.removed();
	}
}
