package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Simple entry points for consumers that just want buildable plans (the demo world, the builders).
 * All plans are {@link dev.t3craft.build.BuildPlan}s relative to the fixed HQ origin (the hub floor
 * centre; {@code y = 0} is the floor layer, the basement goes down to {@code y = -5}); build them with
 * the {@code origin}/{@code rotation} of an {@link HqSite}.
 *
 * <pre>{@code
 * List<dev.t3craft.build.BuildPlan> parts = HqPlans.forRoster(roster);   // hub, archive, portico, spine..., module_0...
 * for (var p : parts) Builders.service().build("HQ", dim, site.origin(), site.rotation(), p, 40, 3);
 * }</pre>
 */
public final class HqPlans {
	private HqPlans() {
	}

	/** The whole HQ of a roster, one layered plan per group, in build order. Order of the roster does not matter. */
	public static List<dev.t3craft.build.BuildPlan> forRoster(Collection<HqInput.Entry> roster) {
		return List.copyOf(byGroup(generate(HqLayout.plan(HqInput.fromRoster(roster)))).values());
	}

	/**
	 * The whole HQ of a roster with {@code site} baked in: positions are {@code site.cell(rel)} and states
	 * are rotated, so the plans are built with origin {@code (0,0,0)} and rotation {@code NONE}. One
	 * layered plan per group, in build order.
	 */
	public static List<dev.t3craft.build.BuildPlan> forRoster(Collection<HqInput.Entry> roster, HqSite site) {
		BuildPlan plan = generate(HqLayout.plan(HqInput.fromRoster(roster)));
		List<dev.t3craft.build.BuildPlan> out = new ArrayList<>();
		for (String g : plan.groups()) {
			List<dev.t3craft.build.Placement> ps = new ArrayList<>();
			for (Placement p : plan.placements(g)) {
				ps.add(new dev.t3craft.build.Placement(site.cell(p.rel()), p.state().rotate(site.rotation()), p.be()));
			}
			out.add(dev.t3craft.build.BuildPlan.of(ps).layered());
		}
		return List.copyOf(out);
	}

	/**
	 * The site that puts the HQ in the builders' demo convention: entrance facing north ({@code -z}),
	 * the front edge of the portico on {@code z = 0}, growing towards {@code +z}, {@code x = 0} the
	 * centre line. {@code y = 0} stays the hub floor; the basement goes down to {@code y = -5}, so the
	 * demo origin must be at least 6 blocks above the world's minimum height.
	 */
	public static HqSite demoSite() {
		return new HqSite(new net.minecraft.core.BlockPos(0, 0, HqGeometry.PORTICO_Z2), net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
	}

	/** {@link #forRoster(Collection, HqSite)} with {@link #demoSite()}: plug into the demo world's structure source. */
	public static List<dev.t3craft.build.BuildPlan> forDemo(Collection<HqInput.Entry> roster) {
		return forRoster(roster, demoSite());
	}

	/** The HQ plan of a layout. */
	public static BuildPlan generate(HqLayout layout) {
		return new HqGenerator().generate(layout);
	}

	/** Every group of {@code plan} as its own layered build plan, keyed by group, in build order. */
	public static Map<String, dev.t3craft.build.BuildPlan> byGroup(BuildPlan plan) {
		Map<String, List<Placement>> parts = new LinkedHashMap<>();
		for (String g : plan.groups()) {
			parts.put(g, new ArrayList<>());
		}
		for (Placement p : plan.placements()) {
			String g = plan.groupAt(p.rel());
			if (g != null) {
				parts.get(g).add(p);
			}
		}
		Map<String, dev.t3craft.build.BuildPlan> out = new LinkedHashMap<>();
		parts.forEach((g, ps) -> out.put(g, HqBuild.toBuildPlan(ps)));
		return out;
	}

	/**
	 * One expansion step from {@code previous} to {@code next}, split by group so each part can be
	 * snapshotted and undone on its own: only the groups whose cells changed appear. Cells that
	 * {@code next} no longer uses are vacated with {@code vacated} and belong to the group that owned
	 * them in {@code previous}.
	 */
	public static Map<String, dev.t3craft.build.BuildPlan> expansion(@Nullable BuildPlan previous, BuildPlan next, BuildPlan.Vacated vacated) {
		Map<String, List<Placement>> parts = new LinkedHashMap<>();
		for (Placement p : BuildPlan.diff(previous, next, vacated)) {
			String g = next.groupAt(p.rel());
			if (g == null && previous != null) {
				g = previous.groupAt(p.rel());
			}
			parts.computeIfAbsent(g == null ? "vacated" : g, k -> new ArrayList<>()).add(p);
		}
		Map<String, dev.t3craft.build.BuildPlan> out = new LinkedHashMap<>();
		parts.forEach((g, ps) -> out.put(g, HqBuild.toBuildPlan(ps)));
		return out;
	}
}
