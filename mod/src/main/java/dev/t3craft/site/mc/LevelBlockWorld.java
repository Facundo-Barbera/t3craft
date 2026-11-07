// Block write flags adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.site.mc;

import dev.t3craft.site.BlockWorld;
import dev.t3craft.site.Canon;
import dev.t3craft.site.StateKey;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * {@link BlockWorld} over a {@link ServerLevel}. Server thread only.
 *
 * <p>Writes use {@link #FLAGS}: clients are told, but no neighbour or shape updates, no drops, no
 * block-entity side effects (containers do not spill) and no {@code onPlace} (no fluid/physics
 * reaction), so restoring a snapshot puts back exactly the stored states. The flag set follows
 * AgentCraft's {@code Plan.FLAGS} (MIT, see THIRD_PARTY_NOTICES.md).
 */
public final class LevelBlockWorld implements BlockWorld {
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
			| Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

	private final ServerLevel level;
	private final Map<BlockState, String> keys = new IdentityHashMap<>();
	private final Map<String, BlockState> states = new HashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public LevelBlockWorld(ServerLevel level) {
		this.level = level;
	}

	public ServerLevel level() {
		return level;
	}

	public String key(BlockState s) {
		return keys.computeIfAbsent(s, LevelBlockWorld::keyOf);
	}

	/** Canonical {@link StateKey} of a block state (uncached). */
	public static String keyOf(BlockState s) {
		return StateKey.fromTag(NbtUtils.writeBlockState(s));
	}

	/** Parses a key; unknown blocks are an error (never silently air). */
	public BlockState parse(String key) {
		BlockState s = states.get(key);
		if (s != null) {
			return s;
		}
		s = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), StateKey.toTag(key));
		String name = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
		if (!name.equals(StateKey.name(key))) {
			throw new IllegalArgumentException("unknown block state " + key);
		}
		states.put(key, s);
		return s;
	}

	@Override
	public String state(int x, int y, int z) {
		return key(level.getBlockState(pos.set(x, y, z)));
	}

	@Override
	public @Nullable CompoundTag blockEntity(int x, int y, int z) {
		BlockEntity be = level.getBlockEntity(pos.set(x, y, z));
		return be == null ? null : be.saveWithFullMetadata(level.registryAccess());
	}

	@Override
	public void set(int x, int y, int z, String state, @Nullable CompoundTag blockEntity) {
		BlockState want = parse(state);
		BlockPos p = new BlockPos(x, y, z);
		BlockState cur = level.getBlockState(p);
		BlockEntity curBe = level.getBlockEntity(p);
		if (cur == want && Canon.sameBlockEntity(curBe == null ? null : curBe.saveWithFullMetadata(level.registryAccess()), blockEntity)) {
			return;
		}
		if (curBe != null || blockEntity != null) {
			// Drop the old block entity first so the stored one is loaded into a fresh instance.
			level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
		}
		level.setBlock(p, want, FLAGS);
		if (blockEntity != null) {
			BlockEntity be = level.getBlockEntity(p);
			if (be != null) {
				be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), blockEntity));
				be.setChanged();
				level.sendBlockUpdated(p, want, want, Block.UPDATE_CLIENTS);
			}
		}
	}

	@Override
	public int minBuildY() {
		return level.getMinY();
	}

	@Override
	public int maxBuildY() {
		return level.getMaxY();
	}
}
