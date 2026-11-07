package dev.t3craft.client.portable.held;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Layout of the bitmap font with a synthetic sheet (no Mojang asset). */
class BitmapFontTest {
	/** 2 columns × 1 row of 8×8 cells: 'A' lit in columns 0..4, 'i' lit in column 0 only. */
	static BitmapFont sheet() {
		int w = 16;
		int h = 8;
		int[] px = new int[w * h];
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x <= 4; x++) {
				px[y * w + x] = 0xFFFFFFFF;
			}
			px[y * w + 8] = 0xFFFFFFFF;
		}
		return BitmapFont.fromSheet(px, w, h, List.of("Ai"));
	}

	@Test
	void widthsFollowTheVanillaRule() {
		BitmapFont f = sheet();
		assertEquals(6, f.width("A"), "last lit column 4 + 2");
		assertEquals(2, f.width("i"), "last lit column 0 + 2");
		assertEquals(4, f.width(" "), "space advances 4");
		assertEquals(6 + 4 + 2, f.width("A i"));
		assertEquals(8, f.cellHeight());
	}

	@Test
	void accentsFoldToTheBaseGlyph() {
		BitmapFont f = sheet();
		assertEquals(f.width("A"), f.width("Á"));
		assertEquals('.', BitmapFont.fold('…'));
		assertEquals('-', BitmapFont.fold('—'));
	}

	@Test
	void drawPaintsGlyphPixelsAndClips() {
		BitmapFont f = sheet();
		int w = 20;
		int h = 10;
		int[] px = new int[w * h];
		int end = f.draw(px, w, h, 1, 1, "Ai", 0x55FFFF, w - 1);
		assertEquals(1 + 6 + 2, end);
		assertEquals(0xFF55FFFF, px[1 * w + 1], "top-left of A");
		assertEquals(0xFF55FFFF, px[8 * w + 5], "A's last lit column");
		assertEquals(0, px[1 * w + 6], "spacing column stays empty");
		assertEquals(0xFF55FFFF, px[1 * w + 7], "i starts after A's advance");

		int[] clipped = new int[w * h];
		int stop = f.draw(clipped, w, h, 0, 0, "AAAA", 0xFFFFFF, 10);
		assertEquals(6, stop, "a glyph that would cross maxX is not drawn");
	}

	@Test
	void clipAddsDots() {
		BitmapFont f = BitmapFont.fromSheet(new int[16 * 8], 16, 8, List.of("A."));
		String s = f.clip("AAAAAAAAAA", 10);
		assertTrue(s.endsWith("..."));
		assertTrue(f.width(s) <= 10, s);
		assertEquals("AA", f.clip("AA", 100));
	}

	@Test
	void classicGridPutsAsciiAtRowTimesSixteen() {
		List<String> g = BitmapFont.classicAsciiGrid();
		assertEquals(16, g.size());
		assertEquals('A', g.get(4).charAt(1));
		assertEquals(' ', g.get(2).charAt(0));
		assertEquals('\u0000', g.get(0).charAt(5));
	}

	@Test
	void boxesFallbackStillLaysOut() {
		BitmapFont f = BitmapFont.boxes();
		assertEquals(4, f.width(" "));
		assertTrue(f.width("ab") > 0);
	}
}
