package dev.t3craft.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Effect RPC (effect 4.1.2) JSON framing over T3's {@code /ws}, as RpcSerialization.layerJson
 * sends it. Client → server: Request, Ack (one per received Chunk; the server waits for it before
 * sending the next chunk), Interrupt, Ping. Server → client: Chunk, Exit, Defect,
 * ClientProtocolError, Pong.
 */
public final class RpcFrames {
	private RpcFrames() {
	}

	public static final String SUBSCRIBE_SHELL = "orchestration.subscribeShell";
	public static final String SUBSCRIBE_THREAD = "orchestration.subscribeThread";
	public static final String GET_TURN_ITEM = "orchestration.getTurnItem";
	public static final String GET_THREAD_PROJECTION = "orchestration.getThreadProjection";

	/**
	 * {@code orchestration.subscribeThread} as the chat uses it: completion marker and a bounded
	 * snapshot (latest window + {@code historyCursor}; older pages over HTTP). No compact/dedupe
	 * options, so items arrive as plain JSON.
	 */
	public static String subscribeThread(String id, String threadId) {
		JsonObject p = new JsonObject();
		p.addProperty("threadId", threadId);
		p.addProperty("requestCompletionMarker", true);
		p.addProperty("acceptBoundedSnapshot", true);
		return request(id, SUBSCRIBE_THREAD, p);
	}
	/** Read-only ({@code orchestration:read}) thread search; a single Exit carries the result. */
	public static final String SEARCH_THREADS = "orchestration.searchThreads";
	/** T3 rejects queries shorter than this (after trimming). */
	public static final int SEARCH_MIN_LENGTH = 2;
	/** T3 rejects longer queries; they are cut to this before sending. */
	public static final int SEARCH_MAX_LENGTH = 200;
	public static final int SEARCH_MAX_LIMIT = 50;

	public static String request(String id, String tag, JsonObject payload) {
		JsonObject o = new JsonObject();
		o.addProperty("_tag", "Request");
		o.addProperty("id", id);
		o.addProperty("tag", tag);
		o.add("payload", payload);
		o.add("headers", new JsonArray());
		return o.toString();
	}

	/**
	 * {@code orchestration.subscribeShell} always with {@code requestCompletionMarker: true}:
	 * T3 only sends {@code synchronized} when asked (regression fixed in bfaebf1).
	 */
	public static String subscribeShell(String id) {
		JsonObject p = new JsonObject();
		p.addProperty("requestCompletionMarker", true);
		return request(id, SUBSCRIBE_SHELL, p);
	}

	/**
	 * {@code orchestration.searchThreads} with {@code query} trimmed and cut to
	 * {@link #SEARCH_MAX_LENGTH} and {@code limit} clamped to 1..{@link #SEARCH_MAX_LIMIT}.
	 * Callers check {@link #searchQuery} first: T3 rejects queries under two characters.
	 */
	public static String searchThreads(String id, String query, int limit, @Nullable String environmentLabel) {
		JsonObject p = new JsonObject();
		p.addProperty("query", searchQuery(query));
		p.addProperty("limit", Math.max(1, Math.min(SEARCH_MAX_LIMIT, limit)));
		if (environmentLabel != null && !environmentLabel.isBlank()) {
			p.addProperty("environmentLabel", environmentLabel);
		}
		return request(id, SEARCH_THREADS, p);
	}

	/** The query as it is sent: trimmed and cut to {@link #SEARCH_MAX_LENGTH}. */
	public static String searchQuery(@Nullable String query) {
		String q = query == null ? "" : query.strip();
		return q.length() > SEARCH_MAX_LENGTH ? q.substring(0, SEARCH_MAX_LENGTH).strip() : q;
	}

	public static String ack(String requestId) {
		JsonObject o = new JsonObject();
		o.addProperty("_tag", "Ack");
		o.addProperty("requestId", requestId);
		return o.toString();
	}

	public static String interrupt(String requestId) {
		JsonObject o = new JsonObject();
		o.addProperty("_tag", "Interrupt");
		o.addProperty("requestId", requestId);
		o.add("interruptors", new JsonArray());
		return o.toString();
	}

	public static String ping() {
		return "{\"_tag\":\"Ping\"}";
	}

	/** A decoded server frame. */
	public sealed interface Frame {
	}

	public record Chunk(String requestId, List<JsonObject> values) implements Frame {
	}

	/**
	 * {@code failure} is a short, secret-free description when the exit is a failure, else null.
	 * {@code value} is the success value of a non-stream RPC (null for streams and failures).
	 */
	public record Exit(String requestId, @Nullable String failure, @Nullable JsonElement value) implements Frame {
		public Exit(String requestId, @Nullable String failure) {
			this(requestId, failure, null);
		}
	}

	public record Defect(String detail) implements Frame {
	}

	public record Pong() implements Frame {
	}

	public record Other(String tag) implements Frame {
	}

	public static Frame parse(String text) {
		JsonElement el;
		try {
			el = JsonParser.parseString(text);
		} catch (RuntimeException e) {
			return new Defect("invalid JSON from server");
		}
		if (!el.isJsonObject()) {
			return new Defect("non-object frame");
		}
		JsonObject o = el.getAsJsonObject();
		String tag = String.valueOf(Json.str(o, "_tag"));
		return switch (tag) {
			case "Chunk" -> {
				List<JsonObject> values = new ArrayList<>();
				JsonArray vs = Json.arr(o, "values");
				if (vs != null) {
					for (JsonElement v : vs) {
						if (v.isJsonObject()) {
							values.add(v.getAsJsonObject());
						}
					}
				}
				yield new Chunk(String.valueOf(Json.str(o, "requestId")), values);
			}
			case "Exit" -> {
				JsonObject exit = Json.obj(o, "exit");
				String failure = null;
				JsonElement value = null;
				if ("Failure".equals(Json.str(exit, "_tag"))) {
					failure = describeCause(Json.arr(exit, "cause"));
				} else if (exit != null && exit.has("value") && !exit.get("value").isJsonNull()) {
					value = exit.get("value");
				}
				yield new Exit(String.valueOf(Json.str(o, "requestId")), failure, value);
			}
			case "Defect", "ClientProtocolError" -> new Defect(tag + ": " + truncate(String.valueOf(o.get("defect") != null ? o.get("defect") : o.get("error"))));
			case "Pong" -> new Pong();
			default -> new Other(tag);
		};
	}

	/** The success value of an {@code Exit} frame ({@code exit.value}), or null. */
	public static @Nullable JsonElement exitValue(String text) {
		try {
			JsonElement el = JsonParser.parseString(text);
			if (!el.isJsonObject()) {
				return null;
			}
			JsonObject exit = Json.obj(el.getAsJsonObject(), "exit");
			if (exit == null || !"Success".equals(Json.str(exit, "_tag"))) {
				return null;
			}
			JsonElement v = exit.get("value");
			return v == null || v.isJsonNull() ? null : v;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String describeCause(@Nullable JsonArray cause) {
		if (cause == null || cause.isEmpty()) {
			return "failure";
		}
		JsonElement first = cause.get(0);
		if (!first.isJsonObject()) {
			return "failure";
		}
		JsonObject c = first.getAsJsonObject();
		JsonObject error = Json.obj(c, "error");
		String name = Json.str(error, "_tag");
		String message = Json.str(error, "message");
		return String.valueOf(Json.str(c, "_tag")) + (name == null ? "" : " " + name) + (message == null ? "" : ": " + truncate(message));
	}

	private static String truncate(String s) {
		return s.length() > 200 ? s.substring(0, 200) + "…" : s;
	}
}
