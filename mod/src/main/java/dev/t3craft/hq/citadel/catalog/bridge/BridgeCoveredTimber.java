package dev.t3craft.hq.citadel.catalog.bridge;

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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a covered timber bridge, the kind that crosses a mountain stream in a cherry valley. Dark
 * stone piers at both ends carry spruce stringers and a striped cherry/spruce plank deck (stone slabs
 * at the ends so it is walkable from {@code y = 0}); spruce log posts stand in bays of about three
 * along both sides with fence railings between them and knee braces of upside-down stairs under the
 * eaves; stripped-cherry tie beams cross over the walkway at every post line, and lanterns hang from
 * the inner beams. The gable roof has a pink cherry eave row, deepslate-tile slopes, a polished ridge
 * (a log ridge beam on odd widths) and, with room, a slab ridge cap with tile-wall finials. Both
 * portals have a cherry valance under the gable overhang and lanterns standing on the piers.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: polished and cobbled deepslate piers; spruce logs,
 *     stringers and fences (dark wood); cherry and spruce deck; stripped cherry beams; cherry eaves;
 *     deepslate tile slopes; polished deepslate ridge.</li>
 * <li><b>Depth</b>: posts (full logs) against thin fence railings; knee braces stepping out from the
 *     posts under the eave; open sides between railing and eave; the roof overhangs the portals by a
 *     row (posts start at {@code v = 1}) with a stair valance under it.</li>
 * <li><b>Finish</b>: hanging lanterns along the walkway, standing lanterns on the four pier corners,
 *     hanging lanterns in both portals.</li>
 * </ul>
 *
 * <pre>
 *  section across u (w = 5)          side (d = 9)
 *  y6      ▂            ridge cap    ▂▂▂▂▂▂▂▂▂
 *  y5    ◢ ═ ◣          ridge beam   ▀▀▀▀▀▀▀▀▀  tiles
 *  y4  ◢ ═══════ ◣  eaves, tie beam  ▀▀▀▀▀▀▀▀▀  cherry eave
 *  y3  ▌◥  ♦  ◤▐    braces, lantern   ▌◥ ◤▌◥ ◤▌
 *  y2  ▌       ▐    open sides        ▌   ▌   ▌
 *  y1  ▌┼┼┼┼┼┼┼▐    railing           ▌┼┼┼▌┼┼┼▌
 *  y0  ▓▒▒▒▒▒▒▒▓    piers / deck     ▓▓▒▒▒▒▒▓▓
 * </pre>
 *
 * Size: 4–5 × 6–15 × 6+ (h 7 adds the ridge cap, h 8 copper rods on the finials on odd widths).
 */
public final class BridgeCoveredTimber implements Variant {
	private static final BlockState POST = b(Blocks.SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	private static final BlockState STRINGER = b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState TIE = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState RIDGE_BEAM = b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState LANTERN_HANGING = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	private static final BlockState CHAIN = b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	/** The eave row (the lowest roof layer). */
	private static final int EAVE = 4;

	@Override
	public String id() {
		return "bridge_covered_timber";
	}

	@Override
	public Family family() {
		return Family.BRIDGE;
	}

	@Override
	public int minW() {
		return 4;
	}

	@Override
	public int minD() {
		return 6;
	}

	@Override
	public int minH() {
		return 6;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 15;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();

		// post lines: v = 1 and v = d - 2, evenly spaced bays of about three between them
		int bays = Math.max(1, Math.round((d - 3) / 3.0f));
		List<Integer> posts = new ArrayList<>();
		for (int i = 0; i <= bays; i++) {
			posts.add(1 + Math.round(i * (d - 3) / (float) bays));
		}

		deck(c, p);
		sides(c, p, posts);
		roof(c, p);

		// [finish] lanterns: under every inner tie beam; with a single bay, on a chain from the ridge mid-span
		int cu = (w - 1) / 2;
		for (int i = 1; i < posts.size() - 1; i++) {
			int pu = w % 2 == 1 ? cu : cu + (i % 2); // even widths alternate the two middle cells
			p.put(c, pu, EAVE - 1, posts.get(i), LANTERN_HANGING, CitadelCanvas.HANGING);
		}
		if (posts.size() == 2) {
			int mv = (d - 1) / 2;
			p.put(c, cu, EAVE, mv, CHAIN, CitadelCanvas.HANGING);
			p.put(c, cu, EAVE - 1, mv, LANTERN_HANGING, CitadelCanvas.HANGING);
			if (d % 2 == 0) {
				p.put(c, cu, EAVE, mv + 1, CHAIN, CitadelCanvas.HANGING);
				p.put(c, cu, EAVE - 1, mv + 1, LANTERN_HANGING, CitadelCanvas.HANGING);
			}
		}
	}

	// ------------------------------------------------------------------ piers and deck (y0)

	private static void deck(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			boolean end = v == 0 || v == d - 1;
			boolean pier = v <= 1 || v >= d - 2;
			for (int u = 0; u < w; u++) {
				boolean side = u == 0 || u == w - 1;
				BlockState st;
				if (side) {
					// [palette] dark stone piers at the ends, spruce stringers between
					st = pier ? (p.hash(u, 0, v, 3) == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE)) : STRINGER;
				} else if (end) {
					// the approach: a stone slab at ground level
					st = p.hash(u, 0, v, 3) == 0 ? slab(Blocks.COBBLED_DEEPSLATE_SLAB) : slab(Blocks.DEEPSLATE_BRICK_SLAB);
				} else {
					// striped plank deck: spruce every third row, cherry otherwise, a few stripped cherry boards
					int k = p.hash(u, 0, v, 7);
					st = v % 3 == 0 ? b(Blocks.SPRUCE_PLANKS) : k == 0 ? b(Blocks.STRIPPED_CHERRY_WOOD) : b(Blocks.CHERRY_PLANKS);
				}
				p.set(c, u, 0, v, st);
			}
			if (end) {
				// [finish] lanterns standing on the pier corners
				p.set(c, 0, 1, v, b(Blocks.LANTERN));
				p.set(c, w - 1, 1, v, b(Blocks.LANTERN));
			}
		}
	}

	// ------------------------------------------------------------------ posts, railings, braces, tie beams

	private static void sides(CitadelCanvas c, Plot p, List<Integer> posts) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {0, w - 1}) {
			for (int v = 1; v <= d - 2; v++) {
				if (posts.contains(v)) {
					for (int y = 1; y < EAVE; y++) {
						p.set(c, u, y, v, POST);
					}
					continue;
				}
				// railing: every cell between two posts touches a rail or a post on both sides
				p.set(c, u, 1, v, b(Blocks.SPRUCE_FENCE)
						.setValue(BlockStateProperties.NORTH, true)
						.setValue(BlockStateProperties.SOUTH, true)
						.setValue(BlockStateProperties.EAST, false)
						.setValue(BlockStateProperties.WEST, false));
				// [depth] knee brace under the eave, its full side against the post
				Direction toPost = posts.contains(v - 1) ? Direction.NORTH : posts.contains(v + 1) ? Direction.SOUTH : null;
				if (toPost != null) {
					p.set(c, u, EAVE - 1, v, stairs(Blocks.SPRUCE_STAIRS, toPost).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
		for (int v : posts) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, EAVE, v, TIE);
			}
		}
	}

	// ------------------------------------------------------------------ roof (y4+)

	private static void roof(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		boolean odd = w % 2 == 1;
		int mid = w / 2; // the ridge cell on odd widths
		for (int v = 0; v < d; v++) {
			boolean end = v == 0 || v == d - 1;
			// eave row: cherry, the citadel's pink line
			p.set(c, 0, EAVE, v, stairs(Blocks.CHERRY_STAIRS, Direction.EAST));
			p.set(c, w - 1, EAVE, v, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
			// upper slopes: deepslate tiles, a few polished deepslate stairs mixed in
			Block tl = p.hash(1, EAVE + 1, v, 5) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
			Block tr = p.hash(w - 2, EAVE + 1, v, 5) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
			p.set(c, 1, EAVE + 1, v, stairs(tl, Direction.EAST));
			p.set(c, w - 2, EAVE + 1, v, stairs(tr, Direction.WEST));
			if (odd) {
				p.set(c, mid, EAVE + 1, v, end ? b(Blocks.POLISHED_DEEPSLATE) : RIDGE_BEAM);
			}
			if (end) {
				// [depth] valance under the gable overhang, full side towards the inside of the bridge
				Direction in = v == 0 ? Direction.SOUTH : Direction.NORTH;
				for (int u = 1; u < w - 1; u++) {
					p.set(c, u, EAVE, v, stairs(Blocks.CHERRY_STAIRS, in).setValue(BlockStateProperties.HALF, Half.TOP));
				}
				// [finish] portal lanterns hanging under the valance
				if (odd) {
					p.put(c, mid, EAVE - 1, v, LANTERN_HANGING, CitadelCanvas.HANGING);
				} else {
					p.put(c, mid - 1, EAVE - 1, v, LANTERN_HANGING, CitadelCanvas.HANGING);
					p.put(c, mid, EAVE - 1, v, LANTERN_HANGING, CitadelCanvas.HANGING);
				}
			}
		}

		// ridge cap and finials, when there is room
		int cap = EAVE + 2;
		if (cap >= p.h()) {
			return;
		}
		for (int v = 0; v < d; v++) {
			boolean end = v == 0 || v == d - 1;
			if (odd) {
				if (end) {
					p.set(c, mid, cap, v, b(Blocks.DEEPSLATE_TILE_WALL)
							.setValue(BlockStateProperties.UP, true)
							.setValue(BlockStateProperties.NORTH_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.SOUTH_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE));
					if (cap + 1 < p.h()) {
						p.set(c, mid, cap + 1, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
					}
				} else {
					p.set(c, mid, cap, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			} else {
				p.set(c, mid - 1, cap, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
				p.set(c, mid, cap, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
		}
	}
}
