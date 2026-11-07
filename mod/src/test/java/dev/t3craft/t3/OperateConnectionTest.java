package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Against {@link FakeT3Server} on 127.0.0.1 only: never a real T3. */
class OperateConnectionTest {
	static OperateConnection connect(FakeT3Server fake, String token) {
		var env = new Environments.Environment("vps", "my-vps", fake.base());
		var s = token == null ? null : new Pairing.Session(token, Pairing.OPERATE_SCOPES, System.currentTimeMillis() + 60_000);
		return new OperateConnection(env, () -> s, HttpClient.newHttpClient());
	}

	static JsonObject cmd() {
		JsonObject c = new JsonObject();
		c.addProperty("type", "example.noop");
		return c;
	}

	@Test
	void sendsRequestWithOperateSessionAndCompletesOnExit() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			fake.script = msg -> {
				JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
				if (!"Request".equals(o.get("_tag").getAsString())) {
					return List.of();
				}
				String id = o.get("id").getAsString();
				if ("example.fail".equals(o.getAsJsonObject("payload").get("type").getAsString())) {
					return List.of("{\"_tag\":\"Exit\",\"requestId\":\"" + id + "\",\"exit\":{\"_tag\":\"Failure\",\"cause\":[{\"_tag\":\"Fail\",\"error\":{\"_tag\":\"OrchestrationCommandError\",\"message\":\"unknown thread\"}}]}}");
				}
				return List.of("{\"_tag\":\"Exit\",\"requestId\":\"" + id + "\",\"exit\":{\"_tag\":\"Success\",\"value\":{\"sequence\":42}}}");
			};
			OperateConnection c = connect(fake, FakeT3Server.TOKEN);
			try {
				OperateResult r = c.call(OperateAction.DISPATCH_COMMAND, cmd()).get(10, TimeUnit.SECONDS);
				assertTrue(r.ok(), r.message());
				assertEquals(42, r.value().getAsJsonObject().get("sequence").getAsInt());
				JsonObject sent = JsonParser.parseString(fake.received.get(0)).getAsJsonObject();
				assertEquals("orchestration.dispatchCommand", sent.get("tag").getAsString());
				assertEquals("example.noop", sent.getAsJsonObject("payload").get("type").getAsString());

				JsonObject fail = new JsonObject();
				fail.addProperty("type", "example.fail");
				OperateResult r2 = c.call(OperateAction.DISPATCH_COMMAND, fail).get(10, TimeUnit.SECONDS);
				assertEquals(OperateResult.Outcome.REJECTED, r2.outcome());
				assertTrue(r2.message().contains("unknown thread"));
				assertEquals(1, fake.requests.stream().filter(x -> x.equals("GET /ws")).count(), "socket reused: " + fake.requests);
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void rejectedSessionIsAuthFailed() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			OperateConnection c = connect(fake, "wrong");
			try {
				assertEquals(OperateResult.Outcome.AUTH_FAILED, c.call(OperateAction.DISPATCH_COMMAND, cmd()).get(10, TimeUnit.SECONDS).outcome());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void noSessionIsReadOnlyWithoutNetwork() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			OperateConnection c = connect(fake, null);
			try {
				assertEquals(OperateResult.Outcome.READ_ONLY, c.call(OperateAction.DISPATCH_COMMAND, cmd()).get(10, TimeUnit.SECONDS).outcome());
				assertTrue(fake.requests.isEmpty());
			} finally {
				c.stop();
			}
		}
	}

	@Test
	void stoppedConnectionAnswersOffline() throws Exception {
		try (FakeT3Server fake = new FakeT3Server()) {
			OperateConnection c = connect(fake, FakeT3Server.TOKEN);
			c.stop();
			assertEquals(OperateResult.Outcome.OFFLINE, c.call(OperateAction.DISPATCH_COMMAND, cmd()).get(5, TimeUnit.SECONDS).outcome());
		}
	}
}
