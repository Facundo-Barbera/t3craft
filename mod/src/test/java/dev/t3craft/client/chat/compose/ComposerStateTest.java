package dev.t3craft.client.chat.compose;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.t3.AuditLog;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateCredentials;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.OperateTransport;
import dev.t3craft.t3.Pairing;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The composer's send path against a fake operate transport (never a real T3): the exact
 * {@code message.dispatch} / {@code run.interrupt} payloads, optimistic rows and their
 * reconciliation, errors, drafts, read-only and Stop.
 */
class ComposerStateTest {
	static final long NOW = 5_000_000;

	/** Records what would go out; each call answers with a future the test completes. */
	static final class FakeTransport implements OperateTransport {
		final List<String> tags = new ArrayList<>();
		final List<JsonObject> payloads = new ArrayList<>();
		final List<CompletableFuture<OperateResult>> answers = new ArrayList<>();

		@Override
		public CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload) {
			tags.add(rpcTag);
			payloads.add(payload);
			CompletableFuture<OperateResult> f = new CompletableFuture<>();
			answers.add(f);
			return f;
		}

		void ok(int i) {
			answers.get(i).complete(OperateResult.ok(JsonParser.parseString("{\"sequence\":7}")));
		}
	}

	record Setup(ComposerState state, FakeTransport transport, List<OperateAction> actions) {
	}

	static Setup setup(Path dir, boolean operate) throws Exception {
		OperateCredentials creds = new OperateCredentials(dir);
		if (operate) {
			creds.save("vps", new Pairing.Session("operate-secret", Pairing.OPERATE_SCOPES, NOW + 60_000));
		}
		FakeTransport t = new FakeTransport();
		OperateGate gate = new OperateGate(creds, env -> "vps".equals(env) ? t : null, AuditLog.in(dir), () -> NOW);
		List<OperateAction> actions = new ArrayList<>();
		AtomicInteger n = new AtomicInteger();
		// confirmed=true stands in for the in-game confirmation screen (Stop is destructive)
		ChatSender sender = new ChatSender(gate, (a, done) -> {
			actions.add(a);
			gate.submit(a, OperateGate.Origin.UI, true).thenAccept(done);
		}, () -> {
			int i = n.incrementAndGet();
			return new ChatSender.Ids("cmd-" + i, "msg-" + i);
		});
		return new Setup(new ComposerState(sender, () -> NOW), t, actions);
	}

	static ChatEntry userEcho(String messageId, String text) {
		return new ChatEntry("t-1:item-" + messageId, "item-" + messageId, messageId, ChatEntry.Kind.USER, "", text, null, false, "completed",
				NOW, 3, false);
	}

	@Test
	void sendDispatchesMessageDispatchWithTheExactContractPayload(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		s.state().setThread("vps", "t-1", "Fix the build");
		s.state().editor().set("  please run the tests\nand report  ");
		assertEquals(ComposerState.Button.SEND, s.state().button());
		assertTrue(s.state().submit());
		assertEquals(List.of(OperateAction.DISPATCH_COMMAND), s.transport().tags);
		assertEquals(OperateKind.MESSAGE_SEND, s.actions().getFirst().kind());
		assertFalse(s.actions().getFirst().destructive(), "sending needs no confirmation");
		JsonObject expected = JsonParser.parseString("""
				{"type":"message.dispatch","commandId":"cmd-1","createdBy":"user","creationSource":"web",
				 "threadId":"t-1","messageId":"msg-1","text":"please run the tests\\nand report",
				 "attachments":[],"deliveryIntent":"auto","dispatchMode":{"type":"start_immediately"}}""").getAsJsonObject();
		assertEquals(expected, s.transport().payloads.getFirst());
	}

	@Test
	void optimisticRowShowsAtOnceAndIsReconciledWithTheServerEcho(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.editor().set("hello");
		long v0 = c.version();
		c.submit();
		assertEquals("", c.editor().text(), "the input empties at once");
		assertEquals(ComposerState.Button.SENDING, c.button());
		List<ChatEntry> rows = c.pendingEntries();
		assertEquals(1, rows.size());
		assertEquals(ChatEntry.Kind.USER, rows.getFirst().kind());
		assertEquals("hello", rows.getFirst().body());
		assertEquals("msg-1", rows.getFirst().messageId());
		assertTrue(c.version() > v0);

		s.transport().ok(0);
		assertEquals(ComposerState.Button.SEND_DISABLED, c.button());
		assertEquals(1, c.pendingEntries().size(), "stays until the stream shows it");

		assertFalse(c.observe(List.of(userEcho("other", "x")), false, null));
		assertEquals(1, c.pendingEntries().size());
		long v1 = c.version();
		assertTrue(c.observe(List.of(userEcho("msg-1", "hello")), true, "run-1"));
		assertTrue(c.pendingEntries().isEmpty());
		assertTrue(c.version() > v1);
	}

	@Test
	void failureRestoresTheTextAndShowsTheError(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.editor().set("first try");
		c.submit();
		c.editor().set("typed meanwhile");
		s.transport().answers.getFirst().complete(OperateResult.of(OperateResult.Outcome.REJECTED, "thread archived"));
		assertTrue(c.pendingEntries().isEmpty(), "the optimistic row goes away");
		assertEquals("first try\ntyped meanwhile", c.editor().text(), "nothing is lost");
		assertNotNull(c.error());
		assertTrue(c.error().contains("thread archived"), c.error());
		// recovery: send again
		c.editor().set("first try");
		assertTrue(c.submit());
		assertNull(c.error(), "a new send clears the error");
		s.transport().ok(1);
		assertEquals(2, s.transport().payloads.size());
		assertEquals("msg-2", s.transport().payloads.get(1).get("messageId").getAsString());
	}

	@Test
	void offlineFailsSynchronouslyAndStillRecovers(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("other-env", "t-9", null); // no transport for it, but also no token → read-only
		assertEquals(ComposerState.Button.ENABLE_CONTROL, c.button());
		c.editor().set("hi");
		assertFalse(c.submit());
		assertEquals("hi", c.editor().text());
		assertTrue(s.transport().payloads.isEmpty());
	}

	@Test
	void readOnlyEnvironmentIsDisabledAndSendsNothing(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, false);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		assertFalse(c.canOperate());
		assertEquals(ComposerState.Button.ENABLE_CONTROL, c.button());
		c.editor().set("hello");
		assertFalse(c.submit());
		c.primary();
		assertTrue(s.transport().payloads.isEmpty());
		assertTrue(s.actions().isEmpty());
		assertEquals("hello", c.editor().text());
	}

	@Test
	void draftsAreKeptPerThread(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.editor().set("draft for one", 5);
		c.setThread("vps", "t-2", null);
		assertEquals("", c.editor().text());
		c.editor().set("draft for two");
		c.setThread("vps", "t-1", null);
		assertEquals("draft for one", c.editor().text());
		assertEquals(5, c.editor().caret(), "caret comes back too");
		assertEquals("draft for two", c.draftOf("vps:t-2"));
		c.setThread("vps", "t-1", "new title");
		assertEquals("draft for one", c.editor().text(), "same thread: nothing reset");
		c.clearThread();
		c.setThread("vps", "t-1", null);
		assertEquals("draft for one", c.editor().text());
	}

	@Test
	void optimisticRowsBelongToTheirThread(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.editor().set("to one");
		c.submit();
		c.setThread("vps", "t-2", null);
		assertTrue(c.pendingEntries().isEmpty());
		assertEquals(ComposerState.Button.SEND_DISABLED, c.button(), "sending in t-1 does not block t-2");
		// t-1 fails while t-2 is shown: its text goes back to t-1's draft
		s.transport().answers.getFirst().complete(OperateResult.of(OperateResult.Outcome.OFFLINE, "down"));
		assertNull(c.error());
		assertEquals("to one", c.draftOf("vps:t-1"));
		c.setThread("vps", "t-1", null);
		assertEquals("to one", c.editor().text());
		assertNotNull(c.error());
	}

	@Test
	void stopReplacesSendWhileATurnRunsAndInterruptsTheActiveRun(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", "Fix the build");
		c.observe(List.of(), true, "run-9");
		assertEquals(ComposerState.Button.STOP, c.button());
		c.editor().set("steer it");
		assertEquals(ComposerState.Button.SEND, c.button(), "with text it sends (T3 steers the running turn)");
		c.editor().clear();
		c.primary();
		assertEquals(ComposerState.Button.STOPPING, c.button());
		OperateAction a = s.actions().getFirst();
		assertEquals(OperateKind.RUN_INTERRUPT, a.kind());
		assertTrue(a.destructive(), "Stop keeps the in-game confirmation");
		JsonObject p = s.transport().payloads.getFirst();
		assertEquals("run.interrupt", p.get("type").getAsString());
		assertEquals("t-1", p.get("threadId").getAsString());
		assertEquals("run-9", p.get("runId").getAsString());
		assertEquals(4, p.size(), "type, commandId, threadId, runId: " + p);
		s.transport().ok(0);
		assertEquals(ComposerState.Button.STOP, c.button(), "until the stream says the run ended");
		c.observe(List.of(), false, null);
		assertEquals(ComposerState.Button.SEND_DISABLED, c.button());
	}

	@Test
	void stopWithoutARunIdSaysSoAndSendsNothing(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.observe(List.of(), true, null);
		assertFalse(c.stop());
		assertNotNull(c.error());
		assertTrue(s.transport().payloads.isEmpty());
	}

	@Test
	void cancelledStopIsNotAnError(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.observe(List.of(), true, "run-1");
		c.stop();
		s.transport().answers.getFirst().complete(OperateResult.of(OperateResult.Outcome.CANCELLED, "cancelled"));
		assertNull(c.error());
		assertEquals(ComposerState.Button.STOP, c.button());
	}

	@Test
	void blankTextIsNotSent(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ComposerState c = s.state();
		c.setThread("vps", "t-1", null);
		c.editor().set("   \n ");
		assertEquals(ComposerState.Button.SEND_DISABLED, c.button());
		assertFalse(c.submit());
		assertTrue(s.transport().payloads.isEmpty());
	}
}
