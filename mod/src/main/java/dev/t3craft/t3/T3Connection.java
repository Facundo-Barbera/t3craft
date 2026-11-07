package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Direct, read-only connection from the mod to one T3 environment:
 * {@code POST /api/auth/websocket-ticket/rpc} (Bearer) → {@code /ws?orchestrationProtocol=2&wsTicket=…}
 * → {@code orchestration.subscribeShell} with {@code requestCompletionMarker:true}, one Ack per
 * Chunk, Ping every 5 s, reconnect with jittered backoff and a fresh single-use ticket each time.
 * Only read-only RPCs are ever sent from this class.
 *
 * <p>The same socket also carries per-thread streams and one-shot reads for every view of this
 * environment ({@link #subscribeThread}, {@link #getTurnItem}, {@link #getThreadProjection}; older
 * history over HTTP with {@link #threadHistory}). Subscriptions are refcounted
 * ({@link StreamRegistry}): any number of views of one thread share one server subscription, which
 * is resubscribed after every reconnect and interrupted when the last view closes. Nothing else in
 * the mod opens its own connection to T3.
 *
 * <p>Secrets: the Bearer token and the ticket never appear in status details or logs
 * ({@link #sanitize}).
 */
public final class T3Connection {
	public enum Status {
		CONNECTING("connecting"),
		OK("ok"),
		/** Token rejected (revoked/expired): pair again. No automatic retries. */
		AUTH_FAILED("auth_failed"),
		NOT_PAIRED("not_paired"),
		OFFLINE("offline"),
		/** Server speaks another orchestration protocol (HTTP 426). */
		INCOMPATIBLE("incompatible");

		public final String wire;

		Status(String wire) {
			this.wire = wire;
		}
	}

	public interface Listener {
		void onStatus(String envId, Status status, String detail);

		/** A consistent copy of the environment's shell after every change. */
		void onShell(String envId, ShellState.View view);
	}

	public static final int ORCHESTRATION_PROTOCOL = 2;
	private static final String SHELL_REQUEST = "1";
	private static final long PING_MS = 5_000;
	private static final long SILENCE_MS = 20_000;
	private static final long MIN_BACKOFF_MS = 1_000;
	private static final long MAX_BACKOFF_MS = 30_000;

	private final Environments.Environment env;
	private final Supplier<Pairing.@Nullable Session> session;
	private final Listener listener;
	private final HttpClient http;
	private final ScheduledExecutorService exec;
	private final AtomicInteger generation = new AtomicInteger();
	private volatile boolean stopped;
	private volatile Status status = Status.CONNECTING;
	private volatile String detail = "";
	private int attempt;
	private @Nullable WebSocket socket;
	private @Nullable ScheduledFuture<?> pending;
	private @Nullable ScheduledFuture<?> pinger;
	private final StreamRegistry streams = new StreamRegistry(RpcFrames::subscribeThread);
	private CompletableFuture<?> sendTail = CompletableFuture.completedFuture(null);

	public T3Connection(Environments.Environment env, Supplier<Pairing.@Nullable Session> session, Listener listener, HttpClient http) {
		this.env = env;
		this.session = session;
		this.listener = listener;
		this.http = http;
		this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "t3craft-t3-" + env.id());
			t.setDaemon(true);
			return t;
		});
	}

	public Environments.Environment environment() {
		return env;
	}

	public Status status() {
		return status;
	}

	public String detail() {
		return detail;
	}

	public void start() {
		int g = generation.get();
		exec.execute(() -> connect(g));
	}

	/** Drops the socket and connects again now (backoff reset). Also retries after AUTH_FAILED. */
	public void reconnect() {
		if (stopped) {
			return;
		}
		exec.execute(() -> {
			int g = generation.incrementAndGet();
			closeSocket();
			attempt = 0;
			connect(g);
		});
	}

	public void stop() {
		stopped = true;
		generation.incrementAndGet();
		try {
			exec.execute(this::closeSocket);
		} catch (java.util.concurrent.RejectedExecutionException ignored) {
			// already stopped
		}
		exec.shutdown();
	}

	// ---- shared streams and reads ---------------------------------------------------------------

	/**
	 * Follows one thread ({@code orchestration.subscribeThread}, bounded snapshot then live events).
	 * Shared and refcounted: close the returned handle when the view goes away. The listener runs on
	 * this connection's thread; a late subscriber first gets {@code onSubscribed} and a replay of
	 * what the others already received.
	 */
	public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
		return streams.subscribe(threadId, listener);
	}

	/** Stream key of {@code scheduledTasks.subscribe} in {@link StreamRegistry}. */
	public static final String SCHEDULED_TASKS_STREAM = "rpc:" + RpcFrames.SUBSCRIBE_SCHEDULED_TASKS;

	/**
	 * Follows the environment's scheduled tasks ({@code scheduledTasks.subscribe}, read-only). Same
	 * shared path as {@link #subscribeThread}: refcounted, resubscribed after every reconnect. Each
	 * item is a full {@code {tasks: [...]}} list ({@link ScheduledTask#listFrom}).
	 */
	public StreamRegistry.Subscription subscribeScheduledTasks(StreamRegistry.Listener listener) {
		return streams.subscribe(SCHEDULED_TASKS_STREAM, (id, key) -> RpcFrames.subscribeScheduledTasks(id), listener);
	}

	/** {@code orchestration.getTurnItem}: the full item (e.g. command output the stream withheld), or null. */
	public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
		JsonObject p = new JsonObject();
		p.addProperty("threadId", threadId);
		p.addProperty("itemId", itemId);
		return streams.call(RpcFrames.GET_TURN_ITEM, p).thenApply(values -> {
			JsonElement last = values.isEmpty() ? null : values.getLast();
			return last != null && last.isJsonObject() ? Json.obj(last.getAsJsonObject(), "item") : null;
		});
	}

	/** {@code orchestration.getThreadProjection}: the thread's full projection (read once). */
	public CompletableFuture<JsonObject> getThreadProjection(String threadId) {
		JsonObject p = new JsonObject();
		p.addProperty("threadId", threadId);
		return streams.call(RpcFrames.GET_THREAD_PROJECTION, p).thenApply(values -> {
			JsonElement last = values.isEmpty() ? null : values.getLast();
			return last != null && last.isJsonObject() ? last.getAsJsonObject() : new JsonObject();
		});
	}

	/**
	 * {@code orchestration.getOlderSettledThreads} (read): settled threads below {@code before},
	 * newest settle first, at most {@code limit} (0..{@value ArchiveWire#MAX_PAGE}).
	 */
	public CompletableFuture<ArchiveWire.OlderSettledPage> getOlderSettledThreads(ArchiveWire.Cursor before, int limit) {
		return streams.call(ArchiveWire.GET_OLDER_SETTLED_THREADS, ArchiveWire.olderSettledRequest(before, limit, null))
				.thenApply(ArchiveWire::decodeOlderSettledValues);
	}

	/** {@code orchestration.getArchivedShellSnapshot} (read): archived threads and their projects. */
	public CompletableFuture<ArchiveWire.ArchivedSnapshot> getArchivedShellSnapshot() {
		return streams.call(ArchiveWire.GET_ARCHIVED_SHELL_SNAPSHOT, ArchiveWire.archivedRequest()).thenApply(ArchiveWire::decodeArchivedValues);
	}

	/**
	 * {@code GET /api/orchestration/threads/:threadId/history?cursor=…} with this environment's
	 * session ({@code OrchestrationV2ThreadHistoryPage}). Fails with a secret-free message.
	 */
	public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
		Pairing.Session s = session.get();
		if (s == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("not paired"));
		}
		HttpRequest req = HttpRequest.newBuilder(historyUri(env.url(), threadId, cursor))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + s.accessToken())
				.header(PROTOCOL_HEADER, String.valueOf(ORCHESTRATION_PROTOCOL))
				.header("Accept", "application/json")
				.GET()
				.build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((res, err) -> {
			if (err != null) {
				throw new IllegalStateException(rootMessage(err));
			}
			int code = res.statusCode();
			if (code == 401 || code == 403) {
				throw new IllegalStateException("T3 refused the history request (" + code + ")");
			}
			if (code == 404) {
				throw new IllegalStateException("thread not found");
			}
			if (code != 200) {
				throw new IllegalStateException("history: HTTP " + code);
			}
			JsonElement el = JsonParser.parseString(res.body());
			if (!el.isJsonObject()) {
				throw new IllegalStateException("history: unexpected response");
			}
			return el.getAsJsonObject();
		});
	}

	/**
	 * Read-only {@code orchestration.searchThreads} on this connection's socket (no second
	 * connection; same request ids and send queue as every other RPC, via {@link StreamRegistry}).
	 * Completes with the decoded result, or exceptionally with a {@link ThreadSearch.SearchException}
	 * (sanitized) when not connected, on a T3 failure, when the socket drops or after
	 * {@code timeoutMs}. Cancelling the future interrupts the request on T3. {@code query} must have
	 * at least {@link RpcFrames#SEARCH_MIN_LENGTH} characters once trimmed.
	 */
	public CompletableFuture<ThreadSearch.Decoded> searchThreads(String query, int limit, long timeoutMs) {
		CompletableFuture<ThreadSearch.Decoded> f = new CompletableFuture<>();
		String q = RpcFrames.searchQuery(query);
		if (q.length() < RpcFrames.SEARCH_MIN_LENGTH) {
			f.completeExceptionally(new ThreadSearch.SearchException("query too short (at least " + RpcFrames.SEARCH_MIN_LENGTH + " characters)"));
			return f;
		}
		Status st = status;
		if (stopped || st != Status.OK) {
			f.completeExceptionally(new ThreadSearch.SearchException("not connected (" + st.wire + ")"));
			return f;
		}
		CompletableFuture<List<JsonElement>> raw = streams.call(id -> RpcFrames.searchThreads(id, q, limit, env.label()));
		raw.whenComplete((values, err) -> {
			if (err != null) {
				f.completeExceptionally(new ThreadSearch.SearchException(callError(err, st)));
				return;
			}
			try {
				f.complete(ThreadSearch.decode(values.isEmpty() ? null : values.getLast()));
			} catch (RuntimeException ex) {
				f.completeExceptionally(new ThreadSearch.SearchException("unexpected search result"));
			}
		});
		ScheduledFuture<?> timer;
		try {
			timer = exec.schedule(() -> {
				if (streams.abandon(raw)) {
					f.completeExceptionally(new ThreadSearch.SearchException("timed out after " + Math.max(1, timeoutMs / 1000) + " s"));
				}
			}, Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.RejectedExecutionException e) {
			raw.cancel(true);
			f.completeExceptionally(new ThreadSearch.SearchException("connection stopped"));
			return f;
		}
		f.whenComplete((r, err) -> {
			timer.cancel(false);
			if (f.isCancelled()) {
				raw.cancel(true); // StreamRegistry interrupts it on T3
			}
		});
		return f;
	}

	/** Secret-free message for a failed one-shot call. */
	private static String callError(Throwable err, Status st) {
		Throwable c = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
		String m = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
		return switch (m) {
			case "connection lost" -> "connection to T3 lost";
			case "not connected" -> "not connected (" + st.wire + ")";
			default -> "T3: " + sanitize(m);
		};
	}

	/** Threads with at least one subscriber (diagnostics, tests). */
	public int activeThreadSubscriptions() {
		return streams.activeThreads();
	}

	public static final String PROTOCOL_HEADER = "x-t3-orchestration-protocol";

	static URI historyUri(URI base, String threadId, String cursor) {
		return base.resolve("/api/orchestration/threads/" + URLEncoder.encode(threadId, StandardCharsets.UTF_8).replace("+", "%20")
				+ "/history?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
	}

	/** One send at a time per socket (the JDK WebSocket rejects overlapping sends). */
	private void send(WebSocket ws, String text) {
		synchronized (this) {
			sendTail = sendTail.handle((v, e) -> null).thenCompose(v -> ws.sendText(text, true));
		}
	}

	/** Removes anything that could carry a credential from error text. */
	public static String sanitize(@Nullable String s) {
		if (s == null) {
			return "";
		}
		return s.replaceAll("wsTicket=[^&\\s\"'<>]+", "wsTicket=<redacted>").replaceAll("(?i)Bearer\\s+\\S+", "Bearer <redacted>")
				.replaceAll("(?i)\\b(access_token|subject_token|token)=[^&\\s\"'<>]+", "$1=<redacted>")
				.replaceAll("(?i)\"(access_token|accessToken|subject_token|credential)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"<redacted>\"");
	}

	static URI socketUri(URI base, String ticket) {
		String scheme = "http".equals(base.getScheme()) ? "ws" : "wss";
		return URI.create(scheme + "://" + base.getRawAuthority() + "/ws?orchestrationProtocol=" + ORCHESTRATION_PROTOCOL
				+ "&wsTicket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
	}

	private boolean current(int g) {
		return !stopped && g == generation.get();
	}

	private void setStatus(int g, Status s, String d) {
		if (!current(g)) {
			return;
		}
		status = s;
		detail = sanitize(d);
		listener.onStatus(env.id(), s, detail);
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
			streams.onClosed();
		}
	}

	private void connect(int g) {
		if (!current(g)) {
			return;
		}
		Pairing.Session s = session.get();
		if (s == null) {
			setStatus(g, Status.NOT_PAIRED, "not paired: /t3craft pair");
			return;
		}
		if (s.expiresAtMs() > 0 && s.expiresAtMs() < System.currentTimeMillis()) {
			setStatus(g, Status.AUTH_FAILED, "session expired: pair again (/t3craft pair)");
			return;
		}
		setStatus(g, Status.CONNECTING, env.url().toString());
		HttpRequest req = HttpRequest.newBuilder(env.url().resolve("/api/auth/websocket-ticket/rpc"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + s.accessToken())
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
				setStatus(g, Status.AUTH_FAILED, "T3 rejected the session (" + res.statusCode() + "): revoked or expired, pair again");
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
			openSocket(g, ticket);
		}, exec);
	}

	private void openSocket(int g, String ticket) {
		ShellState state = new ShellState();
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(socketUri(env.url(), ticket), new Handler(g, state))
				.whenCompleteAsync((ws, err) -> {
					if (err == null) {
						if (!current(g)) {
							ws.abort();
						}
						return;
					}
					Throwable root = err;
					while (root.getCause() != null && !(root instanceof WebSocketHandshakeException)) {
						root = root.getCause();
					}
					if (root instanceof WebSocketHandshakeException h) {
						int code = h.getResponse().statusCode();
						if (code == 426) {
							setStatus(g, Status.INCOMPATIBLE, "T3 speaks another orchestration protocol (426); update T3Craft");
							return;
						}
						if (code == 401 || code == 403) {
							setStatus(g, Status.AUTH_FAILED, "T3 refused the WebSocket (" + code + "): pair again");
							return;
						}
						retry(g, "upgrade: HTTP " + code);
						return;
					}
					retry(g, "upgrade: " + rootMessage(err));
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
		setStatus(g, Status.OFFLINE, reason + " (retry in " + (delay / 1000) + " s)");
		pending = exec.schedule(() -> connect(g), delay, TimeUnit.MILLISECONDS);
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		return sanitize(c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage()));
	}

	private final class Handler implements WebSocket.Listener {
		private final int gen;
		private final ShellState state;
		private final StringBuilder buffer = new StringBuilder();
		private volatile long lastFrameMs = System.currentTimeMillis();
		private boolean ended;

		Handler(int gen, ShellState state) {
			this.gen = gen;
			this.state = state;
		}

		@Override
		public void onOpen(WebSocket ws) {
			exec.execute(() -> {
				if (!current(gen)) {
					ws.abort();
					return;
				}
				socket = ws;
				synchronized (T3Connection.this) {
					sendTail = CompletableFuture.completedFuture(null);
				}
				send(ws, RpcFrames.subscribeShell("1"));
				streams.onOpen(frame -> {
					if (socket != ws || !current(gen)) {
						return false;
					}
					send(ws, frame);
					return true;
				});
				pinger = exec.scheduleAtFixedRate(() -> {
					if (!current(gen) || ended) {
						return;
					}
					if (System.currentTimeMillis() - lastFrameMs > SILENCE_MS) {
						end("no data from T3 for " + (SILENCE_MS / 1000) + " s");
						return;
					}
					send(ws, RpcFrames.ping());
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
				exec.execute(() -> handle(ws, text));
			}
			ws.request(1);
			return null;
		}

		private void handle(WebSocket ws, String text) {
			if (!current(gen) || ended) {
				return;
			}
			RpcFrames.Frame f = RpcFrames.parse(text);
			switch (f) {
				case RpcFrames.Chunk c when !SHELL_REQUEST.equals(c.requestId()) -> {
					if (streams.onChunk(c.requestId(), c.values())) {
						send(ws, RpcFrames.ack(c.requestId()));
					}
				}
				case RpcFrames.Exit e when !SHELL_REQUEST.equals(e.requestId()) -> streams.onExit(e.requestId(), e.failure(), e.value());
				case RpcFrames.Chunk c -> {
					boolean changed = false;
					boolean wasSynced = state.isSynchronized();
					for (JsonObject item : c.values()) {
						changed |= state.apply(item);
					}
					send(ws, RpcFrames.ack(c.requestId()));
					if (state.isSynchronized() && !wasSynced) {
						attempt = 0;
						setStatus(gen, Status.OK, "");
					}
					if (changed || (state.isSynchronized() && !wasSynced)) {
						listener.onShell(env.id(), state.view());
					}
				}
				case RpcFrames.Exit e -> end(e.failure() == null ? "shell subscription ended" : "T3: " + e.failure());
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
				setStatus(gen, Status.AUTH_FAILED, sanitize(reason));
				return;
			}
			retry(gen, reason);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			exec.execute(() -> end("closed " + code + (reason == null || reason.isEmpty() ? "" : " " + reason)));
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			exec.execute(() -> end(rootMessage(error)));
		}
	}
}
