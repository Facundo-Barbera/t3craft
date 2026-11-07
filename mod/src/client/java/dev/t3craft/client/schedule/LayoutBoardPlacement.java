package dev.t3craft.client.schedule;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.AnchorSource;
import dev.t3craft.client.npc.behavior.Pos;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Provisional placement until the HQ registers its board: the panel floats behind the board spot of
 * the registry's fallback layout ({@code NpcRegistry.fallbackLayout()}, the same
 * {@link AnchorSource} the behaviour director and the provisional workstations use), facing the
 * NPCs that gather there. Null while that layout has no board (no NPC shown yet).
 */
public final class LayoutBoardPlacement implements SchedulePlacementSource {
	/** Blocks behind the board spot. */
	public static final double DISTANCE = 2.5;
	/** Text bottom above the board spot's floor. */
	public static final double HEIGHT = 1.2;

	private final Supplier<@Nullable AnchorSource> layout;

	public LayoutBoardPlacement(Supplier<@Nullable AnchorSource> layout) {
		this.layout = layout;
	}

	@Override
	public @Nullable SchedulePlacement placement() {
		AnchorSource l = layout.get();
		Anchor board = l == null ? null : l.board();
		return board == null ? null : at(board);
	}

	static SchedulePlacement at(Anchor board) {
		Pos p = board.pos();
		return SchedulePlacement.inFrontOf(p.x(), p.y(), p.z(), board.yaw(), DISTANCE, HEIGHT);
	}
}
