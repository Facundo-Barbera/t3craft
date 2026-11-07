package dev.t3craft.client.link;

import java.util.concurrent.ThreadLocalRandom;

/** Exponential backoff with full jitter: 0.5s, 1s, 2s ... capped at 15s. */
public final class Backoff {
	private static final long BASE_MS = 500;
	private static final long MAX_MS = 15_000;
	private int attempt;

	public long nextDelayMs() {
		long cap = Math.min(MAX_MS, BASE_MS << Math.min(attempt, 10));
		attempt++;
		return cap / 2 + ThreadLocalRandom.current().nextLong(cap / 2 + 1);
	}

	public void reset() {
		attempt = 0;
	}

	public int attempt() {
		return attempt;
	}
}
