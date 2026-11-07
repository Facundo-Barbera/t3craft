package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.t3craft.client.blocks.MonitorLod.Level;
import org.junit.jupiter.api.Test;

/** LOD by distance (with hysteresis, scaled by screen size), layouts, depth steps and settings. */
class MonitorLodTest {
	static final MonitorSettings S = MonitorSettings.DEFAULT; // nearDistance 8

	@Test
	void switchesWithHysteresis() {
		ScreenExtent one = ScreenExtent.ONE;
		assertEquals(Level.NEAR, MonitorLod.next(null, 3, one, S));
		assertEquals(Level.FAR, MonitorLod.next(null, 12, one, S));
		assertEquals(Level.NEAR, MonitorLod.next(Level.NEAR, 9.5, one, S), "within hysteresis: stays near");
		assertEquals(Level.FAR, MonitorLod.next(Level.NEAR, 10.5, one, S));
		assertEquals(Level.FAR, MonitorLod.next(Level.FAR, 9.5, one, S), "must come back inside the limit");
		assertEquals(Level.NEAR, MonitorLod.next(Level.FAR, 7.9, one, S));
	}

	@Test
	void biggerScreensStayDetailedFurther() {
		ScreenExtent wall = new ScreenExtent(3, 2);
		assertEquals(16, MonitorLod.nearLimit(wall, S), 1e-9);
		assertEquals(Level.NEAR, MonitorLod.next(null, 14, wall, S));
		assertEquals(Level.FAR, MonitorLod.next(null, 14, ScreenExtent.ONE, S));
	}

	@Test
	void layoutsScaleWithTheExtent() {
		MonitorLod.Layout near = MonitorLod.layout(Level.NEAR, ScreenExtent.ONE);
		assertEquals(128f, near.ppb());
		assertTrue(near.cols() >= 16 && near.cols() <= 20, "one block: ~17 columns, got " + near.cols());
		assertTrue(near.rows() >= 6, "one block: a tail of several rows, got " + near.rows());
		MonitorLod.Layout wall = MonitorLod.layout(Level.NEAR, new ScreenExtent(3, 2));
		assertEquals(96f, wall.ppb());
		assertTrue(wall.cols() > 2 * near.cols());
		assertTrue(wall.rows() > near.rows());
		assertEquals(3 * 96f, wall.width());
		MonitorLod.Layout far = MonitorLod.layout(Level.FAR, ScreenExtent.ONE);
		assertEquals(0, far.rows(), "far: no log");
		assertTrue(far.ppb() < near.ppb(), "far text is bigger");
		assertTrue(far.x1() > far.x0() && far.y1() > far.y0());
	}

	@Test
	void depthStepGrowsWithDistance() {
		assertEquals(MonitorLod.MIN_Z_STEP, MonitorLod.zStep(0));
		assertEquals(MonitorLod.MIN_Z_STEP, MonitorLod.zStep(4));
		float at64 = MonitorLod.zStep(64);
		assertTrue(at64 > 0.01f && at64 < 0.05f, "≈0.02 block at 64 blocks: " + at64);
		assertTrue(MonitorLod.zStep(128) > at64);
	}

	@Test
	void settingsAreAdditiveAndClamped() {
		assertEquals(MonitorSettings.DEFAULT, MonitorSettings.from(null));
		assertEquals(MonitorSettings.DEFAULT, MonitorSettings.from(JsonParser.parseString("{\"blocks\":{\"enabled\":true}}").getAsJsonObject()));
		MonitorSettings s = MonitorSettings.from(JsonParser.parseString(
				"{\"blocks\":{\"monitor\":{\"subscribeRadius\":24,\"visibility\":\"line_of_sight\",\"releaseAfterSeconds\":2.5,"
						+ "\"viewDistance\":9999,\"nearDistance\":\"x\",\"liveLog\":false}}}").getAsJsonObject());
		assertEquals(24, s.subscribeRadius());
		assertEquals(MonitorSettings.Visibility.LINE_OF_SIGHT, s.visibility());
		assertEquals(2_500, s.releaseAfterMs());
		assertEquals(256, s.viewDistance(), "clamped");
		assertEquals(8, s.nearDistance(), "mistyped keeps the default");
		assertEquals(false, s.liveLog());
		assertEquals(MonitorSettings.Visibility.FRUSTUM, MonitorSettings.from(JsonParser.parseString(
				"{\"blocks\":{\"monitor\":{\"visibility\":\"psychic\"}}}").getAsJsonObject()).visibility());
	}
}
