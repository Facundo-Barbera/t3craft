package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-spawn-start: the centre of the HQ in world coordinates, where the player spawns. The hub
 * ({@link AnchorNames#HUB}, centre of the hub floor) of the layout's empty plan, else its
 * {@link AnchorNames#SPAWN}; a spot is only used when the plan keeps the feet and head cells free.
 *
 * @param feet      the world cell the player's feet stand in
 * @param floor     the cell under the feet (the hub floor once the plan builds it)
 * @param planFloor the plan's own block for {@code floor} (rotated to the world), when it is a plain
 *                  full block the player can stand on; null otherwise
 * @param yaw       the spawn's yaw (the anchor's facing in the world: into the HQ)
 */
record HqCentre(BlockPos feet, BlockPos floor, @Nullable BlockState planFloor, float yaw) {
	/** Placed under the feet when the plan has no plain full block there; the plan's own floor replaces it. */
	static final BlockState NEUTRAL = Blocks.SMOOTH_STONE.defaultBlockState();

	HqCentre {
		feet = feet.immutable();
		floor = floor.immutable();
	}

	/** The single block placed under the spawn: the plan's own floor block if it has one there, else {@link #NEUTRAL}. */
	BlockState floorBlock() {
		return planFloor != null ? planFloor : NEUTRAL;
	}

	/** The feet cell as a marker cell. */
	DemoMarker.Cell cell() {
		return new DemoMarker.Cell(feet.getX(), feet.getY(), feet.getZ());
	}

	/** The centre of layout {@code layoutId}'s HQ at the demo site; null when no plan spot fits (callers keep the old spawn). */
	static @Nullable HqCentre of(DemoWorldSpec spec, int worldMinY, String layoutId) {
		try {
			dev.t3craft.hq.BuildPlan empty = dev.t3craft.hq.HqPlans.generate(dev.t3craft.hq.HqLayout.EMPTY,
					dev.t3craft.hq.HqStyles.forLayout(layoutId));
			int lift = DemoHq.lift(spec.originY(), worldMinY, empty.footprint().minY());
			HqSite site = DemoHq.worldSite(new BlockPos(spec.originX(), spec.originY(), spec.originZ()), lift);
			for (String name : List.of(AnchorNames.HUB, AnchorNames.SPAWN)) {
				Anchor a = empty.anchor(name);
				if (a == null || !free(empty, a.block()) || !free(empty, a.block().above())) {
					continue;
				}
				BlockPos floorRel = a.block().below();
				Placement p = empty.at(floorRel);
				BlockState own = p != null && standable(p) ? p.state().rotate(site.rotation()) : null;
				return new HqCentre(site.cell(a.block()), site.cell(floorRel), own, site.direction(a.facing()).toYRot());
			}
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("[demo] HQ centre unknown for layout {} ({}), spawn left where it was", layoutId, e.getClass().getSimpleName());
		}
		return null;
	}

	/** No collision in plan cell {@code rel} (nothing there, or something the body passes through). */
	private static boolean free(dev.t3craft.hq.BuildPlan plan, BlockPos rel) {
		Placement p = plan.at(rel);
		return p == null || p.state().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** A plain full block without block entity data: safe to place early, the builders then skip it as already there. */
	private static boolean standable(Placement p) {
		BlockState s = p.state();
		return p.be() == null && !s.isAir() && !s.liquid() && !s.hasBlockEntity()
				&& s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}
}
