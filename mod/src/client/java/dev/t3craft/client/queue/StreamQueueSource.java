package dev.t3craft.client.queue;

import com.google.gson.JsonObject;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Thin adapter: {@link QueueSource} over the shared thread streams of the environment's
 * {@link T3Connection} ({@link T3Connection#subscribeThread}, refcounted by its
 * {@link StreamRegistry}). No socket or request path of its own: a queue view and a chat open on
 * the same thread share one server subscription. Read-only ({@code orchestration:read}).
 */
public final class StreamQueueSource implements QueueSource {
	private final Supplier<@Nullable DirectController> direct;

	public StreamQueueSource(Supplier<@Nullable DirectController> direct) {
		this.direct = direct;
	}

	@Override
	public @Nullable Handle open(String envId, String threadId, Listener l) {
		DirectController d = direct.get();
		T3Connection c = d == null ? null : d.connection(envId);
		if (c == null) {
			return null;
		}
		return bridge(l, sl -> c.subscribeThread(threadId, sl));
	}

	/** Forwards a {@link StreamRegistry} subscription to a queue {@link Listener} (shapes are the same). */
	static Handle bridge(Listener l, java.util.function.Function<StreamRegistry.Listener, StreamRegistry.Subscription> subscribe) {
		StreamRegistry.Subscription s = subscribe.apply(new StreamRegistry.Listener() {
			@Override
			public void onSubscribed() {
				l.onSubscribed();
			}

			@Override
			public void onItems(List<JsonObject> items) {
				l.onItems(items);
			}

			@Override
			public void onEnded(@Nullable String failure) {
				l.onEnded(failure);
			}
		});
		return s::close;
	}

	@Override
	public String unavailableReason() {
		return "no direct connection to that environment (bridge mode or not paired)";
	}
}
