package dev.t3craft.client.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** ThreadFeed over a real StreamRegistry (no socket): lifecycle, sharing and state. */
class ThreadFeedTest {
	static final class FakeEnv implements ChatSource.Env {
		final StreamRegistry registry = new StreamRegistry(RpcFrames::subscribeThread);
		T3Connection.Status status = T3Connection.Status.OK;
		CompletableFuture<JsonObject> history = new CompletableFuture<>();

		@Override
		public String id() {
			return "vps";
		}

		@Override
		public String label() {
			return "VPS";
		}

		@Override
		public T3Connection.Status status() {
			return status;
		}

		@Override
		public String statusDetail() {
			return "offline (retry in 2 s)";
		}

		@Override
		public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
			return registry.subscribe(threadId, listener);
		}

		@Override
		public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
			return history;
		}

		@Override
		public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
			return CompletableFuture.completedFuture(null);
		}
	}

	static final class Wire implements StreamRegistry.Sender {
		final java.util.List<JsonObject> sent = new java.util.ArrayList<>();

		@Override
		public boolean send(String frame) {
			sent.add(JsonParser.parseString(frame).getAsJsonObject());
			return true;
		}

		String lastRequestId() {
			JsonObject o = sent.getLast();
			return o.has("id") ? o.get("id").getAsString() : o.get("requestId").getAsString();
		}
	}

	static final String SNAPSHOT = ("{'kind':'snapshot','snapshotSequence':10,'historyCursor':'c1','hasMoreHistory':true,"
			+ "'projection':{'thread':{'id':'T','title':'Fix it'},'runs':[],'runtimeRequests':[],'nodes':[],'subagents':[],'turnItems':[],"
			+ "'visibleTurnItems':[{'position':0,'visibility':'local','sourceThreadId':'T','sourceItemId':'x','item':{'id':'m1','threadId':'T',"
			+ "'type':'assistant_message','ordinal':1,'status':'completed','title':null,'updatedAt':'2026-11-07T10:00:00Z','messageId':'m1',"
			+ "'text':'hello','streaming':false}}]}}").replace('\'', '"');


	@Test
	void sharesOneSubscriptionAndReleasesOnClose() {
		FakeEnv env = new FakeEnv();
		Wire w = new Wire();
		env.registry.onOpen(w);
		AtomicInteger changes = new AtomicInteger();
		ThreadFeed a = new ThreadFeed(env, "T", changes::incrementAndGet);
		ThreadFeed b = new ThreadFeed(env, "T", () -> {
		});
		a.start();
		a.start(); // idempotent
		assertEquals(ThreadFeed.Phase.CONNECTING, a.state().phase(), "no snapshot yet");
		env.registry.onChunk(w.lastRequestId(), List.of(JsonParser.parseString(SNAPSHOT).getAsJsonObject()));
		assertTrue(a.transcript().hasSnapshot());
		assertEquals(ThreadFeed.Phase.LIVE, a.state().phase());
		assertTrue(changes.get() > 0);

		b.start();
		assertEquals(2, env.registry.refCount("T"), "second view joins the same subscription");
		assertEquals(1, w.sent.size());
		assertTrue(b.transcript().hasSnapshot(), "late view replays the snapshot");
		assertEquals("Fix it", b.transcript().view().title());

		a.close();
		assertFalse(a.subscribed());
		assertEquals(1, env.registry.refCount("T"));
		b.close();
		assertEquals(0, env.registry.activeThreads());
		assertEquals("Interrupt", w.sent.getLast().get("_tag").getAsString());
		b.start();
		assertEquals(0, env.registry.activeThreads(), "a closed feed never resubscribes");
	}

	@Test
	void stateFollowsTheSharedConnection() {
		FakeEnv env = new FakeEnv();
		env.status = T3Connection.Status.OFFLINE;
		ThreadFeed f = new ThreadFeed(env, "T", () -> {
		});
		f.start();
		assertEquals(ThreadFeed.Phase.OFFLINE, f.state().phase());
		assertEquals("offline (retry in 2 s)", f.state().detail());
		env.status = T3Connection.Status.AUTH_FAILED;
		assertEquals(ThreadFeed.Phase.FAILED, f.state().phase());
		env.status = T3Connection.Status.OK;
		Wire w = new Wire();
		env.registry.onOpen(w);
		env.registry.onExit(w.lastRequestId(), "Fail OrchestrationThreadNotFoundError", null);
		assertEquals(ThreadFeed.Phase.FAILED, f.state().phase());
		assertEquals("thread not found on VPS", f.state().detail());
		f.close();
	}

	@Test
	void loadOlderUsesTheConnectionHistory() {
		FakeEnv env = new FakeEnv();
		Wire w = new Wire();
		env.registry.onOpen(w);
		ThreadFeed f = new ThreadFeed(env, "T", () -> {
		});
		f.start();
		env.registry.onChunk(w.lastRequestId(), List.of(JsonParser.parseString(SNAPSHOT).getAsJsonObject()));
		f.loadOlder();
		assertTrue(f.state().loadingOlder());
		f.loadOlder(); // no second request while one is in flight
		env.history.completeExceptionally(new IllegalStateException("history: HTTP 500"));
		assertFalse(f.state().loadingOlder());
		assertTrue(f.state().olderError().contains("HTTP 500"));
		f.close();
	}
}
