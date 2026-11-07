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
	/** One-shot read (scope {@code orchestration:read}): a page of older settled threads. */
	public static final String GET_OLDER_SETTLED_THREADS = "orchestration.getOlderSettledThreads";
	/** One-shot read (scope {@code orchestration:read}): archived threads and their projects. */
	public static final String GET_ARCHIVED_SHELL_SNAPSHOT = "orchestration.getArchivedShellSnapshot";

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
	 * {@code value} is the success value of a one-shot RPC (null on failure, or when T3 sent none).
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
				} else if (exit != null) {
					JsonElement v = exit.get("value");
					value = v == null || v.isJsonNull() ? null : v;
				}
				yield new Exit(String.valueOf(Json.str(o, "requestId")), failure, value);
			}
			case "Defect", "ClientProtocolError" -> new Defect(tag + ": " + truncate(String.valueOf(o.get("defect") != null ? o.get("defect") : o.get("error"))));
			case "Pong" -> new Pong();
			default -> new Other(tag);
		};
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
