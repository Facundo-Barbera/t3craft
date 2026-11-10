package dev.t3craft.client.archive;

import dev.t3craft.client.t3ui.kit.T3PanelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The archive as a screen of its own: {@link ArchivePanel} over the kit's backdrop. Opened by the archive
 * terminal block, {@code /t3craft archive} and the portable's "Archive" button (all through
 * {@link ArchiveClient#openArchive}). Read-only: nothing here writes to T3.
 */
public final class ArchiveScreen extends T3PanelScreen {
	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener) {
		this(store, opener, new ArchiveList());
	}

	/** {@code list}: the search and filters to show (kept by the caller to come back to the same search). */
	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener, ArchiveList list) {
		this(store, opener, list, null);
	}

	/** {@code parent}: the screen Esc / X return to (null = close to the game). */
	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener, ArchiveList list, @Nullable Screen parent) {
		this(parent, new ArchivePanel(ArchivePanel.of(store), opener, list));
	}

	/** Over any page (the gallery's fake archive). */
	public ArchiveScreen(@Nullable Screen parent, ArchivePanel panel) {
		super(parent, panel);
	}

	@Override
	protected int maxWidth() {
		return 520;
	}
}
