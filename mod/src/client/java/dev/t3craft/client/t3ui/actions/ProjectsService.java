package dev.t3craft.client.t3ui.actions;

import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadLaunch;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Projects of every connected environment, read-only from the shell views
 * ({@code orchestration.subscribeShell}), for the sidebar's project filter (feat/t3-ui-unified) and
 * the dialogs of this package. Like T3 Code's sidebar it lists every project of every environment,
 * with how many threads it has and how many are active. Pure: the views and labels are suppliers,
 * production passes {@code DirectController::shellViews} ({@link ActionsClient#projects()}).
 */
public final class ProjectsService {
	/** A project of one environment. {@link #key()} is unique across environments. */
	public record Project(String envId, String envLabel, String projectId, String label, int threads, int active,
			ThreadLaunch.@Nullable ModelChoice defaultModel) {
		public Key key() {
			return new Key(envId, projectId);
		}
	}

	/** Identifies a project across environments (the filter value). */
	public record Key(String envId, String projectId) {
		/** {@code env:projectId}. */
		public String wire() {
			return envId + ":" + projectId;
		}

		public static @Nullable Key parse(@Nullable String wire) {
			if (wire == null) {
				return null;
			}
			int i = wire.indexOf(':');
			return i <= 0 || i == wire.length() - 1 ? null : new Key(wire.substring(0, i), wire.substring(i + 1));
		}
	}

	private final Supplier<Map<String, ShellState.View>> views;
	private final Function<String, String> envLabels;

	public ProjectsService(Supplier<Map<String, ShellState.View>> views, Function<String, String> envLabels) {
		this.views = views;
		this.envLabels = envLabels;
	}

	/** All projects, sorted by environment label then project label. */
	public List<Project> all() {
		List<Project> out = new ArrayList<>();
		views.get().forEach((env, view) -> out.addAll(of(env, view)));
		out.sort(Comparator.comparing((Project p) -> p.envLabel().toLowerCase(Locale.ROOT)).thenComparing(Project::envId)
				.thenComparing(p -> p.label().toLowerCase(Locale.ROOT)).thenComparing(Project::projectId));
		return out;
	}

	/** Projects of one environment, sorted by label; empty when it is not connected. */
	public List<Project> byEnv(String envId) {
		ShellState.View v = views.get().get(envId);
		return v == null ? List.of() : of(envId, v);
	}

	public @Nullable Project find(Key key) {
		return byEnv(key.envId()).stream().filter(p -> p.projectId().equals(key.projectId())).findFirst().orElse(null);
	}

	/** True when {@code filter} is null (all projects) or the thread belongs to that project. */
	public static boolean matches(@Nullable Key filter, String envId, ThreadShell t) {
		return filter == null || (filter.envId().equals(envId) && filter.projectId().equals(t.projectId()));
	}

	private List<Project> of(String env, ShellState.View view) {
		String envLabel = envLabels.apply(env);
		List<Project> out = new ArrayList<>();
		for (ThreadLaunch.ProjectChoice c : ThreadLaunch.projects(view)) {
			int threads = 0;
			int active = 0;
			for (ThreadShell t : view.threads()) {
				if (t.projectId().equals(c.id()) && t.parentThreadId() == null) {
					threads++;
					if (ThreadStatus.of(t) != ThreadStatus.IDLE) {
						active++;
					}
				}
			}
			out.add(new Project(env, envLabel == null ? env : envLabel, c.id(), c.label(), threads, active, c.defaultModel()));
		}
		return out;
	}
}
