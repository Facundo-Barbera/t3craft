package dev.t3craft.client.npc.behavior;

import dev.t3craft.build.Placement;
import dev.t3craft.client.demo.DemoHq;
import dev.t3craft.client.demo.DemoWorldSpec;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * fix/hq-npc-spawn: the HQ world exactly as the demo builds it, in memory. A default superflat
 * world (bedrock at y = -64, dirt, grass top at y = -61) with the parts of {@link DemoHq#full}
 * (plinth plus every group of the plan, baked at {@link DemoHq#bakeSite}) placed at the demo origin
 * of {@link DemoWorldSpec#defaults()}, the way {@code DemoHqSource.plans()} hands them to the
 * builders. Works for every layout: the plan comes from the active one ({@link HqPlans}).
 */
final class DemoHqWorld implements BlockGetter {
	static final int MIN_Y = -64;
	static final int GRASS_Y = -61;

	private final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
	final BuildPlan plan;
	final int lift;
	final HqSite site;
	final BlockPos demoOrigin;

	DemoHqWorld(BuildPlan plan) {
		HqWorld.boot();
		this.plan = plan;
		DemoWorldSpec spec = DemoWorldSpec.defaults();
		this.demoOrigin = new BlockPos(spec.originX(), spec.originY(), spec.originZ());
		int planMinY = HqPlans.generate(HqLayout.EMPTY).footprint().minY();
		this.lift = DemoHq.lift(spec.originY(), MIN_Y, planMinY);
		this.site = DemoHq.worldSite(demoOrigin, lift);
		for (dev.t3craft.build.BuildPlan part : DemoHq.full(plan, lift)) {
			for (Placement p : part.placements()) {
				placed.put(demoOrigin.offset(p.pos()).asLong(), p.state());
			}
		}
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState s = placed.get(pos.asLong());
		if (s != null) {
			return s;
		}
		int y = pos.getY();
		if (y == GRASS_Y) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		if (y < GRASS_Y && y > MIN_Y) {
			return Blocks.DIRT.defaultBlockState();
		}
		return y == MIN_Y ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public int getHeight() {
		return 384;
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}
}
