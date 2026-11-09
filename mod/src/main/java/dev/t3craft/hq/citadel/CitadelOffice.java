package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * Facundo's office, the control tower of the citadel: three levels (lobby and workshop, archive and cartography,
 * control room) on the south-south-east terrace, flush against the outer rim of the elevated promenade. Its door
 * opens on the promenade deck (floor {@code y = y0()}), nine blocks from the esplanade where the player arrives
 * ({@code CitadelFinish.spawn()}): spawn, up the south ramp, along the promenade, in. A piece of its own: static
 * methods taking the {@link CitadelCanvas} first, no state. It runs after the ground, the hub, the core and the nodes
 * (see {@code RingCampusGenerator.generate}), adds no zone and no group, and moves nothing of {@code RingCampusGeometry}.
 *
 * <h2>Scale (struct/leader-office)</h2>
 *
 * Facundo found the old tower tight and its stairs a jump. The interior grew from 7 x 7 x 4 per level to
 * <b>9 x 9</b> with <b>5-high rooms</b> (the control room 7 high): 2.1 times the old volume in the lobby and the
 * archive, 2.3 times in the control room, and no stair eats the middle of a room any more. Every route is at least
 * three blocks wide, every stair is a walking stair (see below).
 *
 * <h2>Plan (local coordinates {@code (dx, dz)} from the tower's centre {@link #CX()}, {@link #CZ()}; north is -dz, the hub)</h2>
 *
 * <pre>
 *   m = max(|dx|, |dz|)   m <= 4 interior 9 x 9     m = 5 wall     m = 6 skirt, pilasters, eave
 *
 *   level    floor y   feet y   what
 *   lobby    y0()        +1     rug from the door, 2 double chests and barrels, 7 workbenches (crafting, smithing,
 *                               fletching, stonecutter, grindstone, loom, blast furnace), anvil, ender chest, stair A (west)
 *   archive  y0() + 6    +1     archive lecterns and shelves, cartography and enchanting tables, jukebox, model of the HQ,
 *                               bed, stair B (east)
 *   control  y0() + 12   +1     big screens, attention board, consoles, chests; north door to the balcony
 *   ceiling  y0() + 20   -      rafters; clerestory band, hip roof, lantern cupola
 * </pre>
 *
 * <h2>Stairs you walk up</h2>
 *
 * Two flights of six steps, each two blocks wide, one step per cell and one block per step (a stair block is a
 * half-block climb, then another half block: no jump). Each flight is entered <b>head-on</b> from a free landing
 * (A: {@code (-4..-3, -4)} in the lobby; B: {@code (3..4, 4)} in the archive), has two free blocks above every step
 * (the floor above is opened over steps 3 to 5, the well, and fenced), and ends flush with the floor above. The
 * rest of the level around the wells is at least three blocks wide.
 *
 * <h2>Control room</h2>
 *
 * <ul>
 * <li><b>Threads</b>: a 7 x 4 big screen (south wall, key {@code office}) bound to {@code HqGenerator.hubBinding}, the
 *     environment with most modules, as on the hub; three consoles (lecterns) face it, the standing cell in front of
 *     them is the {@link #OFFICE} anchor.</li>
 * <li><b>Projects</b>: two 3 x 3 screens on the west wall (keys {@code office_w0}, {@code office_w1}), each bound to
 *     {@code project:<key>} of the two projects with most desks taken ({@link #projectBindings}); unbound while the
 *     HQ has fewer. Chests under them.</li>
 * <li><b>Decision podiums</b>: five along the south wall under the threads screen, bound {@code board:0..4} (the five most
 *     urgent threads that need the player); a click opens the thread to answer it. See {@link #podiums}.</li>
 * <li><b>Attention board</b>: on the east wall, five status lamps anchored {@code board_lamp_5..9}, so they show the
 *     6th to 10th most urgent thread: the hub board keeps ranks 1 to 5. They are plain additive anchors.</li>
 * <li><b>Archive terminal</b>: {@code AnchorNames.ARCHIVE_TERMINAL} is unique in a plan, so the archive level has
 *     lecterns as stand-ins (see {@code docs/proposals/leader-office-blocks.md}: a second terminal needs a kernel
 *     change). Same for the kanban {@code THREAD_WALL} and the board itself.</li>
 * <li><b>Work and storage</b>: lobby workbenches and double chests, archive chests and bed, control-room chests.</li>
 * <li><b>Model</b>: a 3 x 3 model of the HQ on the archive table: hub, four ramps, four barrios; the red carpet is this
 *     tower.</li>
 * </ul>
 *
 * <h2>Adjusting it</h2>
 *
 * <ul>
 * <li><b>Move the tower</b>: change {@link #RIM_OFFSET}/{@link #ANGLE_DEG}; keep every column of the walls and the
 *     skirt ({@code m <= 6}) inside the zone map ({@code r < 40}) and the door ({@code dz = -5}, facing north) on the
 *     promenade deck.</li>
 * <li><b>Furniture</b>: each room is one method with blocks written as {@code (dx, dz)} next to a comment; keep the
 *     landings and the wells' fences as they are.</li>
 * <li><b>Colours</b>: {@link #RUG}, {@link #RUG_EDGE}, {@link #BED}, {@link #BANNER}.</li>
 * </ul>
 *
 * <h2>Anchors, fixtures and groups</h2>
 *
 * Anchors: {@code OFFICE} (in front of the consoles, facing the main screen), {@code OFFICE_BALCONY}, three big screens,
 * five board lamps ({@code board_lamp_5..9}) and five decision podiums ({@code decision_podium_0..4}, bound {@code board:0..4},
 * under the threads screen: a click opens the thread that needs the player). Fixtures (never replaced by the light pass): the
 * screens and their bases, the board, its lamps, the podiums, the signs. The podium anchors use the kernel's
 * {@code AnchorNames.podium} (kit/decision-podium).
 */
public final class CitadelOffice {
	/**
	 * Anchor of the standing cell in front of the consoles, facing the main screen: a place for the game to teleport
	 * or guide the player to. Lives here while {@code AnchorNames} is a shared kernel class; the coordinator
	 * moves it to {@code AnchorNames.OFFICE} (and adds it to {@code AnchorNames.standing}) when integrating.
	 */
	public static final String OFFICE = "office";
	/** Anchor of the balcony's standing cell, looking out over the citadel (same note as {@link #OFFICE}). */
	public static final String OFFICE_BALCONY = "office_balcony";

	/** Angle (degrees from north, clockwise) of the terrace the tower stands on: between the south and south-east ramps. */
	public static final double ANGLE_DEG = 157.5;
	/**
	 * Distance from the promenade's outer rim to the tower's centre. With the 11-wide wall ring this puts the door on
	 * the deck's rim, the north-west corner at r = 25 (the deck stays four blocks wide) and the south-east skirt corner
	 * at r < 40.
	 */
	public static final double RIM_OFFSET = 4.5;

	/** Half width of the interior, of the wall ring and of the skirt (pilasters, eave). */
	private static final int H = 4;
	private static final int W = 5;
	private static final int E = 6;
	/** Floor to floor of the lobby and the archive. */
	private static final int FH = 6;

	/**
	 * Centre of the tower (x, z), derived from the geometry: the promenade's outer radius plus {@link #RIM_OFFSET} along
	 * {@link #ANGLE_DEG} (methods: the radius follows the citadel's scale). The tower itself keeps its size when the citadel
	 * is scaled: only its place follows the zones.
	 */
	public static int CX() {
		return RingCampusGeometry.polar(CitadelCanvas.dims().galleryR + RIM_OFFSET, Math.toRadians(ANGLE_DEG), 0).getX();
	}

	public static int CZ() {
		return RingCampusGeometry.polar(CitadelCanvas.dims().galleryR + RIM_OFFSET, Math.toRadians(ANGLE_DEG), 0).getZ();
	}

	/** Floor rows (the floor block's y): lobby, archive, control room, ceiling of the control room. */
	static int F0() {
		return y0();
	}

	static int F1() {
		return y0() + FH;
	}

	static int F2() {
		return y0() + 2 * FH;
	}

	static int CEIL() {
		return F2() + 8;
	}

	/** Top of the wall (the clerestory band's last row; the hip roof starts one row higher at its ring 5). */
	static int WALL_TOP() {
		return CEIL() + 2;
	}

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
		trim(c);
		stairways(c);
		lobby(c);
		archive(c);
		control(c);
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

	private static BlockState chest(Direction f, ChestType t) {
		return facing(Blocks.CHEST, f).setValue(BlockStateProperties.CHEST_TYPE, t);
	}

	private static BlockState barrel() {
		return Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
	}

	/** Local {@code (dx, dz)} to the canvas. */
	private static void set(CitadelCanvas c, int dx, int y, int dz, BlockState s) {
		c.set(CX() + dx, y, CZ() + dz, s);
	}

	/** A lantern under the ceiling at local {@code (dx, dz)}, on the row {@code y} (the cell under the ceiling). */
	private static void hang(CitadelCanvas c, int dx, int y, int dz) {
		c.put(CX() + dx, y, CZ() + dz, lantern(true), HANGING);
	}

	private static void shelves(CitadelCanvas c, int dx, int y, int dz, int height) {
		for (int i = 0; i < height; i++) {
			set(c, dx, y + i, dz, Blocks.BOOKSHELF.defaultBlockState());
		}
	}

	private static void sign(CitadelCanvas c, int x, int y, int z, Direction face, DyeColor color, String... lines) {
		c.put(x, y, z, c.kit.plaque(face), DECOR);
		long k = BlockPos.asLong(x, y, z);
		c.data.put(k, c.kit.plaqueData(color, lines));
		c.fixtures.add(k);
	}

	/** The direction from a ring-{@code m} cell towards the tower's centre (the cell's own side; corners take the z side). */
	private static Direction inward(int dx, int dz) {
		int m = m(dx, dz);
		return Math.abs(dz) == m ? (dz < 0 ? Direction.SOUTH : Direction.NORTH) : (dx < 0 ? Direction.EAST : Direction.WEST);
	}

	// ------------------------------------------------------------------ site

	/** Clears what the terrace left in the tower's volume (bushes, the promenade's light post) and the balcony's airspace. */
	public static void clear(CitadelCanvas c) {
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -E; dz <= E; dz++) {
				for (int y = F0() + 1; y <= CEIL() + 16; y++) {
					set(c, dx, y, dz, AIR);
				}
			}
		}
		// the balcony's airspace above the promenade (the deck below stays as it is)
		for (int dx = -W; dx <= W; dx++) {
			for (int dz = -8; dz <= -E - 1; dz++) {
				for (int y = F2() - 2; y <= CEIL() + 16; y++) {
					set(c, dx, y, dz, AIR);
				}
			}
		}
		// the door's apron on the promenade: the deck stays, anything standing on it goes
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -8; dz <= -E - 1; dz++) {
				for (int y = F0() + 1; y <= F0() + 4; y++) {
					set(c, dx, y, dz, AIR);
				}
			}
		}
	}

	/**
	 * The solid base under the tower, the wall's footing course, and a one-block skirt round it wherever the ground is
	 * lower than the deck (the tower stands level with the promenade; on the deck itself nothing is added).
	 */
	public static void plinth(CitadelCanvas c) {
		double deck = CitadelCanvas.dims().galleryR - 0.5;
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -E; dz <= E; dz++) {
				int m = m(dx, dz);
				if (m <= W) {
					for (int y = 1; y < F0(); y++) {
						set(c, dx, y, dz, FOOT);
					}
					if (m == W) {
						set(c, dx, F0(), dz, FOOT);
					}
				} else if (Math.hypot(CX() + dx, CZ() + dz) > deck) {
					for (int y = 1; y <= F0(); y++) {
						set(c, dx, y, dz, plinthBlock(CX() + dx, y, CZ() + dz));
					}
					set(c, dx, F0() + 1, dz, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		}
	}

	/** The skirt's materials: polished course under the cap, bricks and cracked tiles scattered in the tiles (never one flat colour). */
	private static BlockState plinthBlock(int x, int y, int z) {
		if (y == F0()) {
			return CitadelCanvas.b(Blocks.POLISHED_DEEPSLATE);
		}
		int h = Math.floorMod(x * 31 + y * 17 + z * 7, 11);
		if (h < 2) {
			return CitadelCanvas.b(Blocks.CRACKED_DEEPSLATE_TILES);
		}
		return h < 4 ? CitadelCanvas.b(Blocks.DEEPSLATE_BRICKS) : FOOT;
	}

	// ------------------------------------------------------------------ floors

	/**
	 * The floors of the three levels and the rafters of the ceiling: the kit's floor with a ring of beams along the
	 * walls; the lobby takes the accent on its ring.
	 */
	public static void floors(CitadelCanvas c) {
		for (int dx = -H; dx <= H; dx++) {
			for (int dz = -H; dz <= H; dz++) {
				int m = m(dx, dz);
				Direction.Axis edge = Math.abs(dz) == H ? Direction.Axis.X : Direction.Axis.Z;
				set(c, dx, F0(), dz, m == H ? c.accent() : c.floor());
				set(c, dx, F1(), dz, m == H ? c.kit.beam(edge) : c.floor());
				// the control room's floor is dark tile so the screens stand out
				set(c, dx, F2(), dz, m == H ? c.kit.beam(edge) : (Math.floorMod(dx + dz, 2) == 0 ? FOOT : CitadelCanvas.RIB));
				set(c, dx, CEIL(), dz, m == H || Math.floorMod(dz, 2) == 0 ? c.kit.beam(m == H ? edge : Direction.Axis.X) : PLANKS);
			}
		}
	}

	// ------------------------------------------------------------------ walls

	/** The wall ring ({@code m = 5}): footing, plaster, beam courses at the floors, windows, doors, pilasters. */
	public static void walls(CitadelCanvas c) {
		for (int dx = -W; dx <= W; dx++) {
			for (int dz = -W; dz <= W; dz++) {
				if (m(dx, dz) == W) {
					wallColumn(c, dx, dz);
				}
			}
		}
	}

	private static void wallColumn(CitadelCanvas c, int dx, int dz) {
		boolean corner = Math.abs(dx) == W && Math.abs(dz) == W;
		boolean north = dz == -W && !corner;
		boolean south = dz == W && !corner;
		boolean ns = north || south;
		int au = Math.abs(ns ? dx : dz);
		Direction.Axis axis = ns ? Direction.Axis.X : Direction.Axis.Z;
		for (int y = F0() + 1; y <= WALL_TOP(); y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y == F1() || y == F2() || y == CEIL()) {
				s = c.kit.beam(axis);
			} else if (y < F1()) {
				s = lobbyWall(c, y, au, north, axis);
			} else if (y < F2()) {
				s = archiveWall(c, y, au, south);
			} else if (y < CEIL()) {
				s = controlWall(c, y, au, north, south);
			} else {
				s = au == 2 ? LOG : (au <= 1 ? c.kit.wallPanel(true) : CALCITE);
			}
			if (s == CALCITE) {
				s = plaster(CX() + dx, y, CZ() + dz);
			}
			set(c, dx, y, dz, s);
		}
	}

	/**
	 * Wall plaster by level: the lobby is stone brick (heavy base: some cracked, some mossy), the rest calcite with polished
	 * diorite and smooth stone scattered in a fixed pattern, so no wall is one flat colour.
	 */
	private static BlockState plaster(int x, int y, int z) {
		int h = Math.floorMod(x * 13 + y * 7 + z * 5, 9);
		if (y < F1()) {
			return CitadelCanvas.b(h == 0 ? Blocks.CRACKED_STONE_BRICKS : h == 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
		}
		return h == 0 ? CitadelCanvas.b(Blocks.POLISHED_DIORITE) : h == 1 ? CitadelCanvas.b(Blocks.SMOOTH_STONE) : CALCITE;
	}

	/** Lobby wall: a footing course, slit windows over the benches and the chests, the door (north) between two logs. */
	private static BlockState lobbyWall(CitadelCanvas c, int y, int au, boolean north, Direction.Axis axis) {
		if (north && au <= 1) {
			if (y <= F0() + 3) {
				return AIR;
			}
			if (y == F0() + 4) {
				return c.kit.beam(axis);
			}
			return CALCITE;
		}
		if (au == 2) {
			return LOG;
		}
		if (y == F0() + 1) {
			return FOOT;
		}
		if (!north && au <= 1 && (y == F0() + 3 || y == F0() + 4)) {
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		if (au >= 3 && y >= F0() + 2 && y <= F0() + 4) {
			return c.kit.wallPanel(false);
		}
		return CALCITE;
	}

	/** Archive wall: a plaster sill, a ribbon of glass over the middle (not on the south, behind the shelves), shoji at the sides. */
	private static BlockState archiveWall(CitadelCanvas c, int y, int au, boolean south) {
		if (au == 2) {
			return LOG;
		}
		if (!south && au <= 1 && y >= F1() + 2 && y <= F1() + 4) {
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		if (au >= 3 && y >= F1() + 2 && y <= F1() + 4) {
			return c.kit.wallPanel(false);
		}
		return CALCITE;
	}

	/**
	 * Control-room wall: on the north a door to the balcony under a glass transom and tall windows at the sides; the other
	 * sides are solid (screens, board and chests stand against them) with lit shoji at the sides.
	 */
	private static BlockState controlWall(CitadelCanvas c, int y, int au, boolean north, boolean south) {
		if (north) {
			if (au <= 1) {
				if (y <= F2() + 3) {
					return AIR;
				}
				return y <= F2() + 6 ? Blocks.GLASS_PANE.defaultBlockState() : CALCITE;
			}
			if (au == 2) {
				return LOG;
			}
			if (y == F2() + 1 || y == F2() + 7) {
				return CALCITE;
			}
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		if (au == 2) {
			return LOG;
		}
		if (au >= 3 && y >= F2() + 2 && y <= F2() + 6) {
			return c.kit.wallPanel(true);
		}
		return CALCITE;
	}

	/**
	 * Outside the walls: a cornice of upside-down stairs at each floor (the balcony takes the north side of the control
	 * floor), three pilasters on the east, south and west faces, sills under the archive windows.
	 */
	public static void trim(CitadelCanvas c) {
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -E; dz <= E; dz++) {
				if (m(dx, dz) != E) {
					continue;
				}
				boolean corner = Math.abs(dx) == E && Math.abs(dz) == E;
				boolean north = dz == -E && !corner;
				boolean ns = Math.abs(dz) == E && !corner;
				int au = Math.abs(ns ? dx : dz);
				if (!corner && !north && au == 2) {
					// pilaster: base, log up to the eave
					set(c, dx, F0() + 1, dz, FOOT);
					for (int y = F0() + 2; y <= CEIL() + 1; y++) {
						set(c, dx, y, dz, LOG);
					}
					// buttress at its foot: a block of the skirt one step further out, capped by a tile stair
					int ox = Math.abs(dx) == E ? Integer.signum(dx) : 0;
					int oz = Math.abs(dz) == E ? Integer.signum(dz) : 0;
					if (Math.hypot(CX() + dx + ox, CZ() + dz + oz) > CitadelCanvas.dims().galleryR - 0.5) {
						for (int y = 1; y <= F0(); y++) {
							set(c, dx + ox, y, dz + oz, plinthBlock(CX() + dx + ox, y, CZ() + dz + oz));
						}
						Direction toward = ox > 0 ? Direction.WEST : ox < 0 ? Direction.EAST : oz > 0 ? Direction.NORTH : Direction.SOUTH;
						set(c, dx + ox, F0() + 1, dz + oz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toward));
					}
					continue;
				}
				for (int row : new int[] {F1(), F2()}) {
					if (row == F2() && north) {
						continue;
					}
					set(c, dx, row, dz, corner ? topSlab(Blocks.CHERRY_SLAB) : cherryStairs(inward(dx, dz), true));
				}
				if (!corner && au <= 1 && dz != E) {
					// sill under the archive window
					set(c, dx, F1() + 1, dz, slab(Blocks.CHERRY_SLAB));
				}
			}
		}
	}

	// ------------------------------------------------------------------ stairs

	/**
	 * Two flights of six steps, two blocks wide.
	 *
	 * <ul>
	 * <li><b>A</b>, lobby to archive, west columns {@code dx = -4, -3}: step {@code i} at {@code dz = -3 + i}
	 *     (rising south), {@code y = F0() + 1 + i}. Landing {@code (-4..-3, -4)}. Well in the archive floor over
	 *     {@code dz = -1..1} (steps 3 to 5), fenced; the last step is in the floor row.</li>
	 * <li><b>B</b>, archive to control room, east columns {@code dx = 3, 4}: step {@code i} at {@code dz = 3 - i}
	 *     (rising north), {@code y = F1() + 1 + i}. Landing {@code (3..4, 4)}. Well over {@code dz = 1..-1}.</li>
	 * </ul>
	 *
	 * Step {@code i} is reached from step {@code i - 1} by half a block and leaves one half block to the next; a player
	 * on step {@code i} has two free blocks above. The mass under each step is plank.
	 */
	public static void stairways(CitadelCanvas c) {
		for (int k = 0; k < 2; k++) {
			for (int i = 0; i < FH; i++) {
				// A: ground to archive, rising south
				int xa = -4 + k;
				int za = -3 + i;
				set(c, xa, F0() + 1 + i, za, cherryStairs(Direction.SOUTH, false));
				for (int y = F0() + 1; y < F0() + 1 + i; y++) {
					set(c, xa, y, za, PLANKS);
				}
				// B: archive to control room, rising north
				int xb = 3 + k;
				int zb = 3 - i;
				set(c, xb, F1() + 1 + i, zb, cherryStairs(Direction.NORTH, false));
				for (int y = F1() + 1; y < F1() + 1 + i; y++) {
					set(c, xb, y, zb, PLANKS);
				}
			}
			// the wells: the headroom of steps 3 to 5 goes through the floor above
			for (int dz = -1; dz <= 1; dz++) {
				set(c, -4 + k, F1(), dz, AIR);
				set(c, 3 + k, F2(), dz, AIR);
			}
		}
	}

	// ------------------------------------------------------------------ lobby (feet y = F0() + 1)

	/** Lobby and workshop: a rug from the door, double chests and barrels, seven workbenches, banners and a plaque. */
	public static void lobby(CitadelCanvas c) {
		int y = F0() + 1;
		// rug: three wide from the door to the middle
		for (int dz = -4; dz <= 1; dz++) {
			set(c, -1, y, dz, carpet(RUG_EDGE));
			set(c, 0, y, dz, carpet(RUG));
			set(c, 1, y, dz, carpet(RUG_EDGE));
		}
		// storage (east wall): two double chests, then barrels two high
		set(c, 4, y, -4, chest(Direction.WEST, ChestType.RIGHT));
		set(c, 4, y, -3, chest(Direction.WEST, ChestType.LEFT));
		set(c, 4, y, -2, chest(Direction.WEST, ChestType.RIGHT));
		set(c, 4, y, -1, chest(Direction.WEST, ChestType.LEFT));
		for (int dz = 0; dz <= 4; dz++) {
			set(c, 4, y, dz, barrel());
			set(c, 4, y + 1, dz, barrel());
		}
		// workbenches (south wall): the player stands north of them
		set(c, -3, y, 4, Blocks.CRAFTING_TABLE.defaultBlockState());
		set(c, -2, y, 4, Blocks.SMITHING_TABLE.defaultBlockState());
		set(c, -1, y, 4, Blocks.FLETCHING_TABLE.defaultBlockState());
		set(c, 0, y, 4, facing(Blocks.STONECUTTER, Direction.NORTH));
		set(c, 1, y, 4, Blocks.GRINDSTONE.defaultBlockState());
		set(c, 2, y, 4, facing(Blocks.LOOM, Direction.NORTH));
		set(c, 3, y, 4, facing(Blocks.BLAST_FURNACE, Direction.NORTH));
		// utility corner: an anvil and an ender chest (storage that follows the player)
		set(c, -4, y, 4, facing(Blocks.ANVIL, Direction.EAST));
		set(c, -4, y, 3, facing(Blocks.ENDER_CHEST, Direction.EAST));
		// banners on the door's inner jambs, a plaque over the door outside
		set(c, -2, y + 2, -4, wallBanner(Direction.SOUTH));
		set(c, 2, y + 2, -4, wallBanner(Direction.SOUTH));
		sign(c, CX(), F0() + 4, CZ() - E, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Oficina", "");
		for (int[] p : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {0, 2}, {0, -3}}) {
			hang(c, p[0], F1() - 1, p[1]);
		}
		// canopy over the door, four blocks above the deck so nothing is in the way: stairs against the wall, a slab edge, two lanterns
		for (int dx = -2; dx <= 2; dx++) {
			set(c, dx, F0() + 5, -E, cherryStairs(Direction.SOUTH, false));
			set(c, dx, F0() + 5, -E - 1, slab(Blocks.CHERRY_SLAB));
		}
		hang(c, -2, F0() + 4, -E - 1);
		hang(c, 2, F0() + 4, -E - 1);
	}

	// ------------------------------------------------------------------ archive (feet y = F1() + 1)

	/** Archive and cartography: shelves and lecterns, the map table and the model of the HQ, a bed nook, a reading corner. */
	public static void archive(CitadelCanvas c) {
		int y = F1() + 1;
		// the well of flight A, fenced on its open sides
		for (int dz = -2; dz <= 1; dz++) {
			set(c, -2, y, dz, fence());
		}
		for (int dx = -4; dx <= -3; dx++) {
			set(c, dx, y, -2, fence());
		}
		// bed nook, north-west: head against the north wall
		set(c, -4, y, -4, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		set(c, -4, y, -3, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(c, -3, y, -4, chest(Direction.SOUTH, ChestType.SINGLE));
		set(c, -3, y, -3, Blocks.JUKEBOX.defaultBlockState());
		// south wall: shelves and the archive lecterns (stand-ins for the archive terminal, see the class comment)
		for (int dx : new int[] {-4, -3, -1, 1}) {
			shelves(c, dx, y, 4, 3);
		}
		for (int dx : new int[] {-2, 0, 2}) {
			set(c, dx, y, 4, facing(Blocks.LECTERN, Direction.NORTH));
		}
		set(c, -2, y + 2, 4, wallBanner(Direction.NORTH));
		set(c, 2, y + 2, 4, wallBanner(Direction.NORTH));
		sign(c, CX(), y + 2, CZ() + 4, Direction.NORTH, DyeColor.ORANGE, "", "Archivo", "", "");
		// north wall: map table and a reading stand under the window, a sofa in the north-east corner
		set(c, -1, y, -4, Blocks.POTTED_BAMBOO.defaultBlockState());
		set(c, 0, y, -4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		set(c, 1, y, -4, facing(Blocks.LECTERN, Direction.SOUTH));
		set(c, 2, y, -4, Blocks.ENCHANTING_TABLE.defaultBlockState());
		set(c, 3, y, -4, cherryStairs(Direction.SOUTH, false));
		set(c, 4, y, -4, cherryStairs(Direction.SOUTH, false));
		set(c, 4, y, -3, cherryStairs(Direction.WEST, false));
		// the model of the HQ: hub in the middle, the four ramps, four barrios; the red carpet is this tower
		set(c, 0, y, 0, CALCITE);
		set(c, 0, y + 1, 0, lantern(false));
		// ramps in bamboo mosaic like the real ones, barrios in pink terracotta
		set(c, 0, y, -1, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, Direction.SOUTH));
		set(c, 0, y, 1, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, Direction.NORTH));
		set(c, 1, y, 0, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, Direction.WEST));
		set(c, -1, y, 0, stairs(Blocks.BAMBOO_MOSAIC_STAIRS, Direction.EAST));
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				set(c, sx, y, sz, CitadelCanvas.terracotta(DyeColor.PINK));
			}
		}
		set(c, 1, y + 1, 1, carpet(DyeColor.RED));
		// rug round the table
		for (int dx = -1; dx <= 2; dx++) {
			set(c, dx, y, 2, carpet(RUG_EDGE));
			set(c, dx, y, -2, carpet(RUG_EDGE));
		}
		for (int dz = -1; dz <= 1; dz++) {
			set(c, 2, y, dz, carpet(RUG_EDGE));
		}
		for (int[] p : new int[][] {{-2, -3}, {2, -3}, {0, 2}, {-2, 3}, {-1, 0}}) {
			hang(c, p[0], F2() - 1, p[1]);
		}
	}

	// ------------------------------------------------------------------ control room (feet y = F2() + 1)

	/**
	 * Control room: the threads screen with its consoles, two project screens over chests, the attention board, a rug,
	 * the fences of flight B's well and the plaque.
	 */
	public static void control(CitadelCanvas c) {
		int y = F2() + 1;
		// the well of flight B, fenced on its open sides
		for (int dz = -1; dz <= 1; dz++) {
			set(c, 2, y, dz, fence());
		}
		for (int dx = 2; dx <= 4; dx++) {
			set(c, dx, y, 2, fence());
		}
		// rug in front of the consoles
		for (int dx = -3; dx <= 1; dx++) {
			for (int dz = -2; dz <= 0; dz++) {
				boolean edge = dx == -3 || dx == 1 || dz == -2;
				set(c, dx, y, dz, carpet(edge ? RUG_EDGE : RUG));
			}
		}
		// the consoles: three lecterns facing the player, slabs between them with a lantern and a plant
		for (int dx : new int[] {-3, -1, 1}) {
			set(c, dx, y, 1, facing(Blocks.LECTERN, Direction.NORTH));
		}
		set(c, -2, y, 1, topSlab(Blocks.CHERRY_SLAB));
		set(c, 0, y, 1, topSlab(Blocks.CHERRY_SLAB));
		set(c, -2, y + 1, 1, lantern(false));
		set(c, 0, y + 1, 1, Blocks.POTTED_BAMBOO.defaultBlockState());
		// under the main screen: the decision podiums (five, west to east) and two barrels of storage
		podiums(c, y);
		for (int dx = 2; dx <= 3; dx++) {
			set(c, dx, y, 4, barrel());
		}
		set(c, -4, y, 4, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		set(c, 4, y, 4, Blocks.POTTED_BAMBOO.defaultBlockState());
		// threads screen: 7 x 4 on the south wall, bound to the hub's feed
		HqGeometry.Surface main = new HqGeometry.Surface(new BlockPos(CX() + 3, F2() + 3, CZ() + 4), Direction.WEST, Direction.NORTH, 7, 4);
		CitadelHub.bigScreen(c, main, OFFICE, HqGenerator.hubBinding(c.layout), true);
		sign(c, CX(), F2() + 7, CZ() + 4, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Hilos", "");
		// project screens: two 3 x 3 on the west wall, chests under them
		List<String> projects = projectBindings(c, 2);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - 4, F2() + 3, CZ() + 3), Direction.NORTH, Direction.EAST, 3, 3),
				"office_w0", projects.get(0), false);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - 4, F2() + 3, CZ() - 1), Direction.NORTH, Direction.EAST, 3, 3),
				"office_w1", projects.get(1), false);
		for (int dz = -3; dz <= 3; dz++) {
			if (dz != 0) {
				set(c, -4, y, dz, chest(Direction.EAST, ChestType.SINGLE));
			}
		}
		set(c, -4, F2() + 3, 0, wallBanner(Direction.EAST));
		set(c, -4, F2() + 4, 0, wallBanner(Direction.EAST));
		attentionBoard(c);
		// light: lanterns only over the middle and the north half; the screen's side gets invisible light, so nothing hangs in front of it
		for (int[] p : new int[][] {{-3, -3}, {0, -3}, {3, -3}, {-3, 0}, {0, 0}}) {
			hang(c, p[0], CEIL() - 1, p[1]);
		}
		for (int dx : new int[] {-3, 0, 3}) {
			set(c, dx, CEIL() - 1, 3, CitadelCanvas.invisibleLight(14));
		}
	}

	/**
	 * The attention board on the east wall: a frame, a dark panel and five status lamps anchored {@code board_lamp_5..9}
	 * (the hub's board owns 0 to 4), so the office shows the next five threads that need the player.
	 */
	private static void attentionBoard(CitadelCanvas c) {
		int x = CX() + H;
		int bottom = F2() + 4;
		for (int dz = -3; dz <= 3; dz++) {
			for (int row = 0; row < 4; row++) {
				boolean edge = Math.abs(dz) == 3 || row == 0 || row == 3;
				BlockState s = edge ? c.kit.beam(row == 0 || row == 3 ? Direction.Axis.Z : Direction.Axis.Y) : c.style.palette().get(HqStyle.Role.BOARD);
				c.fixture(new BlockPos(x, bottom + row, CZ() + dz), s);
			}
		}
		int lamp = 5;
		for (int dz = -2; dz <= 2; dz++) {
			BlockPos p = new BlockPos(x, bottom + 2, CZ() + dz);
			c.fixture(p, c.style.statusLamp());
			c.anchor(Anchor.face(AnchorNames.boardLamp(lamp), p, Direction.WEST));
			// a plaque under each lamp with its rank in the attention queue (the hub board has 1 to 5)
			sign(c, x - 1, bottom + 1, CZ() + dz, Direction.WEST, DyeColor.ORANGE, "", "#" + (lamp + 1), "", "");
			lamp++;
		}
		sign(c, x - 1, bottom + 3, CZ(), Direction.WEST, DyeColor.ORANGE, "", "Atencion", "6 a 10", "");
	}

	/** Number of decision podiums in the control room (anchors {@code decision_podium_0..4}). */
	private static final int PODIUMS = 5;

	/**
	 * The decision podiums: a row of {@link #PODIUMS} along the south wall under the threads screen, fronts to the north
	 * (to the player who comes from the consoles), at {@code dx = -3..1}. Podium {@code i} is bound {@code board:<i>} (anchor
	 * {@link AnchorNames#podium}): the i-th most urgent thread that needs the player, lit and with a bell and a {@code !} when
	 * it waits; a click opens it to answer the pending question or permission. So the office is where the player decides, and the
	 * lamps of {@link #attentionBoard} keep ranks 6 to 10 on the wall. A lectern in the plan: the blocks module swaps each one for
	 * {@code t3craft:decision_podium} (see {@code HqBlockFixtures}), without the module it stays a vanilla lectern. Each stands
	 * with at least three free blocks in front of it (the rug and the aisle behind the consoles).
	 */
	private static void podiums(CitadelCanvas c, int y) {
		for (int i = 0; i < PODIUMS; i++) {
			BlockPos p = new BlockPos(CX() - 3 + i, y, CZ() + 4);
			c.fixture(p, facing(Blocks.LECTERN, Direction.NORTH));
			c.anchor(Anchor.face(AnchorNames.podium(i), p, Direction.NORTH));
		}
	}

	/** {@code project:<key>} of the {@code n} projects with most desks taken (ties: the lowest module); {@code ""} for the rest. */
	private static List<String> projectBindings(CitadelCanvas c, int n) {
		List<HqLayout.Module> mods = new ArrayList<>();
		if (c.layout != null) {
			for (HqLayout.Module mod : c.layout.modules()) {
				if (!mod.desks().isEmpty()) {
					mods.add(mod);
				}
			}
		}
		mods.sort(Comparator.comparingInt((HqLayout.Module mod) -> -mod.desks().size()).thenComparingInt(HqLayout.Module::index));
		List<String> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (HqLayout.Module mod : mods) {
			if (out.size() < n && seen.add(mod.projectKey())) {
				out.add("project:" + mod.projectKey());
			}
		}
		while (out.size() < n) {
			out.add("");
		}
		return out;
	}

	// ------------------------------------------------------------------ balcony

	/**
	 * The balcony on the north side of the control room: a deck three deep over the promenade, on corbels, with a fascia beam,
	 * a fence, corner posts and three standing lanterns.
	 */
	public static void balcony(CitadelCanvas c) {
		int f = F2();
		for (int dx = -W; dx <= W; dx++) {
			for (int dz = -8; dz <= -E; dz++) {
				boolean outer = dz == -8;
				boolean side = Math.abs(dx) == W;
				boolean corner = outer && side;
				set(c, dx, f, dz, outer ? c.kit.beam(Direction.Axis.X) : side ? c.kit.beam(Direction.Axis.Z) : c.floor());
				// corbels under the deck
				if (dz == -E) {
					set(c, dx, f - 1, dz, PLANKS);
				} else if (dz == -7) {
					set(c, dx, f - 1, dz, cherryStairs(Direction.SOUTH, true));
				} else if (!side) {
					set(c, dx, f - 1, dz, topSlab(Blocks.CHERRY_SLAB));
				}
				if (corner) {
					for (int y = f + 1; y <= f + 3; y++) {
						set(c, dx, y, dz, LOG);
					}
					set(c, dx, f + 4, dz, lantern(false));
				} else if (outer || side) {
					set(c, dx, f + 1, dz, fence());
					if (outer && Math.floorMod(dx, 5) == 0) {
						set(c, dx, f + 2, dz, fence());
						set(c, dx, f + 3, dz, lantern(false));
					}
				}
			}
		}
		set(c, -4, f + 1, -7, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		set(c, 4, f + 1, -7, Blocks.POTTED_BAMBOO.defaultBlockState());
	}

	// ------------------------------------------------------------------ roof

	/**
	 * The hip roof: cherry stairs for the eave ({@code m = 6}), deepslate-tile stairs for the five rings above it up to
	 * {@code m = 2}, solid under them; a small glazed cupola over {@code m <= 1} with its own cap and a finial (a fence post
	 * with a lantern). The clerestory band under the roof is lit shoji.
	 */
	public static void roof(CitadelCanvas c) {
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -E; dz <= E; dz++) {
				int m = m(dx, dz);
				Direction in = inward(dx, dz);
				if (m == E) {
					set(c, dx, CEIL() + 2, dz, stairs(Blocks.CHERRY_STAIRS, in));
				} else if (m >= 2) {
					int ys = CEIL() + 2 + (E - m);
					// one cherry ring in the tiles breaks the big slope
					set(c, dx, ys, dz, stairs(m == 4 ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, in));
					for (int y = WALL_TOP() + 1; y < ys; y++) {
						set(c, dx, y, dz, FOOT);
					}
				} else {
					// cupola: floor, glazed walls (m = 1), cap with its own eave
					for (int y = WALL_TOP() + 1; y <= CEIL() + 6; y++) {
						set(c, dx, y, dz, FOOT);
					}
					if (m == 1) {
						boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
						for (int y = CEIL() + 7; y <= CEIL() + 8; y++) {
							set(c, dx, y, dz, corner ? LOG : Blocks.GLASS_PANE.defaultBlockState());
						}
						set(c, dx, CEIL() + 9, dz, FOOT);
						set(c, dx, CEIL() + 10, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
					} else {
						set(c, dx, CEIL() + 9, dz, FOOT);
						set(c, dx, CEIL() + 10, dz, FOOT);
						set(c, dx, CEIL() + 11, dz, fence());
						set(c, dx, CEIL() + 12, dz, lantern(false));
						hang(c, dx, CEIL() + 8, dz);
					}
				}
			}
		}
		// finials on the four eave corners
		for (int sx : new int[] {-E, E}) {
			for (int sz : new int[] {-E, E}) {
				set(c, sx, CEIL() + 3, sz, fence());
				set(c, sx, CEIL() + 4, sz, lantern(false));
			}
		}
		// the cupola's eave
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (m(dx, dz) == 2) {
					set(c, dx, CEIL() + 9, dz, stairs(Blocks.CHERRY_STAIRS, inward(dx, dz)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ anchors

	/** {@code office}: in front of the consoles, facing the threads screen. {@code office_balcony}: the balcony, facing the hub. */
	public static void anchors(CitadelCanvas c) {
		c.anchor(Anchor.at(OFFICE, new BlockPos(CX(), F2() + 1, CZ()), Direction.SOUTH));
		c.anchor(Anchor.at(OFFICE_BALCONY, new BlockPos(CX(), F2() + 1, CZ() - 7), Direction.NORTH));
	}
}
