package dev.t3craft.build;

import dev.t3craft.debug.PerfStats;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * fix/hq-build-stutter: background workers for the <b>pure</b> work around the start and the end of a
 * build (material meters, HQ target plans, site module conversions, HQ presence sources), so none of it
 * runs inside a server tick or a client frame. The caller hands over immutable inputs, gets a
 * {@link CompletableFuture} and applies the result on its own thread in a later tick.
 *
 * <p>Two low-priority daemon threads: enough so a short job does not wait behind a long one, few
 * enough to leave the cores to the game. The crew planner keeps its own thread ({@link DeferredCursor}).
 * Results do not depend on which worker ran them, so the outcome stays deterministic.
 */
public final class Offload {
	private static final AtomicInteger IDS = new AtomicInteger();
	private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "t3craft-offload-" + IDS.incrementAndGet());
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private Offload() {
	}

	/** Runs {@code work} on a background worker, timed into {@code timerKey} ({@link PerfStats#stepAsync}). */
	public static <T> CompletableFuture<T> supply(String timerKey, Supplier<T> work) {
		return CompletableFuture.supplyAsync(() -> {
			long t0 = System.nanoTime();
			try {
				return work.get();
			} finally {
				PerfStats.stepAsync(timerKey, t0);
			}
		}, WORKERS);
	}
}
