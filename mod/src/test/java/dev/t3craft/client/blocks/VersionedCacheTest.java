package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.t3craft.blocks.PlaqueText;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The plaque renderer's glyph cache: one build per (position, text version). */
class VersionedCacheTest {
	@Test
	void buildsOncePerVersion() {
		VersionedCache<Long, String> c = new VersionedCache<>(8);
		PlaqueText t = new PlaqueText();
		t.set(List.of("API", "", "", "vps"), 0x55FFFF);
		String a = c.get(1L, t.version(), () -> new String("API"));
		for (int i = 0; i < 100; i++) {
			assertSame(a, c.get(1L, t.version(), () -> "rebuilt"), "steady plaque: no rebuild per frame");
		}
		assertEquals(1, c.builds());
		t.set(List.of("API", "", "", "vps"), 0x55FFFF); // same text: same version
		c.get(1L, t.version(), () -> "rebuilt");
		assertEquals(1, c.builds());
		t.set(List.of("API", "", "#2", "vps"), 0x55FFFF);
		assertEquals("rebuilt", c.get(1L, t.version(), () -> "rebuilt"));
		assertEquals(2, c.builds());
	}

	@Test
	void isBounded() {
		VersionedCache<Long, String> c = new VersionedCache<>(4);
		for (long i = 0; i < 10; i++) {
			c.get(i, 1, () -> "x");
		}
		assertEquals(true, c.size() <= 4);
	}
}
