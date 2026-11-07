package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Decoding a thread stream into its queue (synthetic fixture, no T3). */
class ThreadQueueTest {
	static JsonObject snapshot() throws Exception {
		try (InputStream in = ThreadQueueTest.class.getResourceAsStream("/fixtures/thread-queue-snapshot.json")) {
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	static JsonObject event(String type, String payload) {
		return JsonParser.parseString("{\"kind\":\"event\",\"sequence\":50,\"event\":{\"type\":\"" + type + "\",\"payload\":" + payload + "}}")
				.getAsJsonObject();
	}

	static List<String> ids(List<QueuedRun> runs) {
		return runs.stream().map(QueuedRun::id).toList();
	}

	@Test
	void unknownUntilSnapshot() {
		ThreadQueue q = new ThreadQueue("vps", "thr-1");
		assertFalse(q.loaded());
		assertFalse(q.apply(JsonParser.parseString("{\"kind\":\"synchronized\"}").getAsJsonObject()));
		assertFalse(q.apply((JsonObject) null));
		assertTrue(q.queued().isEmpty());
	}

	@Test
	void snapshotDecodesQueuedRunsInDeliveryOrder() throws Exception {
		ThreadQueue q = new ThreadQueue("vps", "thr-1");
		assertTrue(q.apply(snapshot()));
		assertTrue(q.loaded());
		// automatic completion first; then queuePosition; run-old has no position → ordinal 5.
		assertEquals(List.of("run-auto", "run-a", "run-b", "run-c", "run-old"), ids(q.queued()));

		QueuedRun a = q.queued().get(1);
		assertEquals("vps", a.envId());
		assertEquals(1L, a.queuePosition());
		assertEquals("first queued", a.preview());
		assertTrue(a.movable());
		assertFalse(a.held());

		QueuedRun b = q.queued().get(2);
		assertTrue(b.held());
		assertNull(b.preview(), "blank text → no preview");

		QueuedRun c = q.queued().get(3);
		assertEquals(java.time.Instant.parse("2026-11-07T10:03:00Z").toEpochMilli(), c.requestedAt());

		QueuedRun old = q.queued().get(4);
		assertNull(old.queuePosition(), "missing queuePosition");
		assertEquals("thr-1", old.threadId(), "missing threadId falls back to the stream's thread");
		assertNull(old.preview(), "no message for it");
		assertNull(old.requestedAt());

		QueuedRun auto = q.queued().get(0);
		assertTrue(auto.automatic());
		assertFalse(auto.movable());
	}

	@Test
	void minimalRunWithOnlyAnId() {
		QueuedRun r = QueuedRun.fromJson("vps", "t", JsonParser.parseString("{\"id\":\"r\"}").getAsJsonObject());
		assertNotNull(r);
		assertEquals("", r.status());
		assertFalse(r.queued());
		assertEquals(0, r.ordinal());
		assertNull(r.queuePosition());
		assertNull(QueuedRun.fromJson("vps", "t", JsonParser.parseString("{\"status\":\"queued\"}").getAsJsonObject()));
		assertNull(QueuedRun.fromJson("vps", "t", null));
		// Zero / garbage positions are treated as absent.
		assertNull(QueuedRun.fromJson("vps", "t", JsonParser.parseString("{\"id\":\"r\",\"queuePosition\":0}").getAsJsonObject()).queuePosition());
		assertNull(QueuedRun.fromJson("vps", "t", JsonParser.parseString("{\"id\":\"r\",\"queuePosition\":\"x\"}").getAsJsonObject()).queuePosition());
	}

	@Test
	void eventsUpdateTheQueue() throws Exception {
		ThreadQueue q = new ThreadQueue("vps", "thr-1");
		q.apply(snapshot());
		// run-a starts (leaves the queue); a new run is queued; positions shift.
		q.apply(List.of(
				event("run.updated", "{\"id\":\"run-a\",\"threadId\":\"thr-1\",\"ordinal\":2,\"status\":\"starting\"}"),
				event("run.created", "{\"id\":\"run-new\",\"threadId\":\"thr-1\",\"ordinal\":7,\"status\":\"queued\",\"queuePosition\":5,\"userMessageId\":\"msg-7\"}"),
				event("message.updated", "{\"id\":\"msg-7\",\"role\":\"user\",\"text\":\"late one\"}"),
				event("run.updated", "{\"id\":\"run-c\",\"threadId\":\"thr-1\",\"ordinal\":4,\"status\":\"cancelled\"}"),
				event("thread.updated", "{\"id\":\"thr-1\"}"),
				event("run.updated", "{\"id\":\"elsewhere\",\"threadId\":\"thr-2\",\"status\":\"queued\"}")));
		assertEquals(List.of("run-auto", "run-b", "run-old", "run-new"), ids(q.queued()));
		assertEquals("late one", q.queued().get(3).preview());
	}

	@Test
	void resubscribeForgetsUntilNextSnapshot() throws Exception {
		ThreadQueue q = new ThreadQueue("vps", "thr-1");
		q.apply(snapshot());
		q.reset();
		assertFalse(q.loaded());
		assertTrue(q.queued().isEmpty());
		q.apply(JsonParser.parseString("{\"kind\":\"snapshot\",\"projection\":{}}").getAsJsonObject());
		assertTrue(q.loaded(), "a snapshot without runs is an empty, known queue");
		assertTrue(q.queued().isEmpty());
	}

	@Test
	void previewIsFirstLineTrimmed() {
		assertEquals("hello", QueuedRun.preview("\n\n  hello  \nworld"));
		assertNull(QueuedRun.preview("  \n "));
		String longText = "x".repeat(500);
		String p = QueuedRun.preview(longText);
		assertEquals(QueuedRun.PREVIEW_MAX, p.length());
		assertTrue(p.endsWith("…"));
	}
}
