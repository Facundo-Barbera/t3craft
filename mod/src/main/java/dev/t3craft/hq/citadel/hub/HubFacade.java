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

	/** Whether the wall column has a window (outer neighbours get a sill and a lintel course). */
	static boolean hasWindow(int x, int z) {
		boolean naveEnd = Math.abs(x) <= HubDims.NAVE_HALF() && Math.abs(z) >= HubDims.INNER();
		return !naveEnd && !pier(x, z) && windowAllowed(x, z);
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

	/** The rim of the rose window stands out one block from the gable (outside the wall). */
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
