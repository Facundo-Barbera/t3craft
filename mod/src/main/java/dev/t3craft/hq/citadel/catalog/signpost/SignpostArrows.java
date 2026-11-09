package dev.t3craft.hq.citadel.catalog.signpost;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Concept: a crossroads fingerpost: a stripped cherry post on a deepslate footing with two or three
 * hanging-sign "arms" at staggered heights, each pointing a different way where the plot allows,
 * alternating cherry and bamboo boards, topped by a lantern on tall plots.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Arms</b>: a wall hanging sign attaches by its bar to the block beside it, perpendicular to
 *     its facing, so a sign in the cell next to the post with {@code facing} turned 90° reads as a
 *     board sticking out of the post.</li>
 * <li><b>Silhouette</b>: arms at different heights and directions (left, right, towards the front),
 *     never a symmetric cross; when only one side has room the arms stack on it, like real fingerposts.</li>
 * <li><b>Base</b>: a stepped plinth of stairs leaning on the footing, with a potted flower, where room.</li>
 * </ul>
 * Size: 2–3 × 1–2 × 3+. The post stands at the back; arms take the cells beside and in front of it.
 */
public final class SignpostArrows implements Variant {
	private static final BlockState POST = b(Blocks.STRIPPED_CHERRY_LOG); // axis Y by default

	@Override
	public String id() {
		return "signpost_arrows";
	}

	@Override
	public Family family() {
		return Family.SIGNPOST;
	}

	@Override
	public int minW() {
		return 2;
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
		return 2;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int pu = w == 3 ? 1 : p.hash(0, 0, 0, 2); // width 2: the post on the left or right column by seed
		int pv = d - 1;
		int cap = p.h() >= 5 ? 4 : -1; // a lantern on top only when there is room above three arms
		int postTop = Math.min(p.h() - 1, 3);

		// --- footing and post
		p.set(c, pu, 0, pv, p.hash(pu, 0, pv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 1; y <= postTop; y++) {
			p.set(c, pu, y, pv, POST);
		}
		if (cap > 0) {
			p.set(c, pu, cap, pv, b(Blocks.LANTERN));
		}

		// --- the directions the arms can take, in a fixed order with a seed-chosen start
		List<Direction> dirs = new ArrayList<>();
		for (Direction dir : new Direction[] {Direction.WEST, Direction.NORTH, Direction.EAST}) {
			if (p.has(pu + dir.getStepX(), 0, pv + dir.getStepZ())) {
				dirs.add(dir);
			}
		}
		int start = p.hash(1, 0, 0, dirs.size());
		int arms = Math.min(3, postTop); // y1..postTop, top first
		for (int i = 0; i < arms; i++) {
			Direction dir = dirs.get((start + i) % dirs.size());
			int y = postTop - i;
			if (i == arms - 1 && arms == 3 && y == 1 && dirs.size() == 1) {
				break; // three arms stacked on one side crowd the post: keep two
			}
			Block sign = (i % 2 == 0) ? Blocks.CHERRY_WALL_HANGING_SIGN : Blocks.BAMBOO_WALL_HANGING_SIGN;
			p.put(c, pu + dir.getStepX(), y, pv + dir.getStepZ(),
					b(sign).setValue(BlockStateProperties.HORIZONTAL_FACING, dir.getClockWise()), CitadelCanvas.DECOR);
		}

		// --- base: stairs leaning on the footing in the free ground cells beside it, one potted flower
		boolean potted = false;
		for (Direction dir : new Direction[] {Direction.WEST, Direction.EAST, Direction.NORTH}) {
			int u = pu + dir.getStepX();
			int v = pv + dir.getStepZ();
			if (!p.has(u, 0, v)) {
				continue;
			}
			if (!potted && p.hash(u, 0, v, 2) == 0) {
				p.set(c, u, 0, v, b(Blocks.POTTED_ALLIUM));
				potted = true;
			} else if (dir == Direction.NORTH) {
				p.set(c, u, 0, v, slab(Blocks.DEEPSLATE_TILE_SLAB)); // the front: a low step to read the signs from
			} else {
				p.set(c, u, 0, v, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, dir.getOpposite()));
			}
		}
	}
}
