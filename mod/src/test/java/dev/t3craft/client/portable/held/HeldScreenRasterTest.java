package dev.t3craft.client.portable.held;

import static dev.t3craft.client.portable.held.HeldFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeldScreenRasterTest {
	@Test
	void screenMatchesTheSpriteAspect() {
		assertEquals(6 * 16, HeldScreenRaster.WIDTH);
		assertEquals(9 * 16, HeldScreenRaster.HEIGHT);
		assertTrue(HeldScreenRaster.maxRows() >= 10);
	}

	@Test
	void framePanelHeaderAndChipColours() {
		List<HeldScreenModel.Row> rows = HeldScreenModel.build(input(connected(
				thread("vps", "n", "Needs me", "needs_attention", 0, "Waiting for your answer", false))));
		int[] px = HeldScreenRaster.render(rows, BitmapFont.boxes());
		int w = HeldScreenRaster.WIDTH;
		assertEquals(0xFF000000 | HeldScreenModel.BORDER, px[0], "frame");
		assertEquals(0xFF000000 | HeldScreenModel.HEADER_BG, px[2 * w + w - 3], "header band");
		assertEquals(0xFF000000 | HeldScreenModel.BG, px[(HeldScreenRaster.HEIGHT - 3) * w + w / 2], "panel");
		int topRowY = 1 + HeldScreenRaster.HEADER_H + HeldScreenRaster.PAD - 1;
		assertEquals(0xFF000000 | HeldScreenModel.SELECTED, px[(topRowY + 4) * w + w - 3], "top thread highlighted");
		int chip = dev.t3craft.client.chat.EnvBadge.of("vps", "my-vps", null).background();
		assertEquals(chip, px[topRowY * w + HeldScreenRaster.PAD], "environment chip");
		for (int p : px) {
			assertEquals(0xFF, p >>> 24, "opaque: no see-through holes in the screen");
		}
	}

	@Test
	void sameRowsSamePixels() {
		List<HeldScreenModel.Row> rows = HeldScreenModel.build(input(connected()));
		assertArrayEquals(HeldScreenRaster.render(rows, BitmapFont.boxes()), HeldScreenRaster.render(rows, BitmapFont.boxes()));
	}
}
