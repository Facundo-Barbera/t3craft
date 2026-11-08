package dev.t3craft.client.npc.behavior;

import dev.t3craft.build.Offload;
import dev.t3craft.debug.PerfStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

/**
 * feat/npc-idle-wander: the strollers' routes, off the client frame, the same way the builders get
 * theirs ({@code dev.t3craft.build.client.CrewRoutes}): a stroller's {@link #route} answers from the
 * cache or a finished search, else queues the request and answers {@link Route.Status#PENDING} (the
 * brain asks again next tick and keeps doing what it does). Once per client tick ({@link #tick}) at
 * most {@code routesPerTick} requests (and {@link #CELLS_PER_TICK} cells) get the collision of their
 * stretch copied into a {@link SnapshotBlocks} on the client thread, and the bounded A* runs on an
 * {@link Offload} worker over that copy (the level is never read off the client thread). Found routes
 * and misses are kept {@link #CACHE_TICKS} ticks per start and goal cell.
 *
 * <p>The builders keep their own {@code CrewRoutes} (untouched); both share {@link SnapshotBlocks},
 * {@link Collision} and the per-finder limits of {@link GridPathfinder}. Client thread only, except
 * the worker job.
 */
final class WanderRoutes {
	/** Search box around both ends (blocks); also the snapshot's horizontal margin (the box never grows past it). */
	static final int PAD = 6;
	static final int BELOW = 2;
	static final int ABOVE = 3;
	/** Expanded nodes per search; past that the spot counts as unreachable. */
	static final int MAX_NODES = 3_000;
	/** Snapshot volume above which no search is tried (no route: the stroller picks another spot). */
	static final long MAX_CELLS = 12_000;
	static final long CELLS_PER_TICK = 12_000;
	/** Searches on the workers at once (the {@link Offload} pool is shared with the HQ jobs). */
	static final int MAX_IN_FLIGHT = 2;
	static final int CACHE_TICKS = 600;
	static final int CACHE_SIZE = 128;
	/** A finished result nobody asked for in this long is dropped. */
	static final int READY_TTL = 60;
	/** A result is used when the stroller stands at most this far (blocks) from where its search started. */
	static final double FROM_SLACK = 1.5;

	static final String SEARCHES = "npc.wander.searches";
	static final String NODES = "npc.wander.nodes";
	static final String FAILED = "npc.wander.failed";
	static final String SKIPPED = "npc.wander.skipped";
	static final String CACHE_HITS = "npc.wander.cache_hits";

	private record Request(String id, long seq, Pos from, Pos to) {
	}

	private record Result(long gen, Request req, @Nullable List<Pos> path, long tick) {
	}

	private record Key(long from, long to) {
	}

	/** A found route ({@code path} first = the search's start) or a miss ({@code path} null). */
	private record Cached(@Nullable List<Pos> path, long tick) {
	}

	private final LinkedHashMap<String, Request> pending = new LinkedHashMap<>();
	private final Map<String, Request> running = new HashMap<>();
	private final Map<String, Result> ready = new HashMap<>();
	private final Queue<Result> done = new ConcurrentLinkedQueue<>();
	private final LinkedHashMap<Key, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Cached> eldest) {
			return size() > CACHE_SIZE;
		}
	};
	private @Nullable BlockGetter level;
	private long tick;
	private long seqs;
	private long gen;

	/** Route for stroller {@code id}: FOUND / NONE when known, else PENDING (queued; ask again next tick). */
	Route route(String id, Pos from, Pos to) {
		Key key = new Key(cell(from), cell(to));
		Cached c = cache.get(key);
		if (c != null) {
			if (tick - c.tick <= CACHE_TICKS) {
				PerfStats.get().add(CACHE_HITS, 1);
				return c.path == null ? Route.NONE : Route.found(fromHere(from, c.path));
			}
			cache.remove(key);
		}
		Result r = ready.get(id);
		if (r != null && cell(r.req.to) == key.to) {
			ready.remove(id);
			if (r.req.from.horizDist(from) <= FROM_SLACK && Math.abs(r.req.from.y() - from.y()) < 1.1) {
				return r.path == null ? Route.NONE : Route.found(fromHere(from, r.path));
			}
		}
		Request q = pending.get(id);
		if (q == null) {
			q = running.get(id);
		}
		if (q != null && cell(q.to) == key.to) {
			return Route.PENDING;
		}
		pending.put(id, new Request(id, ++seqs, from, to));
		return Route.PENDING;
	}

	/** The stroller is gone or no longer strolls: drop what it waits for. */
	void forget(String id) {
		pending.remove(id);
		running.remove(id);
		ready.remove(id);
	}

	/** The world changed (base built, moved): forget cached routes and misses. */
	void invalidate() {
		cache.clear();
	}

	/** Forget everything (level change, reset); results still on a worker are ignored. */
	void clear() {
		pending.clear();
		running.clear();
		ready.clear();
		done.clear();
		cache.clear();
		gen++;
	}

	/** Client thread, once per tick: take finished searches in, then start at most {@code starts} new ones. */
	void tick(BlockGetter lvl, int starts) {
		tick++;
		if (lvl != level) {
			clear();
			level = lvl;
		}
		for (Result r; (r = done.poll()) != null; ) {
			if (r.gen != gen) {
				continue;
			}
			Request now = running.get(r.req.id);
			if (now != null && now.seq == r.req.seq) {
				running.remove(r.req.id);
			}
			cache.put(new Key(cell(r.req.from), cell(r.req.to)), new Cached(r.path, tick));
			ready.put(r.req.id, new Result(r.gen, r.req, r.path, tick));
		}
		if (!ready.isEmpty()) {
			ready.values().removeIf(r -> tick - r.tick > READY_TTL);
		}
		int started = 0;
		long cells = 0;
		Iterator<Request> it = pending.values().iterator();
		while (it.hasNext() && started < starts && running.size() < MAX_IN_FLIGHT) {
			Request req = it.next();
			Pos a = req.from;
			Pos b = req.to;
			int x0 = (int) Math.floor(Math.min(a.x(), b.x())) - PAD;
			int x1 = (int) Math.floor(Math.max(a.x(), b.x())) + PAD;
			int z0 = (int) Math.floor(Math.min(a.z(), b.z())) - PAD;
			int z1 = (int) Math.floor(Math.max(a.z(), b.z())) + PAD;
			int y0 = Math.min(a.blockY(), b.blockY()) - BELOW;
			int y1 = Math.max(a.blockY(), b.blockY()) + ABOVE;
			long vol = SnapshotBlocks.volume(x0, y0, z0, x1, y1, z1);
			it.remove();
			if (vol > MAX_CELLS) {
				PerfStats.get().add(SKIPPED, 1);
				Result miss = new Result(gen, req, null, tick);
				cache.put(new Key(cell(a), cell(b)), new Cached(null, tick));
				ready.put(req.id, miss);
				continue;
			}
			if (started > 0 && cells + vol > CELLS_PER_TICK) {
				pending.putFirst(req.id, req); // next tick
				break;
			}
			started++;
			cells += vol;
			SnapshotBlocks snap = SnapshotBlocks.capture(lvl, x0, y0, z0, x1, y1, z1);
			long g = gen;
			running.put(req.id, req);
			try {
				Offload.supply(PerfStats.STEP_NPC_WANDER_ASYNC, () -> search(g, req, snap))
						.whenComplete((res, ex) -> done.add(res != null ? res : new Result(g, req, null, 0)));
			} catch (RuntimeException e) {
				running.remove(req.id);
				ready.put(req.id, new Result(g, req, null, tick));
			}
		}
	}

	/** Worker thread: A* over the snapshot only. */
	private static Result search(long gen, Request req, SnapshotBlocks snap) {
		List<Pos> path = null;
		try {
			GridPathfinder pf = new GridPathfinder(new Collision(snap), k -> false, PAD, PAD, MAX_NODES);
			GridPathfinder.Search s = pf.search(req.from, req.to, null);
			s.run(MAX_NODES);
			PerfStats.get().add(SEARCHES, 1);
			PerfStats.get().add(NODES, s.expanded());
			if (s.status() == GridPathfinder.Status.FOUND) {
				List<Pos> p = s.path();
				path = p == null || p.size() < 2 ? null : List.copyOf(p);
			}
		} catch (RuntimeException e) {
			path = null;
		}
		if (path == null) {
			PerfStats.get().add(FAILED, 1);
		}
		return new Result(gen, req, path, 0);
	}

	/** The route with its first point (the search's start) replaced by where the stroller stands now. */
	private static List<Pos> fromHere(Pos from, List<Pos> path) {
		List<Pos> out = new ArrayList<>(path);
		out.set(0, from);
		return out;
	}

	private static long cell(Pos p) {
		return GridPathfinder.pack(p.blockX(), p.blockY(), p.blockZ());
	}
}
