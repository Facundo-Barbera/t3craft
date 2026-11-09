package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Street lamp: a cherry post on a dark stone base, with an arm that carries a hanging lantern
 * towards the front (and, on wide plots, a twin arm to each side).
 *
 * <p>Template of quality for small furniture. The tricks:
 * <ul>
 * <li><b>Gradient dark → light, bottom → top</b> (art direction §2): deepslate base (heavy), a polished
 *     deepslate wall as plinth, the cherry fence post (medium), a stripped cherry collar and a cherry
 *     cap (light) on top.</li>
 * <li><b>Silhouette</b>: a thin post (fence, not a log) with a thicker collar where the arm springs
 *     reads as "made", not as a stick; the lantern hangs off-axis so the light falls on the path.</li>
 * <li><b>Transition to the ground</b> (§6): on a 3×3 plot the base is a stepped plinth of stairs
 *     facing the post, with a planted corner, never a block dropped on the grass.</li>
 * <li><b>Connections set by hand</b>: the plan stores states as given, so fence arms carry their
 *     NORTH/SOUTH/EAST/WEST sides explicitly (the report flags fences whose sides do not match).</li>
 * </ul>
 * Size: 1–3 × 1–3 × 3–6. With depth 1 there is no room for an arm: the lantern stands on the cap.
 */
public final class LampCherryPost implements Variant {
	private static final BlockState FENCE = b(Blocks.CHERRY_FENCE);
	private static final BlockState PLINTH = b(Blocks.POLISHED_DEEPSLATE_WALL);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "lamp_cherry_post";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 3;
	}

	@Override
	public int maxW() {
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cu = p.w() / 2;
		int cv = p.d() / 2; // d = 2 → post at the back, arm over v = 0
		int top = p.h() - 1;
		boolean arm = p.d() >= 2 && p.h() >= 4;
		boolean twin = arm && p.w() >= 3 && p.hash(0, 0, 0, 2) == 0; // seed decides: one arm or three

		// --- base: the heaviest, darkest part
		p.set(c, cu, 0, cv, p.hash(cu, 0, cv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		if (p.w() >= 3 && p.d() >= 3) {
			// stepped plinth: stairs climbing towards the post on the four sides, corners low
			p.set(c, cu, 0, cv - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			p.set(c, cu, 0, cv + 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
			p.set(c, cu - 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST));
			p.set(c, cu + 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST));
			int planted = p.hash(1, 0, 1, 4); // one corner gets a flower pot, the rest a low slab
			int[][] corners = {{cu - 1, cv - 1}, {cu + 1, cv - 1}, {cu - 1, cv + 1}, {cu + 1, cv + 1}};
			for (int i = 0; i < 4; i++) {
				p.set(c, corners[i][0], 0, corners[i][1], i == planted ? b(Blocks.POTTED_FLOWERING_AZALEA) : slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}

		// --- shaft: plinth wall (stone), then the cherry post
		int armY = arm ? top - (p.h() >= 5 ? 1 : 0) : top; // the arm springs one below the cap when there is room
		int y = 1;
		if (p.h() >= 5) {
			p.set(c, cu, y++, cv, PLINTH);
		}
		for (; y < armY; y++) {
			p.set(c, cu, y, cv, FENCE);
		}

		if (!arm) {
			// no depth for an arm: the fence post carries a standing lantern on top (a slim post, not a box)
			if (top >= 2) {
				p.set(c, cu, top - 1, cv, FENCE);
			}
			p.set(c, cu, top, cv, LANTERN);
			return;
		}

		// --- head: a fence bracket whose arms connect sideways (no cube collar), arms with lanterns under their tips
		BlockState head = FENCE.setValue(BlockStateProperties.NORTH, true);
		if (twin) {
			head = head.setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.EAST, true);
		}
		p.set(c, cu, armY, cv, head);
		if (armY < top) {
			p.set(c, cu, top, cv, slab(Blocks.CHERRY_SLAB)); // light cap: the top of the gradient
		}
		armTo(c, p, cu, armY, cv, Direction.NORTH);
		if (twin) {
			armTo(c, p, cu, armY, cv, Direction.WEST);
			armTo(c, p, cu, armY, cv, Direction.EAST);
		}
	}

	/** A one-block fence arm from the collar towards {@code side}, with a lantern hanging under its tip. */
	private static void armTo(CitadelCanvas c, Plot p, int u, int y, int v, Direction side) {
		int au = u + side.getStepX();
		int av = v + side.getStepZ();
		p.set(c, au, y, av, FENCE.setValue(prop(side.getOpposite()), true));
		p.put(c, au, y - 1, av, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	private static BooleanProperty prop(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}
}
