package dev.t3craft.site;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fallback {@link SiteBuilder}: writes the whole plan at once, bottom layer first, in the calling
 * (server) thread. Replaced by the animated builders from {@code feat/builders} when integrated.
 */
public final class InstantBuilder implements SiteBuilder {
	private final BlockWorld world;

	public InstantBuilder(BlockWorld world) {
		this.world = world;
	}

	@Override
	public Handle submit(CellPlan plan, CellPlan.Origin origin, CellGuard guard, Consumer<Report> done) {
		BlockWorld w = world;
		Box b = plan.box();
		int placed = 0;
		int skipped = 0;
		int already = 0;
		List<String> sample = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			String want = plan.get(i);
			if (want == null) {
				continue;
			}
			int x = b.xOf(i);
			int y = b.yOf(i);
			int z = b.zOf(i);
			if (want.equals(w.state(x, y, z)) && w.blockEntity(x, y, z) == null) {
				already++;
				continue;
			}
			if (!guard.mayWrite(x, y, z)) {
				skipped++;
				if (sample.size() < 8) {
					sample.add(x + " " + y + " " + z + " " + StateKey.name(w.state(x, y, z)).replace("minecraft:", ""));
				}
				continue;
			}
			w.set(x, y, z, want, null);
			placed++;
		}
		done.accept(new Report(placed, skipped, already, false, sample));
		return new Handle() {
			@Override
			public boolean finished() {
				return true;
			}

			@Override
			public void cancel() {
			}
		};
	}
}
