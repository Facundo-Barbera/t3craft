package dev.t3craft.outbound;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sends {@link OutboundEvent}s to the configured {@link Destination}s. Pure Java, no Minecraft classes.
 *
 * <ul>
 *   <li><b>Off the caller's thread.</b> {@link #emit} only hands the event to one daemon worker thread and
 *   returns; the config read, the secret read, the rendering and the HTTP call all happen there, with
 *   connect and request timeouts ({@link HttpTransport}). Nothing here throws to the caller, so a failure
 *   never reaches the client.</li>
 *   <li><b>Off by default.</b> Nothing is queued unless {@link OutboundConfig#enabled()} and the
 *   destination accepts the event ({@link Destination#accepts}).</li>
 *   <li><b>Debounce and coalesce.</b> Per destination, events of the same thread replace each other while
 *   they wait: a thread that flaps sends only its latest state, {@link Policy#debounceMs()} after the last
 *   change (never later than {@link Policy#maxDelayMs()} after the first). Everything due at once goes out
 *   as one message, and sends to one destination are at least {@link Policy#minGapMs()} apart.</li>
 *   <li><b>Errors.</b> 429 and 503 wait for the server's {@code Retry-After}; timeouts and 5xx retry with
 *   backoff up to {@link Policy#maxAttempts()}; other 4xx drop the message. Events older than
 *   {@link Policy#maxAgeMs()} are dropped. The queue per destination is bounded.</li>
 *   <li><b>Secrets.</b> The URL comes from the {@code secrets} function at send time, is never stored
 *   here and never reaches {@code log} (only destination ids, HTTP codes and exception class names).</li>
 * </ul>
 *
 * <p>All mutable state is confined to the worker thread, so there are no locks.
 */
public final class OutboundEmitter implements AutoCloseable {
	private static final long TICK_MS = 500;

	/** Timing and size limits. All in milliseconds except the counts. */
	public record Policy(long debounceMs, long maxDelayMs, long minGapMs, long maxAgeMs, int maxPending, int maxAttempts,
			long defaultRetryAfterMs, long maxRetryAfterMs) {
		public static final Policy DEFAULT = new Policy(5_000, 30_000, 3_000, 600_000, 50, 3, 30_000, 3_600_000);
	}

	private static final class Entry {
		OutboundEvent event;
		final long firstAt;
		long lastAt;
		long notBefore;
		int attempts;

		Entry(OutboundEvent event, long now) {
			this.event = event;
			this.firstAt = now;
			this.lastAt = now;
		}
	}

	private static final class Queue {
		final Map<String, Entry> pending = new LinkedHashMap<>();
		long blockedUntil;
		long lastSentAt;
		boolean warnedNoUrl;
	}

	private final Supplier<OutboundConfig> config;
	private final Function<String, URI> secrets; // destination id -> webhook URL, or null when none is stored
	private final Transport transport;
	private final Consumer<String> log;
	private final Policy policy;
	private final LongSupplier clock;
	private final ScheduledExecutorService exec;

	// Worker-thread confined.
	private final Map<String, Queue> queues = new HashMap<>();
	private boolean tickScheduled;

	/**
	 * @param config the current settings; called on the worker thread for every event, so it should be cheap
	 *     ({@link OutboundStore#load} is)
	 * @param secrets destination id → webhook URL (null when none); called on the worker thread
	 * @param log gets short messages without URLs or content; may be called from the worker thread
	 */
	public OutboundEmitter(Supplier<OutboundConfig> config, Function<String, URI> secrets, Transport transport, Consumer<String> log,
			Policy policy, LongSupplier clock) {
		this.config = config;
		this.secrets = secrets;
		this.transport = transport;
		this.log = log;
		this.policy = policy;
		this.clock = clock;
		this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "t3craft-outbound");
			t.setDaemon(true);
			return t;
		});
	}

	/** Hands the event over and returns at once. Never throws. */
	public void emit(OutboundEvent event) {
		try {
			exec.execute(() -> guarded(() -> accept(event)));
		} catch (RuntimeException closed) {
			// shut down: nothing to do
		}
	}

	/**
	 * Sends one test message to a saved destination right away (explicit player action), ignoring the
	 * master switch, the event filter and the debounce. {@code done} gets a short result text and is
	 * called on the worker thread.
	 */
	public void sendTest(String destinationId, Consumer<String> done) {
		try {
			exec.execute(() -> {
				String result;
				try {
					result = test(destinationId);
				} catch (RuntimeException e) {
					result = "failed: " + e.getClass().getSimpleName();
				}
				try {
					done.accept(result);
				} catch (RuntimeException ignored) {
					// the callback is the caller's problem
				}
			});
		} catch (RuntimeException closed) {
			done.accept("failed: not running");
		}
	}

	@Override
	public void close() {
		exec.shutdownNow();
	}

	private String test(String destinationId) {
		Destination d = config.get().destination(destinationId);
		if (d == null) {
			return "save the destination first";
		}
		URI url = secrets.apply(destinationId);
		if (url == null) {
			return "no webhook URL set";
		}
		OutboundEvent e = new OutboundEvent(EventType.NEEDS_ATTENTION, "test", "test", "T3Craft test", "Test", EventType.NEEDS_ATTENTION.wire,
				"This is a test message from T3Craft", clock.getAsLong());
		Message m = Message.compose(d, List.of(e));
		return transport.send(url, d.template().render(url, m)).describe();
	}

	private void guarded(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			log.accept("internal error: " + e.getClass().getSimpleName());
		}
	}

	private void accept(OutboundEvent e) {
		OutboundConfig cfg = config.get();
		if (!cfg.enabled()) {
			return;
		}
		long now = clock.getAsLong();
		for (Destination d : cfg.destinations()) {
			if (!d.accepts(e.type())) {
				continue;
			}
			Queue q = queues.computeIfAbsent(d.id(), k -> new Queue());
			// "Pending only" keeps no content at all and all events share one slot.
			OutboundEvent stored = d.pendingOnly() ? OutboundEvent.pending(now) : e;
			String key = d.pendingOnly() ? "*" : e.key();
			Entry existing = q.pending.get(key);
			if (existing != null) {
				existing.event = stored;
				existing.lastAt = now;
				continue;
			}
			if (q.pending.size() >= policy.maxPending()) {
				Iterator<String> oldest = q.pending.keySet().iterator();
				oldest.next();
				oldest.remove();
				log.accept("destination " + d.id() + ": queue full, dropped the oldest event");
			}
			q.pending.put(key, new Entry(stored, now));
		}
		if (!queues.isEmpty()) {
			ensureTick();
		}
	}

	private void ensureTick() {
		if (tickScheduled) {
			return;
		}
		tickScheduled = true;
		try {
			exec.schedule(this::tick, TICK_MS, TimeUnit.MILLISECONDS);
		} catch (RuntimeException closed) {
			tickScheduled = false;
		}
	}

	private void tick() {
		tickScheduled = false;
		try {
			OutboundConfig cfg = config.get();
			if (!cfg.enabled()) {
				queues.clear();
				return;
			}
			Iterator<Map.Entry<String, Queue>> it = queues.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Queue> qe = it.next();
				Destination d = cfg.destination(qe.getKey());
				if (d == null || !d.enabled()) {
					it.remove();
					continue;
				}
				flush(d, qe.getValue(), clock.getAsLong());
				if (qe.getValue().pending.isEmpty()) {
					it.remove();
				}
			}
		} catch (RuntimeException e) {
			log.accept("internal error: " + e.getClass().getSimpleName());
			queues.clear();
		} finally {
			if (!queues.isEmpty()) {
				ensureTick();
			}
		}
	}

	private boolean due(Entry e, long now) {
		return now >= e.notBefore && now >= Math.min(e.lastAt + policy.debounceMs(), e.firstAt + policy.maxDelayMs());
	}

	private void flush(Destination d, Queue q, long now) {
		q.pending.values().removeIf(e -> now - e.firstAt > policy.maxAgeMs());
		if (q.pending.isEmpty() || now < q.blockedUntil || now < q.lastSentAt + policy.minGapMs()) {
			return;
		}
		List<String> keys = new ArrayList<>();
		List<OutboundEvent> events = new ArrayList<>();
		for (Map.Entry<String, Entry> e : q.pending.entrySet()) {
			if (due(e.getValue(), now)) {
				keys.add(e.getKey());
				events.add(e.getValue().event);
			}
		}
		if (keys.isEmpty()) {
			return;
		}
		URI url = secrets.apply(d.id());
		if (url == null) {
			if (!q.warnedNoUrl) {
				q.warnedNoUrl = true;
				log.accept("destination " + d.id() + ": no webhook URL stored, events dropped");
			}
			q.pending.keySet().removeAll(keys);
			return;
		}
		q.warnedNoUrl = false;
		Result r = transport.send(url, d.template().render(url, Message.compose(d, events)));
		q.lastSentAt = clock.getAsLong();
		if (r.ok()) {
			q.pending.keySet().removeAll(keys);
			return;
		}
		if (r.retryAfterMs() > 0 && (r.status() == 429 || r.status() == 503)) {
			q.blockedUntil = q.lastSentAt + Math.min(r.retryAfterMs(), policy.maxRetryAfterMs());
			log.accept("destination " + d.id() + ": HTTP " + r.status() + ", waiting " + Math.min(r.retryAfterMs(), policy.maxRetryAfterMs()) / 1000
					+ " s (Retry-After)");
			return;
		}
		if (r.status() == 429) {
			q.blockedUntil = q.lastSentAt + policy.defaultRetryAfterMs();
			log.accept("destination " + d.id() + ": HTTP 429 without Retry-After, waiting " + policy.defaultRetryAfterMs() / 1000 + " s");
			return;
		}
		boolean retryable = r.status() == 0 || r.status() == 408 || r.status() >= 500;
		if (!retryable) {
			q.pending.keySet().removeAll(keys);
			log.accept("destination " + d.id() + ": rejected (" + r.describe() + "), dropped " + keys.size() + " event(s)");
			return;
		}
		for (String k : keys) {
			Entry e = q.pending.get(k);
			if (e == null) {
				continue;
			}
			e.attempts++;
			if (e.attempts >= policy.maxAttempts()) {
				q.pending.remove(k);
			} else {
				e.notBefore = q.lastSentAt + backoffMs(e.attempts);
			}
		}
		log.accept("destination " + d.id() + ": " + r.describe() + ", will retry");
	}

	/** 5 s, 30 s, 180 s… after the 1st, 2nd, 3rd failed attempt. */
	private static long backoffMs(int attempts) {
		long ms = 5_000;
		for (int i = 1; i < attempts; i++) {
			ms *= 6;
		}
		return ms;
	}
}
