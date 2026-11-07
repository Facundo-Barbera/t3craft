package dev.t3craft.hq.construct;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The HQ a roster asks for, given the layout already applied (so desks and modules stay where they
 * are) and the site's capacity. Threads that would need a module beyond {@code maxModules} get no
 * desk for now ({@link #unseated}); they get one as soon as a desk frees up.
 *
 * @param unseated active threads left without a desk because the HQ is full, sorted
 */
public record HqTarget(HqInput input, HqLayout layout, BuildPlan plan, List<String> unseated) {
	public static HqTarget compute(HqInput input, @Nullable HqLayout previous, int maxModules) {
		Set<String> dropped = new TreeSet<>();
		HqInput in = input;
		HqLayout layout = HqLayout.allocate(in, previous);
		// Each pass removes the threads seated beyond capacity; desks of the others are kept, so it settles quickly.
		for (int pass = 0; pass < 64 && over(layout, maxModules); pass++) {
			for (HqLayout.Module m : layout.modules()) {
				if (m.index() >= maxModules) {
					for (HqLayout.Desk d : m.desks()) {
						dropped.add(d.threadId());
					}
				}
			}
			in = without(input, dropped);
			layout = HqLayout.allocate(in, previous);
		}
		return new HqTarget(input, layout, dev.t3craft.hq.HqPlans.generate(layout), List.copyOf(dropped));
	}

	private static boolean over(HqLayout layout, int max) {
		for (HqLayout.Module m : layout.modules()) {
			if (m.index() >= max) {
				return true;
			}
		}
		return false;
	}

	/** {@code input} without the active threads in {@code drop} (settled ones are kept). */
	static HqInput without(HqInput input, Set<String> drop) {
		List<HqInput.Project> out = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			List<String> active = new ArrayList<>(p.threads());
			active.removeAll(drop);
			out.add(new HqInput.Project(p.key(), p.label(), p.envKey(), p.envLabel(), active, p.settled()));
		}
		return new HqInput(out);
	}

	/** Highest module index the layout uses, -1 when it has no module. */
	public int maxIndex() {
		int m = -1;
		for (HqLayout.Module mod : layout.modules()) {
			m = Math.max(m, mod.index());
		}
		return m;
	}
}
