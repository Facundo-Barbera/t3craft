package dev.t3craft.client.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.ThreadState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * The last thread list and the tail of the most recent transcripts, kept on disk in
 * {@code config/t3craft/cache/threads.json} so the T3 screen paints something right after a game
 * restart, marked "cached" until the environment's shell and the thread's snapshot arrive
 * (feat/t3-thread-cache). Optional and disposable: delete the folder at any time.
 *
 * <p>Holds no secrets: no tokens, URLs or credentials, only what the sidebar and the last
 * {@link #TAIL_ROWS} finished rows show (bodies clipped, tool output and details dropped). Reads and
 * writes run on one background thread; the client thread only swaps immutable snapshots.
 */
public final class ThreadDiskCache {
	public static final int FORMAT = 1;
	public static final int MAX_ROWS = 400;
	public static final int TAIL_THREADS = 6;
	public static final int TAIL_ROWS = 40;
	public static final int MAX_BODY = 4_000;

	/** Kept rows of one transcript. */
	public record Tail(String title, List<ChatEntry> entries) {
	}

	/** What was on disk: the list (any order) and tails by {@code env:threadId}. */
	public record Snapshot(List<ThreadRow> rows, Map<String, Tail> tails) {
		static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
	}

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "t3craft-thread-cache");
		t.setDaemon(true);
		return t;
	});

	private final Path file;
	private volatile Snapshot loaded = Snapshot.EMPTY;
	private volatile boolean loadStarted;
	private volatile boolean loadDone;
	private @Nullable String lastWritten;

	public ThreadDiskCache(Path dir) {
		this.file = dir.resolve("cache").resolve("threads.json");
	}

	/** What the disk had at startup (empty until {@link #loadAsync()} finished). */
	public Snapshot loaded() {
		return loaded;
	}

	public boolean loadDone() {
		return loadDone;
	}

	public @Nullable Tail tail(String key) {
		return loaded.tails().get(key);
	}

	/** Reads the file once, off the client thread. */
	public void loadAsync() {
		if (loadStarted) {
			return;
		}
		loadStarted = true;
		CompletableFuture.runAsync(() -> {
			try {
				if (Files.isRegularFile(file)) {
					loaded = decode(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
				}
			} catch (Exception e) {
				T3Craft.LOGGER.warn("[chat] thread cache unreadable, ignoring it: {}", e.getClass().getSimpleName());
			} finally {
				loadDone = true;
			}
		}, IO);
	}

	/**
	 * Writes {@code rows} and the tails of {@code recent} (most recent first) when they changed.
	 * Builds the JSON on the caller (cheap, bounded) and writes it on the IO thread (temp + move).
	 */
	public void saveAsync(List<ThreadRow> rows, List<ThreadCache.Entry> recent) {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonArray rs = new JsonArray();
		for (int i = 0; i < rows.size() && i < MAX_ROWS; i++) {
			rs.add(encodeRow(rows.get(i)));
		}
		root.add("rows", rs);
		JsonObject tails = new JsonObject();
		for (int i = 0; i < recent.size() && i < TAIL_THREADS; i++) {
			ThreadCache.Entry e = recent.get(i);
			JsonObject t = new JsonObject();
			t.addProperty("title", e.transcript().title());
			JsonArray es = new JsonArray();
			for (ChatEntry c : e.transcript().tail(TAIL_ROWS)) {
				es.add(encodeEntry(c));
			}
			t.add("entries", es);
			tails.add(e.key(), t);
		}
		root.add("tails", tails);
		String text = root.toString();
		if (text.equals(lastWritten)) {
			return;
		}
		lastWritten = text;
		CompletableFuture.runAsync(() -> {
			try {
				Files.createDirectories(file.getParent());
				Path tmp = file.resolveSibling("threads.json.tmp");
				Files.writeString(tmp, text, StandardCharsets.UTF_8);
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				T3Craft.LOGGER.warn("[chat] could not write the thread cache: {}", e.getClass().getSimpleName());
			}
		}, IO);
	}

	// ---- codec --------------------------------------------------------------------------------

	private static JsonObject encodeRow(ThreadRow r) {
		JsonObject o = new JsonObject();
		o.addProperty("key", r.key());
		o.addProperty("env", r.env());
		o.addProperty("envLabel", r.envLabel());
		if (r.threadId() != null) {
			o.addProperty("threadId", r.threadId());
		}
		o.addProperty("title", r.title());
		o.addProperty("project", r.project());
		o.addProperty("state", r.state().name());
		o.addProperty("pending", r.pendingRequest());
		if (r.activity() != null) {
			o.addProperty("activity", r.activity());
		}
		o.addProperty("settled", r.settled());
		if (r.activityMs() != null) {
			o.addProperty("activityMs", r.activityMs());
		}
		o.addProperty("pinned", r.pinned());
		// feat/thread-lineage (additive): a delegated task's parent link, so cached rows nest too
		if (r.lineage().delegated()) {
			o.addProperty("delegated", true);
			if (r.lineage().parentKey() != null) {
				o.addProperty("parentKey", r.lineage().parentKey());
			}
			if (!r.lineage().parentTitle().isEmpty()) {
				o.addProperty("parentTitle", r.lineage().parentTitle());
			}
		}
		return o;
	}

	private static JsonObject encodeEntry(ChatEntry e) {
		JsonObject o = new JsonObject();
		o.addProperty("key", e.key());
		o.addProperty("itemId", e.itemId());
		if (e.messageId() != null) {
			o.addProperty("messageId", e.messageId());
		}
		o.addProperty("kind", e.kind().name());
		o.addProperty("title", e.title());
		o.addProperty("body", e.body().length() > MAX_BODY ? e.body().substring(0, MAX_BODY) + "…" : e.body());
		o.addProperty("status", e.status());
		if (e.at() != null) {
			o.addProperty("at", e.at());
		}
		o.addProperty("ordinal", e.ordinal());
		o.addProperty("inherited", e.inherited());
		return o;
	}

	static Snapshot decode(JsonElement el) {
		if (!el.isJsonObject() || Json.num(el.getAsJsonObject(), "format", 0) != FORMAT) {
			return Snapshot.EMPTY;
		}
		JsonObject root = el.getAsJsonObject();
		List<ThreadRow> rows = new ArrayList<>();
		JsonArray rs = Json.arr(root, "rows");
		if (rs != null) {
			for (JsonElement r : rs) {
				ThreadRow row = r.isJsonObject() ? decodeRow(r.getAsJsonObject()) : null;
				if (row != null && rows.size() < MAX_ROWS) {
					rows.add(row);
				}
			}
		}
		Map<String, Tail> tails = new LinkedHashMap<>();
		JsonObject ts = Json.obj(root, "tails");
		if (ts != null) {
			for (var t : ts.entrySet()) {
				if (!t.getValue().isJsonObject() || tails.size() >= TAIL_THREADS) {
					continue;
				}
				JsonObject o = t.getValue().getAsJsonObject();
				List<ChatEntry> es = new ArrayList<>();
				JsonArray ea = Json.arr(o, "entries");
				if (ea != null) {
					for (JsonElement x : ea) {
						ChatEntry c = x.isJsonObject() ? decodeEntry(x.getAsJsonObject()) : null;
						if (c != null && es.size() < TAIL_ROWS) {
							es.add(c);
						}
					}
				}
				String title = Json.str(o, "title");
				tails.put(t.getKey(), new Tail(title == null ? "" : title, List.copyOf(es)));
			}
		}
		return new Snapshot(List.copyOf(rows), Map.copyOf(tails));
	}

	private static @Nullable ThreadRow decodeRow(JsonObject o) {
		String key = Json.str(o, "key");
		String env = Json.str(o, "env");
		if (key == null || env == null) {
			return null;
		}
		ThreadState state;
		try {
			state = ThreadState.valueOf(String.valueOf(Json.str(o, "state")));
		} catch (IllegalArgumentException e) {
			state = ThreadState.IDLE;
		}
		Long at = o.has("activityMs") ? Json.num(o, "activityMs", 0) : null;
		return new ThreadRow(key, env, orEmpty(Json.str(o, "envLabel")), Json.str(o, "threadId"), orEmpty(Json.str(o, "title")),
				orEmpty(Json.str(o, "project")), state, Json.bool(o, "pending"), Json.str(o, "activity"), Json.bool(o, "settled"), at,
				Json.bool(o, "pinned"), ThreadDetails.EMPTY,
				Json.bool(o, "delegated") ? new RowLineage(Json.str(o, "parentKey"), orEmpty(Json.str(o, "parentTitle")), true) : RowLineage.NONE);
	}

	private static @Nullable ChatEntry decodeEntry(JsonObject o) {
		String key = Json.str(o, "key");
		String itemId = Json.str(o, "itemId");
		if (key == null || itemId == null) {
			return null;
		}
		ChatEntry.Kind kind;
		try {
			kind = ChatEntry.Kind.valueOf(String.valueOf(Json.str(o, "kind")));
		} catch (IllegalArgumentException e) {
			return null;
		}
		Long at = o.has("at") ? Json.num(o, "at", 0) : null;
		return new ChatEntry(key, itemId, Json.str(o, "messageId"), kind, orEmpty(Json.str(o, "title")), orEmpty(Json.str(o, "body")), null,
				false, orEmpty(Json.str(o, "status")), at, Json.num(o, "ordinal", 0), Json.bool(o, "inherited"));
	}

	private static String orEmpty(@Nullable String s) {
		return s == null ? "" : s;
	}
}
