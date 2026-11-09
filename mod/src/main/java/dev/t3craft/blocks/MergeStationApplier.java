package dev.t3craft.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Server thread (integrated server only): sets every loaded merge station's {@code pr} state to what its
 * binding wants. The client computes {@code wanted} (binding wire string → state) from the pull requests
 * of the threads it already knows and hands it over with {@code server.execute}; a station whose binding
 * is not in the map goes {@link MergeState#NONE}. Only stations whose state differs are written.
 * Same shape as {@link LampApplier}.
 */
public final class MergeStationApplier {
	private MergeStationApplier() {
	}

	/** Effective binding → wanted state ({@code positional}: HQ plan fallback, may be null). Returns the number of blocks written. */
	public static int apply(Map<String, MergeState> wanted, @Nullable Map<Long, Binding> positional) {
		int changed = 0;
		for (MergeStationBlockEntity station : MergeStationBlockEntity.snapshot()) {
			Level level = station.getLevel();
			if (level == null || level.isClientSide() || station.isRemoved()) {
				continue;
			}
			BlockPos pos = station.getBlockPos();
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState s = level.getBlockState(pos);
			if (!s.hasProperty(MergeStationBlock.PR)) {
				continue;
			}
			Binding b = Binding.resolve(station.binding(), positional == null ? null : positional.get(pos.asLong()));
			MergeState want = want(wanted, b);
			if (s.getValue(MergeStationBlock.PR) != want) {
				level.setBlock(pos, s.setValue(MergeStationBlock.PR, want), Block.UPDATE_CLIENTS);
				changed++;
			}
		}
		return changed;
	}

	/** What a station with binding {@code b} shows: unbound or unknown → NONE. Pure. */
	public static MergeState want(Map<String, MergeState> wanted, Binding b) {
		if (b.isNone()) {
			return MergeState.NONE;
		}
		MergeState s = wanted.get(b.wire());
		return s == null ? MergeState.NONE : s;
	}
}
