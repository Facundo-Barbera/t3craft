package dev.t3craft.client.npc.behavior;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.construct.HqSiteFrame;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * A fake level with the real HQ in it, the way the site builds it in a superflat world: grass top at
 * {@link #GRASS_Y} (feet at {@code GRASS_Y + 1}), the pad at the same height, and the HQ plan's cells
 * from its floor ({@code y = 0}) up, at {@code site.cell(rel)} with the state turned by the site.
 */
final class HqWorld implements BlockGetter {
	static final int GRASS_Y = -61;
	private static boolean booted;

	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	final BuildPlan plan;
	final HqSite site;

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	/** {@code projects} projects with {@code threads} threads each ("env:p0-t0"...). */
	static BuildPlan plan(int projects, int threads) {
		boot();
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			b.project("env:p" + p, "Project " + p, "env", "env");
			for (int t = 0; t < threads; t++) {
				b.thread("env:p" + p, "Project " + p, "env", "env", "env:p" + p + "-t" + t);
			}
		}
		return HqPlans.plan(b.build(), null).plan();
	}

	HqWorld(BuildPlan plan, HqSite site, int radius) {
		boot();
		this.plan = plan;
		this.site = site;
		BlockPos o = site.origin();
		for (int x = o.getX() - radius; x <= o.getX() + radius; x++) {
			for (int z = o.getZ() - radius; z <= o.getZ() + radius; z++) {
				blocks.put(new BlockPos(x, GRASS_Y, z), Blocks.GRASS_BLOCK.defaultBlockState());
				blocks.put(new BlockPos(x, GRASS_Y - 1, z), Blocks.DIRT.defaultBlockState());
			}
		}
		for (Placement p : plan.placements()) {
			if (!HqSiteFrame.builds(p.rel()) || HqSiteFrame.owner(p.rel().getX(), p.rel().getZ()) == null) {
				continue;
			}
			BlockPos w = site.cell(p.rel());
			BlockState s = p.state().rotate(site.rotation());
			if (s.isAir()) {
				blocks.remove(w);
			} else {
				blocks.put(w, s);
			}
		}
	}

	static HqSite site(int x, int y, int z, Rotation r) {
		return new HqSite(new BlockPos(x, y, z), r);
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
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
		return -64;
	}
}
