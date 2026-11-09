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
		dev.t3craft.hq.HqStyle style = HqSiteFrame.style();
		HqLayout layout = style.allocate(in, previous);
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
			layout = style.allocate(in, previous);
		}
		BuildPlan plan = dev.t3craft.hq.HqPlans.generate(layout, style);
		// feat/lead-review-walk: a lead's new children take the free desk nearest its office (any layout);
		// only seats new in this allocation move, so the plan is generated twice only when one does
		java.util.Map<String, String> team = leadTeam();
		if (!team.isEmpty()) {
			HqLayout near = dev.t3craft.hq.lead.LeadTeamSeats.arrange(layout, previous, team, plan.anchors());
			if (near != layout) {
				layout = near;
				plan = dev.t3craft.hq.HqPlans.generate(layout, style);
			}
		}
		return new HqTarget(input, layout, plan, List.copyOf(dropped));
	}

	/** A lead's live delegated children to the lead (NPC ids), empty when {@code leadOffice.childrenNearLead} is off. */
	private static java.util.Map<String, String> leadTeam() {
		dev.t3craft.hq.lead.LeadPolicy p = dev.t3craft.hq.lead.LeadPolicy.active();
		if (!p.enabled() || !p.childrenNearLead()) {
			return java.util.Map.of();
		}
		return dev.t3craft.hq.lead.LeadOffices.current().childrenOfLeads();
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
