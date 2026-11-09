package dev.t3craft.hq.citadel.catalog.gazebo;

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
 * Concept: an octagonal cherry gazebo in the manner of a Japanese tea pavilion. A dark deepslate
 * plinth rises one step (deepslate-brick stairs all round, climbing inwards) to a parquet of cherry
 * planks with a star of stripped-cherry radials, bamboo-mosaic diagonals and an end-grain cherry boss
 * in the middle, bordered by polished deepslate. Eight stripped-cherry columns stand on chiselled
 * deepslate bases on the octagon; cherry stair benches (backs outwards) fill the faces between them,
 * except the front face, which is the entrance. Upside-down cherry stairs hang between the column
 * heads like a valance, and outside the columns more upside-down stairs act as brackets under the
 * eave. The roof is concave, like a temple hat: a wide flaring brim of dark deepslate-tile stairs
 * (the eave runs right out to the step below), then cherry stairs rising ever steeper to a little
 * cap, a gold ball finial and, with room, a copper rod. Inside, the roof is hollow and a chain drops
 * a lantern into the middle of the pavilion. The plot corners outside the octagon get pots and petals.
 *
 * <p>Palette, dark → light, bottom → top: deepslate bricks / polished and chiselled deepslate
 * (plinth, border, bases) → cherry planks, bamboo mosaic, stripped cherry (floor, columns, benches)
 * → deepslate-tile brim → cherry roof → gold and copper finial.
 *
 * <pre>
 *  section (9 wide, h 9)              plan (9 × 9), front at the bottom
 *        ╿   copper rod                    ·  ✿ ◢ ▄ ◣ ✿  ·
 *        ●   gold ball                     ◢ ▄ ◆ ◣ ◆ ▄ ◣       ◆ column   ◣ bench
 *       ▟█▙  cap                           ▄ ◆ ▒ ▬ ▒ ◆ ▄       ▬ radial   ▒ bamboo
 *     ▄▟▀ ▀▙▄  steepening cherry           ▄ ◣ ▬ ● ▬ ◢ ▄       ● cherry boss
 *  ▄▄▀▀  ¦  ▀▀▄▄  dark brim, brackets      ▄ ◆ ▒ ▬ ▒ ◆ ▄
 *   ║  ◣ o ◢  ║   columns, benches         ◢ ▄ ◆   ◆ ▄ ◣       entrance at the front
 *  ▀▀▀▀▀▀▀▀▀▀▀▀▀  step and plinth          ·  ✿ ◥ ▀ ◤ ✿  ·
 * </pre>
 *
 * Size: 7–9 × 7–9 × 8+ (the octagon follows the smaller side; on 9 × 9 with height 9 the roof gains a
 * layer, and any spare layer above the gold ball takes a copper rod).
 */
public final class GazeboCherryOctagon implements Variant {
	private static final BlockState HANGING_LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);

	@Override
	public String id() {
		return "gazebo_cherry_octagon";
	}

	@Override
	public Family family() {
		return Family.GAZEBO;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	/** Octagonal distance: its level sets are regular octagons with apothem {@code m}. */
	private static double oct(int dx, int dz) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		return Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2));
	}

	/** The dominant direction from the centre towards {@code (dx, dz)}. */
	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** The dominant direction from {@code (dx, dz)} back towards the centre (stairs climb this way). */
	private static Direction in(int dx, int dz) {
		return out(dx, dz).getOpposite();
	}

	private static BlockState log(Block blk, Direction.Axis axis) {
		return blk.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState upside(Block blk, Direction facing) {
		return stairs(blk, facing).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		// apothem of the octagon: 3.5 on 7–8, 4.5 on 9 (the smaller side wins)
		double a0 = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv)) + 0.5;
		int k = (int) Math.round(a0 - 1.5); // column ring: 2 or 3
		int t = (int) Math.round(0.4142 * k); // offset of the two columns on each face: 1
		int jTop = Math.max(2, Math.min(a0 > 4.0 ? 3 : 2, p.h() - 6)); // roof layers above the brim
		double[] rho = new double[jTop + 1];
		for (int j = 0; j <= jTop; j++) {
			// [finish] concave profile: the radius shrinks fast at the brim and slowly near the top
			rho[j] = 1.0 + (a0 - 1.0) * Math.pow(1.0 - (double) j / jTop, 1.7);
		}

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double m = oct(dx, dz);
				if (m > a0) {
					corner(c, p, u, v);
					continue;
				}
				boolean rim = m > k + 0.5;
				if (rim) {
					// [depth] the step up to the plinth, climbing inwards
					p.set(c, u, 0, v, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, in(dx, dz)));
					if (nextToColumn(dx, dz, k, t)) {
						// brackets under the brim, leaning on the column
						p.set(c, u, 3, v, upside(Blocks.CHERRY_STAIRS, in(dx, dz)));
					}
				} else {
					floor(c, p, u, v, dx, dz, m, k, t);
					if (m > k - 0.5) {
						ring(c, p, u, v, dx, dz, k, t);
					}
				}
				roof(c, p, u, v, m, k, a0, rho, jTop);
			}
		}

		// cap, finial and the lantern on its chain inside the hollow roof
		int capY = 4 + jTop;
		p.set(c, cu, capY + 1, cv, b(Blocks.GOLD_BLOCK));
		if (capY + 2 < p.h()) {
			p.set(c, cu, capY + 2, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		}
		for (int y = 4; y < capY; y++) {
			p.put(c, cu, y, cv, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
		}
		p.put(c, cu, 3, cv, HANGING_LANTERN, CitadelCanvas.HANGING);
	}

	private static boolean isColumn(int dx, int dz, int k, int t) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		return (ax == k && az == t) || (ax == t && az == k);
	}

	private static boolean nextToColumn(int dx, int dz, int k, int t) {
		return isColumn(dx - 1, dz, k, t) || isColumn(dx + 1, dz, k, t) || isColumn(dx, dz - 1, k, t) || isColumn(dx, dz + 1, k, t);
	}

	// ------------------------------------------------------------------ plinth and parquet (y0)

	/**
	 * [palette] a polished-deepslate border on the column ring (cracked tiles mixed in, chiselled
	 * under the columns), inside it a cherry parquet: stripped-cherry radials on the axes, bamboo
	 * mosaic on the diagonals and an end-grain cherry boss under the lantern.
	 */
	private static void floor(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double m, int k, int t) {
		BlockState s;
		if (m > k - 0.5) {
			int h = p.hash(u, 0, v, 6);
			s = isColumn(dx, dz, k, t) ? b(Blocks.CHISELED_DEEPSLATE)
					: h == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : h == 1 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE);
		} else if (dx == 0 && dz == 0) {
			s = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		} else if (dx == 0 || dz == 0) {
			s = log(Blocks.STRIPPED_CHERRY_LOG, dz == 0 ? Direction.Axis.X : Direction.Axis.Z);
		} else if (Math.abs(dx) == Math.abs(dz)) {
			s = b(Blocks.BAMBOO_MOSAIC);
		} else {
			s = b(Blocks.CHERRY_PLANKS);
		}
		p.set(c, u, 0, v, s);
	}

	// ------------------------------------------------------------------ columns, benches, valance (y1..y3)

	private static void ring(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, int k, int t) {
		if (isColumn(dx, dz, k, t)) {
			p.set(c, u, 1, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 2, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 3, v, b(Blocks.STRIPPED_CHERRY_WOOD)); // capital
			return;
		}
		boolean entrance = dz < 0 && Math.abs(dz) == k; // the front face stays open
		if (!entrance) {
			p.set(c, u, 1, v, stairs(Blocks.CHERRY_STAIRS, out(dx, dz))); // bench, back outwards
		}
		// [depth] the valance between the column heads, its drop on the outside
		p.set(c, u, 3, v, upside(Blocks.CHERRY_STAIRS, out(dx, dz)));
	}

	// ------------------------------------------------------------------ concave roof (y4..)

	/**
	 * Layer {@code j} (at {@code y = 4 + j}) covers the octagon of radius {@code rho[j]}; where the next
	 * layer does not cover it the cell is a stair climbing inwards, else cherry planks, one ring thick
	 * so the roof stays hollow inside. The brim and the first ring of layer 0 are dark deepslate tile.
	 */
	private static void roof(CitadelCanvas c, Plot p, int u, int v, double m, int k, double a0, double[] rho, int jTop) {
		int dx = u - p.w() / 2;
		int dz = v - p.d() / 2;
		for (int j = 0; j <= jTop; j++) {
			int y = 4 + j;
			boolean last = j == jTop;
			if (m > rho[j] || (!last && m <= rho[j + 1] - 1.0)) {
				continue;
			}
			boolean exposed = last ? m > 0.5 : m > rho[j + 1];
			if (!exposed) {
				p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
				continue;
			}
			Block mat;
			if (j == 0 && m > k + 0.5) {
				// the brim: dark tile, a few cracked-look polished-deepslate stairs mixed in
				mat = p.hash(u, y, v, 7) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
			} else {
				mat = Blocks.CHERRY_STAIRS;
			}
			p.set(c, u, y, v, stairs(mat, in(dx, dz)));
		}
	}

	// ------------------------------------------------------------------ plot corners

	private static void corner(CitadelCanvas c, Plot p, int u, int v) {
		switch (p.hash(u, 0, v, 6)) {
			case 0 -> p.set(c, u, 0, v, b(Blocks.POTTED_PINK_TULIP));
			case 1 -> p.set(c, u, 0, v, b(Blocks.POTTED_FLOWERING_AZALEA));
			case 2, 3 -> p.set(c, u, 0, v, b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 1, v, 4))
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(p.hash(u, 2, v, 4))));
			default -> {
			}
		}
	}
}
