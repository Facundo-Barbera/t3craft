package dev.t3craft.t3;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Performs the token exchange of {@link Pairing} over HTTP: read-only by default, or the
 * optional operate pairing with {@link Pairing#OPERATE_SCOPES}. Always sends an explicit scope.
 */
public final class PairingClient {
	private final HttpClient http;

	public PairingClient(HttpClient http) {
		this.http = http;
	}

	public CompletableFuture<Pairing.Session> exchange(Pairing.Target target, String clientLabel, String clientOs) {
		return exchange(target, clientLabel, clientOs, Pairing.READ_SCOPES);
	}

	/** {@code scopes} must be {@link Pairing#READ_SCOPES} or {@link Pairing#OPERATE_SCOPES}. */
	public CompletableFuture<Pairing.Session> exchange(Pairing.Target target, String clientLabel, String clientOs, java.util.Set<String> scopes) {
		String body;
		try {
			body = Pairing.tokenRequestBody(target.credential(), clientLabel, clientOs, scopes);
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}
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
				return Pairing.parseTokenResponse(res.body(), System.currentTimeMillis(), scopes);
			} catch (Pairing.PairingException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		});
	}
}
