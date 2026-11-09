package dev.t3craft.hq.citadel.catalog.statue;

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

/**
 * Concept: a memorial needle on the rim of the promenade, lit by four braziers. A slender one-block
 * obelisk of pale ashlar with dark bands rises from a low stepped foot (a plus of plinth blocks with stair
 * shoulders), carries a name plaque on the front of its chiselled die and ends in a gilded capstone with
 * a copper rod. A campfire (soul campfire on some seeds) burns on a stone pedestal at each corner of the
 * foot, so the promenade is lit from four sides without any flammable block touching a fire.
 *
 * <ul>
 * <li><b>Scale</b>: the foot is at most two courses high and the whole piece fits a 3 × 3 core (the
 *     shaft stands on the middle cell, which is where the hub places a statue's centre); a 5 × 5 plot adds
 *     an outer ring with the braziers and a seat on every side; a 4-wide plot gets benches on its spare
 *     strip. The shaft grows with the height, up to 12 courses.</li>
 * <li><b>Palette</b>, by seed: pale quartz bricks, calcite or dark polished deepslate for the shaft, always
 *     with bands of the opposite tone, deepslate for the foot, gold on the point. The plaque is a wall sign
 *     (wood by seed); the fire is a campfire or a soul campfire.</li>
 * <li><b>Silhouette</b>: a needle at the centre of a low, wide cross with four lights, nothing like the
 *     monumental lantern or the guardian.</li>
 * </ul>
 *
 * <p>Size: 2–5 × 2–5 × 8+ (2 × 2 on a scale-1 hub, 3 × 3 and 4 × 4 × 11 on the default scale).
 */
public final class StatueObeliskBraziers implements Variant {
	/** Shaft, band and die by seed. */
	private record Stone(Block body, Block band, Block foot, Block step) {
	}

	private static final Stone[] STONES = {
			new Stone(Blocks.QUARTZ_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICK_STAIRS),
			new Stone(Blocks.CALCITE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS),
			new Stone(Blocks.POLISHED_DEEPSLATE, Blocks.CALCITE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS),
	};
	private static final Block[] SIGNS = {Blocks.DARK_OAK_WALL_SIGN, Blocks.CHERRY_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN,
			Blocks.MANGROVE_WALL_SIGN};

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
		return 2;
	}

	@Override
	public int minD() {
		return 2;
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
		int cu = (w - 1) / 2; // the shaft's cell: where the hub puts a statue's centre
		int cv = (d - 1) / 2;
		int r = Math.min(2, Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv)));
		Stone st = STONES[p.hash(1, 0, 0, STONES.length)];
		boolean soul = p.hash(2, 0, 0, 2) == 1;
		Block sign = SIGNS[p.hash(3, 0, 0, SIGNS.length)];
		int top = Math.min(p.h() - 1, 11);

		// --- the needle
		for (int y = 0; y <= top - 2; y++) {
			p.set(c, cu, y, cv, shaft(st, p, cu, y, cv));
		}
		p.set(c, cu, top - 1, cv, b(Blocks.GOLD_BLOCK)); // [finish] the gilded capstone
		p.set(c, cu, top, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());

		if (r == 0) {
			small(c, p, st, soul, sign, cu, cv);
			return;
		}

		// --- the foot: a plus of plinth blocks (the front one carries the plaque) with stair shoulders
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if (i == 0 && j == 0) {
					continue;
				}
				int u = cu + i;
				int v = cv + j;
				boolean corner = i != 0 && j != 0;
				if (corner) {
					if (r == 1) {
						brazier(c, p, st, soul, u, v);
					} else {
						p.set(c, u, 0, v, b(st.foot));
					}
					continue;
				}
				p.set(c, u, 0, v, b(st.foot));
				if (!(i == 0 && j == -1)) {
					p.set(c, u, 1, v, stairs(st.step, toward(u, v, cu, cv))); // [depth] a shoulder
				}
			}
		}
		// the plaque, on the die, above the front plinth
		p.put(c, cu, 1, cv - 1, b(sign).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);

		if (r == 2) {
			// outer ring: a brazier on each corner, a seat on every other cell facing the shaft
			for (int i = -2; i <= 2; i++) {
				for (int j = -2; j <= 2; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != 2) {
						continue;
					}
					int u = cu + i;
					int v = cv + j;
					if (Math.abs(i) == 2 && Math.abs(j) == 2) {
						brazier(c, p, st, soul, u, v);
					} else {
						p.set(c, u, 0, v, stairs(st.step, toward(u, v, cu, cv)));
					}
				}
			}
		} else {
			// spare strips of a 4-wide plot: a bench on each, back to the shaft
			if (cu + 2 < w) {
				for (int j = -1; j <= 1; j++) {
					p.set(c, cu + 2, 0, cv + j, stairs(st.step, Direction.WEST));
				}
			}
			if (cv + 2 < d) {
				for (int i = -1; i <= 1; i++) {
					p.set(c, cu + i, 0, cv + 2, stairs(st.step, Direction.NORTH));
				}
			}
		}
	}

	/** The 2-wide plots of a scale-1 hub: the needle in the corner cell, a brazier on the far diagonal, seats between. */
	private static void small(CitadelCanvas c, Plot p, Stone st, boolean soul, Block sign, int cu, int cv) {
		boolean placed = false;
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				int u = cu + i;
				int v = cv + j;
				if ((i == 0 && j == 0) || !p.has(u, 0, v)) {
					continue;
				}
				if (i != 0 && j != 0) {
					brazier(c, p, st, soul, u, v);
				} else {
					p.set(c, u, 0, v, stairs(st.step, toward(u, v, cu, cv)));
					if (!placed && j == 0 && p.has(u, 1, v)) {
						Direction side = i > 0 ? Direction.EAST : Direction.WEST;
						p.put(c, u, 1, v, b(sign).setValue(BlockStateProperties.HORIZONTAL_FACING, side), CitadelCanvas.DECOR);
						placed = true;
					}
				}
			}
		}
	}

	/** [finish] a campfire (soul campfire by seed) on a stone pedestal: contained fire, nothing flammable next to it. */
	private static void brazier(CitadelCanvas c, Plot p, Stone st, boolean soul, int u, int v) {
		p.set(c, u, 0, v, b(st.foot));
		p.set(c, u, 1, v, soul ? b(Blocks.SOUL_CAMPFIRE) : b(Blocks.CAMPFIRE));
	}

	/** The stair direction that climbs towards the shaft. */
	private static Direction toward(int u, int v, int cu, int cv) {
		int dx = cu - u;
		int dz = cv - v;
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** [palette] plinth, chiselled die, then pale ashlar with a dark band every third course. */
	private static BlockState shaft(Stone st, Plot p, int u, int y, int v) {
		if (y == 0) {
			return b(st.foot);
		}
		if (y == 1) {
			return b(Blocks.CHISELED_DEEPSLATE); // the die: the inscribed block
		}
		if ((y - 2) % 3 == 2) {
			return b(st.band);
		}
		return b(st.body);
	}
}
