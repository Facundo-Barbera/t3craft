package dev.t3craft.client.archive;

import static dev.t3craft.client.archive.ArchiveFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.t3craft.t3.ThreadShell;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

class ArchiveLabelTest {
	@Test
	void fourLinesTitleEnvDateProject() {
		ArchiveEntry e = ArchiveEntry.of("vps", "my-vps", settled("a", "p1", 30 * H), "t3craft");
		List<ArchiveLabel.Line> l = ArchiveLabel.lines(e, ZoneOffset.UTC);
		assertEquals(4, l.size());
		assertEquals("Thread a", l.get(0).text());
		assertEquals("● my-vps", l.get(1).text());
		assertEquals(ArchiveLabel.envColor("vps"), l.get(1).color());
		assertEquals("✔ 2026-11-06", l.get(2).text());
		assertEquals("t3craft", l.get(3).text());
		for (ArchiveLabel.Line line : l) {
			assertTrue(line.text().length() <= ArchiveLabel.WIDTH, line.text());
		}
		ArchiveEntry long_ = new ArchiveEntry("k", "e", "e", "t", "x".repeat(80), "p", "y".repeat(80), null, ArchiveEntry.Kind.ARCHIVED);
		List<ArchiveLabel.Line> l2 = ArchiveLabel.lines(long_, ZoneId.of("America/Argentina/Buenos_Aires"));
		assertTrue(l2.get(0).text().endsWith("…"));
		assertEquals("▣ ?", l2.get(2).text());
	}

	@Test
	void dateUsesTheZone() {
		long late = java.time.Instant.parse("2026-11-07T02:00:00Z").toEpochMilli();
		assertEquals("2026-11-07", ArchiveLabel.date(late, ZoneOffset.UTC));
		assertEquals("2026-11-06", ArchiveLabel.date(late, ZoneId.of("America/Argentina/Buenos_Aires")));
	}

	@Test
	void environmentColoursMatchTheHqAndStayReadable() {
		assertEquals(DyeColor.BLUE, ArchiveLabel.envDye("vps"), "documented in feat-hq-design.md");
		assertEquals(DyeColor.BLACK, ArchiveLabel.envDye("acmesite"));
		assertNotEquals(ArchiveLabel.envColor("vps"), ArchiveLabel.envColor("acmesite"));
		for (DyeColor d : DyeColor.values()) {
			int c = ArchiveLabel.readable(d.getTextColor() & 0xFFFFFF);
			int luma = ((c >> 16 & 0xFF) * 299 + (c >> 8 & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
			assertTrue(luma >= 80, d + " readable");
		}
	}

	@Test
	void candidates() {
		assertTrue(ArchiveEntry.isCandidate(settled("a", "p", H)));
		assertTrue(ArchiveEntry.isCandidate(archived("a", "p", H)));
		assertFalse(ArchiveEntry.isCandidate(active("a", "p")));
		JsonObject sub = json("s", "p");
		sub.addProperty("settledOverride", "settled");
		JsonObject lineage = new JsonObject();
		lineage.addProperty("relationshipToParent", "subagent");
		sub.add("lineage", lineage);
		assertFalse(ArchiveEntry.isCandidate(ThreadShell.fromJson(sub)), "subagents never get a slot");
		JsonObject del = json("d", "p");
		del.addProperty("archivedAt", "2026-11-01T00:00:00Z");
		del.addProperty("deletedAt", "2026-11-02T00:00:00Z");
		assertFalse(ArchiveEntry.isCandidate(ThreadShell.fromJson(del)));

		ArchiveEntry a = ArchiveEntry.of("vps", "", archived("x", "mcp:abcdef123456", 2 * H), null);
		assertEquals(ArchiveEntry.Kind.ARCHIVED, a.kind());
		assertEquals("vps", a.envLabel());
		assertEquals("project abcdef12", a.projectLabel());
		assertEquals(NOW - 2 * H, a.closedAtMs());
		assertEquals("vps:mcp:abcdef123456", a.projectKey());
	}

	@Test
	void newestFirstPutsUnknownLast() {
		ArchiveEntry unknown = new ArchiveEntry("a", "e", "e", "a", "t", "p", "p", null, ArchiveEntry.Kind.SETTLED);
		ArchiveEntry old = entry("e", "b", "p", 10 * H);
		ArchiveEntry fresh = entry("e", "c", "p", H);
		assertEquals(List.of(fresh, old, unknown), java.util.stream.Stream.of(unknown, old, fresh).sorted(ArchiveEntry.NEWEST_FIRST).toList());
	}
}
