package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Culling and level of detail of the blocks' renderers (fix/hq-fps). d = block centre − camera. */
class RenderLodTest {
	/** A one-block monitor at d = (dx, dz) from the camera, looking along (fx, fz). */
	static boolean monitor(double dx, double dz, int nx, int nz, double fx, double fz) {
		return RenderLod.monitor(dx, dz, nx, nz, dx + nx * 0.5, 0, dz + nz * 0.5, 0.5, fx, 0, fz);
	}

	@Test
	void monitorFacingSouthIsSeenFromTheSouthOnly() {
		// FACING south: normal (0, +1). The camera 5 blocks south of it, looking north: d = (0, -5).
		assertTrue(monitor(0, -5, 0, 1, 0, -1));
		assertFalse(monitor(0, 5, 0, 1, 0, 1), "from the north (behind the screen)");
		assertFalse(monitor(5, 0, 0, 1, -1, 0), "level with the face plane, beside it");
		assertTrue(monitor(3, -0.6, 0, 1, 1, 0), "just in front of the face");
	}

	@Test
	void eastAndWestFaces() {
		// FACING east: normal (+1, 0); camera east of it → d.x < 0
		assertTrue(monitor(-4, 0, 1, 0, -1, 0));
		assertFalse(monitor(4, 0, 1, 0, -1, 0));
		assertTrue(monitor(4, 0, -1, 0, 1, 0));
	}

	@Test
	void monitorsBehindTheCameraAreSkippedButNotBigPanelsAround() {
		// facing the camera (south) but the camera looks away (south): behind
		assertFalse(monitor(0, -10, 0, 1, 0, 1));
		// a 9-wide panel whose centre is behind but within its radius + NEAR: kept
		assertTrue(RenderLod.monitor(0, -6, 0, 1, 0, 0, -5.5, 4.5, 0, 0, 1));
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
