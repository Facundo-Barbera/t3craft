package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static dev.t3craft.t3.T3ConnectionTest.await;
import static dev.t3craft.t3.T3ConnectionTest.chunk;
import static dev.t3craft.t3.T3ConnectionTest.connect;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** {@code searchThreads} over the connection's own socket, against {@link FakeT3Server}. */
class T3ConnectionSearchTest {
	/** Shell sync on request "1"; {@code onSearch} answers searchThreads requests. */
	static Function<String, List<String>> script(Function<JsonObject, List<String>> onSearch) {
		return msg -> {
			JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
			if (!"Request".equals(o.get("_tag").getAsString())) {
				return List.of();
			}
			if (RpcFrames.SUBSCRIBE_SHELL.equals(o.get("tag").getAsString())) {
				return List.of(chunk(authoritative(1, threadJson("a", H, "")).toString()), chunk("{\"kind\":\"synchronized\"}"));
			}
			return onSearch.apply(o);
		};
	}

	static String success(String id, String valueJson) {
		return "{\"_tag\":\"Exit\",\"requestId\":\"" + id + "\",\"exit\":{\"_tag\":\"Success\",\"value\":" + valueJson + "}}";
	}

	static T3Connection synced(FakeT3Server fake) throws InterruptedException {
		BlockingQueue<T3ConnectionTest.Event> events = new LinkedBlockingQueue<>();
		T3Connection c = connect(fake, FakeT3Server.TOKEN, events);
		await(events, e -> e.status() == T3Connection.Status.OK);
		return c;
	}

	static JsonObject awaitFrame(FakeT3Server fake, String tag) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			JsonObject o = JsonParser.parseString(fake.nextFrame()).getAsJsonObject();
			String t = o.get("_tag").getAsString();
			if (tag.equals(t) || ("Request".equals(t) && o.has("tag") && tag.equals(o.get("tag").getAsString()))) {
				return o;
			}
		}
		throw new AssertionError("no " + tag + " frame");
	}

	@Test
	void searchSendsReadOnlyRequestAndDecodesTheExitValue() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = script(req -> List.of(success(req.get("id").getAsString(),
					"{\"matches\":[{\"threadId\":\"a\",\"projectId\":\"project-1\",\"source\":\"user\",\"snippet\":\"hello\",\"messageCreatedAt\":null,\"quality\":1}]}")));
			T3Connection c = synced(fake);
			try {
				var result = c.searchThreads("  hello  ", 99, 5_000).get(10, TimeUnit.SECONDS);
				assertEquals(List.of("a"), result.matches().stream().map(ThreadSearch.Match::threadId).toList());
				JsonObject req = awaitFrame(fake, RpcFrames.SEARCH_THREADS);
				assertNotEquals("1", req.get("id").getAsString(), "must not reuse the shell request id");
				JsonObject p = req.getAsJsonObject("payload");
				assertEquals("hello", p.get("query").getAsString());
				assertEquals(50, p.get("limit").getAsInt(), "limit clamped to T3's maximum");
				assertEquals("my-vps", p.get("environmentLabel").getAsString());
				assertEquals(T3Connection.Status.OK, c.status(), "the search does not disturb the shell subscription");
				// only read-only methods were requested
				for (String f : fake.received) {
					JsonObject o = JsonParser.parseString(f).getAsJsonObject();
					if ("Request".equals(o.get("_tag").getAsString())) {
						assertTrue(List.of(RpcFrames.SUBSCRIBE_SHELL, RpcFrames.SEARCH_THREADS).contains(o.get("tag").getAsString()));
					}
				}
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void failureExitIsSanitizedAndKeepsTheConnection() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = script(req -> List.of("{\"_tag\":\"Exit\",\"requestId\":\"" + req.get("id").getAsString()
					+ "\",\"exit\":{\"_tag\":\"Failure\",\"cause\":[{\"_tag\":\"Fail\",\"error\":{\"_tag\":\"OrchestrationSearchThreadsError\",\"message\":\"db busy Bearer leaked-token\"}}]}}"));
			T3Connection c = synced(fake);
			try {
				var ex = assertThrows(ExecutionException.class, () -> c.searchThreads("hello", 10, 5_000).get(10, TimeUnit.SECONDS));
				assertInstanceOf(ThreadSearch.SearchException.class, ex.getCause());
				assertTrue(ex.getCause().getMessage().contains("OrchestrationSearchThreadsError"));
				assertFalse(ex.getCause().getMessage().contains("leaked-token"));
				assertEquals(T3Connection.Status.OK, c.status());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void timeoutFailsAndInterruptsTheRequest() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = script(req -> List.of()); // never answers
			T3Connection c = synced(fake);
			try {
				var f = c.searchThreads("slow query", 10, 300);
				var ex = assertThrows(ExecutionException.class, () -> f.get(10, TimeUnit.SECONDS));
				assertTrue(ex.getCause().getMessage().contains("timed out"));
				String id = awaitFrame(fake, RpcFrames.SEARCH_THREADS).get("id").getAsString();
				assertEquals(id, awaitFrame(fake, "Interrupt").get("requestId").getAsString());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void cancellingInterruptsAndALateAnswerIsIgnored() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = script(req -> List.of());
			T3Connection c = synced(fake);
			try {
				var f = c.searchThreads("old query", 10, 10_000);
				String id = awaitFrame(fake, RpcFrames.SEARCH_THREADS).get("id").getAsString();
				assertTrue(f.cancel(true));
				assertEquals(id, awaitFrame(fake, "Interrupt").get("requestId").getAsString());
				fake.send(success(id, "{\"matches\":[]}")); // late: must not break anything
				var next = c.searchThreads("new query", 10, 5_000);
				String id2 = awaitFrame(fake, RpcFrames.SEARCH_THREADS).get("id").getAsString();
				assertNotEquals(id, id2);
				fake.send(success(id2, "{\"matches\":[{\"threadId\":\"a\"}]}"));
				assertEquals(1, next.get(10, TimeUnit.SECONDS).matches().size());
				assertEquals(T3Connection.Status.OK, c.status());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void notConnectedOrTooShortFailsFast() throws Exception {
		var env = new Environments.Environment("vps", "VPS", java.net.URI.create("http://127.0.0.1:1"));
		T3Connection c = new T3Connection(env, () -> null, new T3Connection.Listener() {
			@Override
			public void onStatus(String envId, T3Connection.Status s, String d) {
			}

			@Override
			public void onShell(String envId, ShellState.View v) {
			}
		}, java.net.http.HttpClient.newHttpClient());
		try {
			var ex = assertThrows(ExecutionException.class, () -> c.searchThreads("hello", 10, 1_000).get(5, TimeUnit.SECONDS));
			assertTrue(ex.getCause().getMessage().startsWith("not connected"));
			var shortEx = assertThrows(ExecutionException.class, () -> c.searchThreads(" a ", 10, 1_000).get(5, TimeUnit.SECONDS));
			assertTrue(shortEx.getCause().getMessage().contains("too short"));
		} finally {
			c.stop();
		}
	}
}
