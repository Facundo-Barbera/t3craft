package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The operate socket of one environment, managed by {@link DirectController} next to that
 * environment's read-only {@link T3Connection} (which keeps the read token only). Opened on demand
 * with the operate session (ticket → {@code /ws}), shared by every action of that environment,
 * closed after {@link #IDLE_MS} without calls. One Request per action, completed by its
 * {@code Exit}. Writes are never retried automatically: if the socket drops after sending, the
 * result is {@link OperateResult.Outcome#FAILED} ("may or may not have applied").
 */
public final class OperateConnection implements OperateTransport {
	static final long IDLE_MS = 60_000;
	static final long CALL_TIMEOUT_MS = 20_000;
	private static final long PING_MS = 5_000;

	private final Environments.Environment env;
	private final Supplier<Pairing.@Nullable Session> session;
	private final HttpClient http;
	private final ScheduledExecutorService exec;
	private final List<Call> queued = new ArrayList<>();
	private final Map<String, Call> inflight = new LinkedHashMap<>();
	private int nextId = 1;
	private int generation;
	private boolean connecting;
	private volatile boolean stopped;
	private @Nullable WebSocket socket;
	private @Nullable ScheduledFuture<?> idle;
	private @Nullable ScheduledFuture<?> pinger;

	private record Call(String id, String frame, CompletableFuture<OperateResult> future) {
	}

	public OperateConnection(Environments.Environment env, Supplier<Pairing.@Nullable Session> session, HttpClient http) {
		this.env = env;
		this.session = session;
		this.http = http;
		this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "t3craft-operate-" + env.id());
			t.setDaemon(true);
			return t;
		});
	}

	public Environments.Environment environment() {
		return env;
	}

	@Override
	public CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload) {
		CompletableFuture<OperateResult> f = new CompletableFuture<>();
		if (stopped) {
			f.complete(OperateResult.of(OperateResult.Outcome.OFFLINE, "connection stopped"));
			return f;
		}
		try {
			exec.execute(() -> {
				String id = Integer.toString(nextId++);
				Call c = new Call(id, RpcFrames.request(id, rpcTag, payload), f);
				exec.schedule(() -> timeout(c), CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				cancelIdle();
				WebSocket ws = socket;
				if (ws != null) {
					send(ws, c);
				} else {
					queued.add(c);
					ensureConnecting();
				}
			});
		} catch (RejectedExecutionException e) {
			f.complete(OperateResult.of(OperateResult.Outcome.OFFLINE, "connection stopped"));
		}
		return f;
	}

	public void stop() {
		stopped = true;
		try {
			exec.execute(() -> {
				generation++;
				failAll(OperateResult.Outcome.OFFLINE, "connection stopped", OperateResult.Outcome.FAILED,
						"connection stopped after sending: check T3, it may have applied");
				closeSocket();
			});
		} catch (RejectedExecutionException ignored) {
			// already stopped
		}
		exec.shutdown();
	}

	private void send(WebSocket ws, Call c) {
		inflight.put(c.id(), c);
		ws.sendText(c.frame(), true);
	}

	private void timeout(Call c) {
		if (inflight.remove(c.id()) != null) {
			c.future().complete(OperateResult.of(OperateResult.Outcome.FAILED,
					"no answer from T3 in " + (CALL_TIMEOUT_MS / 1000) + " s: check T3, it may have applied"));
		} else if (queued.remove(c)) {
			c.future().complete(OperateResult.of(OperateResult.Outcome.OFFLINE, "could not reach T3 in " + (CALL_TIMEOUT_MS / 1000) + " s"));
		}
		scheduleIdle();
	}

	private void ensureConnecting() {
		if (connecting || socket != null) {
			return;
		}
		Pairing.Session s = session.get();
		if (s == null) {
			failQueued(OperateResult.Outcome.READ_ONLY, "control (operate) is not enabled for " + env.id());
			return;
		}
		connecting = true;
		int g = ++generation;
		if (env.fallbacks().isEmpty()) {
			requestTicket(g, s, env.url());
			return;
		}
		// Several routes: the first whose public descriptor checks out (same rule as the read socket).
		EnvironmentProbe.firstReachable(http, env.routes(), env.environmentId()).whenCompleteAsync((chosen, err) -> {
			if (g != generation) {
				return;
			}
			if (err != null) {
				connectFailed(OperateResult.Outcome.OFFLINE, "no route answers: " + T3Connection.routeError(err));
				return;
			}
			requestTicket(g, s, chosen);
		}, exec);
	}

	private void requestTicket(int g, Pairing.Session s, java.net.URI base) {
		HttpRequest req = HttpRequest.newBuilder(base.resolve("/api/auth/websocket-ticket/rpc"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", "Bearer " + s.accessToken())
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenCompleteAsync((res, err) -> {
			if (g != generation) {
				return;
			}
			if (err != null) {
				connectFailed(OperateResult.Outcome.OFFLINE, "ticket: " + rootMessage(err));
				return;
			}
			if (res.statusCode() == 401 || res.statusCode() == 403) {
				connectFailed(OperateResult.Outcome.AUTH_FAILED, "T3 rejected the operate session (" + res.statusCode() + "): revoked or expired, enable control again");
				return;
			}
			if (res.statusCode() != 200) {
				connectFailed(OperateResult.Outcome.OFFLINE, "ticket: HTTP " + res.statusCode());
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
				connectFailed(OperateResult.Outcome.OFFLINE, "ticket: unexpected response");
				return;
			}
			http.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(T3Connection.socketUri(base, ticket), new Handler(g))
					.whenCompleteAsync((ws, e2) -> {
						if (e2 == null) {
							if (g != generation) {
								ws.abort();
							}
							return;
						}
						Throwable root = e2;
						while (root.getCause() != null && !(root instanceof WebSocketHandshakeException)) {
							root = root.getCause();
						}
						if (g != generation) {
							return;
						}
						if (root instanceof WebSocketHandshakeException h) {
							int code = h.getResponse().statusCode();
							connectFailed(code == 401 || code == 403 ? OperateResult.Outcome.AUTH_FAILED : OperateResult.Outcome.OFFLINE,
									code == 426 ? "T3 speaks another orchestration protocol (426)" : "upgrade: HTTP " + code);
							return;
						}
						connectFailed(OperateResult.Outcome.OFFLINE, "upgrade: " + rootMessage(e2));
					}, exec);
		}, exec);
	}

	private void connectFailed(OperateResult.Outcome outcome, String reason) {
		connecting = false;
		failQueued(outcome, reason);
	}

	private void failQueued(OperateResult.Outcome outcome, String reason) {
		List<Call> q = new ArrayList<>(queued);
		queued.clear();
		q.forEach(c -> c.future().complete(OperateResult.of(outcome, reason)));
	}

	private void failAll(OperateResult.Outcome queuedOutcome, String queuedReason, OperateResult.Outcome inflightOutcome, String inflightReason) {
		failQueued(queuedOutcome, queuedReason);
		List<Call> f = new ArrayList<>(inflight.values());
		inflight.clear();
		f.forEach(c -> c.future().complete(OperateResult.of(inflightOutcome, inflightReason)));
	}

	private void cancelIdle() {
		if (idle != null) {
			idle.cancel(false);
			idle = null;
		}
	}

	private void scheduleIdle() {
		if (!inflight.isEmpty() || !queued.isEmpty() || socket == null || stopped) {
			return;
		}
		cancelIdle();
		int g = generation;
		idle = exec.schedule(() -> {
			if (g == generation && inflight.isEmpty() && queued.isEmpty()) {
				generation++;
				closeSocket();
			}
		}, IDLE_MS, TimeUnit.MILLISECONDS);
	}

	private void closeSocket() {
		cancelIdle();
		if (pinger != null) {
			pinger.cancel(false);
			pinger = null;
		}
		connecting = false;
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			ws.abort();
		}
	}

	private void lost(int g, String reason) {
		if (g != generation) {
			return;
		}
		generation++;
		closeSocket();
		String why = T3Connection.sanitize(reason);
		OperateResult.Outcome auth = why.contains("EnvironmentAuthorizationError") ? OperateResult.Outcome.AUTH_FAILED : null;
		failAll(auth != null ? auth : OperateResult.Outcome.OFFLINE, "connection lost: " + why,
				auth != null ? auth : OperateResult.Outcome.FAILED, "connection lost after sending (" + why + "): check T3, it may have applied");
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

		Handler(int gen) {
			this.gen = gen;
		}

		@Override
		public void onOpen(WebSocket ws) {
			exec.execute(() -> {
				if (gen != generation) {
					ws.abort();
					return;
				}
				connecting = false;
				socket = ws;
				List<Call> q = new ArrayList<>(queued);
				queued.clear();
				q.forEach(c -> send(ws, c));
				pinger = exec.scheduleAtFixedRate(() -> {
					if (gen == generation && socket == ws) {
						ws.sendText(RpcFrames.ping(), true);
					}
				}, PING_MS, PING_MS, TimeUnit.MILLISECONDS);
				scheduleIdle();
			});
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
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
			if (gen != generation) {
				return;
			}
			switch (RpcFrames.parse(text)) {
				case RpcFrames.Exit e -> {
					Call c = inflight.remove(e.requestId());
					if (c != null) {
						if (e.failure() == null) {
							c.future().complete(OperateResult.ok(RpcFrames.exitValue(text)));
						} else if (e.failure().contains("EnvironmentAuthorizationError")) {
							c.future().complete(OperateResult.of(OperateResult.Outcome.AUTH_FAILED, "T3: " + e.failure()));
						} else {
							c.future().complete(OperateResult.of(OperateResult.Outcome.REJECTED, "T3: " + e.failure()));
						}
					}
					scheduleIdle();
				}
				case RpcFrames.Chunk c -> ws.sendText(RpcFrames.ack(c.requestId()), true);
				case RpcFrames.Defect d -> lost(gen, "T3 protocol error: " + d.detail());
				case RpcFrames.Pong p -> {
				}
				case RpcFrames.Other o -> {
				}
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
			exec.execute(() -> lost(gen, "closed " + code + (reason == null || reason.isEmpty() ? "" : " " + reason)));
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			exec.execute(() -> lost(gen, rootMessage(error)));
		}
	}
}
