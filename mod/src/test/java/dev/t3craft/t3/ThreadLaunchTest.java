package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Item 23 (launch a thread) against the fake gate/transport. Never talks to T3. */
class ThreadLaunchTest {
	static final ThreadLaunch.Ids IDS = new ThreadLaunch.Ids("cmd-1", "thread-new", "msg-1");
	static final String SECRET_PROMPT = "Rotate the prod DB password hunter2\nand redeploy everything";

	/** A shell like the one T3 streams: one project with a default model, one thread on another model. */
	static ShellState.View view() {
		ShellState s = new ShellState();
		s.apply(JsonParser.parseString("""
				{"kind":"snapshot","snapshot":{"snapshotSequence":3,
				 "projects":[
				  {"id":"p-web","title":"Web","workspaceRoot":"/srv/web",
				   "defaultModelSelection":{"instanceId":"codex","model":"gpt-5.5","options":{"effort":"high"}},"scripts":[]},
				  {"id":"p-api","title":"api","workspaceRoot":"/srv/api","defaultModelSelection":null,"scripts":[]}],
				 "threads":[
				  {"id":"t1","projectId":"p-api","title":"Old","status":"idle",
				   "modelSelection":{"instanceId":"claudeAgent","model":"claude-opus-5-5"}},
				  {"id":"t2","projectId":"p-web","title":"Other","status":"idle",
				   "modelSelection":{"instanceId":"codex","model":"gpt-5.5"}}]}}
				""").getAsJsonObject());
		return s.view();
	}

	@Test
	void payloadFollowsTheLaunchThreadContract() {
		ThreadLaunch.ModelChoice m = new ThreadLaunch.ModelChoice("codex", "gpt-5.5", null);
		JsonObject p = ThreadLaunch.payload("p-web", "  Fix the login page\nsecond line", m, IDS);
		assertEquals(Set.of("commandId", "threadId", "projectId", "title", "generateTitle", "modelSelection", "runtimeMode",
				"interactionMode", "workspaceStrategy", "initialMessage"), p.keySet());
		assertEquals("cmd-1", p.get("commandId").getAsString());
		assertEquals("thread-new", p.get("threadId").getAsString());
		assertEquals("p-web", p.get("projectId").getAsString());
		assertEquals("Fix the login page", p.get("title").getAsString());
		assertTrue(p.get("generateTitle").getAsBoolean());
		assertEquals(JsonParser.parseString("{\"instanceId\":\"codex\",\"model\":\"gpt-5.5\"}"), p.get("modelSelection"));
		assertEquals("approval-required", p.get("runtimeMode").getAsString(), "never T3's full-access default");
		assertEquals("default", p.get("interactionMode").getAsString());
		assertEquals(JsonParser.parseString("{\"type\":\"root\"}"), p.get("workspaceStrategy"));
		JsonObject msg = p.getAsJsonObject("initialMessage");
		assertEquals("msg-1", msg.get("messageId").getAsString());
		assertEquals("  Fix the login page\nsecond line", msg.get("text").getAsString(), "the prompt goes out verbatim");
		assertEquals(0, msg.getAsJsonArray("attachments").size());
		assertFalse(p.has("creationSource"));
		assertFalse(p.has("reuseExistingThread"));
	}

	@Test
	void titleIsTheFirstNonBlankLineCut() {
		assertEquals("Hello world", ThreadLaunch.title("\n\n   Hello    world  \nmore"));
		String t = ThreadLaunch.title("x".repeat(200));
		assertEquals(ThreadLaunch.MAX_TITLE, t.length());
		assertTrue(t.endsWith("…"));
		assertEquals("New thread", ThreadLaunch.title(" \n "));
	}

	@Test
	void promptValidation() {
		assertNotNull(ThreadLaunch.validatePrompt(null));
		assertNotNull(ThreadLaunch.validatePrompt(""));
		assertNotNull(ThreadLaunch.validatePrompt(" \n\t "));
		assertNull(ThreadLaunch.validatePrompt("do it"));
		assertNull(ThreadLaunch.validatePrompt("x".repeat(ThreadLaunch.MAX_PROMPT)));
		assertNotNull(ThreadLaunch.validatePrompt("x".repeat(ThreadLaunch.MAX_PROMPT + 1)));
		ThreadLaunch.ModelChoice m = new ThreadLaunch.ModelChoice("codex", "gpt-5.5", null);
		assertThrows(IllegalArgumentException.class, () -> ThreadLaunch.payload("p-web", "  ", m, IDS));

		ThreadLaunch.Built b = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", "   ", null), view(), () -> IDS);
		assertNull(b.action());
		assertNotNull(b.error());
		assertNull(ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-nope", "hi", null), view(), () -> IDS).action(), "unknown project");
		assertNull(ThreadLaunch.build(new ThreadLaunch.Request("vps", "", "hi", null), view(), () -> IDS).action(), "no project");
	}

	@Test
	void launchIsNotDestructiveAndUsesLaunchThread() {
		OperateAction a = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", "hi", null), view(), () -> IDS).action();
		assertNotNull(a);
		assertEquals(OperateKind.THREAD_LAUNCH, a.kind());
		assertFalse(a.destructive());
		assertEquals("orchestration.launchThread", a.rpcTag());
		assertEquals("thread-new", a.threadId());
		assertFalse(a.toString().contains("hi\""), "payload is redacted in toString");
	}

	@Test
	void modelsComeFromTheShellWithTheProjectDefaultFirst(@TempDir Path dir) {
		ShellState.View v = view();
		List<ThreadLaunch.ModelChoice> web = ThreadLaunch.models(v, "p-web");
		assertEquals(2, web.size(), "the thread's gpt-5.5 de-duplicates against the default");
		assertEquals("gpt-5.5", web.get(0).model());
		assertEquals("high", web.get(0).options().get("effort").getAsString(), "default keeps its options");
		assertEquals("claude-opus-5-5", web.get(1).model());

		// No default for p-api: its own thread's model comes first; the default ("null model") resolves to it.
		ThreadLaunch.ModelChoice api = ThreadLaunch.resolveModel(v, "p-api", null);
		assertEquals("claudeAgent", api.instanceId());
		OperateAction a = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", "hi", null), v, () -> IDS).action();
		assertEquals("high", a.payload().getAsJsonObject("modelSelection").getAsJsonObject("options").get("effort").getAsString());
		ThreadLaunch.ModelChoice chosen = web.get(1);
		OperateAction b = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", "hi", chosen), v, () -> IDS).action();
		assertEquals("claude-opus-5-5", b.payload().getAsJsonObject("modelSelection").get("model").getAsString());

		// Nothing exposed at all → no launch (the model is required by T3).
		ShellState empty = new ShellState();
		empty.apply(JsonParser.parseString("{\"kind\":\"snapshot\",\"snapshot\":{\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"workspaceRoot\":\"/p\",\"defaultModelSelection\":null}],\"threads\":[]}}").getAsJsonObject());
		ThreadLaunch.Built none = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p", "hi", null), empty.view(), () -> IDS);
		assertNull(none.action());
		assertTrue(none.error().contains("model"));
	}

	@Test
	void projectsAreListedAndFoundByIdOrName() {
		List<ThreadLaunch.ProjectChoice> ps = ThreadLaunch.projects(view());
		assertEquals(List.of("p-api", "p-web"), ps.stream().map(ThreadLaunch.ProjectChoice::id).toList(), "sorted by label");
		assertEquals("p-web", ThreadLaunch.findProject(ps, "web").id());
		assertEquals("p-api", ThreadLaunch.findProject(ps, "p-api").id());
		assertNull(ThreadLaunch.findProject(ps, "nope"));
		assertTrue(ThreadLaunch.projects(null).isEmpty());
	}

	@Test
	void shellKeepsAndForgetsDefaultModels() {
		ShellState s = new ShellState();
		s.apply(JsonParser.parseString("{\"kind\":\"snapshot\",\"snapshot\":{\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"workspaceRoot\":\"/p\",\"defaultModelSelection\":{\"provider\":\"codex\",\"model\":\"m\"}}],\"threads\":[]}}").getAsJsonObject());
		assertEquals("codex", ThreadLaunch.ModelChoice.fromJson(s.view().projectDefaultModel("p")).instanceId(), "legacy provider accepted");
		s.apply(JsonParser.parseString("{\"kind\":\"project.updated\",\"sequence\":5,\"project\":{\"id\":\"p\",\"title\":\"P\",\"workspaceRoot\":\"/p\",\"defaultModelSelection\":null}}").getAsJsonObject());
		assertNull(s.view().projectDefaultModel("p"));
		s.apply(JsonParser.parseString("{\"kind\":\"project.updated\",\"sequence\":6,\"project\":{\"id\":\"p\",\"title\":\"P\",\"workspaceRoot\":\"/p\",\"defaultModelSelection\":{\"instanceId\":\"x\",\"model\":\"y\"}}}").getAsJsonObject());
		assertNotNull(s.view().projectDefaultModel("p"));
		s.apply(JsonParser.parseString("{\"kind\":\"project.removed\",\"sequence\":7,\"projectId\":\"p\"}").getAsJsonObject());
		assertNull(s.view().projectDefaultModel("p"));
		assertTrue(new ShellState.View(List.of(), java.util.Map.of(), true).projectDefaultModels().isEmpty(), "old 3-arg shape still works");
	}

	@Test
	void onlyEnvironmentsWithOperateAreOffered(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true); // token for "vps" only
		assertEquals(List.of("vps"), ThreadLaunch.operableEnvs(List.of("home", "vps", "laptop"), s.gate()));
		OperateGateTest.Setup none = OperateGateTest.setup(dir.resolve("none"), false);
		assertTrue(ThreadLaunch.operableEnvs(List.of("vps"), none.gate()).isEmpty());
	}

	@Test
	void readOnlyWithoutTokenSendsNothingAndIsAudited(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, false);
		OperateAction a = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", SECRET_PROMPT, null), view(), () -> IDS).action();
		OperateResult r = s.gate().submit(a, OperateGate.Origin.UI, false).join();
		assertEquals(OperateResult.Outcome.READ_ONLY, r.outcome());
		assertTrue(s.transport().calls.isEmpty(), "nothing goes out read-only");
		assertNull(ThreadLaunch.threadId(r));
		List<JsonObject> lines = OperateGateTest.auditLines(s.audit());
		assertEquals(1, lines.size());
		assertEquals("thread_launch", lines.get(0).get("action").getAsString());
		assertEquals("read_only", lines.get(0).get("result").getAsString());
		assertNoPrompt(s.audit());
	}

	@Test
	void launchGoesOutOnceAndTheAuditNeverHasThePrompt(@TempDir Path dir) throws Exception {
		OperateGateTest.Setup s = OperateGateTest.setup(dir, true);
		s.transport().reply = OperateResult.ok(JsonParser.parseString("{\"threadId\":\"server-thread\",\"projection\":{},\"resumed\":false,\"sequence\":9}"));
		OperateAction a = ThreadLaunch.build(new ThreadLaunch.Request("vps", "p-web", SECRET_PROMPT, null), view(), () -> IDS).action();
		OperateResult r = s.gate().submit(a, OperateGate.Origin.COMMAND, false).join();
		assertEquals(OperateResult.Outcome.OK, r.outcome(), "not destructive: no confirmation needed");
		assertEquals(1, s.transport().calls.size());
		assertTrue(s.transport().calls.get(0).startsWith("orchestration.launchThread "));
		assertEquals("server-thread", ThreadLaunch.threadId(r), "T3's thread id wins");

		JsonObject line = OperateGateTest.auditLines(s.audit()).get(0);
		assertEquals("thread_launch", line.get("action").getAsString());
		assertFalse(line.get("destructive").getAsBoolean());
		assertEquals("ok", line.get("result").getAsString());
		assertEquals("command", line.get("origin").getAsString());
		assertNoPrompt(s.audit());

		// A rejection is audited too, still without the prompt.
		s.transport().reply = OperateResult.of(OperateResult.Outcome.REJECTED, "ThreadLaunchError: resolve-project");
		assertEquals(OperateResult.Outcome.REJECTED, s.gate().submit(a, OperateGate.Origin.UI, false).join().outcome());
		assertEquals(2, OperateGateTest.auditLines(s.audit()).size());
		assertNoPrompt(s.audit());
	}

	@Test
	void promptSummaryNeverContainsTheText() {
		String sum = ThreadLaunch.promptSummary(SECRET_PROMPT);
		assertEquals(SECRET_PROMPT.length() + " characters, 2 lines", sum);
		assertFalse(sum.contains("hunter2"));
	}

	@Test
	void threadIdOnlyFromOkObjects() {
		assertNull(ThreadLaunch.threadId(OperateResult.ok(JsonParser.parseString("{\"sequence\":1}"))));
		assertNull(ThreadLaunch.threadId(OperateResult.ok(null)));
		assertNull(ThreadLaunch.threadId(OperateResult.of(OperateResult.Outcome.FAILED, "timeout")));
	}

	@Test
	void screenStringsAreTranslated() throws Exception {
		for (String l : new String[] {"en_us", "es_es"}) {
			JsonObject o = OperateLangTest.lang(l);
			for (String k : new String[] {"title", "env", "project", "model", "model.default", "model.none", "prompt", "prompt.hint", "review",
					"button", "back", "summary", "summary.title", "summary.prompt", "summary.mode", "no_envs", "no_projects", "open", "sending",
					"portable"}) {
				assertTrue(o.has("t3craft.operate.launch." + k), l + " misses t3craft.operate.launch." + k);
			}
		}
		assertEquals("Lanzar", OperateLangTest.lang("es_es").get("t3craft.operate.launch.button").getAsString());
	}

	private static void assertNoPrompt(Path audit) throws Exception {
		String all = Files.readString(audit);
		assertFalse(all.contains("hunter2"), "prompt text must never reach the audit log");
		assertFalse(all.contains("Rotate the prod"), "nor the title derived from it");
	}
}
