package dev.t3craft.hq.construct.mc;

import dev.t3craft.build.BlockSink;
import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.ServerLevelSink;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

/**
 * Default {@link HqWriter}: fix/build-walkable: around the player, ring by ring ({@link #RING} blocks, the
 * nearest player of the level when the update starts, else the middle of the update), bottom-up inside a
 * ring (it was bottom layer first over the whole update), up to {@link #PER_TICK} cells per server tick
 * (within the global {@link BuildBudget}), through
 * {@link ServerLevelSink} (placement sound and particles) and the claim guard. Ticked by
 * {@link HqRuntime}.
 */
public final class PacedHqWriter implements HqWriter {
	public static final int PER_TICK = 32;
	/** fix/build-walkable: ring width (blocks) of the order around the player, as {@code build.radialRing}'s default. */
	public static final int RING = 5;

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
		jobs.add(new Job(level, radialOrder(writes, centre(level, writes)), done));
	}

	/** The nearest non-spectator player of {@code level} to the middle of {@code writes} ({@code {x, z}}), else that middle. */
	static double[] centre(ServerLevel level, List<Write> writes) {
		double mx = 0;
		double mz = 0;
		for (Write w : writes) {
			mx += w.pos().getX() + 0.5;
			mz += w.pos().getZ() + 0.5;
		}
		if (!writes.isEmpty()) {
			mx /= writes.size();
			mz /= writes.size();
		}
		double best = Double.MAX_VALUE;
		double[] out = {mx, mz};
		for (net.minecraft.server.level.ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double dx = p.getX() - mx;
			double dz = p.getZ() - mz;
			double d = dx * dx + dz * dz;
			if (d < best) {
				best = d;
				out = new double[] {p.getX(), p.getZ()};
			}
		}
		return out;
	}

	/**
	 * fix/build-walkable: {@code writes} by ring around {@code centre} ({@link #RING} blocks), then bottom-up,
	 * then distance, then the given order. Pure; one packed sort.
	 */
	static List<Write> radialOrder(List<Write> writes, double[] centre) {
		int n = writes.size();
		long[] keys = new long[n];
		for (int i = 0; i < n; i++) {
			Write w = writes.get(i);
			double dx = w.pos().getX() + 0.5 - centre[0];
			double dz = w.pos().getZ() + 0.5 - centre[1];
			double d = Math.sqrt(dx * dx + dz * dz);
			long ring = Math.min(2047, (long) Math.floor(d / RING));
			long y = Math.max(0, Math.min(4095, w.pos().getY() + 2048));
			long fine = Math.max(0, Math.min(65535, (long) Math.floor(d * 4) - ring * RING * 4L));
			keys[i] = (ring << 52) | (y << 40) | (fine << 24) | Math.min(i, (1 << 24) - 1);
		}
		java.util.Arrays.sort(keys);
		List<Write> out = new ArrayList<>(n);
		if (n >= 1 << 24) {
			out.addAll(writes); // never in practice (16 M cells): keep the given order
			return out;
		}
		for (long k : keys) {
			out.add(writes.get((int) (k & 0xFFFFFF)));
		}
		return out;
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
