package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The archive terminal's screen: header, latest three, cached by store version. */
class TerminalDisplayTest {
	private static final java.util.function.IntFunction<String> HEADER = n -> "ARCHIVE · " + n + " hilos";

	private static List<ArchiveEntry> entries(int n) {
		List<ArchiveEntry> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(ArchiveFixtures.entry(i % 2 == 0 ? "vps" : "mini", "t" + i, "p", (i + 1) * ArchiveFixtures.H));
		}
		out.sort(ArchiveEntry.NEWEST_FIRST);
		return out;
	}

	@Test
	void headerCountAndLatestThree() {
		List<TerminalDisplay.Line> l = TerminalDisplay.lines(entries(5), ZoneOffset.UTC, HEADER, "sin hilos cerrados");
		assertEquals("ARCHIVE · 5 hilos", l.getFirst().text());
		assertEquals(TerminalDisplay.HEADER, l.getFirst().color());
		assertEquals(1 + 2 * TerminalDisplay.RECENT, l.size(), "header + title/meta for the latest three");
		assertEquals("Thread t0", l.get(1).text(), "newest close first");
		assertEquals("● vps · ✔ 2026-11-07", l.get(2).text());
		assertEquals(ArchiveLabel.envColor("vps"), l.get(2).color());
		assertEquals("Thread t1", l.get(3).text());
		assertEquals("Thread t2", l.get(5).text());
		for (TerminalDisplay.Line line : l) {
			assertTrue(line.text().length() <= TerminalDisplay.WIDTH, line.text());
		}
	}

	@Test
	void fewerThanThreeAndEmpty() {
		assertEquals(3, TerminalDisplay.lines(entries(1), ZoneOffset.UTC, HEADER, "x").size());
		List<TerminalDisplay.Line> empty = TerminalDisplay.lines(List.of(), ZoneOffset.UTC, HEADER, "sin hilos cerrados");
		assertEquals(List.of("ARCHIVE · 0 hilos", "sin hilos cerrados"), empty.stream().map(TerminalDisplay.Line::text).toList());
	}

	@Test
	void rebuiltOnlyWhenTheVersionMoves() {
		TerminalDisplay d = new TerminalDisplay();
		AtomicInteger reads = new AtomicInteger();
		List<ArchiveEntry> data = entries(4);
		TerminalDisplay.Screen a = d.show(7, () -> {
			reads.incrementAndGet();
			return data;
		}, ZoneOffset.UTC, HEADER, "x");
		for (int i = 0; i < 100; i++) {
			assertSame(a, d.show(7, () -> {
				reads.incrementAndGet();
				return data;
			}, ZoneOffset.UTC, HEADER, "x"), "same version: same screen");
		}
		assertEquals(1, reads.get(), "entries read once per version");
		assertEquals(1, d.builds());
		TerminalDisplay.Screen b = d.show(8, () -> entries(6), ZoneOffset.UTC, HEADER, "x");
		assertEquals(8, b.version());
		assertEquals("ARCHIVE · 6 hilos", b.lines().getFirst().text());
		assertEquals(2, d.builds());
		d.invalidate();
		d.show(8, () -> data, ZoneOffset.UTC, HEADER, "x");
		assertEquals(3, d.builds(), "invalidate forces a rebuild");
	}
}
