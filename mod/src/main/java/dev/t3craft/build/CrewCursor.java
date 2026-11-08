package dev.t3craft.build;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@link CrewSchedule}: one front per builder, each walking its lane. Plain Java, unit-tested.
 *
 * <p>The head of a lane is applied once (1) its dependencies are applied (support, same position,
 * the solid neighbours of decoration) and (2) its builder walked there ({@link CrewSchedule#walk}
 * ticks, counted while running; an entry already right in the world is skipped without walking).
 * Each tick the allowance from the global {@link BuildBudget} becomes credit and the ready fronts
 * take one step each in turn, like {@link FrontCursor}; the budget per tick never changes. As the
 * schedule is a topological order of the dependencies, some lane can always go on: no deadlock.
 *
 * <p>Materials (charged jobs): the simulated order is cut into segments of {@code segmentSize}
 * entries checked as a whole when they start (as layers are in {@link FrontCursor}); free jobs use
 * one segment. {@link BuildCursor.Outcome#BLOCKED} pauses only that front; once every front with work
 * left is blocked, the cursor pauses ({@link #blocked()}) and {@link #resume()} retries.
 */
public final class CrewCursor implements JobCursor {
	/** Builder {@code front} starts walking (for {@code ticks}) to its spot for plan entry {@code index}, step {@code step} of its lane. */
	@FunctionalInterface
	public interface Walker {
		void walking(int front, int index, int step, int ticks);
	}

	private final CrewSchedule schedule;
	private final int segmentSize;
	private final int segments;
	private final int maxSkipsPerTick;
	private final int[] pos;
	private final int[] walkLeft;
	private final boolean[] frontBlocked;
	private final boolean[] applied;
	private final int[] segmentLeft;
	private @Nullable IntPredicate already;
	private @Nullable Walker walker;
	private double credit;
	private int rr;
	private volatile int segment;
	private volatile int done;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile boolean blocked;
	private volatile JobState state = JobState.PENDING;

	public CrewCursor(CrewSchedule schedule) {
		this(schedule, 0, BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK);
	}

	/** @param segmentSize entries per material segment; ≤ 0 = the whole plan */
	public CrewCursor(CrewSchedule schedule, int segmentSize, int maxSkipsPerTick) {
		this.schedule = schedule;
		int n = schedule.total();
		this.segmentSize = segmentSize <= 0 ? Math.max(1, n) : segmentSize;
		this.segments = n == 0 ? 0 : (n + this.segmentSize - 1) / this.segmentSize;
		this.maxSkipsPerTick = Math.max(0, maxSkipsPerTick);
		int k = schedule.builders();
		pos = new int[k];
		walkLeft = new int[k];
		Arrays.fill(walkLeft, -1);
		frontBlocked = new boolean[k];
		applied = new boolean[n];
		segmentLeft = new int[segments];
		for (int s = 0; s < segments; s++) {
			segmentLeft[s] = Math.min(this.segmentSize, n - s * this.segmentSize);
		}
	}

	/** Entries already right in the world (skipped without walking); null = none known. Server thread. */
	public void probe(@Nullable IntPredicate already) {
		this.already = already;
	}

	/** Who is told when a builder starts walking. Server thread. */
	public void walker(@Nullable Walker walker) {
		this.walker = walker;
	}

	public CrewSchedule schedule() {
		return schedule;
	}

	private int segmentOf(int index) {
		return schedule.rank(index) / segmentSize;
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
		advanceSegments();
		if (segment >= segments) {
			finish();
			return 0;
		}
		int k = pos.length;
		for (int f = 0; f < k; f++) {
			prepare(f, true);
		}
		double a = Math.max(0, allowance);
		credit = Math.min(credit + a, Math.max(1.0, a));
		int applies = 0;
		int skipsThisTick = 0;
		while (credit >= 1.0 && state == JobState.RUNNING && segment < segments) {
			int f = -1;
			for (int j = 0; j < k; j++) {
				int c = (rr + j) % k;
				if (ready(c)) {
					f = c;
					break;
				}
			}
			if (f < 0) {
				if (allBlocked()) {
					block();
				}
				break;
			}
			rr = (f + 1) % k;
			int index = schedule.lane(f)[pos[f]];
			BuildCursor.Outcome o = step.apply(f, index);
			if (o == BuildCursor.Outcome.BLOCKED) {
				frontBlocked[f] = true;
				continue;
			}
			applied[index] = true;
			segmentLeft[segmentOf(index)]--;
			pos[f]++;
			walkLeft[f] = -1;
			done++;
			applies++;
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
			advanceSegments();
			// Lanes waiting on this entry (support, next segment) may go now; this lane's next step too.
			for (int g = 0; g < k; g++) {
				prepare(g, false);
			}
		}
		advanceSegments();
		if (segment >= segments) {
			finish();
		}
		return applies;
	}

	/** Starts the walk to the head of lane {@code f} once it may go; counts the walk down on the tick pass. */
	private void prepare(int f, boolean tickPass) {
		int[] lane = schedule.lane(f);
		if (pos[f] >= lane.length) {
			return;
		}
		int index = lane[pos[f]];
		if (segmentOf(index) > segment || !depsDone(index)) {
			return;
		}
		if (walkLeft[f] < 0) {
			IntPredicate al = already;
			if (al != null && al.test(index)) {
				walkLeft[f] = 0;
			} else {
				int w = schedule.walk(f, pos[f]);
				walkLeft[f] = w;
				if (w > 0) {
					Walker wk = walker;
					if (wk != null) {
						wk.walking(f, index, pos[f], w);
					}
				}
			}
			return;
		}
		if (tickPass && walkLeft[f] > 0) {
			walkLeft[f]--;
		}
	}

	private boolean ready(int f) {
		int[] lane = schedule.lane(f);
		if (frontBlocked[f] || pos[f] >= lane.length || walkLeft[f] != 0) {
			return false;
		}
		int index = lane[pos[f]];
		return segmentOf(index) == segment && depsDone(index);
	}

	private boolean depsDone(int index) {
		for (int d : schedule.deps(index)) {
			if (!applied[d]) {
				return false;
			}
		}
		return true;
	}

	/** Some front is blocked and none of the others with work left can go on by itself. */
	private boolean allBlocked() {
		boolean any = false;
		for (int f = 0; f < pos.length; f++) {
			int[] lane = schedule.lane(f);
			if (pos[f] >= lane.length || segmentOf(lane[pos[f]]) != segment) {
				continue;
			}
			if (!frontBlocked[f]) {
				return false;
			}
			any = true;
		}
		return any;
	}

	private void advanceSegments() {
		while (segment < segments && segmentLeft[segment] == 0) {
			segment++;
			Arrays.fill(frontBlocked, false);
		}
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
			Arrays.fill(frontBlocked, false);
			blocked = false;
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
		return blocked && state == JobState.PAUSED;
	}

	@Override
	public boolean frontBlocked(int f) {
		return frontBlocked[f];
	}

	@Override
	public int segment() {
		return segment;
	}

	@Override
	public int[] pendingInSegment() {
		if (segment >= segments) {
			return new int[0];
		}
		int from = segment * segmentSize;
		int to = Math.min(schedule.total(), from + segmentSize);
		int[] out = new int[to - from];
		int j = 0;
		for (int r = from; r < to; r++) {
			int i = schedule.orderAt(r);
			if (!applied[i]) {
				out[j++] = i;
			}
		}
		out = Arrays.copyOf(out, j);
		Arrays.sort(out);
		return out;
	}

	/** Whether plan entry {@code index} was applied. Server thread. */
	public boolean applied(int index) {
		return applied[index];
	}

	/** Ticks builder {@code f} still walks before its next step (0 = there, -1 = not started). */
	public int walking(int f) {
		return walkLeft[f];
	}

	@Override
	public int frontCount() {
		return pos.length;
	}

	@Override
	public int frontOf(int index) {
		return schedule.owner(index);
	}

	@Override
	public JobState state() {
		return state;
	}

	@Override
	public int total() {
		return schedule.total();
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
		int t = schedule.total();
		return t == 0 ? 1.0 : (double) done / t;
	}
}
