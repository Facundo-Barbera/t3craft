package dev.t3craft.client.chat;

import java.net.URI;
import java.net.http.HttpClient;
import org.jspecify.annotations.Nullable;

/**
 * Minimal integration point between the chat and whatever knows the paired T3 environments.
 * The default ({@link ChatScreens#defaultSource()}) reads the direct-mode controller of
 * {@code T3CraftClient}; tests or other UIs can pass their own.
 */
public interface ChatSource {
	/** Where and how to read one environment. {@code accessToken} is a secret: never log it. */
	record Endpoint(String envId, String label, URI baseUrl, String accessToken) {
		@Override
		public String toString() {
			return "Endpoint[" + envId + ", " + baseUrl + ", accessToken=<redacted>]";
		}
	}

	/** Thrown with a player-facing reason (not paired, bridge mode, expired...). */
	final class Unavailable extends Exception {
		public Unavailable(String message) {
			super(message);
		}
	}

	Endpoint endpoint(String envId) throws Unavailable;

	/** HTTP client to reuse (shares the connection pool with the shell connections), or null for a new one. */
	@Nullable HttpClient http();
}
