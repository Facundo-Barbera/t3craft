package dev.t3craft.site.mc;

import dev.t3craft.site.Box;
import dev.t3craft.site.TerrainProbe;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * {@link TerrainProbe} over a {@link ServerLevel}. Never loads or generates chunks: a column counts
 * as loaded only if its chunk is already fully loaded. Classification is conservative: anything not
 * recognised as natural terrain or vegetation is {@link Kind#ARTIFICIAL} and makes a site refused.
 */
public final class LevelTerrain implements TerrainProbe {
	private final ServerLevel level;
	private final Map<BlockState, Kind> kinds = new IdentityHashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public LevelTerrain(ServerLevel level) {
		this.level = level;
	}

	@Override
	public boolean loaded(int x, int z) {
		return level.hasChunk(x >> 4, z >> 4);
	}

	@Override
	public int surfaceY(int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
	}

	@Override
	public Kind kind(int x, int y, int z) {
		return kinds.computeIfAbsent(level.getBlockState(pos.set(x, y, z)), LevelTerrain::classify);
	}

	@Override
	public String describe(int x, int y, int z) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock()).getPath();
	}

	@Override
	public int minBuildY() {
		return level.getMinY();
	}

	@Override
	public int maxBuildY() {
		return level.getMaxY();
	}

	@Override
	public List<String> foreignEntities(Box b) {
		AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
		List<String> out = new ArrayList<>();
		for (Entity e : level.getEntities((Entity) null, box, LevelTerrain::foreign)) {
			out.add(EntityType.getKey(e.getType()).getPath() + " at " + e.blockPosition().toShortString());
		}
		return out;
	}

	/** Not a plain wild mob, item, orb or player: item frames, armor stands, minecarts, boats, named/tamed/persistent mobs… */
	static boolean foreign(Entity e) {
		if (e instanceof Player || e instanceof ItemEntity || e instanceof ExperienceOrb) {
			return false;
		}
		if (e instanceof Mob m) {
			return m.isPersistenceRequired() || m.hasCustomName() || (m instanceof TamableAnimal t && t.isTame());
		}
		return true;
	}

	static Kind classify(BlockState s) {
		if (s.isAir()) {
			return Kind.AIR;
		}
		FluidState f = s.getFluidState();
		if (!f.isEmpty()) {
			return f.is(FluidTags.LAVA) ? Kind.LAVA : Kind.WATER;
		}
		Block b = s.getBlock();
		if (s.is(BlockTags.OVERWORLD_NATURAL_LOGS) || s.is(BlockTags.LEAVES) || b == Blocks.BEE_NEST || b == Blocks.MUSHROOM_STEM
				|| b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK) {
			return Kind.TREE;
		}
		if (b instanceof VegetationBlock || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS)
				|| s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CAVE_VINES) || b == Blocks.SNOW || b == Blocks.VINE || b == Blocks.SUGAR_CANE
				|| b == Blocks.CACTUS || b == Blocks.BAMBOO || b == Blocks.PUMPKIN || b == Blocks.MELON || b == Blocks.GLOW_LICHEN
				|| b == Blocks.MOSS_CARPET || b == Blocks.POINTED_DRIPSTONE || b == Blocks.SWEET_BERRY_BUSH) {
			return Kind.PLANT;
		}
		if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SUBSTRATE_OVERWORLD)
				|| s.is(BlockTags.BADLANDS_TERRACOTTA) || s.is(BlockTags.ORES) || s.is(BlockTags.ICE) || s.is(BlockTags.MUD)
				|| s.is(BlockTags.MOSS_BLOCKS) || s.is(BlockTags.GRASS_BLOCKS) || b == Blocks.GRAVEL || b == Blocks.CLAY
				|| b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE
				|| b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK || b == Blocks.BEDROCK || b == Blocks.TERRACOTTA) {
			return Kind.GROUND;
		}
		return Kind.ARTIFICIAL;
	}
}
