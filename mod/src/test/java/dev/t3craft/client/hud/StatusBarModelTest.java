package dev.t3craft.client.hud;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.config.T3CraftSettings.Hud;
import dev.t3craft.client.hud.StatusBarModel.Counts;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class StatusBarModelTest {
	/** 6 px per char, like the vanilla font for most ASCII. */
	static final ToIntFunction<String> W = s -> s.length() * 6;
	static final Hud ON = Hud.defaults();

	@Test
	void countsByWireState() {
		Counts c = Counts.of(List.of("working", "working", "needs_attention", "error", "done", "idle", "bogus"));
		assertEquals(new Counts(2, 1, 1), c);
		assertTrue(Counts.of(List.of("done")).allZero());
	}

	@Test
	void segmentsCarryTextColourAndWidth() {
		StatusBarModel m = StatusBarModel.build(new Counts(3, 1, 0), ON, "Y", W);
		assertEquals(3, m.segments().size());
		StatusBarModel.Segment working = m.segments().get(0);
		assertEquals("⚙ 3 working", working.text());
		assertEquals(ThreadState.WORKING.color, working.color());
		assertEquals(W.applyAsInt("⚙ 3 working"), working.width());
		assertEquals("! 1 needs you", m.segments().get(1).text());
		assertEquals(ThreadState.NEEDS_ATTENTION.color, m.segments().get(1).color());
		assertEquals(StatusBarModel.DIM, m.segments().get(2).color(), "zero count is dimmed");
		int expected = StatusBarModel.PAD * 2 + StatusBarModel.GAP * 2;
		for (StatusBarModel.Segment s : m.segments()) {
			expected += s.width();
		}
		assertEquals(expected, m.width());
	}

	@Test
	void badgeShowsWaitingAndKey() {
		StatusBarModel m = StatusBarModel.build(new Counts(0, 2, 0), ON, "Y", W);
		assertNotNull(m.badge());
		assertEquals("2 waiting · Y", m.badge().text());
		assertEquals(StatusBarModel.ROW_H * 2 + StatusBarModel.BADGE_GAP, m.height());
	}

	@Test
	void badgeWithoutKeyWhenUnbound() {
		assertEquals("1 waiting", StatusBarModel.build(new Counts(0, 1, 0), ON, null, W).badge().text());
	}

	@Test
	void noBadgeWhenNobodyWaits() {
		StatusBarModel m = StatusBarModel.build(new Counts(4, 0, 1), ON, "Y", W);
		assertNull(m.badge());
		assertEquals(StatusBarModel.ROW_H, m.height());
	}

	@Test
	void hiddenWhenAllZeroOrDisabled() {
		assertTrue(StatusBarModel.build(Counts.ZERO, ON, "Y", W).hidden());
		assertFalse(StatusBarModel.build(Counts.ZERO, ON.withHideWhenIdle(false), "Y", W).hidden());
		assertTrue(StatusBarModel.build(new Counts(1, 1, 1), ON.withStatusBar(false), "Y", W).hidden());
	}

	@Test
	void topStacksUnderPeek() {
		StatusBarModel m = StatusBarModel.build(new Counts(1, 0, 0), ON, "Y", W);
		assertArrayEquals(new int[] {(400 - m.width()) / 2, StatusBarModel.MARGIN}, m.place(ON, 400, 240, 0));
		assertEquals(30, m.place(ON, 400, 240, 28)[1]);
		Hud left = ON.withPlacement("top", "left");
		assertEquals(StatusBarModel.MARGIN, m.place(left, 400, 240, 0)[0]);
		Hud right = ON.withPlacement("top", "right");
		assertEquals(400 - StatusBarModel.MARGIN - m.width(), m.place(right, 400, 240, 0)[0]);
	}

	@Test
	void bottomNeverCoversHotbarHeartsOrXp() {
		StatusBarModel m = StatusBarModel.build(new Counts(1, 1, 1), ON, "Y", W);
		int gw = 400;
		int gh = 240;
		int hotbarL = (gw - StatusBarModel.HOTBAR_W) / 2;
		int barsTop = gh - StatusBarModel.SURVIVAL_BARS_H;
		for (String align : Hud.ALIGNS) {
			Hud cfg = ON.withPlacement("bottom", align);
			int[] xy = m.place(cfg, gw, gh, 0);
			boolean overlapsX = xy[0] < hotbarL + StatusBarModel.HOTBAR_W && xy[0] + m.width() > hotbarL;
			boolean overlapsY = xy[1] + m.height() > barsTop;
			assertFalse(overlapsX && overlapsY, align + " covers the survival bars: " + xy[0] + "," + xy[1]);
		}
	}

	@Test
	void bottomSideUsesCornerWhenWide() {
		StatusBarModel m = StatusBarModel.build(new Counts(1, 0, 0), ON, null, W);
		int gw = StatusBarModel.HOTBAR_W + 2 * m.width() + 40;
		int[] xy = m.place(ON.withPlacement("bottom", "left"), gw, 300, 0);
		assertEquals(300 - StatusBarModel.MARGIN - m.height(), xy[1]);
	}

	@Test
	void placementCycleVisitsAllSix() {
		Hud h = Hud.defaults().withPlacement("top", "left");
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < 6; i++) {
			seen.add(h.position() + " " + h.align());
			h = h.nextPlacement();
		}
		assertEquals(6, seen.size());
		assertEquals("top left", h.position() + " " + h.align());
	}

	@Test
	void invalidPlacementFallsBack() {
		Hud h = new Hud(true, "middle", "diagonal", false);
		assertEquals("top", h.position());
		assertEquals("center", h.align());
	}
}
