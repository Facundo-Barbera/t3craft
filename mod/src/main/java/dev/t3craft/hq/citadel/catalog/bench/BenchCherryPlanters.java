package dev.t3craft.hq.citadel.catalog.bench;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
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
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Concept: a park bench of cherry stairs with an open-trapdoor backrest, framed by two dark
 * deepslate planter boxes crowned with flowering azalea, one of them carrying a little lantern.
 * Behind the bench (when there is depth) a low pink-and-green hedge gives it something to lean on.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Backrest in one cell</b>: an open trapdoor facing NORTH sits against the back edge of its
 *     cell, so it stands right over the back of the seat stair (works with depth 1).</li>
 * <li><b>Palette</b> (dark → light, bottom → top): polished deepslate / deepslate tiles mix for the
 *     planter boxes, cherry for seat and backrest, azalea foliage and a gold lantern on top.</li>
 * <li><b>Depth</b>: the planters stand proud of the seat (a slab lip in front on deep plots), the
 *     hedge steps back and down, cherry leaves alternate with flowering azalea leaves by hash.</li>
 * </ul>
 * Size: 2–5 × 1–3 × 2+. Width 2: just the seat; width 3: one planter (side by seed); 4–5: both.
 */
public final class BenchCherryPlanters implements Variant {
	private static final BlockState SEAT = stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH); // tall part at the back: sitter faces the front
	private static final BlockState BACK = b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
			.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "bench_cherry_planters";
	}

	@Override
	public Family family() {
		return Family.BENCH;
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
		return 2;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		boolean left;
		boolean right;
		if (w >= 4) {
			left = true;
			right = true;
		} else if (w == 3) {
			left = p.hash(0, 0, 0, 2) == 0;
			right = !left;
		} else {
			left = false;
			right = false;
		}
		int s0 = left ? 1 : 0;
		int s1 = right ? w - 2 : w - 1;
		int lanternSide = p.hash(1, 0, 0, 2); // which planter gets the lantern (when both exist)

		// --- seat and backrest
		for (int u = s0; u <= s1; u++) {
			p.set(c, u, 0, 0, SEAT);
			p.set(c, u, 1, 0, BACK);
		}

		// --- planter boxes at the ends, full depth
		if (left) {
			planter(c, p, 0, d, !right || lanternSide == 0);
		}
		if (right) {
			planter(c, p, w - 1, d, !left || lanternSide == 1);
		}

		// --- hedge behind the seat: tall row right behind, low petals at the very back
		if (d >= 2) {
			for (int u = s0; u <= s1; u++) {
				p.set(c, u, 0, 1, hedge(p, u, 0, 1));
				if (p.h() >= 3 && p.hash(u, 1, 1, 3) != 0) {
					p.set(c, u, 1, 1, hedge(p, u, 1, 1)); // [depth] a ragged top, not a flat wall
				}
			}
		}
		if (d >= 3) {
			for (int u = s0; u <= s1; u++) {
				p.set(c, u, 0, 2, p.hash(u, 0, 2, 3) == 0 ? b(Blocks.MOSS_CARPET)
						: b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 1, 2, 4))
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			}
		}
	}

	/** A dark planter column at {@code u}: box at y0 over the whole depth, azalea on top, maybe a lantern. */
	private static void planter(CitadelCanvas c, Plot p, int u, int d, boolean lantern) {
		for (int v = 0; v < d; v++) {
			BlockState box = switch (p.hash(u, 0, v, 4)) {
				case 0 -> b(Blocks.DEEPSLATE_TILES);
				case 1 -> b(Blocks.CRACKED_DEEPSLATE_TILES);
				default -> b(Blocks.POLISHED_DEEPSLATE);
			};
			if (d == 3 && v == 0) {
				// [depth] deep plot: the front of the planter is a low lip, the box steps up behind it
				p.set(c, u, 0, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				continue;
			}
			p.set(c, u, 0, v, box);
			if (v <= 1) { // the crown: the two front-most boxes carry foliage, a third one stays bare
				boolean bush = v == (d == 3 ? 1 : 0);
				p.set(c, u, 1, v, bush ? b(Blocks.FLOWERING_AZALEA) : leaves(Blocks.AZALEA_LEAVES));
				if (bush && lantern && p.h() >= 3) {
					p.set(c, u, 2, v, LANTERN); // [finish] a lantern standing on the bush: light at sitting height
				}
			}
		}
	}

	private static BlockState hedge(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 3) == 0 ? leaves(Blocks.CHERRY_LEAVES) : leaves(Blocks.FLOWERING_AZALEA_LEAVES);
	}
}
