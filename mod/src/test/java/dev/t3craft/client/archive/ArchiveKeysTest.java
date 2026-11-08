package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

class ArchiveKeysTest {
	@Test
	void pageKeysMatchMinecraftsCodes() {
		assertEquals(InputConstants.KEY_PAGEUP, ArchiveKeys.KEY_PAGE_UP);
		assertEquals(InputConstants.KEY_PAGEDOWN, ArchiveKeys.KEY_PAGE_DOWN);
	}

	@Test
	void pageStep() {
		assertEquals(-1, ArchiveKeys.pageStep(InputConstants.KEY_PAGEUP));
		assertEquals(1, ArchiveKeys.pageStep(InputConstants.KEY_PAGEDOWN));
		assertEquals(0, ArchiveKeys.pageStep(InputConstants.KEY_UP));
	}

	@Test
	void pagingTheListClampsAtBothEnds() {
		ArchiveList list = new ArchiveList();
		list.step(ArchiveKeys.pageStep(InputConstants.KEY_PAGEUP));
		assertEquals(0, list.page(java.util.List.of(), 10).index());
	}

	@Test
	void onlyMinecraftsLeftButtonOpensARow() {
		// the archive's row filter: GLFW's 0 is not a left click in 26.x
		org.junit.jupiter.api.Assertions.assertTrue(dev.t3craft.client.chat.MouseButtons.isLeft(InputConstants.MOUSE_BUTTON_LEFT));
		org.junit.jupiter.api.Assertions.assertFalse(dev.t3craft.client.chat.MouseButtons.isLeft(0));
		org.junit.jupiter.api.Assertions.assertFalse(dev.t3craft.client.chat.MouseButtons.isLeft(InputConstants.MOUSE_BUTTON_RIGHT));
	}
}
