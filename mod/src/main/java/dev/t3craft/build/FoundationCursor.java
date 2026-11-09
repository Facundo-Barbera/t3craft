package dev.t3craft.build;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * feat/build-near-player: a {@link BuildPlanner#CREW} job in two phases.
 * <ol>
 *   <li><b>Foundation</b> ({@link Foundation}): ground, fill and floors, placed bottom-up in plan order
 *   (a layer is finished before the next one starts) as fast as the per-tick foundation budget allows
 *   ({@link BuildBudget#foundationBlocks}: {@code build.foundationBlocksPerTick} blocks and
 *   {@code build.foundationMsPerTick} ms per server tick, all jobs together, with the MSPT / FPS
 *   throttle). No builder takes part: {@link #frontOf} is -1 for these entries, so the client animates
 *   nothing (no swing, no item), and the placements go through the same sink (guards, effect cap).</li>
 *   <li><b>Visible</b>: the rest of the plan with a {@link CrewCursor} over a sub-plan whose schedule
 *   treats the foundation as solid ground; indices are mapped back to the job's plan.</li>
 * </ol>
 * Only free jobs use it (a charged job checks materials per segment, which this phase skips). Entries
 * already right in the world are skipped (resume).
 */
final class FoundationCursor implements JobCursor {
	private final int[] foundation;
	private final @Nullable CrewCursor crew;
	private final int[] restToPlan;
	private final int[] planToRest;
	private final int total;
	private final int fronts;
	private int next;
	private volatile int fDone;
	private volatile int fPlaced;
	private volatile int fSkipped;
	private volatile int fFailed;
	private volatile JobState state = JobState.PENDING;

	private FoundationCursor(int total, int[] foundation, int[] rest, @Nullable CrewCursor crew, int fronts) {
		this.total = total;
		this.foundation = foundation;
		this.restToPlan = rest;
		this.crew = crew;
		this.fronts = Math.max(1, fronts);
		this.planToRest = new int[total];
		Arrays.fill(planToRest, -1);
		for (int r = 0; r < rest.length; r++) {
			planToRest[rest[r]] = r;
		}
	}

	/**
	 * Splits {@code plan} and plans the crew for the visible rest (off-thread: {@link DeferredCursor}).
	 * Returns a plain {@link CrewCursor} over the whole plan when there is no foundation worth a phase.
	 *
	 * @param focus plan-relative {@code {x, z}} the crew starts from (the player), or null
	 */
	static JobCursor plan(BuildPlan plan, int fronts, int @Nullable [] focus) {
		Foundation.Split split = Foundation.of(plan);
		if (!split.any()) {
			return new CrewCursor(CrewSchedule.of(plan, fronts, null, focus), 0, BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK);
		}
		int[] rest = split.rest();
		CrewCursor crew = null;
		if (rest.length > 0) {
			java.util.List<Placement> ps = new java.util.ArrayList<>(rest.length);
			for (int i : rest) {
				ps.add(plan.get(i));
			}
			crew = new CrewCursor(CrewSchedule.of(BuildPlan.of(ps), fronts, split.cells(), focus), 0, BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK);
		}
		return new FoundationCursor(plan.size(), split.foundation(), rest, crew, fronts);
	}

	/** Still placing the foundation (the job wants almost nothing of the crew budget meanwhile). */
	@Override
	public boolean fastPhase() {
		return next < foundation.length && !state.isTerminal();
	}

	/** Foundation entries / all entries (status). */
	int foundationSize() {
		return foundation.length;
	}

	/** The crew of the visible rest, or null when the plan is all foundation. Its indices are sub-plan indices. */
	@Nullable CrewCursor crew() {
		return crew;
	}

	/** Entries already right in the world, by plan index. Server thread. */
	void probe(@Nullable IntPredicate already) {
		CrewCursor c = crew;
		if (c != null) {
			c.probe(already == null ? null : r -> already.test(restToPlan[r]));
		}
	}

	/** Who hears a builder start walking (plan indices). Server thread. */
	void walker(CrewCursor.@Nullable Walker w) {
		CrewCursor c = crew;
		if (c != null) {
			c.walker(w == null ? null : (front, index, stand, ticks) -> w.walking(front, restToPlan[index], stand, ticks));
		}
	}

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
		if (next < foundation.length) {
			return tickFoundation(step);
		}
		CrewCursor c = crew;
		if (c == null) {
			finish();
			return 0;
		}
		return c.tick(allowance, (front, r) -> step.apply(front, restToPlan[r]));
	}

	private int tickFoundation(FrontCursor.Step step) {
		BuildBudget b = BuildBudget.global();
		int max = b.foundationBlocks();
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return 0;
		}
		long t0 = System.nanoTime();
		int count = 0;
		try {
			while (next < foundation.length && count < max && state == JobState.RUNNING) {
				BuildCursor.Outcome o = step.apply(-1, foundation[next]);
				if (o == BuildCursor.Outcome.BLOCKED) {
					break; // free jobs never block; keep the entry for the next tick
				}
				next++;
				fDone++;
				count++;
				switch (o) {
					case PLACED -> fPlaced++;
					case SKIPPED -> fSkipped++;
					case FAILED -> fFailed++;
					default -> {
					}
				}
				if ((count & 63) == 0 && System.nanoTime() - t0 > nanos) {
					break;
				}
			}
		} finally {
			b.spendFoundation(count, System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_FOUNDATION, t0);
		}
		if (next >= foundation.length && crew == null) {
			finish();
		}
		return count;
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	private boolean crewPhase() {
		return next >= foundation.length && crew != null;
	}

	@Override
	public synchronized boolean pause() {
		CrewCursor c = crew;
		if (crewPhase() && state != JobState.CANCELLED) {
			return c.pause();
		}
		if (state == JobState.RUNNING || state == JobState.PENDING) {
			state = JobState.PAUSED;
			if (c != null) {
				c.pause();
			}
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean resume() {
		CrewCursor c = crew;
		if (crewPhase() && state != JobState.CANCELLED) {
			return c.resume();
		}
		if (state == JobState.PAUSED) {
			state = JobState.RUNNING;
			if (c != null) {
				c.resume();
			}
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean cancel() {
		boolean changed = false;
		if (!state.isTerminal()) {
			state = JobState.CANCELLED;
			changed = true;
		}
		CrewCursor c = crew;
		if (c != null && c.cancel()) {
			changed = true;
		}
		return changed;
	}

	@Override
	public boolean blocked() {
		CrewCursor c = crew;
		return crewPhase() && c != null && c.blocked();
	}

	@Override
	public boolean frontBlocked(int f) {
		CrewCursor c = crew;
		return crewPhase() && c != null && c.frontBlocked(f);
	}

	@Override
	public int segment() {
		CrewCursor c = crew;
		return c == null ? 0 : c.segment();
	}

	@Override
	public int[] pendingInSegment() {
		CrewCursor c = crew;
		if (!crewPhase() || c == null) {
			int[] out = Arrays.copyOfRange(foundation, Math.min(next, foundation.length), foundation.length);
			Arrays.sort(out);
			return out;
		}
		int[] r = c.pendingInSegment();
		int[] out = new int[r.length];
		for (int k = 0; k < r.length; k++) {
			out[k] = restToPlan[r[k]];
		}
		Arrays.sort(out);
		return out;
	}

	@Override
	public int frontCount() {
		CrewCursor c = crew;
		return c == null ? fronts : c.frontCount();
	}

	/** -1 for a foundation entry (no builder animates it); else the builder of the visible entry. */
	@Override
	public int frontOf(int index) {
		int r = index >= 0 && index < planToRest.length ? planToRest[index] : -1;
		CrewCursor c = crew;
		return r < 0 || c == null ? -1 : c.frontOf(r);
	}

	@Override
	public JobState state() {
		// own state while laying the foundation (and once cancelled or completed); the crew's afterwards
		JobState s = state;
		CrewCursor c = crew;
		if (s.isTerminal() || !crewPhase() || c == null) {
			return s;
		}
		return c.state();
	}

	@Override
	public int total() {
		return total;
	}

	@Override
	public int done() {
		CrewCursor c = crew;
		return fDone + (c == null ? 0 : c.done());
	}

	@Override
	public int placed() {
		CrewCursor c = crew;
		return fPlaced + (c == null ? 0 : c.placed());
	}

	@Override
	public int skipped() {
		CrewCursor c = crew;
		return fSkipped + (c == null ? 0 : c.skipped());
	}

	@Override
	public int failed() {
		CrewCursor c = crew;
		return fFailed + (c == null ? 0 : c.failed());
	}

	@Override
	public double progress() {
		return total == 0 ? 1.0 : (double) done() / total;
	}
}
