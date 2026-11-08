package dev.t3craft.client.archive;

/** Keyboard paging of the archive screen (Minecraft key codes, compile-time constants), unit-tested. */
final class ArchiveKeys {
	static final int KEY_PAGE_UP = com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP;
	static final int KEY_PAGE_DOWN = com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN;

	private ArchiveKeys() {
	}

	/** -1 for Page Up, +1 for Page Down, 0 for any other key. */
	static int pageStep(int key) {
		return key == KEY_PAGE_UP ? -1 : key == KEY_PAGE_DOWN ? 1 : 0;
	}
}
