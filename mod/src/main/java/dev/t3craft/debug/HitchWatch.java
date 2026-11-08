package dev.t3craft.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * fix/new-thread-hitch: frame / tick watchdog that does not depend on {@link PerfStats} steps.
 *
 * <p>The game threads mark where a frame (render thread) and a tick (client tick, integrated server
 * tick) start and end: one {@code nanoTime} and a couple of volatile writes each, nothing else. A
 * daemon sampler wakes every {@value #SAMPLE_EVERY_MS} ms and, <b>only while</b> the current frame or
 * server tick has already run for more than {@value #SAMPLE_AFTER_MS} ms, takes
 * {@link Thread#getStackTrace()} of the render thread and of the server thread. Normal frames never
 * get sampled.
 *
 * <p>When a frame or server tick ends above the report threshold (50 ms by default), the sampler
 * thread summarises the samples (the {@code dev.t3craft} methods seen most, inclusive; the deepest
 * game frame; the raw leaf), adds what was going on (client tick inside the frame, recent
 * {@link #mark marks} and slow {@link PerfStats#step steps}, GC time, open screen) and logs it as one
 * {@code [hitch]} WARN in latest.log. {@code /t3dev perf hitches} lists the last {@value #MAX_KEPT}.
 *
 * <p>Frames stretched by the game's own frame limiter (window unfocused / minimised, AFK FPS limit)
 * are recognised from the samples and dropped (counted as {@value #THROTTLED}).
 */
public final class HitchWatch {
	public static final int SAMPLE_EVERY_MS = 5;
	public static final int SAMPLE_AFTER_MS = 30;
	public static final double DEFAULT_REPORT_MS = 50;
	static final int MAX_KEPT = 20;
	/** Samples kept per span: 2,000 × 5 ms = 10 s; beyond that only the count grows. */
	private static final int MAX_SAMPLES = 2000;
	/** At most this many full reports per {@link #BURST_WINDOW_NS}; the rest are counted. */
	private static final int BURST_MAX = 4;
	private static final long BURST_WINDOW_NS = 5_000_000_000L;
	/** Marks kept for the "during" line (newest last). */
	private static final int MAX_MARKS = 64;
	/** Steps at least this long become marks. */
	private static final long STEP_MARK_NS = 2_000_000L;
	public static final String THROTTLED = "hitch.throttled";
	public static final String REPORTED = "hitch.reported";

	private static final HitchWatch INSTANCE = new HitchWatch();

	public static HitchWatch get() {
		return INSTANCE;
	}

	/** One slow frame or tick, as listed by {@code /t3dev perf hitches}. */
	public record Hitch(String at, String kind, double ms, String summary) {
	}

	private static final class Lane {
		final String kind;
		volatile Thread thread;
		/** Start of the current span (nanoTime), 0 = idle. */
		volatile long startNs;
		/** Set by the sampler when it added samples to the current span. */
		volatile boolean sampled;
		/** Guarded by this. */
		final ArrayList<StackTraceElement[]> samples = new ArrayList<>();
		/** Frame lane only: the server thread sampled at the same instants. Guarded by this. */
		final ArrayList<StackTraceElement[]> other = new ArrayList<>();
		int dropped;
		/** Game thread only: longest nested client tick inside the current frame. */
		long innerMaxNs;
		long innerStartNs;
		final ArrayDeque<Long> burst = new ArrayDeque<>();
		int suppressed;

		Lane(String kind) {
			this.kind = kind;
		}
	}

	private record Span(Lane lane, String thread, long startNs, long endNs, long innerMaxNs, List<StackTraceElement[]> samples,
			List<StackTraceElement[]> other, int dropped, long gcMs, String context, long wallMs) {
	}

	private record Mark(long ns, String thread, String text) {
	}

	private final Lane frame = new Lane("frame");
	private final Lane server = new Lane("server tick");
	private volatile boolean enabled;
	private volatile long reportNs = (long) (DEFAULT_REPORT_MS * 1_000_000);
	private final long sampleAfterNs = SAMPLE_AFTER_MS * 1_000_000L;
	private volatile Supplier<String> context = () -> "";
	private final ConcurrentLinkedQueue<Span> pending = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<Mark> marks = new ArrayDeque<>();
	private final ArrayDeque<Hitch> kept = new ArrayDeque<>();
	private volatile long gcBaselineMs;
	private Thread sampler;

	private HitchWatch() {
	}

	// ---- configuration ---------------------------------------------------------------------------

	public synchronized void configure(boolean on, double reportMs) {
		this.reportNs = (long) (Math.max(SAMPLE_AFTER_MS + 5, reportMs) * 1_000_000);
		this.enabled = on;
		if (!on) {
			frame.startNs = 0;
			server.startNs = 0;
			return;
		}
		if (sampler == null) {
			sampler = new Thread(this::run, "t3craft-hitch-sampler");
			sampler.setDaemon(true);
			sampler.setPriority(Thread.MAX_PRIORITY);
			sampler.start();
		}
	}

	public boolean enabled() {
		return enabled;
	}

	public double reportMs() {
		return reportNs / 1e6;
	}

	/** Extra "what was going on" for frame reports (open screen, fps…); called on the render thread, only for slow frames. */
	public void context(Supplier<String> supplier) {
		this.context = supplier;
	}

	// ---- game thread hooks (cheap) ---------------------------------------------------------------

	/** Render thread, once per frame (end of the level render): closes the previous frame, opens the next. */
	public void frame() {
		if (!enabled) {
			return;
		}
		long now = System.nanoTime();
		Lane l = frame;
		if (l.thread != Thread.currentThread()) {
			l.thread = Thread.currentThread();
		}
		long start = l.startNs;
		if (start != 0) {
			close(l, start, now);
		}
		l.innerMaxNs = 0;
		l.startNs = now;
	}

	/** Render thread: no frame is being watched (no level, HUD screen without world…): the next {@link #frame} starts fresh. */
	public void frameIdle() {
		frame.startNs = 0;
	}

	/** Render thread: a client tick starts / ends (nested in the current frame, reported with it). */
	public void clientTickStart() {
		if (enabled) {
			frame.innerStartNs = System.nanoTime();
		}
	}

	public void clientTickEnd() {
		long s = frame.innerStartNs;
		if (enabled && s != 0) {
			frame.innerMaxNs = Math.max(frame.innerMaxNs, System.nanoTime() - s);
			frame.innerStartNs = 0;
		}
	}

	/** Integrated server thread: a tick starts / ends. */
	public void serverTickStart() {
		if (!enabled) {
			return;
		}
		Lane l = server;
		if (l.thread != Thread.currentThread()) {
			l.thread = Thread.currentThread();
		}
		l.startNs = System.nanoTime();
	}

	public void serverTickEnd() {
		Lane l = server;
		long start = l.startNs;
		if (!enabled || start == 0) {
			return;
		}
		close(l, start, System.nanoTime());
	}

	/** Server stopped: forget its thread. */
	public void serverStopped() {
		server.startNs = 0;
		server.thread = null;
	}

	private void close(Lane l, long start, long end) {
		long ns = end - start;
		if (ns <= sampleAfterNs && !l.sampled) {
			l.startNs = 0;
			return;
		}
		List<StackTraceElement[]> s;
		List<StackTraceElement[]> o;
		int dropped;
		synchronized (l) {
			l.startNs = 0;
			s = ns > reportNs ? List.copyOf(l.samples) : List.of();
			o = ns > reportNs ? List.copyOf(l.other) : List.of();
			dropped = l.dropped;
			l.samples.clear();
			l.other.clear();
			l.dropped = 0;
			l.sampled = false;
		}
		if (ns <= reportNs) {
			return;
		}
		String ctx = "";
		if (l == frame) {
			try {
				ctx = context.get();
			} catch (RuntimeException e) {
				ctx = "context failed: " + e;
			}
		}
		pending.add(new Span(l, Thread.currentThread().getName(), start, end, l == frame ? l.innerMaxNs : 0, s, o, dropped, gcTotalMs(), ctx,
				System.currentTimeMillis()));
		LockSupport.unpark(sampler);
	}

	// ---- context marks -----------------------------------------------------------------------------

	/**
	 * Notes something that just happened (a thread appeared, an NPC spawned, a texture uploaded…), any
	 * thread. Marks in the window of a hitch are listed with it. Meant for rare events, not per tick.
	 */
	public static void mark(String text) {
		HitchWatch w = INSTANCE;
		if (!w.enabled) {
			return;
		}
		Mark m = new Mark(System.nanoTime(), Thread.currentThread().getName(), text);
		synchronized (w.marks) {
			if (w.marks.size() >= MAX_MARKS) {
				w.marks.removeFirst();
			}
			w.marks.addLast(m);
		}
	}

	/** Called by {@link PerfStats#step}: steps of 2 ms and more become marks. */
	static void step(String key, long ns) {
		if (ns >= STEP_MARK_NS && INSTANCE.enabled) {
			mark(String.format(Locale.ROOT, "step %s %.1f ms", PerfStats.stepName(key), ns / 1e6));
		}
	}

	/** The last hitches (oldest first). */
	public List<Hitch> recent() {
		synchronized (kept) {
			return List.copyOf(kept);
		}
	}

	public void clear() {
		synchronized (kept) {
			kept.clear();
		}
	}

	// ---- sampler thread -------------------------------------------------------------------------

	private void run() {
		long loops = 0;
		while (true) {
			try {
				LockSupport.parkNanos(SAMPLE_EVERY_MS * 1_000_000L);
				Span sp;
				while ((sp = pending.poll()) != null) {
					report(sp);
				}
				if (!enabled) {
					LockSupport.parkNanos(200_000_000L);
					continue;
				}
				long now = System.nanoTime();
				boolean hot = sample(frame, now, true);
				hot |= sample(server, now, false);
				if (!hot && ++loops % 20 == 0) {
					gcBaselineMs = gcTotalMs();
				}
			} catch (Throwable t) {
				dev.t3craft.T3Craft.LOGGER.warn("[hitch] sampler error: {}", t.toString());
				LockSupport.parkNanos(1_000_000_000L);
			}
		}
	}

	private boolean sample(Lane l, long now, boolean withServer) {
		long start = l.startNs;
		Thread t = l.thread;
		if (start == 0 || t == null || now - start <= sampleAfterNs) {
			return false;
		}
		StackTraceElement[] st = t.getStackTrace();
		Thread srv = withServer ? server.thread : null;
		StackTraceElement[] other = srv != null && srv != t ? srv.getStackTrace() : null;
		synchronized (l) {
			if (l.startNs != start) {
				return true; // that span closed meanwhile
			}
			if (l.samples.size() < MAX_SAMPLES) {
				l.samples.add(st);
				if (other != null) {
					l.other.add(other);
				}
			} else {
				l.dropped++;
			}
			l.sampled = true;
		}
		return true;
	}

	private void report(Span sp) {
		double ms = (sp.endNs - sp.startNs) / 1e6;
		Summary s = Summary.of(sp.samples);
		if (s.throttled) {
			PerfStats.count(THROTTLED);
			return;
		}
		Lane l = sp.lane;
		long now = System.nanoTime();
		while (!l.burst.isEmpty() && now - l.burst.peekFirst() > BURST_WINDOW_NS) {
			l.burst.removeFirst();
		}
		String at = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
		String top = !s.ours.isEmpty() ? s.ours.get(0) : !s.engine.isEmpty() ? s.engine.get(0) : !s.leaf.isEmpty() ? s.leaf.get(0) : "no samples (GC / safepoint?)";
		synchronized (kept) {
			if (kept.size() >= MAX_KEPT) {
				kept.removeFirst();
			}
			kept.addLast(new Hitch(at, l.kind, ms, top));
		}
		PerfStats.count(REPORTED);
		if (l.burst.size() >= BURST_MAX) {
			l.suppressed++;
			return;
		}
		l.burst.addLast(now);
		StringBuilder b = new StringBuilder();
		b.append(String.format(Locale.ROOT, "[hitch] %s %.1f ms on %s", l.kind, ms, sp.thread));
		if (sp.innerMaxNs > 1_000_000) {
			b.append(String.format(Locale.ROOT, " (client tick inside: %.1f ms)", sp.innerMaxNs / 1e6));
		}
		long gc = Math.max(0, sp.gcMs - gcBaselineMs);
		b.append(String.format(Locale.ROOT, "; %d sample(s) every %d ms after %d ms", sp.samples.size() + sp.dropped, SAMPLE_EVERY_MS, SAMPLE_AFTER_MS));
		if (gc > 0) {
			b.append("; GC ").append(gc).append(" ms");
		}
		if (l.suppressed > 0) {
			b.append("; ").append(l.suppressed).append(" more hitch(es) not detailed since the last report");
			l.suppressed = 0;
		}
		if (!sp.context.isEmpty()) {
			b.append("; ").append(sp.context);
		}
		if (sp.samples.isEmpty()) {
			b.append("\n  (no stack sample: the thread was stopped as a whole — GC / safepoint — or the hitch ended between samples)");
		}
		section(b, "ours (dev.t3craft, inclusive)", s.ours);
		section(b, "deepest game frame (net.minecraft / dev.t3craft / blaze3d)", s.engine);
		section(b, "leaf", s.leaf);
		if (!s.path.isEmpty()) {
			b.append("\n  most common stack: ").append(s.path);
		}
		if (!sp.other.isEmpty()) {
			Summary o = Summary.of(sp.other);
			List<String> lines = o.ours.isEmpty() ? o.engine : o.ours;
			if (!lines.isEmpty()) {
				b.append("\n  server thread meanwhile: ").append(String.join(" | ", lines.subList(0, Math.min(3, lines.size()))));
			}
		}
		List<String> during = during(sp.startNs - 250_000_000L, sp.endNs);
		if (!during.isEmpty()) {
			b.append("\n  during: ").append(String.join("; ", during));
		}
		dev.t3craft.T3Craft.LOGGER.warn(b.toString());
	}

	private static void section(StringBuilder b, String title, List<String> lines) {
		if (lines.isEmpty()) {
			return;
		}
		b.append("\n  ").append(title).append(":");
		for (String line : lines) {
			b.append("\n    ").append(line);
		}
	}

	private List<String> during(long from, long to) {
		List<String> out = new ArrayList<>();
		synchronized (marks) {
			for (Mark m : marks) {
				if (m.ns >= from && m.ns <= to) {
					out.add(String.format(Locale.ROOT, "%s [%s, %+.0f ms]", m.text, m.thread, (m.ns - from) / 1e6 - 250));
				}
			}
		}
		return out;
	}

	private static long gcTotalMs() {
		long t = 0;
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			long c = gc.getCollectionTime();
			if (c > 0) {
				t += c;
			}
		}
		return t;
	}

	/** What a set of stack samples says (built on the sampler thread). */
	static final class Summary {
		final List<String> ours;
		final List<String> engine;
		final List<String> leaf;
		final String path;
		final boolean throttled;

		private Summary(List<String> ours, List<String> engine, List<String> leaf, String path, boolean throttled) {
			this.ours = ours;
			this.engine = engine;
			this.leaf = leaf;
			this.path = path;
			this.throttled = throttled;
		}

		static Summary of(List<StackTraceElement[]> samples) {
			int n = samples.size();
			Map<String, Integer> ours = new HashMap<>();
			Map<String, String> oursLine = new HashMap<>();
			Map<String, Integer> engine = new HashMap<>();
			Map<String, Integer> leaf = new HashMap<>();
			Map<String, Integer> paths = new HashMap<>();
			int idle = 0;
			Set<String> seen = new HashSet<>();
			for (StackTraceElement[] st : samples) {
				if (st.length == 0) {
					continue;
				}
				if (idle(st)) {
					idle++;
				}
				leaf.merge(frameName(st[0], true), 1, Integer::sum);
				seen.clear();
				String deepest = null;
				String deepestOurs = null;
				for (StackTraceElement e : st) {
					String c = e.getClassName();
					if (deepest == null && (c.startsWith("net.minecraft.") || c.startsWith("com.mojang.blaze3d.") || (c.startsWith("dev.t3craft.") && !wrapper(c)))) {
						deepest = frameName(e, true);
					}
					if (c.startsWith("dev.t3craft.") && !wrapper(c)) {
						String key = frameName(e, false);
						if (seen.add(key)) {
							ours.merge(key, 1, Integer::sum);
							oursLine.putIfAbsent(key, frameName(e, true));
						}
						if (deepestOurs == null) {
							deepestOurs = frameName(e, true);
						}
					}
				}
				if (deepest != null) {
					engine.merge(deepest, 1, Integer::sum);
				}
				String p = (deepestOurs != null ? deepestOurs + " → " : "") + (deepest != null && !deepest.equals(deepestOurs) ? deepest + " → " : "")
						+ frameName(st[0], true);
				paths.merge(p, 1, Integer::sum);
			}
			boolean throttled = n > 0 && idle * 10 >= n * 6;
			List<String> o = top(ours, n, 5);
			for (int i = 0; i < o.size(); i++) {
				// show the line of the first time this method was seen (usually the deepest call)
				String line = o.get(i);
				String key = line.substring(line.indexOf(' ') + 1);
				String withLine = oursLine.get(key);
				if (withLine != null) {
					o.set(i, line.substring(0, line.indexOf(' ') + 1) + withLine);
				}
			}
			List<String> topPath = top(paths, n, 1);
			return new Summary(o, top(engine, n, 5), top(leaf, n, 3), topPath.isEmpty() ? "" : topPath.get(0), throttled);
		}

		/** Our own plumbing that wraps everything (would top every list). */
		private static boolean wrapper(String cls) {
			return cls.startsWith("dev.t3craft.debug.") || cls.startsWith("dev.t3craft.client.debug.");
		}

		/** The frame limiter / vsync wait / window event wait: not a hitch of ours. */
		private static boolean idle(StackTraceElement[] st) {
			int depth = Math.min(st.length, 24);
			for (int i = 0; i < depth; i++) {
				String c = st[i].getClassName();
				String m = st[i].getMethodName();
				if (m.contains("limitDisplayFPS") || c.contains("FpsLimit") || m.contains("WaitEvents") || m.contains("waitEvents")) {
					return true;
				}
			}
			return false;
		}

		private static String frameName(StackTraceElement e, boolean line) {
			String c = e.getClassName();
			if (c.startsWith("dev.t3craft.")) {
				c = c.substring("dev.t3craft.".length());
			}
			String s = c + "." + e.getMethodName();
			return line && e.getLineNumber() > 0 ? s + ":" + e.getLineNumber() : s;
		}

		private static List<String> top(Map<String, Integer> counts, int n, int k) {
			List<Map.Entry<String, Integer>> es = new ArrayList<>(counts.entrySet());
			es.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
			List<String> out = new ArrayList<>();
			for (int i = 0; i < Math.min(k, es.size()); i++) {
				out.add(es.get(i).getValue() + "/" + n + " " + es.get(i).getKey());
			}
			return out;
		}
	}
}
