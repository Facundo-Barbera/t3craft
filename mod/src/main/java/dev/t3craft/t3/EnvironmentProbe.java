package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * The public environment descriptor, {@code GET /.well-known/t3/environment} (no auth). Checked
 * <i>before</i> any credential is sent to a host: it must answer like a T3 server, speak
 * orchestration protocol {@link T3Connection#ORCHESTRATION_PROTOCOL}, and (for a paired
 * environment) be the same environment. That keeps a one-time pairing credential away from a wrong
 * or dead tunnel hostname and gives clear errors. Also picks the first reachable route of an
 * environment ({@link #firstReachable}). Messages carry no secret.
 */
public final class EnvironmentProbe {
	public static final String PATH = "/.well-known/t3/environment";
	static final Duration TIMEOUT = Duration.ofSeconds(6);

	private EnvironmentProbe() {
	}

	/**
	 * What the descriptor says. {@code environmentId} is null when the server does not publish one
	 * (then it is not pinned). {@code protocolVersion} is {@code orchestrationProtocolVersion}.
	 */
	public record Descriptor(@Nullable String environmentId, int protocolVersion) {
	}

	/** A failed probe, with a message for the player. */
	public static final class ProbeException extends Pairing.PairingException {
		public ProbeException(String message) {
			super(message);
		}
	}

	public static URI descriptorUri(URI base) {
		return base.resolve(PATH);
	}

	/**
	 * Parses the descriptor body. {@code orchestrationProtocolVersion} is required; the environment
	 * id is read from {@code environmentId}, or {@code environment.environmentId} / {@code environment.id}.
	 */
	public static Descriptor parse(String body) throws ProbeException {
		JsonObject o;
		try {
			JsonElement el = JsonParser.parseString(body);
			o = el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			o = null;
		}
		if (o == null) {
			throw new ProbeException("That address answered, but it is not a T3 server (unexpected descriptor).");
		}
		long version = Json.num(o, "orchestrationProtocolVersion", -1);
		if (version < 0) {
			throw new ProbeException("That address answered, but it is not a T3 server (no orchestration protocol in its descriptor).");
		}
		String id = Json.str(o, "environmentId");
		if (id == null) {
			JsonObject env = Json.obj(o, "environment");
			id = Json.str(env, "environmentId");
			if (id == null) {
				id = Json.str(env, "id");
			}
		}
		return new Descriptor(id == null || id.isBlank() ? null : id, (int) Math.min(version, Integer.MAX_VALUE));
	}

	/**
	 * Checks protocol and, when both are known, the environment id. {@code expectedEnvironmentId}
	 * null means a first pairing (nothing to compare).
	 */
	public static void check(Descriptor d, @Nullable String expectedEnvironmentId) throws ProbeException {
		if (d.protocolVersion() != T3Connection.ORCHESTRATION_PROTOCOL) {
			throw new ProbeException("That T3 speaks orchestration protocol " + d.protocolVersion() + "; T3Craft needs "
					+ T3Connection.ORCHESTRATION_PROTOCOL + ". Update T3 or T3Craft.");
		}
		if (expectedEnvironmentId != null && d.environmentId() != null && !expectedEnvironmentId.equals(d.environmentId())) {
			throw new ProbeException("That address is a different T3 environment than the one paired. Pair it again if you moved it.");
		}
	}

	/** Maps a non-200 status to a message. */
	static String describeStatus(int status, URI base) {
		if (status == 404) {
			return base.getHost() + " answered 404: it is not a T3 server, or it is too old to publish its descriptor.";
		}
		if (status == 502 || status == 503 || status == 504 || status == 530) {
			return base.getHost() + " is not answering (" + status + "). If it is a T3 Connect tunnel, the T3 machine may be off or the tunnel down.";
		}
		return base.getHost() + " answered HTTP " + status + " instead of a T3 descriptor.";
	}

	/** Fetches and checks the descriptor of {@code base}. Fails with {@link ProbeException}. */
	public static CompletableFuture<Descriptor> fetch(HttpClient http, URI base, @Nullable String expectedEnvironmentId) {
		HttpRequest req = HttpRequest.newBuilder(descriptorUri(base))
				.timeout(TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((res, err) -> {
			try {
				if (err != null) {
					Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
					throw new ProbeException("Could not reach " + base.getHost() + ": " + T3Connection.sanitize(cause.getClass().getSimpleName())
							+ ". Check the address; for a T3 Connect tunnel the T3 machine must be on.");
				}
				if (res.statusCode() != 200) {
					throw new ProbeException(describeStatus(res.statusCode(), base));
				}
				Descriptor d = parse(res.body());
				check(d, expectedEnvironmentId);
				return d;
			} catch (ProbeException e) {
				throw new CompletionException(e);
			}
		});
	}

	/**
	 * The first of {@code routes} (in order: LAN, Tailscale, tunnel...) whose descriptor checks out.
	 * Sequential, so a dead route costs at most {@link #TIMEOUT}. Fails with the last route's
	 * {@link ProbeException} when none answers.
	 */
	public static CompletableFuture<URI> firstReachable(HttpClient http, List<URI> routes, @Nullable String expectedEnvironmentId) {
		if (routes.isEmpty()) {
			return CompletableFuture.failedFuture(new ProbeException("This environment has no address."));
		}
		return tryRoute(http, routes, 0, expectedEnvironmentId);
	}

	private static CompletableFuture<URI> tryRoute(HttpClient http, List<URI> routes, int i, @Nullable String expectedEnvironmentId) {
		URI base = routes.get(i);
		return fetch(http, base, expectedEnvironmentId).handle((d, err) -> {
			if (err == null) {
				return CompletableFuture.completedFuture(base);
			}
			if (i + 1 < routes.size()) {
				return tryRoute(http, routes, i + 1, expectedEnvironmentId);
			}
			return CompletableFuture.<URI>failedFuture(err instanceof CompletionException && err.getCause() != null ? err.getCause() : err);
		}).thenCompose(f -> f);
	}
}
