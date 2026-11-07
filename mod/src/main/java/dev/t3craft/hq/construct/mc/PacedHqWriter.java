package dev.t3craft.hq.construct.mc;

import dev.t3craft.build.BlockSink;
import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.ServerLevelSink;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

/**
 * Default {@link HqWriter}: bottom layer first, up to {@link #PER_TICK} cells per server tick (within the
 * global {@link BuildBudget}), through
 * {@link ServerLevelSink} (placement sound and particles) and the claim guard. Ticked by
 * {@link HqRuntime}.
 */
public final class PacedHqWriter implements HqWriter {
	public static final int PER_TICK = 32;

	private final class Job {
		final List<Write> writes;
		final BlockSink sink;
		final Consumer<Boolean> done;
		int next;

		Job(ServerLevel level, List<Write> writes, Consumer<Boolean> done) {
			this.writes = writes;
			this.sink = HqWriter.guard(level, writes).apply(new ServerLevelSink(level));
			this.done = done;
		}
	}

	private final List<Job> jobs = new ArrayList<>();
	private boolean paused;
	private double credit;
	private static final String BUDGET_RUNNER = "hq";
	private static final int MAX_SKIPS_PER_TICK = dev.t3craft.build.BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK;

	@Override
	public void write(String label, ServerLevel level, List<Write> writes, Consumer<Boolean> done) {
		List<Write> sorted = new ArrayList<>(writes);
		sorted.sort(Comparator.comparingInt((Write w) -> w.pos().getY()).thenComparingInt(w -> w.pos().getZ()).thenComparingInt(w -> w.pos().getX()));
		jobs.add(new Job(level, sorted, done));
	}

	@Override
	public void pause(boolean p) {
		paused = p;
	}

	/** Server thread. */
	public void tick() {
		if (paused || jobs.isEmpty()) {
			return;
		}
		Job j = jobs.get(0);
		// Within the global per-tick budget shared with the other builders; fractional grants carry over.
		credit = Math.min(credit + BuildBudget.global().claim(BUDGET_RUNNER, PER_TICK), PER_TICK);
		int skips = 0;
		while (credit >= 1 && j.next < j.writes.size() && skips < MAX_SKIPS_PER_TICK) {
			Write w = j.writes.get(j.next++);
			if (j.sink.place(w.pos(), w.state(), w.blockEntity()) != dev.t3craft.build.BuildCursor.Outcome.SKIPPED) {
				credit--;
			} else {
				skips++;
			}
		}
		if (j.next >= j.writes.size()) {
			jobs.remove(0);
			j.done.accept(true);
		}
	}

	/** Server stopping: drop everything (done(false) each). */
	public void cancelAll() {
		List<Job> all = List.copyOf(jobs);
		jobs.clear();
		all.forEach(j -> j.done.accept(false));
	}
}
