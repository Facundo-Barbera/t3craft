package dev.t3craft.hq;

import dev.t3craft.hq.plan.AnchorNames;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import dev.t3craft.hq.plan.PlanShapes;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridge from the HQ plan to the builders ({@code dev.t3craft.build}): turns HQ placements into a
 * {@link dev.t3craft.build.BuildPlan} that a {@code BuildService.build(label, dim, origin, rotation,
 * plan, ...)} call places. Coordinates stay relative to the HQ origin (hub floor centre); the builder
 * adds the origin and the rotation, exactly like {@link HqSite} does for anchors.
 *
 * <pre>{@code
 * HqGenerator.Result r = HqGenerator.plan(input, lastLayout);
 * HqSite site = HqSite.at(r.plan(), AnchorNames.SPAWN, chosenCell, Direction.SOUTH);
 * var full  = HqBuild.toBuildPlan(r.plan());                         // first build
 * var annex = HqBuild.toBuildPlan(BuildPlan.diff(lastPlan, r.plan(), HqBuild.AIR_ABOVE_GROUND)); // later
 * Builders.service().build("HQ", dim, site.origin(), site.rotation(), annex, 40, 3);
 * }</pre>
 *
 * <p>Shapes (panes, walls, stair corners) are already baked into the states (see
 * {@link PlanShapes}), so the builders' no-neighbour-update placement gives the final look without a
 * post-pass. Cells of the plan that hold air are kept: they clear the terrain inside the HQ.
 */
public final class HqBuild {
	/** Vacated cells become air above the floor and grass at and below it (for {@link BuildPlan#diff}). */
	public static final BuildPlan.Vacated AIR_ABOVE_GROUND = rel -> rel.getY() <= 0
			? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
			: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

	/** How the converted plan is ordered. */
	public enum Order {
		/**
		 * Bottom layer first, snaking inside each layer ({@code dev.t3craft.build.Layers}): the look
		 * of a building rising floor by floor. Default.
		 */
		LAYERS,
		/** The HQ plan's own order: module by module (hub, portico, spine, wings, desks). */
		MODULES
	}

	private HqBuild() {
	}

	/** The whole plan, ordered by layers. */
	public static dev.t3craft.build.BuildPlan toBuildPlan(BuildPlan plan) {
		return toBuildPlan(plan.placements(), Order.LAYERS);
	}

	/** Some placements (a whole plan or a {@link BuildPlan#diff}), ordered by layers. */
	public static dev.t3craft.build.BuildPlan toBuildPlan(List<Placement> placements) {
		return toBuildPlan(placements, Order.LAYERS);
	}

	public static dev.t3craft.build.BuildPlan toBuildPlan(List<Placement> placements, Order order) {
		List<dev.t3craft.build.Placement> out = new ArrayList<>(placements.size());
		for (Placement p : placements) {
			out.add(new dev.t3craft.build.Placement(p.rel(), p.state(), p.be()));
		}
		dev.t3craft.build.BuildPlan plan = dev.t3craft.build.BuildPlan.of(out);
		return order == Order.LAYERS ? plan.layered() : plan;
	}
}
