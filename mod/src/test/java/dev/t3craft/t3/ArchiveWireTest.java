package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchiveWireTest {
	@Test
	void olderSettledRequestShape() {
		JsonObject p = ArchiveWire.olderSettledRequest(new ArchiveWire.Cursor(NOW, "t9"), 500, List.of("p1"));
		assertEquals("2026-11-07T12:00:00Z", p.getAsJsonObject("before").get("settledAt").getAsString());
		assertEquals("t9", p.getAsJsonObject("before").get("threadId").getAsString());
		assertEquals(50, p.get("limit").getAsInt(), "clamped to the server's maximum");
		assertEquals("p1", p.getAsJsonArray("projectIds").get(0).getAsString());

		JsonObject strict = ArchiveWire.olderSettledRequest(ArchiveWire.Cursor.before(NOW), -3, null);
		assertFalse(strict.getAsJsonObject("before").has("threadId"));
		assertFalse(strict.has("projectIds"));
		assertEquals(0, strict.get("limit").getAsInt());
	}

	@Test
	void decodesOlderSettledPageTolerantly() {
		String a = threadJson("a", 30 * H, "{\"settledOverride\":\"settled\",\"settledAt\":\"" + iso(30 * H) + "\"}").toString();
		var page = ArchiveWire.decodeOlderSettled(JsonParser.parseString("""
				{"snapshotSequence":42,"threads":[%s,{"title":"no id"},7,null],
				 "counts":[{"projectId":"project-1","count":3},{"projectId":"project-2","count":"x"},{"count":1},{"projectId":"project-1","count":2}],
				 "futureField":true}""".formatted(a)));
		assertEquals(42, page.snapshotSequence());
		assertEquals(1, page.threads().size());
		assertEquals("a", page.threads().get(0).id());
		assertEquals(NOW - 30 * H, page.threads().get(0).settledAt());
		assertEquals(5L, page.counts().get("project-1"), "duplicate project rows add up");
		assertEquals(1, page.counts().size(), "malformed count rows are skipped");
		assertEquals(5, page.total());

		assertSame(ArchiveWire.OlderSettledPage.EMPTY, ArchiveWire.decodeOlderSettled(null));
		assertSame(ArchiveWire.OlderSettledPage.EMPTY, ArchiveWire.decodeOlderSettled(JsonParser.parseString("[]")));
		assertTrue(ArchiveWire.decodeOlderSettled(JsonParser.parseString("{}")).threads().isEmpty());
	}

	@Test
	void decodesArchivedSnapshot() {
		String t = threadJson("z", 100 * H, "{\"archivedAt\":\"" + iso(90 * H) + "\"}").toString();
		var snap = ArchiveWire.decodeArchived(JsonParser.parseString("""
				{"schemaVersion":1,"snapshotSequence":7,
				 "projects":[{"id":"project-1","title":"T3Craft","workspaceRoot":"/w/t3craft"},{"id":"project-2","title":"","workspaceRoot":"/w/forja"},{"title":"x"}],
				 "threads":[%s]}""".formatted(t)));
		assertEquals(7, snap.snapshotSequence());
		assertEquals("T3Craft", snap.projectTitles().get("project-1"));
		assertEquals("forja", snap.projectTitles().get("project-2"), "blank title falls back to the folder");
		assertEquals(NOW - 90 * H, snap.threads().get(0).archivedAt());
		assertSame(ArchiveWire.ArchivedSnapshot.EMPTY, ArchiveWire.decodeArchived(JsonParser.parseString("\"nope\"")));
	}

	@Test
	void cursorAfterLastThread() {
		ThreadShell s = ThreadShell.fromJson(threadJson("q", H, "{\"settledAt\":\"" + iso(H) + "\"}"));
		assertEquals(new ArchiveWire.Cursor(NOW - H, "q"), ArchiveWire.Cursor.after(s));
		assertNull(ArchiveWire.Cursor.after(thread("r", H)));
	}

	@Test
	void decodesStreamCallValues() {
		var page = ArchiveWire.decodeOlderSettled(List.of(json("{\"snapshotSequence\":1}"), json("{\"snapshotSequence\":2,\"threads\":[]}")));
		assertEquals(2, page.snapshotSequence(), "the last value wins");
		assertSame(ArchiveWire.ArchivedSnapshot.EMPTY, ArchiveWire.decodeArchived(List.<JsonObject>of()));
		assertSame(ArchiveWire.OlderSettledPage.EMPTY, ArchiveWire.decodeOlderSettled((List<JsonObject>) null));
		assertEquals(0, ArchiveWire.archivedRequest().size());
	}
}
