package dev.t3craft.hq.citadel.catalog.statue;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
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

/**
 * Concept: a memorial obelisk on a stepped pyramid, with a brazier burning on each corner. The shaft
 * rises from a chiselled die that carries the inscription (a dark oak plaque on the front, chiselled
 * deepslate on all four faces), climbs from dark polished deepslate through stone bricks to calcite, is
 * banded with chiselled courses, and ends in a gold pyramidion with a copper rod (a copper-stair cap on a
 * two-wide shaft). The four corner braziers are campfires (soul fire on some seeds) on deepslate posts.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate tiles and bricks for the pyramid, polished
 *     deepslate at the foot of the shaft, stone bricks (mossy or cracked by hash) in the middle, calcite
 *     at the top, gold and copper as the crown.</li>
 * <li><b>Depth</b>: every ring of the base steps in towards the shaft (stairs facing it, slabs on the
 *     inner corners); the plaque stands off the die; chiselled bands every few courses break the shaft.</li>
 * <li><b>Silhouette</b>: a needle, much taller than wide, with four low flames round its foot; unlike
 *     the guardian it has no figure and no steps from one side only.</li>
 * </ul>
 * The shaft takes the centre column(s) (one or two per axis, by parity); the base has as many steps
 * as the narrow side allows (one on 3–4, two on 5). The obelisk grows with the height up to 12 courses.
 *
 * <p>Size: 3–5 × 3–5 × 8+.
 */
public final class StatueObeliskBraziers implements Variant {
	@Override
	public String id() {
		return "statue_obelisk_braziers";
	}

	@Override
	public Family family() {
		return Family.STATUE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int ul = (w - 1) / 2;
		int ur = w / 2;
		int vl = (d - 1) / 2;
		int vr = d / 2;
		int steps = (Math.min(w, d) - 1) / 2; // 1 on 3–4, 2 on 5: the shaft stands on y = steps
		boolean soul = p.hash(0, 0, 0, 4) == 0;

		// --- the stepped base: each cell rises with its distance from the plot's edge
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int eu = Math.min(u, w - 1 - u);
				int ev = Math.min(v, d - 1 - v);
				int e = Math.min(Math.min(eu, ev), steps);
				boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
				if (corner) {
					// [finish] a brazier on each outer corner: a dark post and the fire on it
					p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
					p.set(c, u, 1, v, soul ? b(Blocks.SOUL_CAMPFIRE) : b(Blocks.CAMPFIRE));
					continue;
				}
				for (int y = 0; y < e; y++) {
					p.set(c, u, y, v, base(p, u, y, v));
				}
				if (e == steps) {
					continue; // the top of the base: full up to y = steps - 1
				}
				if (eu == ev) {
					p.set(c, u, e, v, slab(e == 0 ? Blocks.DEEPSLATE_TILE_SLAB : Blocks.DEEPSLATE_BRICK_SLAB));
				} else {
					// [depth] a step climbing towards the shaft
					Direction up = ev < eu ? (v < vl ? Direction.SOUTH : Direction.NORTH) : (u < ul ? Direction.EAST : Direction.WEST);
					p.set(c, u, e, v, stairs(e == 0 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.DEEPSLATE_BRICK_STAIRS, up));
				}
			}
		}

		// --- the shaft
		int y0 = steps;
		int top = Math.min(p.h() - 1, y0 + 11); // the rod (or the cap) sits on top
		boolean needle = ul == ur && vl == vr;
		int capY = needle ? top - 1 : top; // gold pyramidion (needle) or copper-stair cap
		for (int y = y0; y < capY; y++) {
			int k = y - y0; // course
			double f = (double) (y - y0) / Math.max(1, capY - y0);
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					p.set(c, u, y, v, shaft(p, u, y, v, k, f));
				}
			}
		}
		if (needle) {
			p.set(c, ul, capY, vl, b(Blocks.GOLD_BLOCK));
			p.set(c, ul, top, vl, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		} else {
			// a two-wide shaft gets a pyramid cap of copper stairs, each rising towards the middle
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					Direction in = ul != ur ? (u == ul ? Direction.EAST : Direction.WEST) : (v == vl ? Direction.SOUTH : Direction.NORTH);
					p.set(c, u, capY, v, stairs(Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), in));
				}
			}
		}

		// --- the inscription: a plaque hung on the front of the die (on the step in front of it)
		int pv = vl - 1;
		if (pv >= 0) {
			for (int u = ul; u <= ur; u++) {
				p.put(c, u, y0, pv, b(Blocks.DARK_OAK_WALL_SIGN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR);
			}
		}
	}

	/** [palette] the pyramid: dark tiles at the foot, deepslate bricks above. */
	private static BlockState base(Plot p, int u, int y, int v) {
		int hsh = p.hash(u, y, v, 5);
		if (y == 0) {
			return hsh == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
		}
		return hsh == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
	}

	/** [palette] die, then dark → light along the shaft, with chiselled bands. */
	private static BlockState shaft(Plot p, int u, int y, int v, int course, double f) {
		if (course == 0) {
			return b(Blocks.CHISELED_DEEPSLATE); // the die: the inscribed block
		}
		if (course == 1) {
			return b(Blocks.POLISHED_DEEPSLATE);
		}
		if (course % 4 == 0) {
			return f > 0.6 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : b(Blocks.CHISELED_STONE_BRICKS); // a band
		}
		if (f > 0.66) {
			return b(Blocks.CALCITE);
		}
		int hsh = p.hash(u, y, v, 6);
		Block body = hsh == 0 ? Blocks.MOSSY_STONE_BRICKS : hsh == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
		return b(body);
	}
}
