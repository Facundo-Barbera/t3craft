package dev.t3craft.client.npc.behavior;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The NPCs' way around the level: A* ({@link GridPathfinder}) over real collision shapes
 * ({@link Collision}), shared by all NPCs and kept across ticks by the {@link BehaviorDirector}.
 *
 * <ul>
 *   <li>Node budget: at most {@link #TICK_BUDGET} A* nodes per tick for everyone; a search that
 *       does not finish answers {@link Route.Status#PENDING} and resumes next tick.
 *   <li>Route cache: found routes are kept (as cells) for {@link #CACHE_TTL} ticks and re-checked
 *       against the current blocks on every hit; misses are remembered for {@link #MISS_TTL}.
 *   <li>Blocked spots: when a walker makes no progress, {@link #blocked} marks the cell it is
 *       pushing into for {@link #BLOCK_TTL} ticks and drops cached routes, so the re-plan goes
 *       around it.
 * </ul>
 * Block reads are cached for one tick only ({@link #beginTick}), so doors and builds are seen.
 */
public final class Navigation implements NpcBrain.Navigator {
	static final int TICK_BUDGET = 2_500;
	static final int SLICE = 1_500;
	static final int CACHE_TTL = 100;
	static final int MISS_TTL = 40;
	static final int BLOCK_TTL = 200;
	static final int PENDING_TTL = 20;
	private static final int CACHE_MAX = 256;

	private record Key(long start, long goal, long avoid) {
	}

	private record Cached(List<GridPathfinder.Cell> cells, int tick) {
	}

	private record Pending(GridPathfinder.Search search, int touched) {
	}

	private final Walkable ground;
	private final Collider collider;
	private final @Nullable Collision collision;
	private final int tickBudget;
	private final GridPathfinder finder;
	private final Map<Key, Cached> cache = new HashMap<>();
	private final Map<Key, Integer> misses = new HashMap<>();
	private final Map<Key, Pending> pending = new HashMap<>();
	private final Map<Long, Integer> blockedUntil = new HashMap<>();
	private int tick;
	private int budget;

	private Navigation(Walkable ground, Collider collider, @Nullable Collision collision, int tickBudget) {
		this.ground = ground;
		this.collider = collider;
		this.collision = collision;
		this.tickBudget = tickBudget;
		this.budget = tickBudget;
		this.finder = new GridPathfinder(ground, k -> {
			Integer until = blockedUntil.get(k);
			return until != null && until > tick;
		});
	}

	/** Over real block shapes (the client level, or a fake cell world in tests). */
	public static Navigation over(BlockShapes blocks) {
		return over(blocks, TICK_BUDGET);
	}

	public static Navigation over(BlockShapes blocks, int tickBudget) {
		Collision c = new Collision(blocks);
		return new Navigation(c, c, c, tickBudget);
	}

	/** Over a plain walkable grid, no collision (tests). */
	public static Navigation over(Walkable ground) {
		return new Navigation(ground, Collider.NONE, null, Integer.MAX_VALUE / 2);
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

	/** Start of a client tick: refresh the budget, forget block reads, expire old entries. */
	public void beginTick() {
		tick++;
		budget = tickBudget;
		finder.clearCache();
		if (collision != null) {
			collision.clear();
		}
		cache.values().removeIf(c -> tick - c.tick > CACHE_TTL);
		misses.values().removeIf(t -> tick - t > MISS_TTL);
		pending.values().removeIf(p -> tick - p.touched > PENDING_TTL);
		blockedUntil.values().removeIf(t -> t <= tick);
	}

	@Override
	public @Nullable List<Pos> path(Pos from, Pos to, @Nullable Pos avoid) {
		Route r = route(from, to, avoid);
		return r.status() == Route.Status.FOUND ? r.points() : null;
	}

	@Override
	public Route route(Pos from, Pos to, @Nullable Pos avoid) {
		GridPathfinder.Cell start = finder.cellAt(from);
		GridPathfinder.Cell goal = finder.cellAt(to);
		if (start == null || goal == null) {
			return Route.NONE;
		}
		long avoidKey = avoid == null ? Long.MIN_VALUE : GridPathfinder.pack(avoid.blockX(), avoid.blockY(), avoid.blockZ());
		Key key = new Key(start.key(), goal.key(), avoidKey);
		Cached hit = cache.get(key);
		if (hit != null) {
			if (finder.stillWalkable(hit.cells)) {
				return Route.found(finder.smooth(from, to, hit.cells, avoid));
			}
			cache.remove(key);
		}
		if (misses.containsKey(key)) {
			return Route.NONE;
		}
		Pending p = pending.get(key);
		GridPathfinder.Search s = p != null ? p.search : finder.search(from, to, avoid);
		if (s.status() == GridPathfinder.Status.RUNNING) {
			if (budget <= 0) {
				pending.put(key, new Pending(s, tick));
				return Route.PENDING;
			}
			budget -= s.run(Math.min(budget, SLICE));
		}
		switch (s.status()) {
			case FOUND -> {
				pending.remove(key);
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
				misses.put(key, tick);
				return Route.NONE;
			}
			default -> {
				pending.put(key, new Pending(s, tick));
				return Route.PENDING;
			}
		}
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
		misses.clear();
		pending.clear();
	}

	/** Is the cell temporarily blocked (tests)? */
	boolean isBlocked(int x, int y, int z) {
		Integer until = blockedUntil.get(GridPathfinder.pack(x, y, z));
		return until != null && until > tick;
	}

	Walkable ground() {
		return ground;
	}
}
