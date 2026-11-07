package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Real {@code orchestration.subscribeShell} stream from my-vps (T3 0.1.8-nightly.20261107.4314),
 * captured read-only and anonymised (titles, message text, home paths removed): authoritative
 * snapshot → enrichment snapshot → synchronized. Regression for NPCs all showing "(no project)".
 */
class ShellCaptureTest {
	static JsonArray capture() throws Exception {
		try (InputStream in = ShellCaptureTest.class.getResourceAsStream("/fixtures/shell-capture-vps.json")) {
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
		}
	}

	static ShellState replay() throws Exception {
		ShellState s = new ShellState();
		for (JsonElement e : capture()) {
			s.apply(e.getAsJsonObject());
		}
		return s;
	}

	@Test
	void resolvesEveryThreadsProjectFromTheRealStream() throws Exception {
		ShellState s = replay();
		assertTrue(s.isSynchronized());
		assertEquals(6, s.threads().size());
		ShellState.View v = s.view();
		Set<String> labels = new HashSet<>();
		for (ThreadShell t : v.threads()) {
			assertNotNull(v.projectTitle(t.projectId()), "project of " + t.id() + " must resolve");
			NpcView n = Presentation.present("vps", "my-vps", t, v.projectTitle(t.projectId()));
			assertNotEquals("(no project)", n.groupLabel());
			labels.add(n.groupLabel());
		}
		// "No project" is T3's own scratch project (workspaceRoot ~/.t3/scratch): threads started
		// outside any registered project live there, so that title is the truth, not a lookup miss.
		assertEquals(Set.of("No project", "Orchestrator", "forja"), labels);
	}

	@Test
	void enrichmentInTheRealStreamKeepsProjectsAndThreads() throws Exception {
		JsonArray items = capture();
		ShellState s = new ShellState();
		s.apply(items.get(0).getAsJsonObject());
		String before = s.view().projectTitles().toString();
		s.apply(items.get(1).getAsJsonObject()); // enrichment (resolvedRepositoryIdentityRoots, threads: [])
		assertEquals(6, s.threads().size());
		assertEquals(before, s.view().projectTitles().toString());
	}

	@Test
	void enrichmentBeforeAnyAuthoritativeSnapshotIsAdopted() throws Exception {
		JsonArray items = capture();
		ShellState s = new ShellState();
		s.apply(items.get(1).getAsJsonObject());
		assertFalse(s.view().projectTitles().isEmpty(), "upstream adopts the refresh when it has nothing yet");
		s.apply(items.get(0).getAsJsonObject());
		assertEquals("forja", s.projectTitle("0721e4f3-0e30-470f-a214-2b7ed089663b"));
	}

	@Test
	void fallsBackToRepositoryNameThenWorkspaceFolder() throws Exception {
		JsonArray items = capture();
		JsonObject auth = items.get(0).getAsJsonObject().deepCopy();
		for (JsonElement p : auth.getAsJsonObject("snapshot").getAsJsonArray("projects")) {
			JsonObject o = p.getAsJsonObject();
			o.addProperty("title", "");
			if ("8e505253-b601-4608-b5ab-4c0b61aee952".equals(o.get("id").getAsString())) {
				o.add("repositoryIdentity", com.google.gson.JsonNull.INSTANCE);
			}
		}
		ShellState s = new ShellState();
		s.apply(auth);
		assertEquals("forja", s.projectTitle("0721e4f3-0e30-470f-a214-2b7ed089663b"), "repository name");
		assertEquals("orchestrator", s.projectTitle("8e505253-b601-4608-b5ab-4c0b61aee952"), "workspace folder");
		assertEquals("scratch", s.projectTitle("17f0e27e-ea53-4650-b26e-5761eefd85f6"), "workspace folder");
	}

	@Test
	void unknownProjectGetsShortIdNotNoProject() {
		assertEquals("project 7c1ab0bc", ShellState.projectLabel(null, "mcp:7c1ab0bc-8411-4180-be62-a326902f2861"));
		assertEquals("(no project)", ShellState.projectLabel(null, ""));
		assertEquals("forja", ShellState.projectLabel("forja", "x"));
		assertEquals("t3craft", ShellState.folderName("C:\\\\Users\\\\me\\\\t3craft\\\\"));
		assertEquals("orchestrator", ShellState.folderName("/home/user/projects/orchestrator/"));
	}
}
