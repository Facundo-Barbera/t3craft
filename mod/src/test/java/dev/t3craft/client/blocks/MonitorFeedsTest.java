package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.chat.ThreadFeed;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** One stream per thread however many monitors show it; open/close follow the wanted set. */
class MonitorFeedsTest {
	static final class Env implements ChatSource.Env {
		final StreamRegistry registry = new StreamRegistry(RpcFrames::subscribeThread);

		@Override
		public String id() {
			return "e";
		}

		@Override
		public String label() {
			return "E";
		}

		@Override
		public T3Connection.Status status() {
			return T3Connection.Status.OK;
		}

		@Override
		public String statusDetail() {
			return "";
		}

		@Override
		public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
			return registry.subscribe(threadId, listener);
		}

		@Override
		public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
			return new CompletableFuture<>();
		}

		@Override
		public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
			return CompletableFuture.completedFuture(null);
		}
	}

	static final class Wire implements StreamRegistry.Sender {
		final List<JsonObject> sent = new ArrayList<>();

		@Override
		public boolean send(String frame) {
			sent.add(JsonParser.parseString(frame).getAsJsonObject());
			return true;
		}

		long subscribes() {
			return sent.stream().filter(f -> f.toString().contains("subscribeThread")).count();
		}
	}

	/** Opener over a real StreamRegistry, counting opens per key. */
	static final class Opener implements MonitorFeeds.Opener {
		final Env env = new Env();
		final Map<String, Integer> opens = new HashMap<>();
		boolean fail;

		@Override
		public MonitorFeeds.@Nullable Feed open(String key) {
			opens.merge(key, 1, Integer::sum);
			if (fail) {
				return null;
			}
			ThreadFeed f = new ThreadFeed(env, key.substring(key.indexOf(':') + 1), () -> {
			});
			f.start();
			return new MonitorFeeds.Feed() {
				@Override
				public ChatTranscript transcript() {
					return f.transcript();
				}

				@Override
				public boolean live() {
					return f.transcript().hasSnapshot();
				}

				@Override
				public void close() {
					f.close();
				}
			};
		}
	}

	@Test
	void noDuplicateSubscriptionForTheSameThreadOnSeveralMonitors() {
		Opener op = new Opener();
		Wire w = new Wire();
		op.env.registry.onOpen(w);
		MonitorVisibility v = new MonitorVisibility(MonitorSettings.DEFAULT);
		MonitorFeeds feeds = new MonitorFeeds(op);
		List<MonitorVisibility.Observation> obs = List.of(
				new MonitorVisibility.Observation(1, "e:T", 3, true),
				new MonitorVisibility.Observation(2, "e:T", 4, true),
				new MonitorVisibility.Observation(3, "e:T", 6, true));
		for (int i = 0; i < 5; i++) {
			feeds.retain(v.update(obs, i * 250L), i * 250L);
		}
		assertEquals(1, feeds.size());
		assertEquals(1, op.opens.get("e:T"), "opened once");
		assertEquals(1, op.env.registry.refCount("T"), "one listener for three monitors");
		assertEquals(1, w.subscribes(), "one subscribeThread on the wire");

		// a chat open on the same thread shares the server stream too
		ThreadFeed chat = new ThreadFeed(op.env, "T", () -> {
		});
		chat.start();
		assertEquals(2, op.env.registry.refCount("T"));
		assertEquals(1, w.subscribes());
		chat.close();

		feeds.retain(Set.of(), 2_000);
		assertEquals(0, feeds.size());
		assertEquals(0, op.env.registry.activeThreads(), "released on the server");
	}

	@Test
	void opensAndClosesFollowingTheWantedSet() {
		Opener op = new Opener();
		op.env.registry.onOpen(new Wire());
		MonitorFeeds feeds = new MonitorFeeds(op);
		feeds.retain(Set.of("e:a", "e:b"), 0);
		assertNotNull(feeds.feed("e:a"));
		assertNotNull(feeds.feed("e:b"));
		feeds.retain(Set.of("e:b"), 10);
		assertNull(feeds.feed("e:a"));
		assertEquals(0, op.env.registry.refCount("a"));
		assertEquals(1, op.env.registry.refCount("b"));
		feeds.close();
		assertEquals(0, op.env.registry.activeThreads());
	}

	@Test
	void failedOpenWaitsBeforeRetrying() {
		Opener op = new Opener();
		op.fail = true;
		MonitorFeeds feeds = new MonitorFeeds(op);
		feeds.retain(Set.of("e:a"), 0);
		feeds.retain(Set.of("e:a"), 1_000);
		assertEquals(1, op.opens.get("e:a"), "no retry storm every tick");
		op.fail = false;
		feeds.retain(Set.of("e:a"), MonitorFeeds.RETRY_MS + 1);
		assertEquals(2, op.opens.get("e:a"));
		assertTrue(feeds.feed("e:a") != null);
	}

	@Test
	void bridgeKeysDoNotOpen() {
		assertNull(MonitorLiveLog.open("no-colon"));
		assertNull(MonitorLiveLog.open("env:"));
	}
}
