package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class MaterialMeterTest {
	static final IntPredicate NOTHING_BUILT = i -> false;
	static final String STONE = "minecraft:stone";
	static final String PLANKS = "minecraft:oak_planks";

	/** Layer 0: 3 stone; layer 1: 2 planks + 1 free (air); layer 2: 1 stone. */
	static MaterialMeter meter() {
		return new MaterialMeter(new String[] {STONE, STONE, STONE, PLANKS, null, PLANKS, STONE}, new int[] {0, 0, 0, 1, 1, 1, 2});
	}

	@Test
	void requiredCountsEveryPaidEntry() {
		assertEquals(Map.of(STONE, 4, PLANKS, 2), meter().required());
	}

	@Test
	void layerStartChecksTheWholeLayer() {
		MaterialMeter m = meter();
		FakeSupply s = new FakeSupply().put(STONE, 2);
		assertFalse(m.before(0, NOTHING_BUILT, s), "only 2 of the 3 stone the layer needs");
		assertEquals(Map.of(STONE, 1), m.missing());
		s.put(STONE, 1);
		assertTrue(m.retry(0, NOTHING_BUILT, s));
		assertEquals(Map.of(), m.missing());
	}

	@Test
	void consumesBlockByBlockAndWaitsAtTheNextLayer() {
		MaterialMeter m = meter();
		FakeSupply s = new FakeSupply().put(STONE, 3);
		for (int i = 0; i < 3; i++) {
			assertTrue(m.before(i, NOTHING_BUILT, s));
			m.paid(i, s);
		}
		assertEquals(Map.of(STONE, 3), s.taken);
		assertEquals(Map.of(STONE, 3), m.spent());
		assertFalse(m.before(3, NOTHING_BUILT, s), "layer 1 needs planks");
		assertEquals(Map.of(PLANKS, 2), m.missing());
		s.put(PLANKS, 2);
		assertTrue(m.retry(3, NOTHING_BUILT, s));
		assertTrue(m.before(3, NOTHING_BUILT, s));
		m.paid(3, s);
		assertTrue(m.before(4, NOTHING_BUILT, s), "air is free");
		m.paid(4, s);
		assertTrue(m.before(5, NOTHING_BUILT, s));
		m.paid(5, s);
		assertEquals(Map.of(STONE, 3, PLANKS, 2), m.spent());
	}

	@Test
	void itemsGoneMidLayerPauseAtTheBlock() {
		MaterialMeter m = meter();
		FakeSupply s = new FakeSupply().put(STONE, 3);
		assertTrue(m.before(0, NOTHING_BUILT, s));
		m.paid(0, s);
		s.stock.put(STONE, 0); // the player dropped them
		assertFalse(m.before(1, NOTHING_BUILT, s));
		assertEquals(Map.of(STONE, 2), m.missing(), "what the rest of the layer lacks");
	}

	@Test
	void blocksAlreadyInTheWorldAreNotCharged() {
		MaterialMeter m = meter();
		FakeSupply s = new FakeSupply().put(STONE, 1);
		IntPredicate built = i -> i == 0 || i == 1;
		assertTrue(m.before(0, built, s), "only index 2 needs stone");
		assertTrue(m.before(2, built, s));
		m.paid(2, s);
		assertEquals(Map.of(STONE, 1), s.taken);
	}

	@Test
	void layerEndsAtTheNextY() {
		MaterialMeter m = meter();
		assertEquals(3, m.layerEnd(0));
		assertEquals(6, m.layerEnd(4));
		assertEquals(7, m.layerEnd(6));
	}
}
