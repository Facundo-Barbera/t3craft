package dev.t3craft.client.schedule;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.client.npc.behavior.RingLayout;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Provisional placement until the HQ registers its board: the panel floats behind the provisional
 * board spot of the behaviour director's {@link RingLayout} (where NPCs that need attention gather),
 * facing them. Same origin and members as the director, so it lines up with the NPCs. Null while no
 * NPC is shown (no layout origin yet).
 */
public final class RingBoardPlacement implements SchedulePlacementSource {
	/** Blocks behind the board spot. */
	public static final double DISTANCE = 2.5;
	/** Text bottom above the board spot's floor. */
	public static final double HEIGHT = 1.2;

	private final Supplier<@Nullable Pos> origin;
	private final Supplier<List<RingLayout.Member>> members;
	private @Nullable Pos lastOrigin;
	private @Nullable List<RingLayout.Member> lastMembers;
	private @Nullable SchedulePlacement cached;

	public RingBoardPlacement(Supplier<@Nullable Pos> origin, Supplier<List<RingLayout.Member>> members) {
		this.origin = origin;
		this.members = members;
	}

	@Override
	public @Nullable SchedulePlacement placement() {
		Pos o = origin.get();
		if (o == null) {
			lastOrigin = null;
			cached = null;
			return null;
		}
		List<RingLayout.Member> ms = members.get();
		if (cached == null || !o.equals(lastOrigin) || !Objects.equals(ms, lastMembers)) {
			Anchor board = RingLayout.of(o, ms).board();
			cached = board == null ? null : at(board);
			lastOrigin = o;
			lastMembers = ms;
		}
		return cached;
	}

	static SchedulePlacement at(Anchor board) {
		Pos p = board.pos();
		return SchedulePlacement.inFrontOf(p.x(), p.y(), p.z(), board.yaw(), DISTANCE, HEIGHT);
	}
}
