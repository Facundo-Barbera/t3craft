package dev.t3craft.outbound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * {@link Transport} over the JDK's {@link HttpClient}. Connect and request timeouts are short, redirects
 * are never followed (the body would go to a host the player did not configure) and the response body
 * is discarded. Errors are reported by exception class name only: some JDK messages carry the URL.
 */
public final class HttpTransport implements Transport {
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Override
	public Result send(URI url, Request request) {
		try {
			HttpRequest.Builder b = HttpRequest.newBuilder(url)
					.timeout(REQUEST_TIMEOUT)
					.header("Content-Type", request.contentType())
					.header("User-Agent", "T3Craft");
			for (Map.Entry<String, String> h : request.headers().entrySet()) {
				b.header(h.getKey(), h.getValue());
			}
			HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8)).build();
			HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
			long retry = resp.headers().firstValue("Retry-After")
					.map(v -> RetryAfter.parseMs(v, System.currentTimeMillis()))
					.orElse(-1L);
			return new Result(resp.statusCode(), retry, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Result.failure("interrupted");
		} catch (IOException | RuntimeException e) {
			return Result.failure(e.getClass().getSimpleName());
		}
	}
}
