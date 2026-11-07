package dev.t3craft.site;

import dev.t3craft.build.BuildCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link SiteBuilder} that writes a plan a few cells per tick (bottom layer first) instead of all at
 * once, pacing with the builders' {@link BuildCursor}. Follows the whole {@link SiteBuilder}
 * contract: the guard is asked right before each cell (so a block the player places mid-build is
 * never overwritten), cells already right are skipped for free, and {@code done} is called once.
 * The owner calls {@link #tick()} once per server tick; nothing here touches Minecraft directly
 * (effects come from the {@link BlockWorld} it writes to).
 */
public final class PacedBuilder implements SiteBuilder {
	private final BlockWorld world;
	private final double blocksPerTick;
	private final List<Job> jobs = new ArrayList<>();

	public PacedBuilder(BlockWorld world, double blocksPerTick) {
		this.world = world;
		this.blocksPerTick = blocksPerTick;
	}

	private final class Job implements Handle {
		final CellPlan plan;
		final int[] cells;
		final CellGuard guard;
		final Consumer<Report> done;
		final BuildCursor cursor;
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
			this.cells = idx;
			this.cursor = new BuildCursor(cells.length, blocksPerTick);
		}

		BuildCursor.Outcome step(int k) {
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

	/** Advances every running job by its budget. Server thread. */
	public void tick() {
		for (Job j : List.copyOf(jobs)) {
			if (!j.finished) {
				j.cursor.tick(j::step);
				if (j.cursor.state().isTerminal()) {
					j.finish(j.cursor.state() == dev.t3craft.build.JobState.CANCELLED);
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
