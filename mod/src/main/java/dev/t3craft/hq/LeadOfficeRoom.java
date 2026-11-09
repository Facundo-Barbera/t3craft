package dev.t3craft.hq;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * feat/lead-office: the <b>lead office</b> module type drawn in a classic wing
 * ({@link HqLayout.Type#LEAD_OFFICE}). Same boxes, groups, anchors and site modules as a wing (so the
 * site, the builders and the diff need nothing new); only what stands in the vacant desk slots changes:
 *
 * <pre>
 *   end cap: the module's big screen, bound to project:lead:&lt;npcId&gt; (the lead's children)
 *   seg 2:   north: bookshelves     south: bookshelves     (both: chairs facing the big screen)
 *   seg 1:   north: bookshelves     south: whiteboard
 *   seg 0:   north: the lead's desk south: visitors' chairs facing it, the lead's banner
 *   (spine door)
 * </pre>
 *
 * Slot 0 is the lead's own desk, built like any desk (the lead is the only thread of its project, so
 * the allocator always seats it at slot 0). Pure; the furniture is written into the desk module's own
 * cells (feet and head level), so it never collides with another module.
 */
final class LeadOfficeRoom {
	private LeadOfficeRoom() {
	}

	/** Whiteboard face. */
	static final BlockState BOARD = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	static final BlockState SHELF = Blocks.BOOKSHELF.defaultBlockState();

	/** The lead's banner (standing; the colour of its environment). */
	static BlockState banner(DyeColor c) {
		return Blocks.BANNER.pick(c).defaultBlockState();
	}

	/**
	 * feat/lead-review-walk: the visitors' chairs of lead office {@code i} (slot 1, across the corridor from
	 * the lead's desk), as {@link AnchorNames#leadSeat} anchors facing the lead: like the hub benches, the
	 * free cell in front of each chair {@link #furnish} puts there.
	 */
	static java.util.List<Anchor> seats(int i, String leadNpcId) {
		int u0 = HqGeometry.segmentU(0);
		int chairZ = HqGeometry.wz(i, HqGeometry.chairV(1));
		java.util.List<Anchor> out = new java.util.ArrayList<>();
		int n = 0;
		for (int k = 0; k < 3; k += 2) {
			// like the hub benches' anchors: the free cell in front of the chair, facing the lead
			BlockPos chair = new BlockPos(HqGeometry.wx(i, u0 + k), 1, chairZ);
			out.add(Anchor.at(AnchorNames.leadSeat(leadNpcId, n++), chair.relative(Direction.NORTH), Direction.NORTH));
		}
		return out;
	}

	/**
	 * Furniture of a vacant slot {@code slot} (1..5) of a lead office {@code i}, written into the desk
	 * module's canvas after the style drew its bare floor.
	 */
	static void furnish(PlanBuilder.Canvas c, HqStyle style, int i, int slot, DyeColor accent) {
		int u0 = HqGeometry.segmentU(slot / 2);
		int deskZ = HqGeometry.wz(i, HqGeometry.deskV(slot));
		int chairZ = HqGeometry.wz(i, HqGeometry.chairV(slot));
		int seg = slot / 2;
		boolean north = HqGeometry.northDesk(slot);
		for (int k = 0; k < 3; k++) {
			int x = HqGeometry.wx(i, u0 + k);
			BlockPos wall = new BlockPos(x, 1, deskZ);
			BlockPos front = new BlockPos(x, 1, chairZ);
			if (seg == 0) {
				// visitors' chairs facing the lead's desk across the corridor, the banner against the wall
				if (k != 1) {
					Direction look = Direction.NORTH;
					c.set(front, style.bench(look.getOpposite()));
				} else {
					c.decor(wall, banner(accent));
				}
			} else if (seg == 1 && !north) {
				c.set(wall, BOARD);
				c.set(wall.above(), BOARD);
			} else {
				c.set(wall, SHELF);
				c.set(wall.above(), SHELF);
				if (seg == 2 && k != 1) {
					// meeting seats facing the big screen at the end of the room
					Direction look = HqGeometry.outward(i);
					c.set(front, style.bench(look.getOpposite()));
				}
			}
		}
	}
}
