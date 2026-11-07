package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** End-to-end against {@link FakeT3Server} on 127.0.0.1 (ephemeral port, closed after each test). */
class T3ConnectionTest {
	record Event(String kind, T3Connection.Status status, ShellState.View view) {
	}

	static T3Connection connect(FakeT3Server fake, String token, BlockingQueue<Event> events) {
		var env = new Environments.Environment("vps", "my-vps", fake.base());
		var session = new Pairing.Session(token, Set.of("orchestration:read"), System.currentTimeMillis() + 3_600_000);
		T3Connection c = new T3Connection(env, () -> session, new T3Connection.Listener() {
			@Override
			public void onStatus(String envId, T3Connection.Status s, String detail) {
				events.add(new Event("status", s, null));
			}

			@Override
			public void onShell(String envId, ShellState.View v) {
				events.add(new Event("shell", null, v));
			}
		}, HttpClient.newHttpClient());
		c.start();
		return c;
	}

	static String chunk(String json) {
		return "{\"_tag\":\"Chunk\",\"requestId\":\"1\",\"values\":[" + json + "]}";
	}

	static Event await(BlockingQueue<Event> q, java.util.function.Predicate<Event> p) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			Event e = q.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			if (e != null && p.test(e)) {
				return e;
			}
		}
		throw new AssertionError("event not seen within 10 s");
	}

	@Test
	void syncsWithMarkerAcksEveryChunkAndKeepsThreadsOnEnrichment() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = msg -> {
				var o = JsonParser.parseString(msg).getAsJsonObject();
				if ("Request".equals(o.get("_tag").getAsString())) {
					return List.of(chunk(authoritative(10, threadJson("a", H, "{\"status\":\"running\"}"), threadJson("b", H, "")).toString()));
				}
				return List.of();
			};
			BlockingQueue<Event> events = new LinkedBlockingQueue<>();
			T3Connection c = connect(fake, FakeT3Server.TOKEN, events);
			try {
				var req = JsonParser.parseString(fake.nextFrame()).getAsJsonObject();
				assertEquals("orchestration.subscribeShell", req.get("tag").getAsString());
				assertTrue(req.getAsJsonObject("payload").get("requestCompletionMarker").getAsBoolean());
				assertEquals("2", fake.lastWsQuery.get("orchestrationProtocol"));
				assertEquals(FakeT3Server.TICKET, fake.lastWsQuery.get("wsTicket"));
				assertEquals(RpcFrames.ack("1"), fake.nextFrame()); // ack for the snapshot chunk

				fake.send(chunk(enrichment(10).toString()));
				assertEquals(RpcFrames.ack("1"), fake.nextFrame());
				fake.send(chunk("{\"kind\":\"synchronized\"}"));
				assertEquals(RpcFrames.ack("1"), fake.nextFrame());

				await(events, e -> e.status() == T3Connection.Status.OK);
				assertEquals(T3Connection.Status.OK, c.status());
				Event shell = await(events, e -> "shell".equals(e.kind()) && e.view().synced());
				assertEquals(2, shell.view().threads().size(), "enrichment snapshot must not drop threads");
				assertEquals("Synthetic project", shell.view().projectTitle("project-1"));
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void rejectedTokenIsAuthFailedWithoutLeakingIt() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			BlockingQueue<Event> events = new LinkedBlockingQueue<>();
			T3Connection c = connect(fake, "wrong-token", events);
			try {
				await(events, e -> e.status() == T3Connection.Status.AUTH_FAILED);
				assertFalse(c.detail().contains("wrong-token"));
				assertEquals(List.of("POST /api/auth/websocket-ticket/rpc"), fake.requests);
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void serverErrorRetriesOffline() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.ticketStatus = 503;
			BlockingQueue<Event> events = new LinkedBlockingQueue<>();
			T3Connection c = connect(fake, FakeT3Server.TOKEN, events);
			try {
				await(events, e -> e.status() == T3Connection.Status.OFFLINE);
				assertTrue(c.detail().contains("HTTP 503"));
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void socketUriEncodesTicketAndProtocol() {
		assertEquals("wss://h.ts.net/ws?orchestrationProtocol=2&wsTicket=a%2Bb%2F%3D",
				T3Connection.socketUri(java.net.URI.create("https://h.ts.net"), "a+b/=").toString());
		assertEquals("ws://h:3773/ws?orchestrationProtocol=2&wsTicket=x",
				T3Connection.socketUri(java.net.URI.create("http://h:3773"), "x").toString());
		assertEquals("wss://h/ws?orchestrationProtocol=2&wsTicket=<redacted>",
				T3Connection.sanitize("wss://h/ws?orchestrationProtocol=2&wsTicket=SECRET"));
	}

	@Test
	void pairingExchangeAsksOnlyReadAndAcceptsBearer() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			var target = Pairing.parse(fake.base() + "/pair#token=one-time", null);
			var session = new PairingClient(HttpClient.newHttpClient()).exchange(target, "T3Craft (Minecraft)", "linux").get(10, TimeUnit.SECONDS);
			assertEquals(Set.of("orchestration:read"), session.scopes());
			assertTrue(fake.lastTokenForm.contains("subject_token=one-time"));
			assertTrue(fake.lastTokenForm.contains("scope=orchestration:read"));
			assertFalse(fake.lastTokenForm.contains("operate"));

			fake.tokenResponse = "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":1,\"scope\":\"orchestration:read orchestration:operate\"}";
			var ex = assertThrows(java.util.concurrent.ExecutionException.class,
					() -> new PairingClient(HttpClient.newHttpClient()).exchange(target, "T3Craft", "linux").get(10, TimeUnit.SECONDS));
			assertInstanceOf(Pairing.PairingException.class, ex.getCause());
		}
	}
}
