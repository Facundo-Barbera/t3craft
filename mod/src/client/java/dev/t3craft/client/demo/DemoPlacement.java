package dev.t3craft.client.demo;

/** Where things go around the demo origin (pure, unit tested). */
public final class DemoPlacement {
	/** Blocks in front of the plan front (toward {@code -z}, where the plan's door faces). */
	public static final int VIEW_DISTANCE = 10;

	private DemoPlacement() {
	}

	/** Spawn / first-teleport spot: in front of the site, on the same level as the origin. */
	public static int[] viewpoint(DemoWorldSpec spec) {
		return new int[] {spec.originX(), spec.originY(), spec.originZ() - VIEW_DISTANCE};
	}

	/**
	 * fix/hq-npc-spawn: the viewpoint for the active HQ layout. Classic keeps {@link #viewpoint(DemoWorldSpec)}
	 * (in front of its portico). A layout with its own ground plan (campus, ring, vertical, ensanche,
	 * village) spreads far beyond the classic portico line, so that spot falls inside or under the
	 * building (in the ring: in the cloister's footing, under the hub's podium), and so did the NPCs'
	 * provisional grid around the player. Those layouts use their plan's {@code spawn} anchor at the
	 * demo site instead: a free, standable cell every desk is reachable from (checked by the plan
	 * validators and {@code DemoHqReachabilityTest}).
	 */
	public static int[] viewpoint(DemoWorldSpec spec, int worldMinY) {
		dev.t3craft.hq.HqStyle style = dev.t3craft.hq.HqStyles.current();
		if (!style.ownLayout()) {
			return viewpoint(spec);
		}
		try {
			dev.t3craft.hq.BuildPlan empty = dev.t3craft.hq.HqPlans.generate(dev.t3craft.hq.HqLayout.EMPTY);
			dev.t3craft.hq.Anchor spawn = empty.anchor(dev.t3craft.hq.AnchorNames.SPAWN);
			if (spawn == null) {
				return viewpoint(spec);
			}
			int lift = DemoHq.lift(spec.originY(), worldMinY, empty.footprint().minY());
			net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(spec.originX(), spec.originY(), spec.originZ());
			net.minecraft.core.BlockPos cell = DemoHq.worldSite(origin, lift).cell(spawn.block());
			return new int[] {cell.getX(), cell.getY(), cell.getZ()};
		} catch (RuntimeException e) {
			return viewpoint(spec);
		}
	}
}
