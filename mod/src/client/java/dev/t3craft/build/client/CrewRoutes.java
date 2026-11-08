package dev.t3craft.build.client;

import dev.t3craft.client.npc.behavior.Collision;
import dev.t3craft.client.npc.behavior.GridPathfinder;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.client.npc.behavior.SnapshotBlocks;
import dev.t3craft.debug.PerfStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * fix/crew-route-stutter: the builders' walk routes, off the client frame. A {@link BuildCrew} asks
 * for a route ({@link #request}); once per client tick ({@link #tick}) at most
 * {@link #CAPTURES_PER_TICK} requests (and {@link #CELLS_PER_TICK} cells) get the collision of their
 * stretch copied into a {@link SnapshotBlocks} on the client thread, and the A* runs on a background
 * worker over that copy (the client level is never read off the client thread). Results come back
 * on a later tick and are handed to the crew. A newer request for the same builder replaces the
 * pending one, and a stale result is dropped. Routes found are reused for {@link #CACHE_TICKS} ticks
 * for the same start and goal cells.
 *
 * <p>Animation only: the server's build logic ({@code CrewCursor}) does not wait for any of this.
 * Client thread only, except the worker job.
 */
final class CrewRoutes {
	/** Search box around both ends (blocks); grows ×3 up to {@link #SNAPSHOT_PAD}. */
	static final int SEARCH_PAD = 3;
	/** Horizontal margin of the snapshot around both ends (blocks): the most the search box may grow to. */
	static final int SNAPSHOT_PAD = 6;
	/** Blocks of snapshot below the lower end and above the higher end. */
	static final int SNAPSHOT_BELOW = 2;
	static final int SNAPSHOT_ABOVE = 3;
	/** Expanded nodes per search; past that the builder walks straight. */
	static final int MAX_NODES = 1_500;
	/** Snapshot volume above which no search is tried (straight line). */
	static final long MAX_CELLS = 9_000;
	/** Per-tick budget for the whole client (every crew): snapshots taken and cells copied. */
	static final int CAPTURES_PER_TICK = 3;
	static final long CELLS_PER_TICK = 9_000;
	/** Searches queued or running on the worker at most; the rest wait their turn. */
	static final int MAX_IN_FLIGHT = 6;
	static final int CACHE_TICKS = 20;
	static final int CACHE_SIZE = 64;

	static final String SEARCHES = "crew.route.searches";
	static final String NODES = "crew.route.nodes";
	static final String FAILED = "crew.route.failed";
	static final String SKIPPED = "crew.route.skipped";
	static final String CACHE_HITS = "crew.route.cache_hits";
	static final String REPLACED = "crew.route.replaced";

	private record Key(BuildCrew crew, int builder) {
	}

	private record Request(long seq, Key key, Vec3 goal, int ticks, long tick) {
	}

	private record Result(long gen, Request req, Vec3 from, List<Vec3> route, boolean found, ClientLevel level) {
	}

	private record RouteKey(ClientLevel level, long from, long goal) {
	}

	private record Cached(List<Vec3> route, long tick) {
	}

	private final LinkedHashMap<Key, Request> pending = new LinkedHashMap<>();
	private final Map<Key, Long> latest = new HashMap<>();
	private final Queue<Result> done = new ConcurrentLinkedQueue<>();
	private final LinkedHashMap<RouteKey, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<RouteKey, Cached> eldest) {
			return size() > CACHE_SIZE;
		}
	};
	private @Nullable ExecutorService worker;
	private long tick;
	private long seqs;
	private long gen;
	private int inFlight;

	/** Builder {@code builder} of {@code crew} wants a route to {@code goal}, to arrive in {@code ticks}. Replaces its pending one. */
	void request(BuildCrew crew, int builder, Vec3 goal, int ticks) {
		Key key = new Key(crew, builder);
		long seq = ++seqs;
		latest.put(key, seq);
		if (pending.put(key, new Request(seq, key, goal, ticks, tick)) != null) {
			PerfStats.get().add(REPLACED, 1);
		}
	}

	/** Drops whatever builder {@code builder} of {@code crew} still waits for (it got a new order that needs no route). */
	void cancel(BuildCrew crew, int builder) {
		Key key = new Key(crew, builder);
		if (pending.remove(key) != null || latest.containsKey(key)) {
			latest.put(key, ++seqs);
		}
	}

	/** A route found a few ticks ago for the same start and goal cells, or null. */
	@Nullable List<Vec3> cached(ClientLevel level, Vec3 from, Vec3 goal) {
		RouteKey k = new RouteKey(level, cell(from), cell(goal));
		Cached c = cache.get(k);
		if (c == null) {
			return null;
		}
		if (tick - c.tick > CACHE_TICKS) {
			cache.remove(k);
			return null;
		}
		PerfStats.get().add(CACHE_HITS, 1);
		return c.route;
	}

	/** Drops the pending orders of a crew that stopped building. */
	void forget(BuildCrew crew) {
		pending.keySet().removeIf(k -> k.crew == crew);
		latest.keySet().removeIf(k -> k.crew == crew);
	}

	/** Forget everything (level change, shutdown); results still on the worker are ignored. */
	void clear() {
		pending.clear();
		latest.clear();
		cache.clear();
		done.clear();
		inFlight = 0;
		gen++;
	}

	/** Once per client tick: hand back finished routes, then start the next searches within the budget. */
	void tick() {
		tick++;
		if (done.isEmpty() && pending.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		for (Result r; (r = done.poll()) != null; ) {
			if (r.gen != gen) {
				continue;
			}
			inFlight--;
			if (r.found) {
				cache.put(new RouteKey(r.level, cell(r.from), cell(r.req.goal)), new Cached(r.route, tick));
			}
			deliver(r.req, r.route);
		}
		int captures = 0;
		long cells = 0;
		Iterator<Request> it = pending.values().iterator();
		while (it.hasNext() && captures < CAPTURES_PER_TICK && inFlight < MAX_IN_FLIGHT) {
			Request req = it.next();
			BuildCrew crew = req.key.crew;
			Vec3 from = crew.routeStart(req.key.builder);
			if (from == null) {
				it.remove(); // the crew left or the builder is gone
				continue;
			}
			Vec3 goal = req.goal;
			int x0 = Mth.floor(Math.min(from.x, goal.x)) - SNAPSHOT_PAD;
			int x1 = Mth.floor(Math.max(from.x, goal.x)) + SNAPSHOT_PAD;
			int z0 = Mth.floor(Math.min(from.z, goal.z)) - SNAPSHOT_PAD;
			int z1 = Mth.floor(Math.max(from.z, goal.z)) + SNAPSHOT_PAD;
			int y0 = Mth.floor(Math.min(from.y, goal.y)) - SNAPSHOT_BELOW;
			int y1 = Mth.floor(Math.max(from.y, goal.y)) + SNAPSHOT_ABOVE;
			long vol = SnapshotBlocks.volume(x0, y0, z0, x1, y1, z1);
			if (vol > MAX_CELLS) {
				it.remove();
				PerfStats.get().add(SKIPPED, 1);
				deliver(req, List.of(goal)); // too big a stretch for a cheap search: straight line
				continue;
			}
			if (captures > 0 && cells + vol > CELLS_PER_TICK) {
				break;
			}
			it.remove();
			captures++;
			cells += vol;
			ClientLevel level = crew.level();
			SnapshotBlocks snap = SnapshotBlocks.capture(level, x0, y0, z0, x1, y1, z1);
			long g = gen;
			inFlight++;
			try {
				worker().execute(() -> done.add(search(g, req, from, snap, level)));
			} catch (RejectedExecutionException e) {
				inFlight--;
				deliver(req, List.of(goal));
			}
		}
		PerfStats.step(PerfStats.STEP_CREW_ROUTE, t0);
	}

	/** Worker thread: A* over the snapshot only. */
	private static Result search(long gen, Request req, Vec3 from, SnapshotBlocks snap, ClientLevel level) {
		long t0 = System.nanoTime();
		Vec3 goal = req.goal;
		List<Vec3> out = new ArrayList<>();
		boolean found = false;
		try {
			GridPathfinder pf = new GridPathfinder(new Collision(snap), k -> false, SEARCH_PAD, SNAPSHOT_PAD, MAX_NODES);
			GridPathfinder.Search s = pf.search(new Pos(from.x, from.y, from.z), new Pos(goal.x, goal.y, goal.z), null);
			s.run(MAX_NODES);
			PerfStats.get().add(SEARCHES, 1);
			PerfStats.get().add(NODES, s.expanded());
			List<Pos> path = s.status() == GridPathfinder.Status.FOUND ? s.path() : null;
			if (path != null) {
				found = true;
				for (int i = 1; i < path.size(); i++) {
					Pos p = path.get(i);
					out.add(new Vec3(p.x(), p.y(), p.z()));
				}
			} else {
				PerfStats.get().add(FAILED, 1);
			}
		} catch (RuntimeException e) {
			out.clear();
			found = false;
		}
		if (out.isEmpty() || out.getLast().distanceToSqr(goal) > 1e-4) {
			out.add(goal); // last stretch onto a scaffold spot, or no route at all: straight
		}
		PerfStats.stepAsync(PerfStats.STEP_CREW_ROUTE_ASYNC, t0);
		return new Result(gen, req, from, List.copyOf(out), found, level);
	}

	private void deliver(Request req, List<Vec3> route) {
		Long last = latest.get(req.key);
		if (last == null || last != req.seq) {
			return; // a newer order came in meanwhile
		}
		latest.remove(req.key);
		int left = Math.max(1, req.ticks - (int) (tick - req.tick));
		req.key.crew.routed(req.key.builder, route, left);
	}

	private ExecutorService worker() {
		ExecutorService w = worker;
		if (w == null) {
			w = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "t3craft-crew-route");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				return t;
			});
			worker = w;
		}
		return w;
	}

	private static long cell(Vec3 p) {
		return net.minecraft.core.BlockPos.asLong(Mth.floor(p.x), Mth.floor(p.y + 0.01), Mth.floor(p.z));
	}
}
