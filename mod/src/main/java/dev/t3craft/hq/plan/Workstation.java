package dev.t3craft.hq.plan;

import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * One desk with its screen, relative to the HQ origin (use {@link HqSite} for world coordinates).
 * Stable: a desk's positions depend only on its module index and slot, never on who sits there.
 *
 * <p>fix/desk-screens-3x2: a desk has <b>one screen of {@value #SCREEN_WIDTH} × {@value #SCREEN_HEIGHT}
 * blocks</b> standing on the desk top (no status lamp: the screen's header shows the thread's state).
 * With the blocks module on its cells are {@code t3craft:monitor}s that merge into one screen
 * ({@code MonitorWall}); off, they are the style's computer blocks and the workstation's client
 * display is drawn over the whole face.
 *
 * @param module       module index ({@link HqLayout.Module#index()})
 * @param slot         desk slot in the module, {@code 0..HqLayout.DESKS-1}
 * @param threadId     who works here (vacant desks are not built, so plans only list taken ones;
 *                     nullable for callers that model a free slot)
 * @param deskAnchor   where the NPC works standing (the free cell in front of the screen, foot level), facing it
 * @param computerPos  the screen's bottom-middle cell, right in front of the NPC at eye level (what the player clicks)
 * @param screen       centre of {@code computerPos}'s front face, {@code facing} = outward normal (towards the
 *                     NPC and the player)
 * @param screenOrigin the screen's top-left cell as the viewer faces it (columns grow to the viewer's right,
 *                     rows downward; the same convention as a plan's {@code bigscreen_*} anchor)
 */
public record Workstation(int module, int slot, @Nullable String threadId, Anchor deskAnchor, BlockPos computerPos, Anchor screen,
		BlockPos screenOrigin) {
	/** Desk screen width (blocks, along the desk). */
	public static final int SCREEN_WIDTH = 3;
	/** Desk screen height (blocks). */
	public static final int SCREEN_HEIGHT = 2;

	/** Direction the screen faces. */
	public Direction screenFacing() {
		return screen.facing();
	}

	/** The viewer's right (columns grow this way). */
	public Direction screenRight() {
		return screenFacing().getCounterClockWise();
	}

	/** Every cell of the screen, row by row from the top-left. */
	public List<BlockPos> screenCells() {
		return screenCells(screenOrigin, screenRight());
	}

	/** The screen's bottom-left cell as the viewer faces it (where a merged screen draws from). */
	public BlockPos screenBottomLeft() {
		return screenOrigin.below(SCREEN_HEIGHT - 1);
	}

	/** The top-left cell of a desk screen whose bottom-middle cell is {@code bottomMiddle}. */
	public static BlockPos screenOriginFor(BlockPos bottomMiddle, Direction screenFacing) {
		return bottomMiddle.relative(screenFacing.getCounterClockWise().getOpposite(), SCREEN_WIDTH / 2).above(SCREEN_HEIGHT - 1);
	}

	public static List<BlockPos> screenCells(BlockPos topLeft, Direction right) {
		List<BlockPos> out = new ArrayList<>(SCREEN_WIDTH * SCREEN_HEIGHT);
		for (int r = 0; r < SCREEN_HEIGHT; r++) {
			for (int c = 0; c < SCREEN_WIDTH; c++) {
				out.add(topLeft.relative(right, c).below(r));
			}
		}
		return List.copyOf(out);
	}
}
