package dev.t3craft.hq.citadel.catalog.plazacenter;

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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Plaza centre with a glazed-terracotta rosette floor that is also a place to be: a raised round dais
 * whose mosaic grows by rings (never stretches), framed by polished stone, with a working centrepiece
 * (a low fountain, a tall lamp tower or a map table of the citadel), cherry benches on the stone
 * rim looking at the middle, lamp posts at the four axes, and shade trees in the corners.
 *
 * <pre>
 *  top view (n = 7)                           section through an axis
 *   T ·  b b L ░ ░ L b b  · T                  y4        L lantern   (axis lamp)
 *     b  ◦ planter     ◦  b                  y1  ▙ bench ▙  curb ▙▟ ... centre piece
 *     L ░ ▒ ▒ ▒ ▒ ▒ ▒ ░ L     ░ ramp         y0  ▂▄██████████▄▂   rim curb, frame, mosaic
 *     ░ ░ ▒ ✦ ▒ C ▒ ✦ ░ ░     ▒ white path   street ▔▔▔
 *   T = tree on a moss bed, L = lamp, b = bench, C = centre
 * </pre>
 *
 * <ul>
 * <li>Mosaic: bands from the outside in: stone rim (seats), polished andesite frame,
 *     then rings of petals; every 3 blocks of radius a new ring of the opposite colours, so a 21-wide plaza
 *     has more rings, not bigger tiles. Four 3-wide white paths lead from the street to the centre, up a stair
 *     and a half step (no jumps).</li>
 * <li>Glaze palette by seed: blue and white, ocre (orange) and white, green and white.</li>
 * <li>Centre by seed: fountain, lamp tower, map table.</li>
 * </ul>
 *
 * Size: 13–21 × 13–21 × 9+ (the box grows with the plaza: the mosaic adds rings).
 */
public final class PlazaCenterGlazedRosette implements Variant {
	private static final DyeColor[] GLAZE = {DyeColor.BLUE, DyeColor.ORANGE, DyeColor.GREEN};

	@Override
	public String id() {
		return "plaza_center_glazed_rosette";
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
		return 9;
	}

	@Override
	public int maxW() {
		return 21;
	}

	@Override
	public int maxD() {
		return 21;
	}

	private static BlockState gl(DyeColor color, Direction facing) {
		return Blocks.GLAZED_TERRACOTTA.pick(color).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	private static BlockState top(BlockState s) {
		return s.setValue(StairBlock.HALF, Half.TOP);
	}

	private static BlockState wall(Block w, boolean n, boolean e, boolean s, boolean west) {
		return w.defaultBlockState()
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, west ? WallSide.LOW : WallSide.NONE);
	}

	/** Outward direction of a cell relative to the centre (dominant axis). */
	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Writes only when the cell is in the box (canopies may reach past the edge without counting as clipped). */
	private static void safe(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int cv = (d - 1) / 2;
		int n = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv));
		double radius = n + 0.5;
		int cr = n >= 9 ? 3 : 2;
		int top = Math.min(p.h() - 1, 9);
		int pal = p.hash(11, 0, 3, 3);
		int type = p.hash(5, 9, 2, 3);
		DyeColor glaze = GLAZE[pal];

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > radius) {
					corner(c, p, u, v, dx, dz, n);
				} else {
					disc(c, p, u, v, dx, dz, r, radius, n, cr, glaze);
				}
			}
		}
		int br = n >= 9 ? 2 : 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				tree(c, p, cu + sx * (n - 1), cv + sz * (n - 1), br);
			}
		}
		centre(c, p, cu, cv, cr, type, top);
		lamps(c, p, cu, cv, n);
	}

	// ------------------------------------------------------------------ the dais

	private void disc(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double r, double radius, int n, int cr, DyeColor glaze) {
		double e = radius - r;
		int cheb = Math.max(Math.abs(dx), Math.abs(dz));
		int pa = Math.min(Math.abs(dx), Math.abs(dz));
		Direction f = out(dx, dz);
		if (e < 1.0) {
			if (pa <= 1) {
				// [depth] ramp: stairs rising inwards on the four axes, half a step off the street, then a full block: no jump
				p.set(c, u, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, f.getOpposite()));
				return;
			}
			// the stone rim is a curb 1 block over the street, and a seat on top: cherry stairs, back to the outside
			int h = p.hash(u, 0, v, 6);
			p.set(c, u, 0, v, h == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : h == 1 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			if (Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1) {
				// planter at the diagonal, between two benches
				p.set(c, u, 1, v, CitadelCanvas.leaves(p.hash(u, 1, v, 2) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			} else if (pa != 2) {
				p.set(c, u, 1, v, stairs(Blocks.CHERRY_STAIRS, f));
			}
			return;
		}
		if (cheb <= cr) {
			return; // the centre piece lays its own base
		}
		boolean path = pa <= 1;
		if (e < 2.0) {
			p.set(c, u, 0, v, path ? gl(DyeColor.WHITE, f) : b(Blocks.POLISHED_ANDESITE));
			return;
		}
		int fe = 2;
		// field: rings of eight petals; each ring (about 3 blocks of radius) swaps the colours
		double r0 = cr + 0.5;
		double r1 = radius - fe;
		int nz = Math.max(1, (int) Math.round((r1 - r0) / 3.0));
		double zw = (r1 - r0) / nz;
		double rr = Math.max(0, r - r0);
		int zi = Math.min(nz - 1, (int) Math.floor(rr / zw));
		double local = rr - zi * zw;
		boolean invert = (zi & 1) == 1;
		BlockState bg = invert ? gl(DyeColor.WHITE, f) : gl(glaze, f);
		BlockState petal = invert ? gl(glaze, f) : gl(DyeColor.WHITE, f);
		if (path) {
			p.set(c, u, 0, v, pa == 1 ? gl(DyeColor.WHITE, f) : invert ? gl(glaze, f) : gl(DyeColor.WHITE, f));
			return;
		}
		if (zi > 0 && local < 1.0) {
			p.set(c, u, 0, v, b(Blocks.POLISHED_ANDESITE));
			return;
		}
		double sector = Math.PI / 4;
		double ang = Math.atan2(dz, dx);
		int ray = (int) Math.round(ang / sector);
		double perp = r * Math.sin(Math.abs(ang - ray * sector));
		boolean diag = Math.floorMod(ray, 2) == 1;
		double t = Math.max(0, Math.min(1, local / zw));
		double half = 0.5 + 1.3 * Math.sin(Math.PI * t);
		p.set(c, u, 0, v, diag && perp < half ? petal : bg);
	}

	// ------------------------------------------------------------------ corners: paving, moss beds and trees

	private void corner(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, int n) {
		int br = n >= 9 ? 2 : 1;
		int tx = (dx < 0 ? -1 : 1) * (n - 1);
		int tz = (dz < 0 ? -1 : 1) * (n - 1);
		int ex = Math.abs(dx - tx);
		int ez = Math.abs(dz - tz);
		if (Math.max(ex, ez) <= br) {
			// raised bed: deepslate rim, moss inside, low plants; the middle cell is the trunk's
			if (Math.max(ex, ez) == br) {
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
			} else {
				p.set(c, u, 0, v, b(Blocks.MOSS_BLOCK));
				if (ex + ez > 0) {
					switch (p.hash(u, 1, v, 5)) {
						case 0 -> p.set(c, u, 1, v, b(Blocks.ALLIUM));
						case 1 -> p.set(c, u, 1, v, b(Blocks.PINK_PETALS));
						case 2 -> {
							p.set(c, u, 1, v, b(Blocks.PEONY));
							p.set(c, u, 2, v, Blocks.PEONY.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
						}
						default -> p.set(c, u, 1, v, b(Blocks.FERN));
					}
				}
			}
			return;
		}
		int h = p.hash(u, 0, v, 4);
		p.set(c, u, 0, v, h == 0 ? slab(Blocks.MOSSY_STONE_BRICK_SLAB) : h == 1 ? slab(Blocks.POLISHED_ANDESITE_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
	}

	/** A small cherry tree: trunk from the moss bed, a compact canopy from 5 blocks up (walk and sit under it). */
	private void tree(CitadelCanvas c, Plot p, int tu, int tv, int br) {
		for (int y = 1; y <= 5; y++) {
			safe(c, p, tu, y, tv, b(Blocks.CHERRY_LOG));
		}
		// a few leaves hug the trunk below the canopy so it does not read as a lollipop
		safe(c, p, tu + 1, 4, tv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
		safe(c, p, tu - 1, 4, tv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
		safe(c, p, tu, 4, tv + 1, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
		safe(c, p, tu, 4, tv - 1, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
		double[] rad = br == 1 ? new double[] {1.9, 1.9, 1.5, 0.9} : new double[] {2.6, 2.6, 2.0, 1.2};
		for (int layer = 0; layer < rad.length; layer++) {
			int y = 5 + layer;
			int reach = (int) Math.ceil(rad[layer]);
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dx = -reach; dx <= reach; dx++) {
					if (dx * dx + dz * dz <= rad[layer] * rad[layer] && !(dx == 0 && dz == 0 && y == 5)) {
						safe(c, p, tu + dx, y, tv + dz, CitadelCanvas.leaves(p.hash(tu + dx, y, tv + dz, 6) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.CHERRY_LEAVES));
					}
				}
			}
		}
	}

	/** Lamp posts on the rim, two at each axis, beside the ramp. */
	private void lamps(CitadelCanvas c, Plot p, int cu, int cv, int n) {
		int[][] spots = {{n, 2}, {n, -2}, {-n, 2}, {-n, -2}, {2, n}, {-2, n}, {2, -n}, {-2, -n}};
		for (int[] s : spots) {
			int u = cu + s[0];
			int v = cv + s[1];
			p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 2, v, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false));
			p.set(c, u, 3, v, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false));
			p.set(c, u, 4, v, b(Blocks.LANTERN));
		}
	}

	// ------------------------------------------------------------------ the centre piece

	private void centre(CitadelCanvas c, Plot p, int cu, int cv, int cr, int type, int top) {
		for (int dz = -cr; dz <= cr; dz++) {
			for (int dx = -cr; dx <= cr; dx++) {
				int h = p.hash(cu + dx, 0, cv + dz, 7);
				p.set(c, cu + dx, 0, cv + dz, h == 0 ? b(Blocks.GILDED_BLACKSTONE) : h < 3 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES));
			}
		}
		switch (type) {
			case 0 -> fountain(c, p, cu, cv, cr);
			case 1 -> lampTower(c, p, cu, cv, cr, top);
			default -> mapTable(c, p, cu, cv, cr);
		}
	}

	/** A low fountain: a basin of stone brick with a ledge, water inside, a stone pillar with a lantern. */
	private void fountain(CitadelCanvas c, Plot p, int cu, int cv, int cr) {
		for (int dz = -cr; dz <= cr; dz++) {
			for (int dx = -cr; dx <= cr; dx++) {
				int cheb = Math.max(Math.abs(dx), Math.abs(dz));
				int u = cu + dx;
				int v = cv + dz;
				if (cheb == cr) {
					p.set(c, u, 1, v, p.hash(u, 1, v, 4) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
					p.set(c, u, 2, v, slab(Blocks.STONE_BRICK_SLAB));
				} else if (cheb > 0) {
					p.set(c, u, 1, v, CitadelCanvas.WATER);
				}
			}
		}
		p.set(c, cu, 1, cv, b(Blocks.CHISELED_STONE_BRICKS));
		p.set(c, cu, 2, cv, b(Blocks.POLISHED_ANDESITE));
		p.set(c, cu, 3, cv, wall(Blocks.STONE_BRICK_WALL, false, false, false, false));
		p.set(c, cu, 4, cv, wall(Blocks.STONE_BRICK_WALL, false, false, false, false));
		p.set(c, cu, 5, cv, b(Blocks.LANTERN));
	}

	/** A tall lamp: stepped plinth, a deepslate post, a crossbar with four hanging lanterns and one on top. */
	private void lampTower(CitadelCanvas c, Plot p, int cu, int cv, int cr, int top) {
		int yp = cr - 1;
		for (int lvl = 1; lvl <= yp; lvl++) {
			int rad = cr - lvl;
			for (int dz = -rad; dz <= rad; dz++) {
				for (int dx = -rad; dx <= rad; dx++) {
					p.set(c, cu + dx, lvl, cv + dz, lvl % 2 == 1 ? b(Blocks.STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
				}
			}
		}
		for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			for (int k = 1; k <= cr - 1; k++) {
				// step stairs hugging the plinth on the axes
				p.set(c, cu + dir.getStepX() * (cr - k + 1), k, cv + dir.getStepZ() * (cr - k + 1), stairs(Blocks.STONE_BRICK_STAIRS, dir.getOpposite()));
			}
		}
		p.set(c, cu, yp + 1, cv, b(Blocks.POLISHED_DEEPSLATE));
		int cross = top - 1;
		for (int y = yp + 2; y < cross; y++) {
			p.set(c, cu, y, cv, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false));
		}
		p.set(c, cu, cross, cv, wall(Blocks.POLISHED_DEEPSLATE_WALL, true, true, true, true));
		p.set(c, cu, top, cv, b(Blocks.LANTERN));
		p.set(c, cu, cross - 1, cv, b(Blocks.POLISHED_DEEPSLATE_WALL));
		p.set(c, cu + 1, cross, cv, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, true));
		p.set(c, cu - 1, cross, cv, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, true, false, false));
		p.set(c, cu, cross, cv + 1, wall(Blocks.POLISHED_DEEPSLATE_WALL, true, false, false, false));
		p.set(c, cu, cross, cv - 1, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, true, false));
		BlockState hung = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		p.put(c, cu + 1, cross - 1, cv, hung, CitadelCanvas.HANGING);
		p.put(c, cu - 1, cross - 1, cv, hung, CitadelCanvas.HANGING);
		p.put(c, cu, cross - 1, cv + 1, hung, CitadelCanvas.HANGING);
		p.put(c, cu, cross - 1, cv - 1, hung, CitadelCanvas.HANGING);
	}

	/**
	 * A map table of the citadel: a pedestal with a map top (green gardens, pink barrios, grey roads, a gold
	 * "you are here" in the middle), a corbel of upside-down stairs and a lamp post on each corner.
	 */
	private void mapTable(CitadelCanvas c, Plot p, int cu, int cv, int cr) {
		for (int dz = -cr; dz <= cr; dz++) {
			for (int dx = -cr; dx <= cr; dx++) {
				int cheb = Math.max(Math.abs(dx), Math.abs(dz));
				int u = cu + dx;
				int v = cv + dz;
				boolean corner = Math.abs(dx) == cr && Math.abs(dz) == cr;
				if (cheb < cr || corner) {
					p.set(c, u, 1, v, p.hash(u, 1, v, 5) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
				} else {
					p.set(c, u, 1, v, top(stairs(Blocks.STONE_BRICK_STAIRS, out(dx, dz).getOpposite())));
				}
				if (corner) {
					p.set(c, u, 2, v, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false));
					p.set(c, u, 3, v, wall(Blocks.POLISHED_DEEPSLATE_WALL, false, false, false, false));
					p.set(c, u, 4, v, b(Blocks.LANTERN));
				} else if (dx == 0 && dz == 0) {
					p.set(c, u, 2, v, b(Blocks.GOLD_BLOCK));
				} else if (dx == 0 || dz == 0 || cr == 3 && cheb == 2) {
					p.set(c, u, 2, v, CitadelCanvas.terracotta(DyeColor.LIGHT_GRAY));
				} else if (cheb == 1) {
					p.set(c, u, 2, v, CitadelCanvas.terracotta(DyeColor.PINK));
				} else {
					p.set(c, u, 2, v, p.hash(u, 2, v, 4) == 0 ? CitadelCanvas.terracotta(DyeColor.LIME) : CitadelCanvas.terracotta(DyeColor.GREEN));
				}
			}
		}
	}
}
