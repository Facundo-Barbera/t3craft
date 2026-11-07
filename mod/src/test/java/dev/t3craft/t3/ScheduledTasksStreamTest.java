package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code scheduledTasks.subscribe} over the shared {@link StreamRegistry} (keyed, non-thread stream). */
class ScheduledTasksStreamTest {
	static StreamRegistry.Subscription subscribe(StreamRegistry r, StreamRegistry.Listener l) {
		return r.subscribe(T3Connection.SCHEDULED_TASKS_STREAM, (id, key) -> RpcFrames.subscribeScheduledTasks(id), l);
	}

	@Test
	void frameIsAReadOnlyRequestWithEmptyPayload() {
		var o = com.google.gson.JsonParser.parseString(RpcFrames.subscribeScheduledTasks("7")).getAsJsonObject();
		assertEquals("Request", o.get("_tag").getAsString());
		assertEquals("7", o.get("id").getAsString());
		assertEquals("scheduledTasks.subscribe", o.get("tag").getAsString());
		assertEquals(0, o.getAsJsonObject("payload").size());
	}

	@Test
	void sharesTheRegistryPathAndRefcount() {
		StreamRegistry r = new StreamRegistry(RpcFrames::subscribeThread);
		StreamRegistryTest.Wire w = new StreamRegistryTest.Wire();
		r.onOpen(w);
		StreamRegistryTest.Rec a = new StreamRegistryTest.Rec();
		StreamRegistryTest.Rec b = new StreamRegistryTest.Rec();
		StreamRegistry.Subscription sa = subscribe(r, a);
		StreamRegistry.Subscription sb = subscribe(r, b);
		assertEquals(List.of("Request:scheduledTasks.subscribe"), w.tags(), "one server subscription for two views");
		assertEquals(2, r.refCount(T3Connection.SCHEDULED_TASKS_STREAM));
		String id = w.lastRequestId();

		// thread streams keep their own frame builder next to it
		r.subscribe("T1", new StreamRegistryTest.Rec());
		assertEquals("Request:orchestration.subscribeThread", w.tags().getLast());
		assertNotEquals(id, w.lastRequestId(), "one id generator for both");

		assertTrue(r.onChunk(id, List.of(StreamRegistryTest.item("1"))));
		assertEquals(List.of("1"), a.items);
		assertEquals(List.of("1"), b.items);

		sa.close();
		sb.close();
		assertEquals("Interrupt", w.tags().getLast());
		assertEquals(id, w.lastRequestId());
	}

	@Test
	void resubscribesWithItsOwnFrameAfterReconnect() {
		StreamRegistry r = new StreamRegistry(RpcFrames::subscribeThread);
		StreamRegistryTest.Rec a = new StreamRegistryTest.Rec();
		subscribe(r, a);
		StreamRegistryTest.Wire w1 = new StreamRegistryTest.Wire();
		r.onOpen(w1);
		assertEquals(List.of("Request:scheduledTasks.subscribe"), w1.tags());
		r.onClosed();
		assertEquals(1, a.ended.size());
		StreamRegistryTest.Wire w2 = new StreamRegistryTest.Wire();
		r.onOpen(w2);
		assertEquals(List.of("Request:scheduledTasks.subscribe"), w2.tags());
	}

	@Test
	void serverFailureEndsTheStream() {
		StreamRegistry r = new StreamRegistry(RpcFrames::subscribeThread);
		StreamRegistryTest.Wire w = new StreamRegistryTest.Wire();
		r.onOpen(w);
		StreamRegistryTest.Rec a = new StreamRegistryTest.Rec();
		subscribe(r, a);
		assertTrue(r.onExit(w.lastRequestId(), "Failure ScheduledTaskError", null));
		assertEquals(List.of("Failure ScheduledTaskError"), a.ended);
	}
}
