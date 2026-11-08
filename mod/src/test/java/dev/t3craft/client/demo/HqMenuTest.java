package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.hq.layout.HqLayoutInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HqMenuTest {
	private static HqLayoutInfo info(String id, boolean large) {
		return new HqLayoutInfo(id, "t3craft.hq.layout." + id + ".name", "t3craft.hq.layout." + id + ".desc", large);
	}

	private static final List<HqLayoutInfo> SIX = List.of(info("classic", false), info("campus", true), info("ring", false),
			info("vertical", false), info("ensanche", false), info("village", false));

	private final List<String> saved = new ArrayList<>();

	private HqMenuModel model(String selected, String built, boolean exists) {
		return new HqMenuModel(SIX, selected, built, exists, saved::add);
	}

	// ---- model -------------------------------------------------------------------------------

	@Test
	void startsOnTheConfiguredLayoutWithoutSaving() {
		HqMenuModel m = model("ring", null, false);
		assertEquals("ring", m.selected().id());
		assertTrue(m.isSelected(2));
		assertTrue(saved.isEmpty());
	}

	@Test
	void unknownConfiguredIdFallsBackToTheFirstWithoutSaving() {
		assertEquals("classic", model("bogus", null, false).selected().id());
		assertEquals("classic", model(null, null, false).selected().id());
		assertTrue(saved.isEmpty());
	}

	@Test
	void selectingPersistsOnlyRealChanges() {
		HqMenuModel m = model("classic", null, false);
		assertFalse(m.select(0)); // already chosen
		assertTrue(m.select(3));
		assertFalse(m.select(3));
		assertFalse(m.select(99));
		assertFalse(m.select(-1));
		assertTrue(m.select("village"));
		assertFalse(m.select("nope"));
		assertEquals(List.of("vertical", "village"), saved);
		assertEquals("village", m.selected().id());
	}

	@Test
	void moveClampsToTheList() {
		HqMenuModel m = model("classic", null, false);
		assertFalse(m.move(-1));
		assertTrue(m.move(10));
		assertEquals("village", m.selected().id());
		assertFalse(m.move(1));
		assertEquals(List.of("village"), saved);
	}

	@Test
	void rebuildOnlyWhenAnHqIsBuiltAndTheChoiceDiffers() {
		HqMenuModel m = model("classic", "classic", true);
		assertFalse(m.rebuildOffered()); // same layout
		m.select("ring");
		assertTrue(m.rebuildOffered());
		m.select("classic");
		assertFalse(m.rebuildOffered());

		HqMenuModel none = model("classic", null, true); // world exists, nothing built yet
		none.select("ring");
		assertFalse(none.rebuildOffered());

		HqMenuModel noWorld = model("classic", "classic", false); // stale built id, world gone
		noWorld.select("ring");
		assertFalse(noWorld.rebuildOffered());
	}

	@Test
	void builtAndSelectedAreMarkedSeparately() {
		HqMenuModel m = model("campus", "ring", true);
		assertTrue(m.isSelected(1));
		assertFalse(m.isBuilt(1));
		assertTrue(m.isBuilt(2));
		assertFalse(m.isSelected(2));
		assertEquals("ring", m.built().id());
		assertNull(model("campus", null, true).built());
		assertNull(model("campus", "gone", true).built());
		assertFalse(model("campus", null, true).isBuilt(0));
	}

	@Test
	void largeLayoutsAreFlagged() {
		HqMenuModel m = model("classic", null, false);
		assertTrue(m.warnLarge(1)); // campus
		for (int i = 0; i < m.size(); i++) {
			if (i != 1) {
				assertFalse(m.warnLarge(i), m.get(i).id());
			}
		}
	}

	@Test
	void resetNeedsTheWorld() {
		assertTrue(model("classic", null, true).resetOffered());
		assertFalse(model("classic", null, false).resetOffered());
	}

	@Test
	void emptyRegistryIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new HqMenuModel(List.of(), "classic", null, false, saved::add));
	}

	// ---- list hit-testing with scroll -------------------------------------------------------

	@Test
	void hitTestWithoutScroll() {
		HqMenuList l = new HqMenuList(10, 40, 200, 100, 25, 6); // 150 px of rows in 100
		assertEquals(0, l.rowAt(10, 40));
		assertEquals(0, l.rowAt(209, 64));
		assertEquals(1, l.rowAt(50, 65));
		assertEquals(3, l.rowAt(50, 139)); // last visible pixel
		assertEquals(-1, l.rowAt(50, 140)); // below the viewport: row 4 is hidden there
		assertEquals(-1, l.rowAt(9, 50)); // left of it
		assertEquals(-1, l.rowAt(210, 50)); // right of it
		assertEquals(-1, l.rowAt(50, 39)); // above it
	}

	@Test
	void hitTestFollowsTheScroll() {
		HqMenuList l = new HqMenuList(10, 40, 200, 100, 25, 6);
		assertEquals(50, l.maxScroll());
		l.setScroll(30);
		assertEquals(1, l.rowAt(50, 40)); // y 40 + 30 = row 1 (25..50)
		assertEquals(2, l.rowAt(50, 60)); // 90 -> row 2
		assertEquals(5, l.rowAt(50, 139)); // 169 -> row 5
		assertEquals(40 + 25 - 30, l.rowY(1));
		assertFalse(l.visible(0)); // 0..25 scrolled above
		assertTrue(l.visible(1));
		assertTrue(l.visible(5));
		l.setScroll(500);
		assertEquals(50, l.scroll()); // clamped
		assertEquals(5, l.rowAt(50, 139));
		l.setScroll(-5);
		assertEquals(0, l.scroll());
	}

	@Test
	void hitTestPastTheLastRowIsNothing() {
		HqMenuList l = new HqMenuList(0, 0, 100, 200, 25, 3); // fits: no scroll
		assertFalse(l.scrollable());
		assertNull(l.thumb());
		assertEquals(2, l.rowAt(5, 74));
		assertEquals(-1, l.rowAt(5, 75));
		assertFalse(l.wheel(-1));
		assertEquals(-1, new HqMenuList(0, 0, 100, 200, 25, 0).rowAt(5, 5));
	}

	@Test
	void wheelScrollsOneRowPerNotch() {
		HqMenuList l = new HqMenuList(0, 0, 100, 60, 20, 6); // max 60
		assertTrue(l.wheel(-1)); // down
		assertEquals(20, l.scroll());
		assertTrue(l.wheel(-3));
		assertEquals(40, l.scroll()); // one notch per event, whatever the amount
		l.wheel(-1);
		assertFalse(l.wheel(-1)); // at the end
		assertEquals(60, l.scroll());
		assertTrue(l.wheel(1));
		assertEquals(40, l.scroll());
		assertFalse(l.wheel(0));
	}

	@Test
	void ensureVisibleScrollsTheLeastNeeded() {
		HqMenuList l = new HqMenuList(0, 0, 100, 60, 20, 6);
		l.ensureVisible(4); // 80..100 -> scroll 40
		assertEquals(40, l.scroll());
		l.ensureVisible(3); // already visible
		assertEquals(40, l.scroll());
		l.ensureVisible(0);
		assertEquals(0, l.scroll());
		l.ensureVisible(42); // ignored
		assertEquals(0, l.scroll());
		assertEquals(3, l.pageRows());
	}

	@Test
	void relayoutKeepsTheScrollInRange() {
		HqMenuList l = new HqMenuList(0, 0, 100, 60, 20, 6);
		l.setScroll(60);
		l.layout(0, 0, 100, 200, 20, 6); // taller window: everything fits
		assertEquals(0, l.scroll());
		assertEquals(1, new HqMenuList(0, 0, 100, 5, 20, 6).pageRows()); // tiny viewport (GUI scale)
	}

	@Test
	void thumbStaysInsideTheViewport() {
		HqMenuList l = new HqMenuList(0, 30, 100, 60, 20, 6);
		int[] t = l.thumb();
		assertNotNull(t);
		assertEquals(30, t[0]);
		l.setScroll(l.maxScroll());
		t = l.thumb();
		assertNotNull(t);
		assertEquals(90, t[0] + t[1]);
	}

	// ---- keys -----------------------------------------------------------------------------

	@Test
	void keysUseMinecraftCodes() {
		assertEquals(HqMenuKeys.Action.PREV, HqMenuKeys.action(InputConstants.KEY_UP));
		assertEquals(HqMenuKeys.Action.NEXT, HqMenuKeys.action(InputConstants.KEY_DOWN));
		assertEquals(HqMenuKeys.Action.PAGE_UP, HqMenuKeys.action(InputConstants.KEY_PAGEUP));
		assertEquals(HqMenuKeys.Action.PAGE_DOWN, HqMenuKeys.action(InputConstants.KEY_PAGEDOWN));
		assertEquals(HqMenuKeys.Action.FIRST, HqMenuKeys.action(InputConstants.KEY_HOME));
		assertEquals(HqMenuKeys.Action.LAST, HqMenuKeys.action(InputConstants.KEY_END));
		assertEquals(HqMenuKeys.Action.OPEN, HqMenuKeys.action(InputConstants.KEY_RETURN));
		assertEquals(HqMenuKeys.Action.OPEN, HqMenuKeys.action(InputConstants.KEY_NUMPADENTER));
		assertEquals(HqMenuKeys.Action.NONE, HqMenuKeys.action(InputConstants.KEY_ESCAPE)); // Screen handles Esc
		assertEquals(40, InputConstants.KEY_RETURN); // 26.x, not GLFW
		assertNotEquals(HqMenuKeys.Action.OPEN, HqMenuKeys.action(257)); // GLFW's Enter is not Enter any more
	}

	@Test
	void keyDeltas() {
		assertEquals(-1, HqMenuKeys.delta(HqMenuKeys.Action.PREV, 2, 6, 3));
		assertEquals(1, HqMenuKeys.delta(HqMenuKeys.Action.NEXT, 2, 6, 3));
		assertEquals(-3, HqMenuKeys.delta(HqMenuKeys.Action.PAGE_UP, 2, 6, 3));
		assertEquals(1, HqMenuKeys.delta(HqMenuKeys.Action.PAGE_DOWN, 2, 6, 0)); // page at least 1
		assertEquals(-2, HqMenuKeys.delta(HqMenuKeys.Action.FIRST, 2, 6, 3));
		assertEquals(3, HqMenuKeys.delta(HqMenuKeys.Action.LAST, 2, 6, 3));
		assertEquals(0, HqMenuKeys.delta(HqMenuKeys.Action.OPEN, 2, 6, 3));
		HqMenuModel m = model("classic", null, false);
		m.move(HqMenuKeys.delta(HqMenuKeys.Action.LAST, m.selectedIndex(), m.size(), 3));
		assertEquals("village", m.selected().id());
	}

	@Test
	void onlyLeftClickChoosesARow() {
		assertTrue(HqMenuKeys.choosesRow(InputConstants.MOUSE_BUTTON_LEFT));
		assertTrue(HqMenuKeys.choosesRow(dev.t3craft.client.chat.MouseButtons.LEFT));
		assertFalse(HqMenuKeys.choosesRow(InputConstants.MOUSE_BUTTON_RIGHT));
		assertFalse(HqMenuKeys.choosesRow(0)); // GLFW's left is not left in 26.x
	}
}
