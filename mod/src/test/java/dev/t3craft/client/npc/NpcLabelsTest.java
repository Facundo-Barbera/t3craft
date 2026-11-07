package dev.t3craft.client.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class NpcLabelsTest {
	private static final double FAR_AIM = Double.POSITIVE_INFINITY;

	private static NpcInfo npc(String title) {
		return new NpcInfo("vps:t1", title, "⚙ " + title, "VPS", "VPS · proj", 0x55FFFF, "editing Foo.java", 2, false, "working",
				"vps:p", "proj", "vps/working", "working", "opus 5.5", "claude", "feat/x", "repo");
	}

	@Test
	void compactLineIsGlyphPlusTruncatedTitle() {
		assertEquals("⚙ Fix grid", NpcLabels.compactLine("working", "Fix grid", 24));
		assertEquals("! Needs you", NpcLabels.compactLine("needs_attention", "Needs you", 24));
		assertEquals("· x", NpcLabels.compactLine("weird", "x", 24));
		String line = NpcLabels.compactLine("done", "Implement the whole modular HQ grid layout", 24);
		assertEquals("✔ Implement the whole mod…", line);
		assertEquals(24, line.substring(2).codePointCount(0, line.length() - 2));
	}

	@Test
	void truncateHandlesEdgesAndWhitespace() {
		assertEquals("abc", NpcLabels.truncate("abc", 3));
		assertEquals("ab…", NpcLabels.truncate("abcd", 3));
		assertEquals("…", NpcLabels.truncate("abcd", 1));
		assertEquals("a…", NpcLabels.truncate("a   bcdef", 3)); // no space before the ellipsis
		assertEquals("⚙ a b", NpcLabels.compactLine("working", "  a\n  b ", 24));
		assertEquals("⚙ ", NpcLabels.compactLine("working", null, 24));
		// code points, not chars: emoji are not split
		assertEquals("😀😀…", NpcLabels.truncate("😀😀😀😀", 3));
	}

	@Test
	void compactNameplateIsOneLineWithoutChips() {
		NpcInfo n = npc("A rather long thread title that goes on");
		String tag = Nameplate.compact(n, LabelConfig.DEFAULTS).getString();
		assertFalse(tag.contains("\n"));
		assertFalse(tag.contains("opus"));
		assertFalse(tag.contains("⎇"));
		assertEquals("⚙ A rather long thread ti…", tag);
	}

	@Test
	void onlyOneFarLabelPerNpc() {
		// Far away the panel is hidden, so the name tag is the only text over the NPC.
		NpcLabels.Visibility v = new NpcLabels.Visibility();
		assertFalse(v.update(20, FAR_AIM, 4));
		// The near panel never repeats the title, the chips stay off the name tag, and the activity
		// is left to the speech bubble.
		NpcInfo n = npc("Fix grid");
		String detail = Nameplate.detail(n, ChipsConfig.DEFAULTS).getString();
		assertFalse(detail.contains("Fix grid"));
		assertFalse(detail.contains("editing"));
		assertTrue(detail.contains("◆ opus 5.5"));
	}

	@Test
	void visibilityByDistanceWithHysteresis() {
		NpcLabels.Visibility v = new NpcLabels.Visibility();
		assertFalse(v.update(4.5, FAR_AIM, 4));
		assertTrue(v.update(3.9, FAR_AIM, 4));
		// inside the hysteresis band: stays
		for (int i = 0; i < 50; i++) {
			assertTrue(v.update(4.8, FAR_AIM, 4));
		}
		// outside: grace ticks, then hides
		for (int i = 0; i < NpcLabels.GRACE_TICKS; i++) {
			assertTrue(v.update(6, FAR_AIM, 4));
		}
		assertFalse(v.update(6, FAR_AIM, 4));
		// re-entering the band does not reopen it; only the detail distance does
		assertFalse(v.update(4.8, FAR_AIM, 4));
		assertTrue(v.update(4.0, FAR_AIM, 4));
	}

	@Test
	void aimOpensAndKeepsWithWiderCone() {
		NpcLabels.Visibility v = new NpcLabels.Visibility();
		assertFalse(v.update(10, 0.7, 4));
		assertTrue(v.update(10, 0.3, 4));
		assertTrue(v.update(10, 0.8, 4)); // wider keep cone
		for (int i = 0; i < NpcLabels.GRACE_TICKS; i++) {
			v.update(10, 2.0, 4);
		}
		assertFalse(v.update(10, 2.0, 4));
	}

	@Test
	void clickPinsThenExpiresOrTogglesOff() {
		NpcLabels.Visibility v = new NpcLabels.Visibility();
		v.toggle();
		assertTrue(v.update(10, FAR_AIM, 4));
		v.toggle();
		assertFalse(v.update(10, FAR_AIM, 4));
		v.reveal();
		int shown = 0;
		for (int i = 0; i < NpcLabels.CLICK_TICKS + NpcLabels.GRACE_TICKS + 5; i++) {
			if (v.update(10, FAR_AIM, 4)) {
				shown++;
			}
		}
		assertTrue(shown >= NpcLabels.CLICK_TICKS - 1 && shown <= NpcLabels.CLICK_TICKS + NpcLabels.GRACE_TICKS);
		assertFalse(v.shown());
		v.reveal();
		assertTrue(v.update(10, FAR_AIM, 4));
		v.update(NpcLabels.CLICK_MAX_DIST + 1, FAR_AIM, 4); // walking away ends the pin
		for (int i = 0; i < NpcLabels.GRACE_TICKS; i++) {
			v.update(NpcLabels.CLICK_MAX_DIST + 1, FAR_AIM, 4);
		}
		assertFalse(v.update(NpcLabels.CLICK_MAX_DIST + 1, FAR_AIM, 4));
	}

	@Test
	void aimMissGeometry() {
		// looking along +x from the origin
		assertEquals(0.0, NpcLabels.aimMiss(0, 0, 0, 1, 0, 0, 5, 0, 0, 16), 1e-9);
		assertEquals(0.5, NpcLabels.aimMiss(0, 0, 0, 2, 0, 0, 5, 0.5, 0, 16), 1e-9);
		assertEquals(Double.POSITIVE_INFINITY, NpcLabels.aimMiss(0, 0, 0, 1, 0, 0, -5, 0, 0, 16));
		assertEquals(Double.POSITIVE_INFINITY, NpcLabels.aimMiss(0, 0, 0, 1, 0, 0, 20, 0, 0, 16));
		assertEquals(Double.POSITIVE_INFINITY, NpcLabels.aimMiss(0, 0, 0, 0, 0, 0, 1, 0, 0, 16));
	}

	@Test
	void bubbleStacksAbovePanel() {
		assertEquals(3.05, NpcLabels.bubbleBase(2.45, 3.05, 0.27, 0), 1e-9);
		assertEquals(3.05, NpcLabels.bubbleBase(2.45, 3.05, 0.27, 1), 1e-9);
		assertEquals(2.45 + 3 * 0.27 + 0.1, NpcLabels.bubbleBase(2.45, 3.05, 0.27, 3), 1e-9);
		assertEquals(0, NpcLabels.lines(""));
		assertEquals(3, NpcLabels.lines("a\nb\nc"));
	}

	@Test
	void configParsesAdditiveBlock() {
		assertEquals(LabelConfig.DEFAULTS, LabelConfig.parse(null));
		assertEquals(LabelConfig.DEFAULTS, LabelConfig.parse(JsonParser.parseString("{\"chips\":{}}").getAsJsonObject()));
		LabelConfig c = LabelConfig.parse(JsonParser.parseString(
				"{\"labels\":{\"mode\":\"detailed\",\"detailDistance\":6.5,\"titleMax\":30}}").getAsJsonObject());
		assertEquals(new LabelConfig(false, 6.5, 30), c);
		LabelConfig bad = LabelConfig.parse(JsonParser.parseString(
				"{\"labels\":{\"mode\":7,\"detailDistance\":-1,\"titleMax\":\"x\"}}").getAsJsonObject());
		assertEquals(LabelConfig.DEFAULTS, bad);
	}
}
