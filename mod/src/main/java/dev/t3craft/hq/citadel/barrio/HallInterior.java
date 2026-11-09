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
			floor(c, r, f, s);
			rug(c, r, F, f, s, env);
			furniture(c, r, F, f, y, s, env, seed, desks);
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

	// ------------------------------------------------------------------ floor and rug

	/**
	 * The floor of one storey, redrawn over the shell's plain floor: a dark frame one cell wide along the walls
	 * and a 2x2 checker of two tones inside, by style. Only cells that still hold the shell's floor block are
	 * touched (never the stair hole, never a stair step on the slab level).
	 */
	private static void floor(CitadelCanvas c, Room r, int f, HallStyle s) {
		int W = r.w();
		int D = r.d();
		int yf = f * fh();
		BlockState plain = c.floor();
		for (int u = 0; u < D; u++) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, yf);
				BlockState now = c.at(p.getX(), p.getY(), p.getZ());
				if (now == null || !now.equals(plain)) {
					continue;
				}
				boolean edge = u == 0 || u == D - 1 || v == 0 || v == W - 1;
				boolean dark = (((u >> 1) + (v >> 1)) & 1) == 0;
				c.set(p, b(floorBlock(s, edge, dark, HallStyle.hash(u, v, f))));
			}
		}
	}

	private static net.minecraft.world.level.block.Block floorBlock(HallStyle s, boolean edge, boolean dark, int hsh) {
		return switch (s) {
			case TIMBER -> edge ? Blocks.DARK_OAK_PLANKS : dark ? Blocks.SPRUCE_PLANKS : Blocks.BAMBOO_PLANKS;
			case STONE -> edge ? Blocks.POLISHED_DEEPSLATE : dark ? (hsh % 8 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS) : Blocks.POLISHED_ANDESITE;
			case SHUTTERED -> edge ? Blocks.POLISHED_BLACKSTONE_BRICKS : dark ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS;
			case BALCONY -> edge ? Blocks.POLISHED_DEEPSLATE : dark ? Blocks.BAMBOO_MOSAIC : Blocks.BAMBOO_PLANKS;
		};
	}

	/**
	 * The aisle: a runner three cells wide on the hall's centre line from the door to the back, in the project's
	 * colour with a lighter edge. It is flat (the navigation walks over it) and skips the stairs and the railing.
	 */
	private static void rug(CitadelCanvas c, Room r, int F, int f, HallStyle s, DyeColor env) {
		int D = r.d();
		int mid = r.c();
		DyeColor alt = s.alt(env);
		int y = f * fh() + 1;
		for (int u = 0; u <= D - 2; u++) {
			for (int dv = -1; dv <= 1; dv++) {
				BlockPos p = r.cell(u, mid + dv, y);
				if (floored(c, p) && !(f == 1 && F == 2 && fenced(r, u, mid + dv))) {
					c.set(p, b(Blocks.CARPET.pick(dv == 0 ? env : alt)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static BlockState chest(Direction facing) {
		return Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	private static BlockState concrete(DyeColor col) {
		return b(Blocks.CONCRETE.pick(col));
	}

	/**
	 * The common zone is the strip along the door wall, on both sides of the door's aisle (cells {@code u = 0},
	 * three each side): on one side the project board over a row of chests, on the other the archive (barrel,
	 * chest and lectern with shelves above). Nothing stands in the aisle three cells wide, nor in the desks' columns.
	 */
	private static void furniture(CitadelCanvas c, Room r, int F, int f, int y, HallStyle s, DyeColor env, int seed, Set<Long> desks) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		int h = HallStyle.hash(seed, f);
		board(c, r, y, env, h, desks);
		archive(c, r, y, W, h, desks);
		// the two front corners just inside the common zone: a tub plant or a pot
		for (int sd = 0; sd < 2; sd++) {
			final int side = sd;
			BlockPos p = r.cell(1, sd == 0 ? 0 : W - 1, y);
			place(c, desks, p, () -> {
				if (side == 0) {
					tub(c, p);
				} else {
					put(c, p, HallStyle.pot(h + 3));
				}
			});
		}
		// between the side desks: a lamp on a table on each side wall
		for (int v : new int[] {0, W - 1}) {
			BlockPos p = r.cell(D - 5, v, y);
			place(c, desks, p, () -> lamp(c, p, s));
		}
		// the back corners
		for (int v : new int[] {0, W - 1}) {
			BlockPos p = r.cell(D - 1, v, y);
			place(c, desks, p, () -> tub(c, p));
		}
		// the project log: a lectern on the back centre line, between the two back desks, in front of the window
		BlockPos back = r.cell(D - 1, mid, y);
		place(c, desks, back, () -> c.set(back, Blocks.LECTERN.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, r.back().getOpposite())));
	}

	/** The project board: three columns of cards (to do, doing, done) under a header in the project's colour, over chests. */
	private static void board(CitadelCanvas c, Room r, int y, DyeColor env, int h, Set<Long> desks) {
		DyeColor[] cards = {DyeColor.RED, DyeColor.YELLOW, DyeColor.LIME};
		for (int v = 0; v <= 2; v++) {
			BlockPos base = r.cell(0, v, y);
			if (!floored(c, base) || desks.contains(col(base.getX(), base.getZ()))) {
				continue;
			}
			c.set(base, chest(r.back()));
			c.set(base.above(1), concrete(cards[v]));
			c.set(base.above(2), HallStyle.hash(h, v) % 2 == 0 ? concrete(cards[v]) : concrete(DyeColor.WHITE));
			c.set(base.above(3), concrete(env));
		}
	}

	/** The archive: a barrel, a chest and a lectern along the door wall, bookshelves over the first two. */
	private static void archive(CitadelCanvas c, Room r, int y, int W, int h, Set<Long> desks) {
		for (int k = 0; k <= 2; k++) {
			BlockPos base = r.cell(0, W - 1 - k, y);
			if (!floored(c, base) || desks.contains(col(base.getX(), base.getZ()))) {
				continue;
			}
			switch (k) {
				case 0 -> c.set(base, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, r.back()));
				case 1 -> c.set(base, chest(r.back()));
				default -> {
					c.set(base, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.back()));
					continue;
				}
			}
			c.set(base.above(1), b(Blocks.BOOKSHELF));
			c.set(base.above(2), b(Blocks.BOOKSHELF));
			put(c, base.above(3), HallStyle.pot(h + k));
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
