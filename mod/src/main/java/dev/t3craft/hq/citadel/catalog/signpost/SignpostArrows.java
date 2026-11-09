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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A crossroads fingerpost with real arrows: a dark post on a deepslate footing, and two or three arrows at
 * staggered heights, each pointing a different way where the plot allows (left, right, towards the front).
 * Each arrow is a thin slab board across the post ending in a stair head on one side. Pink cherry, yellow bamboo and pale birch arrows alternate; a lantern crowns tall posts.
 *
 * <ul>
 * <li><b>Arrow head</b>: an upside-down stair whose full step is at the far end of a half-block board.</li>
 * <li><b>Contrast</b>: a near-black dark oak post against bright boards; deepslate at the foot.</li>
 * <li><b>Silhouette</b>: arrows at irregular heights and in different directions, never a symmetric
 *     cross; on a narrow plot they stack on the open side, like real fingerposts.</li>
 * </ul>
 * Size: 2–3 × 1–2 × 3+ (it uses up to 8 blocks of height; a lantern from height 5, up to three arrows).
 */
public final class SignpostArrows implements Variant {
	private static final Block[][] WOODS = {
			{Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB},
			{Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_STAIRS, Blocks.BAMBOO_SLAB},
			{Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB}};

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
		int top = Math.min(p.h() - 1, 7);
		boolean lantern = top >= 4;
		int postTop = lantern ? top - 1 : top;

		// --- footing and post: a deepslate footing, a stripped log collar, a slim dark fence above
		p.set(c, pu, 0, pv, p.hash(pu, 0, pv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		for (int y = 1; y <= postTop; y++) {
			p.set(c, pu, y, pv, y == 1 && postTop >= 3 ? b(Blocks.STRIPPED_DARK_OAK_LOG) : b(Blocks.DARK_OAK_FENCE));
		}
		if (lantern) {
			p.set(c, pu, top, pv, b(Blocks.LANTERN));
		}

		// --- the directions an arrow head can take, in a fixed order with a seed-chosen start
		List<Direction> dirs = new ArrayList<>();
		for (Direction dir : new Direction[] {Direction.WEST, Direction.NORTH, Direction.EAST}) {
			if (p.has(pu + dir.getStepX(), 0, pv + dir.getStepZ())) {
				dirs.add(dir);
			}
		}
		int start = p.hash(1, 0, 0, dirs.size());
		int wood = p.hash(2, 0, 0, WOODS.length);
		// one-row arrows from under the lantern down, a row of bare post between them, never on the collar
		int lowest = postTop >= 3 ? 2 : 1;
		int i = 0;
		for (int y = lantern ? postTop - 1 : postTop; y >= lowest && i < 3; y -= 2) {
			Direction dir = dirs.get((start + i) % dirs.size());
			arrow(c, p, pu, y, pv, dir, WOODS[(wood + i) % WOODS.length]);
			i++;
		}

		// --- base: stairs leaning on the footing in the free ground cells beside it, one potted flower (low
		// posts keep a bare footing, so the arrow is not lost in a skirt)
		if (p.h() <= 3) {
			return;
		}
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
				p.set(c, u, 0, v, slab(Blocks.DEEPSLATE_TILE_SLAB)); // the front: a low step to read the arrows from
			} else {
				p.set(c, u, 0, v, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, dir.getOpposite()));
			}
		}
	}

	/**
	 * One arrow at height y: a thin board (an upper slab) across the post and out on the tail side, and a head
	 * on the other side: an upside-down stair whose full step is at the far end, so the board thickens into
	 * the head.
	 */
	private static void arrow(CitadelCanvas c, Plot p, int pu, int y, int pv, Direction dir, Block[] m) {
		BlockState board = slab(m[2]).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		p.set(c, pu, y, pv, board);
		int tu = pu - dir.getStepX();
		int tv = pv - dir.getStepZ();
		if (p.has(tu, y, tv)) {
			p.set(c, tu, y, tv, board); // the tail
		}
		p.set(c, pu + dir.getStepX(), y, pv + dir.getStepZ(), stairs(m[1], dir).setValue(StairBlock.HALF, Half.TOP));
	}
}
