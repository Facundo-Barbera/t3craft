package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AgentSkinsTest {
	@Test
	void sameThreadAlwaysGetsSameSkin() {
		AgentSkins.Pick a = AgentSkins.forThread("my-vps:thread-123", null);
		AgentSkins.Pick b = AgentSkins.forThread("my-vps:thread-123", null);
		assertEquals(a, b);
		assertTrue(a.path().startsWith(AgentSkins.DIR));
		// Pinned so a refactor cannot silently reshuffle everyone's look.
		assertEquals(AgentSkins.forThread("my-vps:thread-123", null).path(),
				AgentSkins.DIR + AgentSkins.character("my-vps:thread-123").id() + "_" + AgentSkins.shirt("my-vps", null));
	}

	@Test
	void charactersSpreadEvenlyOverThreads() {
		int n = 6000;
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < n; i++) {
			counts.merge(AgentSkins.character("env-" + (i % 3) + ":" + java.util.UUID.nameUUIDFromBytes(("t" + i).getBytes())).id(), 1,
					Integer::sum);
		}
		assertEquals(AgentSkins.CHARACTERS.size(), counts.size(), "every character is used");
		double expected = (double) n / AgentSkins.CHARACTERS.size();
		counts.forEach((id, c) -> assertTrue(c > expected * 0.75 && c < expected * 1.25, id + " used " + c + " times"));
	}

	@Test
	void sequentialIdsStillLookDifferent() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 12; i++) {
			seen.add(AgentSkins.character("env:thread-" + i).id());
		}
		assertTrue(seen.size() >= 6, "12 neighbouring ids gave only " + seen);
	}

	@Test
	void shirtFollowsEnvironment() {
		AgentSkins.Pick a = AgentSkins.forThread("vps:t1", null);
		AgentSkins.Pick b = AgentSkins.forThread("vps:t2", null);
		assertEquals(shirtOf(a), shirtOf(b), "same environment, same shirt");
		// Configured colour snaps to the nearest palette entry.
		for (int i = 0; i < AgentSkins.SHIRTS.length; i++) {
			assertEquals(i, AgentSkins.nearest(AgentSkins.SHIRTS[i]));
			assertEquals(String.valueOf(i), shirtOf(AgentSkins.forThread("vps:t1", AgentSkins.SHIRTS[i])));
		}
		assertEquals(5, AgentSkins.nearest(0x33AA66)); // green-ish → green
		// Environments spread over the palette.
		Set<Integer> shirts = new HashSet<>();
		for (String env : new String[] {"my-vps", "my-mini", "laptop", "mini-acmesite", "work", "home", "ci", "lab"}) {
			shirts.add(AgentSkins.shirt(env, null));
		}
		assertTrue(shirts.size() >= 4, "shirts " + shirts);
	}

	@Test
	void helperWearsApprenticeVariantOfParent() {
		AgentSkins.Pick parent = AgentSkins.forThread("vps:parent", 0x8A2F5E);
		AgentSkins.Pick helper = AgentSkins.forHelper("vps:parent", 0x8A2F5E);
		assertNotEquals(parent.path(), helper.path());
		assertEquals(parent.path().replace(AgentSkins.DIR, AgentSkins.DIR + "apprentice/"), helper.path());
		assertEquals(parent.slim(), helper.slim());
	}

	@Test
	void envOfNpcId() {
		assertEquals("my-vps", AgentSkins.envOf("my-vps:abc:def"));
		assertEquals("solo", AgentSkins.envOf("solo"));
		assertEquals("", AgentSkins.envOf(null));
	}

	@Test
	void missingTextureFallsBackToOwnDefaultThenVanilla() {
		AgentSkins.Pick pick = AgentSkins.forThread("vps:t", null);
		assertSame(pick, AgentSkins.usable(pick, p -> true));
		assertSame(AgentSkins.DEFAULT, AgentSkins.usable(pick, p -> p.equals(AgentSkins.DEFAULT_PATH)));
		assertNull(AgentSkins.usable(pick, p -> false));
	}

	@Test
	void everyReferencedPngExistsAndIsASkin() throws Exception {
		for (AgentSkins.Pick p : AgentSkins.all()) {
			try (InputStream in = getClass().getClassLoader().getResourceAsStream(p.resource())) {
				assertNotNull(in, "missing " + p.resource());
				BufferedImage img = ImageIO.read(in);
				assertNotNull(img, "not a PNG: " + p.resource());
				assertEquals(64, img.getWidth(), p.resource());
				assertEquals(64, img.getHeight(), p.resource());
				// Face front (8..15, 8..15) is opaque: the base layer is fully drawn.
				for (int y = 8; y < 16; y++) {
					for (int x = 8; x < 16; x++) {
						assertEquals(0xFF, img.getRGB(x, y) >>> 24, p.resource() + " face pixel " + x + "," + y);
					}
				}
			}
		}
		assertEquals(AgentSkins.CHARACTERS.size() * AgentSkins.SHIRTS.length * 2 + 1, AgentSkins.all().size());
	}

	@Test
	void shirtPaletteMatchesEnvBadge() throws Exception {
		java.lang.reflect.Field f = dev.t3craft.client.chat.EnvBadge.class.getDeclaredField("PALETTE");
		f.setAccessible(true);
		int[] badge = (int[]) f.get(null);
		assertEquals(badge.length, AgentSkins.SHIRTS.length);
		for (int i = 0; i < badge.length; i++) {
			assertEquals(badge[i] & 0xFFFFFF, AgentSkins.SHIRTS[i]);
		}
		assertEquals(Math.floorMod(AgentSkins.stableHash("my-vps"), 8), AgentSkins.shirt("my-vps", null));
	}

	private static String shirtOf(AgentSkins.Pick p) {
		return p.path().substring(p.path().lastIndexOf('_') + 1);
	}
}
