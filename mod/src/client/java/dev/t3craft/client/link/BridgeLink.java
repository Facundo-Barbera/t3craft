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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket client to the t3craft bridge (URL from {@code config/t3craft.json}; default
 * {@code ws://127.0.0.1:7878/}, normally the homelab's Tailscale address), using
 * {@code java.net.http}. Reconnects forever with {@link Backoff}. Sends {@code hello} on open.
 * Incoming frames are parsed off-thread and handed to the listener on the client thread via
 * {@code clientExecutor}. Outgoing sends are chained so frames never interleave.
 *
 * <p>Every connection attempt has a generation number. {@link #reconnect()} bumps it, so
 * callbacks from an older socket are ignored and no stale reconnect timer fires.
 */
public final class BridgeLink {
	public static final int PROTOCOL_VERSION = 1;

	private final URI uri;
	private final BridgeListener listener;
	private final Consumer<Runnable> clientExecutor;
	private final String modVersion;
	private final @org.jspecify.annotations.Nullable String bridgeKey;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "t3craft-bridge-link");
		t.setDaemon(true);
		return t;
	});
	private final Backoff backoff = new Backoff();
	private final AtomicLong nextId = new AtomicLong(1);

	private final AtomicInteger generation = new AtomicInteger();
	private volatile WebSocket socket;
	private volatile boolean stopped;
	private volatile LinkStatus status = LinkStatus.DISABLED;
	private volatile String statusDetail = "";
	private ScheduledFuture<?> pending;
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

	/** {@code bridgeKey}: optional bridge access key (not a T3 credential), sent in {@code hello}. */
	public BridgeLink(URI uri, @org.jspecify.annotations.Nullable String bridgeKey, String modVersion,
			BridgeListener listener, Consumer<Runnable> clientExecutor) {
		this.uri = uri;
		this.bridgeKey = bridgeKey;
		this.modVersion = modVersion;
		this.listener = listener;
		this.clientExecutor = clientExecutor;
	}

	public URI uri() {
		return uri;
	}

	public void start() {
		int g = generation.get();
		scheduler.execute(() -> connect(g));
	}

	/** Drops the current socket (if any) and connects again right away, resetting the backoff. */
	public void reconnect() {
		if (stopped) {
			return;
		}
		scheduler.execute(() -> {
			int g = generation.incrementAndGet();
			backoff.reset();
			if (pending != null) {
				pending.cancel(false);
				pending = null;
			}
			WebSocket ws = socket;
			socket = null;
			if (ws != null) {
				ws.abort();
			}
			connect(g);
		});
	}

	public void stop() {
		stopped = true;
		generation.incrementAndGet();
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "client stopping").exceptionally(e -> null);
			ws.abort();
		}
		scheduler.shutdownNow();
	}

	public LinkStatus status() {
		return status;
	}

	public String statusDetail() {
		return statusDetail;
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

	private boolean current(int g) {
		return !stopped && g == generation.get();
	}

	private void connect(int g) {
		if (!current(g)) {
			return;
		}
		pending = null;
		status(g, LinkStatus.CONNECTING, uri.toString());
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.buildAsync(uri, new Handler(g))
				.whenComplete((ws, err) -> {
					if (err != null) {
						scheduleReconnect(g, rootMessage(err));
					} else if (!current(g)) {
						ws.abort(); // superseded while opening
					}
				});
	}

	private void scheduleReconnect(int g, String reason) {
		if (!current(g)) {
			return;
		}
		socket = null;
		long delay = backoff.nextDelayMs();
		status(g, LinkStatus.BACKOFF, reason + " (retry in " + delay + " ms)");
		try {
			scheduler.execute(() -> {
				if (current(g)) {
					pending = scheduler.schedule(() -> connect(g), delay, TimeUnit.MILLISECONDS);
				}
			});
		} catch (java.util.concurrent.RejectedExecutionException ignored) {
			// shutting down
		}
	}

	private void status(int g, LinkStatus s, String detail) {
		if (!current(g)) {
			return;
		}
		status = s;
		statusDetail = detail;
		clientExecutor.accept(() -> {
			if (current(g)) {
				listener.onStatus(s, detail);
			}
		});
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
	}

	private final class Handler implements WebSocket.Listener {
		private final int gen;
		private final StringBuilder buffer = new StringBuilder();

		Handler(int gen) {
			this.gen = gen;
		}
		private final java.util.concurrent.atomic.AtomicBoolean ended = new java.util.concurrent.atomic.AtomicBoolean();

		private void end(String reason) {
			if (ended.compareAndSet(false, true)) {
				scheduleReconnect(gen, reason);
			}
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			if (!current(gen)) {
				webSocket.abort();
				return;
			}
			socket = webSocket;
			backoff.reset();
			status(gen, LinkStatus.CONNECTED, uri.toString());
			JsonObject hello = new JsonObject();
			hello.addProperty("client", "t3craft-mod");
			hello.addProperty("version", modVersion);
			hello.addProperty("protocol", PROTOCOL_VERSION);
			if (bridgeKey != null) {
				hello.addProperty("key", bridgeKey);
			}
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
			clientExecutor.accept(() -> {
				if (current(gen)) {
					listener.onMessage(t, msg);
				}
			});
		}
	}
}
