package dev.t3craft.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * Places blocks into a {@link ServerLevel} on the server thread, with the placement sound and
 * block particles (sent to every nearby client, so they show in singleplayer and on LAN). Effects are
 * capped per tick for all builders together ({@link BuildBudget#effect()}, {@code build.maxEffectsPerTick}).
 *
 * <p>Default flags are {@link Block#UPDATE_CLIENTS} | {@link Block#UPDATE_KNOWN_SHAPE}: no
 * neighbour shape updates while building, so two-block parts (doors, beds) and attached blocks
 * (torches) placed in plan order do not pop off before their other half or support exists. Pass
 * {@link Block#UPDATE_ALL} for normal block-update behaviour.
 */
public final class ServerLevelSink implements BlockSink {
	public static final int BUILD_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final ServerLevel level;
	private final int flags;
	private final boolean effects;

	public ServerLevelSink(ServerLevel level) {
		this(level, BUILD_FLAGS, true);
	}

	public ServerLevelSink(ServerLevel level, int flags, boolean effects) {
		this.level = level;
		this.flags = flags;
		this.effects = effects;
	}

	@Override
	public boolean matches(BlockPos pos, BlockState state) {
		return level.isLoaded(pos) && level.getBlockState(pos) == state;
	}

	@Override
	public BuildCursor.Outcome place(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
			return BuildCursor.Outcome.FAILED;
		}
		BlockState current = level.getBlockState(pos);
		if (current == state && blockEntity == null) {
			return BuildCursor.Outcome.SKIPPED;
		}
		long t0 = System.nanoTime();
		if (current != state && !level.setBlock(pos, state, flags) && level.getBlockState(pos) != state) {
			return BuildCursor.Outcome.FAILED;
		}
		if (blockEntity != null) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be != null) {
				be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), blockEntity));
				be.setChanged();
				level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			}
		}
		// fix/hq-build-parallel: the setBlock (light, section marking, packet) is what the time budget measures.
		BuildBudget.global().work(System.nanoTime() - t0, 1);
		if (effects && BuildBudget.global().effect()) {
			if (state.isAir()) {
				if (!current.isAir()) {
					level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(current));
				}
			} else {
				SoundType sound = state.getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
			}
		}
		return BuildCursor.Outcome.PLACED;
	}
}
