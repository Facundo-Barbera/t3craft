package dev.t3craft.client.schedule;

import org.jspecify.annotations.Nullable;

/**
 * Where the schedule board goes. Called on the client thread every tick: return a cached value, do
 * not scan the world here. Null hides the board.
 *
 * <p><b>Integration point (hq).</b> Once the HQ is placed in the world, register
 * {@code ScheduleClient.get().setPlacementSource(() -> SchedulePlacement.onBoard(siteAnchor))} with the
 * world-space {@code AnchorNames.BOARD} anchor; {@code null} goes back to {@link RingBoardPlacement}
 * (in front of the behaviour director's provisional board).
 */
@FunctionalInterface
public interface SchedulePlacementSource {
	SchedulePlacementSource NONE = () -> null;

	@Nullable SchedulePlacement placement();
}
