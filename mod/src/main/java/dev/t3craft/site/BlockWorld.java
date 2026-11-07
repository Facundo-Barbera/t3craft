package dev.t3craft.site;

import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The only way the site code reads or writes blocks. The Minecraft implementation
 * ({@code dev.t3craft.site.mc.LevelBlockWorld}) wraps a {@code ServerLevel} and must be used on the
 * server thread; tests use an in-memory map.
 */
public interface BlockWorld {
	/** Canonical {@link StateKey} of the block at the position. */
	String state(int x, int y, int z);

	/** Full block-entity NBT (with id; position keys are ignored by comparisons), or null. */
	@Nullable CompoundTag blockEntity(int x, int y, int z);

	/**
	 * Writes the block without neighbour/shape updates, drops or block-entity side effects, then
	 * loads {@code blockEntity} into the new block entity when given. A cell whose state and block
	 * entity already match is left alone.
	 */
	void set(int x, int y, int z, String state, @Nullable CompoundTag blockEntity);

	/** Lowest and highest buildable y (inclusive). */
	int minBuildY();

	int maxBuildY();
}
