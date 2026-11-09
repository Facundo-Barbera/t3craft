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
 * <h2>Scale (struct/leader-office3)</h2>
 *
 * The tower was tight: its 9 x 9 rooms are now <b>13 x 13</b> inside (2.1 times the floor), 6 free blocks high (floor to floor 7),
 * the control room 8 high and 15 x 15 inside its cabin. Every route is at least three blocks wide, both flights are walking
 * stairs, and nothing is added to the door's side: the footprint grew by two blocks on every side, and the tower moved out
 * by the same two ({@link #RIM_OFFSET}) so its door is still on the deck's rim.
 *
 * <h2>Plan (local coordinates {@code (dx, dz)} from the tower's centre {@link #CX()}, {@link #CZ()}; north is -dz, the hub)</h2>
 *
 * <pre>
 *   m = max(|dx|, |dz|)   m <= 6 interior 13 x 13   m = 7 wall (lobby, archive), equipment walls (control)
 *                         m = 8 skirt, lobby pilasters, cabin glass     m = 9 the roof's eave
 *
 *   level    floor y   feet y   what
 *   lobby    y0()        +1     rug from the door, workbenches (crafting, smithing, fletching, stonecutter, grindstone, loom,
 *                               blast furnace, furnace, smoker), anvil, ender chest, 4 double chests, barrels, stair A (SW, an L)
 *   archive  y0() + 7    +1     archive lecterns and shelves, cartography and enchanting tables, jukebox, model of the HQ,
 *                               bed, stair B (east); wall set back (m = 7) under the cabin
 *   control  y0() + 14   +1     the cabin, one block out (m = 8): clear glass between cherry mullions every 3 blocks, over a sill of
 *                               upside-down stairs; big screens, attention board, consoles, podiums, chests; door to the balcony
 *   ceiling  y0() + 23   -      rafters; clerestory band, hip roof with a curved eave, glass drum with a lantern
 * </pre>
 *
 * <h2>Exterior</h2>
 *
 * The cabin flies one block past the archive's wall on dark tile corbels. Its glass is <i>clear</i> (tinted only on the transom row)
 * between cherry mullions at {@code |i| = 2, 5} and the corner posts, and the equipment walls behind the screens are pale plaster
 * with the same mullions as pilasters, so from every side, the south-east too, the top floor reads as a glazed lantern instead of a
 * black mass. The eave is a block wider than the glass and swoops up to its four corners in a stepped curve (see {@link #eave});
 * the cupola is a 7 x 7 glass drum, three high. The south-east lawn is a two-tier garden ({@link #garden}) with an open pergola
 * whose rafters carry cave vines and flowering azalea ({@link #arborAt}).
 *
 * <h2>Stairs you walk up</h2>
 *
 * Flight A is an L in the lobby's south-west corner (leg of three along the west wall, a 2 x 3 landing, leg of four along the south
 * wall); flight B is a straight run of seven steps on the east wall of the archive. Both are two blocks wide, one step per cell and
 * one block per step (a stair block is a half-block climb, then another half block: no jump). Each is entered <b>head-on</b> from a
 * free landing, has two free blocks above every step (the floor above is opened over the steps whose roof is lower than that, and
 * fenced) and ends flush with the floor above. See {@link #stairways}.
 *
 * <h2>Control room</h2>
 *
 * <ul>
 * <li><b>Threads</b>: an 11 x 5 big screen (south wall, key {@code office}) bound to {@code HqGenerator.hubBinding}, the
 *     environment with most modules, as on the hub; a bank of five console terminals (kit role {@code CONSOLE}, lecterns without
 *     the blocks module) faces it from the rug; the standing cell in front of them is the {@link #OFFICE} anchor.</li>
 * <li><b>Projects</b>: two 5 x 5 screens on the west wall (keys {@code office_w0}, {@code office_w1}), each bound to
 *     {@code project:<key>} of the two projects with most desks taken ({@link #projectBindings}); unbound while the
 *     HQ has fewer. Chests under them.</li>
 * <li><b>Decision podiums</b>: five along the south wall under the threads screen, bound {@code board:0..4}. See {@link #podiums}.</li>
 * <li><b>Status screen</b>: on the east wall, a 9 x 3 big screen (key {@link #STATUS_SCREEN}) bound {@code beacon}: threads by
 *     state, PRs to review, active projects and the oldest thread waiting for the player, in big numbers (client
 *     {@code StatusScreen}). Under it, a strip with five status lamps anchored {@code board_lamp_5..9}, so they show the
 *     6th to 10th most urgent thread: the hub board keeps ranks 1 to 5. They are plain additive anchors.</li>
 * <li><b>Crown</b>: every screen, the board and the equipment walls behind them top out at {@code F2() + 7}, under one continuous
 *     crown beam ({@code F2() + 8}).</li>
 * <li><b>Archive terminal</b>: {@code AnchorNames.ARCHIVE_TERMINAL} is unique in a plan, so the archive level has
 *     lecterns as stand-ins (see {@code docs/proposals/leader-office-blocks.md}: a second terminal needs a kernel
 *     change). Same for the kanban {@code THREAD_WALL} and the board itself.</li>
 * <li><b>Glow light</b> (kit/glow-panel, {@link #glow}): cool strips under the screens, cool ceiling panels and a warm strip along
 *     each flight, one per step.</li>
 * <li><b>Model</b>: a 5 x 5 model of the HQ on the archive floor: hub, four ramps, four barrios, the outer ring; the red block is
 *     this tower.</li>
 * </ul>
 *
 * <h2>Adjusting it</h2>
 *
 * <ul>
 * <li><b>Move the tower</b>: change {@link #RIM_OFFSET}/{@link #ANGLE_DEG}; keep the door ({@code dz = -7}, facing north) on the
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
	/**
	 * feat/status-screen: key of the status screen (9 x 3, east wall) and its binding, {@code Binding.BEACON.wire()}: a big screen
	 * bound {@code beacon} draws the workspace summary ({@code StatusScreen}, client). Spelled out here so the plan code does not
	 * depend on the blocks module (the wire form is the contract).
	 */
	public static final String STATUS_SCREEN = "office_status";
	static final String STATUS_BINDING = "beacon";
	/** Anchor of the balcony's standing cell, looking out over the citadel (same note as {@link #OFFICE}). */
	public static final String OFFICE_BALCONY = "office_balcony";

	/** Angle (degrees from north, clockwise) of the terrace the tower stands on: between the south and south-east ramps. */
	public static final double ANGLE_DEG = 157.5;
	/**
	 * Distance from the promenade's outer rim to the tower's centre. With the 15-wide wall ring this puts the door on
	 * the deck's rim (the wall ring's north side at {@code dz = -7}) and the south-east skirt corner on the edge of the
	 * inner street.
	 */
	public static final double RIM_OFFSET = 6.5;

	/** Half width of the interior, of the wall ring, of the skirt (pilasters, cabin glass) and of the roof's eave. */
	private static final int H = 6;
	private static final int W = 7;
	private static final int E = 8;
	private static final int R = 9;
	/** Floor to floor of the lobby and the archive. */
	private static final int FH = 7;

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
		return F2() + 9;
	}

	/** Top of the wall (the clerestory band's last row; the hip roof starts one row higher at its ring 8). */
	static int WALL_TOP() {
		return CEIL() + 2;
	}

	/** The roof's last ring (m = 4): the drum stands on it. */
	private static int RT() {
		return CEIL() + 2 + (R - 4);
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

	/** A mullion position along a face: at {@code |i| = 2} and {@code 5} (every third block, the corner posts being at 8). */
	private static boolean post(int i) {
		int a = Math.abs(i);
		return a == 2 || a == 5;
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
				for (int y = F0() + 1; y <= CEIL() + 19; y++) {
					set(c, dx, y, dz, AIR);
				}
			}
		}
		// the eave's ring (m = 9) and the balcony's airspace above the promenade (the deck below stays as it is)
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				if (m(dx, dz) == R) {
					for (int y = F2(); y <= CEIL() + 19; y++) {
						set(c, dx, y, dz, AIR);
					}
				}
			}
		}
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -14; dz <= -E - 1; dz++) {
				for (int y = F2() - 2; y <= CEIL() + 19; y++) {
					set(c, dx, y, dz, AIR);
				}
			}
		}
		// the door's apron on the promenade: the deck stays, anything standing on it goes
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -11; dz <= -E - 1; dz++) {
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
				// the control room's floor is dark tile so the screens stand out, calm: see controlFloor
				set(c, dx, F2(), dz, m == H ? c.kit.beam(edge) : controlFloor(dx, dz));
				set(c, dx, CEIL(), dz, m == H || Math.floorMod(dz, 2) == 0 ? c.kit.beam(m == H ? edge : Direction.Axis.X) : PLANKS);
			}
		}
	}

	/**
	 * The control room's floor, quiet (struct/leader-office3): deep tile with one deepslate brick in nine, a single stripe of polished
	 * blackstone bricks one cell inside the beams (so the room has a frame) and a short inlay on the aisle's axis in front of the
	 * middle podium. No checker, no coloured corners.
	 */
	private static BlockState controlFloor(int dx, int dz) {
		if (m(dx, dz) == H - 1) {
			return CitadelCanvas.RIB;
		}
		if (dx == 0 && dz == 4) {
			return CitadelCanvas.b(Blocks.CHISELED_POLISHED_BLACKSTONE);
		}
		if (dx == 0 && (dz == 3 || dz == 5)) {
			return CitadelCanvas.RIB;
		}
		return Math.floorMod(dx * 7 + dz * 3, 9) == 0 ? CitadelCanvas.b(Blocks.DEEPSLATE_BRICKS) : FOOT;
	}

	// ------------------------------------------------------------------ walls

	/** The wall ring ({@code m = 7}): footing, plaster, beam courses at the floors, windows, doors, pilasters. */
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
				continue; // the control cabin has no wall at m = 7: its glass is at m = 8 (see cabin)
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

	/** Lobby wall: a footing course, a window band over the benches, the door (north, three wide, four high) between two logs. */
	private static BlockState lobbyWall(CitadelCanvas c, int y, int au, boolean north, Direction.Axis axis) {
		if (north && au <= 1) {
			if (y <= F0() + 4) {
				return AIR;
			}
			if (y == F0() + 5) {
				return c.kit.beam(axis);
			}
			return CALCITE;
		}
		if (post(au)) {
			return LOG;
		}
		if (y == F0() + 1) {
			return FOOT;
		}
		if (!north && au <= 1 && y >= F0() + 3 && y <= F0() + 5) {
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		if (au >= 3 && y >= F0() + 3 && y <= F0() + 5) {
			return c.kit.wallPanel(false);
		}
		return CALCITE;
	}

	/** Archive wall: a plaster sill, a ribbon of glass over the middle (not on the south, behind the shelves), shoji at the sides. */
	private static BlockState archiveWall(CitadelCanvas c, int y, int au, boolean south) {
		if (post(au)) {
			return LOG;
		}
		if (!south && au <= 1 && y >= F1() + 2 && y <= F1() + 5) {
			return Blocks.GLASS_PANE.defaultBlockState();
		}
		if (au >= 3 && y >= F1() + 2 && y <= F1() + 5) {
			return c.kit.wallPanel(false);
		}
		return CALCITE;
	}

	/**
	 * One wall cell of the cabin (m = 8) at height {@code y}: the door to the balcony on the north, a sill of upside-down cherry
	 * stairs under clear glass between cherry mullions (every third block), a tinted transom under the beam.
	 *
	 * @param idx position along the face (dx on the north and south faces, dz on the east and west ones)
	 */
	private static BlockState cabinWall(int y, int dx, int dz, boolean north, int idx) {
		boolean door = north && Math.abs(idx) <= 1;
		if (door && y <= F2() + 3) {
			return AIR;
		}
		if (y == F2() + 1) {
			return cherryStairs(inward(dx, dz), true);
		}
		if (post(idx)) {
			return LOG;
		}
		if (y == CEIL() - 1) {
			return CitadelCanvas.b(Blocks.TINTED_GLASS);
		}
		return CitadelCanvas.GLASS;
	}

	/**
	 * The control cabin: the top level flies one block past the archive's wall. Its fascia (a beam ring at the control floor, row
	 * {@code F2()}) sits at m = 8 on a ring of dark tile corbels, with the archive's wall (m = 7) set back under it; the cabin's own
	 * wall is clear glass (rows {@code F2() + 2 .. CEIL() - 1}) over a sill of upside-down cherry stairs, between cherry mullions
	 * every third block and four corner posts, under a beam at the ceiling and a lit clerestory. There is no inner wall above the
	 * floor: the cabin is 15 x 15 inside, the screens, the board and the consoles stand at m = 6 with the equipment walls
	 * ({@link #backing}) behind them at m = 7, and the door to the balcony is in the glass.
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
				int idx = ns ? dx : dz;
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
						s = cabinWall(y, dx, dz, north, idx);
					} else {
						s = post(idx) ? LOG : c.kit.wallPanel(true);
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
				int idx = ns ? dx : dz;
				int au = Math.abs(idx);
				if (!corner && !north && post(idx)) {
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
	 * Two flights, two blocks wide.
	 *
	 * <ul>
	 * <li><b>A</b>, lobby to archive, an L in the south-west corner: leg 1 runs south along the west wall ({@code dx = -6, -5},
	 *     steps at {@code dz = 2 .. 4}, {@code y = F0() + 1 .. + 3}), a 2 x 2 landing at the corner ({@code dz = 5, 6}, top at
	 *     {@code F0() + 4}), then leg 2 runs east along the south wall (steps at {@code dx = -4 .. -1}, {@code dz = 5, 6},
	 *     {@code y = F0() + 4 .. F0() + 7}, the last in the floor row). Entered head-on from {@code (-6..-5, 0..1)}. The
	 *     archive's floor is open over {@code dx = -4 .. -2}, fenced.</li>
	 * <li><b>B</b>, archive to control room, east columns {@code dx = 5, 6}: step {@code i} at {@code dz = 4 - i}
	 *     (rising north), {@code y = F1() + 1 + i}, seven steps, the last in the floor row. Entered head-on from the landing
	 *     {@code (5..6, 5..6)}. Well over {@code dz = -1..1}.</li>
	 * </ul>
	 *
	 * Every step has two free blocks above its walking surface (the well over the steps whose roof is lower than that), the
	 * landing has three, and the mass under each step is plank.
	 */
	public static void stairways(CitadelCanvas c) {
		for (int k = 0; k < 2; k++) {
			// A, leg 1: along the west wall, rising south
			for (int i = 0; i < 3; i++) {
				int x = -H + k;
				int z = 2 + i;
				set(c, x, F0() + 1 + i, z, cherryStairs(Direction.SOUTH, false));
				for (int y = F0() + 1; y < F0() + 1 + i; y++) {
					set(c, x, y, z, PLANKS);
				}
			}
			// A, the landing in the corner: its top is the back of the last step of leg 1
			for (int z = 5; z <= 6; z++) {
				for (int y = F0() + 1; y <= F0() + 3; y++) {
					set(c, -H + k, y, z, PLANKS);
				}
			}
			// A, leg 2: along the south wall, rising east, the last step in the floor row
			for (int j = 0; j < 4; j++) {
				int x = -4 + j;
				int z = 5 + k;
				set(c, x, F0() + 4 + j, z, cherryStairs(Direction.EAST, false));
				for (int y = F0() + 1; y < F0() + 4 + j; y++) {
					set(c, x, y, z, PLANKS);
				}
			}
			// A, the well: the headroom of the steps goes through the floor above
			for (int x = -4; x <= -2; x++) {
				set(c, x, F1(), 5 + k, AIR);
			}
			for (int i = 0; i < FH; i++) {
				// B: archive to control room, rising north
				int xb = 5 + k;
				int zb = 4 - i;
				set(c, xb, F1() + 1 + i, zb, cherryStairs(Direction.NORTH, false));
				for (int y = F1() + 1; y < F1() + 1 + i; y++) {
					set(c, xb, y, zb, PLANKS);
				}
			}
			// B, the well
			for (int dz = -1; dz <= 1; dz++) {
				set(c, 5 + k, F2(), dz, AIR);
			}
		}
	}

	// ------------------------------------------------------------------ lobby (feet y = F0() + 1)

	/**
	 * Lobby and workshop (13 x 13): a rug from the door; on the north wall the workbenches west of the door (crafting, smithing,
	 * fletching, stonecutter, an anvil) and the ender chest and two double chests east of it; on the west wall the blast furnace,
	 * grindstone, loom and barrels (the cells in front of flight A's first step, {@code dz = 0..1}, stay free); on the east wall a
	 * furnace, a smoker, barrels and two more double chests; on the south wall, east of flight A, a second bench and barrels. The
	 * middle of the room stays free from the door to the south wall, and every aisle is at least three wide.
	 */
	public static void lobby(CitadelCanvas c) {
		int y = F0() + 1;
		// rug: five wide from the door to the middle
		for (int dz = -6; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				set(c, dx, y, dz, carpet(Math.abs(dx) == 2 || dz == 2 ? RUG_EDGE : RUG));
			}
		}
		// north wall: workbenches west of the door, the ender chest and the double chests east of it
		set(c, -6, y, -6, Blocks.CRAFTING_TABLE.defaultBlockState());
		set(c, -5, y, -6, Blocks.SMITHING_TABLE.defaultBlockState());
		set(c, -4, y, -6, Blocks.FLETCHING_TABLE.defaultBlockState());
		set(c, -3, y, -6, facing(Blocks.STONECUTTER, Direction.SOUTH));
		set(c, -2, y, -6, facing(Blocks.ANVIL, Direction.EAST));
		set(c, 2, y, -6, facing(Blocks.ENDER_CHEST, Direction.SOUTH));
		for (int dx = 3; dx <= 5; dx += 2) {
			set(c, dx, y, -6, chest(Direction.SOUTH, ChestType.RIGHT));
			set(c, dx + 1, y, -6, chest(Direction.SOUTH, ChestType.LEFT));
		}
		// west wall: blast furnace, grindstone, loom, a barrel stack; dz = -1..1 stay free in front of flight A
		set(c, -6, y, -5, facing(Blocks.BLAST_FURNACE, Direction.EAST));
		set(c, -6, y, -4, Blocks.GRINDSTONE.defaultBlockState());
		set(c, -6, y, -3, facing(Blocks.LOOM, Direction.EAST));
		set(c, -6, y, -2, barrel());
		set(c, -6, y + 1, -2, barrel());
		// east wall: furnace and smoker, barrels, then two double chests
		set(c, 6, y, -5, facing(Blocks.FURNACE, Direction.WEST));
		set(c, 6, y, -4, facing(Blocks.SMOKER, Direction.WEST));
		for (int dz = -3; dz <= -2; dz++) {
			set(c, 6, y, dz, barrel());
			set(c, 6, y + 1, dz, barrel());
		}
		for (int dz = 1; dz <= 3; dz += 2) {
			set(c, 6, y, dz, chest(Direction.WEST, ChestType.RIGHT));
			set(c, 6, y, dz + 1, chest(Direction.WEST, ChestType.LEFT));
		}
		set(c, 6, y, 5, barrel());
		set(c, 6, y + 1, 5, barrel());
		set(c, 6, y, 6, barrel());
		set(c, 6, y + 1, 6, barrel());
		// south wall, east of flight A: a second bench and a row of barrels
		set(c, 0, y, 6, Blocks.CRAFTING_TABLE.defaultBlockState());
		set(c, 1, y, 6, facing(Blocks.LOOM, Direction.NORTH));
		for (int dx = 2; dx <= 5; dx++) {
			set(c, dx, y, 6, barrel());
		}
		// banners on the door's inner jambs, a plaque over the door outside
		set(c, -2, y + 2, -6, wallBanner(Direction.SOUTH));
		set(c, 2, y + 2, -6, wallBanner(Direction.SOUTH));
		sign(c, CX(), F0() + 5, CZ() - E, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Oficina", "");
		for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {0, 4}, {0, -3}}) {
			hang(c, p[0], F1() - 1, p[1]);
		}
		// canopy over the door, five blocks above the deck so nothing is in the way: stairs against the wall, a slab edge, two lanterns
		for (int dx = -2; dx <= 2; dx++) {
			set(c, dx, F0() + 6, -E, cherryStairs(Direction.SOUTH, false));
			set(c, dx, F0() + 6, -E - 1, slab(Blocks.CHERRY_SLAB));
		}
		hang(c, -2, F0() + 5, -E - 1);
		hang(c, 2, F0() + 5, -E - 1);
	}

	// ------------------------------------------------------------------ archive (feet y = F1() + 1)

	/**
	 * Archive and cartography (13 x 13): shelves and lecterns under the west windows, the map table and the model of the HQ, a bed
	 * nook, a reading corner, and flight A's well (south-west, fenced) with its exit on the south wall.
	 */
	public static void archive(CitadelCanvas c) {
		int y = F1() + 1;
		// the well of flight A (open over dx = -4..-2, dz = 5..6), fenced on the floor cells beside it
		for (int dz = 5; dz <= 6; dz++) {
			set(c, -5, y, dz, fence());
		}
		for (int dx = -4; dx <= -2; dx++) {
			set(c, dx, y, 4, fence());
		}
		// bed nook, north-west: head against the north wall
		set(c, -6, y, -6, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		set(c, -6, y, -5, facing(Blocks.BED.pick(BED), Direction.NORTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(c, -5, y, -6, chest(Direction.SOUTH, ChestType.SINGLE));
		set(c, -5, y, -5, Blocks.JUKEBOX.defaultBlockState());
		// west wall: tall shelves, lecterns (stand-ins for the archive terminal, see the class comment) and a low shelf under the window
		shelves(c, -6, y, -4, 3);
		set(c, -6, y, -1, facing(Blocks.LECTERN, Direction.EAST));
		set(c, -6, y, 0, Blocks.BOOKSHELF.defaultBlockState());
		set(c, -6, y, 1, facing(Blocks.LECTERN, Direction.EAST));
		shelves(c, -6, y, 2, 3);
		shelves(c, -6, y, 5, 3);
		shelves(c, -6, y, 6, 3);
		// south wall, east of the well: shelves under a plaque and two banners
		for (int dx = 1; dx <= 3; dx++) {
			shelves(c, dx, y, 6, 3);
		}
		set(c, 1, y + 3, 6, wallBanner(Direction.NORTH));
		set(c, 3, y + 3, 6, wallBanner(Direction.NORTH));
		sign(c, CX() + 2, y + 3, CZ() + 6, Direction.NORTH, DyeColor.ORANGE, "", "Archivo", "", "");
		// north wall: enchanting and map tables, reading stands, a sofa in the north-east corner
		set(c, -2, y, -6, Blocks.ENCHANTING_TABLE.defaultBlockState());
		set(c, -1, y, -6, facing(Blocks.LECTERN, Direction.SOUTH));
		set(c, 0, y, -6, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		set(c, 1, y, -6, facing(Blocks.LECTERN, Direction.SOUTH));
		set(c, 2, y, -6, Blocks.POTTED_BAMBOO.defaultBlockState());
		for (int dx = 4; dx <= 6; dx++) {
			set(c, dx, y, -6, cherryStairs(Direction.NORTH, false));
		}
		set(c, 6, y, -5, cherryStairs(Direction.EAST, false));
		set(c, 6, y, -4, cherryStairs(Direction.EAST, false));
		// the model of the HQ, 5 x 5: hub in the middle, the four ramps, four barrios, the outer ring; the red block is this tower
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (m(dx, dz) == 2) {
					set(c, dx, y, dz, slab(Blocks.POLISHED_ANDESITE_SLAB));
				}
			}
		}
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
		set(c, 1, y, 2, CitadelCanvas.terracotta(DyeColor.RED));
		// a pink rug round the model
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (m(dx, dz) == 3) {
					set(c, dx, y, dz, carpet(RUG_EDGE));
				}
			}
		}
		for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {0, -5}}) {
			hang(c, p[0], F2() - 1, p[1]);
		}
	}

	// ------------------------------------------------------------------ control room (feet y = F2() + 1)

	/**
	 * Control room (13 x 13 floor, 15 x 15 under the glass): the threads screen with its consoles, two project screens over
	 * chests, the attention board, a rug centred on the room's axis, the fences of flight B's well and the plaque.
	 */
	public static void control(CitadelCanvas c) {
		int y = F2() + 1;
		// the well of flight B, fenced on its open sides
		for (int dz = -1; dz <= 1; dz++) {
			set(c, 4, y, dz, fence());
		}
		for (int dx = 4; dx <= 6; dx++) {
			set(c, dx, y, 2, fence());
		}
		// rug in front of the consoles, centred on the room's axis (the door's, the screen's, the middle console's): seven wide and
		// seven deep with a pink border, filling the space between the walkway (dz = -6) and the console bank (dz = 2); the player
		// stands on its south edge (the OFFICE anchor) facing south
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -5; dz <= 1; dz++) {
				boolean edge = Math.abs(dx) == 3 || dz == -5 || dz == 1;
				set(c, dx, y, dz, carpet(edge ? RUG_EDGE : RUG));
			}
		}
		// the consoles: a bank of five console terminals (kit role CONSOLE; a lectern with the blocks module off), side by side at
		// dz = 2, centred on the axis, so their screens read as one command desk, facing the player on the rug to the north. That
		// leaves a 3-wide aisle (dz = 3..5) between the bank and the podiums. A slab with a lantern closes the bank on each end.
		for (int dx = -2; dx <= 2; dx++) {
			set(c, dx, y, 2, c.kit.console(Direction.NORTH));
		}
		for (int dx : new int[] {-3, 3}) {
			set(c, dx, y, 2, topSlab(Blocks.CHERRY_SLAB));
			set(c, dx, y + 1, 2, lantern(false));
		}
		// under the main screen: the decision podiums (five, every other cell from west to east, so each one has a free cell beside
		// it and its own display reads); between them a cherry pilaster with a dark capital in the screen's base, so the plinth
		// reads as bays
		podiums(c, y);
		for (int dx : new int[] {-5, -3, -1, 1, 3, 5}) {
			set(c, dx, y, H, c.kit.beam(Direction.Axis.Y));
		}
		// threads screen: 11 x 5 on the south wall, bound to the hub's feed; its top row is the room's crown line (F2() + 7)
		HqGeometry.Surface main = new HqGeometry.Surface(new BlockPos(CX() + 5, F2() + 3, CZ() + H), Direction.WEST, Direction.NORTH, 11, 5);
		CitadelHub.bigScreen(c, main, OFFICE, HqGenerator.hubBinding(c.layout), true);
		for (int dx : new int[] {-5, -3, -1, 1, 3, 5}) {
			c.fixture(new BlockPos(CX() + dx, F2() + 2, CZ() + H), CitadelCanvas.RIB);
		}
		sign(c, CX(), F2() + 8, CZ() + H, Direction.NORTH, DyeColor.ORANGE, "", "Facundo", "Hilos", "");
		// project screens: two 5 x 5 on the west wall, same crown line, chests under them
		List<String> projects = projectBindings(c, 2);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - H, F2() + 3, CZ() + 5), Direction.NORTH, Direction.EAST, 5, 5),
				"office_w0", projects.get(0), false);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(CX() - H, F2() + 3, CZ() - 1), Direction.NORTH, Direction.EAST, 5, 5),
				"office_w1", projects.get(1), false);
		for (int dz = -5; dz <= 5; dz++) {
			if (Math.abs(dz) >= 2) {
				// a free cell either side of the merge station (dz = 0), so it is not boxed in
				set(c, -H, y, dz, chest(Direction.EAST, ChestType.SINGLE));
			}
			c.fixture(new BlockPos(CX() - H, F2() + 2, CZ() + dz), c.style.screenBase());
		}
		set(c, -H, F2() + 3, 0, wallBanner(Direction.EAST));
		set(c, -H, F2() + 4, 0, wallBanner(Direction.EAST));
		// a map table in the north-west corner, for planning on the HQ's map, and a chest of plans in the north-east one
		set(c, -H, y, -H, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		set(c, H, y, -H, chest(Direction.WEST, ChestType.SINGLE));
		// merge station (kit role MERGE_STATION; a smithing table without the blocks module) in the gap between the two project
		// screens, facing the room: its list opens the pending PRs of the first screen's project
		BlockPos station = new BlockPos(CX() - H, y, CZ());
		c.fixture(station, c.kit.mergeStation(Direction.EAST));
		c.anchor(Anchor.face(AnchorNames.mergeStation("office", projects.get(0)), station, Direction.EAST));
		attentionBoard(c);
		backing(c);
		// light: lanterns only over the middle and the north half; the screen's side gets glow panels, so nothing hangs in front of it
		for (int[] p : new int[][] {{-4, -4}, {0, -4}, {4, -4}, {-4, 0}, {0, 0}}) {
			hang(c, p[0], CEIL() - 1, p[1]);
		}
		glow(c);
	}

	/**
	 * The equipment walls behind the screens and the board (the cabin has no inner wall): at m = 7, full height up to one crown
	 * beam ({@code F2() + 8}) shared by every wall, as wide as what hangs on them (thirteen behind the threads screen and the
	 * podiums, eleven behind the board and the project screens), so nothing floats; the corners of the walkway stay open and glazed,
	 * and reachable from the room's corners. The base is dark (two rows), the body pale plaster so the cabin's glass reads as a lantern
	 * from outside, with cherry pilasters on the mullions' axes.
	 */
	private static void backing(CitadelCanvas c) {
		for (int y = F2() + 1; y < CEIL(); y++) {
			for (int i = -H; i <= H; i++) {
				set(c, i, y, W, backingBlock(c, i, y, Direction.Axis.X, CX() + i, CZ() + W));
			}
			for (int i = -5; i <= 5; i++) {
				set(c, W, y, i, backingBlock(c, i, y, Direction.Axis.Z, CX() + W, CZ() + i));
				set(c, -W, y, i, backingBlock(c, i, y, Direction.Axis.Z, CX() - W, CZ() + i));
			}
		}
	}

	private static BlockState backingBlock(CitadelCanvas c, int i, int y, Direction.Axis axis, int x, int z) {
		if (y == CEIL() - 1) {
			return c.kit.beam(axis);
		}
		if (post(i)) {
			return LOG;
		}
		if (y <= F2() + 2) {
			int h = Math.floorMod(i * 5 + y * 3, 7);
			return h == 0 ? CitadelCanvas.RIB : h < 3 ? CitadelCanvas.b(Blocks.DEEPSLATE_BRICKS) : FOOT;
		}
		return plaster(x, y, z);
	}

	/**
	 * kit/glow-panel: integrated light for the control room and the two flights, with the kit's glow blocks (vanilla
	 * fallback: the invisible light block, or a froglight in the panels). Nothing hangs in front of the screens:
	 * <ul>
	 * <li>three cool ceiling panels over the screen's side ({@code dx = -4, 0, 4}), on the room's axis;</li>
	 * <li>cool strips under the screens, hugging their wall: in front of the main screen's base (11 cells), and under the
	 *     two project screens and the banner between them (11 cells), so the light falls on the console, the barrels and the chests;</li>
	 * <li>warm strips along the wall side of each flight, one per step in the cell above it, so the climb is lit step by
	 *     step (the last step of each flight is left alone: the next floor's furniture stands there).</li>
	 * </ul>
	 * Everything is placed only into air, and no strip blocks anybody (no collision).
	 */
	private static void glow(CitadelCanvas c) {
		int y = F2() + 1;
		for (int dx : new int[] {-4, 0, 4}) {
			if (solid(c, dx, CEIL(), 4)) {
				set(c, dx, CEIL(), 4, c.kit.glowPanel(Direction.DOWN, true));
			}
		}
		// under the main screen: its base beam is on the wall row (dz = 6), the strip hugs its front, at the top of the cell
		for (int dx = -5; dx <= 5; dx++) {
			glowStrip(c, dx, y + 1, H - 1, c.kit.glowStrip(Direction.NORTH, true, true));
		}
		// under the project screens (west wall), one cell into the room (dx = -5), like the main screen's
		for (int dz = -5; dz <= 5; dz++) {
			glowStrip(c, -H + 1, y + 1, dz, c.kit.glowStrip(Direction.EAST, true, true));
		}
		// the flights, along the wall: B rises north on the east wall (dx = 6)
		for (int i = 0; i < FH - 1; i++) {
			glowStrip(c, H, F1() + 2 + i, 4 - i, c.kit.glowStrip(Direction.WEST, false, false));
		}
		// A is an L: leg 1 rises south on the west wall (dx = -6), leg 2 rises east on the south wall (dz = 6)
		for (int i = 0; i < 3; i++) {
			glowStrip(c, -H, F0() + 2 + i, 2 + i, c.kit.glowStrip(Direction.EAST, false, false));
		}
		for (int j = 0; j < 2; j++) {
			glowStrip(c, -4 + j, F0() + 5 + j, H, c.kit.glowStrip(Direction.NORTH, false, false));
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
	 * The east wall: the status screen (nine wide, three high, its top row on the room's crown line) over a strip (a dark plinth
	 * with plaques, and a beam row) that carries the attention board's five status lamps, anchored {@code board_lamp_5..9} (the
	 * hub's board owns 0 to 4), so the office shows the next five threads that need the player.
	 */
	private static void attentionBoard(CitadelCanvas c) {
		int x = CX() + H;
		int bottom = F2() + 4;
		// feat/status-screen: the board's frame is now a strip of one row (a beam with the five lamps in it) under the status screen:
		// a 9 x 3 big screen bound `beacon` (the workspace summary: threads by state, PRs, projects, oldest waiting), its top row on
		// the room's crown line like the others. Viewed from the room the east wall's left end is the north one (dz = -4).
		for (int dz = -4; dz <= 4; dz++) {
			c.fixture(new BlockPos(x, bottom - 1, CZ() + dz), c.style.screenBase()); // plinth: the plaques hang on it
			if (Math.abs(dz) > 2) {
				c.fixture(new BlockPos(x, bottom, CZ() + dz), c.kit.beam(Direction.Axis.Z));
			}
		}
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(x, bottom + 1, CZ() - 4), Direction.SOUTH, Direction.WEST, 9, 3),
				STATUS_SCREEN, STATUS_BINDING, false);
		int lamp = 5;
		for (int dz = -2; dz <= 2; dz++) {
			BlockPos p = new BlockPos(x, bottom, CZ() + dz);
			c.fixture(p, c.style.statusLamp());
			c.anchor(Anchor.face(AnchorNames.boardLamp(lamp), p, Direction.WEST));
			// a plaque under each lamp with its rank in the attention queue (the hub board has 1 to 5)
			sign(c, x - 1, bottom - 1, CZ() + dz, Direction.WEST, DyeColor.ORANGE, "", "#" + (lamp + 1), "", "");
			lamp++;
		}
		sign(c, x - 1, bottom - 1, CZ() - 3, Direction.WEST, DyeColor.ORANGE, "", "Estado", "de un vistazo", "");
		sign(c, x - 1, bottom - 1, CZ() + 3, Direction.WEST, DyeColor.ORANGE, "", "Atencion", "6 a 10", "");
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
	 * with three free blocks in front of it (the aisle behind the consoles).
	 */
	private static void podiums(CitadelCanvas c, int y) {
		for (int i = 0; i < PODIUMS; i++) {
			BlockPos p = new BlockPos(CX() - 4 + 2 * i, y, CZ() + H);
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
	 * The balcony on the north side of the control room: a deck five deep over the promenade, in front of the glass door, on
	 * corbels, with a fascia beam, a fence, corner posts, standing lanterns and two benches facing the citadel.
	 */
	public static void balcony(CitadelCanvas c) {
		int f = F2();
		for (int dx = -E; dx <= E; dx++) {
			for (int dz = -13; dz <= -E - 1; dz++) {
				boolean outer = dz == -13;
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
		set(c, -6, f + 1, -12, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		set(c, 6, f + 1, -12, Blocks.POTTED_BAMBOO.defaultBlockState());
		// two benches against the rail, facing the view
		for (int dx : new int[] {-3, 3}) {
			set(c, dx, f + 1, -12, cherryStairs(Direction.SOUTH, false));
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * The curved eave cell at ring {@code m = 9}: the eave swoops up towards each corner in a stepped curve, with {@code along} the
	 * cell's distance from the face's middle. Heights above the eave's base row ({@code CEIL() + 2}):
	 * <pre>
	 *   along <= 5  stair
	 *   6           stair, slab        (about half a block up)
	 *   7           stair, planks      (one up)
	 *   8           stair, planks, stair
	 *   9 (corner)  planks x3, stair, fence post and lantern (the finial), a hanging lantern under it
	 * </pre>
	 */
	private static void eave(CitadelCanvas c, int dx, int dz, Direction in, boolean corner) {
		int base = CEIL() + 2;
		int along = Math.abs(dx) == R ? Math.abs(dz) : Math.abs(dx);
		if (corner) {
			set(c, dx, base, dz, PLANKS);
			set(c, dx, base + 1, dz, PLANKS);
			set(c, dx, base + 2, dz, PLANKS);
			set(c, dx, base + 3, dz, stairs(Blocks.CHERRY_STAIRS, in));
			set(c, dx, base + 4, dz, fence());
			set(c, dx, base + 5, dz, lantern(false));
			hang(c, dx, CEIL() + 1, dz);
			return;
		}
		set(c, dx, base, dz, stairs(Blocks.CHERRY_STAIRS, in));
		if (along == 6) {
			set(c, dx, base + 1, dz, slab(Blocks.CHERRY_SLAB));
		} else if (along == 7) {
			set(c, dx, base + 1, dz, PLANKS);
		} else if (along == 8) {
			set(c, dx, base + 1, dz, PLANKS);
			set(c, dx, base + 2, dz, stairs(Blocks.CHERRY_STAIRS, in));
		}
	}

	/**
	 * The hip roof: an eave at m = 9 (cherry stairs) with a curved sweep to each corner ({@link #eave}), cherry and
	 * deepslate-tile stairs for the five rings above it up to {@code m = 4}, solid under them; over {@code m <= 3} a glass drum
	 * (7 x 7, three high, lit from inside) with a stepped cap of its own, an eave and a finial. The clerestory band under the roof
	 * (the cabin's wall at m = 8) is lit shoji between the mullions.
	 */
	public static void roof(CitadelCanvas c) {
		int rt = RT();
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				int m = m(dx, dz);
				Direction in = inward(dx, dz);
				boolean corner = Math.abs(dx) == R && Math.abs(dz) == R;
				if (m == R) {
					eave(c, dx, dz, in, corner);
				} else if (m >= 4) {
					int ys = CEIL() + 2 + (R - m);
					// one cherry ring in the tiles breaks the big slope
					set(c, dx, ys, dz, stairs(m == 6 ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, in));
					for (int y = WALL_TOP() + 1; y < ys; y++) {
						set(c, dx, y, dz, FOOT);
					}
				} else {
					// the drum: floor, glass ring (m = 3), cap, stepped top
					for (int y = WALL_TOP() + 1; y <= rt; y++) {
						set(c, dx, y, dz, FOOT);
					}
					if (m == 3) {
						boolean post = Math.abs(dx) == 3 && Math.abs(dz) == 3;
						for (int y = rt + 1; y <= rt + 3; y++) {
							set(c, dx, y, dz, post ? LOG : CitadelCanvas.GLASS);
						}
					}
					set(c, dx, rt + 4, dz, FOOT);
					if (m == 3) {
						set(c, dx, rt + 5, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
					} else {
						set(c, dx, rt + 5, dz, FOOT);
						if (m == 2) {
							set(c, dx, rt + 6, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
						} else {
							set(c, dx, rt + 6, dz, FOOT);
							if (m == 1) {
								set(c, dx, rt + 7, dz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in));
							} else {
								set(c, dx, rt + 7, dz, FOOT);
								set(c, dx, rt + 8, dz, fence());
								set(c, dx, rt + 9, dz, lantern(false));
								// inside the drum: a standing lantern
								set(c, dx, rt + 1, dz, lantern(false));
							}
						}
					}
				}
			}
		}
		// inside the drum: four hanging lanterns
		for (int dx : new int[] {-1, 1}) {
			for (int dz : new int[] {-1, 1}) {
				hang(c, dx, rt + 3, dz);
			}
		}
		// the drum's eave
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (m(dx, dz) == 4) {
					set(c, dx, rt + 4, dz, stairs(Blocks.CHERRY_STAIRS, inward(dx, dz)));
				}
			}
		}
	}

	// ------------------------------------------------------------------ anchors

	/** {@code office}: in front of the consoles, facing the threads screen. {@code office_balcony}: the balcony, facing the hub. */
	public static void anchors(CitadelCanvas c) {
		c.anchor(Anchor.at(OFFICE, new BlockPos(CX(), F2() + 1, CZ() + 1), Direction.SOUTH));
		c.anchor(Anchor.at(OFFICE_BALCONY, new BlockPos(CX(), F2() + 1, CZ() - 11), Direction.NORTH));
	}

	// ------------------------------------------------------------------ garden on the south-east slope

	/** Top block (y, flat frame) of the upper and the lower garden terrace over the lawn (whose top block is y = 0). */
	private static final int T1 = 4;
	private static final int T2 = 2;
	/** Cells out from the skirt the upper tier reaches ({@link #T1}); the lower one goes on to {@link #OUT2}. */
	private static final int OUT1 = 6;
	private static final int OUT2 = 11;

	/**
	 * Cells the garden is out from the tower's skirt on its east and south sides ({@code 0}: not part of the garden). East of
	 * the tower the garden spans {@code dz = -8..11}, south of it {@code dx = -8..11}; round the corner the rings are square.
	 */
	private static int outward(int dx, int dz) {
		if (dx > E && dz > E) {
			return Math.max(dx, dz) - E;
		}
		if (dx > E && dz >= -8 && dz <= 11) {
			return dx - E;
		}
		if (dz > E && dx >= -8 && dx <= 11) {
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
		if (o <= 0 || o > OUT2 || !lawn(c, CX() + dx, CZ() + dz)) {
			return 0;
		}
		return o <= OUT1 ? T1 : T2;
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
	 * The south-east slope of the terrace, which was bare grass, is a terraced garden: two tiers
	 * (top blocks y = 4 and y = 2 over the lawn at y = 0) against the tower's skirt on its east and south sides, held by
	 * deepslate walls with a calcite coping, with flowers and azalea bushes on them. A stone path two wide climbs from the lawn
	 * by two short stairs per tier (two steps each, so every step is half a block) to the upper tier, and an open pergola
	 * ({@link #arbor}) stands on the field east of the tower: cherry posts, slatted rafters, cave vines and azalea hanging from them,
	 * two benches facing each other over a map table. Only lawn columns of the terrace zone are touched, in the flat frame the relief
	 * lifts afterwards.
	 */
	public static void garden(CitadelCanvas c) {
		for (int dx = E + 1; dx <= 24; dx++) {
			for (int dz = -8; dz <= 17; dz++) {
				gardenColumn(c, dx, dz);
			}
		}
		for (int dx = -8; dx <= E; dx++) {
			for (int dz = E + 1; dz <= 17; dz++) {
				gardenColumn(c, dx, dz);
			}
		}
		arbor(c);
		// lamps beside the path on the lower tier
		for (int dz : new int[] {2, 5}) {
			if (tier(c, E + 9, dz) == T2) {
				for (int y = T2 + 1; y <= T2 + 2; y++) {
					set(c, E + 9, y, dz, fence());
				}
				set(c, E + 9, T2 + 3, dz, lantern(false));
			}
		}
	}

	private static void gardenColumn(CitadelCanvas c, int dx, int dz) {
		int o = outward(dx, dz);
		int x = CX() + dx;
		int z = CZ() + dz;
		boolean path = dx > E && (dz == 3 || dz == 4) && o > 0 && o <= OUT2 + 6;
		int t = tier(c, dx, dz);
		if (t == 0) {
			// the path goes on over the lawn past the lower tier: a stair at the foot, then flat stones
			if (path && o > OUT2 && lawn(c, x, z)) {
				if (o == OUT2 + 1) {
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
		if (path && o <= OUT2) {
			boolean last = o == OUT1 || o == OUT2;
			if (last) {
				// the stair that climbs onto the tier: the column below it is wall, the stair stands on top of it
				c.set(x, t, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
				return;
			}
			c.set(x, t, z, gardenPath(x, z));
			if (o == OUT1 + 1) {
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
	 * The pergola, found on the grass field east of the tower (the podium's middle tier, top block y = 3): a 3 x 9 footprint (or 7, or
	 * 4 where the field is short), {@code dx0 .. dx0 + 2}, {@code dz0 .. dz0 + len - 1}, nearest the tower that is all bed. Nothing is
	 * built when the field has no such place.
	 */
	private static void arbor(CitadelCanvas c) {
		for (int len : new int[] {9, 7, 4}) {
			for (int dx0 = E + 1; dx0 <= 20; dx0++) {
				for (int dz0 = -2; dz0 >= -20; dz0--) {
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

	/**
	 * An open pergola (struct/leader-office3): posts of cherry log three high every third cell, a beam along each long side and
	 * slatted rafters across every second cell (so the sky shows between them), stone paving, two benches facing each other with a
	 * map table between them at each end, and vegetation hanging from the rafters: cave vines two blocks long down the middle,
	 * a vine under each side beam between the posts, azalea leaves resting on some rafters, and two hanging lanterns.
	 */
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
					for (int y = b; y <= b + 5; y++) {
						set(c, dx, y, dz, AIR);
					}
				}
			}
		}
		Set<Integer> posts = new HashSet<>();
		for (int dz = dz0; dz <= last; dz += 3) {
			posts.add(dz);
		}
		posts.add(last);
		for (int dx : new int[] {dx0, dx0 + 2}) {
			for (int dz : posts) {
				for (int y = b; y < b + 3; y++) {
					set(c, dx, y, dz, LOG);
				}
			}
			for (int dz = dz0; dz <= last; dz++) {
				set(c, dx, b + 3, dz, c.kit.beam(Direction.Axis.Z));
			}
			// a vine under the side beam between the posts
			for (int dz = dz0; dz <= last; dz++) {
				if (!posts.contains(dz) && Math.floorMod(dz - dz0, 2) == 1) {
					c.put(CX() + dx, b + 2, CZ() + dz, vineUp(), HANGING);
				}
			}
		}
		for (int dz = dz0; dz <= last; dz += 2) {
			for (int dx = dx0; dx <= dx0 + 2; dx++) {
				set(c, dx, b + 4, dz, c.kit.beam(Direction.Axis.X));
			}
			// azalea resting on some rafters
			if (Flora.hash(CX() + mid, CZ() + dz, 9) % 3 != 0) {
				set(c, mid, b + 5, dz, CitadelCanvas.leaves(Flora.hash(CX() + mid, CZ() + dz, 5) % 2 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES
						: Blocks.AZALEA_LEAVES));
			}
		}
		// the benches' pairs (backs outwards, facing each other), a map table between them at each end
		int[] pairs = len >= 7 ? new int[] {dz0 + 1, last - 1} : new int[] {dz0 + 1};
		for (int dz : pairs) {
			set(c, dx0, b, dz, cherryStairs(Direction.WEST, false));
			set(c, dx0 + 2, b, dz, cherryStairs(Direction.EAST, false));
			set(c, mid, b, dz, dz == dz0 + 1 ? CitadelCanvas.b(Blocks.CARTOGRAPHY_TABLE) : Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		}
		// under the rafters of the middle: cave vines two long (the heads clear the tables and the heads of people), and two lanterns
		for (int dz = dz0; dz <= last; dz += 2) {
			if (len >= 7 && (dz == dz0 + 4 || dz == last)) {
				hang(c, mid, b + 3, dz);
			} else {
				c.put(CX() + mid, b + 3, CZ() + dz, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES,
						Flora.hash(CX() + mid, CZ() + dz, 3) % 2 == 0), HANGING);
				c.put(CX() + mid, b + 2, CZ() + dz, Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true), HANGING);
			}
		}
	}

	/** A vine hanging from the block above it. */
	private static BlockState vineUp() {
		return Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.UP, true);
	}
}
