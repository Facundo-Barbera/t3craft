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

	/** Prefix of the per-module client tick timers ({@code tick.<module>.ns}). */
	public static final String TICK_PREFIX = "tick.";
}
