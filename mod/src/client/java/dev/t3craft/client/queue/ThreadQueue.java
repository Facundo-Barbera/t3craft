package dev.t3craft.client.queue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The queue of one thread, rebuilt from the items of its {@code orchestration.subscribeThread}
 * stream (snapshot → {@code projection.runs}/{@code projection.messages}; events
 * {@code run.created}/{@code run.updated} and {@code message.*}). Pure, tolerant: unknown items
 * and malformed runs are ignored. Not thread-safe (the {@link QueueStore} synchronizes).
 */
public final class ThreadQueue {
	private final String envId;
	private final String threadId;
	private final Map<String, QueuedRun> runs = new LinkedHashMap<>();
	/** userMessageId → (delegated completion?, movable?, preview). */
	private final Map<String, Msg> messages = new HashMap<>();
	private boolean loaded;

	private record Msg(boolean automatic, boolean movable, @Nullable String preview) {
	}

	public ThreadQueue(String envId, String threadId) {
		this.envId = envId;
		this.threadId = threadId;
	}

	public String envId() {
		return envId;
	}

	public String threadId() {
		return threadId;
	}

	/** True once a snapshot arrived (before that the queue is unknown, not empty). */
	public boolean loaded() {
		return loaded;
	}

	/** A (re)subscription started: forget everything until the next snapshot. */
	public void reset() {
		runs.clear();
		messages.clear();
		loaded = false;
	}

	/** Applies stream items in order; returns whether the queue may have changed. */
	public boolean apply(List<JsonObject> items) {
		boolean changed = false;
		for (JsonObject item : items) {
			changed |= apply(item);
		}
		return changed;
	}

	public boolean apply(@Nullable JsonObject item) {
		String kind = Json.str(item, "kind");
		if (kind == null) {
			return false;
		}
		switch (kind) {
			case "snapshot" -> {
				JsonObject p = Json.obj(item, "projection");
				if (p == null) {
					JsonObject snap = Json.obj(item, "snapshot"); // tolerate a wrapped detail snapshot
					p = Json.obj(snap, "projection");
				}
				runs.clear();
				messages.clear();
				putMessages(Json.arr(p, "messages"));
				JsonArray rs = Json.arr(p, "runs");
				if (rs != null) {
					for (JsonElement e : rs) {
						putRun(e.isJsonObject() ? e.getAsJsonObject() : null);
					}
				}
				loaded = true;
				return true;
			}
			case "event" -> {
				JsonObject ev = Json.obj(item, "event");
				String type = Json.str(ev, "type");
				if (type == null) {
					return false;
				}
				if (type.equals("run.created") || type.equals("run.updated")) {
					return putRun(Json.obj(ev, "payload"));
				}
				if (type.startsWith("message.")) {
					JsonObject m = Json.obj(ev, "payload");
					if (m != null && Json.obj(m, "message") != null) {
						m = Json.obj(m, "message");
					}
					return putMessage(m);
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	private boolean putRun(@Nullable JsonObject o) {
		QueuedRun r = QueuedRun.fromJson(envId, threadId, o);
		if (r == null || !r.threadId().equals(threadId)) {
			return false;
		}
		runs.put(r.id(), r);
		return true;
	}

	private void putMessages(@Nullable JsonArray ms) {
		if (ms == null) {
			return;
		}
		for (JsonElement e : ms) {
			putMessage(e.isJsonObject() ? e.getAsJsonObject() : null);
		}
	}

	private boolean putMessage(@Nullable JsonObject m) {
		String id = Json.str(m, "id");
		if (id == null) {
			return false;
		}
		String role = Json.str(m, "role");
		if (role != null && !role.equals("user")) {
			return false;
		}
		// Same rules as T3's QueuedRunOrder / dispatchQueuedRunReorder.
		boolean automatic = Json.obj(m, "delegatedCompletion") != null;
		boolean movable = !automatic && Json.obj(m, "notification") == null;
		messages.put(id, new Msg(automatic, movable, QueuedRun.preview(Json.str(m, "text"))));
		return true;
	}

	/** Queued runs in T3's delivery order ({@link QueueOrder#sort}). Empty until loaded. */
	public List<QueuedRun> queued() {
		List<QueuedRun> out = new ArrayList<>();
		for (QueuedRun r : runs.values()) {
			if (!r.queued()) {
				continue;
			}
			Msg m = r.userMessageId() == null ? null : messages.get(r.userMessageId());
			out.add(m == null ? r : r.withMessage(m.automatic(), m.movable(), m.preview()));
		}
		return QueueOrder.sort(out);
	}
}
