package dev.t3craft.client.schedule;

import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ScheduledTask;
import java.util.List;

/**
 * Where the board's data comes from: the read-only {@code scheduledTasks.subscribe} of every paired
 * environment ({@link dev.t3craft.t3.RpcFrames#SUBSCRIBE_SCHEDULED_TASKS}). This module never opens a
 * connection or sends a request itself; an adapter puts the stream on each environment's existing
 * {@code T3Connection} through its shared request path.
 *
 * <p><b>Adapter status.</b> The shared path ({@code StreamRegistry}: {@code streams.subscribe(...)}
 * with refcount, one id generator and a serialized {@code send()} per socket) arrives with
 * feat/chat. Until it is on main the default is {@link #PENDING}, which delivers nothing and reports
 * every environment as waiting. The real adapter is a few lines: per environment,
 * {@code streams.subscribe(SUBSCRIBE_SCHEDULED_TASKS, {}, value -> sink.tasks(envId, ScheduledTask.listFrom(value)))},
 * closing the subscriptions in {@link Handle#close()}, registered with
 * {@code ScheduleClient.get().setSource(...)}.
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
	@FunctionalInterface
	interface Handle {
		Handle NONE = () -> {
		};

		void close();
	}

	/** Starts streaming every environment of {@code controller} into {@code sink}. */
	Handle open(DirectController controller, Sink sink);

	/** Reason shown while no adapter is wired. */
	String PENDING_REASON = "waiting for the shared stream path (feat/chat)";

	/** No adapter yet: every configured environment reports {@link #PENDING_REASON}, nothing is sent to T3. */
	ScheduleSource PENDING = (controller, sink) -> {
		controller.statuses().forEach(s -> sink.unavailable(s.id(), PENDING_REASON));
		return Handle.NONE;
	};
}
