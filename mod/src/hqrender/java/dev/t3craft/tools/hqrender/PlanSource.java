package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.HqStyles;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.List;

/**
 * The plan a piece view or a report looks at: a registered layout grown through a chain of roster
 * sizes, exactly as the full galleries build it ({@link RingCampusViews} for the citadel,
 * {@link LayoutViews} for the other layouts), so a signature taken here is the one of the plan the
 * gallery draws. Minecraft's registries must be up first ({@link HqRender#bootstrap}).
 */
public final class PlanSource {
	private PlanSource() {
	}

	/** The id of the citadel, the default layout. */
	public static final String DEFAULT_LAYOUT = RingCampusGeometry.ID;
	/** Roster sizes used when none are given. */
	public static final List<Integer> DEFAULT_SIZES = List.of(10);

	/**
	 * @param layout the layout of the last size (modules, allocation)
	 * @param label  e.g. {@code hybrid-ringcampus, 10 projects}
	 */
	public record Planned(BuildPlan plan, HqLayout layout, String label) {
	}

	/**
	 * Generates the plan of {@code layoutId} (null = the citadel) for the last of {@code sizes}, each
	 * roster allocated against the previous layout (a chain such as {@code 4, 10, 20} grows the HQ the
	 * way the game does; a single size starts from nothing). Empty or null sizes = {@link #DEFAULT_SIZES}.
	 */
	public static Planned plan(String layoutId, List<Integer> sizes) {
		String id = layoutId == null ? DEFAULT_LAYOUT : layoutId;
		List<Integer> chain = sizes == null || sizes.isEmpty() ? DEFAULT_SIZES : sizes;
		String label = id + ", " + chain.get(chain.size() - 1) + " projects";
		HqLayout layout = null;
		BuildPlan plan = null;
		if (id.equals(RingCampusGeometry.ID)) {
			HqStyle style = HqStyles.byId(RingCampusGeometry.ID);
			for (int n : chain) {
				HqGenerator.Result r = HqGenerator.plan(Growth.roster(n), layout, style);
				layout = r.layout();
				plan = HqFixtures.active().apply(r.plan());
			}
			return new Planned(plan, layout, label);
		}
		HqLayoutInfo info = HqLayouts.find(id).orElseThrow(() -> new IllegalArgumentException(
				"unknown layout " + id + "; known: " + HqLayouts.all().stream().map(HqLayoutInfo::id).toList()));
		HqLayouts.use(info.id());
		try {
			for (int n : chain) {
				HqGenerator.Result r = HqPlans.plan(CampusViews.roster(n), layout);
				layout = r.layout();
				plan = r.plan();
			}
		} finally {
			HqLayouts.use(null);
		}
		return new Planned(plan, layout, label);
	}
}
