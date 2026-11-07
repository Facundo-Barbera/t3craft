package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.AuditLog;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateCredentials;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.OperateTransport;
import dev.t3craft.t3.Pairing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Queue writes through a fake {@link OperateGate} transport. Never talks to T3. */
class QueueOpsTest {
	static final long NOW = 5_000_000;

	static final class FakeTransport implements OperateTransport {
		final List<String> tags = new ArrayList<>();
		final List<JsonObject> payloads = new ArrayList<>();

		@Override
		public CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload) {
			tags.add(rpcTag);
			payloads.add(payload);
			return CompletableFuture.completedFuture(OperateResult.ok(JsonParser.parseString("{\"sequence\":3}")));
		}
	}

	record Setup(QueueOps ops, FakeTransport transport, Path audit) {
	}

	static Setup setup(Path dir, boolean withToken) throws Exception {
		OperateCredentials creds = new OperateCredentials(dir);
		if (withToken) {
			creds.save("vps", new Pairing.Session("operate-secret", Pairing.OPERATE_SCOPES, NOW + 60_000));
		}
		FakeTransport t = new FakeTransport();
		AuditLog audit = AuditLog.in(dir);
		AtomicInteger n = new AtomicInteger();
		OperateGate gate = new OperateGate(creds, env -> "vps".equals(env) ? t : null, audit, () -> NOW);
		return new Setup(new QueueOps(gate, new QueueActions(() -> "cmd-" + n.incrementAndGet())), t, audit.file());
	}

	static List<QueuedRun> queue() {
		return List.of(
				new QueuedRun("vps", "thr-1", "run-a", "queued", 2, 1L, false, null, "m2", false, true, "first"),
				new QueuedRun("vps", "thr-1", "run-b", "queued", 3, 2L, false, null, "m3", false, true, "second"),
				new QueuedRun("vps", "thr-1", "run-c", "queued", 4, 3L, false, null, "m4", false, true, "third"));
	}

	static List<JsonObject> audit(Path f) throws Exception {
		return Files.exists(f) ? Files.readAllLines(f).stream().map(l -> JsonParser.parseString(l).getAsJsonObject()).toList() : List.of();
	}

	@Test
	void cancelNeedsConfirmationThenSendsTheContractCommand(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		QueuedRun b = queue().get(1);
		OperateAction a = s.ops().cancelAction(b, "Fix the build");
		assertEquals(OperateKind.QUEUE_CANCEL, a.kind());
		assertTrue(a.destructive());

		OperateResult unconfirmed = s.ops().cancel(b, "Fix the build", OperateGate.Origin.COMMAND, false).join();
		assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, unconfirmed.outcome());
		assertTrue(s.transport().tags.isEmpty(), "nothing sent without confirmation");

		OperateResult ok = s.ops().cancel(b, "Fix the build", OperateGate.Origin.COMMAND, true).join();
		assertTrue(ok.ok());
		assertEquals(List.of(OperateAction.DISPATCH_COMMAND), s.transport().tags);
		JsonObject cmd = s.transport().payloads.get(0);
		assertEquals("queued-run.cancel", cmd.get("type").getAsString());
		assertEquals("thr-1", cmd.get("threadId").getAsString());
		assertEquals("run-b", cmd.get("runId").getAsString());
		assertTrue(cmd.get("commandId").getAsString().startsWith("cmd-"));
		assertEquals(4, cmd.size(), "exactly {type, commandId, threadId, runId}");
	}

	@Test
	void reorderIsNotDestructiveAndSendsBeforeRunId(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		List<QueuedRun> q = queue();
		assertTrue(s.ops().move(q, q.get(2), QueueOps.Direction.UP, null, OperateGate.Origin.UI).join().ok());
		assertTrue(s.ops().move(q, q.get(1), QueueOps.Direction.DOWN, null, OperateGate.Origin.UI).join().ok());
		assertEquals(2, s.transport().payloads.size());

		JsonObject up = s.transport().payloads.get(0);
		assertEquals("queued-run.reorder", up.get("type").getAsString());
		assertEquals("run-c", up.get("runId").getAsString());
		assertEquals("run-b", up.get("beforeRunId").getAsString());

		JsonObject down = s.transport().payloads.get(1);
		assertEquals("run-b", down.get("runId").getAsString());
		assertTrue(down.has("beforeRunId"), "beforeRunId is required (NullOr), never omitted");
		assertTrue(down.get("beforeRunId").isJsonNull(), "to the end");
		assertTrue(down.toString().contains("\"beforeRunId\":null"), "null survives serialization");
		assertNotEquals(up.get("commandId").getAsString(), down.get("commandId").getAsString(), "fresh command id per action");

		assertFalse(s.ops().moveAction(q, q.get(1), QueueOps.Direction.UP, null).destructive());
	}

	@Test
	void impossibleMovesAreRejectedLocally(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		List<QueuedRun> q = queue();
		assertEquals(OperateResult.Outcome.REJECTED, s.ops().move(q, q.get(0), QueueOps.Direction.UP, null, OperateGate.Origin.UI).join().outcome());
		assertEquals(OperateResult.Outcome.REJECTED, s.ops().move(q, q.get(2), QueueOps.Direction.DOWN, null, OperateGate.Origin.UI).join().outcome());
		assertTrue(s.transport().tags.isEmpty());
		assertNull(s.ops().moveAction(q, q.get(0), QueueOps.Direction.UP, null));
	}

	@Test
	void withoutOperateTokenEverythingIsReadOnly(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, false);
		List<QueuedRun> q = queue();
		assertEquals(OperateResult.Outcome.READ_ONLY, s.ops().cancel(q.get(0), null, OperateGate.Origin.UI, true).join().outcome());
		assertEquals(OperateResult.Outcome.READ_ONLY, s.ops().move(q, q.get(1), QueueOps.Direction.UP, null, OperateGate.Origin.UI).join().outcome());
		assertTrue(s.transport().tags.isEmpty());
		assertEquals(List.of("read_only", "read_only"), audit(s.audit()).stream().map(o -> o.get("result").getAsString()).toList());
	}

	@Test
	void everyAttemptIsAuditedWithoutContent(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		List<QueuedRun> q = queue();
		s.ops().cancel(q.get(0), "Secret title", OperateGate.Origin.COMMAND, false).join();
		s.ops().gate().cancelled(s.ops().cancelAction(q.get(0), "Secret title"), OperateGate.Origin.UI); // player said no
		s.ops().cancel(q.get(0), "Secret title", OperateGate.Origin.UI, true).join();
		s.ops().move(q, q.get(1), QueueOps.Direction.DOWN, "Secret title", OperateGate.Origin.UI).join();

		List<JsonObject> lines = audit(s.audit());
		assertEquals(List.of("needs_confirmation", "cancelled", "ok", "ok"), lines.stream().map(o -> o.get("result").getAsString()).toList());
		assertEquals(List.of("queue_cancel", "queue_cancel", "queue_cancel", "queue_reorder"),
				lines.stream().map(o -> o.get("action").getAsString()).toList());
		assertEquals(List.of(true, true, true, false), lines.stream().map(o -> o.get("destructive").getAsBoolean()).toList());
		assertEquals("command", lines.get(0).get("origin").getAsString());
		assertEquals("thr-1", lines.get(3).get("thread").getAsString());
		String raw = Files.readString(s.audit());
		assertFalse(raw.contains("Secret title"));
		assertFalse(raw.contains("first"), "no message previews");
		assertFalse(raw.contains("operate-secret"));
	}

	@Test
	void findByIdOrUniquePrefix() {
		List<QueuedRun> q = List.of(
				new QueuedRun("vps", "t", "run-abc", "queued", 1, 1L, false, null, null, false, true, null),
				new QueuedRun("vps", "t", "run-abd", "queued", 2, 2L, false, null, null, false, true, null),
				new QueuedRun("vps", "t", "zzz", "queued", 3, 3L, false, null, null, false, true, null));
		assertEquals("run-abc", QueueOps.find(q, "run-abc").id());
		assertEquals("zzz", QueueOps.find(q, "z").id());
		assertNull(QueueOps.find(q, "run-ab"), "ambiguous");
		assertNull(QueueOps.find(q, "nope"));
		assertNull(QueueOps.find(q, ""));
	}
}
