package dev.t3craft.hq.citadel.catalog.gazebo;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
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
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Concept: the patio's sitting place, an octagonal cherry tea pavilion. The plan is a true octagon
 * that grows with the plot (apothem 3.5 on 7, 6.5 on 13) and is built in concentric rings:
 * <ol>
 * <li><b>step ring</b>, a single course of deepslate-brick stairs climbing inwards (the whole base is one
 *     block high; at the entrances it is the walk-up step, head on, nothing to jump);</li>
 * <li><b>post ring</b>: eight cherry posts (chiselled-deepslate base, two logs, stripped capital) at the
 *     octagon's vertices, a low cherry fence between them (flower pots on top, lanterns hanging over) and
 *     open doorways, 3 to 5 wide, on the front and back faces (and, on big plots and by seed, on the sides);</li>
 * <li><b>bench ring</b>: a continuous cherry-stair bench, back outwards, broken only by the doorways;</li>
 * <li><b>centre</b>: a low cherry table (or a jukebox, or a lectern) under a lantern on a chain.</li>
 * </ol>
 * The seed picks the use: tea (table with a candle, cushions, a barrel of leaves), music (jukebox and
 * note blocks) or reading (lectern and chiselled bookshelves), and the roof: one hip, or on tall plots
 * two tiers with a glazed drum between them. Clear height under the eave is 4, brackets of upside-down
 * stairs sit under the eave, the eave edge is dark deepslate tile and the finial is gold (plus a copper
 * rod or a lantern).
 *
 * <p>Palette, dark → light, bottom → top: deepslate bricks / polished / chiselled (base, bases) →
 * cherry (parquet, posts, benches, fence, roof) with a bamboo-mosaic accent → deepslate-tile eave →
 * gold finial.
 *
 * <pre>
 *  section (13 wide, two tiers)               plan (9 × 9), front at the bottom
 *         ╿  ●          finial                     ▒ ▒ ▒ ▒ ▒ ▒ ▒
 *        ▟█▙            upper roof                ▒ ◆ ═ ░ ═ ◆ ▒      ◆ post   ═ fence
 *       ▄▌░▐▄           drum (glass)              ▒ ░ ▭ ▭ ▭ ░ ▒      ▭ bench  ░ doorway
 *    ▄▄▟▀▀▀▀▀▙▄▄        lower roof                ▒ ═ ▭ ▣ ▭ ═ ▒      ▣ table
 *  ▄▀▀  ¦ ¦ ¦ ¦ ▀▀▄     dark eave, brackets       ▒ ◆ ═ ░ ═ ◆ ▒
 *    ║ ▭   ▣   ▭ ║     posts, benches, table     ▒ ▒ ▒ ░ ▒ ▒ ▒      steps at the doorways
 *  ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀    one-block base
 * </pre>
 *
 * Size: 7–13 × 7–13 × 9+ (the octagon follows the smaller side; the rest of the plot gets flowers; the
 * two-tier roof needs height 12+, i.e. the scale-200 plots).
 */
public final class GazeboCherryOctagon implements Variant {
	private static final BlockState HANGING_LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	private static final BlockState CUSHION = Blocks.CARPET.pick(DyeColor.PINK).defaultBlockState();
	private static final BlockState PINK_GLASS = Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState();
	private static final Direction[] HORIZ = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	/** First layer of the roof (the brim); the clear height under it is 4. */
	private static final int ROOF_Y = 5;

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
		return 9;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 13;
	}

	// ------------------------------------------------------------------ geometry

	/**
	 * The plan. {@code r} is the half side (square apothem), {@code s} the largest {@code |dx|+|dz|}, so the
	 * octagon is {@code |dx|,|dz| <= r and |dx|+|dz| <= s}. Shrinking both by one gives the next ring in.
	 * {@code lf}: half the length of the straight part of the post ring's face (the posts stand at its ends);
	 * {@code g}: half the width of a doorway.
	 */
	private record Geo(int r, int s, int lf, int g, boolean sideEntry) {
		static boolean inside(int ax, int az, int rr, int ss) {
			return rr >= 0 && ax <= rr && az <= rr && ax + az <= ss;
		}

		/** 0 outside, 1 step ring, 2 post ring, 3 bench ring, 4 centre. */
		int ring(int dx, int dz) {
			int ax = Math.abs(dx);
			int az = Math.abs(dz);
			if (!inside(ax, az, r, s)) {
				return 0;
			}
			for (int i = 1; i <= 3; i++) {
				if (!inside(ax, az, r - i, s - i)) {
					return i;
				}
			}
			return 4;
		}

		boolean post(int dx, int dz) {
			int ax = Math.abs(dx);
			int az = Math.abs(dz);
			return (ax == lf && az == r - 1) || (ax == r - 1 && az == lf);
		}

		/** The doorway sectors: front and back always, left and right on big plots by seed. */
		boolean aisle(int dx, int dz) {
			int ax = Math.abs(dx);
			int az = Math.abs(dz);
			return (az > ax && ax <= g) || (sideEntry && ax > az && az <= g);
		}

		/** Low fence between the posts (not on the smallest plot, where the benches take that ring). */
		boolean rail(int dx, int dz) {
			return r >= 4 && ring(dx, dz) == 2 && !post(dx, dz) && !aisle(dx, dz);
		}

		boolean benchOnPostRing(int dx, int dz) {
			return r == 3 && ring(dx, dz) == 2 && !post(dx, dz) && !aisle(dx, dz);
		}

		/** The two seats at the back corners of the bench ring that hold the use's furniture. */
		boolean special(int dx, int dz) {
			return dz == r - 2 && Math.abs(dx) == lf;
		}

		boolean bench(int dx, int dz) {
			return r >= 4 && ring(dx, dz) == 3 && !aisle(dx, dz) && !special(dx, dz);
		}
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

	private static BlockState pot(int h) {
		return h == 0 ? b(Blocks.POTTED_PINK_TULIP) : b(Blocks.POTTED_FLOWERING_AZALEA);
	}

	// ------------------------------------------------------------------ build

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		int r = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv));
		double a0 = r + 0.5;
		int s = (int) Math.floor(a0 * Math.sqrt(2) + 1e-9);
		int lf = s - r;
		boolean sideEntry = r >= 5 && p.hash(2, 2, 2, 2) == 1;
		Geo geo = new Geo(r, s, lf, lf - 1, sideEntry);
		int use = p.hash(0, 0, 0, 3); // 0 tea, 1 music, 2 reading

		// roof profile
		int layers = p.h() - ROOF_Y; // free layers for roof and finial
		boolean twoTier = r >= 4 && layers >= 7 && p.hash(1, 1, 1, 2) == 1;
		double[] rho;
		int drum = -1;
		if (twoTier) {
			double rd = Math.max(1.5, Math.min(3.0, a0 * 0.42));
			int nLow = layers >= 8 ? 3 : 2;
			int nUp = Math.min(4, layers - 2 - nLow);
			rho = new double[nLow + 1 + nUp];
			for (int j = 0; j < nLow; j++) {
				rho[j] = rd + (a0 - rd) * Math.pow(1.0 - (double) j / nLow, 1.4);
			}
			drum = nLow;
			rho[drum] = rd;
			for (int k = 0; k < nUp; k++) {
				rho[nLow + 1 + k] = 1.0 + (rd + 0.2) * Math.pow(1.0 - (double) k / (nUp - 1), 1.4);
			}
		} else {
			int n = Math.min(layers - 1, Math.max(3, (int) Math.ceil(a0 - 0.5)));
			rho = new double[n];
			for (int j = 0; j < n; j++) {
				rho[j] = 1.0 + (a0 - 1.0) * Math.pow(1.0 - (double) j / (n - 1), 1.6);
			}
		}
		int n = rho.length;

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				int ring = geo.ring(dx, dz);
				if (ring == 0) {
					outside(c, p, u, v, dx, dz, geo);
					continue;
				}
				switch (ring) {
					case 1 -> stepRing(c, p, u, v, dx, dz, geo);
					case 2 -> postRing(c, p, u, v, dx, dz, geo, use);
					default -> inner(c, p, u, v, dx, dz, geo, use, ring);
				}
				roof(c, p, u, v, dx, dz, oct(dx, dz), rho, drum, r >= 4);
			}
		}

		centre(c, p, cu, cv, geo, use);

		// finial and the lantern on its chain inside the hollow roof
		int capY = ROOF_Y + n - 1;
		p.set(c, cu, capY + 1, cv, b(Blocks.GOLD_BLOCK));
		if (capY + 2 < p.h()) {
			p.set(c, cu, capY + 2, cv, p.hash(3, 3, 3, 2) == 0 ? Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState() : b(Blocks.LANTERN));
		}
		for (int y = 4; y < capY; y++) {
			p.put(c, cu, y, cv, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
		}
		p.put(c, cu, 3, cv, HANGING_LANTERN, CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ step ring (y0, y4)

	/**
	 * [depth] The base is one course of deepslate-brick stairs climbing inwards: at the doorways it is the
	 * step (first step head on, then the floor), elsewhere a ledge outside the fence. Under the eave the
	 * brackets (upside-down cherry stairs) lean on the posts, and the lanterns hang at the corners.
	 */
	private static void stepRing(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, Geo geo) {
		Block mat = p.hash(u, 0, v, 5) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_BRICK_STAIRS;
		p.set(c, u, 0, v, stairs(mat, in(dx, dz)));
		boolean bracket = false;
		boolean lamp = false;
		for (Direction dir : HORIZ) {
			int nx = dx + dir.getStepX();
			int nz = dz + dir.getStepZ();
			if (geo.post(nx, nz)) {
				Direction od = out(nx, nz);
				if (nx + od.getStepX() == dx && nz + od.getStepZ() == dz) {
					bracket = true;
				} else {
					lamp = true;
				}
			}
		}
		if (bracket) {
			p.set(c, u, 4, v, upside(Blocks.CHERRY_STAIRS, in(dx, dz)));
		} else if (lamp) {
			p.put(c, u, 4, v, HANGING_LANTERN, CitadelCanvas.HANGING);
		} else if (!geo.aisle(dx, dz)) {
			// [palette] a dark deepslate-tile fascia under the pink eave (the doorways stay clear)
			p.set(c, u, 4, v, upside(p.hash(u, 4, v, 4) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, out(dx, dz)));
		}
	}

	// ------------------------------------------------------------------ post ring (y0..y4)

	private static void postRing(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, Geo geo, int use) {
		if (geo.post(dx, dz)) {
			// [palette] dark base, bark-dark cherry shaft, light capital
			p.set(c, u, 0, v, b(Blocks.CHISELED_DEEPSLATE));
			p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 2, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 3, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, 4, v, b(Blocks.CHERRY_WOOD));
			return;
		}
		int h = p.hash(u, 0, v, 6);
		p.set(c, u, 0, v, h == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : h == 1 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE));
		if (geo.rail(dx, dz)) {
			boolean north = connects(geo, dx, dz - 1);
			boolean east = connects(geo, dx + 1, dz);
			boolean south = connects(geo, dx, dz + 1);
			boolean west = connects(geo, dx - 1, dz);
			p.set(c, u, 1, v, b(Blocks.CHERRY_FENCE).setValue(BlockStateProperties.NORTH, north).setValue(BlockStateProperties.EAST, east)
					.setValue(BlockStateProperties.SOUTH, south).setValue(BlockStateProperties.WEST, west));
			if (p.hash(u, 2, v, 3) == 0) {
				p.set(c, u, 2, v, pot(p.hash(u, 3, v, 2)));
			} else if (p.hash(u, 4, v, 3) == 1) {
				p.put(c, u, 4, v, HANGING_LANTERN, CitadelCanvas.HANGING);
			}
		} else if (geo.benchOnPostRing(dx, dz)) {
			p.set(c, u, 1, v, stairs(Blocks.CHERRY_STAIRS, out(dx, dz)));
		}
	}

	private static boolean connects(Geo geo, int dx, int dz) {
		return geo.post(dx, dz) || geo.rail(dx, dz);
	}

	// ------------------------------------------------------------------ bench ring and parquet (y0, y1)

	/** [palette] cherry parquet: stripped-cherry radials on the axes, bamboo mosaic on the diagonals, end-grain boss. */
	private static void inner(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, Geo geo, int use, int ring) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		BlockState f;
		if (dx == 0 && dz == 0) {
			f = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		} else if (dx == 0 || dz == 0) {
			f = log(Blocks.STRIPPED_CHERRY_LOG, dz == 0 ? Direction.Axis.X : Direction.Axis.Z);
		} else if (ax == az) {
			f = b(Blocks.BAMBOO_MOSAIC);
		} else {
			f = b(Blocks.CHERRY_PLANKS);
		}
		p.set(c, u, 0, v, f);
		if (ring != 3) {
			return;
		}
		if (geo.special(dx, dz)) {
			switch (use) {
				case 0 -> p.set(c, u, 1, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
				case 1 -> p.set(c, u, 1, v, b(Blocks.NOTE_BLOCK));
				default -> p.set(c, u, 1, v, b(Blocks.CHISELED_BOOKSHELF).setValue(BlockStateProperties.HORIZONTAL_FACING, in(dx, dz)));
			}
		} else if (geo.bench(dx, dz)) {
			p.set(c, u, 1, v, stairs(Blocks.CHERRY_STAIRS, out(dx, dz))); // bench, back outwards
		}
	}

	/** The table in the middle (3 long on big plots), what is on it, and the cushions of the tea use. */
	private static void centre(CitadelCanvas c, Plot p, int cu, int cv, Geo geo, int use) {
		int tx = geo.r() >= 5 ? 1 : 0;
		for (int i = -tx; i <= tx; i++) {
			if (i == 0) {
				switch (use) {
					case 0 -> {
						p.set(c, cu, 1, cv, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
						p.set(c, cu, 2, cv, b(Blocks.POTTED_PINK_TULIP));
					}
					case 1 -> p.set(c, cu, 1, cv, b(Blocks.JUKEBOX));
					default -> p.set(c, cu, 1, cv, b(Blocks.LECTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				}
			} else {
				p.set(c, cu + i, 1, cv, CitadelCanvas.slab(Blocks.CHERRY_SLAB)); // the low wings of the table: a walkable half step
			}
			if (use == 0) {
				p.set(c, cu + i, 1, cv - 1, CUSHION); // cushions
				p.set(c, cu + i, 1, cv + 1, CUSHION);
			}
		}
	}

	// ------------------------------------------------------------------ roof (y5..)

	/**
	 * Layer {@code j} (at {@code y = 5 + j}) covers the octagon of radius {@code rho[j]}; where the next layer
	 * does not cover it the cell is a stair climbing inwards, else cherry planks, one ring thick so the roof
	 * stays hollow inside. The outer ring of a brim is dark deepslate tile. Layer {@code drum} (if any) is a
	 * glazed wall of one ring, the tier break of a two-tier roof.
	 */
	private static void roof(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double m, double[] rho, int drum, boolean ribs) {
		int n = rho.length;
		for (int j = 0; j < n; j++) {
			int y = ROOF_Y + j;
			if (j == drum) {
				if (m <= rho[j] && m > rho[j] - 1.0) {
					p.set(c, u, y, v, dx == 0 || dz == 0 ? PINK_GLASS : b(Blocks.STRIPPED_CHERRY_WOOD));
				}
				continue;
			}
			boolean last = j == n - 1;
			double inner = last ? -1.0 : rho[j + 1] - 1.0;
			if (drum >= 0 && j == drum + 1) {
				inner = Math.min(inner, rho[drum] - 1.0); // the upper brim closes over the whole drum
			}
			if (m > rho[j] || (!last && m <= inner)) {
				continue;
			}
			boolean exposed = last ? m > 0.5 : m > rho[j + 1];
			if (!exposed) {
				p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
				continue;
			}
			p.set(c, u, y, v, stairs(Blocks.CHERRY_STAIRS, in(dx, dz)));
		}
	}

	// ------------------------------------------------------------------ rest of the plot

	/** Flowers and shrubs on the plot outside the octagon; the approach to every doorway stays clear. */
	private static void outside(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, Geo geo) {
		if (geo.aisle(dx, dz)) {
			return;
		}
		switch (p.hash(u, 0, v, 8)) {
			case 0 -> p.set(c, u, 0, v, b(Blocks.POTTED_PINK_TULIP));
			case 1 -> p.set(c, u, 0, v, b(Blocks.POTTED_FLOWERING_AZALEA));
			case 2, 3 -> p.set(c, u, 0, v, b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 1, v, 4))
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(p.hash(u, 2, v, 4))));
			case 4 -> p.set(c, u, 0, v, b(Blocks.FLOWERING_AZALEA));
			default -> {
			}
		}
	}
}
