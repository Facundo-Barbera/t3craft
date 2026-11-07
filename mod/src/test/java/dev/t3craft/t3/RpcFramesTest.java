package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class RpcFramesTest {
	/** Regression (bfaebf1): without the marker T3 never sends `synchronized`. */
	@Test
	void subscribeShellRequestsCompletionMarker() {
		var o = JsonParser.parseString(RpcFrames.subscribeShell("1")).getAsJsonObject();
		assertEquals("Request", o.get("_tag").getAsString());
		assertEquals("1", o.get("id").getAsString());
		assertEquals("orchestration.subscribeShell", o.get("tag").getAsString());
		assertTrue(o.getAsJsonObject("payload").get("requestCompletionMarker").getAsBoolean());
		assertTrue(o.getAsJsonArray("headers").isEmpty());
	}

	@Test
	void ackAndPing() {
		assertEquals("{\"_tag\":\"Ack\",\"requestId\":\"7\"}", RpcFrames.ack("7"));
		assertEquals("{\"_tag\":\"Ping\"}", RpcFrames.ping());
	}

	@Test
	void parsesServerFrames() {
		var chunk = (RpcFrames.Chunk) RpcFrames.parse("{\"_tag\":\"Chunk\",\"requestId\":\"1\",\"values\":[{\"kind\":\"synchronized\"},5]}");
		assertEquals("1", chunk.requestId());
		assertEquals(1, chunk.values().size());
		var ok = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"1\",\"exit\":{\"_tag\":\"Success\",\"value\":null}}");
		assertNull(ok.failure());
		var fail = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"1\",\"exit\":{\"_tag\":\"Failure\",\"cause\":[{\"_tag\":\"Fail\",\"error\":{\"_tag\":\"EnvironmentAuthorizationError\",\"message\":\"scope\"}}]}}");
		assertEquals("Fail EnvironmentAuthorizationError: scope", fail.failure());
		assertInstanceOf(RpcFrames.Pong.class, RpcFrames.parse("{\"_tag\":\"Pong\"}"));
		assertInstanceOf(RpcFrames.Defect.class, RpcFrames.parse("{\"_tag\":\"Defect\",\"defect\":\"x\"}"));
		assertInstanceOf(RpcFrames.Defect.class, RpcFrames.parse("garbage"));
		assertInstanceOf(RpcFrames.Other.class, RpcFrames.parse("{\"_tag\":\"Novel\"}"));
	}

	@Test
	void exitCarriesTheSuccessValueOfOneShotRpcs() {
		var ok = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"7\",\"exit\":{\"_tag\":\"Success\",\"value\":{\"matches\":[]}}}");
		assertEquals("7", ok.requestId());
		assertNull(ok.failure());
		assertTrue(ok.value().getAsJsonObject().has("matches"));
		var none = (RpcFrames.Exit) RpcFrames.parse("{\"_tag\":\"Exit\",\"requestId\":\"1\",\"exit\":{\"_tag\":\"Success\",\"value\":null}}");
		assertNull(none.value());
		assertNull(new RpcFrames.Exit("1", null).value());
	}

	@Test
	void searchThreadsFraming() {
		var o = com.google.gson.JsonParser.parseString(RpcFrames.searchThreads("5", "  " + "q".repeat(300) + " ", 0, "  ")).getAsJsonObject();
		assertEquals("Request", o.get("_tag").getAsString());
		assertEquals("orchestration.searchThreads", o.get("tag").getAsString());
		var p = o.getAsJsonObject("payload");
		assertEquals(RpcFrames.SEARCH_MAX_LENGTH, p.get("query").getAsString().length());
		assertEquals(1, p.get("limit").getAsInt());
		assertFalse(p.has("environmentLabel"), "blank label is omitted");
		assertEquals("ab", RpcFrames.searchQuery("  ab \n"));
		assertEquals("", RpcFrames.searchQuery(null));
	}
}
