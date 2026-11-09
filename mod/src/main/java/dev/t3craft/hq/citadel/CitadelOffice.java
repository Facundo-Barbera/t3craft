package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.citadel.nature.Flora;
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
 *   m = max(|dx|, |dz|)   m <= 4 interior 9 x 9     m = 5 wall (lobby, archive)     m = 6 skirt, lobby pilasters, cabin glass
 *                         m = 7 the roof's eave
 *
 *   level    floor y   feet y   what
 *   lobby    y0()        +1     rug from the door, 2 double chests and barrels, 7 workbenches (crafting, smithing, fletching,
 *                               stonecutter, grindstone, loom, blast furnace), anvil, ender chest, stair A (south-west L)
 *   archive  y0() + 6    +1     archive lecterns and shelves, cartography and enchanting tables, jukebox, model of the HQ,
 *                               bed, stair B (east); wall set back (m = 5) under the cabin
 *   control  y0() + 12   +1     the cabin, one block out (m = 6): tinted glass all round over a sill of upside-down stairs;
 *                               big screens, attention board, consoles, podiums, chests; glass door to the balcony (north)
 *   ceiling  y0() + 20   -      rafters; clerestory band, hip roof with turned-up corners, glass drum with a lantern
 * </pre>
 *
 * <h2>Exterior (struct/leader-office2)</h2>
 *
 * The control cabin flies one block past the archive's wall on dark tile corbels, with a running band of tinted glass (no wall of
 * its own behind the screens: see {@link #cabin}); the lobby keeps the three pilasters per face; the roof's eave is a block wider
 * than the glass and its four corners turn up with a finial; the cupola is a 5 x 5 glass drum, three high; the lawn on the
 * south-east is a two-tier garden with a path and an arbor ({@link #garden}).
 *
 * <h2>Stairs you walk up</h2>
 *
 * Flight A is an L in the lobby's south-west corner (leg along the west wall, a 2 x 2 landing, leg along the south wall); flight B
 * is a straight run of six steps on the east wall. Both are two blocks wide, one step per cell and one block per step (a stair
 * block is a half-block climb, then another half block: no jump). Each is entered <b>head-on</b> from a free landing, has two free
 * blocks above every step (the floor above is opened over the steps whose roof is lower than that, and fenced) and ends flush with
 * the floor above. See {@link #stairways}.
 *
 * <h2>Control room</h2>
 *
 * <ul>
 * <li><b>Threads</b>: a 7 x 4 big screen (south wall, key {@code office}) bound to {@code HqGenerator.hubBinding}, the
 *     environment with most modules, as on the hub; three console terminals (kit role {@code CONSOLE}, lecterns without
 *     the blocks module) face it, the standing cell in front of
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
 * <li><b>Glow light</b> (kit/glow-panel, {@link #glow}): cool strips under the screens, three cool ceiling panels over the
 *     screen's side and a warm strip along each flight, one per step. They replace the invisible lights of the screen's side.</li>
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
 * five board lamps ({@code board_lamp_5..9}), five decision podiums ({@code decision_podium_0..4}, bound {@code board:0..4},
 * under the threads screen: a click opens the thread that needs the player) and the merge station
 * ({@code mergestation_office=<binding>}, between the two project screens, bound to the first one's project). Fixtures
 * (never replaced by the light pass): the screens and their bases, the board, its lamps, the podiums, the merge station, the
 * signs. The consoles come from {@code HqKit.Role.CONSOLE}, the station from {@code HqKit.Role.MERGE_STATION}; the podium
 * anchors use the kernel's {@code AnchorNames.podium} (kit/decision-podium).
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
	/** Half width of the roof's eave (one block past the cabin's glass). */
	private static final int R = 7;
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
		cabin(c);
		stairways(c);
		lobby(c);
		archive(c);
		control(c);
		balcony(c);
		roof(c);
		anchors(c);
		garden(c);
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
		// the eave's ring (m = 7) and the balcony's airspace above the promenade (the deck below stays as it is)
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				if (m(dx, dz) == R) {
					for (int y = F2(); y <= CEIL() + 16; y++) {
						set(c, dx, y, dz, AIR);
					}
				}
			}
		}
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -10; dz <= -E - 1; dz++) {
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
				// the control room's floor is dark tile so the screens stand out, with an inlay: see controlFloor
				set(c, dx, F2(), dz, m == H ? c.kit.beam(edge) : controlFloor(dx, dz));
				set(c, dx, CEIL(), dz, m == H || Math.floorMod(dz, 2) == 0 ? c.kit.beam(m == H ? edge : Direction.Axis.X) : PLANKS);
			}
		}
	}

	/**
	 * The control room's floor: dark tiles and deepslate bricks in a fixed checker, a stripe of polished blackstone with pink
	 * terracotta corners one cell inside the beams (so the room has a frame), and the aisle's cross (see {@link #control}).
	 */
	private static BlockState controlFloor(int dx, int dz) {
		if (m(dx, dz) == 3) {
			return Math.abs(dx) == 3 && Math.abs(dz) == 3 ? CitadelCanvas.terracotta(DyeColor.PINK) : CitadelCanvas.RIB;
		}
		return Math.floorMod(dx + dz, 2) == 0 ? FOOT : CitadelCanvas.b(Blocks.DEEPSLATE_BRICKS);
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
			if (y > F2() && y != CEIL()) {
				continue; // the control cabin has no wall at m = 5: its glass is at m = 6 (see cabin)
			}
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y == F1() || y == F2() || y == CEIL()) {
				s = c.kit.beam(axis);
			} else if (y < F1()) {
				s = lobbyWall(c, y, au, north, axis);
			} else {
				s = archiveWall(c, y, au, south);
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
	 * One wall cell of the cabin (m = 6) at height {@code y}: the door to the balcony on the north, a sill of upside-down cherry
	 * stairs under a running band of tinted glass everywhere else.
	 */
	private static BlockState cabinWall(int y, int dx, int dz, boolean north, int au) {
		boolean door = north && au <= 1;
		if (door && y <= F2() + 3) {
			return AIR;
		}
		if (y == F2() + 1) {
			return cherryStairs(inward(dx, dz), true);
		}
		return CitadelCanvas.b(Blocks.TINTED_GLASS);
	}

	/**
	 * The control cabin (struct/leader-office2): the top level flies one block past the archive's wall. Its fascia (a beam
	 * ring at the control floor, row {@code F2()}) sits at m = 6 on a ring of dark tile corbels, with the archive's wall
	 * (m = 5) set back under it; the cabin's own wall is a running band of tinted glass (rows {@code F2() + 2 .. CEIL() - 1})
	 * over a sill of upside-down cherry stairs, between four cherry corner posts, under a beam at the ceiling and a lit
	 * clerestory. The old inner wall (m = 5) is gone above the floor, so the cabin is 11 x 11 inside: the screens, the board
	 * and the consoles stand where they stood, with a one-block walkway behind them (floor: the beam ring), and the door to
	 * the balcony is now in the glass.
	 */
	public static void cabin(CitadelCanvas c) {
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -E; dz <= E; dz++) {
				if (m(dx, dz) != E) {
					continue;
				}
				boolean corner = Math.abs(dx) == E && Math.abs(dz) == E;
				boolean north = dz == -E && !corner;
				boolean ns = Math.abs(dz) == E && !corner;
				int au = Math.abs(ns ? dx : dz);
				Direction.Axis axis = Math.abs(dz) == E ? Direction.Axis.X : Direction.Axis.Z;
				// fascia and the dark corbels under it
				set(c, dx, F2(), dz, corner ? LOG : c.kit.beam(axis));
				set(c, dx, F2() - 1, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, inward(dx, dz)).setValue(StairBlock.HALF, Half.TOP));
				for (int y = F2() + 1; y <= WALL_TOP(); y++) {
					BlockState s;
					if (corner) {
						s = LOG;
					} else if (y == CEIL()) {
						s = c.kit.beam(axis);
					} else if (y < CEIL()) {
						s = cabinWall(y, dx, dz, north, au);
					} else {
						s = au == 2 ? LOG : c.kit.wallPanel(true);
					}
					set(c, dx, y, dz, s);
				}
			}
		}
	}

	/**
	 * Outside the walls: a cornice of upside-down stairs over the lobby (the archive's and the cabin's sit on their own
	 * fascia), three pilasters on the east, south and west faces of the lobby only (the archive's wall stays set back under the
	 * flying cabin), sills under the archive windows.
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
					// pilaster: base, log up to the lobby's cornice
					set(c, dx, F0() + 1, dz, FOOT);
					for (int y = F0() + 2; y < F1(); y++) {
						set(c, dx, y, dz, LOG);
					}
					set(c, dx, F1(), dz, cherryStairs(inward(dx, dz), true));
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
				set(c, dx, F1(), dz, corner ? topSlab(Blocks.CHERRY_SLAB) : cherryStairs(inward(dx, dz), true));
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
	 * <li><b>A</b>, lobby to archive, an L in the south-west corner (struct/leader-office2): leg 1 runs south along the west
	 *     wall ({@code dx = -4, -3}, steps at {@code dz = 1, 2}, {@code y = F0() + 1, + 2}), a 2 x 2 landing at the corner
	 *     ({@code dz = 3, 4}, top at {@code F0() + 3}), then leg 2 runs east along the south wall (steps at {@code dx = -2 .. 1},
	 *     {@code dz = 3, 4}, {@code y = F0() + 3 .. F0() + 6}, the last in the floor row). Entered head-on from
	 *     {@code (-4..-3, 0)}. The archive's floor is open over {@code dx = -2 .. 0}, fenced.</li>
	 * <li><b>B</b>, archive to control room, east columns {@code dx = 3, 4}: step {@code i} at {@code dz = 3 - i}
	 *     (rising north), {@code y = F1() + 1 + i}. Landing {@code (3..4, 4)}. Well over {@code dz = 1..-1}.</li>
	 * </ul>
	 *
	 * Every step has two free blocks above its walking surface (the well over the steps whose roof is lower than that), the
	 * landing has three, and the mass under each step is plank.
	 */
	public static void stairways(CitadelCanvas c) {
		for (int k = 0; k < 2; k++) {
			// A, leg 1: along the west wall, rising south
			for (int i = 0; i < 2; i++) {
				int x = -4 + k;
				int z = 1 + i;
				set(c, x, F0() + 1 + i, z, cherryStairs(Direction.SOUTH, false));
				for (int y = F0() + 1; y < F0() + 1 + i; y++) {
					set(c, x, y, z, PLANKS);
				}
			}
			// A, the landing in the corner: its top is the back of the last step of leg 1
			for (int z = 3; z <= 4; z++) {
				for (int y = F0() + 1; y <= F0() + 2; y++) {
					set(c, -4 + k, y, z, PLANKS);
				}
			}
			// A, leg 2: along the south wall, rising east, the last step in the floor row
			for (int j = 0; j < 4; j++) {
				int x = -2 + j;
				int z = 3 + k;
				set(c, x, F0() + 3 + j, z, cherryStairs(Direction.EAST, false));
				for (int y = F0() + 1; y < F0() + 3 + j; y++) {
					set(c, x, y, z, PLANKS);
				}
			}
			// A, the well: the headroom of steps 3 to 5 goes through the floor above
			for (int x = -2; x <= 0; x++) {
				set(c, x, F1(), 3 + k, AIR);
			}
			for (int i = 0; i < FH; i++) {
				// B: archive to control room, rising north
				int xb = 3 + k;
				int zb = 3 - i;
				set(c, xb, F1() + 1 + i, zb, cherryStairs(Direction.NORTH, false));
				for (int y = F1() + 1; y < F1() + 1 + i; y++) {
					set(c, xb, y, zb, PLANKS);
				}
			}
			// B, the well
			for (int dz = -1; dz <= 1; dz++) {
				set(c, 3 + k, F2(), dz, AIR);
			}
		}
	}

	// ------------------------------------------------------------------ lobby (feet y = F0() + 1)

	/**
	 * Lobby and workshop: a rug from the door, double chests and barrels on the east wall, seven workbenches (four on the west
	 * wall, one beside the door, two on the south wall next to flight A), an anvil and an ender chest, banners and a plaque. The
	 * middle of the room stays free from the door to the south wall, and flight A's corner (south-west) keeps the door's side clear.
	 */
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
		// workbenches, west wall (the player stands east of them; the cells in front of flight A's first step, dz = 0, stay free)
		set(c, -4, y, -4, Blocks.CRAFTING_TABLE.defaultBlockState());
		set(c, -4, y, -3, Blocks.SMITHING_TABLE.defaultBlockState());
		set(c, -4, y, -2, Blocks.FLETCHING_TABLE.defaultBlockState());
		set(c, -4, y, -1, facing(Blocks.STONECUTTER, Direction.EAST));
		// south wall, east of flight A
		set(c, 2, y, 4, Blocks.GRINDSTONE.defaultBlockState());
		set(c, 3, y, 4, facing(Blocks.LOOM, Direction.NORTH));
		// beside the door: the blast furnace, an anvil and an ender chest (storage that follows the player)
		set(c, -3, y, -4, facing(Blocks.BLAST_FURNACE, Direction.SOUTH));
		set(c, -2, y, -4, facing(Blocks.ANVIL, Direction.EAST));
		set(c, 2, y, -4, facing(Blocks.ENDER_CHEST, Direction.SOUTH));
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

	/**
	 * Archive and cartography: shelves and lecterns under the west windows, the map table and the model of the HQ, a bed
	 * nook, a reading corner, and flight A's well (south-west, fenced) with its exit on the south wall.
	 */
	public static void archive(CitadelCanvas c) {
		int y = F1() + 1;
		// the well of flight A (open over dx = -2..0, dz = 3..4), fenced on the floor cells beside it
		for (int dz = 3; dz <= 4; dz++) {
			set(c, -3, y, dz, fence());
		}
		for (int dx = -2; dx <= 0; dx++) {
			set(c, dx, y, 2, fence());
		}
		// bed nook, north-west: head against the north wall
		set(c, -4, y, -4, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		set(c, -4, y, -3, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(c, -3, y, -4, chest(Direction.SOUTH, ChestType.SINGLE));
		set(c, -3, y, -3, Blocks.JUKEBOX.defaultBlockState());
		// west wall: tall shelves at the ends, lecterns (stand-ins for the archive terminal, see the class comment) and a low
		// shelf under the window
		shelves(c, -4, y, -2, 3);
		set(c, -4, y, -1, facing(Blocks.LECTERN, Direction.EAST));
		set(c, -4, y, 0, Blocks.BOOKSHELF.defaultBlockState());
		set(c, -4, y, 1, facing(Blocks.LECTERN, Direction.EAST));
		shelves(c, -4, y, 2, 3);
		// south-west corner, over flight A's landing: shelves
		shelves(c, -4, y, 3, 3);
		shelves(c, -4, y, 4, 3);
		set(c, 1, y + 2, 4, wallBanner(Direction.NORTH));
		sign(c, CX() + 2, y + 2, CZ() + 4, Direction.NORTH, DyeColor.ORANGE, "", "Archivo", "", "");
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
		for (int dx = 1; dx <= 2; dx++) {
			set(c, dx, y, 2, carpet(RUG_EDGE));
		}
		for (int dx = -1; dx <= 2; dx++) {
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
		// rug in front of the consoles, centred on the room's axis (the door's, the screen's, the middle console's): five wide and four
		// deep with a pink border, the player stands on it facing south. The cell of the well's fence is left out (dx = 2, dz = -1).
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -4; dz <= -1; dz++) {
				if (dx == 2 && dz == -1) {
					continue;
				}
				boolean edge = Math.abs(dx) == 2 || dz == -4 || dz == -1;
				set(c, dx, y, dz, carpet(edge ? RUG_EDGE : RUG));
			}
		}
		// the aisle's cross in the floor, on the axis, in front of the middle podium: pink terracotta round a chiseled centre
		set(c, 0, F2(), 2, CitadelCanvas.b(Blocks.CHISELED_POLISHED_BLACKSTONE));
		for (int[] p : new int[][] {{-1, 2}, {1, 2}, {0, 1}, {0, 3}}) {
			set(c, p[0], F2(), p[1], CitadelCanvas.terracotta(DyeColor.PINK));
		}
		// the consoles: a bank of three console terminals (kit role CONSOLE; a lectern with the blocks module off), side by side
		// at dz = 0, centred on the axis, so their screens read as one command desk, facing the player, who stands on the rug to the
		// north. That leaves a 3-wide aisle (dz = 1..3) between the bank and the podiums, and dx = -3 free so the merge station can be
		// reached. A slab with a lantern closes the bank on its west end; the well's fence closes it on the east.
		for (int dx = -1; dx <= 1; dx++) {
			set(c, dx, y, 0, c.kit.console(Direction.NORTH));
		}
		set(c, -2, y, 0, topSlab(Blocks.CHERRY_SLAB));
		set(c, -2, y + 1, 0, lantern(false));
		// under the main screen: the decision podiums (five, every other cell from west to east, so each one has a free cell beside
		// it and its own display reads). Between them no plants (a dead podium looked like a flower pot): a cherry pilaster, with a
		// dark capital in the screen's base, so the plinth reads as bays
		podiums(c, y);
		for (int dx : new int[] {-3, -1, 1, 3}) {
			set(c, dx, y, 4, c.kit.beam(Direction.Axis.Y));
		}
		// threads screen: 7 x 4 on the south wall, bound to the hub's feed
		HqGeometry.Surface main = new HqGeometry.Surface(new BlockPos(CX() + 3, F2() + 3, CZ() + 4), Direction.WEST, Direction.NORTH, 7, 4);
		CitadelHub.bigScreen(c, main, OFFICE, HqGenerator.hubBinding(c.layout), true);
		for (int dx : new int[] {-3, -1, 1, 3}) {
			c.fixture(new BlockPos(CX() + dx, F2() + 2, CZ() + 4), CitadelCanvas.RIB);
		}
		sign(c, CX(), F2() + 7, CZ() + 4, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Hilos", "");
		// project screens: two 3 x 3 on the west wall, chests under them
		List<String> projects = projectBindings(c, 2);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - 4, F2() + 3, CZ() + 3), Direction.NORTH, Direction.EAST, 3, 3),
				"office_w0", projects.get(0), false);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - 4, F2() + 3, CZ() - 1), Direction.NORTH, Direction.EAST, 3, 3),
				"office_w1", projects.get(1), false);
		for (int dz = -3; dz <= 3; dz++) {
			// a free cell either side of the merge station (dz = 0), so it is not boxed in
			if (Math.abs(dz) >= 2) {
				set(c, -4, y, dz, chest(Direction.EAST, ChestType.SINGLE));
			}
		}
		set(c, -4, F2() + 3, 0, wallBanner(Direction.EAST));
		set(c, -4, F2() + 4, 0, wallBanner(Direction.EAST));
		// a map table in the north-west corner, for planning on the HQ's map
		set(c, -4, y, -4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		// merge station (kit role MERGE_STATION; a smithing table without the blocks module) in the gap between the two project
		// screens, facing the room: its list opens the pending PRs of the first screen's project
		BlockPos station = new BlockPos(CX() - 4, y, CZ());
		c.fixture(station, c.kit.mergeStation(Direction.EAST));
		c.anchor(Anchor.face(AnchorNames.mergeStation("office", projects.get(0)), station, Direction.EAST));
		attentionBoard(c);
		backing(c);
		// light: lanterns only over the middle and the north half; the screen's side gets invisible light, so nothing hangs in front of it
		for (int[] p : new int[][] {{-3, -3}, {0, -3}, {3, -3}, {-3, 0}, {0, 0}}) {
			hang(c, p[0], CEIL() - 1, p[1]);
		}
		glow(c);
	}

	/**
	 * The equipment walls behind the screens and the board (the cabin has no inner wall any more): dark deepslate at m = 5, full
	 * height, as wide as what hangs on them (nine behind the threads screen and the podiums, seven behind the board and the project
	 * screens), so nothing floats; the corners of the walkway stay open and glazed, and reachable from the room's corners.
	 */
	private static void backing(CitadelCanvas c) {
		for (int y = F2() + 1; y < CEIL(); y++) {
			for (int i = -4; i <= 4; i++) {
				set(c, i, y, W, backingBlock(i, y));
			}
			for (int i = -3; i <= 3; i++) {
				set(c, W, y, i, backingBlock(i + 11, y));
				set(c, -W, y, i, backingBlock(i - 11, y));
			}
		}
	}

	private static BlockState backingBlock(int i, int y) {
		int h = Math.floorMod(i * 5 + y * 3, 7);
		return h == 0 ? CitadelCanvas.RIB : h < 3 ? CitadelCanvas.b(Blocks.DEEPSLATE_BRICKS) : FOOT;
	}

	/**
	 * kit/glow-panel: integrated light for the control room and the two flights, with the kit's glow blocks (vanilla
	 * fallback: the invisible light block, or a froglight in the panels). It replaces the three invisible lights that
	 * used to light the screen's side and hangs nothing in front of the screen:
	 * <ul>
	 * <li>three cool ceiling panels over the screen's side ({@code dx = -3, 0, 3}), on the room's axis;</li>
	 * <li>cool strips under the screens, hugging their wall: in front of the main screen's base (7 cells), and under the
	 *     two project screens and the banner between them (7 cells), at the top of the cell that is just below the
	 *     screen, so the light falls on the console, the barrels and the chests;</li>
	 * <li>warm strips along the wall side of each flight, one per step in the cell above it, so the climb is lit step by
	 *     step (the last step of each flight is left alone: the next floor's furniture stands there).</li>
	 * </ul>
	 * Everything is placed only into air, and no strip blocks anybody (no collision).
	 */
	private static void glow(CitadelCanvas c) {
		int y = F2() + 1;
		for (int dx : new int[] {-3, 0, 3}) {
			if (solid(c, dx, CEIL(), 3)) {
				set(c, dx, CEIL(), 3, c.kit.glowPanel(Direction.DOWN, true));
			}
		}
		// under the main screen: its base beam is on the wall row (dz = 4), the strip hugs its front, at the top of the cell
		for (int dx = -3; dx <= 3; dx++) {
			glowStrip(c, dx, y + 1, 3, c.kit.glowStrip(Direction.NORTH, true, true));
		}
		// under the project screens (west wall): the wall row's cell below the screens holds their base beam, so the strip hugs its
		// front, one cell into the room (dx = -3), like the main screen's
		for (int dz = -3; dz <= 3; dz++) {
			glowStrip(c, -3, y + 1, dz, c.kit.glowStrip(Direction.EAST, true, true));
		}
		// the flights, along the wall: A rises south on the west wall (dx = -4), B rises north on the east wall (dx = 4)
		for (int i = 0; i < FH - 1; i++) {
			glowStrip(c, 4, F1() + 2 + i, 3 - i, c.kit.glowStrip(Direction.WEST, false, false));
		}
		// A is an L now: leg 1 rises south on the west wall (dx = -4), leg 2 rises east on the south wall (dz = 4)
		for (int i = 0; i < 2; i++) {
			glowStrip(c, -4, F0() + 2 + i, 1 + i, c.kit.glowStrip(Direction.EAST, false, false));
			glowStrip(c, -2 + i, F0() + 4 + i, 4, c.kit.glowStrip(Direction.NORTH, false, false));
		}
	}

	/** The cell at local {@code (dx, dz)} holds a block (the ceiling to set a panel in). */
	private static boolean solid(CitadelCanvas c, int dx, int y, int dz) {
		BlockState s = c.at(CX() + dx, y, CZ() + dz);
		return s != null && !s.isAir();
	}

	/** Sets {@code s} at local {@code (dx, dz)} only if the cell is still air (a strip never replaces anything). */
	private static void glowStrip(CitadelCanvas c, int dx, int y, int dz, BlockState s) {
		BlockState now = c.at(CX() + dx, y, CZ() + dz);
		if (now == null || now.isAir()) {
			c.put(CX() + dx, y, CZ() + dz, s, CitadelCanvas.DECOR);
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
	 * (to the player who comes from the consoles), at {@code dx = -4, -2, 0, 2, 4}. Podium {@code i} is bound {@code board:<i>} (anchor
	 * {@link AnchorNames#podium}): the i-th most urgent thread that needs the player, lit and with a bell and a {@code !} when
	 * it waits; a click opens it to answer the pending question or permission. So the office is where the player decides, and the
	 * lamps of {@link #attentionBoard} keep ranks 6 to 10 on the wall. A lectern in the plan: the blocks module swaps each one for
	 * {@code t3craft:decision_podium} (see {@code HqBlockFixtures}), without the module it stays a vanilla lectern. Each stands
	 * with at least three free blocks in front of it (the rug and the aisle behind the consoles).
	 */
	private static void podiums(CitadelCanvas c, int y) {
		for (int i = 0; i < PODIUMS; i++) {
			BlockPos p = new BlockPos(CX() - 4 + 2 * i, y, CZ() + 4);
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
	 * The balcony on the north side of the control room: a deck three deep over the promenade, in front of the glass door, on
	 * corbels, with a fascia beam, a fence, corner posts and three standing lanterns.
	 */
	public static void balcony(CitadelCanvas c) {
		int f = F2();
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -9; dz <= -E - 1; dz++) {
				boolean outer = dz == -9;
				boolean side = Math.abs(dx) == E;
				boolean corner = outer && side;
				set(c, dx, f, dz, outer ? c.kit.beam(Direction.Axis.X) : side ? c.kit.beam(Direction.Axis.Z) : c.floor());
				// corbels under the deck
				if (dz == -E - 1) {
					set(c, dx, f - 1, dz, PLANKS);
				} else if (dz == -E - 2) {
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
		set(c, -4, f + 1, -8, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		set(c, 4, f + 1, -8, Blocks.POTTED_BAMBOO.defaultBlockState());
	}

	// ------------------------------------------------------------------ roof

	/**
	 * The hip roof: an eave at m = 7 (cherry stairs) whose four corners turn up (a cherry plank, a stair, a finial of a fence post
	 * and a lantern, with a hanging lantern under it, and a slab step on the two cells beside each corner), cherry and
	 * deepslate-tile stairs for the five rings above it up to {@code m = 3}, solid under them; over {@code m <= 2} a glass drum
	 * (5 x 5, three high, lit from inside) with a stepped cap of its own, an eave and a finial. The clerestory band under the roof
	 * (the cabin's wall at m = 6) is lit shoji.
	 */
	public static void roof(CitadelCanvas c) {
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				int m = m(dx, dz);
				Direction in = inward(dx, dz);
				boolean corner = Math.abs(dx) == R && Math.abs(dz) == R;
				if (m == R) {
					if (corner) {
						set(c, dx, CEIL() + 2, dz, PLANKS);
						set(c, dx, CEIL() + 3, dz, stairs(Blocks.CHERRY_STAIRS, in));
						set(c, dx, CEIL() + 4, dz, fence());
						set(c, dx, CEIL() + 5, dz, lantern(false));
						hang(c, dx, CEIL() + 1, dz);
					} else {
						set(c, dx, CEIL() + 2, dz, stairs(Blocks.CHERRY_STAIRS, in));
						// the two cells beside a corner rise half a block: the eave turns up
						int along = Math.abs(dx) == R ? Math.abs(dz) : Math.abs(dx);
						if (along == R - 1) {
							set(c, dx, CEIL() + 3, dz, slab(Blocks.CHERRY_SLAB));
						}
					}
				} else if (m >= 3) {
					int ys = CEIL() + 2 + (R - m);
					// one cherry ring in the tiles breaks the big slope
					set(c, dx, ys, dz, stairs(m == 5 ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, in));
					for (int y = WALL_TOP() + 1; y < ys; y++) {
						set(c, dx, y, dz, FOOT);
					}
				} else {
					// the drum: floor, glass ring (m = 2), cap, stepped top
					for (int y = WALL_TOP() + 1; y <= CEIL() + 6; y++) {
						set(c, dx, y, dz, FOOT);
					}
					if (m == 2) {
						boolean post = Math.abs(dx) == 2 && Math.abs(dz) == 2;
						for (int y = CEIL() + 7; y <= CEIL() + 9; y++) {
							set(c, dx, y, dz, post ? LOG : CitadelCanvas.GLASS);
						}
					}
					set(c, dx, CEIL() + 10, dz, FOOT);
					if (m == 2) {
						set(c, dx, CEIL() + 11, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
					} else {
						set(c, dx, CEIL() + 11, dz, FOOT);
						if (m == 1) {
							set(c, dx, CEIL() + 12, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
						} else {
							set(c, dx, CEIL() + 12, dz, FOOT);
							set(c, dx, CEIL() + 13, dz, fence());
							set(c, dx, CEIL() + 14, dz, lantern(false));
							// inside the drum: a standing lantern and a hanging one
							set(c, dx, CEIL() + 7, dz, lantern(false));
							hang(c, dx, CEIL() + 9, dz);
						}
					}
				}
			}
		}
		// the drum's eave
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (m(dx, dz) == 3) {
					set(c, dx, CEIL() + 10, dz, stairs(Blocks.CHERRY_STAIRS, inward(dx, dz)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ anchors

	/** {@code office}: in front of the consoles, facing the threads screen. {@code office_balcony}: the balcony, facing the hub. */
	public static void anchors(CitadelCanvas c) {
		c.anchor(Anchor.at(OFFICE, new BlockPos(CX() - 1, F2() + 1, CZ() - 1), Direction.SOUTH));
		c.anchor(Anchor.at(OFFICE_BALCONY, new BlockPos(CX(), F2() + 1, CZ() - 7), Direction.NORTH));
	}

	// ------------------------------------------------------------------ garden on the south-east slope

	/** Top block (y, flat frame) of the upper and the lower garden terrace over the lawn (whose top block is y = 0). */
	private static final int T1 = 4;
	private static final int T2 = 2;

	/**
	 * Cells the garden is out from the tower's skirt on its east and south sides ({@code 0}: not part of the garden). East of
	 * the tower the garden spans {@code dz = -6..9}, south of it {@code dx = -6..9}; round the corner the rings are square.
	 */
	private static int outward(int dx, int dz) {
		if (dx > E && dz > E) {
			return Math.max(dx, dz) - E;
		}
		if (dx > E && dz >= -6 && dz <= 9) {
			return dx - E;
		}
		if (dz > E && dx >= -6 && dx <= 9) {
			return dz - E;
		}
		return 0;
	}

	/** The lawn of the terrace (or its paved walk) at the flat ground, free to be built on. */
	private static boolean lawn(CitadelCanvas c, int x, int z) {
		if (c.zoneAt(x, z).kind() != RingCampusGeometry.Kind.TERRACE) {
			return false;
		}
		BlockState g = c.at(x, 0, z);
		return g != null && g != CitadelCanvas.LIGHT && (Flora.soil(g) || g.equals(c.floor()) || g.equals(c.accent()));
	}

	/** The garden's top block at local {@code (dx, dz)}: {@link #T1}, {@link #T2} or {@code 0} (no garden here). */
	private static int tier(CitadelCanvas c, int dx, int dz) {
		int o = outward(dx, dz);
		if (o <= 0 || o > 9 || !lawn(c, CX() + dx, CZ() + dz)) {
			return 0;
		}
		return o <= 5 ? T1 : T2;
	}

	/** The garden at {@code (dx, dz)} is lower than {@code t} (the tower itself is not). */
	private static boolean lower(CitadelCanvas c, int dx, int dz, int t) {
		return m(dx, dz) > E && tier(c, dx, dz) < t;
	}

	private static BlockState gardenWall(int x, int y, int z) {
		int h = Flora.hash(x, z, 311 + y) % 20;
		return CitadelCanvas.b(h < 11 ? Blocks.POLISHED_DEEPSLATE : h < 16 ? Blocks.DEEPSLATE_BRICKS : h < 18 ? Blocks.CRACKED_DEEPSLATE_BRICKS
				: Blocks.DEEPSLATE_TILES);
	}

	private static BlockState gardenPath(int x, int z) {
		int h = Flora.hash(x, z, 77) % 6;
		return CitadelCanvas.b(h < 3 ? Blocks.POLISHED_ANDESITE : h < 5 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS);
	}

	/**
	 * struct/leader-office2: the south-east slope of the terrace, which was bare grass, becomes a terraced garden: two tiers
	 * (top blocks y = 4 and y = 2 over the lawn at y = 0) against the tower's skirt on its east and south sides, held by
	 * deepslate walls with a calcite coping, with flowers and azalea bushes on them. A stone path two wide climbs from the lawn
	 * by two short stairs per tier (two steps each, so every step is half a block) to an arbor on the upper tier (a pergola of
	 * cherry posts and beams over two benches facing each other, a cartography table and hanging lanterns), a place to sit and
	 * plan with the map. Only lawn columns of the terrace zone are touched, in the flat frame the relief lifts afterwards.
	 */
	public static void garden(CitadelCanvas c) {
		for (int dx = E + 1; dx <= 20; dx++) {
			for (int dz = -6; dz <= 15; dz++) {
				gardenColumn(c, dx, dz);
			}
		}
		for (int dx = -6; dx <= E; dx++) {
			for (int dz = E + 1; dz <= 15; dz++) {
				gardenColumn(c, dx, dz);
			}
		}
		arbor(c);
		// lamps beside the path on the lower tier
		for (int dz : new int[] {2, 5}) {
			if (tier(c, E + 7, dz) == T2) {
				for (int y = T2 + 1; y <= T2 + 2; y++) {
					set(c, E + 7, y, dz, fence());
				}
				set(c, E + 7, T2 + 3, dz, lantern(false));
			}
		}
	}

	private static void gardenColumn(CitadelCanvas c, int dx, int dz) {
		int o = outward(dx, dz);
		int x = CX() + dx;
		int z = CZ() + dz;
		boolean path = dx > E && (dz == 3 || dz == 4) && o > 0 && o <= 14 && dz <= 6;
		int t = tier(c, dx, dz);
		if (t == 0) {
			// the path goes on over the lawn past the lower tier: a stair at the foot, then flat stones
			if (path && o >= 10 && lawn(c, x, z)) {
				if (o == 10) {
					c.set(x, 1, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
				} else {
					c.set(x, 0, z, gardenPath(x, z));
					c.set(x, 1, z, AIR);
				}
			}
			return;
		}
		// a column is a retaining wall where a neighbour is lower (the tower's skirt is never lower)
		boolean edge = lower(c, dx - 1, dz, t) || lower(c, dx + 1, dz, t) || lower(c, dx, dz - 1, t) || lower(c, dx, dz + 1, t);
		for (int y = 1; y < t; y++) {
			c.set(x, y, z, edge ? gardenWall(x, y, z) : CitadelCanvas.b(Blocks.DIRT));
		}
		if (path && o <= 9) {
			boolean last = o == 5 || o == 9;
			if (last) {
				// the stair that climbs onto the tier: the column below it is wall, the stair stands on top of it
				c.set(x, t, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
				return;
			}
			c.set(x, t, z, gardenPath(x, z));
			if (o == 6) {
				// the upper stair's first step stands on the lower tier's path
				c.set(x, t + 1, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
			}
			return;
		}
		if (edge) {
			c.set(x, t, z, CALCITE);
			return;
		}
		c.set(x, t, z, Flora.turf(x, z));
		int h = Flora.hash(x, z, 41) % 100;
		boolean besidePath = dx > E && dz >= 2 && dz <= 5;
		if (h < 30) {
			Flora.plantOn(c, x, t, z, Flora.bloom(1 + Math.floorDiv(x + z, 5), h >> 1));
		} else if (h < 42 && !besidePath) {
			c.set(x, t + 1, z, Flora.bush(h));
		} else if (h < 60) {
			Flora.plantOn(c, x, t, z, CitadelCanvas.b(Blocks.SHORT_GRASS));
		}
	}

	/** A bed of the podium's middle tier (grass on its top block {@code y = 3}, free above) at the absolute cell. */
	private static boolean bed(CitadelCanvas c, int x, int z) {
		if (c.zoneAt(x, z).kind() != RingCampusGeometry.Kind.TERRACE) {
			return false;
		}
		BlockState g = c.at(x, 3, z);
		return g != null && Flora.soil(g);
	}

	/**
	 * The arbor, found on the grass field east of the tower (the podium's middle tier, top block y = 3): a 3 x 7 footprint,
	 * {@code dx0 .. dx0 + 2}, {@code dz0 .. dz0 + 6}, nearest the tower that is all bed. Six cherry posts three high, beams along its
	 * length, rafters across, two benches facing each other with a cartography table between them, a second pair, two hanging
	 * lanterns, stone paving under it. Nothing is built when the field has no such place.
	 */
	private static void arbor(CitadelCanvas c) {
		for (int len : new int[] {7, 4}) {
			for (int dx0 = E + 1; dx0 <= 16; dx0++) {
				for (int dz0 = -2; dz0 >= -16; dz0--) {
					boolean ok = true;
					for (int dx = dx0; dx <= dx0 + 2 && ok; dx++) {
						for (int dz = dz0; dz < dz0 + len && ok; dz++) {
							ok = bed(c, CX() + dx, CZ() + dz);
						}
					}
					if (ok) {
						arborAt(c, dx0, dz0, len, 3);
						return;
					}
				}
			}
		}
	}

	private static void arborAt(CitadelCanvas c, int dx0, int dz0, int len, int g) {
		int b = g + 1; // standing level
		int mid = dx0 + 1;
		int last = dz0 + len - 1;
		// the paving under it, clear above (and a margin of path round it where there is grass, so it reads as a place)
		for (int dx = dx0 - 1; dx <= dx0 + 3; dx++) {
			for (int dz = dz0 - 1; dz <= last + 1; dz++) {
				boolean inside = dx >= dx0 && dx <= dx0 + 2 && dz >= dz0 && dz <= last;
				if (inside || (Math.floorMod(dx + dz, 2) == 0 && bed(c, CX() + dx, CZ() + dz))) {
					set(c, dx, g, dz, gardenPath(CX() + dx, CZ() + dz));
					for (int y = b; y <= b + 4; y++) {
						set(c, dx, y, dz, AIR);
					}
				}
			}
		}
		for (int dx : new int[] {dx0, dx0 + 2}) {
			for (int dz : len == 7 ? new int[] {dz0, dz0 + 3, last} : new int[] {dz0, last}) {
				for (int y = b; y < b + 3; y++) {
					set(c, dx, y, dz, LOG);
				}
			}
			for (int dz = dz0; dz <= last; dz++) {
				set(c, dx, b + 3, dz, c.kit.beam(Direction.Axis.Z));
			}
		}
		for (int dz = dz0; dz <= last; dz += 2) {
			for (int dx = dx0; dx <= dx0 + 2; dx++) {
				set(c, dx, b + 4, dz, c.kit.beam(Direction.Axis.X));
			}
		}
		set(c, mid, b, dz0 + 1, CitadelCanvas.b(Blocks.CARTOGRAPHY_TABLE));
		hang(c, mid, b + 3, dz0 + 2);
		for (int dz : len == 7 ? new int[] {dz0 + 1, dz0 + 5} : new int[] {dz0 + 1}) {
			set(c, dx0, b, dz, cherryStairs(Direction.WEST, false));
			set(c, dx0 + 2, b, dz, cherryStairs(Direction.EAST, false));
		}
		if (len == 7) {
			hang(c, mid, b + 3, dz0 + 4);
		}
	}
}
