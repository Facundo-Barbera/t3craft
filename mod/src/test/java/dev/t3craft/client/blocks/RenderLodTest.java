package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.blocks.RenderLod.MonitorDetail;
import org.junit.jupiter.api.Test;

/** Culling and level of detail of the blocks' renderers (fix/hq-fps). d = block centre − camera. */
class RenderLodTest {
	@Test
	void monitorFacingSouthIsSeenFromTheSouthOnly() {
		// FACING south: normal (0, +1). The camera 5 blocks south of it: d = (0, 0, -5).
		assertEquals(MonitorDetail.FULL, RenderLod.monitor(0, 0, -5, 0, 1));
		// from the north (behind the screen): nothing
		assertEquals(MonitorDetail.NONE, RenderLod.monitor(0, 0, 5, 0, 1));
		// level with the face plane, beside it: grazing, not drawn
		assertEquals(MonitorDetail.NONE, RenderLod.monitor(5, 0, 0, 0, 1));
		// just in front of the face
		assertEquals(MonitorDetail.FULL, RenderLod.monitor(3, 0, -0.6, 0, 1));
	}

	@Test
	void farMonitorsDrawTheBackgroundOnlyThenNothing() {
		assertEquals(MonitorDetail.FULL, RenderLod.monitor(0, 0, -RenderLod.MONITOR_TEXT + 0.5, 0, 1));
		assertEquals(MonitorDetail.BACKGROUND, RenderLod.monitor(0, 0, -RenderLod.MONITOR_TEXT - 1, 0, 1));
		assertEquals(MonitorDetail.BACKGROUND, RenderLod.monitor(0, 0, -RenderLod.MONITOR_VIEW + 1, 0, 1));
		assertEquals(MonitorDetail.NONE, RenderLod.monitor(0, 0, -RenderLod.MONITOR_VIEW - 1, 0, 1));
	}

	@Test
	void eastAndWestFaces() {
		// FACING east: normal (+1, 0); camera east of it → d.x < 0
		assertEquals(MonitorDetail.FULL, RenderLod.monitor(-4, 1, 0, 1, 0));
		assertEquals(MonitorDetail.NONE, RenderLod.monitor(4, 1, 0, 1, 0));
		assertEquals(MonitorDetail.FULL, RenderLod.monitor(4, 1, 0, -1, 0));
	}

	@Test
	void lampsBehindTheCameraAreSkipped() {
		// looking north (-Z)
		double fx = 0;
		double fy = 0;
		double fz = -1;
		assertTrue(RenderLod.lamp(0, 0, -10, false, fx, fy, fz), "in front");
		assertTrue(RenderLod.lamp(10, 0, -1, false, fx, fy, fz), "to the side (wide FOV)");
		assertFalse(RenderLod.lamp(0, 0, 10, false, fx, fy, fz), "behind");
		assertTrue(RenderLod.lamp(0, 0, 2, false, fx, fy, fz), "right behind the player's back but close: kept");
		assertFalse(RenderLod.lamp(0, 0, -RenderLod.LAMP_VIEW - 1, false, fx, fy, fz), "too far");
	}

	@Test
	void theBeaconIsDrawnFromFarAndBehind() {
		assertTrue(RenderLod.lamp(0, 0, 100, true, 0, 0, -1), "the beam can be in view while its block is behind");
		assertFalse(RenderLod.lamp(0, 0, RenderLod.BEACON_VIEW + 1, true, 0, 0, -1));
	}

	@Test
	void noCameraDirectionMeansDistanceOnly() {
		assertTrue(RenderLod.lamp(0, 0, 10, false, 0, 0, 0));
	}

	@Test
	void hqScaleWorkCount() {
		// 24 desk lamps on a 6 × 4 grid 3 blocks apart, the player in the middle looking north:
		// before, all 24 (off-screen rendering, 160 blocks); now only those not behind the camera.
		int drawn = 0;
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 4; j++) {
				double dx = (i - 2.5) * 3;
				double dz = (j - 1.5) * 8;
				if (RenderLod.lamp(dx, 1, dz, false, 0, 0, -1)) {
					drawn++;
				}
			}
		}
		assertTrue(drawn <= 16, "lamps drawn: " + drawn);
		assertTrue(drawn >= 10, "lamps in front stay: " + drawn);
	}
}
