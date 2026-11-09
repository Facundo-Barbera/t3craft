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
 * A notice board for a plaza, at the scale of the city (a 7 wide, 2 deep, 7 high plot): a dark-oak board between two cherry
 * posts on deepslate footings, a little pink cherry roof that overhangs the front, a gold lantern hanging under the eave at
 * each post, pale papers pinned on the board (birch signs, open birch trapdoors, a banner), and a cherry bench in front of it
 * to read from.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Contrast</b>: light "papers" read well on the dark board; which ones and where comes from {@code p.hash}, so
 *     no two boards carry the same notes.</li>
 * <li><b>Depth</b>: the roof stairs overhang the papers by a block and the stripped header sits behind them; the bench
 *     and the stair buttresses stand in front; a ridge slab on tall plots.</li>
 * <li><b>Palette</b>: deepslate (footings, sill) → dark oak (board) → cherry (posts, header, roof, bench) and birch
 *     (papers), gold lanterns as accent. No item frames: they are entities, the catalog only places blocks.</li>
 * </ul>
 * Size: 5–7 × 2 × 5+ (front row: bench, papers and lanterns; back row: posts and board). The roof sits two blocks under
 * the top of the plot (at most 5 high), the ridge slab on top of it.
 */
public final class SignpostNoticeBoard implements Variant {
	private static final DyeColor[] BANNERS = {DyeColor.WHITE, DyeColor.PINK, DyeColor.LIGHT_GRAY};

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
		return 5;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 5;
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
		int last = w - 1;
		int roof = Math.min(p.h() - 2, 5); // the y of the eave and the header
		boolean ridge = p.h() >= roof + 2;

		// --- back row (v = 1): footings, posts, board, header
		for (int u = 0; u <= last; u++) {
			boolean post = u == 0 || u == last;
			p.set(c, u, 0, 1, post ? b(Blocks.POLISHED_DEEPSLATE) : p.hash(u, 0, 1, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES));
			for (int y = 1; y < roof; y++) {
				p.set(c, u, y, 1, post ? b(Blocks.CHERRY_LOG)
						: y == 1 ? b(Blocks.DEEPSLATE_BRICKS) // [depth] a stone sill under the board
						: p.hash(u, y, 1, 5) == 0 ? b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)
						: b(Blocks.DARK_OAK_PLANKS));
			}
			p.set(c, u, roof, 1, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // [depth] the header
			if (ridge) {
				p.set(c, u, roof + 1, 1, slab(Blocks.CHERRY_SLAB)); // [finish] ridge
			}
		}

		// --- front row (v = 0): the roof overhang, buttresses and lanterns at the posts, the bench between them
		for (int u = 0; u <= last; u++) {
			p.set(c, u, roof, 0, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // tall part at the back: the eave drops to the front
		}
		BlockState hanging = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int u : new int[] {0, last}) {
			p.set(c, u, 0, 0, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			p.put(c, u, roof - 1, 0, hanging, CitadelCanvas.HANGING);
		}
		int benchFrom = w >= 7 ? 2 : 1;
		for (int u = 1; u < last; u++) {
			if (u >= benchFrom && u <= last - benchFrom) {
				p.set(c, u, 0, 0, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH)); // a seat facing the board, back to the street
			} else {
				switch (p.hash(u, 0, 0, 3)) { // the gaps: a pot or some moss
					case 0 -> p.set(c, u, 0, 0, b(Blocks.POTTED_PINK_TULIP));
					case 1 -> p.set(c, u, 0, 0, b(Blocks.MOSS_CARPET));
					default -> {
					}
				}
			}
		}

		// --- the notes on the board face (v = 0, above the bench), from y = 2 up under the eave
		int lo = roof >= 4 ? 2 : 1;
		int banner = w >= 6 ? 2 + p.hash(0, 2, 0, w - 4) : -1;
		int placed = 0;
		for (int u = 1; u < last; u++) {
			if (u == banner) {
				DyeColor col = BANNERS[p.hash(u, 2, 0, BANNERS.length)];
				p.put(c, u, roof - 1, 0, b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR); // hangs over the row under it too, which stays free
				placed++;
				continue;
			}
			for (int y = lo; y < roof; y++) {
				BlockState note = note(p, u, y);
				if (note == null && !(placed == 0 && u == last - 1 && y == roof - 1)) {
					continue;
				}
				p.put(c, u, y, 0, note != null ? note : sign(Blocks.BIRCH_WALL_SIGN), CitadelCanvas.DECOR);
				placed++;
			}
		}
	}

	/** A note at (u, y): mostly birch (paper-white), some open trapdoors, some cherry; null = bare board. */
	private static BlockState note(Plot p, int u, int y) {
		return switch (p.hash(u, y, 0, 7)) {
			case 0, 1 -> sign(Blocks.BIRCH_WALL_SIGN);
			case 2 -> sign(Blocks.CHERRY_WALL_SIGN);
			case 3, 4 -> b(Blocks.BIRCH_TRAPDOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.OPEN, true); // a sheet pinned flat to the board
			default -> null;
		};
	}

	private static BlockState sign(Block block) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH); // text towards the front
	}
}
