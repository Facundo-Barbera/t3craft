package dev.t3craft.hq;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One block of a {@link BuildPlan}: a position relative to the plan origin (the hub floor centre),
 * the block state, and optionally the block entity data to load into it after placing (for example
 * a sign's text). Shared contract with the world builder: it adds the origin and places the states
 * in list order, then loads {@code be} with {@code BlockEntity#loadCustomOnly}.
 *
 * <p>{@code be} is never mutated by the generator after the plan is built; appliers must copy it
 * ({@link CompoundTag#copy()}) before handing it to the world.
 */
public record Placement(BlockPos rel, BlockState state, @Nullable CompoundTag be) {
	public Placement {
		Objects.requireNonNull(rel, "rel");
		Objects.requireNonNull(state, "state");
		rel = rel.immutable();
	}

	public Placement(BlockPos rel, BlockState state) {
		this(rel, state, null);
	}

	/** Same block and same block entity data (position ignored). */
	public boolean sameContent(Placement o) {
		return state.equals(o.state) && Objects.equals(be, o.be);
	}
}
