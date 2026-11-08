package dev.t3craft.build;

/**
 * Scheduling core of a {@link BuildJob} with several fronts working at once: like {@link BuildCursor}
 * (budget per tick, pause / resume / cancel, progress, counters) but walking a {@link Fronts}
 * schedule. Plain Java, unit-tested.
 *
 * <p>Each tick the allowance (from the global {@link BuildBudget}) becomes credit, and the fronts that
 * still have work in the current segment take one step each in turn (round-robin, starting after the
 * last front served, so no front is favoured). When every front finished the segment, the next
 * segment opens (layer barrier, see {@link Fronts}).
 *
 * <p>{@link BuildCursor.Outcome#BLOCKED} pauses only that front: the step is not consumed and the other
 * fronts go on. When every front with work left is blocked, the cursor goes to {@link JobState#PAUSED}
 * ({@link #blocked()} true); {@link #resume()} unblocks every front and retries the same entries.
 * Every plan index is applied exactly once whatever the pauses, blocks and allowances (resume never
 * repeats nor skips).
 *
 * <p>Credit accrues only while running and is capped, so a pause never turns into a burst; skipped
 * entries (already right) are free but bounded per tick. Threading as {@link BuildCursor}.
 */
public final class FrontCursor implements JobCursor {
	@FunctionalInterface
	public interface Step {
		BuildCursor.Outcome apply(int front, int index);
	}

	private final Fronts fronts;
	private final int maxSkipsPerTick;
	private final int[] pos;
	private final boolean[] frontBlocked;
	private double credit;
	private int rr;
	private volatile int segment;
	private volatile int done;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile boolean blocked;
	private volatile JobState state = JobState.PENDING;

	public FrontCursor(Fronts fronts) {
		this(fronts, BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK);
	}

	public FrontCursor(Fronts fronts, int maxSkipsPerTick) {
		this.fronts = fronts;
		this.maxSkipsPerTick = Math.max(0, maxSkipsPerTick);
		this.pos = new int[fronts.fronts()];
		this.frontBlocked = new boolean[fronts.fronts()];
	}

	/**
	 * Advances by at most {@code allowance} placements (fractional is fine). Returns how many steps
	 * were applied (placed + skipped + failed); 0 when paused, finished or out of credit.
	 */
	public int tick(double allowance, Step step) {
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
		advanceFinishedSegments();
		if (segment >= fronts.segments()) {
			finish();
			return 0;
		}
		double a = Math.max(0, allowance);
		credit = Math.min(credit + a, Math.max(1.0, a));
		int applied = 0;
		int skipsThisTick = 0;
		int n = pos.length;
		while (credit >= 1.0 && state == JobState.RUNNING && segment < fronts.segments()) {
			int[][] lanes = fronts.lanes(segment);
			int f = -1;
			for (int k = 0; k < n; k++) {
				int c = (rr + k) % n;
				if (!frontBlocked[c] && pos[c] < lanes[c].length) {
					f = c;
					break;
				}
			}
			if (f < 0) {
				if (segmentDone(lanes)) {
					nextSegment();
					continue;
				}
				block();
				break;
			}
			rr = (f + 1) % n;
			BuildCursor.Outcome o = step.apply(f, lanes[f][pos[f]]);
			if (o == BuildCursor.Outcome.BLOCKED) {
				frontBlocked[f] = true;
				continue;
			}
			pos[f]++;
			done++;
			applied++;
			switch (o) {
				case PLACED -> {
					placed++;
					credit -= 1.0;
				}
				case FAILED -> {
					failed++;
					credit -= 1.0;
				}
				case SKIPPED -> {
					skipped++;
					if (++skipsThisTick >= maxSkipsPerTick) {
						credit = Math.min(credit, 0.999);
					}
				}
				case BLOCKED -> throw new AssertionError();
			}
		}
		advanceFinishedSegments();
		if (segment >= fronts.segments()) {
			finish();
		}
		return applied;
	}

	private void advanceFinishedSegments() {
		while (segment < fronts.segments() && segmentDone(fronts.lanes(segment))) {
			nextSegment();
		}
	}

	private boolean segmentDone(int[][] lanes) {
		for (int k = 0; k < pos.length; k++) {
			if (pos[k] < lanes[k].length) {
				return false;
			}
		}
		return true;
	}

	private void nextSegment() {
		segment++;
		java.util.Arrays.fill(pos, 0);
		java.util.Arrays.fill(frontBlocked, false);
	}

	private synchronized void block() {
		if (state == JobState.RUNNING) {
			state = JobState.PAUSED;
			blocked = true;
		}
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	/** Pauses a pending or running cursor. Returns false if it was not pausable. */
	public synchronized boolean pause() {
		if (state == JobState.RUNNING || state == JobState.PENDING) {
			state = JobState.PAUSED;
			return true;
		}
		return false;
	}

	/** Resumes a paused cursor; blocked fronts are retried. Returns false if it was not paused. */
	public synchronized boolean resume() {
		if (state == JobState.PAUSED) {
			java.util.Arrays.fill(frontBlocked, false);
			blocked = false;
			state = JobState.RUNNING;
			return true;
		}
		return false;
	}

	/** Cancels a non-terminal cursor; already placed blocks stay. Returns false if already terminal. */
	public synchronized boolean cancel() {
		if (state.isTerminal()) {
			return false;
		}
		state = JobState.CANCELLED;
		return true;
	}

	/** Paused because every front with work left was blocked (not by {@link #pause()}). */
	public boolean blocked() {
		return blocked && state == JobState.PAUSED;
	}

	/** Whether front {@code f} is blocked in the current segment. Server thread. */
	public boolean frontBlocked(int f) {
		return frontBlocked[f];
	}

	/** Index of the current segment (layer); {@code segments()} once finished. */
	public int segment() {
		return segment;
	}

	/** Plan indices of the current segment not applied yet, in plan order. Server thread. */
	public int[] pendingInSegment() {
		if (segment >= fronts.segments()) {
			return new int[0];
		}
		int[][] lanes = fronts.lanes(segment);
		int count = 0;
		for (int k = 0; k < pos.length; k++) {
			count += lanes[k].length - pos[k];
		}
		int[] out = new int[count];
		int j = 0;
		for (int k = 0; k < pos.length; k++) {
			for (int p = pos[k]; p < lanes[k].length; p++) {
				out[j++] = lanes[k][p];
			}
		}
		java.util.Arrays.sort(out);
		return out;
	}

	public Fronts fronts() {
		return fronts;
	}

	@Override
	public int frontCount() {
		return fronts.fronts();
	}

	@Override
	public int frontOf(int index) {
		return fronts.frontOf(index);
	}

	public JobState state() {
		return state;
	}

	public int total() {
		return fronts.total();
	}

	/** Steps already applied (placed + skipped + failed). */
	public int done() {
		return done;
	}

	public int placed() {
		return placed;
	}

	public int skipped() {
		return skipped;
	}

	public int failed() {
		return failed;
	}

	/** Fraction of the plan already processed, 0..1 (an empty plan is complete: 1). */
	public double progress() {
		int t = fronts.total();
		return t == 0 ? 1.0 : (double) done / t;
	}
}
