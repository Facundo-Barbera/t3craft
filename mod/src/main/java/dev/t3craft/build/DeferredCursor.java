package dev.t3craft.build;

import dev.t3craft.T3Craft;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A {@link JobCursor} whose real cursor is computed off-thread (the {@link CrewPlanner} of a large HQ
 * part takes a second or two): until it is ready the job stays {@link JobState#PENDING} and places
 * nothing; pause and cancel asked meanwhile are applied once it arrives. If planning fails the
 * {@code fallback} (the layer order) is used, so a job never gets stuck.
 */
final class DeferredCursor implements JobCursor {
	private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "t3craft-crew-planner");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final CompletableFuture<JobCursor> future;
	private final Supplier<JobCursor> fallback;
	private final int total;
	private final int fronts;
	private volatile @Nullable JobCursor real;
	private boolean paused;
	private boolean cancelled;

	DeferredCursor(Supplier<JobCursor> planned, Supplier<JobCursor> fallback, int total, int fronts) {
		this.future = CompletableFuture.supplyAsync(planned, PLANNER);
		this.fallback = fallback;
		this.total = total;
		this.fronts = fronts;
	}

	/** The real cursor once planning finished (installing it if it just did); null while planning. */
	@Nullable JobCursor ready() {
		JobCursor r = real;
		if (r != null) {
			return r;
		}
		if (!future.isDone()) {
			return null;
		}
		synchronized (this) {
			if (real == null) {
				JobCursor c;
				try {
					c = future.join();
				} catch (RuntimeException e) {
					T3Craft.LOGGER.warn("crew planning failed, building layer by layer", e);
					c = fallback.get();
				}
				if (cancelled) {
					c.cancel();
				} else if (paused) {
					c.pause();
				}
				real = c;
			}
			return real;
		}
	}

	/** Blocks until the real cursor is there (tests). */
	JobCursor await() {
		try {
			future.join();
		} catch (RuntimeException ignored) {
			// fallback below
		}
		return ready();
	}

	@Override
	public int tick(double allowance, FrontCursor.Step step) {
		JobCursor r = ready();
		return r == null ? 0 : r.tick(allowance, step);
	}

	@Override
	public synchronized boolean pause() {
		JobCursor r = real;
		if (r != null) {
			return r.pause();
		}
		if (cancelled || paused) {
			return false;
		}
		paused = true;
		return true;
	}

	@Override
	public synchronized boolean resume() {
		JobCursor r = real;
		if (r != null) {
			return r.resume();
		}
		if (!paused || cancelled) {
			return false;
		}
		paused = false;
		return true;
	}

	@Override
	public synchronized boolean cancel() {
		JobCursor r = real;
		if (r != null) {
			return r.cancel();
		}
		if (cancelled) {
			return false;
		}
		cancelled = true;
		return true;
	}

	@Override
	public boolean blocked() {
		JobCursor r = real;
		return r != null && r.blocked();
	}

	@Override
	public boolean frontBlocked(int f) {
		JobCursor r = real;
		return r != null && r.frontBlocked(f);
	}

	@Override
	public int segment() {
		JobCursor r = ready();
		return r == null ? 0 : r.segment();
	}

	@Override
	public int[] pendingInSegment() {
		JobCursor r = ready();
		return r == null ? new int[0] : r.pendingInSegment();
	}

	@Override
	public int frontCount() {
		return fronts;
	}

	@Override
	public int frontOf(int index) {
		JobCursor r = real;
		return r == null ? 0 : r.frontOf(index);
	}

	@Override
	public synchronized JobState state() {
		JobCursor r = real;
		if (r != null) {
			return r.state();
		}
		return cancelled ? JobState.CANCELLED : paused ? JobState.PAUSED : JobState.PENDING;
	}

	@Override
	public int total() {
		return total;
	}

	@Override
	public int done() {
		JobCursor r = real;
		return r == null ? 0 : r.done();
	}

	@Override
	public int placed() {
		JobCursor r = real;
		return r == null ? 0 : r.placed();
	}

	@Override
	public int skipped() {
		JobCursor r = real;
		return r == null ? 0 : r.skipped();
	}

	@Override
	public int failed() {
		JobCursor r = real;
		return r == null ? 0 : r.failed();
	}

	@Override
	public double progress() {
		JobCursor r = real;
		return r == null ? (total == 0 ? 1.0 : 0.0) : r.progress();
	}
}
