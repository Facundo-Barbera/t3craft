package dev.t3craft.client.npc.helpers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.t3craft.client.npc.helpers.HelperFormation.Spot;
import org.junit.jupiter.api.Test;

class HelperFormationTest {
	static final double EPS = 1e-6;

	@Test
	void singleHelperStandsStraightBehind() {
		// yaw 0 faces +z, so behind is -z.
		Spot s = HelperFormation.spot(10, 20, 0f, 0, 1);
		assertEquals(10, s.x(), EPS);
		assertEquals(20 - HelperFormation.RADIUS, s.z(), EPS);
		// yaw 90 faces -x, so behind is +x.
		Spot t = HelperFormation.spot(0, 0, 90f, 0, 1);
		assertEquals(HelperFormation.RADIUS, t.x(), EPS);
		assertEquals(0, t.z(), EPS);
	}

	@Test
	void everySlotIsOnTheRingBehindTheParentAndDistinct() {
		for (int count = 1; count <= HelperConfig.MAX; count++) {
			for (int i = 0; i < count; i++) {
				Spot s = HelperFormation.spot(0, 0, 0f, i, count);
				assertEquals(HelperFormation.RADIUS, Math.hypot(s.x(), s.z()), EPS);
				// Never in front of the parent's face (the arc is at most 240° wide).
				assertTrue(s.z() <= HelperFormation.RADIUS * Math.cos(Math.toRadians(60)) + EPS, "slot " + i + "/" + count);
				for (int j = 0; j < i; j++) {
					Spot o = HelperFormation.spot(0, 0, 0f, j, count);
					assertTrue(Math.hypot(o.x() - s.x(), o.z() - s.z()) > 0.6, "slots too close: " + j + "," + i + "/" + count);
				}
			}
		}
	}

	@Test
	void arcIsSymmetric() {
		assertEquals(0, HelperFormation.angle(0, 1), EPS);
		assertEquals(-25, HelperFormation.angle(0, 2), EPS);
		assertEquals(25, HelperFormation.angle(1, 2), EPS);
		assertEquals(-120, HelperFormation.angle(0, 8), EPS);
		assertEquals(120, HelperFormation.angle(7, 8), EPS);
	}

	@Test
	void followStaysStillInDeadzoneStepsWhenNearSnapsWhenFar() {
		Spot here = new Spot(0, 0);
		assertSame(here, HelperFormation.step(here, new Spot(0.1, 0.1)));
		Spot moved = HelperFormation.step(here, new Spot(3, 4));
		assertEquals(HelperFormation.MAX_STEP, Math.hypot(moved.x(), moved.z()), EPS);
		assertEquals(0.6, moved.x() / Math.hypot(moved.x(), moved.z()), EPS);
		Spot far = new Spot(30, 0);
		assertEquals(far, HelperFormation.step(here, far));
		Spot close = new Spot(0.21, 0);
		assertEquals(close, HelperFormation.step(here, close));
	}

	@Test
	void followConvergesOnAWalkingParent() {
		Spot helper = HelperFormation.spot(0, 0, 0f, 0, 1);
		double px = 0;
		for (int tick = 0; tick < 200; tick++) {
			px += 0.15; // parent walks along +x faster than the deadzone but slower than MAX_STEP
			helper = HelperFormation.step(helper, HelperFormation.spot(px, 0, -90f, 0, 1));
		}
		Spot target = HelperFormation.spot(px, 0, -90f, 0, 1);
		assertTrue(Math.hypot(helper.x() - target.x(), helper.z() - target.z()) <= HelperFormation.DEADZONE + 0.16);
	}

	@Test
	void yawHelpers() {
		assertEquals(0f, HelperFormation.yawTowards(new Spot(0, 0), new Spot(0, 5), 99f), 1e-4);
		assertEquals(90f, HelperFormation.yawTowards(new Spot(0, 0), new Spot(-5, 0), 99f), 1e-4);
		assertEquals(99f, HelperFormation.yawTowards(new Spot(1, 1), new Spot(1, 1), 99f), 1e-4);
		assertEquals(-175f, HelperFormation.lerpYaw(170f, -160f, 0.5f), 1e-4);
		assertEquals(10f, HelperFormation.lerpYaw(0f, 10f, 2f), 1e-4);
	}

	@Test
	void configDefaultsAndClamping() {
		assertEquals(HelperConfig.DEFAULTS, HelperConfig.parse(JsonParser.parseString("{}").getAsJsonObject()));
		assertEquals(new HelperConfig(true, 3), HelperConfig.DEFAULTS);
		assertEquals(new HelperConfig(false, 5),
				HelperConfig.parse(JsonParser.parseString("{\"helpers\":{\"enabled\":false,\"maxPerParent\":5}}").getAsJsonObject()));
		assertEquals(new HelperConfig(true, HelperConfig.MAX),
				HelperConfig.parse(JsonParser.parseString("{\"helpers\":{\"maxPerParent\":99}}").getAsJsonObject()));
		assertEquals(new HelperConfig(true, 0),
				HelperConfig.parse(JsonParser.parseString("{\"helpers\":{\"maxPerParent\":-2}}").getAsJsonObject()));
		assertEquals(HelperConfig.DEFAULTS,
				HelperConfig.parse(JsonParser.parseString("{\"helpers\":{\"enabled\":\"no\",\"maxPerParent\":\"x\"}}").getAsJsonObject()));
		assertEquals(HelperConfig.DEFAULTS, HelperConfig.parse(JsonParser.parseString("{\"helpers\":true}").getAsJsonObject()));
	}
}
