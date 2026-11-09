package dev.t3craft.t3;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Performs the token exchange of {@link Pairing} over HTTP: read-only by default, or the
 * optional operate pairing with {@link Pairing#OPERATE_SCOPES}. Always sends an explicit scope.
 * Before the one-time credential leaves the machine, the target answers
 * {@link EnvironmentProbe#PATH} like a T3 server on the right protocol (so a wrong or dead
 * tunnel hostname never receives it, and the player gets a clear error instead).
 */
public final class PairingClient {
	private final HttpClient http;

	public PairingClient(HttpClient http) {
		this.http = http;
	}

	/** A completed pairing: the session and what the public descriptor said about the environment. */
	public record Paired(Pairing.Session session, EnvironmentProbe.Descriptor descriptor) {
	}

	public CompletableFuture<Pairing.Session> exchange(Pairing.Target target, String clientLabel, String clientOs) {
		return exchange(target, clientLabel, clientOs, Pairing.READ_SCOPES);
	}

	/** {@code scopes} must be {@link Pairing#READ_SCOPES} or {@link Pairing#OPERATE_SCOPES}. */
	public CompletableFuture<Pairing.Session> exchange(Pairing.Target target, String clientLabel, String clientOs, java.util.Set<String> scopes) {
		return exchangeVerified(target, clientLabel, clientOs, scopes, null).thenApply(Paired::session);
	}

	/**
	 * Probes the descriptor of {@code target.baseUrl()} first, then exchanges the credential.
	 * {@code expectedEnvironmentId} (null on a first pairing) must match the descriptor's id: for
	 * "Enable control" on an environment that is already paired.
	 */
	public CompletableFuture<Paired> exchangeVerified(Pairing.Target target, String clientLabel, String clientOs,
			java.util.Set<String> scopes, String expectedEnvironmentId) {
		String body;
		try {
			body = Pairing.tokenRequestBody(target.credential(), clientLabel, clientOs, scopes);
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}
		return EnvironmentProbe.fetch(http, target.baseUrl(), expectedEnvironmentId).thenCompose(descriptor -> {
			HttpRequest req = HttpRequest.newBuilder(Pairing.tokenEndpoint(target.baseUrl()))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("Accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
				try {
					if (res.statusCode() != 200) {
						throw new Pairing.PairingException(Pairing.describeTokenError(res.statusCode(), res.body(), scopes));
					}
					return new Paired(Pairing.parseTokenResponse(res.body(), System.currentTimeMillis(), scopes), descriptor);
				} catch (Pairing.PairingException e) {
					throw new java.util.concurrent.CompletionException(e);
				}
			});
		});
	}
}
