package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

/**
 * The shell of the basilica as seen from outside and from the aisles: the outer wall (piers on the bay
 * lines, stained lancet windows between them, cornice), its parapet with merlons and pinnacles, the rose
 * window of the two gables with its coping, and the trim of both portals. Pure functions of the column;
 * nothing random (colours cycle by bay).
 *
 * <p>Bays: along a wall face the coordinate {@code t} (z on the east and west faces, x on the others)
 * repeats every 4 cells ({@code s(4)}); {@code u = floorMod(t + 2, 4)}: {@code u == 0} is a pier (the same
 * lines as the nave's arcade piers), {@code u == 1..3} a bay, {@code u == 2} its middle.
 */
public final class HubFacade {
	private HubFacade() {
	}

	/** Position along the face of a wall column. */
	static int along(int x, int z) {
		return Math.abs(x) >= Math.abs(z) ? z : x;
	}

	/** {@code 0}: pier; {@code 1..3}: bay (2 is the middle). */
	static int bay(int x, int z) {
		return Math.floorMod(along(x, z) + HubDims.PIER_PHASE(), HubDims.PIER_PERIOD());
	}

	static boolean pier(int x, int z) {
		return bay(x, z) == 0;
	}

	/** The colour of the window of a bay, cycled by the bay and the quadrant. */
	static DyeColor bayColor(int x, int z) {
		int i = Math.floorDiv(along(x, z) + HubDims.PIER_PHASE(), HubDims.PIER_PERIOD()) + (x > 0 ? 1 : 0) + (z > 0 ? 2 : 0);
		return HubBlocks.GLASS_COLORS[Math.floorMod(i, HubBlocks.GLASS_COLORS.length)];
	}

	/** Whether an outer-wall window may open here: not behind the screens, thread wall nor board. */
	static boolean windowAllowed(int x, int z) {
		boolean ew = Math.abs(x) > Math.abs(z);
		return !(ew && Math.abs(z) <= HubDims.BLIND_HALF());
	}

	/** The dark base course of a wall column (the podium): y1 to the floor, a scatter of deepslate variants. */
	public static void base(CitadelCanvas c, int x, int z) {
		for (int y = 1; y <= y0(); y++) {
			c.set(x, y, z, HubBlocks.darkBase(x, y, z));
		}
	}

	/**
	 * Whether the wall column has a window of the original wall (outer neighbours get a sill and a lintel course from
	 * {@code HubExterior}). From scale 2 the tall facade draws its own sills and hoods ({@link #paintOuter}), so this is false.
	 */
	static boolean hasWindow(int x, int z) {
		if (HubDims.TALL()) {
			return false;
		}
		boolean naveEnd = Math.abs(x) <= HubDims.NAVE_HALF() && Math.abs(z) >= HubDims.INNER();
		return !naveEnd && !pier(x, z) && windowAllowed(x, z);
	}

	// ------------------------------------------------------------------ the tall facade (struct/hub4)

	/** Axis the face runs along: z on the east and west faces, x elsewhere. */
	private static Direction.Axis faceAxis(int x, int z) {
		return Math.abs(x) >= Math.abs(z) ? Direction.Axis.Z : Direction.Axis.X;
	}

	private static boolean naveEnd(int x, int z) {
		return Math.abs(x) <= HubDims.NAVE_HALF() && Math.abs(z) >= HubDims.INNER();
	}

	/** Whether the column is inside the span of a window of its bay (any row). */
	private static boolean winSpan(int x, int z, boolean naveEnd) {
		if (naveEnd) {
			int ax = Math.abs(x);
			return ax >= HubDims.END_WIN_LO() && ax <= HubDims.END_WIN_HI();
		}
		int u = bay(x, z);
		return u >= 2 && u <= HubDims.PIER_PERIOD() - 2;
	}

	/** Half the window's width: 2 (5 wide) in a bay of 8; 1 (3 wide) beside the portal. */
	private static int winHalf(boolean naveEnd) {
		return naveEnd ? (HubDims.END_WIN_HI() - HubDims.END_WIN_LO()) / 2 : (HubDims.PIER_PERIOD() - 4) / 2;
	}

	/** Distance of the column from the middle of its window (0 = the mullion's column). */
	private static int winOff(int x, int z, boolean naveEnd) {
		if (naveEnd) {
			return Math.abs(Math.abs(x) - (HubDims.END_WIN_LO() + HubDims.END_WIN_HI()) / 2);
		}
		return Math.abs(bay(x, z) - HubDims.PIER_PERIOD() / 2);
	}

	/** Whether the cell is open (glass or mullion): a window with a stepped head, narrower in its top row. */
	private static boolean winOpen(int x, int z, int r, boolean naveEnd) {
		if (r < HubDims.WIN_SILL() || r > HubDims.WIN_TOP() || !winSpan(x, z, naveEnd)) {
			return false;
		}
		int half = winHalf(naveEnd);
		int inset = Math.max(0, (half + 1) / 2 - (HubDims.WIN_TOP() - r));
		return winOff(x, z, naveEnd) <= half - inset;
	}

	/**
	 * The one bay that gets a tapestry: the middle of the north end, over the door and behind the board. (The bays of the east and west
	 * faces behind the screens have the same windows as the others, in grey glass with a coloured head: dark where a screen is behind.)
	 */
	private static boolean blindBay(int x, int z, boolean naveEnd) {
		return naveEnd && z < 0 && Math.abs(x) <= HubDims.PIER_PERIOD() / 2 - 1;
	}

	/** One cell of the tall wall: row {@code r} above the podium of the column {@code (x, z)}. */
	private static BlockState tallCell(int x, int y, int z, int r, boolean naveEnd) {
		int h = HubDims.WALL_H();
		int pl = HubDims.PLINTH();
		int p = HubDims.PIER_PERIOD();
		Direction.Axis along = faceAxis(x, z);
		if (r < pl) {
			return HubBlocks.darkBase(x, y, z); // the dark plinth: deepslate variants
		}
		if (r == pl) {
			return RIB; // its polished cap course, the sill line of the windows
		}
		if (r == h) {
			return naveEnd ? RIB : CALCITE;
		}
		if (pier(x, z)) {
			return r == pl + 1 ? RIB : r == h - 1 ? CALCITE : HubBlocks.strippedLog(Direction.Axis.Y);
		}
		if (winOpen(x, z, r, naveEnd)) {
			int top = HubDims.WIN_TOP();
			if (!naveEnd && winOff(x, z, naveEnd) == 0) {
				return HubBlocks.strippedLog(Direction.Axis.Y); // the mullion
			}
			if (r == top - 2) {
				return HubBlocks.strippedLog(along); // the transom
			}
			if (r > top - 2) {
				return HubBlocks.pane(bayColor(x, z));
			}
			return windowAllowed(x, z) || naveEnd ? Blocks.GLASS_PANE.defaultBlockState() : HubBlocks.pane(DyeColor.GRAY);
		}
		if (r == h - 2) {
			// the impost band over the heads of the windows, with a keystone in the middle of each one
			boolean key = winSpan(x, z, naveEnd) && winOff(x, z, naveEnd) == 0;
			return key ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : HubBlocks.strippedLog(along);
		}
		if (blindBay(x, z, naveEnd) && r >= HubDims.WIN_SILL() && r <= HubDims.WIN_TOP()) {
			return tapestry(Math.abs(x), p / 2 - 1, r, along);
		}
		return PLANKS;
	}

	/**
	 * A tapestry hung in a blind bay: a frame of stripped cherry, a red field with a gold stripe down the middle and a gold hem,
	 * so the long walls behind the screens read as bays and not as a blank field.
	 */
	private static BlockState tapestry(int off, int half, int r, Direction.Axis along) {
		if (r == HubDims.WIN_SILL() || r == HubDims.WIN_TOP()) {
			return HubBlocks.strippedLog(along);
		}
		if (off == half) {
			return HubBlocks.strippedLog(Direction.Axis.Y);
		}
		boolean gold = off == 0 || r == HubDims.WIN_SILL() + 1;
		return Blocks.WOOL.pick(gold ? DyeColor.YELLOW : DyeColor.RED).defaultBlockState();
	}

	/**
	 * What the tall facade adds on the cells outside the wall, after {@code HubExterior.paint} (it paints on top of the patio and
	 * the bridges' sides): a stepped cornice (a cherry corbel course under the dark one), a sill and a stone hood over each window,
	 * and a calcite pilaster with a capital on the piers of the flat faces and the end walls. Nothing is put where a cell is
	 * already taken (buttresses, towers) or beside a bridge.
	 */
	public static void paintOuter(CitadelCanvas c) {
		if (!HubDims.TALL()) {
			return;
		}
		int y0 = y0();
		int h = HubDims.WALL_H();
		int pl = HubDims.PLINTH();
		for (int x = -HubDims.SCAN(); x <= HubDims.SCAN(); x++) {
			for (int z = -HubDims.SCAN(); z <= HubDims.SCAN(); z++) {
				if (c.zoneAt(x, z).kind() != Kind.HUB_WALL) {
					continue;
				}
				boolean end = naveEnd(x, z);
				boolean flat = Math.abs(x) == HubDims.FACE() || Math.abs(z) == HubDims.FACE();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = x + d.getStepX();
					int nz = z + d.getStepZ();
					Kind k = c.zoneAt(nx, nz).kind();
					if (k == Kind.HUB || k == Kind.HUB_WALL || k == Kind.NONE || k == Kind.BRIDGE) {
						continue;
					}
					Direction back = d.getOpposite();
					boolean pier = pier(x, z);
					if (pier && flat && !corner(x, z) && c.at(nx, y0 + 1, nz) == null && !(end && Math.abs(x) < HubDims.NAVE_HALF())) {
						outerPilaster(c, nx, nz, back);
						continue;
					}
					if (winSpan(x, z, end) && winOpen(x, z, HubDims.WIN_SILL(), end)) {
						// sill under the window, hood of stone brick stairs over its head
						if (c.at(nx, y0 + pl, nz) == null) {
							c.set(nx, y0 + pl, nz, HubBlocks.stoneSlab(true));
						}
						if (c.at(nx, y0 + h - 2, nz) == null) {
							c.set(nx, y0 + h - 2, nz, HubBlocks.stoneStairs(back, true));
						}
					}
					if (!pier) {
						// the corbel course under the cornice: cherry stairs upside down (the dark course over it is HubExterior's)
						if (c.at(nx, y0 + h - 1, nz) == null) {
							c.set(nx, y0 + h - 1, nz, HubBlocks.stairsTop(Blocks.CHERRY_STAIRS, back));
						}
						if (end && c.at(nx, y0 + h, nz) == null) {
							c.set(nx, y0 + h, nz, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, back));
						}
					}
				}
			}
		}
	}

	/** A calcite pilaster one cell out from a pier: dark foot to the podium, a polished course, calcite shaft, chiselled capital, corbel. */
	private static void outerPilaster(CitadelCanvas c, int nx, int nz, Direction back) {
		int y0 = y0();
		int h = HubDims.WALL_H();
		int pl = HubDims.PLINTH();
		for (int y = 1; y < y0 + pl; y++) {
			c.set(nx, y, nz, HubBlocks.darkBase(nx, y, nz));
		}
		c.set(nx, y0 + pl, nz, RIB);
		c.set(nx, y0 + pl + 1, nz, RIB);
		for (int r = pl + 2; r <= h - 2; r++) {
			c.set(nx, y0 + r, nz, CALCITE);
		}
		c.set(nx, y0 + h - 1, nz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		c.set(nx, y0 + h, nz, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, back));
	}

	/**
	 * Dresses the inside of the walls (before the furniture): a pilaster against the wall at each corner of the nave's end walls, where the
	 * colonnade meets them, so the bays between piers read inside as they do outside. Only free cells.
	 */
	public static void dress(CitadelCanvas c) {
		if (!HubDims.TALL()) {
			return;
		}
		int z = HubDims.INNER();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int x = sx * HubDims.NAVE_HALF();
				if (c.zoneAt(x, sz * z).kind() == Kind.HUB && c.at(x, y0() + 1, sz * z) == null) {
					HubInterior.pier(c, x, sz * z);
				}
			}
		}
	}

	/** One column of the outer wall (the zone map says this column is {@code HUB_WALL}). */
	public static void wall(CitadelCanvas c, int x, int z) {
		boolean naveEnd = Math.abs(x) <= HubDims.NAVE_HALF() && Math.abs(z) >= HubDims.INNER();
		column(c, x, z, naveEnd);
		if (naveEnd) {
			gable(c, x, z);
		} else {
			parapet(c, x, z);
		}
		portals(c, x, z);
	}

	/** Footing (stone-brick course), panels, windows and piers up to the cornice. */
	static void column(CitadelCanvas c, int x, int z, boolean naveEnd) {
		if (HubDims.TALL()) {
			for (int r = 1; r <= HubDims.WALL_H(); r++) {
				c.set(x, y0() + r, z, tallCell(x, y0() + r, z, r, naveEnd));
			}
			return;
		}
		boolean pier = !naveEnd && pier(x, z);
		int u = bay(x, z);
		boolean win = !naveEnd && !pier && windowAllowed(x, z);
		for (int y = y0() + 1; y <= CitadelHub.aisleTop(); y++) {
			BlockState s;
			if (y <= y0() + 1) {
				s = HubBlocks.stoneBrick(x, y, z);
			} else if (y == CitadelHub.aisleTop()) {
				s = naveEnd ? RIB : CALCITE;
			} else if (pier) {
				s = y == y0() + 2 ? RIB : CALCITE;
			} else if (win && y >= y0() + CitadelHub.hy(3) && y <= y0() + CitadelHub.hy(5)) {
				s = HubBlocks.pane(bayColor(x, z));
			} else if (win && y == y0() + CitadelHub.hy(6) && u == HubDims.PIER_PERIOD() / 2) {
				s = HubBlocks.pane(bayColor(x, z));
			} else if (!naveEnd && y == y0() + 2 && Math.floorMod(x * 5 + z * 3, 6) == 0) {
				s = c.kit.wallPanel(true);
			} else {
				s = c.kit.wallPanel(false);
			}
			c.set(x, y, z, s);
		}
	}

	/** Whether a wall column has an open side (a neighbour outside the hub), i.e. is on the face and not behind it. */
	static boolean outer(CitadelCanvas c, int x, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Kind k = c.zoneAt(x + d.getStepX(), z + d.getStepZ()).kind();
			if (k != Kind.HUB && k != Kind.HUB_WALL && k != Kind.NONE) {
				return true;
			}
		}
		return false;
	}

	/** The cross gables of the transept stand on the east and west faces within the transept's width. */
	static boolean transept(int x, int z) {
		return Math.abs(x) == HubDims.FACE() && Math.abs(z) <= HubDims.TRANSEPT_HALF();
	}

	/**
	 * The parapet on the aisle walls, only on the outer column: merlons (calcite under a tile slab) on every
	 * other cell with a tile slab between, a taller calcite merlon with a light post on each pier and at
	 * the eight vertices. The inner column of a thick chamfer only gets a slab.
	 */
	static void parapet(CitadelCanvas c, int x, int z) {
		int top = CitadelHub.aisleTop() + 1;
		if (transept(x, z)) {
			transeptGable(c, x, z);
			return;
		}
		if (!outer(c, x, z)) {
			c.set(x, top, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		} else if (corner(x, z) || pier(x, z)) {
			c.set(x, top, z, CALCITE);
			c.set(x, top + 1, z, CALCITE);
			c.set(x, top + 2, z, corner(x, z) ? HubBlocks.stoneWall() : slab(Blocks.DEEPSLATE_TILE_SLAB));
		} else if (Math.floorMod(along(x, z), 2) == 0) {
			c.set(x, top, z, CALCITE);
			c.set(x, top + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		} else {
			c.set(x, top, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/** The end wall of a transept gable: calcite up to the roof line, a small window on the ridge, a dark coping. */
	static void transeptGable(CitadelCanvas c, int x, int z) {
		int hz = Math.abs(z);
		int base = HubDims.TERRACE_Y();
		int top = base + HubDims.TRANSEPT_HALF() - hz;
		for (int y = base; y <= top; y++) {
			int w = c.s(1);
			boolean window = hz <= w && y >= base + 1 && y <= top - 1 && !(hz == w && y > base + 2);
			c.set(x, y, z, window ? HubBlocks.glass(HubBlocks.GLASS_COLORS[Math.floorMod(x > 0 ? 0 : 1, 2)]) : CALCITE);
		}
		c.set(x, top + 1, z, hz == 0 ? slab(Blocks.DEEPSLATE_TILE_SLAB) : HubBlocks.darkStairs(z > 0 ? Direction.NORTH : Direction.SOUTH, false));
	}

	/** The eight vertices of the octagon: where a face meets the chamfer. */
	static boolean corner(int x, int z) {
		int ax = Math.abs(x);
		int az = Math.abs(z);
		return (ax == HubDims.FACE() && az == HubDims.FACE_HALF()) || (az == HubDims.FACE() && ax == HubDims.FACE_HALF());
	}

	/** Gable of the nave up to the vault: a stained rose window in it, a coping and a finial on top. */
	static void gable(CitadelCanvas c, int x, int z) {
		int top = CitadelHub.vaultY(Math.abs(x));
		for (int y = CitadelHub.aisleTop() + 1; y <= top; y++) {
			c.set(x, y, z, rose(x, y));
		}
		int ax = Math.abs(x);
		roseRim(c, x, z);
		c.set(x, top + 1, z, ax <= 1 ? slab(Blocks.DEEPSLATE_TILE_SLAB) : HubBlocks.darkStairs(x > 0 ? Direction.WEST : Direction.EAST, false));
		if (x == 0) {
			c.set(x, top + 1, z, CALCITE);
			c.set(x, top + 2, z, HubBlocks.stoneWall());
			c.set(x, top + 3, z, HubBlocks.rod());
		}
	}

	/** The rose window: a yellow eye, a pink ring, eight calcite spokes with blue and magenta petals, a stone-brick rim. */
	static BlockState rose(int x, int y) {
		double dy = y - CitadelHub.spring();
		double r = Math.hypot(x, dy);
		RingCampusGeometry.Dims d = CitadelCanvas.dims();
		if (HubDims.TALL()) {
			return traceryRose(x, dy, r, d.sd(1) / 2.0);
		}
		if (r > d.sd(3.7)) {
			return CALCITE;
		}
		if (r > d.sd(2.8)) {
			return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		}
		if (r <= d.sd(0.9)) {
			return HubBlocks.glass(DyeColor.YELLOW);
		}
		if (r <= d.sd(1.8)) {
			return HubBlocks.glass(DyeColor.PINK);
		}
		double a = Math.atan2(dy, x);
		double sector = a / (Math.PI / 4);
		if (Math.abs(sector - Math.rint(sector)) < 0.2) {
			return CALCITE;
		}
		return HubBlocks.glass(Math.floorMod((int) Math.floor(sector), 2) == 0 ? DyeColor.LIGHT_BLUE : DyeColor.MAGENTA);
	}

	/**
	 * The rose of the tall facade (struct/hub4), with tracery: a yellow eye, a calcite ring, eight petals (pink and orange) split by
	 * calcite spokes, a second ring and eight outer lights (blue and magenta) on the same spokes, inside the stone-brick rim.
	 * {@code f} is the scale of the original (1 at 200); the bars are one block thick whatever the radius.
	 */
	private static BlockState traceryRose(int x, double dy, double r, double f) {
		if (r > 7.4 * f) {
			return CALCITE;
		}
		if (r > 5.6 * f) {
			return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		}
		if (r <= 1.5 * f) {
			return HubBlocks.glass(DyeColor.YELLOW);
		}
		if (r <= 2.1 * f || (r > 3.5 * f && r <= 4.1 * f)) {
			return CALCITE;
		}
		boolean inner = r <= 3.5 * f;
		double sector = Math.atan2(dy, x) / (Math.PI / 4);
		double bar = r * Math.abs(sector - Math.rint(sector)) * (Math.PI / 4);
		if (bar < 0.5) {
			return CALCITE;
		}
		boolean even = Math.floorMod((int) Math.floor(sector), 2) == 0;
		return HubBlocks.glass(inner ? (even ? DyeColor.PINK : DyeColor.ORANGE) : (even ? DyeColor.LIGHT_BLUE : DyeColor.MAGENTA));
	}

	/**
	 * The rim of the rose window stands out one block from the gable (outside the wall).
	 */
	static void roseRim(CitadelCanvas c, int x, int z) {
		int sz = z > 0 ? 1 : -1;
		for (int y = CitadelHub.aisleTop() + 1; y <= CitadelHub.vaultY(Math.abs(x)); y++) {
			double r = Math.hypot(x, y - CitadelHub.spring());
			if (r > c.sd(2.8) && r <= c.sd(3.7)) {
				c.set(x, y, z + sz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			}
		}
	}

	/** Half width of the main portal: 2 (5 wide) at scale 1; from scale 2 it grows with the nave, {@code s(2) + 1} (11 wide at 200). */
	static int portalHalf() {
		int s = CitadelCanvas.dims().s(1);
		return s > 1 ? CitadelCanvas.dims().s(2) + 1 : 2;
	}

	/** Height of the main portal's opening: 5 at scale 1; from scale 2, {@code hy(6)} (10 at 200, under a nave 30 high). */
	static int portalHeight() {
		return CitadelCanvas.dims().s(1) > 1 ? CitadelHub.hy(6) : 5;
	}

	/**
	 * Main portal (south, a stepped arch: 5 x 5 at scale 1, 11 x 10 at 200, so it is no longer a small door under a nave 30
	 * high; struct/hub3) and the north door under the board (3 x 3), both framed in calcite. The width and height of the
	 * opening only change what is cut in the wall: the bridge and the anchors do not depend on them.
	 */
	static void portals(CitadelCanvas c, int x, int z) {
		int half = portalHalf();
		boolean layered = half >= 4 && c.zoneAt(x, z + 1).kind() != Kind.HUB_WALL; // the outer course of the wall
		if (z > 0 && Math.abs(x) <= half + (layered ? 2 : 1)) {
			if (layered) {
				portalOuter(c, x, z, half, portalHeight());
			} else {
				portalFrame(c, x, z, half, portalHeight());
			}
		}
		if (z < 0 && Math.abs(x) <= 2) {
			portalFrame(c, x, z, 1, 3);
		}
	}

	/**
	 * The outer course of a wide portal (struct/hub3): a reveal, so the opening reads in layers and not as a hole cut in
	 * a wall. The opening is one cell narrower here ({@code half - 1}) with its own stepped head; the cells of the inner
	 * opening's jamb that this narrows are polished blackstone (the dark reveal); two cells of calcite pilaster frame it
	 * outside, and a chiselled string course runs over the lintel.
	 */
	private static void portalOuter(CitadelCanvas c, int x, int z, int half, int h) {
		int ax = Math.abs(x);
		int oh = half - 1;
		if (ax <= oh) {
			int cut = ax == oh ? 2 : ax == oh - 1 ? 1 : 0;
			c.fill(x, y0() + 1, y0() + h - cut, z, CitadelCanvas.AIR);
			if (cut > 0) {
				c.fill(x, y0() + h - cut + 1, y0() + h, z, CALCITE);
			}
			c.set(x, y0() + h + 1, z, CALCITE);
		} else if (ax == half) {
			c.fill(x, y0() + 1, y0() + h - 1, z, RIB);
			c.fill(x, y0() + h, y0() + h + 1, z, CALCITE);
		} else {
			c.fill(x, y0() + 1, y0() + h + 1, z, CALCITE);
		}
		c.set(x, y0() + h + 2, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
	}

	/**
	 * Opens the cell if it is inside the opening ({@code half}, {@code h} tall) and trims the jambs and lintel. A wide
	 * opening ({@code half >= 4}) has a stepped head: two cells shorter at the jamb, one at the next cell in.
	 */
	private static void portalFrame(CitadelCanvas c, int x, int z, int half, int h) {
		int ax = Math.abs(x);
		if (ax <= half) {
			int cut = h < 5 ? 0 : half >= 4 ? (ax == half ? 2 : ax == half - 1 ? 1 : 0) : (ax == half ? 1 : 0);
			c.fill(x, y0() + 1, y0() + h - cut, z, CitadelCanvas.AIR);
			if (cut > 0) {
				c.fill(x, y0() + h - cut + 1, y0() + h, z, CALCITE);
			}
			c.set(x, y0() + h + 1, z, CALCITE);
		} else {
			c.fill(x, y0() + 1, y0() + h + 1, z, CALCITE);
		}
	}
}
