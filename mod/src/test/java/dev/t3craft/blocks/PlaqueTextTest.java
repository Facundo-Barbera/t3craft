package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The plaque's text: normalized, versioned only on real changes, readable on the ink plate. */
class PlaqueTextTest {
	@Test
	void versionChangesOnlyWhenTheTextChanges() {
		PlaqueText t = new PlaqueText();
		assertTrue(t.set(List.of("Web", "", "", "Acmesite"), 0x55FFFF));
		long v = t.version();
		assertFalse(t.set(List.of("Web", "", "", "Acmesite"), 0x55FFFF), "same text: no new version");
		assertFalse(t.set(Arrays.asList("Web ", "", "", " Acmesite", ""), 0x55FFFF), "same after normalizing");
		assertEquals(v, t.version());
		assertTrue(t.set(List.of("Web", "", "#2", "Acmesite"), 0x55FFFF));
		assertNotEquals(v, t.version());
		long v2 = t.version();
		assertTrue(t.set(List.of("Web", "", "#2", "Acmesite"), 0xFF5555), "colour counts");
		assertNotEquals(v2, t.version());
	}

	@Test
	void versionsAreGloballyUnique() {
		PlaqueText a = new PlaqueText();
		PlaqueText b = new PlaqueText();
		a.set(List.of("A"), 0xFFFFFF);
		b.set(List.of("A"), 0xFFFFFF);
		assertNotEquals(a.version(), b.version(), "a plaque replaced at the same spot never reuses a cached version");
	}

	@Test
	void linesAreClippedAndTrimmed() {
		PlaqueText t = new PlaqueText();
		t.set(List.of("A very long project name indeed", "", "", "", "fifth line"), 0xFFFFFF);
		assertEquals(1, t.lines().size(), "trailing empties dropped, at most four lines");
		assertEquals(PlaqueText.WIDTH, t.lines().get(0).length());
		assertTrue(t.lines().get(0).endsWith("…"));
	}

	@Test
	void darkAccentsAreLifted() {
		assertEquals(0x55FFFF, PlaqueText.readable(0x55FFFF));
		int black = PlaqueText.readable(0x000000);
		assertTrue((black >> 8 & 0xFF) > 90, Integer.toHexString(black));
	}

	@Test
	void dataRoundTripsKeys() {
		CompoundTag t = PlaqueText.data(0x123456, "API", "", "#2", "vps");
		assertEquals("API", t.getStringOr("line0", ""));
		assertFalse(t.contains("line1"), "empty lines omitted");
		assertEquals("#2", t.getStringOr("line2", ""));
		assertEquals("vps", t.getStringOr("line3", ""));
		assertEquals(0x123456, t.getIntOr(PlaqueText.COLOR_KEY, 0));
	}
}
