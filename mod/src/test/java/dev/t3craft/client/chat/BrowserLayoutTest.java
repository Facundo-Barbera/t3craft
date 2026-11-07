package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BrowserLayoutTest {
	/** Vanilla "auto" GUI scale: the largest scale that keeps at least 320×240 scaled pixels. */
	static int autoScale(int w, int h) {
		int s = 1;
		while (w / (s + 1) >= 320 && h / (s + 1) >= 240) {
			s++;
		}
		return s;
	}

	/** Vanilla guiWidth/guiHeight: window size divided by the scale, rounded up. */
	static int gui(int px, int scale) {
		return (px + scale - 1) / scale;
	}

	static Stream<Arguments> screens() {
		return Stream.of(
				Arguments.of(1920, 1080, 2),
				Arguments.of(1920, 1080, 3),
				Arguments.of(1920, 1080, 0),
				Arguments.of(2560, 1440, 2),
				Arguments.of(2560, 1440, 3),
				Arguments.of(2560, 1440, 0));
	}

	@ParameterizedTest(name = "{0}x{1} scale {2}")
	@MethodSource("screens")
	void centredWithEqualMarginsAndOnScreen(int pw, int ph, int scaleOrAuto) {
		int scale = scaleOrAuto == 0 ? autoScale(pw, ph) : scaleOrAuto;
		int gw = gui(pw, scale);
		int gh = gui(ph, scale);
		BrowserLayout l = BrowserLayout.compute(gw, gh);
		Rect f = l.frame();
		int left = f.x();
		int right = gw - f.right();
		int top = f.y();
		int bottom = gh - f.bottom();
		assertEquals(left, right, "left/right margins");
		assertEquals(top, bottom, "top/bottom margins");
		assertTrue(left >= BrowserLayout.DEFAULT_MARGIN && top >= BrowserLayout.DEFAULT_MARGIN, "never touches an edge: " + f);
		assertTrue(f.w() <= BrowserLayout.MAX_W && f.h() <= BrowserLayout.MAX_H, "capped: " + f);
		assertTrue(f.w() <= f.h() * BrowserLayout.MAX_RATIO, "reasonable ratio: " + f);
		// these real setups are all big enough for the split view
		assertFalse(l.compact(), "split at " + gw + "x" + gh);
		assertTrue(l.list().x() == f.x() && l.conversation().right() == f.right(), "list left, conversation right");
		assertTrue(l.list().w() >= 130 && l.list().w() <= 200);
		assertTrue(l.conversation().w() > l.list().w());
	}

	@Test
	void scaledSizesMatchVanilla() {
		assertEquals(4, autoScale(1920, 1080));
		assertEquals(6, autoScale(2560, 1440));
		assertEquals(854, gui(2560, 3));
		// 1440p at scale 2: capped and centred, nowhere near the bottom-left corner
		Rect f = BrowserLayout.compute(1280, 720).frame();
		assertEquals(new Rect(260, 140, 760, 440), f);
		// 1440p auto (426x240): fills the screen minus the margins
		Rect a = BrowserLayout.compute(gui(2560, 6), gui(1440, 6)).frame();
		assertEquals(new Rect(8, 8, 411, 224), a);
	}

	@Test
	void compactWhenItDoesNotFit() {
		// 640x480 window at scale 3 = 214x160
		BrowserLayout l = BrowserLayout.compute(gui(640, 3), gui(480, 3));
		assertTrue(l.compact());
		assertEquals(Rect.EMPTY, l.list());
		assertEquals(l.frame(), l.conversation());
		Rect f = l.frame();
		assertEquals(f.x(), 214 - f.right());
		assertEquals(f.y(), 160 - f.bottom());
		assertTrue(f.x() >= 0 && f.y() >= 0);
	}

	@Test
	void degenerateSizesStayOnScreen() {
		for (int[] s : new int[][] {{0, 0}, {10, 7}, {99, 1000}, {3000, 50}}) {
			Rect f = BrowserLayout.compute(s[0], s[1]).frame();
			assertTrue(f.x() >= 0 && f.y() >= 0 && f.right() <= s[0] && f.bottom() <= s[1], f + " in " + s[0] + "x" + s[1]);
		}
	}

	// ---- fix/chat-direct-connect: clicking a row in the centred list ---------------------------

	@Test
	void rowHitTestFollowsTheCentredLayoutAndScroll() {
		BrowserLayout l = BrowserLayout.compute(960, 540); // 1920x1080 at scale 2: frame centred with wide margins
		Rect list = l.list();
		assertTrue(list.x() >= 100, "the list is offset by the centring: " + list);
		int rowH = 14;
		// rows drawn inside the list panel, below its header and chips
		Rect rows = new Rect(list.x() + 6, list.y() + 40, list.w() - 16, 10 * rowH);
		double cx = rows.x() + rows.w() / 2.0;
		assertEquals(0, BrowserLayout.rowAt(cx, rows.y() + 1, rows, rowH, 0, 30));
		assertEquals(3, BrowserLayout.rowAt(cx, rows.y() + 3 * rowH + 7, rows, rowH, 0, 30));
		assertEquals(9, BrowserLayout.rowAt(cx, rows.bottom() - 1, rows, rowH, 0, 30));
		// scrolled by 5: the same spot is row 8
		assertEquals(8, BrowserLayout.rowAt(cx, rows.y() + 3 * rowH + 7, rows, rowH, 5, 30));
		// below the last visible row, outside, in the uncentred position: nothing
		assertEquals(-1, BrowserLayout.rowAt(cx, rows.y() + 4 * rowH + 1, rows, rowH, 0, 4));
		assertEquals(-1, BrowserLayout.rowAt(cx, rows.y() - 1, rows, rowH, 0, 30));
		assertEquals(-1, BrowserLayout.rowAt(rows.right() + 1, rows.y() + 1, rows, rowH, 0, 30), "scrollbar gutter");
		assertEquals(-1, BrowserLayout.rowAt(cx - list.x(), rows.y() + 1, rows, rowH, 0, 30), "coordinates without the centring offset");
		assertEquals(-1, BrowserLayout.rowAt(cx, rows.y() + 1, Rect.EMPTY, rowH, 0, 30), "not drawn yet");
	}
}
