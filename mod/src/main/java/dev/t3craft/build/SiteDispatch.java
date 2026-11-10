package dev.t3craft.build;

/**
 * feat/builder-sites: the dispatch of {@code build.order: "sites"}: which site (obra) the crew works and how many
 * builders it gets. Pure: plain numbers in, a choice out, no world access and no state ({@link SiteCursor} keeps
 * the state and applies the choice). Design: docs/plans/builder-scheduling.md.
 *
 * <p><b>The focus</b> is the site the crew is sent to:
 * <ol>
 *   <li>the site the player is <b>in</b> (its footprint, {@link SiteProgram#siteAt}), if it still has work;</li>
 *   <li>else the site already in focus, while it has work and the player is within {@link #KEEP} blocks of it: a
 *   site that was opened is not dropped because the player stepped outside;</li>
 *   <li>else the nearest site that was <b>started</b> and not finished, within {@link #KEEP} blocks: half-built
 *   buildings near the player are closed before a fresh one is opened;</li>
 *   <li>else the nearest site not started; else the nearest with anything left.</li>
 * </ol>
 * Distances are compared in buckets of {@link #BUCKET} blocks and the site index (the build order) breaks ties, so
 * the choice does not flip while the player walks about. {@link SiteCursor} adds its own hold before it changes
 * the focus, and never takes a builder off a bay it is raising.
 */
final class SiteDispatch {
	/** A site keeps the focus, and a started one keeps its place ahead of fresh ones, within this many blocks of the player. */
	static final double KEEP = 32.0;
	/** Distances are bucketed by this many blocks. */
	static final double BUCKET = 8.0;

	/** Site states for the dispatch. */
	static final byte IDLE = 0;
	static final byte STARTED = 1;
	static final byte DONE = 2;

	private SiteDispatch() {
	}

	/**
	 * @param current the site in focus now, -1 = none
	 * @param inside  the site the player stands in, -1 = none
	 * @param state   {@link #IDLE}, {@link #STARTED} or {@link #DONE} per site
	 * @param dist    blocks from the player to each site (0 inside its box); a site that must come last (the
	 *                details pass) gets {@link Double#MAX_VALUE}
	 * @return the site to work now, -1 when every site is done
	 */
	static int focus(int current, int inside, byte[] state, double[] dist) {
		if (inside >= 0 && state[inside] != DONE) {
			return inside;
		}
		if (current >= 0 && state[current] != DONE && dist[current] <= KEEP) {
			return current;
		}
		int started = -1;
		int fresh = -1;
		int any = -1;
		for (int s = 0; s < state.length; s++) {
			if (state[s] == DONE) {
				continue;
			}
			if (any < 0 || nearer(dist[s], dist[any])) {
				any = s;
			}
			if (state[s] == STARTED && dist[s] <= KEEP && (started < 0 || nearer(dist[s], dist[started]))) {
				started = s;
			}
			if (state[s] == IDLE && dist[s] < 1.0e9 && (fresh < 0 || nearer(dist[s], dist[fresh]))) {
				fresh = s;
			}
		}
		return started >= 0 ? started : fresh >= 0 ? fresh : any;
	}

	/** Whether distance {@code a} falls in a nearer bucket than {@code b} (equal buckets: no, the earlier site stays). */
	static boolean nearer(double a, double b) {
		return bucket(a) < bucket(b);
	}

	private static long bucket(double d) {
		return d >= 1.0e9 ? Long.MAX_VALUE : (long) Math.floor(Math.max(0, d) / BUCKET);
	}

	/**
	 * Builders for an open site: {@code min}, one more per {@code perBuilder} builder entries it has left, at most
	 * {@code max}; never more than the bays it can hand out now ({@code freeTasks}; a second builder may still help
	 * on the last bay), and never more than {@code available}.
	 */
	static int crew(int left, int freeTasks, int min, int max, int perBuilder, int available) {
		int want = Math.max(min, Math.min(max, min + left / Math.max(1, perBuilder)));
		want = Math.min(want, Math.max(Math.min(min, 2), freeTasks));
		return Math.max(0, Math.min(want, available));
	}
}
