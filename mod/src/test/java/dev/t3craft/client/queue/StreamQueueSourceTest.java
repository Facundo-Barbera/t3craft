package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.StreamRegistry;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The queue reads through the shared {@link StreamRegistry} (no socket, no T3): one subscription with the chat. */
class StreamQueueSourceTest {
	static final class Wire implements StreamRegistry.Sender {
		final List<JsonObject> sent = new ArrayList<>();

		@Override
		public boolean send(String frame) {
			sent.add(JsonParser.parseString(frame).getAsJsonObject());
			return true;
		}

		String subscribeId() {
			for (JsonObject o : sent) {
				if (o.has("tag") && RpcFrames.SUBSCRIBE_THREAD.equals(o.get("tag").getAsString())) {
					return o.get("id").getAsString();
				}
			}
			throw new AssertionError("no subscribe sent");
		}

		long subscribes() {
			return sent.stream().filter(o -> o.has("tag") && RpcFrames.SUBSCRIBE_THREAD.equals(o.get("tag").getAsString())).count();
		}
	}

	static final class ChatView implements StreamRegistry.Listener {
		int items;

		@Override
		public void onSubscribed() {
		}

		@Override
		public void onItems(List<JsonObject> its) {
			items += its.size();
		}

		@Override
		public void onEnded(@Nullable String failure) {
		}
	}

	@Test
	void queueAndChatShareOneThreadStream() throws Exception {
		StreamRegistry registry = new StreamRegistry(RpcFrames::subscribeThread);
		Wire wire = new Wire();
		registry.onOpen(wire);
		QueueSource src = (env, thread, l) -> StreamQueueSource.bridge(l, sl -> registry.subscribe(thread, sl));
		QueueStore store = new QueueStore(() -> src, () -> {
		});

		ChatView chat = new ChatView();
		StreamRegistry.Subscription chatSub = registry.subscribe("thr-1", chat);
		QueueStore.Watch w = store.watch("vps", "thr-1");
		assertEquals(1, wire.subscribes(), "one server subscription for chat + queue");
		assertEquals(2, registry.refCount("thr-1"));

		registry.onChunk(wire.subscribeId(), List.of(ThreadQueueTest.snapshot()));
		assertEquals(QueueStore.Status.OK, store.get("vps", "thr-1").status());
		assertEquals(5, store.count("vps", "thr-1"));
		assertEquals(1, chat.items);

		w.close();
		assertEquals(1, registry.refCount("thr-1"), "closing the queue keeps the chat's stream");
		chatSub.close();
		assertEquals(0, registry.refCount("thr-1"));
	}

	@Test
	void noDirectConnectionMeansUnavailable() {
		StreamQueueSource src = new StreamQueueSource(() -> null);
		assertNull(src.open("vps", "thr-1", new QueueSource.Listener() {
			@Override
			public void onSubscribed() {
			}

			@Override
			public void onItems(List<JsonObject> items) {
			}

			@Override
			public void onEnded(@Nullable String failure) {
			}
		}));
		QueueStore store = new QueueStore(() -> src, () -> {
		});
		store.watch("vps", "thr-1");
		assertEquals(QueueStore.Status.UNAVAILABLE, store.get("vps", "thr-1").status());
		assertTrue(store.get("vps", "thr-1").reason().contains("direct"));
	}
}
