package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import dev.t3craft.hq.citadel.CitadelNodes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The scheduled-tasks rotunda: a round drum of radius 7 under a banded dome with a glass cupola.
 *
 * <ul>
 * <li><b>Floor</b>: a clock face: 12 hour marks at d = 5 (lights at 12, 3, 6 and 9), hands at ten past ten
 *     and an accent ring at d = 4.</li>
 * <li><b>Drum</b> (d &gt; 6, 10 high): dark base (a cracked one in six), a calcite body, cherry-log pilasters on the
 *     8 rays of 45 degrees, tall glass windows between them (y 4..8), a lintel course, a lit band at the
 *     top, and doors 3 wide on the four sides.</li>
 * <li><b>Dome</b> (radius 7.2 over the drum): a dark skirt of deepslate tiles, cherry planks, a golden cap of
 *     bamboo mosaic; wherever the ring inside is exactly one higher the top block is a stair, so the dome is
 *     smooth; four lanterns hang from it on the diagonals.</li>
 * <li><b>Cupola</b> on top: 3 × 3 of glass between four logs, a light inside it, a stair roof, a gold block and
 *     a rod.</li>
 * </ul>
 */
public final class RotundaRoom {
	/** Radius inside which the hall is open (the wall is further out). */
	private static final double WALL_R = 6.0;
	/** Top of the drum (a lit band). */
	private static final int WALL_TOP = 10;
	/** Radius of the dome sphere. */
	private static final double DOME_R = 7.2;
	/** The dome is stretched in height by this factor (a taller, more pointed dome). */
	private static final double STRETCH = 1.2;
	/** Height of the dome above the drum at the centre. */
	private static final int DOME_H = (int) Math.round(STRETCH * DOME_R);

	private static int domeTop(double d) {
		return WALL_TOP + (int) Math.round(STRETCH * Math.sqrt(Math.max(0, DOME_R * DOME_R - d * d)));
	}

	private RotundaRoom() {
	}

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}, {@code d} from the centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		floor(c, x, z, dx, dz, d);
		wall(c, x, z, dx, dz, d);
		dome(c, x, z, dx, dz, d);
	}

	/** The floor: a clock face. */
	public static void floor(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		BlockState s = Math.abs(d - 4) < 0.5 ? c.accent() : c.floor();
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			if (dx == (int) Math.round(5 * Math.sin(a)) && dz == (int) Math.round(-5 * Math.cos(a))) {
				s = h % 3 == 0 ? LIGHT : RIB;
			}
		}
		for (int r = 1; r <= 3; r++) {
			double hour = 5 * Math.PI / 3;
			double minute = Math.PI / 3;
			boolean h1 = r <= 2 && dx == (int) Math.round(r * Math.sin(hour)) && dz == (int) Math.round(-r * Math.cos(hour));
			boolean m1 = dx == (int) Math.round(r * Math.sin(minute)) && dz == (int) Math.round(-r * Math.cos(minute));
			if (h1 || m1) {
				s = RIB;
			}
		}
		c.set(x, 0, z, s);
	}

	/** The drum. */
	public static void wall(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		if (d <= WALL_R || d > WALL_R + 1.0) {
			return; // inside the hall, or the apron round the drum when the citadel is scaled (the zone grows, the room does not)
		}
		double off = NodeDecor.rayOffset(dx, dz);
		for (int y = 1; y <= WALL_TOP; y++) {
			BlockState s;
			if (y == WALL_TOP) {
				s = c.kit.wallPanel(true);
			} else if (off < 0.55) {
				s = y <= 2 ? RIB : LOG;
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 3) {
				s = CALCITE;
			} else if (y <= WALL_TOP - 2 && off >= 1.3) {
				s = GLASS;
			} else if (y == WALL_TOP - 1) {
				s = RIB;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		if (CitadelNodes.door(dx, dz, 6)) {
			c.fill(x, 1, 4, z, AIR);
		}
	}

	private static BlockState shell(int y, net.minecraft.core.Direction inward, boolean stair) {
		Block full;
		Block stairs;
		if (y <= WALL_TOP + 2) {
			full = Blocks.DEEPSLATE_TILES;
			stairs = Blocks.DEEPSLATE_TILE_STAIRS;
		} else if (y >= WALL_TOP + DOME_H - 2) {
			full = Blocks.BAMBOO_MOSAIC;
			stairs = Blocks.BAMBOO_MOSAIC_STAIRS;
		} else {
			full = Blocks.CHERRY_PLANKS;
			stairs = Blocks.CHERRY_STAIRS;
		}
		return stair ? stairs(stairs, inward) : full.defaultBlockState();
	}

	/** The dome and its cupola. */
	public static void dome(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int top = domeTop(d);
		// the shell starts at the top of the ring outside it, so the rings touch face to face
		int low = domeTop(Math.min(DOME_R, d + 1));
		int lo = Math.max(WALL_TOP + 1, Math.min(low, top));
		int topIn = domeTop(Math.max(0, d - 1));
		net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
		for (int y = lo; y <= top; y++) {
			boolean ramp = y == top && d >= 1 && topIn == top + 1;
			c.set(x, y, z, d < 1 ? GLASS : shell(y, in, ramp));
		}
		// the cupola: 3 x 3 of glass between four logs, a light inside, a roof, a gold block, a rod
		if (d < 1.5) {
			boolean center = dx == 0 && dz == 0;
			boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
			for (int y = top + 1; y <= top + 3; y++) {
				if (center) {
					if (y == top + 2) {
						c.set(x, y, z, LIGHT);
					}
				} else {
					c.set(x, y, z, corner ? LOG : GLASS);
				}
			}
			c.set(x, top + 4, z, center ? BAMBOO : stairs(Blocks.CHERRY_STAIRS, in));
			if (center) {
				c.set(x, top + 5, z, NodeDecor.ROD);
			}
		}
		// a lantern hangs from the dome on each diagonal at d ~ 4.5
		if (NodeDecor.rayOffset(dx, dz) < 0.55 && d >= 4.2 && d <= 4.9) {
			c.put(x, lo - 1, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), HANGING);
		}
	}

	// =====================================================================================================
	// The scaled rotunda (scale > 1): painted directly at its real size, not magnified from the design size.
	//
	// Plan (R = the zone's radius, 14 at scale 2; everything is derived from it):
	//   hall      radius R - 2.5 (11.5), open floor, centre = planning table over a floor clock (dial of
	//             calcite, accent ring, 12 hour marks, quarter marks lit, hands at ten o'clock)
	//   drum      d in (R - 2.5, R - 1], height h = sy(10) (14): deepslate base, calcite string course, 16 cherry
	//             pilasters, tall windows with blackstone mullions, lintel, lit band; doors 5 wide, 5 high on
	//             N, S and W (the street runs N-S through the node; the east side is the display wall)
	//   plinth    d in (R - 1, R]: a slab ring with a buttress at every pilaster
	//   dome      glass shell on 16 cherry ribs, a dark skirt, a cherry belt, a bamboo cap; an open oculus
	//             under the lantern (a round glass lantern drum with a roof, a gold ball and a rod)
	//   display   the east side of the hall is cut flat at x = xw: a wall of archive shelves with the
	//             "schedule" screen (7 x 3, y 2..4) framed by piers, a lintel and a cornice, facing west
	//   seating   two rows of benches facing the screen, a 3-wide aisle between them; chests and lecterns
	//             under every other window, benches under the rest; eight chandeliers hang from the ribs
	// =====================================================================================================

	/** Dimensions of the scaled rotunda, all from the zone's radius. */
	private static final BlockState SLOG = b(Blocks.STRIPPED_CHERRY_LOG);

	private record Geo(double size, double ri, double rw, int h, int dh, int xw, double lr, double dial) {
		static Geo of(CitadelCanvas c, Node nd) {
			double size = nd.size();
			double ri = size - 2.5;
			double rw = size - 1.0;
			int h = c.sy(10);
			int dh = (int) Math.round(1.15 * rw);
			int xw = (int) Math.floor(ri) - 2;
			double lr = size >= 12 ? 2.5 : 1.5;
			return new Geo(size, ri, rw, h, dh, xw, lr, Math.round(ri * 0.62));
		}

		/** x of the screen plane (one in front of the display wall). */
		int xs() {
			return xw - 1;
		}

		int doorH() {
			return Math.min(5, h - 4);
		}

		/** Top of the dome shell at distance {@code d} from the centre. */
		int top(double d) {
			double q = Math.max(0, 1 - (d / rw) * (d / rw));
			return h + (int) Math.round(dh * Math.sqrt(q));
		}
	}

	/** Arc distance (in cells) from the cell to the nearest of {@code n} rays; {@code phase} shifts the rays by that many steps. */
	private static double arc(int dx, int dz, int n, double phase) {
		double d = Math.hypot(dx, dz);
		if (d < 0.5) {
			return 99;
		}
		double step = 2 * Math.PI / n;
		double a = Math.atan2(dz, dx) / step - phase;
		return Math.abs(a - Math.rint(a)) * step * d;
	}

	private static boolean in(CitadelCanvas c, Node nd, int dx, int dz) {
		Zone zn = c.zoneAt(nd.cx() + dx, nd.cz() + dz);
		return zn.kind() == Kind.NODE && zn.node() == nd.k();
	}

	/** A doorway: 5 wide on the north, south and west axes, through the drum and the plinth. */
	private static boolean door(Geo g, int dx, int dz) {
		int a = Math.min(Math.abs(dx), Math.abs(dz));
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		return a <= 2 && m >= (int) Math.floor(g.ri) - 1 && !(dx > 0 && Math.abs(dz) <= 2);
	}

	/** The pier beside a doorway. */
	private static boolean jamb(Geo g, int dx, int dz) {
		int a = Math.min(Math.abs(dx), Math.abs(dz));
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		return a == 3 && m >= (int) Math.floor(g.ri) - 1 && !(dx > 0);
	}

	/** Paints the whole scaled rotunda: every column of the node, then the furniture. Fixtures go after (see {@link #fixtures}). */
	public static void build(CitadelCanvas c, Node nd) {
		Geo g = Geo.of(c, nd);
		int span = (int) Math.ceil(g.size) + 1;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				if (!in(c, nd, dx, dz)) {
					continue;
				}
				int x = nd.cx() + dx;
				int z = nd.cz() + dz;
				double d = Math.hypot(dx, dz);
				dialFloor(c, g, x, z, dx, dz, d);
				drum(c, g, x, z, dx, dz, d);
				displayWall(c, g, x, z, dx, dz, d);
				glassDome(c, g, x, z, dx, dz, d);
			}
		}
		furnishHall(c, g, nd);
	}

	/** The fixtures of the scaled rotunda: the schedule screen (7 x 3 at reading height, with its base) and a walk spot. */
	public static void fixtures(CitadelCanvas c, Node nd) {
		Geo g = Geo.of(c, nd);
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new dev.t3craft.hq.HqGeometry.Surface(new net.minecraft.core.BlockPos(cx + g.xs(), 2, cz - 3),
				net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, 7, 3), "schedule", "", true);
		c.walkSpot(new net.minecraft.core.BlockPos(cx - 3, 1, cz), net.minecraft.core.Direction.EAST);
	}

	// ------------------------------------------------------------------ floor: the clock

	private static void dialFloor(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		BlockState s = c.floor();
		if (d > g.ri) {
			s = door(g, dx, dz) ? c.floor() : CitadelCanvas.FOOT;
		} else if (d <= g.dial + 0.5) {
			s = d >= g.dial - 0.5 ? c.accent() : CALCITE;
			double mr = g.dial - 1.5;
			for (int hr = 0; hr < 12; hr++) {
				double a = hr * Math.PI / 6;
				int mx = (int) Math.round(mr * Math.sin(a));
				int mz = (int) Math.round(-mr * Math.cos(a));
				if (dx == mx && dz == mz) {
					s = hr % 3 == 0 ? LIGHT : RIB;
				} else if (hr % 3 == 0 && Math.abs(dx - mx) + Math.abs(dz - mz) == 1 && d < g.dial - 0.5) {
					s = BAMBOO;
				}
			}
			if (hand(dx, dz, 0, g.dial - 1, 0.6) || hand(dx, dz, 5 * Math.PI / 3, g.dial - 3, 0.9)) {
				s = RIB;
			}
		} else if (d <= g.dial + 1.5) {
			s = c.accent();
		}
		c.set(x, 0, z, s);
	}

	/** A clock hand from the centre along the clockwise angle {@code a} from north, {@code len} long, {@code thick} wide. */
	private static boolean hand(int dx, int dz, double a, double len, double thick) {
		double ux = Math.sin(a);
		double uz = -Math.cos(a);
		double t = dx * ux + dz * uz;
		double perp = Math.abs(dx * uz - dz * ux);
		return t >= 1.5 && t <= len && perp <= thick;
	}

	// ------------------------------------------------------------------ the drum and its plinth

	private static void drum(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d <= g.ri) {
			return;
		}
		boolean door = door(g, dx, dz);
		double pil = arc(dx, dz, 16, 0);
		if (d > g.rw) {
			// plinth: a slab ring, a buttress where a pilaster meets the ground
			if (!door) {
				if (pil < 0.7) {
					c.set(x, 1, z, CitadelCanvas.FOOT);
					c.set(x, 2, z, CitadelCanvas.FOOT);
					c.set(x, 3, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, NodeDecor.inward(dx, dz)));
				} else {
					c.set(x, 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
			return;
		}
		boolean jamb = jamb(g, dx, dz);
		boolean mullion = arc(dx, dz, 16, 0.5) < 0.5;
		for (int y = 1; y <= g.h; y++) {
			BlockState s;
			if (door && y <= g.doorH()) {
				c.set(x, y, z, AIR);
				continue;
			}
			if (y == g.h) {
				s = c.kit.wallPanel(true);
			} else if (jamb || pil < 0.55) {
				s = y <= 2 ? RIB : SLOG;
			} else if (y <= 3) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 4) {
				s = CALCITE;
			} else if (y <= g.h - 3 && pil >= 1.3) {
				s = mullion ? RIB : GLASS;
			} else if (y == g.h - 2) {
				s = RIB;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ the flat east wall: shelves behind the screen

	private static void displayWall(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d > g.ri || dx < g.xw) {
			return;
		}
		for (int y = 1; y <= g.h; y++) {
			BlockState s;
			if (y == g.h) {
				s = c.kit.wallPanel(true);
			} else if (dx > g.xw) {
				s = CitadelCanvas.FOOT;
			} else if (y <= 3) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y <= 6) {
				s = CALCITE;
			} else if (y == g.h - 2) {
				s = RIB;
			} else if (y == g.h - 1) {
				s = CALCITE;
			} else if (Math.floorMod(dz, 3) == 0) {
				s = SLOG;
			} else if (y % 2 == 1) {
				s = CitadelCanvas.b(Blocks.BOOKSHELF);
			} else {
				s = NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, net.minecraft.core.Direction.WEST);
			}
			c.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ the glass dome and its lantern

	private static void glassDome(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d > g.rw) {
			return;
		}
		int top = g.top(d);
		int low = g.top(Math.min(g.rw, d + 1));
		int lo = Math.max(g.h + 1, Math.min(low, top));
		if (d >= g.lr - 0.5) {
			for (int y = lo; y <= top; y++) {
				int rel = y - g.h;
				BlockState s;
				if (rel <= 2) {
					s = CitadelCanvas.FOOT;
				} else if (rel >= g.dh - 1 && d < g.lr + 2.0) {
					s = BAMBOO;
				} else if (arc(dx, dz, 8, 0) < 0.6 && d >= g.lr + 1.5) {
					s = SLOG;
				} else if (rel == g.dh / 2) {
					s = CitadelCanvas.PLANKS;
				} else {
					s = GLASS;
				}
				c.set(x, y, z, s);
			}
		}
		if (d <= g.lr) {
			int base = g.top(g.lr);
			if (d > g.lr - 1.0) {
				for (int y = base + 1; y <= base + 5; y++) {
					c.set(x, y, z, arc(dx, dz, 8, 0) < 0.6 ? SLOG : GLASS);
				}
			}
			if (dx == 0 && dz == 0) {
				c.set(x, base + 3, z, LIGHT);
			}
			net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
			c.set(x, base + 6, z, d > 1.5 ? stairs(Blocks.CHERRY_STAIRS, in) : BAMBOO);
			if (d <= 1.5) {
				c.set(x, base + 7, z, d > 0.5 ? stairs(Blocks.BAMBOO_MOSAIC_STAIRS, in) : b(Blocks.GOLD_BLOCK));
			}
			if (dx == 0 && dz == 0) {
				c.set(x, base + 8, z, NodeDecor.ROD);
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static void furnishHall(CitadelCanvas c, Geo g, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		BlockState lanternUp = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
		// the planning table in the middle of the clock: a cartography table, an atril on each side, slabs at the corners
		put(c, nd, 0, 1, 0, b(Blocks.CARTOGRAPHY_TABLE), CitadelCanvas.SOLID);
		for (net.minecraft.core.Direction f : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			put(c, nd, f.getStepX(), 1, f.getStepZ(), NodeDecor.facing(Blocks.LECTERN, f).setValue(BlockStateProperties.HAS_BOOK, true), CitadelCanvas.SOLID);
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				put(c, nd, sx, 1, sz, slab(Blocks.CHERRY_SLAB), CitadelCanvas.SOLID);
			}
		}
		// two rows of benches facing the screen, a 3-wide aisle between them
		for (int row : new int[] {g.xw - 5, g.xw - 3}) {
			for (int dz = 2; dz <= 5; dz++) {
				put(c, nd, row, 1, dz, stairs(Blocks.CHERRY_STAIRS, net.minecraft.core.Direction.WEST), CitadelCanvas.SOLID);
				put(c, nd, row, 1, -dz, stairs(Blocks.CHERRY_STAIRS, net.minecraft.core.Direction.WEST), CitadelCanvas.SOLID);
			}
		}
		// round the drum, one module under each window: benches, or a chest - atril - chest station
		int span = (int) Math.ceil(g.ri);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > g.ri || d <= g.ri - 1.0 || Math.min(Math.abs(dx), Math.abs(dz)) <= 3 || dx >= g.xw - 2) {
					continue;
				}
				int sector = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) / (Math.PI / 8)), 16);
				double mid = arc(dx, dz, 16, 0.5);
				if (sector % 2 == 0) {
					put(c, nd, dx, 1, dz, stairs(Blocks.CHERRY_STAIRS, NodeDecor.outward(dx, dz)), CitadelCanvas.SOLID);
				} else if (mid < 0.7) {
					put(c, nd, dx, 1, dz, NodeDecor.facing(Blocks.LECTERN, NodeDecor.inward(dx, dz)).setValue(BlockStateProperties.HAS_BOOK, true), CitadelCanvas.SOLID);
				} else if (mid < 1.7) {
					put(c, nd, dx, 1, dz, NodeDecor.facing(Blocks.CHEST, NodeDecor.inward(dx, dz)), CitadelCanvas.SOLID);
				}
			}
		}
		// the frame of the screen (plane xs): piers, a lintel, a cornice, lanterns on top
		int xs = g.xs();
		for (int dz = -5; dz <= 5; dz++) {
			int az = Math.abs(dz);
			for (int y = 1; y <= 6; y++) {
				if (az <= 3 && y >= 1 && y <= 4) {
					continue; // the screen and its base
				}
				BlockState s;
				if (y == 6) {
					s = NodeDecor.topSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
				} else if (y == 1) {
					s = CitadelCanvas.FOOT;
				} else if (y == 5) {
					s = az <= 3 ? CitadelCanvas.PLANKS : az == 4 ? SLOG : RIB;
				} else {
					s = az == 4 ? SLOG : RIB;
				}
				put(c, nd, xs, y, dz, s, CitadelCanvas.SOLID);
			}
		}
		// a presenter's atril at each side of the screen, a planter beyond it
		for (int sz : new int[] {-1, 1}) {
			put(c, nd, xs - 1, 1, 4 * sz, NodeDecor.facing(Blocks.LECTERN, net.minecraft.core.Direction.WEST).setValue(BlockStateProperties.HAS_BOOK, true), CitadelCanvas.SOLID);
			put(c, nd, xs - 1, 1, 7 * sz, c.kit.gardenPlanter(), CitadelCanvas.SOLID);
		}
		put(c, nd, xs, 7, -5, lanternUp, CitadelCanvas.DECOR);
		put(c, nd, xs, 7, 5, lanternUp, CitadelCanvas.DECOR);
		// eight chandeliers on the ribs: a chain from the dome and a lantern above head height
		for (int j = 0; j < 8; j++) {
			double a = j * Math.PI / 4;
			int dx = (int) Math.round(8.5 * Math.cos(a));
			int dz = (int) Math.round(8.5 * Math.sin(a));
			double d = Math.hypot(dx, dz);
			if (dx >= g.xs() || !in(c, nd, dx, dz) || arc(dx, dz, 16, 0) > 0.6) {
				continue;
			}
			int lo = Math.max(g.h + 1, Math.min(g.top(Math.min(g.rw, d + 1)), g.top(d)));
			for (int y = g.h - 3; y < lo; y++) {
				put(c, nd, dx, y, dz, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
			}
			put(c, nd, dx, g.h - 4, dz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}

	private static void put(CitadelCanvas c, Node nd, int dx, int y, int dz, BlockState s, int layer) {
		if (in(c, nd, dx, dz)) {
			c.put(nd.cx() + dx, y, nd.cz() + dz, s, layer);
		}
	}
}
