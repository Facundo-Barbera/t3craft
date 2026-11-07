package dev.t3craft.client.queue;

import com.google.gson.JsonObject;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where the queue view reads a thread's runs from. The shell ({@code subscribeShell}) does not
 * carry runs, so this is the thread stream ({@code orchestration.subscribeThread}). The queue
 * package does <b>not</b> open requests of its own: the stream comes from the connection's shared
 * thread streams (feat/chat's {@code StreamRegistry}) through a thin adapter installed in
 * {@link QueueClient}; the {@link Listener} has the same shape as {@code StreamRegistry.Listener}
 * so the adapter is a one-liner. Until that is wired, {@link #UNAVAILABLE} reports the queue as
 * unknown (never as empty).
 */
public interface QueueSource {
	/** Same contract as {@code StreamRegistry.Listener}; called on a connection thread. */
	interface Listener {
		void onSubscribed();

		void onItems(List<JsonObject> items);

		/** {@code failure} null = paused (reconnects), else the stream ended with that reason. */
		void onEnded(@Nullable String failure);
	}

	/** One open stream; {@link #close()} is idempotent. */
	interface Handle extends AutoCloseable {
		@Override
		void close();
	}

	/**
	 * Opens the thread stream of {@code threadId} in {@code envId}, or returns null when this
	 * environment cannot provide it (not connected, bridge mode, no thread streams yet).
	 */
	@Nullable Handle open(String envId, String threadId, Listener listener);

	/** Short reason shown when {@link #open} returns null. */
	default String unavailableReason() {
		return "thread streams are not available";
	}

	QueueSource UNAVAILABLE = new QueueSource() {
		@Override
		public @Nullable Handle open(String envId, String threadId, Listener listener) {
			return null;
		}

		@Override
		public String unavailableReason() {
			return "this build cannot read thread runs yet (needs the shared thread streams)";
		}
	};
}
