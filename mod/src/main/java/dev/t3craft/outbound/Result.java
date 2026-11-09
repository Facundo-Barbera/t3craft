package dev.t3craft.outbound;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of one POST. {@code status} 0 means no HTTP answer (timeout, DNS, TLS…): then {@code error}
 * holds the exception's class name only, never its message (the message of some JDK exceptions
 * contains the URL, which is a secret). {@code retryAfterMs} is the server's {@code Retry-After}, or -1.
 */
public record Result(int status, long retryAfterMs, @Nullable String error) {
	public boolean ok() {
		return status >= 200 && status < 300;
	}

	public static Result failure(String error) {
		return new Result(0, -1, error);
	}

	/** Short text for the settings screen and the log. Never contains the URL. */
	public String describe() {
		if (error != null) {
			return "failed: " + error;
		}
		return ok() ? "sent (HTTP " + status + ")" : "failed: HTTP " + status;
	}
}
