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
 * A crossroads fingerpost at the scale of the city (a 7 wide, 2 deep, 8 high plot): a stout two-block-deep dark stripped-oak
 * post on a two-step deepslate base, two gold lanterns on top, and a double signboard: two stacked arrows, a pink cherry one
 * above a bamboo one, pointing opposite ways across the road (so someone walking along the avenue reads "left / right" face on)
 * and of different lengths. Each arrow is a two-block-tall, two-block-deep board of planks that runs through the post (one
 * block of tail on the other side) and ends in a tip cut at both corners with stairs, so it narrows to a point.
 *
 * <ul>
 * <li><b>Arrow</b>: planks two rows tall; at the tip an upright stair above and an upside-down stair below, both with their
 *     tall side to the tail: the end is chamfered into a point.</li>
 * <li><b>Contrast</b>: near-black dark oak post, pink cherry and yellow bamboo arrows, gold lanterns, grey deepslate base.</li>
 * <li><b>Silhouette</b>: the arrows point opposite ways and differ in length, never a symmetric cross.</li>
 * </ul>
 * Size: 3–7 × 1–2 × 3+ (it uses up to 9 blocks of height; the double board from height 7, a lantern from height 5). The post
 * stands on the back rows, in the middle of the frontage; the arms hang over the cells beside it (3 each way at 7 wide), so the
 * only ground it uses is the post's own cells, and the lowest board starts 3 blocks over the road.
 */
public final class SignpostArrows implements Variant {
	/** Planks and stairs of the upper (cherry) and the lower (bamboo) arrow. */
	private static final Block[][] WOODS = {
			{Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS},
			{Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_STAIRS}};

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
		int top = Math.min(p.h() - 1, 8);
		boolean lantern = top >= 4;
		boolean base = top >= 4; // a two-block base; shorter posts have the footing only
		int ah = top >= 6 ? 2 : 1; // arrow board height
		int lowest = Math.max(1, Math.min(3, top - 1)); // arms hang over the road: at least 3 blocks of headroom under them
		int postTop = lantern ? top - 1 : top;

		// --- base and post, on the back row and the one in front of it
		for (int v = d - 1; v >= Math.max(0, d - 2); v--) {
			put(c, p, pc, 0, v, p.hash(pc, 0, v, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
			if (base) {
				put(c, p, pc, 1, v, p.hash(pc, 1, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
			}
			for (int y = base ? 2 : 1; y <= postTop; y++) {
				put(c, p, pc, y, v, b(Blocks.STRIPPED_DARK_OAK_LOG));
			}
			if (lantern) {
				put(c, p, pc, top, v, b(Blocks.LANTERN));
			}
		}

		// --- the arrows, stacked from just under the lantern down, opposite ways, the lower one shorter
		int first = p.hash(1, 0, 0, 2) == 0 ? 1 : -1;
		int y = top - ah;
		for (int i = 0; i < 2 && y >= lowest; i++, y -= ah + (ah == 1 ? 1 : 0)) {
			arrow(c, p, pc, y, ah, d, i % 2 == 0 ? first : -first, WOODS[i], i == 0 ? 3 : 2);
		}
	}

	/**
	 * One arrow whose bottom row is y, {@code ah} rows tall, pointing {@code s} (+1 right, -1 left), at most {@code len} blocks
	 * out of the post: planks across the post, one block of tail behind it and the chamfered tip. Where the plot is too
	 * narrow the arm is shorter. It covers the post's rows (the back row and the one in front of it).
	 */
	private static void arrow(CitadelCanvas c, Plot p, int pc, int y, int ah, int d, int s, Block[] m, int len) {
		int reach = Math.min(len, s > 0 ? p.w() - 1 - pc : pc);
		Direction tail = s > 0 ? Direction.WEST : Direction.EAST; // local: EAST is right
		BlockState plank = b(m[0]);
		for (int v = d - 1; v >= Math.max(0, d - 2); v--) {
			for (int k = -1; k <= reach; k++) {
				boolean tip = k == reach && reach >= 2 && ah == 2;
				for (int r = 0; r < ah; r++) {
					BlockState st = plank;
					if (tip) {
						// tall side towards the tail: the outer corners are cut, the end narrows to a point
						st = r == 0 ? stairs(m[1], tail).setValue(BlockStateProperties.HALF, Half.TOP) : stairs(m[1], tail);
					}
					put(c, p, pc + s * k, y + r, v, st);
				}
			}
		}
	}

	private static void put(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
