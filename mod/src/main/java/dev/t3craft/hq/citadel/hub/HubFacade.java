package dev.t3craft.hq.citadel.hub;

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
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

/**
 * The shell of the basilica as seen from outside and from the aisles: the outer wall (piers on the bay
 * lines, stained lancet windows between them, cornice), its parapet with merlons and pinnacles, the rose
 * window of the two gables with its coping, and the trim of both portals. Pure functions of the column;
 * nothing random (colours cycle by bay).
 *
 * <p>Bays: along a wall face the coordinate {@code t} (z on the east and west faces, x on the others)
 * repeats every 4 cells; {@code u = floorMod(t + 2, 4)}: {@code u == 0} is a pier (the same lines as the
 * nave's arcade piers), {@code u == 1..3} a bay, {@code u == 2} its middle.
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
		return Math.floorMod(along(x, z) + HubDims.PIER_PHASE, HubDims.PIER_PERIOD);
	}

	static boolean pier(int x, int z) {
		return bay(x, z) == 0;
	}

	/** The colour of the window of a bay, cycled by the bay and the quadrant. */
	static DyeColor bayColor(int x, int z) {
		int i = Math.floorDiv(along(x, z) + HubDims.PIER_PHASE, HubDims.PIER_PERIOD) + (x > 0 ? 1 : 0) + (z > 0 ? 2 : 0);
		return HubBlocks.GLASS_COLORS[Math.floorMod(i, HubBlocks.GLASS_COLORS.length)];
	}

	/** Whether an outer-wall window may open here: not behind the screens, thread wall nor board. */
	static boolean windowAllowed(int x, int z) {
		boolean ew = Math.abs(x) > Math.abs(z);
		return !(ew && Math.abs(z) <= HubDims.BLIND_HALF);
	}

	/** The dark base course of a wall column (the podium): y1 to the floor, a scatter of deepslate variants. */
	public static void base(CitadelCanvas c, int x, int z) {
		for (int y = 1; y <= Y0; y++) {
			c.set(x, y, z, HubBlocks.darkBase(x, y, z));
		}
	}

	/** Whether the wall column has a window (outer neighbours get a sill and a lintel course). */
	static boolean hasWindow(int x, int z) {
		boolean naveEnd = Math.abs(x) <= HubDims.NAVE_HALF && Math.abs(z) >= HubDims.INNER;
		return !naveEnd && !pier(x, z) && windowAllowed(x, z);
	}

	/** One column of the outer wall (the zone map says this column is {@code HUB_WALL}). */
	public static void wall(CitadelCanvas c, int x, int z) {
		boolean naveEnd = Math.abs(x) <= HubDims.NAVE_HALF && Math.abs(z) >= HubDims.INNER;
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
		for (int y = Y0 + 1; y <= CitadelHub.AISLE_TOP; y++) {
			BlockState s;
			if (y <= Y0 + 1) {
				s = HubBlocks.stoneBrick(x, y, z);
			} else if (y == CitadelHub.AISLE_TOP) {
				s = naveEnd ? RIB : CALCITE;
			} else if (pier) {
				s = y == Y0 + 2 ? RIB : CALCITE;
			} else if (win && y >= Y0 + 3 && y <= Y0 + 5) {
				s = HubBlocks.pane(bayColor(x, z));
			} else if (win && y == Y0 + 6 && u == 2) {
				s = HubBlocks.pane(bayColor(x, z));
			} else if (!naveEnd && y == Y0 + 2 && Math.floorMod(x * 5 + z * 3, 6) == 0) {
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
		return Math.abs(x) == HubDims.FACE && Math.abs(z) <= HubDims.TRANSEPT_HALF;
	}

	/**
	 * The parapet on the aisle walls, only on the outer column: merlons (calcite under a tile slab) on every
	 * other cell with a tile slab between, a taller calcite merlon with a light post on each pier and at
	 * the eight vertices. The inner column of a thick chamfer only gets a slab.
	 */
	static void parapet(CitadelCanvas c, int x, int z) {
		int top = CitadelHub.AISLE_TOP + 1;
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
		int base = HubDims.TERRACE_Y;
		int top = base + HubDims.TRANSEPT_HALF - hz;
		for (int y = base; y <= top; y++) {
			boolean window = hz <= 1 && y >= base + 1 && y <= top - 1 && !(hz == 1 && y > base + 2);
			c.set(x, y, z, window ? HubBlocks.glass(HubBlocks.GLASS_COLORS[Math.floorMod(x > 0 ? 0 : 1, 2)]) : CALCITE);
		}
		c.set(x, top + 1, z, hz == 0 ? slab(Blocks.DEEPSLATE_TILE_SLAB) : HubBlocks.darkStairs(z > 0 ? Direction.NORTH : Direction.SOUTH, false));
	}

	/** The eight vertices of the octagon: where a face meets the chamfer. */
	static boolean corner(int x, int z) {
		int ax = Math.abs(x);
		int az = Math.abs(z);
		return (ax == HubDims.FACE && az == HubDims.FACE_HALF) || (az == HubDims.FACE && ax == HubDims.FACE_HALF);
	}

	/** Gable of the nave up to the vault: a stained rose window in it, a coping and a finial on top. */
	static void gable(CitadelCanvas c, int x, int z) {
		int top = CitadelHub.vaultY(Math.abs(x));
		for (int y = CitadelHub.AISLE_TOP + 1; y <= top; y++) {
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
		double dy = y - CitadelHub.SPRING;
		double r = Math.hypot(x, dy);
		if (r > 3.7) {
			return CALCITE;
		}
		if (r > 2.8) {
			return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		}
		if (r <= 0.9) {
			return HubBlocks.glass(DyeColor.YELLOW);
		}
		if (r <= 1.8) {
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
		for (int y = CitadelHub.AISLE_TOP + 1; y <= CitadelHub.vaultY(Math.abs(x)); y++) {
			double r = Math.hypot(x, y - CitadelHub.SPRING);
			if (r > 2.8 && r <= 3.7) {
				c.set(x, y, z + sz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			}
		}
	}

	/** Main portal (south, 5 x 5, stepped arch) and the north door under the board (3 x 3), both framed in calcite. */
	static void portals(CitadelCanvas c, int x, int z) {
		if (z > 0 && Math.abs(x) <= 3) {
			portalFrame(c, x, z, 2, 5);
		}
		if (z < 0 && Math.abs(x) <= 2) {
			portalFrame(c, x, z, 1, 3);
		}
	}

	/** Opens the cell if it is inside the opening ({@code half}, {@code h} tall) and trims the jambs and lintel. */
	private static void portalFrame(CitadelCanvas c, int x, int z, int half, int h) {
		int ax = Math.abs(x);
		if (ax <= half) {
			boolean arch = ax == half && h >= 5;
			c.fill(x, Y0 + 1, Y0 + h - (arch ? 1 : 0), z, CitadelCanvas.AIR);
			if (arch) {
				c.set(x, Y0 + h, z, CALCITE);
			}
			c.set(x, Y0 + h + 1, z, CALCITE);
		} else {
			c.fill(x, Y0 + 1, Y0 + h + 1, z, CALCITE);
		}
	}
}
