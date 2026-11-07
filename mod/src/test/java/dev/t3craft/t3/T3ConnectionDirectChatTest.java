package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * fix/chat-direct-connect: opening a thread in direct mode must reach T3 (and never leave the
 * connection silently stuck at CONNECTING). End-to-end against {@link FakeT3Server}.
 */
class T3ConnectionDirectChatTest {
	@TempDir
	Path dir;

	private static final long EXP = System.currentTimeMillis() + 3_600_000;

	static final class Collect implements StreamRegistry.Listener {
		final BlockingQueue<List<JsonObject>> items = new LinkedBlockingQueue<>();
		volatile int subscribed;

		@Override
		public void onSubscribed() {
			subscribed++;
		}

		@Override
		public void onItems(List<JsonObject> i) {
			items.add(i);
		}

		@Override
		public void onEnded(@Nullable String failure) {
		}
	}

	static T3Connection connection(FakeT3Server fake, java.util.function.Supplier<Pairing.@Nullable Session> session) {
		return new T3Connection(new Environments.Environment("my-vps", "my-vps", fake.base()), session, new T3Connection.Listener() {
			@Override
			public void onStatus(String envId, T3Connection.Status status, String detail) {
			}

			@Override
			public void onShell(String envId, ShellState.View view) {
			}
		}, HttpClient.newHttpClient());
	}

	static JsonObject nextRequest(FakeT3Server fake, String tag) throws InterruptedException {
		for (int i = 0; i < 20; i++) {
			JsonObject o = JsonParser.parseString(fake.nextFrame()).getAsJsonObject();
			if ("Request".equals(o.get("_tag").getAsString()) && tag.equals(o.get("tag").getAsString())) {
				return o;
			}
		}
		throw new AssertionError("no " + tag + " request; got " + fake.received);
	}

	static void awaitStatus(T3Connection c, T3Connection.Status s) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (c.status() != s) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("status " + c.status() + " (" + c.detail() + "), wanted " + s);
			}
			Thread.sleep(20);
		}
	}

	@Test
	void threadOpenedWhileConnectingIsSubscribedWithTheReadPlusOperateCredential() throws Exception {
		Environments envs = new Environments(dir);
		try (FakeT3Server fake = new FakeT3Server()) {
			// default pairing of fix/pair-default-operate: one read + operate session
			envs.pair(new Environments.Environment("my-vps", "my-vps", fake.base()),
					new Pairing.Session(FakeT3Server.TOKEN, Pairing.OPERATE_SCOPES, EXP));
			assertNotNull(envs.credential("my-vps"), "the read connection must find the combined session");
			fake.script = msg -> {
				JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
				if ("Request".equals(o.get("_tag").getAsString()) && RpcFrames.SUBSCRIBE_SHELL.equals(o.get("tag").getAsString())) {
					// snapshot but no "synchronized" yet: the environment stays CONNECTING
					return List.of(T3ConnectionTest.chunk(authoritative(1, threadJson("T", H, "")).toString()));
				}
				return List.of();
			};
			T3Connection c = connection(fake, () -> envs.credential("my-vps"));
			Collect l = new Collect();
			StreamRegistry.Subscription sub = c.subscribeThread("T", l); // opened before the socket is up
			assertEquals(T3Connection.Status.CONNECTING, c.status());
			c.start();
			try {
				JsonObject req = nextRequest(fake, RpcFrames.SUBSCRIBE_THREAD);
				assertEquals("T", req.getAsJsonObject("payload").get("threadId").getAsString());
				String id = req.get("id").getAsString();
				fake.send("{\"_tag\":\"Chunk\",\"requestId\":\"" + id + "\",\"values\":[{\"kind\":\"snapshot\"}]}");
				List<JsonObject> got = l.items.poll(10, TimeUnit.SECONDS);
				assertNotNull(got, "thread items reach the view while the shell is still syncing");
				assertEquals("snapshot", got.getFirst().get("kind").getAsString());
				assertEquals(1, c.activeThreadSubscriptions());
			} finally {
				sub.close();
				c.stop();
			}
		}
	}

	@Test
	void anExceptionWhileConnectingRetriesInsteadOfStayingConnecting() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			// a token the HTTP client refuses as a header value: HttpRequest.Builder throws after
			// CONNECTING was set. Before the fix the exception vanished in the executor: CONNECTING forever.
			T3Connection c = connection(fake, () -> new Pairing.Session("bad\ntoken", Pairing.READ_SCOPES, EXP));
			c.start();
			try {
				awaitStatus(c, T3Connection.Status.OFFLINE);
				assertTrue(c.detail().startsWith("connect: IllegalArgumentException"), c.detail());
				assertFalse(c.detail().contains("bad\ntoken"));
				assertFalse(c.detail().contains("token\n"));
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void aShellThatNeverSynchronizesIsDroppedAndRetried() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = msg -> List.of(); // T3 accepts the socket but never answers the shell
			T3Connection c = connection(fake, () -> new Pairing.Session(FakeT3Server.TOKEN, Pairing.READ_SCOPES, EXP));
			c.shellSyncTimeoutMs = 300;
			c.start();
			try {
				nextRequest(fake, RpcFrames.SUBSCRIBE_SHELL);
				awaitStatus(c, T3Connection.Status.OFFLINE);
				assertTrue(c.detail().contains("did not finish loading the shell"), c.detail());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void environmentsCredentialAcceptsReadAndReadPlusOperateOnly() throws Exception {
		Environments envs = new Environments(dir);
		var env = new Environments.Environment("vps", "vps", java.net.URI.create("https://vps.example"));
		envs.save(env, new Pairing.Session("ro", Pairing.READ_SCOPES, EXP));
		assertEquals("ro", envs.credential("vps").accessToken());
		envs.pair(env, new Pairing.Session("rw", Pairing.OPERATE_SCOPES, EXP));
		assertEquals("rw", envs.credential("vps").accessToken());
		assertTrue(Files.exists(dir.resolve("credentials")));
	}
}
