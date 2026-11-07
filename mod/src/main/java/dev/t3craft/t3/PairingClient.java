package dev.t3craft.t3;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Performs the read-only token exchange of {@link Pairing} over HTTP. */
public final class PairingClient {
	private final HttpClient http;

	public PairingClient(HttpClient http) {
		this.http = http;
	}

	public CompletableFuture<Pairing.Session> exchange(Pairing.Target target, String clientLabel, String clientOs) {
		HttpRequest req = HttpRequest.newBuilder(Pairing.tokenEndpoint(target.baseUrl()))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(Pairing.tokenRequestBody(target.credential(), clientLabel, clientOs)))
				.build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			try {
				if (res.statusCode() != 200) {
					throw new Pairing.PairingException(Pairing.describeTokenError(res.statusCode(), res.body()));
				}
				return Pairing.parseTokenResponse(res.body(), System.currentTimeMillis());
			} catch (Pairing.PairingException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		});
	}
}
