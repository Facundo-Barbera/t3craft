package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Item 22 over the fake gate of {@link OperateGateTest}: never talks to a real T3. */
class ThreadOpsTest {
	static final Instant NOW = Instant.parse("2026-11-07T15:30:00Z");
	static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires"); // UTC-3: 12:30 local

	static ThreadOps ops() {
		AtomicInteger n = new AtomicInteger();
		return new ThreadOps(() -> NOW, ZONE, () -> "cmd-" + n.incrementAndGet());
	}

	static final ThreadOps.Target T = new ThreadOps.Target("vps", "t-1", "Fix the build");

	static JsonObject sent(OperateGateTest.FakeTransport t, int i) {
		String call = t.calls.get(i);
		assertTrue(call.startsWith(OperateAction.DISPATCH_COMMAND + " "), call);
		return JsonParser.parseString(call.substring(call.indexOf(' ') + 1)).getAsJsonObject();
	}

	@Test
	void payloadsFollowTheOrchestrationV2Contract() {
		ThreadOps o = ops();
		assertEquals(JsonParser.parseString("{\"type\":\"thread.settle\",\"commandId\":\"cmd-1\",\"threadId\":\"t-1\"}"), o.settle(T).payload());
		assertEquals(JsonParser.parseString("{\"type\":\"thread.snooze\",\"commandId\":\"cmd-2\",\"threadId\":\"t-1\",\"snoozedUntil\":\"2026-11-07T16:30:00.000Z\"}"),
				o.snooze(T, ThreadOps.Snooze.HOUR_1).payload());
		assertEquals(JsonParser.parseString("{\"type\":\"thread.pin\",\"commandId\":\"cmd-3\",\"threadId\":\"t-1\"}"), o.pin(T).payload());
		assertEquals(JsonParser.parseString("{\"type\":\"thread.unpin\",\"commandId\":\"cmd-4\",\"threadId\":\"t-1\"}"), o.unpin(T).payload());
		assertEquals(JsonParser.parseString("{\"type\":\"thread.visit\",\"commandId\":\"cmd-5\",\"threadId\":\"t-1\",\"visitedAt\":\"2026-11-07T15:30:00.000Z\"}"),
				o.markRead(T).payload());

		OperateAction a = o.settle(T);
		assertEquals(OperateKind.THREAD_SETTLE, a.kind());
		assertEquals("vps", a.envId());
		assertEquals("t-1", a.threadId());
		assertEquals("Fix the build", a.threadTitle());
		assertEquals(OperateAction.DISPATCH_COMMAND, a.rpcTag());
		assertEquals(OperateKind.THREAD_SNOOZE, o.snooze(T, ThreadOps.Snooze.TOMORROW).kind());
		assertEquals(OperateKind.THREAD_PIN, o.pin(T).kind());
		assertEquals(OperateKind.THREAD_UNPIN, o.unpin(T).kind());
		assertEquals(OperateKind.THREAD_MARK_READ, o.markRead(T).kind());
	}

	@Test
	void commandIdsAreFreshPerAction() {
		ThreadOps real = ThreadOps.system();
		String a = real.pin(T).payload().get("commandId").getAsString();
		String b = real.pin(T).payload().get("commandId").getAsString();
		assertNotEquals(a, b);
		assertFalse(a.isBlank());
	}

	@Test
	void onlySettleIsDestructive() {
		ThreadOps o = ops();
		assertTrue(o.settle(T).destructive());
		for (OperateAction a : List.of(o.snooze(T, ThreadOps.Snooze.HOURS_4), o.pin(T), o.unpin(T), o.markRead(T))) {
			assertFalse(a.destructive(), a.kind().wire);
		}
	}

	@Test
	void snoozeDurations() {
		assertEquals(Instant.parse("2026-11-07T16:30:00Z"), ThreadOps.Snooze.HOUR_1.until(NOW, ZONE));
		assertEquals(Instant.parse("2026-11-07T19:30:00Z"), ThreadOps.Snooze.HOURS_4.until(NOW, ZONE));
		// tomorrow 09:00 local (UTC-3) = 12:00Z
		assertEquals(Instant.parse("2026-11-08T12:00:00Z"), ThreadOps.Snooze.TOMORROW.until(NOW, ZONE));
		// just before local midnight: still "tomorrow" = the next calendar day
		Instant late = Instant.parse("2026-11-08T02:50:00Z"); // 23:50 local on the 7th
		assertEquals(Instant.parse("2026-11-08T12:00:00Z"), ThreadOps.Snooze.TOMORROW.until(late, ZONE));
		// right after midnight: the 9th
		Instant early = Instant.parse("2026-11-08T03:10:00Z"); // 00:10 local on the 8th
		assertEquals(Instant.parse("2026-11-09T12:00:00Z"), ThreadOps.Snooze.TOMORROW.until(early, ZONE));

		assertEquals("2026-11-08T12:00:00.000Z", ops().snooze(T, ThreadOps.Snooze.TOMORROW).payload().get("snoozedUntil").getAsString());
		assertEquals("2026-11-07T15:30:00.123Z", ThreadOps.iso(Instant.parse("2026-11-07T15:30:00.123456Z")));
	}

	@Test
	void snoozeParsing() {
		assertEquals(ThreadOps.Snooze.HOUR_1, ThreadOps.Snooze.parse("1h"));
		assertEquals(ThreadOps.Snooze.HOURS_4, ThreadOps.Snooze.parse("4H"));
		assertEquals(ThreadOps.Snooze.TOMORROW, ThreadOps.Snooze.parse("tomorrow"));
		assertEquals(ThreadOps.Snooze.TOMORROW, ThreadOps.Snooze.parse("mañana"));
		assertNull(ThreadOps.Snooze.parse("2d"));
		assertNull(ThreadOps.Snooze.parse(null));
	}

	@Test
	void targetsFromNpcIds() {
		ThreadOps.Target t = ThreadOps.Target.parse("vps:abc-123", "Title");
		assertNotNull(t);
		assertEquals("vps", t.envId());
		assertEquals("abc-123", t.threadId());
		assertEquals("vps:abc-123", t.npcId());
		assertNull(ThreadOps.Target.parse("nocolon", null));
		assertNull(ThreadOps.Target.parse(":x", null));
		assertNull(ThreadOps.Target.parse("x:", null));
	}

	@Test
	void throughTheGateSettleNeedsConfirmationTheRestGoesStraight(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		ThreadOps o = ops();
		OperateAction settle = o.settle(T);
		assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, s.gate().submit(settle, OperateGate.Origin.UI, false).join().outcome());
		assertTrue(s.transport().calls.isEmpty());
		assertTrue(s.gate().submit(settle, OperateGate.Origin.UI, true).join().ok());
		assertEquals("thread.settle", sent(s.transport(), 0).get("type").getAsString());

		for (OperateAction a : List.of(o.snooze(T, ThreadOps.Snooze.HOUR_1), o.pin(T), o.unpin(T), o.markRead(T))) {
			assertTrue(s.gate().submit(a, OperateGate.Origin.COMMAND, false).join().ok(), a.kind().wire);
		}
		assertEquals(List.of("thread.settle", "thread.snooze", "thread.pin", "thread.unpin", "thread.visit"),
				java.util.stream.IntStream.range(0, 5).mapToObj(i -> sent(s.transport(), i).get("type").getAsString()).toList());
	}

	@Test
	void readOnlyWithoutTokenSendsNothing(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, false);
		ThreadOps o = ops();
		for (OperateAction a : List.of(o.settle(T), o.snooze(T, ThreadOps.Snooze.TOMORROW), o.pin(T), o.unpin(T), o.markRead(T))) {
			assertEquals(OperateResult.Outcome.READ_ONLY, s.gate().submit(a, OperateGate.Origin.UI, true).join().outcome(), a.kind().wire);
		}
		assertTrue(s.transport().calls.isEmpty());
	}

	@Test
	void auditLogsEveryAttemptWithoutTitles(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		ThreadOps o = ops();
		s.gate().cancelled(o.settle(T), OperateGate.Origin.UI);
		s.gate().submit(o.settle(T), OperateGate.Origin.COMMAND, true).join();
		s.gate().submit(o.snooze(T, ThreadOps.Snooze.HOURS_4), OperateGate.Origin.UI, false).join();
		s.gate().submit(o.pin(T), OperateGate.Origin.UI, false).join();
		s.gate().submit(o.unpin(T), OperateGate.Origin.UI, false).join();
		s.gate().submit(o.markRead(T), OperateGate.Origin.UI, false).join();

		List<JsonObject> lines = OperateGateTest.auditLines(s.audit());
		assertEquals(List.of("thread_settle", "thread_settle", "thread_snooze", "thread_pin", "thread_unpin", "thread_mark_read"),
				lines.stream().map(l -> l.get("action").getAsString()).toList());
		assertEquals(List.of("cancelled", "ok", "ok", "ok", "ok", "ok"), lines.stream().map(l -> l.get("result").getAsString()).toList());
		assertEquals(List.of(true, true, false, false, false, false), lines.stream().map(l -> l.get("destructive").getAsBoolean()).toList());
		assertEquals("command", lines.get(1).get("origin").getAsString());
		lines.forEach(l -> assertEquals("t-1", l.get("thread").getAsString()));
		String raw = java.nio.file.Files.readString(s.audit());
		assertFalse(raw.contains("Fix the build"));
		assertFalse(raw.contains("snoozedUntil"), "payloads are not logged");
	}
}
