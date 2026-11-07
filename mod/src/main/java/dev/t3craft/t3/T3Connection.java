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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Direct, read-only connection from the mod to one T3 environment:
 * {@code POST /api/auth/websocket-ticket/rpc} (Bearer) → {@code /ws?orchestrationProtocol=2&wsTicket=…}
 * → {@code orchestration.subscribeShell} with {@code requestCompletionMarker:true}, one Ack per
 * Chunk, Ping every 5 s, reconnect with jittered backoff and a fresh single-use ticket each time.
 * Only read-only RPCs are ever sent from this class: one-shot reads go through {@link #call}, which
 * only accepts the tags in {@link #ONE_SHOT_READS} and multiplexes them on the same socket (request
 * ids 2, 3, …; the shell subscription is id 1).
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
	private static final long PING_MS = 5_000;
	private static final long SILENCE_MS = 20_000;
	private static final long MIN_BACKOFF_MS = 1_000;
	private static final long MAX_BACKOFF_MS = 30_000;
	/** A one-shot read that got no Exit by then fails (and is interrupted on the server). */
	static final long CALL_TIMEOUT_MS = 15_000;
	private static final String SHELL_REQUEST_ID = "1";
	/** The only one-shot RPCs this class sends (all {@code orchestration:read}). */
	public static final Set<String> ONE_SHOT_READS = Set.of(RpcFrames.GET_OLDER_SETTLED_THREADS, RpcFrames.GET_ARCHIVED_SHELL_SNAPSHOT);

	/** Thrown into a one-shot read's future: not connected, timed out, socket dropped or T3 failure. */
	public static final class CallException extends RuntimeException {
		public CallException(String message) {
			super(sanitize(message), null, false, false);
		}
	}

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
	/** Open one-shot reads by request id (connection thread only). */
	private final Map<String, CompletableFuture<JsonElement>> calls = new HashMap<>();
	private long nextRequestId = 2;

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

	/**
	 * Older settled threads below {@code before}, newest settle first, at most {@code limit}
	 * (0..{@value ArchiveWire#MAX_PAGE}; 0 reads only the counts). Fails with {@link CallException}
	 * when the environment is not connected. Completes on this connection's thread.
	 */
	public CompletableFuture<ArchiveWire.OlderSettledPage> getOlderSettledThreads(ArchiveWire.Cursor before, int limit) {
		return call(RpcFrames.GET_OLDER_SETTLED_THREADS, ArchiveWire.olderSettledRequest(before, limit, null), ArchiveWire::decodeOlderSettled);
	}

	/** Archived threads and their projects. Same failure and threading rules as {@link #getOlderSettledThreads}. */
	public CompletableFuture<ArchiveWire.ArchivedSnapshot> getArchivedShellSnapshot() {
		return call(RpcFrames.GET_ARCHIVED_SHELL_SNAPSHOT, new JsonObject(), ArchiveWire::decodeArchived);
	}

	/**
	 * Sends one read-only, one-shot RPC on the open socket and decodes its Exit value. Only tags in
	 * {@link #ONE_SHOT_READS} are accepted; anything else fails without being sent.
	 */
	<T> CompletableFuture<T> call(String tag, JsonObject payload, Function<@Nullable JsonElement, T> decode) {
		CompletableFuture<JsonElement> raw = new CompletableFuture<>();
		if (!ONE_SHOT_READS.contains(tag)) {
			raw.completeExceptionally(new CallException("not a read-only one-shot RPC: " + tag));
			return raw.thenApply(decode);
		}
		try {
			exec.execute(() -> {
				WebSocket ws = socket;
				if (stopped || ws == null || status != Status.OK) {
					raw.completeExceptionally(new CallException("not connected (" + status.wire + ")"));
					return;
				}
				String id = Long.toString(nextRequestId++);
				calls.put(id, raw);
				ws.sendText(RpcFrames.request(id, tag, payload), true);
				exec.schedule(() -> {
					CompletableFuture<JsonElement> f = calls.remove(id);
					if (f != null) {
						WebSocket now = socket;
						if (now != null) {
							now.sendText(RpcFrames.interrupt(id), true);
						}
						f.completeExceptionally(new CallException(tag + ": no answer in " + (CALL_TIMEOUT_MS / 1000) + " s"));
					}
				}, CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			});
		} catch (java.util.concurrent.RejectedExecutionException e) {
			raw.completeExceptionally(new CallException("connection stopped"));
		}
		return raw.thenApply(decode);
	}

	/** Completes a one-shot read from its Exit. Returns false when the id is not an open call. */
	private boolean finishCall(RpcFrames.Exit e) {
		CompletableFuture<JsonElement> f = calls.remove(e.requestId());
		if (f == null) {
			return false;
		}
		if (e.failure() != null) {
			f.completeExceptionally(new CallException("T3: " + e.failure()));
		} else {
			f.complete(e.value());
		}
		return true;
	}

	private void failCalls(String reason) {
		if (calls.isEmpty()) {
			return;
		}
		Map<String, CompletableFuture<JsonElement>> open = new HashMap<>(calls);
		calls.clear();
		open.values().forEach(f -> f.completeExceptionally(new CallException(reason)));
	}

	/** Removes anything that could carry a credential from error text. */
	public static String sanitize(@Nullable String s) {
		if (s == null) {
			return "";
		}
		return s.replaceAll("wsTicket=[^&\\s\"'<>]+", "wsTicket=<redacted>").replaceAll("(?i)Bearer\\s+\\S+", "Bearer <redacted>");
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
		}
		failCalls("connection closed");
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
				ws.sendText(RpcFrames.subscribeShell(SHELL_REQUEST_ID), true);
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
				case RpcFrames.Chunk c when !SHELL_REQUEST_ID.equals(c.requestId()) -> ws.sendText(RpcFrames.ack(c.requestId()), true);
				case RpcFrames.Exit e when !SHELL_REQUEST_ID.equals(e.requestId()) -> finishCall(e);
				case RpcFrames.Chunk c -> {
					boolean changed = false;
					boolean wasSynced = state.isSynchronized();
					for (JsonObject item : c.values()) {
						changed |= state.apply(item);
					}
					ws.sendText(RpcFrames.ack(c.requestId()), true);
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
