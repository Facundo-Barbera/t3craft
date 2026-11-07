package dev.t3craft.client.portable.pending;

import dev.t3craft.client.portable.PortableThread;

/**
 * When the minimal conversation reader asks T3 again (pure, tested). Cheap by default: nothing while
 * nobody looks; at once when a thread is first shown; soon after its shell row changes (state or
 * latest message: the shell stream already tells us something happened, for free); otherwise a slow
 * safety poll, faster while the thread works. Never two requests at once.
 */
public final class FetchPolicy {
	/** Minimum gap between two reads of the same thread. */
	public static final long MIN_GAP_MS = 2_000;
	public static final long WORKING_POLL_MS = 6_000;
	public static final long IDLE_POLL_MS = 45_000;
	/** After a failure, wait at least this long (the shell connection reports outages anyway). */
	public static final long FAILURE_BACKOFF_MS = 10_000;

	private FetchPolicy() {
	}

	/**
	 * @param lastFetchMs   start of the last read of this thread, or 0 if never
	 * @param rowChanged    the thread's shell row differs from the one seen at the last read
	 * @param working       the thread is working (or needs attention: requests come and go)
	 * @param lastFailed    the last read failed
	 */
	public static boolean shouldFetch(long nowMs, long lastFetchMs, boolean inFlight, boolean rowChanged, boolean working, boolean lastFailed) {
		if (inFlight) {
			return false;
		}
		if (lastFetchMs <= 0) {
			return true;
		}
		long since = nowMs - lastFetchMs;
		if (lastFailed && since < FAILURE_BACKOFF_MS) {
			return false;
		}
		if (since < MIN_GAP_MS) {
			return false;
		}
		if (rowChanged) {
			return true;
		}
		return since >= (working ? WORKING_POLL_MS : IDLE_POLL_MS);
	}

	/** What about a row counts as "changed" (state + latest activity text). */
	public static String fingerprint(PortableThread t) {
		return t.state() + "|" + t.activity();
	}
}
