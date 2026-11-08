package dev.t3craft.client.t3ui.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadLaunch;
import dev.t3craft.t3.ThreadShell;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Dialog validation, the status chip mapping and the projects service. */
class DialogsStatusProjectsTest {
	static ThreadShell thread(String json) {
		return ThreadShell.fromJson(JsonParser.parseString(json).getAsJsonObject());
	}

	static NewThreadForm validForm() {
		NewThreadForm f = new NewThreadForm();
		f.envId = "vps";
		f.projectId = "p1";
		f.prompt = "do it";
		f.pick = new ModelPick("codex", "gpt-5", null, null, Modes.Runtime.DEFAULT, Modes.Interaction.DEFAULT);
		return f;
	}

	@Test
	void newThreadValidation() {
		assertEquals(List.of(), validForm().errors(true, null));
		NewThreadForm f = new NewThreadForm();
		assertEquals(List.of("Choose an environment.", "Choose a project.", "Choose a model.", "Write a prompt first."), f.errors(true, null));
		f = validForm();
		f.pick = null;
		assertEquals(List.of("Choose a model."), f.errors(true, null));
		assertEquals(List.of(), f.errors(true, new ThreadLaunch.ModelChoice("codex", "gpt-5", null)), "the project default counts");
		f = validForm();
		f.workspace = NewThreadForm.Workspace.WORKTREE;
		assertEquals(List.of("Write the base branch for the worktree."), f.errors(true, null));
		f.baseRef = "bad ref";
		assertEquals(List.of("The base branch is not a valid git ref."), f.errors(true, null));
		f.baseRef = "origin/main";
		f.branch = "-oops";
		assertEquals(List.of("The branch name is not a valid git ref."), f.errors(true, null));
		f.branch = "feat/t3-actions";
		assertEquals(List.of(), f.errors(true, null));
		f.prompt = "x".repeat(ThreadLaunch.MAX_PROMPT + 1);
		assertTrue(f.errors(true, null).getFirst().startsWith("The prompt is too long"));
		assertNotNull(f.build(true, null, ThreadLaunch.Ids::random).error());
	}

	@Test
	void gitRefRules() {
		for (String ok : List.of("main", "origin/main", "feat/t3-actions", "v1.2", "release-2026")) {
			assertTrue(NewThreadForm.validRef(ok), ok);
		}
		for (String bad : List.of("", "a b", "a..b", "-x", "x.lock", "x/", "x.", "a~1", "a^", "a:b", "a?", "a*", "a[", "a\\b", "a//b")) {
			assertFalse(NewThreadForm.validRef(bad), bad);
		}
	}

	@Test
	void registerProjectValidation() {
		RegisterProjectForm f = new RegisterProjectForm();
		assertEquals(List.of("Choose an environment.", "Write the workspace path."), f.errors(true, List.of()));
		f.envId = "vps";
		f.workspaceRoot = "relative/dir";
		assertEquals(List.of("The workspace path must be absolute (/home/me/project, ~/project or C:\\project)."), f.errors(true, List.of()));
		for (String ok : List.of("/srv/app", "~/code/app", "C:\\work\\app", "D:/work/app", "\\\\host\\share")) {
			f.workspaceRoot = ok;
			assertEquals(List.of(), f.errors(true, List.of()), ok);
		}
		f.workspaceRoot = "/srv/app/";
		assertEquals(List.of("That folder is already a project in this environment."), f.errors(true, List.of("/srv/app")));
		f.title = "n".repeat(RegisterProjectForm.MAX_TITLE + 1);
		assertTrue(f.errors(true, List.of()).getFirst().startsWith("The name is too long"));
		f.title = "  My App ";
		assertEquals("My App", f.effectiveTitle());
		f.title = "";
		assertEquals("app", f.effectiveTitle());
		assertEquals("project", RegisterProjectForm.inferTitle("C:\\work\\project\\"));
		assertEquals("/", RegisterProjectForm.normalizePath("/"));
		assertEquals("C:\\", RegisterProjectForm.normalizePath("C:\\"));
	}

	@Test
	void statusMatchesT3SidebarPill() {
		assertEquals(ThreadStatus.PENDING_APPROVAL, ThreadStatus.of(thread("""
				{"id":"t","status":"running","pendingRuntimeRequest":{"id":"r","kind":"command"}}""")));
		assertEquals(ThreadStatus.AWAITING_INPUT, ThreadStatus.of(thread("""
				{"id":"t","status":"waiting","pendingRuntimeRequest":{"id":"r","kind":"user_input","blocking":true}}""")));
		assertEquals(ThreadStatus.WORKING, ThreadStatus.of(thread("""
				{"id":"t","status":"running","pendingRuntimeRequest":{"id":"r","kind":"user_input","blocking":false}}""")),
				"non-blocking questions do not stop the pill");
		assertEquals(ThreadStatus.GUEST_REQUEST, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"idle\",\"pendingGuestMessageCount\":2}")));
		assertEquals(ThreadStatus.WORKING, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"waiting\"}")));
		assertEquals(ThreadStatus.CONNECTING, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"queued\"}")));
		assertEquals(ThreadStatus.WAITING, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"completed\",\"pendingBackgroundTasks\":[{}]}")));
		assertEquals(ThreadStatus.FAILED, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"failed\"}")));
		assertEquals(ThreadStatus.PLAN_READY, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"completed\",\"hasActionableProposedPlan\":true}")));
		assertEquals(ThreadStatus.COMPLETED, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"completed\"}")));
		assertEquals(ThreadStatus.IDLE, ThreadStatus.of(thread("{\"id\":\"t\",\"status\":\"interrupted\"}")));
		assertTrue(ThreadStatus.WORKING.pulse);
		assertTrue(ThreadStatus.PENDING_APPROVAL.priority > ThreadStatus.FAILED.priority);
		assertTrue(ThreadStatus.WORKING.running());
		assertFalse(ThreadStatus.COMPLETED.running());
	}

	@Test
	void projectsServiceListsEveryEnvironment() {
		JsonObject sel = JsonParser.parseString("{\"instanceId\":\"codex\",\"model\":\"gpt-5\"}").getAsJsonObject();
		ShellState.View vps = new ShellState.View(List.of(
				thread("{\"id\":\"a\",\"projectId\":\"p1\",\"status\":\"running\"}"),
				thread("{\"id\":\"b\",\"projectId\":\"p1\",\"status\":\"idle\"}"),
				thread("{\"id\":\"c\",\"projectId\":\"p1\",\"status\":\"running\",\"lineage\":{\"parentThreadId\":\"a\"}}"),
				thread("{\"id\":\"d\",\"projectId\":\"p2\",\"status\":\"completed\"}")),
				Map.of("p1", "Zeta", "p2", "alpha"), true, Map.of("p1", sel));
		ShellState.View laptop = new ShellState.View(List.of(), Map.of("p9", "Notes"), true);
		Map<String, ShellState.View> views = new LinkedHashMap<>();
		views.put("vps", vps);
		views.put("laptop", laptop);
		ProjectsService s = new ProjectsService(() -> views, env -> env.equals("vps") ? "VPS" : "Laptop");
		List<ProjectsService.Project> all = s.all();
		assertEquals(List.of("laptop:p9", "vps:p2", "vps:p1"), all.stream().map(p -> p.key().wire()).toList());
		ProjectsService.Project zeta = s.find(new ProjectsService.Key("vps", "p1"));
		assertEquals(2, zeta.threads(), "subagents are not counted");
		assertEquals(1, zeta.active());
		assertEquals("gpt-5", zeta.defaultModel().model());
		assertEquals(List.of(), s.byEnv("missing"));
		assertEquals(new ProjectsService.Key("vps", "p1"), ProjectsService.Key.parse("vps:p1"));
		assertNull(ProjectsService.Key.parse("nocolon"));
		ThreadShell a = vps.threads().getFirst();
		assertTrue(ProjectsService.matches(null, "vps", a));
		assertTrue(ProjectsService.matches(new ProjectsService.Key("vps", "p1"), "vps", a));
		assertFalse(ProjectsService.matches(new ProjectsService.Key("laptop", "p1"), "vps", a));
		assertFalse(ProjectsService.matches(new ProjectsService.Key("vps", "p2"), "vps", a));
	}
}
