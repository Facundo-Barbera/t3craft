package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link BlockShapes} over the client level: the real {@code getCollisionShape} boxes of each
 * block. Doors that open by hand (not iron) and fence gates count as open, i.e. passable; liquids,
 * fire and powder snow are hazards. Unloaded chunks read as air (no ground, so not walkable).
 */
final class LevelBlocks implements BlockShapes {
	private final BlockGetter level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	LevelBlocks(BlockGetter level) {
		this.level = level;
	}

	@Override
	public List<Box> boxes(int x, int y, int z) {
		cursor.set(x, y, z);
		BlockState s = level.getBlockState(cursor);
		if (s.isAir()) {
			return List.of();
		}
		if (s.getBlock() instanceof DoorBlock d && d.type().canOpenByHand() || s.getBlock() instanceof FenceGateBlock) {
			return List.of();
		}
		VoxelShape shape = s.getCollisionShape(level, cursor);
		if (shape.isEmpty()) {
			return List.of();
		}
		List<AABB> aabbs = shape.toAabbs();
		List<Box> out = new ArrayList<>(aabbs.size());
		for (AABB a : aabbs) {
			out.add(new Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
		}
		return out;
	}

	@Override
	public boolean liquid(int x, int y, int z) {
		cursor.set(x, y, z);
		BlockState s = level.getBlockState(cursor);
		return s.getBlock() instanceof LiquidBlock || s.getBlock() instanceof BaseFireBlock || s.getBlock() instanceof PowderSnowBlock;
	}
}
