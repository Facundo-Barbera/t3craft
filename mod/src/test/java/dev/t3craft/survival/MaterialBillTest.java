package dev.t3craft.survival;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialBillTest {
	@Test
	void countsItemsAndSkipsAirAndBlanks() {
		MaterialBill b = MaterialBill.of(List.of("minecraft:oak_planks", "minecraft:air", "minecraft:oak_planks", "", "minecraft:torch"));
		b.add(null).add("minecraft:cave_air").add("minecraft:cobblestone", 0).add("minecraft:cobblestone", 4);
		assertEquals(Map.of("minecraft:oak_planks", 2, "minecraft:torch", 1, "minecraft:cobblestone", 4), b.items());
		assertEquals(List.of("minecraft:oak_planks", "minecraft:torch", "minecraft:cobblestone"), List.copyOf(b.items().keySet()), "first-seen order");
		assertEquals(7, b.total());
		assertFalse(b.isEmpty());
		assertTrue(new MaterialBill().add("minecraft:air").isEmpty());
	}

	@Test
	void missingIsTheShortfall() {
		MaterialBill b = new MaterialBill().add("minecraft:oak_planks", 10).add("minecraft:torch", 2);
		assertEquals(Map.of("minecraft:oak_planks", 4, "minecraft:torch", 2), b.missing(Map.of("minecraft:oak_planks", 6)));
		assertFalse(b.affordable(null));
		assertTrue(b.affordable(Map.of("minecraft:oak_planks", 64, "minecraft:torch", 2)));
		assertThrows(UnsupportedOperationException.class, () -> b.items().put("x", 1));
	}
}
