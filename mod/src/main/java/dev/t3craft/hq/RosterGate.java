package dev.t3craft.hq;

import java.util.Collection;

/**
 * When the HQ in the world may be reconciled against the live roster. Building (and, above all,
 * <b>removing</b>) desks from a roster that is still loading would tear down every station of an
 * environment that has not answered yet, so on world load the HQ waits until <b>every paired
 * environment has delivered its first synced shell</b>.
 *
 * <ul>
 *   <li>No environment paired: the roster is whatever the client shows (the demo's example HQ):
 *   {@link Decision#RECONCILE} right away.</li>
 *   <li>Every paired environment synced: {@link Decision#RECONCILE}.</li>
 *   <li>Otherwise {@link Decision#WAIT}. Exception: when nothing has been built yet (no HQ to tear
 *   down) and {@code waitedMs >= firstBuildTimeoutMs}, the first build goes ahead with what is
 *   there; later syncs reconcile by diff.</li>
 * </ul>
 * Pure.
 */
public final class RosterGate {
	/** How long a first build waits for slow environments. */
	public static final long FIRST_BUILD_TIMEOUT_MS = 30_000;

	private RosterGate() {
	}

	public enum Decision {
		WAIT, RECONCILE
	}

	/**
	 * @param paired      environments the client is paired with (direct mode); bridge mode passes
	 *                    {@code ["bridge"]} until its first NPC snapshot, then also in {@code synced}
	 * @param synced      environments whose first shell has arrived
	 * @param builtBefore the world already holds an HQ from an earlier session
	 * @param waitedMs    time since the world was entered
	 */
	public static Decision decide(Collection<String> paired, Collection<String> synced, boolean builtBefore, long waitedMs,
			long firstBuildTimeoutMs) {
		if (synced.containsAll(paired)) {
			return Decision.RECONCILE;
		}
		if (!builtBefore && waitedMs >= firstBuildTimeoutMs) {
			return Decision.RECONCILE;
		}
		return Decision.WAIT;
	}

	public static Decision decide(Collection<String> paired, Collection<String> synced, boolean builtBefore, long waitedMs) {
		return decide(paired, synced, builtBefore, waitedMs, FIRST_BUILD_TIMEOUT_MS);
	}
}
