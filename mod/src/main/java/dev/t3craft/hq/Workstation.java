package dev.t3craft.hq;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * One desk with its computer, relative to the HQ origin (use {@link HqSite} for world coordinates).
 * Stable: a desk's positions depend only on its module index and slot, never on who sits there.
 *
 * @param module      module index ({@link HqLayout.Module#index()})
 * @param slot        desk slot in the module, {@code 0..HqLayout.DESKS-1}
 * @param threadId    who works here, or null when the desk is vacant
 * @param deskAnchor  where the NPC sits (the chair, foot level), facing the computer
 * @param computerPos the computer block (black concrete when taken, grey when vacant)
 * @param screen      centre of the computer's screen face, {@code facing} = outward normal (towards the
 *                    chair and the player): where a live screen or a sign is drawn
 * @param lampPos     the status lamp (a waxed copper bulb; toggle {@code lit})
 */
public record Workstation(int module, int slot, @Nullable String threadId, Anchor deskAnchor, BlockPos computerPos, Anchor screen,
		BlockPos lampPos) {
	/** Direction the screen faces. */
	public Direction screenFacing() {
		return screen.facing();
	}
}
