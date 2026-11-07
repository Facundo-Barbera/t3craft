package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
	void exitCarriesSuccessValue() {
		var ok = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"3\",\"exit\":{\"_tag\":\"Success\",\"value\":{\"threads\":[]}}}");
		assertNull(ok.failure());
		assertTrue(ok.value().isJsonObject());
		var fail = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"3\",\"exit\":{\"_tag\":\"Failure\",\"cause\":[]}}");
		assertNull(fail.value());
		assertNotNull(fail.failure());
	}

	@Test
	void oneShotReadsFailFastWhenNotConnectedAndRejectOtherTags() {
		var env = new Environments.Environment("vps", "vps", java.net.URI.create("http://127.0.0.1:1"));
		T3Connection c = new T3Connection(env, () -> null, new T3Connection.Listener() {
			@Override
			public void onStatus(String envId, T3Connection.Status s, String d) {
			}

			@Override
			public void onShell(String envId, ShellState.View v) {
			}
		}, HttpClient.newHttpClient());
		try {
			var e = assertThrows(ExecutionException.class, () -> c.getArchivedShellSnapshot().get(5, TimeUnit.SECONDS));
			assertInstanceOf(T3Connection.CallException.class, e.getCause());
			var w = assertThrows(ExecutionException.class,
					() -> c.call("orchestration.dispatchCommand", new JsonObject(), v -> v).get(5, TimeUnit.SECONDS));
			assertTrue(w.getCause().getMessage().contains("not a read-only"));
		} finally {
			c.stop();
		}
	}

	/** End to end: the read goes out on the shell's socket with its own id and its Exit completes it. */
	@Test
	void oneShotReadMultiplexedOnShellSocket() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			String settled = threadJson("old", 50 * H, "{\"settledOverride\":\"settled\",\"settledAt\":\"" + iso(48 * H) + "\"}").toString();
			fake.script = msg -> {
				var o = JsonParser.parseString(msg).getAsJsonObject();
				if (!"Request".equals(o.get("_tag").getAsString())) {
					return List.of();
				}
				String id = o.get("id").getAsString();
				return switch (o.get("tag").getAsString()) {
					case RpcFrames.SUBSCRIBE_SHELL -> List.of(T3ConnectionTest.chunk(authoritative(1).toString()),
							T3ConnectionTest.chunk("{\"kind\":\"synchronized\"}"));
					case RpcFrames.GET_OLDER_SETTLED_THREADS -> List.of("{\"_tag\":\"Exit\",\"requestId\":\"" + id
							+ "\",\"exit\":{\"_tag\":\"Success\",\"value\":{\"snapshotSequence\":9,\"threads\":[" + settled + "],\"counts\":[]}}}");
					default -> List.of();
				};
			};
			var events = new LinkedBlockingQueue<T3ConnectionTest.Event>();
			T3Connection c = T3ConnectionTest.connect(fake, FakeT3Server.TOKEN, events);
			try {
				T3ConnectionTest.await(events, e -> e.status() == T3Connection.Status.OK);
				var page = c.getOlderSettledThreads(ArchiveWire.Cursor.before(NOW), 10).get(10, TimeUnit.SECONDS);
				assertEquals(9, page.snapshotSequence());
				assertEquals("old", page.threads().get(0).id());
				var req = fake.received.stream().map(s -> JsonParser.parseString(s).getAsJsonObject())
						.filter(o -> RpcFrames.GET_OLDER_SETTLED_THREADS.equals(o.has("tag") ? o.get("tag").getAsString() : null))
						.findFirst().orElseThrow();
				assertNotEquals("1", req.get("id").getAsString());
				assertEquals(10, req.getAsJsonObject("payload").get("limit").getAsInt());
				assertEquals(T3Connection.Status.OK, c.status(), "the shell subscription is untouched");
				assertTrue(Set.of(RpcFrames.GET_OLDER_SETTLED_THREADS, RpcFrames.GET_ARCHIVED_SHELL_SNAPSHOT).equals(T3Connection.ONE_SHOT_READS));
			} finally {
				c.stop();
			}
		}
	}
}
