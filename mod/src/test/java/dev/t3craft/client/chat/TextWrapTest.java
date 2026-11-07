package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.Markdown.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextWrapTest {
	/** Every char is 1 unit wide; bold adds 1 per piece (like the game font). */
	private static final TextWrap.Measure MONO = s -> s.text().length() + (s.bold() ? 1 : 0);

	private static List<String> plain(List<TextWrap.Line> lines) {
		return lines.stream().map(TextWrap.Line::plain).toList();
	}

	@Test
	void fitsOnOneLine() {
		List<TextWrap.Line> l = TextWrap.wrap(List.of(Span.plain("hello world")), 20, MONO);
		assertEquals(List.of("hello world"), plain(l));
		assertEquals(11, l.getFirst().width());
	}

	@Test
	void wrapsAtSpacesWithoutLeadingOrTrailingSpaces() {
		List<TextWrap.Line> l = TextWrap.wrap(List.of(Span.plain("aaa bbb ccc ddd")), 7, MONO);
		assertEquals(List.of("aaa bbb", "ccc ddd"), plain(l));
		l.forEach(line -> assertTrue(line.width() <= 7));
	}

	@Test
	void longWordIsSplitByCharacters() {
		List<TextWrap.Line> l = TextWrap.wrap(List.of(Span.plain("ab abcdefghij")), 4, MONO);
		assertEquals(List.of("ab", "abcd", "efgh", "ij"), plain(l));
	}

	@Test
	void explicitNewlinesBreak() {
		assertEquals(List.of("a", "", "b"), plain(TextWrap.wrap(List.of(Span.plain("a\n\nb")), 10, MONO)));
	}

	@Test
	void emptyInputGivesOneEmptyLine() {
		assertEquals(List.of(""), plain(TextWrap.wrap(List.of(), 10, MONO)));
	}

	@Test
	void stylesAcrossSpansKeepPositionsForLinkHitTesting() {
		List<Span> spans = List.of(Span.plain("go "), new Span("here", false, false, false, "https://x"), Span.plain(" now"));
		List<TextWrap.Line> l = TextWrap.wrap(spans, 40, MONO);
		assertEquals(1, l.size());
		TextWrap.Line line = l.getFirst();
		assertNull(line.urlAt(1));
		assertEquals("https://x", line.urlAt(3));
		assertEquals("https://x", line.urlAt(6));
		assertNull(line.urlAt(7));
	}

	@Test
	void measureIsUsedPerPiece() {
		// Bold pieces are 1 wider each: "xx"=3, " "=2, so "xx xx" = 8.
		List<TextWrap.Line> l = TextWrap.wrap(List.of(new Span("xx xx xx", true, false, false, null)), 8, MONO);
		assertEquals(List.of("xx xx", "xx"), plain(l));
	}

	@Test
	void zeroOrNegativeWidthStillProgresses() {
		List<TextWrap.Line> l = TextWrap.wrap(List.of(Span.plain("abc")), 0, MONO);
		assertEquals(List.of("a", "b", "c"), plain(l));
	}

	@Test
	void tokensSplitWordsSpacesAndNewlines() {
		assertEquals(List.of("a", "  ", "b", "\n", "c"), TextWrap.tokens("a  b\nc"));
	}
}
