package dev.t3craft.client.npc.behavior;

import dev.t3craft.debug.PerfStats;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The NPCs' way around the level: A* ({@link GridPathfinder}) over real collision shapes
 * ({@link Collision}), shared by all NPCs and kept across ticks by the {@link BehaviorDirector}.
 *
 * <ul>
 *   <li>Node budget: at most {@link #TICK_BUDGET} A* nodes per tick for everyone; a search that
 *       does not finish answers {@link Route.Status#PENDING} and resumes next tick.
 *   <li>Time budget: besides nodes, at most {@link #TIME_BUDGET_NS} of searching per tick once
 *       {@link #MIN_NODES_BEFORE_TIME} nodes were expanded (collision checks vary a lot in cost; the
 *       client tick runs on the render thread, so a slow tick is a dropped frame).
 *   <li>Route cache: found routes are kept (as cells) for {@link #CACHE_TTL} ticks and re-checked
 *       against the current blocks on every hit; misses are remembered with an exponential backoff
 *       ({@link #MISS_TTL} doubling per consecutive miss up to {@link #MISS_TTL_MAX}), so an
 *       unreachable spot (e.g. a desk inside a closed HQ) does not re-run a full
 *       {@link GridPathfinder#MAX_NODES} search for every NPC every two seconds.
 *   <li>Blocked spots: when a walker makes no progress, {@link #blocked} marks the cell it is
 *       pushing into for {@link #BLOCK_TTL} ticks and drops cached routes, so the re-plan goes
 *       around it.
 *   <li>Via point: when there is no direct route (too far for one bounded search, or the search box
 *       misses the way in), the route is tried in two legs through {@link #setVia} (the base's
 *       entrance): outside → entrance → desk.
 *   <li>{@link #invalidate}: the base was built, moved or removed; forget every route, miss and
 *       blocked spot.
 *   <li>Why there is no route is logged at debug level, once per goal every {@link #LOG_EVERY} ticks.
 * </ul>
 * Block reads are cached for one tick only ({@link #beginTick}), so doors and builds are seen.
 */
public final class Navigation implements NpcBrain.Navigator {
	static final int TICK_BUDGET = 2_500;
	static final int SLICE = 1_500;
	static final int CACHE_TTL = 100;
	static final int MISS_TTL = 40;
	/** Longest wait before searching an unreachable route again (60 s). */
	static final int MISS_TTL_MAX = 1_200;
	/** A miss streak is forgotten this long after its last retry window ended. */
	static final int MISS_FORGET = 2_400;
	/** Wall-clock cap on searching per tick (after {@link #MIN_NODES_BEFORE_TIME} nodes). */
	static final long TIME_BUDGET_NS = 2_000_000L;
	static final int MIN_NODES_BEFORE_TIME = 64;
	/** Nodes run between clock checks. */
	static final int CHUNK = 64;
	static final int BLOCK_TTL = 200;
	static final int PENDING_TTL = 20;
	static final int LOG_EVERY = 600;
	private static final int CACHE_MAX = 256;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("t3craft/npc-nav");

	private record Key(long start, long goal, long avoid) {
	}

	private record Cached(List<GridPathfinder.Cell> cells, int tick) {
	}

	private record Pending(GridPathfinder.Search search, int touched) {
	}

	/** A remembered miss: answered NONE until {@code until}; {@code streak} consecutive misses. */
	private record Miss(int until, int streak) {
	}

	private final Walkable ground;
	private final Collider collider;
	private final @Nullable Collision collision;
	private final int tickBudget;
	private final GridPathfinder finder;
	private final Map<Key, Cached> cache = new HashMap<>();
	private final Map<Key, Miss> misses = new HashMap<>();
	private final Map<Key, Pending> pending = new HashMap<>();
	private final Long2IntOpenHashMap blockedUntil = new Long2IntOpenHashMap();
	private final Map<Long, Integer> loggedAt = new HashMap<>();
	private @Nullable Pos via;
	private final long timeBudgetNs;
	private final LongSupplier clock;
	private int tick;
	private int budget;
	private long spentNs;
	private int nodesThisTick;
	/** Work counters (tests, {@code /t3dev perf} has the global ones). */
	private long searches;
	private long nodes;

	private Navigation(Walkable ground, Collider collider, @Nullable Collision collision, int tickBudget, long timeBudgetNs, LongSupplier clock) {
		this.ground = ground;
		this.collider = collider;
		this.collision = collision;
		this.tickBudget = tickBudget;
		this.budget = tickBudget;
		this.timeBudgetNs = timeBudgetNs;
		this.clock = clock;
		this.finder = new GridPathfinder(ground, k -> {
			return blockedUntil.get(k) > tick; // 0 when absent
		});
	}

	/** Over real block shapes, node budget only (tests; the live level uses {@link #forLevel}). */
	public static Navigation over(BlockShapes blocks) {
		return over(blocks, TICK_BUDGET);
	}

	public static Navigation over(BlockShapes blocks, int tickBudget) {
		return over(blocks, tickBudget, Long.MAX_VALUE, System::nanoTime);
	}

	/** The live level: node budget plus the wall-clock cap ({@link #TIME_BUDGET_NS}). */
	public static Navigation forLevel(BlockShapes blocks) {
		return over(blocks, TICK_BUDGET, TIME_BUDGET_NS, System::nanoTime);
	}

	/** With an explicit time budget and clock (tests: a fake clock makes the time cap deterministic). */
	public static Navigation over(BlockShapes blocks, int tickBudget, long timeBudgetNs, LongSupplier clock) {
		Collision c = new Collision(blocks);
		return new Navigation(c, c, c, tickBudget, timeBudgetNs, clock);
	}

	/** Over a plain walkable grid, no collision (tests). */
	public static Navigation over(Walkable ground) {
		return new Navigation(ground, Collider.NONE, null, Integer.MAX_VALUE / 2, Long.MAX_VALUE, System::nanoTime);
	}

	public GridPathfinder finder() {
		return finder;
	}

	public int tick() {
		return tick;
	}

	/** Nodes left for this tick. */
	public int budgetLeft() {
		return budget;
	}

	/** Where long routes may go through (the base's entrance), null = none. */
	public void setVia(@Nullable Pos via) {
		this.via = via;
	}

	/** The world the routes were found in changed (base built, moved, removed): forget everything learned. */
	public void invalidate() {
		cache.clear();
		misses.clear();
		pending.clear();
		blockedUntil.clear();
		loggedAt.clear();
		finder.clearCache();
		if (collision != null) {
			collision.clear();
		}
	}

	/** Start of a client tick: refresh the budget, forget block reads, expire old entries. */
	public void beginTick() {
		tick++;
		budget = tickBudget;
		spentNs = 0;
		nodesThisTick = 0;
		finder.clearCache();
		if (collision != null) {
			collision.clear();
		}
		cache.values().removeIf(c -> tick - c.tick > CACHE_TTL);
		misses.values().removeIf(m -> tick - m.until() > MISS_FORGET);
		pending.values().removeIf(p -> tick - p.touched > PENDING_TTL);
		blockedUntil.long2IntEntrySet().removeIf(e -> e.getIntValue() <= tick);
		loggedAt.values().removeIf(t -> tick - t > LOG_EVERY);
	}

	@Override
	public @Nullable List<Pos> path(Pos from, Pos to, @Nullable Pos avoid) {
		Route r = route(from, to, avoid);
		return r.status() == Route.Status.FOUND ? r.points() : null;
	}

	@Override
	public Route route(Pos from, Pos to, @Nullable Pos avoid) {
		Route direct = direct(from, to, avoid);
		Pos v = via;
		if (direct.status() != Route.Status.NONE || v == null) {
			return direct;
		}
		GridPathfinder.Cell vc = finder.cellAt(v);
		GridPathfinder.Cell start = finder.cellAt(from);
		GridPathfinder.Cell goal = finder.cellAt(to);
		if (vc == null || vc.equals(start) || vc.equals(goal)) {
			return direct;
		}
		Route in = direct(from, v, avoid);
		if (in.status() != Route.Status.FOUND) {
			return in;
		}
		// The second leg starts where the first one ends (next to the via when the via is inside a block).
		Pos joint = in.points().getLast();
		Route on = direct(joint, to, avoid);
		if (on.status() != Route.Status.FOUND) {
			return on;
		}
		List<Pos> pts = new java.util.ArrayList<>(in.points());
		pts.addAll(on.points().subList(1, on.points().size()));
		return Route.found(pts);
	}

	private Route direct(Pos from, Pos to, @Nullable Pos avoid) {
		GridPathfinder.Cell start = finder.cellAt(from);
		GridPathfinder.Cell goal = finder.cellAt(to);
		if (start == null || goal == null) {
			logNone(goal != null ? goal.key() : Long.MIN_VALUE, from, to,
					start == null ? "no walkable cell near the NPC" : "no walkable cell near the target");
			return Route.NONE;
		}
		long avoidKey = avoid == null ? Long.MIN_VALUE : GridPathfinder.pack(avoid.blockX(), avoid.blockY(), avoid.blockZ());
		Key key = new Key(start.key(), goal.key(), avoidKey);
		Cached hit = cache.get(key);
		if (hit != null) {
			if (finder.stillWalkable(hit.cells)) {
				PerfStats.count(PerfStats.NAV_CACHE_HITS);
				return Route.found(finder.smooth(from, to, hit.cells, avoid));
			}
			cache.remove(key);
		}
		Miss miss = misses.get(key);
		if (miss != null && tick < miss.until()) {
			PerfStats.count(PerfStats.NAV_MISS_HITS);
			return Route.NONE;
		}
		Pending p = pending.get(key);
		GridPathfinder.Search s;
		if (p != null) {
			s = p.search;
		} else {
			s = finder.search(from, to, avoid);
			searches++;
			PerfStats.count(PerfStats.NAV_SEARCHES);
		}
		if (s.status() == GridPathfinder.Status.RUNNING) {
			if (exhausted()) {
				pending.put(key, new Pending(s, tick));
				PerfStats.count(PerfStats.NAV_DEFERRED);
				return Route.PENDING;
			}
			run(s);
		}
		switch (s.status()) {
			case FOUND -> {
				pending.remove(key);
				misses.remove(key);
				if (cache.size() >= CACHE_MAX) {
					Iterator<Key> it = cache.keySet().iterator();
					it.next();
					it.remove();
				}
				cache.put(key, new Cached(s.cells(), tick));
				return Route.found(finder.smooth(from, to, s.cells(), avoid));
			}
			case NONE -> {
				pending.remove(key);
				int streak = miss == null ? 1 : miss.streak() + 1;
				misses.put(key, new Miss(tick + missTtl(streak), streak));
				PerfStats.count(PerfStats.NAV_FAILED);
				logNone(goal.key(), from, to, s.reason());
				return Route.NONE;
			}
			default -> {
				pending.put(key, new Pending(s, tick));
				return Route.PENDING;
			}
		}
	}

	/** No nodes left, or over the time cap once the minimum progress was made. */
	private boolean exhausted() {
		return budget <= 0 || (nodesThisTick >= MIN_NODES_BEFORE_TIME && spentNs >= timeBudgetNs);
	}

	/** One slice of {@code s} (at most {@link #SLICE} nodes), in chunks so the time cap is honoured. */
	private void run(GridPathfinder.Search s) {
		int left = Math.min(budget, SLICE);
		long t0 = clock.getAsLong();
		int used = 0;
		while (left > 0 && s.status() == GridPathfinder.Status.RUNNING) {
			int n = s.run(Math.min(left, CHUNK));
			used += n;
			left -= n;
			budget -= n;
			nodesThisTick += n;
			long now = clock.getAsLong();
			spentNs += now - t0;
			t0 = now;
			if (n == 0 || exhausted()) {
				break;
			}
		}
		nodes += used;
		PerfStats.count(PerfStats.NAV_NODES, used);
	}

	/** Searches started since creation. */
	long searches() {
		return searches;
	}

	/** A* nodes expanded since creation. */
	long nodes() {
		return nodes;
	}

	/** Backoff after {@code streak} consecutive misses of one route: 40, 80, 160 … ticks, at most {@link #MISS_TTL_MAX}. */
	static int missTtl(int streak) {
		int shift = Math.min(Math.max(streak - 1, 0), 10);
		return (int) Math.min((long) MISS_TTL << shift, MISS_TTL_MAX);
	}

	/** Time spent searching this tick (tests, stats). */
	long spentThisTick() {
		return spentNs;
	}

	@Override
	public Pos move(Pos from, Pos to) {
		return collider.move(from, to);
	}

	@Override
	public boolean embedded(Pos p) {
		return collider.embedded(p);
	}

	@Override
	public @Nullable Pos nearestFree(Pos p) {
		return collider.nearestFree(p);
	}

	/**
	 * A walker at {@code at} heading for {@code toward} made no progress: block the cell it is
	 * pushing into (unless it is the cell it stands in or the final goal) and forget cached routes.
	 */
	@Override
	public void blocked(Pos at, Pos toward, Pos goal) {
		double dx = toward.x() - at.x();
		double dz = toward.z() - at.z();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len > 1e-4) {
			int bx = (int) Math.floor(at.x() + dx / len * 0.8);
			int bz = (int) Math.floor(at.z() + dz / len * 0.8);
			boolean own = bx == at.blockX() && bz == at.blockZ();
			boolean isGoal = bx == goal.blockX() && bz == goal.blockZ();
			if (!own && !isGoal) {
				for (int dy = -1; dy <= 1; dy++) {
					blockedUntil.put(GridPathfinder.pack(bx, at.blockY() + dy, bz), tick + BLOCK_TTL);
				}
			}
		}
		cache.clear();
		// Blocks changed under someone's feet: every miss may be reachable again, so retry now, but
		// keep the streaks (an unreachable spot that is still unreachable keeps backing off).
		misses.replaceAll((k, m) -> new Miss(tick, m.streak()));
		pending.clear();
	}

	@Override
	public void forgetBlocked() {
		// fix/hq-fps: only when walkers had marked spots (the likely reason for the misses), and
		// keep the streaks: an NPC that has had no route for long must not reset every other NPC's
		// backoff, or N unreachable desks go back to N full searches every few seconds.
		if (blockedUntil.isEmpty()) {
			return;
		}
		blockedUntil.clear();
		misses.replaceAll((k, m) -> new Miss(Math.min(m.until(), tick), m.streak()));
	}

	/** Debug log of a missing route, at most once per goal every {@link #LOG_EVERY} ticks. */
	private void logNone(long goalKey, Pos from, Pos to, String why) {
		if (!LOG.isDebugEnabled() || loggedAt.putIfAbsent(goalKey, tick) != null) {
			return;
		}
		LOG.debug("[npc-nav] no route {} -> {}: {}", from, to, why);
	}

	/** Is the cell temporarily blocked (tests)? */
	boolean isBlocked(int x, int y, int z) {
		return blockedUntil.get(GridPathfinder.pack(x, y, z)) > tick;
	}

	Walkable ground() {
		return ground;
	}
}
