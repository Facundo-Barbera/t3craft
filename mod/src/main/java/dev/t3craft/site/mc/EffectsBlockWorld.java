package dev.t3craft.site.mc;

import dev.t3craft.site.BlockWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * {@link LevelBlockWorld} that also plays the placement sound and block particles for each write
 * (the same feedback as the builders' {@code ServerLevelSink}), for the paced site builder. Used only
 * for building; restores stay silent. Server thread.
 */
final class EffectsBlockWorld implements BlockWorld {
	private final LevelBlockWorld world;

	EffectsBlockWorld(LevelBlockWorld world) {
		this.world = world;
	}

	@Override
	public String state(int x, int y, int z) {
		return world.state(x, y, z);
	}

	@Override
	public @Nullable CompoundTag blockEntity(int x, int y, int z) {
		return world.blockEntity(x, y, z);
	}

	@Override
	public void set(int x, int y, int z, String state, @Nullable CompoundTag blockEntity) {
		ServerLevel level = world.level();
		BlockPos pos = new BlockPos(x, y, z);
		BlockState before = level.getBlockState(pos);
		world.set(x, y, z, state, blockEntity);
		BlockState now = level.getBlockState(pos);
		if (now == before) {
			return;
		}
		if (now.isAir()) {
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(before));
		} else {
			SoundType sound = now.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, now), x + 0.5, y + 0.5, z + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
		}
	}

	@Override
	public int minBuildY() {
		return world.minBuildY();
	}

	@Override
	public int maxBuildY() {
		return world.maxBuildY();
	}
}
