package dev.t3craft.client.schedule;

import dev.t3craft.t3.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Latest scheduled tasks per environment, fed by a {@link ScheduleSource} (possibly on connection
 * threads) and read on the client thread. Thread-safe; pure Java (unit-tested). Read-only by design:
 * it only stores what T3 sends.
 */
public final class ScheduleBook implements ScheduleSource.Sink {
	private final Map<String, List<ScheduledTask>> tasks = new ConcurrentHashMap<>();
	private final Map<String, String> unavailable = new ConcurrentHashMap<>();

	@Override
	public void tasks(String envId, List<ScheduledTask> list) {
		tasks.put(envId, List.copyOf(list));
		unavailable.remove(envId);
	}

	@Override
	public void unavailable(String envId, String reason) {
		unavailable.put(envId, reason);
	}

	/** Environments that sent a list at least once. */
	public Set<String> environments() {
		return Set.copyOf(tasks.keySet());
	}

	/** Why an environment has no stream right now (sanitized), or null. */
	public @Nullable String unavailable(String envId) {
		return unavailable.get(envId);
	}

	public Map<String, String> unavailable() {
		return Map.copyOf(unavailable);
	}

	/** Forgets environments that are no longer configured. */
	public void retain(Set<String> envIds) {
		tasks.keySet().retainAll(envIds);
		unavailable.keySet().retainAll(envIds);
	}

	public void clear() {
		tasks.clear();
		unavailable.clear();
	}

	/**
	 * Every task of every environment, in {@link ScheduleEntry#ORDER}. {@code envLabel(envId)} and
	 * {@code projectTitle(envId, projectId)} may return null (falls back to the ids).
	 */
	public List<ScheduleEntry> entries(Function<String, @Nullable String> envLabel, BiFunction<String, String, @Nullable String> projectTitle) {
		List<ScheduleEntry> out = new ArrayList<>();
		tasks.forEach((env, list) -> {
			String label = envLabel.apply(env);
			for (ScheduledTask t : list) {
				String project = t.projectId().isEmpty() ? null : projectTitle.apply(env, t.projectId());
				out.add(new ScheduleEntry(env, label == null ? env : label, project == null ? t.projectId() : project, t));
			}
		});
		out.sort(ScheduleEntry.ORDER);
		return out;
	}
}
