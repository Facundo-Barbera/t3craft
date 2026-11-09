package dev.t3craft.hq.citadel.catalog.signpost;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A crossroads fingerpost at the scale of the city (a 7 wide, 2 deep, 7 high plot): a dark stripped-oak post on a two-step
 * deepslate base, a lantern on top, and two arrows at staggered heights pointing opposite ways across the road, so
 * that someone walking along the avenue reads "left / right" face on. Each arrow is a one-block board of planks that runs
 * through the post (one block of tail on the other side) ending in a three-block head: a stair above, a stair below
 * (upside down) and the tip between them, so it narrows to a point.
 *
 * <ul>
 * <li><b>Arrow</b>: planks along the arm, then a head column (stair, plank, upside-down stair) and a one-block tip.</li>
 * <li><b>Contrast</b>: near-black dark oak post, pink cherry and yellow bamboo arrows, gold lantern, grey deepslate base.</li>
 * <li><b>Silhouette</b>: the arrows point opposite ways at different heights, never a symmetric cross.</li>
 * </ul>
 * Size: 3–7 × 1–2 × 3+ (it uses up to 8 blocks of height; two arrows from height 6 and a lantern from height 5). The post
 * stands on the back row, in the middle of the frontage; the arms hang over the cells beside it (3 each way at 7 wide),
 * so the only ground it uses is the post cell and the stair in front of it.
 */
public final class SignpostArrows implements Variant {
	private static final Block[][] WOODS = {
			{Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS},
			{Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_STAIRS},
			{Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS}};

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
		return 3;
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
		return 7;
	}

	@Override
	public int maxD() {
		return 2;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int pc = (w - 1) / 2; // the post, in the middle of the frontage
		int pv = d - 1; // on the back row
		int top = Math.min(p.h() - 1, 7);
		boolean lantern = top >= 4;
		boolean base = top >= 4; // a two-block base; shorter posts have the footing only

		// --- base and post: polished deepslate footing, deepslate bricks, then a dark stripped oak post
		put(c, p, pc, 0, pv, p.hash(pc, 0, pv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		if (base) {
			put(c, p, pc, 1, pv, p.hash(pc, 1, pv, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
		}
		int postTop = lantern ? top - 1 : top;
		for (int y = base ? 2 : 1; y <= postTop; y++) {
			put(c, p, pc, y, pv, b(Blocks.STRIPPED_DARK_OAK_LOG));
		}
		if (lantern) {
			put(c, p, pc, top, pv, b(Blocks.LANTERN));
		}
		if (d > 1 && base) {
			put(c, p, pc, 0, pv - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH)); // buttress, tall side to the post
		}

		// --- the arrows: from just under the lantern down, two rows apart, opposite ways
		int lowest = Math.max(1, Math.min(3, top - 1)); // arms hang over the road: at least 3 blocks of headroom under them
		int first = p.hash(1, 0, 0, 2) == 0 ? 1 : -1;
		int wood = p.hash(2, 0, 0, WOODS.length);
		int i = 0;
		for (int y = postTop; y >= lowest && i < 3; y -= 2, i++) {
			arrow(c, p, pc, y, pv, i % 2 == 0 ? first : -first, WOODS[(wood + i) % WOODS.length]);
		}
	}

	/**
	 * One arrow at height y pointing {@code s} (+1 right, -1 left): planks across the post, one block of tail behind it, a
	 * head column and the tip. Where the plot is too narrow the arm is shorter (a bare tip with no head at 1).
	 */
	private static void arrow(CitadelCanvas c, Plot p, int pc, int y, int pv, int s, Block[] m) {
		BlockState plank = b(m[0]);
		int reach = Math.min(3, s > 0 ? p.w() - 1 - pc : pc);
		Direction tail = s > 0 ? Direction.WEST : Direction.EAST; // local: EAST is right
		put(c, p, pc - s, y, pv, plank); // tail
		put(c, p, pc, y, pv, plank); // through the post
		for (int k = 1; k <= reach; k++) {
			put(c, p, pc + s * k, y, pv, plank);
		}
		if (reach >= 2) {
			int hc = pc + s * (reach - 1); // head column: tall side towards the tail, so the stairs fall away to the tip
			put(c, p, hc, y + 1, pv, stairs(m[1], tail));
			if (y >= 4) { // the lower wedge stays 3 blocks over the road: a walker never meets it
				put(c, p, hc, y - 1, pv, stairs(m[1], tail).setValue(BlockStateProperties.HALF, Half.TOP));
			}
		}
	}

	private static void put(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
