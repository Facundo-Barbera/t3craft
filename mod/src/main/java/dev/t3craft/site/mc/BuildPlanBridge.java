package dev.t3craft.site.mc;

import dev.t3craft.T3Craft;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import dev.t3craft.site.Box;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.ModuleDesigner;
import dev.t3craft.site.SiteFrame;
import dev.t3craft.site.SiteModule;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Converts between the site's {@link CellPlan} and the builders' {@link BuildPlan}
 * ({@code dev.t3craft.build}).
 */
public final class BuildPlanBridge {
	private BuildPlanBridge() {
	}

	/** The block rotation matching a site's facing (plan front = north). */
	public static Rotation rotation(CellPlan.Facing facing) {
		return Rotation.values()[SiteFrame.quarterTurns(facing)];
	}

	/** A module plan cut for the site, with what did not make it (both should be 0). */
	public record Cut(CellPlan plan, int clipped, int dropped) {
	}

	/**
	 * A {@link ModuleDesigner} from module plans in the builders' format. {@code plans} returns, for a
	 * module, a {@link BuildPlan} in <b>site plan-relative</b> coordinates: origin = site origin
	 * (pad centre, {@code y = 0} = first layer above the pad), front = {@code -z}, unrotated, holding
	 * only that module's cells. The bridge turns positions and states (doors, stairs) to the site's
	 * facing ({@link #cut}); anything outside the module area or carrying block entity data (site
	 * plans never carry block entities) is a mistake of the caller and is logged.
	 */
	public static ModuleDesigner designer(Function<SiteModule, BuildPlan> plans) {
		return (module, area, origin) -> {
			Cut c = cut(plans.apply(module), area, origin);
			if (c.clipped() > 0 || c.dropped() > 0) {
				T3Craft.LOGGER.warn("module {}: {} placements outside its area skipped, {} block entities dropped", module.id(), c.clipped(), c.dropped());
			}
			return c.plan();
		};
	}

	/** {@code plan} (site plan-relative) turned to {@code origin} and clipped to {@code area}. */
	public static Cut cut(BuildPlan plan, Box area, CellPlan.Origin origin) {
		CellPlan out = new CellPlan(area, origin);
		Rotation rot = rotation(origin.facing());
		int clipped = 0;
		int dropped = 0;
		for (Placement p : plan.placements()) {
			BlockPos b = p.pos();
			int[] w = SiteFrame.toWorld(origin, b.getX(), b.getY(), b.getZ());
			if (!area.contains(w[0], w[1], w[2])) {
				clipped++;
				continue;
			}
			if (p.hasBlockEntity()) {
				dropped++;
			}
			out.set(w[0], w[1], w[2], LevelBlockWorld.keyOf(p.state().rotate(rot)));
		}
		return new Cut(out, clipped, dropped);
	}

	/**
	 * A site plan as a {@link BuildPlan} relative to its box's minimum corner, bottom layer first,
	 * for builders that want to animate it (place the job at {@code (box.minX, box.minY, box.minZ)}
	 * with {@link Rotation#NONE}). Unset cells are left out.
	 */
	public static BuildPlan toBuildPlan(CellPlan plan, LevelBlockWorld world) {
		Box b = plan.box();
		BuildPlan.Builder out = BuildPlan.builder();
		for (int i = 0; i < plan.size(); i++) {
			String s = plan.get(i);
			if (s != null) {
				BlockState st = world.parse(s);
				out.set(new BlockPos(b.xOf(i) - b.minX(), b.yOf(i) - b.minY(), b.zOf(i) - b.minZ()), st);
			}
		}
		return out.build();
	}
}
