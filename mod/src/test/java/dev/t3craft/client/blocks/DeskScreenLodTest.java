package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.blocks.MonitorLod.Level;
import dev.t3craft.client.chat.ChatEntry.Kind;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.workstation.StationPlacement;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.t3.ThreadState;
import org.junit.jupiter.api.Test;

/**
 * fix/desk-screens-3x2: the desk's one 3×2 screen carries the thread's state and its live log, and
 * stays readable near and far (LOD), never dropping out up close.
 */
class DeskScreenLodTest {
	private static final ScreenExtent DESK = new ScreenExtent(3, 2);

	@Test
	void nearShowsHeaderAndARealLogTail() {
		MonitorLod.Layout near = MonitorLod.layout(Level.NEAR, DESK);
		assertEquals(96f, near.ppb());
		assertTrue(near.rows() >= 12, "a 2-high screen shows a long log tail: " + near.rows());
		assertTrue(near.cols() >= 40, "a 3-wide screen fits a tool row with its path: " + near.cols());
	}

	@Test
	void modelHasStateAndLogTogether() {
		MonitorLod.Layout near = MonitorLod.layout(Level.NEAR, DESK);
		ChatTranscript.Status needs = new ChatTranscript.Status(ThreadState.NEEDS_ATTENTION, "approval", null);
		MonitorLog.Model m = MonitorLog.of(MonitorLogTest.view(needs,
				MonitorLogTest.e(Kind.TOOL, "$ ./gradlew build", ""),
				MonitorLogTest.e(Kind.TOOL, "Edit mod/src/Foo.java (+3 −1)", "")), null, null, near.rows(), near.cols());
		assertEquals("Fix login loop", m.title(), "header title");
		assertEquals(StationStatus.ATTENTION, m.status(), "header status");
		assertNotNull(m.needsYou(), "needs-you footer");
		assertEquals(2, m.rows().size(), "log tail under the header");
	}

	@Test
	void farIsOnlyTitleAndStatusLarge() {
		MonitorLod.Layout far = MonitorLod.layout(Level.FAR, DESK);
		assertEquals(0, far.rows(), "far: no log");
		int statusW = 9 * MonitorLod.GLYPH_W; // "⚙ WORKING"
		int titleW = 16 * MonitorLod.GLYPH_W; // "fix desk screens"
		MonitorLod.FarText t = MonitorLod.farText(far, statusW, titleW);
		assertTrue(t.statusScale() >= 2.5f, "status word big across a hall: " + t.statusScale());
		assertTrue(t.titleScale() >= 1.5f, "title big too: " + t.titleScale());
		assertTrue(t.titleScale() <= t.statusScale(), "title never above the status");
		assertTrue(statusW * t.statusScale() <= far.x1() - far.x0() + 1e-3, "fits the width");
		assertTrue(t.height(true) <= far.y1() - far.y0() + 1e-3, "fits the height");
		// a very long title is ellipsized at scale 1, the status stays big
		MonitorLod.FarText longTitle = MonitorLod.farText(far, statusW, 400);
		assertEquals(1f, longTitle.titleScale());
		assertTrue(longTitle.statusScale() >= 2.5f);
	}

	@Test
	void switchesToFarAcrossTheHallAndBackNearUpClose() {
		MonitorSettings s = MonitorSettings.DEFAULT;
		double limit = MonitorLod.nearLimit(DESK, s);
		assertTrue(limit >= 12, "the agent's corridor reads the log: " + limit);
		assertEquals(Level.NEAR, MonitorLod.next(null, 1.0, DESK, s), "right at the screen: still drawn, NEAR");
		assertEquals(Level.FAR, MonitorLod.next(null, limit + 10, DESK, s));
		assertEquals(Level.NEAR, MonitorLod.next(Level.FAR, limit - 1, DESK, s));
	}

	@Test
	void notCulledUpCloseInFrontOrFromAnyDistanceInFront() {
		// player 0.6 blocks in front of the face (bottom-left block's centre at +1.1 along -n), looking away
		// sideways: the panel (radius 1.5) must still draw
		assertTrue(RenderLod.monitor(0, 1.1, 0, -1, 1, 0.5, 1.1, 1.5, 1, 0, 0), "face right in front, camera looking along it");
		assertTrue(RenderLod.monitor(0, 30, 0, -1, 0, 0.5, 30, 1.5, 0, 0, 1), "across the hall, looking at it");
		assertTrue(RenderLod.monitor(0, 0.9, 0, -1, 0, 0.5, 0.9, 1.5, 0, 0, -1), "almost touching, even looking away");
	}

	@Test
	void layersStaySeparatedAtEveryDistance() {
		assertTrue(MonitorLod.zStep(0.3) >= MonitorLod.MIN_Z_STEP);
		assertTrue(MonitorLod.zStep(0.3) < 0.01f, "nothing floats off the face up close");
	}

	@Test
	void needsYouBreathesButNeverVanishes() {
		int bg = 0xFF0B0D0F;
		int amber = 0xFF000000 | StationStatus.ATTENTION.color;
		int dim = MonitorLod.breathe(amber, bg, 0f);
		int bright = MonitorLod.breathe(amber, bg, 1f);
		assertEquals(amber, bright);
		assertNotEquals(bg, dim);
		assertTrue(((dim >> 16) & 0xFF) > 0x60, "the dim phase is still clearly amber: " + Integer.toHexString(dim));
		float lo = 1;
		float hi = 0;
		for (long ms = 0; ms < MonitorLod.BREATH_MS; ms += 50) {
			float b = MonitorLod.breath(ms);
			lo = Math.min(lo, b);
			hi = Math.max(hi, b);
		}
		assertTrue(lo < 0.05f && hi > 0.95f, "a full breath per period");
	}

	@Test
	void vanillaFallbackDisplayCoversTheWholeDeskScreen() {
		// a south-facing desk screen whose bottom-middle cell is (10, 66, 20)
		StationPlacement p = StationPlacement.onFace(10, 66, 20, 10, 66, 20, 3, 0f);
		assertEquals(10.5, p.screenX(), 1e-9, "centred across the 3 cells");
		assertTrue(p.screenZ() > 21.0 && p.screenZ() < 21.1, "just in front of the face: " + p.screenZ());
		assertTrue(p.screenY() > 66 && p.screenY() < 66.3, "bottom line near the bottom edge");
		assertTrue(p.scale() > 0.6f, "scaled to 3 blocks: " + p.scale());
		float width = 170 * 0.025f * p.scale();
		assertTrue(width > 2.5f && width <= 3f, "text block spans the screen without overflowing: " + width);
	}
}
