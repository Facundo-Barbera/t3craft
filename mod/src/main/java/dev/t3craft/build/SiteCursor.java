package dev.t3craft.build;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * feat/builder-sites: builds a free {@link BuildPlanner#CREW} job <b>site by site</b> ({@code build.order: "sites"})
 * instead of column by column around the player ({@link RadialCursor}, still the default). The player chooses
 * <i>which site</i> and <i>from which side</i>; the site decides the order. Design and the decisions behind it:
 * docs/plans/builder-scheduling.md; how to try it and what the status line says: docs/features/builder-sites.md.
 *
 * <p>Three parts, on purpose with simple invariants (this is written without a compiler at hand):
 * <ul>
 *   <li>{@link SiteProgram} (pure, off-thread): the sites (obras) of the plan, their footprints, bays (tramos) and
 *   storeys (plantas), and the order inside a site once it is opened ({@link SiteProgram#compile});</li>
 *   <li>{@link SiteDispatch} (pure): which site is in focus and how many builders it gets;</li>
 *   <li>this class (server thread): explicit queues per open site, no global order, no re-order when the player
 *   moves, no stalled pointers that wake up.</li>
 * </ul>
 *
 * <p><b>A site opens</b> when the dispatch puts the focus on it (the player is in its footprint, or it is the
 * nearest started one, or the nearest fresh one). Its program is compiled off-thread for the <b>access</b>: the column
 * of the site nearest the player at that moment, frozen until the site closes. The crew is assigned at once and
 * walks to the access together; the site's ground is laid as a wave from the access (no builders); the builders
 * follow, one bay each, nearest the access first.
 *
 * <p><b>Storey by storey, stage by stage.</b> The program is a list of waves; a wave is a list of tasks (a bay
 * each). Builders only take tasks of the current wave, and the next wave starts when every task of this one is
 * done: the whole site lays a storey's slab, raises its structure, closes it, finishes it, and only then starts the
 * next storey, however the player walks inside it. Support was resolved when the program was compiled
 * ({@link SiteProgram}), so nothing here stalls waiting for a neighbour: what found no support is the site's
 * finishing pass, its last wave.
 *
 * <p><b>Leaving a site.</b> When the focus moves to another site, the new one opens at once and the one left behind
 * first finishes the storey it is on with the crew it has (never a ragged skyline). Then its main crew goes and a
 * <b>rear guard</b> of {@link SiteDispatch#REAR} builders stays and keeps raising it, storey by storey, while the
 * player is within {@link SiteDispatch#KEEP} blocks; further away the rear guard closes its storey and the site is
 * frozen. A started site near the player that nobody works gets a rear guard too. At most {@code build.sitesOpen}
 * sites are open (one more when the crew has builders to spare, {@link SiteDispatch#openLimit}); the farthest one
 * over the limit closes at its next storey. The focus gets {@link SiteDispatch#FOCUS_SHARE} of the builders' pace
 * while other sites are worked too. A closed site that is not finished stays "started" and comes back first when
 * the player is near it again; it is then compiled again, from the new access. {@code build.sitesOpen: 1} is one
 * site at a time: the new focus waits until the old one closed its storey.
 *
 * <p><b>Streams</b> (no builder, {@link #frontOf} -1), all out of the foundation budget:
 * <ol>
 *   <li>the <b>corridor</b>: the floor under the player's feet and ahead of it while it walks, as in
 *   {@link RadialCursor} (same settings); the floor is the terrain, or the plan's floor at the player's level when
 *   that is not the terrain (the hub's floor over a hidden room);</li>
 *   <li>the <b>carpet</b>: the floor of every column within {@code build.radiusMin + build.radiusStep} blocks of the
 *   player, nearest first, and a <b>trail</b> {@link #TRAIL_WIDTH} wide from there to the access of the nearest open
 *   site while the player is outside it;</li>
 *   <li>the <b>site wave</b>: ground and clearing (air) of the open site's bays and of the apron around it, in wave
 *   order from the access, plus the entries of the site already right in the world (resume: no builder walks to
 *   them);</li>
 *   <li>the <b>fill</b>: the hidden terrain under a column whose ground is laid, in the order the ground was laid;</li>
 *   <li>the <b>last sweep</b>: once every site is done, whatever is left of the plan (the ground far from every
 *   site), in rings around where the player stood when it started, then once more column by column.</li>
 * </ol>
 * A placement that answers BLOCKED (its chunk is not loaded) parks its column, or its task, for
 * {@link #PARK_TICKS}; nothing is ever dropped, so the job completes only when every entry was applied.
 *
 * <p><b>Counters</b> for the acceptance criteria of the plan ({@link #describe}): builder placements outside an open
 * site, the largest storey difference inside a site, builders taken off a bay before it was done (by a focus change /
 * by a chunk), and the share of builder placements that went to the site the player was in.
 */
public final class SiteCursor implements JobCursor {
	/** A column or task waits this many ticks after a placement answered BLOCKED (chunk not loaded). */
	static final int PARK_TICKS = 20;
	/** The dispatch looks again every this many ticks (and right after a wave or a site ends). */
	static final int DISPATCH_EVERY = 10;
	/** A new focus must be chosen this many dispatch rounds in a row before it replaces one that still has work. */
	static final int HOLD = 2;
	/** A builder places up to this far (horizontally, each axis) from where it stands without walking. */
	static final int REACH = 4;
	/** A builder climbs once the entry it places is this far above its feet. */
	static final int RAISE_ABOVE = 3;
	/** Most builders on one task (its owner and one helper)... */
	static final int TASK_CREW = 2;
	/** ...and a helper joins only when the task has this many entries left. */
	static final int HELP_MIN = 8;
	/** A task with at least this many entries is raised at {@code build.climbBlocksPerTick}. */
	static final int TALL_TASK = 24;
	/** The crew of a site that opens walks to its access in this many ticks, all together. */
	static final int GATHER_TICKS = 20;
	/** Entries already right in the world (skips) a stream handles per tick at most. */
	static final int STREAM_SKIPS = 1024;
	/** World reads per tick and open site looking for entries already right (resume). */
	static final int PROBES_MAX = 1024;
	/** Columns the last sweep looks at per tick. */
	static final int SCAN_MAX = 8192;
	/** Largest carpet radius (blocks). */
	static final int CARPET_MAX = 24;
	/** The trail to the nearest open site: this long at most, this wide (blocks). */
	static final double TRAIL_MAX = 48.0;
	static final int TRAIL_WIDTH = 3;
	/** The corridor: as {@link RadialCursor}. */
	static final double FEET_RADIUS = 2.0;
	static final double SAMPLE = 0.5;
	static final double MOVING_SPEED = 1.0;
	static final double VELOCITY_TAU_MS = 150.0;
	static final double TELEPORT_SPEED = 30.0;

	/** Cells of a disc of {@link #CARPET_MAX} blocks, nearest first: {@code {dx, dz, squared distance}}. */
	private static final int[][] DISC = disc(CARPET_MAX);

	/** One open site: its program and where its crew is in it. Server thread. */
	private static final class Open {
		final int site;
		@Nullable CompletableFuture<SiteProgram.Program> pending;
		SiteProgram.@Nullable Program prog;
		/** Current wave, and its tasks not done yet. */
		int wave;
		int waveTasksLeft;
		/** Per task: next entry (index into the program's entries), builders on it, parked until, done. */
		int[] taskNext = new int[0];
		int[] taskCrew = new int[0];
		int[] taskPark = new int[0];
		boolean[] taskDone = new boolean[0];
		/** Bays of the program's wave order before this one have their ground and clearing done. */
		int groundBay;
		/** Next program entry to look at in the world (resume). */
		int probeNext;
		/** Builders assigned, wanted, and holding a task. */
		int crew;
		int want;
		int working;
		/** It is the dispatch's focus. A site that is not is closing its storey, or is worked by a rear guard. */
		boolean focus;
		/** Its storey was closed after the focus moved on (or it was opened by proximity): a rear guard works it. */
		boolean rear;
		/** It closes at its next storey boundary (too far from the player, or one site too many). */
		boolean leaving;
		/** No task of the current storey was taken yet (the site stands at a storey boundary). */
		boolean fresh = true;
		/** A storey boundary the dispatch has not looked at yet: a site that is not the focus hands out nothing until it has. */
		boolean boundary;
		/** The program ran to its end. */
		boolean finished;

		Open(int site) {
			this.site = site;
		}
	}

	private final SiteProgram p;
	private final int n;
	private final int builders;
	private final double perBuilder;

	// ---- entries and columns (server thread) --------------------------------------------------
	private final boolean[] applied;
	private final int[] by;
	/** Entries of a finishing pass: left out of the storey counts (the storey-spread counter is about the storeys proper). */
	private final boolean[] outOfStorey;
	/** Next ground entry of each column (index inside the column, {@code colFill … colStack}). */
	private final int[] gNext;
	/** Next air entry of each column (index into {@link SiteProgram#airEntries}). */
	private final int[] aNext;
	/** Next fill entry of each column (index inside the column, {@code 0 … colFill}). */
	private final int[] fNext;
	private final int[] parkUntil;
	private final int[] colTop;
	private final boolean[] fillQueued;
	private IntArrayList fillQueue = new IntArrayList();
	private int fillHead;
	private int lastPark = Integer.MIN_VALUE / 2;

	// ---- sites --------------------------------------------------------------------------------
	/** Ground and air entries left in the columns of each bay: a task waits until its bay has none. */
	private final int[] bayStreamLeft;
	private final int[] siteLeft;
	private final byte[] siteState;
	private final @Nullable Open[] open;
	private final IntArrayList openList = new IntArrayList();
	/** Builder entries left per storey of each site: {@code storeyLeft[storeyStart[s] + storey]}. */
	private final int[] storeyStart;
	private final int[] storeyLeft;
	private final int[] lowStorey;
	private int sitesDone;
	private int focusSite = -1;
	private int candidate = -1;
	private int candidateRounds;
	private int insideSite = -1;
	private int dispatchAt;

	// ---- builders -----------------------------------------------------------------------------
	private final int[] tSite;
	private final int[] tTask;
	private final int[] walkLeft;
	private final int[] px;
	private final int[] py;
	private final int[] pz;
	private double credit;
	private int rr;
	/** Builder placements skipped this tick (already right in the world), all sites together. */
	private int tickSkips;
	/** The entry a builder is placing right now ({@link #frontOf} is asked from inside the placement). */
	private int nowIndex = -1;
	private int nowFront = -1;

	// ---- streams ------------------------------------------------------------------------------
	private int sweepCol;
	private boolean sweepParked;
	private int sweepAgainAt;
	/** The last sweep's rings: centre (fixed when it starts), the ring being laid, the strip inside it; -1 = not started. */
	private int ringR = -1;
	private int ringDx;
	private int ringX;
	private int ringZ;
	private int ringMax;
	private int carpetX = Integer.MIN_VALUE;
	private int carpetZ;
	private int carpetLevel;
	private int carpetTx = Integer.MIN_VALUE;
	private int carpetTz;
	private boolean carpetIdle;

	// ---- the player (written by the server thread through focus) ------------------------------
	private double cx;
	private double cz;
	private double playerY = Double.NaN;
	private boolean hasPlayer;
	private int level = WalkLevels.NO_LEVEL;
	private double vx;
	private double vz;
	private double trackX;
	private double trackZ;
	private long trackAt;
	private boolean trackValid;

	// ---- counters (status) --------------------------------------------------------------------
	private long builderPlaced;
	private long offSite;
	private long detailPlaced;
	private long insideAll;
	private long insideIn;
	private int storeySpread;
	private int switchWalk;
	private int switchChunk;
	private int lifted;

	private @Nullable IntPredicate already;
	private CrewCursor.@Nullable Walker walker;
	private int tickNo;

	private volatile int done;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile JobState state = JobState.PENDING;

	private SiteCursor(SiteProgram program, int builders, double perBuilder) {
		this.p = program;
		this.n = program.n;
		this.builders = Math.max(1, builders);
		this.perBuilder = Math.max(1.0, perBuilder);
		applied = new boolean[n];
		outOfStorey = new boolean[n];
		by = new int[n];
		Arrays.fill(by, -1);
		int nc = p.cols;
		gNext = Arrays.copyOf(p.colFill, nc);
		aNext = Arrays.copyOf(p.airStart, nc);
		fNext = new int[nc];
		parkUntil = new int[nc];
		colTop = new int[nc];
		Arrays.fill(colTop, Integer.MIN_VALUE);
		fillQueued = new boolean[nc];
		bayStreamLeft = new int[p.bays];
		for (int b = 0; b < p.bays; b++) {
			int left = 0;
			for (int k = p.bayColStart[b]; k < p.bayColStart[b + 1]; k++) {
				int c = p.bayCols[k];
				left += p.colStack[c] - p.colFill[c] + p.airStart[c + 1] - p.airStart[c];
			}
			bayStreamLeft[b] = left;
		}
		siteLeft = p.siteTotal.clone();
		siteState = new byte[p.sites];
		open = new Open[p.sites];
		storeyStart = new int[p.sites + 1];
		for (int s = 0; s < p.sites; s++) {
			storeyStart[s + 1] = storeyStart[s] + p.storeyY[s].length;
		}
		storeyLeft = new int[storeyStart[p.sites]];
		lowStorey = new int[p.sites];
		for (int i = 0; i < n; i++) {
			if (p.kind[i] == SiteProgram.BUILD) {
				storeyLeft[storeyStart[p.siteOf[i]] + p.storeyOf[i]]++;
			}
		}
		tSite = new int[this.builders];
		Arrays.fill(tSite, -1);
		tTask = new int[this.builders];
		Arrays.fill(tTask, -1);
		walkLeft = new int[this.builders];
		px = new int[this.builders];
		py = new int[this.builders];
		pz = new int[this.builders];
		cx = p.fallback[0];
		cz = p.fallback[1];
		int baseY = n == 0 ? 0 : p.y[p.colEntries[0]];
		for (int f = 0; f < this.builders; f++) {
			px[f] = (int) Math.round(cx) + (f % 3) - 1;
			py[f] = baseY;
			pz[f] = (int) Math.round(cz) + (f / 3 % 3) - 1;
		}
	}

	/**
	 * Analyses {@code plan} (off-thread: {@link DeferredCursor}) and returns the cursor that builds it site by site.
	 *
	 * @param builders   builders (fronts) of the crew
	 * @param perBuilder most blocks one builder places per tick (big tasks: {@code build.climbBlocksPerTick})
	 * @param focus      plan-relative {@code {x, z}} to centre on while nobody is in the build, or null
	 */
	static SiteCursor plan(BuildPlan plan, int builders, double perBuilder, int @Nullable [] focus) {
		long t0 = System.nanoTime();
		try {
			return new SiteCursor(SiteProgram.of(plan, BuildBudget.global().tuning().siteStorey(), focus), builders, perBuilder);
		} finally {
			dev.t3craft.debug.PerfStats.stepAsync(dev.t3craft.debug.PerfStats.STEP_RADIAL_PLAN, t0);
		}
	}

	private static int[][] disc(int r) {
		int side = 2 * r + 1;
		long[] keys = new long[side * side];
		int k = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 <= r * r) {
					keys[k++] = ((long) d2 << 32) | ((long) (dx + r) << 16) | (dz + r);
				}
			}
		}
		Arrays.sort(keys, 0, k);
		int[][] out = new int[3][k];
		for (int j = 0; j < k; j++) {
			out[0][j] = (int) ((keys[j] >>> 16) & 0xFFFF) - r;
			out[1][j] = (int) (keys[j] & 0xFFFF) - r;
			out[2][j] = (int) (keys[j] >>> 32);
		}
		return out;
	}

	// ---- wiring (server thread) ---------------------------------------------------------------

	/** Entries already right in the world (a stream applies them, no builder walks there); null = none known. */
	void probe(@Nullable IntPredicate already) {
		this.already = already;
	}

	/** Who hears a builder start walking (or climb). */
	void walker(CrewCursor.@Nullable Walker w) {
		this.walker = w;
	}

	/**
	 * The player now, plan-relative {@code {x, z, y, onGround}}; null = nobody in the build (the fallback centre).
	 * Only remembers it (and the player's velocity, for the corridor): nothing is re-ordered when the player moves.
	 * Server thread.
	 */
	void focus(double @Nullable [] rel, long nowMs) {
		track(rel, nowMs);
		if (rel == null) {
			cx = p.fallback[0];
			cz = p.fallback[1];
			playerY = Double.NaN;
			level = WalkLevels.NO_LEVEL;
			return;
		}
		cx = rel[0];
		cz = rel[1];
		if (rel.length < 3) {
			playerY = Double.NaN;
			level = WalkLevels.NO_LEVEL;
			return;
		}
		playerY = rel[2];
		int feet = WalkLevels.feet(rel[2]);
		boolean onGround = rel.length < 4 || rel[3] != 0;
		// the level follows the feet on the ground; in the air only after a real fall or climb (a jump never counts)
		if (level == WalkLevels.NO_LEVEL || onGround || Math.abs(feet - level) >= WalkLevels.LEVEL_MOVE) {
			level = feet;
		}
	}

	/** The player's velocity (plan-relative, blocks/s), smoothed; reset by a teleport, a long gap, or nobody in the build. */
	private void track(double @Nullable [] rel, long nowMs) {
		if (rel == null) {
			hasPlayer = false;
			trackValid = false;
			vx = 0;
			vz = 0;
			return;
		}
		hasPlayer = true;
		if (trackValid) {
			long dt = nowMs - trackAt;
			if (dt <= 0) {
				return; // same millisecond: wait for a measurable step
			}
			double ix = (rel[0] - trackX) * 1000.0 / dt;
			double iz = (rel[1] - trackZ) * 1000.0 / dt;
			if (dt > 1000 || ix * ix + iz * iz > TELEPORT_SPEED * TELEPORT_SPEED) {
				vx = 0;
				vz = 0;
			} else {
				double a = Math.min(1.0, dt / VELOCITY_TAU_MS);
				vx += (ix - vx) * a;
				vz += (iz - vz) * a;
			}
		}
		trackX = rel[0];
		trackZ = rel[1];
		trackAt = nowMs;
		trackValid = true;
	}

	// ---- bookkeeping --------------------------------------------------------------------------

	private boolean parked(int c) {
		return parkUntil[c] > tickNo;
	}

	private void park(int c) {
		parkUntil[c] = tickNo + PARK_TICKS;
		lastPark = tickNo;
	}

	/** Records that {@code i} was applied (any outcome but BLOCKED). */
	private void applied(int i, BuildCursor.Outcome o) {
		applied[i] = true;
		done++;
		switch (o) {
			case PLACED -> placed++;
			case SKIPPED -> skipped++;
			case FAILED -> failed++;
			default -> {
			}
		}
		int c = p.colOf[i];
		byte k = p.kind[i];
		if (k == SiteProgram.BUILD) {
			int s = p.siteOf[i];
			siteLeft[s]--;
			if (!outOfStorey[i]) {
				storeyLeft[storeyStart[s] + p.storeyOf[i]]--;
			}
		} else if (k != SiteProgram.FILL) {
			for (int q = p.colBayStart[c]; q < p.colBayStart[c + 1]; q++) {
				bayStreamLeft[p.colBays[q]]--;
			}
		}
		if (!p.air[i]) {
			colTop[c] = Math.max(colTop[c], p.y[i]);
		}
	}

	/**
	 * One stream placement (no builder) of entry {@code i} in column {@code c}; {@code counts} gets one more placed
	 * ({@code [0]}) or skipped ({@code [1]}). False = its chunk is not loaded: the column is parked, nothing applied.
	 */
	private boolean stream(int c, int i, FrontCursor.Step step, int[] counts) {
		BuildCursor.Outcome o = step.apply(-1, i);
		if (o == BuildCursor.Outcome.BLOCKED) {
			park(c);
			return false;
		}
		applied(i, o);
		if (o == BuildCursor.Outcome.SKIPPED) {
			counts[1]++;
		} else {
			counts[0]++;
		}
		return true;
	}

	/**
	 * Lays the ground of column {@code c} (surface and the layer under it) until it is done or parked. False = the
	 * budget ({@code max} placed, {@code maxSkips} skipped) ended first.
	 */
	private boolean layGround(int c, FrontCursor.Step step, int[] counts, int max, int maxSkips) {
		int a = p.colStart[c];
		while (gNext[c] < p.colStack[c]) {
			int i = p.colEntries[a + gNext[c]];
			if (applied[i]) {
				gNext[c]++;
				continue;
			}
			if (counts[0] >= max || counts[1] >= maxSkips) {
				return false;
			}
			if (!stream(c, i, step, counts)) {
				return true; // parked: the caller goes on with the next column
			}
			gNext[c]++;
		}
		if (!fillQueued[c] && p.colFill[c] > 0) {
			fillQueued[c] = true;
			fillQueue.add(c); // the hidden fill follows the ground, in the order it was laid
		}
		return true;
	}

	/** Clears column {@code c} (its air entries) until it is done or parked. False = the budget ended first. */
	private boolean layAir(int c, FrontCursor.Step step, int[] counts, int max, int maxSkips) {
		while (aNext[c] < p.airStart[c + 1]) {
			int i = p.airEntries[aNext[c]];
			if (applied[i]) {
				aNext[c]++;
				continue;
			}
			if (counts[0] >= max || counts[1] >= maxSkips) {
				return false;
			}
			if (!stream(c, i, step, counts)) {
				return true;
			}
			aNext[c]++;
		}
		return true;
	}

	/**
	 * The floor of column {@code c} at the player's level when that is not the terrain (the walkable candidate nearest
	 * the player's feet, {@link WalkLevels#nearer}): laid by the stream like ground, with the block under it on a
	 * floor's edge or under a thin floor. Its site's tasks find it applied and pass over it. False = the budget ended.
	 */
	private boolean layLift(int c, FrontCursor.Step step, int[] counts, int max, int maxSkips) {
		if (level == WalkLevels.NO_LEVEL || p.candStart[c] >= p.candStart[c + 1] || parked(c)) {
			return true;
		}
		int a = p.colStart[c];
		int best = -1;
		int bestY = WalkLevels.NO_LEVEL;
		for (int q = p.candStart[c]; q < p.candStart[c + 1]; q++) {
			int sy = p.y[p.colEntries[a + p.candK[q]]];
			if (WalkLevels.nearer(sy, bestY, level)) {
				best = q;
				bestY = sy;
			}
		}
		if (best < 0) {
			return true;
		}
		int s = p.colEntries[a + p.candK[best]];
		if (p.kind[s] != SiteProgram.BUILD) {
			return true; // the terrain top: the ground lays it
		}
		int sup = p.candSup[best] ? p.colEntries[a + p.candK[best] - 1] : -1;
		if (sup >= 0 && !applied[sup]) {
			if (counts[0] >= max || counts[1] >= maxSkips) {
				return false;
			}
			if (!stream(c, sup, step, counts)) {
				return true;
			}
			lifted++;
		}
		if (!applied[s]) {
			if (counts[0] >= max || counts[1] >= maxSkips) {
				return false;
			}
			if (stream(c, s, step, counts)) {
				lifted++;
			}
		}
		return true;
	}

	// ---- tick ---------------------------------------------------------------------------------

	@Override
	public int tick(double allowance, FrontCursor.Step step) {
		JobState s;
		synchronized (this) {
			if (state == JobState.PENDING) {
				state = JobState.RUNNING;
			}
			s = state;
		}
		if (s != JobState.RUNNING) {
			return 0;
		}
		long t0 = System.nanoTime();
		int n0 = done;
		try {
			tickNo++;
			BuildTuning t = BuildBudget.global().tuning();
			install();
			if (tickNo >= dispatchAt) {
				dispatchAt = tickNo + DISPATCH_EVERY;
				dispatch(t);
			}
			tickCorridor(step, t);
			tickCarpet(step, t);
			tickSites(step, t);
			tickBuilders(allowance, step, t);
			tickFill(step, t);
			if (sitesDone >= p.sites) {
				tickSweep(step, t);
			}
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_TICK, t0);
		}
		if (done >= n) {
			finish();
		}
		return done - n0;
	}

	// ---- dispatch: which site is open, and its crew -------------------------------------------

	/** Opening sites whose program arrived: the crew gathers at the access and the first wave starts. */
	private void install() {
		for (int k = 0; k < openList.size(); k++) {
			Open o = open[openList.getInt(k)];
			CompletableFuture<SiteProgram.Program> f = o == null ? null : o.pending;
			if (o == null || f == null || !f.isDone()) {
				continue;
			}
			o.pending = null;
			SiteProgram.Program g;
			try {
				g = f.join();
			} catch (RuntimeException e) {
				dev.t3craft.T3Craft.LOGGER.warn("site program failed off-thread; the last sweep places what is left of this site", e);
				o.finished = true;
				dispatchAt = tickNo;
				continue;
			}
			o.prog = g;
			int tasks = g.tasks();
			o.taskNext = Arrays.copyOf(g.taskStart(), tasks);
			o.taskCrew = new int[tasks];
			o.taskPark = new int[tasks];
			o.taskDone = new boolean[tasks];
			o.wave = 0;
			o.fresh = true;
			if (g.remateWave() < g.waves()) {
				// the finishing pass is not part of a storey: it does not count for the storey spread
				for (int q = g.taskStart()[g.waveStart()[g.remateWave()]]; q < g.entries().length; q++) {
					int i = g.entries()[q];
					if (!outOfStorey[i]) {
						outOfStorey[i] = true;
						if (!applied[i]) {
							storeyLeft[storeyStart[o.site] + p.storeyOf[i]]--;
						}
					}
				}
			}
			enterWave(o);
			if (o.finished) {
				dispatchAt = tickNo;
				continue;
			}
			gather(o);
		}
	}

	/** Moves {@code o} to the first wave, from its current one on, that has a task left; none = the program is over. */
	private void enterWave(Open o) {
		SiteProgram.Program g = o.prog;
		if (g == null) {
			return;
		}
		while (o.wave < g.waves()) {
			int left = 0;
			for (int tk = g.waveStart()[o.wave]; tk < g.waveStart()[o.wave + 1]; tk++) {
				if (o.taskDone[tk]) {
					continue;
				}
				if (taskHead(o, tk) < 0) {
					o.taskDone[tk] = true;
				} else {
					left++;
				}
			}
			if (left > 0) {
				o.waveTasksLeft = left;
				return;
			}
			o.wave++;
		}
		o.waveTasksLeft = 0;
		o.finished = true;
	}

	/** The current wave of {@code o} is done: on to the next. A new storey is a boundary: the dispatch decides there what a site that is not the focus does next. */
	private void advance(Open o) {
		SiteProgram.Program g = o.prog;
		if (g == null || o.wave >= g.waves()) {
			return;
		}
		int storey = g.waveStorey()[o.wave];
		o.wave++;
		enterWave(o);
		if (!o.finished && g.waveStorey()[o.wave] != storey) {
			o.fresh = true;
			o.boundary = true;
		}
		dispatchAt = tickNo; // staff the new wave (or close the site, or leave a rear guard) on the next tick
	}

	/** Next entry of task {@code tk} not applied yet, or -1 when the task has none left. */
	private int taskHead(Open o, int tk) {
		SiteProgram.Program g = o.prog;
		if (g == null) {
			return -1;
		}
		int[] ents = g.entries();
		int end = g.taskStart()[tk + 1];
		while (o.taskNext[tk] < end && applied[ents[o.taskNext[tk]]]) {
			o.taskNext[tk]++;
		}
		return o.taskNext[tk] < end ? ents[o.taskNext[tk]] : -1;
	}

	/** Task {@code tk} of the current wave has nothing left: counted once; the wave moves on when it was the last. */
	private void taskOver(Open o, int tk) {
		if (o.taskDone[tk]) {
			return;
		}
		o.taskDone[tk] = true;
		if (--o.waveTasksLeft <= 0) {
			advance(o);
		}
	}

	/** Whether a builder could take a task of the current wave of {@code o} right now. */
	private boolean pickable(Open o) {
		SiteProgram.Program g = o.prog;
		if (g == null || o.finished || o.wave >= g.waves()) {
			return false;
		}
		for (int tk = g.waveStart()[o.wave]; tk < g.waveStart()[o.wave + 1]; tk++) {
			if (!o.taskDone[tk] && o.taskPark[tk] <= tickNo && bayStreamLeft[g.taskBay()[tk]] <= 0) {
				return true;
			}
		}
		return false;
	}

	private void dispatch(BuildTuning t) {
		insideSite = p.sites == 0 ? -1 : p.siteAt(cx, cz, hasPlayer ? playerY : Double.NaN);
		// finished sites leave
		for (int k = openList.size() - 1; k >= 0; k--) {
			Open o = open[openList.getInt(k)];
			if (o != null && o.finished) {
				siteState[o.site] = SiteDispatch.DONE;
				sitesDone++;
				closeSite(o);
			}
		}
		if (sitesDone >= p.sites) {
			focusSite = -1;
			return;
		}
		double[] dist = new double[p.sites];
		for (int s = 0; s < p.sites; s++) {
			dist[s] = s == p.details ? Double.MAX_VALUE : p.distance(s, cx, cz);
		}
		int want = SiteDispatch.focus(focusSite, insideSite, siteState, dist);
		if (want != focusSite) {
			// hold: the player brushing past another building does not move the crew
			boolean urgent = focusSite < 0 || siteState[focusSite] == SiteDispatch.DONE;
			if (want == candidate) {
				candidateRounds++;
			} else {
				candidate = want;
				candidateRounds = 1;
			}
			if (urgent || candidateRounds >= HOLD) {
				focusSite = want;
				candidate = -1;
				candidateRounds = 0;
			}
		} else {
			candidate = -1;
			candidateRounds = 0;
		}
		int limit = SiteDispatch.openLimit(t.sitesOpen(), builders, t.siteCrewMax());
		int hardCap = t.sitesOpen() <= 1 ? 1 : limit + 1; // the focus opens at once even when a site still has to close its storey
		// sites over the limit: the farthest ones that are not the focus leave at their next storey boundary
		int others = 0;
		for (int k = 0; k < openList.size(); k++) {
			Open o = open[openList.getInt(k)];
			if (o == null) {
				continue;
			}
			o.focus = o.site == focusSite;
			o.leaving = false;
			if (!o.focus) {
				others++;
			}
		}
		for (int surplus = others - Math.max(0, limit - (focusSite >= 0 ? 1 : 0)); surplus > 0; surplus--) {
			Open far = null;
			for (int k = 0; k < openList.size(); k++) {
				Open o = open[openList.getInt(k)];
				if (o != null && !o.focus && !o.leaving && (far == null || dist[o.site] > dist[far.site])) {
					far = o;
				}
			}
			if (far == null) {
				break;
			}
			far.leaving = true;
		}
		for (int k = openList.size() - 1; k >= 0; k--) {
			Open o = open[openList.getInt(k)];
			if (o == null) {
				continue;
			}
			if (o.focus) {
				o.rear = false;
				o.boundary = false;
				continue;
			}
			if (dist[o.site] > SiteDispatch.KEEP) {
				o.leaving = true; // far from the player: it closes its storey and is frozen
			}
			boolean untouched = siteState[o.site] == SiteDispatch.IDLE; // no builder ever placed there: nothing to guard
			boolean stalled = o.prog != null && o.working == 0 && !pickable(o);
			if (o.fresh && untouched || o.leaving && (o.prog == null || o.fresh || stalled)) {
				closeSite(o);
				continue;
			}
			if (o.fresh) {
				o.rear = true; // its storey is closed: the main crew moves on, the rear guard stays
			}
			o.boundary = false;
		}
		if (focusSite >= 0 && open[focusSite] == null && openList.size() < hardCap) {
			openSite(focusSite).focus = true;
		}
		// by proximity: a started site near the player that nobody works gets a rear guard, while a slot and two builders are free
		if (openList.size() < limit && (focusSite < 0 || open[focusSite] != null)) {
			int free = 0;
			for (int f = 0; f < builders; f++) {
				if (tSite[f] < 0) {
					free++;
				}
			}
			if (free >= SiteDispatch.REAR) {
				boolean[] isOpen = new boolean[p.sites];
				for (int k = 0; k < openList.size(); k++) {
					isOpen[openList.getInt(k)] = true;
				}
				int near = SiteDispatch.nearestStarted(siteState, dist, isOpen);
				if (near >= 0 && near != focusSite) {
					openSite(near).rear = true;
				}
			}
		}
		// crews: the focus first, then the sites closing their storey, then the rear guards
		for (int role = 0; role < 3; role++) {
			for (int k = 0; k < openList.size(); k++) {
				Open o = open[openList.getInt(k)];
				if (o != null && (o.focus ? 0 : o.rear ? 2 : 1) == role) {
					staff(o, t);
				}
			}
		}
	}

	/** Opens site {@code s}: its program is compiled off-thread for where the player is now (the access). */
	private Open openSite(int s) {
		Open o = new Open(s);
		boolean[] snapshot = null;
		if (siteLeft[s] < p.siteTotal[s]) {
			int a = p.siteEntStart[s];
			snapshot = new boolean[p.siteEntStart[s + 1] - a];
			for (int k = 0; k < snapshot.length; k++) {
				snapshot[k] = applied[p.siteEnts[a + k]];
			}
		}
		boolean[] doneLocal = snapshot;
		double ax = cx;
		double az = cz;
		SiteProgram program = p;
		o.pending = Offload.supply(dev.t3craft.debug.PerfStats.STEP_RADIAL_ORDER, () -> program.compile(s, ax, az, doneLocal));
		open[s] = o;
		openList.add(s);
		return o;
	}

	/** Closes {@code o}: its builders are free again. A builder taken off a task with entries left is counted. */
	private void closeSite(Open o) {
		for (int f = 0; f < builders; f++) {
			if (tSite[f] != o.site) {
				continue;
			}
			if (tTask[f] >= 0 && !o.finished && taskHead(o, tTask[f]) >= 0) {
				switchWalk++;
			}
			tTask[f] = -1;
			tSite[f] = -1;
			walkLeft[f] = 0;
		}
		CompletableFuture<SiteProgram.Program> f = o.pending;
		if (f != null) {
			f.cancel(false);
			o.pending = null;
		}
		open[o.site] = null;
		for (int k = 0; k < openList.size(); k++) {
			if (openList.getInt(k) == o.site) {
				openList.removeInt(k);
				break;
			}
		}
	}

	/**
	 * Sizes the crew of {@code o} and assigns or frees builders that hold no task. The focus: {@link SiteDispatch#crew}
	 * with the free builders. A site closing its storey: the same, but it only keeps builders, it gets no new ones. A
	 * rear guard: {@link SiteDispatch#REAR}.
	 */
	private void staff(Open o, BuildTuning t) {
		int free = 0;
		for (int f = 0; f < builders; f++) {
			if (tSite[f] < 0) {
				free++;
			}
		}
		int tasks = o.prog == null ? builders : o.waveTasksLeft;
		if (o.finished) {
			o.want = 0;
		} else if (o.rear && !o.focus) {
			o.want = Math.min(Math.min(SiteDispatch.REAR, Math.max(1, tasks)), o.crew + free);
		} else {
			o.want = SiteDispatch.crew(siteLeft[o.site], tasks, t.siteCrewMin(), t.siteCrewMax(), t.entriesPerBuilder(), o.focus ? o.crew + free : o.crew);
		}
		for (int f = 0; f < builders && o.crew < o.want; f++) {
			if (tSite[f] < 0) {
				tSite[f] = o.site;
				tTask[f] = -1;
				walkLeft[f] = 0;
				o.crew++;
			}
		}
		for (int f = builders - 1; f >= 0 && o.crew > o.want; f--) {
			if (tSite[f] == o.site && tTask[f] < 0) {
				tSite[f] = -1;
				o.crew--;
			}
		}
	}

	/** The crew of {@code o} walks to its access, to arrive together in {@link #GATHER_TICKS}. */
	private void gather(Open o) {
		SiteProgram.Program g = o.prog;
		if (g == null || g.entries().length == 0) {
			return;
		}
		int target = g.entries()[0];
		int ac = p.colAt.get(CrewPlanner.key(g.accessX(), 0, g.accessZ()));
		int feet = ac >= 0 && p.colStack[ac] > 0 ? p.y[p.colEntries[p.colStart[ac] + p.colStack[ac] - 1]] + 1 : p.y[target];
		int k = 0;
		CrewCursor.Walker w = walker;
		for (int f = 0; f < builders; f++) {
			if (tSite[f] != o.site || tTask[f] >= 0) {
				continue;
			}
			px[f] = g.accessX() + (k % 3) - 1;
			py[f] = feet;
			pz[f] = g.accessZ() + (k / 3 % 3) - 1;
			k++;
			walkLeft[f] = GATHER_TICKS;
			if (w != null) {
				w.walking(f, target, new int[] {px[f], py[f], pz[f]}, GATHER_TICKS);
			}
		}
	}

	// ---- streams ------------------------------------------------------------------------------

	/**
	 * The corridor, as the one of {@link RadialCursor}: the floor of every column under the player's feet
	 * ({@link #FEET_RADIUS}) and, while it moves, of a corridor {@code build.corridorWidth} wide reaching
	 * {@code build.corridorLength} blocks ahead along its smoothed velocity. Before every other stream, at
	 * {@code build.corridorBlocksPerTick} and {@code build.corridorMsPerTick}.
	 */
	private void tickCorridor(FrontCursor.Step step, BuildTuning t) {
		int max = t.corridorBlocksPerTick();
		if (!hasPlayer || max <= 0 || p.cols == 0) {
			return;
		}
		long nanos = (long) (t.corridorMsPerTick() * 1_000_000.0);
		long t0 = System.nanoTime();
		int[] counts = new int[2];
		int skips = BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK;
		try {
			double speed = Math.hypot(vx, vz);
			boolean moving = speed >= MOVING_SPEED;
			double hx = moving ? vx / speed : 1.0;
			double hz = moving ? vz / speed : 0.0;
			double len = moving ? Math.max(FEET_RADIUS, t.corridorLength()) : FEET_RADIUS;
			double half = moving ? t.corridorWidth() / 2.0 : FEET_RADIUS + SAMPLE;
			int last = -1;
			outer:
			for (double s = -FEET_RADIUS; s <= len; s += SAMPLE) {
				double w = s <= FEET_RADIUS ? Math.max(half, FEET_RADIUS + SAMPLE) : half;
				for (int k = 0; ; k++) {
					double o = ((k + 1) / 2) * SAMPLE * (k % 2 == 0 ? 1 : -1); // 0, -0.5, +0.5, -1, +1, ...
					if (Math.abs(o) > w) {
						break;
					}
					int bx = (int) Math.floor(cx + hx * s - hz * o);
					int bz = (int) Math.floor(cz + hz * s + hx * o);
					int c = p.colAt.get(CrewPlanner.key(bx, 0, bz));
					if (c < 0 || c == last || parked(c)) {
						continue;
					}
					last = c;
					int before = counts[0] + counts[1];
					if (!layGround(c, step, counts, max, skips) || !layLift(c, step, counts, max, skips)) {
						break outer;
					}
					if (counts[0] + counts[1] > before && System.nanoTime() - t0 > nanos) {
						break outer;
					}
				}
			}
		} finally {
			BuildBudget.global().spendFoundation(counts[0], System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_CORRIDOR, t0);
		}
	}

	/**
	 * The carpet: the floor (terrain, or the plan's floor at the player's level) of the columns within
	 * {@code build.radiusMin + build.radiusStep} blocks of the centre, nearest first, at {@code build.groundBlocksPerTick};
	 * then the trail: a strip {@link #TRAIL_WIDTH} wide from the carpet's edge towards the access of the nearest open
	 * site, while the player is outside it. A full pass that found nothing left is not repeated until the centre changes
	 * cell or level, or the trail changes target.
	 */
	private void tickCarpet(FrontCursor.Step step, BuildTuning t) {
		int bx = (int) Math.floor(cx);
		int bz = (int) Math.floor(cz);
		// the trail's target: the access of the open site nearest the player, unless the player is in that site
		int tx = Integer.MIN_VALUE;
		int tz = 0;
		double best = Double.MAX_VALUE;
		for (int k = 0; k < openList.size(); k++) {
			Open o = open[openList.getInt(k)];
			SiteProgram.Program g = o == null ? null : o.prog;
			if (o == null || g == null || o.site == insideSite || o.site == p.details) {
				continue;
			}
			double d = Math.hypot(g.accessX() + 0.5 - cx, g.accessZ() + 0.5 - cz);
			if (d < best) {
				best = d;
				tx = g.accessX();
				tz = g.accessZ();
			}
		}
		if (carpetIdle && bx == carpetX && bz == carpetZ && level == carpetLevel && tx == carpetTx && tz == carpetTz) {
			return;
		}
		BuildBudget b = BuildBudget.global();
		int max = Math.min(b.foundationBlocks(), t.groundBlocksPerTick());
		if (max <= 0 || b.foundationNanos() <= 0) {
			return;
		}
		int r = Math.min(CARPET_MAX, t.radiusMin() + t.radiusStep());
		int r2 = r * r;
		long t0 = System.nanoTime();
		int[] counts = new int[2];
		boolean complete = true;
		boolean budget = true;
		try {
			int[] dxs = DISC[0];
			int[] dzs = DISC[1];
			int[] d2s = DISC[2];
			for (int k = 0; k < d2s.length && d2s[k] <= r2; k++) {
				int c = p.colAt.get(CrewPlanner.key(bx + dxs[k], 0, bz + dzs[k]));
				if (c < 0) {
					continue;
				}
				if (parked(c)) {
					complete = false;
					continue;
				}
				if (!layGround(c, step, counts, max, STREAM_SKIPS) || !layLift(c, step, counts, max, STREAM_SKIPS)) {
					budget = false;
					break;
				}
				if (parked(c)) {
					complete = false;
				}
			}
			if (budget && tx != Integer.MIN_VALUE && best > r) {
				double ux = (tx + 0.5 - cx) / best;
				double uz = (tz + 0.5 - cz) / best;
				double end = Math.min(best, TRAIL_MAX);
				int half = TRAIL_WIDTH / 2;
				int last = -1;
				trail:
				for (double d = r; d <= end; d += SAMPLE) {
					for (int w = -half; w <= half; w++) {
						int c = p.colAt.get(CrewPlanner.key((int) Math.floor(cx + ux * d - uz * w), 0, (int) Math.floor(cz + uz * d + ux * w)));
						if (c < 0 || c == last) {
							continue;
						}
						last = c;
						if (parked(c)) {
							complete = false;
							continue;
						}
						if (!layGround(c, step, counts, max, STREAM_SKIPS) || !layLift(c, step, counts, max, STREAM_SKIPS)) {
							budget = false;
							break trail;
						}
						if (parked(c)) {
							complete = false;
						}
					}
				}
			}
		} finally {
			b.spendFoundation(counts[0], System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_GROUND, t0);
		}
		carpetIdle = complete && budget;
		carpetX = bx;
		carpetZ = bz;
		carpetLevel = level;
		carpetTx = tx;
		carpetTz = tz;
	}

	/** The open sites' own streams: their ground wave, and the entries already right in the world. */
	private void tickSites(FrontCursor.Step step, BuildTuning t) {
		if (openList.isEmpty()) {
			return;
		}
		BuildBudget b = BuildBudget.global();
		int max = Math.min(b.foundationBlocks(), t.groundBlocksPerTick());
		if (max <= 0 || b.foundationNanos() <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		int[] counts = new int[2];
		try {
			// the focus first, then the others
			for (int pass = 0; pass < 2; pass++) {
				for (int k = 0; k < openList.size(); k++) {
					Open o = open[openList.getInt(k)];
					if (o == null || o.prog == null || (o.site == focusSite) != (pass == 0)) {
						continue;
					}
					probe(o, step, counts);
					if (!groundWave(o, step, counts, max)) {
						return;
					}
				}
			}
		} finally {
			b.spendFoundation(counts[0], System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_GROUND, t0);
		}
	}

	/**
	 * The ground wave of {@code o}: ground, then clearing, of its bays in wave order (nearest the access first). A
	 * bay with a column waiting for its chunk is passed over and looked at again next tick. False = the budget ended.
	 */
	private boolean groundWave(Open o, FrontCursor.Step step, int[] counts, int max) {
		SiteProgram.Program g = o.prog;
		if (g == null) {
			return true;
		}
		int[] order = g.bayOrder();
		boolean leading = true;
		for (int j = o.groundBay; j < order.length; j++) {
			int bay = order[j];
			if (bayStreamLeft[bay] > 0) {
				for (int k = p.bayColStart[bay]; k < p.bayColStart[bay + 1]; k++) {
					int c = p.bayCols[k];
					if (parked(c)) {
						continue;
					}
					if (!layGround(c, step, counts, max, STREAM_SKIPS)) {
						return false;
					}
					if (parked(c)) {
						continue;
					}
					if (!layAir(c, step, counts, max, STREAM_SKIPS)) {
						return false;
					}
				}
			}
			if (leading && bayStreamLeft[bay] <= 0) {
				o.groundBay = j + 1;
			} else {
				leading = false;
			}
		}
		return true;
	}

	/**
	 * Resume: looks at up to {@link #PROBES_MAX} entries of the program of {@code o} in the world and applies the ones
	 * already right (skipped, no builder), so the crew does not walk a building that is already there.
	 */
	private void probe(Open o, FrontCursor.Step step, int[] counts) {
		IntPredicate al = already;
		SiteProgram.Program g = o.prog;
		if (al == null || g == null) {
			return;
		}
		int[] ents = g.entries();
		int looked = 0;
		while (o.probeNext < ents.length && looked < PROBES_MAX) {
			int i = ents[o.probeNext];
			if (!applied[i]) {
				looked++;
				if (al.test(i) && !stream(p.colOf[i], i, step, counts)) {
					return; // its chunk went away: this entry again next tick
				}
			}
			o.probeNext++;
		}
	}

	/** The fill: the hidden terrain under the columns whose ground is laid, in that order, at {@code build.fillBlocksPerTick}. */
	private void tickFill(FrontCursor.Step step, BuildTuning t) {
		if (fillHead >= fillQueue.size()) {
			return;
		}
		BuildBudget b = BuildBudget.global();
		int max = Math.min(b.foundationBlocks(), t.fillBlocksPerTick());
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		int[] counts = new int[2];
		try {
			int end = fillQueue.size(); // a column that parks goes back to the tail and is not looked at twice in a tick
			while (fillHead < end) {
				int c = fillQueue.getInt(fillHead);
				if (parked(c)) {
					fillHead++;
					fillQueue.add(c);
					continue;
				}
				int a = p.colStart[c];
				boolean budget = false;
				while (fNext[c] < p.colFill[c]) {
					int i = p.colEntries[a + fNext[c]];
					if (applied[i]) {
						fNext[c]++;
						continue;
					}
					if (counts[0] >= max || counts[1] >= STREAM_SKIPS) {
						budget = true;
						break;
					}
					if (!stream(c, i, step, counts)) {
						break;
					}
					fNext[c]++;
				}
				if (budget) {
					break;
				}
				fillHead++;
				if (fNext[c] < p.colFill[c]) {
					fillQueue.add(c); // parked half-way
				}
				if (System.nanoTime() - t0 > nanos) {
					break;
				}
			}
			if (fillHead > 4096 && fillHead * 2 > fillQueue.size()) {
				IntArrayList rest = new IntArrayList(fillQueue.size() - fillHead + 16);
				for (int k = fillHead; k < fillQueue.size(); k++) {
					rest.add(fillQueue.getInt(k));
				}
				fillQueue = rest;
				fillHead = 0;
			}
		} finally {
			b.spendFoundation(counts[0], System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_FILL, t0);
		}
	}

	/**
	 * The last sweep, once every site is done: whatever is left of the plan (ground and fill far from every site,
	 * air). First in rings around where the centre was when the sweep started, so the leftover floor grows outwards
	 * from the player instead of in plan order; then once more column by column, which also catches the columns that
	 * waited for their chunk. The job completes only when every entry was applied.
	 */
	private void tickSweep(FrontCursor.Step step, BuildTuning t) {
		if (done >= n || tickNo < sweepAgainAt) {
			return;
		}
		BuildBudget b = BuildBudget.global();
		int max = Math.min(b.foundationBlocks(), t.groundBlocksPerTick() + t.fillBlocksPerTick());
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		int[] counts = new int[2];
		try {
			if (ringR < 0) {
				ringX = (int) Math.floor(cx);
				ringZ = (int) Math.floor(cz);
				double far = 0;
				for (int k = 0; k < 4; k++) {
					far = Math.max(far, Math.hypot((k < 2 ? p.loX : p.hiX) - ringX, (k % 2 == 0 ? p.loZ : p.hiZ) - ringZ));
				}
				ringMax = (int) Math.ceil(far) + 1;
				ringR = 0;
				ringDx = 0;
			}
			if (ringR <= ringMax && !sweepRings(step, counts, max)) {
				return;
			}
			int scanned = 0;
			while (sweepCol < p.cols && scanned < SCAN_MAX) {
				scanned++;
				if (!sweepColumn(sweepCol, step, counts, max)) {
					return; // this column again next tick
				}
				sweepCol++;
				if ((scanned & 255) == 0 && System.nanoTime() - t0 > nanos) {
					return;
				}
			}
			if (sweepCol >= p.cols) {
				// entries are still left (this method returns at once when none are): columns waited for their chunk; go round again
				sweepParked = false;
				sweepCol = 0;
				sweepAgainAt = tickNo + PARK_TICKS;
			}
		} finally {
			b.spendFoundation(counts[0], System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_GROUND, t0);
		}
	}

	/**
	 * The rings of the last sweep: ring {@code r} is the cells whose distance to the centre is over {@code r - 1} and
	 * at most {@code r}, walked strip by strip ({@link #ringDx}); a strip is done again when the budget ends inside it
	 * (applied entries are passed over). True = every ring is done; false = go on next tick.
	 */
	private boolean sweepRings(FrontCursor.Step step, int[] counts, int max) {
		int scanned = 0;
		while (ringR <= ringMax) {
			int r = ringR;
			while (ringDx <= r) {
				int dx = ringDx;
				long inner = (long) (r - 1) * (r - 1) - (long) dx * dx;
				int zMax = (int) Math.floor(Math.sqrt((double) ((long) r * r - (long) dx * dx)));
				int zMin = r == 0 || inner < 0 ? 0 : (int) Math.floor(Math.sqrt((double) inner)) + 1;
				for (int dz = zMin; dz <= zMax; dz++) {
					for (int sign = dz == 0 ? 1 : -1; sign <= 1; sign += 2) {
						scanned++;
						int c = p.colAt.get(CrewPlanner.key(ringX + dx, 0, ringZ + sign * dz));
						if (c >= 0 && !sweepColumn(c, step, counts, max)) {
							return false;
						}
					}
				}
				ringDx++;
				if (scanned >= SCAN_MAX) {
					return false;
				}
			}
			ringR++;
			ringDx = -ringR;
		}
		return true;
	}

	/** Applies everything left in column {@code c} through the stream. False = the budget ended; a parked column is passed over (and remembered). */
	private boolean sweepColumn(int c, FrontCursor.Step step, int[] counts, int max) {
		if (parked(c)) {
			sweepParked = true;
			return true;
		}
		for (int k = p.colStart[c]; k < p.colStart[c + 1]; k++) {
			int i = p.colEntries[k];
			if (applied[i]) {
				continue;
			}
			if (counts[0] >= max || counts[1] >= STREAM_SKIPS) {
				return false;
			}
			if (!stream(c, i, step, counts)) {
				sweepParked = true;
				return true;
			}
		}
		return true;
	}

	// ---- builders -----------------------------------------------------------------------------

	/**
	 * Builders: each raises its task (a bay of the current wave of its site), climbing in place, then takes the next.
	 * While the focus and other sites are worked at once, the focus gets {@link SiteDispatch#FOCUS_SHARE} of the
	 * tick's credit and the others the rest; what one side cannot use goes to the other.
	 */
	private void tickBuilders(double allowance, FrontCursor.Step step, BuildTuning t) {
		long t0 = System.nanoTime();
		try {
			double climb = Math.max(perBuilder, t.climbBlocksPerTick());
			boolean focusWork = false;
			boolean otherWork = false;
			for (int f = 0; f < builders; f++) {
				if (walkLeft[f] > 0) {
					walkLeft[f]--;
				}
				if (tTask[f] < 0 && tSite[f] >= 0 && walkLeft[f] == 0) {
					pick(f);
				}
				if (tTask[f] >= 0) {
					if (tSite[f] == focusSite) {
						focusWork = true;
					} else {
						otherWork = true;
					}
				}
			}
			double a = Math.max(0, allowance);
			credit = Math.min(credit + a, Math.max(1.0, a));
			int[] used = new int[builders];
			tickSkips = 0;
			if (focusWork && otherWork) {
				double total = credit;
				// the focus always gets at least one placement when there is credit for one
				double spent = work(1, Math.max(total * SiteDispatch.FOCUS_SHARE, Math.min(total, 1.0)), climb, used, step);
				spent += work(2, total - spent, climb, used, step);
				spent += work(1, total - spent, climb, used, step);
				credit = total - spent;
			} else {
				credit -= work(0, credit, climb, used, step);
			}
			if (tickSkips >= BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
				credit = Math.min(credit, 0.999); // bounded work per tick even where most of a site is already there
			}
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_BUILDERS, t0);
		}
	}

	/**
	 * Lets the builders of {@code group} (0: all, 1: the focus site's, 2: the others) place up to {@code limit}
	 * blocks, in turns; {@code used} counts each builder's placements this tick across calls. Returns the credit spent.
	 */
	private double work(int group, double limit, double climb, int[] used, FrontCursor.Step step) {
		double left = limit;
		boolean progress = true;
		while (left >= 1.0 && progress && state == JobState.RUNNING && tickSkips < BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
			progress = false;
			for (int j = 0; j < builders && left >= 1.0 && tickSkips < BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK; j++) {
				int f = (rr + j) % builders;
				int s = tSite[f];
				int tk = tTask[f];
				if (s < 0 || tk < 0 || walkLeft[f] != 0 || group == 1 && s != focusSite || group == 2 && s == focusSite) {
					continue;
				}
				Open o = open[s];
				SiteProgram.Program g = o == null ? null : o.prog;
				if (o == null || g == null) {
					tTask[f] = -1;
					continue;
				}
				boolean tall = g.taskStart()[tk + 1] - g.taskStart()[tk] >= TALL_TASK;
				if (used[f] >= (tall ? climb : perBuilder)) {
					continue;
				}
				int i = taskHead(o, tk);
				if (i < 0) {
					release(f);
					taskOver(o, tk);
					if (tSite[f] >= 0) {
						pick(f);
					}
					continue;
				}
				int c = p.colOf[i];
				if (Math.abs(p.colX[c] - px[f]) > REACH || Math.abs(p.colZ[c] - pz[f]) > REACH) {
					standFor(f, c, i); // out of reach: a short walk inside the bay
					if (walkLeft[f] != 0) {
						continue;
					}
				} else if (p.y[i] > py[f] + RAISE_ABOVE || p.y[i] < py[f] - 2) {
					climbTo(f, i); // up (or down) the scaffold where it stands: no walk
				}
				nowIndex = i;
				nowFront = f;
				BuildCursor.Outcome out = step.apply(f, i);
				nowIndex = -1;
				if (out == BuildCursor.Outcome.BLOCKED) {
					o.taskPark[tk] = tickNo + PARK_TICKS; // chunk not loaded: the task waits, the builder takes another
					lastPark = tickNo;
					switchChunk++;
					release(f);
					continue;
				}
				by[i] = f;
				applied(i, out);
				count(i);
				progress = true;
				if (out == BuildCursor.Outcome.SKIPPED) {
					tickSkips++;
				} else {
					left -= 1.0;
					used[f]++;
				}
				rr = (f + 1) % builders;
			}
		}
		return limit - left;
	}

	/** Counters of one builder placement (the acceptance criteria of the plan, see {@link #describe}). */
	private void count(int i) {
		builderPlaced++;
		int s = p.siteOf[i];
		if (s < 0) {
			return;
		}
		if (siteState[s] == SiteDispatch.IDLE) {
			siteState[s] = SiteDispatch.STARTED;
		}
		if (insideSite >= 0) {
			insideAll++;
			if (s == insideSite) {
				insideIn++;
			}
		}
		if (s == p.details) {
			detailPlaced++;
			return;
		}
		if (open[s] == null) {
			offSite++;
		}
		if (outOfStorey[i]) {
			return;
		}
		int base = storeyStart[s];
		int count = p.storeyY[s].length;
		while (lowStorey[s] < count - 1 && storeyLeft[base + lowStorey[s]] <= 0) {
			lowStorey[s]++;
		}
		storeySpread = Math.max(storeySpread, p.storeyOf[i] - lowStorey[s]);
	}

	/**
	 * Free builder {@code f} takes a task of the current wave of its site: the first one nobody holds, in wave order
	 * (the front spreads from the access); else it helps on the held task with the most left. A task waits while its
	 * bay's ground is not laid or its chunk is not loaded. A site that is not the focus hands out nothing past a storey
	 * boundary until the dispatch looked at it (it may close there, or go on with a rear guard).
	 */
	private void pick(int f) {
		int s = tSite[f];
		Open o = s < 0 ? null : open[s];
		if (o == null) {
			return;
		}
		for (int guard = 0; guard < 4; guard++) {
			SiteProgram.Program g = o.prog;
			if (g == null || o.finished || o.boundary && !o.focus) {
				return;
			}
			int w = o.wave;
			int free = -1;
			int help = -1;
			int helpLeft = 0;
			boolean moved = false;
			for (int tk = g.waveStart()[w]; tk < g.waveStart()[w + 1]; tk++) {
				if (o.taskDone[tk]) {
					continue;
				}
				int head = taskHead(o, tk);
				if (head < 0) {
					taskOver(o, tk); // a stream laid what was left of it
					if (o.finished || o.wave != w) {
						moved = true;
						break;
					}
					continue;
				}
				if (o.taskPark[tk] > tickNo || bayStreamLeft[g.taskBay()[tk]] > 0) {
					continue;
				}
				if (o.taskCrew[tk] == 0) {
					free = tk;
					break;
				}
				int left = g.taskStart()[tk + 1] - o.taskNext[tk];
				if (o.taskCrew[tk] < TASK_CREW && left >= HELP_MIN && left > helpLeft) {
					help = tk;
					helpLeft = left;
				}
			}
			if (moved) {
				continue; // the wave moved on while looking: look at the new one
			}
			int tk = free >= 0 ? free : help;
			if (tk < 0) {
				return;
			}
			tTask[f] = tk;
			o.taskCrew[tk]++;
			o.working++;
			o.fresh = false;
			int head = taskHead(o, tk);
			standFor(f, p.colOf[head], head);
			return;
		}
	}

	/** Builder {@code f} lets go of its task; it leaves the site too when the site has more builders than it wants. */
	private void release(int f) {
		int s = tSite[f];
		int tk = tTask[f];
		Open o = s < 0 ? null : open[s];
		if (o != null && tk >= 0) {
			o.taskCrew[tk] = Math.max(0, o.taskCrew[tk] - 1);
			o.working = Math.max(0, o.working - 1);
			if (o.crew > o.want) {
				tSite[f] = -1;
				o.crew--;
			}
		}
		tTask[f] = -1;
		walkLeft[f] = 0;
	}

	/**
	 * Builder {@code f} goes to a spot beside column {@code c} to place {@code i}: the neighbouring cell on its own
	 * side (else the centre's side), feet one below the block, on the scaffold (as {@link RadialCursor}).
	 */
	private void standFor(int f, int c, int i) {
		int sx = Integer.signum(px[f] - p.colX[c]);
		int sz = Integer.signum(pz[f] - p.colZ[c]);
		if (sx == 0 && sz == 0) {
			sx = p.colX[c] >= cx ? 1 : -1;
		}
		if (sx != 0 && sz != 0) {
			if (Math.abs(px[f] - p.colX[c]) >= Math.abs(pz[f] - p.colZ[c])) {
				sz = 0;
			} else {
				sx = 0;
			}
		}
		int ax = p.colX[c] + sx;
		int az = p.colZ[c] + sz;
		int nc = p.colAt.get(CrewPlanner.key(ax, 0, az));
		int feet = p.y[i] - 1; // on the scaffold, the block at chest height
		if (nc >= 0 && colTop[nc] != Integer.MIN_VALUE && colTop[nc] < p.y[i]) {
			feet = Math.max(p.y[i] - 2, colTop[nc] + 1); // stands on what is built there
		}
		double d = CrewPlanner.walkDistance(px[f], py[f], pz[f], ax, feet, az);
		int ticks = d <= 1.5 ? 0 : (int) Math.min(CrewCursor.MAX_WALK_TICKS, Math.ceil(d / CrewCursor.RUN_PER_TICK));
		px[f] = ax;
		py[f] = feet;
		pz[f] = az;
		walkLeft[f] = ticks;
		CrewCursor.Walker w = walker;
		if (w != null) {
			w.walking(f, i, new int[] {ax, feet, az}, Math.max(1, ticks));
		}
	}

	/** Builder {@code f} climbs (or steps down) the scaffold where it stands to place {@code i}: no walk, it places this turn. */
	private void climbTo(int f, int i) {
		py[f] = p.y[i] - 1;
		CrewCursor.Walker w = walker;
		if (w != null) {
			w.walking(f, i, new int[] {px[f], py[f], pz[f]}, 1);
		}
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	/**
	 * Blocks per tick this job wants from the crews' budget (server thread): each builder on a task
	 * {@code build.climbBlocksPerTick}, the assigned ones without a task {@code perBuilder}; 1 while no builder has
	 * work (the streams have their own budget).
	 */
	public double rate() {
		if (state.isTerminal()) {
			return 0.0;
		}
		double climb = Math.max(perBuilder, BuildBudget.global().tuning().climbBlocksPerTick());
		double r = 0;
		boolean any = false;
		for (int f = 0; f < builders; f++) {
			if (tTask[f] >= 0) {
				any = true;
				r += climb;
			} else if (tSite[f] >= 0) {
				r += perBuilder;
			}
		}
		return any ? r : Math.min(1.0, perBuilder);
	}

	// ---- JobCursor ----------------------------------------------------------------------------

	/** Only the streams are at work (no builder holds a task): the job asks almost nothing of the crews' budget. */
	@Override
	public boolean fastPhase() {
		if (state.isTerminal() || done >= n) {
			return false;
		}
		for (int f = 0; f < builders; f++) {
			if (tTask[f] >= 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public synchronized boolean pause() {
		if (state == JobState.RUNNING || state == JobState.PENDING) {
			state = JobState.PAUSED;
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean resume() {
		if (state == JobState.PAUSED) {
			state = JobState.RUNNING;
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean cancel() {
		if (state.isTerminal()) {
			return false;
		}
		state = JobState.CANCELLED;
		return true;
	}

	@Override
	public boolean blocked() {
		return false; // free jobs only: nothing waits for materials
	}

	@Override
	public boolean frontBlocked(int f) {
		return false;
	}

	@Override
	public int segment() {
		return 0;
	}

	@Override
	public int[] pendingInSegment() {
		int[] out = new int[Math.max(0, n - done)];
		int k = 0;
		for (int i = 0; i < n && k < out.length; i++) {
			if (!applied[i]) {
				out[k++] = i;
			}
		}
		return k == out.length ? out : Arrays.copyOf(out, k);
	}

	@Override
	public int frontCount() {
		return builders;
	}

	/** The builder that placed (or is placing) {@code index}; -1 for the streams (no animation). */
	@Override
	public int frontOf(int index) {
		if (index < 0 || index >= n) {
			return -1;
		}
		int b = by[index];
		return b >= 0 ? b : index == nowIndex ? nowFront : -1;
	}

	@Override
	public JobState state() {
		return state;
	}

	@Override
	public int total() {
		return n;
	}

	@Override
	public int done() {
		return done;
	}

	@Override
	public int placed() {
		return placed;
	}

	@Override
	public int skipped() {
		return skipped;
	}

	@Override
	public int failed() {
		return failed;
	}

	@Override
	public double progress() {
		return n == 0 ? 1.0 : (double) done / n;
	}

	/**
	 * Status line ({@code /t3craft demo status}): sites done / total; per open site its storey, wave, builder
	 * entries left and crew; the focus and whether the player is inside a site; and the counters of the acceptance
	 * criteria (docs/features/builder-sites.md). Asked from the client thread while the server thread works: it only
	 * reads, and a value caught half-way gives a short line instead of an error.
	 */
	public String describe() {
		try {
			return status();
		} catch (RuntimeException e) {
			return "sites " + sitesDone + "/" + p.sites + " (busy, ask again)";
		}
	}

	private String status() {
		StringBuilder sb = new StringBuilder("sites ").append(sitesDone).append('/').append(p.sites);
		int working = 0;
		for (int s = 0; s < open.length; s++) {
			Open o = open[s];
			if (o == null) {
				continue;
			}
			working += o.working;
			SiteProgram.Program g = o.prog;
			int w = o.wave;
			sb.append(" | ").append(o.focus ? "focus " : o.rear ? "rear " : "closing ").append(p.siteName[s]);
			if (g == null) {
				sb.append(" planning");
			} else if (w < g.waves()) {
				int stage = g.waveStage()[w];
				sb.append(" storey ").append(g.waveStorey()[w] + 1).append('/').append(p.storeyY[s].length)
						.append(p.storeyReal[s][g.waveStorey()[w]] ? " (floor) " : " (lift) ")
						.append(stage >= 0 && stage < SiteProgram.STAGE_NAMES.length ? SiteProgram.STAGE_NAMES[stage] : "?").append(" wave ").append(w + 1)
						.append('/').append(g.waves()).append(" bays-left ").append(o.waveTasksLeft);
			}
			sb.append(" left ").append(siteLeft[s]).append('/').append(p.siteTotal[s]).append(" crew ").append(o.crew);
			if (o.leaving) {
				sb.append(" leaving");
			}
		}
		int focus = focusSite;
		int inside = insideSite;
		sb.append(" | player ").append(inside < 0 ? "outside" : "in " + p.siteName[inside]);
		if (focus >= 0 && open[focus] == null) {
			sb.append(" | focus ").append(p.siteName[focus]).append(" waiting");
		}
		sb.append(" | builders ").append(working).append('/').append(builders);
		sb.append(" | off-site ").append(offSite).append(" storey-spread ").append(storeySpread).append(" task-switches walk ").append(switchWalk).append(" chunk ")
				.append(switchChunk);
		long all = insideAll;
		if (all > 0) {
			sb.append(String.format(Locale.ROOT, " in-site %.0f%%", Math.min(100.0, insideIn * 100.0 / all)));
		}
		sb.append(" placed ").append(builderPlaced);
		if (detailPlaced > 0) {
			sb.append(" details ").append(detailPlaced);
		}
		if (lifted > 0) {
			sb.append(" lifted-floor ").append(lifted);
		}
		if (tickNo - lastPark < PARK_TICKS) {
			sb.append(" waiting-chunks");
		}
		sb.append(" centre ").append(Math.round(cx)).append(',').append(Math.round(cz));
		if (level != WalkLevels.NO_LEVEL) {
			sb.append(" level ").append(level);
		}
		return sb.toString();
	}
}
