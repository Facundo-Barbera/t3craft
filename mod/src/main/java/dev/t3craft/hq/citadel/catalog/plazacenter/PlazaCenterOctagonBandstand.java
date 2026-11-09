package dev.t3craft.hq.citadel.catalog.plazacenter;

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
 * An octagonal music pavilion you can walk into and use. A dark deepslate platform one block high (two
 * from 17 wide) is climbed on all four axes by steps of stairs (3 or 5 wide, no jumps). On it, eight
 * stripped-cherry columns on polished-deepslate bases carry a beam; a cherry balustrade joins them and
 * stays open on the four axes, so the pavilion is crossed from any side. Under an octagonal hip roof of
 * stairs (real pitch, eave, dark hip ribs) the stage is a concentric parquet of cherry and bamboo with a
 * jukebox (record in) between note blocks, a ring of musicians' benches with lecterns (music stands)
 * against the balustrade, potted saplings in the front and a hanging lantern.
 * Outside, in the four corners of the plot: cherry benches facing the pavilion with azalea hedges
 * behind, lamp posts, and (bigger plots) mossy flower beds.
 *
 * <p>By seed: roof of slate, oxidised copper or cherry, and the finial (a lantern on a post or a gold
 * orb on a lightning-rod spire).
 *
 * <pre>
 *  side view                              plan (one quadrant)
 *        ╿ finial                          ▒▒ steps (axis)   ◆ column   ░ rail   s seat  L lectern
 *      ▗▄▙▟▄▖  hip roof                     ┌─────────────
 *     ▟▌     ▐▙ eave                        │ ·  ·  ◆ ░ (open at the axis)
 *     ▐▌ ║ ║ ▐▌ columns, open rails         │ ·  L s ░
 *   ▄▄▟▛▔▔▔▔▔▜▙▄▄ platform + steps          │ ▒▒ ▒▒  ◆
 * </pre>
 *
 * Size: 13–21 × 13–21 × 10+ (grows with the scale like every plaza centre).
 */
public final class PlazaCenterOctagonBandstand implements Variant {
	@Override
	public String id() {
		return "plaza_center_octagon_bandstand";
	}

	@Override
	public Family family() {
		return Family.PLAZA_CENTER;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 10;
	}

	@Override
	public int maxW() {
		return 21;
	}

	@Override
	public int maxD() {
		return 21;
	}

	// ------------------------------------------------------------------ geometry

	/** Octagonal distance: level sets are regular octagons with apothem {@code m}. */
	private static double oct(int dx, int dz) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		return Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2));
	}

	/** The local side a cell lies on (ties go to x). */
	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static BlockState log(Block blk, Direction.Axis axis) {
		return blk.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState topStairs(Block blk, Direction facing) {
		return stairs(blk, facing).setValue(BlockStateProperties.HALF, Half.TOP);
	}

	/**
	 * @param cu centre column, cv centre row (local)
	 * @param n  free half-extent in every direction from the centre
	 * @param h  platform height (1, or 2 from n = 8)
	 * @param pl platform apothem (octagon metric)
	 * @param k  radius of the column ring
	 * @param t  offset of the two columns on each face
	 * @param sw half-width of the steps (1 = 3 wide, 2 = 5 wide)
	 */
	private record Geo(int cu, int cv, int n, int h, int pl, int k, int t, int sw) {
		boolean ring(double m) {
			return m > k - 0.5 && m <= k + 0.5;
		}

		boolean column(int dx, int dz) {
			int ax = Math.abs(dx);
			int az = Math.abs(dz);
			return (ax == k && az == t) || (ax == t && az == k);
		}

		/** Balustrade: the ring minus the columns minus the openings on the four axes. */
		boolean rail(int dx, int dz) {
			return ring(oct(dx, dz)) && !column(dx, dz) && Math.min(Math.abs(dx), Math.abs(dz)) > 1;
		}

		boolean solid(int dx, int dz) {
			return column(dx, dz) || rail(dx, dz);
		}

		boolean interior(double m) {
			return m <= k - 0.5;
		}

		boolean centre(int dx, int dz) {
			return Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
		}

		/** A cell of the musicians' ring: inside, against the balustrade, not in an entrance lane. */
		boolean seat(int dx, int dz) {
			return interior(oct(dx, dz)) && Math.min(Math.abs(dx), Math.abs(dz)) >= 2
					&& (solid(dx + 1, dz) || solid(dx - 1, dz) || solid(dx, dz + 1) || solid(dx, dz - 1));
		}
	}

	/** The roof: material kind, step per layer, layers, finial. */
	private record Roof(int kind, int s, int layers, int fs, int fin) {
		Block stair(boolean rib, int hash) {
			if (rib) {
				return switch (kind) {
					case 0 -> Blocks.CHERRY_STAIRS;
					case 1 -> Blocks.DEEPSLATE_BRICK_STAIRS;
					default -> Blocks.DEEPSLATE_TILE_STAIRS;
				};
			}
			return switch (kind) {
				case 0 -> Blocks.DEEPSLATE_TILE_STAIRS;
				case 1 -> Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
				default -> Blocks.CHERRY_STAIRS;
			};
		}

		BlockState tread(boolean rib, int hash) {
			if (rib) {
				return switch (kind) {
					case 0 -> b(Blocks.CHERRY_PLANKS);
					case 1 -> b(Blocks.DEEPSLATE_BRICKS);
					default -> b(Blocks.DEEPSLATE_TILES);
				};
			}
			return switch (kind) {
				case 0 -> b(hash == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES);
				case 1 -> Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState();
				default -> b(Blocks.CHERRY_PLANKS);
			};
		}
	}

	private static void at(CitadelCanvas c, Plot p, Geo g, int dx, int y, int dz, BlockState s) {
		int u = g.cu() + dx;
		int v = g.cv() + dz;
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}

	private static void atLayer(CitadelCanvas c, Plot p, Geo g, int dx, int y, int dz, BlockState s, int layer) {
		int u = g.cu() + dx;
		int v = g.cv() + dz;
		if (p.has(u, y, v)) {
			p.put(c, u, y, v, s, layer);
		}
	}

	// ------------------------------------------------------------------ build

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		int n = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv));
		int ph = n >= 8 && p.h() >= 12 ? 2 : 1;
		int pl = n - ph;
		int k = pl - 1;
		int t = (int) Math.round(0.4142 * k);
		Geo g = new Geo(cu, cv, n, ph, pl, k, t, n >= 9 ? 2 : 1);

		// roof: as many layers as the height allows, flatter (steps of 2 or 3 cells) when it is short
		int lmax = p.h() - ph - 6;
		int s = 4;
		int layers = lmax;
		for (int cand = 1; cand <= 4; cand++) {
			int need = 1 + (k - 1 + cand - 1) / cand;
			if (need <= lmax) {
				s = cand;
				layers = need;
				break;
			}
		}
		layers = Math.max(1, layers);
		int fs = Math.max(1, Math.min(6, p.h() - 1 - (ph + 4 + layers)));
		Roof roof = new Roof(p.hash(1, 5, 3, 3), s, layers, fs, p.hash(1, 5, 5, 2));

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double m = oct(dx, dz);
				if (m <= pl + 0.5) {
					platform(c, p, g, u, v, dx, dz, m);
					frame(c, p, g, u, v, dx, dz, m);
				} else {
					steps(c, p, g, u, v, dx, dz);
				}
				roof(c, p, g, roof, u, v, dx, dz, m);
			}
		}
		finial(c, p, g, roof);
		stage(c, p, g);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				corner(c, p, g, sx, sz);
			}
		}
	}

	// ------------------------------------------------------------------ platform, steps

	private void platform(CitadelCanvas c, Plot p, Geo g, int u, int v, int dx, int dz, double m) {
		int top = g.h() - 1;
		for (int y = 0; y < top; y++) {
			p.set(c, u, y, v, p.hash(u, y, v, 5) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
		}
		BlockState s;
		int hs = p.hash(u, top, v, 6);
		if (m > g.k() + 0.5) {
			// the walk outside the balustrade: dark bricks, mixed
			s = hs == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : hs < 2 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
		} else if (g.ring(m)) {
			s = b(Blocks.POLISHED_DEEPSLATE);
		} else if (m <= 1.5) {
			s = b(Blocks.CHISELED_DEEPSLATE);
		} else if (m <= 2.5) {
			s = hs == 0 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : b(Blocks.CHERRY_PLANKS);
		} else {
			int band = (int) Math.floor(m - 2.5);
			s = (band & 1) == 0 ? b(Blocks.BAMBOO_MOSAIC) : hs == 0 ? log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y) : b(Blocks.CHERRY_PLANKS);
		}
		p.set(c, u, top, v, s);
	}

	private void steps(CitadelCanvas c, Plot p, Geo g, int u, int v, int dx, int dz) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		int a = Math.max(ax, az);
		int lat = Math.min(ax, az);
		int e = a - g.pl();
		if (lat > g.sw() || e < 1 || e > g.h()) {
			return;
		}
		int ys = g.h() - e;
		for (int y = 0; y < ys; y++) {
			p.set(c, u, y, v, b(Blocks.DEEPSLATE_BRICKS));
		}
		// facing = the way up: the tall half is towards the platform
		Block blk = p.hash(u, ys, v, 3) == 0 ? Blocks.POLISHED_ANDESITE_STAIRS : Blocks.STONE_BRICK_STAIRS;
		p.set(c, u, ys, v, stairs(blk, out(dx, dz).getOpposite()));
	}

	// ------------------------------------------------------------------ columns, rail, beam

	private BlockState fence(Geo g, int dx, int dz) {
		return b(Blocks.CHERRY_FENCE)
				.setValue(BlockStateProperties.NORTH, g.solid(dx, dz - 1))
				.setValue(BlockStateProperties.SOUTH, g.solid(dx, dz + 1))
				.setValue(BlockStateProperties.EAST, g.solid(dx + 1, dz))
				.setValue(BlockStateProperties.WEST, g.solid(dx - 1, dz));
	}

	private void frame(CitadelCanvas c, Plot p, Geo g, int u, int v, int dx, int dz, double m) {
		int y0 = g.h();
		int beam = y0 + 4;
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		if (g.column(dx, dz)) {
			// [depth] a dark base, two shafts and a beam-height capital
			p.set(c, u, y0, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, y0 + 1, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, y0 + 2, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, y0 + 3, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			p.set(c, u, beam, v, log(Blocks.STRIPPED_CHERRY_WOOD, ax > az ? Direction.Axis.Z : Direction.Axis.X));
			return;
		}
		if (g.ring(m)) {
			p.set(c, u, beam, v, log(Blocks.STRIPPED_CHERRY_WOOD, ax > az ? Direction.Axis.Z : Direction.Axis.X));
			if (g.rail(dx, dz)) {
				p.set(c, u, y0, v, fence(g, dx, dz));
				// brackets under the beam next to each column
				Direction toCol = null;
				for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
					if (g.column(dx + dir.getStepX(), dz + dir.getStepZ())) {
						toCol = dir;
						break;
					}
				}
				if (toCol != null) {
					p.set(c, u, y0 + 3, v, topStairs(Blocks.CHERRY_STAIRS, toCol));
				} else if (((ax + az) & 1) == 0) {
					// [finish] a standing lantern on every other balustrade post
					p.set(c, u, y0 + 1, v, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
				}
			}
			return;
		}
		if (g.interior(m)) {
			// ceiling of the stage
			p.set(c, u, beam, v, m <= 1.5 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.CHERRY_PLANKS));
		}
	}

	// ------------------------------------------------------------------ hip roof

	private static boolean isRib(int dx, int dz, double m) {
		if (m <= 1.4) {
			return false;
		}
		double angle = Math.atan2(dz, dx);
		double step = Math.PI / 4;
		double offs = Math.PI / 8;
		double rel = (angle - offs) / step;
		double da = Math.abs(angle - (Math.round(rel) * step + offs));
		return m * Math.sin(da) < 0.42;
	}

	private void roof(CitadelCanvas c, Plot p, Geo g, Roof r, int u, int v, int dx, int dz, double m) {
		int base = g.h() + 4;
		boolean rib = isRib(dx, dz, m);
		Direction in = out(dx, dz).getOpposite();
		// the eave: one block beyond the beam, low side out
		if (m > g.k() + 0.5 && m <= g.k() + 1.5) {
			p.set(c, u, base, v, stairs(r.stair(rib, 0), in));
		}
		for (int i = 1; i <= r.layers(); i++) {
			double ri = g.k() + 0.5 - r.s() * (i - 1);
			if (m > ri) {
				continue;
			}
			int y = base + i;
			if (m > ri - 1 && m > 0.5) {
				p.set(c, u, y, v, stairs(r.stair(rib, 0), in));
			} else {
				p.set(c, u, y, v, r.tread(rib, p.hash(u, y, v, 7)));
			}
		}
	}

	private void finial(CitadelCanvas c, Plot p, Geo g, Roof r) {
		int y = g.h() + 4 + r.layers() + 1;
		int fs = r.fs();
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
		for (int i = 0; i < fs; i++) {
			BlockState s;
			if (r.fin() == 0) {
				s = i == fs - 1 ? lantern : b(Blocks.CHERRY_FENCE);
			} else {
				s = fs >= 2 && i == 0 ? b(Blocks.GOLD_BLOCK) : Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
			}
			at(c, p, g, 0, y + i, 0, s);
		}
	}

	// ------------------------------------------------------------------ the stage: music

	private void stage(CitadelCanvas c, Plot p, Geo g) {
		int y = g.h();
		at(c, p, g, 0, y, 0, Blocks.JUKEBOX.defaultBlockState().setValue(BlockStateProperties.HAS_RECORD, true));
		at(c, p, g, -1, y, 0, b(Blocks.NOTE_BLOCK));
		at(c, p, g, 1, y, 0, b(Blocks.NOTE_BLOCK));
		at(c, p, g, 0, y, 1, b(Blocks.NOTE_BLOCK));
		atLayer(c, p, g, 0, y + 3, 0, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		for (int dz = -g.k(); dz <= g.k(); dz++) {
			for (int dx = -g.k(); dx <= g.k(); dx++) {
				if (!g.seat(dx, dz)) {
					continue;
				}
				if (dz < 0) {
					// front half: potted saplings in the corners of the stage
					if (((Math.abs(dx) + Math.abs(dz)) & 1) == 0) {
						at(c, p, g, dx, y, dz, b(Blocks.POTTED_CHERRY_SAPLING));
					}
					continue;
				}
				Direction f = out(dx, dz);
				if (!g.solid(dx + f.getStepX(), dz + f.getStepZ())) {
					Direction alt = f.getAxis() == Direction.Axis.X ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
					f = alt;
				}
				// seat: tall half (the backrest) against the balustrade
				at(c, p, g, dx, y, dz, stairs(Blocks.CHERRY_STAIRS, f));
				if (((Math.abs(dx) + Math.abs(dz)) & 1) == 0) {
					int lx = dx - f.getStepX();
					int lz = dz - f.getStepZ();
					if (!g.centre(lx, lz) && !g.seat(lx, lz) && g.interior(oct(lx, lz))) {
						// the music stand in front of the seat, its book towards the player
						at(c, p, g, lx, y, lz, Blocks.LECTERN.defaultBlockState()
								.setValue(BlockStateProperties.HORIZONTAL_FACING, f).setValue(BlockStateProperties.HAS_BOOK, true));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ the four corners of the plot

	/** 0 nothing, 1 lamp, 2 bench facing along z, 3 bench facing along x, 4 hedge, 5 flower bed. */
	private static int cornerRole(Geo g, int a, int bb, double m) {
		int n = g.n();
		if (m <= g.pl() + 0.5) {
			return 0;
		}
		if (a == n - 1 && bb == n - 1) {
			return 1;
		}
		if (bb == n - 1 && a >= n - 4 && a <= n - 2) {
			return 2;
		}
		if (a == n - 1 && bb >= n - 4 && bb <= n - 2) {
			return 3;
		}
		if ((bb == n && a >= n - 4 && a <= n) || (a == n && bb >= n - 4 && bb <= n)) {
			return 4;
		}
		if (a >= g.sw() + 3 && bb >= g.sw() + 3 && a <= n && bb <= n && m > g.pl() + 1.5) {
			return 5;
		}
		return 0;
	}

	private void corner(CitadelCanvas c, Plot p, Geo g, int sx, int sz) {
		int n = g.n();
		for (int bb = 0; bb <= n; bb++) {
			for (int a = 0; a <= n; a++) {
				int dx = sx * a;
				int dz = sz * bb;
				int role = cornerRole(g, a, bb, oct(dx, dz));
				if (role == 0) {
					continue;
				}
				int u = g.cu() + dx;
				int v = g.cv() + dz;
				switch (role) {
					case 1 -> {
						at(c, p, g, dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE));
						at(c, p, g, dx, 1, dz, b(Blocks.CHERRY_FENCE));
						at(c, p, g, dx, 2, dz, b(Blocks.CHERRY_FENCE));
						at(c, p, g, dx, 3, dz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
					}
					case 2 -> at(c, p, g, dx, 0, dz, stairs(Blocks.CHERRY_STAIRS, sz > 0 ? Direction.SOUTH : Direction.NORTH));
					case 3 -> at(c, p, g, dx, 0, dz, stairs(Blocks.CHERRY_STAIRS, sx > 0 ? Direction.EAST : Direction.WEST));
					case 4 -> {
						// the hedge behind a bench, with the odd flowering shrub
						boolean flowering = p.has(u, 0, v) && p.hash(u, 0, v, 3) == 0;
						at(c, p, g, dx, 0, dz, CitadelCanvas.leaves(flowering ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
					}
					default -> {
						at(c, p, g, dx, 0, dz, b(Blocks.MOSS_BLOCK));
						int h = p.hash(u, 1, v, 8);
						BlockState plant = h < 3 ? b(Blocks.PINK_PETALS) : h < 5 ? CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES) : h == 5 ? b(Blocks.FERN) : null;
						if (plant != null) {
							at(c, p, g, dx, 1, dz, plant);
						}
					}
				}
			}
		}
	}
}
