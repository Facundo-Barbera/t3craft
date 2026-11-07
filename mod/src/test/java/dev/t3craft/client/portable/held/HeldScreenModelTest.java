package dev.t3craft.client.portable.held;

import static dev.t3craft.client.portable.held.HeldFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.portable.held.HeldScreenModel.Kind;
import dev.t3craft.client.portable.held.HeldScreenModel.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeldScreenModelTest {
	@Test
	void notPairedIsSaidHonestly() {
		List<Row> rows = HeldScreenModel.build(input(PortableState.EMPTY));
		assertEquals(Kind.HEADER, rows.get(0).kind());
		assertTrue(String.join(" ", texts(rows)).contains("No T3 environment paired"));
	}

	@Test
	void disconnectedListsEachEnvironmentStatusAndNoStaleThreads() {
		PortableState s = PortableState.of("direct", Map.of("vps", new String[] {"my-vps", "offline", ""}),
				List.of(thread("vps", "a", "Old row", "working", 2, null, false)), true, null);
		List<String> t = texts(HeldScreenModel.build(input(s)));
		assertTrue(t.contains("Not connected."), t.toString());
		assertTrue(t.stream().anyMatch(x -> x.startsWith("V") && x.contains("offline")), t.toString());
		assertTrue(t.stream().noneMatch(x -> x.contains("Old row")), "no stale rows shown as live");
	}

	@Test
	void connectedButEmpty() {
		List<String> t = texts(HeldScreenModel.build(input(connected())));
		assertTrue(String.join(" ", t).contains("No threads to show."), t.toString());
	}

	@Test
	void unifiedListAttentionFirstWithChipsAndTopThreadMessage() {
		PortableState s = connected(
				thread("mini", "w", "Working one", "working", 2, "Running tests", false),
				thread("vps", "n", "Needs me", "needs_attention", 0, "Approval needed: command", false),
				thread("vps", "d", "Done one", "done", 3, "All green", true));
		List<Row> rows = HeldScreenModel.build(input(s));
		List<String> t = texts(rows);
		assertTrue(t.get(0).startsWith("T3") && t.get(0).contains("!1"), "header shows the attention count: " + t.get(0));
		assertEquals("V ! Needs me", t.get(1), "top: the thread that needs attention, chip V (my-vps)");
		assertEquals(HeldScreenModel.SELECTED, rows.get(1).bg());
		assertTrue(t.get(2).contains("Approval"), "its latest message: " + t);
		assertEquals(HeldScreenModel.AMBER, rows.get(2).spans().get(0).color());
		int rule = t.indexOf("");
		assertEquals(Kind.RULE, rows.get(rule).kind());
		assertTrue(t.get(rule + 1).startsWith("M * Working"), "threads of all environments in one list: " + t);
		assertEquals("V v Done one", t.get(rule + 2));
		Row chipRow = rows.get(1);
		assertEquals(EnvBadge.of("vps", "my-vps", null).background() & 0xFFFFFF, chipRow.spans().get(0).bg());
	}

	@Test
	void orderIsPriorityThenLiveBeforeSettledKeepingSourceOrder() {
		PortableThread a = thread("vps", "a", "A", "done", 3, null, true);
		PortableThread b = thread("vps", "b", "B", "done", 3, null, false);
		PortableThread c = thread("mini", "c", "C", "needs_attention", 0, null, false);
		PortableThread d = thread("vps", "d", "D", "done", 3, null, false);
		assertEquals(List.of(c, b, d, a), HeldScreenModel.order(List.of(a, b, c, d)));
	}

	@Test
	void longListIsCappedWithMoreCountAndFitsTheScreen() {
		List<PortableThread> many = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			many.add(thread("vps", "t" + i, "Thread " + i, "working", 2, null, false));
		}
		List<Row> rows = HeldScreenModel.build(input(connected(many.toArray(PortableThread[]::new))));
		assertTrue(rows.size() <= HeldScreenRaster.maxRows());
		assertTrue(texts(rows).getLast().matches("\\+\\d+ more"), texts(rows).getLast());
		int shown = (int) texts(rows).stream().filter(x -> x.contains("Thread ")).count();
		assertEquals(40, shown + Integer.parseInt(texts(rows).getLast().replaceAll("\\D", "")));
	}

	@Test
	void longTitlesAndMessagesAreClippedToTheWidth() {
		String longText = "word ".repeat(60);
		List<Row> rows = HeldScreenModel.build(input(connected(thread("vps", "x", "A very very long thread title indeed", "working", 2, longText, false))));
		for (Row r : rows) {
			int w = r.spans().stream().mapToInt(sp -> MONO.width(sp.text()) + (sp.bg() != 0 ? HeldScreenModel.CHIP_PAD : 0)).sum();
			assertTrue(w <= HeldScreenRaster.textWidth(), "row too wide: " + text(r));
		}
		long msgRows = rows.stream().filter(r -> r.bg() == HeldScreenModel.SELECTED).count() - 1;
		assertEquals(HeldScreenModel.MESSAGE_LINES, msgRows);
		assertTrue(text(rows.get(1 + HeldScreenModel.MESSAGE_LINES)).endsWith("..."));
	}

	@Test
	void equalInputsGiveEqualRows() {
		PortableState s = connected(thread("vps", "n", "Needs me", "needs_attention", 0, "Waiting for your answer", false));
		assertEquals(HeldScreenModel.build(input(s)), HeldScreenModel.build(input(s)));
	}

	@Test
	void badgeLetterAndColour() {
		assertEquals("V", EnvBadge.of("vps", "my-vps", null).initial());
		assertEquals("M", EnvBadge.of("mini", "mini", null).initial());
		assertEquals("X", EnvBadge.of("x1", " ", null).initial(), "blank label falls back to the id");
		assertEquals(0xFF123456, EnvBadge.of("vps", "my-vps", 0x123456).background(), "config colour wins");
		assertEquals(EnvBadge.of("vps", "a", null).background(), EnvBadge.of("vps", "b", null).background(), "stable by id");
	}
}
