package dev.t3craft.client.schedule;

import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ScheduledTask;
import java.util.List;

/**
 * Where the board's data comes from: the read-only {@code scheduledTasks.subscribe} of every paired
 * environment. This module never opens a connection or sends a request itself. The default,
 * {@link ConnectionScheduleSource}, subscribes through each environment's existing
 * {@code T3Connection} and its shared {@code StreamRegistry}.
 */
@FunctionalInterface
public interface ScheduleSource {
	/** Receives updates, possibly on connection threads (implementations must be thread-safe). */
	interface Sink {
		/** An environment's full task list after every change. */
		void tasks(String envId, List<ScheduledTask> tasks);

		/** The stream is not available for this environment (sanitized reason). */
		void unavailable(String envId, String reason);
	}

	/** An open subscription; {@link #close()} stops every stream it started. */
	interface Handle {
		Handle NONE = () -> {
		};

		void close();

		/** True when the subscribed connections no longer match the controller (re-open). Client thread. */
		default boolean stale() {
			return false;
		}
	}

	/** Starts streaming every environment of {@code controller} into {@code sink}. */
	Handle open(DirectController controller, Sink sink);
}
