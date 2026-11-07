package dev.t3craft.client.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.Json;
import dev.t3craft.t3.RpcFrames;
import dev.t3craft.t3.T3Connection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Live, read-only feed of one T3 thread for the chat screen. Uses only {@code orchestration:read}
 * calls (vendored contracts, T3 Code {@code packages/contracts}):
 * <ul>
 *   <li>WebSocket {@code /ws} (fresh ticket from {@code POST /api/auth/websocket-ticket/rpc})
 *   → {@code orchestration.subscribeThread {threadId, requestCompletionMarker, acceptBoundedSnapshot}}:
 *   a bounded snapshot (latest window + {@code historyCursor}), then live events. One Ack per
 *   Chunk, Ping every 5 s, reconnect with jittered backoff resuming at {@code afterSequence}.</li>
 *   <li>{@code GET /api/orchestration/threads/:threadId/history?cursor=…} for older pages
 *   ("load earlier").</li>
 * </ul>
 * Its own socket (not the shell connection's), opened while a chat screen is open and closed with
 * it. Never sends {@code dispatchCommand} or anything that needs {@code orchestration:operate}.
 */
public final class ThreadFeed implements AutoCloseable {
	public enum Phase { CONNECTING, LIVE, OFFLINE, FAILED }

	/** Connection line for the screen. {@code detail} is secret-free. */
	public record State(Phase phase, String detail, boolean loadingOlder, @Nullable String olderError) {
	}

	static final String PROTOCOL_HEADER = "x-t3-orchestration-protocol";
	private static final long PING_MS = 5_000;
	private static final long SILENCE_MS = 20_000;
	private static final long MIN_BACKOFF_MS = 1_000;
	private static final long MAX_BACKOFF_MS = 30_000;

	private final ChatSource.Endpoint endpoint;
	private final ChatTranscript transcript;
	private final HttpClient http;
	private final Runnable onChange;
	private final ScheduledExecutorService exec;
	private volatile boolean closed;
	private volatile State state = new State(Phase.CONNECTING, "connecting", false, null);
	private int generation;
	private int attempt;
	private @Nullable WebSocket socket;
	private @Nullable ScheduledFuture<?> pending;
	private @Nullable ScheduledFuture<?> pinger;

	public ThreadFeed(ChatSource.Endpoint endpoint, String threadId, @Nullable HttpClient http, Runnable onChange) {
		this.endpoint = endpoint;
		this.transcript = new ChatTranscript(threadId);
		this.http = http != null ? http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		this.onChange = onChange;
		this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "t3craft-chat-" + endpoint.envId());
			t.setDaemon(true);
			return t;
		});
	}

	public ChatTranscript transcript() {
		return transcript;
	}

	public State state() {
		return state;
	}

	public ChatSource.Endpoint endpoint() {
		return endpoint;
	}

	public void start() {
		run(() -> connect(generation));
	}

	/** Drops the socket and resubscribes now (backoff reset). */
	public void reconnect() {
		run(() -> {
			generation++;
			closeSocket();
			attempt = 0;
			connect(generation);
		});
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		run(() -> {
			generation++;
			closeSocket();
		});
		exec.shutdown();
	}

	public boolean isClosed() {
		return closed;
	}

	private void run(Runnable r) {
		try {
			exec.execute(r);
		} catch (RejectedExecutionException ignored) {
			// closed
		}
	}

	// ---- older history ----------------------------------------------------------------------

	/** Loads the page before the oldest row, if any and not already loading. */
	public void loadOlder() {
		run(() -> {
			String cursor = transcript.historyCursor();
			if (closed || cursor == null || state.loadingOlder()) {
				return;
			}
			setState(state.phase(), state.detail(), true, null);
			HttpRequest req = HttpRequest.newBuilder(historyUri(endpoint.baseUrl(), transcript.threadId(), cursor))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "Bearer " + endpoint.accessToken())
					.header(PROTOCOL_HEADER, String.valueOf(T3Connection.ORCHESTRATION_PROTOCOL))
					.header("Accept", "application/json")
					.GET()
					.build();
			http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenCompleteAsync((res, err) -> {
				if (closed) {
					return;
				}
				String error = null;
				if (err != null) {
					error = "could not load earlier messages: " + rootMessage(err);
				} else if (res.statusCode() == 401 || res.statusCode() == 403) {
					error = "T3 refused the history request (" + res.statusCode() + ")";
				} else if (res.statusCode() == 404) {
					error = "thread not found";
				} else if (res.statusCode() != 200) {
					error = "history: HTTP " + res.statusCode();
				} else {
					try {
						JsonElement el = JsonParser.parseString(res.body());
						transcript.applyHistoryPage(el.isJsonObject() ? el.getAsJsonObject() : null, cursor);
					} catch (RuntimeException e) {
						error = "history: unexpected response";
					}
				}
				setState(state.phase(), state.detail(), false, error);
			}, exec);
		});
	}

	static URI historyUri(URI base, String threadId, String cursor) {
		return base.resolve("/api/orchestration/threads/" + URLEncoder.encode(threadId, StandardCharsets.UTF_8).replace("+", "%20")
				+ "/history?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
	}

	/** The subscribe request frame; {@code afterSequence} < 0 asks for a fresh snapshot. */
	static String subscribeFrame(String requestId, String threadId, long afterSequence) {
		JsonObject p = new JsonObject();
		p.addProperty("threadId", threadId);
		if (afterSequence >= 0) {
			p.addProperty("afterSequence", afterSequence);
		}
		p.addProperty("requestCompletionMarker", true);
		p.addProperty("acceptBoundedSnapshot", true);
		return RpcFrames.request(requestId, RpcFrames.SUBSCRIBE_THREAD, p);
	}

	static URI socketUri(URI base, String ticket) {
		String scheme = "http".equals(base.getScheme()) ? "ws" : "wss";
		return URI.create(scheme + "://" + base.getRawAuthority() + "/ws?orchestrationProtocol=" + T3Connection.ORCHESTRATION_PROTOCOL
				+ "&wsTicket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
	}

	// ---- live subscription ------------------------------------------------------------------

	private boolean current(int g) {
		return !closed && g == generation;
	}

	private void setState(Phase p, String detail, boolean loadingOlder, @Nullable String olderError) {
		state = new State(p, T3Connection.sanitize(detail), loadingOlder, olderError == null ? null : T3Connection.sanitize(olderError));
		onChange.run();
	}

	private void setPhase(int g, Phase p, String detail) {
		if (current(g)) {
			setState(p, detail, state.loadingOlder(), state.olderError());
		}
	}

	private void closeSocket() {
		if (pending != null) {
			pending.cancel(false);
			pending = null;
		}
		if (pinger != null) {
			pinger.cancel(false);
			pinger = null;
		}
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			ws.abort();
		}
	}

	private void connect(int g) {
		if (!current(g)) {
			return;
		}
		setPhase(g, Phase.CONNECTING, "connecting to " + endpoint.label());
		HttpRequest req = HttpRequest.newBuilder(endpoint.baseUrl().resolve("/api/auth/websocket-ticket/rpc"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + endpoint.accessToken())
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenCompleteAsync((res, err) -> {
			if (!current(g)) {
				return;
			}
			if (err != null) {
				retry(g, "ticket: " + rootMessage(err));
				return;
			}
			if (res.statusCode() == 401 || res.statusCode() == 403) {
				setPhase(g, Phase.FAILED, "T3 rejected the session (" + res.statusCode() + "): pair again (/t3craft pair)");
				return;
			}
			if (res.statusCode() != 200) {
				retry(g, "ticket: HTTP " + res.statusCode());
				return;
			}
			String ticket = null;
			try {
				JsonElement el = JsonParser.parseString(res.body());
				ticket = el.isJsonObject() ? Json.str(el.getAsJsonObject(), "ticket") : null;
			} catch (RuntimeException ignored) {
				// handled below
			}
			if (ticket == null || ticket.isBlank()) {
				retry(g, "ticket: unexpected response");
				return;
			}
			http.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(socketUri(endpoint.baseUrl(), ticket), new Handler(g))
					.whenCompleteAsync((ws, wsErr) -> {
						if (wsErr == null) {
							if (!current(g)) {
								ws.abort();
							}
							return;
						}
						Throwable root = wsErr;
						while (root.getCause() != null && !(root instanceof WebSocketHandshakeException)) {
							root = root.getCause();
						}
						if (root instanceof WebSocketHandshakeException h) {
							int code = h.getResponse().statusCode();
							if (code == 426) {
								setPhase(g, Phase.FAILED, "T3 speaks another orchestration protocol (426); update T3Craft");
								return;
							}
							if (code == 401 || code == 403) {
								setPhase(g, Phase.FAILED, "T3 refused the WebSocket (" + code + "): pair again");
								return;
							}
							retry(g, "upgrade: HTTP " + code);
							return;
						}
						retry(g, "upgrade: " + rootMessage(wsErr));
					}, exec);
		}, exec);
	}

	private void retry(int g, String reason) {
		if (!current(g)) {
			return;
		}
		closeSocket();
		long cap = Math.min(MAX_BACKOFF_MS, MIN_BACKOFF_MS << Math.min(attempt, 10));
		attempt++;
		long delay = cap / 2 + ThreadLocalRandom.current().nextLong(cap / 2 + 1);
		setPhase(g, Phase.OFFLINE, reason + " (retry in " + Math.max(1, delay / 1000) + " s)");
		pending = exec.schedule(() -> connect(g), delay, TimeUnit.MILLISECONDS);
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return T3Connection.sanitize(c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage()));
	}

	private final class Handler implements WebSocket.Listener {
		private final int gen;
		private final StringBuilder buffer = new StringBuilder();
		private volatile long lastFrameMs = System.currentTimeMillis();
		private boolean ended;

		Handler(int gen) {
			this.gen = gen;
		}

		@Override
		public void onOpen(WebSocket ws) {
			run(() -> {
				if (!current(gen)) {
					ws.abort();
					return;
				}
				socket = ws;
				// Always a fresh bounded snapshot: small, and it never depends on a resume point the
				// server may have compacted away. Older pages loaded before are dropped with it.
				ws.sendText(subscribeFrame("1", transcript.threadId(), -1), true);
				pinger = exec.scheduleAtFixedRate(() -> {
					if (!current(gen) || ended) {
						return;
					}
					if (System.currentTimeMillis() - lastFrameMs > SILENCE_MS) {
						end("no data from T3 for " + (SILENCE_MS / 1000) + " s");
						return;
					}
					ws.sendText(RpcFrames.ping(), true);
				}, PING_MS, PING_MS, TimeUnit.MILLISECONDS);
			});
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			lastFrameMs = System.currentTimeMillis();
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				run(() -> handle(ws, text));
			}
			ws.request(1);
			return null;
		}

		private void handle(WebSocket ws, String text) {
			if (!current(gen) || ended) {
				return;
			}
			switch (RpcFrames.parse(text)) {
				case RpcFrames.Chunk c -> {
					boolean changed = false;
					for (JsonObject item : c.values()) {
						changed |= transcript.applyStreamItem(item);
					}
					ws.sendText(RpcFrames.ack(c.requestId()), true);
					if (state.phase() != Phase.LIVE && transcript.hasSnapshot()) {
						attempt = 0;
						setPhase(gen, Phase.LIVE, "live");
					} else if (changed) {
						onChange.run();
					}
				}
				case RpcFrames.Exit e -> {
					if (e.failure() != null && e.failure().contains("NotFound")) {
						ended = true;
						closeSocket();
						setPhase(gen, Phase.FAILED, "thread not found on " + endpoint.label());
						return;
					}
					end(e.failure() == null ? "thread subscription ended" : "T3: " + e.failure());
				}
				case RpcFrames.Defect d -> end("T3 protocol error: " + d.detail());
				case RpcFrames.Pong p -> {
				}
				case RpcFrames.Other o -> {
				}
			}
		}

		private void end(String reason) {
			if (ended) {
				return;
			}
			ended = true;
			if (reason.contains("EnvironmentAuthorizationError")) {
				closeSocket();
				setPhase(gen, Phase.FAILED, reason);
				return;
			}
			retry(gen, reason);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			run(() -> end("closed " + code + (reason == null || reason.isEmpty() ? "" : " " + reason)));
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			run(() -> end(rootMessage(error)));
		}
	}
}
