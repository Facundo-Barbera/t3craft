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
			deskRugs(c, r, f, s, env);
			rug(c, r, F, f, s, env);
			furniture(c, r, F, f, y, s, env, seed, desks);
			commonTable(c, r, F, y, s, env, desks);
			workArea(c, r, f, y, s, desks, seed);
			runway(c, r, F, y, desks);
			pendants(c, r, F, f, m, desks);
			beams(c, r, f, m, s);
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

	/** A free cell with a solid floor under it (not the stair hole, not a stair). */
	private static boolean floored(CitadelCanvas c, BlockPos p) {
		BlockState below = c.at(p.getX(), p.getY() - 1, p.getZ());
		// not on a stair either: a rug over the top step of the flight lifts the floor by 1/16 and makes the last step a jump
		return free(c, p) && below != null && !below.isAir() && !(below.getBlock() instanceof net.minecraft.world.level.block.StairBlock);
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
				boolean accent = HallStyle.hash(u, v, f) % 9 == 0;
				c.set(p, b(floorBlock(s, edge, accent)));
			}
		}
	}

	/** A calm floor: one field with a sparse second tone, inside a dark frame (all in the hall's own palette). */
	private static net.minecraft.world.level.block.Block floorBlock(HallStyle s, boolean edge, boolean accent) {
		return switch (s) {
			case TIMBER -> edge || accent ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS;
			case STONE -> edge ? Blocks.POLISHED_DEEPSLATE : accent ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
			case SHUTTERED -> edge ? Blocks.POLISHED_BLACKSTONE_BRICKS : accent ? Blocks.SPRUCE_PLANKS : Blocks.DARK_OAK_PLANKS;
			case BALCONY -> edge ? Blocks.POLISHED_DEEPSLATE : accent ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_QUARTZ;
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
		if (r.w() >= 15) {
			env = softened(env);
			alt = alt == env ? DyeColor.WHITE : alt;
		}
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

	/**
	 * Scaled halls ({@code W >= 15}): each of the six desk slots gets its own work-zone rug, five by five cells in the
	 * floor itself (wool, so desks, standing cells and the navigation are untouched): a grey border round the project's
	 * colour, from the desk's wall out into the hall. Together with the aisle runner they split the big room into
	 * team areas. Only floor cells are redrawn (never a stair, never the stair hole, never the walls).
	 */
	/** A project colour as a rug colour that reads on a floor: dark ones become cyan, saturated magenta pink, pale ones light blue. */
	private static DyeColor softened(DyeColor env) {
		return switch (env) {
			case BLACK, GRAY, BROWN -> DyeColor.CYAN;
			case MAGENTA -> DyeColor.PINK;
			case LIGHT_GRAY, WHITE -> DyeColor.LIGHT_BLUE;
			default -> env;
		};
	}

	private static void deskRugs(CitadelCanvas c, Room r, int f, HallStyle s, DyeColor env) {
		int W = r.w();
		int D = r.d();
		if (W < 15) {
			return;
		}
		int yf = f * fh();
		// a rug must read against its floor: dark floors (wood) get a pale border, stone a black one, quartz a grey one;
		// a dark or fully saturated project colour is softened for the field of the rug
		DyeColor edge = switch (s) {
			case TIMBER, SHUTTERED -> DyeColor.LIGHT_GRAY;
			case STONE -> DyeColor.BLACK;
			case BALCONY -> DyeColor.GRAY;
		};
		env = softened(env);
		int depth = 5;
		int half = 2;
		for (int slot = 0; slot < 6; slot++) {
			BlockPos dp = RingCampusGeometry.desk(r, 0, slot).desk();
			int ud = (dp.getX() - r.origin().getX()) * r.back().getStepX() + (dp.getZ() - r.origin().getZ()) * r.back().getStepZ();
			int vd = (dp.getX() - r.origin().getX()) * r.side().getStepX() + (dp.getZ() - r.origin().getZ()) * r.side().getStepZ();
			int u0;
			int u1;
			int v0;
			int v1;
			if (slot < 2) {
				u0 = ud - depth + 1;
				u1 = ud;
				v0 = vd - half;
				v1 = vd + half;
			} else {
				u0 = ud - half;
				u1 = ud + half;
				v0 = vd == 0 ? 0 : W - depth;
				v1 = vd == 0 ? depth - 1 : W - 1;
			}
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (u < 0 || u >= D || v < 0 || v >= W) {
						continue;
					}
					BlockPos p = r.cell(u, v, yf);
					BlockState now = c.at(p.getX(), p.getY(), p.getZ());
					if (now == null || now.isAir() || now.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
						continue;
					}
					boolean border = u == u0 || u == u1 || v == v0 || v == v1;
					c.set(p, b(Blocks.WOOL.pick(border ? edge : env)));
				}
			}
		}
	}

	/**
	 * Scaled halls: the common table, in the middle of the hall, where the aisle ends: five cells long and three wide in
	 * the style's own planks, with a runner in the project's colour, a cartography table in the middle (the project map),
	 * lanterns and pots, three chairs (stair seats) down each long side and one at each head. The aisle runs into
	 * its end and the room walks round it. On a two-floor hall it stands past the stair flight and its landing
	 * ({@code u >= fh + 2}), and the hall's own table and chairs are only put into free, floored cells.
	 */
	private static void commonTable(CitadelCanvas c, Room r, int F, int y, HallStyle s, DyeColor env, Set<Long> desks) {
		if (r.w() < 15) {
			return;
		}
		int mid = r.c();
		int half = 2;
		// at the end of the aisle, in front of the back desks' lectern, so the middle of the hall stays an open plaza and the
		// aisle is never cut: the way to every desk runs round the table's two sides
		int uc = Math.max(r.d() - 7, F == 2 ? fh() + 4 : 0);
		BlockState top = s.stairCore();
		for (int u = uc - half; u <= uc + half; u++) {
			for (int dv = -1; dv <= 1; dv++) {
				BlockPos p = r.cell(u, mid + dv, y);
				boolean centre = u == uc && dv == 0;
				placeOnRug(c, desks, p, () -> c.set(p, centre ? b(Blocks.CARTOGRAPHY_TABLE) : top));
			}
		}
		for (int u = uc - half; u <= uc + half; u++) {
			BlockPos p = r.cell(u, mid, y + 1);
			if (u != uc && !free(c, p.below())) {
				put(c, p, b(Blocks.CARPET.pick(env)));
			}
		}
		int[][] props = {{uc - 1, 1}, {uc + 1, -1}};
		for (int[] pr : props) {
			BlockPos p = r.cell(pr[0], mid + pr[1], y + 1);
			if (!free(c, p.below())) {
				put(c, p, HallFacade.lantern());
			}
		}
		int[][] pots = {{uc - 1, -1}, {uc + 1, 1}};
		for (int[] pr : pots) {
			BlockPos p = r.cell(pr[0], mid + pr[1], y + 1);
			if (!free(c, p.below())) {
				put(c, p, HallStyle.pot(HallStyle.hash(uc, pr[0], pr[1])));
			}
		}
		// chairs: the seat's back is away from the table
		for (int u = uc - 1; u <= uc + 1; u++) {
			for (int side = -1; side <= 1; side += 2) {
				BlockPos p = r.cell(u, mid + 2 * side, y);
				Direction back = side < 0 ? r.side().getOpposite() : r.side();
				placeOnRug(c, desks, p, () -> c.set(p, stairs(s.stairBlock(), back)));
			}
		}
		BlockPos head = r.cell(uc - half - 1, mid, y);
		placeOnRug(c, desks, head, () -> c.set(head, stairs(s.stairBlock(), r.back().getOpposite())));
		BlockPos foot = r.cell(uc + half + 1, mid, y);
		placeOnRug(c, desks, foot, () -> c.set(foot, stairs(s.stairBlock(), r.back())));
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

	/** Columns of the board and of the archive: three in a one-storey-wide hall, five in a wider one, seven in a scaled hall. */
	private static int zoneWidth(Room r) {
		return Math.min(r.w() >= 15 ? 7 : 5, r.c() - 1);
	}

	/**
	 * Scaled halls (struct/barrio-hall2): the board is a kanban. A dark slate backdrop in a frame of dark-oak logs and
	 * planks, five stage columns (a muted terracotta header each: to do red, doing orange, review yellow, done lime,
	 * shipped green), loose cards (single concrete cells in the stage colour, a blank white one now and then) with
	 * gaps between them, over a row of chests. The project's colour sits on the middle of the top rail.
	 */
	private static void kanban(CitadelCanvas c, Room r, int y, DyeColor env, int h, Set<Long> desks, int n) {
		DyeColor[] cards = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN};
		int rows = Math.max(3, fh() - 3);
		// struct/barrio-hall3: one lane per stage (up to five), side by side with no dark divider, so the five colours read at a
		// glance from across the hall; the cards stagger (a lane has them on its odd rows, the next on its even ones), never
		// stack, and a coloured header strip crowns the lanes under the project's rail
		int lanes = n - 2;
		for (int k = 0; k < n; k++) {
			int v = r.c() - 2 - k;
			BlockPos base = r.cell(0, v, y);
			if (!floored(c, base) || desks.contains(col(base.getX(), base.getZ()))) {
				continue;
			}
			if (k == 0 || k == n - 1) {
				for (int row = 0; row <= rows + 1; row++) {
					c.set(base.above(row), b(Blocks.DARK_OAK_LOG));
				}
				continue;
			}
			int idx = lanes - k;
			DyeColor stage = cards[lanes <= 1 ? 0 : idx * (cards.length - 1) / (lanes - 1)];
			c.set(base, chest(r.back()));
			for (int row = 1; row <= rows - 1; row++) {
				boolean card = (row + k) % 2 == 0 && HallStyle.hash(h, v, row) % 100 < 88;
				boolean blank = card && HallStyle.hash(v, row, h) % 6 == 0;
				c.set(base.above(row), card ? concrete(blank ? DyeColor.WHITE : stage) : b(Blocks.BLACKSTONE));
			}
			c.set(base.above(rows), terracotta(stage));
			c.set(base.above(rows + 1), k == n / 2 ? concrete(env) : b(Blocks.DARK_OAK_PLANKS));
		}
	}

	private static BlockState terracotta(DyeColor col) {
		return b(Blocks.DYED_TERRACOTTA.pick(col));
	}

	/**
	 * The project board, on the door wall left of the door's jamb: a header plate in the project's colour between
	 * dark frame cells over rows of cards (to do red, doing orange, review yellow, done lime, shipped green; the
	 * empty slots are white), over chests. Scaled halls draw a kanban instead ({@link #kanban}).
	 */
	private static void board(CitadelCanvas c, Room r, int y, DyeColor env, int h, Set<Long> desks) {
		DyeColor[] cards = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN};
		int n = zoneWidth(r);
		if (r.w() >= 15 && n >= 5) {
			kanban(c, r, y, env, h, desks, n);
			return;
		}
		int rows = fh() >= 8 ? 3 : 2;
		for (int k = 0; k < n; k++) {
			int v = r.c() - 2 - k;
			BlockPos base = r.cell(0, v, y);
			if (!floored(c, base) || desks.contains(col(base.getX(), base.getZ()))) {
				continue;
			}
			DyeColor card = cards[n == 3 ? k * 2 : n - 1 - k];
			c.set(base, chest(r.back()));
			for (int row = 1; row <= rows; row++) {
				boolean on = row == 1 || HallStyle.hash(h, v, row) % 3 != 0;
				c.set(base.above(row), concrete(on ? card : DyeColor.WHITE));
			}
			c.set(base.above(rows + 1), k == n / 2 ? concrete(env) : b(Blocks.DARK_OAK_PLANKS));
		}
	}

	/**
	 * The archive, on the door wall right of the door's jamb: a lectern next to the door, then chests and barrels,
	 * with bookshelves over the chests and barrels.
	 */
	private static void archive(CitadelCanvas c, Room r, int y, int W, int h, Set<Long> desks) {
		int n = zoneWidth(r);
		int shelf = Math.min(3, fh() - 4);
		for (int k = 0; k < n; k++) {
			BlockPos base = r.cell(0, r.c() + 2 + k, y);
			if (!floored(c, base) || desks.contains(col(base.getX(), base.getZ()))) {
				continue;
			}
			if (k == 0) {
				c.set(base, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.back()));
				continue;
			}
			c.set(base, k % 2 == 1 ? chest(r.back())
					: Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, r.back()));
			for (int row = 1; row <= shelf - 1; row++) {
				c.set(base.above(row), b(Blocks.BOOKSHELF));
			}
			put(c, base.above(shelf), HallStyle.pot(h + k));
		}
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
		int u0 = 3;
		int u1 = D - 6;
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
				// walkability audit: never over the flight of a two-floor hall (a pendant over the first steps was at
				// head height of the climber: the ring-1 hall's upper floor could not be reached on foot)
				if (F == 2 && f == 0 && Math.abs(v - mid) <= 1 && u <= fh()) {
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

	/**
	 * kit/glow-panel: a luminous skirting along the aisle. A warm glow strip lines each side of the 3-wide runner, one
	 * cell off its carpet and hugging the far edge of that cell, looking at the aisle: a continuous line of light at floor
	 * level from the door to the back wall that leads to the desks and lights the floor without hanging anything lower. It
	 * is light, not furniture: strips have no collision, so it never narrows the aisle, and it goes only into free,
	 * floored cells that are not a desk's column or its standing cell, and not along the flight of a two-floor hall
	 * (the first {@code fh()} cells). With the blocks module off the cells hold the invisible light block, so the light
	 * stays.
	 */
	private static void runway(CitadelCanvas c, Room r, int F, int y, Set<Long> desks) {
		int mid = r.c();
		for (int u = 1; u <= r.d() - 2; u++) {
			if (F == 2 && u <= fh()) {
				continue;
			}
			for (int side = -1; side <= 1; side += 2) {
				int v = mid + 2 * side;
				if (v < 1 || v > r.w() - 2) {
					continue;
				}
				BlockPos p = r.cell(u, v, y);
				if (floored(c, p) && !desks.contains(col(p.getX(), p.getZ()))) {
					put(c, p, c.kit.glowStrip(side < 0 ? r.side() : r.side().getOpposite(), false, false));
				}
			}
		}
	}

	/**
	 * Ceiling beams: a beam across the hall's width under the ceiling every {@value #PENDANT_STEP} cells, halfway
	 * between two rows of pendant lanterns (so it never meets one) and clear of the stair hole. They give the
	 * ceiling depth and make the height of the hall readable.
	 */
	private static void beams(CitadelCanvas c, Room r, int f, Mats m, HallStyle s) {
		int W = r.w();
		int D = r.d();
		int cu = D / 2;
		int yb = (f + 1) * fh() - 1;
		// barrio-hall3: dark oak on the scaled wooden halls, like the frame outside
		BlockState beam = HallFacade.beamBlock(c, s, m, r.side().getAxis());
		for (int u = cu % PENDANT_STEP + PENDANT_STEP / 2; u < D; u += PENDANT_STEP) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, yb);
				if (free(c, p) && !free(c, p.above())) {
					c.set(p, beam);
				}
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
