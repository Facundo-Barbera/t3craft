package dev.t3craft.client.t3ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.Rect;
import org.junit.jupiter.api.Test;

class T3UiLayoutTest {
	static boolean inside(Rect inner, Rect outer) {
		return inner.w() == 0 || (inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom());
	}

	static boolean overlap(Rect a, Rect b) {
		return a.w() > 0 && b.w() > 0 && a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}

	/** Every rectangle inside its panel and no two sidebar/main parts overlapping, at many screen sizes. */
	@Test
	void partsStayInsideAndApartAtEverySize() {
		// 1920x1080 at GUI scale 1..6, 1280x720 at 1..4, tiny and odd windows
		int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {384, 216}, {320, 180}, {1280, 720}, {427, 240}, {320, 240}, {300, 140},
				{200, 120}, {854, 480}, {1000, 200}, {150, 600}};
		for (int[] s : sizes) {
			for (boolean side : new boolean[] {true, false}) {
				T3UiLayout l = T3UiLayout.compute(s[0], s[1], side);
				String at = s[0] + "x" + s[1] + " side=" + side;
				assertTrue(l.frame().right() <= s[0] && l.frame().bottom() <= s[1] && l.frame().x() >= 0 && l.frame().y() >= 0, at);
				assertTrue(inside(l.sidebar(), l.frame()) && inside(l.main(), l.frame()), at);
				assertFalse(overlap(l.sidebar(), l.main()), at);
				T3UiLayout.SidebarRects sr = T3UiLayout.sidebar(l.sidebar());
				Rect[] parts = {sr.brand(), sr.search(), sr.project(), sr.newThread(), sr.list(), sr.footer()};
				for (int i = 0; i < parts.length; i++) {
					assertTrue(inside(parts[i], l.sidebar()), at + " sidebar part " + i);
					for (int j = i + 1; j < parts.length; j++) {
						assertFalse(overlap(parts[i], parts[j]), at + " sidebar parts " + i + "/" + j);
					}
				}
				T3UiLayout.MainRects mr = T3UiLayout.main(l.main(), 40);
				Rect[] mp = {mr.title(), mr.actions(), mr.conversation(), mr.composer()};
				for (int i = 0; i < mp.length; i++) {
					assertTrue(inside(mp[i], l.main()), at + " main part " + i);
					for (int j = i + 1; j < mp.length; j++) {
						assertFalse(overlap(mp[i], mp[j]), at + " main parts " + i + "/" + j);
					}
				}
			}
		}
	}

	@Test
	void wideScreenSplitsSidebarAndThreadCentred() {
		T3UiLayout l = T3UiLayout.compute(1920, 1080, true);
		assertFalse(l.compact());
		assertEquals(T3UiLayout.MAX_W, l.frame().w());
		assertEquals(T3UiLayout.MAX_H, l.frame().h());
		assertEquals((1920 - T3UiLayout.MAX_W) / 2, l.frame().x());
		assertEquals(T3UiLayout.SIDEBAR_MAX, l.sidebar().w());
		assertEquals(l.sidebar().right() + T3UiLayout.GAP, l.main().x());
		assertEquals(l.frame().right(), l.main().right());
	}

	@Test
	void hiddenSidebarGivesTheThreadTheWholeFrame() {
		T3UiLayout l = T3UiLayout.compute(1280, 720, false);
		assertFalse(l.compact());
		assertEquals(0, l.sidebar().w());
		assertEquals(l.frame(), l.main());
	}

	@Test
	void narrowWindowOrHighGuiScaleIsCompactOnePanelAtATime() {
		// 640x360 at GUI scale 2 → 320x180 scaled pixels
		T3UiLayout side = T3UiLayout.compute(320, 180, true);
		assertTrue(side.compact());
		assertEquals(side.frame(), side.sidebar());
		assertEquals(0, side.main().w());
		T3UiLayout thread = T3UiLayout.compute(320, 180, false);
		assertEquals(thread.frame(), thread.main());
		assertEquals(0, thread.sidebar().w());
		// 1920x1080 at GUI scale 6 → 320x180, same
		assertTrue(T3UiLayout.compute(1920 / 6, 1080 / 6, true).compact());
		// short but wide
		assertTrue(T3UiLayout.compute(1000, 160, true).compact());
	}

	@Test
	void shortSidebarPutsProjectAndNewThreadOnOneRow() {
		T3UiLayout.SidebarRects tall = T3UiLayout.sidebar(new Rect(0, 0, 200, 400));
		assertTrue(tall.newThread().y() > tall.project().y());
		T3UiLayout.SidebarRects tight = T3UiLayout.sidebar(new Rect(0, 0, 200, 140));
		assertEquals(tight.project().y(), tight.newThread().y());
		assertTrue(tight.list().h() >= 3 * T3UiLayout.ROW_H, "room for a few rows even at a high GUI scale");
	}

	@Test
	void compactStartsOnTheThreadWhenOpenedFromABlock() {
		assertTrue(T3UiLayout.startsOnSidebar(null));
		assertFalse(T3UiLayout.startsOnSidebar("vps:thr_1"));
	}

	@Test
	void rowHitTestFollowsTheScroll() {
		Rect list = new Rect(10, 100, 150, 5 * T3UiLayout.ROW_H + 4); // 5 whole rows
		assertEquals(5, T3UiLayout.capacity(list));
		assertEquals(0, T3UiLayout.rowAt(20, 100, list, 0, 20));
		assertEquals(2, T3UiLayout.rowAt(20, 100 + 2 * T3UiLayout.ROW_H + 1, list, 0, 20));
		// scrolled by 7: the second drawn row is item 8
		assertEquals(8, T3UiLayout.rowAt(20, 100 + T3UiLayout.ROW_H, list, 7, 20));
		// outside horizontally, above, or in the leftover strip under the last whole row
		assertEquals(-1, T3UiLayout.rowAt(5, 105, list, 0, 20));
		assertEquals(-1, T3UiLayout.rowAt(20, 99, list, 0, 20));
		assertEquals(-1, T3UiLayout.rowAt(20, 100 + 5 * T3UiLayout.ROW_H + 1, list, 0, 20));
		// fewer items than rows: empty space below is not a row
		assertEquals(-1, T3UiLayout.rowAt(20, 100 + 3 * T3UiLayout.ROW_H + 1, list, 0, 3));
	}

	@Test
	void scrollClampsAndRevealsTheSelection() {
		Rect list = new Rect(0, 0, 100, 5 * T3UiLayout.ROW_H);
		assertEquals(15, T3UiLayout.clampFirst(99, 20, list));
		assertEquals(0, T3UiLayout.clampFirst(-3, 20, list));
		assertEquals(0, T3UiLayout.clampFirst(4, 3, list));
		assertEquals(8, T3UiLayout.reveal(0, 12, 20, list), "below: last visible row");
		assertEquals(3, T3UiLayout.reveal(10, 3, 20, list), "above: first visible row");
		assertEquals(2, T3UiLayout.reveal(2, 4, 20, list), "already visible: unchanged");
		assertEquals(2, T3UiLayout.reveal(2, -1, 20, list));
	}

	@Test
	void composerSitsAtTheBottomUnderTheConversation() {
		T3UiLayout.MainRects mr = T3UiLayout.main(new Rect(100, 10, 500, 400), 40);
		assertEquals(410, mr.composer().bottom());
		assertEquals(40, mr.composer().h());
		assertTrue(mr.conversation().bottom() < mr.composer().y());
		assertTrue(mr.title().bottom() <= mr.actions().y());
		assertTrue(mr.actions().bottom() <= mr.conversation().y());
		T3UiLayout.MainRects none = T3UiLayout.main(new Rect(100, 10, 500, 400), 0);
		assertEquals(0, none.composer().w());
		assertEquals(410, none.conversation().bottom());
	}
}
