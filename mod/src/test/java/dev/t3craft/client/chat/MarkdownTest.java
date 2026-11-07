package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.client.chat.Markdown.Block;
import dev.t3craft.client.chat.Markdown.CodeBlock;
import dev.t3craft.client.chat.Markdown.Heading;
import dev.t3craft.client.chat.Markdown.ListItem;
import dev.t3craft.client.chat.Markdown.Paragraph;
import dev.t3craft.client.chat.Markdown.Quote;
import dev.t3craft.client.chat.Markdown.Rule;
import dev.t3craft.client.chat.Markdown.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownTest {
	@Test
	void emptyAndNullGiveNoBlocks() {
		assertTrue(Markdown.parse(null).isEmpty());
		assertTrue(Markdown.parse("").isEmpty());
		assertTrue(Markdown.parse("\n\n  \n").isEmpty());
	}

	@Test
	void headingsWithLevelsAndTrailingHashes() {
		List<Block> b = Markdown.parse("# Title\n### Sub ###\n####### not a heading");
		assertEquals(new Heading(1, List.of(Span.plain("Title"))), b.get(0));
		assertEquals(new Heading(3, List.of(Span.plain("Sub"))), b.get(1));
		assertInstanceOf(Paragraph.class, b.get(2));
	}

	@Test
	void paragraphsJoinLinesAndSplitOnBlankLines() {
		List<Block> b = Markdown.parse("one\ntwo\n\nthree");
		assertEquals(2, b.size());
		assertEquals("one two", Markdown.plain(((Paragraph) b.get(0)).spans()));
		assertEquals("three", Markdown.plain(((Paragraph) b.get(1)).spans()));
	}

	@Test
	void bulletNumberedNestedAndTaskLists() {
		List<Block> b = Markdown.parse("- a\n* b\n  - nested\n1. first\n10) tenth\n- [x] done\n- [ ] todo");
		assertEquals(new ListItem(0, "•", List.of(Span.plain("a"))), b.get(0));
		assertEquals(new ListItem(0, "•", List.of(Span.plain("b"))), b.get(1));
		assertEquals(new ListItem(1, "•", List.of(Span.plain("nested"))), b.get(2));
		assertEquals(new ListItem(0, "1.", List.of(Span.plain("first"))), b.get(3));
		assertEquals(new ListItem(0, "10)", List.of(Span.plain("tenth"))), b.get(4));
		assertEquals("☑", ((ListItem) b.get(5)).marker());
		assertEquals("☐", ((ListItem) b.get(6)).marker());
		assertEquals("todo", Markdown.plain(((ListItem) b.get(6)).spans()));
	}

	@Test
	void listItemLazyContinuation() {
		List<Block> b = Markdown.parse("- first line\n  continues here\n- second");
		assertEquals(2, b.size());
		assertEquals("first line continues here", Markdown.plain(((ListItem) b.get(0)).spans()));
	}

	@Test
	void fencedCodeKeepsLinesVerbatimAndLanguage() {
		List<Block> b = Markdown.parse("before\n```java\nint x = 1; // **not bold**\n\n  indented\n```\nafter");
		assertEquals(3, b.size());
		CodeBlock code = (CodeBlock) b.get(1);
		assertEquals("java", code.language());
		assertEquals(List.of("int x = 1; // **not bold**", "", "  indented"), code.lines());
		assertEquals("after", Markdown.plain(((Paragraph) b.get(2)).spans()));
	}

	@Test
	void unclosedFenceRunsToTheEndAndTildesWork() {
		CodeBlock open = (CodeBlock) Markdown.parse("```\na\nb").getFirst();
		assertNull(open.language());
		assertEquals(List.of("a", "b"), open.lines());
		CodeBlock tilde = (CodeBlock) Markdown.parse("~~~sh\nls\n~~~").getFirst();
		assertEquals("sh", tilde.language());
		assertEquals(List.of("ls"), tilde.lines());
		// A shorter fence does not close a longer one.
		CodeBlock nested = (CodeBlock) Markdown.parse("````\n```\ninner\n```\n````").getFirst();
		assertEquals(List.of("```", "inner", "```"), nested.lines());
	}

	@Test
	void quotesAndRules() {
		List<Block> b = Markdown.parse("> quoted\n> more\n\n---\n***\ntext");
		assertEquals("quoted more", Markdown.plain(((Quote) b.get(0)).spans()));
		assertInstanceOf(Rule.class, b.get(1));
		assertInstanceOf(Rule.class, b.get(2));
		assertInstanceOf(Paragraph.class, b.get(3));
	}

	@Test
	void crlfAndTabsAreNormalized() {
		List<Block> b = Markdown.parse("# A\r\n\r\n\t- tabbed");
		assertEquals(2, b.size());
		assertEquals(2, ((ListItem) b.get(1)).depth());
	}

	@Test
	void inlineBoldItalicCode() {
		List<Span> s = Markdown.inline("a **bold** and *it* and _it2_ and `co*de*`");
		assertEquals(List.of(
				Span.plain("a "),
				new Span("bold", true, false, false, null),
				Span.plain(" and "),
				new Span("it", false, true, false, null),
				Span.plain(" and "),
				new Span("it2", false, true, false, null),
				Span.plain(" and "),
				new Span("co*de*", false, false, true, null)), s);
	}

	@Test
	void nestedEmphasisAndDoubleBacktickCode() {
		List<Span> s = Markdown.inline("**bold *both***");
		assertEquals(new Span("bold ", true, false, false, null), s.get(0));
		assertEquals(new Span("both", true, true, false, null), s.get(1));
		assertEquals(List.of(new Span("a `tick` b", false, false, true, null)), Markdown.inline("`` a `tick` b ``"));
	}

	@Test
	void unmatchedMarkersStayLiteral() {
		assertEquals("2 * 3 = 6 and **open", Markdown.plain(Markdown.inline("2 * 3 = 6 and **open")));
		assertEquals(1, Markdown.inline("2 * 3 = 6 and **open").size());
		assertEquals("snake_case_name", Markdown.plain(Markdown.inline("snake_case_name")));
		assertFalse(Markdown.inline("snake_case_name").getFirst().italic());
		assertEquals("a `b", Markdown.plain(Markdown.inline("a `b")));
	}

	@Test
	void escapes() {
		assertEquals(List.of(Span.plain("*not italic* [x]")), Markdown.inline("\\*not italic\\* \\[x\\]"));
	}

	@Test
	void markdownLinksCarryUrlAndStyle() {
		List<Span> s = Markdown.inline("see [the **docs**](https://example.com/a \"title\") now");
		assertEquals(Span.plain("see "), s.get(0));
		assertEquals(new Span("the ", false, false, false, "https://example.com/a"), s.get(1));
		assertEquals(new Span("docs", true, false, false, "https://example.com/a"), s.get(2));
		assertEquals(Span.plain(" now"), s.get(3));
	}

	@Test
	void bareUrlsAreLinksWithoutTrailingPunctuation() {
		List<Span> s = Markdown.inline("open https://t3.example/x?y=1, then (http://a.b/c).");
		assertEquals(new Span("https://t3.example/x?y=1", false, false, false, "https://t3.example/x?y=1"), s.get(1));
		assertEquals(", then (", s.get(2).text());
		assertEquals("http://a.b/c", s.get(3).url());
		assertEquals(").", s.get(4).text());
	}

	@Test
	void urlsInsideCodeAreNotLinks() {
		List<Span> s = Markdown.inline("`curl https://x.y/z`");
		assertEquals(1, s.size());
		assertNull(s.getFirst().url());
		assertTrue(s.getFirst().code());
	}

	@Test
	void headingsAndListsParseInlineMarkup() {
		Heading h = (Heading) Markdown.parse("## Use `gradle`").getFirst();
		assertEquals(new Span("gradle", false, false, true, null), h.spans().get(1));
		ListItem li = (ListItem) Markdown.parse("- **x**: y").getFirst();
		assertTrue(li.spans().getFirst().bold());
	}
}
