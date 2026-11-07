package dev.t3craft.client.schedule;

import java.util.List;

/**
 * <b>Integration point (blocks).</b> When the custom-blocks module (feat/blocks-core) brings its
 * "task board" block, its renderer registers here with {@code ScheduleClient.get().setBoardHook(...)}
 * and draws the same data on the block. Called on the client thread once per tick while the
 * schedule board is enabled; returning {@code true} means "shown", which hides the floating display
 * entities. This module never depends on the blocks module.
 */
@FunctionalInterface
public interface ScheduleBoardHook {
	/**
	 * @param board   the current page, already formatted (same text the displays would show)
	 * @param entries every known task in board order (all pages)
	 */
	boolean show(ScheduleText.Board board, List<ScheduleEntry> entries, long nowMs);
}
