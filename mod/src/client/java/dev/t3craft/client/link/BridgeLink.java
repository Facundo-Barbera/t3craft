package dev.t3craft.client.link;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket client to the local t3craft bridge ({@code ws://127.0.0.1:7878} by default), using
 * {@code java.net.http}. Reconnects forever with {@link Backoff}. Sends {@code hello} on open.
 * Incoming frames are parsed off-thread and handed to the listener on the client thread via
 * {@code clientExecutor}. Outgoing sends are chained so frames never interleave.
 */
public final class BridgeLink {
	public static final int PROTOCOL_VERSION = 1;

	private final URI uri;
	private final BridgeListener listener;
	private final Consumer<Runnable> clientExecutor;
	private final String modVersion;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "t3craft-bridge-link");
		t.setDaemon(true);
		return t;
	});
	private final Backoff backoff = new Backoff();
	private final AtomicLong nextId = new AtomicLong(1);

	private volatile WebSocket socket;
	private volatile boolean stopped;
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

	public BridgeLink(URI uri, String modVersion, BridgeListener listener, Consumer<Runnable> clientExecutor) {
		this.uri = uri;
		this.modVersion = modVersion;
		this.listener = listener;
		this.clientExecutor = clientExecutor;
	}

	public URI uri() {
		return uri;
	}

	public void start() {
		scheduler.execute(this::connect);
	}

	public void stop() {
		stopped = true;
		WebSocket ws = socket;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "client stopping");
		}
		scheduler.shutdownNow();
	}

	public boolean isConnected() {
		return socket != null;
	}

	/** Sends one envelope. Adds {@code v} and a fresh {@code id}; returns the id used. */
	public synchronized String send(String type, JsonObject payload) {
		JsonObject msg = payload == null ? new JsonObject() : payload.deepCopy();
		msg.addProperty("v", PROTOCOL_VERSION);
		msg.addProperty("type", type);
		String id = "m" + nextId.getAndIncrement();
		msg.addProperty("id", id);
		WebSocket ws = socket;
		if (ws == null) {
			T3Craft.LOGGER.debug("dropping {} (not connected)", type);
			return id;
		}
		String text = msg.toString();
		sendChain = sendChain
				.handle((ok, err) -> null)
				.thenCompose(ignored -> ws.sendText(text, true));
		return id;
	}

	private void connect() {
		if (stopped) {
			return;
		}
		status(LinkStatus.CONNECTING, uri.toString());
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.buildAsync(uri, new Handler())
				.whenComplete((ws, err) -> {
					if (err != null) {
						scheduleReconnect(rootMessage(err));
					}
				});
	}

	private void scheduleReconnect(String reason) {
		socket = null;
		if (stopped) {
			return;
		}
		long delay = backoff.nextDelayMs();
		status(LinkStatus.BACKOFF, reason + " (retry in " + delay + " ms)");
		try {
			scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.RejectedExecutionException ignored) {
			// shutting down
		}
	}

	private void status(LinkStatus s, String detail) {
		clientExecutor.accept(() -> listener.onStatus(s, detail));
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
	}

	private final class Handler implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();
		private final java.util.concurrent.atomic.AtomicBoolean ended = new java.util.concurrent.atomic.AtomicBoolean();

		private void end(String reason) {
			if (ended.compareAndSet(false, true)) {
				scheduleReconnect(reason);
			}
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			backoff.reset();
			status(LinkStatus.CONNECTED, uri.toString());
			JsonObject hello = new JsonObject();
			hello.addProperty("client", "t3craft-mod");
			hello.addProperty("version", modVersion);
			hello.addProperty("protocol", PROTOCOL_VERSION);
			send("hello", hello);
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				dispatch(text);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			end("closed " + statusCode + (reason.isEmpty() ? "" : " " + reason));
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			end(rootMessage(error));
		}

		private void dispatch(String text) {
			JsonObject msg;
			try {
				JsonElement el = JsonParser.parseString(text);
				if (!el.isJsonObject()) {
					return;
				}
				msg = el.getAsJsonObject();
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("bridge sent invalid JSON: {}", e.getMessage());
				return;
			}
			JsonElement v = msg.get("v");
			JsonElement type = msg.get("type");
			if (v == null || !v.isJsonPrimitive() || v.getAsInt() != PROTOCOL_VERSION
					|| type == null || !type.isJsonPrimitive()) {
				T3Craft.LOGGER.warn("ignoring frame with unexpected envelope: {}", text.length() > 200 ? text.substring(0, 200) : text);
				return;
			}
			String t = type.getAsString();
			clientExecutor.accept(() -> listener.onMessage(t, msg));
		}
	}
}
