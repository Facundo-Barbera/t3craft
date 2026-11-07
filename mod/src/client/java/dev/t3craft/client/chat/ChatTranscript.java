package dev.t3craft.client.chat;

import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The read-only transcript of one T3 thread, fed by {@code orchestration.subscribeThread} stream
 * items (bounded snapshot, then live events) and by older history pages
 * ({@code GET /api/orchestration/threads/:id/history?cursor=}). Pure Java and thread-safe: the feed
 * writes on its own thread, the screen reads an immutable {@link View}.
 *
 * <p>Ordering follows T3: snapshot rows in {@code visibleTurnItems} order, older pages prepended
 * (each page oldest first), new items appended in ordinal order. Rows are keyed by
 * {@code sourceThreadId:itemId}, so live upserts (streaming text, tool output) replace in place.
 */
public final class ChatTranscript {
	/** Thread status line: state, a short reason, and who is waiting on whom (null when nobody). */
	public record Status(ThreadState state, String detail, @Nullable String needs) {
	}

	public record View(
			long version,
			String threadId,
			String title,
			List<ChatEntry> entries,
			boolean synced,
			boolean hasMoreHistory,
			@Nullable String historyCursor,
			Status status) {
	}

	private record Run(String id, long ordinal, String status) {
	}

	private record Request(String id, String nodeId, String kind, String status, @Nullable Long createdAt) {
	}

	private static final Set<String> RUN_ACTIVE = Set.of("preparing", "queued", "starting", "running");

	private final String threadId;
	private final List<ChatEntry> entries = new ArrayList<>();
	private final Map<String, Run> runs = new LinkedHashMap<>();
	private final Map<String, Request> requests = new LinkedHashMap<>();
	/** nodeId → readable name for subagent nodes ("who needs it"). */
	private final Map<String, String> subagents = new LinkedHashMap<>();
	private String title = "";
	private boolean synced;
	private boolean hasMoreHistory;
	private @Nullable String historyCursor;
	private long sequence = -1;
	private long version;
	private @Nullable View cached;

	public ChatTranscript(String threadId) {
		this.threadId = threadId;
	}

	public String threadId() {
		return threadId;
	}

	/** Last applied stream sequence (snapshotSequence or event sequence), -1 before the snapshot. */
	public synchronized long sequence() {
		return sequence;
	}

	public synchronized boolean hasSnapshot() {
		return sequence >= 0;
	}

	/**
	 * Applies one {@code OrchestrationV2ThreadStreamItem}. Returns true when something visible
	 * changed.
	 */
	public synchronized boolean applyStreamItem(@Nullable JsonObject item) {
		if (item == null) {
			return false;
		}
		boolean changed = switch (String.valueOf(Json.str(item, "kind"))) {
			case "synchronized" -> {
				boolean was = synced;
				synced = true;
				yield !was;
			}
			case "snapshot" -> applySnapshot(item);
			case "event" -> applyEvent(item);
			case "unknown-event" -> {
				sequence = Math.max(sequence, Json.num(item, "sequence", sequence));
				yield false;
			}
			default -> false;
		};
		if (changed) {
			touch();
		}
		return changed;
	}

	private boolean applySnapshot(JsonObject item) {
		JsonObject projection = Json.obj(item, "projection");
		if (projection == null) {
			return false;
		}
		sequence = Json.num(item, "snapshotSequence", 0);
		historyCursor = Json.str(item, "historyCursor");
		hasMoreHistory = Json.bool(item, "hasMoreHistory") && historyCursor != null;
		entries.clear();
		runs.clear();
		requests.clear();
		subagents.clear();
		applyThread(Json.obj(projection, "thread"));
		TurnItems.objects(Json.arr(projection, "runs")).forEach(this::applyRun);
		TurnItems.objects(Json.arr(projection, "runtimeRequests")).forEach(this::applyRequest);
		TurnItems.objects(Json.arr(projection, "subagents")).forEach(this::applySubagent);
		Map<String, ChatEntry> seen = new LinkedHashMap<>();
		var rows = Json.arr(projection, "visibleTurnItems");
		if (rows != null) {
			for (JsonObject row : TurnItems.objects(rows)) {
				ChatEntry e = TurnItems.decodeRow(row, threadId);
				if (e != null) {
					seen.putIfAbsent(e.key(), e);
				}
			}
		} else {
			// Older servers: fall back to the thread's own turn items.
			for (JsonObject ti : TurnItems.objects(Json.arr(projection, "turnItems"))) {
				ChatEntry e = TurnItems.decode(ti, threadId, false);
				if (e != null) {
					seen.putIfAbsent(e.key(), e);
				}
			}
		}
		entries.addAll(seen.values());
		return true;
	}

	private boolean applyEvent(JsonObject item) {
		long seq = Json.num(item, "sequence", -1);
		if (seq >= 0 && seq <= sequence) {
			return false; // replayed event already in the snapshot
		}
		if (seq >= 0) {
			sequence = seq;
		}
		JsonObject event = Json.obj(item, "event");
		String type = String.valueOf(Json.str(event, "type"));
		JsonObject payload = Json.obj(event, "payload");
		if (payload == null) {
			return false;
		}
		if (type.startsWith("thread.")) {
			return applyThread(payload);
		}
		return switch (type) {
			case "turn-item.updated" -> upsert(TurnItems.decode(payload, threadId, false));
			case "message.updated" -> applyMessage(payload);
			case "run.created", "run.updated" -> applyRun(payload);
			case "runtime-request.updated" -> applyRequest(payload);
			case "subagent.updated" -> applySubagent(payload);
			default -> false;
		};
	}

	/** Prepends an older history page ({@code OrchestrationV2ThreadHistoryPage}). */
	public synchronized boolean applyHistoryPage(@Nullable JsonObject page, String requestedCursor) {
		if (page == null || !requestedCursor.equals(historyCursor)) {
			return false; // stale: a new snapshot replaced the window meanwhile
		}
		Map<String, ChatEntry> current = new LinkedHashMap<>();
		entries.forEach(e -> current.put(e.key(), e));
		List<ChatEntry> older = new ArrayList<>();
		for (JsonObject row : TurnItems.objects(Json.arr(page, "items"))) {
			ChatEntry e = TurnItems.decodeRow(row, threadId);
			if (e != null && !current.containsKey(e.key())) {
				current.put(e.key(), e);
				older.add(e);
			}
		}
		historyCursor = Json.str(page, "nextCursor");
		hasMoreHistory = Json.bool(page, "hasMoreHistory") && historyCursor != null;
		entries.addAll(0, older);
		touch();
		return true;
	}

	private boolean upsert(@Nullable ChatEntry e) {
		if (e == null) {
			return false;
		}
		for (int i = entries.size() - 1; i >= 0; i--) {
			if (entries.get(i).key().equals(e.key())) {
				if (entries.get(i).equals(e)) {
					return false;
				}
				entries.set(i, e);
				return true;
			}
		}
		// New local item: after the last local row with a lower ordinal (normally the end).
		int at = entries.size();
		while (at > 0) {
			ChatEntry prev = entries.get(at - 1);
			if (prev.inherited() || prev.ordinal() <= e.ordinal()) {
				break;
			}
			at--;
		}
		entries.add(at, e);
		return true;
	}

	private boolean applyMessage(JsonObject m) {
		String id = Json.str(m, "id");
		if (id == null) {
			return false;
		}
		String text = Json.str(m, "text");
		boolean streaming = Json.bool(m, "streaming");
		for (int i = entries.size() - 1; i >= 0; i--) {
			ChatEntry e = entries.get(i);
			if (id.equals(e.messageId())) {
				if (text == null || (text.equals(e.body()) && streaming == e.streaming())) {
					return false;
				}
				entries.set(i, e.withText(TurnItems.clip(text, TurnItems.MAX_BODY_CHARS), streaming));
				return true;
			}
		}
		return false; // its turn item arrives with turn-item.updated
	}

	private boolean applyThread(@Nullable JsonObject t) {
		String tt = Json.str(t, "title");
		if (tt == null || tt.equals(title)) {
			return false;
		}
		title = tt;
		return true;
	}

	private boolean applyRun(JsonObject r) {
		String id = Json.str(r, "id");
		if (id == null) {
			return false;
		}
		Run run = new Run(id, Json.num(r, "ordinal", 0), String.valueOf(Json.str(r, "status")));
		return !run.equals(runs.put(id, run));
	}

	private boolean applyRequest(JsonObject r) {
		String id = Json.str(r, "id");
		if (id == null) {
			return false;
		}
		Request req = new Request(id, String.valueOf(Json.str(r, "nodeId")), String.valueOf(Json.str(r, "kind")),
				String.valueOf(Json.str(r, "status")), Json.time(r, "createdAt"));
		return !req.equals(requests.put(id, req));
	}

	private boolean applySubagent(JsonObject s) {
		String id = Json.str(s, "id");
		if (id == null) {
			return false;
		}
		String name = Json.str(s, "title");
		if (name == null || name.isBlank()) {
			name = TurnItems.firstLine(String.valueOf(Json.str(s, "prompt")));
		}
		return !name.equals(subagents.put(id, name));
	}

	private void touch() {
		version++;
		cached = null;
	}

	/** Immutable copy for the render thread; cached until the next change. */
	public synchronized View view() {
		if (cached == null) {
			cached = new View(version, threadId, title, List.copyOf(entries), synced, hasMoreHistory, historyCursor, status(System.currentTimeMillis()));
		}
		return cached;
	}

	public synchronized @Nullable String historyCursor() {
		return hasMoreHistory ? historyCursor : null;
	}

	Status status(long nowMs) {
		List<Request> pending = requests.values().stream().filter(r -> "pending".equals(r.status()))
				.sorted(Comparator.comparing(r -> r.createdAt() == null ? 0L : r.createdAt())).toList();
		if (!pending.isEmpty()) {
			Request r = pending.getFirst();
			String what = switch (r.kind()) {
				case "user_input" -> "answer a question";
				case "auth_refresh" -> "sign in again";
				case "dynamic_tool_call" -> "approve a tool call";
				default -> "approve " + r.kind();
			};
			String sub = subagents.get(r.nodeId());
			String who = sub != null ? "subagent \"" + sub + "\"" : "this thread's agent";
			String ago = r.createdAt() == null ? "" : " · " + ago(nowMs - r.createdAt());
			String more = pending.size() > 1 ? " (+" + (pending.size() - 1) + " more)" : "";
			return new Status(ThreadState.NEEDS_ATTENTION, "waiting for you",
					"You: " + what + ", asked by " + who + ago + more);
		}
		Run latest = runs.values().stream().max(Comparator.comparingLong(Run::ordinal)).orElse(null);
		if (latest == null) {
			return new Status(ThreadState.IDLE, "idle", null);
		}
		String s = latest.status();
		if (RUN_ACTIVE.contains(s)) {
			return new Status(ThreadState.WORKING, s, null);
		}
		return switch (s) {
			case "waiting" -> new Status(ThreadState.NEEDS_ATTENTION, "waiting", "You: the agent is waiting for input");
			case "failed" -> new Status(ThreadState.ERROR, "failed", null);
			case "completed" -> new Status(ThreadState.DONE, "completed", null);
			default -> new Status(ThreadState.IDLE, s.replace('_', ' '), null);
		};
	}

	static String ago(long ms) {
		long s = Math.max(ms, 0) / 1000;
		if (s < 60) {
			return s + " s ago";
		}
		if (s < 3600) {
			return (s / 60) + " min ago";
		}
		if (s < 86_400) {
			return (s / 3600) + " h ago";
		}
		return (s / 86_400) + " d ago";
	}
}
