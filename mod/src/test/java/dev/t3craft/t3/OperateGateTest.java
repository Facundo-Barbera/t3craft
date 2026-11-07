package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperateGateTest {
	static final long NOW = 5_000_000;

	/** Records what would go out; replies with a canned result. Never talks to T3. */
	static final class FakeTransport implements OperateTransport {
		final List<String> calls = new ArrayList<>();
		OperateResult reply = OperateResult.ok(JsonParser.parseString("{\"sequence\":7}"));

		@Override
		public CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload) {
			calls.add(rpcTag + " " + payload);
			return CompletableFuture.completedFuture(reply);
		}
	}

	/**
	 * Example action, wired only here to validate the API (the real ones live in their own
	 * branches): settle a thread via dispatchCommand.
	 */
	static OperateAction exampleSettle(OperateGate gate, String env, String threadId) {
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", "thread.settle");
		cmd.addProperty("threadId", threadId);
		return gate.operate(env).dispatch(OperateKind.THREAD_SETTLE, threadId, "Fix the build", cmd);
	}

	static OperateAction exampleReply(String env, String text) {
		JsonObject cmd = new JsonObject();
		cmd.addProperty("type", "message.dispatch");
		cmd.addProperty("text", text);
		return OperateAction.dispatch(OperateKind.MESSAGE_SEND, env, "t-1", "Fix the build", cmd);
	}

	record Setup(OperateGate gate, FakeTransport transport, OperateCredentials creds, Path audit) {
	}

	static Setup setup(Path dir, boolean withToken) throws Exception {
		OperateCredentials creds = new OperateCredentials(dir);
		if (withToken) {
			creds.save("vps", new Pairing.Session("operate-secret", Pairing.OPERATE_SCOPES, NOW + 60_000));
		}
		FakeTransport t = new FakeTransport();
		AuditLog audit = AuditLog.in(dir);
		return new Setup(new OperateGate(creds, env -> "vps".equals(env) ? t : null, audit, () -> NOW), t, creds, audit.file());
	}

	static List<JsonObject> auditLines(Path f) throws Exception {
		if (!Files.exists(f)) {
			return List.of();
		}
		return Files.readAllLines(f).stream().map(l -> JsonParser.parseString(l).getAsJsonObject()).toList();
	}

	@Test
	void withoutTokenEverythingIsReadOnlyAndNothingIsSent(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, false);
		assertEquals(OperateGate.Availability.READ_ONLY, s.gate().availability("vps"));
		assertFalse(s.gate().operate("vps").available());
		OperateResult r = s.gate().submit(exampleReply("vps", "hi"), OperateGate.Origin.UI, false).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r.outcome());
		OperateResult r2 = s.gate().submit(exampleSettle(s.gate(), "vps", "t-1"), OperateGate.Origin.COMMAND, true).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r2.outcome());
		assertTrue(s.transport().calls.isEmpty());
		assertEquals(List.of("read_only", "read_only"), auditLines(s.audit()).stream().map(o -> o.get("result").getAsString()).toList());
	}

	@Test
	void expiredTokenIsReadOnly(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, false);
		s.creds().save("vps", new Pairing.Session("old", Pairing.OPERATE_SCOPES, NOW - 1));
		assertEquals(OperateGate.Availability.EXPIRED, s.gate().availability("vps"));
		OperateResult r = s.gate().submit(exampleReply("vps", "hi"), OperateGate.Origin.UI, false).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r.outcome());
		assertTrue(r.message().contains("expired"));
	}

	@Test
	void destructiveNeedsConfirmation(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		OperateAction settle = exampleSettle(s.gate(), "vps", "t-1");
		assertTrue(settle.destructive());
		assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, s.gate().submit(settle, OperateGate.Origin.UI, false).join().outcome());
		assertTrue(s.transport().calls.isEmpty());

		OperateResult ok = s.gate().operate("vps").submit(settle, OperateGate.Origin.UI, true).join();
		assertTrue(ok.ok());
		assertEquals(7, ok.value().getAsJsonObject().get("sequence").getAsInt());
		assertEquals(1, s.transport().calls.size());
		assertTrue(s.transport().calls.get(0).startsWith("orchestration.dispatchCommand {\"type\":\"thread.settle\""));
	}

	@Test
	void nonDestructiveGoesStraightThrough(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		assertTrue(s.gate().submit(exampleReply("vps", "hi"), OperateGate.Origin.UI, false).join().ok());
		assertEquals(1, s.transport().calls.size());
	}

	@Test
	void transportProblemsBecomeResultsNotExceptions(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		s.creds().save("acmesite", new Pairing.Session("t", Pairing.OPERATE_SCOPES, NOW + 60_000));
		assertEquals(OperateResult.Outcome.OFFLINE, s.gate().submit(exampleReply("acmesite", "x"), OperateGate.Origin.UI, false).join().outcome());

		OperateGate throwing = new OperateGate(s.creds(), env -> (tag, p) -> {
			throw new IllegalStateException("boom");
		}, AuditLog.in(dir), () -> NOW);
		assertEquals(OperateResult.Outcome.FAILED, throwing.submit(exampleReply("vps", "x"), OperateGate.Origin.UI, false).join().outcome());

		OperateGate failing = new OperateGate(s.creds(), env -> (tag, p) -> CompletableFuture.failedFuture(new RuntimeException()), AuditLog.in(dir), () -> NOW);
		assertEquals(OperateResult.Outcome.FAILED, failing.submit(exampleReply("vps", "x"), OperateGate.Origin.UI, false).join().outcome());

		assertEquals(OperateResult.Outcome.REJECTED,
				s.gate().operate("acmesite").submit(exampleReply("vps", "x"), OperateGate.Origin.UI, false).join().outcome());
	}

	@Test
	void auditRecordsEveryAttemptWithoutContentOrTokens(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		s.gate().submit(exampleReply("vps", "my private message"), OperateGate.Origin.UI, false).join();
		OperateAction settle = exampleSettle(s.gate(), "vps", "t-1");
		s.gate().cancelled(settle, OperateGate.Origin.UI);
		s.gate().submit(settle, OperateGate.Origin.COMMAND, true).join();

		List<JsonObject> lines = auditLines(s.audit());
		assertEquals(3, lines.size());
		JsonObject first = lines.get(0);
		assertEquals("vps", first.get("env").getAsString());
		assertEquals("t-1", first.get("thread").getAsString());
		assertEquals("message_send", first.get("action").getAsString());
		assertFalse(first.get("destructive").getAsBoolean());
		assertEquals("ok", first.get("result").getAsString());
		assertEquals("ui", first.get("origin").getAsString());
		assertTrue(first.get("time").getAsString().endsWith("Z"));
		assertEquals("cancelled", lines.get(1).get("result").getAsString());
		assertEquals("command", lines.get(2).get("origin").getAsString());
		assertTrue(lines.get(2).get("destructive").getAsBoolean());

		String raw = Files.readString(s.audit());
		assertFalse(raw.contains("my private message"));
		assertFalse(raw.contains("operate-secret"));
		assertFalse(raw.contains("Fix the build"), "thread titles are content too");
	}

	@Test
	void destructiveClassification() {
		Set<OperateKind> destructive = Stream.of(OperateKind.values()).filter(k -> k.destructive).collect(Collectors.toSet());
		assertEquals(EnumSet.of(OperateKind.RUN_INTERRUPT, OperateKind.THREAD_STOP, OperateKind.REQUEST_DENY,
				OperateKind.THREAD_SETTLE, OperateKind.QUEUE_CANCEL, OperateKind.THREAD_ARCHIVE), destructive);
		assertFalse(OperateKind.isDestructive("message_send"));
		assertTrue(OperateKind.isDestructive("thread_stop"));
		assertTrue(OperateKind.isDestructive("something_new"), "unknown kinds are destructive");
		assertEquals("t3craft.operate.kind.thread_stop", OperateKind.THREAD_STOP.langKey());
	}

	@Test
	void actionValidatesAndHidesPayload() {
		JsonObject p = new JsonObject();
		p.addProperty("text", "secret text");
		var a = OperateAction.dispatch(OperateKind.MESSAGE_SEND, "vps", "t", null, p);
		assertFalse(a.toString().contains("secret"));
		p.addProperty("text", "changed");
		assertEquals("secret text", a.payload().get("text").getAsString(), "defensive copy");
		assertThrows(IllegalArgumentException.class, () -> new OperateAction(OperateKind.MESSAGE_SEND, "vps", null, null, "filesystem.write", new JsonObject()));
		assertThrows(IllegalArgumentException.class, () -> new OperateAction(null, "vps", null, null, OperateAction.DISPATCH_COMMAND, new JsonObject()));
	}
}
