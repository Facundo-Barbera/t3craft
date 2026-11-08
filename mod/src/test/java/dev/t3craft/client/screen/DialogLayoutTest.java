package dev.t3craft.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DialogLayoutTest {
	private static final int LINE = 9;

	/** Rows of the config screens: [by - 24, by - 4] and [by + 4, by + 24] with by = height - 52. */
	private static boolean overlapsButtonRow(int textY, int height) {
		int by = height - 52;
		int[][] rows = {{by - 24, by - 4}, {by + 4, by + 24}};
		for (int[] r : rows) {
			if (textY < r[1] && textY + LINE > r[0]) {
				return true;
			}
		}
		return false;
	}

	@Test
	void statusLineNeverSitsOnAButtonRow() {
		for (int h = 120; h <= 1080; h++) {
			int y = DialogLayout.statusY(h, LINE);
			assertFalse(overlapsButtonRow(y, h), "height " + h + " → y " + y);
			assertTrue(y + LINE <= h, "on screen at height " + h);
		}
	}

	@Test
	void oldStatusPositionDidOverlap() {
		assertTrue(overlapsButtonRow(240 - 64, 240), "regression guard for the old height - 64");
	}

	@Test
	void linesThatFitClampsToTheLimit() {
		assertEquals(3, DialogLayout.linesThatFit(100, 9, 128, 10));
		assertEquals(2, DialogLayout.linesThatFit(100, 9, 128, 2));
		assertEquals(0, DialogLayout.linesThatFit(100, 9, 100, 5));
		assertEquals(0, DialogLayout.linesThatFit(100, 9, 90, 5));
		assertEquals(0, DialogLayout.linesThatFit(100, 0, 200, 5));
	}

	@Test
	void buttonsGoBelowTallText() {
		assertEquals(160, DialogLayout.buttonsBelow(240, 160, 120), "short text: preferred row");
		assertEquals(176, DialogLayout.buttonsBelow(240, 160, 170), "tall text pushes the row down");
		assertEquals(216, DialogLayout.buttonsBelow(240, 160, 230), "but never off screen");
		assertEquals(0, DialogLayout.buttonsBelow(10, 0, 0));
	}

	@Test
	void submitKeys() {
		assertTrue(DialogLayout.isSubmit(DialogLayout.KEY_ENTER));
		assertTrue(DialogLayout.isSubmit(DialogLayout.KEY_KP_ENTER));
		assertTrue(DialogLayout.isSubmit(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN));
		assertTrue(DialogLayout.isSubmit(com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER));
		assertFalse(DialogLayout.isSubmit(257), "GLFW's Enter is not Minecraft 26's Enter");
		assertFalse(DialogLayout.isSubmit(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE));
	}

	@Test
	void envRowSideBySideWhenWide() {
		DialogLayout.EnvRow r = DialogLayout.EnvRow.of(470, 258, 90);
		assertFalse(r.stacked());
		assertEquals(DialogLayout.EnvRow.ROW, r.rowH());
		assertEquals(212, r.buttonsX());
		assertEquals(0, r.buttonsY());
		assertEquals(196, r.textW());
	}

	@Test
	void envRowStacksWhenNarrowAndButtonsStayInside() {
		for (int w = 200; w < 364; w++) {
			DialogLayout.EnvRow r = DialogLayout.EnvRow.of(w, 258, 90);
			assertTrue(r.stacked(), "width " + w);
			assertTrue(r.buttonsX() >= 0, "buttons never left of the row at width " + w);
			assertTrue(r.buttonsY() + 20 <= r.rowH(), "buttons inside the row");
			assertTrue(r.textW() >= 90 || w < 102, "text gets the full width");
		}
	}

	@Test
	void rowsPerPageAtLeastOne() {
		assertEquals(1, DialogLayout.rowsPerPage(36, 30, 24));
		assertEquals(5, DialogLayout.rowsPerPage(36, 160, 24));
	}
}
