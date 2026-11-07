package dev.t3craft.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Server thread (integrated server only): sets every loaded lamp's {@code status} to what its
 * binding wants. The client computes {@code wanted} (binding wire string → status) from the thread
 * states it already has and hands it over with {@code server.execute}; a lamp whose binding is not in
 * the map goes {@link LampStatus#OFF}. Only lamps whose state differs are written, so a steady HQ
 * costs one map lookup per lamp.
 *
 * <p>Same idea as AgentCraft's {@code HqWorldDriver} (client state → block states on the integrated
 * server); this is a new, smaller implementation over {@link LiveLamps}.
 */
public final class LampApplier {
	private LampApplier() {
	}

	/** Effective binding → wanted status ({@code positional}: HQ plan fallback, may be null). */
	public static int apply(Map<String, LampStatus> wanted, @Nullable Map<Long, Binding> positional) {
		int changed = 0;
		for (LampBlockEntity lamp : LiveLamps.snapshot()) {
			Level level = lamp.getLevel();
			if (level == null || level.isClientSide() || lamp.isRemoved()) {
				continue;
			}
			BlockPos pos = lamp.getBlockPos();
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState s = level.getBlockState(pos);
			if (!s.hasProperty(StatusLampBlock.STATUS)) { // status lamp, board bulb or the kit's paper lantern
				continue;
			}
			Binding b = Binding.resolve(lamp.binding(), positional == null ? null : positional.get(pos.asLong()));
			LampStatus want = want(wanted, b);
			if (s.getValue(StatusLampBlock.STATUS) != want) {
				level.setBlock(pos, s.setValue(StatusLampBlock.STATUS, want), Block.UPDATE_CLIENTS);
				changed++;
			}
		}
		return changed;
	}

	/**
	 * What a lamp with binding {@code b} shows: unbound or unknown → OFF; a module bulb → the most
	 * urgent of its threads. Pure.
	 */
	public static LampStatus want(Map<String, LampStatus> wanted, Binding b) {
		if (b.isNone()) {
			return LampStatus.OFF;
		}
		if (b.kind() == Binding.Kind.MODULE) {
			LampStatus best = LampStatus.OFF;
			for (String t : b.moduleThreads()) {
				LampStatus s = wanted.get(Binding.thread(t).wire());
				if (s != null && roomRank(s) > roomRank(best)) {
					best = s;
				}
			}
			return best;
		}
		LampStatus s = wanted.get(b.wire());
		return s == null ? LampStatus.OFF : s;
	}

	/**
	 * Order of a room's (module's) bulb, fix/hq-rooms-real: the room shows what most needs the
	 * player in it, <b>needs you &gt; error &gt; working &gt; done &gt; idle</b>, off when nobody is
	 * there. (Board bulbs keep {@link LampStatus#urgency()}, error first.)
	 */
	public static int roomRank(LampStatus s) {
		return switch (s) {
			case ATTENTION -> 5;
			case ERROR -> 4;
			case WORKING -> 3;
			case DONE -> 2;
			case IDLE -> 1;
			case OFF -> 0;
		};
	}

	/** A room's bulb for the statuses of the threads at its desks (unknown threads count as off). */
	public static LampStatus room(java.util.Collection<LampStatus> threads) {
		LampStatus best = LampStatus.OFF;
		for (LampStatus s : threads) {
			if (s != null && roomRank(s) > roomRank(best)) {
				best = s;
			}
		}
		return best;
	}
}
