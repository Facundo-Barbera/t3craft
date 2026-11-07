package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectionTest {
	private static SiteEvaluator.Evaluation eval(FakeWorld w, int cx, int cz) {
		return SiteEvaluator.evaluate(new Columns(w), cx, cz);
	}

	@Test
	void flatGroundIsAccepted() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		SiteEvaluator.Evaluation e = eval(w, 0, 0);
		assertTrue(e.ok(), e.problems().toString());
		assertEquals(64, e.floorY());
		assertEquals(0, e.relief());
		assertEquals(0, e.cut() + e.fill());
		Box b = e.box();
		assertNotNull(b);
		assertEquals(SiteLimits.FOOTPRINT + 2 * SiteLimits.APRON, b.sizeX());
		assertEquals(63, b.minY());
		assertEquals(64 + SiteLimits.HEADROOM, b.maxY());
		assertTrue(b.volume() <= SiteLimits.MAX_VOLUME);
		assertTrue(b.chunkCount() <= SiteLimits.MAX_CHUNKS);
	}

	@Test
	void slopeWithinLimitsUsesTheMedian() {
		FakeWorld w = new FakeWorld();
		for (int x = -30; x <= 30; x++) {
			for (int z = -30; z <= 30; z++) {
				w.column(x, z, 64 + Math.floorDiv(x, 4)); // gentle slope along x
			}
		}
		SiteEvaluator.Evaluation e = eval(w, 0, 0);
		assertTrue(e.ok(), e.problems().toString());
		assertTrue(e.relief() <= SiteLimits.MAX_RELIEF);
		assertTrue(e.cut() > 0 && e.fill() > 0);
	}

	@Test
	void hillIsRefused() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		for (int x = 0; x <= 3; x++) {
			for (int z = 0; z <= 3; z++) {
				w.column(x, z, 75);
			}
		}
		SiteEvaluator.Evaluation e = eval(w, 0, 0);
		assertFalse(e.ok());
		assertTrue(e.problems().get(0).contains("uneven"));
	}

	@Test
	void waterLavaAndBuildingsAreRefused() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		w.put(3, 64, 3, "minecraft:water[level=0]", null);
		assertTrue(eval(w, 0, 0).problems().get(0).contains("water"));

		FakeWorld ring = new FakeWorld().flat(-30, -30, 30, 30, 64);
		// one block outside the box (footprint 7 + apron 2 + 1)
		ring.put(10, 64, 0, "minecraft:water[level=0]", null);
		assertFalse(eval(ring, 0, 0).ok(), "fluid right next to the box could flow in");

		FakeWorld lava = new FakeWorld().flat(-30, -30, 30, 30, 64);
		lava.put(2, 60, 2, "minecraft:lava[level=0]", null);
		assertFalse(eval(lava, 0, 0).ok(), "lava just below the ground");

		FakeWorld house = new FakeWorld().flat(-30, -30, 30, 30, 64);
		house.put(-5, 65, 4, "minecraft:oak_planks", null);
		SiteEvaluator.Evaluation e = eval(house, 0, 0);
		assertFalse(e.ok());
		assertTrue(e.problems().get(0).contains("oak_planks"), e.problems().toString());

		FakeWorld underLeaves = new FakeWorld().flat(-30, -30, 30, 30, 64);
		underLeaves.put(1, 70, 1, "minecraft:oak_leaves", null);
		underLeaves.put(1, 65, 1, "minecraft:torch", null);
		assertFalse(eval(underLeaves, 0, 0).ok(), "a torch hidden under a canopy still counts");

		FakeWorld frame = new FakeWorld().flat(-30, -30, 30, 30, 64);
		frame.entity(2, 66, 2, "item_frame");
		assertTrue(eval(frame, 0, 0).problems().get(0).contains("entities"));
	}

	@Test
	void treesAndPlantsAreAllowedButCost() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		w.put(0, 65, 0, "minecraft:short_grass", null);
		for (int y = 65; y < 70; y++) {
			w.put(2, y, 2, "minecraft:oak_log[axis=y]", null);
		}
		SiteEvaluator.Evaluation e = eval(w, 0, 0);
		assertTrue(e.ok(), e.problems().toString());
		assertEquals(5, e.trees());
		assertEquals(64, e.floorY(), "logs are not ground");
	}

	@Test
	void unloadedChunksAreNeverUsed() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		w.unloadChunk(0, 0);
		SiteEvaluator.Evaluation e = eval(w, 0, 0);
		assertFalse(e.ok());
		assertNull(e.box());
	}

	@Test
	void pickFindsTheFlatSpotAndAvoidsTheCurrentSite() {
		FakeWorld w = new FakeWorld();
		for (int x = -60; x <= 60; x++) {
			for (int z = -60; z <= 60; z++) {
				// rough terrain everywhere except a plateau east of the player
				boolean plateau = x >= 14 && x <= 40 && z >= -12 && z <= 12;
				w.column(x, z, plateau ? 70 : 64 + Math.floorMod(x * 7 + z * 13, 11));
			}
		}
		SiteEvaluator.Search s = SiteEvaluator.search(new Columns(w), 0, 0, SiteLimits.SEARCH_RADIUS, SiteLimits.SEARCH_STEP, null);
		assertNotNull(s.best(), s.rejected().toString());
		SiteEvaluator.Evaluation best = s.best();
		assertEquals(70, best.floorY());
		assertEquals(0, best.relief());
		assertTrue(best.centerX() >= 21 && best.centerX() <= 33, "inside the plateau: " + best.centerX());

		SiteEvaluator.Search avoiding = SiteEvaluator.search(new Columns(w), 0, 0, SiteLimits.SEARCH_RADIUS, SiteLimits.SEARCH_STEP,
				new Box(10, 60, -20, 45, 90, 20));
		assertTrue(avoiding.best() == null || !avoiding.best().box().intersects(new Box(9, 60, -21, 46, 90, 21)));
		assertTrue(avoiding.rejected().getOrDefault("overlaps the current site", 0) > 0);
	}

	@Test
	void levelingBuildsPadFoundationAndApron() {
		FakeWorld w = new FakeWorld().flat(-30, -30, 30, 30, 64);
		// one corner of the footprint lower (needs foundation), one higher (needs a cut)
		for (int x = -9; x <= -5; x++) {
			for (int z = -9; z <= -5; z++) {
				w.column(x, z, 61);
			}
		}
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				w.column(x, z, 67);
			}
		}
		Columns cols = new Columns(w);
		SiteEvaluator.Evaluation e = SiteEvaluator.evaluate(cols, 0, 0);
		assertTrue(e.ok(), e.problems().toString());
		CellPlan plan = Leveling.plan(cols, e, CellPlan.Facing.SOUTH);
		int f = e.floorY();
		assertEquals(64, f);
		assertEquals(Leveling.PAD, plan.get(0, f, 0));
		assertEquals(Leveling.PAD_EDGE, plan.get(-7, f, 0));
		assertEquals(Leveling.WALL, plan.get(-7, 62, -7), "retaining wall on the low edge");
		assertEquals(Leveling.CORE, plan.get(-6, 62, -6));
		assertEquals(StateKey.AIR, plan.get(6, 66, 6), "the high corner is cut");
		assertEquals(StateKey.AIR, plan.get(0, f + SiteLimits.HEADROOM, 0));
		// apron next to the low corner is raised part of the way, topped with grass
		assertEquals(Leveling.GRASS, plan.get(-8, 63, -8));
		assertEquals(Leveling.DIRT, plan.get(-8, 62, -8));
		assertEquals(Leveling.GRASS, plan.get(-9, 62, -9));
		// nothing outside the box
		assertNull(plan.get(-10, 64, 0));
		// apron on flat ground is untouched
		assertNull(plan.get(0, 64, 9));
	}
}
