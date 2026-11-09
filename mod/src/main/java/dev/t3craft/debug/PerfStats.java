package dev.t3craft.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Cheap in-game performance counters ({@code /t3dev perf}): how often per second the hot paths do
 * their work (routes searched, A* nodes, monitor glyph rebuilds, BER draws and skips, {@code setText}
 * on display entities, display entities spawned) and how long the client tick of each module takes.
 *
 * <p>Cost: one map lookup and an atomic add per {@link #count} (keys are constants, so no
 * allocation). Totals only grow; {@link #roll} (once a second from the client tick, or any time
 * with a fake clock in tests) turns the totals into per-second rates over the last window.
 * Thread-safe (the integrated server counts too).
 *
 * <p>Key convention: {@code area.what} (e.g. {@code nav.nodes}); timers end in {@code .ns} and are
 * shown as ms per second.
 */
public final class PerfStats {
	private static final PerfStats INSTANCE = new PerfStats(System::nanoTime);

	/** One counter's rate over the last full window. */
	public record Rate(String key, long total, double perSecond) {
		public boolean timer() {
			return key.endsWith(".ns");
		}
	}

	private final LongSupplier clock;
	private final Map<String, AtomicLong> totals = new ConcurrentHashMap<>();
	private volatile Map<String, Long> windowStart = Map.of();
	private volatile List<Rate> rates = List.of();
	private volatile long windowStartNs;
	/** fix/hq-build-stutter: longest single run of each {@link #step} key since the last reset, ns. */
	private final Map<String, AtomicLong> peaks = new ConcurrentHashMap<>();
	/** The last {@link #MAX_STALLS} steps over {@link #STALL_WARN_MS} on a game thread, newest last. */
	private final java.util.ArrayDeque<String> stalls = new java.util.ArrayDeque<>();

	public PerfStats(LongSupplier nanoClock) {
		this.clock = nanoClock;
		this.windowStartNs = nanoClock.getAsLong();
	}

	public static PerfStats get() {
		return INSTANCE;
	}

	/** Shorthand for {@code get().add(key, 1)}. */
	public static void count(String key) {
		INSTANCE.add(key, 1);
	}

	public static void count(String key, long n) {
		INSTANCE.add(key, n);
	}

	/** Adds {@code System.nanoTime() - startNs} to timer {@code key} (must end in {@code .ns}). */
	public static void since(String key, long startNs) {
		INSTANCE.add(key, System.nanoTime() - startNs);
	}

	// ---- steps (fix/hq-build-stutter) ----------------------------------------------------------

	/** A single step on a game thread (server tick or client frame) above this logs a WARN. */
	public static final double STALL_WARN_MS = 20.0;
	private static final long STALL_WARN_NS = (long) (STALL_WARN_MS * 1_000_000);
	/** Counts the steps that went over {@link #STALL_WARN_MS} on a game thread. */
	public static final String STALLS = "step.stalls";
	/** Prefix of the step timers ({@code step.<area>.<what>.ns}). */
	public static final String STEP_PREFIX = "step.";
	static final int MAX_STALLS = 16;

	/**
	 * Ends one step that runs on a <b>game thread</b> (server or client tick): adds its time to timer
	 * {@code key} (must end in {@code .ns}), keeps its peak, and logs a WARN when it took longer than
	 * {@link #STALL_WARN_MS} (a visible hitch). Returns the elapsed nanoseconds. Cost: two nanoTime
	 * calls and two map lookups.
	 */
	public static long step(String key, long startNs) {
		long ns = System.nanoTime() - startNs;
		INSTANCE.add(key, ns);
		INSTANCE.peak(key, ns);
		if (ns > STALL_WARN_NS) {
			INSTANCE.add(STALLS, 1);
			String line = String.format(java.util.Locale.ROOT, "%s %.1f ms on %s", stepName(key), ns / 1e6, Thread.currentThread().getName());
			INSTANCE.stall(line);
			dev.t3craft.T3Craft.LOGGER.warn("[perf] slow step {} (over {} ms: the game hitches)", line, (int) STALL_WARN_MS);
		}
		return ns;
	}

	/** Like {@link #step} for work done <b>off</b> the game threads (background planner): timed, peak kept, no WARN. */
	public static long stepAsync(String key, long startNs) {
		long ns = System.nanoTime() - startNs;
		INSTANCE.add(key, ns);
		INSTANCE.peak(key, ns);
		return ns;
	}

	private void peak(String key, long ns) {
		peaks.computeIfAbsent(key, k -> new AtomicLong()).accumulateAndGet(ns, Math::max);
	}

	private void stall(String line) {
		synchronized (stalls) {
			if (stalls.size() >= MAX_STALLS) {
				stalls.removeFirst();
			}
			stalls.addLast(line);
		}
	}

	/** Peak (ns) of every step timed since the last reset, sorted by key. */
	public java.util.SortedMap<String, Long> peaks() {
		java.util.TreeMap<String, Long> out = new java.util.TreeMap<>();
		peaks.forEach((k, v) -> out.put(k, v.get()));
		return out;
	}

	/** The last slow steps on a game thread ({@code "key 34.2 ms on Server thread"}), oldest first. */
	public List<String> recentStalls() {
		synchronized (stalls) {
			return List.copyOf(stalls);
		}
	}

	/** {@code step.build.job_new.ns} → {@code build.job_new}. */
	public static String stepName(String key) {
		String k = key.startsWith(STEP_PREFIX) ? key.substring(STEP_PREFIX.length()) : key;
		return k.endsWith(".ns") ? k.substring(0, k.length() - 3) : k;
	}

	public void add(String key, long n) {
		if (n != 0) {
			totals.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(n);
		}
	}

	public long total(String key) {
		AtomicLong a = totals.get(key);
		return a == null ? 0 : a.get();
	}

	/**
	 * Closes the window if at least {@code minWindowNs} passed: rates become (total now − total at
	 * window start) / seconds. Returns true when a new window was computed.
	 */
	public boolean roll(long minWindowNs) {
		long now = clock.getAsLong();
		long elapsed = now - windowStartNs;
		if (elapsed < minWindowNs || elapsed <= 0) {
			return false;
		}
		double seconds = elapsed / 1e9;
		Map<String, Long> start = windowStart;
		Map<String, Long> next = new java.util.HashMap<>();
		List<Rate> out = new ArrayList<>();
		for (Map.Entry<String, AtomicLong> e : totals.entrySet()) {
			long t = e.getValue().get();
			next.put(e.getKey(), t);
			out.add(new Rate(e.getKey(), t, (t - start.getOrDefault(e.getKey(), 0L)) / seconds));
		}
		out.sort(Comparator.comparing(Rate::key));
		windowStart = Map.copyOf(next);
		windowStartNs = now;
		rates = List.copyOf(out);
		return true;
	}

	/** Per-second rates over the last full window (sorted by key). */
	public List<Rate> rates() {
		return rates;
	}

	public double rate(String key) {
		for (Rate r : rates) {
			if (r.key().equals(key)) {
				return r.perSecond();
			}
		}
		return 0;
	}

	public void reset() {
		totals.clear();
		peaks.clear();
		synchronized (stalls) {
			stalls.clear();
		}
		windowStart = Map.of();
		rates = List.of();
		windowStartNs = clock.getAsLong();
	}

	/** One line for chat: {@code nav.nodes 1,234/s} or {@code tick.npcs.ns 3.2 ms/s}. */
	public static String format(Rate r) {
		if (r.timer()) {
			return String.format(java.util.Locale.ROOT, "%s %.2f ms/s", r.key().substring(0, r.key().length() - 3), r.perSecond() / 1e6);
		}
		return String.format(java.util.Locale.ROOT, "%s %,.0f/s (total %,d)", r.key(), r.perSecond(), r.total());
	}

	// ---- keys (constants: no string building on hot paths) ------------------------------------

	/** A* searches started (cache misses that had to search). */
	public static final String NAV_SEARCHES = "nav.searches";
	/** A* nodes expanded. */
	public static final String NAV_NODES = "nav.nodes";
	/** Searches that ended without a route. */
	public static final String NAV_FAILED = "nav.failed";
	/** Route asks answered from the found-route cache. */
	public static final String NAV_CACHE_HITS = "nav.cache_hits";
	/** Route asks answered from the remembered misses (backoff), no search. */
	public static final String NAV_MISS_HITS = "nav.miss_hits";
	/** Route asks deferred because the tick's node or time budget was used up. */
	public static final String NAV_DEFERRED = "nav.deferred";
	public static final String NAV_TIME = "nav.ns";

	/** Monitor BER: glyph runs rebuilt (should be ~0/s with steady threads). */
	public static final String MONITOR_GLYPHS = "monitor.glyph_rebuilds";
	/** Monitor BER: monitors in range that passed the culling (drawn this frame). */
	public static final String MONITOR_DRAWN = "monitor.drawn";
	/** Monitor BER: in range but skipped (seen from behind, or behind the camera). */
	public static final String MONITOR_CULLED = "monitor.culled";
	public static final String LAMP_DRAWN = "lamp.drawn";
	public static final String LAMP_CULLED = "lamp.culled";

	/** {@code setText} on any of our display entities. */
	public static final String DISPLAY_SET_TEXT = "display.set_text";
	/** Client-only display / interaction entities spawned. */
	public static final String DISPLAY_SPAWNED = "display.spawned";

	/** Server: lamp sweeps ({@code LampApplier.apply}) and block states written. */
	public static final String LAMP_SWEEPS = "lamp.sweeps";
	public static final String LAMP_WRITES = "lamp.writes";
	public static final String BINDING_WRITES = "binding.writes";

	// fix/hq-build-stutter: start and end of build work (game-thread steps unless marked "async")
	/** Server: a BuildJob created (plan copy, small crew plans planned inline). Client thread when a crew submits. */
	public static final String STEP_JOB_NEW = "step.build.job_new.ns";
	/** Async: crew schedule ({@code CrewSchedule.of}: k-means and simulation). */
	public static final String STEP_SCHEDULE = "step.build.schedule_async.ns";
	/** Async: material costs and meter of a charged job ({@code BlockCosts}, {@code MaterialMeter}). */
	public static final String STEP_METER = "step.build.meter_async.ns";
	/** feat/build-near-player: server, one tick of fast foundation layers ({@code FoundationCursor}). */
	public static final String STEP_FOUNDATION = "step.build.foundation.ns";
	/** feat/build-near-player: async, foundation/visible split + crew plan of the visible rest. */
	public static final String STEP_FOUNDATION_SPLIT = "step.build.foundation_split_async.ns";
	/** feat/build-near-player: async, part priority by distance to the player ({@code NearPriority}). */
	public static final String STEP_PRIORITY_ASYNC = "step.build.priority_async.ns";
	/** feat/build-radial: server, one tick of a radial job (both streams: foundation and builders). */
	public static final String STEP_RADIAL_TICK = "step.build.radial.ns";
	/** feat/build-radial: async, plan analysis of a radial job (foundation split, columns, first order). */
	public static final String STEP_RADIAL_PLAN = "step.build.radial_plan_async.ns";
	/** feat/build-radial: async, column re-order around the moved centre. */
	public static final String STEP_RADIAL_ORDER = "step.build.radial_order_async.ns";
	/** feat/build-radial: client, merging the demo's parts into one plan (async). */
	public static final String STEP_RADIAL_MERGE = "step.build.radial_merge_async.ns";
	/** Server: listeners of jobs that started / finished, per tick. */
	public static final String STEP_STARTED = "step.build.started.ns";
	public static final String STEP_FINISHED = "step.build.finished.ns";
	/** Server: one whole BuildManager tick (placing included). */
	public static final String STEP_MANAGER_TICK = "step.build.manager_tick.ns";
	/** Server: one HQ reconcile ({@code HqConstruction.tick}, once a second). */
	public static final String STEP_HQ_TICK = "step.hq.construct_tick.ns";
	/** Async: HQ target plan ({@code HqTarget.compute}: HqPlans, lighting, packs). */
	public static final String STEP_HQ_TARGET = "step.hq.target_async.ns";
	/** Server: the in-place diff of the HQ (cells and removals of the built modules). */
	public static final String STEP_HQ_DIFF = "step.hq.diff.ns";
	/** Server: {@code Ports.update}: site claims (dry + real), cost check, writer start. */
	public static final String STEP_HQ_UPDATE = "step.hq.update.ns";
	/** Server: HQ construction created; fix/hq-load-npc-reconcile: only starts the load (inputs captured, worker asked). */
	public static final String STEP_HQ_LOAD = "step.hq.load.ns";
	/** Async (fix/hq-load-npc-reconcile): plan of the saved layout ({@code HqPlans.generate}: generator, lighting, packs). */
	public static final String STEP_HQ_LOAD_ASYNC = "step.hq.load_async.ns";
	/** Server (fix/hq-load-npc-reconcile): the loaded plan taken in on the tick it is ready. */
	public static final String STEP_HQ_LOAD_APPLY = "step.hq.load_apply.ns";
	/** Server: a site expansion started (scan, snapshot capture and fsynced saves). */
	public static final String STEP_SITE_EXPAND = "step.site.expand.ns";
	/** Server: site cell claims ({@code SiteService.claim}). */
	public static final String STEP_SITE_CLAIM = "step.site.claim.ns";
	/** Async: a site module turned into a crew plan ({@code BuildPlanBridge.toBuildPlan}, layered). */
	public static final String STEP_SITE_PLAN = "step.site.plan_async.ns";
	/** Client: builders of one crew spawned / despawned this tick. */
	public static final String STEP_CREW_SPAWN = "step.crew.spawn.ns";
	public static final String STEP_CREW_DESPAWN = "step.crew.despawn.ns";
	/** Client: build events of the tick applied to the crews (walk routes included). */
	public static final String STEP_CREW_EVENTS = "step.crew.events.ns";
	/** Client (fix/crew-route-stutter): builder routes of the tick (results applied, stretch snapshots copied). */
	public static final String STEP_CREW_ROUTE = "step.crew.route.ns";
	/** Async (fix/crew-route-stutter): one builder A* over a snapshot, on the route worker. */
	public static final String STEP_CREW_ROUTE_ASYNC = "step.crew.route_async.ns";
	/** Client: HQ presence installed (NPC anchors, stations, fixture bindings). */
	public static final String STEP_PRESENCE = "step.hq.presence.ns";
	/** Async: HQ presence sources built ({@code HqWorldAnchors}, {@code HqStations}, {@code HqBindings}). */
	public static final String STEP_PRESENCE_PREP = "step.hq.presence_async.ns";
	/** Client: NPC roster reconcile (spawns and removals). */
	public static final String STEP_NPC_RECONCILE = "step.npc.reconcile.ns";
	/** Client (fix/hq-load-npc-reconcile): reconcile part: gate, roster diff, fallback grid, grouping. */
	public static final String STEP_NPC_PLAN = "step.npc.plan.ns";
	/** Client (fix/hq-load-npc-reconcile): reconcile part: NPCs removed this tick (budgeted). */
	public static final String STEP_NPC_REMOVE = "step.npc.remove.ns";
	/** Client (fix/hq-load-npc-reconcile): reconcile part: NPCs spawned this tick (entities, skin, labels; budgeted). */
	public static final String STEP_NPC_SPAWN = "step.npc.spawn.ns";
	/** Client (fix/hq-load-npc-reconcile): changed labels handed to a worker or applied (inline when few). */
	public static final String STEP_NPC_LABELS = "step.npc.labels.ns";
	/** Async (fix/hq-load-npc-reconcile): name tags and panels of changed NPCs built ({@code Nameplate}). */
	public static final String STEP_NPC_LABELS_ASYNC = "step.npc.labels_async.ns";
	/** Client (feat/npc-idle-wander): idle strollers of the tick (spots picked, route results applied, stretch snapshots copied). */
	public static final String STEP_NPC_WANDER = "step.npc.wander.ns";
	/** Async (feat/npc-idle-wander): one stroller A* over a snapshot, on an {@code Offload} worker. */
	public static final String STEP_NPC_WANDER_ASYNC = "step.npc.wander_async.ns";
	/** Client: workstation stations tick (spawns included). */
	public static final String STEP_STATIONS = "step.stations.tick.ns";

	/** Prefix of the per-module client tick timers ({@code tick.<module>.ns}). */
	public static final String TICK_PREFIX = "tick.";
}
