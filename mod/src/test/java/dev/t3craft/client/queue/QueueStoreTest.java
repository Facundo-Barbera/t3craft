package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** QueueStore over a fake {@link QueueSource} (no socket, no T3). */
class QueueStoreTest {
	static final class FakeSource implements QueueSource {
		final Map<String, Listener> open = new HashMap<>();
		final List<String> opened = new ArrayList<>();
		final List<String> closed = new ArrayList<>();
		final Set<String> offline = new java.util.HashSet<>();

		@Override
		public @Nullable Handle open(String envId, String threadId, Listener listener) {
			String k = envId + ":" + threadId;
			if (offline.contains(envId)) {
				return null;
			}
			opened.add(k);
			open.put(k, listener);
			return () -> {
				if (open.remove(k) != null) {
					closed.add(k);
				}
			};
		}

		@Override
		public String unavailableReason() {
			return "offline";
		}

		void snapshot(String key, String runsJson) {
			open.get(key).onSubscribed();
			open.get(key).onItems(List.of(JsonParser.parseString("{\"kind\":\"snapshot\",\"projection\":{\"runs\":" + runsJson + "}}").getAsJsonObject(),
					JsonParser.parseString("{\"kind\":\"synchronized\"}").getAsJsonObject()));
		}
	}

	static final String TWO = "[{\"id\":\"r1\",\"threadId\":\"t\",\"ordinal\":1,\"status\":\"queued\",\"queuePosition\":2},"
			+ "{\"id\":\"r2\",\"threadId\":\"t\",\"ordinal\":2,\"status\":\"queued\",\"queuePosition\":1}]";

	@Test
	void watchIsRefcountedAndKnownAfterSnapshot() {
		FakeSource src = new FakeSource();
		AtomicInteger changes = new AtomicInteger();
		QueueStore store = new QueueStore(() -> src, changes::incrementAndGet);

		QueueStore.Watch w1 = store.watch("vps", "t");
		QueueStore.Watch w2 = store.watch("vps", "t");
		assertEquals(List.of("vps:t"), src.opened, "one stream for both watchers");
		assertEquals(QueueStore.Status.LOADING, store.get("vps", "t").status());
		assertEquals(-1, store.count("vps", "t"), "unknown, not zero");

		src.snapshot("vps:t", TWO);
		QueueStore.Snapshot s = store.get("vps", "t");
		assertEquals(QueueStore.Status.OK, s.status());
		assertEquals(List.of("r2", "r1"), s.runs().stream().map(QueuedRun::id).toList());
		assertEquals(2, store.count("vps", "t"));
		assertTrue(changes.get() > 0);

		w1.close();
		w1.close();
		assertTrue(src.closed.isEmpty(), "still watched by w2");
		w2.close();
		assertEquals(List.of("vps:t"), src.closed);
		assertEquals(QueueStore.Status.UNAVAILABLE, store.get("vps", "t").status());
	}

	@Test
	void pausedKeepsTheLastQueueAndFailureMakesItUnavailable() {
		FakeSource src = new FakeSource();
		QueueStore store = new QueueStore(() -> src, () -> {
		});
		store.watch("vps", "t");
		src.snapshot("vps:t", TWO);
		src.open.get("vps:t").onEnded(null);
		assertEquals(QueueStore.Status.PAUSED, store.get("vps", "t").status());
		assertEquals(2, store.count("vps", "t"));
		src.open.get("vps:t").onEnded("thread not found");
		assertEquals(QueueStore.Status.UNAVAILABLE, store.get("vps", "t").status());
		assertEquals("thread not found", store.get("vps", "t").reason());
		assertEquals(-1, store.count("vps", "t"));
	}

	@Test
	void unavailableSourceIsReportedNotEmpty() {
		QueueStore store = new QueueStore(() -> QueueSource.UNAVAILABLE, () -> {
		});
		store.watch("vps", "t");
		QueueStore.Snapshot s = store.get("vps", "t");
		assertEquals(QueueStore.Status.UNAVAILABLE, s.status());
		assertFalse(s.known());
		assertNotNull(s.reason());
	}

	@Test
	void loadWaitsForTheSnapshotThenLetsGo() {
		FakeSource src = new FakeSource();
		QueueStore store = new QueueStore(() -> src, () -> {
		});
		CompletableFuture<QueueStore.Snapshot> f = store.load("vps", "t", 5_000);
		assertFalse(f.isDone());
		src.snapshot("vps:t", TWO);
		QueueStore.Snapshot s = f.join();
		assertEquals(QueueStore.Status.OK, s.status());
		assertEquals(2, s.runs().size());
		assertEquals(List.of("vps:t"), src.closed, "the one-shot watch is released");

		src.offline.add("acmesite");
		assertEquals(QueueStore.Status.UNAVAILABLE, store.load("acmesite", "t", 5_000).join().status());
		assertEquals(QueueStore.Status.LOADING, store.load("vps", "slow", 10).join().status(), "timeout → still unknown");
	}

	@Test
	void backgroundWatchesFollowTheGivenSet() {
		FakeSource src = new FakeSource();
		QueueStore store = new QueueStore(() -> src, () -> {
		});
		store.setBackground(Set.of(QueueStore.Key.of("vps", "a"), QueueStore.Key.of("vps", "b")));
		assertEquals(Set.of("vps:a", "vps:b"), src.open.keySet());
		QueueStore.Watch screen = store.watch("vps", "a");
		store.setBackground(Set.of(QueueStore.Key.of("vps", "c")));
		assertEquals(Set.of("vps:a", "vps:c"), src.open.keySet(), "a stays open for the screen");
		screen.close();
		assertEquals(Set.of("vps:c"), src.open.keySet());
		store.clear();
		assertTrue(src.open.isEmpty());
		assertTrue(store.all().isEmpty());
	}

	@Test
	void keyParsing() {
		assertEquals(QueueStore.Key.of("vps", "thr-1"), QueueStore.Key.parse("vps:thr-1"));
		assertEquals(QueueStore.Key.of("", "thr-1"), QueueStore.Key.parse("thr-1"));
		assertEquals("vps:thr-1", QueueStore.Key.of("vps", "thr-1").toString());
	}
}
