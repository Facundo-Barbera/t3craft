package dev.t3craft.hq;

import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import dev.t3craft.hq.plan.PlanApplier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The common HQ reconcile step, shared by the demo and the site HQ (feat/hq-construction): what to
 * place <b>and what to take away</b> to turn a world that holds {@code previous} into {@code next}.
 *
 * <ul>
 *   <li><b>Removals</b>: every cell {@code previous} built that {@code next} no longer has (a removed
 *   module, the spine row it needed) is restored with a {@link BuildPlan.Vacated} (air or the plan's
 *   ground; on a survival site, the site snapshot: see {@link #restoring}); and every cell that
 *   {@code next} turns from a block into air (a desk whose thread released it). Grouped by the group
 *   that owned the cell in {@code previous}; within a group in reverse plan order, so decorations and
 *   hanging things go before what holds them.</li>
 *   <li><b>Builds</b>: every other placement of {@code next} whose content changed, by {@code next}'s
 *   group, in plan order.</li>
 * </ul>
 * Same roster, same building: {@code between(p, p, ...)} is empty. Pure.
 *
 * @param removals group → placements that clear cells (apply first)
 * @param builds   group → placements that add or change blocks
 */
public record HqDiff(Map<String, List<Placement>> removals, Map<String, List<Placement>> builds) {
	/** Group used for cells no module owns (should not happen with generated plans). */
	public static final String LOOSE = "vacated";

	public HqDiff {
		removals = copy(removals);
		builds = copy(builds);
	}

	public static final HqDiff NONE = new HqDiff(Map.of(), Map.of());

	public static HqDiff between(@Nullable BuildPlan previous, BuildPlan next, BuildPlan.Vacated vacated) {
		Map<String, List<Placement>> removals = new LinkedHashMap<>();
		Map<String, List<Placement>> builds = new LinkedHashMap<>();
		if (previous != null) {
			List<Placement> prev = previous.placements();
			for (int i = prev.size() - 1; i >= 0; i--) {
				Placement p = prev.get(i);
				Placement n = next.at(p.rel());
				if (n == null) {
					BlockState s = vacated.at(p.rel());
					if (!p.state().equals(s) || p.be() != null) {
						removals.computeIfAbsent(group(previous, p.rel()), k -> new ArrayList<>()).add(new Placement(p.rel(), s));
					}
				} else if (n.state().isAir() && !p.state().isAir()) {
					removals.computeIfAbsent(group(previous, p.rel()), k -> new ArrayList<>()).add(n);
				}
			}
		}
		for (Placement n : next.placements()) {
			Placement old = previous == null ? null : previous.at(n.rel());
			if (old != null && n.state().isAir() && !old.state().isAir()) {
				continue; // listed as a removal
			}
			if (old == null || !old.sameContent(n)) {
				builds.computeIfAbsent(group(next, n.rel()), k -> new ArrayList<>()).add(n);
			}
		}
		return new HqDiff(removals, builds);
	}

	public boolean isEmpty() {
		return removals.isEmpty() && builds.isEmpty();
	}

	public int removedCells() {
		return removals.values().stream().mapToInt(List::size).sum();
	}

	public int builtCells() {
		return builds.values().stream().mapToInt(List::size).sum();
	}

	/** Groups of {@code previous} that {@code next} dropped entirely (their every cell is a removal). */
	public static List<String> removedGroups(@Nullable BuildPlan previous, BuildPlan next) {
		List<String> out = new ArrayList<>();
		if (previous != null) {
			List<String> keep = next.groups();
			for (String g : previous.groups()) {
				if (!keep.contains(g)) {
					out.add(g);
				}
			}
		}
		return out;
	}

	/** Every placement: removals first, then builds (one flat list, e.g. for {@link PlanApplier}). */
	public List<Placement> placements() {
		List<Placement> out = new ArrayList<>();
		removals.values().forEach(out::addAll);
		builds.values().forEach(out::addAll);
		return out;
	}

	/**
	 * The parts to hand the builders, one per group: {@code remove:<group>} parts first (in the
	 * order they must run: top-down, as listed), then the build parts (layered).
	 */
	public Map<String, dev.t3craft.build.BuildPlan> parts() {
		Map<String, dev.t3craft.build.BuildPlan> out = new LinkedHashMap<>();
		removals.forEach((g, ps) -> out.put(REMOVE_PREFIX + g, HqBuild.toBuildPlan(ps, HqBuild.Order.MODULES)));
		builds.forEach((g, ps) -> out.put(g, HqBuild.toBuildPlan(ps)));
		return out;
	}

	public static final String REMOVE_PREFIX = "remove:";

	/**
	 * A vacated rule that puts back what was there before the HQ, falling back to {@code fallback}
	 * where {@code original} knows nothing (returns null). For the survival site: {@code original}
	 * reads the cell from the site's snapshot ({@code dev.t3craft.site.Snapshot}, HQ-relative cell
	 * → world cell via {@link HqSite#cell}), so a later {@code site undo} still finds "ours" cells and
	 * the player's changes are left alone.
	 */
	public static BuildPlan.Vacated restoring(Function<BlockPos, @Nullable BlockState> original, BuildPlan.Vacated fallback) {
		return rel -> {
			BlockState s = original.apply(rel);
			return s != null ? s : fallback.at(rel);
		};
	}

	private static String group(BuildPlan plan, BlockPos rel) {
		String g = plan.groupAt(rel);
		return g == null ? LOOSE : g;
	}

	private static Map<String, List<Placement>> copy(Map<String, List<Placement>> m) {
		Map<String, List<Placement>> out = new LinkedHashMap<>();
		m.forEach((k, v) -> out.put(k, List.copyOf(v)));
		return java.util.Collections.unmodifiableMap(out);
	}
}
