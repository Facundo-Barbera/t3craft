package dev.t3craft.build;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@link CrewSchedule}: one front per builder. Plain Java, no Minecraft classes.
 *
 * <p>The lanes of the schedule are a plan, not a contract: each builder picks its next target at run
 * time, so it never stands still waiting for a dependency that sits in another builder's lane on
 * the far side of the map (head-of-line blocking; the simulated timing never matches the real one:
 * budget per tick, walks, material segments). An entry may go once its dependencies are applied
 * (support, same position, the solid neighbours of decoration) and nobody else claimed it. When a
 * builder is free it chooses, in order (see {@code docs/research/builders-ai.md}, "Builders
 * independientes y rápidos"):
 * <ol>
 *   <li><b>Burst</b>: a ready entry it reaches from where it stands, without walking: first among the
 *   next {@link #BURST_WINDOW} entries of its own lane, then in a neighbour's look-ahead when that
 *   neighbour does not reach it. At each stop a builder places everything ready around it, several
 *   per tick when the credit allows.</li>
 *   <li><b>Own lane, look-ahead</b>: the first ready entry among the next {@link #WINDOW} entries of
 *   its lane (the head when it is ready); skipped entries are tried again on the next pick.</li>
 *   <li><b>Steal the blocker</b>: when the head is not ready, the ready ancestors of the head (its
 *   missing dependencies, transitively, up to {@link #BLOCKER_SEARCH} nodes) that this builder
 *   reaches no slower than their owner, or within {@link #NEAR_TICKS}. It is taken instead of the
 *   look-ahead entry when it is the cheaper walk.</li>
 *   <li><b>Steal work</b>: nothing at all for it: the nearest ready entry of another lane's
 *   look-ahead, provided this builder is no slower than that lane's owner or the entry is not among
 *   the owner's next {@link #STEAL_KEEP} entries.</li>
 * </ol>
 * Claiming is exclusive ({@link #frontOf} reports the builder that claimed/placed an entry), so two
 * builders never place the same entry. The walk is computed from where the builder really is: none
 * when the block is in reach ({@link CrewPlanner#REACH}), when it is air (clearing) or already right
 * in the world; otherwise to the planned stand spot (of that entry or of a nearby one in the
 * look-aheads) that reaches the entry and the most pending entries around it, so the next stop
 * serves a burst; the {@link Walker} hears the new entry and destination. Builders trot at
 * {@link #RUN_PER_TICK} (twice the planner's pace), place as soon as the block comes in reach, and
 * never wait more than {@link #MAX_WALK_TICKS} for one walk (the avatar takes a shortcut). Picks scan fronts and lanes in a fixed order, so the same
 * plan and the same tick inputs give the same run (resumable: entries already right are skipped).
 *
 * <p>No deadlock: the schedule is a topological order of the dependencies and each lane follows it,
 * so the lowest-ranked pending entry of the segment is ready and the head of its owner's lane (or
 * claimed by someone who will place it); what the plan leaves floating is ranked last ({@code forced}).
 * Each tick the allowance from the global {@link BuildBudget} becomes credit and the ready fronts
 * take steps in turn, like {@link FrontCursor}: the credit of a builder that is walking goes to the
 * ones that are ready (one ready builder may use all of it); the budget per tick never changes.
 *
 * <p>Materials (charged jobs): the simulated order is cut into segments of {@code segmentSize}
 * entries checked as a whole when they start (as layers are in {@link FrontCursor}); free jobs use
 * one segment, and builders only pick entries of the current segment. {@link BuildCursor.Outcome#BLOCKED}
 * pauses only that front (it keeps its target); once no other front can go on, the cursor pauses
 * ({@link #blocked()}) and {@link #resume()} retries.
 */
public final class CrewCursor implements JobCursor {
	/** Look-ahead of a lane: entries (not applied yet) a builder may pick out of order. */
	static final int WINDOW = 16;
	/** Look-ahead scanned for a ready entry in reach without walking (the burst at each stop). */
	static final int BURST_WINDOW = 48;
	/** Pace of a builder at work, blocks per tick (the client's top work pace; the planner keeps its own). */
	static final double RUN_PER_TICK = 0.6;
	/** Longest wait for one walk; a longer way is a shortcut for the avatar. */
	static final int MAX_WALK_TICKS = 20;
	/** Missing dependencies of a lane head examined when looking for one to steal. */
	static final int BLOCKER_SEARCH = 48;
	/** A blocking dependency this close (walk ticks) is stolen whatever its owner does. */
	static final int NEAR_TICKS = (int) Math.ceil(6.0 / 0.6);
	/** Next entries of a lane that only a builder no slower than its owner may steal. */
	static final int STEAL_KEEP = 4;
	/**
	 * feat/build-near-player: work of another lane farther than this (walk ticks, about 24 blocks at
	 * {@link #RUN_PER_TICK}) is not stolen: a builder keeps to its own side instead of crossing the map.
	 */
	static final int STEAL_MAX_TICKS = 40;

	/**
	 * Builder {@code front} starts walking to plan-relative feet cell {@code stand} to apply plan entry
	 * {@code index}, to arrive in {@code ticks} (at {@link #RUN_PER_TICK}, at most {@link #MAX_WALK_TICKS};
	 * it may place a little before, once the block is in reach); told whenever a builder picks a
	 * target it must walk to.
	 */
	@FunctionalInterface
	public interface Walker {
		void walking(int front, int index, int[] stand, int ticks);
	}

	private final CrewSchedule schedule;
	private final int segmentSize;
	private final int segments;
	private final int maxSkipsPerTick;
	/** First step of each lane that may still be pending (everything before it is applied). */
	private final int[] head;
	private final int[] target;
	private final int[] walkLeft;
	private final int[] px;
	private final int[] py;
	private final int[] pz;
	private final boolean[] frontBlocked;
	private final boolean[] applied;
	/** Front that claimed (and so places) each entry; -1 = nobody yet. */
	private final int[] by;
	private final int[] segmentLeft;
	private final int[] seen;
	private int stamp;
	private final int[] queue;
	private final int[] cand = new int[2 * WINDOW];
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
		head = new int[k];
		target = new int[k];
		Arrays.fill(target, -1);
		walkLeft = new int[k];
		Arrays.fill(walkLeft, -1);
		px = new int[k];
		py = new int[k];
		pz = new int[k];
		for (int f = 0; f < k; f++) {
			int[] st = schedule.start(f);
			px[f] = st[0];
			py[f] = st[1];
			pz[f] = st[2];
		}
		frontBlocked = new boolean[k];
		applied = new boolean[n];
		by = new int[n];
		Arrays.fill(by, -1);
		seen = new int[n];
		queue = new int[BLOCKER_SEARCH];
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
		int k = head.length;
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
			int index = target[f];
			BuildCursor.Outcome o = step.apply(f, index);
			if (o == BuildCursor.Outcome.BLOCKED) {
				frontBlocked[f] = true;
				continue;
			}
			applied[index] = true;
			segmentLeft[segmentOf(index)]--;
			target[f] = -1;
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
			// Builders waiting on this entry (support, next segment) may go now; this one picks its next.
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

	/** A free builder picks a target (and starts walking); the walk counts down on the tick pass. */
	private void prepare(int f, boolean tickPass) {
		if (target[f] < 0) {
			if (!frontBlocked[f]) {
				pick(f);
			}
			return;
		}
		if (tickPass && walkLeft[f] > 0) {
			walkLeft[f]--;
		}
	}

	/** Chooses the next target of free builder {@code f} (see the class doc); none = it waits. */
	private void pick(int f) {
		if (segment >= segments) {
			return;
		}
		int[] lane = schedule.lane(f);
		while (head[f] < lane.length && applied[lane[head[f]]]) {
			head[f]++;
		}
		// 0. burst: a ready entry of its own lane it reaches from where it stands (no walk)
		// 1. own lane, look-ahead
		int own = -1;
		int laneHead = -1;
		int count = 0;
		for (int s = head[f]; s < lane.length; s++) {
			int i = lane[s];
			if (applied[i]) {
				continue;
			}
			if (++count > BURST_WINDOW || segmentOf(i) > segment) {
				break;
			}
			if (by[i] >= 0) {
				continue;
			}
			if (count <= WINDOW && laneHead < 0) {
				laneHead = i;
			}
			if (!depsDone(i)) {
				continue;
			}
			if (cost(f, i) == 0) {
				claim(f, i);
				return;
			}
			if (count <= WINDOW && own < 0) {
				own = i;
			}
		}
		// 0b. burst: a ready entry of a neighbour's look-ahead in reach here and not of its owner
		int near = reachableElsewhere(f);
		if (near >= 0) {
			claim(f, near);
			return;
		}
		int ownCost = own < 0 ? Integer.MAX_VALUE : cost(f, own);
		// 2. steal what blocks the head
		int best = own;
		if (laneHead >= 0 && laneHead != own) {
			int blocker = blocker(f, laneHead);
			if (blocker >= 0 && (own < 0 || cost(f, blocker) < ownCost)) {
				best = blocker;
			}
		}
		// 3. steal work from another lane
		if (best < 0) {
			best = stealWork(f);
		}
		if (best >= 0) {
			claim(f, best);
		}
	}

	/** First ready entry in another lane's look-ahead that {@code f} reaches without walking and its owner does not. */
	private int reachableElsewhere(int f) {
		for (int g = 0; g < head.length; g++) {
			if (g == f) {
				continue;
			}
			int[] lane = schedule.lane(g);
			int count = 0;
			for (int s = head[g]; s < lane.length; s++) {
				int i = lane[s];
				if (applied[i]) {
					continue;
				}
				if (++count > WINDOW || segmentOf(i) > segment) {
					break;
				}
				if (by[i] < 0 && !schedule.air(i) && inReach(f, i) && !inReach(g, i) && depsDone(i)) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Nearest ready ancestor of {@code root} that {@code f} may take (breadth-first, bounded), or -1. */
	private int blocker(int f, int root) {
		int st = ++stamp;
		int qh = 0;
		int qt = 0;
		queue[qt++] = root;
		seen[root] = st;
		int best = -1;
		int bestCost = Integer.MAX_VALUE;
		while (qh < qt) {
			int i = queue[qh++];
			for (int d : schedule.deps(i)) {
				if (applied[d] || seen[d] == st || by[d] >= 0) {
					continue; // done, visited, or someone is on it already
				}
				seen[d] = st;
				if (segmentOf(d) == segment && depsDone(d)) {
					int c = cost(f, d);
					int g = schedule.owner(d);
					boolean may = g == f || c <= NEAR_TICKS || c <= cost(g, d);
					if (may && c < bestCost) {
						best = d;
						bestCost = c;
					}
				} else if (qt < queue.length) {
					queue[qt++] = d;
				}
			}
		}
		return best;
	}

	/** Nearest ready entry in the look-ahead of another lane that {@code f} may take, or -1. */
	private int stealWork(int f) {
		int best = -1;
		int bestCost = Integer.MAX_VALUE;
		for (int g = 0; g < head.length; g++) {
			if (g == f) {
				continue;
			}
			int[] lane = schedule.lane(g);
			int count = 0;
			int free = 0;
			for (int s = head[g]; s < lane.length; s++) {
				int i = lane[s];
				if (applied[i]) {
					continue;
				}
				if (++count > WINDOW || segmentOf(i) > segment) {
					break;
				}
				if (by[i] >= 0) {
					continue;
				}
				int ahead = free++;
				if (!depsDone(i)) {
					continue;
				}
				int c = cost(f, i);
				if (c <= STEAL_MAX_TICKS && c < bestCost && (ahead >= STEAL_KEEP || c <= cost(g, i))) {
					best = i;
					bestCost = c;
				}
			}
		}
		return best;
	}

	/** Ticks builder {@code f} walks before it can apply {@code i}, from where it is (or is heading). */
	private int cost(int f, int i) {
		// (the world probe is left to claim: it reads blocks, too dear for every candidate)
		if (schedule.air(i) || inReach(f, i)) {
			return 0;
		}
		return walkTicks(f, i, schedule.standFor(i));
	}

	/**
	 * Ticks before {@code f}, walking at {@link #RUN_PER_TICK} towards {@code stand}, can apply {@code i}:
	 * it places as soon as the block comes in reach (about {@code REACH − reach left at the spot}
	 * before arriving). Not capped: compares builders; {@link #claim} caps the real wait.
	 */
	private int walkTicks(int f, int i, int[] stand) {
		double d = CrewPlanner.walkDistance(px[f], py[f], pz[f], stand[0], stand[1], stand[2]);
		double early = Math.max(0, CrewPlanner.REACH - eyeDistance(stand, i));
		double left = Math.max(0, d - early);
		return left <= 1e-9 ? 0 : (int) Math.min(1_000_000, Math.ceil(left / RUN_PER_TICK));
	}

	private double eyeDistance(int[] s, int i) {
		double dx = s[0] - schedule.x(i);
		double dy = s[1] + CrewPlanner.EYE - (schedule.y(i) + 0.5);
		double dz = s[2] - schedule.z(i);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private boolean inReach(int f, int i) {
		return reaches(px[f], py[f], pz[f], i);
	}

	/** Feet in {@code (sx, sy, sz)} reach entry {@code i} (and the block is not where the body is). */
	private boolean reaches(int sx, int sy, int sz, int i) {
		int x = schedule.x(i);
		int y = schedule.y(i);
		int z = schedule.z(i);
		boolean body = x == sx && z == sz && (y == sy || y == sy + 1);
		return !body && CrewPlanner.inReach(sx, sy, sz, x, y, z);
	}

	/** {@code f} takes {@code i}: nobody else will; it walks there unless it needs not. */
	private void claim(int f, int i) {
		by[i] = f;
		target[f] = i;
		IntPredicate al = already;
		if (schedule.air(i) || inReach(f, i) || al != null && al.test(i)) {
			walkLeft[f] = 0;
			return;
		}
		int[] s = standCovering(f, i);
		// never waits more than MAX_WALK_TICKS: beyond that the avatar takes a shortcut (BuildCrew)
		int w = Math.min(MAX_WALK_TICKS, walkTicks(f, i, s));
		double d = CrewPlanner.walkDistance(px[f], py[f], pz[f], s[0], s[1], s[2]);
		int arrive = (int) Math.max(1, Math.min(MAX_WALK_TICKS, Math.ceil(d / RUN_PER_TICK)));
		px[f] = s[0];
		py[f] = s[1];
		pz[f] = s[2];
		walkLeft[f] = w;
		Walker wk = walker;
		if (wk != null) {
			wk.walking(f, i, s.clone(), arrive);
		}
	}

	/**
	 * Where {@code f} stands for {@code i}: among the planned spots of the look-ahead of its own lane
	 * and of {@code i}'s lane that reach {@code i}, the one reaching the most pending entries of those
	 * look-aheads (then the shortest walk; another builder's spot is never taken), so one stop serves
	 * a burst. The planned spot of {@code i} is always a candidate.
	 */
	private int[] standCovering(int f, int i) {
		int g = schedule.owner(i);
		int nc = collect(f, 0);
		if (g != f) {
			nc = collect(g, nc);
		}
		int[] best = schedule.standFor(i);
		int bestCover = cover(best, nc);
		double bestWalk = CrewPlanner.walkDistance(px[f], py[f], pz[f], best[0], best[1], best[2]);
		for (int c = 0; c < nc; c++) {
			int[] s = schedule.standFor(cand[c]);
			if (!reaches(s[0], s[1], s[2], i) || takenByOther(f, s)) {
				continue;
			}
			int cv = cover(s, nc);
			double w = CrewPlanner.walkDistance(px[f], py[f], pz[f], s[0], s[1], s[2]);
			if (cv > bestCover || cv == bestCover && w < bestWalk - 1e-9) {
				best = s;
				bestCover = cv;
				bestWalk = w;
			}
		}
		return best;
	}

	/** Appends the pending, unclaimed, non-air entries of lane {@code g}'s look-ahead to {@link #cand}. */
	private int collect(int g, int nc) {
		int[] lane = schedule.lane(g);
		int count = 0;
		for (int s = head[g]; s < lane.length && nc < cand.length; s++) {
			int i = lane[s];
			if (applied[i]) {
				continue;
			}
			if (++count > WINDOW || segmentOf(i) > segment) {
				break;
			}
			if (by[i] < 0 && !schedule.air(i)) {
				cand[nc++] = i;
			}
		}
		return nc;
	}

	private int cover(int[] s, int nc) {
		int n = 0;
		for (int c = 0; c < nc; c++) {
			if (reaches(s[0], s[1], s[2], cand[c])) {
				n++;
			}
		}
		return n;
	}

	private boolean takenByOther(int f, int[] s) {
		for (int o = 0; o < head.length; o++) {
			if (o != f && px[o] == s[0] && pz[o] == s[2] && Math.abs(py[o] - s[1]) < 2) {
				return true;
			}
		}
		return false;
	}

	private boolean ready(int f) {
		int i = target[f];
		return !frontBlocked[f] && i >= 0 && walkLeft[f] == 0 && segmentOf(i) == segment;
	}

	private boolean depsDone(int index) {
		for (int d : schedule.deps(index)) {
			if (!applied[d]) {
				return false;
			}
		}
		return true;
	}

	/** Some front is blocked and no other front has a target to go on with. */
	private boolean allBlocked() {
		boolean any = false;
		for (int f = 0; f < head.length; f++) {
			if (frontBlocked[f]) {
				any = true;
			} else if (target[f] >= 0) {
				return false;
			}
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

	/** Ticks builder {@code f} still walks before its next step (0 = there, -1 = no target). */
	public int walking(int f) {
		return walkLeft[f];
	}

	/** Plan entry builder {@code f} is going for, or -1. */
	public int target(int f) {
		return target[f];
	}

	@Override
	public int frontCount() {
		return head.length;
	}

	/** The builder that claimed (and places or placed) {@code index}; the planned owner while nobody did. */
	@Override
	public int frontOf(int index) {
		int b = by[index];
		return b >= 0 ? b : schedule.owner(index);
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
