package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Item 21 (interrupt / stop) against the fake transport of {@link OperateGateTest}; never a real T3. */
class ThreadControlTest {
	static JsonObject sent(OperateGateTest.Setup s, int i) {
		String call = s.transport().calls.get(i);
		assertTrue(call.startsWith(OperateAction.DISPATCH_COMMAND + " "), call);
		return JsonParser.parseString(call.substring(OperateAction.DISPATCH_COMMAND.length() + 1)).getAsJsonObject();
	}

	@Test
	void interruptPayloadMatchesRunInterruptContract(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		OperateAction a = ThreadControl.interrupt(s.gate().operate("vps"), "t-1", "Fix the build", "run-9", () -> "cmd-1");
		assertEquals(OperateKind.RUN_INTERRUPT, a.kind());
		assertTrue(a.destructive());
		assertEquals("t-1", a.threadId());
		assertTrue(s.gate().submit(a, OperateGate.Origin.UI, true).join().ok());
		JsonObject p = sent(s, 0);
		assertEquals("run.interrupt", p.get("type").getAsString());
		assertEquals("cmd-1", p.get("commandId").getAsString());
		assertEquals("t-1", p.get("threadId").getAsString());
		assertEquals("run-9", p.get("runId").getAsString());
		assertEquals(4, p.size(), "no optional fields: " + p);
	}

	@Test
	void stopPayloadMatchesThreadStopContract(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		OperateAction a = ThreadControl.stop(s.gate().operate("vps"), "t-1", "Fix the build", () -> "cmd-2");
		assertEquals(OperateKind.THREAD_STOP, a.kind());
		assertTrue(a.destructive());
		assertTrue(s.gate().submit(a, OperateGate.Origin.COMMAND, true).join().ok());
		JsonObject p = sent(s, 0);
		assertEquals("thread.stop", p.get("type").getAsString());
		assertEquals("cmd-2", p.get("commandId").getAsString());
		assertEquals("t-1", p.get("threadId").getAsString());
		assertEquals(3, p.size(), "stops delegated tasks too (T3 default): " + p);
	}

	@Test
	void freshCommandIdPerAction(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		String a = ThreadControl.stop(s.gate().operate("vps"), "t-1", null).payload().get("commandId").getAsString();
		String b = ThreadControl.stop(s.gate().operate("vps"), "t-1", null).payload().get("commandId").getAsString();
		assertNotEquals(a, b);
		assertEquals(36, a.length(), "UUID");
	}

	@Test
	void bothNeedConfirmation(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		OperateAction stop = ThreadControl.stop(s.gate().operate("vps"), "t-1", null);
		OperateAction intr = ThreadControl.interrupt(s.gate().operate("vps"), "t-1", null, "run-1");
		for (OperateAction a : List.of(stop, intr)) {
			OperateResult r = s.gate().submit(a, OperateGate.Origin.UI, false).join();
			assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, r.outcome());
			assertFalse(ThreadControl.feedback(r).ok());
		}
		assertTrue(s.transport().calls.isEmpty());
	}

	@Test
	void readOnlyWithoutTokenSendsNothing(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, false);
		OperateResult r = s.gate().submit(ThreadControl.stop(s.gate().operate("vps"), "t-1", null), OperateGate.Origin.COMMAND, true).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r.outcome());
		OperateResult r2 = s.gate().submit(ThreadControl.interrupt(s.gate().operate("vps"), "t-1", null, "run-1"), OperateGate.Origin.UI, true).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r2.outcome());
		assertTrue(s.transport().calls.isEmpty());
		ThreadControl.Feedback f = ThreadControl.feedback(r);
		assertFalse(f.ok());
		assertTrue(f.text().contains("read-only"), f.text());
	}

	@Test
	void auditRecordsInterruptAndStop(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		OperateAction intr = ThreadControl.interrupt(s.gate().operate("vps"), "t-1", "Secret title", "run-1");
		s.gate().cancelled(intr, OperateGate.Origin.UI);
		s.gate().submit(ThreadControl.stop(s.gate().operate("vps"), "t-1", "Secret title"), OperateGate.Origin.COMMAND, true).join();
		List<JsonObject> lines = OperateGateTest.auditLines(s.audit());
		assertEquals(2, lines.size());
		assertEquals("run_interrupt", lines.get(0).get("action").getAsString());
		assertEquals("cancelled", lines.get(0).get("result").getAsString());
		assertEquals("ui", lines.get(0).get("origin").getAsString());
		assertEquals("thread_stop", lines.get(1).get("action").getAsString());
		assertEquals("ok", lines.get(1).get("result").getAsString());
		assertEquals("command", lines.get(1).get("origin").getAsString());
		assertTrue(lines.get(1).get("destructive").getAsBoolean());
		assertFalse(java.nio.file.Files.readString(s.audit()).contains("Secret title"));
	}

	@Test
	void failedIsShownAndNotRetried(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		s.transport().reply = OperateResult.of(OperateResult.Outcome.FAILED, "timeout");
		OperateResult r = s.gate().submit(ThreadControl.stop(s.gate().operate("vps"), "t-1", null), OperateGate.Origin.UI, true).join();
		assertEquals(OperateResult.Outcome.FAILED, r.outcome());
		assertEquals(1, s.transport().calls.size(), "sent exactly once");
		ThreadControl.Feedback f = ThreadControl.feedback(r);
		assertFalse(f.ok());
		assertTrue(f.text().contains("may or may not have applied"), f.text());
	}

	@Test
	void feedbackForEveryOutcome() {
		for (OperateResult.Outcome o : OperateResult.Outcome.values()) {
			OperateResult r = o == OperateResult.Outcome.OK ? OperateResult.ok(null) : OperateResult.of(o, "why");
			ThreadControl.Feedback f = ThreadControl.feedback(r);
			assertEquals(o == OperateResult.Outcome.OK, f.ok(), o.name());
			assertFalse(f.text().isBlank(), o.name());
		}
		assertTrue(ThreadControl.feedback(OperateResult.of(OperateResult.Outcome.REJECTED, "unknown thread")).text().contains("unknown thread"));
	}

	@Test
	void parsesNpcTargets() {
		assertEquals(new ThreadControl.Target("vps", "t-1"), ThreadControl.parse("vps:t-1"));
		assertEquals(new ThreadControl.Target("vps", "a:b"), ThreadControl.parse("vps:a:b"));
		assertEquals("vps:t-1", ThreadControl.parse("vps:t-1").npcId());
		assertNull(ThreadControl.parse("t-1"));
		assertNull(ThreadControl.parse(":t-1"));
		assertNull(ThreadControl.parse("vps:"));
		assertNull(ThreadControl.parse(null));
	}

	@Test
	void validatesInputs(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		assertThrows(IllegalArgumentException.class, () -> ThreadControl.interrupt(s.gate().operate("vps"), "t-1", null, ""));
		assertThrows(IllegalArgumentException.class, () -> ThreadControl.stop(s.gate().operate("vps"), " ", null));
	}

	@Test
	void buttonsAreTranslated() throws Exception {
		for (String l : new String[] {"en_us", "es_es"}) {
			JsonObject o = OperateLangTest.lang(l);
			for (String key : new String[] {"t3craft.operate.control.interrupt", "t3craft.operate.control.stop",
					"t3craft.operate.control.no_run", OperateKind.RUN_INTERRUPT.langKey(), OperateKind.THREAD_STOP.langKey()}) {
				assertTrue(o.has(key), l + " misses " + key);
			}
		}
		assertEquals("Detener", OperateLangTest.lang("es_es").get("t3craft.operate.control.stop").getAsString());
	}
}
