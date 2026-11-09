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
 * A notice board for a plaza, at the scale of the city (a 7 wide, 2 deep, 8 high plot): a dark-oak board between two stripped
 * cherry posts on deepslate footings, under a little pink cherry roof that overhangs the front, a gold lantern hanging under
 * the eave at each post and a cherry bench in front to read from. The papers are two big pale sheets (calcite, smooth quartz or birch,
 * two blocks by two) set into the dark board at different heights, with birch signs and open trapdoors pinned on them and a
 * banner between them.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Contrast</b>: big light sheets on a dark board read from the far side of the plaza; which sheet material, which
 *     side is high and which notes are pinned comes from {@code p.hash}, so no two boards are alike.</li>
 * <li><b>Depth</b>: the roof stairs overhang the front by a block, the dark header sits behind them, the sheets are
 *     sunk into the board and the notes stand out from them; the bench and the stair buttresses stand in front.</li>
 * <li><b>Palette</b>: deepslate (footings, sill) → dark oak (board) → cherry (posts, roof, bench) and calcite / birch
 *     (papers), gold lanterns as accent. No item frames: they are entities, the catalog only places blocks.</li>
 * </ul>
 * Size: 5–7 × 2 × 5+ (front row: bench, notes and lanterns; back row: posts, board and sheets). The roof sits two blocks under
 * the top of the plot (at most 6 high), the ridge slab on top of it.
 */
public final class SignpostNoticeBoard implements Variant {
	private static final DyeColor[] BANNERS = {DyeColor.WHITE, DyeColor.PINK, DyeColor.LIGHT_GRAY};
	private static final Block[] SHEETS = {Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.CALCITE, Blocks.BIRCH_PLANKS};

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
		int roof = Math.min(p.h() - 2, 6); // the y of the eave and the header
		boolean ridge = p.h() >= roof + 2;
		int top = roof - 1; // the highest row of the board
		int lo = roof >= 4 ? 2 : 1; // the lowest row with papers (a stone sill under it)

		// --- the sheets: two pale 2 x 2 patches on the board at different heights (one when the board is narrow)
		boolean flip = p.hash(0, 0, 0, 2) == 0;
		int rowsHigh = Math.max(lo, top - 1);
		int rowsLow = Math.max(lo, top - 3);
		int[][] sheets = w >= 7
				? new int[][] {{1, flip ? rowsHigh : rowsLow}, {last - 2, flip ? rowsLow : rowsHigh}}
				: new int[][] {{1, flip ? rowsHigh : rowsLow}};

		// --- back row (v = 1): footings, posts, board, sheets, header
		for (int u = 0; u <= last; u++) {
			boolean post = u == 0 || u == last;
			p.set(c, u, 0, 1, post ? b(Blocks.POLISHED_DEEPSLATE) : p.hash(u, 0, 1, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES));
			for (int y = 1; y < roof; y++) {
				BlockState s = post ? b(Blocks.STRIPPED_CHERRY_LOG)
						: y == 1 && lo > 1 ? b(Blocks.DEEPSLATE_BRICKS) // [depth] a stone sill under the board
						: p.hash(u, y, 1, 5) == 0 ? b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)
						: b(Blocks.DARK_OAK_PLANKS);
				for (int[] sh : sheets) {
					if (u >= sh[0] && u < sh[0] + 2 && y >= sh[1] && y < sh[1] + 2) {
						s = b(SHEETS[p.hash(sh[0], sh[1], 1, SHEETS.length)]); // [palette] the pale sheet
					}
				}
				p.set(c, u, y, 1, s);
			}
			p.set(c, u, roof, 1, b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // [depth] the header
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
			p.put(c, u, top, 0, hanging, CitadelCanvas.HANGING);
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

		// --- the notes on the front face (v = 0): a banner on the dark middle, signs and trapdoors on the sheets
		int mid = w >= 7 ? 3 : last - 1; // the board column with no sheet
		DyeColor col = BANNERS[p.hash(mid, 2, 0, BANNERS.length)];
		p.put(c, mid, top, 0, b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
				CitadelCanvas.DECOR); // hangs over the row under it too, which stays free
		for (int[] sh : sheets) {
			for (int du = 0; du < 2; du++) {
				for (int dy = 0; dy < 2; dy++) {
					if (p.hash(sh[0] + du, sh[1] + dy, 0, 3) == 0) {
						continue; // a gap: the bare sheet shows
					}
					int u = sh[0] + du;
					int y = sh[1] + dy;
					p.put(c, u, y, 0, (du + dy) % 2 == 0 ? sign(p.hash(u, y, 0, 2) == 0 ? Blocks.BIRCH_WALL_SIGN : Blocks.CHERRY_WALL_SIGN)
							: b(Blocks.BIRCH_TRAPDOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
									.setValue(BlockStateProperties.OPEN, true), // a sheet pinned flat to the board
							CitadelCanvas.DECOR);
				}
			}
		}
	}

	private static BlockState sign(Block block) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH); // text towards the front
	}
}
