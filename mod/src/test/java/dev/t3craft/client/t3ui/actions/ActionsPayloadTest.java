package dev.t3craft.client.t3ui.actions;

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
import dev.t3craft.t3.ThreadLaunch;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact payloads of the configure / launch / project.create commands, sent through a fake transport. Never talks to T3. */
class ActionsPayloadTest {
	static final long NOW = 5_000_000;

	static final class FakeTransport implements OperateTransport {
		final List<String> tags = new ArrayList<>();
		final List<JsonObject> payloads = new ArrayList<>();
		String answer = "{\"sequence\":7}";

		@Override
		public CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload) {
			tags.add(rpcTag);
			payloads.add(payload);
			return CompletableFuture.completedFuture(OperateResult.ok(JsonParser.parseString(answer)));
		}
	}

	record Setup(OperateGate gate, FakeTransport transport) {
	}

	static Setup setup(Path dir, boolean operate) throws Exception {
		OperateCredentials creds = new OperateCredentials(dir);
		if (operate) {
			creds.save("vps", new Pairing.Session("operate-secret", Pairing.OPERATE_SCOPES, NOW + 60_000));
		}
		FakeTransport t = new FakeTransport();
		return new Setup(new OperateGate(creds, env -> "vps".equals(env) ? t : null, AuditLog.in(dir), () -> NOW), t);
	}

	static ThreadCommands commands() {
		AtomicInteger n = new AtomicInteger();
		return new ThreadCommands(() -> "cmd-" + n.incrementAndGet());
	}

	static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void configureSendsOnlyTheChangedCommandsWithTheContractShape(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		ModelPick pick = new ModelPick("claudeAgent", "claude-opus-5-5", "effort", "high", Modes.Runtime.AUTO_ACCEPT_EDITS, Modes.Interaction.PLAN);
		List<OperateAction> all = commands().configure("vps", "thr-1", "Fix", pick,
				EnumSet.allOf(ModelPickerLogic.Dimension.class));
		assertEquals(3, all.size());
		for (OperateAction a : all) {
			assertEquals(OperateKind.THREAD_CONFIGURE, a.kind());
			assertFalse(a.destructive());
			assertEquals(OperateAction.DISPATCH_COMMAND, a.rpcTag());
			assertTrue(s.gate().submit(a, OperateGate.Origin.UI, false).join().ok());
		}
		assertEquals(List.of(OperateAction.DISPATCH_COMMAND, OperateAction.DISPATCH_COMMAND, OperateAction.DISPATCH_COMMAND), s.transport().tags);
		assertEquals(json("""
				{"type":"thread.model-selection.set","commandId":"cmd-1","threadId":"thr-1",
				 "modelSelection":{"instanceId":"claudeAgent","model":"claude-opus-5-5","options":[{"id":"effort","value":"high"}]}}"""),
				s.transport().payloads.get(0));
		assertEquals(json("""
				{"type":"thread.runtime-mode.set","commandId":"cmd-2","threadId":"thr-1","runtimeMode":"auto-accept-edits"}"""),
				s.transport().payloads.get(1));
		assertEquals(json("""
				{"type":"thread.interaction-mode.set","commandId":"cmd-3","threadId":"thr-1","interactionMode":"plan"}"""),
				s.transport().payloads.get(2));

		List<OperateAction> onlyRuntime = commands().configure("vps", "thr-1", null, pick, Set.of(ModelPickerLogic.Dimension.RUNTIME));
		assertEquals(1, onlyRuntime.size());
		assertEquals("thread.runtime-mode.set", onlyRuntime.getFirst().payload().get("type").getAsString());
		assertTrue(commands().configure("vps", "thr-1", null, pick, Set.of()).isEmpty());
	}

	@Test
	void modelSelectionWithoutEffortHasNoOptions() {
		ModelPick pick = new ModelPick("codex", "gpt-5", "reasoningEffort", null, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		assertEquals(json("{\"instanceId\":\"codex\",\"model\":\"gpt-5\"}"), pick.modelSelection());
	}

	@Test
	void launchPayloadMatchesTheContract(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		NewThreadForm f = new NewThreadForm();
		f.envId = "vps";
		f.projectId = "proj-1";
		f.prompt = "Fix the build\nplease";
		f.pick = new ModelPick("claudeAgent", "claude-opus-5-5", "effort", "max", Modes.Runtime.FULL_ACCESS, Modes.Interaction.DEFAULT);
		f.workspace = NewThreadForm.Workspace.WORKTREE;
		f.baseRef = " main ";
		f.branch = "t3/fix-build";
		f.startFromOrigin = true;
		NewThreadForm.Built b = f.build(true, null, () -> new ThreadLaunch.Ids("cmd-x", "thr-new", "msg-x"));
		assertNull(b.error());
		OperateAction a = b.action();
		assertEquals(OperateKind.THREAD_LAUNCH, a.kind());
		assertEquals(OperateAction.LAUNCH_THREAD, a.rpcTag());
		assertEquals(json("""
				{"commandId":"cmd-x","threadId":"thr-new","projectId":"proj-1","title":"Fix the build","generateTitle":true,
				 "modelSelection":{"instanceId":"claudeAgent","model":"claude-opus-5-5","options":[{"id":"effort","value":"max"}]},
				 "runtimeMode":"full-access","interactionMode":"default",
				 "workspaceStrategy":{"type":"worktree","baseRef":"main","branch":"t3/fix-build","startFromOrigin":true},
				 "initialMessage":{"messageId":"msg-x","text":"Fix the build\\nplease","attachments":[]}}"""), a.payload());
		s.transport().answer = "{\"threadId\":\"thr-server\",\"resumed\":false}";
		OperateResult r = s.gate().submit(a, OperateGate.Origin.UI, false).join();
		assertEquals("thr-server", ThreadLaunch.threadId(r), "the created thread id comes from T3's answer");
	}

	@Test
	void launchWithProjectDefaultUsesRootAndSafeModes() {
		NewThreadForm f = new NewThreadForm();
		f.envId = "vps";
		f.projectId = "proj-1";
		f.prompt = "hi";
		NewThreadForm.Built b = f.build(true, new ThreadLaunch.ModelChoice("codex", "gpt-5", null),
				() -> new ThreadLaunch.Ids("c", "t", "m"));
		JsonObject p = b.action().payload();
		assertEquals(json("{\"type\":\"root\"}"), p.getAsJsonObject("workspaceStrategy"));
		assertEquals("approval-required", p.get("runtimeMode").getAsString());
		assertEquals("default", p.get("interactionMode").getAsString());
		assertEquals(json("{\"instanceId\":\"codex\",\"model\":\"gpt-5\"}"), p.getAsJsonObject("modelSelection"));
	}

	@Test
	void projectCreatePayloadGoesToProjectsMutate(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		RegisterProjectForm f = new RegisterProjectForm();
		f.envId = "vps";
		f.workspaceRoot = "  /home/me/t3craft/ ";
		RegisterProjectForm.Built b = f.build(true, List.of(), () -> new RegisterProjectForm.Ids("cmd-p", "proj-new"));
		assertNull(b.error());
		OperateAction a = b.action();
		assertEquals(OperateKind.PROJECT_CREATE, a.kind());
		assertEquals(OperateAction.PROJECTS_MUTATE, a.rpcTag());
		assertEquals(json("""
				{"type":"project.create","commandId":"cmd-p","projectId":"proj-new","title":"t3craft",
				 "workspaceRoot":"/home/me/t3craft","createWorkspaceRootIfMissing":false,"defaultModelSelection":null}"""), a.payload());
		s.transport().answer = "{\"id\":\"proj-new\",\"title\":\"t3craft\"}";
		OperateResult r = s.gate().submit(a, OperateGate.Origin.UI, false).join();
		assertEquals(List.of("projects.mutate"), s.transport().tags);
		assertEquals("proj-new", RegisterProjectForm.projectId(r));
	}

	@Test
	void archiveIsDestructiveAndNeedsConfirmation(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, true);
		OperateAction a = commands().archive("vps", "thr-1", "Fix");
		assertTrue(a.destructive());
		assertEquals(json("{\"type\":\"thread.archive\",\"commandId\":\"cmd-1\",\"threadId\":\"thr-1\"}"), a.payload());
		assertEquals(OperateResult.Outcome.NEEDS_CONFIRMATION, s.gate().submit(a, OperateGate.Origin.UI, false).join().outcome());
		assertTrue(s.transport().tags.isEmpty());
	}

	@Test
	void withoutOperateNothingIsSentAndTheWidgetsAreDisabled(@TempDir Path dir) throws Exception {
		Setup s = setup(dir, false);
		ActionGate gate = ActionGate.of(s.gate());
		assertEquals(ActionGate.State.READ_ONLY, gate.state("vps"));
		assertFalse(gate.enabled("vps"));
		assertEquals("t3craft.actions.enable_control", ActionGate.disabledKey(gate.state("vps")));
		assertEquals(ActionGate.State.NO_ENV, gate.state(null));
		assertNull(ActionGate.disabledKey(ActionGate.State.ENABLED));

		ModelPick pick = new ModelPick("codex", "gpt-5", null, null, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		for (OperateAction a : commands().configure("vps", "thr-1", null, pick, EnumSet.allOf(ModelPickerLogic.Dimension.class))) {
			assertEquals(OperateResult.Outcome.READ_ONLY, s.gate().submit(a, OperateGate.Origin.UI, false).join().outcome());
		}
		assertTrue(s.transport().tags.isEmpty(), "read-only: nothing reaches T3");

		NewThreadForm f = new NewThreadForm();
		f.envId = "vps";
		f.projectId = "p";
		f.prompt = "x";
		f.pick = pick;
		assertEquals("Enable control (operate) for this environment first.", f.build(false, null, ThreadLaunch.Ids::random).error());
		RegisterProjectForm rf = new RegisterProjectForm();
		rf.envId = "vps";
		rf.workspaceRoot = "/srv/app";
		assertEquals("Enable control (operate) for this environment first.", rf.build(false, List.of(), RegisterProjectForm.Ids::random).error());
	}

	@Test
	void expiredSessionReadsEnableAgain(@TempDir Path dir) throws Exception {
		OperateCredentials creds = new OperateCredentials(dir);
		creds.save("vps", new Pairing.Session("s", Pairing.OPERATE_SCOPES, NOW - 1));
		OperateGate g = new OperateGate(creds, env -> null, AuditLog.in(dir), () -> NOW);
		ActionGate gate = ActionGate.of(g);
		assertEquals(ActionGate.State.EXPIRED, gate.state("vps"));
		assertEquals("t3craft.actions.enable_control.expired", ActionGate.disabledKey(gate.state("vps")));
	}

	@Test
	void onlyProjectsMutateIsAllowedOutsideOrchestration() {
		assertThrows(IllegalArgumentException.class,
				() -> new OperateAction(OperateKind.PROJECT_CREATE, "vps", null, null, "projects.writeFile", new JsonObject()));
	}
}
