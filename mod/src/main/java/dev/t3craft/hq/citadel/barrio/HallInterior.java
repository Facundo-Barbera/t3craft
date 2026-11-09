package dev.t3craft.hq.citadel.barrio;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.fh;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The inside of a barrio hall, by {@link HallStyle}: a rug in the project's colour, bookshelves, tub plants,
 * pots and side tables with a lantern, pendant lanterns on chains instead of ceiling lights, a chandelier in
 * one-floor halls and a railing round the stair hole of the upper floor. Furniture only stands where it
 * cannot be in anybody's way: never on the columns of a desk or its standing cell (the six slots, bound or
 * not), never in the door's aisle or on the stairs and never on a cell that is not free. Rugs go anywhere
 * free (a rug is flat: the navigation walks over it). Call after the shell, roof and stairs and before the
 * desks.
 */
public final class HallInterior {
	private HallInterior() {
	}

	/**
	 * @param F floors of the hall
	 */
	public static void furnish(CitadelCanvas c, Room r, int F, Mats m, HallStyle s, DyeColor env, int seed) {
		Set<Long> desks = deskColumns(r);
		for (int f = 0; f < F; f++) {
			int y = f * fh() + 1;
			rug(c, r, F, f, s, env);
			furniture(c, r, F, f, y, s, seed, desks);
			workArea(c, r, f, y, s, desks, seed);
			pendants(c, r, F, f, m, desks);
		}
		if (F == 1) {
			chandelier(c, r);
		} else {
			stairRail(c, r, s);
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Columns (x, z) of every desk and standing cell of the six slots. */
	private static Set<Long> deskColumns(Room r) {
		Set<Long> cols = new HashSet<>();
		for (int slot = 0; slot < 6; slot++) {
			DeskSpot d = RingCampusGeometry.desk(r, 0, slot);
			Direction right = d.facing().getClockWise();
			for (int k = -1; k <= 1; k++) {
				BlockPos p = d.desk().relative(right, k);
				cols.add(col(p.getX(), p.getZ()));
			}
			cols.add(col(d.stand().getX(), d.stand().getZ()));
		}
		return cols;
	}

	private static boolean free(CitadelCanvas c, BlockPos p) {
		BlockState s = c.at(p.getX(), p.getY(), p.getZ());
		return s == null || s.isAir();
	}

	/** A free cell with a solid floor under it (not the stair hole). */
	private static boolean floored(CitadelCanvas c, BlockPos p) {
		BlockState below = c.at(p.getX(), p.getY() - 1, p.getZ());
		return free(c, p) && below != null && !below.isAir();
	}

	private static void put(CitadelCanvas c, BlockPos p, BlockState s) {
		c.put(p.getX(), p.getY(), p.getZ(), s, DECOR);
	}

	/** A column of {@code h} bookshelves with an optional pot on top. */
	private static void shelf(CitadelCanvas c, BlockPos base, int h, boolean pot, int hsh) {
		for (int i = 0; i < h; i++) {
			c.set(base.above(i), b(Blocks.BOOKSHELF));
		}
		if (pot) {
			put(c, base.above(h), HallStyle.pot(hsh));
		}
	}

	/** A tub plant: a barrel with a flowering azalea bush on top. */
	private static void tub(CitadelCanvas c, BlockPos base) {
		c.set(base, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		c.set(base.above(), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
	}

	/** A side table with a lantern: fence post and a lantern on it. */
	private static void lamp(CitadelCanvas c, BlockPos base, HallStyle s) {
		c.set(base, b(s.fence()));
		put(c, base.above(), HallFacade.lantern());
	}

	/** A bench: a stair seat against the wall, its back to {@code wall}. */
	private static void bench(CitadelCanvas c, BlockPos base, Direction wall, HallStyle s) {
		c.set(base, stairs(s.stairBlock(), wall));
	}

	// ------------------------------------------------------------------ rugs

	private static void rug(CitadelCanvas c, Room r, int F, int f, HallStyle s, DyeColor env) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		int uMax = D - 3;
		DyeColor alt = s.alt(env);
		int y = f * fh() + 1;
		for (int u = 0; u <= uMax; u++) {
			for (int v = 1; v <= W - 2; v++) {
				DyeColor col = rugColor(s, u, v, uMax, W, mid, env, alt);
				if (col == null) {
					continue;
				}
				BlockPos p = r.cell(u, v, y);
				if (floored(c, p) && !(f == 1 && F == 2 && fenced(r, u, v))) {
					c.set(p, b(Blocks.CARPET.pick(col)));
				}
			}
		}
	}

	private static @Nullable DyeColor rugColor(HallStyle s, int u, int v, int uMax, int W, int mid, DyeColor env, DyeColor alt) {
		switch (s) {
			case TIMBER -> {
				// a runner along the aisle
				int dv = Math.abs(v - mid);
				return dv > 1 ? null : dv == 1 ? alt : env;
			}
			case STONE -> {
				// a bordered rug
				if (u < 1 || u > uMax - 1 || v < 2 || v > W - 3) {
					return null;
				}
				return u == 1 || u == uMax - 1 || v == 2 || v == W - 3 ? alt : env;
			}
			case SHUTTERED -> {
				// a checkered rug
				if (u < 1 || u > uMax - 1 || v < 2 || v > W - 3) {
					return null;
				}
				return (u + v) % 2 == 0 ? env : alt;
			}
			default -> {
				// concentric rings
				int ring = Math.min(Math.min(u, uMax - u), Math.min(v - 1, W - 2 - v));
				return ring % 2 == 0 ? alt : env;
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static void furniture(CitadelCanvas c, Room r, int F, int f, int y, HallStyle s, int seed, Set<Long> desks) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		int h = HallStyle.hash(seed, f);
		// the two front corners: cells (0,0) (1,0) (0,1) and their mirror
		for (int sd = 0; sd < 2; sd++) {
			final int side = sd;
			int v0 = side == 0 ? 0 : W - 1;
			int step = side == 0 ? 1 : -1;
			BlockPos a = r.cell(0, v0, y);
			BlockPos bb = r.cell(1, v0, y);
			BlockPos cc = r.cell(0, v0 + step, y);
			Direction wall = r.back().getOpposite();
			boolean first = side == 0;
			switch (s) {
				case TIMBER -> {
					place(c, desks, a, () -> shelf(c, a, 3, false, h));
					place(c, desks, bb, () -> shelf(c, bb, 2, true, h + 1));
					place(c, desks, cc, () -> tub(c, cc));
				}
				case STONE -> {
					place(c, desks, a, () -> tub(c, a));
					place(c, desks, bb, () -> shelf(c, bb, 2, false, h));
					place(c, desks, cc, () -> put(c, cc, HallStyle.pot(h + side)));
				}
				case SHUTTERED -> {
					place(c, desks, a, () -> bench(c, a, wall, s));
					place(c, desks, cc, () -> bench(c, cc, wall, s));
					place(c, desks, bb, () -> shelf(c, bb, first ? 2 : 1, true, h + 3));
				}
				case BALCONY -> {
					place(c, desks, a, () -> shelf(c, a, 2, true, h));
					place(c, desks, bb, () -> tub(c, bb));
					place(c, desks, cc, () -> put(c, cc, HallStyle.pot(h + 2 + side)));
				}
			}
		}
		// between the side desks: a lamp on a table on each side wall
		for (int v : new int[] {0, W - 1}) {
			BlockPos p = r.cell(D - 5, v, y);
			place(c, desks, p, () -> lamp(c, p, s));
		}
		// the back corners and the back centre
		for (int v : new int[] {0, W - 1}) {
			BlockPos p = r.cell(D - 1, v, y);
			place(c, desks, p, () -> tub(c, p));
		}
		BlockPos back = r.cell(D - 1, mid, y);
		place(c, desks, back, () -> shelf(c, back, s == HallStyle.SHUTTERED ? 2 : 3, s != HallStyle.TIMBER, h + 5));
	}

	/** Work blocks of the tables of a scaled hall, in the order a row repeats them. */
	private static final net.minecraft.world.level.block.Block[] WORK = {
			Blocks.CRAFTING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.SMITHING_TABLE, Blocks.LOOM, Blocks.LECTERN};

	/**
	 * Scaled halls only ({@code W >= 15}, so scale 1 is untouched): a big hall must not be a carpeted void. Two long
	 * worktables (crafting, cartography, fletching, smithing tables, looms and lecterns in turn: blocks with a use)
	 * run along the hall a quarter of its width off the centre line, so a 3-wide aisle stays free to the desks of
	 * the side walls and to the middle (the stairs, the door); bookcases line the side and back walls in runs of
	 * two between the desks and chests stand at the end of each table. Nothing on a desk column, the standing cells,
	 * the centre line or the stair hole; every cell is checked free.
	 */
	private static void workArea(CitadelCanvas c, Room r, int f, int y, HallStyle s, Set<Long> desks, int seed) {
		int W = r.w();
		int D = r.d();
		if (W < 15) {
			return;
		}
		int mid = r.c();
		int off = Math.max(5, W / 4);
		int u0 = fh() + 1;
		int u1 = D - 5;
		for (int side = -1; side <= 1; side += 2) {
			int v = mid + side * off;
			int i = 0;
			for (int u = u0; u <= u1; u++) {
				BlockPos p = r.cell(u, v, y);
				if (u == u0 || u == u1) {
					placeOnRug(c, desks, p, () -> c.set(p, Blocks.CHEST.defaultBlockState()));
				} else if (u % 3 != 0) {
					net.minecraft.world.level.block.Block wb = WORK[Math.floorMod(i++ + HallStyle.hash(seed, side), WORK.length)];
					placeOnRug(c, desks, p, () -> c.set(p, b(wb)));
				}
			}
		}
		// bookcases against the side walls, two cells in every five, two high
		for (int v : new int[] {0, W - 1}) {
			for (int u = 3; u < D - 3; u++) {
				if (u % 5 < 2) {
					BlockPos p = r.cell(u, v, y);
					placeOnRug(c, desks, p, () -> shelf(c, p, 2, false, 0));
				}
			}
		}
	}

	/** Like {@link #place}, but the cell may hold the hall's rug (carpet), which the new block replaces. */
	private static void placeOnRug(CitadelCanvas c, Set<Long> desks, BlockPos p, Runnable build) {
		BlockState here = c.at(p.getX(), p.getY(), p.getZ());
		BlockState below = c.at(p.getX(), p.getY() - 1, p.getZ());
		boolean rug = here != null && here.getBlock().getDescriptionId().endsWith("_carpet");
		if ((free(c, p) || rug) && below != null && !below.isAir() && !desks.contains(col(p.getX(), p.getZ()))) {
			build.run();
		}
	}

	/** Runs {@code build} when the cell is free, floored and not on a desk column. */
	private static void place(CitadelCanvas c, Set<Long> desks, BlockPos p, Runnable build) {
		if (floored(c, p) && !desks.contains(col(p.getX(), p.getZ()))) {
			build.run();
		}
	}

	// ------------------------------------------------------------------ light

	/** Spacing of the pendant lanterns of a hall ceiling (cells, both ways). */
	private static final int PENDANT_STEP = 4;

	/**
	 * Pendant lanterns on a chain, on a grid centred on the room (citadel/light-aesthetic): one row on the
	 * hall's centre line and rows every {@value #PENDANT_STEP} cells either side of it, columns every
	 * {@value #PENDANT_STEP} cells either side of the middle of its depth, so the ceiling is symmetric about
	 * the door's axis. Never over a desk or its standing cell ({@code desks}), never over the stair hole, and
	 * not in the centre of a one-floor hall, where the chandelier hangs. The ceiling lights of the shell are
	 * gone: the light of a hall is these lanterns, the chandelier and the table lamps.
	 */
	private static void pendants(CitadelCanvas c, Room r, int F, int f, Mats m, Set<Long> desks) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		int cu = D / 2;
		int ceil = (f + 1) * fh();
		for (int u = cu - PENDANT_STEP * (cu / PENDANT_STEP); u < D - 1; u += PENDANT_STEP) {
			for (int v = mid - PENDANT_STEP * (mid / PENDANT_STEP); v < W; v += PENDANT_STEP) {
				if (u < 1 || v < 1 || v > W - 2 || F == 1 && u == cu && v == mid) {
					continue;
				}
				BlockPos p = r.cell(u, v, ceil);
				// never over a desk, and only into open air (not the stairs' core under the top step)
				if (desks.contains(col(p.getX(), p.getZ())) || !free(c, p.below()) || !free(c, p.below(2))) {
					continue;
				}
				BlockState now = c.at(p.getX(), p.getY(), p.getZ());
				if (now == null || now.isAir() || (f < F - 1 && now.getLightEmission() > 0)) {
					continue;
				}
				if (f == F - 1) {
					c.set(p, m.ceiling());
				}
				c.put(p.getX(), ceil - 1, p.getZ(), Blocks.IRON_CHAIN.defaultBlockState(), HANGING);
				c.put(p.getX(), ceil - 2, p.getZ(), HallFacade.hangingLantern(), HANGING);
			}
		}
	}

	/** A chandelier over the middle of a one-floor hall: a chain and three lanterns. */
	private static void chandelier(CitadelCanvas c, Room r) {
		int mid = r.c();
		int u = r.d() / 2;
		BlockPos top = r.cell(u, mid, fh());
		BlockState ceil = c.at(top.getX(), top.getY(), top.getZ());
		if (ceil == null || ceil.isAir()) {
			return;
		}
		c.put(top.getX(), fh() - 1, top.getZ(), Blocks.IRON_CHAIN.defaultBlockState(), HANGING);
		c.put(top.getX(), fh() - 2, top.getZ(), Blocks.IRON_CHAIN.defaultBlockState(), HANGING);
		c.put(top.getX(), fh() - 3, top.getZ(), HallFacade.hangingLantern(), HANGING);
	}

	// ------------------------------------------------------------------ stairs

	/** Cells of the railing round the stair hole on the upper floor: the hole's two long sides and its front end. */
	private static boolean fenced(Room r, int u, int v) {
		int mid = r.c();
		// the hole is u = fh - 4 .. fh - 1 on the centre line (2..5 at scale 1, see CitadelBarrios.hallShell)
		return Math.abs(v - mid) <= 1 && u >= 1 && u <= fh() - 1 && !(Math.abs(v - mid) == 0 && u >= fh() - 4);
	}

	/** The railing round the hole of the upper floor's stairs. */
	private static void stairRail(CitadelCanvas c, Room r, HallStyle s) {
		int mid = r.c();
		int y = fh() + 1;
		for (int u = 1; u <= fh() - 1; u++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (fenced(r, u, mid + dv)) {
					BlockPos p = r.cell(u, mid + dv, y);
					if (floored(c, p)) {
						c.set(p, b(s.fence()));
					}
				}
			}
		}
	}
}
