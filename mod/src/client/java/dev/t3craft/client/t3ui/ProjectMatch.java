package dev.t3craft.client.t3ui;

import dev.t3craft.client.t3ui.actions.ProjectsService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Maps the sidebar's logical project (a folded name, one folder across environments) to a real T3
 * project for "New thread": the one with that name on {@code preferEnv} when there is one, else the
 * first with that name. Pure (tested).
 */
final class ProjectMatch {
	private ProjectMatch() {
	}

	static ProjectsService.@Nullable Project find(List<ProjectsService.Project> projects, @Nullable String projectKey, @Nullable String preferEnv) {
		if (projectKey == null || projectKey.isEmpty()) {
			return null;
		}
		ProjectsService.Project any = null;
		for (ProjectsService.Project p : projects) {
			if (!SidebarModel.fold(p.label()).trim().equals(projectKey)) {
				continue;
			}
			if (preferEnv != null && preferEnv.equals(p.envId())) {
				return p;
			}
			if (any == null) {
				any = p;
			}
		}
		return any;
	}
}
