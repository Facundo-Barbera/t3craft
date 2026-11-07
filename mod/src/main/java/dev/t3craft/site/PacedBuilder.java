package dev.t3craft.site;

import dev.t3craft.build.BuildCursor;
import dev.t3craft.build.FrontCursor;
import dev.t3craft.build.Fronts;
import dev.t3craft.build.JobState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link SiteBuilder} that writes a plan a few cells per tick (bottom layer first) instead of all at
 * once: every layer is split into parallel fronts ({@link FrontCursor}, {@link Fronts}) that share a
 * per-tick budget ({@link Allocator}, the global build budget in game). Follows the whole {@link SiteBuilder}
 * contract: the guard is asked right before each cell (so a block the player places mid-build is
 * never overwritten), cells already right are skipped for free, and {@code done} is called once.
 * The owner calls {@link #tick()} once per server tick; nothing here touches Minecraft directly
 * (effects come from the {@link BlockWorld} it writes to).
 */
public final class PacedBuilder implements SiteBuilder {
	/**
	 * Per-tick allowance of the running jobs: given their states and wanted rates (blocks/tick), what
	 * each may place this tick. The site wires the global build budget ({@code BuildBudget.allocate}).
	 */
	@FunctionalInterface
	public interface Allocator {
		double[] allocate(List<JobState> states, double[] rates);

		/** Every job at its own rate, no shared budget. */
		Allocator UNLIMITED = (states, rates) -> rates.clone();
	}

	private final BlockWorld world;
	private final double blocksPerTick;
	private final int fronts;
	private final Allocator allocator;
	private final List<Job> jobs = new ArrayList<>();

	public PacedBuilder(BlockWorld world, double blocksPerTick) {
		this(world, blocksPerTick, 1, Allocator.UNLIMITED);
	}

	/**
	 * @param fronts    parallel fronts per plan (layer by layer, see {@link Fronts}), each at {@code blocksPerTick}
	 * @param allocator shared per-tick budget
	 */
	public PacedBuilder(BlockWorld world, double blocksPerTick, int fronts, Allocator allocator) {
		if (!(blocksPerTick > 0) || Double.isInfinite(blocksPerTick)) {
			throw new IllegalArgumentException("blocksPerTick must be > 0 and finite: " + blocksPerTick);
		}
		this.world = world;
		this.blocksPerTick = blocksPerTick;
		this.fronts = Math.max(1, fronts);
		this.allocator = allocator;
	}

	private final class Job implements Handle {
		final CellPlan plan;
		final int[] cells;
		final CellGuard guard;
		final Consumer<Report> done;
		final FrontCursor cursor;
		final List<String> sample = new ArrayList<>();
		int placed;
		int skipped;
		int already;
		boolean finished;

		Job(CellPlan plan, CellGuard guard, Consumer<Report> done) {
			this.plan = plan;
			this.guard = guard;
			this.done = done;
			int n = 0;
			int[] idx = new int[plan.count()];
			for (int i = 0; i < plan.size(); i++) {
				if (plan.get(i) != null) {
					idx[n++] = i;
				}
			}
			this.cells = java.util.Arrays.copyOf(idx, n);
			int[] ys = new int[n];
			for (int k = 0; k < n; k++) {
				ys[k] = plan.box().yOf(cells[k]);
			}
			// Cells are in box order (y outermost), so each y is one segment; cells are distinct positions.
			this.cursor = new FrontCursor(Fronts.split(ys, null, fronts));
		}

		BuildCursor.Outcome step(int front, int k) {
			int i = cells[k];
			Box b = plan.box();
			int x = b.xOf(i);
			int y = b.yOf(i);
			int z = b.zOf(i);
			String want = plan.get(i);
			if (want.equals(world.state(x, y, z)) && world.blockEntity(x, y, z) == null) {
				already++;
				return BuildCursor.Outcome.SKIPPED;
			}
			if (!guard.mayWrite(x, y, z)) {
				skipped++;
				if (sample.size() < 8) {
					sample.add(x + " " + y + " " + z + " " + StateKey.name(world.state(x, y, z)).replace("minecraft:", ""));
				}
				return BuildCursor.Outcome.SKIPPED;
			}
			world.set(x, y, z, want, null);
			placed++;
			return BuildCursor.Outcome.PLACED;
		}

		void finish(boolean cancelled) {
			if (finished) {
				return;
			}
			finished = true;
			done.accept(new Report(placed, skipped, already, cancelled, List.copyOf(sample)));
		}

		@Override
		public boolean finished() {
			return finished;
		}

		@Override
		public void cancel() {
			cursor.cancel();
			finish(true);
		}
	}

	@Override
	public Handle submit(CellPlan plan, CellPlan.Origin origin, CellGuard guard, Consumer<Report> done) {
		Job j = new Job(plan, guard, done);
		jobs.add(j);
		return j;
	}

	/** Advances the running jobs within the shared budget, every front of each in turn. Server thread. */
	public void tick() {
		List<Job> live = List.copyOf(jobs);
		List<JobState> states = new ArrayList<>(live.size());
		double[] rates = new double[live.size()];
		for (int i = 0; i < rates.length; i++) {
			Job j = live.get(i);
			states.add(j.finished ? JobState.CANCELLED : j.cursor.state());
			rates[i] = blocksPerTick * fronts;
		}
		double[] allow = allocator.allocate(states, rates);
		for (int i = 0; i < rates.length; i++) {
			Job j = live.get(i);
			if (!j.finished && !(allow[i] <= 0 && j.cursor.state() == JobState.PENDING)) {
				j.cursor.tick(allow[i], j::step);
				if (j.cursor.state().isTerminal()) {
					j.finish(j.cursor.state() == JobState.CANCELLED);
				}
			}
		}
		jobs.removeIf(j -> j.finished);
	}

	/** Cancels every job (server stopping); each {@code done} is called with {@code cancelled}. */
	public void cancelAll() {
		for (Job j : List.copyOf(jobs)) {
			j.cancel();
		}
		jobs.clear();
	}

	public boolean idle() {
		return jobs.isEmpty();
	}
}
