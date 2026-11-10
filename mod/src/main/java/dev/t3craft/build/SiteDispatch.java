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
 *
 * <p><b>Several sites open</b> ({@code build.sitesOpen}, 2 by default). When the focus moves on, the site left
 * behind first closes the storey it is on with the crew it has; then the main crew goes and a <b>rear guard</b> of
 * {@link #REAR} builders stays and keeps raising it, storey by storey, while the player is within {@link #KEEP}
 * blocks. Beyond that the rear guard closes its storey and the site is frozen (it comes back first when the player
 * returns: rules 1 and 3). A started site near the player that nobody works gets a rear guard too, while a slot and
 * two builders are free. {@link #openLimit}: one more site may be open when the crew has builders to spare. The
 * focus gets {@link #FOCUS_SHARE} of the pace, the others the rest.
 *
 * <p>#53: with {@code build.sitesAtOnce} over 0 the sites are built in parallel instead ({@link #activeLimit},
 * {@link #retainSlots}, {@link #shares}, {@link #nearestFresh}; the state lives in {@code SiteCursor.dispatchParallel}).
 */
final class SiteDispatch {
	/** A site keeps the focus, and a started one keeps its place ahead of fresh ones, within this many blocks of the player. */
	static final double KEEP = 32.0;
	/** Distances are bucketed by this many blocks. */
	static final double BUCKET = 8.0;

	/** Builders of a rear guard. */
	static final int REAR = 2;
	/** Share of the builders' pace the focus gets while other sites are being worked too. */
	static final double FOCUS_SHARE = 0.7;

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
		return focus(current, inside, state, dist, true);
	}

	/**
	 * Like {@link #focus(int, int, byte[], double[])}. {@code allowFresh} false (#48: too many sites are started and
	 * not finished, {@link #STARTED_MAX}): rules 3 and 4 do not start a new site, the nearest started one is closed
	 * first, at any distance. The site the player stands in (rule 1) still opens.
	 */
	static int focus(int current, int inside, byte[] state, double[] dist, boolean allowFresh) {
		if (inside >= 0 && state[inside] != DONE) {
			return inside;
		}
		if (current >= 0 && state[current] != DONE && dist[current] <= KEEP) {
			return current;
		}
		int started = -1;
		int startedFar = -1;
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
			if (state[s] == STARTED && (startedFar < 0 || nearer(dist[s], dist[startedFar]))) {
				startedFar = s;
			}
			if (state[s] == IDLE && dist[s] < 1.0e9 && (fresh < 0 || nearer(dist[s], dist[fresh]))) {
				fresh = s;
			}
		}
		if (started >= 0) {
			return started;
		}
		if (!allowFresh && startedFar >= 0) {
			return startedFar;
		}
		return fresh >= 0 ? fresh : any;
	}

	/** #48: sites started and not finished at most before rules 3 and 4 open no new one (rule 1, the player's, still does). */
	static final int STARTED_MAX = 3;

	/**
	 * Sites that may be open at once: {@code sitesOpen} (the focus and the ones being closed), plus one when the crew
	 * still has a rear guard to spare after a full focus crew and the rear guards of the others. 1 stays 1 (one site at
	 * a time, as in phase 1).
	 */
	static int openLimit(int sitesOpen, int builders, int crewMax) {
		if (sitesOpen <= 1) {
			return 1;
		}
		int spare = builders - crewMax - REAR * (sitesOpen - 1);
		return sitesOpen + (spare >= REAR ? 1 : 0);
	}

	/**
	 * The nearest site that was started, is not finished, is not open and is within {@link #KEEP} blocks: the one a
	 * rear guard is sent to by proximity; -1 = none.
	 */
	static int nearestStarted(byte[] state, double[] dist, boolean[] isOpen) {
		int best = -1;
		for (int s = 0; s < state.length; s++) {
			if (state[s] == STARTED && !isOpen[s] && dist[s] <= KEEP && (best < 0 || dist[s] < dist[best])) {
				best = s;
			}
		}
		return best;
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

	// ---- #53: sites in parallel (build.sitesAtOnce over 0) ----------------------------------------

	/** Builders a <b>retained</b> site keeps: one more site than the limit stays open with this many, never with none. */
	static final int RETAIN = 1;
	/** Retained sites at most, past {@code build.sitesAtOnce}; past them too the farthest closes at its next storey boundary. */
	static final int RETAIN_MAX = 2;
	/** Builders a site that closes at its next storey boundary ({@code leaving}) keeps at most. */
	static final int LEAVING_CREW = REAR;
	/** A fresh site this near a player on the ground opens by itself when builders are to spare (after a hold). */
	static final double NEAR_FRESH = 16.0;

	/**
	 * Sites built with a full crew at once: {@code atOnce}, but never more than the crew can give {@code crewMin} each
	 * (so every one of them gets its minimum); at least 1.
	 */
	static int activeLimit(int atOnce, int builders, int crewMin) {
		return Math.max(1, Math.min(atOnce, builders / Math.max(1, crewMin)));
	}

	/** Retained sites the crew can still keep ({@link #RETAIN} each) after {@code active} sites got {@code crewMin} each; at most {@link #RETAIN_MAX}. */
	static int retainSlots(int active, int builders, int crewMin) {
		return Math.max(0, Math.min(RETAIN_MAX, (builders - active * Math.max(1, crewMin)) / RETAIN));
	}

	/**
	 * How many builders each open site gets, the sites given in priority order (the player's site first). Three passes:
	 * every site gets its {@code floor} (so no open site with work stands with none), then each one in order up to
	 * {@code cap} (what it can use now: the player's site takes the most), then what is left goes one by one, in order,
	 * up to {@code hard}. Builders past every {@code hard} stay free. Never hands out more than {@code builders}.
	 */
	static int[] shares(int builders, int[] floor, int[] cap, int[] hard) {
		int n = floor.length;
		int[] out = new int[n];
		int left = Math.max(0, builders);
		for (int k = 0; k < n && left > 0; k++) {
			int g = Math.min(Math.max(0, floor[k]), left);
			out[k] = g;
			left -= g;
		}
		for (int k = 0; k < n && left > 0; k++) {
			int g = Math.min(cap[k] - out[k], left);
			if (g > 0) {
				out[k] += g;
				left -= g;
			}
		}
		boolean any = true;
		while (left > 0 && any) {
			any = false;
			for (int k = 0; k < n && left > 0; k++) {
				if (out[k] < hard[k]) {
					out[k]++;
					left--;
					any = true;
				}
			}
		}
		return out;
	}

	/** The nearest site not started, not open and within {@link #NEAR_FRESH} blocks; -1 = none (the details are never near). */
	static int nearestFresh(byte[] state, double[] dist, boolean[] isOpen) {
		int best = -1;
		for (int s = 0; s < state.length; s++) {
			if (state[s] == IDLE && !isOpen[s] && dist[s] <= NEAR_FRESH && (best < 0 || dist[s] < dist[best])) {
				best = s;
			}
		}
		return best;
	}
}
