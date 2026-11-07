package dev.t3craft.hq.construct.mc;

import dev.t3craft.build.BlockSink;
import dev.t3craft.build.BuildCursor;
import dev.t3craft.site.mc.LevelBlockWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Writes the in-place changes of the HQ (cells the site already accepted with
 * {@code SiteService.claim}). The default ({@link PacedHqWriter}) writes a few cells per server tick
 * with the builders' sink (sound and particles); the client installs one with the animated crew.
 * Either way every cell goes through {@link #guard}: it is written only while it still holds the
 * state it had when it was claimed (or already the new one), so a block the player put there in the
 * meantime is never replaced.
 */
@FunctionalInterface
public interface HqWriter {
	/**
	 * One cell: the new state (world rotation applied), the state key it must still hold to be
	 * replaced, and an optional block entity (sign text) loaded when the state matches.
	 */
	record Write(BlockPos pos, BlockState state, String before, @Nullable CompoundTag blockEntity) {
	}

	/** Starts writing; {@code done} runs once on the server thread (true = finished, false = cancelled). */
	void write(String label, ServerLevel level, List<Write> writes, Consumer<Boolean> done);

	/** Pause / resume running writes (best effort; default no-op). */
	default void pause(boolean paused) {
	}

	/** A sink wrapper that refuses cells no longer holding {@link Write#before} or the new state. */
	static UnaryOperator<BlockSink> guard(ServerLevel level, List<Write> writes) {
		Map<BlockPos, Write> byPos = new HashMap<>();
		for (Write w : writes) {
			byPos.put(w.pos(), w);
		}
		return inner -> (pos, state, be) -> {
			Write w = byPos.get(pos);
			if (w == null) {
				return BuildCursor.Outcome.SKIPPED;
			}
			BlockState cur = level.getBlockState(pos);
			if (cur != state && !LevelBlockWorld.keyOf(cur).equals(w.before())) {
				return BuildCursor.Outcome.SKIPPED;
			}
			return inner.place(pos, state, be);
		};
	}
}
