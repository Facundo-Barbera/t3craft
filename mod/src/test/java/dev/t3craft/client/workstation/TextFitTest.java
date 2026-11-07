package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextFitTest {
	@Test
	void normalizeCollapsesWhitespaceAndControls() {
		assertEquals("a b c", TextFit.normalize("  a\n\tb \u0007 c  "));
		assertEquals("", TextFit.normalize(null));
		assertEquals("", TextFit.normalize(" \n "));
	}

	@Test
	void clipKeepsShortAndEllipsizesLong() {
		assertEquals("short", TextFit.clip("short", 10));
		assertEquals("exactly10!", TextFit.clip("exactly10!", 10));
		String c = TextFit.clip("this title is far too long", 10);
		assertEquals(10, c.length());
		assertTrue(c.endsWith("…"));
		// Cut lands right after a space: no "tit …" with a dangling blank.
		assertEquals("this tit…", TextFit.clip("this tit  plus", 9));
		assertEquals("…", TextFit.clip("abc", 1));
		assertEquals("", TextFit.clip("abc", 0));
	}

	@Test
	void wrapsOnWordBoundaries() {
		assertEquals(List.of("the quick brown", "fox jumps over", "the lazy dog"), TextFit.wrap("the quick brown fox jumps over the lazy dog", 15, 5));
	}

	@Test
	void wrapEllipsizesWhenOutOfLines() {
		List<String> l = TextFit.wrap("one two three four five six seven eight nine ten", 10, 2);
		assertEquals(2, l.size());
		assertTrue(l.get(1).endsWith("…"), l.toString());
		l.forEach(x -> assertTrue(x.length() <= 10, x));
	}

	@Test
	void wrapEllipsizesFullLastLine() {
		List<String> l = TextFit.wrap("x".repeat(30), 10, 2);
		assertEquals(List.of("x".repeat(10), "x".repeat(9) + "…"), l);
	}

	@Test
	void wrapSplitsLongWords() {
		assertEquals(List.of("abcdefgh", "ijkl mno"), TextFit.wrap("abcdefghijkl mno", 8, 3));
	}

	@Test
	void wrapEmptyAndDegenerate() {
		assertTrue(TextFit.wrap(null, 10, 3).isEmpty());
		assertTrue(TextFit.wrap("   ", 10, 3).isEmpty());
		assertTrue(TextFit.wrap("abc", 0, 3).isEmpty());
		assertTrue(TextFit.wrap("abc", 10, 0).isEmpty());
		assertEquals(List.of("abc"), TextFit.wrap("abc", 10, 1));
	}
}
