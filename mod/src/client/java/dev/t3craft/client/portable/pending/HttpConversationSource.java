package dev.t3craft.client.portable.pending;

import dev.t3craft.client.portable.PortableThread;

import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.Pairing;
import dev.t3craft.t3.T3Connection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Minimal read-only conversation reader (until feat/chat's live transcript replaces it, see
 * {@link ConversationSource}): one {@code GET /api/orchestration/threads/:threadId/bounded} with
 * the paired session's Bearer token and {@code x-t3-orchestration-protocol: 2}. T3 checks
 * {@code orchestration:read} for it (apps/server/src/orchestration-v2/http.ts). It reuses the
 * direct controller's HTTP client (same connection pool as the shell connections); no WebSocket
 * is opened. Reads only when {@link FetchPolicy} says so and only while something shows it.
 *
 * <p>Secrets: the token is read from {@link Environments#credential} at request time and never
 * stored, logged or put in error text ({@link T3Connection#sanitize}).
 */
public final class HttpConversationSource implements ConversationSource {
	public static final String PROTOCOL_HEADER = "x-t3-orchestration-protocol";

	private final T3CraftClient client;
	private final AtomicLong revision = new AtomicLong();
	private volatile Conversation current = Conversation.NONE;
	private @Nullable PortableThread selected;
	private long lastFetchMs;
	private String lastFingerprint = "";
	private volatile boolean inFlight;
	private volatile boolean lastFailed;
	/** Bumped on select: answers for an older selection are dropped. */
	private final AtomicLong generation = new AtomicLong();

	public HttpConversationSource(T3CraftClient client) {
		this.client = client;
	}

	@Override
	public void select(@Nullable PortableThread thread) {
		if (thread != null && selected != null && thread.id().equals(selected.id())) {
			selected = thread;
			return;
		}
		generation.incrementAndGet();
		selected = thread;
		lastFetchMs = 0;
		lastFailed = false;
		lastFingerprint = "";
		inFlight = false;
		publish(thread == null ? Conversation.NONE : Conversation.loading(thread.id()));
	}

	@Override
	public void tick(@Nullable PortableThread thread, long nowMs) {
		PortableThread sel = selected;
		if (sel == null) {
			return;
		}
		if (thread != null && thread.id().equals(sel.id())) {
			selected = sel = thread;
		}
		boolean rowChanged = !FetchPolicy.fingerprint(sel).equals(lastFingerprint);
		boolean working = "working".equals(sel.state()) || sel.needsAttention();
		if (!FetchPolicy.shouldFetch(nowMs, lastFetchMs, inFlight, rowChanged, working, lastFailed)) {
			return;
		}
		lastFetchMs = nowMs;
		lastFingerprint = FetchPolicy.fingerprint(sel);
		fetch(sel);
	}

	@Override
	public Conversation current() {
		return current;
	}

	@Override
	public long revision() {
		return revision.get();
	}

	@Override
	public void close() {
		generation.incrementAndGet();
		selected = null;
		publish(Conversation.NONE);
	}

	private void publish(Conversation c) {
		current = c;
		revision.incrementAndGet();
	}

	private void fetch(PortableThread t) {
		if (t.threadId() == null || t.env().isEmpty()) {
			publish(Conversation.unavailable(t.id(), "This thread has no T3 id here; open it from direct mode."));
			return;
		}
		DirectController direct = client.direct();
		if (direct == null) {
			publish(Conversation.unavailable(t.id(), "The conversation is read straight from T3: it needs direct mode (/t3craft pair)."));
			return;
		}
		Environments envs = direct.environments();
		Environments.Environment env = envs.list().stream().filter(e -> e.id().equals(t.env())).findFirst().orElse(null);
		Pairing.Session session = env == null ? null : envs.credential(env.id());
		if (env == null || session == null) {
			publish(Conversation.unavailable(t.id(), "Environment " + t.envLabel() + " is not paired here (/t3craft pair)."));
			return;
		}
		if (session.expiresAtMs() > 0 && session.expiresAtMs() < System.currentTimeMillis()) {
			publish(Conversation.unavailable(t.id(), "The session for " + env.label() + " expired: pair again."));
			return;
		}
		HttpRequest req = HttpRequest.newBuilder(boundedUri(env.url(), t.threadId()))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + session.accessToken())
				.header(PROTOCOL_HEADER, String.valueOf(T3Connection.ORCHESTRATION_PROTOCOL))
				.header("Accept", "application/json")
				.GET()
				.build();
		long gen = generation.get();
		inFlight = true;
		HttpClient http = direct.http();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
			if (gen != generation.get()) {
				return;
			}
			inFlight = false;
			Conversation before = current;
			if (err != null) {
				lastFailed = true;
				publish(before.failed("Could not reach " + env.label() + " (" + T3Connection.sanitize(err.getClass().getSimpleName()) + ")"));
				return;
			}
			int code = res.statusCode();
			if (code != 200) {
				lastFailed = true;
				publish(before.failed(describe(code, env.label())));
				return;
			}
			try {
				ConversationParser.Parsed p = ConversationParser.parse(JsonParser.parseString(res.body()));
				lastFailed = false;
				publish(new Conversation(t.id(), Conversation.Status.LIVE, "", p.lines(), p.hasOlder(), System.currentTimeMillis()));
			} catch (RuntimeException e) {
				lastFailed = true;
				T3Craft.LOGGER.debug("portable terminal: unreadable thread snapshot", e);
				publish(before.failed("T3 sent a thread snapshot this version cannot read"));
			}
		});
	}

	static URI boundedUri(URI base, String threadId) {
		return base.resolve("/api/orchestration/threads/" + URLEncoder.encode(threadId, StandardCharsets.UTF_8).replace("+", "%20") + "/bounded");
	}

	static String describe(int code, String envLabel) {
		return switch (code) {
			case 401, 403 -> envLabel + " refused the read (" + code + "): session revoked, expired or missing orchestration:read";
			case 404 -> "Thread not found on " + envLabel + " (archived or deleted?)";
			case 426 -> envLabel + " speaks another orchestration protocol (426)";
			default -> envLabel + " answered HTTP " + code;
		};
	}
}
