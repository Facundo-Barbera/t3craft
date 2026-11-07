package dev.t3craft.build;

/**
 * Pure scheduling core of a {@link BuildJob}: walks the indices {@code 0..total-1} of a plan in
 * order under a per-tick block budget, with pause / resume / cancel and progress. Knows nothing
 * about Minecraft, so it is unit-tested on its own.
 *
 * <p>Budget: {@code blocksPerTick} may be fractional ({@code 0.5} = one block every two ticks,
 * {@code 3} = three per tick). Credit only accrues while running and is capped, so a long pause
 * never turns into a burst. Steps that report {@link Outcome#SKIPPED} (the world already has that
 * block) are free but bounded per tick by {@code maxSkipsPerTick}.
 *
 * <p>Threading: {@link #tick} runs on one thread (the server thread); {@link #pause},
 * {@link #resume}, {@link #cancel} and the getters are safe from any thread and take effect on
 * the next tick at the latest.
 */
public final class BuildCursor {
	public enum Outcome {
		PLACED,
		/** The target already matched; costs no budget. */
		SKIPPED,
		/** Could not place (unloaded chunk, refused); costs budget so a bad plan cannot spin. */
		FAILED
	}

	@FunctionalInterface
	public interface Step {
		Outcome apply(int index);
	}

	public static final int DEFAULT_MAX_SKIPS_PER_TICK = 256;

	private final int total;
	private final double blocksPerTick;
	private final int maxSkipsPerTick;
	private double credit;
	private volatile int next;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile JobState state = JobState.PENDING;

	public BuildCursor(int total, double blocksPerTick) {
		this(total, blocksPerTick, DEFAULT_MAX_SKIPS_PER_TICK);
	}

	public BuildCursor(int total, double blocksPerTick, int maxSkipsPerTick) {
		if (total < 0) {
			throw new IllegalArgumentException("total < 0");
		}
		if (!(blocksPerTick > 0) || Double.isInfinite(blocksPerTick)) {
			throw new IllegalArgumentException("blocksPerTick must be > 0 and finite: " + blocksPerTick);
		}
		this.total = total;
		this.blocksPerTick = blocksPerTick;
		this.maxSkipsPerTick = Math.max(0, maxSkipsPerTick);
	}

	/**
	 * Advances by at most the budget. Returns how many steps were applied (placed + skipped +
	 * failed) this tick; 0 when paused, finished or out of credit.
	 */
	public int tick(Step step) {
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
		if (next >= total) {
			finish();
			return 0;
		}
		credit = Math.min(credit + blocksPerTick, Math.max(1.0, blocksPerTick));
		int applied = 0;
		int skipsThisTick = 0;
		while (credit >= 1.0 && next < total && state == JobState.RUNNING) {
			Outcome o = step.apply(next);
			next++;
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
						// Bounded work per tick even for a plan that is mostly already built.
						credit = Math.min(credit, 0.999);
					}
				}
			}
		}
		if (next >= total) {
			finish();
		}
		return applied;
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	/** Pauses a pending or running job. Returns false if it was not pausable. */
	public synchronized boolean pause() {
		if (state == JobState.RUNNING || state == JobState.PENDING) {
			state = JobState.PAUSED;
			return true;
		}
		return false;
	}

	/** Resumes a paused job. Returns false if it was not paused. */
	public synchronized boolean resume() {
		if (state == JobState.PAUSED) {
			state = JobState.RUNNING;
			return true;
		}
		return false;
	}

	/** Cancels a non-terminal job; already placed blocks stay. Returns false if already terminal. */
	public synchronized boolean cancel() {
		if (state.isTerminal()) {
			return false;
		}
		state = JobState.CANCELLED;
		return true;
	}

	public JobState state() {
		return state;
	}

	public int total() {
		return total;
	}

	/** Index of the next step to apply; equals the number of steps already applied. */
	public int done() {
		return next;
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

	public double blocksPerTick() {
		return blocksPerTick;
	}

	/** Fraction of the plan already processed, 0..1 (an empty plan is complete: 1). */
	public double progress() {
		return total == 0 ? 1.0 : (double) next / total;
	}
}
