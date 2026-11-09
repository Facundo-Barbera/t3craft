package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.RingCampusGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * Facundo's office: a three-level pagoda tower, the "mirador" of the citadel. It stands on the
 * south-south-east garden terrace, flush against the outer rim of the elevated promenade (its door
 * opens straight onto the promenade deck, floor {@code y = Y0}), nine blocks from the main esplanade
 * where the player arrives ({@code CitadelFinish.SPAWN}): spawn, up the south ramp, along the
 * promenade, in. The balcony looks over the hub's roofline, the patio and the whole ring of barrios.
 * A piece of its own: static methods taking the {@link CitadelCanvas} first, no state. It runs after the
 * ground, the hub, the core and the nodes (see {@code RingCampusGenerator.generate}) and paints over the
 * terrace it stands on; it adds no zone, no group (every cell belongs to {@code cloister} by position,
 * like the terrace) and moves nothing of {@code RingCampusGeometry}.
 *
 * <h2>Plan (local coordinates {@code (dx, dz)} from the tower's centre {@link #CX}, {@link #CZ})</h2>
 *
 * <pre>
 *   m = max(|dx|, |dz|)   m <= 3  interior 7 x 7     m = 4  wall      m = 5  balcony walk
 *                         m = 6   balcony rail        m = 7  roof eave
 *
 *   level   floor y    feet y   what
 *   ground  Y0 (4)     5        lobby and archive: rug, bookcases and lectern, chests and barrels
 *   library Y0 + 5     10       bookcases, cartography table, lectern, bed, rug
 *   office  Y0 + 10    15       the desk, the big screen, rug, sofa, plants, banners, plaque; doors to the balcony
 *   ceiling Y0 + 16    -        exposed rafters; attic under the hip roof (lantern roof, finial)
 * </pre>
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li>{@link #office(CitadelCanvas)}: the whole tower, in order: {@link #clear} (air in the volume that
 *     the tower takes from the terrace), {@link #plinth}, {@link #floors}, {@link #walls}, {@link #stairways},
 *     {@link #lobby}, {@link #library}, {@link #study}, {@link #balcony}, {@link #roof},
 *     {@link #anchors}.</li>
 * <li>{@link #stairways}: two flights of five steps in the west column (ground to library, rising south)
 *     and the east column (library to office, rising north), each with the well in the floor above it
 *     fenced. Step {@code i} of a flight stands at {@code y = floor + 1 + i}; the last step is in the
 *     floor row above, so a player walks off it onto the next floor.</li>
 * <li>{@link #bigScreen}: the personal screen. A 5 x 3 big screen against the south wall of the office,
 *     bound to {@code HqGenerator.hubBinding(layout)}, the feed of the environment with most modules:
 *     the same binding as the hub screen, the most complete the plan offers (the kanban of every thread
 *     is {@code AnchorNames.THREAD_WALL}, one per plan, so it cannot repeat here). To show something else
 *     change the binding string in this one call ({@code Binding.project(..)}, {@code env:<id>}).</li>
 * </ul>
 *
 * <h2>Adjusting it</h2>
 *
 * <ul>
 * <li><b>Move the tower</b>: change {@link #CX}/{@link #CZ}; keep the footprint ({@code m <= 7}) inside the
 *     zone map (it needs {@code r < 40} for every column) and the door ({@code dz = -4}, facing north)
 *     against the promenade. Everything else is relative to it.</li>
 * <li><b>Furniture</b>: each room is one method with its blocks written as {@code (dx, dz)} next to a
 *     comment; the fences round the stair wells and the cell in front of the desk (the {@code office}
 *     anchor) are the only cells that must stay free.</li>
 * <li><b>Colours</b>: {@link #RUG}, {@link #RUG_EDGE}, {@link #BED}, {@link #BANNER}, and the roof
 *     blocks in {@link #roof}.</li>
 * <li><b>Archive terminal</b>: not added. {@code AnchorNames.ARCHIVE_TERMINAL} is unique in a plan (the
 *     archive cloister owns it; a second would clash and the blocks module turns only one into the
 *     terminal). The lobby has a lectern and bookcases as a stand-in; if the game later accepts a second
 *     terminal, put its block where the lectern is and publish the new anchor there.</li>
 * </ul>
 *
 * <h2>Blocks and roles</h2>
 *
 * Canvas: {@code FOOT}, {@code CALCITE}, {@code LOG}, {@code PLANKS}, {@code AIR}. Kit: {@code floor()},
 * {@code accent()}, {@code beam(axis)}, {@code wallPanel(true)}, {@code plaque}/{@code plaqueData}. Style:
 * {@code screen()}/{@code screenBase()} (through {@link CitadelHub#bigScreen}). Vanilla: cherry stairs,
 * slabs and fences, deepslate tile stairs, glass panes, bookshelves, a chest, barrels, a cartography
 * table, lecterns, a bed, carpets, wall banners, lanterns, potted plants. Light comes from lanterns
 * hanging under the ceilings and standing on the balcony posts, and from the lit shoji band of the attic;
 * there are no lamps in the floors.
 *
 * <h2>Anchors, fixtures and groups</h2>
 *
 * Anchors: {@code OFFICE} (the standing cell in front of the desk, facing the screen),
 * {@code OFFICE_BALCONY} (north side of the balcony, facing the hub) and the big screen
 * ({@code bigScreen("office", 5, 3, binding)}). Fixtures (never replaced by the light pass): the screen,
 * its base and the signs. The tower makes no walk spot: the NPCs have no business in it.
 */
public final class CitadelOffice {
	/**
	 * Anchor of the standing cell in front of the desk, facing its screen: a place for the game to teleport
	 * or guide the player to. Lives here while {@code AnchorNames} is a shared kernel class; the coordinator
	 * moves it to {@code AnchorNames.OFFICE} (and adds it to {@code AnchorNames.standing}) when integrating.
	 */
	public static final String OFFICE = "office";
	/** Anchor of the balcony's standing cell, looking out over the citadel (same note as {@link #OFFICE}). */
	public static final String OFFICE_BALCONY = "office_balcony";

	/** Angle (degrees from north, clockwise) of the terrace the tower stands on: between the south and south-east ramps. */
	public static final double ANGLE_DEG = 157.5;
	/** Distance from the promenade's outer rim to the tower's centre (half the 9-wide footprint, minus the overlap with the rim). */
	public static final double RIM_OFFSET = 3.5;
	/**
	 * Centre of the tower (x, z), derived from the geometry: the promenade's outer radius plus
	 * {@link #RIM_OFFSET} along {@link #ANGLE_DEG}. Its interior spans {@code CX - 3 .. CX + 3} and
	 * {@code CZ - 3 .. CZ + 3}. The tower itself (footprint, doors, stairs) keeps its size when the
	 * citadel is scaled: only its place follows the zones.
	 */
	public static final int CX = RingCampusGeometry.polar(RingCampusGeometry.GALLERY_R + RIM_OFFSET, Math.toRadians(ANGLE_DEG), 0).getX();
	public static final int CZ = RingCampusGeometry.polar(RingCampusGeometry.GALLERY_R + RIM_OFFSET, Math.toRadians(ANGLE_DEG), 0).getZ();

	/** Floor rows (the floor block's y): ground, library, office, ceiling of the office. */
	static final int F0 = Y0;
	static final int F1 = Y0 + 5;
	static final int F2 = Y0 + 10;
	static final int CEIL = Y0 + 16;
	/** Top of the wall (the attic's last row; the hip roof starts one row higher at its ring 4). */
	static final int WALL_TOP = CEIL + 3;

	public static final DyeColor RUG = DyeColor.RED;
	public static final DyeColor RUG_EDGE = DyeColor.PINK;
	public static final DyeColor BED = DyeColor.RED;
	public static final DyeColor BANNER = DyeColor.RED;

	private CitadelOffice() {
	}

	/** Paints the tower: see the class comment for the order and what each step does. */
	public static void office(CitadelCanvas c) {
		clear(c);
		plinth(c);
		floors(c);
		walls(c);
		stairways(c);
		lobby(c);
		library(c);
		study(c);
		bigScreen(c);
		balcony(c);
		roof(c);
		anchors(c);
	}

	// ------------------------------------------------------------------ helpers

	private static int m(int dx, int dz) {
		return Math.max(Math.abs(dx), Math.abs(dz));
	}

	private static BlockState facing(Block b, Direction f) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState cherryStairs(Direction facing, boolean top) {
		BlockState s = stairs(Blocks.CHERRY_STAIRS, facing);
		return top ? s.setValue(StairBlock.HALF, Half.TOP) : s;
	}

	private static BlockState fence() {
		return Blocks.CHERRY_FENCE.defaultBlockState();
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	private static BlockState carpet(DyeColor color) {
		return Blocks.CARPET.pick(color).defaultBlockState();
	}

	private static BlockState topSlab(Block b) {
		return slab(b).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState wallBanner(Direction f) {
		return Blocks.WALL_BANNER.pick(BANNER).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	/** A lantern under the ceiling at local {@code (dx, dz)}, on the row {@code y} (the cell under the ceiling). */
	private static void hang(CitadelCanvas c, int dx, int y, int dz) {
		c.put(CX + dx, y, CZ + dz, lantern(true), HANGING);
	}

	private static void shelves(CitadelCanvas c, int dx, int y, int dz, int height) {
		for (int i = 0; i < height; i++) {
			c.set(CX + dx, y + i, CZ + dz, Blocks.BOOKSHELF.defaultBlockState());
		}
	}

	private static void sign(CitadelCanvas c, int x, int y, int z, Direction face, DyeColor color, String... lines) {
		c.put(x, y, z, c.kit.plaque(face), DECOR);
		long k = BlockPos.asLong(x, y, z);
		c.data.put(k, c.kit.plaqueData(color, lines));
		c.fixtures.add(k);
	}

	// ------------------------------------------------------------------ site

	/** Clears what the terrace left in the tower's volume (bushes, the promenade's light post) and the ledge. */
	public static void clear(CitadelCanvas c) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = F0 + 1; y <= WALL_TOP + 3; y++) {
					c.set(CX + dx, y, CZ + dz, AIR);
				}
			}
		}
		// the door's apron on the promenade: the deck stays, anything standing on it goes
		for (int dx = -1; dx <= 1; dx++) {
			for (int y = F0 + 1; y <= F0 + 4; y++) {
				c.set(CX + dx, y, CZ - 5, AIR);
			}
		}
	}

	/** The solid base under the tower and the wall's footing course: the tower stands level with the promenade. */
	public static void plinth(CitadelCanvas c) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int y = 1; y < F0; y++) {
					c.set(CX + dx, y, CZ + dz, FOOT);
				}
				if (m(dx, dz) == 4) {
					c.set(CX + dx, F0, CZ + dz, FOOT);
				}
			}
		}
	}

	// ------------------------------------------------------------------ floors

	/**
	 * The floors of the three levels and the rafters of the ceiling: the kit's floor with a ring of beams
	 * along the walls; the ground floor takes the accent on its ring.
	 */
	public static void floors(CitadelCanvas c) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int m = m(dx, dz);
				Direction.Axis edge = Math.abs(dz) == 3 ? Direction.Axis.X : Direction.Axis.Z;
				c.set(CX + dx, F0, CZ + dz, m == 3 ? c.accent() : c.floor());
				for (int f : new int[] {F1, F2}) {
					c.set(CX + dx, f, CZ + dz, m == 3 ? c.kit.beam(edge) : c.floor());
				}
				c.set(CX + dx, CEIL, CZ + dz, m == 3 || Math.floorMod(dz, 2) == 0 ? c.kit.beam(m == 3 ? edge : Direction.Axis.X) : PLANKS);
			}
		}
	}

	// ------------------------------------------------------------------ walls

	/** The wall ring ({@code m = 4}): footing, plaster, beam courses at the floors, windows, doors. */
	public static void walls(CitadelCanvas c) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (m(dx, dz) == 4) {
					wallColumn(c, dx, dz);
				}
			}
		}
	}

	private static void wallColumn(CitadelCanvas c, int dx, int dz) {
		int adx = Math.abs(dx);
		int adz = Math.abs(dz);
		boolean corner = adx == 4 && adz == 4;
		boolean ew = adx == 4 && !corner;
		boolean north = dz == -4 && !corner;
		boolean south = dz == 4 && !corner;
		boolean west = dx == -4 && !corner;
		boolean east = dx == 4 && !corner;
		int au = Math.abs(ew ? dz : dx);
		Direction.Axis axis = ew ? Direction.Axis.Z : Direction.Axis.X;
		for (int y = F0 + 1; y <= WALL_TOP; y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y == F1 || y == F2 || y == CEIL) {
				s = c.kit.beam(axis);
			} else if (y < F1) {
				s = groundWall(y, au, north, east);
			} else if (y < F2) {
				s = au <= 1 && (y == F1 + 2 || y == F1 + 3) ? Blocks.GLASS_PANE.defaultBlockState() : CALCITE;
			} else if (y < CEIL) {
				s = officeWall(c, y, au, south, north || west);
			} else {
				s = y == CEIL + 2 && au <= 2 ? c.kit.wallPanel(true) : CALCITE;
			}
			c.set(CX + dx, y, CZ + dz, s);
		}
	}

	/**
	 * Ground level of the wall: a footing course, a slit window on the east side (the others have the
	 * stairs, the archive wall and a banner behind them), the door (north) between jambs.
	 */
	private static BlockState groundWall(int y, int au, boolean north, boolean east) {
		if (north) {
			if (au <= 1 && y <= F0 + 3) {
				return AIR;
			}
			if (au == 2) {
				return LOG;
			}
		}
		if (y == F0 + 1) {
			return FOOT;
		}
		if (east && au == 0 && (y == F0 + 2 || y == F0 + 3)) {
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		return CALCITE;
	}

	/**
	 * The office's wall: a plaster sill, a ribbon of glass panes up to the lintel on the north, west and east
	 * sides (the south is solid, behind the screen), mullions, and a door to the balcony on the north and west.
	 */
	private static BlockState officeWall(CitadelCanvas c, int y, int au, boolean south, boolean door) {
		if (south) {
			return CALCITE;
		}
		if (door && au <= 1 && y <= F2 + 3) {
			return AIR;
		}
		if (y == F2 + 1 || y == CEIL - 1) {
			return CALCITE;
		}
		if (au == 2) {
			return LOG;
		}
		return Blocks.GLASS_PANE.defaultBlockState();
	}

	// ------------------------------------------------------------------ stairs

	/**
	 * Two flights of five steps. West column {@code dx = -3}: from the ground floor rising south, steps at
	 * {@code dz = -3..1}; its well in the library floor is {@code dz = -2..0}. East column {@code dx = 3}:
	 * from the library rising north, steps at {@code dz = 3..-1}; its well in the office floor is
	 * {@code dz = 0..2}. The wells are fenced on the open sides; the mass under each step is plank.
	 */
	public static void stairways(CitadelCanvas c) {
		for (int i = 0; i < 5; i++) {
			// ground to library: step i at dz = -3 + i, y = F0 + 1 + i, rising south
			int x = CX - 3;
			int z = CZ - 3 + i;
			c.set(x, F0 + 1 + i, z, cherryStairs(Direction.SOUTH, false));
			for (int y = F0 + 1; y < F0 + 1 + i; y++) {
				c.set(x, y, z, PLANKS);
			}
			// library to office: step i at dz = 3 - i, y = F1 + 1 + i, rising north
			int xe = CX + 3;
			int ze = CZ + 3 - i;
			c.set(xe, F1 + 1 + i, ze, cherryStairs(Direction.NORTH, false));
			for (int y = F1 + 1; y < F1 + 1 + i; y++) {
				c.set(xe, y, ze, PLANKS);
			}
		}
		// the wells: the headroom of a step through the floor above, from the step before it (the nav sweeps the move)
		for (int dz = -2; dz <= 0; dz++) {
			c.set(CX - 3, F1, CZ + dz, AIR);
		}
		for (int dz = 0; dz <= 2; dz++) {
			c.set(CX + 3, F2, CZ + dz, AIR);
		}
		// fences round them, on the floor above (the north end of the first well is a bookcase)
		for (int dz = -2; dz <= 0; dz++) {
			c.set(CX - 2, F1 + 1, CZ + dz, fence());
		}
		for (int dz = 0; dz <= 2; dz++) {
			c.set(CX + 2, F2 + 1, CZ + dz, fence());
		}
		c.set(CX + 3, F2 + 1, CZ + 3, fence());
	}

	// ------------------------------------------------------------------ lobby (ground level, feet y = F0 + 1)

	/** Lobby and archive: a rug from the door, the archive wall (bookcases round a lectern), chests and barrels. */
	public static void lobby(CitadelCanvas c) {
		int y = F0 + 1;
		for (int dz = -3; dz <= 1; dz++) {
			c.set(CX - 1, y, CZ + dz, carpet(RUG_EDGE));
			c.set(CX, y, CZ + dz, carpet(RUG));
			c.set(CX + 1, y, CZ + dz, carpet(RUG_EDGE));
		}
		// archive wall (south): bookcases round a lectern, a banner above it
		for (int dx : new int[] {-2, -1, 1, 2}) {
			shelves(c, dx, y, 3, 2);
		}
		c.set(CX, y, CZ + 3, facing(Blocks.LECTERN, Direction.NORTH));
		c.set(CX, y + 2, CZ + 3, wallBanner(Direction.NORTH));
		// storage (east): a double chest and barrels
		c.set(CX + 3, y, CZ - 2, facing(Blocks.CHEST, Direction.WEST).setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT));
		c.set(CX + 3, y, CZ - 1, facing(Blocks.CHEST, Direction.WEST).setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT));
		for (int dz = 1; dz <= 3; dz++) {
			c.set(CX + 3, y, CZ + dz, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
		c.set(CX + 3, y + 1, CZ + 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		// banners on the jambs, a plaque over the door outside
		c.set(CX - 2, y + 2, CZ - 3, wallBanner(Direction.SOUTH));
		c.set(CX + 2, y + 2, CZ - 3, wallBanner(Direction.SOUTH));
		sign(c, CX, F0 + 4, CZ - 5, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Oficina", "");
		hang(c, 0, F1 - 1, -2);
		hang(c, 0, F1 - 1, 2);
		hang(c, 2, F1 - 1, -2);
		hang(c, -2, F1 - 1, -2);
		hang(c, -2, F1 - 1, 1);
	}

	// ------------------------------------------------------------------ library (feet y = F1 + 1)

	/** Library: bookcases round a north window, the cartography table and a lectern, a bed, a rug and plants. */
	public static void library(CitadelCanvas c) {
		int y = F1 + 1;
		// bookcases: both sides of the north window and the south-west corner
		for (int dz : new int[] {2, 3}) {
			shelves(c, -3, y, dz, 3);
		}
		shelves(c, -3, y, -3, 3);
		shelves(c, -2, y, -3, 3);
		shelves(c, -2, y, 3, 3);
		shelves(c, -1, y, 3, 3);
		// the map table and a lectern on the south wall, the way to the east stairs stays free at dx = 2
		c.set(CX, y, CZ + 3, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		c.set(CX + 1, y, CZ + 3, facing(Blocks.LECTERN, Direction.NORTH));
		// bed in the north-east corner, head against the north wall
		c.set(CX + 2, y, CZ - 3, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		c.set(CX + 2, y, CZ - 2, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		c.set(CX + 2, y, CZ - 1, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		c.set(CX, y, CZ - 3, Blocks.POTTED_BAMBOO.defaultBlockState());
		// rug
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				c.set(CX + dx, y, CZ + dz, carpet(dx == 0 && dz == 0 ? RUG : RUG_EDGE));
			}
		}
		hang(c, 0, F2 - 1, 0);
		hang(c, -1, F2 - 1, 2);
		hang(c, 2, F2 - 1, -2);
		hang(c, -2, F2 - 1, -2);
		hang(c, -2, F2 - 1, 2);
	}

	// ------------------------------------------------------------------ office (feet y = F2 + 1)

	/** The study: desk (the screen is {@link #bigScreen}), rug, sofa, bookcases, plants, banners and the plaque. */
	public static void study(CitadelCanvas c) {
		int y = F2 + 1;
		// the desk: a row of top slabs in front of the screen, a lantern and a pot on it
		for (int dx = -2; dx <= 1; dx++) {
			c.set(CX + dx, y, CZ + 2, topSlab(Blocks.CHERRY_SLAB));
		}
		c.set(CX + 1, y + 1, CZ + 2, lantern(false));
		c.set(CX - 2, y + 1, CZ + 2, Blocks.POTTED_BAMBOO.defaultBlockState());
		// rug
		for (int dx = -2; dx <= 1; dx++) {
			for (int dz = -2; dz <= 0; dz++) {
				boolean edge = dx == -2 || dx == 1 || dz == -2;
				c.set(CX + dx, y, CZ + dz, carpet(edge ? RUG_EDGE : RUG));
			}
		}
		// sofa against the east wall (north end), bookcases in the north-west, plants in the south-west
		c.set(CX + 3, y, CZ - 3, cherryStairs(Direction.EAST, false));
		c.set(CX + 3, y, CZ - 2, cherryStairs(Direction.EAST, false));
		for (int dz : new int[] {-3, -2}) {
			shelves(c, -3, y, dz, 3);
		}
		c.set(CX - 3, y, CZ + 2, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		c.set(CX - 3, y, CZ + 3, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		// banners and the plaque on the solid south wall
		c.set(CX - 3, y + 1, CZ + 3, wallBanner(Direction.NORTH));
		c.set(CX + 3, y + 1, CZ + 3, wallBanner(Direction.NORTH));
		sign(c, CX, CEIL - 1, CZ + 3, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "", "");
		hang(c, -2, CEIL - 1, -2);
		hang(c, 2, CEIL - 1, -2);
		hang(c, -2, CEIL - 1, 1);
		hang(c, 2, CEIL - 1, 1);
	}

	/** The personal big screen, against the south wall of the office, on a base (see the class comment). */
	public static void bigScreen(CitadelCanvas c) {
		HqGeometry.Surface s = new HqGeometry.Surface(new BlockPos(CX + 2, F2 + 2, CZ + 3), Direction.WEST, Direction.NORTH, 5, 3);
		CitadelHub.bigScreen(c, s, "office", HqGenerator.hubBinding(c.layout), true);
	}

	// ------------------------------------------------------------------ balcony

	/**
	 * The balcony round the office: a walk ({@code m = 5}) on corbels (upside-down stairs under it), a
	 * fascia beam and a fence ({@code m = 6}), corner posts to the eaves and, three to a side, fence posts
	 * with a standing lantern.
	 */
	public static void balcony(CitadelCanvas c) {
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				int m = m(dx, dz);
				if (m < 5) {
					continue;
				}
				int x = CX + dx;
				int z = CZ + dz;
				boolean corner = Math.abs(dx) == m && Math.abs(dz) == m;
				Direction in = Math.abs(dz) == m ? (dz < 0 ? Direction.SOUTH : Direction.NORTH) : (dx < 0 ? Direction.EAST : Direction.WEST);
				Direction.Axis edge = Math.abs(dz) == m ? Direction.Axis.X : Direction.Axis.Z;
				if (m == 5) {
					c.set(x, F2, z, c.floor());
					c.set(x, F2 - 1, z, corner ? FOOT : cherryStairs(in, true));
				} else {
					c.set(x, F2, z, c.kit.beam(edge));
					if (corner) {
						for (int y = F2 + 1; y <= CEIL + 1; y++) {
							c.set(x, y, z, LOG);
						}
					} else if (Math.abs(ew(dx, dz)) % 3 == 0) {
						c.set(x, F2 + 1, z, fence());
						c.set(x, F2 + 2, z, fence());
						c.set(x, F2 + 3, z, lantern(false));
					} else {
						c.set(x, F2 + 1, z, fence());
					}
				}
			}
		}
	}

	/** The coordinate along the side a ring-6 cell is on. */
	private static int ew(int dx, int dz) {
		return Math.abs(dz) == 6 ? dx : dz;
	}

	// ------------------------------------------------------------------ roof

	/**
	 * The hip roof: deepslate-tile stairs, one ring per row from the eave ({@code m = 7}, cherry stairs, at
	 * {@code y = CEIL + 1}) up to {@code m = 3}, a flat of tiles over {@code m <= 2}, a second small tier
	 * ({@code m <= 1}) and a finial: a fence post with a lantern. The attic under it is empty.
	 */
	public static void roof(CitadelCanvas c) {
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				int m = m(dx, dz);
				int x = CX + dx;
				int z = CZ + dz;
				Direction in = Math.abs(dz) == m ? (dz < 0 ? Direction.SOUTH : Direction.NORTH) : (dx < 0 ? Direction.EAST : Direction.WEST);
				if (m >= 3) {
					Block tile = m == 7 ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					c.set(x, CEIL + 1 + (7 - m), z, stairs(tile, in));
					if (m == 3) {
						// the upper tiers rest on a ring of tiles that touches the wall's roof ring
						c.set(x, CEIL + 4, z, FOOT);
					}
				} else if (m == 2) {
					c.set(x, CEIL + 5, z, FOOT);
				} else if (m == 1) {
					c.set(x, CEIL + 5, z, FOOT);
					c.set(x, CEIL + 6, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
				} else {
					c.set(x, CEIL + 5, z, FOOT);
					c.set(x, CEIL + 6, z, FOOT);
					c.set(x, CEIL + 7, z, fence());
					c.set(x, CEIL + 8, z, fence());
					c.set(x, CEIL + 9, z, lantern(false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ anchors

	/** {@code office}: in front of the desk, facing the screen. {@code office_balcony}: the balcony's north side, facing the hub. */
	public static void anchors(CitadelCanvas c) {
		c.anchor(Anchor.at(OFFICE, new BlockPos(CX, F2 + 1, CZ + 1), Direction.SOUTH));
		c.anchor(Anchor.at(OFFICE_BALCONY, new BlockPos(CX, F2 + 1, CZ - 5), Direction.NORTH));
	}
}
