package dev.t3craft.hq;

import static dev.t3craft.hq.HqGeometry.BOARD_HALF;
import static dev.t3craft.hq.HqGeometry.BOARD_Z;
import static dev.t3craft.hq.HqGeometry.DOOR_V1;
import static dev.t3craft.hq.HqGeometry.DOOR_V2;
import static dev.t3craft.hq.HqGeometry.HUB;
import static dev.t3craft.hq.HqGeometry.PORTICO_X;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z1;
import static dev.t3craft.hq.HqGeometry.PORTICO_Z2;
import static dev.t3craft.hq.HqGeometry.ROW;
import static dev.t3craft.hq.HqGeometry.SEGMENT;
import static dev.t3craft.hq.HqGeometry.SPINE;
import static dev.t3craft.hq.HqGeometry.WING_WALL;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The original HQ look ("classic", AgentCraft-inspired "Warm Studio"): copper-roofed timber hall
 * with a glazed lantern, glass-roofed spine, timber-and-pale-oak wings with skylights and copper
 * trims, on the redesigned layout (standing desks, archive terminal, reserved screens). Materials in
 * {@link Palette}.
 */
final class ClassicStyle extends HqStyle {
	static final String ID = "classic";

	private final Palette k = new Palette();
	private final Map<Role, BlockState> palette;

	ClassicStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.put(Role.FLOOR, k.woodFloor);
		m.put(Role.FOOTING, k.plinth);
		m.put(Role.WALL, k.infill);
		m.put(Role.COLUMN, k.post());
		m.put(Role.BEAM, k.beam(Direction.Axis.X));
		m.put(Role.ROOF, k.copperRoof(Direction.NORTH));
		m.put(Role.ROOF_TRIM, k.copperSlab);
		m.put(Role.RIDGE, k.copper);
		m.put(Role.WINDOW, k.pane);
		m.put(Role.DOOR, k.stoneFloor);
		m.put(Role.DECOR, k.bookshelf);
		m.put(Role.PATH, k.dirtPath);
		m.put(Role.GARDEN, k.grass);
		m.put(Role.GARDEN_TREE, k.floweringAzalea);
		m.put(Role.LIGHT, k.copperLantern(false));
		m.put(Role.DESK, k.deskTop);
		m.put(Role.BENCH, k.bench(Direction.SOUTH));
		m.put(Role.BOARD, k.boardFace);
		m.put(Role.COMPUTER_ON, k.monitorOn);
		m.put(Role.COMPUTER_OFF, k.monitorOff);
		m.put(Role.STATUS_LAMP, k.lamp());
		m.put(Role.SCREEN, k.boardFace);
		m.put(Role.BEACON, k.copper);
		m.put(Role.TERMINAL, k.archiveTerminal(Direction.NORTH));
		m.put(Role.PLAQUE, k.wallSign(Direction.SOUTH));
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	/** Warm timber and copper: shroomlight lamps in floors, walls and ceilings (fix/hq-lanterns). */
	@Override
	BlockState floorLight() {
		return net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState();
	}

	@Override
	BlockState wallLight() {
		return net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState();
	}

	// ------------------------------------------------------------------ hub

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		for (int z = -HUB; z <= HUB; z++) {
			for (int x = -HUB; x <= HUB; x++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				boolean door = Math.abs(x) <= 1 && (z == HUB || (northDoor && z == -HUB));
				// floor: tuff under the walls, andesite border, copper ring, spruce centre, copper rose
				BlockState floor = m == HUB ? k.plinth : m == 7 ? k.stoneFloor : m == 6 ? k.copper
						: Math.abs(x) <= 1 && Math.abs(z) <= 1 ? k.copperInlay : k.woodFloor;
				c.set(x, 0, z, door ? k.stoneFloor : floor);
				if (m == HUB) {
					hubWall(c, x, z, door);
				}
				// roof: two copper eave steps, flat copper terrace, glazed lantern over the light well
				if (m == HUB) {
					c.set(x, 6, z, k.copperRoof(inward(x, z)));
				} else if (m > 2) {
					c.set(x, 6, z, k.darkPlanks);
				}
				if (m == 7) {
					c.set(x, 7, z, k.copperRoof(inward(x, z)));
				} else if (m >= 3 && m <= 6) {
					c.set(x, 7, z, k.copperSlab);
				} else if (m == 2) {
					BlockState lw = Math.abs(x) == 2 && Math.abs(z) == 2 ? k.copper : k.glass;
					c.set(x, 7, z, lw);
					c.set(x, 8, z, lw);
				}
				if (m <= 2) {
					c.set(x, 9, z, x == 0 && z == 0 ? k.copper : k.copperSlab);
				}
			}
		}
		c.decor(0, 10, 0, k.lightningRod);
		// chandelier hanging from the lantern cap into the light well
		c.hang(0, 8, 0, k.chain);
		c.hang(0, 7, 0, k.chain);
		c.hang(0, 6, 0, k.copperLantern(true));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				c.hang(5 * sx, 5, 5 * sz, k.lantern(true));
				// reading corners
				c.set(7 * sx, 1, 7 * sz, k.bookshelf);
				c.set(7 * sx, 2, 7 * sz, k.bookshelf);
				c.decor(7 * sx, 3, 7 * sz, k.fern);
			}
			// planters at the ends of the bench rows
			c.decor(5 * sx, 1, -3, k.pottedFloweringAzalea);
			c.decor(5 * sx, 1, 3, k.pottedFloweringAzalea);
			// lamp pedestals by the north doorway (the portico's lamp posts already frame the entrance)
			c.set(2 * sx, 1, -7, k.copperGrate);
			c.decor(2 * sx, 2, -7, k.copperLantern(false));
		}
		// runner from the entrance to the board
		for (int z = 2; z <= 7; z++) {
			for (int x = -1; x <= 1; x++) {
				c.decor(x, 1, z, k.carpet(DyeColor.CYAN));
			}
		}
	}

	private void hubWall(PlanBuilder.Canvas c, int x, int z, boolean door) {
		boolean ns = Math.abs(z) == HUB;
		int t = ns ? x : z;
		boolean post = Math.abs(t) == HUB || Math.abs(t) == 5 || Math.abs(t) == 2;
		Direction.Axis along = ns ? Direction.Axis.X : Direction.Axis.Z;
		for (int y = 1; y <= 5; y++) {
			BlockState s;
			if (post) {
				s = k.post();
			} else if (y == 5) {
				s = k.beam(along);
			} else if (door) {
				s = y == 4 ? k.beam(along) : k.air;
			} else {
				s = y == 1 ? k.plinth : y == 4 ? k.infill : k.pane;
			}
			c.set(x, y, z, s);
		}
	}

	/** The "needs attention" board: a dark slate panel framed in timber with copper signal bulbs. */
	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		int z = BOARD_Z;
		for (int y = 1; y <= 5; y++) {
			c.set(-BOARD_HALF - 1, y, z, k.post());
			c.set(BOARD_HALF + 1, y, z, k.post());
		}
		for (int x = -BOARD_HALF; x <= BOARD_HALF; x++) {
			c.set(x, 1, z, k.beam(Direction.Axis.X));
			for (int y = 2; y <= 4; y++) {
				c.set(x, y, z, k.boardFace);
			}
			c.set(x, 5, z, k.copper); // bulbs overwritten by the generator
		}
		c.decor(new BlockPos(0, 5, z + 1), k.wallSign(Direction.SOUTH), sign.signTag());
	}

	/** Direction from an edge cell towards the centre (the tall side of a roof step). */
	static Direction inward(int x, int z) {
		if (z == -Math.max(Math.abs(x), Math.abs(z))) {
			return Direction.SOUTH;
		}
		if (z == Math.max(Math.abs(x), Math.abs(z))) {
			return Direction.NORTH;
		}
		return x < 0 ? Direction.EAST : Direction.WEST;
	}

	// ------------------------------------------------------------------ archive

	/** The archive nook: bookshelves either side of the terminal, a sign above it. */
	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		Box n = HqGeometry.archiveNook();
		for (int x = n.minX(); x <= n.maxX(); x++) {
			for (int y = 1; y <= 4; y++) {
				c.set(x, y, n.minZ(), x == HqGeometry.ARCHIVE_TERMINAL.getX() ? k.air : y == 4 ? k.beam(Direction.Axis.X) : k.bookshelf);
			}
		}
		c.decor(HqGeometry.ARCHIVE_TERMINAL.above(2), k.wallSign(Direction.NORTH), SignTags.sign(DyeColor.LIGHT_GRAY, true, "", "Archive", "", ""));
	}

	@Override
	BlockState bench(Direction back) {
		return k.bench(back);
	}

	// ------------------------------------------------------------------ portico

	@Override
	void portico(PlanBuilder.Canvas c) {
		for (int z = PORTICO_Z1; z <= PORTICO_Z2; z++) {
			for (int x = -PORTICO_X; x <= PORTICO_X; x++) {
				boolean deck = Math.abs(x) <= 3 && z <= PORTICO_Z2 - 1;
				BlockState g = deck ? (Math.abs(x) == 3 ? k.plinth : k.stoneFloor) : Math.abs(x) <= 1 ? k.dirtPath : k.grass;
				c.set(x, 0, z, g);
				c.set(x, 5, z, Math.abs(x) == PORTICO_X || z == PORTICO_Z2 ? k.copperSlab : k.copper);
				if (Math.abs(x) <= 2 && z <= PORTICO_Z2 - 2) {
					c.set(x, 6, z, k.copperSlab);
				}
			}
		}
		int front = PORTICO_Z2 - 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int y = 1; y <= 4; y++) {
				c.set(3 * sx, y, front, k.post());
				c.set(3 * sx, y, PORTICO_Z1, k.post());
			}
			for (int z = PORTICO_Z1 + 1; z < front; z++) {
				c.set(3 * sx, 4, z, k.beam(Direction.Axis.Z));
			}
			c.decor(4 * sx, 1, PORTICO_Z1 + 1, k.floweringAzalea);
			c.decor(4 * sx, 1, front, k.azalea);
			c.set(2 * sx, 1, PORTICO_Z2, k.plinthWall);
			c.decor(2 * sx, 2, PORTICO_Z2, k.copperLantern(false));
		}
		for (int x = -2; x <= 2; x++) {
			c.set(x, 4, front, k.beam(Direction.Axis.X));
			c.set(x, 4, PORTICO_Z1, k.beam(Direction.Axis.X));
		}
		c.hang(0, 4, PORTICO_Z1 + 2, k.copperLantern(true));
	}

	// ------------------------------------------------------------------ spine

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		int zn = HqGeometry.rowNorth(r);
		for (int v = 0; v < ROW; v++) {
			int z = zn + v;
			boolean beamLine = v == 0 || v == 4 || v == 8 || v == ROW - 1;
			for (int x = -SPINE; x <= SPINE; x++) {
				int ax = Math.abs(x);
				boolean wing = (x > 0 ? eastSign : westSign) != null;
				boolean door = ax == SPINE && wing && v >= DOOR_V1 && v <= DOOR_V2;
				BlockState floor = ax == SPINE ? (door ? k.woodFloor : k.plinth) : ax == 2 ? k.darkPlanks
						: x == 0 && v % 4 == 2 ? k.copperInlay : k.woodFloor;
				c.set(x, 0, z, floor);
				if (ax == SPINE) {
					for (int y = 1; y <= 4; y++) {
						BlockState s;
						if (beamLine) {
							s = k.post();
						} else if (y == 4) {
							s = k.beam(Direction.Axis.Z);
						} else if (door) {
							s = k.air;
						} else {
							s = y == 1 ? k.plinth : wing ? k.infill : k.pane;
						}
						c.set(x, y, z, s);
					}
					c.set(x, 5, z, k.darkPlanks);
					c.set(x, 6, z, k.copperSlab);
				} else {
					c.set(x, 5, z, beamLine ? k.beam(Direction.Axis.X) : k.glass);
				}
			}
			if (v == 4 || v == 8) {
				c.hang(0, 4, z, k.copperLantern(true));
			}
			if (v == 1 || v == ROW - 2) {
				c.decor(-2, 1, z, k.fern);
				c.decor(2, 1, z, k.fern);
			}
		}
		int zs = zn + (DOOR_V1 + DOOR_V2) / 2;
		if (eastSign != null) {
			c.decor(new BlockPos(2, 4, zs), k.wallSign(Direction.WEST), eastSign.signTag());
		}
		if (westSign != null) {
			c.decor(new BlockPos(-2, 4, zs), k.wallSign(Direction.EAST), westSign.signTag());
		}
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		int z = box.minZ();
		for (int x = -SPINE; x <= SPINE; x++) {
			int ax = Math.abs(x);
			c.set(x, 0, z, k.plinth);
			for (int y = 1; y <= 4; y++) {
				c.set(x, y, z, ax == SPINE ? k.post() : y == 4 ? k.beam(Direction.Axis.X) : y == 1 ? k.plinth : ax <= 1 ? k.pane : k.infill);
			}
			c.set(x, 5, z, k.darkPlanks);
			c.set(x, 6, z, k.copperSlab);
		}
	}

	// ------------------------------------------------------------------ wings

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		int x = HqGeometry.wx(i, u);
		for (int v = 0; v < ROW; v++) {
			int z = HqGeometry.wz(i, v);
			if (v == ROW - 1) {
				c.set(x, 0, z, k.grass);
				continue;
			}
			boolean door = inner && v >= DOOR_V1 && v <= DOOR_V2;
			c.set(x, 0, z, door ? k.woodFloor : k.plinth);
			boolean post = inner ? v == 0 || v == 4 || v == 8 || v == WING_WALL : v == 0 || v == 6 || v == WING_WALL;
			boolean window = !inner && ((v >= 2 && v <= 4) || (v >= 8 && v <= 10));
			for (int y = 1; y <= 4; y++) {
				BlockState s = post ? k.post() : y == 4 ? k.beam(Direction.Axis.Z) : door ? k.air : y == 1 ? k.plinth : window ? k.pane : k.infill;
				c.set(x, y, z, s);
			}
			c.set(x, 5, z, k.darkPlanks);
			c.set(x, 6, z, k.copperSlab);
		}
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		int u0 = HqGeometry.segmentU(s);
		for (int col = 0; col < SEGMENT; col++) {
			int x = HqGeometry.wx(i, u0 + col);
			boolean pillar = col == SEGMENT - 1;
			for (int v = 0; v < ROW; v++) {
				int z = HqGeometry.wz(i, v);
				if (v == ROW - 1) {
					// garden strip between rows: grass, a bush mid-segment, a lamp post at the pillar
					c.set(x, 0, z, k.grass);
					if (pillar) {
						// a lamp post at every other pillar, between the hall's lanterns
						if (s % 2 == 1) {
							c.set(x, 1, z, k.plinthWall);
							c.decor(x, 2, z, k.copperLantern(false));
						}
					} else {
						c.decor(x, 1, z, col == 1 ? (s % 2 == 0 ? k.floweringAzalea : k.azalea) : k.shortGrass);
					}
					continue;
				}
				boolean wall = v == 0 || v == WING_WALL;
				if (wall) {
					c.set(x, 0, z, k.plinth);
					for (int y = 1; y <= 4; y++) {
						// full glass at y = 3, not panes: a pane would change shape with the desk's 3×2 screen in front of it
						c.set(x, y, z, pillar ? k.post() : y == 1 ? k.plinth : y == 2 ? k.infill : y == 3 ? k.glass : k.beam(Direction.Axis.X));
					}
					c.set(x, 5, z, k.darkPlanks);
					c.set(x, 6, z, k.copperSlab);
					continue;
				}
				if (c.owns(x, 0, z)) {
					c.set(x, 0, z, pillar ? k.darkPlanks : k.woodFloor);
				}
				boolean sky = v >= 4 && v <= 8;
				c.set(x, 5, z, pillar ? k.beam(Direction.Axis.Z) : sky ? k.glass : k.infill);
				if (!sky || pillar) {
					c.set(x, 6, z, k.copperSlab);
				}
				if (pillar && (v == 1 || v == WING_WALL - 1)) {
					c.set(x, 1, z, k.bookshelf);
					c.decor(x, 2, z, k.pottedAzalea);
				}
				// one lantern down the hall's axis at every pillar (the floor lamps do the lighting)
				if (pillar && v == WING_WALL / 2) {
					c.hang(x, 4, z, k.copperLantern(true));
				}
				if (v >= DOOR_V1 && v <= DOOR_V2) {
					c.decor(x, 1, z, k.carpet(accent));
				}
			}
		}
	}

	/** Desk row against the wall (drawer barrel, two desk tops; the 3×2 screen goes on top); the agent stands behind it. */
	@Override
	void desk(PlanBuilder.Canvas c, DeskCells d) {
		BlockPos desk = d.computer().below();
		BlockPos right = d.right();
		Direction side = d.facing().getClockWise();
		for (int dx = -1; dx <= 1; dx++) {
			c.set(desk.below().relative(side, dx), k.woodFloor);
			c.set(d.stand().below().relative(side, dx), k.woodFloor);
		}
		if (!d.occupied()) {
			return;
		}
		c.set(d.drawer(), k.barrel(d.facing().getOpposite()));
		c.set(desk, k.deskTop);
		c.set(right, k.deskTop);
	}
}
