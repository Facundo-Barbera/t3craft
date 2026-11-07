package dev.t3craft.client.schedule;

import com.google.gson.JsonObject;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ScheduledTask;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The default {@link ScheduleSource}: {@link T3Connection#subscribeScheduledTasks} on every
 * environment's existing connection (shared {@link StreamRegistry} path: one id generator, the
 * serialized send queue, refcount and resubscription after reconnects). Read-only.
 *
 * <p>The handle goes {@link Handle#stale() stale} when the controller's set of connections changes
 * (pairing, unpairing, {@code start()}), so {@code ScheduleClient} re-opens it.
 */
public final class ConnectionScheduleSource implements ScheduleSource {
	@Override
	public Handle open(DirectController controller, Sink sink) {
		Map<String, T3Connection> conns = connections(controller);
		List<StreamRegistry.Subscription> subs = new ArrayList<>();
		conns.forEach((envId, c) -> subs.add(c.subscribeScheduledTasks(listener(envId, sink))));
		return new Handle() {
			@Override
			public void close() {
				subs.forEach(StreamRegistry.Subscription::close);
				subs.clear();
			}

			@Override
			public boolean stale() {
				Map<String, T3Connection> now = connections(controller);
				if (!now.keySet().equals(conns.keySet())) {
					return true;
				}
				for (Map.Entry<String, T3Connection> e : conns.entrySet()) {
					if (now.get(e.getKey()) != e.getValue()) {
						return true;
					}
				}
				return false;
			}
		};
	}

	private static Map<String, T3Connection> connections(DirectController controller) {
		Map<String, T3Connection> out = new LinkedHashMap<>();
		for (DirectController.EnvStatus s : controller.statuses()) {
			T3Connection c = controller.connection(s.id());
			if (c != null) {
				out.put(s.id(), c);
			}
		}
		return out;
	}

	/**
	 * Stream items → sink. Each item is a full list, so only the newest decodable one in a batch is
	 * delivered; items that are not a list are ignored. Pure; unit-tested.
	 */
	static StreamRegistry.Listener listener(String envId, Sink sink) {
		return new StreamRegistry.Listener() {
			@Override
			public void onSubscribed() {
				// a fresh full list follows; keep showing the old one until then
			}

			@Override
			public void onItems(List<JsonObject> items) {
				List<ScheduledTask> latest = null;
				for (JsonObject item : items) {
					List<ScheduledTask> l = ScheduledTask.listFrom(item);
					if (l != null) {
						latest = l;
					}
				}
				if (latest != null) {
					sink.tasks(envId, latest);
				}
			}

			@Override
			public void onEnded(@Nullable String failure) {
				// null: the socket went away and the registry resubscribes on reconnect
				sink.unavailable(envId, failure == null ? "reconnecting" : T3Connection.sanitize(failure));
			}
		};
	}
}
