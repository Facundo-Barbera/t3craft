package dev.t3craft.hq.citadel.catalog.signpost;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Concept: a village notice board: a dark-oak board between two cherry posts on deepslate footings,
 * under a little pink cherry roof that overhangs the front; pale birch signs pinned on it like notes,
 * a banner as a poster on wide boards, and a lantern hanging under the eave at each post.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Contrast</b>: light "papers" (birch and cherry wall signs, a banner) read well on the dark
 *     board; the scatter comes from {@code p.hash}, so no two boards carry the same notes.</li>
 * <li><b>Depth</b>: the roof stairs overhang the papers by a block and the stripped header sits behind
 *     them; stairs buttress the footings in front; a ridge slab on tall plots.</li>
 * <li><b>Palette</b>: deepslate (footings, sill) → dark oak (board) → cherry (posts, header, roof), gold
 *     lanterns as accent. No item frames: they are entities, the catalog only places blocks.</li>
 * </ul>
 * Size: 3–5 × 2 × 4+ (front row: papers and lanterns; back row: posts and board).
 */
public final class SignpostNoticeBoard implements Variant {
	private static final DyeColor[] BANNERS = {DyeColor.PINK, DyeColor.WHITE, DyeColor.LIGHT_GRAY};

	@Override
	public String id() {
		return "signpost_notice_board";
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
		return 2;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 2;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int last = w - 1;

		// --- back row (v = 1): footings, posts, board, header
		for (int u = 0; u <= last; u++) {
			boolean post = u == 0 || u == last;
			p.set(c, u, 0, 1, post ? b(Blocks.POLISHED_DEEPSLATE) : p.hash(u, 0, 1, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES));
			for (int y = 1; y <= 2; y++) {
				p.set(c, u, y, 1, post ? b(Blocks.CHERRY_LOG)
						: p.hash(u, y, 1, 4) == 0 ? b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)
						: b(Blocks.DARK_OAK_PLANKS));
			}
			p.set(c, u, 3, 1, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // [depth] the header
			if (p.h() >= 5) {
				p.set(c, u, 4, 1, slab(Blocks.CHERRY_SLAB)); // [finish] ridge
			}
		}

		// --- front row (v = 0): the roof overhang over everything
		for (int u = 0; u <= last; u++) {
			p.set(c, u, 3, 0, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // tall part at the back: the eave drops to the front
		}
		// footing buttresses and lanterns under the eave at the posts
		BlockState hanging = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int u : new int[] {0, last}) {
			p.set(c, u, 0, 0, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			p.put(c, u, 2, 0, hanging, CitadelCanvas.HANGING);
		}

		// --- the notes: wall signs on the board, a banner on wide boards
		int banner = w >= 4 ? 1 + p.hash(0, 2, 0, w - 2) : -1;
		int placed = 0;
		for (int u = 1; u < last; u++) {
			if (u == banner) {
				DyeColor col = BANNERS[p.hash(u, 2, 0, BANNERS.length)];
				p.put(c, u, 2, 0, b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR); // hangs over y1 too, so y1 stays free
				placed++;
				continue;
			}
			for (int y = 1; y <= 2; y++) {
				BlockState note = note(p, u, y);
				if (note == null && !(placed == 0 && u == last - 1 && y == 2)) {
					continue;
				}
				p.put(c, u, y, 0, note != null ? note : sign(Blocks.BIRCH_WALL_SIGN), CitadelCanvas.DECOR);
				placed++;
			}
		}

		// --- at the foot of the board: a pot or some moss in the gaps
		for (int u = 1; u < last; u++) {
			if (u == banner) {
				continue; // the banner hangs down to y1: keep its foot clear
			}
			switch (p.hash(u, 0, 0, 4)) {
				case 0 -> p.set(c, u, 0, 0, b(Blocks.POTTED_PINK_TULIP));
				case 1 -> p.set(c, u, 0, 0, b(Blocks.MOSS_CARPET));
				default -> {
				}
			}
		}
	}

	/** A note at (u, y): mostly birch (paper-white), some cherry, some gaps; null = bare board. */
	private static BlockState note(Plot p, int u, int y) {
		return switch (p.hash(u, y, 0, 5)) {
			case 0, 1, 2 -> sign(Blocks.BIRCH_WALL_SIGN);
			case 3 -> sign(Blocks.CHERRY_WALL_SIGN);
			default -> null;
		};
	}

	private static BlockState sign(Block block) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH); // text towards the front
	}
}
