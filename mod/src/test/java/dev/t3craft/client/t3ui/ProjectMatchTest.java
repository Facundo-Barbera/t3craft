package dev.t3craft.client.t3ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.t3craft.client.t3ui.actions.ProjectsService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectMatchTest {
	static ProjectsService.Project p(String env, String id, String label) {
		return new ProjectsService.Project(env, env.toUpperCase(), id, label, 0, 0, null);
	}

	@Test
	void newThreadPicksTheSidebarProjectOnTheSelectedEnvironment() {
		List<ProjectsService.Project> all = List.of(p("lap", "p1", "T3Craft"), p("vps", "p2", "t3craft"), p("vps", "p3", "Website"));
		assertEquals("p2", ProjectMatch.find(all, "t3craft", "vps").projectId(), "same name on the selected environment");
		assertEquals("p1", ProjectMatch.find(all, "t3craft", "other").projectId(), "else the first with that name");
		assertEquals("p1", ProjectMatch.find(all, "t3craft", null).projectId());
		assertEquals("p3", ProjectMatch.find(all, SidebarModel.fold("Wébsite"), "lap").projectId(), "folded like the sidebar keys");
		assertNull(ProjectMatch.find(all, "missing", "vps"));
		assertNull(ProjectMatch.find(all, SidebarModel.NO_PROJECT, "vps"), "\"No project\" maps to none");
		assertNull(ProjectMatch.find(all, null, "vps"));
	}
}
