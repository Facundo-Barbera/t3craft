package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StreamRegistryTest {
	/** Captures frames; can pretend to be disconnected. */
	static final class Wire implements StreamRegistry.Sender {
		final List<JsonObject> sent = new ArrayList<>();
		boolean up = true;

		@Override
		public boolean send(String frame) {
			if (!up) {
				return false;
			}
			sent.add(JsonParser.parseString(frame).getAsJsonObject());
			return true;
		}

		List<String> tags() {
			return sent.stream().map(o -> o.get("_tag").getAsString() + (o.has("tag") ? ":" + o.get("tag").getAsString() : "")).toList();
		}

		String lastRequestId() {
			return sent.getLast().has("id") ? sent.getLast().get("id").getAsString() : sent.getLast().get("requestId").getAsString();
		}
	}

	static final class Rec implements StreamRegistry.Listener {
		int subscribed;
		final List<String> items = new ArrayList<>();
		final List<@Nullable String> ended = new ArrayList<>();

		@Override
		public void onSubscribed() {
			subscribed++;
			items.clear();
		}

		@Override
		public void onItems(List<JsonObject> its) {
			its.forEach(i -> items.add(i.get("n").getAsString()));
		}

		@Override
		public void onEnded(@Nullable String failure) {
			ended.add(failure);
		}
	}

	static JsonObject item(String n) {
		JsonObject o = new JsonObject();
		o.addProperty("n", n);
		return o;
	}

	static StreamRegistry registry() {
		return new StreamRegistry(RpcFrames::subscribeThread);
	}

	@Test
	void viewsOfOneThreadShareOneSubscriptionAndTheLastCloseInterrupts() {
		StreamRegistry r = registry();
		Wire w = new Wire();
		r.onOpen(w);
		Rec a = new Rec();
		Rec b = new Rec();
		StreamRegistry.Subscription sa = r.subscribe("T1", a);
		StreamRegistry.Subscription sb = r.subscribe("T1", b);
		assertEquals(List.of("Request:orchestration.subscribeThread"), w.tags(), "one server subscription for two views");
		assertEquals(2, r.refCount("T1"));
		assertEquals(1, r.activeThreads());
		String id = w.sent.getFirst().get("id").getAsString();
		assertEquals("T1", w.sent.getFirst().getAsJsonObject("payload").get("threadId").getAsString());

		assertTrue(r.onChunk(id, List.of(item("snap"), item("ev1"))));
		assertEquals(List.of("snap", "ev1"), a.items);
		assertEquals(List.of("snap", "ev1"), b.items);

		sa.close();
		sa.close(); // idempotent
		assertEquals(1, r.refCount("T1"));
		assertEquals(1, w.sent.size(), "no interrupt while a view remains");
		sb.close();
		assertEquals(0, r.activeThreads());
		assertEquals("Interrupt", w.sent.getLast().get("_tag").getAsString());
		assertEquals(id, w.sent.getLast().get("requestId").getAsString());
		assertTrue(r.onChunk(id, List.of(item("late"))), "late chunks of a released request are still ours (acked, dropped)");
		assertFalse(b.items.contains("late"));
	}

	@Test
	void lateJoinerGetsReplayOfWhatOthersSaw() {
		StreamRegistry r = registry();
		Wire w = new Wire();
		r.onOpen(w);
		Rec a = new Rec();
		r.subscribe("T1", a);
		String id = w.lastRequestId();
		r.onChunk(id, List.of(item("snap")));
		r.onChunk(id, List.of(item("ev1")));
		Rec late = new Rec();
		r.subscribe("T1", late);
		assertEquals(1, late.subscribed);
		assertEquals(List.of("snap", "ev1"), late.items);
		assertEquals(1, w.sent.size(), "replay, no new server request");
		r.onChunk(id, List.of(item("ev2")));
		assertEquals(List.of("snap", "ev1", "ev2"), late.items);
	}

	@Test
	void replayOverflowForcesAFreshSubscriptionForEveryone() {
		StreamRegistry r = registry();
		Wire w = new Wire();
		r.onOpen(w);
		Rec a = new Rec();
		r.subscribe("T1", a);
		String id = w.lastRequestId();
		List<JsonObject> many = new ArrayList<>();
		for (int i = 0; i <= StreamRegistry.REPLAY_CAP; i++) {
			many.add(item("i" + i));
		}
		r.onChunk(id, many);
		Rec late = new Rec();
		r.subscribe("T1", late);
		assertEquals(List.of("Request:orchestration.subscribeThread", "Interrupt", "Request:orchestration.subscribeThread"), w.tags());
		String newId = w.lastRequestId();
		assertFalse(newId.equals(id));
		assertEquals(2, a.subscribed, "everyone resets for the new snapshot");
		assertEquals(1, late.subscribed);
		assertTrue(a.items.isEmpty());
		r.onChunk(newId, List.of(item("snap2")));
		assertEquals(List.of("snap2"), a.items);
		assertEquals(List.of("snap2"), late.items);
	}

	@Test
	void subscribeWhileOfflineWaitsAndReconnectResubscribesAll() {
		StreamRegistry r = registry();
		Rec a = new Rec();
		Rec b = new Rec();
		r.subscribe("T1", a);
		r.subscribe("T2", b);
		Wire w = new Wire();
		r.onOpen(w);
		assertEquals(2, w.sent.size(), "both threads subscribed when the socket comes up");
		String id1 = w.sent.get(0).get("id").getAsString();
		r.onChunk(id1, List.of(item("x")));

		r.onClosed();
		assertEquals(1, a.ended.size());
		assertNull(a.ended.getFirst(), "socket loss is a pause, not a failure");
		assertFalse(r.onChunk(id1, List.of(item("y"))) && a.items.contains("y"));

		Wire w2 = new Wire();
		r.onOpen(w2);
		assertEquals(2, w2.sent.size());
		assertFalse(w2.sent.get(0).get("id").getAsString().equals(id1), "fresh request ids after reconnect");
		assertEquals(3, a.subscribed, "at subscribe, at first open, after reconnect");
		assertTrue(a.items.isEmpty());
	}

	@Test
	void serverEndIsReportedAndNotInterruptedAgain() {
		StreamRegistry r = registry();
		Wire w = new Wire();
		r.onOpen(w);
		Rec a = new Rec();
		StreamRegistry.Subscription s = r.subscribe("T1", a);
		String id = w.lastRequestId();
		assertTrue(r.onExit(id, "Fail OrchestrationThreadNotFoundError", null));
		assertEquals("Fail OrchestrationThreadNotFoundError", a.ended.getFirst());
		s.close();
		assertEquals(1, w.sent.size(), "an ended request needs no interrupt");
		assertFalse(r.onExit("1", null, null), "the shell's request is not ours");
	}

	@Test
	void oneShotCallsCollectValuesAndFailOnDisconnect() throws Exception {
		StreamRegistry r = registry();
		CompletableFuture<List<JsonObject>> offline = r.call(RpcFrames.GET_TURN_ITEM, new JsonObject());
		assertTrue(offline.isCompletedExceptionally());

		Wire w = new Wire();
		r.onOpen(w);
		CompletableFuture<List<JsonObject>> f = r.call(RpcFrames.GET_TURN_ITEM, new JsonObject());
		String id = w.lastRequestId();
		assertEquals("orchestration.getTurnItem", w.sent.getLast().get("tag").getAsString());
		JsonObject value = item("result");
		assertTrue(r.onExit(id, null, value));
		assertEquals("result", f.get().getFirst().get("n").getAsString());
		assertEquals(0, r.pendingCalls());

		CompletableFuture<List<JsonObject>> lost = r.call(RpcFrames.GET_THREAD_PROJECTION, new JsonObject());
		r.onClosed();
		assertTrue(lost.isCompletedExceptionally());
		assertTrue(r.call(RpcFrames.GET_TURN_ITEM, new JsonObject()).isCompletedExceptionally(), "offline: fails at once");
	}

	@Test
	void brokenListenerDoesNotStarveOthers() {
		StreamRegistry r = registry();
		Wire w = new Wire();
		r.onOpen(w);
		r.subscribe("T1", new StreamRegistry.Listener() {
			@Override
			public void onSubscribed() {
				throw new IllegalStateException("boom");
			}

			@Override
			public void onItems(List<JsonObject> items) {
				throw new IllegalStateException("boom");
			}

			@Override
			public void onEnded(@Nullable String failure) {
			}
		});
		Rec ok = new Rec();
		r.subscribe("T1", ok);
		r.onChunk(w.lastRequestId(), List.of(item("a")));
		assertEquals(List.of("a"), ok.items);
	}

	@Test
	void framesAndHistoryUri() {
		JsonObject f = JsonParser.parseString(RpcFrames.subscribeThread("7", "thr")).getAsJsonObject();
		assertEquals("orchestration.subscribeThread", f.get("tag").getAsString());
		JsonObject p = f.getAsJsonObject("payload");
		assertTrue(p.get("requestCompletionMarker").getAsBoolean());
		assertTrue(p.get("acceptBoundedSnapshot").getAsBoolean());
		assertFalse(RpcFrames.subscribeThread("7", "thr").contains("dispatchCommand"), "read-only");
		assertEquals("https://host:3773/api/orchestration/threads/th%2F1%20x/history?cursor=a%2Bb%2Fc%3D",
				T3Connection.historyUri(URI.create("https://host:3773"), "th/1 x", "a+b/c=").toString());
		RpcFrames.Exit e = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"9\",\"exit\":{\"_tag\":\"Success\",\"value\":{\"item\":null}}}");
		assertEquals("9", e.requestId());
		assertNull(e.failure());
		assertTrue(e.value().has("item"));
	}
}
