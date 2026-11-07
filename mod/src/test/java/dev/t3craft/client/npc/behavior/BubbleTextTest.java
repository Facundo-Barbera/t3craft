package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BubbleTextTest {
	@Test
	void wrapsAndClips() {
		String s = "Refactored the pathfinder and added tests for the board slots; next I will wire the anchors into the HQ builder and then";
		List<String> l = BubbleText.lines(s, Mode.WORKING, false, Emote.NONE);
		assertEquals(BubbleText.MAX_LINES, l.size());
		l.forEach(x -> assertTrue(x.length() <= BubbleText.WIDTH + 1, x));
		assertTrue(l.getLast().endsWith("…"));
	}

	@Test
	void fallbackAndEmote() {
		assertEquals(List.of("! I need you!"), BubbleText.lines(null, Mode.ATTENTION, false, Emote.EXCLAIM));
		assertEquals(List.of("Something went wrong"), BubbleText.lines("  ", Mode.ATTENTION, true, Emote.NONE));
	}

	@Test
	void splitsLongTokens() {
		List<String> l = BubbleText.wrap("x".repeat(80), 34, 5);
		assertEquals(List.of("x".repeat(34), "x".repeat(34), "x".repeat(12)), l);
	}

	@Test
	void modeFromState() {
		assertEquals(Mode.WORKING, Mode.of("working"));
		assertEquals(Mode.ATTENTION, Mode.of("needs_attention"));
		assertEquals(Mode.ATTENTION, Mode.of("error"));
		assertEquals(Mode.COMPLETED, Mode.of("done"));
		assertEquals(Mode.IDLE, Mode.of("idle"));
		assertEquals(Mode.IDLE, Mode.of("???"));
	}
}
